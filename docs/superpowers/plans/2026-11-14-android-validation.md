# Android v1 integration and physical acceptance implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. One fresh whole-branch review after implementation. Existing user authorization covers execution, PRs and merges; physical evidence is a completion gate.

**Goal:** Complete #15's integrated flow, lifecycle, compatibility, resource and performance acceptance, then merge the dependent v1 PRs when every gate is proven.
**Architecture:** Extend the existing real-service instrumented suite and CI rather than introduce another app. Add a non-debuggable profileable variant and measured trace boundaries, with generated fixtures and reproducible device-side actions. Preserve raw traces and distinguish action-to-present latency from render/draw completion. Keep unmeasured physical rows explicitly not run.
**Tech stack:** Existing Kotlin/Compose/GLES3/MediaStore; Android instrumentation; system Perfetto and official record_android_trace helper. API29 and current stable Android17/API37 runtimes (API36 may remain for continuity), compile/target36 unchanged unless a demonstrated incompatibility requires a coordinated update.
**Spec:** [#15](https://github.com/HaaaappyDay/spektrafilm/issues/15), [parent #9](https://github.com/HaaaappyDay/spektrafilm/issues/9), docs/android/v1-contract.md, import/color/editor/export validation reports. [Official Android17 release](https://developer.android.com/blog/posts/android-17-is-here) establishes API37 as a stable runtime; existing API36 results are not the current-stable matrix row.

## Global constraints

- Real import → each of three presets → manual strength/EV/temperature → original → reset → export → system gallery, offline. Auto remains v2.
- Preserve model math, shared interfaces, source files and other apps' gallery items. No broad storage/network permission or personal-photo fixtures.
- 1600px preview, export4096 cap/no upscale, same-size pre-JPEG maxRGB error≤2 codes. Generated 48MP sampled within limits or clearly rejected, no OOM/ANR.
- Physical profileable performance: warm up then 30 changes, action→display p50/p95, p95≤200ms;12MP button→publication≤10s; stage times and peakJava/native/graphics memory. 20imports+10exports require post-cache resource trends.
- All predecessor PRs must merge before completion. Emulator/software-GPU evidence cannot satisfy physical checks. Only exact tested commits/APKs receive pass claims.

## Review focus

- A resumed Activity or completed draw is not a presented frame; match content identity and FrameTimeline presentation before accepting preview latency.
- Noncooperative previous imports and temperature-only renders must not overwrite later source/parameters; deterministic gated tests cover these conditions.
- Interrupted process/export recovery must preserve published output and durable source, including failure halfway through tiled render.
- Test/profiling cleanup must never delete user photos or wipe unrelated app data on a physical device; only generated fixture identities are eligible.
- Resource sampling must account for cache warmup, native/graphics allocations and thread growth, with trace loss/unsupported counters stated rather than interpreted as zero.

### Task 1: Integrated compatibility and lifecycle acceptance

Files: e2e/PhotoEditingFlowTest.kt, e2e/LifecycleAndRecoveryTest.kt, existing editor/render tests for targeted gaps, .github/workflows/android.yml, scripts/generate_android_fixtures.py as required.
Interfaces: Consume MainActivity and its real EditorViewModel factory, existing fixture/import hooks and testOutputDirectory(). Produce per-API v1-flow.json and device-build.json with model/API/RAM/GPU/driver, actual dimensions, commit/build and pass/failure evidence.
- [x] Read prior reports, map each #15 assertion to existing tests or an explicit new case. Extend the flow with all presets, all three controls, original/reset, cancelled/failed/replaced import and airplane-mode gallery opening.
- [x] Add gated temperature-only and noncooperative superseded-import regressions; observe failures if a defect exists, then minimal fix and whole-suite pass. A new coverage test that passes existing correct behavior is recorded as coverage, not a fabricated RED.
- [x] Add lifecycle cases for durable restoration, missing source, actual renderer/context recreation and tiled cancellation. Use controlled failures to reproduce real edge cases; preserve source hashes.
- [x] Reuse the existing generated48MP fixture and ImageNormalizerTest sample-at1600 coverage; add full editor flow and4096 export-cap sampling coverage without full-resolution decode-before-sample; retain existing EXIF/P3/alpha/corrupt/tiny/12MP cases.
- [x] Add stable API37 runtime and CI device/build metadata; keep explicit2048MiB RAM/128MiB Javaheap in emulator configuration and report actual Runtime.maxMemory, never infer device limits from defaults. Run clean-checkout assemble/unit/lint/connected on required APIs and Python109/reference reproduction. Expected: all executed tests pass, recorded failures remain blocking.

### Task 2: Reproducible physical measurement harness

Files: android/app/build.gradle.kts, profileable manifest overlay, minimal trace instrumentation in editor/preview/export, scripts/record_android_v1.py, docs/android/v1-perfetto.cfg, tests/android/test_v1_report.py.
Interfaces: `record_android_v1.py --serial SERIAL --out DIR` records generated-fixture-only scenario and raw system trace using official record_android_trace; no device selected implicitly when multiple present. Report includes build identity and timestamps, no personal URIs. Tests of parser/report reject missing presentation samples, trace loss and fewer than30 post-warmup changes rather than emitting false pass.
- [x] Define and test timing/report format before implementation. User-action markers carry unique change IDs; preview content draw markers identify matching new content; frame presentation comes from FrameTimeline on the reference device. Record draw-only timing separately when presentation cannot be proven.
- [x] Use an isolated validation applicationId suffix and dynamic fixture-provider authority so physical tests cannot clear an existing editor session or collide with installed test providers. Add non-debuggable profileable variant signed with local debug key for measurement; identify source commit/build type in artifacts. Verify merged manifest and installation, without production signing secrets.
- [x] Add bounded asynchronous trace markers for user-action→preview publication/draw and button→MediaStore publication, plus decode/render/encode/IO phases. Ensure cancellation closes markers and heavy work stays off Main.
- [x] Add reproducible warmup+30preview actions,20import/edit cycles,10exports and resource sampling. Use Perfetto scheduling/frame/memory data plus snapshots of Java/native/graphics/threads/FD after stable caches. Retain original data, do not hide missed target outliers.
- [x] Dry-run harness on emulator to validate artifacts only; physical performance remains not run until actual vivo connection. Expected: parseable complete trace and honest report status; emulator timings never satisfy physical target.

### Task 3: Physical acceptance, release evidence and PR integration

Files: docs/android/v1-validation.md, android/README.md, compact generated evidence/links, CI artifacts.
- [ ] Connect authorized vivo X200 Pro mini through available ADB. Record actual model/API/RAM/GPU/driver rather than infer from product name. Run color reference, full flow, lifecycle and generated-fixture tests on its GPU.
- [ ] Run the profileable measurement and inspect traces. Report30preview p50/p95 and12MP export breakdown/peakmemory;20cycle+10export trends. If missed, trace root cause, document proposal and implement verified targeted optimization without lowering thresholds.
- [ ] Fresh Android-only checkout builds committed assets without Python; publish identifiable debugAPK with SHA256 and artifact link, packaged preset/manifest/license inventory.
- [ ] Reconcile every #9/#15 checklist row as pass/fail/not-run against evidence. One whole-branch review; one tested Important/Critical fix pass; minor follow-ups linked.
- [ ] Create dependent #15 PR with Closes#15 and parent link. Merge PR19→PR20→export→validation only after each prerequisite's acceptance and exact-head checks pass. Close/reconcile issues only after merge. Goal completion requires full physical gate; unavailable hardware is not an emulator waiver.
