import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from android_v1_report import resource_report


def evidence():
    return dict(device=dict(physical=True, profileable=True, debuggable=False),
                warmedPresets=["kodak_portra_400", "kodak_gold_200", "kodak_ektar_100"],
                resourceSamples=[dict(index=i, phase="baseline" if i == 0 else "import" if i <= 20 else "export",
                    javaBytes=1_000_000 + i * 1024, nativeBytes=2_000_000, graphicsPssBytes=3_000_000,
                    threads=10, fileDescriptors=40) for i in range(31)])


def test_preserves_all_trends_and_requires_retention_review_instead_of_inventing_a_limit():
    data = evidence()
    report = resource_report(data)
    assert report["status"] == "review-required"
    assert report["physicalAccepted"] is False
    assert report["samples"] == data["resourceSamples"]
    assert report["metrics"]["javaBytes"]["delta"] == 30 * 1024
    assert report["metrics"]["javaBytes"]["slopePerCycle"] == 1024
    assert report["metrics"]["fileDescriptors"]["delta"] == 0


@pytest.mark.parametrize("mutation", [
    lambda d: d["resourceSamples"].pop(),
    lambda d: d["resourceSamples"][12].pop("graphicsPssBytes"),
    lambda d: d["resourceSamples"][12].update(threads=None),
    lambda d: d["resourceSamples"][12].update(index=11),
    lambda d: d["resourceSamples"][12].update(phase="export"),
    lambda d: d["warmedPresets"].pop(),
])
def test_missing_cycles_warm_caches_or_resource_counters_remain_incomplete(mutation):
    data = evidence(); mutation(data)
    report = resource_report(data)
    assert report["status"] == "incomplete" and report["physicalAccepted"] is False
    assert report["reasons"]


def test_emulator_trends_do_not_count_as_physical_acceptance():
    data = evidence(); data["device"]["physical"] = False
    assert resource_report(data)["status"] == "emulator-only"
