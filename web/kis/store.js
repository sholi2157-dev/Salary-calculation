/*
 * The 2.0 web data layer. It reads and writes the same browser storage the site has always used
 * ("work_complete_backup", with the old "user_work_shifts" read as before) so existing visitors keep
 * every shift. A one-time checkpoint (WorkRuntime.checkpoint) is taken before the first write.
 * Weekly plans live beside it ("work_series_v1"); device-only choices in "kis_web_v1".
 */
(function (root) {
'use strict';
const PLANS = 'work_series_v1', LOCAL = 'kis_web_v1', RECENT = 'kis_web_recent_v1';
const uid = () => (root.crypto?.randomUUID ? root.crypto.randomUUID() : 'id-' + Date.now().toString(36) + Math.random().toString(36).slice(2));

function createStore(storage, { now = () => Date.now() } = {}) {
  let data = null, plans = { series: [], exceptions: [] }, local = {}, broken = null, lastRaw = null, lastPlans = null;
  const listeners = new Set();
  const emit = () => listeners.forEach(fn => { try { fn(); } catch (e) { console.error(e); } });

  function withDefaults(e) {
    // 2.0 fields default exactly like Android (WorkEntry): a shift with known pay and no bonus.
    // Ids are local only (Android ignores them). Early versions used Date.now() numbers: keep the value, as text, so every
    // screen can find the row; a row without one gets a stable id from its content until it is first saved.
    const id = e.id != null && e.id !== '' ? String(e.id) : 'legacy-' + hash(JSON.stringify(e));
    return { kind: 'shift', payKnown: true, bonus: 0, seriesKey: null, autoConfirmed: false, ...e, id };
  }
  function hash(t) { let h = 0; for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) | 0; return (h >>> 0).toString(36); }
  /** Rows that use nothing from 2.0 are stored exactly as before (also keeps a way back to the previous site clean). */
  function stored(e) {
    const row = { ...e };
    if (row.kind === 'shift') delete row.kind; if (row.payKnown === true) delete row.payKnown; if (!row.bonus) delete row.bonus;
    if (row.seriesKey == null) delete row.seriesKey; if (!row.autoConfirmed) delete row.autoConfirmed;
    return row;
  }
  function load() {
    lastRaw = storage.getItem('work_complete_backup'); lastPlans = storage.getItem(PLANS);
    try {
      const d = root.WorkRuntime.loadGuest(storage);
      data = { entries: d.entries.map(withDefaults), categories: d.categories || [], workers: d.workers || [],
        webPreferences: root.WorkCategories.preferences(d.webPreferences || {}) };
      broken = null;
    } catch (e) { broken = e.message; data = { entries: [], categories: [], workers: [], webPreferences: root.WorkCategories.preferences({}) }; }
    try { plans = JSON.parse(storage.getItem(PLANS) || '{"series":[],"exceptions":[]}'); } catch { plans = { series: [], exceptions: [] }; }
    try { local = JSON.parse(storage.getItem(LOCAL) || '{}'); } catch { local = {}; }
    return data;
  }
  function persist() {
    if (broken) throw Error('הנתונים המקומיים דורשים שחזור; לא בוצע שינוי');
    root.WorkRuntime.checkpoint(storage);
    // One copy of everything exactly as the previous site left it, before 2.0 writes for the first time.
    if (!storage.getItem('work_pre_v2_snapshot_v1')) {
      const keys = ['work_complete_backup', 'user_work_shifts', 'work_transfer_meta', 'work_default_currency'];
      const values = Object.fromEntries(keys.map(k => [k, storage.getItem(k)]));
      if (keys.some(k => values[k] !== null)) storage.setItem('work_pre_v2_snapshot_v1', JSON.stringify({ savedAt: now(), values }));
    }
    lastRaw = JSON.stringify({ ...data, entries: data.entries.map(stored) });
    storage.setItem('work_complete_backup', lastRaw);
  }
  /** Every change goes through here: one atomic snapshot, then the screens redraw. */
  /** Another tab wrote meanwhile: start from what is stored now, never from an older copy. */
  function fresh() { if (storage.getItem('work_complete_backup') !== lastRaw || storage.getItem(PLANS) !== lastPlans) load(); }
  function update(fn) { fresh(); const next = JSON.parse(JSON.stringify(data)); fn(next); const prev = data; data = next; try { persist(); } catch (e) { data = prev; throw e; } emit(); return data; }
  function updatePlans(fn) { fresh(); const next = JSON.parse(JSON.stringify(plans)); fn(next); plans = next; lastPlans = JSON.stringify(plans); storage.setItem(PLANS, lastPlans); emit(); }
  function setLocal(key, value, quiet = false) { local = { ...local, [key]: value }; storage.setItem(LOCAL, JSON.stringify(local)); if (!quiet) emit(); }

  // ---- shifts ----
  function addEntries(rows) { const added = rows.map(r => withDefaults({ ...r, id: r.keepId || uid(), keepId: undefined })).map(({ keepId, ...e }) => e);
    update(d => { for (const e of added) if (!d.entries.some(x => x.id === e.id)) d.entries.push(e); }); return added.map(e => e.id); }
  /** Saving an edit: if the row changed in another tab meanwhile, nothing is overwritten (WorkRuntime.assertUnchangedEdit). */
  function replaceEntry(id, row, base) {
    update(d => { const i = d.entries.findIndex(e => e.id === id);
      if (base) root.WorkRuntime.assertUnchangedEdit(withDefaults(base), i < 0 ? null : d.entries[i]);
      if (i < 0) throw Error('המשמרת נמחקה בחלון אחר'); d.entries[i] = withDefaults({ ...row, id }); }); }
  function deleteEntries(ids) {
    const gone = data.entries.filter(e => ids.includes(e.id));
    update(d => { d.entries = d.entries.filter(e => !ids.includes(e.id)); });
    // A recorded plan day that is deleted stays skipped, so it is not recorded again by itself.
    markPlanDays(gone.map(e => e.seriesKey).filter(Boolean), true);
    return gone;
  }
  function restoreEntries(rows) { update(d => { for (const r of rows) if (!d.entries.some(e => e.id === r.id)) d.entries.push(r); }); markPlanDays(rows.map(e => e.seriesKey).filter(Boolean), false); }
  function markPlanDays(keys, skipped) {
    if (!keys.length) return;
    updatePlans(p => { for (const k of keys) { const sid = k.slice(0, k.lastIndexOf('@')), date = k.slice(k.lastIndexOf('@') + 1);
      const x = p.exceptions.find(e => e.seriesId === sid && e.date === date);
      if (x) x.cancelled = skipped; else p.exceptions.push(exception(sid, date, { cancelled: skipped })); } });
  }
  function setPaid(ids, paid) { update(d => d.entries.forEach(e => { if (ids.includes(e.id)) e.isPaid = paid; })); }

  // ---- timer (same key and shape as before: work_active_shift_v1:guest) ----
  const timerKey = root.WorkRuntime.timerKey(null);
  function timer() { try { return root.WorkRuntime.readTimer(storage, null); } catch { return null; } }
  function startTimer(category, rate, currency) { storage.setItem(timerKey, JSON.stringify({ id: uid(), category, rate: rate ?? 0, currency, startedAt: now(), rateUnknown: rate == null })); emit(); }
  function cancelTimer() { const t = timer(); storage.removeItem(timerKey); emit(); return t; }
  function restoreTimer(t) { storage.setItem(timerKey, JSON.stringify(t)); emit(); }

  // ---- plans ----
  function exception(seriesId, date, over = {}) { return { seriesId, date, cancelled: false, hold: false, startTime: null, endTime: null, rateOverridden: false, hourlyRate: null, ...over }; }

  return {
    load, update, updatePlans, setLocal, addEntries, replaceEntry, deleteEntries, restoreEntries, markPlanDays, setPaid,
    timer, startTimer, cancelTimer, restoreTimer, exception, uid,
    get data() { return data; }, get plans() { return plans; }, get local() { return local; }, get broken() { return broken; },
    subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }, emit,
    recent() { try { return JSON.parse(storage.getItem(RECENT) || '[]'); } catch { return []; } },
    addRecent(note) { const all = [note, ...this.recent()].slice(0, 20); storage.setItem(RECENT, JSON.stringify(all)); },
  };
}

const api = { createStore };
if (typeof module !== 'undefined') module.exports = api; else root.KisStoreFactory = api;
})(globalThis);
