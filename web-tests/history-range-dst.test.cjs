// Synthetic shifts only. Pin the timezone before loading the production module.
const previousTZ = process.env.TZ;
process.env.TZ = 'Asia/Jerusalem';
const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

after(() => {
  if (previousTZ === undefined) delete process.env.TZ;
  else process.env.TZ = previousTZ;
});

// Isolate History's browser global from other unit tests; use real local dates.
const context = vm.createContext({
  KisUi: {
    startOfDay(ms) {
      const date = new Date(ms);
      date.setHours(0, 0, 0, 0);
      return date.getTime();
    },
  },
});
vm.runInContext(fs.readFileSync(require.resolve('../web/kis/history.js'), 'utf8'), context);
const history = context.KisHistory;
const midnight = day => new Date(`${day}T00:00:00`).getTime();
const shift = (id, day, time) => ({
  id, category: 'בדיקה סינתטית', date: new Date(`${day}T${time}:00`).getTime(),
  createdAt: 0, hours: 1, hourlyRate: 60, totalEarnings: 60, currency: '₪', isPaid: false,
});

for (const [name, from, to, previous, next] of [
  ['spring DST day', '2026-03-27', '2026-03-27', '2026-03-26', '2026-03-28'],
  ['autumn DST day', '2026-10-25', '2026-10-25', '2026-10-24', '2026-10-26'],
  ['normal day', '2026-02-10', '2026-02-10', '2026-02-09', '2026-02-11'],
  ['last day of month', '2026-04-30', '2026-04-30', '2026-04-29', '2026-05-01'],
  ['last day of year', '2026-12-31', '2026-12-31', '2026-12-30', '2027-01-01'],
  ['multi-day range ending on spring DST', '2026-03-25', '2026-03-27', '2026-03-24', '2026-03-28'],
  ['multi-day range ending on autumn DST', '2026-10-23', '2026-10-25', '2026-10-22', '2026-10-26'],
]) {
  test(`History range includes 23:30 and excludes next-day 00:30: ${name}`, () => {
    const rows = [shift('before', previous, '23:30'), shift('first', from, '00:30'),
      shift('last', to, '23:30'), shift('after', next, '00:30')];
    const filter = { period: 'RANGE', from: midnight(from), to: midnight(to),
      pay: 'ALL', category: null, currency: null, sort: 'NEWEST' };
    assert.deepEqual(Array.from(history.apply(rows, filter, ''), row => row.id), ['last', 'first']);
  });
}
