"""Create a local candidate manifest only. Never uploads/publishes anything."""
import argparse, hashlib, json, os, pathlib, re, subprocess
parser=argparse.ArgumentParser()
parser.add_argument('--apk',required=True,type=pathlib.Path)
parser.add_argument('--tag',required=True)
parser.add_argument('--notes',required=True,type=pathlib.Path)
parser.add_argument('--output',default='release.json',type=pathlib.Path)
args=parser.parse_args()
assert re.fullmatch(r'[A-Za-z0-9._-]+',args.tag)
assert re.fullmatch(r'[A-Za-z0-9._-]+\.apk',args.apk.name)
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
subprocess.run([str(tools/'apksigner'),'verify',str(args.apk)],check=True)
badging=subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(args.apk)],text=True)
package,code,name=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging).groups()
assert package=='com.aistudio.worktracker.qztvdw.distribution'
assert 'application-debuggable' not in badging
manifest=dict(versionCode=int(code),versionName=name,apkUrl=f'https://github.com/sholi2157-dev/Salary-calculation/releases/download/{args.tag}/{args.apk.name}',
 sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(),releaseNotes=args.notes.read_text(),
 versionPage=f'https://github.com/sholi2157-dev/Salary-calculation/releases/tag/{args.tag}')
args.output.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print('Prepared local manifest; no public release was created.')
