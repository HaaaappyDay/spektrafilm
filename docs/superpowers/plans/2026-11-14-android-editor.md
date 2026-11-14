# Android editor implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline with tests first and one whole-branch independent review.

**Goal:** Complete #13: a usable real offline editor with presets, three controls, original comparison, bounded preview scheduling and durable restoration.
**Architecture:** EditorViewModel owns immutable StateFlow state and the cached normalized input. A bounded latest-wins scheduler uses at most two active coroutine requests plus one conflated pending request, permitting a cancelled noncooperative old result to finish after the latest result without publication. A single conflated writer atomically persists lightweight source/params; retired source files are removed only after the current source is durably saved. Stable Compose layouts keep the canvas separate from scrollable controls.
**Tech stack:** Existing stable Compose/Lifecycle/Coroutines stack, Android AtomicFile/JSON, real ImageRepository/PresetRepository/FilmRenderer from #11/#12.
**Spec:** [v1 contract](../../android/v1-contract.md), [#13](https://github.com/HaaaappyDay/spektrafilm/issues/13). Base is renderer branch e41d50a (draft PR19 pending physical GPU); this work remains dependent until #12 can merge.

## Global constraints

- Exact shared core APIs remain unchanged. No Auto/network/camera/RAW/spatial effects.
- New successful import selects Portra400 at1/0/0; failed/cancelled import preserves old source and edits. Preset changes retain values; reset retains preset.
- Strength0..1, EV−2..2 quantized0.1, temperature−100..100 integer. Original comparison is normalized untouched input, distinct from strength0.
- Preview input decoded once per photo at1600 cap; render version covers source and immutable params. Never recycle displayed bitmaps or renderer inputs still in use.
- Source/params only in private atomic session JSON. Resume from last committed snapshot; never persist Bitmap/GL objects or export success. Missing/corrupt source recovers with an explanation.
- PhotoExporter is optional constructor injection until #14 wires the real coordinator. UI disables export when unavailable, no photo, stale/failed preview, importing or saving. Captured export request is immutable; saving blocks edits/import/duplicates.
- Single screen; use stable BoxWithConstraints/Row/Column, insets and saved scroll state. Apply adaptive skill's relevant sizing/state guidance without adding Navigation3 or experimental APIs (explicit issue constraint).

## Review focus

- Older noncooperative renders or cancelled imports must never overwrite a newer photo/parameters or recycle visible images.
- Hundreds of slider updates must keep bounded active/pending work and leave final state/display consistent.
- Interrupted/corrupt session writes and stale queued writes must not delete the source referenced by durable state.
- Activity recreation/clearing and export races must not duplicate saving or leak GL resources.
- Landscape, short windows and large text must expose controls without obscuring the image; accessibility actions must operate the real ranges.

### Task 1: State, scheduling and session storage

**Files:** ui/editor/{EditorUiState,EditorViewModel,EditorSessionStore}.kt; androidTest/ui/editor/{EditorViewModelTest,EditorSessionStoreTest}.kt.
**Interfaces:** StoredEdit(source:ImageSource,params:EditParams); SessionPersistence.load():StoredEdit?, save(edit:StoredEdit?). EditorSessionStore(context). EditorViewModel(images,presets,renderer,sessions,exporter:PhotoExporter?=null) exposes state:StateFlow<EditorUiState>; importPhoto(Uri?), setPreset(String), setStrength(Float), setExposure(Float), setTemperature(Float), reset(), toggleOriginal(), retry(), export(). State contains source/input/preview/params/presets/initializing/importing/rendering/exporting/showOriginal/error/errorStage/exportResult/previewValid.

- [ ] Write instrumented tests first (real Bitmap/Android dispatcher, no Robolectric dependency): B finishes before cancelled noncooperative A, late previous-photo output discarded,100changes bounded≤2active+1pending, only one decode per source, final output matches final params.
- [ ] Verify defaults/preset/reset/quantization/original distinction; cancelled/corrupt imports retain photo/edits. Export fake records one immutable request while changes/import/repeated save are blocked; failure retries once.
- [ ] Session tests: exact JSON roundtrip, invalid/nonfinite/corrupt state, atomic rollback, source safety; startup removes only managed orphan .source files before the first editor loads in this process and preserves the durable source/other files. Recreated VM restores source/params via re-decode/re-render; missing source shows recovery and no false saved result.
- [ ] Observe missing implementation RED in CI. Implement state machine, bounded scheduler and one conflated persistence writer. Persisted-source deletion only follows confirmed successful write of current source. Unaccepted candidates are never persisted and cleaned immediately.
- [ ] On clearing, cancel jobs and release the owned renderer after they finish, outside main; never manually recycle displayed input/preview. Run state/storage tests; expected pass.

### Task 2: Adaptive editor UI and real integration

**Files:** ui/editor/{EditorScreen,PresetSelector,AdjustmentControls,BeforeAfterPreview}.kt, MainActivity.kt, values*/strings.xml; androidTest/ui/editor/EditorScreenTest.kt; adapt existing Activity/foundation integration tests.
**Interfaces:** EditorScreen(state,onImport,onPreset,onStrength,onExposure,onTemperature,onReset,onToggleOriginal,onRetry,onExport,onOpenExport). Controls expose stable semantics/test tags and localized labels/values.

- [ ] Compose tests first: no-photo disables editing/export; sliders support accessible set-progress with exact ranges/steps; preset changes,reset,original toggle; saving lockout; error/retry; stale preview disables export.
- [ ] Portrait: canvas above a bounded scrolling controls panel; landscape/wide: canvas alongside scrolling controls. Image uses Fit, always remains visible; controls use48dp targets and system insets. Large fonts and short windows scroll controls without placing them over the image.
- [ ] Preset selector uses readable names; label slider values (%/EV/cool–warm relative units). Original accessible toggle; reset retains preset. Rendering/error status accurately distinguishes stale preview/original.
- [ ] Wire real repositories/renderer/store in MainActivity ViewModel factory; leave PhotoExporter unavailable until #14. Update former import-only Activity test to real editor and keep low-level import tests. Expose actual4096-cap output dimensions before save.
- [ ] Capture real generated-photo editor portrait/landscape/error screenshots and an interaction recording on emulator. Verify rotate/background/foreground preserves state. Run full Android suite; expected pass.

### Task 3: Handoff and review

**Files:** docs/android/editor-validation.md, android/README.md.

- [ ] Record exact CI/device results, state-race cases, screenshots/recording, persistence/bitmap ownership and PhotoExporter injection contract for #14. Physical checks remain #15.
- [ ] Independent whole-branch review; fix important issues with tests. Final checks green. Create dependent PR Closes #13, link #9/#19; merge only after renderer prerequisite is satisfied.
