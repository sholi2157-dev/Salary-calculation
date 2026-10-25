from pathlib import Path
import re
# 2.1: the distribution build signs in with Google and syncs through the account (versioned records only).
# The retired integer-ID cloud writer stays off, and Firebase starts only through FirebaseSafeInitializer.
paths=list(Path('app/build/generated/source/buildConfig/release').rglob('BuildConfig.java'))
assert len(paths)==1
text=paths[0].read_text()
for flag in ['ACCOUNTS_ENABLED','VERSIONED_SYNC_ENABLED','GOOGLE_SIGN_IN_ENABLED']:
    assert re.search(flag+r'\s*=\s*true',text),flag
assert re.search(r'CLOUD_SYNC_ENABLED\s*=\s*false',text),'CLOUD_SYNC_ENABLED'
assert re.search(r'LOCAL_DISTRIBUTION\s*=\s*true',text)
manifests=list(Path('app/build/intermediates/merged_manifests/release').rglob('AndroidManifest.xml'))
assert manifests
for file in manifests:
    assert 'com.google.firebase.provider.FirebaseInitProvider' not in file.read_text()
print('Release package gates verified: Google account sync on, retired cloud writer off, no automatic Firebase initializer')
