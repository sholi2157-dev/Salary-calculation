// 2.3.0 (audit M-43, decision D-13): the CSV is "a table for Excel", not a backup. It keeps the exact time (1:40 stays
// 1:40, a timer's seconds stay), and importing it again adds nothing that is already here. Synthetic data only.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.KisRules = require('../web/v2/rules.js');
const S = require('../web/v2/share.js'), T = require('../web/transfer.js');
const at = (m, d) => new Date(2026, m - 1, d, 12, 0, 0, 0).getTime();
const PLACES = ['מסעדה', 'הובלות', 'שמרטפות', 'מחסן', 'קייטרינג', 'שיעורים', 'אירועים', 'ניקיון'];
/** 22 shifts in 8 places: clock shifts with odd minutes, an overnight shift with a break, typed hours, a timer with seconds,
 * a job, unknown pay, a bonus, a group shift, dollars, formula-looking notes and two identical shifts on one day. */
function synthetic() {
  const base = (i, x) => ({ id: 'syn-' + i, category: PLACES[i % 8], date: at(10, 1 + i), createdAt: at(10, 1 + i) + 1000, isTimeRange: false, startTime: null, endTime: null,
    hours: 4, hourlyRate: 40, totalEarnings: 160, isPaid: i % 3 === 0, notes: '', currency: '₪', isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '', ...x });
  const rows = [
    base(0, { hours: 100 / 60, hourlyRate: 33.33, totalEarnings: 55.55 }), // 1:40 typed as hours
    base(1, { isTimeRange: true, startTime: '08:00', endTime: '08:50', hours: 50 / 60, hourlyRate: 33.33, totalEarnings: 27.78 }),
    base(2, { isTimeRange: true, startTime: '22:00', endTime: '06:00', hours: 7.5, hourlyRate: 39.996, totalEarnings: 299.97 }),
    base(3, { hours: 28812 / 3600, hourlyRate: 45, totalEarnings: 360.15 }), // timer 8:00:12
    base(4, { kind: 'job', hours: 0, hourlyRate: 0, totalEarnings: 850 }),
    base(5, { payKnown: false, hourlyRate: 0, totalEarnings: 0 }),
    base(6, { bonus: 50, hours: 5.5, hourlyRate: 52, totalEarnings: 336 }),
    base(7, { isGroupShift: true, hours: 6, hourlyRate: 40, totalEarnings: 300, employerRate: 50, workerRate: 40, groupWorkersJson: JSON.stringify([{ name: 'דנה', hours: 6, isPaid: false }]) }),
    base(8, { currency: '$', hours: 3.25, hourlyRate: 20, totalEarnings: 65 }),
    base(9, { notes: '=HYPERLINK("x")', hours: 2, totalEarnings: 80 }),
    base(10, { notes: '+972 בדיקה', isTimeRange: true, startTime: '09:07', endTime: '13:31', hours: 264 / 60, hourlyRate: 31.17, totalEarnings: 137.15 }),
  ];
  for (let i = 11; i < 20; i++) rows.push(base(i, { isTimeRange: true, startTime: '16:00', endTime: (16 + (i % 5)) + ':' + String((i * 7) % 60).padStart(2, '0'),
    hours: ((i % 5) * 60 + (i * 7) % 60) / 60 || 0.5, hourlyRate: 35.4, totalEarnings: 100 + i }));
  rows.push(base(20, { date: at(10, 30), hours: 4, totalEarnings: 160 }), base(21, { date: at(10, 30), hours: 4, totalEarnings: 160, createdAt: at(10, 30) + 9000 }));
  return rows.map(r => ({ ...r, ...(r.isTimeRange && r.startTime && r.endTime && r.hours === 0.5 ? { endTime: '16:30' } : {}) }));
}

test('M-43: the hours column is the exact duration, h:mm or h:mm:ss', () => {
  assert.equal(S.exactDuration(100 / 60), '1:40');
  assert.equal(S.exactDuration(28812 / 3600), '8:00:12');
  assert.equal(S.exactDuration(0), '0:00');
  assert.equal(S.exactDuration(7.5), '7:30');
  const csv = S.csv(synthetic());
  assert.ok(csv.includes(',1:40,') && csv.includes(',8:00:12,'), 'no rounded 1.67 / 8.00');
  assert.ok(!/,1\.67,/.test(csv));
});

test('M-43: re-importing the exported CSV finds every one of the 22 shifts already here, none is added twice', () => {
  const existing = synthetic();
  assert.equal(existing.length, 22); assert.equal(new Set(existing.map(e => e.category)).size, 8);
  const decoded = T.decode(S.csv(existing));
  assert.equal(decoded.fromTable, true); assert.equal(decoded.entries.length, 22);
  assert.deepEqual(T.missing(existing, decoded.entries, { table: true }), []);
  // The same with the tab-separated copy for Excel.
  assert.deepEqual(T.missing(existing, T.decode(S.excel(existing)).entries, { table: true }), []);
});

test('M-43: into an empty device the table brings back the exact time; a changed row is new, an edited note or paid mark is not', () => {
  const existing = synthetic(), decoded = T.decode(S.csv(existing)).entries;
  for (const [i, e] of existing.entries()) assert.equal(Math.round(decoded[i].hours * 3600), Math.round(e.hours * 3600), e.id);
  assert.equal(decoded[0].hours, 100 / 60, '1:40 is read as exactly 100 minutes');
  const lines = S.csv(existing).split('\r\n');
  const header = lines[0], row = n => lines.find(l => l.includes(n));
  const notesEdited = header + '\r\n' + row('8:00:12').replace(',ממתין,', ',שולם,').replace(/,[^,]*,כן$/, ',הערה מאקסל,כן');
  assert.deepEqual(T.missing(existing, T.decode(notesEdited).entries, { table: true }), [], 'notes / paid edited in Excel: same shift');
  const changed = header + '\r\n' + row('8:00:12').replace('8:00:12', '8:00:13');
  assert.equal(T.missing(existing, T.decode(changed).entries, { table: true }).length, 1, 'one second longer is another shift');
  // Two identical shifts on one day: both already here; a third identical row is added.
  const twice = header + '\r\n' + lines.filter(l => l.includes('30/10/2026')).join('\r\n') + '\r\n' + lines.filter(l => l.includes('30/10/2026'))[0];
  assert.equal(T.missing(existing, T.decode(twice).entries, { table: true }).length, 1);
});

test('M-43: formula protection (PR #23) is kept in the exported table', () => {
  const csv = S.csv(synthetic());
  assert.ok(csv.includes(`"'=HYPERLINK(""x"")"`), 'formula text is prefixed');
  assert.ok(csv.includes(",'+972 בדיקה,"));
  for (const line of csv.split('\r\n').slice(1)) for (const cell of line.split(',')) assert.ok(!/^[=+@]/.test(cell) && !/^-[^0-9]/.test(cell), cell);
});

test('M-43: a backup (JSON) still uses the full signature, unchanged', () => {
  const existing = synthetic(), backup = T.decode(JSON.stringify({ formatVersion: 2, entries: existing.map(({ id, ...e }) => e), categories: [] }));
  assert.equal(backup.fromTable, undefined);
  assert.equal(T.missing(existing, backup.entries).length, 0);
});


test('M-43/D-13: CSV money cells and duplicate keys use the money contract, without changing history', () => {
  const amounts = [1.005, 2.675, 149.985, 1.00499999999, 42.12345, 0];
  const existing = amounts.map((totalEarnings, i) => ({ ...synthetic()[0], id: 'round-' + i,
    date: at(11, i + 1), totalEarnings, hourlyRate: totalEarnings, bonus: totalEarnings }));
  const before = JSON.stringify(existing);
  for (const output of [S.csv(existing), S.excel(existing)]) {
    const decoded = T.decode(output).entries;
    decoded.forEach((e, i) => {
      const expected = KisRules.money(1, amounts[i]);
      assert.equal(e.totalEarnings, expected);
      assert.equal(e.hourlyRate, expected);
      assert.equal(e.bonus ?? 0, expected);
    });
    assert.deepEqual(T.missing(existing, decoded, { table: true }), []);
    assert.equal(T.missing(existing, [...decoded, decoded[0]], { table: true }).length, 1);
    assert.equal(T.missing(existing, [{ ...decoded[0], totalEarnings: 1.02 }], { table: true }).length, 1);
  }
  assert.equal(JSON.stringify(existing), before, 'export / comparison never writes rounded values to history');
  const restored = T.decode(T.encode({ entries: existing, categories: [], workers: [] })).entries;
  assert.deepEqual(restored.map(e => e.totalEarnings), amounts, 'JSON retains all stored decimals');
  assert.deepEqual(T.missing(existing, restored), []);
});
