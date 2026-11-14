import copy
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from extract_android_v1 import assemble_evidence


def fixture():
    workload = dict(completed=True, cleanupVerified=True, device=dict(profileable=True, debuggable=False, pid=100),
        warmupChanges=5, previewActions=[dict(inputId=1, changeId=2, width=1600, height=1200)],
        exportActions=[dict(inputId=3, exportId=4, sourceWidth=4000, sourceHeight=3000,
            outputWidth=4000, outputHeight=3000, success=True, sampleIntervalMs=100,
            memorySamples=[dict(elapsedNs=i, javaBytes=20+i, nativeBytes=40+i, graphicsPssBytes=60+i) for i in (1, 2)])])
    markers = [dict(name=name, ts=start, dur=duration) for name, start, duration in [
        ("FilmInputPreview:1", 100, 600), ("FilmPreview:2", 120, 100),
        ("FilmInputExport:3", 1000, 1100), ("FilmExport:4", 1010, 1000),
        ("FilmExportDecode:4", 1020, 100), ("FilmExportRender:4", 1130, 300),
        ("FilmExportEncode:4", 1440, 200), ("FilmExportIo:4", 1650, 300),
        ("FilmExportPublished:4", 1900, 0), ("FilmExportOutcome:4:success", 2005, 0),
    ]]
    frames = [dict(changeId=2, handlerNs=120, drawNs=250, presentNs=400, surfaceFrameToken=11,
                   displayFrameToken=12, appFrameId=13, displayFrameId=14, flowVerified=1)]
    return workload, frames, markers, dict(errorCount=0, lostEvents=0)


def extract(data=None):
    return assemble_evidence(*(data or fixture()), physical=True)


def test_measures_input_dispatch_not_vm_start_and_uses_real_publication():
    result = extract()
    assert result["previewSamples"][0]["actionNs"] == 100
    assert result["previewSamples"][0]["presentNs"] == 400
    sample = result["exportSamples"][0]
    assert sample["actionNs"] == 1000 and sample["publishedNs"] == 1900 and sample["completedNs"] == 2010
    assert sample["published"] is True and sample["success"] is True
    assert sample["stages"]["render"] == dict(startNs=1130, endNs=1430)
    assert sample["peakMemory"]["nativeBytes"] == 42


def test_uses_earliest_proven_presentation_and_retains_missing_samples():
    data = fixture()
    later = copy.deepcopy(data[1][0]); later.update(drawNs=500, presentNs=600)
    data[1].insert(0, later)
    assert extract(data)["previewSamples"][0]["presentNs"] == 400
    data[1].clear()
    sample = extract(data)["previewSamples"][0]
    assert sample["presentNs"] is None and sample["flowVerified"] is False


def test_reused_or_unclosed_input_marker_cannot_supply_latency():
    data = fixture(); data[2].append(copy.deepcopy(data[2][0]))
    assert extract(data)["previewSamples"][0]["actionNs"] is None
    data = fixture(); data[2][0]["dur"] = -1
    assert extract(data)["previewSamples"][0]["actionNs"] is None


def test_real_presentation_after_pacing_callbacks_is_still_counted():
    data = fixture()
    # The input marker ends at 700 after test pacing callbacks. It is not a
    # presentation fence; real SF evidence must retain later frames/outliers.
    data[1][0].update(drawNs=650, presentNs=750)
    assert extract(data)["previewSamples"][0]["presentNs"] == 750
    data[1][0].update(drawNs=750, presentNs=800)
    assert extract(data)["previewSamples"][0]["presentNs"] == 800


def test_cross_operation_frame_or_early_publication_is_rejected():
    data = fixture(); data[1][0]["handlerNs"] = 121
    assert extract(data)["previewSamples"][0]["presentNs"] is None
    data = fixture(); data[2][-2]["ts"] = 900
    sample = extract(data)["exportSamples"][0]
    assert sample["publishedNs"] is None and sample["published"] is False


def test_provider_marker_without_success_does_not_become_successful_export():
    data = fixture(); data[2].pop()
    assert extract(data)["exportSamples"][0]["success"] is False


def test_unknown_memory_counter_is_not_converted_to_zero_or_ignored():
    data = fixture(); data[0]["exportActions"][0]["memorySamples"][0].pop("graphicsPssBytes")
    assert extract(data)["exportSamples"][0]["peakMemory"]["graphicsPssBytes"] is None


def test_duplicate_scenario_input_ids_are_reported():
    data = fixture(); data[0]["exportActions"][0]["inputId"] = 1
    assert extract(data)["validationErrors"]


def test_incomplete_workload_and_cleanup_are_not_accepted():
    data = fixture(); data[0].update(completed=False, cleanupVerified=False)
    assert len(extract(data)["validationErrors"]) == 2


def test_unknown_or_invalid_instrumented_process_identity_is_incomplete():
    for pid in (None, 0, True, "100; DROP TABLE process"):
        data = fixture(); data[0]["device"]["pid"] = pid
        assert extract(data)["validationErrors"]
