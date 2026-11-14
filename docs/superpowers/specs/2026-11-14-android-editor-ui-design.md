# A simple, photo-first Android editor

The maintainer tried the vivo build and found the interface too plain. They explicitly chose **Snapseed (指划修图)** as the reference: simple and easy to learn. This extends v1 delivery; renderer, export, physical acceptance and dependent PR merges remain required. The implementation remains native Kotlin/Compose using the pinned dependencies.

## Approach

Adopt a photo-first workspace with three labeled destinations: **胶片 / 调整 / 导出** (Films / Adjust / Export). This is the chosen reference direction, adapted to our existing three films and three controls. Retaining the current scrolling form would keep the photo small; a dense professional toolbar would work against the requested ease of use. The following details are implementation decisions under the maintainer's standing authorization to proceed autonomously.

Use neutral light and dark palettes following the system, restrained accent/selection marks, consistent typography and spacing. Avoid colored panel backgrounds, oversized slider handles, dense tick marks and decoration that competes with photo colors. Preserve the Film Editor name and attribution; do not copy Snapseed branding or introduce unsupported tools.

## Screen and behavior

- Empty editor: one clear import action and a brief explanation; editing/export cannot run without a source. No full disabled parameter form. Initialization/error/retry remain visible and accessible.
- Loaded editor: photo takes most of the portrait workspace (target at least 55% at normal font size on a phone); fit the whole image, never crop the main preview. Top bar provides import/change photo and comparison with a selected/pressed state. All tap targets remain at least 48dp.
- Bottom Films destination: horizontal cards for Portra 400, Gold 200 and Ektar 100 with real thumbnails of the current normalized photo at each film's default settings. Clear selected film, accessible name and selected semantics. Thumbnail failure falls back to labeled cards without disabling editing or showing a fake effect.
- Adjust destination: three labeled parameter choices (film strength, exposure, temperature), showing one slider/value at a time. Keep existing ranges/quantization (0–100% by 1%, -2…+2 EV by 0.1, -100…+100 by 1). Expose accessible SetProgress and current value. Add a subtle zero/default position; no visible slider ticks. Reset restores the current film's defaults.
- Export destination: actual output dimensions, a clear Save photo button and progress/disabled state; success gives Open photo. Opening the destination never saves by itself. Preserve current JPEG95/sRGB/4096cap/no-upscale and source protection.
- Switching destinations or parameters never changes edits, triggers an export, or loses the source. Preserve selected destination/parameter across rotation using saveable UI state. Back from a secondary destination returns to Films before leaving the editor. Comparison remains available regardless of destination, with existing explicit toggle semantics; no gesture-only operation.
- Compact landscape/tablet uses a photo pane and a bounded tool pane. Large fonts/small windows allow tool content to scroll without obscuring the navigation or trapping actions. Chinese and English labels remain localized. Errors/retry stay discoverable in every destination; saving disables import, edit controls, duplicate saves and inappropriate navigation.

## Thumbnail ownership and performance

Use the existing checked AssetPresetRepository and ColorMath on Dispatchers.Default for thumbnails with a maximum long edge of 160px. Reuse the exact film math; do not introduce another GL context or change production rendering. Produce at most three images for the current source, independent of manual parameter changes. Cancel obsolete generation and reject late results by input/source identity. Never recycle a bitmap handed to Compose. Clear image references on source replacement; do not persist bitmap data or personal images. Thumbnail failures are optional-UI failures, separate from preview/export validity.

## Validation and delivery

Retain real input→present and export timing markers. Update existing UI/flow/performance actions to navigate visible destinations explicitly; hidden controls must not be used to simulate success. Cover empty state, selected cards, accessible parameter adjustment, tool-switch/rotation state, export lockout, error/retry, large text and landscape. Verify actual thumbnail pixels against existing CPU math and reject stale source results. Capture generated-fixture screens on vivo and emulators. Run required unit/lint/Python/API29+36+37 tests, physical color/full flow/process restore, then physical performance/resource checks on the final UI. User trial is useful feedback, not a substitute for these gates.
