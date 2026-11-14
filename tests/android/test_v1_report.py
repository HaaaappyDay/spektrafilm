import copy
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from android_v1_report import preview_report


def evidence():
    return {
        "device": {"physical": True, "profileable": True, "debuggable": False},
        "trace": {"errorCount": 0, "lostEvents": 0},
        "warmupChanges": 5,
        "previewSamples": [dict(changeId=i + 1, actionNs=i * 1_000_000_000,
                                drawNs=i * 1_000_000_000 + 80_000_000,
                                presentNs=i * 1_000_000_000 + 100_000_000,
                                surfaceFrameToken=100+i, displayFrameToken=200+i,
                                appFrameId=300+i, displayFrameId=400+i,
                                flowVerified=True, width=1600, height=1200)
                           for i in range(30)],
    }


def test_reports_all_30_action_to_display_samples():
    report = preview_report(evidence())
    assert report["status"] == "pass"
    assert report["physicalAccepted"] is True
    assert report["p50Ms"] == report["p95Ms"] == 100
    assert report["drawOnlyMs"] == [80] * 30
    assert report["latenciesMs"] == [100] * 30


@pytest.mark.parametrize("field,value", [("presentNs", None), ("flowVerified", False),
    ("surfaceFrameToken", None), ("displayFrameToken", None), ("appFrameId", None),
    ("surfaceFrameToken", 0), ("displayFrameToken", 0),
    ("displayFrameId", None), ("width", 800), ("presentNs", -1), ("actionNs", float("nan"))])
def test_missing_or_invalid_presentation_proof_never_becomes_a_pass(field, value):
    data = evidence()
    data["previewSamples"][7][field] = value
    report = preview_report(data)
    assert report["status"] == "incomplete"
    assert report["physicalAccepted"] is False
    assert report["p95Ms"] is None
    assert report["reasons"]


def test_draw_completion_is_not_presentation_and_slow_samples_are_not_dropped():
    data = evidence()
    for sample in data["previewSamples"][-3:]:
        sample["presentNs"] = sample["actionNs"] + 350_000_000
    report = preview_report(data)
    assert report["status"] == "fail"
    assert report["p95Ms"] == 350
    assert report["maxMs"] == 350
    assert len(report["latenciesMs"]) == 30


@pytest.mark.parametrize("mutation", [
    lambda d: d["trace"].update(errorCount=1),
    lambda d: d["trace"].update(lostEvents=1),
    lambda d: d["trace"].pop("lostEvents"),
    lambda d: d.update(warmupChanges=0),
    lambda d: d["previewSamples"].pop(),
    lambda d: d["previewSamples"][3].update(changeId=1),
])
def test_incomplete_or_lost_trace_cannot_pass(mutation):
    data = evidence()
    mutation(data)
    assert preview_report(data)["status"] == "incomplete"


def test_emulator_and_debug_builds_never_satisfy_physical_acceptance():
    data = evidence()
    data["device"]["physical"] = False
    result = preview_report(data)
    assert result["status"] == "emulator-only"
    assert result["physicalAccepted"] is False
    assert result["p95Ms"] == 100
    for key, value in (("debuggable", True), ("profileable", False)):
        other = copy.deepcopy(evidence())
        other["device"][key] = value
        assert preview_report(other)["status"] == "incomplete"
