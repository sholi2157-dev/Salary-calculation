#!/usr/bin/env bash
set -euo pipefail
pkg=com.aistudio.worktracker.qztvdw.distribution
# Separate fresh AVD; never clear/uninstall the update-test installation.
if adb shell pm list packages | grep -Fx "package:$pkg"; then echo 'Expected separate fresh emulator'; exit 1; fi
adb shell wm size 720x1600
adb shell wm density 320
adb install candidate-b.apk
adb install app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell svc wifi disable
adb shell svc data disable
adb shell am instrument -w -e class com.example.OnboardingFreshInstallTest#firstLaunchCompletionReplaySkipAndNoSideEffects \
  "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee release-evidence/onboarding-fresh.txt
for n in $(seq 0 8); do
  adb pull "/sdcard/Android/data/$pkg/files/onboarding-device-$n.png" "release-evidence/onboarding-device-$n.png" >/dev/null 2>&1 || true
done
grep -F 'OK (1 test)' release-evidence/onboarding-fresh.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/onboarding-fresh.txt
adb shell am force-stop "$pkg"
adb shell am instrument -w -e class com.example.OnboardingFreshInstallTest#completedColdLaunchStaysHidden \
  "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee release-evidence/onboarding-cold-reopen.txt
grep -F 'OK (1 test)' release-evidence/onboarding-cold-reopen.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' release-evidence/onboarding-cold-reopen.txt
adb logcat -d -b crash > release-evidence/onboarding-crash-log.txt
! grep -E "FATAL EXCEPTION|AndroidRuntime|Process: $pkg" release-evidence/onboarding-crash-log.txt
