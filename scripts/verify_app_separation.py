"""Check the merged Android manifests after assembling both products.

Usage: python scripts/verify_app_separation.py [debug|release]
Uses only the Python standard library; also runs in CI.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
VARIANT = sys.argv[1] if len(sys.argv) > 1 else "debug"
if VARIANT not in ("debug", "release"):
    raise SystemExit("Expected debug or release")

LAUNCHER_PERMISSIONS = {
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.EXPAND_STATUS_BAR",
    "android.permission.READ_MEDIA_IMAGES",
}

for product, package in (("player", "com.pxr.cymatic"), ("launcher", "com.pxr.cymatic.launcher")):
    manifests = list((ROOT / f"app-{product}/build/intermediates/merged_manifests/{VARIANT}").rglob("AndroidManifest.xml"))
    assert len(manifests) == 1, f"Build app-{product} {VARIANT} first"
    manifest = ET.parse(manifests[0]).getroot()
    assert manifest.get("package") == package, f"Incorrect {product} application ID"
    app = manifest.find("application")
    activities = app.findall("activity")
    main = next(a for a in activities if a.get(ANDROID + "name") == "com.pxr.cymatic.MainActivity")
    assert main.get(ANDROID + "exported") == "true"
    categories = {c.get(ANDROID + "name") for c in main.findall("intent-filter/category")}
    assert "android.intent.category.LAUNCHER" in categories
    home = "android.intent.category.HOME"
    all_categories = {c.get(ANDROID + "name") for c in app.findall(".//intent-filter/category")}
    assert (home in all_categories) == (product == "launcher"), f"Incorrect {product} HOME role"
    permissions = {p.get(ANDROID + "name") for p in manifest.findall("uses-permission")}
    assert "android.permission.READ_MEDIA_AUDIO" in permissions
    assert "android.permission.POST_NOTIFICATIONS" in permissions
    legacy = next(p for p in manifest.findall("uses-permission") if p.get(ANDROID + "name") == "android.permission.READ_EXTERNAL_STORAGE")
    assert legacy.get(ANDROID + "maxSdkVersion") == "32"
    if product == "player":
        assert not permissions & LAUNCHER_PERMISSIONS, "Launcher permissions leaked into player"
        assert app.get(ANDROID + "name") == "com.pxr.cymatic.CymaticApp"
        assert home not in all_categories
        assert not manifest.findall("queries/intent/category"), "Launcher app discovery leaked into player"
    else:
        assert LAUNCHER_PERMISSIONS <= permissions
        assert app.get(ANDROID + "name") == "com.pxr.cymatic.LauncherApp"
    service = next(s for s in app.findall("service") if s.get(ANDROID + "name") == "com.pxr.cymatic.PlaybackService")
    assert service.get(ANDROID + "foregroundServiceType") == "mediaPlayback"
    assert service.get(ANDROID + "exported") == "true"
    apks = list((ROOT / f"app-{product}/build/outputs/apk/{VARIANT}").glob("*.apk"))
    assert len(apks) == 1, f"Expected one assembled {product} APK"
    with zipfile.ZipFile(apks[0]) as apk:
        dex = b"".join(apk.read(name) for name in apk.namelist() if name.endswith(".dex"))
    prefix = f"cymatic-{product}-"
    assert prefix.encode("utf-8") in dex, "Product update configuration missing from APK"
    if VARIANT == "debug":
        # Debug bytecode keeps source class names; check the actual packaged code.
        assert b"Lcom/pxr/cymatic/PlaybackService;" in dex
        for launcher_class in (
            b"Lcom/pxr/cymatic/data/store/LauncherStore;",
            b"Lcom/pxr/cymatic/data/launcher/LauncherAppsLoader;",
        ):
            assert (launcher_class in dex) == (product == "launcher"), "Incorrect packaged launcher code"
    print(f"PASS {product}: {package}, correct entry point, permissions and playback service")
