#!/usr/bin/env bash
# Direct update from the PUBLIC 2.3.1 (code 54, exact release bytes) to the candidate with rich 2.x data (audit QA-02).
# This is the path every existing user takes: no intermediate version is installed. Synthetic data only; the runner
# must provide a fresh disposable emulator. Never uninstall/reset as a workaround.
set -euo pipefail
pkg=com.aistudio.worktracker.qztvdw.distribution
if adb shell pm list packages | grep -Fx "package:$pkg"; then echo 'Expected fresh emulator; refusing to overwrite existing data'; exit 1; fi
mkdir -p release-evidence/rich
# On failure, print what happened (the evidence artifact may not be reachable from every reviewer).
diagnose() {
  echo '===== rich upgrade: failure diagnostics ====='
  for f in release-evidence/rich/*.txt; do echo "--- $f"; tail -n 60 "$f"; done
  adb logcat -d 2>/dev/null | grep -E "RichUpgradeTest|TestRunner|AndroidRuntime|SQLite|System.err" | tail -n 120 || true
}
trap 'rc=$?; [ $rc -ne 0 ] && diagnose; exit $rc' EXIT
echo "b6380d60a0bffb9e94157902307c380646a66e48f9c6f0f6d3c900a1ab3404f4  candidate-prev.apk" | sha256sum -c -
adb shell wm size 720x1600
adb shell wm density 320
adb install candidate-prev.apk | tee release-evidence/rich/install-231.txt
adb install app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell svc wifi disable
adb shell svc data disable
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS || true
rich_stage() {
  log="release-evidence/rich/$1.txt"
  adb shell am force-stop "$pkg"
  adb shell am instrument -w -e class com.example.RichUpgradeTest -e stage "$1" \
    "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee "$log"
  grep -F 'OK (1 test)' "$log"
  ! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "$log"
}
rich_stage seed
# 2.3.1 itself opens the seeded data (and its running timer) before the update.
adb shell am start -W -n "$pkg/com.example.MainActivity" | tee release-evidence/rich/start-231.txt
grep -F "Status: ok" release-evidence/rich/start-231.txt
sleep 3
adb exec-out screencap -p > release-evidence/rich/home-231.png || true
# The snapshot is taken after 2.3.1 itself opened the data, so anything it does on its own is part of "before".
rich_stage snapshot
adb shell dumpsys package "$pkg" | grep -E "versionCode|versionName" | tee release-evidence/rich/version-before.txt
adb install -r candidate-b.apk | tee release-evidence/rich/install-candidate.txt
adb shell dumpsys package "$pkg" | grep -E "versionCode|versionName" | tee release-evidence/rich/version-after.txt
rich_stage verify
adb shell am start -W -n "$pkg/com.example.MainActivity" | tee release-evidence/rich/start-candidate.txt
grep -F "Status: ok" release-evidence/rich/start-candidate.txt
sleep 3
adb exec-out screencap -p > release-evidence/rich/home-candidate.png || true
for f in rich-before.json rich-after.json; do adb pull "/sdcard/Android/data/$pkg/files/$f" "release-evidence/rich/$f"; done
adb logcat -d -b crash > release-evidence/rich/crash-log.txt
! grep -E "FATAL EXCEPTION|AndroidRuntime|Process: $pkg" release-evidence/rich/crash-log.txt
echo 'Rich 2.3.1 → candidate update: user data, preferences and the running timer preserved.'
