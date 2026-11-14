"""Conservative acceptance reporting for measured Android v1 traces."""

import math
import statistics


def resource_report(evidence):
    """Expose post-cache trends; snapshots alone cannot prove no retained objects."""
    reasons = []
    device = evidence.get("device", {})
    if device.get("profileable") is not True or device.get("debuggable") is not False:
        reasons.append("A non-debuggable profileable build is required")
    if type(device.get("physical")) is not bool:
        reasons.append("Device type is unknown")
    warmed = evidence.get("warmedPresets", [])
    if len(warmed) != 3 or set(warmed) != {"kodak_portra_400", "kodak_gold_200", "kodak_ektar_100"}:
        reasons.append("All three preset caches must be warm before baseline")
    samples = evidence.get("resourceSamples", [])
    if len(samples) != 31:
        reasons.append("Baseline, twenty import/edit cycles and ten exports are required")
    keys = ("javaBytes", "nativeBytes", "graphicsPssBytes", "threads", "fileDescriptors")
    for i, sample in enumerate(samples):
        phase = "baseline" if i == 0 else "import" if i <= 20 else "export"
        if type(sample.get("index")) is not int or sample["index"] != i or sample.get("phase") != phase:
            reasons.append(f"Resource sample {i} has an invalid sequence or phase")
        for key in keys:
            value = sample.get(key)
            if type(value) is not int or value < 0:
                reasons.append(f"Resource sample {i} has unknown {key}")
    metrics = {}
    if not reasons:
        xs = list(range(len(samples)))
        for key in keys:
            ys = [sample[key] for sample in samples]
            metrics[key] = dict(start=ys[0], end=ys[-1], delta=ys[-1] - ys[0],
                                minimum=min(ys), maximum=max(ys),
                                slopePerCycle=statistics.linear_regression(xs, ys).slope)
    status = "incomplete" if reasons else "emulator-only" if not device["physical"] else "review-required"
    return dict(status=status, physicalAccepted=False, reasons=reasons, samples=samples,
                metrics=metrics, retentionReviewRequired=True)


def export_report(evidence):
    """Keep every export, and distinguish sampled memory from a true peak."""
    reasons = []
    device = evidence.get("device", {})
    health = evidence.get("trace", {})
    for name in ("errorCount", "lostEvents"):
        if type(health.get(name)) is not int or health[name] != 0:
            reasons.append(f"Trace {name} must be known and zero")
    if device.get("profileable") is not True or device.get("debuggable") is not False:
        reasons.append("A non-debuggable profileable build is required")
    if type(device.get("physical")) is not bool:
        reasons.append("Device type is unknown")
    samples = evidence.get("exportSamples", [])
    if len(samples) != 10:
        reasons.append("Exactly ten 12MP exports are required")
    ids, latencies, results = set(), [], []
    for index, sample in enumerate(samples):
        valid = True
        export_id = sample.get("exportId")
        if type(export_id) is not int or export_id <= 0 or export_id in ids:
            valid = False
        else:
            ids.add(export_id)
        action, published, completed = (sample.get(key) for key in ("actionNs", "publishedNs", "completedNs"))
        timing_valid = all(type(value) is int for value in (action, published, completed))
        timing_valid = timing_valid and 0 <= action <= published <= completed
        latencies.append((published - action) / 1e6 if timing_valid else None)
        valid &= timing_valid and sample.get("published") is True and sample.get("success") is True
        sw, sh, ow, oh = (sample.get(key) for key in ("sourceWidth", "sourceHeight", "outputWidth", "outputHeight"))
        dimensions_valid = all(type(value) is int and value > 0 for value in (sw, sh, ow, oh))
        if dimensions_valid:
            # Generated 12MP fixture must exercise the full-resolution path.
            dimensions_valid = sorted((sw, sh)) == [3000, 4000] and (ow, oh) == (sw, sh)
        valid &= dimensions_valid
        stages_ms = {}
        previous = action
        for name in ("decode", "render", "encode", "io"):
            stage = sample.get("stages", {}).get(name, {})
            start, end = stage.get("startNs"), stage.get("endNs")
            stage_valid = timing_valid and type(start) is int and type(end) is int and type(previous) is int
            stage_valid = stage_valid and previous <= start <= end <= completed
            if name == "io":
                stage_valid = stage_valid and start <= published <= end
            stages_ms[name] = (end - start) / 1e6 if stage_valid else None
            valid &= stage_valid
            previous = end
        memory = sample.get("peakMemory", {})
        for name in ("javaBytes", "nativeBytes", "graphicsPssBytes"):
            value = memory.get(name)
            valid &= type(value) is int and value >= 0
        count, interval = memory.get("sampleCount"), memory.get("intervalMs")
        valid &= type(count) is int and count >= 2
        valid &= type(interval) is int and interval > 0
        results.append(dict(exportId=export_id, stagesMs=stages_ms, sampledPeakMemory=dict(memory)))
        if not valid:
            reasons.append(f"Export {index + 1} lacks complete 12MP publication, stage, or sampled-memory evidence")
    complete = not reasons
    maximum = max(latencies) if complete else None
    within_target = maximum <= 10000 if maximum is not None else None
    status = ("incomplete" if not complete else "emulator-only" if not device["physical"]
              else "pass" if within_target else "fail")
    return dict(status=status, physicalAccepted=status == "pass", reasons=reasons,
                maxMs=maximum, targetMaxMs=10000, withinTarget=within_target,
                latenciesMs=latencies, sampleCount=len(samples), samples=results,
                memoryIsSampledLowerBound=True)


def preview_report(evidence):
    """Accept only 30 complete content→SurfaceFlinger flow matches after warmup.

    The trace extractor supplies presentation timestamps from the linked display
    frame, never from the application's draw or GPU-completion timestamp.
    Keep all samples, including misses. p95 uses the nearest-rank definition.
    """
    reasons = []
    device = evidence.get("device", {})
    health = evidence.get("trace", {})
    for name in ("errorCount", "lostEvents"):
        if type(health.get(name)) is not int or health[name] != 0:
            reasons.append(f"Trace {name} must be known and zero")
    if device.get("profileable") is not True or device.get("debuggable") is not False:
        reasons.append("A non-debuggable profileable build is required")
    if type(device.get("physical")) is not bool:
        reasons.append("Device type is unknown")
    warmup = evidence.get("warmupChanges")
    if type(warmup) is not int or warmup < 5:
        reasons.append("Five completed warmup changes are required")
    samples = evidence.get("previewSamples", [])
    if len(samples) != 30:
        reasons.append("Exactly 30 post-warmup preview changes are required")
    ids = set()
    latencies = []
    draw_latencies = []
    for index, sample in enumerate(samples):
        valid = True
        change_id = sample.get("changeId")
        if type(change_id) is not int or change_id <= 0 or change_id in ids:
            valid = False
        else:
            ids.add(change_id)
        action, draw, present = (sample.get(key) for key in ("actionNs", "drawNs", "presentNs"))
        draw_valid = type(action) is int and type(draw) is int and 0 <= action <= draw
        draw_latencies.append((draw - action) / 1e6 if draw_valid else None)
        timing_valid = draw_valid and type(present) is int and present >= draw
        latencies.append((present - action) / 1e6 if timing_valid else None)
        valid &= timing_valid
        for key in ("surfaceFrameToken", "displayFrameToken", "appFrameId", "displayFrameId"):
            value = sample.get(key)
            valid &= type(value) is int and value >= (1 if key.endswith("Token") else 0)
        valid &= sample.get("flowVerified") is True
        width, height = sample.get("width"), sample.get("height")
        valid &= type(width) is int and type(height) is int and min(width, height) > 0 and max(width, height) == 1600
        if not valid:
            reasons.append(f"Sample {index + 1} lacks unique 1600px content and complete presentation proof")
    complete = not reasons
    ordered = sorted(latencies) if complete else []
    p95 = ordered[math.ceil(.95 * len(ordered)) - 1] if ordered else None
    within_target = p95 <= 200 if p95 is not None else None
    status = ("incomplete" if not complete else "emulator-only" if not device["physical"]
              else "pass" if within_target else "fail")
    return dict(status=status, physicalAccepted=status == "pass", reasons=reasons,
                p50Ms=statistics.median(ordered) if ordered else None,
                p95Ms=p95, maxMs=max(ordered) if ordered else None,
                targetP95Ms=200, withinTarget=within_target, p95Method="nearest-rank",
                latenciesMs=latencies, drawOnlyMs=draw_latencies, sampleCount=len(samples))
