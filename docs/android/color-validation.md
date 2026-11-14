# Film presets and color validation — issue #12

Implementation and independent review complete on `feat/android-render`.
Emulator CPU/GPU validation passed; required physical-device evidence is pending.
Issue #12 remains open until that gate is satisfied.

## Reproduction

Python 3.13 with project dependencies (`pip install -e '.[dev]'`):

```sh
python scripts/build_android_presets.py --out android/app/src/main/assets/presets
python scripts/verify_android_color.py --presets android/app/src/main/assets/presets --out android/app/src/androidTest/assets/render-fixtures
python scripts/verify_android_color.py --presets android/app/src/main/assets/presets --out docs/android/color-evidence --approximation
python -m pytest tests/android tests/lut_creator/test_cube_format.py tests/lut_creator/test_builders.py tests/test_lut_mode.py
```

Executed locally with Python3.13.18: **109 passed**, 7 existing dependency/fixture
deprecation warnings. One stale test assertion referenced a removed
`GrainParams.mult_usm_amount` field; that assertion alone was removed. Current
spatial/stochastic checks and runtime/model defaults are unchanged.

Preset generation uses the existing BundleSpec/BundleBuilder, with QA enabled,
sRGB input/output, topology1lut, resolution33 and explicit native SDR headroom
(`stops_above_midgray=None`). The complete effective settings, engine commit,
generator SHA256, dependency versions and all cube checksums are in
[`manifest.json`](../../android/app/src/main/assets/presets/manifest.json).
Presets are Portra400, Gold200 and Ektar100, each paired with Portra Endura.
The original asset license is copied byte-for-byte; attribution/change history
accompany the generated cubes. No Python execution is needed to build the APK.

The generator writes artifacts within its requested output tree, uses a temporary
subdirectory there for full QA figures, then retains all numerical QA text and
bundle metadata under `bake-reports/`. Developer QA plots are omitted from APK
assets. The independent approximation plots below are committed separately.

## Independent direct-model approximation

The reference evaluates the deterministic physical pipeline at new points with
the same gamut, transfer and headroom settings; it does not use another LUT as
ground truth. Seed19103, 7227 samples: fixed RGB8 edge/random patches, 4096 additional
off-grid float samples and a1024-point gray ramp. See
[full samples and statistics](color-evidence/approximation.json).

| Film | Mean RGB code error | p95 | Maximum | Mean ΔE00 | Maximum ΔE00 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Portra400 | 0.0680 | 0.2363 | 1.3075 | 0.0412 | 0.4354 |
| Gold200 | 0.0647 | 0.2267 | 1.2394 | 0.0378 | 0.3947 |
| Ektar100 | 0.0751 | 0.2708 | 1.7298 | 0.0442 | 0.5312 |

RGB code error is absolute encoded error multiplied by255, before quantization.
These sampled approximation results are separate from the ≤2/255 Android
implementation-equivalence requirement. The three gray-ramp comparison figures
are under `color-evidence/`; Portra/Ektar were visually inspected and show close
overlap of the sampled curves, with sub-code gray-ramp residuals.

**Existing bake QA flags:** each preset reports2 pass,2 fail,12 informational
results. The failed categories are cube-wide diagonal monotonicity and output
gamut face folds. Gray centerline violations are0. For example Portra's worst
negative step is−0.0004885 (about0.125/255), and its reported face-fold fraction
is0.2025. Exact results and caveats are retained in each preset's `bake-reports`.
These flags concern invertibility/gamut behavior of the chosen deterministic
film transform; the low sampled approximation error does not erase those failed
QA results or guarantee every saturated gradient. No physical-model change or QA
threshold relaxation has been made. v1 is a forward photographic transform;
its LUT approximation and rendering fidelity are measured separately.

## Android implementation checks

Run on an attached device or the API29/API36 CI emulators:

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Tests cover strict cube headers/domains/counts/nonfinite values, R-fastest order,
identity/EV/warm-gain invariants, manifest/checksum/path failures and unknown IDs.
Independent fixtures contain all27 strength/EV/temperature combinations for each
of three films (81 cases), using asymmetric43px rows, corners, near endpoints,
transfer knees, all256 gray codes and512 seeded random colors. All84 fixture
files exactly reproduced from the committed cubes in a separate output tree.

Real GLES tests compare every RGB channel against those Python bytes and record
mean/p95/max plus GL vendor/renderer/version/fragment-highp precision. Additional
checks exercise17px tiles versus full-image rendering, nondefault domains,
context destruction/recreation, queued calls, cancellation at return and release
while active. Output remains opaque software ARGB8888 sRGB; input bytes must be
unchanged. Rebuilding a context in a controlled test is not evidence of every
vendor driver/context-loss condition.

Current CI: initial missing-parser/reference tests failed before implementation;
parser/math build and unit/lint passed at75f494f. Device compilation then failed
on the missing GlFilmRenderer, before its implementation. The complete implementation
at f4c7f8e passed [CI43543239126](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43543239126):
9 unit tests and31 device tests per API, zero failures/errors; lint0 errors,
14 warnings. The reports include all pre-existing import tests.

| Device/API | Reference | RGB channels | Mean code error | p95 | Max |
| --- | --- | ---: | ---: | ---: | ---: |
| x86_64 emulator /29 | CPU vs Python | 512001 | 0.00000390624 | 0 | 1 |
| x86_64 emulator /29 | GPU vs Python | 512001 | 0.00127148 | 0 | 1 |
| x86_64 emulator /36 | CPU vs Python | 512001 | 0.00000390624 | 0 | 1 |
| x86_64 emulator /36 | GPU vs Python | 512001 | 0.00127148 | 0 | 1 |

Both emulators report Google SwiftShader4.0.0.1 via Android Emulator OpenGL ES
Translator, GLES3.0, fragment highp23bits, max texture8192. These are two Android
API levels on the same software GPU implementation, not two hardware GPU vendors.
Exact JSON is in `color-evidence/api{29,36}-{cpu,gpu}-color-error.json`; generated
GPU patch PNGs and XML are retained in the linked CI artifacts.

Independent whole-branch review found no critical or important defects. One minor
coverage recommendation is deferred to driver/recovery validation: inject reported
context loss and cancellation after a partial tile (current tests exercise healthy
context recreation, cube-load cancellation and dispatcher-return cancellation).
The current report makes no claim that those additional fault paths were tested.

vivo X200 Pro mini ADB is not connected. Its actual API/GPU/RAM and real GPU
results remain required before #12 closes. Preview/export performance is #15;
emulator timing is not reference-device acceptance.
