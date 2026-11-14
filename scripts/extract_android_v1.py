"""Join generated workload identities to app and SurfaceFlinger trace evidence."""

import csv
import io
import json
import subprocess
import tempfile
from collections import defaultdict
from pathlib import Path

from android_v1_report import export_report, preview_report, resource_report


APP = "io.github.haaaappyday.filmeditor.validation"
MARKERS_SQL = f"""
SELECT s.name, s.ts, s.dur FROM slice s
LEFT JOIN process_track pt ON pt.id = s.track_id
LEFT JOIN thread_track tt ON tt.id = s.track_id
LEFT JOIN thread t ON t.utid = tt.utid
JOIN process p ON p.upid = COALESCE(pt.upid, t.upid)
WHERE p.name = '{APP}' AND s.name GLOB 'Film*'
ORDER BY s.ts;
"""
HEALTH_SQL = """
SELECT COALESCE(SUM(CASE WHEN severity = 'error' THEN ABS(value) ELSE 0 END), 0) AS errorCount,
       COALESCE(SUM(CASE WHEN severity = 'data_loss' THEN ABS(value) ELSE 0 END), 0) AS lostEvents
FROM stats;
"""


def assemble_evidence(workload, frames, markers, health, *, physical):
    by_name = defaultdict(list)
    for marker in markers:
        by_name[marker["name"]].append(marker)

    def one(name):
        found = by_name[name]
        if len(found) != 1:
            return None
        ts, dur = found[0].get("ts"), found[0].get("dur")
        if type(ts) is not int or type(dur) is not int or min(ts, dur) < 0:
            return None
        return dict(startNs=ts, endNs=ts + dur)

    def enclosed(child, parent):
        return bool(child and parent and parent["startNs"] <= child["startNs"] <= child["endNs"] <= parent["endNs"])

    errors = []
    for key in ("completed", "cleanupVerified"):
        if workload.get(key) is not True:
            errors.append(f"Workload {key} is not proven")
    seen_inputs = set()
    for action in workload.get("previewActions", []) + workload.get("exportActions", []):
        value = action.get("inputId")
        if type(value) is not int or value <= 0 or value in seen_inputs:
            errors.append("Scenario input IDs must be unique positive integers")
        else:
            seen_inputs.add(value)
    previews = []
    for action in workload.get("previewActions", []):
        sample = dict(action)
        input_span = one(f"FilmInputPreview:{action.get('inputId')}")
        operation = one(f"FilmPreview:{action.get('changeId')}")
        valid = enclosed(operation, input_span)
        candidates = [row for row in frames if valid and row.get("changeId") == action.get("changeId")
                      and row.get("handlerNs") == operation["startNs"]
                      and type(row.get("drawNs")) is int
                      and operation["endNs"] <= row["drawNs"] <= input_span["endNs"]]
        presented = [row for row in candidates if row.get("flowVerified") == 1
                     and type(row.get("presentNs")) is int
                     and row["drawNs"] <= row["presentNs"] <= input_span["endNs"]]
        row = min(presented, key=lambda r: r["presentNs"]) if presented else (
            min(candidates, key=lambda r: r["drawNs"]) if candidates else {})
        for key in ("drawNs", "presentNs", "surfaceFrameToken", "displayFrameToken", "appFrameId", "displayFrameId"):
            sample[key] = row.get(key) if key == "drawNs" or presented else None
        sample.update(actionNs=input_span["startNs"] if valid else None, flowVerified=bool(presented))
        previews.append(sample)
    exports = []
    for action in workload.get("exportActions", []):
        sample = dict(action)
        export_id = action.get("exportId")
        input_span = one(f"FilmInputExport:{action.get('inputId')}")
        operation = one(f"FilmExport:{export_id}")
        valid = enclosed(operation, input_span)
        published = one(f"FilmExportPublished:{export_id}")
        success = one(f"FilmExportOutcome:{export_id}:success")
        stages = {name.lower(): one(f"FilmExport{name}:{export_id}") for name in ("Decode", "Render", "Encode", "Io")}
        stages = {name: stage if enclosed(stage, operation) else {} for name, stage in stages.items()}
        publication_valid = valid and enclosed(published, stages["io"])
        sample.update(actionNs=input_span["startNs"] if valid else None,
                      completedNs=operation["endNs"] if valid else None,
                      publishedNs=published["startNs"] if publication_valid else None,
                      published=bool(publication_valid),
                      success=bool(valid and enclosed(success, operation) and action.get("success") is True),
                      stages=stages)
        memory = action.get("memorySamples", [])
        peak = dict(sampleCount=len(memory), intervalMs=action.get("sampleIntervalMs"))
        stamps = [row.get("elapsedNs") for row in memory]
        ordered = all(type(value) is int and value >= 0 for value in stamps)
        ordered = ordered and all(a < b for a, b in zip(stamps, stamps[1:]))
        for key in ("javaBytes", "nativeBytes", "graphicsPssBytes"):
            values = [row.get(key) for row in memory]
            peak[key] = max(values) if values and ordered and all(type(v) is int and v >= 0 for v in values) else None
        sample["peakMemory"] = peak
        exports.append(sample)
    return dict(device=dict(workload.get("device", {}), physical=physical), trace=health,
                warmupChanges=workload.get("warmupChanges"), warmupExports=workload.get("warmupExports"),
                previewSamples=previews, exportSamples=exports,
                resourceSamples=workload.get("resourceSamples", []), warmedPresets=workload.get("warmedPresets", []),
                validationErrors=errors)


def query(processor, trace, sql):
    # The pinned helper currently ships v58.2, whose query command has no --format.
    # Legacy -q emits CSV in both that binary and v59.1 (including agent mode).
    with tempfile.NamedTemporaryFile(mode="w", suffix=".sql") as query_file:
        query_file.write(sql)
        query_file.flush()
        completed = subprocess.run([str(processor), "-q", query_file.name, str(trace)],
                                   check=True, capture_output=True, text=True, timeout=120)
    def value(item):
        if item in ("[NULL]", "NULL", ""):
            return None
        try:
            return int(item)
        except ValueError:
            return item
    return [{key: value(item) for key, item in row.items()} for row in csv.DictReader(io.StringIO(completed.stdout))]


def extract(processor, trace, workload, out, *, physical):
    """Save raw query rows as well as reports so every join is inspectable."""
    out = Path(out)
    frames = query(processor, trace, Path(__file__).with_name("android_v1_frames.sql").read_text())
    markers = query(processor, trace, MARKERS_SQL)
    health_rows = query(processor, trace, HEALTH_SQL)
    if len(health_rows) != 1:
        raise ValueError("Trace health query returned no unique result")
    for name, rows in (("frames", frames), ("markers", markers), ("trace-health", health_rows)):
        (out / f"{name}.json").write_text(json.dumps(rows, indent=2) + "\n")
    evidence = assemble_evidence(workload, frames, markers, health_rows[0], physical=physical)
    (out / "performance-evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
    report = dict(preview=preview_report(evidence), export=export_report(evidence), resources=resource_report(evidence))
    for section in report.values():
        if evidence["validationErrors"]:
            section.update(status="incomplete", physicalAccepted=False)
            section["reasons"].extend(evidence["validationErrors"])
    (out / "performance-report.json").write_text(json.dumps(report, indent=2) + "\n")
    return report
