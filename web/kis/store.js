/*
 * The 2.0 web data layer. It reads and writes the same browser storage the site has always used
 * ("work_complete_backup", with the old "user_work_shifts" read as before) so existing visitors keep
 * every shift. One-time checkpoints are taken before the first write.
 * Weekly plans live beside it ("work_series_v1"); device-only choices in "kis_web_v1".
 */
(function (root) {
'use strict';
const PLANS = 'work_series_v1', LOCAL = 'kis_web_v1', RECENT = 'kis_web_recent_v1';
const uid = () => (root.crypto?.randomUUID ? root.crypto.randomUUID() : 'id-' + Date.now().toString(36) + Math.random().toString(36).slice(2));

/*
 * One action that changes several keys (shifts, plans, timer) is one commit (audit D03). localStorage writes one key at
 * a time, so the previous value of every key goes into a journal first; a failed write puts back what was already
 * written, and a commit cut off half way (tab closed, crash) is finished or undone on the next load: finished when every
 * key already holds its new value, undone otherwise. Older versions ignore the journal key, and the stored formats are
 * unchanged, so an older build still reads everything.
 */
const JOURNAL = 'kis_commit_journal_v1', PRE_23 = 'work_pre_v23_snapshot_v1';
function textHash(t) { let h = 0x811c9dc5; for (let i = 0; i < t.length; i++) { h ^= t.charCodeAt(i); h = Math.imul(h, 0x01000193); } return (h >>> 0).toString(36) + ':' + t.length; }
const hashOf = v => (v == null ? null : textHash(v));
function commitKeys(storage, writes) {
  const keys = Object.keys(writes).filter(k => storage.getItem(k) !== writes[k]);
  if (!keys.length) return;
  const prev = Object.fromEntries(keys.map(k => [k, storage.getItem(k)]));
  try { storage.setItem(JOURNAL, JSON.stringify({ prev, next: Object.fromEntries(keys.map(k => [k, hashOf(writes[k])])) })); }
  catch (e) { throw failure(e); }
  const done = [];
  try {
    for (const k of keys) { if (writes[k] == null) storage.removeItem(k); else storage.setItem(k, writes[k]); done.push(k); }
  } catch (e) {
    for (const k of done.reverse()) { try { if (prev[k] == null) storage.removeItem(k); else storage.setItem(k, prev[k]); } catch { /* the journal still holds it */ } }
    try { storage.removeItem(JOURNAL); } catch { /* recovered on the next load */ }
    throw failure(e);
  }
  storage.removeItem(JOURNAL);
}
const failure = e => Error('השמירה נכשלה ולא בוצע שום שינוי' + (/quota/i.test(String(e?.name) + String(e?.message)) ? ' (אין מספיק מקום באחסון של הדפדפן)' : ''));
/** A commit that did not finish: finish it if every key already has its new value, otherwise put the old values back. */
function recoverJournal(storage) {
  const raw = storage.getItem(JOURNAL); if (!raw) return null;
  let j; try { j = JSON.parse(raw); } catch { storage.removeItem(JOURNAL); return 'discarded'; }
  const keys = Object.keys(j?.prev || {});
  const complete = keys.every(k => hashOf(storage.getItem(k)) === j.next?.[k]);
  if (!complete) for (const k of keys) { if (j.prev[k] == null) storage.removeItem(k); else storage.setItem(k, j.prev[k]); }
  storage.removeItem(JOURNAL);
  return complete ? 'finished' : 'undone';
}
/** Rows keep their ids; a repeated id (two identical old rows without one, audit D01) gets a stable suffix, never a merge. */
function uniqueIds(entries) {
  const seen = new Map();
  return entries.map(e => { const n = (seen.get(e.id) || 0) + 1; seen.set(e.id, n); return n === 1 ? e : { ...e, id: e.id + '~' + n }; });
}
/** The plans file has a fixed shape; anything else is kept exactly as it is and never overwritten (audit D04). */
const plansShape = p => !!p && typeof p === 'object' && !Array.isArray(p) && Array.isArray(p.series) && Array.isArray(p.exceptions);
const EMPTY_PLANS = JSON.stringify({ series: [], exceptions: [] });

function createStore(storage, { now = () => Date.now() } = {}) {
  let data = null, plans = { series: [], exceptions: [] }, local = {}, broken = null, plansBroken = null, recovered = null, lastRaw = null, lastPlans = null;
  const listeners = new Set();
  const emit = () => listeners.forEach(fn => { try { fn(); } catch (e) { console.error(e); } });
  // Timer: same key and shape as before (work_active_shift_v1:guest).
  const timerKey = root.WorkRuntime.timerKey(null);

  function withDefaults(e) {
    // 2.0 fields default exactly like Android (WorkEntry): a shift with known pay and no bonus.
    // Ids are local only (Android ignores them). Early versions used Date.now() numbers: keep the value, as text, so every
    // screen can find the row; a row without one gets a stable id from its content until it is first saved.
    const id = e.id != null && e.id !== '' ? String(e.id) : 'legacy-' + hash(JSON.stringify(e));
    return { kind: 'shift', payKnown: true, bonus: 0, seriesKey: null, autoConfirmed: false, passThrough: 0, ...e, id };
  }
  function hash(t) { let h = 0; for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) | 0; return (h >>> 0).toString(36); }
  /** Rows that use nothing from 2.0 are stored exactly as before (also keeps a way back to the previous site clean). */
  function stored(e) {
    const row = { ...e };
    if (row.kind === 'shift') delete row.kind; if (row.payKnown === true) delete row.payKnown; if (!row.bonus) delete row.bonus;
    if (row.seriesKey == null) delete row.seriesKey; if (!row.autoConfirmed) delete row.autoConfirmed; if (!row.passThrough) delete row.passThrough;
    return row;
  }
  function load() {
    try { recovered = recoverJournal(storage) || recovered; } catch { /* storage unavailable: reading below reports it */ }
    lastRaw = storage.getItem('work_complete_backup'); lastPlans = storage.getItem(PLANS);
    try {
      const d = root.WorkRuntime.loadGuest(storage);
      data = { entries: uniqueIds(d.entries.map(withDefaults)), categories: d.categories || [], workers: d.workers || [],
        webPreferences: root.WorkCategories.preferences(d.webPreferences || {}) };
      broken = null;
    } catch (e) { broken = e.message; data = { entries: [], categories: [], workers: [], webPreferences: root.WorkCategories.preferences({}) }; }
    plansBroken = null; plans = { series: [], exceptions: [] };
    if (lastPlans != null) {
      let p = null; try { p = JSON.parse(lastPlans); } catch { /* reported below */ }
      if (plansShape(p)) plans = p; else plansBroken = 'קובץ המשמרות הקבועות פגום';
    }
    try { local = JSON.parse(storage.getItem(LOCAL) || '{}'); } catch { local = {}; }
    return data;
  }
  /** One-time copies taken before the first write, so an older site (or a person) can always get back to them. */
  function snapshots() {
    root.WorkRuntime.checkpoint(storage);
    const keys = ['work_complete_backup', 'user_work_shifts', 'work_transfer_meta', 'work_default_currency'];
    // Before 2.0 wrote for the first time, and (2.3) before this version writes for the first time, plans included.
    for (const [key, list] of [['work_pre_v2_snapshot_v1', keys], [PRE_23, [...keys, PLANS]]]) {
      if (storage.getItem(key)) continue;
      const values = Object.fromEntries(list.map(k => [k, storage.getItem(k)]));
      if (list.some(k => values[k] !== null)) storage.setItem(key, JSON.stringify({ savedAt: now(), values }));
    }
  }
  /** Another tab wrote meanwhile: start from what is stored now, never from an older copy. */
  function fresh() { if (storage.getItem('work_complete_backup') !== lastRaw || storage.getItem(PLANS) !== lastPlans || storage.getItem(JOURNAL)) load(); }
  /**
   * Every change goes through here. fn gets fresh copies of the data, the plans and the timer (taken from what is stored
   * now, never from an older copy) and edits them; everything it changed is written as one commit, then screens redraw.
   */
  function transact(fn) {
    fresh();
    if (broken) throw Error('הנתונים המקומיים דורשים שחזור; לא בוצע שינוי');
    const t0 = storage.getItem(timerKey);
    const next = { data: JSON.parse(JSON.stringify(data)), plans: JSON.parse(JSON.stringify(plans)), timer: t0 ? JSON.parse(t0) : null };
    const result = fn(next);
    const writes = {}, raw = JSON.stringify({ ...next.data, entries: next.data.entries.map(stored) });
    if (raw !== JSON.stringify({ ...data, entries: data.entries.map(stored) }) || (lastRaw == null && next.data.entries.length)) writes['work_complete_backup'] = raw;
    const plansRaw = JSON.stringify(next.plans);
    if (plansRaw !== JSON.stringify(plans)) {
      if (plansBroken) throw Error(plansBroken + '; לא בוצע שינוי');
      writes[PLANS] = plansRaw;
    }
    const timerRaw = next.timer ? JSON.stringify(next.timer) : null;
    if (timerRaw !== t0) writes[timerKey] = timerRaw;
    if (Object.keys(writes).length) { snapshots(); commitKeys(storage, writes); }
    data = next.data; plans = next.plans;
    if ('work_complete_backup' in writes) lastRaw = raw;
    if (PLANS in writes) lastPlans = plansRaw;
    if (Object.keys(writes).length) emit();
    return result;
  }
  function update(fn) { transact(n => fn(n.data)); return data; }
  function updatePlans(fn) { transact(n => fn(n.plans)); }
  function setLocal(key, value, quiet = false) { local = { ...local, [key]: value }; storage.setItem(LOCAL, JSON.stringify(local)); if (!quiet) emit(); }

  /**
   * Recovery while the stored data cannot be read (audit CW-15) or the plans file is damaged (D04): the unreadable text
   * is first kept, unchanged, under a key of its own; then the checked backup becomes the data, in one commit. Nothing
   * runs without the person's confirmation in the recovery screen.
   */
  function restoreDamaged(decoded) {
    const keep = Object.fromEntries([['work_complete_backup', broken], [PLANS, plansBroken]].filter(([, bad]) => bad).map(([k]) => [k, storage.getItem(k)]));
    if (!Object.keys(keep).length) throw Error('אין נתונים פגומים לשחזור');
    const copyKey = 'work_damaged_copy_' + now();
    const writes = { [copyKey]: JSON.stringify({ savedAt: now(), values: keep }) };
    if (broken) writes['work_complete_backup'] = JSON.stringify({ entries: decoded.entries.map(e => stored(withDefaults({ ...e, id: uid() }))),
      categories: decoded.categories || [], workers: decoded.workers || [], webPreferences: root.WorkCategories.preferences(decoded.webPreferences || {}) });
    if (plansBroken) writes[PLANS] = JSON.stringify({ series: decoded.series || [], exceptions: decoded.exceptions || [] });
    commitKeys(storage, writes);
    load(); emit();
    return copyKey;
  }

  // ---- shifts ----
  function addEntries(rows) { const added = rows.map(r => withDefaults({ ...r, id: r.keepId || uid(), keepId: undefined })).map(({ keepId, ...e }) => e);
    update(d => { for (const e of added) if (!d.entries.some(x => x.id === e.id)) d.entries.push(e); }); return added.map(e => e.id); }
  /** Saving an edit: if the row changed in another tab meanwhile, nothing is overwritten (WorkRuntime.assertUnchangedEdit). */
  function replaceEntry(id, row, base) {
    update(d => { const i = d.entries.findIndex(e => e.id === id);
      if (base) root.WorkRuntime.assertUnchangedEdit(withDefaults(base), i < 0 ? null : d.entries[i]);
      if (i < 0) throw Error('המשמרת נמחקה בחלון אחר'); d.entries[i] = withDefaults({ ...row, id }); }); }
  // A recorded plan day that is deleted stays skipped, so it is not recorded again by itself: one commit with the delete.
  function deleteEntries(ids) {
    return transact(n => { const gone = n.data.entries.filter(e => ids.includes(e.id));
      n.data.entries = n.data.entries.filter(e => !ids.includes(e.id)); markDays(n.plans, gone.map(e => e.seriesKey).filter(Boolean), true); return gone; });
  }
  function restoreEntries(rows) {
    transact(n => { for (const r of rows) if (!n.data.entries.some(e => e.id === r.id)) n.data.entries.push(r); markDays(n.plans, rows.map(e => e.seriesKey).filter(Boolean), false); });
  }
  function markDays(p, keys, skipped) {
    for (const k of keys) { const sid = k.slice(0, k.lastIndexOf('@')), date = k.slice(k.lastIndexOf('@') + 1);
      const x = p.exceptions.find(e => e.seriesId === sid && e.date === date);
      if (x) x.cancelled = skipped; else p.exceptions.push(exception(sid, date, { cancelled: skipped })); }
  }
  function markPlanDays(keys, skipped) { if (keys.length) transact(n => markDays(n.plans, keys, skipped)); }
  function setPaid(ids, paid) { update(d => d.entries.forEach(e => { if (ids.includes(e.id)) e.isPaid = paid; })); }

  // ---- timer ----
  function timer() { try { return root.WorkRuntime.readTimer(storage, null); } catch { return null; } }
  function startTimer(category, rate, currency) { storage.setItem(timerKey, JSON.stringify({ id: uid(), category, rate: rate ?? 0, currency, startedAt: now(), rateUnknown: rate == null })); emit(); }
  function cancelTimer() { const t = timer(); storage.removeItem(timerKey); emit(); return t; }
  function restoreTimer(t) { storage.setItem(timerKey, JSON.stringify(t)); emit(); }

  // ---- plans ----
  function exception(seriesId, date, over = {}) { return { seriesId, date, cancelled: false, hold: false, startTime: null, endTime: null, rateOverridden: false, hourlyRate: null, ...over }; }

  return {
    load, update, updatePlans, transact, restoreDamaged, setLocal, addEntries, replaceEntry, deleteEntries, restoreEntries, markPlanDays, setPaid,
    timer, startTimer, cancelTimer, restoreTimer, exception, uid,
    get data() { return data; }, get plans() { return plans; }, get local() { return local; }, get broken() { return broken; },
    /** The plans file could not be read; it is kept untouched (rawPlans) and plan changes are refused until it is restored. */
    get plansBroken() { return plansBroken; }, rawPlans: () => storage.getItem(PLANS),
    /** 'finished' / 'undone' when an interrupted save was completed or rolled back while loading. */
    get recovered() { return recovered; },
    subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }, emit,
    recent() { try { return JSON.parse(storage.getItem(RECENT) || '[]'); } catch { return []; } },
    addRecent(note) { const all = [note, ...this.recent()].slice(0, 20); storage.setItem(RECENT, JSON.stringify(all)); },
    setRecent(list) { storage.setItem(RECENT, JSON.stringify(list.slice(0, 20))); },
  };
}

const api = { createStore, commitKeys, recoverJournal, uniqueIds, JOURNAL };
if (typeof module !== 'undefined') module.exports = api; else root.KisStoreFactory = api;
})(globalThis);
