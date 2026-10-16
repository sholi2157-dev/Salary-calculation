/* Salary Calculation 2.0 prototypes: shared behaviour engine.
   Synthetic data only. Every direction renders its own UI on top of this. */
const Core = (() => {
  'use strict';
  const DAY = ['ראשון', 'שני', 'שלישי', 'רביעי', 'חמישי', 'שישי', 'שבת'];
  const DAY_SHORT = ['א׳', 'ב׳', 'ג׳', 'ד׳', 'ה׳', 'ו׳', 'ש׳'];
  const MONTH = ['ינואר', 'פברואר', 'מרץ', 'אפריל', 'מאי', 'יוני', 'יולי', 'אוגוסט', 'ספטמבר', 'אוקטובר', 'נובמבר', 'דצמבר'];

  /* ---------- dates & times ---------- */
  const pad = n => String(n).padStart(2, '0');
  const ymd = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const parseYmd = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
  const addDays = (s, n) => { const d = parseYmd(s); d.setDate(d.getDate() + n); return ymd(d); };
  const toMin = t => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };
  const fromMin = m => { m = ((m % 1440) + 1440) % 1440; return pad(Math.floor(m / 60)) + ':' + pad(m % 60); };
  const weekday = s => parseYmd(s).getDay();
  const ym = s => s.slice(0, 7);

  let clockOffset = 0; // minutes; the prototype panel can move time forward
  const now = () => new Date(Date.now() + clockOffset * 60000);
  const today = () => ymd(now());
  const nowMin = () => { const n = now(); return n.getHours() * 60 + n.getMinutes(); };
  const roundedEnd = () => fromMin(Math.floor(nowMin() / 30) * 30);

  function dayLabel(s) {
    const t = today();
    if (s === t) return 'היום';
    if (s === addDays(t, -1)) return 'אתמול';
    if (s === addDays(t, 1)) return 'מחר';
    const d = parseYmd(s);
    return `יום ${DAY_SHORT[d.getDay()]} ${d.getDate()}.${d.getMonth() + 1}`;
  }
  const shortDate = s => { const d = parseYmd(s); return `${d.getDate()}.${d.getMonth() + 1}`; };
  const monthName = k => { const [y, m] = k.split('-').map(Number); return `${MONTH[m - 1]} ${y}`; };
  const prevMonth = k => { const [y, m] = k.split('-').map(Number); const d = new Date(y, m - 2, 1); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`; };
  const nextMonth = k => { const [y, m] = k.split('-').map(Number); const d = new Date(y, m, 1); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`; };

  /* Shift span. End earlier than start means the shift crosses midnight. */
  function span(start, end, breakMin = 0) {
    if (!start || !end) return null;
    const s = toMin(start); let e = toMin(end);
    if (e === s) return { minutes: 0, overnight: false, invalid: true, reason: 'שעת ההתחלה והסיום זהות' };
    const overnight = e < s;
    if (overnight) e += 1440;
    const minutes = e - s - (Number(breakMin) || 0);
    if (minutes <= 0) return { minutes: 0, overnight, invalid: true, reason: 'ההפסקה ארוכה מהמשמרת' };
    return { minutes, overnight, invalid: false, long: minutes > 16 * 60 };
  }
  const endDateTime = (date, start, end) => {
    const d = parseYmd(date); const s = toMin(start); let e = toMin(end); if (e <= s) e += 1440;
    d.setMinutes(e); return d;
  };
  const startDateTime = (date, start) => { const d = parseYmd(date); d.setMinutes(toMin(start)); return d; };

  /* ---------- formatting ---------- */
  const nf = new Intl.NumberFormat('he-IL', { maximumFractionDigits: 2 });
  const nf0 = new Intl.NumberFormat('he-IL', { maximumFractionDigits: 0 });
  const money = (v, cur, whole) => cur + (whole ? nf0 : nf).format(v);
  const hours = mins => { const h = mins / 60; return (Math.round(h * 100) / 100).toLocaleString('he-IL', { maximumFractionDigits: 2 }); };
  const hm = mins => `${Math.floor(mins / 60)}:${pad(mins % 60)}`;
  const round2 = v => Math.round((v + Number.EPSILON) * 100) / 100;
  const pl = (n, one, many) => n === 1 ? one : many.replace('#', n);
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /* ---------- state ---------- */
  let KEY = 'salary-v2-proto';
  let state;
  const listeners = new Set();
  const uid = () => Math.random().toString(36).slice(2, 10);

  const CATS = [
    { id: 'c1', name: 'מסעדת הנמל', rate: 52, currency: '₪', hue: 0 },
    { id: 'c2', name: 'שיעורים פרטיים', rate: 120, currency: '₪', hue: 1 },
    { id: 'c3', name: 'פרילנס — עיצוב', rate: 35, currency: '$', hue: 2 },
    { id: 'c4', name: 'גן אירועים אלמוג', rate: null, currency: '₪', hue: 3 },
  ];
  const WORKERS = ['נועה', 'איתי', 'מאיה', 'עומר'];

  function seed() {
    const t = today();
    const shifts = [];
    const add = (offset, cat, start, end, extra = {}) => {
      const date = addDays(t, offset);
      const c = CATS.find(x => x.id === cat);
      const rate = 'rate' in extra ? extra.rate : c.rate;
      const sp = span(start, end, extra.breakMin || 0);
      const bonus = extra.bonus || 0;
      shifts.push({
        id: uid(), cat, date, start, end, breakMin: extra.breakMin || 0, rate, currency: c.currency, bonus,
        total: rate == null ? null : round2(sp.minutes / 60 * rate + bonus),
        paid: offset < -13, notes: extra.notes || '', group: extra.group || null, auto: false, ruleKey: null,
      });
    };
    // Six weeks of a realistic mixed pattern.
    for (let off = -44; off <= -1; off++) {
      const wd = weekday(addDays(t, off));
      if (wd === 0 || wd === 2) add(off, 'c1', '17:00', off % 3 === 0 ? '23:00' : '22:30');
      if (wd === 4 && off < -7) add(off, 'c1', '17:00', '22:30');
      if (wd === 1 || wd === 3) add(off, 'c2', '16:00', '18:00');
      if (wd === 5 && off % 2 === 0) add(off, 'c3', '09:00', '13:30', { breakMin: 30 });
    }
    // Details that show the new behaviour.
    add(-9, 'c1', '20:00', '02:00', { bonus: 50, notes: 'סגירה של אירוע פרטי' });
    add(-6, 'c4', '18:00', '01:00', { rate: null, notes: 'חתונה — התעריף עוד לא סוכם' });
    add(-2, 'c4', '19:00', '00:30', { rate: null });
    // Hours-only entry (the existing "ידני" mode): no clock times.
    shifts.push({ id: uid(), cat: 'c3', date: addDays(t, -3), start: null, end: null, hours: 3.5, breakMin: 0, rate: 35, currency: '$', bonus: 0,
      total: round2(3.5 * 35), paid: false, notes: 'תיקוני לוגו מהבית', group: null, auto: false, ruleKey: null });
    add(-12, 'c4', '17:30', '23:30', {
      rate: 45, bonus: 40,
      group: { workers: [{ name: 'נועה', bonus: 40 }, { name: 'איתי', bonus: 40 }, { name: 'מאיה', bonus: 0 }] },
      notes: 'הפקה עם צוות',
    });
    const rules = [{
      id: 'r1', cat: 'c1', days: [0, 2, 4], start: '17:00', end: '22:30', rate: 52, currency: '₪',
      from: t, until: addDays(t, 55),
    }, {
      id: 'r2', cat: 'c2', days: [1, 3], start: '16:00', end: '18:00', rate: 120, currency: '₪',
      from: addDays(t, 1), until: addDays(t, 40),
    }];
    return {
      v: 2, cats: CATS.map(c => ({ ...c })), workers: [...WORKERS], shifts, rules, exceptions: {},
      settings: {
        autoConfirm: true, notifyMonthly: true, notifyRate: true, rateFrequency: 'weekly',
        planningView: 'unified', defaultCat: 'c1', aiKey: false, theme: 'system',
        quiet: true, quietFrom: '22:00', quietTo: '07:00',
      },
      lastAuto: [], clockOffset: 0,
    };
  }

  function load(direction) {
    KEY = 'salary-v2-proto-' + direction;
    try { state = JSON.parse(localStorage.getItem(KEY) || 'null'); } catch { state = null; }
    if (!state || state.v !== 2) state = seed();
    clockOffset = state.clockOffset || 0;
    return state;
  }
  function save() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch { /* storage may be blocked */ } }
  function commit() { save(); listeners.forEach(fn => fn(state)); }
  const subscribe = fn => listeners.add(fn);
  function reset() { state = seed(); clockOffset = 0; commit(); }
  function advance(minutes) { clockOffset += minutes; state.clockOffset = clockOffset; const done = catchUp(); commit(); return done; }

  const cat = id => state.cats.find(c => c.id === id) || state.cats[0];

  const shiftMinutes = s => s.start ? span(s.start, s.end, s.breakMin).minutes : Math.round((s.hours || 0) * 60);
  const timeLabel = s => s.start ? `${s.start}–${s.end}` : `${hours(shiftMinutes(s))} שעות`;

  /* ---------- live timer (persists like the current app's active shift) ---------- */
  function startTimer(catId) { state.timer = { date: today(), start: fromMin(nowMin()), cat: catId || state.settings.defaultCat, at: now().getTime() }; commit(); }
  function timerElapsed() { return state.timer ? Math.max(0, Math.floor((now().getTime() - state.timer.at) / 1000)) : 0; }
  function stopTimer() {
    const t = state.timer; state.timer = null; commit();
    const d = newDraft({ start: t.start, end: fromMin(nowMin()), date: t.date }); setDraftCat(d, t.cat); d.start = t.start; return d;
  }
  function cancelTimer() { const t = state.timer; state.timer = null; commit(); return t; }
  function restoreTimer(t) { state.timer = t; commit(); }

  /* ---------- planned shifts ---------- */
  const occKey = (ruleId, date) => `${ruleId}@${date}`;
  function occurrences() {
    const out = [];
    const confirmed = new Set(state.shifts.filter(s => s.ruleKey).map(s => s.ruleKey));
    for (const r of state.rules) {
      for (let d = r.from; d <= r.until; d = addDays(d, 1)) {
        if (!r.days.includes(weekday(d))) continue;
        const key = occKey(r.id, d);
        if (confirmed.has(key)) continue;
        const ex = state.exceptions[key] || {};
        if (ex.cancelled) continue;
        const o = { key, ruleId: r.id, cat: r.cat, date: d, start: ex.start || r.start, end: ex.end || r.end,
          rate: 'rate' in ex ? ex.rate : r.rate, currency: r.currency, bonus: ex.bonus || 0, edited: !!ex.start || 'rate' in ex };
        o.status = endDateTime(o.date, o.start, o.end) <= now() ? 'awaiting' : 'planned';
        out.push(o);
      }
    }
    return out.sort((a, b) => startDateTime(a.date, a.start) - startDateTime(b.date, b.start));
  }
  function shiftFromOcc(o, auto) {
    const sp = span(o.start, o.end);
    return { id: uid(), cat: o.cat, date: o.date, start: o.start, end: o.end, breakMin: 0, rate: o.rate, currency: o.currency,
      bonus: o.bonus || 0, total: o.rate == null ? null : round2(sp.minutes / 60 * o.rate + (o.bonus || 0)),
      paid: false, notes: '', group: null, auto, ruleKey: o.key };
  }
  /* Safe catch-up: idempotent because each occurrence key can be confirmed once. */
  function catchUp() {
    if (!state.settings.autoConfirm) return [];
    const done = [];
    for (const o of occurrences()) if (o.status === 'awaiting' && !state.exceptions[o.key]?.hold) { const s = shiftFromOcc(o, true); state.shifts.push(s); done.push(s.id); }
    if (done.length) state.lastAuto = done;
    return done;
  }
  function undoAuto(ids) {
    // Undo turns them back into planned/awaiting occurrences; nothing else changes.
    for (const s of state.shifts) if (ids.includes(s.id) && s.ruleKey) state.exceptions[s.ruleKey] = { ...(state.exceptions[s.ruleKey] || {}), hold: true };
    state.shifts = state.shifts.filter(s => !ids.includes(s.id));
    commit();
  }
  function confirmOcc(key) { const o = occurrences().find(x => x.key === key); if (o) { state.shifts.push(shiftFromOcc(o, false)); commit(); } }
  function cancelOcc(key) { state.exceptions[key] = { ...(state.exceptions[key] || {}), cancelled: true }; commit(); }
  function restoreOcc(key) { const ex = state.exceptions[key]; if (ex) delete ex.cancelled; commit(); }
  function editOcc(key, patch, scope) {
    const [ruleId, date] = key.split('@');
    const r = state.rules.find(x => x.id === ruleId);
    if (scope === 'one') { state.exceptions[key] = { ...(state.exceptions[key] || {}), ...patch }; }
    else {
      // This and following: end the old series the day before, start a new one.
      const nr = { ...r, id: 'r' + uid(), from: date, ...patch };
      r.until = addDays(date, -1);
      state.rules.push(nr);
    }
    commit();
  }
  function futureCount(ruleId) { return occurrences().filter(o => o.ruleId === ruleId && o.status === 'planned').length; }
  function applyRateToFuture(ruleId, rate) {
    const r = state.rules.find(x => x.id === ruleId); r.rate = rate;
    for (const o of occurrences()) if (o.ruleId === ruleId && o.status === 'planned' && state.exceptions[o.key]) delete state.exceptions[o.key].rate;
    commit();
  }
  function previewRule(rule) {
    const dates = [];
    for (let d = rule.from; d <= rule.until; d = addDays(d, 1)) if (rule.days.includes(weekday(d))) dates.push(d);
    const occ = occurrences();
    const clashes = dates.filter(d => occ.some(o => o.date === d && overlaps(o, rule)) || state.shifts.some(s => s.date === d && s.start && overlaps(s, rule)));
    return { dates, clashes };
  }
  function overlaps(a, b) {
    const as = toMin(a.start), bs = toMin(b.start);
    let ae = toMin(a.end), be = toMin(b.end); if (ae <= as) ae += 1440; if (be <= bs) be += 1440;
    return as < be && bs < ae;
  }

  /* ---------- drafts (add / edit shift) ---------- */
  function suggestStart(catId, date) {
    const same = state.shifts.filter(s => s.cat === catId && weekday(s.date) === weekday(date));
    const mode = list => { const m = {}; list.forEach(s => { if (s.start) m[s.start] = (m[s.start] || 0) + 1; }); return Object.entries(m).sort((a, b) => b[1] - a[1]); };
    const strong = mode(same);
    const chips = mode(state.shifts.filter(s => s.cat === catId)).slice(0, 3).map(x => x[0]);
    const fallback = chips.length ? chips : mode(state.shifts).slice(0, 3).map(x => x[0]);
    return { start: strong.length && strong[0][1] >= 3 ? strong[0][0] : null, chips: fallback };
  }
  function newDraft(over = {}) {
    const c = cat(state.settings.defaultCat);
    const d = { id: null, mode: 'clock', hours: '', cat: c.id, date: today(), start: null, end: roundedEnd(), breakMin: 0, rate: c.rate, rateUnknown: c.rate == null,
      currency: c.currency, bonus: 0, bonusTarget: 'me', notes: '', group: null, repeat: null, ...over };
    if (!over.start) d.start = suggestStart(d.cat, d.date).start;
    return d;
  }
  function draftFromShift(s) {
    return { id: s.id, mode: !s.start ? 'hours' : s.group ? 'group' : 'clock', hours: s.hours ?? '', cat: s.cat, date: s.date, start: s.start, end: s.end || roundedEnd(), breakMin: s.breakMin, rate: s.rate, rateUnknown: s.rate == null,
      currency: s.currency, bonus: s.bonus, bonusTarget: 'me', notes: s.notes, group: s.group ? JSON.parse(JSON.stringify(s.group)) : null, repeat: null, auto: s.auto };
  }
  function setDraftCat(d, id) {
    const c = cat(id); d.cat = id; d.currency = c.currency;
    if (!d.id) { d.rate = c.rate; d.rateUnknown = c.rate == null; const s = suggestStart(id, d.date); if (s.start) d.start = s.start; }
  }
  function setMode(d, m) {
    d.mode = m;
    if (m === 'group' && !d.group) d.group = { workers: [] };
    if (m !== 'group') d.group = null;
  }
  function evaluate(d) {
    const errors = [], warnings = [];
    let sp;
    if (d.mode === 'hours') {
      const h = Number(d.hours);
      if (!(h > 0)) errors.push('הזן מספר שעות');
      else if (h > 24) errors.push('יותר מ-24 שעות ביום');
      sp = h > 0 ? { minutes: Math.round(h * 60), overnight: false, invalid: false } : null;
    } else {
      if (!d.start) errors.push('בחר שעת התחלה');
      sp = d.start ? span(d.start, d.end, d.breakMin) : null;
    }
    if (sp && sp.invalid) errors.push(sp.reason);
    if (sp && sp.long) warnings.push('משמרת של יותר מ-16 שעות. לוודא?');
    if (sp && sp.overnight && toMin(d.end) > toMin(d.start) - 60 && sp.minutes > 20 * 60) warnings.push('נראה כמו טעות בשעות');
    const rate = d.rateUnknown ? null : Number(d.rate);
    if (!d.rateUnknown && !(rate >= 0)) errors.push('הזן תעריף, או סמן "תעריף חסר"');
    const myBonus = d.group ? (d.bonusTarget === 'others' ? 0 : Number(d.bonus) || 0) : Number(d.bonus) || 0;
    const base = sp && !sp.invalid && rate != null ? sp.minutes / 60 * rate : null;
    return { errors, warnings, span: sp, base, bonus: myBonus, total: base == null ? null : round2(base + myBonus) };
  }
  function saveDraft(d) {
    const ev = evaluate(d);
    if (ev.errors.length) return { ok: false, errors: ev.errors };
    if (d.repeat) {
      const r = { id: 'r' + uid(), cat: d.cat, days: d.repeat.days, start: d.start, end: d.end, rate: d.rateUnknown ? null : Number(d.rate),
        currency: d.currency, from: d.date, until: d.repeat.until };
      state.rules.push(r); const done = catchUp(); commit();
      return { ok: true, rule: r, auto: done };
    }
    const hoursOnly = d.mode === 'hours';
    const rec = { cat: d.cat, date: d.date, start: hoursOnly ? null : d.start, end: hoursOnly ? null : d.end, hours: hoursOnly ? Number(d.hours) : undefined, breakMin: Number(d.breakMin) || 0, rate: d.rateUnknown ? null : Number(d.rate),
      currency: d.currency, bonus: ev.bonus, total: ev.total, notes: d.notes, group: d.group };
    if (d.id) { const s = state.shifts.find(x => x.id === d.id); Object.assign(s, rec, { auto: false }); }
    else state.shifts.push({ id: uid(), paid: false, auto: false, ruleKey: null, ...rec });
    commit(); return { ok: true };
  }
  function deleteShift(id) { const i = state.shifts.findIndex(s => s.id === id); const [s] = state.shifts.splice(i, 1); commit(); return s; }
  function restoreShift(s) { state.shifts.push(s); commit(); }

  /* Bulk rate completion: writes only to shifts whose rate is missing. */
  function bulkRatePreview(ids, rate) {
    const list = state.shifts.filter(s => ids.includes(s.id));
    const target = list.filter(s => s.rate == null);
    return { target: target.map(s => ({ ...s, newTotal: round2(shiftMinutes(s) / 60 * rate + (s.bonus || 0)) })), skipped: list.length - target.length };
  }
  function bulkRateApply(ids, rate) {
    const done = [];
    for (const s of state.shifts) if (ids.includes(s.id) && s.rate == null) {
      s.rate = rate; s.total = round2(shiftMinutes(s) / 60 * rate + (s.bonus || 0)); done.push(s.id);
    }
    commit(); return done;
  }
  function setPaid(ids, paid) { state.shifts.forEach(s => { if (ids.includes(s.id)) s.paid = paid; }); commit(); }

  /* ---------- summaries ---------- */
  function monthStats(k) {
    // k is a month (YYYY-MM) or a whole year (YYYY).
    const list = state.shifts.filter(s => s.date.startsWith(k));
    const byCur = {};
    let minutes = 0, pending = 0;
    for (const s of list) {
      const m = shiftMinutes(s); minutes += m;
      if (s.total == null) { pending++; continue; }
      const c = byCur[s.currency] ||= { total: 0, paid: 0, unpaid: 0, minutes: 0 };
      c.total += s.total; c.minutes += m; s.paid ? c.paid += s.total : c.unpaid += s.total;
    }
    // Compare like with like: for the running month, only the same days of last month.
    const cutoff = k === ym(today()) ? Number(today().slice(8)) : 31;
    const prev = k.length !== 7 ? 0 : state.shifts.filter(s => ym(s.date) === prevMonth(k) && Number(s.date.slice(8)) <= cutoff).reduce((a, s) => a + shiftMinutes(s), 0);
    const days = Array(7).fill(0); list.forEach(s => days[weekday(s.date)] += shiftMinutes(s));
    const cats = {}; list.forEach(s => cats[s.cat] = (cats[s.cat] || 0) + shiftMinutes(s));
    const planned = occurrences().filter(o => o.date.startsWith(k) && o.status === 'planned');
    const plannedMin = planned.reduce((a, o) => a + span(o.start, o.end).minutes, 0);
    return { k, list, count: list.length, minutes, byCur, pending, prevMinutes: prev, days, cats, planned: planned.length, plannedMin };
  }
  function insights(st) {
    const out = [];
    const unpaid = Object.entries(st.byCur).filter(([, c]) => c.unpaid > 0);
    if (unpaid.length) out.push({ kind: 'unpaid', text: `${unpaid.map(([cur, c]) => money(c.unpaid, cur, true)).join(' · ')} עדיין לא סומנו כשולמו` });
    if (st.pending) out.push({ kind: 'pending', text: pl(st.pending, 'משמרת אחת ממתינה לתעריף', '# משמרות ממתינות לתעריף') });
    if (st.prevMinutes > 0 && st.minutes > 0) {
      const ch = Math.round((st.minutes - st.prevMinutes) / st.prevMinutes * 100);
      if (Math.abs(ch) >= 5) out.push({ kind: 'trend', text: `${Math.abs(ch)}% ${ch > 0 ? 'יותר' : 'פחות'} שעות מ${MONTH[parseYmd(prevMonth(st.k) + '-01').getMonth()]}` });
    }
    const top = st.days.indexOf(Math.max(...st.days));
    if (st.minutes > 0) out.push({ kind: 'day', text: `יום ${DAY[top]} הוא היום העמוס שלך` });
    return out.slice(0, 3);
  }
  function wrapped(k) {
    const st = monthStats(k);
    const longest = st.list.reduce((a, s) => (!a || shiftMinutes(s) > shiftMinutes(a) ? s : a), null);
    const topCat = Object.entries(st.cats).sort((a, b) => b[1] - a[1])[0];
    const nights = st.list.filter(s => s.start && (span(s.start, s.end).overnight || toMin(s.end) >= 23 * 60)).length;
    const bestDay = st.days.indexOf(Math.max(...st.days));
    return { st, longest, topCat: topCat ? { name: cat(topCat[0]).name, minutes: topCat[1] } : null, nights, bestDay: DAY[bestDay] };
  }

  /* Text for WhatsApp / copy, like the current app's share. */
  function shareText(list) {
    // Isolate times and amounts so they never flip inside Hebrew text (WhatsApp, notes, clipboard).
    const L = t => '\u2066' + t + '\u2069';
    const rows = [...list].sort((a, b) => (a.date + (a.start || '')).localeCompare(b.date + (b.start || ''))).map(s =>
      `${L(shortDate(s.date))} · ${cat(s.cat).name} · ${s.start ? L(`${s.start}–${s.end}`) + ` (${L(hours(shiftMinutes(s)))} ש׳)` : `${L(hours(shiftMinutes(s)))} שעות`} · ${s.total == null ? 'תעריף חסר' : L(money(s.total, s.currency))}${s.bonus ? ` (כולל בונוס ${L(money(s.bonus, s.currency))})` : ''}`);
    const tot = {}; let mins = 0, pend = 0;
    list.forEach(s => { mins += shiftMinutes(s); if (s.total == null) pend++; else tot[s.currency] = (tot[s.currency] || 0) + s.total; });
    rows.push('', `סה״כ: ${L(hours(mins))} שעות · ${Object.entries(tot).map(([c, v]) => L(money(v, c))).join(' + ') || '—'}${pend ? ` · ${pend} בלי תעריף` : ''}`);
    return rows.join('\n');
  }

  /* Quiet hours: a notification due inside the window waits until the window ends; it is never dropped. */
  function quietDelay(atMin) {
    const st = state.settings; if (!st.quiet) return null;
    const f = toMin(st.quietFrom), t = toMin(st.quietTo);
    const inside = f === t ? false : f < t ? atMin >= f && atMin < t : atMin >= f || atMin < t;
    return inside ? st.quietTo : null;
  }

  /* Haptics: short, distinct patterns. The Android app uses native HapticFeedback for the same moments. */
  const HAPTIC = { tap: 6, tick: 4, select: 14, success: [12, 60, 22], warn: [35, 50, 35] };
  function haptic(kind = 'tap') {
    if (state?.settings?.haptics === false) return;
    try { navigator.vibrate?.(HAPTIC[kind] ?? 6); } catch { /* not available (desktop or embedded frame) */ }
  }

  /* ---------- shared UI helpers ---------- */
  function toast(msg, action, fn, ms = 5200) {
    let host = document.getElementById('core-toast');
    if (!host) { host = document.createElement('div'); host.id = 'core-toast'; host.setAttribute('role', 'status'); document.body.append(host); }
    host.innerHTML = `<span>${esc(msg)}</span>${action ? `<button type="button">${esc(action)}</button>` : ''}`;
    host.className = 'show';
    if (action) host.querySelector('button').onclick = () => { host.className = ''; fn(); };
    clearTimeout(host._t); host._t = setTimeout(() => host.className = '', ms);
  }
  function setTheme(t) {
    state.settings.theme = t; commit();
    if (t === 'system') document.documentElement.removeAttribute('data-theme'); else document.documentElement.setAttribute('data-theme', t);
  }
  function applyTheme() { const t = state.settings.theme; if (t && t !== 'system') document.documentElement.setAttribute('data-theme', t); }

  /* Prototype control panel: not part of the product design. */
  function mountPanel({ name, onBrand, onWrapped }) {
    const b = document.createElement('button');
    b.type = 'button'; b.id = 'proto-fab'; b.textContent = 'אב-טיפוס'; b.setAttribute('aria-haspopup', 'dialog');
    const p = document.createElement('div'); p.id = 'proto-panel'; p.hidden = true; p.setAttribute('role', 'dialog'); p.setAttribute('aria-label', 'הגדרות אב-טיפוס');
    const draw = () => {
      const s = state.settings;
      const seg = (key, opts) => `<div class="pp-seg">${opts.map(([v, l]) => `<button type="button" data-k="${key}" data-v="${v}" aria-pressed="${s[key] === v}">${l}</button>`).join('')}</div>`;
      p.innerHTML = `<div class="pp-head"><strong>${esc(name)}</strong><button type="button" data-close aria-label="סגירה">✕</button></div>
        <p class="pp-note">לוח בדיקה בלבד. הנתונים בדויים ונשמרים רק בדפדפן הזה.</p>
        <label class="pp-l">ערכת צבעים</label>${seg('theme', [['system', 'מערכת'], ['light', 'בהיר'], ['dark', 'כהה']])}
        <label class="pp-l">תצוגת משמרות מתוכננות</label>${seg('planningView', [['unified', 'מאוחדת'], ['separate', 'אזור נפרד']])}
        <label class="pp-l">הדמיית זמן <span class="pp-clock">${esc(dayLabel(today()))} ${fromMin(nowMin())}</span></label>
        <div class="pp-seg"><button type="button" data-adv="360">+6 שעות</button><button type="button" data-adv="1440">+יום</button><button type="button" data-adv="10080">+שבוע</button></div>
        <div class="pp-actions"><button type="button" data-brand>מיתוג ואייקון</button><button type="button" data-wrapped>Wrapped</button><button type="button" data-reset>איפוס נתונים</button></div>`;
    };
    p.addEventListener('click', e => {
      const t = e.target.closest('button'); if (!t) return;
      if (t.dataset.close !== undefined) { p.hidden = true; return; }
      if (t.dataset.k === 'theme') setTheme(t.dataset.v);
      else if (t.dataset.k) { state.settings[t.dataset.k] = t.dataset.v; commit(); }
      if (t.dataset.adv) {
        const done = advance(Number(t.dataset.adv));
        if (done.length) toast(pl(done.length, 'משמרת מתוכננת הסתיימה ונרשמה', '# משמרות מתוכננות הסתיימו ונרשמו'), 'ביטול', () => undoAuto(done));
        else toast('הזמן הוזז');
      }
      if (t.dataset.reset !== undefined) { reset(); toast('נתוני הדמו אופסו'); }
      if (t.dataset.brand !== undefined) { p.hidden = true; onBrand(); }
      if (t.dataset.wrapped !== undefined) { p.hidden = true; onWrapped(); }
      draw();
    });
    b.onclick = () => { draw(); p.hidden = !p.hidden; };
    document.body.append(b, p);
  }

  /* Long-press helper: native-feeling multi-select on touch, also works with a mouse. */
  function longPress(root, selector, onLong) {
    let timer = null, fired = false;
    root.addEventListener('pointerdown', e => {
      const el = e.target.closest(selector); if (!el) return; fired = false;
      timer = setTimeout(() => { fired = true; haptic('select'); onLong(el); }, 450);
    });
    const clear = () => clearTimeout(timer);
    ['pointerup', 'pointerleave', 'pointercancel', 'pointermove'].forEach(ev => root.addEventListener(ev, e => { if (ev !== 'pointermove' || Math.abs(e.movementY) > 4) clear(); }));
    root.addEventListener('click', e => { if (fired && e.target.closest(selector)) { e.stopPropagation(); e.preventDefault(); fired = false; } }, true);
  }

  return {
    DAY, DAY_SHORT, MONTH, pad, ymd, parseYmd, addDays, toMin, fromMin, weekday, ym, now, today, nowMin, roundedEnd, dayLabel, shortDate, monthName, prevMonth, nextMonth,
    span, endDateTime, money, pl, hours, hm, round2, esc, load, save, commit, subscribe, reset, advance, cat, get state() { return state; },
    occurrences, catchUp, undoAuto, confirmOcc, cancelOcc, restoreOcc, editOcc, futureCount, applyRateToFuture, previewRule,
    suggestStart, newDraft, draftFromShift, setDraftCat, evaluate, saveDraft, deleteShift, restoreShift, bulkRatePreview, bulkRateApply, setPaid,
    shiftMinutes, timeLabel, setMode, startTimer, stopTimer, cancelTimer, restoreTimer, timerElapsed, monthStats, insights, wrapped, toast, haptic, quietDelay, shareText, setTheme, applyTheme, mountPanel, longPress, WORKERS,
  };
})();
