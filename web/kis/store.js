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
  let data = null, plans = { series: [], exceptions: [] }, local = {}, broken = null;
  const listeners = new Set();
  const emit = () => listeners.forEach(fn => { try { fn(); } catch (e) { console.error(e); } });

  function withDefaults(e) {
    // 2.0 fields default exactly like Android (WorkEntry): a shift with known pay and no bonus.
    return { kind: 'shift', payKnown: true, bonus: 0, seriesKey: null, autoConfirmed: false, ...e, id: e.id || uid() };
  }
  function load() {
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
    storage.setItem('work_complete_backup', JSON.stringify(data));
  }
  /** Every change goes through here: one atomic snapshot, then the screens redraw. */
  function update(fn) { const next = JSON.parse(JSON.stringify(data)); fn(next); const prev = data; data = next; try { persist(); } catch (e) { data = prev; throw e; } emit(); return data; }
  function updatePlans(fn) { const next = JSON.parse(JSON.stringify(plans)); fn(next); plans = next; storage.setItem(PLANS, JSON.stringify(plans)); emit(); }
  function setLocal(key, value) { local = { ...local, [key]: value }; storage.setItem(LOCAL, JSON.stringify(local)); emit(); }

  // ---- shifts ----
  function addEntries(rows) { const added = rows.map(r => withDefaults({ ...r, id: uid() })); update(d => { d.entries.push(...added); }); return added.map(e => e.id); }
  function replaceEntry(id, row) { update(d => { const i = d.entries.findIndex(e => e.id === id); if (i < 0) throw Error('המשמרת נמחקה בחלון אחר'); d.entries[i] = withDefaults({ ...row, id }); }); }
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
