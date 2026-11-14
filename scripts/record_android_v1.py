#!/usr/bin/env python3
"""Record the isolated generated workload; reports never waive physical gates."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
import uuid

from extract_android_v1 import APP, extract

ROOT = Path(__file__).resolve().parents[1]


def run(argv, **kwargs):
    return subprocess.run([str(value) for value in argv], check=True, text=True,
                          capture_output=True, timeout=kwargs.pop("timeout", 60), **kwargs).stdout.strip()


def sha256(path):
    with Path(path).open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def record(args):
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_commit):
        raise ValueError("--source-commit must be a full Git commit")
    app, test = args.app_apk.resolve(), args.test_apk.resolve()
    for apk, package in ((app, APP), (test, APP + ".test")):
        badging = run([args.aapt2, "dump", "badging", apk])
        if not re.search(r"^package: name='" + re.escape(package) + r"'", badging, re.M):
            raise ValueError(f"APK must use isolated package {package}")
        if package == APP and "application-debuggable" in badging:
            raise ValueError("Measurement APK must not be debuggable")
    adb = ["adb", "-s", args.serial]
    if run(adb + ["get-state"]) != "device":
        raise ValueError("Selected device is not ready")
    properties = {}
    for name in ("ro.kernel.qemu", "ro.boot.qemu", "ro.hardware", "ro.build.fingerprint", "ro.build.version.sdk"):
        properties[name] = run(adb + ["shell", "getprop", name])
    if int(properties["ro.build.version.sdk"]) < 31:
        raise ValueError("Presentation measurement requires Android 12+ FrameTimeline")
    emulated = (args.serial.startswith("emulator-") or any(properties[name] == "1" for name in ("ro.kernel.qemu", "ro.boot.qemu"))
                or properties["ro.hardware"] in ("ranchu", "goldfish")
                or "generic" in properties["ro.build.fingerprint"])
    physical = not emulated if properties["ro.hardware"] and properties["ro.build.fingerprint"] else None
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)  # Never replace a prior run or its raw evidence.
    metadata = dict(sourceCommit=args.source_commit, deviceProperties=properties, physical=physical,
                    appSha256=sha256(app), testApkSha256=sha256(test),
                    recorderSha256=sha256(args.recorder), traceProcessorLauncherSha256=sha256(args.trace_processor),
                    traceProcessorVersion=run([args.trace_processor, "--version"]),
                    traceConfigSha256=sha256(args.config), startedUtc=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    (out / "recording.json").write_text(json.dumps(metadata, indent=2) + "\n")
    run(adb + ["install", "-r", app], timeout=180)
    run(adb + ["install", "-r", test], timeout=180)
    run(adb + ["shell", "input", "keyevent", "KEYCODE_WAKEUP"])
    remote = f"/sdcard/Android/media/{APP}/performance-{uuid.uuid4().hex}"
    trace = out / "system.perfetto-trace"
    log = out / "recorder.log"
    instrument_ok = False
    recorder = None
    try:
        with log.open("w") as output:
            recorder = subprocess.Popen([sys.executable, "-u", str(args.recorder), "--serial", args.serial,
                "-c", str(args.config), "-o", str(trace), "-n"], stdout=output, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 45
            while "Trace started." not in log.read_text():
                if recorder.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError("Perfetto did not become ready; inspect recorder.log")
                time.sleep(.1)
            command = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                "io.github.haaaappyday.filmeditor.e2e.PerformanceScenario#warmPreviewImportsAndExports",
                "-e", "sourceCommit", args.source_commit, "-e", "additionalTestOutputDir", remote,
                APP + ".test/androidx.test.runner.AndroidJUnitRunner"]
            with (out / "instrumentation.log").open("w") as output:
                result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, timeout=840)
            text = (out / "instrumentation.log").read_text()
            instrument_ok = result.returncode == 0 and bool(re.search(r"OK \(1 test\)", text))
    finally:
        if recorder is not None and recorder.poll() is None:
            recorder.send_signal(signal.SIGINT)  # Official helper flushes/pulls only its own trace.
            try:
                recorder.wait(timeout=45)
            except subprocess.TimeoutExpired:
                recorder.send_signal(signal.SIGINT)
                recorder.wait(timeout=15)
        pulled = subprocess.run(adb + ["pull", remote + "/performance-workload.json", str(out / "performance-workload.json")],
                                capture_output=True, text=True, timeout=60)
        (out / "pull.log").write_text(pulled.stdout + pulled.stderr)
        for name in ("performance-accessibility.json", "performance-screen.png"):
            subprocess.run(adb + ["pull", remote + "/" + name, str(out / name)],
                           capture_output=True, text=True, timeout=60)
        # Only this UUID output directory, never the validation app's other files.
        subprocess.run(adb + ["shell", "rm", "-rf", remote], check=True, capture_output=True, timeout=30)
    if not trace.exists() or not (out / "performance-workload.json").exists():
        raise RuntimeError("Recording or workload evidence is missing; inspect retained logs")
    workload = json.loads((out / "performance-workload.json").read_text())
    if workload.get("sourceCommit") != args.source_commit:
        raise ValueError("Measured APK identity does not match the requested source commit")
    report = extract(args.trace_processor, trace, workload, out, physical=physical)
    metadata.update(traceSha256=sha256(trace), instrumentationSucceeded=instrument_ok,
                    finishedUtc=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    (out / "recording.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps({name: result["status"] for name, result in report.items()}))
    return 0 if instrument_ok and all(section["status"] not in ("incomplete", "fail") for section in report.values()) else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--app-apk", type=Path, default=ROOT / "android/app/build/outputs/apk/profileable/app-profileable.apk")
    parser.add_argument("--test-apk", type=Path, default=ROOT / "android/app/build/outputs/apk/androidTest/profileable/app-profileable-androidTest.apk")
    parser.add_argument("--config", type=Path, default=ROOT / "docs/android/v1-perfetto.cfg")
    parser.add_argument("--recorder", type=Path, default=Path.home() / ".cache/perfetto-tools/record_android_trace")
    parser.add_argument("--trace-processor", type=Path, default=Path.home() / ".cache/perfetto-tools/trace_processor")
    sdk = os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", ""))
    parser.add_argument("--aapt2", type=Path, default=Path(sdk) / "build-tools/35.0.0/aapt2")
    args = parser.parse_args()
    try:
        return record(args)
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
