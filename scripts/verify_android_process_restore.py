"""Kill a live prepared app, then verify recovery in a different process.

Requires installed validation + validationAndroidTest APKs. Uses only generated
fixtures in the isolated package; never selects a device implicitly or clears app data.
"""

import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

PACKAGE = "io.github.haaaappyday.filmeditor.validation"
PHASES = "io.github.haaaappyday.filmeditor.e2e.ProcessRestorePhases"
INVARIANTS = ("sourcePreserved", "paramsRestored", "pendingRemoved", "publishedPreserved",
              "noDuplicateExport", "previewRestored", "noTransientSuccess")


def _identity(data, run_id):
    if data.get("schemaVersion") != 1 or data.get("package") != PACKAGE or data.get("runId") != run_id:
        raise ValueError("Unexpected validation package, schema or stale run identity")
    pid = data.get("preparedPid")
    if type(pid) is not int or pid <= 0:
        raise ValueError("Missing prepared process identity")


def validate_ready(data, run_id, live_pid):
    _identity(data, run_id)
    if data.get("readyForKill") is not True or str(data["preparedPid"]) != live_pid.strip():
        raise ValueError("The prepared process must still be alive when force-stop is issued")


def validate_result(data, run_id, killed_pid):
    _identity(data, run_id)
    restored = data.get("restoredPid")
    if data["preparedPid"] != killed_pid or type(restored) is not int or restored <= 0 or restored == killed_pid:
        raise ValueError("Restoration must run in a different process after the verified kill")
    if any(data.get(key) is not True for key in INVARIANTS):
        raise ValueError("Missing or failed recovery invariant")


def require_one_test(output):
    if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE) or any(
        marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed.")
    ):
        raise ValueError("Instrumentation phase did not complete exactly one passing test")


def run(serial, out):
    out.mkdir(parents=True, exist_ok=True)
    run_id = uuid.uuid4().hex
    adb = ["adb", "-s", serial]

    def command(*args, check=True, timeout=30):
        return subprocess.run(adb + list(args), text=True, capture_output=True, check=check, timeout=timeout)

    def phase(name):
        return ["shell", "am", "instrument", "-w", "-r", "-e", "class", f"{PHASES}#{name}",
                "-e", "probeMode", name, "-e", "probeRunId", run_id,
                f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"]

    def read_json(name):
        result = command("exec-out", "run-as", PACKAGE, "cat", f"files/{name}", check=False)
        return json.loads(result.stdout) if result.returncode == 0 else None

    if command("get-state").stdout.strip() != "device":
        raise ValueError("Explicit serial is not a connected, authorized device")
    prepare = None
    failed = False
    try:
        with (out / "prepare.log").open("w") as log:
            prepare = subprocess.Popen(adb + phase("prepare"), stdout=log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 120
            while True:
                if prepare.poll() is not None:
                    raise RuntimeError("Prepare terminated before host force-stop; inspect prepare.log")
                ready = read_json("process-probe-ready.json")
                if ready and ready.get("runId") == run_id:
                    break
                if time.monotonic() >= deadline:
                    raise TimeoutError("Timed out waiting for durable state and live prepared process")
                time.sleep(.25)
            pid = command("shell", "pidof", PACKAGE).stdout.strip()
            validate_ready(ready, run_id, pid)
            command("shell", "am", "force-stop", PACKAGE)
            if command("shell", "pidof", PACKAGE, check=False).stdout.strip():
                raise RuntimeError("App process survived force-stop")
            prepare.wait(timeout=30)
        verified = command(*phase("verify"), timeout=120)
        (out / "verify.log").write_text(verified.stdout + verified.stderr)
        require_one_test(verified.stdout)
        result = read_json("process-probe-result.json")
        if result is None:
            raise ValueError("Verification did not produce a report")
        validate_result(result, run_id, int(pid))
        result["hostForceStopVerified"] = True
        (out / "process-restoration.json").write_text(json.dumps(result, indent=2) + "\n")
    except BaseException:
        failed = True
        raise
    finally:
        # End only the explicitly selected validation process; cleanup itself
        # checks the run ID and deletes only its tracked generated fixtures.
        command("shell", "am", "force-stop", PACKAGE, check=False)
        if prepare is not None and prepare.poll() is None:
            prepare.terminate()
            prepare.wait(timeout=10)
        cleanup = command(*phase("cleanup"), check=False, timeout=60)
        (out / "cleanup.log").write_text(cleanup.stdout + cleanup.stderr)
        if not failed:
            cleanup.check_returncode()
            require_one_test(cleanup.stdout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit adb device serial")
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    run(args.serial, args.out)


if __name__ == "__main__":
    main()
