#!/usr/bin/env bash
set -euo pipefail
capture_on_failure() {
  local status=$?
  if (( status != 0 )); then
    mkdir -p app/build/reports/ota-upgrade
    adb pull /data/local/tmp/lanu-comic-objection.png app/build/reports/ota-upgrade/comic-objection.png || true
    adb pull /data/local/tmp/lanu-persistent-objection.png app/build/reports/ota-upgrade/persistent-objection.png || true
    for part in meal motive plan jump; do
      adb pull "/data/local/tmp/lanu-story-$part.png" "app/build/reports/ota-upgrade/story-$part.png" || true
    done
  fi
  return "$status"
}
trap capture_on_failure EXIT
# Controlled CI emulator only. Explicit opt-in avoids resetting a physical phone.
./gradlew -Planu.uiSmoke=true -Planu.versionCode=14 -Planu.versionName=0.8.1-alpha assembleDebug --stacktrace --max-workers=2
cp app/build/outputs/apk/debug/app-debug.apk "$RUNNER_TEMP/lanu-ota-next.apk"
adb push "$RUNNER_TEMP/lanu-ota-next.apk" /data/local/tmp/lanu-ota-next.apk
./gradlew -Planu.uiSmoke=true connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.lanu.allowProgressReset=true -Pandroid.testInstrumentationRunnerArguments.lanu.otaCandidate=/data/local/tmp/lanu-ota-next.apk --stacktrace --max-workers=2
mkdir -p app/build/reports/ota-upgrade
adb pull /data/local/tmp/lanu-comic-objection.png app/build/reports/ota-upgrade/comic-objection.png
adb pull /data/local/tmp/lanu-persistent-objection.png app/build/reports/ota-upgrade/persistent-objection.png
for part in meal motive plan jump; do
  adb pull "/data/local/tmp/lanu-story-$part.png" "app/build/reports/ota-upgrade/story-$part.png"
done
# The connected-test runner removes installed test packages after it finishes.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p app/build/reports/ota-upgrade
run_phase() {
  local method="$1" phase="$2" output="$3"
  adb shell am instrument -w -r -e class "com.lanu.yalan.OtaCompatibilityTest#$method" -e lanu.upgradePhase "$phase" -e lanu.allowProgressReset true -e lanu.expectedVersionCode 14 com.lanu.yalan.test/androidx.test.runner.AndroidJUnitRunner | tee "$output"
  python3 - "$output" <<'PY'
import sys
text=open(sys.argv[1]).read()
assert 'OK (1 test)' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
PY
}
run_phase prepareUpgrade prepare app/build/reports/ota-upgrade/before.txt
adb install -r "$RUNNER_TEMP/lanu-ota-next.apk"
run_phase afterUpgradeReadsProgress verify app/build/reports/ota-upgrade/after.txt
