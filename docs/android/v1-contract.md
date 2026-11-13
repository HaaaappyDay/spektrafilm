# Android v1 contract — version 1

Source: https://github.com/HaaaappyDay/spektrafilm/issues/9

Confirmed by the maintainer on 2026-11-14. Reference physical device: vivo X200 Pro mini (API, RAM and GPU to be measured when connected). This document captures the approved contract; check the linked tracker for live completion state.

# Android v1: offline film photo editor — design and implementation tracker

## User-confirmed requirements
1. Import → select a film preset → adjust → preview → export.
2. v1 provides manual film-effect strength, exposure and temperature controls.
3. Automatic exposure and temperature are deferred to v2. Auto must not change film strength.
4. Each implementation issue must let a new agent work without the original conversation. Parallel work is possible after shared interfaces are fixed.

## Architecture and engineering defaults
The following are explicit planning choices, not individually user-specified requirements. They are the baseline for implementation. Record changes here and synchronize dependent issues instead of letting agents invent incompatible behavior.
- Kotlin + Jetpack Compose, one app module, OpenGL ES 3.0 offline LUT rendering. Python remains the offline preset-generation and validation reference.
- Alternatives considered: (1) pre-baked on-device LUTs — selected for offline operation and reuse of the existing exporter; spatial effects need separate work; (2) a Python server — adds photo uploads/operations, excluded; (3) a complete native physics port — substantially more dependency/performance work, excluded from v1.
- Independent Gradle root at `android/`. Package/application ID: `io.github.haaaappyday.filmeditor`. Development display name: "Film Editor". Minimum Android 10/API 29; require GLES 3.0.
- The foundation issue selects and pins a currently compatible stable compile/target SDK, AGP, Gradle, JDK, Kotlin and Compose combination. Record exact versions and compatibility sources. No dynamic versions or alpha dependencies.
- Import one static SDR JPEG/PNG. Clearly reject unsupported types. Use the system photo picker with a system document-picker fallback; do not request broad photo-library access. Copy the selected source into app-private storage without modifying the original.
- Convert tagged images to sRGB through platform color management, rather than relabeling pixels. Treat untagged inputs as sRGB. Composite transparent PNG pixels onto white in encoded sRGB before subsequent processing; all working images are opaque. Normalize all EXIF orientations, including mirrors, exactly once.
- HDR/gain-map input is outside the guarantee: use a reliably decoded SDR base or reject it explicitly; never interpret HDR values as SDR.
- Preview long edge: at most 1600 px. Export long edge: at most 4096 px. Preserve aspect ratio and never upscale smaller images. Show actual output dimensions before export; do not promise original resolution when downsizing.
- Export sRGB JPEG, quality=95, into MediaStore `Pictures/FilmEditor`. Do not copy original metadata such as GPS or stale orientation/ICC tags. Show saving, success, failure and retry states.
- At least three real presets: `kodak_portra_400`, `kodak_gold_200`, `kodak_ektar_100`, paired with `kodak_portra_endura`. Provide readable film names and attribution. An identity LUT is a test fixture, not a shipped film preset.
- Excluded: camera capture, RAW/HEIF, HDR output, video, batch editing, grain/halation, curves, green/magenta tint, Auto, accounts, networking and Play Store publication.

## Repository context and required reading
Planning baseline: commit `3bb2c2d2801ff68b92019cf1dbcbb133d60832bc`. Implement from the fork's current main and check subsequent changes.
- [Repository overview](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/README.md), [linear RGB runtime contract](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/src/spektrafilm/README.md), [LUT export layer](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/src/spektrafilm_lut_creator/README.md).
- [BundleSpec](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/src/spektrafilm_lut_creator/bundles.py), [BundleBuilder](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/src/spektrafilm_lut_creator/builders.py), [cube format](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/src/spektrafilm_lut_creator/formats/cube.py), [sampling order](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/src/spektrafilm_lut_creator/grid.py).
- [lut_mode disables automatic/spatial/stochastic behavior](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/src/spektrafilm/runtime/params_builder.py) and [regression tests](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/tests/test_lut_mode.py). A static LUT does not reproduce the complete physics pipeline.
- [Python dependencies](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/pyproject.toml) require Python 3.13 and a substantial scientific stack. Ordinary Android builds must not invoke Python preset baking.
- Preserve [LICENSE](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/LICENSE), the actual [asset license text](https://github.com/HaaaappyDay/spektrafilm/blob/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc/SPEKTRAFILM_LICENSE.txt), authorship and source attribution. README and asset-license wording differ; refer to the actual texts without rewriting terms or representing requests as legal obligations.

## Shared API contract v1
The foundation issue creates these under `android/app/src/main/java/io/github/haaaappyday/filmeditor/core/`. Consumers use the same types. Split files as appropriate; preserve signatures and semantics.

```kotlin
data class EditParams(
    val presetId: String,
    val strength: Float = 1f,    // [0, 1]; UI 0–100%
    val exposureEv: Float = 0f, // [-2, +2]; UI step 0.1 EV
    val temperature: Float = 0f // [-100, +100]; positive = warmer, not Kelvin
)
data class ImageSource(
    val id: String,             // stable UUID
    val localPath: String,      // immutable app-private source copy
    val width: Int,             // oriented dimensions before sampling
    val height: Int,
    val mimeType: String
)
data class FilmPreset(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val sha256: String
)
interface ImageRepository {
    suspend fun importImage(uri: android.net.Uri): ImageSource
    suspend fun decode(source: ImageSource, maxLongEdge: Int): android.graphics.Bitmap
    suspend fun remove(source: ImageSource)
}
interface PresetRepository {
    suspend fun listPresets(): List<FilmPreset>
}
interface FilmRenderer {
    suspend fun render(input: android.graphics.Bitmap, params: EditParams): android.graphics.Bitmap
    suspend fun release()
}
data class ExportRequest(val source: ImageSource, val params: EditParams)
data class ExportResult(val uri: android.net.Uri, val width: Int, val height: Int)
interface PhotoExporter {
    suspend fun export(request: ExportRequest): ExportResult
}
enum class EditorErrorCode {
    UNSUPPORTED_IMAGE, IMAGE_TOO_LARGE, READ_FAILED, INVALID_PARAMS,
    PRESET_UNAVAILABLE, RENDER_FAILED, EXPORT_FAILED
}
class EditorException(val code: EditorErrorCode, cause: Throwable? = null)
    : Exception(code.name, cause)
```

- Bitmap contract: software-readable ARGB_8888, sRGB, alpha=255, orientation already normalized. decode returns a new exclusively owned bitmap; the caller owns it. render neither modifies nor recycles its input and returns a new exclusively owned bitmap of the same dimensions.
- Suspend APIs move disk, decoding and GL work off the main thread. Serialize rendering on a dedicated GL thread. Define lifecycle ownership; never recycle a bitmap still displayed by Compose.
- Parameters are immutable snapshots. Reject NaN/Inf, out-of-range values and unknown presets with typed errors; do not silently substitute another preset. UI sliders enforce valid ranges.
- Propagate CancellationException; do not wrap cancellation as a business failure. Map errors to user messages at the UI boundary. Persist source descriptors and parameters, never Bitmap/GL objects in SavedState.
- Preview and export share FilmRenderer math. Export re-decodes the private source at output size; it never enlarges a preview bitmap.

## Color and parameter math — consistent across implementations
Temperature is a relative warm/cool effect, not recovery of capture Kelvin or complete white balance from JPEG.
1. Decode encoded sRGB input S in [0,1] to linear L using the standard piecewise EOTF:
   `S <= 0.04045 ? S / 12.92 : ((S + 0.055) / 1.055)^2.4`.
2. Let `t = temperature / 100`, `g = (2^(0.25*t), 1, 2^(-0.25*t))`.
   `C = clamp(L * g * 2^exposureEv, 0, 1)`. Positive temperature warms. Clipped information cannot be recovered.
3. Encode C to X using sRGB OETF:
   `C <= 0.0031308 ? 12.92*C : 1.055*C^(1/2.4)-0.055`.
4. Sample the 3D LUT at X with trilinear interpolation, producing encoded sRGB F. Clamp F to [0,1] and decode it to linear F_lin.
5. Blend in linear space: `O_lin = (1-strength)*C + strength*F_lin`. Encode to sRGB, clamp and quantize to 8-bit.
6. strength=0 bypasses only the film effect and preserves exposure/temperature. Original comparison displays the completely unedited normalized input. These are different operations.
7. Presets are sRGB→sRGB, 33³, 1-LUT. Set `stops_above_midgray=None` explicitly when baking to preserve input linear scale rather than rely on the default SDR gain. Record all other effective bake settings in the manifest. This bake configuration is not UI Auto.
8. Cube order is R fastest, then G, then B; Python indexing is [b,g,r,:]. Test texture layout, coordinates and edge sampling; prevent channel swaps, half-texel errors and unintended double sRGB conversions.
9. Resizing and a nonlinear pixel transform do not commute. Compare numerical correctness at identical input dimensions and parameters before JPEG encoding; do not demand byte equality between a screenshot and a JPEG.

## State and interaction
- A successful new import starts with Portra 400, strength=1, EV=0, temperature=0.
- Preset changes preserve all three adjustment values. A new successful import resets them; a failed/cancelled import preserves the previous photo and edits.
- Preview scheduling is latest-wins: an obsolete render cannot overwrite a newer image/parameter result. Export captures a source+params snapshot; disable editing/import and duplicate export while saving. Backgrounding/rotation must not duplicate saves.
- Preserve source/parameters across rotation. Restore a killed process from the private source and lightweight state; if the file is gone, return to import with a message. Continuing export after process death is not required; clean unpublished temporary entries and never show false success.
- Provide an accessible original-comparison toggle. Reset keeps the current preset and restores 1/0/0. Disable editing/export without a photo.
- Handle storage exhaustion, corrupt inputs, lost access and GL context loss through recoverable states.

## Implementation issues
- [ ] #10 — [Android v1] 01 Foundation, shared contracts and reproducible builds
- [ ] #11 — [Android v1] 02 Photo import, orientation/color normalization and base preview
- [ ] #12 — [Android v1] 03 Preset baking, 3D LUT rendering and color validation
- [ ] #13 — [Android v1] 04 Editor controls, preset selection and original comparison
- [ ] #14 — [Android v1] 05 Gallery export and failure recovery
- [ ] #15 — [Android v1] 06 End-to-end, performance and delivery acceptance

Dependency graph:
```text
Foundation / shared contracts
 ├── Import / base preview ──┬── Editor interaction ──┐
 └── LUT / rendering ────────┤                       ├── Integration acceptance
                            └── Photo export ───────┘
```
Import and rendering can proceed in parallel. Editor/export may start against fakes once contracts exist, but integration requires the real dependencies to merge.
v2 Auto #16 is an independent backlog item and does not block v1.

## Definition of done
- [ ] All six implementation issues and prerequisite PRs are complete/merged; builds, unit tests and lint pass.
- [ ] Three real film presets work offline. A fresh Android build does not require baking.
- [ ] Import, all three manual controls, original comparison and gallery export work. No Auto behavior.
- [ ] Evidence covers color math, mirrored/rotated EXIF, transparent PNG, wide-gamut conversion, 12MP inputs, rapid-edit races, cancellation/error recovery and process restoration.
- [ ] Validate on an Android 10 emulator and at least one GLES 3 physical device. Record model, API, RAM and build type; do not use emulator timing as device-performance evidence.
- [ ] Reference-device targets: after warm-up, 30 consecutive 1600px preview changes have end-to-end p95 ≤200 ms; a 12MP input exports at the 4096-long-edge cap in ≤10 s. Report misses and optimize or obtain maintainer acceptance; never silently relax targets.
- [ ] Provide a debug APK, reproducible build instructions, validation report and known limitations. Store publication, signing-key uploads and disclosure of personal photos are not requested.
- [ ] Existing Python behavior is preserved. Run relevant tests for Python changes; never automatically rewrite baseline snapshots.

## New-agent starting checklist
1. Read this issue, the assigned task, merged prerequisite PRs and any actual repository AGENTS.md.
2. Inspect current main and the build environment; work within the task's file ownership.
3. Write falsifiable tests for core math, formats and concurrency; retain meaningful device validation.
4. Report PR links, executed commands/results and unverified items. Writing tests is not evidence that they passed.

