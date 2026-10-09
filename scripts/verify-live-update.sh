#!/usr/bin/env bash
set -Eeuo pipefail
pkg=com.aistudio.worktracker.qztvdw.distribution
runner="$pkg.test/androidx.test.runner.AndroidJUnitRunner"
external="/sdcard/Android/data/$pkg/files"
mkdir -p live-update-evidence
update_code=$(python3 -c 'import json; print(json.load(open("docs/android-update-release.json"))["versionCode"])')
update_name=$(python3 -c 'import json; print(json.load(open("docs/android-update-release.json"))["versionName"])')
update_sha=$(python3 -c 'import json; print(json.load(open("docs/android-update-release.json"))["sha256"])')
collect_failure() {
  adb logcat -d > live-update-evidence/device-log.txt || true
  adb shell dumpsys activity activities > live-update-evidence/failure-activities.txt || true
  adb shell screencap -p /sdcard/failure.png || true
  adb pull /sdcard/failure.png live-update-evidence/failure.png || true
}
trap collect_failure ERR
adb install previous/candidate-b.apk
adb install app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb shell appops set "$pkg" REQUEST_INSTALL_PACKAGES allow
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS
adb logcat -c
instrument() {
  local method="$1" output="$2"
  shift 2
  timeout 240s adb shell am instrument -w -r -e timeout_msec 180000 -e previousVersionCode 22 -e updateVersionCode "$update_code" -e updateVersionName "$update_name" -e updateSha256 "$update_sha" -e class "$method" "$@" "$runner" | tee "live-update-evidence/$output.txt"
  grep -E 'OK \(1 test\)' "live-update-evidence/$output.txt"
}
instrument com.example.DistributionUpdateTest seed -e stage seed
# Seed completed data means this is an existing installation, not a fresh onboarding run.
instrument com.example.DistributionUpdateTest restart -e stage restart
# The seed suppressed automatic startup checks; the manual button contacts the live channel.
adb shell am force-stop "$pkg"
instrument 'com.example.LiveUpdateChannelTest#previousReleaseFindsAndDownloadsThroughActualButton' live-check
adb pull "$external/live-downloaded.apk" live-update-evidence/live-downloaded.apk
printf '%s\n' "$update_sha  live-update-evidence/live-downloaded.apk" | sha256sum --check
adb pull "$external/live-before.json" live-update-evidence/live-before.json
adb shell dumpsys activity activities > live-update-evidence/installer-activities.txt
adb shell uiautomator dump /sdcard/live-installer.xml
adb pull /sdcard/live-installer.xml live-update-evidence/live-installer.xml
adb shell screencap -p /sdcard/live-installer.png
adb pull /sdcard/live-installer.png live-update-evidence/live-installer.png
python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('live-update-evidence/live-installer.xml').getroot()
nodes=[n for n in root.iter('node') if n.get('text','').casefold() in ('update','install','עדכון','התקנה') and n.get('enabled')=='true' and n.get('clickable')=='true' and 'packageinstaller' in n.get('package','')]
assert len(nodes)==1, 'Expected actual Android package installer confirmation, not app UI'
bounds=list(map(int,re.findall(r'\d+',nodes[0].get('bounds'))))
x=(bounds[0]+bounds[2])//2;y=(bounds[1]+bounds[3])//2
subprocess.run(['adb','shell','input','tap',str(x),str(y)],check=True)
PY
for attempt in $(seq 1 60); do
  if adb shell dumpsys package "$pkg" | grep "versionCode=$update_code " > /dev/null; then break; fi
  sleep 1
done
adb shell dumpsys package "$pkg" > live-update-evidence/installed-package.txt
grep "versionCode=$update_code " live-update-evidence/installed-package.txt
# versionCode changes before Android finishes dexopt and unfreezes the package.
# Wait for the actual successful-install screen instead of racing instrumentation.
python3 - <<'DONEPY'
import re, subprocess, time, pathlib, xml.etree.ElementTree as ET
deadline=time.monotonic()+60
while time.monotonic()<deadline:
    subprocess.run(['adb','shell','uiautomator','dump','/sdcard/live-install-complete.xml'],check=True,timeout=15)
    subprocess.run(['adb','pull','/sdcard/live-install-complete.xml','live-update-evidence/live-install-complete.xml'],check=True,timeout=15)
    root=ET.parse('live-update-evidence/live-install-complete.xml').getroot()
    nodes=list(root.iter('node'))
    buttons=[n for n in nodes if n.get('text','').casefold() in ('done','סיום','בוצע') and n.get('enabled')=='true' and n.get('clickable')=='true' and 'packageinstaller' in n.get('package','')]
    if buttons:
        texts=' '.join(n.get('text','') for n in nodes).casefold()
        assert 'app installed' in texts or 'האפליקציה הותקנה' in texts, texts
        assert len(buttons)==1
        subprocess.run(['adb','shell','screencap','-p','/sdcard/live-install-complete.png'],check=True,timeout=15)
        subprocess.run(['adb','pull','/sdcard/live-install-complete.png','live-update-evidence/live-install-complete.png'],check=True,timeout=15)
        bounds=list(map(int,re.findall(r'\d+',buttons[0].get('bounds'))))
        subprocess.run(['adb','shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2)],check=True,timeout=15)
        break
    time.sleep(1)
else:
    raise AssertionError('Android did not report successful completed installation')
DONEPY
# Start retained-data verification only after Android has finished installation.
adb shell input keyevent KEYCODE_HOME
adb shell am force-stop "$pkg"
sleep 1
instrument 'com.example.LiveUpdateRetainedDataTest#updatedReleaseRetainsDataAndDoesNotOfferItselfAgain' live-retained
adb pull "$external/live-after.json" live-update-evidence/live-after.json
cmp live-update-evidence/live-before.json live-update-evidence/live-after.json
adb logcat -b crash -d > live-update-evidence/crash-log.txt
if grep -E 'FATAL EXCEPTION|Process: com.aistudio.worktracker.qztvdw.distribution' live-update-evidence/crash-log.txt; then exit 1; fi
python3 - <<'PY'
import json,pathlib
config=json.loads(pathlib.Path('docs/android-update-release.json').read_text())
pathlib.Path('live-update-evidence/result.json').write_text(json.dumps({'originalInstalledVersionCode':22,'liveManualCheckFoundVersionCode':config['versionCode'],'liveApkChecksumVerified':True,'androidInstallerConfirmed':True,'installedVersionCode':config['versionCode'],'syntheticDataPreservedExactly':True,'sameVersionNotOfferedAgain':True},indent=2)+'\n')
PY
