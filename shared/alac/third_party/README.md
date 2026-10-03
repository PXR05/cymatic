Apple's Apache-2.0 ALAC decoder is vendored from:

https://github.com/macosforge/alac/tree/c38887c5c5e64a4b31108733bd79ca9b2496d987

Only decoding sources are compiled. The Android build does not fetch dependencies. Original copyright and license notices remain in the source, and the license is packaged in `assets/licenses/AppleALAC.txt`.

Local changes:

- `ALACBitUtilities.h/.c`: bounded bit reads and advances, with a sticky error flag; no encoder support.
- `ag_dec.c`: bounded Rice decoding with the original adaptive mean and zero-run algorithm.
- `ALACDecoder.cpp`: normalized configuration validation, bounded packet/frame/channel handling, predictor-mode validation, complete frame consumption and zero-padding checks; missing channels are rejected.
- `dp_dec.c`: bounded short-frame predictor warmup, safe zero denominator shift and unsigned sample shifts.
- `matrix_dec.c`: unsigned shifts for signed PCM packing.

Signed predictor arithmetic uses wrapping semantics, as required by the reference implementation. Android little-endian byte order is set explicitly for all supported ABIs.

Upstream source hashes are recorded in `UPSTREAM-SHA256.txt`; modified files are marked in their license headers.
