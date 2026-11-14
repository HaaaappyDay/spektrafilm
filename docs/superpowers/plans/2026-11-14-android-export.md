# Android gallery export implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, tests first, and one fresh whole-branch review.

**Goal:** Complete #14's real offline gallery export and integrate it into the #13 editor.
**Architecture:** ExportCoordinator serializes recovery and allows one active immutable export. MediaStorePhotoExporter re-decodes/renders, stages a fresh sRGB JPEG privately, journals an operation before inserting its pending MediaStore row, and closes/verifies output before publication. Ownership-checked fallback scanning covers insertion before URI journaling. The editor distinguishes service initialization from recoverable source-restoration errors.
**Tech stack:** Existing Kotlin/coroutines/Compose/Android API 29+ stack; MediaStore; pinned stable AndroidX ExifInterface 1.4.2. No storage permissions or new network access.
**Spec:** [#14](https://github.com/HaaaappyDay/spektrafilm/issues/14), [v1 contract](../../android/v1-contract.md), [editor handoff](../../android/editor-validation.md). Base: editor PR20 at `972f373`; renderer PR19's physical GPU gate remains pending.

## Global constraints

- Preserve the core PhotoExporter, ExportRequest, ExportResult, ImageRepository and FilmRenderer contracts. Do not duplicate color math or parameter models.
- Decode at a 4096-pixel long-edge cap without upscaling. Render identical dimensions, opaque ARGB8888 sRGB. Encode JPEG quality 95 with fresh EXIF sRGB/normal-orientation markers; never copy source GPS/ICC/EXIF.
- Use unique `FilmEditor_<UTC timestamp>_<full UUID>.jpg` names, MIME `image/jpeg`, path `Pictures/FilmEditor/`, and IS_PENDING=1 until writing/closing/read-back verification succeeds.
- Startup may delete only verified own unpublished images with the exact naming/path convention. Preserve published or foreign media and source files. Treat corrupt journal data as untrusted.
- Propagate cancellation after releasing owned bitmaps/streams/staging files and rolling back this operation's output. Report cleanup failure and retain recovery information when provider deletion fails.
- Lock edits/import/duplicate saving during export. Rotation must not restart it. Startup and every subsequent save/retry must recover previous interrupted output under the same process-wide gate. Never persist/replay export success.
- The Android SDK is unavailable locally; actual build/device verification runs in CI. Real Bitmap/MediaStore tests use API 29/36 emulators. Do not claim physical performance or manual accessibility validation from emulator results.

## Review focus

- An insert creates a row before cancellation or URI-journal failure. Recovery must find it without deleting another owner's image.
- Cancellation after publication or during dispatcher return must roll back only this invocation's output. Startup must preserve published images.
- Write/close/publish/cleanup failures must not claim success or delete source files; retry must clean prior leftovers before creating a new output.
- Startup recovery racing with an active export or rotation must not delete live output or save twice. Failed service initialization must not let import erase the retry state.
- Fresh JPEG metadata and actual sRGB pixels must agree with normalization for all eight EXIF orientations and transparency; JPEG itself is lossy.

### Task 1: Recovery journal and serialized coordinator

**Files:** `export/PendingExportStore.kt`, `export/ExportCoordinator.kt`; instrumented `export/PendingExportStoreTest.kt` and `export/ExportCoordinatorTest.kt`.
**Interfaces:** PendingExport(id,displayName,uri?), with `create()`. PendingExportStore(context,directory,commit) provides synchronous IO-only `write(record)`, `read():List<PendingExport>`, `clear()` and `remove(id)` using checked atomic replacement. ExportCoordinator(delegate,recoverAction):PhotoExporter provides `export(request)` and `recover()` under a process-wide mutex. Duplicate exports fail rather than queue. Recovery runs before every export as well as at startup.

- [x] Write journal tests for roundtrip/URI updates, interrupted staging, failed commits, corrupt/oversized/forged records and preservation of unrelated files/symlinks. Write coordinator tests for one active operation across instances, immutable capture, recovery exclusion, cancellation and failure/retry.
- [x] Observe missing-type RED in CI43548484171. Implement journal/coordinator; CI43548806016 passes 52 device tests per API plus build/unit/lint/Python checks.
- [x] Add the discovered retry-cleanup regression: it fails at `5a42c1d` and passes after recovery is added before every export. Record results in the ledger.

### Task 2: Real MediaStore JPEG export and recovery

**Files:** `export/MediaStorePhotoExporter.kt`, `export/MediaStoreGateway.kt`; instrumented `export/MediaStorePhotoExporterTest.kt`; dependency catalog and app Gradle file.
**Interfaces:** MediaStorePhotoExporter(context,images,renderer,journal,gateway,stagingDirectory,encode):PhotoExporter, with `recover()`. MediaRow(uri,name,path,owner,pending). MediaStoreGateway provides synchronous `insert(name):Uri`, `open(uri):OutputStream`, `publish(uri,name)`, `rows():List<MediaRow>`, and `delete(row,onlyPending)`. Android implementation explicitly includes pending rows on API 29 and uses match-pending query arguments on newer APIs. `encodeSrgbJpeg(bitmap,file)` writes a fresh marked JPEG.

- [x] Write real-provider tests: 4000×3000 unchanged, 6000×4000→4096×2731, tiny input not enlarged, readable published URI, correct sRGB/orientation/no-GPS metadata and unchanged source hashes.
- [x] Compare all eight orientations and alpha fixtures against same-size preview before encoding; maximum RGB error must be ≤2 codes. Verify decoded JPEG dimensions/readability separately.
- [x] Inject insert/open/ENOSPC-write/close/encode/publish failures, cancellation during writing and after publication, URI-journal failure, and failed rollback followed by recovery. Assert no false success, correct row/file cleanup, preserved source and recycled export bitmaps.
- [x] Exercise journaled pending rows, insert-before-URI-journal window, corrupted journal, owner mismatch, actual other-directory media and published/unrelated media preservation. Repeat ten exports and record unique URIs, descriptor counts and bitmap/temporary-file cleanup.
- [x] Observe missing implementation RED in CI43549157776. Implement staged encoding → intent journal → pending insert → URI journal → write/sync/close → hash read-back → publish → cleanup → real result. CI43550072332 passes 60 device tests per API and all other jobs.
- [ ] Verify the additional shell-owned pending/published media case with a forged journal URI. Retain actual ownership metadata and clean only test-created rows afterward.

### Task 3: Editor integration, evidence and review

**Files:** MainActivity, EditorViewModel, EditorUiState, EditorScreen, localized strings; instrumented ExportActivityTest and ViewModel regressions; README and `docs/android/export-validation.md`.
**Interfaces:** EditorViewModel adds optional `prepareExport:suspend()->Unit={}` and tracks `servicesReady`. MainActivity shares one renderer between preview/export and injects the real coordinator plus `coordinator::recover`. Service initialization must complete before editing/import; a later missing-source restoration error still permits importing a replacement.

- [x] Write real Activity tests for airplane-mode saving, duplicate requests, rotation/backgrounding, immutable parameters, system-gallery opening and startup cleanup without replaying a published export. Write startup recovery failure/retry tests before wiring.
- [x] Observe missing `prepareExport` RED in CI43550545933. Wire real services, initialization and localized errors. Both Activity tests pass at `5a42c1d`; inspect their artifacts rather than equating Activity resume with a displayed image.
- [x] Reproduce failed-initialization import and empty-catalog defects in CI43551508419. Implement service-readiness gating while preserving missing-source replacement. Strengthen gallery capture to wait for actual photo pixels instead of a transition frame.
- [ ] Full API 29/36 build/unit/instrument/lint/Python checks pass on the final implementation. Inspect gallery screenshots and actual foreign-owner evidence. Record commands, device/build details, dimensions, metadata/hash/color/resource/failure results and remaining physical limitations.
- [ ] One fresh whole-branch review; fix Important/Critical findings with regressions and a green suite. Record deferred minor findings. Create a dependent PR closing #14, linked to #9, based on the editor branch; merge only after prerequisites are accepted. Continue physical acceptance and performance in #15.
