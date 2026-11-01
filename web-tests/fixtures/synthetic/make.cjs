/*
 * Deterministic synthetic datasets for the 2.3.0 remediation (Stage 0). No real person's data.
 * TZ=Asia/Jerusalem node web-tests/fixtures/synthetic/make.cjs  → rewrites rich-v2.json and legacy-noid.json in this folder.
 * Dates are fixed (local noon, 2026) so results never depend on the day the tests run.
 */
const fs = require('node:fs'), path = require('node:path');
const at = (y, m, d, h = 12) => new Date(y, m - 1, d, h, 0, 0, 0).getTime();
const row = (id, category, date, hours, rate, cur = '₪', x = {}) => ({ id, category, date, createdAt: date + 1000, isTimeRange: false, startTime: null, endTime: null,
  hours, hourlyRate: rate, totalEarnings: Math.round(hours * rate * 100) / 100, isPaid: false, notes: '', currency: cur, isGroupShift: false,
  employerRate: null, workerRate: null, groupWorkersJson: '', ...x });
const categories = [['עצמאי', 40], ['מסעדה', 50], ['שיעורים פרטיים במתמטיקה ופיזיקה לתיכון', 120], ['Tech Support', 30], ['הובלות', 60], ['אירועים', 70], ['מוקד לילה', 48]]
  .map(([name, defaultRate]) => ({ name, defaultRate }));
const entries = [
  row('r01', 'מסעדה', at(2026, 3, 1), 6, 50, '₪', { isTimeRange: true, startTime: '11:00', endTime: '17:00' }),
  row('r02', 'מסעדה', at(2026, 3, 2), 3.5, 50, '₪', { isTimeRange: true, startTime: '22:00', endTime: '02:00', notes: 'לילה, חצי שעה הפסקה' }),
  row('r03', 'שיעורים פרטיים במתמטיקה ופיזיקה לתיכון', at(2026, 3, 3), 100 / 60, 120, '₪', { totalEarnings: 200 }),
  row('r04', 'Tech Support', at(2026, 3, 4), 4, 30, '$', { isPaid: true }),
  row('r05', 'מסעדה', at(2026, 3, 5), 5, 0, '₪', { payKnown: false, totalEarnings: 0 }),
  row('r06', 'הובלות', at(2026, 3, 6), 0, 0, '₪', { kind: 'job', totalEarnings: 850, notes: 'הובלת דירה' }),
  row('r07', 'הובלות', at(2026, 3, 7), 2, 0, '₪', { kind: 'job', payKnown: false, totalEarnings: 0 }),
  row('r08', 'אירועים', at(2026, 3, 8), 5, 70, '₪', { isTimeRange: true, startTime: '18:00', endTime: '23:00', isGroupShift: true, employerRate: 70, workerRate: 50,
    bonus: 20, passThrough: 45, totalEarnings: 370, groupWorkersJson: JSON.stringify([{ name: 'יוסי', hours: 5, rate: 50 }, { name: 'מנחם מענדל', hours: 4, rate: 50, paid: true }]) }),
  row('r09', 'מסעדה', at(2026, 3, 9), 1.234567, 34.123456, '₪', { totalEarnings: 42.12345, notes: 'סכום מיובא חריג: אסור לחשב מחדש' }),
  row('r10', 'עצמאי', at(2026, 3, 10), 7.75, 40, '₪', { isPaid: true, notes: 'א'.repeat(300) }),
  row('r11', 'מסעדה', at(2026, 3, 11), 1.5, 99.99, '₪', { totalEarnings: 149.98, notes: 'CAL-02: נשמר 149.98' }),
  row('r12', 'מוקד לילה', at(2026, 3, 28), 9, 48, '₪', { isTimeRange: true, startTime: '22:00', endTime: '07:00', notes: 'לילה של מעבר לשעון קיץ' }),
  row('r13', 'אירועים', at(2026, 4, 2), 3, 70.01, '₪', { isGroupShift: true, employerRate: 70.01, workerRate: 50.02, totalEarnings: 210.03,
    groupWorkersJson: JSON.stringify([{ name: 'עובד א', hours: 1 / 60, rate: 50.02 }]) }),
  row('r14', 'Tech Support', at(2026, 4, 3), 2.5, 30, '$', { bonus: 10, totalEarnings: 85 }),
  row('r15', 'מסעדה', at(2027, 1, 4, 12), 5, 50, '₪', { notes: 'עתידית' }),
];
const rich = { entries, categories, workers: [{ name: 'יוסי' }, { name: 'מנחם מענדל' }],
  webPreferences: { mainCurrency: '₪', defaultCategory: 'מסעדה', categoryCurrencies: { 'Tech Support': '$' } } };
const plans = { series: [{ id: 'series-rich', category: 'מסעדה', daysMask: 42, startTime: '09:00', endTime: '17:00', hourlyRate: 50, currency: '₪', fromDate: '2026-01-01', untilDate: '2027-12-31' }],
  exceptions: [{ seriesId: 'series-rich', date: '2026-02-02', cancelled: true }, { seriesId: 'series-rich', date: '2026-02-04', hold: true }] };
// D01: two identical rows without id, in the oldest storage format ("user_work_shifts").
const legacyRow = { category: 'מסעדה', date: at(2025, 12, 1), hours: 4, hourlyRate: 45, totalEarnings: 180, isPaid: false, notes: '', currency: '₪' };
const legacy = [legacyRow, { ...legacyRow }, { ...legacyRow, date: at(2025, 12, 2), hours: 3, totalEarnings: 135 }];
const dir = __dirname;
fs.writeFileSync(path.join(dir, 'rich-v2.json'), JSON.stringify({ work_complete_backup: rich, work_series_v1: plans }, null, 1) + '\n');
fs.writeFileSync(path.join(dir, 'legacy-noid.json'), JSON.stringify({ user_work_shifts: legacy }, null, 1) + '\n');
module.exports = { big(n = 1000) { const out = []; for (let i = 0; i < n; i++) { const c = categories[i % categories.length]; out.push(row('b' + i, c.name, at(2025, 1, 1) + i * 43_200_000, 2 + (i % 7) * 0.25, c.defaultRate, i % 5 ? '₪' : '$', { isPaid: i % 3 === 0 })); } return { ...rich, entries: out }; } };
