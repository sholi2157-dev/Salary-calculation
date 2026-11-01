// 2.3.0 (audit S-CUR-01): an assistant change and its undo record are kept together. Synthetic storage, no network.
const { test } = require('node:test'), assert = require('node:assert/strict');
const A = require('../lib/server/assistant.cjs'), P = require('../web/sync/protocol.js');
const e = { category: 'בדיקה', date: 1700000000000, createdAt: 1, hours: 1, hourlyRate: 40, totalEarnings: 40, isPaid: false, notes: '', currency: '₪', isTimeRange: false, startTime: null, endTime: null, isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '' };
const id = 'a'.repeat(32);
function db({ logFails = false, recordConflict = false } = {}) {
  const docs = new Map([['records_v1/' + id, { schema: 1, type: 'entry', version: 1, operation: id + ':seed', deleted: false, payload: P.encode('entry', e) }]]);
  return { docs,
    list: async () => [...docs].filter(([k]) => k.startsWith('records_v1/')).map(([k, data]) => ({ id: k.slice(11), data, updateTime: 'rev-1' })),
    put: async (path, data) => { if (path.startsWith('assistant_log/') && logFails) throw Error('synthetic log failure'); if (path.startsWith('records_v1/') && recordConflict) return false; docs.set(path, data); return true; },
    remove: async path => { docs.delete(path); return true; } };
}
const ctx = d => ({ db: d, grant: { s: ['shifts.delete', 'shifts.read', 'shifts.write', 'shifts.edit'], g: 'synthetic-grant', n: 'audit', z: 'UTC' } });
const logs = d => [...d.docs.keys()].filter(k => k.startsWith('assistant_log/'));

test('no undo record, no change: the shift stays when the log cannot be saved', async () => {
  const d = db({ logFails: true });
  await assert.rejects(A.run(ctx(d), 'delete_shift', { id }), /undo record/);
  assert.equal(d.docs.get('records_v1/' + id).deleted, false);
  assert.equal(logs(d).length, 0);
});

test('a change that conflicts leaves no undo record behind', async () => {
  const d = db({ recordConflict: true });
  await assert.rejects(A.run(ctx(d), 'delete_shift', { id }), /changed on another device/);
  assert.equal(logs(d).length, 0);
  assert.equal(d.docs.get('records_v1/' + id).deleted, false);
});

test('a successful delete keeps the record needed to undo it', async () => {
  const d = db();
  const r = await A.run(ctx(d), 'delete_shift', { id });
  assert.ok(r.deleted);
  assert.equal(d.docs.get('records_v1/' + id).deleted, true);
  const [log] = logs(d).map(k => d.docs.get(k));
  assert.equal(log.before, P.encode('entry', e)); assert.equal(log.after, null);
});

// 2.3.0 (audit S-CUR-06, S-CUR-07): assistant edits keep a break and add the bonus when pay becomes known.
async function edit(entry, args) {
  const P2 = require('../web/sync/protocol.js'), docs = new Map([['records_v1/' + id, { schema: 1, type: 'entry', version: 1, operation: id + ':seed', deleted: false, payload: P2.encode('entry', entry) }]]);
  const d = { docs, list: async () => [...docs].filter(([k]) => k.startsWith('records_v1/')).map(([k, data]) => ({ id: k.slice(11), data, updateTime: 'rev-1' })),
    put: async (path, data) => { docs.set(path, data); return true; }, remove: async p => docs.delete(p) };
  await A.run(ctx(d), 'update_shift', { id, ...args });
  return P2.decode('entry', docs.get('records_v1/' + id).payload);
}
test('changing the end keeps the existing 30-minute break', async () => {
  const shift = { ...e, isTimeRange: true, startTime: '09:00', endTime: '17:00', hours: 7.5, hourlyRate: 40, totalEarnings: 300 };
  const out = await edit(shift, { end: '18:00' });
  assert.equal(out.hours, 8.5); assert.equal(out.totalEarnings, 340);
});
test('a rate given to a shift without one includes its bonus, also at rate 0', async () => {
  const shift = { ...e, hours: 2, hourlyRate: 0, payKnown: false, bonus: 20, totalEarnings: 0 };
  assert.equal((await edit(shift, { rate: 0 })).totalEarnings, 20);
  assert.equal((await edit(shift, { rate: 50 })).totalEarnings, 120);
});
test('a note-only edit never changes the saved amount', async () => {
  const shift = { ...e, hours: 1.234567, hourlyRate: 34.123456, totalEarnings: 42.12345 };
  assert.equal((await edit(shift, { notes: 'רק הערה' })).totalEarnings, 42.12345);
});
