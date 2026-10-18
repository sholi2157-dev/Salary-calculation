// Generates the shared summary cases from web/v2/summary.js. Android (V2SummaryTest) and Web (v2-summary.test.cjs)
// both assert these exact results. Usage: node scripts/v2/make-summary-fixtures.cjs <out.json>
const fs = require('fs');
const S = require('../../web/v2/summary.js');
const row = (date, start, end, hours, o = {}) => ({ date, start, end, hours, kind: 'shift', payKnown: true, total: 0, currency: '₪', paid: false, category: 'מסעדה', ...o });
const paid = (r, rate) => ({ ...r, total: Math.round(r.hours * rate * 100) / 100 });
const mixed = [
  paid(row('2026-09-02', '17:00', '23:30', 6.5), 50),
  paid(row('2026-09-04', '22:00', '06:00', 8, { paid: true }), 60),
  paid(row('2026-09-05', '10:00', '14:00', 4, { category: 'שיעורים' }), 120),
  row('2026-09-09', null, null, 3, { category: 'הובלות', currency: '$', kind: 'job', total: 250 }),
  row('2026-09-10', '09:00', '13:30', 4.5, { category: 'שיעורים', payKnown: false }),
  paid(row('2026-10-01', '17:00', '22:00', 5), 50),
  paid(row('2026-10-03', '08:00', '16:00', 8, { category: 'שיעורים', paid: true }), 120),
  paid(row('2026-10-07', '18:00', '01:00', 7, { category: 'הובלות', currency: '$' }), 30),
  row('2026-10-09', '12:00', '15:00', 3, { payKnown: false }),
  paid(row('2026-10-14', '17:30', '23:15', 5.75), 52),
  paid(row('2026-10-20', '17:00', '22:00', 5), 50),
  paid(row('2025-12-31', '20:00', '02:00', 6), 40),
];
const persona = (key, rows) => ({ name: 'persona_' + key, today: '2026-10-31', scope: '2026-10', rows });
const cases = [
  { name: 'running_month_compares_same_days', today: '2026-10-15', scope: '2026-10', rows: mixed },
  { name: 'full_previous_month', today: '2026-10-15', scope: '2026-09', rows: mixed },
  { name: 'whole_year', today: '2026-10-15', scope: '2026', rows: mixed },
  { name: 'year_across_new_year', today: '2026-10-15', scope: '2025', rows: mixed },
  { name: 'empty_month', today: '2026-10-15', scope: '2026-08', rows: mixed },
  persona('owl', [paid(row('2026-10-02', '21:00', '03:00', 6), 40), paid(row('2026-10-06', '12:00', '16:00', 4), 40)]),
  persona('bird', [paid(row('2026-10-05', '06:00', '12:00', 6), 40), paid(row('2026-10-06', '12:00', '15:00', 3), 40)]),
  persona('weekend', [paid(row('2026-10-02', '10:00', '15:00', 5), 40), paid(row('2026-10-03', '10:00', '13:00', 3), 40), paid(row('2026-10-05', '10:00', '15:00', 5), 40), paid(row('2026-10-06', '10:00', '15:00', 5), 40)]),
  persona('marathon', [paid(row('2026-10-05', '10:00', '19:00', 9, { category: 'א' }), 40), paid(row('2026-10-06', '10:00', '18:00', 8, { category: 'ב' }), 40)]),
  persona('loyal', [paid(row('2026-10-05', '10:00', '14:00', 4), 40), paid(row('2026-10-06', '10:00', '14:00', 4), 40), paid(row('2026-10-07', '10:00', '13:00', 3, { category: 'אחר' }), 40)]),
  persona('juggler', [paid(row('2026-10-05', '10:00', '14:00', 4, { category: 'א' }), 40), paid(row('2026-10-06', '10:00', '14:00', 4, { category: 'ב' }), 40), paid(row('2026-10-07', '10:00', '14:00', 4, { category: 'ג' }), 40)]),
  { name: 'future_rows_ignored', today: '2026-10-05', scope: '2026-10', rows: mixed },
];
for (const c of cases) c.expected = S.summary(c.rows, c.scope, c.today);
for (const k of ['owl', 'bird', 'weekend', 'marathon', 'loyal', 'juggler']) if (cases.find(c => c.name === 'persona_' + k).expected.persona !== k) throw new Error('persona case ' + k);
fs.writeFileSync(process.argv[2], JSON.stringify({ cases }, null, 1) + '\n');
console.log(cases.length + ' cases');
