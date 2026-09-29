#!/usr/bin/env bash
set -euo pipefail
set +x
: "${KEYSTORE_PATH:?Permanent key required}" "${STORE_PASSWORD:?}" "${KEY_PASSWORD:?}" "${CERT_SHA256:?}"
mkdir -p release-evidence
# Upgrade from the exact owner-tested RC1 binary, never a reconstruction.
: "${RC1_APK:?Original RC1 artifact required}"
echo "35e078511b64d47a36977e1dffb5de374f9d1e8452b0fd20735ba3bd1a278910  $RC1_APK" | sha256sum -c -
cp "$RC1_APK" candidate-a.apk
version_b=$(python3 -c 'import re;print(re.search(r"orNull\?\.toInt\(\) \?: (\d+)",open("app/build.gradle.kts").read())[1])')
gradle :app:assembleRelease :app:assembleReleaseAndroidTest :app:testReleaseUnitTest --tests 'com.example.Work*Test' \
  -PdistributionInstrumentation=true -PdistributionVersionCode="$version_b"  \
  --no-configuration-cache --console=plain
cp app/build/outputs/apk/release/app-release.apk candidate-b.apk
for apk in candidate-a.apk candidate-b.apk app/build/outputs/apk/androidTest/release/app-release-androidTest.apk; do
  report="release-evidence/$(basename "$apk").txt"
  {
    if [ "$apk" = candidate-a.apk ]; then
      echo "Source commit: 2d6a3f9e3d24a04a3d5777253e225096b0dd9c93 (original RC1 artifact)"
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
