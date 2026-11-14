# Android gallery export validation (#14)

The export backend re-decodes the private original at a 4096-pixel long-edge cap,
uses the same FilmRenderer as preview, and publishes a fresh sRGB JPEG at quality
95 to `Pictures/FilmEditor`. Small images are never enlarged. This branch depends
on editor PR20 and renderer PR19. Real editor/gallery integration is implemented;
the implementation and independent-review fixes pass the emulator suite.
Physical GPU/performance acceptance remains outstanding.

## Publication and recovery

ExportCoordinator permits one active export across instances and serializes
startup recovery and recovery before every subsequent save with that operation. Duplicate requests fail rather than queue
another image. The editor captures immutable source/parameters; the backend
never reads mutable screen state or exports an enlarged preview bitmap.

A fresh private JPEG is encoded and marked with EXIF ColorSpace=1 (sRGB) and
Orientation=1 (normal). Original metadata is not copied. The actual input/output
bitmaps are opaque sRGB. The encoder uses AndroidX ExifInterface1.4.2, a pinned
[stable release](https://developer.android.com/jetpack/androidx/releases/exifinterface#1.4.2).

The operation's unique UTC timestamp/UUID name is journaled before insertion.
MediaStore starts at IS_PENDING=1. The URI is journaled, the stream is written,
flushed and closed, and read-back SHA-256 must match the staged JPEG before
IS_PENDING becomes0. This hash compares stored encoded bytes, not JPEG pixels
with a lossless reference. Success returns the real readable URI and actual
rendered dimensions. No broad storage/network permission is added.

Recovery queries provider metadata, including pending entries explicitly on both
API29 and newer versions. It checks owner package, exact output directory and
strict generated filename pattern. Journal URIs alone cannot authorize deletion.
The fallback scan covers insertion before URI journaling; published or foreign
photos are preserved. Deletion also constrains ownership/name/path/pending state
in its provider selection. Only canonical owned journal/staging filenames are
removed from private directories; source files are outside those directories.

The active operation handles failure and cancellation with non-cancellable
cleanup. If cancellation occurs before its result is delivered, it can roll back
its own uniquely identified output even after publication; startup never deletes
published media. If provider deletion fails, recovery information is retained
and the failure is reported instead of claiming successful cleanup. The live
exporter retains its failed invocation's rollback obligation: before retry it
must delete that invocation's own output, including a published row, or fail
without inserting another row. This authority is not reconstructed from disk.
A fresh exporter after process death preserves already-published photos and
never replays save success. Export-owned
input/output bitmaps are recycled; the session retains ownership of its renderer.

These choices follow the Android documentation for
[pending publication and shared media](https://developer.android.com/training/data-storage/shared/media),
[matching pending records](https://developer.android.com/reference/android/provider/MediaStore#QUERY_ARG_MATCH_PENDING)
and [provider ownership metadata](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#OWNER_PACKAGE_NAME).

## Executed backend evidence

Final implementation `030a6a3` (tested PR merge `3de79dc`) passed
[CI43554098196](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43554098196):
68 instrumented tests on each API29/API36, nine unit tests and 109 Python
reference/regression tests, with no skipped/failed/error cases. Build succeeded;
lint reported zero errors and 16 warnings (pinned-version/target updates, launcher
resource/icon suggestions and KTX suggestions). These are not physical-device
results. [Compact machine-readable summary](export-evidence/summary.json).

The backend cases executed with the real image repository, GLES renderer,
JPEG encoder and MediaStore, not a simulated gallery:

| Input | Actual JPEG dimensions |
| --- | --- |
| 4000×3000 JPEG | 4000×3000 |
| 6000×4000 JPEG | 4096×2731 |
| 1×1 PNG | 1×1 |
| EXIF-rotated JPEG with source GPS | 60×80 |

Output was readable, decoded as sRGB with normal orientation, and contained no
original GPS latitude/longitude. Private source hashes and the external test
original hash remained unchanged. All8EXIF fixtures and transparent PNG produced
pre-encoding output matching a same-size preview render with maximum RGB code
error0 on both APIs (acceptance≤2); JPEG pixel/byte equality is not required.

Insert, stream-open, ENOSPC write, close, encoder and publication failures were
injected around actual MediaStore rows. Cancellation during write and after
publication removed only that operation's output. Startup recovery exercised
journaled pending rows, the insert-before-URI-journal window, corrupted journal
records, and preservation of published/unrelated media. Owner mismatch uses an
injected metadata case; other-directory preservation uses an actual provider row.
The real shell-created external-media case also passed: provider ownership is
NULL, and both pending/published rows plus their exact JPEG bytes survive recovery
even when an own journal points at their URI.

Ten repeated exports created ten unique readable outputs. Open descriptor counts
stayed at 65 on API29 and 119 on API36 across all ten operations in the
final `030a6a3` run. Export input and
encoded output bitmaps were recycled and private temporary files did not remain.
These observations do not constitute a physical heap/GPU-memory/performance
profile; #15 owns those measurements.

Raw JSON, source hashes and test XML are retained in CI artifacts under
`export-dimensions-metadata-hashes.json`, `export-orientation-alpha-color.json`,
`export-failure-cleanup.json` and `export-repeat-resources.json`. Current devices
are x86_64 emulators with SwiftShader, build type debug; the vivo X200 Pro mini
has not been measured.

## Real editor and gallery integration

On both API29 and API36, ExportActivityTest imported the generated 12 MP source,
changed film/parameters, enabled airplane mode, requested saving twice, rotated
and backgrounded the Activity, and observed exactly one 4000×3000 output with
unchanged captured parameters. The Open action launched the system Gallery.
The test waits until the synthetic image's pixels occupy the expected screen
area, then captures `export-system-gallery.png`. Both final screenshots were
visually inspected and show the exported photo, rather than a transition frame.
`export-activity-gallery.json` records dimensions, output count, airplane mode,
parameters and the resumed gallery Activity. The test restores airplane mode and
deletes only its own generated output.

A recreated real ViewModel cleans an owned pending output while preserving a
previously published image, restores the durable source and does not repeat a
save or display stale success. Service initialization errors block import/edit
until retry succeeds; missing-source/decode errors after services initialize
still permit replacing the source. Tests cover failed recovery, empty preset
catalogs and retry. The coordinator also recovers before each save so a failed
cleanup cannot accumulate abandoned outputs on repeated attempts.

## Commands and remaining acceptance

CI runs with JDK17, the committed Gradle wrapper and pinned SDK packages:

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The independent Python job runs the LUT/mobile regression selection and
byte-compares regenerated mobile references with committed fixtures. No Python
runtime is packaged in the APK. See `.github/workflows/android.yml` for the
exact selection and artifact paths.

The Android SDK is unavailable in the local workspace, so Android verification
above is CI execution. Independent review found two Important issues (combined rollback/retry and an
incorrect external-fixture assumption). Both were corrected and verified by the
final suite. The combined rollback test failed before the fix and now passes
for both provider-committed-then-thrown failure and cancellation after publication;
it also proves that a fresh exporter preserves published output. No Critical or
Minor findings were reported. Renderer PR19's
physical GPU gate and #15's vivo performance/memory/complete-flow acceptance
remain required before v1 is complete.

The expanded regression run at `79f583e` reproduced a published-output rollback
failure followed by a duplicate on retry. It also exposed the API29 emulator's
incidental 16 MiB Java heap limit in two color-reference tests. CI now explicitly
configures 2048 MiB device RAM and a 128 MiB Java heap; the app does not request
`largeHeap`. This environment change does not establish low-memory or physical
performance acceptance. The corrected external-media fixture uses actual
provider metadata (`owner_package_name=NULL` for shell-created media). On API29,
the shell reads/writes only its validated fixture path because the old content
CLI sends a null calling package when opening a file; API36 uses provider IO.
Both versions still exercise actual pending/published MediaStore rows, forged
journal URI handling and exact JPEG-byte preservation. Named foreign-owner
metadata is covered separately by the explicit injected-metadata case.

Compact generated evidence is committed in [API29](export-evidence/api29/) and
[API36](export-evidence/api36/). System-gallery screenshots:
[API29](export-evidence/api29/export-system-gallery.png),
[API36](export-evidence/api36/export-system-gallery.png). Full XML, logcat, APK,
editor recordings and numerical color reports are retained in the linked CI run.

Implementation decisions: tests use real Android instrumentation instead of
Robolectric; JPEG staging costs additional private disk space but allows metadata
normalization and byte verification before publication; provider ownership is
required even when a journal supplies a URI; a process-wide coordinator rejects
duplicates and serializes startup/retry cleanup. The services-ready phase blocks
premature imports after service initialization fails, while source-restoration
errors still allow a replacement import. Live rollback debt is deliberately not
startup deletion authority: after process death, preserving a published photo
takes precedence over inferring whether its previous result was delivered.
