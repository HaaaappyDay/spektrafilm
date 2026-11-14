# Android photo import implementation plan

> **For agentic workers:** Use superpowers:executing-plans, test first, then independent branch review.

**Goal:** Complete #11: durable JPEG/PNG import and correctly normalized base preview.
**Architecture:** A real ImageRepository copies sources into no-backup private storage. ImageDecoder performs native orientation and tagged-color decoding before white compositing. A small injectable import screen/controller exposes loading, preview and recoverable errors; #13 later replaces the temporary entry-point wiring with the full editor.
**Tech Stack:** Existing stable Kotlin/Compose/Activity stack, ImageDecoder API 29+, coroutines, JUnit/Compose instrumented tests.
**Spec:** [v1 contract](../../android/v1-contract.md), [issue #11](https://github.com/HaaaappyDay/spektrafilm/issues/11). Prerequisite #10 merged in #17.

## Global constraints

- Accept only actual static JPEG/PNG, no broad library/storage permissions.
- Private immutable source copy; oriented original dimensions; no original modifications.
- Decode to new exclusively owned software ARGB_8888 opaque sRGB, aspect preserved, no upscale.
- Preview ≤1600; export decode ≤4096; all copying/decoding off main.
- Typed unsupported/size/read errors, unwrapped cancellation, no obsolete preview publication.
- Photos live under noBackupFilesDir; no personal photos/URIs in tests or reports.

## Review focus

- Path traversal/symlink/restored malicious descriptors cannot read/delete external files.
- Cancellation at dispatcher return must clean a result never delivered to its caller.
- Startup partial cleanup must not delete another active import's temporary file.
- Misleading extension/provider MIME, truncated/animated/oversized content must fail safely.
- Platform color conversion and EXIF handling must be measured, not assumed from labels.

### Task 1: Normalization and durable source repository

**Files:** `image/{ImageSizing,ImageNormalizer,AndroidImageRepository}.kt`, unit `image/ImageSizingTest.kt`, instrumented `image/{ImageNormalizerTest,AndroidImageRepositoryTest}.kt`, `src/androidTest/assets/import-fixtures/`, `scripts/generate_android_import_fixtures.py`, `res/xml/data_extraction_rules.xml`.
**Interfaces:** Implement the exact ImageRepository contract. `ImageSizing.fit(width:Int,height:Int,maxLongEdge:Int): Pair<Int,Int>`. `ImageNormalizer.inspect(file:File): ImageMetadata` and `decode(file:File,maxLongEdge:Int):Bitmap` are internal; metadata contains oriented dimensions and actual MIME.

- [x] Write sizing tests: 4000×3000→1600×1200, 6000×4000→4096×2731, small images unchanged, positive extreme-aspect result, invalid/overflow dimensions rejected.
- [x] Generate original synthetic fixtures with provenance: all eight EXIF orientations, transparency, sRGB/P3/untagged patches, corrupt/unsupported/tiny/extreme/12MP/48MP images. No Pillow required in ordinary Android builds.
- [x] Write instrumented tests for normalized pixels/dimensions/config/alpha/color space, all eight orientations at multiple caps and original hashes.
- [x] Write repository tests for durable copy after original removal, format spoofing, stream errors/cancellation, stale partial cleanup, active import protection and path safety.
- [x] Observe RED on missing implementation in CI, then implement off-main streaming/atomic finalize, bounded header/decode, explicit cleanup and normalized output.
- [x] Run unit/lint/connected tests; inspect actual image assertions on API29 and newer API. Explicitly reject HDR transfer functions; validate SDR gain-map base behavior before accepting it.

### Task 2: Picker and base preview

**Files:** `image/PhotoPickerLauncher.kt`, `ui/importphoto/{ImportPhotoScreen,ImportPhotoViewModel}.kt`, minimal `MainActivity.kt` wiring, strings, instrumented UI tests.
**Interfaces:** `ImportPhotoScreen(bitmap:Bitmap?,loading:Boolean,error:EditorErrorCode?,onImport:()->Unit,onRetry:()->Unit)`; ViewModel owns the source/preview and publishes immutable state. ImageRepository remains constructor-injected.

- [x] Write tests showing loading/error/retry, enabled picker, preserved old preview on cancellation/failure, latest import wins, recreation and landscape/large-font scroll access.
- [x] Use PickVisualMedia(ImageOnly), whose documented system document fallback requires no broad permission.
- [x] Implement previews and source ownership: publish only after full import+preview success, remove unaccepted sources, retain previous good source on failure. Do not recycle displayed bitmaps; release references when no longer owned.
- [x] Run real UI tests; capture empty/preview portrait and landscape screenshots using generated inputs. Actual user picker cancellation must preserve state.

### Task 3: Verification, review and PR

**Files:** `docs/android/import-validation.md`, `android/README.md`, CI adjustments only as needed.

- [x] Run assembleDebug/testDebugUnitTest/lintDebug/connectedDebugAndroidTest; retain XML reports and screenshots.
- [x] Record device/API/build, fixture provenance, normalization measurements and unverified hardware cases.
- [x] Independent review, resolve important defects and verify implementation head.
- [x] PR #18 merged as `216471d`; #11 closed and #9 updated.

## Builder decisions

- Source byte cap 100 MiB, header area cap 100 MP and dimension cap 32768; allow 48MP sampled decode. Output cap 4096 bounds decoded memory; invalid caller caps return INVALID_PARAMS.
- Repository instance operations share a process lock for owned-partial bookkeeping. Startup only deletes UUID-named abandoned .part files; completed sources belong to session lifetime.
- Exact output sizes use rounded aspect ratio, never zero in extreme ratios.
- Explicit Android12+ extraction exclusions accompany no-backup storage. No permissions added.
- Full edit/session process restoration belongs #13; this milestone uses ViewModel for configuration changes.
