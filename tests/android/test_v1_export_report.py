import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from android_v1_report import export_report


def evidence():
    exports = []
    for i in range(10):
        start = i * 30_000_000_000
        exports.append(dict(exportId=i + 1, actionNs=start, publishedNs=start + 900_000_000,
            completedNs=start + 920_000_000, published=True, success=True,
            sourceWidth=4000, sourceHeight=3000, outputWidth=4000, outputHeight=3000,
            stages={name: dict(startNs=start + a * 1_000_000, endNs=start + b * 1_000_000)
                    for name, a, b in (("decode", 10, 100), ("render", 110, 500),
                                       ("encode", 510, 700), ("io", 710, 910))},
            peakMemory=dict(javaBytes=32_000_000, nativeBytes=96_000_000, graphicsPssBytes=16_000_000,
                            sampleCount=9, intervalMs=100)))
    return dict(device=dict(physical=True, profileable=True, debuggable=False),
                trace=dict(errorCount=0, lostEvents=0), exportSamples=exports)


def test_reports_button_to_publication_all_stage_times_and_sampled_memory():
    report = export_report(evidence())
    assert report["status"] == "pass" and report["physicalAccepted"] is True
    assert report["latenciesMs"] == [900] * 10
    assert report["samples"][0]["stagesMs"] == dict(decode=90, render=390, encode=190, io=200)
    assert report["samples"][0]["sampledPeakMemory"]["nativeBytes"] == 96_000_000
    assert report["memoryIsSampledLowerBound"] is True


@pytest.mark.parametrize("mutation", [
    lambda s: s.update(published=False),
    lambda s: s.update(success=False),
    lambda s: s.update(publishedNs=None),
    lambda s: s.update(outputWidth=4096),  # cannot upscale a 4000px source
    lambda s: s.update(sourceWidth=400),
    lambda s: s["stages"].pop("encode"),
    lambda s: s["stages"]["render"].update(endNs=-1),
    lambda s: s["stages"]["io"].update(endNs=500_000_000),
    lambda s: s["peakMemory"].pop("graphicsPssBytes"),
    lambda s: s["peakMemory"].update(sampleCount=1),
])
def test_incomplete_export_cannot_pass(mutation):
    data = evidence(); mutation(data["exportSamples"][0])
    result = export_report(data)
    assert result["status"] == "incomplete" and result["physicalAccepted"] is False
    assert result["maxMs"] is None


def test_does_not_hide_a_single_export_over_ten_seconds():
    data = evidence(); sample = data["exportSamples"][0]
    sample.update(publishedNs=11_000_000_000, completedNs=11_100_000_000)
    sample["stages"]["io"]["endNs"] = 11_050_000_000
    result = export_report(data)
    assert result["status"] == "fail" and result["maxMs"] == 11000
    assert len(result["latenciesMs"]) == 10


def test_no_emulator_short_run_or_trace_loss_waiver():
    data = evidence(); data["device"]["physical"] = False
    assert export_report(data)["status"] == "emulator-only"
    data = evidence(); data["exportSamples"].pop()
    assert export_report(data)["status"] == "incomplete"
    data = evidence(); data["trace"]["lostEvents"] = 1
    assert export_report(data)["status"] == "incomplete"
