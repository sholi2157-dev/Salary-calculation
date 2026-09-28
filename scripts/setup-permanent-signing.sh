#!/usr/bin/env bash
# Run ONLY by the owner in their private Codespace terminal. Never in Actions or ChatGPT.
set -euo pipefail
set +x
umask 077
repo='sholi2157-dev/Salary-calculation'
signing_dir="$HOME/salary-signing-private"
mkdir -p "$signing_dir"
keyfile="$signing_dir/salary-distribution.p12"
command -v keytool >/dev/null
command -v gh >/dev/null
# Use owner CLI authentication, not a Codespaces repo token with insufficient secret privileges.
unset GH_TOKEN GITHUB_TOKEN
if ! gh auth status >/dev/null 2>&1; then gh auth login --hostname github.com --git-protocol https --web --scopes repo; fi
existing=$(gh secret list --repo "$repo" --json name --jq '.[].name')
if ! test -f "$keyfile" && [[ "$existing" == *ANDROID_KEYSTORE_BASE64* ]]; then
  echo 'A signing secret already exists. STOP: restore the original backup; never generate a replacement.' >&2
  exit 1
fi
read -r -s -p 'Keystore password (save in your password manager; at least 16 characters): ' SALARY_SIGNING_PASSWORD
printf '\n'
[[ ${#SALARY_SIGNING_PASSWORD} -ge 16 ]] || { echo 'Password too short'; exit 1; }
export SALARY_SIGNING_PASSWORD
trap 'unset SALARY_SIGNING_PASSWORD password_again' EXIT
if ! test -f "$keyfile"; then
  read -r -s -p 'Repeat password: ' password_again
  printf '\n'
  [[ "$password_again" == "$SALARY_SIGNING_PASSWORD" ]] || { echo 'Passwords do not match'; exit 1; }
  keytool -genkeypair -keystore "$keyfile" -storetype PKCS12 -alias salary-distribution \
    -keyalg RSA -keysize 3072 -validity 36500 -dname 'CN=Salary Calculation, OU=Android Distribution' \
    -storepass:env SALARY_SIGNING_PASSWORD -keypass:env SALARY_SIGNING_PASSWORD
fi
keytool -list -v -keystore "$keyfile" -storepass:env SALARY_SIGNING_PASSWORD -alias salary-distribution > "$signing_dir/public-certificate.txt"
echo "Before continuing, download $keyfile from Codespaces Explorer and keep two protected copies."
echo 'Keep its password separately in your password manager. Never upload this file to chat or commit it.'
read -r -p 'Type BACKED-UP only after downloading and preserving your key: ' confirmation
[[ "$confirmation" == 'BACKED-UP' ]] || { echo 'Stopped safely. Re-run with this same key to continue.'; exit 1; }
base64 -w0 "$keyfile" | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$repo"
printf '%s' "$SALARY_SIGNING_PASSWORD" | gh secret set ANDROID_STORE_PASSWORD --repo "$repo"
printf '%s' "$SALARY_SIGNING_PASSWORD" | gh secret set ANDROID_KEY_PASSWORD --repo "$repo"
fingerprint=$(sed -n 's/.*SHA256: //p' "$signing_dir/public-certificate.txt" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
[[ "$fingerprint" =~ ^[0-9a-f]{64}$ ]]
printf '%s' "$fingerprint" | gh secret set ANDROID_CERT_SHA256 --repo "$repo"
echo 'Signing secrets configured. Public certificate fingerprint:'
printf '%s\n' "$fingerprint"
echo 'Return to the owner review session. Do not publish a release yet.'
