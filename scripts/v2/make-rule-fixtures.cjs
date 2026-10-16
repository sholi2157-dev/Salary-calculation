// Generates shared calculation test cases from the approved 2.0 prototype engine.
// Usage: node scripts/v2/make-rule-fixtures.cjs <core.js> <out.json>
// Android (Kotlin) and Web tests both assert these exact results, so the platforms cannot drift.
const fs = require('fs');
const [coreFile, out] = process.argv.slice(2);
global.localStorage = { getItem: () => null, setItem: () => {} };
eval(fs.readFileSync(coreFile, 'utf8') + ';global.Core=Core');
const C = global.Core;
C.load('fixtures');
const S = C.state; S.shifts.length = 0; S.rules.length = 0;

const cases = { roundedEnd: [], span: [], pay: [], quiet: [], seriesDates: [], bulkRate: [] };

// Rounded-down end time: floor to the previous 30-minute boundary.
for (const m of [0, 1, 29, 30, 31, 59, 600, 1182, 1199, 1439]) {
  const h = Math.floor(m / 60), mm = m % 60;
  cases.roundedEnd.push({ nowMin: m, expected: C.fromMin(Math.floor(m / 30) * 30), note: `${C.pad(h)}:${C.pad(mm)}` });
}
// Span, overnight, break, invalid.
for (const [s, e, b] of [['17:00', '22:30', 0], ['22:00', '06:00', 0], ['08:00', '07:30', 0], ['09:00', '09:00', 0], ['09:00', '13:30', 30], ['10:00', '10:20', 30], ['23:45', '00:15', 0], ['00:00', '23:59', 0], ['06:00', '23:00', 60]]) {
  const r = C.span(s, e, b);
  cases.span.push({ start: s, end: e, breakMin: b, minutes: r.minutes, overnight: r.overnight, invalid: r.invalid, long: !!r.long });
}
// Pay evaluation for every entry type.
const pay = (d) => { const draft = { ...C.newDraft(), ...d }; if (d.mode) C.setMode(draft, d.mode); Object.assign(draft, d); const r = C.evaluate(draft); return { input: d, total: r.total, bonus: r.bonus, minutes: r.span ? r.span.minutes : null, hasErrors: r.errors.length > 0 }; };
[
  { mode: 'clock', start: '17:00', end: '22:30', breakMin: 0, rate: 52, rateUnknown: false, bonus: 0 },
  { mode: 'clock', start: '17:00', end: '23:00', breakMin: 30, rate: 40, rateUnknown: false, bonus: 25 },
  { mode: 'clock', start: '22:00', end: '06:00', breakMin: 0, rate: 20, rateUnknown: false, bonus: 30 },
  { mode: 'clock', start: '18:00', end: '01:00', breakMin: 0, rate: null, rateUnknown: true, bonus: 0 },
  { mode: 'clock', start: '18:00', end: '01:00', breakMin: 0, rate: null, rateUnknown: true, bonus: 40 },
  { mode: 'clock', start: null, end: '22:00', breakMin: 0, rate: 52, rateUnknown: false, bonus: 0 },
  { mode: 'clock', start: '09:00', end: '09:00', breakMin: 0, rate: 52, rateUnknown: false, bonus: 0 },
  { mode: 'hours', hours: '6', rate: 20, rateUnknown: false, bonus: 30 },
  { mode: 'hours', hours: '3.5', rate: 35, rateUnknown: false, bonus: 0 },
  { mode: 'hours', hours: '0', rate: 35, rateUnknown: false, bonus: 0 },
  { mode: 'hours', hours: '25', rate: 35, rateUnknown: false, bonus: 0 },
  { mode: 'job', amount: '50', amountUnknown: false, hours: '' },
  { mode: 'job', amount: '80', amountUnknown: false, hours: '2.5' },
  { mode: 'job', amount: '', amountUnknown: true, hours: '' },
  { mode: 'job', amount: '0', amountUnknown: false, hours: '' },
  { mode: 'group', start: '17:30', end: '23:30', breakMin: 0, rate: 45, rateUnknown: false, bonus: 40, bonusTarget: 'all' },
  { mode: 'group', start: '17:30', end: '23:30', breakMin: 0, rate: 45, rateUnknown: false, bonus: 40, bonusTarget: 'others' },
  { mode: 'group', start: '17:30', end: '23:30', breakMin: 0, rate: 45, rateUnknown: false, bonus: 40, bonusTarget: 'me' },
].forEach(d => cases.pay.push(pay(d)));
// Quiet hours: a notice inside the window is delayed to its end.
S.settings.quiet = true;
for (const [f, t] of [['22:00', '07:00'], ['13:00', '15:00'], ['08:00', '08:00']]) {
  S.settings.quietFrom = f; S.settings.quietTo = t;
  for (const at of [0, 360, 419, 420, 600, 780, 840, 900, 1319, 1320, 1439]) cases.quiet.push({ from: f, to: t, atMin: at, deliverAt: C.quietDelay(at) });
}
S.settings.quiet = false; cases.quiet.push({ from: '22:00', to: '07:00', atMin: 1380, off: true, deliverAt: C.quietDelay(1380) });
// Recurring series: which dates a rule produces (no clashes in an empty store).
for (const r of [{ days: [0, 2, 4], from: '2026-10-15', until: '2026-11-10' }, { days: [5, 6], from: '2026-12-25', until: '2027-01-10' }, { days: [1], from: '2027-02-27', until: '2027-03-15' }, { days: [], from: '2026-10-01', until: '2026-10-31' }]) {
  const rule = { ...r, start: '17:00', end: '22:30' };
  cases.seriesDates.push({ ...r, dates: C.previewRule(rule).dates });
}
// Bulk rate completion: only shifts with a missing rate change; jobs and known rates never do.
const mk = (id, extra) => ({ id, cat: 'c1', date: '2026-10-10', start: '17:00', end: '22:30', breakMin: 0, rate: 52, currency: '₪', bonus: 0, total: 286, paid: false, notes: '', group: null, auto: false, ruleKey: null, ...extra });
const list = [mk('a', { rate: null, total: null }), mk('b', { rate: null, total: null, bonus: 40, start: '18:00', end: '01:00' }), mk('c', {}), mk('d', { kind: 'job', start: null, end: null, hours: null, rate: null, total: null }), mk('e', { start: null, end: null, hours: 3.5, rate: null, total: null })];
S.shifts.push(...JSON.parse(JSON.stringify(list)));
const pv = C.bulkRatePreview(list.map(s => s.id), 60);
cases.bulkRate.push({ rate: 60, shifts: list, updated: pv.target.map(s => ({ id: s.id, total: s.newTotal })), skipped: pv.skipped });

fs.writeFileSync(out, JSON.stringify({ $comment: 'Generated by scripts/v2/make-rule-fixtures.cjs from the approved prototype engine. Do not edit by hand.', ...cases }, null, 1) + '\n');
console.log(Object.fromEntries(Object.entries(cases).map(([k, v]) => [k, v.length])));
