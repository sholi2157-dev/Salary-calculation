from pathlib import Path
import json, xml.etree.ElementTree as ET
# Every local test class must be selected, not just enough tests to meet a total.
# Instrumentation tests have their own device gates; screen/render tests are Debug-only.
debug_only = {'ExampleRobolectricTest', 'GreetingScreenshotTest', 'KisUiTest',
              'AiShiftExperienceTest', 'OnboardingExperienceTest'}
classes = {p.stem for p in Path('app/src/test/java/com/example').glob('*Test.kt')}
assert classes, 'no local test sources found'
result={}
for variant in ('Debug','Release'):
    totals=dict(tests=0, failures=0, errors=0, skipped=0)
    files=list(Path(f'app/build/test-results/test{variant}UnitTest').glob('TEST-com.example.*Test.xml'))
    assert files, f'{variant}: no test reports'
    for file in files:
        suite=ET.parse(file).getroot()
        for key in totals: totals[key]+=int(suite.get(key,0))
    assert totals['tests']>=51 and not any(totals[key] for key in ('failures','errors','skipped')), totals
    expected = classes if variant == 'Debug' else classes - debug_only
    for name in sorted(expected):
        found = [f for f in files if f.name == f'TEST-com.example.{name}.xml']
        assert found, f'{variant}: {name} did not run'
        suite = ET.parse(found[0]).getroot()
        assert int(suite.get('tests', 0)) > 0, f'{variant}: {name} ran zero tests'
        print(f"{variant} {name}: tests={suite.get('tests')} failures={suite.get('failures')} errors={suite.get('errors')} skipped={suite.get('skipped')}")
    result[variant]=totals
Path('release-evidence').mkdir(exist_ok=True)
Path('release-evidence/unit-tests.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
