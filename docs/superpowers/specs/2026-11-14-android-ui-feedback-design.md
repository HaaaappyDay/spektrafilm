# Android editor feedback

The maintainer tried the photo-first editor and explicitly requested three independently tracked changes: cartridge icons instead of photo thumbnails (#23), stable image sizing when loading feedback appears (#24), and long-press original comparison while retaining the toolbar toggle (#25). All public-facing repository prose must be English. Existing Android v1 scope, source protection, color math, and physical performance gates remain unchanged.

## Implementation decisions

- #24: keep a fixed progress slot and a single-line status footer. The complete fitted image retains identical bounds as rendering/import/export indicators change. An overlay would also work, but a fixed slot preserves the current visual separation without covering the photo.
- #25: hold-to-compare is temporary local UI state, separate from the persistent toolbar toggle. A recognized long press displays the normalized input; release or cancellation restores the prior toggle-controlled display. Source replacement, export lockout, and leaving the resumed lifecycle cancel the hold. Do not persist a gesture or alter edit parameters. Preserve actual edited-content tracing: a temporary original frame is never marked as a rendered edit.
- #23: use small native cartridge illustrations with stock-specific colors and labels for the existing three films. Keep accessible names and selection outlines. Remove the now-unused CPU photo-thumbnail generator, ViewModel state, and obsolete thumbnail tests; retain renderer/export pixel validation. No downloaded artwork or new dependency is required.

These are bounded implementation choices under the maintainer's existing instruction to execute autonomously. The explicit cartridge request supersedes the previous thumbnail design. Each issue receives an independent dependent PR, with one final integrated validation/review of the complete candidate before the existing merge chain is completed.
