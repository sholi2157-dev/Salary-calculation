#!/usr/bin/env bash
set -euo pipefail
pkg=com.aistudio.worktracker.qztvdw.distribution
# Runner must provide a fresh disposable emulator. Never uninstall/reset as a workaround.
if adb shell pm list packages | grep -Fx "package:$pkg"; then echo 'Expected fresh emulator; refusing to overwrite existing data'; exit 1; fi
mkdir -p release-evidence
collect_ui_evidence() {
  for name in rc2-home rc2-currency rc2-group rc2-history rc2-settings rc2-keyboard; do
    adb pull "/sdcard/Android/data/$pkg/files/$name.png" "release-evidence/$name.png" >/dev/null 2>&1 || true
  done
}
trap collect_ui_evidence EXIT
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
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS
run_stage seed
adb shell am start -W -n "$pkg/com.example.MainActivity" | tee release-evidence/offline-start.txt
grep -F "Status: ok" release-evidence/offline-start.txt
run_stage restart
adb push candidate-b.apk "/sdcard/Android/data/$pkg/files/candidate-b.apk"
run_stage updater
adb install -r candidate-b.apk | tee release-evidence/install-update.txt
run_stage verify
for evidence_file in restart-before.json updater-before.json verify-before.json verify-after-edit.json; do
  adb pull "/sdcard/Android/data/$pkg/files/$evidence_file" "release-evidence/$evidence_file"
done
cmp release-evidence/restart-before.json release-evidence/verify-before.json
adb shell am start -W -n "$pkg/com.example.MainActivity" | tee release-evidence/offline-start-after-update.txt
grep -F "Status: ok" release-evidence/offline-start-after-update.txt
adb shell am force-stop "$pkg"
adb shell am instrument -w -e class com.example.DistributionUiTest "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee release-evidence/ui.txt
grep -F 'OK (1 test)' release-evidence/ui.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/ui.txt
adb exec-out screencap -p > release-evidence/rc2-screen.png
adb logcat -d -b crash > release-evidence/crash-log.txt
! grep -F "$pkg" release-evidence/crash-log.txt
