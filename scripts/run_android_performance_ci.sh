#!/usr/bin/env bash
# Called inside the already-running API36 emulator job (cwd: android).
set -euo pipefail
source_commit="$1"
perfetto_tools="$RUNNER_TEMP/perfetto-tools"
mkdir -p "$perfetto_tools"
for tool in record_android_trace trace_processor; do
  curl --fail --location --retry 2 "https://raw.githubusercontent.com/google/perfetto/v59.1/tools/$tool" -o "$perfetto_tools/$tool"
done
chmod +x "$perfetto_tools/trace_processor"
"$perfetto_tools/trace_processor" --version
./gradlew :app:assembleProfileable :app:assembleProfileableAndroidTest -PvalidationBuildType=profileable -PsourceCommit="$source_commit"
python3 ../scripts/record_android_v1.py --serial emulator-5554 --source-commit "$source_commit" --out app/build/reports/performance --recorder "$perfetto_tools/record_android_trace" --trace-processor "$perfetto_tools/trace_processor"
