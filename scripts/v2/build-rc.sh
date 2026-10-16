#!/usr/bin/env bash
# Builds the signed 2.0 release candidate (B) and pins the exact previously delivered RC16 (A).
set -euo pipefail
set +x
: "${KEYSTORE_PATH:?Permanent key required}" "${STORE_PASSWORD:?}" "${KEY_PASSWORD:?}" "${CERT_SHA256:?}"
: "${RC_CODE:?}" "${RC_NAME:?}"
mkdir -p release-evidence
# A = the exact public RC16 bytes (versionCode 24), never a rebuild.
curl -fsSL -o candidate-a.apk "https://github.com/sholi2157-dev/Salary-calculation/releases/download/android-v1.5-rc16/SalaryRC16.apk"
echo "400e11bccd9a46e7911c498137da9ba5d23b3b86cee50f0f1ea6400f40e7cc30  candidate-a.apk" | sha256sum -c -
# The previously delivered 2.0 candidate, exact bytes from its own candidate run.
: "${PREV_CODE:?}" "${PREV_NAME:?}"
cp prev-rc/candidate-b.apk candidate-prev.apk
echo "$PREV_SHA  candidate-prev.apk" | sha256sum -c -
test "$RC_CODE" -gt "$PREV_CODE"
test "$PREV_CODE" -gt 24
gradle :app:assembleRelease :app:assembleReleaseAndroidTest -PdistributionInstrumentation=true \
  -PdistributionVersionCode="$RC_CODE" -PdistributionVersionName="$RC_NAME" --no-configuration-cache --console=plain
cp app/build/outputs/apk/release/app-release.apk candidate-b.apk
for apk in candidate-a.apk candidate-prev.apk candidate-b.apk app/build/outputs/apk/androidTest/release/app-release-androidTest.apk; do
  report="release-evidence/$(basename "$apk").txt"
  {
    case "$apk" in
      candidate-a.apk) echo "Source: public release android-v1.5-rc16 (delivered RC16)";;
      candidate-prev.apk) echo "Source: run $PREV_RUN artifact (delivered $PREV_NAME)";;
      *) echo "Source commit: $GITHUB_SHA";;
    esac
    "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$apk"
    sha256sum "$apk"
    "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$apk" | sed -n '/^package:/p; /^sdkVersion:/p; /^targetSdkVersion:/p'
  } > "$report"
  actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$report")
  test "$actual" = "$CERT_SHA256" || { echo 'Permanent signing fingerprint mismatch'; exit 1; }
  ! grep -F 'CN=Android Debug' "$report"
done
python3 scripts/check-local-build.py
RC_CODE="$RC_CODE" RC_NAME="$RC_NAME" PREV_CODE="$PREV_CODE" PREV_NAME="$PREV_NAME" python3 - <<'CHECK'
from pathlib import Path
import os, re
for name, code, version in [('candidate-a.apk', '24', '1.5-rc16'), ('candidate-prev.apk', os.environ['PREV_CODE'], os.environ['PREV_NAME']), ('candidate-b.apk', os.environ['RC_CODE'], os.environ['RC_NAME'])]:
    report = Path(f'release-evidence/{name}.txt').read_text()
    assert re.search(r"name='com.aistudio.worktracker.qztvdw.distribution' versionCode='" + code + r"' versionName='" + re.escape(version) + "'", report), name
print('Identity verified: same package, same permanent certificate, versionCode', os.environ['RC_CODE'], '>', os.environ['PREV_CODE'], '> 24')
CHECK
sha256sum candidate-b.apk | tee release-evidence/candidate-b.sha256
