import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NS = {"p": "http://maven.apache.org/POM/4.0.0"}


def main():
    parser = argparse.ArgumentParser(description="Bundle dependency license notices.")
    parser.add_argument("inventory", type=Path)
    parser.add_argument("poms", type=Path)
    args = parser.parse_args()
    assets = ROOT / "shared/music/src/main/assets"
    output = assets / "licenses/dependencies"
    output.mkdir(parents=True, exist_ok=True)
    cache = Path(os.environ["USERPROFILE"]) / ".gradle/caches/modules-2/files-2.1"

    def metadata(group, name, version):
        file = args.poms / f"{group}__{name}__{version}.pom"
        root = ET.parse(file).getroot()
        licenses = [{"name": node.findtext("p:name", namespaces=NS),
                     "url": node.findtext("p:url", namespaces=NS)}
                    for node in root.findall("p:licenses/p:license", NS)]
        if not licenses:
            parent = root.find("p:parent", NS)
            if parent is not None:
                licenses = metadata(*(parent.findtext(f"p:{key}", namespaces=NS)
                                      for key in ("groupId", "artifactId", "version")))[1]
        if not licenses:
            raise ValueError(f"No license metadata for {group}:{name}:{version}")
        return root, licenses

    def bundle(text):
        digest = hashlib.sha256(text.encode("utf-8")).hexdigest()[:24]
        path = output / f"{digest}.txt"
        path.write_text(text, encoding="utf-8")
        return path.relative_to(assets).as_posix()

    def notices(archive):
        found = []
        with zipfile.ZipFile(archive) as source:
            for name in source.namelist():
                if re.search(r"(^|/)(license|notice|copying|copyright)([._/-]|$)", name, re.I) and not name.endswith("/"):
                    text = source.read(name).decode("utf-8", errors="replace")
                    if text.strip():
                        found.append(bundle(text))
                elif name == "classes.jar":
                    found.extend(notices(io.BytesIO(source.read(name))))
        return found

    entries = []
    for row in json.loads(args.inventory.read_text(encoding="utf-8-sig")):
        group, name, version = (row[key] for key in ("group", "name", "version"))
        pom, licenses = metadata(group, name, version)
        texts = []
        for archive in sorted((cache / group / name / version).glob("**/*")):
            if archive.suffix in (".jar", ".aar") and not any(token in archive.name for token in ("-sources", "-javadoc")):
                texts.extend(notices(archive))
                                                                                                  
        if not texts and all("apache" in (entry["name"] or "").lower() for entry in licenses):
            texts.append("licenses/contour/LICENSE")
        if not texts:
            raise ValueError(f"No bundled license text for {group}:{name}:{version}")
        entries.append({"title": f"{name} {version}", "coordinate": f"{group}:{name}:{version}",
                        "project": pom.findtext("p:url", namespaces=NS) or "",
                        "licenses": licenses, "documents": sorted(set(texts))})
    (assets / "licenses/dependencies.json").write_text(
        json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Bundled {len(entries)} resolved dependency records and their available runtime notices.")


if __name__ == "__main__":
    main()
