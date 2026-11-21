# Native DSD Decoder (`shared/dsd`)

Native C++ (`cymatic_dsd`) and Kotlin wrapper (`DsdFrameDecoder`) for decoding planar DSF blocks into 24-bit PCM or bit-perfect DSD over PCM (DoP).

## Input packet format

Each call to `DsdFrameDecoder.decode` expects a direct `ByteBuffer` containing:

- 4-byte little-endian `uint32` sample count (`1 .. 32768`, corresponding to `BLOCK_BYTES * 8`)
- 1 or 2 planar channel blocks of `4096` bytes (`BLOCK_BYTES`) each

Partial trailing bytes are padded with `0x69` DSD silence, and LSB-first streams are bit-reversed before processing.

## Supported rates

- **44.1 kHz family:** DSD64 (`2.8224 MHz`), DSD128 (`5.6448 MHz`), DSD256 (`11.2896 MHz`), DSD512 (`22.5792 MHz`)
- **48 kHz family:** DSD64 (`3.072 MHz`), DSD128 (`6.144 MHz`), DSD256 (`12.288 MHz`), DSD512 (`24.576 MHz`)
- **Channels:** Mono or stereo

## Output modes

Both modes write packed 24-bit little-endian samples into a direct output `ByteBuffer`:

- **PCM conversion (`dop = false`):** Decimates to `176.4 kHz` (44.1 kHz family) or `192 kHz` (48 kHz family) using a precomputed byte-indexed FIR low-pass lookup table (`decimation * 48` taps, 40 kHz cutoff, 4-term Blackman-Harris window).
- **DoP packing (`dop = true`):** Requires `rate / outputRate == 16`. Packs 16 DSD bits per 24-bit carrier sample with alternating `0x05` and `0xFA` marker bytes in the most significant byte.
