import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from verify_android_process_restore import validate_ready, validate_result, require_one_test, decode_device_json


def ready():
    return {"schemaVersion": 1, "runId": "run-123", "package":
            "io.github.haaaappyday.filmeditor.validation", "preparedPid": 123,
            "readyForKill": True}


def result():
    return dict(ready(), restoredPid=456, sourcePreserved=True, paramsRestored=True,
                pendingRemoved=True, publishedPreserved=True, noDuplicateExport=True,
                previewRestored=True, noTransientSuccess=True)


def test_requires_live_prepared_process_and_matching_run():
    validate_ready(ready(), "run-123", "123")
    for patch, pid in [({"runId": "old-run"}, "123"), ({}, "456"),
                       ({"package": "io.github.haaaappyday.filmeditor"}, "123"),
                       ({"readyForKill": False}, "123"), ({}, "")]:
        with pytest.raises(ValueError):
            validate_ready(dict(ready(), **patch), "run-123", pid)


def test_requires_new_process_and_every_recovery_invariant():
    validate_result(result(), "run-123", 123)
    for key in ("sourcePreserved", "paramsRestored", "pendingRemoved", "publishedPreserved",
                "noDuplicateExport", "previewRestored", "noTransientSuccess"):
        for value in (False, None, "true", 1):
            with pytest.raises(ValueError):
                validate_result(dict(result(), **{key: value}), "run-123", 123)
    for patch in ({"restoredPid": 123}, {"preparedPid": 789}, {"runId": "old-run"}):
        with pytest.raises(ValueError):
            validate_result(dict(result(), **patch), "run-123", 123)


@pytest.mark.parametrize("output", ["OK (0 tests)", "OK (2 tests)", "INSTRUMENTATION_FAILED",
                                    "FAILURES!!!\nOK (1 test)", "Process crashed.\nOK (1 test)", ""])
def test_instrumentation_exit_zero_is_insufficient(output):
    with pytest.raises(ValueError):
        require_one_test(output)


def test_accepts_one_completed_phase():
    require_one_test("Time: 1.27\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n")


def test_ready_poll_handles_absent_file_and_empty_adb_output():
    assert decode_device_json(1, "") is None
    assert decode_device_json(0, "") is None
    assert decode_device_json(0, " \n") is None
    assert decode_device_json(0, '{"readyForKill": true}') == {"readyForKill": True}
    with pytest.raises(ValueError):
        decode_device_json(0, "malformed nonempty result")
