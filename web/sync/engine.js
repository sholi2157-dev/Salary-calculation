/*
 * Account sync for the 2.0 web store (KisStore), the same rules as Android (data/WorkSyncEngine.kt):
 * - The local data stays the working copy; the account copy is reached through a transport.
 * - Read first, so an item that already exists in the account (the same shift typed on the phone and imported
 *   here, a place with the same name) is joined instead of being sent a second time.
 * - Every write is a compare-and-set on the item's version. A lost answer is retried with the same operation.
 * - An item changed here and in the account since the last sync is never overwritten: both stay until the
 *   user picks one (conflicts / resolve). Settings are the exception: the account wins before the first sync.
 * The sync state (versions and fingerprints of what was last agreed) lives in "work_sync_v1:<uid>".
 */
(function (root) {
'use strict';
const P = () => root.WorkSyncProtocol || require('./protocol.js');
const R = () => root.WorkRuntime || require('../runtime.js');

/** Every syncable item in the store, keyed "type|key". */
function snapshot(store) {
  const d = store.data, p = store.plans, out = new Map();
  const add = (type, item) => { const key = P().keyOf(type, item); out.set(type + '|' + key, { type, key, item }); };
  d.entries.forEach(e => add('entry', e));
  d.categories.forEach(c => add('category', { name: c.name, defaultRate: c.defaultRate }));
  d.workers.forEach(w => add('worker', { name: w.name }));
  (p.series || []).forEach(s => add('series', s));
  (p.exceptions || []).forEach(x => add('exception', x));
  for (const [name, value] of Object.entries(R().androidPreferences(d.webPreferences || {}))) if (value != null) add('preference', { name, value });
  return out;
}

/** Writes received items into the store in one data write and one plans write. */
function applyAll(store, changes) {
  if (!changes.length) return;
  const by = t => changes.filter(c => c.type === t);
  if (changes.some(c => ['entry', 'category', 'worker', 'preference'].includes(c.type))) store.update(d => {
    for (const c of by('entry')) { d.entries = d.entries.filter(e => String(e.id) !== c.key); if (c.item) d.entries.push({ ...c.item, id: c.key }); }
    for (const c of by('category')) { const i = d.categories.findIndex(x => x.name === c.key);
      if (!c.item) { if (i >= 0) d.categories.splice(i, 1); } else if (i >= 0) d.categories[i] = { ...d.categories[i], ...c.item }; else d.categories.push(c.item); }
    for (const c of by('worker')) { d.workers = d.workers.filter(w => w.name !== c.key); if (c.item) d.workers.push(c.item); }
    const prefs = by('preference');
    if (prefs.length) {
      const local = R().androidPreferences(d.webPreferences || {});
      for (const c of prefs) { if (c.item) local[c.key] = c.item.value; else delete local[c.key]; }
      d.webPreferences = R().preferencesFromAndroid(local);
    }
  });
  if (changes.some(c => c.type === 'series' || c.type === 'exception')) store.updatePlans(p => {
    for (const c of by('series')) { p.series = p.series.filter(s => s.id !== c.key); if (c.item) p.series.push(c.item); }
    for (const c of by('exception')) { p.exceptions = p.exceptions.filter(x => x.seriesId + '@' + x.date !== c.key); if (c.item) p.exceptions.push(c.item); }
  });
}

/** Same meaning for joining before the first sync: a shift by everything it holds, a place or worker by its name. */
function sameIdentity(type, a, b) {
  if (type === 'category' || type === 'worker') return a.name === b.name;
  return P().canonical(type, P().encode(type, a)) === P().canonical(type, P().encode(type, b));
}

function createSync({ store, storage, uid, deviceId, transport, currentUid }) {
  if (!uid || !deviceId) throw Error('Account required');
  const key = 'work_sync_v1:' + uid;
  let running = null;
  const read = () => { try { const s = JSON.parse(storage.getItem(key) || 'null'); if (s && s.records) return s; } catch {} return { records: {}, cursor: null, lastSync: null }; };
  const write = state => { guard(); storage.setItem(key, JSON.stringify(state)); };
  function guard() { if (currentUid() !== uid) throw Error('Account changed'); }
  const fp = (type, payload) => P().fingerprint(P().canonical(type, payload));
  const index = state => { const m = new Map(); for (const [id, r] of Object.entries(state.records)) m.set(r.type + '|' + r.key, id); return m; };

  /** Local items that differ from what was last agreed with the account. */
  async function pending(state) {
    const local = snapshot(store), byKey = index(state), out = [];
    for (const [k, l] of local) {
      const id = byKey.get(k), rec = id && state.records[id];
      if (rec?.conflict) continue;
      const payload = P().encode(l.type, l.item), print = fp(l.type, payload);
      if (rec && !rec.deleted && rec.print === print) continue;
      out.push({ syncId: id || (P().KEYED.includes(l.type) ? await P().keyedId(l.type, l.key) : l.type === 'entry' && P().SYNC_ID.test(l.key) ? l.key : P().randomId()),
        type: l.type, key: l.key, payload, print, base: rec?.version || 0 });
    }
    for (const [id, rec] of Object.entries(state.records)) {
      if (rec.deleted || rec.conflict || local.has(rec.type + '|' + rec.key)) continue;
      // Never reached the account: nothing to delete there.
      if (rec.version === 0) { delete state.records[id]; continue; }
      out.push({ syncId: id, type: rec.type, key: rec.key, payload: null, print: fp(rec.type, null), base: rec.version });
    }
    return out;
  }
  /** Local items not agreed with the account yet (no record, or one that was never acknowledged). */
  const unsent = (state, byKey, lk) => { const id = byKey.get(lk); return !id || state.records[id].version === 0; };

  /** One received record: joined, applied, ignored as old, or kept beside the local change for the user. */
  async function receive(state, local, byKey, remote, changes) {
    const r = P().validate(remote);
    let rec = state.records[r.syncId];
    if (rec && rec.type !== r.type) throw Error('Record type changed');
    if (rec && r.version <= rec.version) return;
    const print = fp(r.type, r.payload);
    if (!rec) {
      // Not known here yet. A keyed item may exist locally under the same key; anything else may be the same item typed twice.
      let k = null;
      if (P().KEYED.includes(r.type)) {
        for (const [lk, l] of local) if (l.type === r.type && unsent(state, byKey, lk) && await P().keyedId(l.type, l.key) === r.syncId) { k = l.key; break; }
      } else if (!r.deleted) {
        const item = P().decode(r.type, r.payload);
        for (const [lk, l] of local) if (l.type === r.type && unsent(state, byKey, lk) && sameIdentity(r.type, l.item, item)) { k = l.key; break; }
      }
      if (k != null) {
        const l = local.get(r.type + '|' + k), localPrint = fp(r.type, P().encode(r.type, l.item)), reserved = byKey.get(r.type + '|' + k);
        if (reserved) delete state.records[reserved];
        rec = state.records[r.syncId] = { type: r.type, key: k, version: 0, print: localPrint, deleted: false, conflict: null };
        byKey.set(r.type + '|' + k, r.syncId);
        // A setting never synced takes the account's value; equal content is simply joined.
        if (localPrint === print || r.type === 'preference' || r.type === 'category' || r.type === 'worker') { rec.version = r.version; rec.print = print; rec.deleted = r.deleted;
          if (localPrint !== print) changes.push({ type: r.type, key: k, item: r.deleted ? null : P().decode(r.type, r.payload) });
          return; }
        rec.conflict = r; return;
      }
      const item = r.deleted ? null : P().decode(r.type, r.payload);
      const newKey = r.type === 'entry' ? r.syncId : item ? P().keyOf(r.type, item) : null;
      if (newKey == null) { state.records[r.syncId] = { type: r.type, key: '', version: r.version, print, deleted: true, conflict: null }; return; }
      state.records[r.syncId] = { type: r.type, key: newKey, version: r.version, print, deleted: r.deleted, conflict: null };
      byKey.set(r.type + '|' + newKey, r.syncId);
      if (item) changes.push({ type: r.type, key: newKey, item });
      return;
    }
    const l = local.get(rec.type + '|' + rec.key), localPrint = l ? fp(rec.type, P().encode(rec.type, l.item)) : fp(rec.type, null);
    const changedHere = rec.deleted ? !!l : localPrint !== rec.print;
    if (changedHere && localPrint !== print) { rec.conflict = r; return; }
    apply(rec, r, changes, byKey, r.syncId);
  }
  function apply(rec, r, changes, byKey, syncId) {
    const item = r.deleted ? null : P().decode(r.type, r.payload);
    const newKey = r.type === 'entry' || !item ? rec.key : P().keyOf(r.type, item);
    // A renamed place arrives under its new name: the old name goes, the new one comes.
    if (newKey !== rec.key) { changes.push({ type: r.type, key: rec.key, item: null }); byKey?.delete(r.type + '|' + rec.key); }
    changes.push({ type: r.type, key: newKey, item });
    Object.assign(rec, { key: newKey, version: r.version, print: fp(r.type, r.payload), deleted: r.deleted, conflict: null });
    byKey?.set(r.type + '|' + newKey, syncId);
  }

  async function run() {
    guard();
    let state = read();
    // 1. Read what changed in the account since last time.
    const { records, cursor } = await transport.read(uid, state.cursor);
    guard(); store.load(); state = read();
    const local = snapshot(store), byKey = index(state), changes = [];
    for (const r of records) await receive(state, local, byKey, r, changes);
    applyAll(store, changes); state.cursor = cursor ?? state.cursor; write(state);
    // 2. Send what changed here.
    const outbox = await pending(state);
    // The chosen syncId is kept before sending, so a lost answer is retried under the same one.
    for (const m of outbox) if (!state.records[m.syncId]) state.records[m.syncId] = { type: m.type, key: m.key, version: 0, print: null, deleted: false, conflict: null };
    write(state);
    for (const m of outbox) {
      guard();
      const operation = m.syncId + ':' + (await P().sha256([deviceId, m.base, m.print, m.payload == null].join('|'))).slice(0, 40);
      const proposed = { syncId: m.syncId, type: m.type, version: m.base + 1, operation, deleted: m.payload == null, payload: m.payload };
      const accepted = P().validate(await transport.exchange(uid, m.base, proposed));
      guard(); store.load(); state = read();
      if (accepted.syncId !== m.syncId || accepted.type !== m.type) throw Error('Unexpected sync answer');
      if (accepted.operation === operation) {
        state.records[m.syncId] = { ...(state.records[m.syncId] || {}), type: m.type, key: m.key, version: accepted.version, print: m.print, deleted: proposed.deleted, conflict: null };
      } else {
        const after = [];
        await receive(state, snapshot(store), index(state), accepted, after);
        applyAll(store, after);
      }
      write(state);
    }
    state.lastSync = Date.now(); write(state);
    return conflicts().length;
  }

  /** Runs one sync at a time (also across tabs when the browser supports locks). */
  function sync() {
    if (running) return running;
    const go = () => run();
    running = (root.navigator?.locks ? root.navigator.locks.request('work-sync:' + uid, go) : go()).finally(() => { running = null; });
    return running;
  }

  function conflicts() {
    const state = read(), local = snapshot(store);
    return Object.entries(state.records).filter(([, r]) => r.conflict).map(([syncId, r]) => {
      const l = local.get(r.type + '|' + r.key);
      return { syncId, type: r.type, mine: l ? l.item : null, theirs: r.conflict.deleted ? null : P().decode(r.type, r.conflict.payload) };
    });
  }
  /** The user's choice for one conflict. Keeping this device's version sends it on the next sync. */
  function resolve(syncId, keepLocal) {
    guard();
    const state = read(), rec = state.records[syncId];
    if (!rec?.conflict) throw Error('Conflict missing');
    const remote = rec.conflict;
    if (keepLocal) { rec.version = remote.version; rec.print = fp(remote.type, remote.payload); rec.deleted = remote.deleted; rec.conflict = null; }
    else { const changes = []; apply(rec, remote, changes, null, syncId); applyAll(store, changes); }
    write(state);
  }
  function status() { const s = read(); return { lastSync: s.lastSync, conflicts: Object.values(s.records).filter(r => r.conflict).length }; }

  return { sync, conflicts, resolve, status, pending: async () => (await pending(read())).length };
}

const api = { createSync, snapshot, applyAll };
if (typeof module !== 'undefined') module.exports = api; else root.WorkSync = api;
})(globalThis);
