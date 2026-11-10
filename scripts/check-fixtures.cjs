#!/usr/bin/env node
// Verifies that every expected-results fixture shared by web and Android is byte-identical to the Android test
// resource on the Android release branch. Usage: node scripts/check-fixtures.cjs [git repo path] [ref]
// The repo defaults to this checkout (the web and Android branches share one repository); the ref defaults to
// origin/release/2.4-android. Exits 1 on any mismatch, on a fixture missing on either side, and when the ref itself is
// not available (X-2: a missing ref used to "skip" with exit 0, so the gate could pass without comparing anything).
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const repo = process.argv[2] || process.env.ANDROID_REPO || path.resolve(__dirname, '..');
const ref = process.argv[3] || process.env.ANDROID_REF || 'origin/release/2.4-android';
const RES = 'app/src/test/resources/';
/** [path in this web checkout, path on the Android ref]. */
const PAIRS = [
  ...['v2-rules.json', 'v2-plan.json', 'v2-share-fixtures.json', 'v2-summary.json', 'v2-recap.json',
    'money-contract.json', 'group-reconcile.json', 'hebrew-dates.json'].map(name => ['web-tests/fixtures/' + name, RES + name]),
  // Written by integration-tests/erase-e2e.test.cjs against the Firestore emulator; Android validates the same shape.
  ['integration-tests/fixtures-erase-tombstones.json', RES + 'erase-tombstones-web.json'],
];
const git = args => execFileSync('git', ['-C', repo, ...args], { stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 64 << 20 });
try { git(['rev-parse', '--verify', '--quiet', ref + '^{commit}']); }
catch {
  console.error(`check-fixtures: FAILED — ${ref} is not available in ${repo}; fetch it first, e.g. ` +
    `git fetch --no-tags origin +refs/heads/release/2.4-android:refs/remotes/origin/release/2.4-android`);
  process.exit(1);
}
let failed = 0;
for (const [web, android] of PAIRS) {
  const local = path.join(__dirname, '..', web);
  let expected;
  try { expected = git(['show', `${ref}:${android}`]); }
  catch { console.error(`check-fixtures: ${android} missing on ${ref}`); failed++; continue; }
  const actual = fs.existsSync(local) ? fs.readFileSync(local) : null;
  if (!actual) { console.error(`check-fixtures: ${web} missing in this checkout`); failed++; }
  else if (Buffer.compare(actual, expected) === 0) console.log(`check-fixtures: ${web} matches ${ref}:${android}`);
  else { console.error(`check-fixtures: ${web} differs from ${ref}:${android} — keep both byte-identical`); failed++; }
}
console.log(`check-fixtures: ${PAIRS.length - failed}/${PAIRS.length} shared fixtures identical`);
process.exit(failed ? 1 : 0);
