from pathlib import Path
import json, xml.etree.ElementTree as ET
result={}
for variant in ('Debug','Release'):
    totals=dict(tests=0, failures=0, errors=0, skipped=0)
    files=list(Path(f'app/build/test-results/test{variant}UnitTest').glob('TEST-com.example.Work*Test.xml'))
    assert files, f'{variant}: no test reports'
    for file in files:
        suite=ET.parse(file).getroot()
        for key in totals: totals[key]+=int(suite.get(key,0))
    assert totals['tests']>=51 and not any(totals[key] for key in ('failures','errors','skipped')), totals
    result[variant]=totals
Path('release-evidence').mkdir(exist_ok=True)
Path('release-evidence/unit-tests.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
