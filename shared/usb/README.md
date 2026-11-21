# Native USB Isochronous Transport (`shared/usb`)

Native C++17 (`cymatic_usb`) and Kotlin wrapper for streaming PCM and DoP audio directly to USB Audio Class DACs over Linux `usbdevfs` isochronous URBs, bypassing Android's audio mixer.

## Components

- `UsbPcmStream` (`UsbPcmStream.kt` / `UsbPcmStream.cpp`): Continuous background-threaded isochronous output stream.
  - Uses a ring buffer fed via direct `ByteBuffer` writes from Kotlin.
  - Pipelines up to 16 in-flight `USBDEVFS_URB_TYPE_ISO` URBs (`USBDEVFS_URB_ISO_ASAP`), up to 64 packets per URB.
  - Supports initial priming frames, pause/resume, mute, graceful end-of-stream draining, and DoP silence/marker continuity (`0x05` / `0xFA`).
  - Exposes a 22-entry statistics array (completed frames/bytes/packets/transfers, underruns, packet errors, short packets, feedback telemetry) surfaced in **Output info**.
- `UsbFeedback.h`: Asynchronous UAC feedback endpoint parser and validator.
  - Accepts 3-byte (Full-Speed 10.14) and 4-byte (High-Speed 16.16) feedback payloads.
  - Rejects out-of-range values outside +/-5% of the nominal rate or exceeding the endpoint's max packet frame capacity.
- `UsbIsochronousConnection` (`UsbIsochronousConnection.kt` / `UsbIsochronousConnection.cpp`): Low-level connection helper for querying USB speed, running bounded one-shot PCM transfers, inspecting attached kernel interface drivers, reconnecting interfaces, and cancelling in-flight transfers.

## Build notes

Built via CMake (`3.22.1`) with `-std=c++17 -Wall -Wextra -Werror` and Android NDK `29.0.14206865`.
