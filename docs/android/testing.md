# Android testing strategy

Baseline analysis: no Android module, DI container, unit/UI tests or CI existed
before #10. The Python tests remain independent. The approved design needs simple
constructor injection, so test doubles implement the shared interfaces directly;
Hilt, Mockito and a navigation framework add no value to this single-screen app.

- Local JUnit4: immutable edit defaults/snapshots and typed errors now; parser,
  color math, scheduling and state-machine behavior in later issues.
- Instrumented JUnit4/Compose: real Android Bitmap/Uri interfaces, test-only
  repository/renderer/exporter substitution, disabled empty state, recreation,
  and the installed manifest. Later tests exercise actual decoding, GL,
  MediaStore, accessibility and the full user journey.
- Generated fixtures rather than personal photos: eight EXIF orientations,
  transparency, gamut/reference patches, corrupt/large inputs. Added by #11/#12.
- CI: `assembleDebug testDebugUnitTest lintDebug` plus an API 29 emulator job.
  Reports and APK artifacts are retained by the workflow. Building tests is not
  evidence they passed; inspect their result reports.
- Physical device: vivo X200 Pro mini, pending connection and property capture.
  Record API, RAM, GPU/driver, commit and build type. Color error checks and
  action-to-frame p95 measurements on hardware are required for final v1.

Run from `android/`:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

No screenshot baseline plugin or alpha dependencies are introduced for the empty
shell. Later editor acceptance records portrait/landscape screenshots and an
interaction recording, not arbitrary pixel-perfect design requirements. The final
validation matrix belongs in `docs/android/v1-validation.md` under #15.
