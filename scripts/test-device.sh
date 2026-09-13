#!/usr/bin/env bash
set +e
bash gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.bonamassaIntegration=true --stacktrace
test_exit=$?
mkdir -p device-screenshots
adb pull /data/local/tmp/bonamassa-screenshots/. device-screenshots/ || true
exit "$test_exit"
