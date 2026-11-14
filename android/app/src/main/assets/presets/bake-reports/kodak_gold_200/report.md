# QA report — `spektrafilm_v034_gold200_portraendura_1lut_srgb_srgb`

- **Print**: `kodak_portra_endura`
- **Film**: `kodak_gold_200`
- **Input color space**: `sRGB`
- **Output color space**: `sRGB`
- **Topology**: `1lut`  ·  **Resolution**: `33^3`
- **spektrafilm version**: `0.3.4`
- **Generated**: `2026-11-14T01:41:03Z`

## Summary

| Test | Headline numbers |
|---|---|
| [off_grid_identity](#off-grid-identity) | trilinear_dE2000_max=0.3856  trilinear_dE2000_p99=0.1682  trilinear_dE2000_p50=0.02689  tetrahedral_dE2000_max=0.3286  tetrahedral_dE2000_p99=0.1359  tetrahedral_dE2000_p50=0.02485  trilinear_dITP_max=0.521  trilinear_dITP_p99=0.1648 |
| [monotonicity](#monotonicity) | violations=74  worst_negative_diff=-0.0007506  centerline_pin_encoded=0.4614  centerline_violations=0 |
| [jacobian_condition](#jacobian-condition) | max_log10_cond=2.321  p99_log10_cond=2.046  p50_log10_cond=0.7987 |
| [total_variation](#total-variation) | tv=0.02784  tv_r=0.007605  tv_g=0.009831  tv_b=0.0104  axial_highband_ratio_r=0.07293  axial_highband_ratio_g=0.07101  axial_highband_ratio_b=0.05665  axial_highband_ratio_mean=0.06686 |
| [output_gamut_compression](#output-gamut-compression) | fold_triangles=1794  fold_fraction=0.292  input_hull_volume=0.06071  output_hull_volume=0.0223  compression_ratio=0.3672  compression_algorithm=cam16ucs  rim_oog_fraction=0.3806  rim_oog_samples=419  rim_max_displacement=0.1764  rim_mean_displacement=0.08005 |
| [characteristic_curve](#characteristic-curve) | system_gamma_at_mid=1.209  max_channel_density_spread=0.131 |
| [dynamic_range_usage](#dynamic-range-usage) | encoded_range_stops=10.46  active_range_stops=6.471  toe_collapsed_stops=3.685  shoulder_collapsed_stops=0.3  midgray_output_y=0.1742  midgray_offset_stops=-0.04764 |
| [planckian_sweep](#planckian-sweep) | max_bend_angle_deg=14.55  cct_range_k=2700-10000 |
| [hue_twist_oklab](#hue-twist-oklab) | max_rotation_chroma_0.20_0.35=64.61  max_rotation_chroma_0.35_0.50=28.24  max_rotation_chroma_0.50_0.70=24.6  max_rotation_chroma_0.70_1.00=23.69  max_hue_rotation_deg=64.61  samples_in_locus=35937  samples_filtered_out_of_locus=0 |
| [spectral_locus_envelope](#spectral-locus-envelope) | cube_resolution=33  cube_cells=35937  valid_cells=35937  inside_output_gamut_fraction=1  near_locus_fraction=0 |
| [input_gamut_compression_preview](#input-gamut-compression-preview) | active=1  algorithm=xy  knee_threshold=0  knee_limit=1  knee_power=6  oog_fraction=0  n_oog_samples=0  n_oog_bright=0  reference_illuminant=D55 |
| [input_gamut_compression_smoothness](#input-gamut-compression-smoothness) | active=1  algorithm=xy  knee_threshold=0  knee_limit=1  knee_power=6  probe_radius=0.3  probe_samples=720  worst_step=0.002614  median_step=0.002409  worst_over_median_step=1.085  reference_illuminant=D55 |
| [noise_sensitivity](#noise-sensitivity) | L_slice=0.5646  sigma_in_encoded=0.005  n_input_samples=32  max_sigma1=0.9052  p99_sigma1=0.8863  p50_sigma1=0.7378  max_anisotropy=71.02  p99_anisotropy=59.92  max_hue_rotation_deg=21.24  worst_hue_deg=-90  ellipse_display_scale=1.5 |
| [noise_gradient](#noise-gradient) | image_side=512  sigma_in_encoded=0.02  mid_L=0.5646  rim_scale=0.96  input_in_gamut_fraction=0.998  mean_abs_output_delta=0.008529  p99_abs_output_delta=0.04717 |
| [output_gamut_edge_stress](#output-gamut-edge-stress) | Rec.709_oog_fraction_saturated_row=0.4238  DCI-P3_oog_fraction_saturated_row=0.9956  Rec.2020_oog_fraction_saturated_row=1 |
| [rg_plane_slices](#rg-plane-slices) | n_slices=9  cube_resolution=33 |

## off_grid_identity

**Units**: ΔE₀₀

| Metric | Value |
|---|---|
| `trilinear_dE2000_max` | 0.3856 |
| `trilinear_dE2000_p99` | 0.1682 |
| `trilinear_dE2000_p50` | 0.02689 |
| `tetrahedral_dE2000_max` | 0.3286 |
| `tetrahedral_dE2000_p99` | 0.1359 |
| `tetrahedral_dE2000_p50` | 0.02485 |
| `trilinear_dITP_max` | 0.521 |
| `trilinear_dITP_p99` | 0.1648 |


**Reference values:**
- `trilinear_dE2000_max` ≤ 2.0 — perceptual visibility threshold for graphics work
- `trilinear_dE2000_p99` ≤ 1.0 — interpolation-quality target across the cube
- `tetrahedral_dE2000_max` ≤ 2.0 — same threshold under tetrahedral interpolation
- `tetrahedral_dE2000_p99` ≤ 1.0 — same target under tetrahedral interpolation

ΔE₀₀ measures perceptual error between the LUT's prediction (via interpolation in the host's mode — trilinear or tetrahedral) and the live pipeline at off-grid positions. Above the visibility threshold users will see interpolation artifacts the on-grid test cannot detect; remedies are higher LUT resolution or wire-shaping changes. ΔITP is reported as a secondary, HDR-aware companion metric.

## monotonicity

**Units**: cells

| Metric | Value |
|---|---|
| `violations` | 74 |
| `worst_negative_diff` | -0.0007506 |
| `centerline_pin_encoded` | 0.4614 |
| `centerline_violations` | 0 |


**Reference values:**
- `violations` == 0 — any cube-wide fold-back is a hard invertibility break
- `worst_negative_diff` == 0.0 when violations == 0; a tiny negative (≈ -1e-5) on the centerline is bake jitter rather than a real fold

Each diagonal axis-channel pair (R-in vs R-out, etc.) must be monotonic for the LUT to be invertible without fold-backs. The `violations` count is cube-wide; the figure visualizes the centerline sweep through middle-gray-encoded for honest comparison across input color spaces (without this, log inputs like V-Log would be evaluated through their encoded midpoint, which is mid-bright rather than gray and produces visually confusing curve shapes). Violations on the centerline indicate either a model regime that legitimately produces a fold (DIR couplers in shadows, gamut compression at the saturation knee) or a bake artifact at the cube boundary; investigate both before relaxing the test.

## jacobian_condition

**Units**: log10(cond J)

| Metric | Value |
|---|---|
| `max_log10_cond` | 2.321 |
| `p99_log10_cond` | 2.046 |
| `p50_log10_cond` | 0.7987 |


Where the cube colors locally compress onto a near-curve (e.g., the highlight shoulder collapsing chroma), log-cond rises sharply. Shape of the high-cond region matters more than its absolute value — a thin shell near the gamut boundary is expected; a fat interior region is suspicious.

## total_variation

| Metric | Value |
|---|---|
| `tv` | 0.02784 |
| `tv_r` | 0.007605 |
| `tv_g` | 0.009831 |
| `tv_b` | 0.0104 |
| `axial_highband_ratio_r` | 0.07293 |
| `axial_highband_ratio_g` | 0.07101 |
| `axial_highband_ratio_b` | 0.05665 |
| `axial_highband_ratio_mean` | 0.06686 |


Total variation is the mean absolute finite-difference of the cube table — a smoothness scalar. The axial-FFT high-band ratio adds spectral-domain evidence: a bake with banding shows lifted energy in the upper half of the axial spectrum. The histogram plot is a sanity check on clipping incidence at 0 and 1.

## output_gamut_compression

| Metric | Value |
|---|---|
| `fold_triangles` | 1794 |
| `fold_fraction` | 0.292 |
| `input_hull_volume` | 0.06071 |
| `output_hull_volume` | 0.0223 |
| `compression_ratio` | 0.3672 |
| `compression_algorithm` | `cam16ucs` |
| `rim_oog_fraction` | 0.3806 |
| `rim_oog_samples` | 419 |
| `rim_max_displacement` | 0.1764 |
| `rim_mean_displacement` | 0.08005 |


**Reference values:**
- `fold_triangles` == 0 — any cube-face fold is a hard non-invertibility
- `compression_ratio` in [0.05, 1.05] — < 0.05 is extreme collapse, > 1.05 is unexpected expansion

Face folds mean the cube surface maps onto itself — a non-invertible region that breaks grading. The compression ratio quantifies how much perceptual volume the LUT throws away; numbers in [0.05, 1.05] are normal, outside means either degenerate output (very small ratio) or unexpected expansion (ratio > 1). The figure's left panel shows the LUT's compressed gamut in OkLab (faint cube cloud) with the unbounded rim (solid colored lines) and the compressed rim (dashed) overlaid; the right panel is the canonical xy-chromaticity preview of the same compression event.

## characteristic_curve

**Units**: density

| Metric | Value |
|---|---|
| `system_gamma_at_mid` | 1.209 |
| `max_channel_density_spread` | 0.131 |


The system's response to neutral input should be smooth, with the three channels tracking each other (small spread). Big channel divergence on a neutral ramp is a calibration or chemistry-model bug, not a LUT bug; check the print chemistry's neutral handling.

## dynamic_range_usage

**Units**: stops

| Metric | Value |
|---|---|
| `encoded_range_stops` | 10.46 |
| `active_range_stops` | 6.471 |
| `toe_collapsed_stops` | 3.685 |
| `shoulder_collapsed_stops` | 0.3 |
| `midgray_output_y` | 0.1742 |
| `midgray_offset_stops` | -0.04764 |


Midgray exposure: the camera's 18% gray renders at -0.05 stops vs the ideal 0.18 output (on target). For a log input this should be ≈0 (identity gain); a large offset means the LUT is re-exposing midgray. For SDR inputs a small positive drift is the documented simple-gain trade-off when stops_above_midgray boosts the shoulder.
The 'active rendering range' is how many input stops the LUT distinguishes — slope above 0.10 D/stop. Below that threshold, an input stop change barely moves the output, so the stop is effectively collapsed. The 'input encoding range' is a property of the input color space (sRGB ~2.5 EV above middle gray, V-Log ~8 EV), not the LUT. Toe and shoulder collapsed stops sit *within* the encoded range — they're rendering decisions, not encoding limits.

## planckian_sweep

**Units**: degrees

| Metric | Value |
|---|---|
| `max_bend_angle_deg` | 14.55 |
| `cct_range_k` | `2700-10000` |


**Reference values:**
- `max_bend_angle_deg` ≤ 30° — daylight CCT sweep should map to a smooth curve; pure monotone smoothness gives ~0°

White points across the daylight CCT range should map to a smooth curve in output chromaticity. Sharp bends suggest the model is doing something discontinuous to chromatic adaptation — worth investigating the scan illuminant handling and the print's spectral response curves.

## hue_twist_oklab

**Units**: degrees

| Metric | Value |
|---|---|
| `max_rotation_chroma_0.20_0.35` | 64.61 |
| `max_rotation_chroma_0.35_0.50` | 28.24 |
| `max_rotation_chroma_0.50_0.70` | 24.6 |
| `max_rotation_chroma_0.70_1.00` | 23.69 |
| `max_hue_rotation_deg` | 64.61 |
| `samples_in_locus` | 35937 |
| `samples_filtered_out_of_locus` | 0 |


Hue rotation as a function of input chroma is the single thing colorists notice without instrumentation. Some rotation is part of the look (a film simulation IS a hue rotation in places); the magnitude is stock-specific. Use this number to track drift between bakes of the same stock — large jumps signal a model-side regression. Per-stock pass/fail thresholds wait on the baselines work (n080 §10).

## spectral_locus_envelope

| Metric | Value |
|---|---|
| `cube_resolution` | 33 |
| `cube_cells` | 35937 |
| `valid_cells` | 35937 |
| `inside_output_gamut_fraction` | 1 |
| `near_locus_fraction` | 0 |


Every cube cell projected to xy and rendered at its own output RGB color. Density variations show where the LUT concentrates color reproduction (achromatic core dense, saturated rim sparse). `inside_output_gamut_fraction` near 1.0 confirms output gamut compression is keeping the simulation inside the output primaries triangle as intended. The complementary `output_gamut_compression` figure shows the rim envelope and the compression's effect explicitly.

## input_gamut_compression_preview

| Metric | Value |
|---|---|
| `active` | true |
| `algorithm` | `xy` |
| `knee_threshold` | 0 |
| `knee_limit` | 1 |
| `knee_power` | 6 |
| `oog_fraction` | 0 |
| `n_oog_samples` | 0 |
| `n_oog_bright` | 0 |
| `reference_illuminant` | `D55` |


Shows which cube samples fall outside the visible spectral locus (where Hanatos 2025 spectral upsampling is well defined) and where the compression maps them. Red = input OOG, cyan = compressed destination, orange arrows = displacement (bright OOG subset only, capped at 400 for legibility). Samples in the locus pass through unchanged. The compression is baked into the per-film tc_lut at build time (n100 §3.1); this plot is the build's audit trail. Informational only — no pass/fail.

## input_gamut_compression_smoothness

| Metric | Value |
|---|---|
| `active` | true |
| `algorithm` | `xy` |
| `knee_threshold` | 0 |
| `knee_limit` | 1 |
| `knee_power` | 6 |
| `probe_radius` | 0.3 |
| `probe_samples` | 720 |
| `worst_step` | 0.002614 |
| `median_step` | 0.002409 |
| `worst_over_median_step` | 1.085 |
| `reference_illuminant` | `D55` |


A ring of input chromaticities around the film reference illuminant runs through the compression and emerges as a rainbow ring of output points. A smooth, evenly-spaced ring means the compression is hue-uniform; bunching or color jumps reveal hue discontinuities that would translate into banding in the baked LUT. `worst_over_median_step` near 1 is ideal; >>1 indicates a kink. Informational only — no pass/fail.

## noise_sensitivity

**Units**: OkLab per encoded-RGB

| Metric | Value |
|---|---|
| `L_slice` | 0.5646 |
| `sigma_in_encoded` | 0.005 |
| `n_input_samples` | 32 |
| `max_sigma1` | 0.9052 |
| `p99_sigma1` | 0.8863 |
| `p50_sigma1` | 0.7378 |
| `max_anisotropy` | 71.02 |
| `p99_anisotropy` | 59.92 |
| `max_hue_rotation_deg` | 21.24 |
| `worst_hue_deg` | -90 |
| `ellipse_display_scale` | 1.5 |


First-order noise propagation Σ_out ≈ J·Σ_in·Jᵀ with isotropic encoded-input noise. σ₁(J) is the worst-case noise-gain scalar at each input chromaticity; ellipse orientation is the direction that gain points in OkLab a*b*. Eccentric ellipses point along the direction the LUT amplifies most — e.g., orange samples with an ellipse tilted toward the red axis exactly reproduces the user-reported 'red speckle on orange' phenomenon. The σ₁ heatmap shows where in chromaticity the LUT is noisiest; the hue rosette summarizes per-hue luminance vs chroma noise gain at one chroma ring. Per-stock pass/fail thresholds wait on baselines (n080 §10).

## noise_gradient

**Units**: encoded RGB

| Metric | Value |
|---|---|
| `image_side` | 512 |
| `sigma_in_encoded` | 0.02 |
| `mid_L` | 0.5646 |
| `rim_scale` | 0.96 |
| `input_in_gamut_fraction` | 0.998 |
| `mean_abs_output_delta` | 0.008529 |
| `p99_abs_output_delta` | 0.04717 |


Continuous visual noise diagnostic. The clean panel is a square OkLab hue-cycle gradient with black → saturated → white vertically; the noisy panel applies a single seeded σ=0.02 encoded-input noise realization before the LUT. If the LUT amplifies certain hues or tones, the noisy panel reveals it as visible grain, hue wobble, or texture on what should be a smooth gradient.

## output_gamut_edge_stress

| Metric | Value |
|---|---|
| `Rec.709_oog_fraction_saturated_row` | 0.4238 |
| `DCI-P3_oog_fraction_saturated_row` | 0.9956 |
| `Rec.2020_oog_fraction_saturated_row` | 1 |


Each panel is a Granger-style RGB stress chart at the edges of one target RGB space: each column is a tent white → saturated_edge(hue) → black in target-cs encoded RGB. The image is CAT-adapted into the bundle's input primaries (no clipping) and pushed through the runtime pipeline, which handles chromaticities outside the bundle's input cube via spectral upsampling + input gamut compression toward the visible locus. Output is rendered to sRGB for viewing via CAT + a hard clip to [0, 1] (the runtime's own output gamut compression is expected to have already done the cube containment). The gradient should be continuous and smooth from top to bottom; visible bands, hue jumps, or posterization reveal model-side pathology at saturated input. `oog_fraction_*` reports how many pixels lie outside the bundle's input primaries cube — a diagnostic of how much of each target's saturated rim the bundle has to handle as out-of-input-gamut input (the pipeline still renders them, but they're physically outside the bundle's declared input gamut).

## rg_plane_slices

| Metric | Value |
|---|---|
| `n_slices` | 9 |
| `cube_resolution` | 33 |


Cube cross-sections at constant input B, displayed in sRGB (hard-clipped). Smooth color gradation across the panels indicates a well-behaved cube along B; abrupt changes between adjacent slices point at low-resolution or noisy regions. Within each panel the gradient runs R left→right, G bottom→top — corner colors are the LUT's renderings of input (R, G, B) corners at that B.

---

Generated by `spektrafilm_lut_creator.qa` (spektrafilm 0.3.4). See `studies/a40_lut_system/n080_lut_quality_and_visualization.md` for design context.

QA figures omitted from mobile assets; all numerical results retained.
