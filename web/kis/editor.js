/*
 * Add / edit, as on Android (KisEditor + EntryDraft): a calm page with the purple glow; the workplace colour marks only
 * the chosen place and the ruler span. Clock / hours / job / group; both times always shown and edited digit by digit;
 * −15/+15 per time; unknown rate is
 * never 0; bonus; break; weekly repeat with an honest preview. A new draft survives reloads; an edit is a framed pop-up.
 */
(function (root) {
'use strict';
const U = () => root.KisUi, R = () => root.KisRules;
const DRAFT = 'kis_web_draft_v1';
const MODES = [['CLOCK', 'שעון'], ['HOURS', 'ידני'], ['JOB', 'קבלנות'], ['GROUP', 'קבוצה']];
const num = s => { const t = String(s ?? '').trim().replace(',', '.'); if (!t) return null; const n = Number(t); return Number.isFinite(n) ? n : null; };
const clean = v => v == null ? '' : String(Math.round(v * 100) / 100);
// New UI typing uses H:MM; existing decimal drafts and persisted hours keep their meaning.
const durationEdit = s => String(s).replace(/[.,]/g, ':');
function durationHours(s) {
  const t = String(s ?? '').trim();
  if (!t.includes(':')) return num(t);
  if (!/^\d{1,2}:\d{2}$/.test(t)) return null;
  const [h, m] = t.split(':').map(Number);
  return m < 60 ? (h * 60 + m) / 60 : null;
}
function durationDisplay(s) {
  if (!String(s ?? '').trim() || String(s).includes(':')) return s ?? '';
  const h = durationHours(s); if (h == null) return s;
  const m = Math.round(h * 60); return `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}`;
}
const nowMin = () => { const d = new Date(); return d.getHours() * 60 + d.getMinutes(); };

function suggestStart(category, day, history) {
  const wd = ms => new Date(ms).getDay();
  const ranked = list => Object.entries(list.filter(e => e.isTimeRange && e.startTime).reduce((m, e) => (m[e.startTime] = (m[e.startTime] || 0) + 1, m), {})).sort((a, b) => b[1] - a[1]);
  const same = ranked(history.filter(e => e.category === category && wd(e.date) === wd(day)));
  let chips = ranked(history.filter(e => e.category === category)).slice(0, 3).map(x => x[0]);
  if (!chips.length) chips = ranked(history).slice(0, 3).map(x => x[0]);
  return { start: same[0] && same[0][1] >= 3 ? same[0][0] : null, chips };
}
/** A new shift's length until the user moves it: both times are always shown, never an empty start (Android: EntryDraft.DEFAULT_MINUTES). */
const DEFAULT_MINUTES = 240;
const defaultStart = end => R().fromMinutes(R().toMinutes(end) - DEFAULT_MINUTES);

// ---- typing a time digit by digit (Android: typeTime / parseTime / timeDigits) ----
/** What the hidden typing field holds, so a delete always has something to remove. */
const SINK = '        ';
/** "17:30" → "1730"; no time yet → "····". */
const timeDigits = t => R().isTime(t) ? R().fromMinutes(R().toMinutes(t)).replace(':', '') : '····';
/** Four digits (unset ones count as 0) → "HH:MM", or null when it is not a time of day. */
function parseTime(digits) {
  const s = String(digits).replace(/·/g, '0');
  if (!/^\d{4}$/.test(s)) return null;
  const h = +s.slice(0, 2), m = +s.slice(2);
  return h > 23 || m > 59 ? null : R().fromMinutes(h * 60 + m);
}
/**
 * One key on a time edited in place: the digit at [cursor] (0–1 hours, 2–3 minutes) is replaced and the cursor moves on;
 * 4 means complete. So 05:30 → 05:35 is "tap the last digit, type 5". A first digit that can only start a one-digit hour
 * (3–9) becomes 0X and jumps to the minutes; likewise 6–9 for the minutes. Once all four are typed, another digit
 * replaces the last one (it never starts over or spills into the other time). Android: typeTime / typeTimeOrNull.
 */
function typeTimeOrNull(digits, cursor, key, maxHour = 23) {
  const b = (String(digits) + '····').slice(0, 4).split('');
  let at = cursor >= 0 && cursor <= 4 ? cursor : 0;
  if (key === 'del') { if (at > 0) { at--; b[at] = '·'; } return [b.join(''), at]; }
  for (const k of String(key)) {
    if (!/\d/.test(k)) continue;
    if (at >= 4) at = 3;
    if (at === 0) { if (+k > Math.floor(maxHour / 10)) { b[0] = '0'; b[1] = k; at = 2; } else { b[0] = k; if (/\d/.test(b[1]) && +k * 10 + +b[1] > maxHour) b[1] = '0'; at = 1; } }
    // An hour that cannot be (2 then 5) is refused: nothing changes.
    else if (at === 1) { if ((/\d/.test(b[0]) ? +b[0] : 0) * 10 + +k > maxHour) return null; b[1] = k; at = 2; }
    else if (at === 2) { if (k > '5') { b[2] = '0'; b[3] = k; at = 4; } else { b[2] = k; at = 3; } }
    else { b[3] = k; at = 4; }
  }
  return [b.join(''), at];
}
function typeTime(digits, cursor, key, maxHour = 23) {
  return typeTimeOrNull(digits, cursor, key, maxHour) || [(String(digits) + '····').slice(0, 4), Math.min(4, Math.max(0, cursor))];
}

const durationDigits = value => /^\d{1,2}:\d{2}$/.test(durationDisplay(value)) ? durationDisplay(value).replace(':', '').padStart(4, '0') : '····';
function parseDuration(digits) {
  const s = String(digits).replace(/·/g, '0');
  if (!/^\d{4}$/.test(s)) return null;
  const h = +s.slice(0, 2), m = +s.slice(2);
  return m < 60 && h * 60 + m <= 1440 ? h + ':' + String(m).padStart(2, '0') : null;
}
function newDraft(store) {
  const cats = store.data.categories, prefs = store.data.webPreferences;
  const name = root.WorkCategories.defaultName(cats, prefs), cat = cats.find(c => c.name === name);
  const day = U().startOfDay(Date.now()), end = R().roundedEnd(nowMin());
  return { id: null, mode: 'CLOCK', category: cat ? name : '', day, start: defaultStart(end), end, breakMin: 0,
    hours: '', rate: cat ? clean(cat.defaultRate) : '', rateUnknown: false, currency: root.WorkCategories.currency(name, prefs), bonus: '', bonusTarget: 'ME',
    amount: '', amountUnknown: false, notes: '', workers: [], separateRates: false, employerRate: '', workerRate: '', isPaid: false, repeat: false, repeatDays: null, repeatUntil: null };
}
function breakOf(e) { if (!e.isTimeRange || !R().isTime(e.startTime) || !R().isTime(e.endTime)) return 0; return Math.max(0, Math.round(R().span(e.startTime, e.endTime).minutes - e.hours * 60)); }
function fromEntry(e) {
  const job = e.kind === 'job', clock = e.isTimeRange && R().isTime(e.startTime) && R().isTime(e.endTime);
  const workers = root.KisPay.workers(e).map(w => ({ name: w.name, hours: job && w.hours === 0 && w.amount != null ? '' : clean(w.hours), isPaid: w.isPaid, bonus: w.bonus > 0 ? clean(w.bonus) : '', source: w.source || {},
    ...(job ? { byHours: w.amount == null, rate: clean(w.workerRate), amount: clean(w.amount) } : {}) }));
  // A team job stores the user's share; the form shows the job's full price (share + team).
  const price = job && e.payKnown ? e.totalEarnings + (root.KisPay.isTeamJob(e) ? root.KisPay.teamPay(e) ?? 0 : 0) : null;
  return { id: e.id, mode: job ? 'JOB' : e.isGroupShift ? 'GROUP' : clock ? 'CLOCK' : 'HOURS', category: e.category, day: U().startOfDay(e.date),
    start: clock ? e.startTime : null, end: clock ? e.endTime : R().roundedEnd(nowMin()), breakMin: breakOf(e), hours: clean(e.hours),
    rate: !job && e.payKnown ? clean(e.hourlyRate) : '', rateUnknown: !job && !e.payKnown, currency: e.currency, bonus: e.bonus > 0 ? clean(e.bonus) : '', bonusTarget: 'ME',
    amount: price != null ? clean(price) : '', amountUnknown: job && !e.payKnown, notes: e.notes || '', workers,
    separateRates: e.employerRate != null || e.workerRate != null, employerRate: clean(e.employerRate), workerRate: clean(e.workerRate), isPaid: e.isPaid, repeat: false };
}
/** On a job team (קבלנות) a worker gets a fixed amount or, when byHours, hours at their own rate. */
const teamJob = d => d.mode === 'JOB' && d.workers.length > 0;
function jobWorkerPay(w) {
  if (w.byHours) { const h = durationHours(w.hours || ''), r = num(w.rate || ''); return h == null || r == null || h < 0 || r < 0 ? null : root.KisPay.amount(h, r); }
  const a = num(w.amount || ''); return a != null && a >= 0 ? a : null;
}
function teamPayOf(d) {
  if (!teamJob(d)) return 0;
  let sum = 0; for (const w of d.workers) { const p = jobWorkerPay(w); if (p == null) return null; sum += p; }
  return Math.round(sum * 100) / 100;
}
function myShare(d) { const total = num(d.amount), team = teamPayOf(d); return total == null || team == null ? null : Math.round((total - team) * 100) / 100; }
function evaluate(d) {
  const clockish = d.mode === 'CLOCK' || (d.mode === 'GROUP' && d.start != null);
  const ev = R().evaluate({ mode: d.mode === 'GROUP' && !clockish ? 'HOURS' : d.mode, start: clockish ? d.start : null, end: clockish ? d.end : null, breakMinutes: clockish ? d.breakMin : 0,
    hours: durationHours(d.hours) ?? 'invalid', rate: num(d.rate), rateUnknown: d.rateUnknown, bonus: num(d.bonus) || 0, bonusTarget: d.bonusTarget, amount: d.amount, amountUnknown: d.amountUnknown });
  const errors = [...ev.errors];
  if (!d.category) errors.unshift('בחר מקום עבודה');
  if (d.mode === 'GROUP' && d.workers.some(w => !w.name.trim())) errors.push('לכל עובד צריך שם');
  if (d.mode === 'GROUP' && d.workers.some(w => w.hours.trim() && (durationHours(w.hours) == null || durationHours(w.hours) < 0 || durationHours(w.hours) > 24))) errors.push('שעות העובד לא תקינות');
  if (d.mode === 'GROUP' && d.separateRates && [d.employerRate, d.workerRate].some(v => v.trim() && num(v) == null)) errors.push('תעריף לא תקין');
  if (d.mode === 'JOB' && d.hours.trim() && (durationHours(d.hours) == null || durationHours(d.hours) < 0 || durationHours(d.hours) > 24)) errors.push('משך לא תקין');
  if (teamJob(d)) {
    if (d.workers.some(w => !w.name.trim())) errors.push('לכל עובד צריך שם');
    if (d.workers.some(w => jobWorkerPay(w) == null)) errors.push('חסר כמה מקבל כל עובד');
    else if (!d.amountUnknown && (myShare(d) ?? 0) < 0) errors.push('הצוות מקבל יותר מסכום העבודה');
  }
  // A team job shows what is left for the user, which is what is stored and counted.
  const share = teamJob(d) && ev.total != null ? myShare(d) : null;
  const total = teamJob(d) ? (share != null && share >= 0 ? share : null) : ev.total == null || d.mode === 'JOB' || clockish ? ev.total : root.KisPay.amount(durationHours(d.hours) || 0, num(d.rate) || 0, ev.bonus);
  return { ...ev, total, errors, canSave: errors.length === 0, clock: clockish };
}
/** The stored shift, exactly as Android builds it (EntryDraft.toEntry), keeping untouched stored values. */
function toEntry(d, base) {
  const was = base ? fromEntry(base) : null, c = evaluate(d), job = d.mode === 'JOB', clock = c.clock;
  const same = was && was.mode === d.mode && was.start === d.start && (d.start == null || was.end === d.end) && was.breakMin === d.breakMin && was.hours === d.hours;
  const hours = same ? base.hours : (job || !clock) ? (durationHours(d.hours) || 0) : (c.minutes || 0) / 60;
  const rate = was && !was.rateUnknown && was.rate === d.rate ? base.hourlyRate : (num(d.rate) || 0);
  const bonusN = was && was.bonus === d.bonus ? base.bonus : (num(d.bonus) || 0);
  const myBonus = job || (d.mode === 'GROUP' && d.bonusTarget === 'OTHERS') ? 0 : bonusN;
  const payKnown = job ? !d.amountUnknown : !d.rateUnknown;
  let date;
  if (base && U().startOfDay(base.date) === d.day) date = base.date;
  else if (clock) { const t = new Date(d.day), m = R().toMinutes(d.start); t.setHours(Math.floor(m / 60), m % 60, 0, 0); date = t.getTime(); }
  else date = d.day + 12 * 3600000;
  const team = teamJob(d), sameJob = was && was.amount === d.amount && !was.amountUnknown && JSON.stringify(was.workers) === JSON.stringify(d.workers);
  const amountN = sameJob ? base.totalEarnings : team ? (myShare(d) ?? 0) : (num(d.amount) || 0);
  // A job worker's pay is exactly what the form shows (amount, or hours × rate), with no separate bonus.
  const groupJson = team ? root.KisPay.encodeWorkers(d.workers.map(w => ({ name: w.name.trim(), hours: durationHours(w.hours || '') || 0, isPaid: !!w.isPaid, bonus: 0,
    workerRate: w.byHours ? num(w.rate) : null, amount: w.byHours ? null : num(w.amount), source: w.source || {} })), true) : d.mode === 'GROUP' ? JSON.stringify(d.workers.map(w => { const o = { ...(w.source || {}), name: w.name.trim(), hours: Number.isFinite(w.source?.hours) && clean(w.source.hours) === w.hours ? w.source.hours : durationHours(w.hours) || 0, isPaid: !!w.isPaid }; const b = num(w.bonus); if (b > 0) o.bonus = b; else delete o.bonus; return o; })) : '';
  const total = !payKnown ? 0 : job ? amountN
    : base && base.payKnown && base.kind !== 'job' && hours === base.hours && rate === base.hourlyRate && myBonus === (base.bonus || 0) ? base.totalEarnings
    : root.KisPay.amount(hours, rate, myBonus);
  const proto = base || { createdAt: Date.now(), isPaid: false, seriesKey: null, autoConfirmed: false };
  return { ...proto, category: d.category, date, isTimeRange: clock, startTime: clock ? d.start : null, endTime: clock ? d.end : null, hours,
    hourlyRate: job || !payKnown ? 0 : rate, totalEarnings: total, notes: d.notes.trim(), currency: d.currency, kind: job ? 'job' : 'shift', payKnown, bonus: myBonus,
    isGroupShift: d.mode === 'GROUP' || team,
    groupWorkersJson: (d.mode === 'GROUP' || team) && was && was.mode === d.mode && JSON.stringify(was.workers) === JSON.stringify(d.workers) ? base.groupWorkersJson : groupJson,
    // Untouched group rates keep their stored value exactly (an imported 33.3333 is not rounded by an unrelated edit).
    employerRate: d.mode === 'GROUP' && d.separateRates ? (was && was.employerRate === d.employerRate ? base.employerRate : num(d.employerRate)) : null,
    workerRate: d.mode === 'GROUP' && d.separateRates ? (was && was.workerRate === d.workerRate ? base.workerRate : num(d.workerRate)) : null,
    isPaid: d.isPaid, autoConfirmed: base ? false : proto.autoConfirmed };
}

function changeMode(d, mode) {
  const clock = d.mode === 'CLOCK' || d.mode === 'GROUP' && d.start != null;
  if (mode === 'HOURS' && clock) return { ...d, mode, hours: durationDisplay(String((evaluate(d).minutes || 0) / 60)) };
  if (mode === 'GROUP' && d.mode === 'HOURS') return { ...d, mode, start: null };
  if (mode === 'CLOCK' && (d.mode === 'HOURS' || d.mode === 'GROUP' && d.start == null)) {
    const minutes = Math.round((durationHours(d.hours) || 0) * 60);
    return { ...d, mode, start: minutes > 0 && minutes <= 1440 ? R().fromMinutes(R().toMinutes(d.end) - minutes) : d.start || defaultStart(d.end), breakMin: 0 };
  }
  return { ...d, mode };
}

function open(app, { entry = null, restore = null } = {}) {
  const { store } = app, base = entry;
  let d = restore || (base ? fromEntry(base) : newDraft(store));
  // A restored draft is the user's typing: leaving it asks first, like any other unsaved work.
  const original = restore ? '' : JSON.stringify(d);
  // Times are edited digit by digit: [buf] holds the four digits ("·" = not set) and [cursor] the one the next key writes.
  // [fresh]: the time was opened as a whole (not on one digit), so the first key starts it over.
  // [pad]: a time is typed with the device's own keyboard, through the hidden [sink] field.
  let target = 'START', pad = false, fresh = false, dirtyDigits = false, buf = '', cursor = 0, more = d.breakMin > 0 || !!d.notes || !!d.bonus, teamOpen = false;
  const host = document.createElement('div');
  host.className = 'editor' + (base ? ' framed' : '');
  host.setAttribute('data-testid', base ? 'edit-page' : 'add-page');
  host.setAttribute('role', 'dialog'); host.setAttribute('aria-modal', 'true'); host.setAttribute('aria-label', base ? 'עריכת משמרת' : 'משמרת חדשה');
  const opener = document.activeElement, behind = [document.getElementById('page'), document.querySelector('.nav-wrap')];
  host.innerHTML = '<div class="editor-in"><div class="kis-glow" style="position:absolute"></div><div data-top class="editor-top"></div><div data-body class="editor-body"></div><div data-bottom></div></div>'
    + `<input class="key-sink" data-testid="key-sink" inputmode="numeric" autocomplete="off" aria-label="הקלדת שעה" value="${SINK}">`;
  document.body.append(host);
  // The device's keyboard without a visible field: the page keeps the time, each key arrives here as a digit or a delete.
  const sink = host.querySelector('[data-testid=key-sink]');
  const resetSink = () => { sink.value = SINK; try { sink.setSelectionRange(SINK.length, SINK.length); } catch {} };
  sink.addEventListener('input', () => {
    const v = sink.value, removed = SINK.length - (v.match(/ /g) || []).length;
    resetSink();
    if (!pad) return;
    for (let i = 0; i < removed; i++) key('del');
    for (const ch of v) if (/\d/.test(ch)) key(ch);
  });
  sink.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); if (target === 'START' && hasEnd()) openPad('END'); else done(); } });
  // Focus leaving the hidden field (a tap on a day, a place, the rate) ends the typing and keeps the time. The page is
  // redrawn after that tap's own click (redrawing between its press and release would swallow the tap).
  sink.addEventListener('blur', () => {
    if (!pad || !commitBuf()) return;
    pad = false; fresh = false;
    let drawn = false; const later = () => { if (!drawn && !pad) { drawn = true; draw(); } };
    document.addEventListener('click', later, { once: true }); setTimeout(later, 800);
  });
  // A tap on a digit or the bar keeps the keyboard open (the focus stays in the hidden field).
  host.addEventListener('mousedown', e => { if (pad && e.target.closest?.('[data-t], [data-a=next], [data-a=done], [data-q]')) e.preventDefault(); });
  behind.forEach(b => b && (b.inert = true));
  const onKey = e => {
    if (!pad || e.target.closest?.('input, textarea')) return;
    if (/^[0-9]$/.test(e.key)) { e.preventDefault(); key(e.key); }
    else if (e.key === 'Backspace') { e.preventDefault(); key('del'); }
    else if (e.key === 'Enter') { e.preventDefault(); done(); }
  };
  document.addEventListener('keydown', onKey);
  app.editorOpen = true;
  const persist = () => { if (!base) try { localStorage.setItem(DRAFT, JSON.stringify(d)); } catch {} };
  const changed = () => JSON.stringify(d) !== original;
  function close(force = false) {
    if (!force && changed()) return U().confirmSheet(base ? 'לצאת בלי לשמור את השינויים?' : 'לצאת בלי לשמור את המשמרת?', 'מה שהקלדת לא יישמר.', 'לצאת', () => close(true));
    host.remove(); document.removeEventListener('keydown', onKey); behind.forEach(b => b && (b.inert = false));
    if (opener?.isConnected) opener.focus({ preventScroll: true });
    app.editorOpen = false; if (!base) localStorage.removeItem(DRAFT); app.refresh();
  }
  app.closeEditor = () => close();
  // Back / Escape: typing a time ends first, then the editor (asking when something was typed).
  app.editorBack = () => { if (pad) done(); else close(); };
  const set = patch => { d = { ...d, ...patch }; persist(); draw(); };
  const timeOf = t => t === 'HOURS' ? durationDisplay(d.hours) : t === 'START' ? d.start : d.end;
  const fieldDigits = t => t === 'HOURS' ? durationDigits(d.hours) : timeDigits(timeOf(t));
  const parseDigits = b => target === 'HOURS' ? parseDuration(b) : parseTime(b);
  const writeDigits = v => { d = target === 'HOURS' ? { ...d, hours: v } : target === 'START' ? { ...d, start: v } : { ...d, end: v }; persist(); };
  const shown = t => pad && t === target && buf ? buf.slice(0, 2) + ':' + buf.slice(2) : timeOf(t) || '';
  function commitBuf() {
    if (!buf) return true;
    if (/[0-9]/.test(buf) && (target !== 'HOURS' || dirtyDigits)) {
      const v = parseDigits(buf);
      if (!v) { U().toast(target === 'HOURS' ? 'משך לא תקין' : 'שעה לא תקינה'); buf = fieldDigits(target); fresh = true; return false; }
      writeDigits(v);
    }
    buf = ''; dirtyDigits = false; return true;
  }
  const hasEnd = () => d.mode === 'CLOCK' || d.mode === 'GROUP' && d.start != null;
  /**
   * Starts typing [t] with the device's keyboard. [at] is the digit (0–1 hours, 2–3 minutes) the next key replaces;
   * null selects the whole time, so typing starts it over (an empty time always starts over).
   */
  function openPad(t, at = null) {
    if (!commitBuf()) return;
    target = t; pad = true; dirtyDigits = false; buf = fieldDigits(t);
    fresh = at == null || !/[0-9]/.test(buf); cursor = fresh ? 0 : at;
    sink.setAttribute('enterkeyhint', t === 'START' && hasEnd() ? 'next' : 'done');
    sink.setAttribute('aria-label', t === 'HOURS' ? 'שעות ודקות' : t === 'START' ? 'שעת התחלה' : 'שעת סיום');
    draw();
    resetSink(); sink.focus({ preventScroll: true });
  }
  /** Ends typing: keeps the time and closes the keyboard. */
  function done() { if (!commitBuf()) return; pad = false; fresh = false; sink.blur(); draw(); }
  function key(k) {
    if (buf.length !== 4) buf = fieldDigits(target);
    // The whole time was selected: a digit starts it over, a delete takes back its last digit.
    if (fresh) { fresh = false; if (k === 'del') cursor = 4; else { buf = '····'; cursor = 0; } }
    const next = typeTimeOrNull(buf, cursor, k, target === 'HOURS' ? 24 : 23);
    if (!next) { U().haptic?.('warn'); return draw(); }
    dirtyDigits = true; [buf, cursor] = next;
    // A complete, valid value shows at once (total). A full time stays on its last digit: more keys change only that digit.
    const v = !buf.includes('·') && parseDigits(buf);
    if (v) { writeDigits(v); }
    draw();
  }
  /** Four tappable digits: a tap on one selects it (the next key replaces only it); the marked part is what the next key changes. */
  function digitsHtml(t, value, label, id) {
    const { esc } = U(), editing = pad && target === t, at = Math.min(cursor, 3);
    const v = /^\d{1,2}:\d{2}$/.test(value) ? value.padStart(5, '0') : value && value.length === 5 ? value : '--:--';
    return `<b dir="ltr" data-t="${t}" data-testid="${id}" class="digits-row${editing && fresh ? ' all' : ''}${value ? '' : ' blank'}">${[0, 1, 2, 3].map(i =>
      `${i === 2 ? '<i>:</i>' : ''}<button data-t="${t}" data-at="${i}" aria-pressed="${editing && !fresh && at === i}" aria-label="${label} · ספרה ${i + 1}" data-testid="${id}-d${i}">${esc(v[i < 2 ? i : i + 1])}</button>`).join('')}</b>`;
  }

  let lastRepeatBlocked = false, lastRepeatOn = false;
  function metaHtml(c) {
    const { moneyHtml } = U();
    const total = base && c.canSave ? root.KisPay.pay(toEntry(d, base)) : c.total;
    return `<span class="grow">${c.minutes > 0 && d.mode !== 'JOB' && !c.errors.some(e => e.startsWith('שעת') || e.startsWith('ההפסקה')) ? U().hm(c.minutes) + ' שעות' : ''}</span>
      ${total != null ? `<b class="amount" data-testid="editor-total">${moneyHtml(total, d.currency)}</b>` : d.rateUnknown && d.mode !== 'JOB' ? '<b class="label">תעריף חסר</b>' : d.amountUnknown && d.mode === 'JOB' ? '<b class="label">סכום חסר</b>' : ''}`;
  }
  function durationControl(optional = false) {
    return `<div class="field"><span>שעות ודקות${optional ? ' (לא חובה)' : ''}</span><div class="time-box${pad && target === 'HOURS' ? ' on' : ''}" data-t="HOURS">${digitsHtml('HOURS', shown('HOURS') || '00:00', 'משך', 'hours-input')}</div></div>`;
  }
  function bottomHtml(c) {
    const { esc } = U();
    // While a time is typed, a slim bar above the keyboard names it and closes it (it never saves).
    return pad ? `<div class="type-bar" data-testid="entry-bar"><span class="label grow" data-testid="entry-label">${target === 'HOURS' ? 'שעות ודקות' : target === 'START' ? 'שעת התחלה' : 'שעת סיום'}</span>
        ${target === 'START' && hasEnd() ? '<button class="btn ghost" data-a="next" data-testid="entry-next">לשעת הסיום</button>' : ''}<button class="btn primary" data-a="done" data-testid="entry-done">אישור</button></div>`
      : `<div class="save-bar">${c.canSave ? '' : `<p data-testid="save-hint">${esc(c.errors[0])}</p>`}<button class="btn primary" data-a="save" data-testid="save-shift" ${c.canSave && (!base || changed()) && !lastRepeatBlocked ? '' : 'disabled'}>${lastRepeatOn ? 'יצירת הסדרה' : base ? (changed() ? 'שמירת שינויים' : 'אין שינויים') : 'שמירה'}</button></div>`;
  }
  function draw() {
    const active = document.activeElement, focusKey = active && host.contains(active)
      ? ['data-f', 'data-testid', 'data-t', 'data-h', 'data-mode', 'data-cat'].map(a => active.getAttribute(a) != null ? `[${a}="${CSS.escape(active.getAttribute(a))}"]` : null).find(Boolean) : null;
    const caret = active && 'selectionStart' in active ? (() => { try { return active.selectionStart; } catch { return null; } })() : null;
    const c = evaluate(d), place = d.category ? U().placeOf(d.category, store) : U().PLACES[0];
    host.style.setProperty('--acc', U().accent(place)); host.style.setProperty('--acc-ink', U().accentInk(place));
    const { esc, moneyHtml } = U(), cats = store.data.categories;
    const clockMode = d.mode === 'CLOCK' || (d.mode === 'GROUP' && d.start != null);
    const top = host.querySelector('[data-top]');
    top.innerHTML = `
      <div class="top-row"><button class="pill" data-a="close" data-testid="editor-close">${base ? 'סגירה' : 'ביטול'}</button><h1>${base ? 'עריכת משמרת' : 'משמרת חדשה'}</h1>
        ${base ? '<button class="pill" data-a="delete" data-testid="editor-delete">מחיקה</button>' : '<span style="width:64px"></span>'}</div>
      ${base ? `<div class="caption" data-testid="was-banner" style="background:color-mix(in srgb,var(--ink) 8%,transparent);border-radius:10px;padding:6px 10px">${changed() ? '<span class="tag warn">שונה</span> ' : ''}לפני: ${esc(U().dayLabel(base.date))} · ${esc(U().timeLabel(base))} · ${root.KisPay.pay(base) != null ? esc(U().money(base.totalEarnings, base.currency)) : base.kind === 'job' ? 'סכום חסר' : 'תעריף חסר'}</div>` : ''}
      <div class="modes" role="tablist" aria-label="סוג הרישום">${MODES.map(([m, l]) => `<button role="tab" aria-selected="${d.mode === m}" aria-pressed="${d.mode === m}" data-mode="${m}" data-testid="mode-${m.toLowerCase()}">${l}</button>`).join('')}</div>
      <div class="places">${cats.map(x => `<button aria-pressed="${x.name === d.category}" data-cat="${esc(x.name)}" data-testid="place-${esc(x.name)}">${esc(x.name)}</button>`).join('')}<button data-a="add-place" data-testid="add-place">+ מקום</button></div>
      ${d.mode === 'JOB' ? `<div class="row"><label class="field grow">סכום (${esc(d.currency)})<input class="input" inputmode="decimal" data-f="amount" value="${esc(d.amount)}" ${d.amountUnknown ? 'disabled' : ''} data-testid="job-amount"></label>
          <div>${durationControl(true)}</div></div>
          <button class="chip${d.amountUnknown ? ' on' : ''}" data-a="amount-unknown">${d.amountUnknown ? 'סכום חסר · נגיעה להזנה' : 'לא יודע עדיין'}</button>`
        : clockMode ? `<div class="clock-row">${[['START', 'התחלה'], ['END', 'סיום']].map(([t, label]) => { const v = shown(t) || '--:--', id = t === 'START' ? 'clock-start' : 'clock-end';
            // A tap on the time selects all of it (typing starts it over); a tap on one digit changes only it. Each time has its own −15/+15.
            return `<div class="time-box${target === t && pad ? ' on' : ''}" data-t="${t}"><span class="caption">${label}</span>
              ${digitsHtml(t, shown(t), label, id)}
              ${pad || !timeOf(t) ? '' : `<div class="nudges">${[-15, 15].map(n => `<button data-nt="${t}" data-n="${n}" aria-label="${label} ${n > 0 ? n + ' דקות קדימה' : -n + ' דקות אחורה'}" data-testid="nudge-${t.toLowerCase()}-${n}">${n > 0 ? '+' + n : '−' + -n}</button>`).join('')}</div>`}</div>`; }).join('')}</div>
          ${pad ? '' : ruler(d, target)}
          ${d.mode === 'GROUP' ? '<button class="link" data-a="group-hours">הזנת שעות במקום שעון</button>' : ''}`
        : `${durationControl()}
          <div class="chips">${[4, 6, 8, 10, 12].map(h => `<button class="chip" data-q="${h}">${h} ש׳</button>`).join('')}${d.mode === 'GROUP' ? '<button class="chip" data-a="group-clock">לפי שעון</button>' : ''}</div>`}
      <div class="meta-line" data-testid="editor-meta">${metaHtml(c)}</div>`;

    const body = host.querySelector('[data-body]');
    const days = Array.from({ length: 7 }, (_, i) => (() => { const day = new Date(U().startOfDay(Date.now())); day.setDate(day.getDate() + i - 3); return day.getTime(); })());
    const repeatOn = d.repeat && !base && d.mode === 'CLOCK' && d.start != null;
    const repeatDays = new Set(d.repeatDays ?? [new Date(d.day).getDay()]);
    const fromIso = root.KisPlan.dateOf(d.day), until = d.repeatUntil || root.KisRules.civilFromDays(root.KisRules.daysFromCivil(fromIso) + 56);
    const preview = repeatOn ? root.KisPlans.previewFor(store, repeatDays, fromIso, until < fromIso ? fromIso : until, d.start, d.end) : null;
    const created = preview ? preview.dates.length - preview.clashes.length : 0;
    body.innerHTML = `
      <div class="days">${days.map(ms => `<button aria-pressed="${ms === d.day}" data-day="${ms}"><small>${ms === days[3] ? 'היום' : U().DAY_SHORT[new Date(ms).getDay()]}</small>${new Date(ms).getDate()}</button>`).join('')}</div>
      <div class="row" style="align-items:center;flex-wrap:wrap"><label class="chip" style="position:relative">${days.includes(d.day) ? 'תאריך אחר' : esc(U().dayLabelLong(d.day))}
          <input type="date" data-f="date" value="${root.KisHistory.iso(d.day)}" aria-label="תאריך אחר" style="position:absolute;inset:0;opacity:0;cursor:pointer"></label>
        <div class="seg">${['₪', '$'].map(cur => `<button aria-pressed="${d.currency === cur}" data-cur="${cur}">${cur}</button>`).join('')}</div></div>
      ${d.mode !== 'JOB' ? `<div class="row" style="align-items:flex-end;flex-wrap:wrap"><label class="field grow" style="min-width:140px">תעריף לשעה (${esc(d.currency)})<input class="input" inputmode="decimal" data-f="rate" value="${esc(d.rate)}" ${d.rateUnknown ? 'disabled' : ''} data-testid="rate-input"></label>
          <button class="chip${d.rateUnknown ? ' warn' : ''}" style="min-height:52px" data-a="rate-unknown" data-testid="rate-unknown">${d.rateUnknown ? 'תעריף חסר · נגיעה להזנה' : 'לא יודע'}</button>
          <button class="chip" style="min-height:52px" data-a="more" data-testid="editor-more">${more ? 'פחות' : 'הפסקה · הערה · בונוס'}</button></div>
` : ''}
      ${d.mode === 'GROUP' ? groupBlock(d) : ''}
      ${d.mode === 'JOB' ? jobTeamBlock(d) : ''}
      ${!base && d.mode === 'CLOCK' && d.start != null ? `<div class="card" style="display:grid;gap:10px"><div class="row" style="align-items:center"><div class="grow title-row"><b class="label">חוזרת כל שבוע</b>${U().infoDot('חוזרת כל שבוע', 'משמרת קבועה: תופיע מראש ב"מתוכנן" ותירשם לבד כשהזמן נגמר. תמיד אפשר לבטל.')}</div>
          <button class="switch" role="switch" aria-checked="${d.repeat}" aria-label="חוזרת כל שבוע" data-a="repeat" data-testid="repeat-switch"></button></div>
          ${d.repeat ? `<b class="label">בימים</b><div class="weekdays">${U().DAY_SHORT.map((n, i) => `<button aria-pressed="${repeatDays.has(i)}" data-wd="${i}" data-testid="repeat-day-${i}">${n}</button>`).join('')}</div>
            <label class="chip" style="position:relative;justify-self:start">עד ${U().shortDate(root.KisPlan.startMillis(until, '12:00'))}<input type="date" data-f="until" value="${until}" aria-label="עד תאריך" style="position:absolute;inset:0;opacity:0"></label>
            <p class="caption" data-testid="repeat-preview">${!preview.dates.length ? 'אין ימים בטווח שנבחר' : (created === 1 ? 'משמרת אחת תיכנס ללוח' : created + ' משמרות ייכנסו ללוח') + (preview.clashes.length ? ` · ${preview.clashes.length} חופפות למשמרת קיימת ולא ייווצרו` : '')}</p>
            ${d.breakMin > 0 || d.notes.trim() ? `<p class="caption">${[d.breakMin > 0 ? 'הפסקה' : '', d.notes.trim() ? 'הערה' : ''].filter(Boolean).join(' ו')} לא נשמרות במשמרת קבועה</p>` : ''}` : ''}</div>` : ''}
      ${more && d.mode !== 'JOB' ? `<div class="card" style="display:grid;gap:10px">
          ${clockMode ? `<b class="label">הפסקה</b><div class="chips">${[0, 15, 30, 45, 60].map(m => `<button class="chip" aria-pressed="${d.breakMin === m}" data-b="${m}" data-testid="break-${m}">${m ? m + ' דק׳' : 'ללא'}</button>`).join('')}
            <input class="input" style="width:90px;min-height:44px" inputmode="numeric" data-f="break" value="${[0, 15, 30, 45, 60].includes(d.breakMin) ? '' : d.breakMin}" placeholder="אחר" aria-label="הפסקה בדקות"></div>` : ''}
          <label class="field">בונוס (${esc(d.currency)})<input class="input" inputmode="decimal" data-f="bonus" value="${esc(d.bonus)}" placeholder="לא חובה"></label>
          <label class="field">הערה<textarea data-f="notes" placeholder="לא חובה" data-testid="notes-input">${esc(d.notes)}</textarea></label></div>` : ''}
      ${d.mode === 'JOB' ? `<label class="field">מה עשית<textarea data-f="notes" placeholder="לא חובה" data-testid="notes-input">${esc(d.notes)}</textarea></label>` : ''}`;

    const bottom = host.querySelector('[data-bottom]');
    lastRepeatBlocked = repeatOn && !(preview && created > 0); lastRepeatOn = repeatOn;
    bottom.innerHTML = bottomHtml(c);
    // Keep the keyboard where it was: the same control is focused again after a redraw.
    if (focusKey) { const el = host.querySelector(focusKey); if (el) { el.focus({ preventScroll: true }); if (caret != null && el.setSelectionRange) try { el.setSelectionRange(caret, caret); } catch {} } }
    host.querySelector('.days [aria-pressed=true]')?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    bind();
  }

  function bind() {
    const q = s => host.querySelector(s), all = s => host.querySelectorAll(s);
    q('[data-a=close]').onclick = () => close();
    q('[data-a=delete]')?.addEventListener('click', () => U().confirmSheet('למחוק את המשמרת?', 'אפשר לבטל מיד אחרי המחיקה.', 'מחיקה', () => {
      const gone = store.deleteEntries([base.id]); close(true); U().toast('המשמרת נמחקה', 'ביטול', () => store.restoreEntries(gone)); }));
    all('[data-mode]').forEach(b => b.onclick = () => { if (pad) done(); if (pad) return; const mode = b.dataset.mode;
      target = mode === 'CLOCK' || mode === 'GROUP' ? 'START' : target;
      d = changeMode(d, mode); persist(); draw(); });
    all('[data-cat]').forEach(b => b.onclick = () => { commitBuf(); const cat = store.data.categories.find(x => x.name === b.dataset.cat);
      set({ category: cat.name, rate: d.rateUnknown ? d.rate : clean(cat.defaultRate), currency: root.WorkCategories.currency(cat.name, store.data.webPreferences) }); });
    q('[data-a=add-place]').onclick = () => root.KisSettings.placeSheet(app, null, name => { const cat = store.data.categories.find(x => x.name === name); set({ category: name, rate: clean(cat?.defaultRate), currency: root.WorkCategories.currency(name, store.data.webPreferences) }); });
    all('[data-t]').forEach(b => b.onclick = e => { e.stopPropagation(); openPad(b.dataset.t, b.dataset.at != null ? +b.dataset.at : null); });
    q('[data-a=next]')?.addEventListener('click', () => openPad('END'));
    q('[data-a=done]')?.addEventListener('click', () => done());
    all('[data-nt]').forEach(b => b.onclick = e => { e.stopPropagation(); const t = b.dataset.nt, v = timeOf(t), n = +b.dataset.n; if (!v) return;
      set(t === 'START' ? { start: R().fromMinutes(R().toMinutes(v) + n) } : { end: R().fromMinutes(R().toMinutes(v) + n) }); U().haptic('tick'); });
    all('[data-q]').forEach(b => b.onclick = () => { buf = ''; dirtyDigits = false; fresh = true; set({ hours: b.dataset.q }); });
    all('[data-day]').forEach(b => b.onclick = () => set({ day: +b.dataset.day, repeatDays: d.repeatDays ? [...new Set([...d.repeatDays, new Date(+b.dataset.day).getDay()])] : null }));
    all('[data-cur]').forEach(b => b.onclick = () => set({ currency: b.dataset.cur }));
    all('[data-b]').forEach(b => b.onclick = () => set({ breakMin: +b.dataset.b }));
    all('[data-wd]').forEach(b => b.onclick = () => { const s = new Set(d.repeatDays ?? [new Date(d.day).getDay()]); const i = +b.dataset.wd; s.has(i) ? s.delete(i) : s.add(i); set({ repeatDays: [...s].sort() }); });
    q('[data-a=group-hours]')?.addEventListener('click', () => set({ start: null }));
    q('[data-a=group-clock]')?.addEventListener('click', () => set({ start: defaultStart(d.end) }));
    q('[data-a=rate-unknown]')?.addEventListener('click', () => set({ rateUnknown: !d.rateUnknown }));
    q('[data-a=amount-unknown]')?.addEventListener('click', () => set({ amountUnknown: !d.amountUnknown }));
    q('[data-a=more]')?.addEventListener('click', () => { more = !more; draw(); });
    q('[data-a=repeat]')?.addEventListener('click', () => set({ repeat: !d.repeat }));
    q('[data-a=save]')?.addEventListener('click', save);
    // Text fields update the draft without redrawing (no lost caret); numbers redraw the totals on change.
    all('[data-f]').forEach(i => {
      const f = i.dataset.f;
      const apply = () => {
        if (f === 'date' || f === 'until') { if (!i.value) return; if (f === 'until') return set({ repeatUntil: i.value });
          const [y, m, dd] = i.value.split('-').map(Number); const day = new Date(y, m - 1, dd).getTime(); return set({ day, repeatDays: d.repeatDays ? [...new Set([...d.repeatDays, new Date(day).getDay()])] : null }); }
        if (f === 'break') return set({ breakMin: Math.max(0, Math.round(num(i.value) || 0)) });
        d = { ...d, [f]: f === 'hours' ? durationEdit(i.value) : i.value }; persist();
      };
      if (f === 'notes') i.oninput = () => { apply(); refreshMeta(); };
      else if (f === 'date' || f === 'until') i.onchange = apply;
      // Numbers update the totals and the save bar in place: a tap on "שמירה" right after typing is never lost.
      else { i.oninput = () => { d = { ...d, [f]: f === 'hours' ? durationEdit(i.value) : i.value }; persist(); refreshMeta(); }; i.onchange = () => { apply(); refreshMeta(); }; }
    });
    all('[data-w]').forEach(i => { const [idx, f] = i.dataset.w.split(':'); const ev = f === 'isPaid' ? 'change' : 'input';
      i.addEventListener(ev, () => { const ws = d.workers.map((w, k) => k === +idx ? { ...w, [f]: f === 'isPaid' ? i.checked : f === 'hours' ? durationEdit(i.value) : i.value } : w); d = { ...d, workers: ws }; persist(); if (f !== 'name') refreshMeta(); }); });
    all('[data-wdel]').forEach(b => b.onclick = () => set({ workers: d.workers.filter((_, k) => k !== +b.dataset.wdel) }));
    q('[data-a=add-worker]')?.addEventListener('click', () => set({ workers: [...d.workers, { name: '', hours: d.start ? String((evaluate(d).minutes || 0) / 60) : d.hours, isPaid: false, bonus: '', source: {} }] }));
    all('[data-wname]').forEach(b => b.onclick = () => set({ workers: [...d.workers, { name: b.dataset.wname, hours: d.start ? String((evaluate(d).minutes || 0) / 60) : d.hours, isPaid: false, bonus: '', source: {} }] }));
    q('[data-a=sep]')?.addEventListener('click', () => set({ separateRates: !d.separateRates }));
    // Job team (קבלנות): each person gets a fixed sum or hours × their own rate.
    q('[data-a=job-team-open]')?.addEventListener('click', () => { teamOpen = true; draw(); });
    all('[data-jw]').forEach(i => { const [idx, f] = i.dataset.jw.split(':');
      i.addEventListener('input', () => { d = { ...d, workers: d.workers.map((w, k) => k === +idx ? { ...w, [f]: f === 'hours' ? durationEdit(i.value) : i.value } : w) }; persist(); refreshMeta(); }); });
    all('[data-jwmode]').forEach(b => b.onclick = () => { const [idx, m] = b.dataset.jwmode.split(':'); set({ workers: d.workers.map((w, k) => k === +idx ? { ...w, byHours: m === 'hourly' } : w) }); });
    all('[data-jwdel]').forEach(b => b.onclick = () => set({ workers: d.workers.filter((_, k) => k !== +b.dataset.jwdel) }));
    const jobWorker = name => ({ name, hours: '', isPaid: false, bonus: '', source: {}, byHours: false, rate: '', amount: '' });
    all('[data-jwname]').forEach(b => b.onclick = () => set({ workers: [...d.workers, jobWorker(b.dataset.jwname)] }));
    q('[data-a=job-add-worker]')?.addEventListener('click', () => { const n = q('[data-testid=job-worker-name]')?.value.trim(); if (n) set({ workers: [...d.workers, jobWorker(n)] }); });
    q('[data-testid=job-worker-name]')?.addEventListener('keydown', ev => { if (ev.key === 'Enter') { ev.preventDefault(); q('[data-a=job-add-worker]').click(); } });
    bindSplit();
    all('[data-bt]').forEach(b => b.onclick = () => set({ bonusTarget: b.dataset.bt }));
    rulerBind(host, d, t => { target = t; }, (h, v) => { d = h === 'START' ? { ...d, start: v } : { ...d, end: v }; persist(); draw(); });
  }
  function refreshMeta() {
    const c = evaluate(d);
    host.querySelector('[data-testid=editor-meta]').innerHTML = metaHtml(c);
    const totals = host.querySelector('[data-testid=job-team-totals]');
    if (totals) { totals.outerHTML = jobTeamTotals(d); bindSplit(); }
    if (pad) return;
    // The same save button is updated in place, so a tap that lands while a field commits still saves.
    const btn = host.querySelector('[data-a=save]'), hint = host.querySelector('[data-testid=save-hint]');
    if (btn) {
      btn.disabled = !(c.canSave && (!base || changed()) && !lastRepeatBlocked);
      btn.textContent = lastRepeatOn ? 'יצירת הסדרה' : base ? (changed() ? 'שמירת שינויים' : 'אין שינויים') : 'שמירה';
      if (c.canSave) hint?.remove();
      else if (hint) hint.textContent = c.errors[0];
      else btn.insertAdjacentHTML('beforebegin', `<p data-testid="save-hint">${U().esc(c.errors[0])}</p>`);
    }
  }

  function save() {
    const c = evaluate(d); if (!c.canSave) { U().haptic('warn'); return; }
    if (d.repeat && !base && d.mode === 'CLOCK' && d.start != null) {
      const from = root.KisPlan.dateOf(d.day), until = d.repeatUntil || root.KisRules.civilFromDays(root.KisRules.daysFromCivil(from) + 56);
      const res = root.KisPlans.createSeries(store, { category: d.category, days: new Set(d.repeatDays ?? [new Date(d.day).getDay()]), from, until, start: d.start, end: d.end,
        rate: d.rateUnknown ? null : num(d.rate), currency: d.currency });
      close(true); U().haptic('success');
      U().toast((res.created === 1 ? 'נוצרה משמרת קבועה אחת' : `נוצרו ${res.created} משמרות קבועות`) + (res.recorded.length ? ` · ${res.recorded.length} שכבר עברו נרשמו` : ''));
      return;
    }
    const row = toEntry(d, base);
    try {
      if (base) { store.replaceEntry(base.id, row, base); close(true); U().haptic('success'); U().toast('השינויים נשמרו ✓', 'ביטול', () => store.replaceEntry(base.id, base)); }
      else {
        const [id] = store.addEntries([row]); remember(d); close(true); U().haptic('success');
        U().toast(row.payKnown ? 'המשמרת נשמרה ✓' : 'נשמרה · התעריף יחכה להשלמה', 'ביטול', () => { const gone = store.data.entries.filter(e => e.id === id); store.update(x => { x.entries = x.entries.filter(e => e.id !== id); }); return gone; });
      }
    } catch (e) { U().toast(e.message); }
  }
  function remember(dr) { // new worker names join the directory for next time (Android: workersDirectory)
    if (dr.mode !== 'GROUP' && dr.mode !== 'JOB') return;
    const names = dr.workers.map(w => w.name.trim()).filter(Boolean).filter(n => !store.data.workers.some(w => w.name === n));
    if (names.length) store.update(x => { x.workers.push(...names.map(name => ({ name }))); });
  }
  function bindSplit() {
    host.querySelector('[data-a=job-split]')?.addEventListener('click', () => { const price = num(d.amount), each = Math.floor(price / (d.workers.length + 1) * 100) / 100;
      set({ workers: d.workers.map(w => ({ ...w, amount: clean(each) })) }); });
  }
  function jobTeamBlock(dr) {
    const { esc } = U();
    // A solo job stays as simple as before: the team opens only when asked for (or when it already has people).
    if (!teamOpen && !dr.workers.length) return '<button class="chip dashed" data-a="job-team-open" data-testid="job-team-open" style="justify-self:start">+ עבדו איתי עוד אנשים</button>';
    const known = store.data.workers.map(w => w.name).filter(n => !dr.workers.some(w => w.name === n));
    return `<div class="card" style="display:grid;gap:10px" data-testid="job-team-block"><b class="label">מי עבד איתך</b>
      <span class="caption">לכל אחד סכום קבוע או שעות לפי תעריף משלו. מה שנשאר אחרי הצוות הוא ההכנסה שלך, ורק הוא נספר בסיכומים.</span>
      ${dr.workers.map((w, i) => `<div style="display:grid;gap:8px;padding:10px;border-radius:14px;background:var(--bg)" data-testid="job-worker-${i}">
        <div class="row" style="align-items:center"><b class="label grow">${esc(w.name)}</b>
          <div class="seg"><button aria-pressed="${!w.byHours}" data-jwmode="${i}:fixed" data-testid="job-worker-fixed-${i}">סכום</button><button aria-pressed="${!!w.byHours}" data-jwmode="${i}:hourly" data-testid="job-worker-hourly-${i}">לפי שעה</button></div>
          <button class="icon-btn" aria-label="הסרת ${esc(w.name)}" data-jwdel="${i}">${U().ICON.close}</button></div>
        ${w.byHours ? `<div class="row"><label class="field grow">שעות:דקות<input class="input" inputmode="decimal" dir="ltr" value="${esc(durationDisplay(w.hours || ''))}" data-jw="${i}:hours" data-testid="job-worker-hours-${i}"></label>
          <label class="field grow">לשעה (${esc(dr.currency)})<input class="input" inputmode="decimal" value="${esc(w.rate || '')}" data-jw="${i}:rate" data-testid="job-worker-rate-${i}"></label></div>`
        : `<label class="field">כמה הוא מקבל (${esc(dr.currency)})<input class="input" inputmode="decimal" value="${esc(w.amount || '')}" data-jw="${i}:amount" data-testid="job-worker-amount-${i}"></label>`}</div>`).join('')}
      ${known.length ? `<div class="chips">${known.map(n => `<button class="chip dashed" data-jwname="${esc(n)}">+ ${esc(n)}</button>`).join('')}</div>` : ''}
      <div class="row" style="align-items:flex-end"><label class="field grow">עובד חדש<input class="input" data-testid="job-worker-name" enterkeyhint="done"></label>
        <button class="chip" style="min-height:52px" data-a="job-add-worker" data-testid="job-worker-add">+ הוספה</button></div>
      ${dr.workers.length ? jobTeamTotals(dr) : ''}</div>`;
  }
  function jobTeamTotals(dr) {
    const { moneyHtml, esc } = U(), team = teamPayOf(dr), price = num(dr.amount), share = myShare(dr);
    const line = (k, v, id) => `<div class="row"><span class="caption grow">${k}</span><b class="label" data-testid="${id}">${v == null ? '—' : moneyHtml(v, dr.currency)}</b></div>`;
    const err = evaluate(dr).errors.find(e => e === 'חסר כמה מקבל כל עובד' || e === 'הצוות מקבל יותר מסכום העבודה');
    return `<div style="display:grid;gap:4px" data-testid="job-team-totals">${dr.amountUnknown ? '' : line('סכום העבודה', price, 'job-price')}${line('לצוות', team, 'job-team-pay')}${dr.amountUnknown ? '' : line('נשאר לך', share, 'job-my-share')}
      ${err ? `<span class="caption" style="color:var(--pending)" data-testid="job-team-error">${esc(err)}</span>` : ''}
      ${price > 0 && !dr.workers.some(w => w.byHours) ? `<button class="link" data-a="job-split" data-testid="job-split-even" style="justify-self:start">חלוקה שווה (${dr.workers.length + 1} אנשים)</button>` : ''}</div>`;
  }
  function groupBlock(dr) {
    const { esc } = U(), known = store.data.workers.map(w => w.name).filter(n => !dr.workers.some(w => w.name === n));
    return `<div class="card" style="display:grid;gap:10px" data-testid="group-block"><b class="label">עובדים איתי</b>
      ${dr.workers.map((w, i) => `<div class="row" style="align-items:center"><input class="input grow" placeholder="שם" value="${esc(w.name)}" data-w="${i}:name" aria-label="שם עובד">
        <input class="input" style="width:84px" inputmode="decimal" placeholder="1:40" dir="ltr" value="${esc(durationDisplay(w.hours))}" data-w="${i}:hours" aria-label="שעות ודקות">
        <label class="caption" style="display:flex;align-items:center;gap:4px"><input type="checkbox" ${w.isPaid ? 'checked' : ''} data-w="${i}:isPaid">שולם</label>
        <button class="icon-btn" aria-label="הסרה" data-wdel="${i}">${U().ICON.close}</button></div>`).join('')}
      <div class="chips"><button class="chip" data-a="add-worker" data-testid="add-worker">+ עובד</button>${known.map(n => `<button class="chip dashed" data-wname="${esc(n)}">${esc(n)}</button>`).join('')}</div>
      <button class="chip" aria-pressed="${dr.separateRates}" data-a="sep" style="justify-self:start">תעריפים נפרדים למעסיק ולעובדים</button>
      ${dr.separateRates ? `<div class="row"><label class="field grow">המעסיק משלם לשעת עובד<input class="input" inputmode="decimal" data-f="employerRate" value="${esc(dr.employerRate)}"></label>
        <label class="field grow">אני משלם לעובד לשעה<input class="input" inputmode="decimal" data-f="workerRate" value="${esc(dr.workerRate)}"></label></div>` : ''}
      ${num(dr.bonus) > 0 ? `<span class="caption">הבונוס של</span><div class="seg">${[['ME', 'שלי'], ['OTHERS', 'העובדים'], ['ALL', 'כולם']].map(([v, l]) => `<button aria-pressed="${dr.bonusTarget === v}" data-bt="${v}">${l}</button>`).join('')}</div>` : ''}</div>`;
  }
  draw();
  host.querySelector('[data-testid=editor-close]')?.focus({ preventScroll: true });
  return { close };
}

function ruler(d, target) {
  if (!d.start) return `<div class="ruler" data-ruler><div class="track"></div>${handle('END', d.end, 46, 'סיום', target)}${ticks()}</div>`;
  const a = R().toMinutes(d.start), b = R().toMinutes(d.end), pieces = b > a ? [[a, b]] : [[a, 1440], [0, b]];
  return `<div class="ruler" data-ruler><div class="track"></div>${pieces.filter(([x, y]) => y > x).map(([x, y]) => `<div class="span" style="left:${(1 - y / 1440) * 100}%;width:${(y - x) / 1440 * 100}%"></div>`).join('')}
    ${handle('START', d.start, 0, 'התחלה', target)}${handle('END', d.end, 46, 'סיום', target)}${ticks()}</div>`;
}
const handle = (h, v, top, label, target) => `<div class="handle${target === h ? ' on' : ''}" data-h="${h}" data-testid="ruler-${h.toLowerCase()}" role="slider" aria-label="${label}" aria-valuemin="0" aria-valuemax="1439" aria-valuenow="${R().toMinutes(v)}" aria-valuetext="${v}" tabindex="0" style="top:${top}px;left:${(1 - R().toMinutes(v) / 1440) * 100}%">${label}</div>`;
const ticks = () => `<div class="ticks">${[0, 6, 12, 18, 24].map(h => `<span style="left:${(1 - h / 24) * 100}%">${String(h).padStart(2, '0')}</span>`).join('')}</div>`;
/** Handles move only after a deliberate 8px drag, in 15-minute steps (00 on the right, as in RTL). Arrow keys move by 15. */
function rulerBind(host, d, select, change) {
  const box = host.querySelector('[data-ruler]'); if (!box) return;
  box.querySelectorAll('[data-h]').forEach(h => {
    h.addEventListener('keydown', e => { const step = e.key === 'ArrowLeft' ? 15 : e.key === 'ArrowRight' ? -15 : 0; if (!step) return; e.preventDefault();
      const v = h.dataset.h === 'START' ? d.start : d.end; select(h.dataset.h); change(h.dataset.h, R().fromMinutes(R().toMinutes(v) + step)); });
    h.addEventListener('pointerdown', e => {
      const rect = box.getBoundingClientRect(), x0 = e.clientX; let live = false, last = null;
      h.setPointerCapture(e.pointerId);
      const move = ev => { if (!live && Math.abs(ev.clientX - x0) < 8) return; live = true;
        const m = Math.min(1425, Math.max(0, Math.round((1 - (ev.clientX - rect.left) / rect.width) * 96) * 15));
        if (m !== last) { last = m; h.style.left = (1 - m / 1440) * 100 + '%'; h.setAttribute('aria-valuetext', R().fromMinutes(m)); U().haptic('tick'); } };
      const up = () => { h.removeEventListener('pointermove', move); h.removeEventListener('pointerup', up); h.removeEventListener('pointercancel', up);
        select(h.dataset.h); if (live && last != null) change(h.dataset.h, R().fromMinutes(last)); else change(h.dataset.h, h.dataset.h === 'START' ? d.start : d.end); };
      h.addEventListener('pointermove', move); h.addEventListener('pointerup', up); h.addEventListener('pointercancel', up);
    });
  });
}
function pendingDraft() { try { const d = JSON.parse(localStorage.getItem(DRAFT) || 'null'); return d && typeof d === 'object' && d.mode ? d : null; } catch { return null; } }

root.KisEditor = { open, toEntry, fromEntry, evaluate, newDraft, suggestStart, pendingDraft, DRAFT, typeTime, typeTimeOrNull, parseTime, timeDigits, defaultStart, DEFAULT_MINUTES, durationHours, durationEdit, durationDisplay, durationDigits, parseDuration, changeMode };
})(globalThis);
