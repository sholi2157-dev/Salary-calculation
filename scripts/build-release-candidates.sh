#!/usr/bin/env bash
set -euo pipefail
set +x
: "${KEYSTORE_PATH:?Permanent key required}" "${STORE_PASSWORD:?}" "${KEY_PASSWORD:?}" "${CERT_SHA256:?}"
mkdir -p release-evidence
# Upgrade from the exact owner-tested RC9 binary, never a reconstruction.
: "${PREVIOUS_APK:?Original RC9 artifact required}"
echo "c63dd258bc328e25204498f593b9704399723e9166c5169839e5bc4e9d751734  $PREVIOUS_APK" | sha256sum -c -
cp "$PREVIOUS_APK" candidate-a.apk
version_b=$(python3 -c 'import re;print(re.search(r"orNull\?\.toInt\(\) \?: (\d+)",open("app/build.gradle.kts").read())[1])')
gradle :app:assembleRelease :app:assembleReleaseAndroidTest \
  -PdistributionInstrumentation=true -PdistributionVersionCode="$version_b"  \
  --no-configuration-cache --console=plain
cp app/build/outputs/apk/release/app-release.apk candidate-b.apk
for apk in candidate-a.apk candidate-b.apk app/build/outputs/apk/androidTest/release/app-release-androidTest.apk; do
  report="release-evidence/$(basename "$apk").txt"
  {
    if [ "$apk" = candidate-a.apk ]; then
      echo "Source commit: 87c288cd3f18c44669a550a0d9b5a944dd5902e3 (original RC9 artifact)"
    else echo "Source commit: $GITHUB_SHA"; fi
    "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$apk"
    sha256sum "$apk"
    "$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "$apk" | sed -n '/^package:/p; /^sdkVersion:/p; /^targetSdkVersion:/p'
  } > "$report"
  actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$report")
  test "$actual" = "$CERT_SHA256" || { echo 'Permanent signing fingerprint mismatch'; exit 1; }
  ! grep -F 'CN=Android Debug' "$report"
done
python3 scripts/check-local-build.py
# Pin both real installed identities as well as their shared permanent signer.
python3 - <<'CHECK'
from pathlib import Path
import re
for name, code, version in [('candidate-a.apk', 17, '1.5-rc9'), ('candidate-b.apk', 18, '1.5-rc10')]:
    report = Path(f'release-evidence/{name}.txt').read_text()
    assert re.search(r"name='com.aistudio.worktracker.qztvdw.distribution' versionCode='" + str(code) + r"' versionName='" + re.escape(version) + "'", report), name
CHECK

