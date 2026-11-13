# Film Editor for Android

Offline film photo editing based on spektrafilm. The [v1 contract](../docs/android/v1-contract.md)
is tracked in [issue #9](https://github.com/HaaaappyDay/spektrafilm/issues/9).

## Current milestone

Foundation (#10): shared import/render/export interfaces, an installable Compose
empty state, and CI. The import button is intentionally disabled until #11.
Film presets, adjustments and export arrive in #12–#14. This milestone is not
an operational photo editor. No production fake simulates successful editing.

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
| Coroutines | 1.10.2 |

These are intentionally pinned stable versions, not an assertion that each is
the newest release. AGP 8.13 supports SDK 36 and requires Gradle 8.13/JDK 17.
The Kotlin Compose plugin shares the Kotlin compiler version. Compatibility sources:
[AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[Kotlin/AGP support](https://developer.android.com/build/kotlin-support),
[Compose compiler](https://developer.android.com/jetpack/androidx/releases/compose-kotlin),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom/bom-mapping),
[Gradle checksums](https://gradle.org/release-checksums/).
Dependency versions are centralized in `gradle/libs.versions.toml`.

## Device verification

Use an Android 10+ device with GLES 3. Enable USB debugging and authorize your
computer on the device; `adb devices -l` must show `device`, not `unauthorized`.
For multiple devices set `ANDROID_SERIAL` to the chosen device.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n io.github.haaaappyday.filmeditor/.MainActivity
./gradlew :app:connectedDebugAndroidTest
```

CI builds, unit-tests and lints on Linux, uploads APK/reports, and runs device
checks on an API 29 emulator. Reports live under `app/build/reports/` and
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
is not copied. These features are pending subsequent issues, not foundation behavior.

## Attribution

Film modeling powered by [spektrafilm](https://github.com/andreavolpato/spektrafilm),
by Andrea Volpato and contributors. Preserve the repository's [GPLv3 license](../LICENSE)
and [asset license text](../SPEKTRAFILM_LICENSE.txt). Actual generated presets and
their attribution/license manifest will be committed in #12. The Python reference
implementation and its existing behavior remain unchanged.
