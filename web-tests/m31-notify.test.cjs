const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function setup(local = {}, entries = [{ date: 1, payKnown: false }]) {
  const recent = [];
  const context = {
    localStorage: { getItem: () => null },
    KisUi: { upcoming: e => !!e.future, MONTHS: ['חודש'] },
    KisPlan: { dateOf: () => '2026-01-30' },
    KisSummary: { recapNew: () => ({ month: '2026-01' }) },
    KisSummaryUi: { monthOf: () => 0 },
  };
  vm.runInNewContext(fs.readFileSync('web/kis/settings.js', 'utf8'), context);
  const store = { local: { ...local }, data: { entries }, setLocal(k, v) { this.local[k] = v; }, addRecent(n) { recent.push(n); } };
  return { due: () => Array.from(context.KisSettings.dueReminders({ store }), n => n.kind), store, recent };
}

test('M-31: reminder types are independently gated and never modify historical records', () => {
  for (const monthly of [false, true]) for (const rate of [false, true]) {
    const app = setup({ notifyMonthly: monthly, notifyRate: rate });
    const before = JSON.stringify(app.store.data);
    assert.deepEqual(app.due(), [...(monthly ? ['monthly'] : []), ...(rate ? ['rate'] : [])]);
    assert.deepEqual(app.due(), [], 'already delivered reminders are not repeated');
    assert.equal(JSON.stringify(app.store.data), before);
    if (!monthly) assert.equal(app.store.local.lastMonthly, undefined);
    if (!rate) assert.equal(app.store.local.lastRate, undefined);
  }
});

test('M-31: disabled reminders do not consume their delivery window', () => {
  const app = setup();
  assert.deepEqual(app.due(), []);
  app.store.local.notifyRate = true;
  assert.deepEqual(app.due(), ['rate']);
  app.store.local.notifyMonthly = true;
  assert.deepEqual(app.due(), ['monthly']);
});

test('M-31: empty, paid-known and future-only histories produce no missing-rate reminders', () => {
  for (const entries of [[], [{ date: 1, payKnown: true }], [{ date: 1, payKnown: false, future: true }]]) {
    const app = setup({ notifyRate: true }, entries);
    assert.deepEqual(app.due(), []);
  }
});
