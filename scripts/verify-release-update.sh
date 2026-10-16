#!/usr/bin/env bash
set -euo pipefail
pkg=com.aistudio.worktracker.qztvdw.distribution
# Runner must provide a fresh disposable emulator. Never uninstall/reset as a workaround.
if adb shell pm list packages | grep -Fx "package:$pkg"; then echo 'Expected fresh emulator; refusing to overwrite existing data'; exit 1; fi
mkdir -p release-evidence
collect_ui_evidence() {
  adb exec-out screencap -p > release-evidence/last-ui.png || true
  for name in v2-home v2-add-hours v2-add-job v2-add-group v2-add-clock v2-history v2-history-selection v2-detail v2-settings v2-feedback v2-clock-keyboard v2-hours-keyboard v2-save-with-ime v2-history-final v2-home-before-live v2-home-timeout v2-live; do
    adb pull "/sdcard/Android/data/$pkg/files/$name.png" "release-evidence/$name.png" >/dev/null 2>&1 || true
  done
}
trap collect_ui_evidence EXIT
adb shell wm size 720x1600
adb shell wm density 320
adb install candidate-a.apk
adb install app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell svc wifi disable
adb shell svc data disable
run_stage() {
  log="release-evidence/${2:-$1}.txt"
  adb shell am force-stop "$pkg"
  adb shell am instrument -w -e class com.example.DistributionUpdateTest -e stage "$1" \
    "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee "$log"
  grep -F 'OK (1 test)' "$log"
  ! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "$log"
}
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS
run_stage seed
adb shell am start -W -n "$pkg/com.example.MainActivity" | tee release-evidence/offline-start.txt
grep -F "Status: ok" release-evidence/offline-start.txt
run_stage restart
adb push candidate-b.apk "/sdcard/Android/data/$pkg/files/candidate-b.apk"
run_stage updater
# The owner's real path: RC16 → the delivered 2.0-rc1 → this candidate, data kept at every step.
if [ -f candidate-rc1.apk ]; then
  adb install -r candidate-rc1.apk | tee release-evidence/install-rc1.txt
  run_stage restart after-rc1
fi
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
adb shell am force-stop "$pkg"
adb shell am instrument -w -e class com.example.CurrencyFeedbackTest "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee release-evidence/currency-feedback.txt
grep -F 'OK (1 test)' release-evidence/currency-feedback.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/currency-feedback.txt
adb shell am force-stop "$pkg"
adb shell am instrument -w -e class com.example.ReportKeyboardTest "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee release-evidence/keyboard-ui.txt
grep -F 'OK (1 test)' release-evidence/keyboard-ui.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/keyboard-ui.txt
adb exec-out screencap -p > release-evidence/rc2-screen.png
adb logcat -d -b crash > release-evidence/crash-log.txt
! grep -E "FATAL EXCEPTION|AndroidRuntime|Process: $pkg" release-evidence/crash-log.txt
