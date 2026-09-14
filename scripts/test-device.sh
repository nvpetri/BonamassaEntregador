#!/usr/bin/env bash
set +e
bash gradlew :app:connectedDebugAndroidTest \
  -PbonamassaApiUrl=http://10.0.2.2:3001 \
  -PbonamassaStoreSlug=bonamassa \
  -Pandroid.testInstrumentationRunnerArguments.bonamassaIntegration=true \
  --stacktrace
test_exit=$?
mkdir -p device-screenshots
adb pull /data/local/tmp/bonamassa-screenshots/. device-screenshots/ || true
exit "$test_exit"
