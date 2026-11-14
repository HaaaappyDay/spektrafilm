# Android v1 acceptance — in progress

Tracking: [#15](https://github.com/HaaaappyDay/spektrafilm/issues/15),
[parent #9](https://github.com/HaaaappyDay/spektrafilm/issues/9).
The integrated emulator flow passes. Physical color/performance acceptance,
final delivery and prerequisite merges remain incomplete.

## Initial integrated candidate

Source and tested commit: `683d15eb8495205f0e6defd48cad2c6d6985a8ac`.
[CI run 43557947368](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43557947368)
completed all five jobs successfully. Fresh CI checkouts built the APK, passed
9 unit tests and lint with zero errors, passed 119 Python regression tests and
reproduced the committed Python color-reference bytes. Versions are pinned in
[the Android README](../../android/README.md).

| Runtime | Connected tests | Live process-kill recovery | Actual RAM / Java heap limit |
| --- | --- | --- | --- |
| API 29 / Android 10 | 77 pass, 0 failed/error/skipped | Pass; PID 4354→4460 | 2089517056 / 536870912 bytes |
| API 36 / Android 16 | 77 pass, 0 failed/error/skipped | Pass; PID 3437→3531 | 2593079296 / 201326592 bytes |
| API 37.2 image / Android 17, API 37 | 77 pass, 0 failed/error/skipped | Pass; PID 8467→8598 | 4103217152 / 201326592 bytes |

CI requests 2048M RAM/128M heap; the table reports values read from each running
image, rather than assuming the image honors those settings identically.
The API 37.2 image uses 16 KB pages. All three GPUs are emulator SwiftShader;
none establishes physical GPU or memory/performance acceptance.

[Compact summary](v1-evidence/summary.json) retains source identity, XML hashes,
counts and APK hash. Per-API folders retain actual device/build/GLES metadata,
full-flow and 48MP reports, process-restoration assertions, color errors and
synthetic-gallery screenshots:
[API 29](v1-evidence/api29/device-build.json),
[API 36](v1-evidence/api36/device-build.json),
[API 37](v1-evidence/api37/device-build.json).
The complete XML/HTML, phase logs and images are CI artifacts.

The debug APK is in the CI artifact
`android-debug-683d15eb8495205f0e6defd48cad2c6d6985a8ac`, version `0.1.0`/`1`.
SHA256: `ed855c35e9813fb3baee1e9da0b3744bca7cfc85ff4b3dc00335c7a650d1a154`.
This identifies the tested development candidate; the final delivery build will
be recorded after performance work and review.

## Requirement coverage

“Pass” in this table means the three emulator runtimes above, at the stated
commit. Physical checks remain separately incomplete.

| Requirement | Evidence | Status |
| --- | --- | --- |
| API 29 and current stable API compatibility | Device metadata and 77-test XML per runtime | Pass; physical GLES 3 device not run |
| Generated EXIF/color/size/error fixtures | Import/normalization tests; [import report](import-validation.md) | Pass |
| Three films, all manual controls, original/reset, airplane-mode gallery | `PhotoEditingFlowTest`; [flow JSON](v1-evidence/api37/v1-flow.json), [gallery screenshot](v1-evidence/api37/v1-full-flow-gallery.png) | Pass |
| Failed/cancelled import preserves edits; new source resets | `LifecycleAndRecoveryTest` | Pass |
| 48MP sampling without full-resolution decode | [48MP report](v1-evidence/api37/v1-48mp.json): 1600×1200 preview, 4096×3072 export decode | Pass; physical trace not run |
| No-photo controls, latest-result races, temperature-only and stale-import races | `EditorScreenTest`, `EditorViewModelTest` | Pass |
| Background/rotation and export exclusivity | `EditorActivityTest`, `ExportActivityTest` | Pass |
| Actual process death restores source/edits and cleans pending output | Host driver + `ProcessRestorePhases`; [report](v1-evidence/api37/process-restoration.json) | Pass |
| Missing private source returns to import | `LifecycleAndRecoveryTest` | Pass |
| GL recreation, bounded context-loss retry and cancellation after a completed tile | `GlFilmRendererTest`, `GlContextLossTest`, `GlPartialTileTest` | Pass |
| No space/I/O/cancellation/guarded rollback | Export tests; [export report](export-validation.md) | Pass |
| Shared preview/export math, same-size pre-JPEG error ≤2 codes | Real GLES/Python comparisons: 512001 channels, max 1, mean 0.00127148, p95 0 codes on each runtime | Pass; physical GPU not run |
| Orientation, alpha, output dimensions, original hash, no copied GPS | Import/export pixel, hash and metadata assertions | Pass |
| Python compatibility and reproducible reference bytes | 119 tests and CI byte comparison | Pass |
| Android-only checkout and APK/license inventory | [Fresh d274014 build inventory](v1-evidence/android-only-d274014.json) | Development candidate verified; final delivery pending |

Process preparation keeps a real Activity/instrumentation process alive after
its source/edits and pending export journal are durable. The host validates that
PID, force-stops the isolated app and confirms it is gone. A different process
then restores the same source hash/parameters/preview, removes unpublished output,
preserves the already published JPEG byte-for-byte, and avoids duplicate saves
or false success. Tracked fixture cleanup also passes. This tests death after
pending-row journaling, not every instruction boundary during an export.

The GL loss test injects the typed context-loss signal after real EGL creation;
it checks fresh-context recovery, one-retry bounds, typed failure on repeated
loss and successful subsequent rendering. It does not claim to reset a physical
GPU driver. The partial-tile test cancels after real draw/readback of tile one,
checks deletion of transient textures/framebuffer and preservation of the input,
then verifies the next render's pixels.

Stable Espresso 3.7.0 is pinned for API 37: Compose's transitive 3.5.0 called a
removed hidden InputManager API. The 15 previously failing UI tests now pass.
No alpha dependency or app target-SDK change was needed.

## Physical performance and sustained resources

Reference phone: maintainer-provided vivo X200 Pro mini. Only the local emulator
is currently visible to ADB. The phone's actual API/RAM/GPU/driver must be measured
on connection.

| Measurement | Required evidence | Status |
| --- | --- | --- |
| 1600px preview | Non-debuggable profileable build, warmup then 30 user-action→presented-frame samples; p50/p95, p95 ≤200ms | Physical not run; emulator harness verified |
| 12MP export | Button→MediaStore publication ≤10s, dimensions, decode/render/encode/I/O times, peak Java/native/graphics memory | Not run |
| Sustained resources | 20 import/edit cycles +10 exports, post-cache bitmap/GL/thread/resource trends | Not run |
| 48MP behavior and main-thread health | Bounded sampling or clear rejection, no OOM/ANR, heavy work off Main | Physical trace not run |

Draw completion and an app FrameTimeline slice end are not display presentation.
The measurement must follow the matching content frame to SurfaceFlinger's actual
display-frame end. Missing frame links, trace loss or fewer than 30 measured changes
must not produce a performance pass. Emulator measurements never satisfy this gate.

## Verified measurement harness and Android-only build

Source `d274014c5b37cab19d157d99a02b026c3325b66a` completed all five jobs in
[CI run 43565025046](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43565025046),
including API 29/36/37.2 instrumentation, process restoration and the full API 36
profileable workload. The earlier 77-test evidence above remains tied to its own
commit; it is not relabeled as this candidate. Full current CI artifacts remain
attached to that run. Downloading those artifacts locally temporarily failed with
TLS EOF, so the detailed measurements below are from the separately retained
local run, not numbers inferred from the CI job status.

The fresh local profileable run used an API 36 x86_64 SwiftShader emulator,
2593083392 bytes of RAM and a 201326592-byte Java heap limit. It verified the
APK's embedded source commit, non-debuggable/profileable flags and actual PID.
All three LUTs, one 12MP export and five slider changes were warmed before the
30 measured preview actions, 20 import/edit cycles and 10 measured exports.

| Local emulator measurement | Observed result | Acceptance |
| --- | --- | --- |
| Action → linked SurfaceFlinger presentation | 30 samples; p50 149.496ms, p95 263.906ms, max 278.399ms | Emulator only; misses 200ms p95 target |
| 4000×3000 export → MediaStore publication | 10 samples; max 958.282ms; decode/render/encode/I/O retained | Emulator only |
| Post-cache resources | 31 checkpoints; threads 34→34, FDs 149→149, Java +114688 bytes, native +126896 bytes | Retention review required; no leak-free claim |
| Trace and fixture cleanup | Zero reported trace errors/lost events; workload and generated-output cleanup completed | Harness validation passes |

The [complete report](v1-evidence/performance-local-d274014/performance-report.json)
retains every latency, export stage, sampled memory peak and resource checkpoint.
[Workload data](v1-evidence/performance-local-d274014/performance-workload.json)
retains actual device/GPU information and raw memory samples;
[recording metadata](v1-evidence/performance-local-d274014/recording.json) retains
APK, test APK, host-script and raw-trace SHA256 identities. The full local trace
is retained at `/tmp/v1-local-pid-harness/system.perfetto-trace`; the CI run has
its own independently recorded trace in the API 36 artifact. They are distinct
runs and their hashes/timings must not be interchanged. Graphics PSS was reported
as zero by this emulator; that does not establish zero physical GPU allocation.
100ms memory sampling yields lower bounds, not exact allocation peaks.

A fresh sparse checkout containing only `android/` built this same source using
JDK 17 and the installed SDK, without the Python project or Qt. Assemble, all 13
unit tests and lint passed (zero errors, 11 warnings). APK inspection verified
the three real 33³ LUTs against the manifest and committed bytes, their bake
reports, packaged license/attribution and changelog, and absence of Python/Qt
runtimes, Internet or broad photo/storage permissions. The
[inventory](v1-evidence/android-only-d274014.json) records all hashes and the build
command. Its locally debug-signed APK has SHA256
`4c5a1ca6bfdcd2eeb0fd4d0d28c3d4e85f8c8e59079c85b891a699eeb6ad3fa8`,
version `0.1.0`/`1`, with embedded commit `d274014`. This local APK is distinct
from the CI artifact and is not the final release delivery.

## Reproduction, limits and remaining integration

See [Android README](../../android/README.md) for SDK setup, isolated connected
tests and the explicit-serial process driver. `connectedValidationAndroidTest`
replaces #15's original `connectedDebugAndroidTest` command so tests can reset
`io.github.haaaappyday.filmeditor.validation` without touching the everyday app's
session. Both use the same application services; each variant has its own test
fixture-provider authority. The ordinary debug app keeps its original package.

Color-model/LUT limits and cube-wide QA flags remain documented in the
[color report](color-validation.md); no physics or regression snapshot was changed.
Fixtures are generated, not personal photos. The shown blue gallery image is the
known synthetic fixture exported by the actual app.

Physical traces, measured performance, final delivery and whole-branch review
remain required. PRs 19/20/21 and the eventual validation PR must merge in dependency
order before issues 12–15 or the parent can be considered complete.
