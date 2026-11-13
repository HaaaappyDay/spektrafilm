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

## Foundation verification (2026-11-14)

Commit `a25451b6476e98241b5b78ab0a4513b2e3532b35` was built from a clean CI checkout:
[workflow run 43461398565](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43461398565).

| Check | Result |
| --- | --- |
| `:app:assembleDebug` | Pass; debug APK generated |
| `:app:testDebugUnitTest` | 3 tests, 0 failures/errors |
| `:app:lintDebug` | 0 errors, 11 warnings |
| `:app:connectedDebugAndroidTest` | 4 tests passed, Android 10/API 29 x86_64 emulator, debug build |
| Independent branch review | No critical/important implementation findings |
| Physical device | Not run; ADB currently shows no devices |
| Landscape / enlarged text | Not yet exercised; include in #13 UI acceptance |

The emulator tests install and launch MainActivity, recreate it, verify disabled
import/visible notice, test service substitution/cancellation, and inspect the
installed package's GLES3 requirement and absence of sensitive permissions.
They do not establish real photo editing, GL color correctness or performance.

Lint warnings are retained rather than hidden: nine concern newer stable
SDK/toolchain/dependency versions; two concern the missing application icon and
explicit Android 12+ data extraction rules. Add the app icon with the editor UI;
ensure private source/session storage and backup rules are addressed during
#11/#13 before users import photos. No user photo storage exists in this milestone.

[Debug APK and build reports](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43461398565/artifacts/14056312504)
are available from the workflow's artifact list (artifact name
`android-debug-a25451b6476e98241b5b78ab0a4513b2e3532b35`).
APK SHA-256: `2378d4efb3ae6d99c66eb40a2d680767ee0976d704020c4e69e4d4bdb7f1000e`.
[Device test reports](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43461398565/artifacts/14056662978).
CI artifacts expire according to repository retention; rerun the pinned build to regenerate.
