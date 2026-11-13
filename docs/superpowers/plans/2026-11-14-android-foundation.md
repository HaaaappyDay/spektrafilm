# Android foundation implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task, then request an independent whole-branch review.

**Goal:** Complete issue #10 with a reproducible Android build, shared contracts, and an honest empty app shell.

**Architecture:** Independent `android/` Gradle root, one Compose app module. Constructor-injected interfaces isolate import, rendering and export. Python remains unchanged.

**Tech Stack:** Kotlin, Compose Material 3, GLES 3 contract, JUnit4, Compose instrumented tests, GitHub Actions. Pin a compatible stable toolchain after checking official release documentation.

**Spec:** [v1 contract](../../android/v1-contract.md); [foundation issue](https://github.com/HaaaappyDay/spektrafilm/issues/10).

## Global constraints

- minSdk 29; application ID `io.github.haaaappyday.filmeditor`; development name Film Editor.
- No network, camera or broad media/storage permissions. Require GLES 3.
- Immutable edit snapshots; exclusive output bitmap ownership; propagate cancellation.
- Ordinary builds do not run Python or bake presets. No alpha/dynamic dependencies.
- Each issue gets a separate PR; tests and review precede merge to main.

## Review focus

- Fake operations must not ship in the APK or imply successful import/export.
- Rotation/large text/system insets must leave the empty-state message accessible.
- Test doubles must compile against every exact shared signature, including suspend/release.
- Clean checkout builds must not depend on local SDK paths, generated secrets or missing wrapper files.
- APK must declare GLES 3 and lack permissions outside the approved scope.

### Task 1: Reproducible build and shared contracts

**Files:** `android/{settings.gradle.kts,build.gradle.kts,gradle.properties,gradlew,gradlew.bat}`, `android/gradle/{libs.versions.toml,wrapper/*}`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/java/io/github/haaaappyday/filmeditor/core/*.kt`, `android/app/src/test/java/io/github/haaaappyday/filmeditor/core/ContractTest.kt`, test-only `fakes/`.

**Interfaces:** Produce exactly the nine core types/interfaces plus ExportRequest/ExportResult/EditorErrorCode specified in the contract. Consumers in #11–#14 must not need alternate models.

- [ ] Pin SDK/AGP/Gradle/JDK/Kotlin/Compose versions; obtain the official wrapper and verify its distribution checksum.
- [ ] Write contract tests for 1/0/0 defaults, immutable copies, error cause/code preservation and substitution of all four repositories/services. Observe failure before adding contracts.
- [ ] Implement contract files with ownership, range and cancellation documentation.
- [ ] Run `cd android && ./gradlew :app:testDebugUnitTest`; expect all tests passing.

### Task 2: Installable, accessible empty shell

**Files:** `MainActivity.kt`, `ui/theme/Theme.kt`, `res/values/strings.xml`, `res/values-zh-rCN/strings.xml`, `res/values/themes.xml`, `src/androidTest/.../FoundationScreenTest.kt`.

**Interfaces:** Consume the resource/theme layer only; import remains disabled until #11. No production fakes.

- [ ] Write Compose tests asserting the import action is disabled and the empty state is visible after recreation; check the installed manifest has GLES 3 and no requested permissions.
- [ ] Implement an inset-aware, scrolling Material 3 empty state with localized labels and no fake results.
- [ ] Run assemble/lint and the device tests when connected; retain explicit not-run status if device is unavailable.

### Task 3: CI, documentation and review

**Files:** `.github/workflows/android.yml`, `.gitignore`, `android/README.md`, `docs/android/testing.md`.

**Interfaces:** CI uses the same wrapper/tasks as local instructions and uploads the APK and reports.

- [ ] Add Android-only CI with JDK setup, assembleDebug, testDebugUnitTest, lintDebug, artifact upload and an API 29 emulator job.
- [ ] Document versions/sources, SDK setup, exact build/device commands, test strategy and current feature boundaries.
- [ ] Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; inspect results and APK manifest. Check a clean checkout builds via CI.
- [ ] Independently review the branch against #10; address findings and rerun affected checks.
- [ ] Submit PR with evidence and `Closes #10`; merge only after required verification passes. Continue with #11.

## Execution decisions

- User confirmed #9 and delegated routine decisions. Execute inline without another planning approval round.
- Use the clean checkout on a dedicated feature branch; preserve untracked local `.agents/` skills.
- Follow the approved simple injection/stable-library requirements over skill suggestions for Hilt or experimental screenshot plugins.
- Target device is vivo X200 Pro mini; connection and actual properties remain unverified.
