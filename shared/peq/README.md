# USB DAC PEQ protocols

Vendored from [Contour](https://github.com/Chronosauros/contour), commit
`95d592df1f6985991ecfed86470525f3b48b6a79` (Apache-2.0).

This module isolates the device catalog, protocol math and HID client from Cymatic's
editor and playback. The existing player EQ stays separate. Device identity is
strict (VID/PID and product name where the upstream catalog requires it); unknown
DACs are never written. Upstream proven-feature gates remain in release mode,
including in Cymatic debug builds.

Changes and full licences: `../music/src/main/assets/licenses/contour/`.
Update source and device records together. The core's unused
serialization annotations and text codecs are omitted. No firmware updates or
factory resets are exposed by Cymatic.
