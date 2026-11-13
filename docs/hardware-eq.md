# USB DAC hardware EQ

Open **Equalizer → EQ target → USB DAC**. The existing player EQ and its selection
stay independent of the DAC draft. Both touch and wheel layouts support the editor.
Player EQ bands also live under a single **Bands** page. Back returns to the
containing EQ page; nested band controls return to the band list.

The main page shows the DAC status, preset, preamp, bands and
Apply. There is no Options page. The connected DAC's current EQ is loaded
once per connection, after USB access is granted. This read does not switch slots
or write settings. Resuming the app does not overwrite local edits. Tap the DAC name/status to open Device details. With several supported DACs
connected, **Preset → Select DAC** changes the device.

**Preset → Load from DAC** refreshes the draft manually. **Preset → Load preset**
loads an app preset; **Save as preset** saves the draft to the app library. Editing
or selecting presets does not write to the DAC. **Apply to DAC** writes explicitly,
backs up the destination first and verifies the result. On multi-slot DACs it uses
the active USER slot, or the default USER slot when a factory preset is active.

Some DACs restart after saving. **Verify saved EQ** reads the returning DAC; no
success is reported before the result matches. A mismatched save is reported as
failed and can be inspected by loading the current DAC EQ again. Automatic backup
files are retained internally; there are no Flat draft or Previous DAC EQ actions.

Credits and licenses are grouped under **Settings → Licenses**, after **Version**.
See [license inventory](licenses.md) for coverage and update instructions.

The hardware catalog is vendored from Contour commit
`95d592df1f6985991ecfed86470525f3b48b6a79`: Protocol Micro/Max, TRN Black Pearl,
FiiO KA15/K13 R2R/JA11, SnowSky Melody/Tiny A, Moondrop Dawn Pro 2/Echo-A/DUSK-SP,
Kiwi Ears Allegro Mini and JCally JM12. Identity checks include the exact USB product
name where the catalog requires it. Other DACs retain player EQ, without hardware writes.
These are upstream-supported devices; Cymatic still needs physical-device testing.

Known behavior:

- Saved DAC EQ affects audio from other apps and other hosts too. Player EQ combines
  with it on Android output; direct USB playback bypasses the player EQ processor.
- Protocol Micro/Echo-A use a fixed 93,750 Hz DSP clock. High shelves are represented
  as low shelves plus preamp; their gain and preamp use whole dB steps.
- KT Micro tuning assumes a 48 kHz audio stream. Different playback sample rates
  shift its filters. The hardware EQ switch is separate and read back after saving.
- A DAC without an EQ preamp requires a 0 dB preamp in the profile.
- A state that cannot be imported into an exact backup is not overwritten.
- Only the HID interface is claimed for EQ; audio interfaces stay with their owner.
  FiiO models needing an audio stream get a silent Android track unless Cymatic
  already owns a direct USB stream. No firmware update or factory reset UI is exposed.

Protocol code, source/version and license notes are in [shared/peq](../shared/peq/README.md).
Licenses are also available from **Settings → Licenses** and are bundled in `shared/music/src/main/assets/licenses/contour/`.

Build:

```text
gradlew :app-player:assembleDebug :app-launcher:assembleDebug
```

Verify EQ operation on each physical DAC.
