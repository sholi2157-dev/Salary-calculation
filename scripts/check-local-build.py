from pathlib import Path
import re
paths=list(Path('app/build/generated/source/buildConfig/release').rglob('BuildConfig.java'))
assert len(paths)==1
text=paths[0].read_text()
for flag in ['ACCOUNTS_ENABLED','VERSIONED_SYNC_ENABLED','CLOUD_SYNC_ENABLED','GOOGLE_SIGN_IN_ENABLED']:
    assert re.search(flag+r'\s*=\s*false',text),flag
assert re.search(r'LOCAL_DISTRIBUTION\s*=\s*true',text)
manifests=list(Path('app/build/intermediates/merged_manifests/release').rglob('AndroidManifest.xml'))
assert manifests
for file in manifests:
    assert 'com.google.firebase.provider.FirebaseInitProvider' not in file.read_text()
print('Release package local gates and Firebase initializer removal verified')
