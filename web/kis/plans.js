/*
 * Weekly plans on the web (stage C rules, KisPlan): computed occurrences, never stored as shifts. Recording one writes a
 * normal shift with a unique seriesKey, so the same day is never recorded twice (also across tabs: the key is checked
 * against what is stored right before writing). Reminders show only while the site is open.
 */
(function (root) {
'use strict';
const U = () => root.KisUi;
const status = o => String(o.status).toUpperCase();
const isAwaiting = o => status(o) === 'AWAITING';
const LOOK_BACK = 62, REVIEW_BACK = 400;

function occurrences(store, now = Date.now(), back = LOOK_BACK) {
  const { series, exceptions } = store.plans;
  if (!series.length) return [];
  const today = root.KisPlan.dateOf(now), d = root.KisRules.daysFromCivil(today);
  const confirmed = new Set(store.data.entries.map(e => e.seriesKey).filter(Boolean));
  return root.KisPlan.occurrences(series, exceptions, confirmed, root.KisRules.civilFromDays(d - back), root.KisRules.civilFromDays(d + 28), now);
}
/** Ended plan days that are neither recorded nor skipped, back to REVIEW_BACK days (older ones are no longer lost, CW-05). */
function dueDays(store, now = Date.now()) {
  const have = new Set(store.data.entries.map(e => e.seriesKey).filter(Boolean));
  return root.KisPlan.toConfirm(occurrences(store, now, REVIEW_BACK)).filter(o => !have.has(o.key)).sort((a, b) => (a.date < b.date ? -1 : 1));
}
/** Days that wait for the person's review (see KisPlan.splitCatchUp); shown on Home and in the review sheet. */
function awaitingReview(store, now = Date.now()) {
  if (!store.plans.series.length) return [];
  const due = dueDays(store, now);
  return store.local.autoConfirm === false ? due : root.KisPlan.splitCatchUp(due, now).review;
}

/** Records the few days that just ended (auto-record on); more than that waits for review. Returns the new shift ids. */
function catchUp(store, now = Date.now()) {
  if (store.local.autoConfirm === false || !store.plans.series.length) return [];
  store.load(); // another tab may have recorded meanwhile
  const { auto } = root.KisPlan.splitCatchUp(dueDays(store, now), now);
  return auto.length ? store.addEntries(auto.map(o => root.KisPlan.toEntry(o, true))) : [];
}

/** Review of plan days that were not recorded: the person records the ones they worked and skips the rest. */
function reviewSheet(app, after) {
  const { store } = app, { esc, money, dayLabel } = U();
  U().sheet('plan-review-sheet', (el, close) => {
    const list = awaitingReview(store), picked = new Set(list.map(o => o.key));
    const draw = () => {
      const chosen = list.filter(o => picked.has(o.key)), totals = {};
      for (const o of chosen) if (o.rate != null) totals[o.currency] = (totals[o.currency] || 0) + root.KisPay.money((root.KisPlan.endMillis(o.date, o.start, o.end) - root.KisPlan.startMillis(o.date, o.start)) / 3600000, o.rate);
      el.innerHTML = `<h2>משמרות קבועות שעברו</h2>
        <p class="caption" style="margin:0">לא נרשמו לבד, כי הן רבות או ישנות. מה שמסומן יירשם. מה שלא, אפשר לדלג עליו.</p>
        <div class="list" style="display:grid;gap:2px;max-height:50vh;overflow:auto" data-testid="plan-review-list">${list.map(o => `<label class="row" style="align-items:center;min-height:48px;gap:10px">
          <input type="checkbox" data-k="${esc(o.key)}" ${picked.has(o.key) ? 'checked' : ''} style="width:22px;height:22px">
          <span class="grow"><b class="label">${esc(dayLabel(root.KisPlan.startMillis(o.date, o.start)))}</b> <span class="caption">⁦${o.start}–${o.end}⁩ · ${esc(o.category)}</span></span>
          <span class="caption">${o.rate == null ? 'תעריף חסר' : esc(money(root.KisPay.money((root.KisPlan.endMillis(o.date, o.start, o.end) - root.KisPlan.startMillis(o.date, o.start)) / 3600000, o.rate), o.currency))}</span></label>`).join('')}</div>
        <button class="btn primary" data-a="record" data-testid="plan-review-record" ${chosen.length ? '' : 'disabled'}>רישום ${chosen.length} משמרות${Object.keys(totals).length ? ' · ' + Object.entries(totals).map(([c, v]) => esc(money(v, c))).join(' · ') : ''}</button>
        <button class="btn ghost" data-a="skip" data-testid="plan-review-skip" ${list.length - chosen.length ? '' : 'disabled'}>לא עבדתי ב־${list.length - chosen.length} האחרות</button>`;
      el.querySelectorAll('[data-k]').forEach(c => c.onchange = () => { if (c.checked) picked.add(c.dataset.k); else picked.delete(c.dataset.k); draw(); });
      el.querySelector('[data-a=record]').onclick = () => {
        // Recorded and the rest kept for later, as one commit; the key check keeps a day from being recorded twice.
        const ids = store.transact(n => { const have = new Set(n.data.entries.map(e => e.seriesKey).filter(Boolean));
          const rows = list.filter(o => picked.has(o.key) && !have.has(o.key)).map(o => ({ ...root.KisPlan.toEntry(o, false), id: store.uid() }));
          n.data.entries.push(...rows); return rows.map(r => r.id); });
        close(); U().toast(`נרשמו ${ids.length} משמרות`); after?.();
      };
      el.querySelector('[data-a=skip]').onclick = () => {
        store.transact(n => { for (const o of list) if (!picked.has(o.key)) { const x = n.plans.exceptions.find(e => e.seriesId === o.seriesId && e.date === o.date);
          if (x) x.cancelled = true; else n.plans.exceptions.push(store.exception(o.seriesId, o.date, { cancelled: true })); } });
        list.splice(0, list.length, ...list.filter(o => picked.has(o.key))); draw(); if (!list.length) { close(); after?.(); }
      };
    };
    draw();
  });
}

function exceptionFor(store, o) { return store.plans.exceptions.find(x => x.seriesId === o.seriesId && x.date === o.date); }
function setException(store, o, patch) {
  store.updatePlans(p => { const x = p.exceptions.find(e => e.seriesId === o.seriesId && e.date === o.date);
    if (x) Object.assign(x, patch); else p.exceptions.push(store.exception(o.seriesId, o.date, patch)); });
}

function card(o, store) {
  const { esc, avatar, dayLabel, moneyHtml } = U();
  const waiting = isAwaiting(o), mins = root.KisRules.span(o.start, o.end).minutes;
  const overnight = root.KisRules.toMinutes(o.end) <= root.KisRules.toMinutes(o.start);
  const amount = o.rate != null ? `<span class="pay muted">${moneyHtml(root.KisPay.money(mins / 60, o.rate), o.currency)}</span>` : '<span class="pay muted">?</span>';
  return `<div class="shift" style="display:grid;gap:10px" data-plan="${esc(o.key)}" data-testid="planned-${esc(o.key)}">
    <button class="plain" style="display:flex;align-items:center;gap:12px;width:100%" data-plan-open aria-expanded="false">${avatar(o.category, store, true)}
      <span class="meta"><span class="top"><b class="label">${esc(dayLabel(root.KisPlan.startMillis(o.date, o.start)))}</b><span class="tag${waiting ? ' warn' : ''}">${waiting ? 'ממתין לאישור' : 'מתוכנן'}</span></span>
      <span class="sub">⁦${esc(o.start)}–${esc(o.end)}⁩${overnight ? ' · למחרת' : ''} · ${esc(o.category)}</span></span>${amount}</button>
    <div data-plan-actions hidden style="display:grid;gap:6px"><div class="row"><button class="btn small primary grow" data-pa="worked">עבדתי</button><button class="btn small ghost grow" data-pa="skip">לא עבדתי</button></div>
      <div class="row"><button class="btn small ghost grow" data-pa="hours">שינוי שעות</button><button class="btn small ghost grow" data-pa="share">שיתוף</button></div><button class="btn small ghost" data-pa="copy">העתקה</button></div></div>`;
}

function bind(el, app) {
  const { store } = app;
  el.querySelectorAll('[data-plan]').forEach(box => {
    const o = occurrences(store).find(x => x.key === box.dataset.plan); if (!o) return;
    const open = box.querySelector('[data-plan-open]'), acts = box.querySelector('[data-plan-actions]');
    open.onclick = () => { acts.hidden = !acts.hidden; open.setAttribute('aria-expanded', String(!acts.hidden)); };
    box.querySelectorAll('[data-pa]').forEach(b => b.onclick = () => act(app, o, b.dataset.pa));
  });
}

function act(app, o, what) {
  const { store } = app, U_ = U();
  if (what === 'worked') {
    if (store.data.entries.some(e => e.seriesKey === o.key)) return;
    const [id] = store.addEntries([root.KisPlan.toEntry(o, false)]);
    U_.haptic('success');
    U_.toast('נרשמה ✓', 'ביטול', () => { const gone = store.data.entries.find(e => e.id === id); store.update(d => { d.entries = d.entries.filter(e => e.id !== id); }); if (gone) setException(store, o, { hold: true }); });
  } else if (what === 'skip') {
    setException(store, o, { cancelled: true });
    U_.toast('סומן שלא עבדת', 'ביטול', () => setException(store, o, { cancelled: false }));
  } else if (what === 'share') {
    U_.shareOrCopy(root.KisShare.planned(o), 'שיתוף משמרת מתוכננת');
  } else if (what === 'copy') {
    const text=root.KisShare.planned(o,true); U_.copy(text).then(ok=>ok?U_.toast('הועתק ✓'):U_.showText(text));
  } else if (what === 'hours') hoursSheet(app, o);
}

function hoursSheet(app, o) {
  const { store } = app;
  U().sheet('plan-hours-sheet', (el, close) => {
    el.innerHTML = `<h2>שעות ל${U().esc(U().dayLabelLong(root.KisPlan.startMillis(o.date, '12:00')))}</h2>
      <div class="row"><label class="field grow">התחלה<input type="time" class="input" value="${o.start}" data-f="s"></label><label class="field grow">סיום<input type="time" class="input" value="${o.end}" data-f="e"></label></div>
      <div class="row"><button class="btn primary grow" data-a="once">רק הפעם</button><button class="btn ghost grow" data-a="on">מעכשיו והלאה</button></div>`;
    const v = () => [el.querySelector('[data-f=s]').value, el.querySelector('[data-f=e]').value];
    el.querySelector('[data-a=once]').onclick = () => { const [s, e] = v(); if (!root.KisRules.isTime(s) || !root.KisRules.isTime(e) || s === e) return; setException(store, o, { startTime: s, endTime: e }); close(); U().toast('השעות עודכנו ליום הזה ✓'); };
    el.querySelector('[data-a=on]').onclick = () => {
      const [s, e] = v(); if (!root.KisRules.isTime(s) || !root.KisRules.isTime(e) || s === e) return;
      const series = store.plans.series.find(x => x.id === o.seriesId); if (!series) return;
      const recorded = new Set(store.data.entries.map(x => x.seriesKey).filter(Boolean));
      const split = root.KisPlan.split(series, store.plans.exceptions, o.date, store.uid(), { start: s, end: e, now: Date.now(), recorded });
      store.updatePlans(p => {
        p.exceptions = p.exceptions.filter(x => !split.dropped.some(d => d.seriesId === x.seriesId && d.date === x.date));
        p.series = p.series.filter(x => x.id !== split.oldId);
        if (split.old) p.series.push(split.old);
        p.series.push(split.new); p.exceptions.push(...split.moved);
      });
      close(); U().toast('השעות עודכנו מעכשיו והלאה ✓');
    };
  });
}

/** Creates a plan from the editor. Days that clash with a saved timed shift or another plan are not created. */
function createSeries(store, { category, days, from, until, start, end, rate, currency }) {
  const preview = previewFor(store, days, from, until, start, end);
  const s = { id: store.uid(), category, daysMask: root.KisPlan.mask(days), startTime: start, endTime: end, hourlyRate: rate, currency, fromDate: from, untilDate: until, createdAt: Date.now() };
  store.updatePlans(p => { p.series.push(s); for (const d of preview.clashes) p.exceptions.push(store.exception(s.id, d, { cancelled: true })); });
  const created = preview.dates.length - preview.clashes.length;
  return { created, recorded: catchUp(store) };
}
function previewFor(store, days, from, until, start, end) {
  const others = from > until ? [] : root.KisPlan.occurrences(store.plans.series, store.plans.exceptions,
    new Set(store.data.entries.map(e => e.seriesKey).filter(Boolean)), from, until, Date.now());
  return root.KisPlan.preview(days, from, until, start, end, store.data.entries, others);
}

function seriesSheet(app) {
  const { store } = app, { esc, avatar, money, shortDate } = U();
  U().sheet('series-sheet', (el, close) => {
    const draw = () => {
      const occ = occurrences(store);
      el.innerHTML = `<h2>משמרות קבועות</h2>
        <div class="row" style="align-items:center"><div class="grow title-row"><div class="label">רישום אוטומטי</div>${U().infoDot('רישום אוטומטי', 'כשהזמן נגמר המשמרת נרשמת לבד. תמיד אפשר לבטל.')}</div>
          <button class="switch" role="switch" aria-checked="${store.local.autoConfirm !== false}" data-a="auto" aria-label="רישום אוטומטי" data-testid="auto-confirm"></button></div>
        <div class="divider"></div>
        ${store.plans.series.length ? '' : '<p class="caption">אין משמרות קבועות</p>'}
        ${store.plans.series.map(s => { const left = occ.filter(o => o.seriesId === s.id && !isAwaiting(o)).length;
          return `<div class="card" style="background:var(--bg);display:grid;gap:8px"><div class="row" style="align-items:center">${avatar(s.category, store, true, 36)}<div class="grow"><b class="label">${esc(s.category)}</b>
            <div class="caption">${esc(root.KisPlan.days(s.daysMask).sort().map(d => U().DAY_SHORT[d]).join(' '))} · ⁦${s.startTime}–${s.endTime}⁩ · עד ${shortDate(root.KisPlan.startMillis(s.untilDate, '12:00'))}${left ? ' · עוד ' + left : ''}</div></div></div>
            <div class="row"><button class="chip${s.hourlyRate == null ? ' warn' : ''}" data-rate="${esc(s.id)}">${s.hourlyRate == null ? 'תעריף חסר' : esc(money(s.hourlyRate, s.currency)) + ' לשעה'}</button><button class="chip" data-end="${esc(s.id)}">סיום הסדרה</button></div></div>`; }).join('')}`;
      el.querySelector('[data-a=auto]').onclick = () => { store.setLocal('autoConfirm', store.local.autoConfirm === false); draw(); };
      el.querySelectorAll('[data-rate]').forEach(b => b.onclick = () => rateSheet(app, b.dataset.rate, draw));
      el.querySelectorAll('[data-end]').forEach(b => b.onclick = () => U().confirmSheet('לסיים את הסדרה?', 'משמרות מתוכננות מעכשיו לא יופיעו יותר. מה שכבר נרשם נשאר כמו שהוא.', 'סיום', () => {
        store.updatePlans(p => { const s = p.series.find(x => x.id === b.dataset.end); if (!s) return;
          const until = root.KisPlan.endUntil(s, Date.now());
          if (until == null) { p.series = p.series.filter(x => x !== s); p.exceptions = p.exceptions.filter(x => x.seriesId !== s.id); } else s.untilDate = until; });
        draw();
      }));
    };
    draw();
  });
}
function rateSheet(app, id, after) {
  const { store } = app, s = store.plans.series.find(x => x.id === id); if (!s) return;
  U().sheet('series-rate-sheet', (el, close) => {
    el.innerHTML = `<h2>תעריף חדש לסדרה</h2><p class="caption">יחול מהיום והלאה. מה שכבר נרשם לא משתנה.</p>
      <label class="field">לשעה (${U().esc(s.currency)})<input class="input" inputmode="decimal" value="${s.hourlyRate ?? ''}" data-f="r"></label><button class="btn primary" data-a="ok">החלה</button>`;
    el.querySelector('[data-a=ok]').onclick = () => {
      const raw = el.querySelector('[data-f=r]').value.trim(), rate = Number(raw.replace(',', '.')); if (!(rate >= 0) || raw === '') return;
      const today = root.KisPlan.dateOf(Date.now());
      const waiting = root.KisPlan.occurrences([s], store.plans.exceptions, new Set(store.data.entries.map(e => e.seriesKey).filter(Boolean)), s.fromDate, today, Date.now()).filter(isAwaiting);
      const { series: updated, changes } = root.KisPlan.applyRateFromToday(s, store.plans.exceptions, rate, today, waiting);
      store.updatePlans(p => { p.series = p.series.map(x => x.id === s.id ? updated : x);
        for (const c of changes) { const i = p.exceptions.findIndex(x => x.seriesId === c.seriesId && x.date === c.date); if (i >= 0) p.exceptions[i] = c; else p.exceptions.push(c); } });
      close(); after?.();
    };
  });
}

root.KisPlans = { occurrences, catchUp, awaitingReview, reviewSheet, dueDays, card, bind, act, createSeries, previewFor, seriesSheet, isAwaiting, exceptionFor };
})(globalThis);
