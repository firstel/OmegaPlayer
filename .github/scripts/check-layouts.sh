#!/usr/bin/env bash
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
bash gradlew :app:connectedDebugAndroidTest --stacktrace
layout_test_status=$?
mkdir -p app/build/reports/layout-captures
adb pull /sdcard/Pictures/OmegaPlayerLayout/. app/build/reports/layout-captures/ || true
exit "$layout_test_status"
