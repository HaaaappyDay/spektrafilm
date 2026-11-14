# Photo import validation — issue #11

Implementation branch: `feat/android-import`, following #10 / merged PR #17.
Verified implementation head: `de2eae9d43dc6a05d552e9e8517326e8d890b4cf`.
[CI run 43541479578](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43541479578)
passed build, unit/lint and both emulator jobs. Physical checks remain pending.

## Behavior and engineering bounds

- AndroidX `PickVisualMedia(ImageOnly)` automatically falls back to the system
  document picker. No broad storage/gallery permissions are added.
- Actual content, not provider MIME/extension, determines JPEG/PNG acceptance.
  Animated PNG is explicitly rejected even when a platform returns its first
  frame as a static image. Direct HLG/PQ transfer functions are rejected. A
  gain-map JPEG uses its SDR base: the gain map is detached from the owned
  decoded bitmap before compositing. This is not an HDR tone-mapping pipeline.
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
- A native Android-generated gain-map JPEG has known RGB(100,140,170) SDR
  base pixels. The exact same committed container is checked on both APIs;
  newer Android also verifies the fixture really contains a gain map.
- Corrupt/header-truncated/payload-truncated/animated/unsupported samples. Test-only ContentProvider
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

## Review and earlier evidence

Independent branch review identified three important evidence gaps: cleanup at
coroutine dispatcher-return cancellation, actual gain-map input on older/newer
Android, and payload (rather than only header) truncation. Dedicated tests now
cover each. No critical production defect was identified by that review.

- [Boundary validation run](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43540056192):
  build, 5 unit tests, lint, and 22 device tests per API passed. At that revision,
  detected gain maps were deliberately rejected.
- [SDR-base red run](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43540674722):
  build/API29 passed; API36 ran 23 tests with exactly 2 failures at the old
  gain-map rejection. Both new tests require known SDR-base pixels. This proves
  the acceptance tests fail before the compatibility change.
- Compose captures from that run were inspected: portrait shows the complete
  asymmetric reference image and size, landscape preserves the image ratio in
  a scrolling viewport, and read failure retains the image with a retry action.
  The final editor's compact/adaptive controls remain #13 work.

A separate existing Python LUT baseline was run in preparation for #12:
`pytest tests/lut_creator/test_cube_format.py tests/lut_creator/test_builders.py tests/test_lut_mode.py`.
It produced 97 passes and 1 failure: an old test references the removed
`GrainParams.mult_usm_amount`. No Python runtime or those test files change in
#11. The obsolete test will be investigated in #12; this is not an all-green
Python-suite claim.

## Final implementation verification

Environment: Linux CI, JDK17.0.21, Gradle8.13, AGP8.13.2, debug build,
Android10/API29 and Android16/API36 x86_64 emulators. Executed commands above:
5 unit tests, 24 instrumented tests per API, zero failures/errors. Lint has
0 errors and 13 warnings (dependency/SDK update notices, missing development
launcher icon and a KTX suggestion). Final editor branding belongs to #13.

Measured normalization samples on both API levels (unchanged normalizer at
`4c35561`; current head fixes only test synchronization/documentation):

| Reference | Compared RGB channels | Maximum absolute code error |
| --- | ---: | ---: |
| All eight EXIF orientations, two caps | 192 | 0/255 |
| Tagged sRGB/P3 and untagged patches | 45 | 1/255 |
| White alpha composite | 12 | 0/255 |

These are sampled patches, not a claim about every possible image/profile.
Gain-map tests compare every pixel of a native32×24 known-color SDR base
within2/255 on both APIs. 12/24/48MP sampling, byte limits, truncated payloads,
path safety, original hashes, URI independence, cancellation cleanup and
latest-import/error/retry/rotation behavior are included in the suite.

The first SDR fix run exposed a timing flaw in the cancellation test itself:
instant fake decoding could finish before withContext suspended, so there was
no queued return continuation. Explicit latches now hold IO until suspension;
the final full suite passed with those deterministic boundaries.

Artifacts on the linked run contain the debug APK, JUnit/lint reports,
per-test normalization JSON and three Compose screenshots. No personal images
are used. Local Android tests were not run: local SDK downloads were unavailable;
CI is the actual build/device execution environment.

Verified `de2eae9` debug APK SHA256:
`c42e1d8b79fba4069a3ea9cfac1fc70c3c2e596ba5df03c2244f0cf83226312f`.
