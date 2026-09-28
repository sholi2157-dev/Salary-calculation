#!/usr/bin/env bash
set -euo pipefail
pkg=com.aistudio.worktracker.qztvdw.distribution
# Runner must provide a fresh disposable emulator. Never uninstall/reset as a workaround.
if adb shell pm list packages | grep -Fx "package:$pkg"; then echo 'Expected fresh emulator; refusing to overwrite existing data'; exit 1; fi
mkdir -p release-evidence
adb install candidate-a.apk
adb install app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell svc wifi disable
adb shell svc data disable
run_stage() {
  adb shell am force-stop "$pkg"
  adb shell am instrument -w -e class com.example.DistributionUpdateTest -e stage "$1" \
    "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee "release-evidence/$1.txt"
  grep -F 'OK (1 test)' "release-evidence/$1.txt"
  ! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "release-evidence/$1.txt"
}
run_stage seed
adb shell am start -W -n "$pkg/com.example.MainActivity" | tee release-evidence/offline-start.txt
run_stage restart
adb install -r candidate-b.apk | tee release-evidence/install-update.txt
run_stage verify
adb shell am start -W -n "$pkg/com.example.MainActivity" | tee release-evidence/offline-start-after-update.txt
adb logcat -d -b crash > release-evidence/crash-log.txt
! grep -F "$pkg" release-evidence/crash-log.txt
