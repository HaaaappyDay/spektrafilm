# Android presets and renderer implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, then one independent whole-branch review.

**Goal:** Implement #12: three genuine offline presets, one CPU-referenced GLES3 renderer, measured color validation.
**Architecture:** Existing BundleBuilder generates committed assets. Independent Python reference produces byte fixtures and compares LUT approximation with the deterministic direct pipeline. Pure Kotlin parser/math and a serialized offscreen GLES renderer consume the same cubes; rendering tiles without resizing.
**Tech stack:** Python3.13/project scientific dependencies, existing Kotlin/coroutines, GLES3/EGL14. No new Android runtime dependency.
**Spec:** [v1 contract](../../android/v1-contract.md) and [#12](https://github.com/HaaaappyDay/spektrafilm/issues/12).

## Global constraints

- Preserve exact shared FilmRenderer/PresetRepository APIs. No Python/Qt in APK or ordinary build.
- Presets portra400/gold200/ektar100 with portra_endura, sRGB→sRGB, topology1lut, resolution33, stops_above_midgray=None, QA enabled. Record every effective BundleSpec setting and versions.
- Follow parent math exactly: explicit sRGB transfer, linear EV/temperature, trilinear LUT in encoded domain, linear-strength blending. R fastest [b,g,r]. No implicit sRGB texture conversions.
- New opaque software ARGB8888 bitmap, unchanged dimensions/input. Invalid/nonfinite params and unknown presets fail explicitly. Cancellation propagates with no unpublished bitmap leak.
- Serialize GL and release. Tile beyond texture limits, preserve pixel ordering, test no seams. Handle context recreation; release is idempotent and terminal.
- Numerical implementation equivalence ≤2/255; report mean/p95/max separately from physical-model approximation. Physical GPU validation remains required before #12 is complete.

## Review focus

- Malformed manifests/cube files: bounded parsing, SHA mismatch, unknown ids, missing assets must not silently substitute presets.
- Cancellation during queued/tiled work and dispatcher return must preserve input and recycle unreturned output.
- release racing with render must wait for active GL use and reject later renders without leaking the thread.
- Context loss must clean invalid objects and permit a subsequent render with a fresh context.
- GPU row/channel ordering and tile boundaries must agree with independent bytes for asymmetric, off-grid and edge samples.

### Task 1: Python reference and real baked assets

**Files:** scripts/build_android_presets.py, scripts/verify_android_color.py, tests/android/test_mobile_reference.py, tests/test_lut_mode.py; assets/presets/*; androidTest/assets/render-fixtures/*.
**Interfaces:** build_android_presets.make_spec(film)->BundleSpec, build(out:Path)->dict; verify_android_color.sample_cube(table, rgb, domain_min, domain_max)->ndarray; transform(rgb,table,strength,exposure_ev,temperature)->ndarray; generate_fixtures(presets:Path,out:Path), approximate(presets:Path,out:Path).

- [ ] Tests first: identity cube preserves neutral colors; strength0 preserves EV/temp; +1EV doubles unclipped linear; warm gain R>neutral>B; nondefault domains/clamping; ordering and off-grid interpolation; invalid parameters rejected; make_spec explicitly disables headroom gain/enables QA; all three manifest hashes/domain/resolution/license verified after baking.
- [ ] Run pytest tests/android: expected missing-module RED. Investigate baseline stale grain-field assertion from removed schema; remove obsolete assertion only, retain all current spatial/stochastic guards. Run required existing LUT tests.
- [ ] Implement independent float64 NumPy math and fixed-seed fixture generation: 8 corners, 256 gray codes, near endpoints and >=256 random RGB8 patches in asymmetric image; all 27 combinations strength0/.5/1 EV-2/0/2 temperature-100/0/100 for each actual preset. Store RGBA input/expected bytes and metadata, no Python Android build step.
- [ ] Implement generator via existing BundleSpec/Builder with QA, write only requested output tree, preserve exact license/upstream attribution/change log, complete bakeSpec/version/engine commit; commit cubes and compact QA reports. No existing model/exporter default changes.
- [ ] Compare independent trilinear results against separately evaluated deterministic runtime pipeline configured from same spec, including transfer/gamut/headroom. Report sample count/seed, mean/p95/max RGB and DeltaE2000, worst samples and gray/rgb ramp plot. Do not conflate approximation with GPU implementation error.
- [ ] Run python -m pytest tests/android tests/lut_creator/test_cube_format.py tests/lut_creator/test_builders.py tests/test_lut_mode.py; expected all pass. Commit reference/assets/evidence.

### Task 2: Strict parser, reference math and asset repository

**Files:** render/CubeParser.kt, ColorMath.kt, AssetPresetRepository.kt; matching unit/instrumented tests.
**Interfaces:** Cube(size:Int, domainMin:FloatArray, domainMax:FloatArray, values:FloatArray), CubeParser.parse(text:String):Cube. ColorMath.transform(rgb:FloatArray,cube:Cube,params:EditParams):FloatArray and validate(params). AssetPresetRepository(context), suspend listPresets(), internal suspend loadCube(id):Cube.

- [ ] Tests first: valid TITLE/comments/tabs/whitespace, domain defaults/custom domains, identity and swapped-channel cube; reject 1D/combined, duplicate/late headers, NaN/Inf, wrong triplets, missing/extra values and size outside2..65. Bound text/assets before allocation.
- [ ] CPU tests against committed Python expected fixtures for all presets/params; unit identity/EV/warm invariants. Repository instrumentation verifies shipped assets and corruption/missing/unknown failures using injected asset reader.
- [ ] Observe RED in CI, implement parser/math/repository. Run unit/instrumented/lint, expected pass. Commit.

### Task 3: Offscreen GLES3 renderer and lifecycle

**Files:** render/GlFilmRenderer.kt, render/GlSession.kt, assets/shaders/film.{vert,frag}; androidTest/render/GlFilmRendererTest.kt.
**Interfaces:** GlFilmRenderer(context,presets=AssetPresetRepository(context),tileEdge=1024):FilmRenderer. Dedicated coroutine dispatcher plus mutex owns EGL/context/resources. Internal resetContextForTest() and diagnostics for tests only; core API unchanged.

- [ ] Tests first: real GPU compares every Python fixture <=2codes, alpha255, software/sRGB/config/dimensions/input hash; identity including nondefaultdomain via injected cube provider. TileEdge17 vs whole fixture agree/no seams; same render contract for preview/export equal dims.
- [ ] Race/cancellation tests: two concurrent requests, cancellation then recovery, release while active and repeated release, later render fails, injected context invalidation then real recreation. Record GL vendor/renderer/version/highp precision to additional test output plus numerical errors and generated previews.
- [ ] Observe missing-renderer RED. Implement EGL3 pbuffer+RGBA8 FBO, plain RGBA8 source, RGBA32F LUT with explicit eight-neighbor texelFetch, full-screen triangle, no blending/dithering, bounded tiles/readback. Preserve top-to-bottom rows consistently. Check GL errors and clean per-call resources in finally; recycle unreturned bitmap on dispatcher cancellation.
- [ ] Run CI API29/36. Inspect reports and visual artifacts. Run same tests on vivo when available; record actual API/GPU. Physical unavailability is not a passing gate.

### Task 4: Delivery and independent review

**Files:** docs/android/color-validation.md, android/README.md, CI paths for Python scripts/tests.

- [ ] Record all exact generation/test commands, manifests/license, measured implementation and approximation errors, device build/GL, limitations and reproducibility.
- [ ] Independent whole-branch review once, resolve important findings with targeted tests, final green suite.
- [ ] PR Closes #12 linking #9; merge only after required evidence, update parent. If physical device pending, retain draft and continue independent editor/export work without claiming #12 complete.
