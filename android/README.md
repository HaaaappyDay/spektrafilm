# Film Editor for Android

Offline film photo editing based on spektrafilm. The [v1 contract](../docs/android/v1-contract.md)
is tracked in [issue #9](https://github.com/HaaaappyDay/spektrafilm/issues/9).

## Current milestone

Editor and gallery export (#13/#14, dependent on renderer #12): import a static SDR JPEG/PNG, choose
Portra 400, Gold 200 or Ektar 100, adjust film strength/exposure/temperature,
compare with the normalized original and reset the selected film's controls.
Gain-map JPEGs use their SDR base; direct HLG/PQ inputs are rejected. The source
is copied privately without modifying the original. Failed/cancelled imports
keep the old photo; retry is available. Save re-decodes the original at a 4096
long-edge cap and publishes a real JPEG to `Pictures/FilmEditor`. The Open action
displays the saved URI in an available system image viewer.

See [import validation](../docs/android/import-validation.md) for size limits,
color/orientation behavior, synthetic fixture provenance and verification status.
The editor preserves source/parameters across rotation and restores the last
committed private session by decoding/rendering again after process death.
See [color validation](../docs/android/color-validation.md) for the numerical
reference and outstanding physical-GPU gate, and
[editor validation](../docs/android/editor-validation.md) for state/UI evidence.
See [export validation](../docs/android/export-validation.md) for JPEG metadata,
original preservation, publication/recovery and fault-injection evidence.

## Reproducible build

Install JDK 17 and Android SDK command-line tools. Set `JAVA_HOME` and
`ANDROID_HOME` to your installations (or set `sdk.dir` in ignored
`android/local.properties`). Accept the SDK licenses using `sdkmanager --licenses`,
then install:

```sh
sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed for
local testing, not Play distribution. The wrapper verifies the downloaded
Gradle ZIP using `distributionSha256Sum`. Build downloads require networking;
the installed app does not request Internet permission. No Python installation,
Qt, preset baking or developer-specific paths are required to build Android.

| Component | Pinned version |
| --- | --- |
| Minimum / compile / target SDK | 29 / 36 / 36 |
| SDK Build Tools | 35.0.0 |
| JDK / JVM target | 17 |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| Kotlin + Compose compiler plugin | 2.2.21 |
| Compose BOM | 2025.10.01 |
| Activity / Core | 1.11.0 / 1.17.0 |
| Lifecycle | 2.9.4 |
| Coroutines | 1.10.2 |
| ExifInterface | 1.4.2 |
| Espresso (instrumentation only) | 3.7.0 |

These are intentionally pinned stable versions, not an assertion that each is
the newest release. AGP 8.13 supports SDK 36 and requires Gradle 8.13/JDK 17.
The Kotlin Compose plugin shares the Kotlin compiler version. Compatibility sources:
[AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[Kotlin/AGP support](https://developer.android.com/build/kotlin-support),
[Compose compiler](https://developer.android.com/jetpack/androidx/releases/compose-kotlin),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom/bom-mapping),
[Gradle checksums](https://gradle.org/release-checksums/).
Dependency versions are centralized in `gradle/libs.versions.toml`.
Espresso is pinned explicitly because Compose's transitive 3.5.0 reflects a
removed `InputManager.getInstance` method on API 37; the stable
[3.7.0 release](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)
uses the public system service instead. This dependency is only in the test APK.

## Device verification

Use an Android 10+ device with GLES 3. Enable USB debugging and authorize your
computer on the device; `adb devices -l` must show `device`, not `unauthorized`.
For multiple devices set `ANDROID_SERIAL` to the chosen device.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n io.github.haaaappyday.filmeditor/.MainActivity
./gradlew :app:connectedValidationAndroidTest
```

Instrumented tests install a separate `io.github.haaaappyday.filmeditor.validation`
app and its own fixture provider. They may reset that validation app’s session;
the regular debug app and its session remain separate.

The process-death check is a separate host-driven test. It keeps the prepared
instrumentation process alive, verifies its PID, force-stops that exact validation
package, and then checks restoration in a new process. The generated source and
edits must survive; an unpublished export must be removed, and an already
published JPEG must remain byte-identical with no duplicate save. Run after the
connected suite (replace `SERIAL` with the selected device's serial):

```sh
adb -s SERIAL install -r app/build/outputs/apk/validation/app-validation.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/validation/app-validation-androidTest.apk
python3 ../scripts/verify_android_process_restore.py --serial SERIAL --out app/build/reports/process-restoration
```

The driver uses Python's standard library, requires no Python project install,
and cleans up only its tracked generated images in the validation app. Its
`process-restoration.json` and phase logs are retained in CI. This check models
death after pending-row journaling; it does not claim to reproduce every possible
instruction boundary during export. Normal connected tests exclude the host-only
phases, which must not be run alone without their driver.

CI builds, unit-tests and lints on Linux, uploads APK/reports, and runs device
checks on API 29, 36 and 37.2 emulators (37.2 uses the Google APIs 16 KB image). Reports live under `app/build/reports/` and
`app/build/outputs/androidTest-results/`. See [testing strategy](../docs/android/testing.md).
The maintainer's reference device is vivo X200 Pro mini. Its API, RAM, GPU and
performance must be measured from the connected device; emulator results do
not establish physical-device color/performance acceptance.

The integrated API 29/36/37.2 results and remaining physical gates are recorded
in [v1 validation](../docs/android/v1-validation.md). To build the isolated,
non-debuggable, shell-profileable measurement app and its instrumentation APK:

```sh
source_commit="$(git rev-parse HEAD)"
./gradlew :app:assembleProfileable :app:assembleProfileableAndroidTest -PvalidationBuildType=profileable -PsourceCommit="$source_commit"
```

It is signed with the local debug key and uses the validation app ID, so it
replaces a previous validation installation. Its manifest enables shell profiling
without making the app debuggable. Building this APK is not performance acceptance. The host process
restoration driver above requires the debuggable `validation` variant, because
it reads private ready markers with `run-as`.

For the performance workload, use Android 12+ with FrameTimeline and an unlocked
screen. The host requires Python 3.11+, ADB, the pinned build-tools `aapt2`, and
the official Perfetto helpers (v59.1). Install the helpers once:

```sh
mkdir -p "$HOME/.cache/perfetto-tools"
curl -fL https://raw.githubusercontent.com/google/perfetto/v59.1/tools/record_android_trace -o "$HOME/.cache/perfetto-tools/record_android_trace"
curl -fL https://raw.githubusercontent.com/google/perfetto/v59.1/tools/trace_processor -o "$HOME/.cache/perfetto-tools/trace_processor"
chmod +x "$HOME/.cache/perfetto-tools/trace_processor"
python3 ../scripts/record_android_v1.py --serial SERIAL --source-commit "$source_commit" --out app/build/reports/performance-RUN
```

Use a new output directory for each run. The driver installs only the isolated
profileable APKs, verifies the APK's embedded commit, records SHA256 identities,
and retains the workload JSON, raw trace, query rows and report. It warms all
three LUTs and one 12MP export, then measures 30 real accessibility slider
actions after five warmups, followed by 20 import/edit cycles and 10 exports.
Only generated fixtures and new validation-app outputs are cleaned up.

Preview latency starts before accessibility dispatch and ends at the linked
SurfaceFlinger display frame; a draw or application GPU completion alone cannot
pass. Export timing ends at successful MediaStore publication and includes
decode/render/encode/IO stage details. Memory is sampled every 100ms, so reported
peaks are sampled lower bounds; Java used heap, native allocated bytes and
graphics PSS describe different memory categories. Unknown counters stay unknown.
Resource checkpoints use a consistent post-GC policy outside timed operations;
all 31 checkpoints and trends are retained. They require trace/retention review
and never automatically certify the absence of a leak. Emulator reports remain
`emulator-only`, regardless of timings. The complete local harness dry run and
API 36 CI workload have passed artifact validation at commit `d274014`; see
[v1 validation](../docs/android/v1-validation.md) for the retained samples and
limitations. The local preview p95 was 263.906ms, above the 200ms target; no
samples were discarded. Physical v1 acceptance still requires the reference phone.
The helper tag identifies the downloaded scripts; `recording.json` records the
actual trace-processor version, which may differ from the helper tag.

## Boundaries and ownership

One `app` module, package `io.github.haaaappyday.filmeditor`, constructor injection.
`core/` defines immutable `ImageSource`, `EditParams`, `FilmPreset`, and the
suspending `ImageRepository`, `PresetRepository`, `FilmRenderer`, `PhotoExporter`
APIs. Exact signatures, color math, lifecycle semantics and limitations are in
the versioned contract. Test doubles live only in test source sets.

Decoded/rendered bitmaps are software-readable ARGB_8888, opaque sRGB, orientation
normalized exactly once. Decode/render return exclusively owned new bitmaps;
render must not mutate or recycle its input. Callers own results and must not
recycle displayed bitmaps. I/O, decoding and GL work run off main; cancellation
propagates unchanged. Missing presets and invalid parameters produce typed
errors instead of substituting defaults. No Bitmap/GL objects enter saved state.

Final v1 imports static SDR JPEG/PNG only, previews at long edge ≤1600 and exports
JPEG quality 95 to `Pictures/FilmEditor` at long edge ≤4096 without upscaling.
Output dimensions are shown before saving; original GPS/ICC/orientation metadata
is not copied. A fresh EXIF sRGB/normal-orientation marker describes the normalized
pixels. Saving disables edits/import/duplicate requests. Interrupted unpublished
outputs are recovered using provider-verified ownership; published or foreign
gallery photos are preserved. Export results are never replayed after process death.

## Attribution

Film modeling powered by [spektrafilm](https://github.com/andreavolpato/spektrafilm),
by Andrea Volpato and contributors. Preserve the repository's [GPLv3 license](../LICENSE)
and [asset license text](../SPEKTRAFILM_LICENSE.txt). Generated presets, their
attribution/license manifest and bake reports are in `app/src/main/assets/presets/`.
The Python reference implementation and its existing behavior remain unchanged.
