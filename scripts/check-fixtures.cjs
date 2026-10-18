#!/usr/bin/env node
// Verifies that the shared 2.0 fixtures in web-tests/fixtures are byte-identical to the Android test resources
// on origin/claude/v2-android. Usage: node scripts/check-fixtures.cjs [git repo path] [ref]
// The repo defaults to this checkout (the web and Android branches share one repository). Exits 1 on any mismatch;
// skips with exit 0 when git or the ref is unavailable.
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const repo = process.argv[2] || process.env.ANDROID_REPO || path.resolve(__dirname, '..');
const ref = process.argv[3] || process.env.ANDROID_REF || 'origin/claude/v2-android';
const names = ['v2-rules.json', 'v2-plan.json', 'v2-share-fixtures.json'];
const git = args => execFileSync('git', ['-C', repo, ...args], { stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 64 << 20 });
try { git(['rev-parse', '--verify', '--quiet', ref + '^{commit}']); }
catch { console.log(`check-fixtures: skipped — ${ref} is not available in ${repo}`); process.exit(0); }
let failed = 0;
for (const name of names) {
  const local = path.join(__dirname, '..', 'web-tests', 'fixtures', name);
  let expected;
  try { expected = git(['show', `${ref}:app/src/test/resources/${name}`]); }
  catch { console.error(`check-fixtures: ${name} missing on ${ref}`); failed++; continue; }
  const actual = fs.existsSync(local) ? fs.readFileSync(local) : null;
  if (actual && Buffer.compare(actual, expected) === 0) console.log(`check-fixtures: ${name} matches ${ref}`);
  else { console.error(`check-fixtures: ${name} differs from ${ref} — copy app/src/test/resources/${name} byte-for-byte`); failed++; }
}
process.exit(failed ? 1 : 0);
