// Synthetic timers only: a confirmation must not cancel a replacement from another tab.
const { test } = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm');

function element() {
  const children = new Map();
  return { innerHTML: '', textContent: '', dataset: {}, isConnected: true, hidden: true,
    querySelector(s) { if (!children.has(s)) children.set(s, element()); return children.get(s); },
    querySelectorAll: () => [], addEventListener() {}, setAttribute() {}, focus() {},
    remove() { this.isConnected = false; } };
}
function setup() {
  const values = new Map(), mutations = [], delays = [];
  const storage = { getItem: k => values.get(k) ?? null,
    setItem(k, v) { mutations.push(k); values.set(k, String(v)); },
    removeItem(k) { mutations.push(k); values.delete(k); } };
  const page = element(), toast = element(); let scrim;
  const context = vm.createContext({ localStorage: storage,
    document: { readyState: 'loading', addEventListener() {},
      getElementById: id => id === 'toast' ? toast : page,
      querySelector: () => element(), querySelectorAll: () => [],
      createElement() { scrim = element(); scrim.firstChild = element(); return scrim; },
      body: { append() {} } },
    setInterval() {}, clearInterval() {}, setTimeout(fn, delay) { delays.push(delay); }, clearTimeout() {},
    KisAccount: { storageFor: s => s },
    KisPlans: { awaitingReview: () => [], occurrences: () => [], isAwaiting: () => false, bind() {} },
    KisPlan: { dateOf: () => '2026-11-01' },
    KisRules: { daysFromCivil: () => 0, civilFromDays: () => '2026-11-07' },
    KisPay: { totals: () => ({}), pending: () => [], unpaidAging: () => [], agingLine: () => '' },
    KisSettings: { unread: () => false }, KisSummaryUi: { fresh: () => [] },
    KisHistory: { state: { selection: new Set() } },
  });
  for (const file of ['runtime.js', 'categories.js', 'kis/store.js', 'kis/ui.js', 'kis/home.js', 'kis/app.js']) {
    vm.runInContext(fs.readFileSync(require.resolve('../web/' + file), 'utf8'), context);
  }
  const app = context.Kis, key = context.WorkRuntime.timerKey(null);
  app.store.load();
  const original = { id: 'shift-a', category: 'בדיקה', rate: 0, currency: '₪', startedAt: 1793700000000 };
  storage.setItem(key, JSON.stringify(original));
  const draw = () => context.KisHome.render(page, app);
  draw();
  return { app, storage, key, original, mutations, delays, toast, draw,
    open() { page.querySelector('[data-act=cancel]').onclick(); return scrim; },
    confirm() { scrim.firstChild.querySelector('[data-a=ok]').onclick(); } };
}

for (const replacement of ['shift-b', null]) {
  test(`confirm preserves storage when the live shift ${replacement ? 'was replaced' : 'no longer exists'}`, () => {
    const h = setup(), dialog = h.open();
    if (replacement) h.storage.setItem(h.key, JSON.stringify({ ...h.original, id: replacement }));
    else h.storage.removeItem(h.key);
    h.draw(); // storage sync redraws Home while the original dialog stays open
    const before = h.storage.getItem(h.key), writes = h.mutations.length;
    h.confirm();
    assert.equal(h.storage.getItem(h.key), before);
    assert.equal(h.mutations.length, writes, 'stale confirmation writes nothing');
    assert.equal(dialog.isConnected, false, 'confirmation closes');
    assert.match(h.toast.innerHTML, /המשמרת הפעילה השתנתה, לא בוטלה/);
    assert.equal(h.delays.at(-1), 2600, 'short informational toast, without Undo');
  });
}

test('normal cancel keeps the six-second Undo and restores the exact captured timer', () => {
  const h = setup(), before = h.storage.getItem(h.key), dialog = h.open();
  h.confirm();
  assert.equal(h.storage.getItem(h.key), null);
  assert.equal(dialog.isConnected, false);
  assert.match(h.toast.innerHTML, /המשמרת הפעילה בוטלה/);
  assert.equal(h.delays.at(-1), 6000);
  h.toast.querySelector('button').onclick();
  assert.equal(h.storage.getItem(h.key), before);
});

test('captures the current live id when opening, even before a storage-event redraw', () => {
  const h = setup();
  const replacement = JSON.stringify({ ...h.original, id: 'shift-b' });
  h.storage.setItem(h.key, replacement);
  h.open(); h.confirm();
  assert.equal(h.storage.getItem(h.key), null);
  h.toast.querySelector('button').onclick();
  assert.equal(h.storage.getItem(h.key), replacement);
});
