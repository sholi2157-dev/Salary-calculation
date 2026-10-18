"""Publish only an explicitly selected, already-tested signed artifact; never rebuild it."""
import hashlib, json, os, pathlib, re, subprocess, sys, time, urllib.request
root=pathlib.Path('update-publication');root.mkdir(exist_ok=True)
config=json.loads(pathlib.Path('docs/android-update-release.json').read_text())
repo='sholi2157-dev/Salary-calculation'
base=f'https://github.com/{repo}/releases'
tag=config['tag'];name=config['fileName']
assert re.fullmatch(r'[A-Za-z0-9._-]+',tag)
assert re.fullmatch(r'[A-Za-z0-9._-]+\.apk',name)
def command(*args):return subprocess.check_output(args,text=True)
def download(url,path):
    last=None
    for attempt in range(6):
        try:
            with urllib.request.urlopen(url,timeout=90) as response:
                path.write_bytes(response.read())
            return
        except Exception as error:
            last=error;time.sleep(min(5*(attempt+1),20))
    raise last
if sys.argv[1]=='prepare':
    run=json.loads(command('gh','api',f'repos/{repo}/actions/runs/{config["runId"]}'))
    assert run['conclusion']=='success' and run['head_sha']==config['sourceSha']
    subprocess.run(['gh','run','download',str(config['runId']),'--repo',repo,'--name',config['artifactName'],'--dir',str(root/'verified')],check=True)
    apk=(root/'verified/candidate-b.apk').read_bytes()
    assert hashlib.sha256(apk).hexdigest()==config['sha256']
    (root/name).write_bytes(apk)
    tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
    signature=command(str(tools/'apksigner'),'verify','--verbose','--print-certs',str(root/name))
    assert f'Signer #1 certificate SHA-256 digest: {config["certificateSha256"]}' in signature
    (root/'signature.txt').write_text(signature)
    badging=command(str(tools/'aapt2'),'dump','badging',str(root/name))
    assert f"name='com.aistudio.worktracker.qztvdw.distribution' versionCode='{config['versionCode']}' versionName='{config['versionName']}'" in badging
    assert 'application-debuggable' not in badging
    manifest={k:config[k] for k in ['versionCode','versionName','sha256','releaseNotes']}
    manifest['apkUrl']=f'{base}/download/{tag}/{name}'
    (root/'release.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    (root/'notes.md').write_text(config['releaseNotes']+'\n\nגרסה '+config['versionName']+' / קוד '+str(config['versionCode'])+'\n\nTested source: '+config['sourceSha']+'\nAPK SHA256: '+config['sha256']+'\n')
    (root/'verified-run.json').write_text(json.dumps({k:run[k] for k in ['id','head_sha','conclusion','html_url']},indent=2))
elif sys.argv[1]=='publish':
    # Refuse to replace a newer channel or mutate an existing release's APK.
    try:
        latest=json.loads(command('gh','api',f'repos/{repo}/releases/latest'))
    except subprocess.CalledProcessError:
        latest=None
    if latest:
        manifests=[a for a in latest['assets'] if a['name']=='release.json']
        if manifests:
            download(manifests[0]['browser_download_url'],root/'previous-release.json')
            old=json.loads((root/'previous-release.json').read_text())
            assert old['versionCode']<=config['versionCode'],'Refusing channel downgrade'
            if old['versionCode']==config['versionCode']:assert old['sha256']==config['sha256']
    try:
        release=json.loads(command('gh','api',f'repos/{repo}/releases/tags/{tag}'))
    except subprocess.CalledProcessError:
        release=None
    if not release:
        subprocess.run(['gh','release','create',tag,str(root/name),str(root/'release.json'),'--repo',repo,'--target',os.environ['GITHUB_SHA'],'--title','חישוב שכר — '+config['versionName'],'--notes-file',str(root/'notes.md'),'--draft'],check=True)
    else:
        existing=root/'existing';existing.mkdir(exist_ok=True)
        subprocess.run(['gh','release','download',tag,'--repo',repo,'--pattern',name,'--pattern','release.json','--dir',str(existing)],check=True)
        assert hashlib.sha256((existing/name).read_bytes()).hexdigest()==config['sha256']
        assert json.loads((existing/'release.json').read_text())==json.loads((root/'release.json').read_text())
    subprocess.run(['gh','release','edit',tag,'--repo',repo,'--draft=false','--prerelease=false','--latest'],check=True)
    download(f'{base}/latest/download/release.json',root/'anonymous-release.json')
    manifest=json.loads((root/'anonymous-release.json').read_text())
    assert manifest==json.loads((root/'release.json').read_text())
    download(manifest['apkUrl'],root/'anonymous.apk')
    assert hashlib.sha256((root/'anonymous.apk').read_bytes()).hexdigest()==config['sha256']
    (root/'public-evidence.json').write_text(json.dumps({'manifestUrl':f'{base}/latest/download/release.json','apkUrl':manifest['apkUrl'],'versionCode':manifest['versionCode'],'sha256':manifest['sha256'],'anonymousManifestAndApkVerified':True},indent=2)+'\n')
    print((root/'public-evidence.json').read_text())
else:raise ValueError('Expected prepare or publish')
