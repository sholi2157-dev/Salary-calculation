// M-42: import a plan-only backup through the actual preview/save handlers. Synthetic data only.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.WorkRuntime = require('../web/runtime.js');
globalThis.WorkCategories = require('../web/categories.js');
globalThis.WorkTransfer = require('../web/transfer.js');
const { createStore } = require('../web/kis/store.js');
require('../web/kis/settings.js');
const series = { id: 'synthetic-plan', category: 'מקום מתוכנן', daysMask: 2, startTime: '09:00', endTime: '17:00',
  hourlyRate: null, currency: '$', fromDate: '2026-10-01', untilDate: '2026-12-31', createdAt: 5 };
const entry = { id: 'saved', category: 'קיים', date: 1760000000000, createdAt: 1760000000000, hours: 2,
  hourlyRate: 40, totalEarnings: 79.13, isPaid: false, currency: '$', notes: 'synthetic', custom: 'retained' };
function setup(categories = [], entries = []) {
  const m = new Map([['work_complete_backup', JSON.stringify({ entries, categories, workers: [] })]]);
  const storage = { getItem: k => m.get(k) ?? null, setItem: (k, v) => m.set(k, String(v)), removeItem: k => m.delete(k) };
  const store = createStore(storage); store.load();
  const nodes = new Map(), el = { querySelector(s) { if (!nodes.has(s)) nodes.set(s, { value: '', checked: false }); return nodes.get(s); } };
  globalThis.KisUi = { sheet: (id, draw) => draw(el, () => {}), infoDot: () => '', toast() {} };
  globalThis.KisHome = { sortedTotals: () => [] };
  globalThis.KisSettings.importSheet({ store });
  return { store, storage, check(backup) {
    el.querySelector('[data-f=t]').value = JSON.stringify({ formatVersion: 3, entries: [], categories: [], series: [series], ...backup });
    el.querySelector('[data-a=check]').onclick();
    assert.equal(el.querySelector('[data-a=save]').disabled, false);
    return el.querySelector('[data-f=sum]').textContent;
  }, save: () => el.querySelector('[data-a=save]').onclick() };
}

test('series-only orphan category is previewed and created once with the existing default rate', () => {
  const h = setup();
  assert.match(h.check({ series: [series, { ...series, id: 'second' }] }), /מקומות חדשים: 1/);
  h.save();
  assert.deepEqual(h.store.data.categories, [{ name: series.category, defaultRate: 40 }]);
  assert.equal(h.store.plans.series.length, 2);
  assert.deepEqual(h.store.data.entries, []);
});

test('a same-name category keeps its rate and metadata without duplication', () => {
  const categories = [{ name: series.category, defaultRate: 17.25, custom: 'retained' }], h = setup(categories);
  assert.doesNotMatch(h.check(), /מקומות חדשים/);
  h.save();
  assert.deepEqual(h.store.data.categories, categories);
  assert.deepEqual(h.store.plans.series, [series]);
});

test('series import leaves existing entries and their saved amounts unchanged', () => {
  const h = setup([{ name: entry.category, defaultRate: 99 }], [entry]);
  const before = JSON.stringify(h.store.data.entries);
  h.check(); h.save();
  assert.equal(JSON.stringify(h.store.data.entries), before);
  assert.deepEqual(JSON.parse(h.storage.getItem('work_complete_backup')).entries, [entry]);
});

test('series category rates preserve zero and explicit rates, and entry-derived categories win', () => {
  for (const hourlyRate of [0, 23.5]) {
    const h = setup(); h.check({ series: [{ ...series, hourlyRate }] }); h.save();
    assert.deepEqual(h.store.data.categories, [{ name: series.category, defaultRate: hourlyRate }]);
  }
  const h = setup();
  h.check({ entries: [{ ...entry, category: series.category }], series: [{ ...series, hourlyRate: 99 }] }); h.save();
  assert.deepEqual(h.store.data.categories, [{ name: series.category, defaultRate: entry.hourlyRate }]);
  assert.equal(h.store.data.entries[0].totalEarnings, entry.totalEarnings);
});
