// 2.3.0 (audit CW-13): a new-shift draft belongs to the account whose storage it was typed in.
const { test } = require('node:test'), assert = require('node:assert/strict');
globalThis.KisRules = require('../web/v2/rules.js'); globalThis.KisPay = require('../web/v2/pay.js');
globalThis.KisUi = { startOfDay: ms => { const t = new Date(ms); t.setHours(0, 0, 0, 0); return +t; } };
require('../web/kis/editor.js'); require('../web/kis/account.js');
const E = globalThis.KisEditor, A = globalThis.KisAccount;
const browser = () => { const m = new Map(); return { getItem: k => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: k => m.delete(k) }; };

test('account B never opens or saves account A’s draft', () => {
  const ls = browser();
  const draftA = { mode: 'HOURS', category: 'מסעדה', notes: 'פרטי של A', hours: '2:00', rate: '50' };
  // A is the first (bound) account: its storage is the browser's own keys.
  ls.setItem('work_account_v2', JSON.stringify({ bound: 'uid-A', current: 'uid-A' }));
  A.storageFor(ls).setItem(E.DRAFT, JSON.stringify(draftA));
  assert.equal(E.pendingDraft(A.storageFor(ls)).notes, 'פרטי של A');
  // Switching to B in the same browser: B's storage has no draft.
  ls.setItem('work_account_v2', JSON.stringify({ bound: 'uid-A', current: 'uid-B' }));
  assert.equal(E.pendingDraft(A.storageFor(ls)), null);
  // B types its own draft; back on A, A's draft is still A's.
  A.storageFor(ls).setItem(E.DRAFT, JSON.stringify({ ...draftA, notes: 'של B' }));
  ls.setItem('work_account_v2', JSON.stringify({ bound: 'uid-A', current: 'uid-A' }));
  assert.equal(E.pendingDraft(A.storageFor(ls)).notes, 'פרטי של A');
});
