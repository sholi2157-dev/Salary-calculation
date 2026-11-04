// M-37: marking several shifts as paid (or not) can be undone, and undo gives each one back its own previous mark in
// one commit. Synthetic data only.
const { test } = require('node:test'), assert = require('node:assert/strict'), path = require('node:path'), fs = require('node:fs');
globalThis.WorkRuntime = require('../web/runtime.js');
globalThis.WorkCategories = require('../web/categories.js');
const S = require('../web/kis/store.js');
const rich = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'synthetic', 'rich-v2.json'), 'utf8'));
function memory(init = {}) {
  const m = new Map(Object.entries(init).map(([k, v]) => [k, typeof v === 'string' ? v : JSON.stringify(v)])), writes = [];
  return { m, writes, getItem: k => (m.has(k) ? m.get(k) : null), setItem(k, v) { writes.push(k); m.set(k, String(v)); }, removeItem(k) { m.delete(k); },
    key: i => [...m.keys()][i] ?? null, get length() { return m.size; } };
}
test('bulk paid then undo restores every shift\'s own previous mark, and leaves the others alone', () => {
  const st = memory(rich), store = S.createStore(st); store.load();
  const rows = store.data.entries.slice(0, 4);
  store.update(d => { d.entries[0].isPaid = true; d.entries[1].isPaid = false; d.entries[2].isPaid = true; d.entries[3].isPaid = false; });
  const others = store.data.entries.slice(4).map(e => [e.id, e.isPaid]);
  const ids = rows.slice(0, 3).map(e => e.id);
  const was = Object.fromEntries(store.data.entries.filter(e => ids.includes(e.id)).map(e => [e.id, e.isPaid === true]));
  store.setPaid(ids, true);
  assert.deepEqual(store.data.entries.slice(0, 3).map(e => e.isPaid), [true, true, true]);
  const before = st.writes.length;
  store.restorePaid(was);
  assert.deepEqual(store.data.entries.slice(0, 4).map(e => e.isPaid), [true, false, true, false]);
  assert.deepEqual(store.data.entries.slice(4).map(e => [e.id, e.isPaid]), others);
  const again = S.createStore(st); again.load();
  assert.deepEqual(again.data.entries.slice(0, 4).map(e => e.isPaid), [true, false, true, false], 'saved');
  assert.ok(st.writes.length - before >= 1);
});
test('undo does not touch amounts or any other field', () => {
  const st = memory(rich), store = S.createStore(st); store.load();
  const snapshot = JSON.stringify(store.data.entries.map(({ isPaid, ...e }) => e));
  const ids = store.data.entries.map(e => e.id), was = Object.fromEntries(store.data.entries.map(e => [e.id, e.isPaid === true]));
  store.setPaid(ids, false); store.restorePaid(was);
  assert.equal(JSON.stringify(store.data.entries.map(({ isPaid, ...e }) => e)), snapshot);
  assert.deepEqual(store.data.entries.map(e => e.isPaid === true), Object.values(was));
});
