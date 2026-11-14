# Android gallery export validation (#14)

The export backend re-decodes the private original at a 4096-pixel long-edge cap,
uses the same FilmRenderer as preview, and publishes a fresh sRGB JPEG at quality
95 to `Pictures/FilmEditor`. Small images are never enlarged. This branch depends
on editor PR20 and renderer PR19; real editor wiring and final branch validation
are still in progress. Physical GPU/performance acceptance remains outstanding.

## Publication and recovery

ExportCoordinator permits one active export across instances and serializes
startup recovery with that operation. Duplicate requests fail rather than queue
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
and the failure is reported instead of claiming successful cleanup. Export-owned
input/output bitmaps are recycled; the session retains ownership of its renderer.

These choices follow the Android documentation for
[pending publication and shared media](https://developer.android.com/training/data-storage/shared/media),
[matching pending records](https://developer.android.com/reference/android/provider/MediaStore#QUERY_ARG_MATCH_PENDING)
and [provider ownership metadata](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#OWNER_PACKAGE_NAME).

## Executed backend evidence

The first backend implementation `1d93c88` ran 60 device tests on each API29 and
API36 in [CI43549661226](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43549661226).
Each had59 passes and one failure: the URI-journal fault injector searched raw
JSON text, so Android's slash escaping prevented injection. It was corrected to
inspect the parsed field and assert that injection occurred; final results will
be recorded after that run completes. Build/unit/lint and Python jobs passed.

The other backend cases executed with the real image repository, GLES renderer,
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
Actual externally-owned media and editor/gallery integration remain to verify.

Ten repeated exports created ten unique readable outputs. Open descriptor counts
stayed60 on API29 and113 on API36 across all ten operations. Export input and
encoded output bitmaps were recycled and private temporary files did not remain.
These observations do not constitute a physical heap/GPU-memory/performance
profile; #15 owns those measurements.

Raw JSON, source hashes and test XML are retained in CI artifacts under
`export-dimensions-metadata-hashes.json`, `export-orientation-alpha-color.json`,
`export-failure-cleanup.json` and `export-repeat-resources.json`. Current devices
are x86_64 emulators with SwiftShader, build type debug; the vivo X200 Pro mini
has not been measured.
