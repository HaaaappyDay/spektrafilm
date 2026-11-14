"""Conservative acceptance reporting for measured Android v1 traces."""

import math
import statistics


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
