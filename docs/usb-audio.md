# USB DAC, DSD & Hardware PEQ

Cymatic can bypass Android's audio mixer to send audio directly to compatible USB DACs, play DSF/DSD files, and configure hardware parametric EQ on supported dongles and DACs.

## Direct USB audio

1. Plug in your USB DAC.
2. Enable **Settings → Playback → USB audio → Direct USB**.
3. Allow USB device access when Android prompts you.

When direct USB output is active, a hollow diamond icon appears on the Now Playing screen.

### Supported formats

- **Lossless:** WAV, FLAC (including Ogg FLAC), ALAC (M4A/MP4), DSF (DSD)
- **Lossy:** MP3, Ogg Vorbis, Opus

If a track format or DAC configuration is not supported over direct USB, Cymatic automatically falls back to standard Android playback.

## DSD playback (DSF)

Cymatic plays mono and stereo DSF files at DSD64, DSD128, DSD256, and DSD512, including 48 kHz-family rates.

- **Standard playback:** Converts DSD to 24-bit PCM at 176.4 kHz or 192 kHz.
- **Direct USB playback:** Uses bit-perfect DSD over PCM (DoP) for DSD64 and DSD128 when your DAC supports the carrier rate, and PCM conversion otherwise. Higher DSD rates (DSD256/DSD512) use PCM conversion.

### Missing DSF files

If Android's media scanner does not index your DSF files automatically, add their folder under **Settings → Storage → Add directory** and rescan. Cymatic reads DSF metadata (title, artist, album, duration) and embedded ID3 cover art directly.

## USB DAC hardware PEQ

For supported USB DACs, Cymatic can read and write parametric EQ presets directly to the DAC's onboard DSP. Hardware PEQ is managed separately from the player's built-in software EQ, and unrecognized DACs are never written to.

Supported DACs:

- CrinEar Protocol Max
- CrinEar Protocol Micro
- FiiO JadeAudio JA11
- FiiO K13 R2R
- FiiO KA15
- JCally JM12
- Kiwi Ears Allegro Mini
- Moondrop Dawn Pro 2
- Moondrop DUSK-SP
- Moondrop Echo-A
- SnowSky Melody
- SnowSky Tiny A
- TRN Black Pearl

## Troubleshooting with Output info

Hold **Center** on the Now Playing screen and open **Track actions → Output info** to inspect live playback diagnostics. Pages cover:

- Overview
- Format and decoder
- Processing
- Device and volume
- USB stream health
- Recent errors and events

Rotate the click wheel to browse pages and scroll details, press Center to return, or choose **Copy report** to copy the diagnostic text when reporting an issue. Hardware details that Android cannot observe are marked explicitly.
