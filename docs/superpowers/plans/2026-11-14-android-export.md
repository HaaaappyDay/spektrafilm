# Android gallery export implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, tests first, and one fresh whole-branch review.

**Goal:** Complete #14's real offline gallery export and integrate it into the #13 editor.
**Architecture:** ExportCoordinator serializes startup recovery and allows one active immutable export. MediaStorePhotoExporter re-decodes/renders, stages a fresh marked sRGB JPEG privately, journals an operation before inserting a pending MediaStore row, closes its output before publication, and rolls back failures/cancellation. PendingExportStore retains operation IDs/names/URIs for recovery; ownership-checked fallback scanning covers insertion before URI journaling.
**Tech Stack:** Existing Kotlin/coroutines/Compose/Android API29+ stack; MediaStore; pinned AndroidX ExifInterface1.4.2 (official stable release). No storage permissions or new network access.
**Spec:** [#14](https://github.com/HaaaappyDay/spektrafilm/issues/14), [v1 contract](../../android/v1-contract.md), [editor handoff](../../android/editor-validation.md). Dependent on editorPR20 and rendererPR19, whose physical GPU gate remains pending.

## Global constraints

- Preserve exact core PhotoExporter, ExportRequest, ExportResult, ImageRepository and FilmRenderer contracts; no second color math or parameter model.
- Decode at4096 cap, no upscaling; render identical dimensions, opaque ARGB8888 sRGB; JPEG quality95, fresh EXIF color-space=sRGB/orientation=normal, no source GPS/ICC/EXIF copying.
- Unique FilmEditor_<UTC timestamp>_<full UUID>.jpg, MIMEimage/jpeg, Pictures/FilmEditor/, IS_PENDING1 until successful encoding/writing/close then publish0.
- Startup may delete only verified own unpublished images matching exact app naming/path; published or foreign media and source files remain untouched. Corrupt journal data is untrusted.
- Cancellation propagates after releasing bitmaps/streams/staging files and deleting this operation's output. No false successful URI. Preserve journal when provider cleanup fails so later recovery retries.
- UI disables edits/import/duplicate saving; rotation does not restart export. Startup recovery runs before editor initialization completes; process restoration never repeats an export.
- CI is actual Android verification; local Android SDK unavailable. Instrumented tests use real Bitmap/MediaStore and API29/36 emulators. No passing claims for unexecuted vivo/TalkBack/performance checks.

## Review focus

- Insert returns a row but cancellation or journal failure occurs before URI persistence; recovery must find it without deleting another owner's image.
- Cancellation immediately after publication/dispatcher return must not report success or strand an output of the cancelled operation; startup must still preserve published outputs.
- Write/close/publish/cleanup failures retain enough recovery state and never delete sources or claim success.
- Startup recovery racing with active export/rotation must not delete live output or save twice.
- JPEG metadata, actual sRGB pixels, all8EXIF orientations and transparency must agree with normalization, independently of lossy JPEG equality.

### Task 1: Recovery journal and serialized coordinator

**Files:** export/PendingExportStore.kt, export/ExportCoordinator.kt; androidTest/export/{PendingExportStoreTest,ExportCoordinatorTest}.kt.
**Interfaces:** PendingExport(id:String,displayName:String,uri:Uri?); PendingExportStore(context,directory=private). write(record), read():List<PendingExport>, clear():Unit, remove(id). These synchronous operations run on IO and use checked atomic replacement. ExportCoordinator(delegate:PhotoExporter,recover:suspend()->Unit):PhotoExporter; suspend recover(). Process-wide mutex serializes recovery and export; export uses tryLock and fails concurrent request withEXPORT_FAILED rather than queuing a duplicate.

- [ ] Write journal tests: roundtrip/URI update, interrupted staging leaves last snapshot, corrupt/oversize/forged URI ignored safely, clear/remove touch only owned journal files. Write coordinator gates: immutable capture, oneactive across instances, recovery waits for current export, cancellation/failure unlock and allowretry.
- [ ] Run Android CI; Expected: new types missing (RED). Implement checked writes/validation and coordinator; rerun Expected: testsPASS.
- [ ] Commit independently tested implementation and record actual results.

### Task 2: Real MediaStore JPEG export and recovery

**Files:** export/MediaStorePhotoExporter.kt, export/MediaStoreGateway.kt; androidTest/export/MediaStorePhotoExporterTest.kt; gradle/libs.versions.toml and app/build.gradle.kts.
**Interfaces:** MediaStorePhotoExporter(context,images,renderer,journal=PendingExportStore, gateway=AndroidMediaStoreGateway, encode=(Bitmap,File)->Unit):PhotoExporter with suspend recover(). MediaRow(uri,name,path,owner,pending); gateway insert(name):Uri, open(uri):OutputStream, publish(uri,name), rows():List<MediaRow>, delete(row,onlyPending):Unit. Android implementation queries pending explicitly (API29 include-pending URI;API30+match-pending Bundle), enforces owner/path/name/pending on deletion. Production encoder is fresh JPEG95 + ExifInterface marking. Inject gateway/encoder only for deterministic failure tests.

- [ ] Write real MediaStore tests beforeimplementation: output URIreadable/published with correct dimensions/sRGB/normal/noGPS andoriginalSHAunchanged;4000x3000same,6000x4000→4096x2731,tinynoUpscale.
- [ ] Cover real8orientations/alpha inputs with same-renderer preencodingcomparison≤2codes and decodedJPEG metadata/readability; freshJPEGmustnotcopyGPS.
- [ ] Injectinsert/open/writeENOSPC/close/encode/publishfailures andcancellation(includingafterpublication): no false success, no ownedpending/tempfiles, preservedsource, retryonevalidoutput. Assertexportbitmapsrecycledandrendererinputunchanged.
- [ ] RecoveractualpendingrowswithjournalandwithoutURIjournal; corruptjournal/foreignname/path/publishedrows preserved; deletionfailureleavesjournalandretrysucceeds. Includeownershippredicateunitcaseandrealexternallyownedgalleryitemwhereplatformtesttoolsallowcreation.
- [ ] ObserveREDinCI, then implement decode4096→render→stagedJPEG→intentjournal→insertpending→URIjournal→write/sync/close→readability→publish→removejournal→return; NonCancellable cleanup onerror/cancel. Startupfallbackscanthenjournalclearandownedtempcleanup under coordinatorgate.
- [ ] Run full CI ExpectedPASS; retain XML/numeric/hash evidence andresourcecounts. Commit.

### Task 3: Editor integration and end-to-end evidence

**Files:** MainActivity.kt, ui/editor/EditorViewModel.kt, ui/editor/EditorScreen.kt, values*/strings.xml; androidTest/export/ExportActivityTest.kt; README and docs/android/export-validation.md.
**Interfaces:** EditorViewModel optional prepareExport:suspend()->Unit={} called duringinitialization; MainActivity constructs one sharedrenderer, realexporter, coordinator, injectsPhotoExporterandcoordinator::recover. Existing core interfaces unchanged. Initializationfailureprovidesretry; success/openactionalreadyownedbyeditor.

- [ ] TestrealActivityimport/edit/export, doubleclick/rotation/backgroundnoextraoutput, actualgalleryopen/screenshots andairplanemode. Tenrepeatedexports: validuniqueURIeach, stableownedtemporaryfiles/FDcount/bitmaps (allow runtimewarmup, not zeroheap assertions).
- [ ] ObserveREDbeforewiring; implementminimalfactory/init/errorcopychanges, neverpersist/replaysuccess.
- [ ] FullAPI29/36build/unit/instrument/lint/PythonreferencechecksPASS. Recordcommands/device/buildtype/results/limitations, exactdimensions/hash/metadata/faultoutcomes andgalleryevidence.
- [ ] Onefreshwholebranchreview;fixImportant/Criticalwithregressionsandgreensuite; minorcoverageledgered. DependentPRCloses14,parent9,baseeditorbranch; retarget/mergeonlyafterprerequisitesaccepted. Physicalacceptanceandperformancecontinue#15.
