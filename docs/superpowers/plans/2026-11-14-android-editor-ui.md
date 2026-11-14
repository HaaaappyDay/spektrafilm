# Android photo-first editor UI implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. Existing user authorization covers implementation and GitHub delivery; preserve physical acceptance gates. One fresh final code review covers the final validation/UI range.

**Goal:** Deliver the maintainer's Snapseed-inspired simple, easy-to-learn editor on the vivo X200 Pro mini.
**Architecture:** Keep EditorViewModel and existing real image/GL/export services; add bounded optional CPU film thumbnails, a responsive photo workspace, and explicit Films/Adjust/Export navigation. Update automated actions to operate the visible UI.
**Tech stack:** Existing Kotlin, Compose Material3, coroutines, Android instrumentation and Perfetto; no new runtime dependency.
**Spec:** ../specs/2026-11-14-android-editor-ui-design.md

## Global constraints

- Preserve all v1 color/image/export semantics, three real films, no Auto or new editing tools.
- Thumbnail long edge ≤160px, at most three current-source images, off Main, reject obsolete results; no personal photos committed as evidence.
- Minimum target48dp; localized Chinese/English, system light/dark, fit whole main image; normal phone portrait preview target≥55%.
- Keep strength0…1/.01, exposure-2…2/.1, temperature-100…100/1. Tool navigation alone cannot edit/export.
- Physical 30-preview p95≤200ms,12MPexport≤10s,20imports+10exports resource review remain gates; emulator or user trial is not physical acceptance.

## Review focus

- A late thumbnail from an old photo must never replace current-source cards or change editing/export validity.
- Empty/failing/restoring sessions must expose import/retry without disabled-control clutter or a navigation trap.
- Rotation, back and tab changes must preserve current edits and selected UI state, without causing saves.
- Large text and landscape must retain reachable tool actions and a useful full-image preview.
- Performance tests must select visible controls, preserve content/frame proof, and measure the final UI with thumbnail memory included.

### Task 1: Bounded real film thumbnail previews

Files: render/PresetThumbnailRenderer.kt; ui/editor/EditorUiState.kt; ui/editor/EditorViewModel.kt; MainActivity.kt; corresponding instrumented renderer/ViewModel tests.
Interfaces: add an optional `(suspend (Bitmap, List<FilmPreset>) -> Map<String, Bitmap>)?` thumbnail generator to EditorViewModel, supplied by MainActivity; expose `presetThumbnails: Map<String, Bitmap>` in EditorUiState. PresetThumbnailRenderer consumes the existing AssetPresetRepository and calls ColorMath.transform with EditParams(preset.id), returns complete owned output map.
- [ ] Write pixel/dimension/source-preservation tests for 3 default film thumbnails, max160px/no upscale, offMain/cancellation cleanup. Run and observe missing implementation RED.
- [ ] Implement the renderer on Dispatchers.Default, cancellation checks per row, shared checked cubes; recycle only unpublished temporaries.
- [ ] Add gated VM tests: current source gets thumbnails, manual edits do not regenerate them, obsolete/noncooperative completion cannot publish after replacement, thumbnail failure leaves editing/export usable. Observe RED, then implement generation lifecycle and identity guards.
- [ ] Run thumbnail and VM instrumented tests; existing unit/lint. Expected: all pass, stale source not shown, no pipeline parameter changes. Commit task.

### Task 2: Photo-first navigation and native visual refinement

Files: ui/editor/EditorScreen.kt, BeforeAfterPreview.kt, AdjustmentControls.kt, PresetSelector.kt, ui/theme/Theme.kt, localized strings; EditorScreenTest.kt and lifecycle tests.
Interfaces: keep EditorScreen public callback signature; consume new thumbnail map; save destination/selected adjustment in rememberSaveable. Expose stable tags `tab-films`, `tab-adjust`, `tab-export`, `adjust-strength`, `adjust-exposure`, `adjust-temperature`, `preset-<id>`; retain `import`, `original`, `reset`, `export`, `retry`, slider tags and localized accessibility labels. Add no new image-processing callback.
- [ ] Add interaction tests for empty import, explicit navigation/no edit or save side effects, one visible adjustment/SetProgress, film-card selection, save lockout, 2x font retry/dimensions, destination restoration/back and portrait preview bounds. Observe failures on existing form.
- [ ] Implement neutral light/dark theme, compact top bar with comparison, large fitted photo, responsive bottom/side tool pane and three labeled destinations. Empty state has clear import; every error remains reachable.
- [ ] Replace dropdown with thumbnail cards; replace stacked sliders with one selected parameter and tick-free slider retaining quantization/accessibility; create explicit export panel with dimensions and success/open.
- [ ] Update existing screen/activity tests to exercise visible destinations, preserving original state/export invariants. Run UI/lifecycle tests and unit/lint. Expected: behavior tests pass and generated portrait/landscape/large-text screenshots show usable layout. Commit task.

### Task 3: Real-flow, performance and final delivery reconciliation

Files: e2e/PhotoEditingFlowTest.kt, e2e/PerformanceScenario.kt, other editor/export activity tests needing navigation; README and validation evidence.
Interfaces: all real actions use Task2 tags or localized descriptions. Accessibility performance driver selects Adjust/Strength before preview actions and Export before exports; input markers start before actual measured action and existing FrameTimeline/export evidence remains mandatory.
- [ ] Update e2e and performance navigation for actual visible UI; preserve all three films/manual/original/reset/offline gallery checks and30+20+10 workload. Run on vivo and emulator; diagnose any failure before changing assertions.
- [ ] Run assemble,unit,lint,requiredPython and API29/36/37CI; capture generated UI screenshots. Run host process restore and physical color/flow then exact-source profileable performance/resource review. Expected: all functional checks pass and physical metrics meet unchanged gates; otherwise investigate and fix.
- [ ] Reconcile source/APK/trace hashes and standalone build/license inventory for final candidate. Create dependent UI PR and update validation report/PR22; one fresh review of validation+UI since dfdf65a, one tested Important/Critical fix pass, deferred minors ledgered. Merge PR19→20→21→22→UI only after gates and exact tested heads are verified.
