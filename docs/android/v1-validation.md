# Android v1 acceptance — in progress

Tracking: [#15](https://github.com/HaaaappyDay/spektrafilm/issues/15),
[parent #9](https://github.com/HaaaappyDay/spektrafilm/issues/9).
This report is not a release acceptance. Physical color/performance evidence,
final clean-checkout delivery and prerequisite merges remain incomplete.

## Build identity and evidence

- Integrated candidate `f4691c5ce97bb06dd84ef9aec60043fbde2fc1be`:
  [CI run](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43557283930).
  API 29 and 36 each completed 75 connected tests successfully. The subsequent
  host driver failed while polling an absent ready file (empty ADB output). A
  tested polling fix is prepared; process restoration is not yet a pass. API 37.2
  is still pending.
- Local Ubuntu: Temurin 17.0.21, Gradle 8.13, SDK compile/target 36,
  Build Tools 35.0.0. Validation APK and instrumentation APK compile; 9 debug
  unit tests pass, lint has no errors. Python regression: 119 passed, including
  10 process-driver report-validation tests. These are not device acceptance.
- Instrumentation uses `io.github.haaaappyday.filmeditor.validation` and its
  corresponding test/provider package. The ordinary debug APK retains
  `io.github.haaaappyday.filmeditor`. Version name/code remain `0.1.0`/`1`.
- Each connected run emits `device-build.json`: actual API/release/fingerprint,
  model, RAM, heap, GLES vendor/renderer/version, package, debuggability and
  source/tested commits. Physical acceptance is never inferred from the model.

## Requirement coverage

| Requirement | Test/evidence | Acceptance state |
| --- | --- | --- |
| API 29, current stable API, physical GLES 3 | CI API 29/36/37.2; `DeviceEnvironmentTest` | API 29/36:75 tests pass; API 37.2 pending; vivo not run |
| Generated orientation/color/size/error fixtures | `AndroidImageRepositoryTest`, normalization/sizing tests; [import report](import-validation.md) | Prior emulator evidence available; current candidate pending |
| Each film, all controls, original/reset, airplane-mode gallery | `PhotoEditingFlowTest`; `v1-flow.json`, gallery screenshot | API 29/36 connected tests pass; API 37.2 pending |
| Failed/cancelled import preserves edits; 48MP replacement resets/samples | `LifecycleAndRecoveryTest`; `v1-48mp.json` | API 29/36 connected tests pass; API 37.2 pending |
| No-photo controls, latest result, temperature-only race, stale import | `EditorScreenTest`, `EditorViewModelTest`; [editor report](editor-validation.md) | Prior coverage plus new deterministic races; candidate pending |
| Background/rotation/export exclusivity | `EditorActivityTest`, `ExportActivityTest` | Prior emulator evidence; candidate pending |
| Actual process kill/restoration and unpublished output recovery | Host driver + `ProcessRestorePhases`; `process-restoration.json` | Host polling failed; tested fix awaits runtime verification |
| Missing private source recovers to import | `LifecycleAndRecoveryTest` | API 29/36 connected tests pass; API 37.2 pending |
| GL context recreation and cancellation | `GlFilmRendererTest`; new partial-tile and injected-loss tests | Existing recreation passes; new cancellation and bounded-retry cases compile, runtime pending |
| No space, I/O failure, cancellation, guarded rollback | `MediaStorePhotoExporterTest`; [export report](export-validation.md) | Prior emulator evidence; candidate pending |
| Shared preview/export math; same-size pre-JPEG max error ≤2 codes | Real GLES/Python fixture comparisons and export tests | Prior emulator max 1 code; physical GPU not run |
| EXIF, transparency, dimensions, original hash and GPS removal | Import/export fixtures and byte/hash/metadata checks | Prior emulator evidence; candidate pending |
| Fresh Android-only build; three real presets/licenses; identifiable APK | Final clean checkout, APK inventory/hash/artifact | Final delivery not run |
| Python compatibility and committed reference bytes | 119 local Python tests; CI reference reproduction | 119 local tests pass; candidate CI 118 tests/reference reproduction passed |

Historical color/export numbers apply only to the commits documented in the
linked reports. They are not evidence for an untested candidate or physical GPU.

## Performance and sustained resources

Reference phone: maintainer-provided vivo X200 Pro mini. No authorized device is
currently visible to ADB. Its actual API/RAM/GPU/driver must be measured on connection.

| Measurement | Required evidence | Status |
| --- | --- | --- |
| 1600px preview | Profileable build, warmup then 30 user-action→presented-frame samples, p50/p95; p95 ≤200ms | Not run; trace harness incomplete |
| 12MP export | Button→MediaStore publication ≤10s, dimensions, decode/render/encode/I/O phases, peak Java/native/graphics memory | Not run |
| Sustained resources | 20 import/edit cycles +10 exports, post-cache bitmap/GL/thread/resource trends | Not run |
| 48MP behavior | Bounded sampling or clear rejection, no OOM/ANR, heavy work off Main | Emulator functional sampling test exists; physical trace not run |

Emulator timings, shader-only timing and completed Compose draws do not satisfy
the physical presentation target. Missing/unsupported trace data must remain
unverified rather than be interpreted as zero latency or zero allocations.

## Reproduction and artifacts

See [Android README](../../android/README.md) for SDK setup, build, isolated
connected tests and the explicit-serial process driver. For #15's clean-checkout
command, `connectedValidationAndroidTest` replaces `connectedDebugAndroidTest`
so session-resetting tests cannot clear the everyday app's state. It executes
the same real application services under a separate application ID.

CI retains the debug APK/build reports, connected XML/HTML and additional JSON/
PNG output, process prepare/verify/cleanup logs, stable SDK inventory and Python
JUnit report. The host kills a verified live prepared process after its private
source/edit and pending export journal are durable, then verifies a different
PID, preserved published bytes and no duplicate export. This models that crash
boundary; it does not prove all possible instruction boundaries in an export.

Final APK SHA256/link, physical traces, measured performance, screenshots and
the final requirement-by-requirement reconciliation will be recorded here before
acceptance. Dependent PRs 19/20/21 and this validation PR must merge in order;
their current draft state is not completion of issues 12–15.
