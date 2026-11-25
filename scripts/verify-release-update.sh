#!/usr/bin/env bash
set -euo pipefail
pkg=com.aistudio.worktracker.qztvdw.distribution
# Runner must provide a fresh disposable emulator. Never uninstall/reset as a workaround.
if adb shell pm list packages | grep -Fx "package:$pkg"; then echo 'Expected fresh emulator; refusing to overwrite existing data'; exit 1; fi
mkdir -p release-evidence
collect_ui_evidence() {
  adb exec-out screencap -p > release-evidence/last-ui.png || true
  for name in v2-launch v2-launch-name v2-home v2-add-hours v2-add-job v2-add-group v2-add-clock v2-history v2-history-selection v2-detail v2-settings v2-feedback v2-clock-keyboard v2-hours-keyboard v2-save-with-ime v2-history-final v2-home-before-live v2-home-timeout v2-keypad v2-live; do
    adb pull "/sdcard/Android/data/$pkg/files/$name.png" "release-evidence/$name.png" >/dev/null 2>&1 || true
  done
}
trap collect_ui_evidence EXIT
# A device UI test that stops answering fails after 10 minutes with its log, instead of using up the whole job.
instrument() {
  local cls="$1" out="$2"
  if ! timeout 600 adb shell am instrument -w -e class "$cls" "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee "$out"; then
    echo "===== $cls did not finish: last test-runner and crash log lines ====="
    adb logcat -d 2>/dev/null | grep -E "TestRunner|AndroidRuntime|System.err|ComposeTest|IdlingResource" | tail -n 150 || true
    # Where the app's threads are waiting (main and the instrumentation thread).
    adb root >/dev/null 2>&1 || true; timeout 20 adb wait-for-device || true
    local pid; pid=$(adb shell pidof "$pkg" | tr -d '\r' || true)
    if [ -n "$pid" ]; then
      adb shell kill -3 "$pid" || true; timeout 8 tail -f /dev/null || true
      local trace; trace=$(adb shell 'ls -t /data/anr/ 2>/dev/null | head -n 1' | tr -d '\r' || true)
      [ -n "$trace" ] && adb shell cat "/data/anr/$trace" | grep -A 45 -E '^"(main|Instr: |androidx.test|Test worker)' | head -n 220 || true
    fi
    return 1
  fi
}
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
# The owner's real path: RC16 → the delivered 2.0 build → this candidate, data kept at every step.
test -f candidate-prev.apk
adb install -r candidate-prev.apk | tee release-evidence/install-prev.txt
run_stage restart after-prev
adb install -r candidate-b.apk | tee release-evidence/install-update.txt
run_stage verify
for evidence_file in restart-before.json updater-before.json verify-before.json verify-after-edit.json; do
  adb pull "/sdcard/Android/data/$pkg/files/$evidence_file" "release-evidence/$evidence_file"
done
# updater-before.json was written on RC16 itself and is never overwritten by the later stages.
cmp release-evidence/updater-before.json release-evidence/verify-before.json
adb shell am start -W -n "$pkg/com.example.MainActivity" | tee release-evidence/offline-start-after-update.txt
grep -F "Status: ok" release-evidence/offline-start-after-update.txt
adb shell am force-stop "$pkg"
instrument com.example.DistributionUiTest release-evidence/ui.txt
grep -F 'OK (1 test)' release-evidence/ui.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/ui.txt
adb shell am force-stop "$pkg"
instrument com.example.CurrencyFeedbackTest release-evidence/currency-feedback.txt
grep -F 'OK (1 test)' release-evidence/currency-feedback.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/currency-feedback.txt
adb shell am force-stop "$pkg"
instrument com.example.ReportKeyboardTest release-evidence/keyboard-ui.txt
grep -F 'OK (1 test)' release-evidence/keyboard-ui.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/keyboard-ui.txt
adb shell am force-stop "$pkg"
# Stage C: a synthetic weekly plan is recorded once even with concurrent recorders, then removed again.
instrument com.example.PlanAutoRecordTest release-evidence/plan-auto-record.txt
grep -F 'OK (1 test)' release-evidence/plan-auto-record.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/plan-auto-record.txt
adb pull "/sdcard/Android/data/$pkg/files/v2-plan-home.png" release-evidence/v2-plan-home.png || true
adb shell am force-stop "$pkg"
# 2.0.4: the monthly summary, a finished plan shift (with its "לא עבדתי" button) and a new version reach the notification shade.
instrument com.example.NotificationsDeviceTest release-evidence/notifications.txt
grep -F 'OK (1 test)' release-evidence/notifications.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/notifications.txt
for name in v2-notify-monthly v2-notify-plan v2-notify-update; do
  adb pull "/sdcard/Android/data/$pkg/files/$name.png" "release-evidence/$name.png" || true
done
adb exec-out screencap -p > release-evidence/rc2-screen.png
adb logcat -d -b crash > release-evidence/crash-log.txt
! grep -E "FATAL EXCEPTION|AndroidRuntime|Process: $pkg" release-evidence/crash-log.txt
