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
