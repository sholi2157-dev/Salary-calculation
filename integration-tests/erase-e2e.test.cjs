// M-13 end to end against the Firebase Emulator Suite: the real Firebase SDK, the project's own firestore.rules, several
// devices of one synthetic account (example.invalid). Never a real account or production data.
// Run: npx firebase emulators:exec --project demo-salary-sync --only auth,firestore --config firebase.test.json "node --test integration-tests/erase-e2e.test.cjs"
const { test } = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const firebase = require('firebase/compat/app'); require('firebase/compat/auth'); require('firebase/compat/firestore');
globalThis.WorkRuntime = require('../web/runtime.js'); globalThis.WorkCategories = require('../web/categories.js'); globalThis.WorkTransfer = require('../web/transfer.js');
globalThis.WorkSyncProtocol = require('../web/sync/protocol.js');
const { createStore } = require('../web/kis/store.js'), { createSync } = require('../web/sync/engine.js'), { firestoreTransport } = require('../web/sync/firestore.js');
const E = require('../web/sync/erase.js');

function memory() { const m = new Map(); return { getItem: k => m.has(k) ? m.get(k) : null, setItem: (k, v) => m.set(k, String(v)), removeItem: k => m.delete(k),
  get length() { return m.size; }, key: i => [...m.keys()][i] ?? null }; }
const shift = (o = {}) => ({ category: 'מסעדה', date: 1760000000000, createdAt: 1760000000500, isTimeRange: true, startTime: '09:00', endTime: '17:00', hours: 8, hourlyRate: 40.25,
  totalEarnings: 322.33, isPaid: false, notes: '', currency: '₪', isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '', ...o });
const password = 'Synthetic-test-password-123!';
let n = 0;
const apps = [];
function app() { const a = firebase.initializeApp({ apiKey: 'fake-emulator-key', projectId: 'demo-salary-sync' }, 'erase-' + (++n)); apps.push(a);
  a.auth().useEmulator('http://127.0.0.1:9099', { disableWarnings: true }); a.firestore().useEmulator('127.0.0.1', 8080); return a; }
async function account() { const a = app(), email = crypto.randomUUID() + '@example.invalid'; await a.auth().createUserWithEmailAndPassword(email, password); return { email, first: a, uid: a.auth().currentUser.uid }; }
async function device(acc, local = memory()) {
  const a = acc.first && !acc.firstUsed ? (acc.firstUsed = true, acc.first) : app();
  if (!a.auth().currentUser) await a.auth().signInWithEmailAndPassword(acc.email, password);
  const store = createStore(local); store.load();
  const transport = firestoreTransport(firebase, a);
  return { app: a, local, store, transport, sync: createSync({ store, storage: local, uid: acc.uid, deviceId: a.name, transport, currentUid: () => a.auth().currentUser?.uid }) };
}
/** What the account holds, read by a separate fresh client straight from the server. */
async function live(acc) {
  const a = app(); await a.auth().signInWithEmailAndPassword(acc.email, password);
  const snap = await a.firestore().collection('users').doc(acc.uid).collection('records_v1').get({ source: 'server' });
  const docs = snap.docs.map(d => ({ id: d.id, ...d.data() }));
  return { docs, live: docs.filter(d => !d.deleted), entries: docs.filter(d => !d.deleted && d.type === 'entry') };
}
async function seed(d) {
  d.store.update(x => { x.categories.push({ name: 'מסעדה', defaultRate: 40.25 }); x.workers.push({ name: 'יוסי' }); });
  d.store.addEntries([shift(), shift({ date: 1760086400000, createdAt: 1760086400500, totalEarnings: 300.5 })]);
  d.store.updatePlans(p => { p.series.push({ id: 'S1', category: 'מסעדה', daysMask: 2, startTime: '09:00', endTime: '17:00', hourlyRate: 40, currency: '₪', fromDate: '2025-06-01', untilDate: '2025-07-01', createdAt: 1 }); });
}
const offline = d => d.app.firestore().disableNetwork(), online = d => d.app.firestore().enableNetwork();

test('Firebase emulator: account-wide deletion with several devices, an offline one, reconnecting and a new device', { timeout: 120000 }, async () => {
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-salary-sync');
  const acc = await account(), A = await device(acc), B = await device(acc), C = await device(acc);
  await seed(A); await A.sync.sync(); await B.sync.sync(); await C.sync.sync();
  B.store.addEntries([shift({ notes: 'רק במכשיר B', createdAt: 9 })]); await B.sync.sync(); // A has not seen it
  await offline(C); // C is offline during the deletion
  const before = await live(acc); assert.ok(before.entries.length === 3);
  const r = await E.eraseAccount({ store: A.store, sync: A.sync, transport: A.transport, uid: acc.uid });
  assert.deepEqual(r, { ok: true, left: 0 });
  const after = await live(acc);
  assert.equal(after.entries.length, 0, 'no shift left in the account'); assert.equal(await E.remoteLeft(A.transport, acc.uid), 0);
  assert.ok(after.docs.filter(d => d.type === 'entry').every(d => d.deleted && d.payload == null && d.schema === 1), 'ordinary tombstones (schema 1)');
  await B.sync.sync(); assert.equal(B.store.data.entries.length, 0); assert.equal(B.store.plans.series.length, 0);
  // C comes back online: it empties and puts nothing back.
  await online(C); await C.sync.sync(); await C.sync.sync();
  assert.equal(C.store.data.entries.length, 0); assert.equal((await live(acc)).entries.length, 0, 'nothing reappeared after C reconnected');
  // A device that signs in after the deletion gets nothing.
  const D = await device(acc); await D.sync.sync(); assert.equal(D.store.data.entries.length, 0);
  // Keep the tombstones as the fixture the Android test feeds to its sync engine (2.2.1 compatibility).
  fs.writeFileSync(path.join(__dirname, 'fixtures-erase-tombstones.json'), JSON.stringify(after.docs.filter(d => d.type === 'entry')
    .map(d => ({ syncId: d.id, type: d.type, version: d.version, operation: d.operation, deleted: d.deleted, payload: d.payload ?? null })), null, 1) + '\n');
});

test('Firebase emulator: offline changes on another device never bring deleted shifts back silently', { timeout: 120000 }, async () => {
  const acc = await account(), A = await device(acc), B = await device(acc);
  await seed(A); await A.sync.sync(); await B.sync.sync();
  await offline(B);
  B.store.update(d => { d.entries[0].notes = 'שינוי בלי חיבור'; }); // an edit of a shift that will be deleted
  B.store.addEntries([shift({ notes: 'משמרת חדשה בלי חיבור', createdAt: 77, date: 1760172800000 })]); // a new shift never in the account
  await E.eraseAccount({ store: A.store, sync: A.sync, transport: A.transport, uid: acc.uid });
  await online(B); await B.sync.sync();
  const after = await live(acc);
  assert.equal(B.sync.conflicts().length, 1, 'the edited shift waits for a choice on B');
  assert.ok(!after.entries.some(d => d.payload.includes('שינוי בלי חיבור')), 'the edited, deleted shift is not back in the account');
  assert.equal(after.entries.length, 1, 'only the shift that was created offline and never deleted reaches the account');
  assert.ok(after.entries[0].payload.includes('משמרת חדשה בלי חיבור'));
  // Choosing "keep the deletion" on B removes the edited copy there as well.
  B.sync.resolve(B.sync.conflicts()[0].syncId, false); await B.sync.sync();
  assert.ok(!B.store.data.entries.some(e => e.notes === 'שינוי בלי חיבור'));
});

test('Firebase emulator: an interrupted deletion reports it, deletes nothing back, and a retry or a normal sync finishes it', { timeout: 120000 }, async () => {
  const acc = await account(), A = await device(acc);
  await seed(A); A.store.addEntries(Array.from({ length: 6 }, (_, i) => shift({ createdAt: 100 + i, date: 1760200000000 + i * 864e5 }))); await A.sync.sync();
  // The connection drops after three writes.
  let writes = 0; const real = A.transport.exchange;
  A.transport.exchange = async (...a) => { if (++writes > 3) throw Error('network lost'); return real.apply(A.transport, a); };
  await assert.rejects(E.eraseAccount({ store: A.store, sync: A.sync, transport: A.transport, uid: acc.uid }));
  const mid = await live(acc);
  assert.ok(mid.entries.length > 0 && mid.entries.length < 8, 'part deleted: ' + mid.entries.length);
  A.transport.exchange = real;
  // The app reopens on the same storage: its outbox still holds the deletions, and an ordinary sync completes them.
  const again = await device(acc, A.local); await again.sync.sync();
  assert.equal((await live(acc)).entries.length, 0);
  // And the explicit retry is harmless when everything is already gone.
  assert.deepEqual(await E.eraseAccount({ store: again.store, sync: again.sync, transport: again.transport, uid: acc.uid }), { ok: true, left: 0 });
});

test('Firebase emulator: deleting from one device leaves the account and the other devices untouched; signing in again restores it', { timeout: 120000 }, async () => {
  const acc = await account(), A = await device(acc), B = await device(acc);
  await seed(A); await A.sync.sync(); await B.sync.sync();
  const before = await live(acc), versions = JSON.stringify(before.docs.map(d => [d.id, d.version, d.deleted]).sort());
  assert.deepEqual(await E.deviceReady(A.sync), { ok: true, unsent: 0, conflicts: 0 });
  await A.app.auth().signOut(); E.wipeDevice(A.local, '', acc.uid);
  const after = await live(acc);
  assert.equal(JSON.stringify(after.docs.map(d => [d.id, d.version, d.deleted]).sort()), versions, 'not one document written');
  await B.sync.sync(); assert.equal(B.store.data.entries.length, 2);
  const back = await device(acc, A.local); await back.sync.sync();
  assert.deepEqual(back.store.data.entries.map(e => e.totalEarnings).sort(), [300.5, 322.33]);
  assert.equal(JSON.stringify((await live(acc)).docs.map(d => [d.id, d.version, d.deleted]).sort()), versions, 'still nothing written after signing in again');
});

test('Firebase emulator: refused while a change has not reached the account; another account is untouched and unreachable', { timeout: 120000 }, async () => {
  const acc = await account(), other = await account(), A = await device(acc), O = await device(other);
  await seed(A); await seed(O); await A.sync.sync(); await O.sync.sync();
  await offline(A); A.store.addEntries([shift({ notes: 'לא נשלח', createdAt: 55 })]);
  const ready = await E.deviceReady(A.sync); assert.equal(ready.ok, false); assert.equal(ready.unsent, 1);
  await online(A);
  const otherBefore = (await live(other)).live.length;
  await E.eraseAccount({ store: A.store, sync: A.sync, transport: A.transport, uid: acc.uid });
  assert.equal((await live(other)).live.length, otherBefore, 'the other account keeps everything');
  await assert.rejects(A.app.firestore().collection('users').doc(other.uid).collection('records_v1').get({ source: 'server' }), 'the rules refuse reading another account');
  await assert.rejects(A.app.firestore().collection('users').doc(other.uid).collection('records_v1').doc('0'.repeat(32)).delete(), 'and deleting there');
  await Promise.all(apps.map(a => a.delete()));
});
