# Android v1 acceptance — in progress

Tracking: [#15](https://github.com/HaaaappyDay/spektrafilm/issues/15),
[parent #9](https://github.com/HaaaappyDay/spektrafilm/issues/9).
The integrated emulator and physical functional flows pass. Physical preview,
export, and bounded resource checks pass. Final review and prerequisite merges
remain incomplete.

## Current editor feedback candidate

Source `71ca4acab807d0ca025f53e2ab834f868802a435` contains the photo-first editor,
stock-specific cartridge icons (#23), stable preview bounds during loading (#24),
and temporary long-press original comparison with the toolbar toggle retained (#25).
[CI run 43605020612](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43605020612)
passed all five jobs at tested merge `1af0cd7cb3dd88f318b825214917f3e2aeb0e9ea`:
88 instrumented tests on each of API 29, 36, and 37 (37.2 SDK image), 13 JVM tests,
lint, 178 Python regression tests, and byte-for-byte reference reproduction.
Each runtime also passed host-verified process death/restoration with a different
PID and generated-fixture cleanup. [Compact evidence](v1-evidence/ui-71ca4ac/summary.json)
records exact source/merge identities, test counts, and XML hashes.

The same source passed all 88 local API 36 tests. Generated-fixture light/dark,
portrait/landscape, and large-text editor captures were inspected. The separate
dark-mode Activity run passed both tests and restored the emulator's night mode.
The API 36 CI profileable workload completed 30 presented-frame samples and
10 exports; it remains emulator evidence, not physical acceptance.

A fresh Android-only sparse checkout built without Python or Qt, passed all
13 JVM tests, and reported zero lint errors and nine warnings. The
[APK inventory](v1-evidence/ui-71ca4ac/android-only-inventory.json) verifies all
packaged assets against committed bytes, three real 33³ LUTs, attribution,
the embedded source commit, and absence of Internet/broad media permissions.
Its debug APK SHA256 is
`edc697958e5e6d736ca40245d61e70f3c76532887af3f6fd4ff14daf1343a705`.
The same candidate passed all 88 instrumented tests on the vivo in 96.248 seconds.
[Physical functional evidence](v1-evidence/ui-71ca4ac/vivo/instrumentation.log)
uses validation APK `ceef5bab6aa567c439f0ba08450d2de7639c715300a5513d88ad77807fbdecea`
and test APK `59acd70038c9966268dd86e38527d3dc5cfb8ce2f94e974d0677b7bca10f1d08`.
The physical GPU compared 512001 channels against independent Python references:
maximum error 1 code, p95 0, mean 0.00000195312. The 48MP fixture decoded to
1600×1200 for preview and 4096×3072 for export. Host-verified process restoration
passed with PID 9793→10047, all recovery invariants, and cleanup.
The [device report](v1-evidence/ui-71ca4ac/vivo/device-build.json) records
16095416320 bytes of RAM and a 268435456-byte Java heap limit. Its
`physicalAcceptance: false` field does not waive the separate performance gate.
The [CI debug APK bundle](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43605020612/artifacts/14117647762)
contains an independently CI-signed debug APK with SHA256
`f0801fb944aa2d8a9d72ca56d30afbc076c2430bc8397cc171c06dca37a19fe1`.
Its provenance is the tested merge/run above; its embedded source field is
`unrecorded`, unlike the locally built APK. Do not interchange those identities.
Review and merges are still pending.

### Physical performance result

The non-debuggable profileable APK from source `71ca4ac` passed the physical
workload. [Recording metadata](v1-evidence/ui-71ca4ac/vivo/performance/recording.json)
records both APK hashes, actual PID/device, host-script hashes and raw-trace hash.
The host's fixture-provider readiness step was added after an initial run failed
before warmup; that incomplete run is retained at `/tmp/v1-vivo-icons-performance`.
The accepted run is `/tmp/v1-vivo-icons-performance-provider`; its unchanged app
APK is `c69987a298af5fe6bbb2a938a7d628e14fe7ec50beb5242abae977e4bc09f0e3`.

| Measurement | Physical result |
| --- | --- |
| Input → linked SurfaceFlinger presentation | 30 samples; p50 121.943ms, p95 133.538ms, max 134.022ms; passes 200ms p95 target |
| 4000×3000 export → MediaStore publication | 10 samples; slowest 499.229ms; passes 10s target |
| Export stage ranges | Decode 38.365–50.906ms; render 282.597–307.137ms; encode 40.159–61.916ms; I/O 32.462–50.703ms |
| Largest sampled export memory values | Java 16170768 bytes; native 184289216 bytes; graphics PSS 120330240 bytes |
| Warm sustained workload | 20 import/edit cycles, 10 exports, 31 post-GC checkpoints; threads 45→45, FDs 214→214 |
| Trace and cleanup | Zero reported trace errors/lost events; completed workload and generated-output cleanup |

The [full report](v1-evidence/ui-71ca4ac/vivo/performance/performance-report.json)
retains every sample. Export stages complete on worker threads; GLES work is
dispatched to the dedicated `film-gl` thread. Post-GC Java/native end deltas are
+32688/+59392 bytes with nonpositive fitted slopes; graphics PSS declines by
26800128 bytes. The [resource review](v1-evidence/ui-71ca4ac/vivo/performance/resource-review.json)
accepts this bounded workload without claiming universal leak freedom. Memory
sampling every 100ms provides lower bounds on peaks, and graphics PSS is not a
complete GPU allocation inventory. The raw trace remains locally retained with
SHA256 `6e122701464bc1ad45678d0e32e242d99944f59899e9b5c4285e629319ff7d27`.

The earlier candidate sections below retain their original identities and
measurements; they are not measurements of this UI candidate.

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

## Earlier physical acceptance checklist

Reference phone: maintainer-provided vivo X200 Pro mini, connected through the
Windows native USB ADB server from WSL. The measured device is V2419A running
Android 17/API 37 with an ARM Mali-G925-Immortalis MC12 GPU and OpenGL ES 3.2.
Current-candidate physical functional and performance evidence is recorded above.
The table below records the earlier candidate's pending gates; those earlier
measurements are not relabeled as current-candidate results.

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

The current candidate now has physical functional/trace evidence and identifiable
debug APKs. Whole-branch review and dependency-order merges remain required before
issues 12–15, the feedback issues, or the parent can be considered complete.
