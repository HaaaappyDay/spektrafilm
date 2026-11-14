"""Set actual AVD hardware keys before the emulator starts, and retain evidence."""
import os
from pathlib import Path

config = Path(os.environ["ANDROID_AVD_HOME"]) / "test.avd/config.ini"
keys = {"hw.ramSize", "hw.heapSize", "vm.heapSize"}
lines = [line for line in config.read_text().splitlines() if line.partition("=")[0] not in keys]
# AVD properties use integer MiB and vm.heapSize, not hw.heapSize.
config.write_text("\n".join(lines + ["hw.ramSize=2048", "vm.heapSize=128"]) + "\n")
Path(os.environ["RUNNER_TEMP"], "android-avd-config.ini").write_text(config.read_text())
