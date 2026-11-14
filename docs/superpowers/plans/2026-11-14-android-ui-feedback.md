# Android editor feedback implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. Each issue is a separate dependent PR. One fresh final review covers the combined validation/UI/feedback candidate before merge; preserve the unfinished v1 acceptance work.

**Goal:** Implement issues #24, #25, and #23 from physical-device feedback.
**Architecture:** Keep the existing native editor and image services. Stabilize the preview footer, add a transient comparison gesture, and replace optional generated thumbnails with static cartridge illustrations.
**Tech Stack:** Pinned Kotlin/Compose/Android dependencies; no new runtime dependency.
**Spec:** ../specs/2026-11-14-android-ui-feedback-design.md

## Global constraints

- Public-facing repository prose is English. Preserve existing localization support.
- Preserve complete-image fitting, edit values, immutable export snapshots, and source protection.
- Keep explicit accessible comparison and 48dp minimum control targets.
- Keep physical 30-preview p95<=200ms, 12MP export<=10s, and 20-import/10-export resource gates.
- Do not add film stocks, Auto, or unrelated editing functionality.

## Review focus

- Busy-state transitions and wrapped text must not shrink the image.
- Cancelled/multitouch/interrupted gestures must never leave a temporary original stuck on screen.
- Existing comparison selection and edit parameters must survive hold/release and rotation correctly.
- Temporary original draws must not claim an edited-content trace ID.
- Cartridge fallback/selection must work in dark mode and large text without obsolete thumbnail work.

### Task 1: Stable preview dimensions (#24)

Files: ui/editor/BeforeAfterPreview.kt; ui/editor/EditorScreenTest.kt.
Interfaces: keep BeforeAfterPreview(state, modifier); actual image remains exposed by localized photo_preview description.
- [x] Add a state-transition test comparing exact photo bounds while rendering/importing/exporting/initializing/original states change at font scales1 and2. Run EditorScreenTest; expect RED on the current conditional progress bar.
- [x] Reserve a fixed10dp progress slot and constrain footer status to one line. Preserve visibility/live-region feedback and complete image fitting.
- [x] Run EditorScreenTest and EditorActivityTest; expect PASS and inspect portrait/landscape screenshots. Commit and open dependent PR with Closes #24.

### Task 2: Temporary long-press comparison (#25)

Files: ui/editor/BeforeAfterPreview.kt, EditorScreen.kt if lifecycle propagation is necessary; ui/editor/EditorScreenTest.kt and EditorActivityTest.kt.
Interfaces: keep persistent EditorUiState.showOriginal controlled only by toolbar callback; gesture-local original uses input without modifying it. The edited frame marker is valid only when neither comparison mechanism is showing input.
- [x] Add touch tests with distinct solid input/output colors: long press shows input; release/cancel returns edited; preselected original remains original; short tap does not compare; source replacement/export lock cancel gesture. Observe RED before implementation.
- [x] Implement bounded pointer handling, transient state reset by source/enabled/lifecycle, and a finalizer on cancellation. Keep explicit toolbar behavior and restore correct display after background/rotation.
- [x] Run UI/activity tests with real image checks and lifecycle interruption. Expected PASS; commit and open dependent PR with Closes #25.

### Task 3: Cartridge icons and integrated delivery (#23)

Files: ui/editor/PresetSelector.kt and a small cartridge drawing component; EditorUiState.kt, EditorViewModel.kt, MainActivity.kt; remove unused PresetThumbnailRenderer.kt and its tests; update thumbnail-only VM/activity/performance expectations; localized hint; validation docs.
Interfaces: PresetSelector consumes film metadata/selection only. All remaining render and export interfaces stay unchanged. Performance workload still exercises visible controls and records final resource state.
- [x] Add a selector test ensuring all3 stock icons and selected names exist without photo-derived state; preserve parameter-selection assertions. Observe RED on thumbnail cards.
- [x] Add native stock-specific cartridge illustrations and remove obsolete thumbnail production/state/tests. Replace thumbnail hint with selection guidance. Update performance checkpoints to report the actual final UI resources.
- [ ] Run complete emulator+vivo functional suites, unit/lint/Python, live process restoration, and final-source profileable performance. Expected all functional checks pass and unchanged physical gates met. Inspect dark/light/large-text/landscape and generated-fixture phone images.
- [ ] Commit/open dependent PR with Closes #23, reconcile final evidence and source/APK identities, perform one fresh whole-candidate review with tested Important/Critical fixes. Finish the existing v1/initialUI delivery and merge dependencies only after gates pass.
