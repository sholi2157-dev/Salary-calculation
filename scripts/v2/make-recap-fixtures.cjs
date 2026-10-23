// Generates the shared recap cases (when a recap opens, is new, the archive, and the picked words) from
// web/v2/summary.js. Android (V2RecapTest) and Web (v2-recap.test.cjs) assert these exact results.
// Usage: node scripts/v2/make-recap-fixtures.cjs <out.json>
const fs = require('fs');
const S = require('../../web/v2/summary.js');
const days = ['2026-09-30', '2026-10-23', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-03', '2026-11-04', '2026-11-29', '2026-11-30',
  '2026-12-04', '2026-12-05', '2026-12-30', '2027-01-03', '2027-01-04', '2027-02-27', '2027-02-28', '2027-03-04', '2027-03-05', '2028-02-29'];
const windows = days.map(today => ({ today, ...S.recapNew(today), archive: S.recapArchive(today),
  open: ['2026-09', '2026-10', '2026-11', '2026', '2027-02'].filter(s => S.recapOpen(s, today)) }));
const voices = [];
for (const seed of ['a1b2c3d4', 'zz', 'k9x0m2q7', '']) for (const scope of ['2026-10', '2026-11', '2026-12', '2026', '2027'])
  voices.push({ seed, scope, hash: S.hash(`${seed}|${scope}`), voice: S.voice(scope, seed) });
fs.writeFileSync(process.argv[2], JSON.stringify({ first: S.FIRST_RECAP, newDays: S.RECAP_NEW_DAYS, windows, voices }, null, 1) + '\n');
console.log(windows.length + ' windows, ' + voices.length + ' voices');
