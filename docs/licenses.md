# Third-party notices

**Settings → Licenses** shows shared license texts once: Apache 2.0, SIL Open Font License,
Other licenses and Attributions. Component credits and required notices remain
accessible separately. Original per-component notices remain bundled. Text is formatted for reading; original bundled
license files remain available as app assets.

The current inventory includes:

- 193 resolved Maven dependency/platform records from the debug and release
  runtime graphs of both apps. This includes metadata-only platform modules;
  it is not a count of compiled libraries in a particular APK.
- Copyright/license and NOTICE files found in their cached runtime JAR/AAR files,
  including nested `classes.jar` archives. Shared texts are stored once by hash.
- Apple ALAC and libFLAC. The compiled ALAC decoding sources carry Apple
  Apache-2.0 headers; the previous app asset incorrectly contained the repository's
  unrelated APSL text. The app asset now contains the decoder attribution,
  modification notice and Apache license; original vendored source files remain intact.
- The LLVM runtime notice from the installed NDK 29 distribution, including LLVM exceptions.
- JetBrains Mono, Departure Mono and LanaPixel, with their OFL notices and authors.
- Contour, devicePEQ and the upstream third-party notices retained with the PEQ module.

Sources for font notices:

- https://github.com/JetBrains/JetBrainsMono/blob/master/OFL.txt
- https://github.com/rektdeckard/departure-mono/blob/main/public/assets/LICENSE
- https://opengameart.org/content/lanapixel-localization-friendly-pixel-font
  (OFL text retained at https://github.com/tommyettinger/fonts/blob/main/LanaPixel-OpenFontLicense.txt).

## Updating the dependency inventory

```text
gradlew -I gradle/license-inventory.gradle exportLicenseDependencies --offline
python scripts/update_license_inventory.py .codex-tmp/license-dependencies.json PATH_TO_OFFICIAL_POMS
```

The Python script expects official Maven POM files named
`GROUP__ARTIFACT__VERSION.pom`, including parent POMs where license declarations
are inherited. It reads runtime archives from the existing Gradle cache. Missing
license metadata or license text fails generation rather than guessing from a
package name. Metadata can be obtained from Google's Maven repository for AndroidX
and Maven Central for other libraries. Keep the generated asset index and its
referenced text files together.

This inventory covers identified dependencies and vendored components. Any additional
copied code or assets need their original source, license and required notices added
separately; a dependency scan cannot identify unattributed snippets.
