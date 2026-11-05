// X-2: scripts/check-fixtures.cjs compares every shared fixture (rules, plan, share, summary, recap, money contract,
// group reconcile and the erase tombstones) with the Android test resources, and FAILS when the ref is missing instead
// of skipping. Runs against a throw-away git repository standing in for the Android branch; no network.
const { test } = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path'), { spawnSync, execFileSync } = require('node:child_process');
const ROOT = path.join(__dirname, '..'), SCRIPT = path.join(ROOT, 'scripts', 'check-fixtures.cjs');
const RES = 'app/src/test/resources/';
const SHARED = [['web-tests/fixtures/v2-rules.json', 'v2-rules.json'], ['web-tests/fixtures/v2-plan.json', 'v2-plan.json'],
  ['web-tests/fixtures/v2-share-fixtures.json', 'v2-share-fixtures.json'], ['web-tests/fixtures/v2-summary.json', 'v2-summary.json'],
  ['web-tests/fixtures/v2-recap.json', 'v2-recap.json'], ['web-tests/fixtures/money-contract.json', 'money-contract.json'],
  ['web-tests/fixtures/group-reconcile.json', 'group-reconcile.json'],
  ['integration-tests/fixtures-erase-tombstones.json', 'erase-tombstones-web.json']];
const hasGit = spawnSync('git', ['--version']).status === 0;

function androidRepo(edit = () => {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fixtures-'));
  const g = (...a) => execFileSync('git', ['-C', dir, '-c', 'user.name=t', '-c', 'user.email=t@example.invalid', ...a], { stdio: 'ignore' });
  g('init', '-q');
  fs.mkdirSync(path.join(dir, RES), { recursive: true });
  for (const [web, android] of SHARED) fs.copyFileSync(path.join(ROOT, web), path.join(dir, RES, android));
  edit(path.join(dir, RES));
  g('add', '-A'); g('commit', '-q', '-m', 'synthetic android');
  return dir;
}
const run = (repo, ref = 'HEAD') => spawnSync(process.execPath, [SCRIPT, repo, ref], { encoding: 'utf8' });

test('X-2: all 8 shared fixtures are compared and pass when byte-identical', { skip: !hasGit && 'git unavailable' }, () => {
  const r = run(androidRepo());
  assert.equal(r.status, 0, r.stderr);
  for (const [web, android] of SHARED) assert.match(r.stdout, new RegExp(`${web.replace(/[.]/g, '\\.')} matches HEAD:${RES}${android}`));
  assert.match(r.stdout, /8\/8 shared fixtures identical/);
});

test('X-2: one changed byte in money-contract, group-reconcile or the tombstones fails the check', { skip: !hasGit && 'git unavailable' }, () => {
  for (const name of ['money-contract.json', 'group-reconcile.json', 'erase-tombstones-web.json']) {
    const r = run(androidRepo(dir => fs.appendFileSync(path.join(dir, name), ' ')));
    assert.equal(r.status, 1, name);
    assert.match(r.stderr, new RegExp(`differs from HEAD:${RES}${name.replace(/[.]/g, '\\.')}`));
  }
});

test('X-2: a fixture missing on the Android ref fails the check', { skip: !hasGit && 'git unavailable' }, () => {
  const r = run(androidRepo(dir => fs.unlinkSync(path.join(dir, 'erase-tombstones-web.json'))));
  assert.equal(r.status, 1);
  assert.match(r.stderr, /erase-tombstones-web\.json missing on HEAD/);
});

test('X-2: a missing ref fails instead of skipping', { skip: !hasGit && 'git unavailable' }, () => {
  const r = run(androidRepo(), 'origin/no-such-android-branch');
  assert.equal(r.status, 1);
  assert.match(r.stderr, /FAILED — origin\/no-such-android-branch is not available/);
  const notRepo = run(fs.mkdtempSync(path.join(os.tmpdir(), 'norepo-')));
  assert.equal(notRepo.status, 1);
});
