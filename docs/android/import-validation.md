# Photo import validation — issue #11

Implementation branch: `feat/android-import`, following #10 / merged PR #17.
Final CI results and PR link will be recorded after tests pass. This document is
not a claim that pending tests or physical-device checks have passed.

## Behavior and engineering bounds

- AndroidX `PickVisualMedia(ImageOnly)` automatically falls back to the system
  document picker. No broad storage/gallery permissions are added.
- Actual content, not provider MIME/extension, determines JPEG/PNG acceptance.
  Animated PNG is explicitly rejected even when a platform returns its first
  frame as a static image. HDR transfer functions and detected gain maps are
  rejected rather than advertised as verified SDR conversion.
- Inputs are streamed into UUID `.part` files under `noBackupFilesDir/sources`,
  fsynced, validated and renamed in the same directory. Original bytes remain
  untouched. An app-private `.source` survives loss of the initial URI grant.
- Input cap: 100 MiB; image header cap: 100 MP, max dimension 32768. Preview cap
  1600; decode/export-ready cap 4096. No upscaling; dimensions round to the
  nearest integer with a minimum of one pixel. UI shows actual preview size.
- ImageDecoder samples before producing the decoded bitmap, applies EXIF
  rotation/mirroring, and requests sRGB. A second explicit opaque sRGB target
  composites PNG transparency onto white. There is no relabeling of P3 pixels.
- Coroutine cancellation cleans unpublished copies/results, including the
  dispatcher-return boundary. Process-local active-part tracking prevents a
  second repository from deleting an in-progress import during stale cleanup.
- Paths must be canonical UUID-named files directly in the source directory;
  forged paths and symlinks cannot delete or decode external files.
- The import-only ViewModel preserves preview over Activity recreation, cancels
  obsolete imports, retains old photo on failure/cancel and offers retry.
  Full editor/session restoration after process death and completed orphan-source
  collection remain #13 work. Only abandoned partials are collected here.

## Fixtures and independent references

Committed original synthetic fixtures and SHA256SUMS live in
`android/app/src/androidTest/assets/import-fixtures/`. Generator:
`scripts/generate_android_import_fixtures.py` (Python 3.13.18, Pillow 12.1.0,
system LittleCMS2). Regeneration was run twice and produced identical hashes.
The script writes only to `--out`; ordinary Android builds never invoke Python.

- Eight EXIF JPEGs: asymmetric colored corners/marker. Expected orientations
  come from Pillow's EXIF transpose of the actual JPEG bytes.
- Tagged sRGB/Display-P3 and untagged patches: expected sRGB from Pillow/LCMS,
  per-channel error tolerance ≤2/255. P3 ICC is generated from primaries, D65
  and the sRGB curve, not borrowed from an external image asset.
- Transparency: exact encoded-sRGB white composite, ±1 quantization tolerance.
- 12/24/48MP JPEGs, tiny and extreme-aspect PNGs exercise real decoder sizing.
  Large solid images test sampling/allocation, not worst-case entropy/runtime.
- Corrupt/truncated/animated/unsupported samples. Test-only ContentProvider
  exposes genuine content:// URIs, lies about MIME and supports revoked access.

## Checks

Run from `android/`:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

CI runs API 29 and 36 x86_64 emulators, debug builds, and collects JUnit XML,
normalization/import/UI results and generated-photo screenshots. No physical
vivo X200 Pro mini evidence exists until ADB is connected. Main-thread latency,
GL rendering and final end-to-end performance are evaluated in later issues.
