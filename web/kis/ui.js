/* Shared screen helpers for the 2.0 web app: formatting (same as KisFormat), colours, sheets, messages, sharing. */
(function (root) {
'use strict';
const DAY_SHORT = ['א׳', 'ב׳', 'ג׳', 'ד׳', 'ה׳', 'ו׳', 'ש׳'];
const MONTHS = ['ינואר', 'פברואר', 'מרץ', 'אפריל', 'מאי', 'יוני', 'יולי', 'אוגוסט', 'ספטמבר', 'אוקטובר', 'נובמבר', 'דצמבר'];
// The 8 fixed workplace colours (KisTheme.Workplaces): vivid end, dark end, ink on it, dark-mode accent and its ink.
const PLACES = [
  ['אינדיגו', '#5145E8', '#1E1873', '#FFFFFF', '#8F87FF', '#120F3A'], ['ירוק', '#0E8059', '#073D2C', '#FFFFFF', '#3DDC97', '#03140E'],
  ['ורד', '#D6336C', '#5C1230', '#FFFFFF', '#FF6B9A', '#2E0A18'], ['חרדל', '#F5B82E', '#C98A10', '#1A1200', '#FFC94D', '#2A1C00'],
  ['סגול', '#7D3CF0', '#2E1466', '#FFFFFF', '#B48CFF', '#1E0B3D'], ['תכלת', '#1F72E0', '#0C2F66', '#FFFFFF', '#5AA9FF', '#06203F'],
  ['טורקיז', '#0E8A86', '#063B3A', '#FFFFFF', '#3CD3CB', '#04201F'], ['בורדו', '#B5294E', '#4A0F21', '#FFFFFF', '#FF6F8E', '#2E0812'],
].map(([name, from, to, on, accentDark, accentDarkInk]) => ({ name, from, to, on, accentDark, accentDarkInk }));

const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
/** "1,234" for whole amounts, "1,234.50" otherwise. */
function number(v) {
  const cents = Math.round(v * 100);
  return cents % 100 === 0 ? (cents / 100).toLocaleString('en-US') : v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
const money = (v, cur) => cur + number(v);
/** Money as HTML with the symbol in Rubik and the digits isolated left-to-right. */
const moneyHtml = (v, cur, cls = '') => `<span class="money ${cls}"><span class="cur">${esc(cur)}</span>${number(v)}</span>`;
function hours(h) { const r = Math.round(h * 100) / 100; return Number.isInteger(r) ? String(r) : String(r); }
const hm = m => Math.floor(m / 60) + ':' + String(m % 60).padStart(2, '0');
const startOfDay = ms => { const d = new Date(ms); d.setHours(0, 0, 0, 0); return d.getTime(); };
const shortDate = ms => { const d = new Date(ms); return d.getDate() + '.' + (d.getMonth() + 1); };
function dayLabel(ms) {
  const diff = Math.round((startOfDay(Date.now()) - startOfDay(ms)) / 86400000), d = new Date(ms);
  return diff === 0 ? 'היום' : diff === 1 ? 'אתמול' : diff === -1 ? 'מחר' : `יום ${DAY_SHORT[d.getDay()]} ${d.getDate()}.${d.getMonth() + 1}`;
}
function dayLabelLong(ms) { const d = new Date(ms); return dayLabel(ms) + (d.getFullYear() !== new Date().getFullYear() ? '.' + d.getFullYear() : ''); }
const monthKey = ms => { const d = new Date(ms); return d.getFullYear() * 12 + d.getMonth(); };
const monthName = k => MONTHS[k % 12] + ' ' + Math.floor(k / 12);
const isDark = () => document.documentElement.dataset.theme === 'dark' || (document.documentElement.dataset.theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);

function placeOf(name, store) {
  const pick = store.local['color:' + name];
  if (Number.isInteger(pick) && PLACES[pick]) return PLACES[pick];
  const i = store.data.categories.findIndex(c => c.name === name);
  if (i >= 0) return PLACES[i % PLACES.length];
  let h = 0; for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) | 0; return PLACES[Math.abs(h) % PLACES.length];
}
const accent = p => isDark() ? p.accentDark : p.from;
const accentInk = p => isDark() ? p.accentDarkInk : p.on;
function avatar(name, store, planned = false, size = 44) {
  const p = placeOf(name, store);
  return `<span class="avatar${planned ? ' planned' : ''}" style="--av:${accent(p)};background:linear-gradient(90deg,${p.from},${p.to});color:${p.on};width:${size}px;height:${size}px">${esc((name || '?').trim().charAt(0))}</span>`;
}
// A job paid by the job has no hours to show unless some were typed.
const timeLabel = e => e.isTimeRange && e.startTime && e.endTime ? `⁦${e.startTime}–${e.endTime}⁩` : e.kind === 'job' && !(e.hours > 0) ? 'קבלנות' : `${hours(e.hours)} שעות`;
function subtitle(e) {
  const parts = [];
  if (e.kind === 'job') { parts.push('קבלנות: ' + (e.notes.trim() || e.category)); if (e.hours > 0) parts.push(hours(e.hours) + ' ש׳'); }
  else {
    parts.push(timeLabel(e));
    if (e.isTimeRange && e.startTime && e.endTime) { if (e.endTime <= e.startTime) parts.push('למחרת'); parts.push(hours(e.hours) + ' ש׳'); }
    parts.push(e.category);
  }
  if (e.bonus > 0) parts.push('בונוס ' + money(e.bonus, e.currency));
  if (e.passThrough > 0) parts.push('החזר ' + money(e.passThrough, e.currency));
  if (e.isGroupShift) { const n = root.KisPay.workers(e).length; if (n) parts.push((n + 1) + ' אנשים'); }
  return parts.join(' · ');
}
const upcoming = (e, now = Date.now()) => startOfDay(e.date) > startOfDay(now);

/** One shift card (the same on Home and History). */
function shiftCard(e, store, { selected = false, tag = 'shift' } = {}) {
  const planned = upcoming(e), pay = root.KisPay.pay(e);
  const tags = [planned ? '<span class="tag" data-testid="planned-tag">מתוכנן</span>' : '', e.autoConfirmed ? '<span class="tag">אוטו׳</span>' : '', !e.isPaid && !planned ? '<span class="tag">לא שולם</span>' : ''].join('');
  const amount = pay != null ? `<span class="pay${planned ? ' muted' : ''}">${moneyHtml(pay, e.currency)}</span>` : `<span class="tag warn">${e.kind === 'job' ? 'סכום חסר' : 'תעריף חסר'}</span>`;
  return `<button class="shift${selected ? ' selected' : ''}" data-id="${esc(e.id)}" data-testid="${tag}-${esc(e.id)}" aria-pressed="${selected}">
    ${avatar(e.category, store, planned)}<span class="meta"><span class="top"><b class="label">${esc(dayLabel(e.date))}</b>${tags}</span><span class="sub">${esc(subtitle(e))}</span></span>${amount}</button>`;
}

// ---- messages ----
let toastTimer = null;
function toast(text, action, onAction) {
  const host = document.getElementById('toast');
  host.innerHTML = `<div><span>${esc(text)}</span>${action ? `<button type="button" data-testid="toast-action">${esc(action)}</button>` : ''}</div>`;
  host.hidden = false;
  if (action) host.querySelector('button').onclick = () => { host.hidden = true; onAction?.(); };
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { host.hidden = true; }, action ? 6000 : 2600);
}

// ---- sheets ----
const sheets = [];
/** A bottom sheet. render(el) fills it; Escape and the scrim close it. Returns close(). */
function sheet(testid, render, { onClose } = {}) {
  const scrim = document.createElement('div');
  scrim.className = 'scrim';
  scrim.innerHTML = `<div class="sheet" role="dialog" aria-modal="true" data-testid="${testid}"></div>`;
  const el = scrim.firstChild;
  const opener = document.activeElement;
  const close = () => { if (!scrim.isConnected) return; scrim.remove(); sheets.splice(sheets.indexOf(close), 1); onClose?.(); if (opener?.isConnected) opener.focus({ preventScroll: true }); };
  scrim.addEventListener('click', e => { if (e.target === scrim) close(); });
  document.body.append(scrim); sheets.push(close);
  render(el, close);
  const h = el.querySelector('h2'); if (h) { h.id ||= 'sheet-title-' + sheets.length; el.setAttribute('aria-labelledby', h.id); }
  el.querySelector('button, input, select, textarea')?.focus({ preventScroll: true });
  return close;
}
const closeTopSheet = () => { const c = sheets[sheets.length - 1]; if (c) { c(); return true; } return false; };
const anySheet = () => sheets.length > 0;

function confirmSheet(title, text, okLabel, onOk, danger = true) {
  sheet('confirm-sheet', (el, close) => {
    el.innerHTML = `<h2>${esc(title)}</h2><p class="caption">${esc(text)}</p><div class="row"><button class="btn ghost grow" data-a="no">ביטול</button><button class="btn ${danger ? 'danger' : 'primary'} grow" data-a="ok" data-testid="confirm-ok">${esc(okLabel)}</button></div>`;
    el.querySelector('[data-a=no]').onclick = close;
    el.querySelector('[data-a=ok]').onclick = () => { close(); onOk(); };
  });
}

// ---- sharing (docs/v2-sharing.md): share sheet, else clipboard, else selectable text ----
async function copy(text) {
  try { await navigator.clipboard.writeText(text); return true; } catch { return false; }
}
async function shareOrCopy(text, title = 'שיתוף') {
  if (navigator.share) { try { await navigator.share({ text }); return; } catch (e) { if (e?.name === 'AbortError') return; } }
  if (await copy(text)) { toast('הועתק ✓ · מוכן להדבקה'); return; }
  showText(text, title);
}
function showText(text, title = 'טקסט לשיתוף') {
  sheet('share-text-sheet', (el, close) => {
    el.innerHTML = `<h2>${esc(title)}</h2><p class="caption">אפשר לסמן ולהעתיק את הטקסט.</p><textarea class="input" rows="8" readonly>${esc(text)}</textarea><button class="btn primary">סגירה</button>`;
    el.querySelector('button').onclick = close; el.querySelector('textarea').select();
  });
}
const haptic = (kind = 'tap') => { try { if (root.Kis?.store?.local.haptics !== false) navigator.vibrate?.({ tap: 8, success: [12, 60, 22], warn: [35, 50, 35], tick: 4 }[kind] || 8); } catch {} };
const ICON = {
  search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>',
  filter: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 6h16M7 12h10M10 18h4"/></svg>',
  copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h8"/></svg>',
  close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>',
  prev: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m9 6 6 6-6 6"/></svg>',
  info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>',
  bell: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 20a2 2 0 0 0 4 0"/></svg>',
  person: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="8" r="4"/><path d="M4 20a8 8 0 0 1 16 0"/></svg>',
  share: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 15V3M7 8l5-5 5 5"/><path d="M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6"/></svg>',
  next: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m15 6-6 6 6 6"/></svg>',
};
/** ⓘ: the explanation of a title, opened only when asked for. Screens say what things are; how-to lives here. */
const infoDot = (title, text, testid = '') => `<button type="button" class="icon-btn info-dot" data-info-title="${esc(title)}" data-info="${esc(text)}" aria-label="הסבר: ${esc(title)}"${testid ? ` data-testid="${testid}"` : ''}>${ICON.info}</button>`;
document.addEventListener('click', e => {
  const b = e.target.closest?.('[data-info]');
  if (!b) return;
  e.preventDefault(); e.stopPropagation();
  sheet('info-sheet', (el, close) => {
    el.innerHTML = `<h2>${esc(b.dataset.infoTitle)}</h2><p style="margin:0">${esc(b.dataset.info)}</p><button class="btn primary">הבנתי</button>`;
    el.querySelector('button').onclick = close;
  });
}, true);
const logo = (size = 38) => `<span class="logo" style="width:${size}px;height:${size}px" aria-hidden="true"><svg viewBox="0 0 64 64" width="${size}" height="${size}"><g stroke-width="7" stroke-linecap="round"><path d="M19 44V30" stroke="#7C8CFF"/><path d="M32 44V20" stroke="#FFC54D"/><path d="M45 44V25" stroke="#34C79A"/><path d="M19 44h26" stroke="#EDF0F5"/></g></svg></span>`;

root.KisUi = { DAY_SHORT, MONTHS, PLACES, esc, number, money, moneyHtml, hours, hm, startOfDay, shortDate, dayLabel, dayLabelLong, monthKey, monthName,
  isDark, placeOf, accent, accentInk, avatar, timeLabel, subtitle, upcoming, shiftCard, toast, sheet, closeTopSheet, anySheet, confirmSheet, copy, shareOrCopy,
  showText, haptic, ICON, logo, infoDot };
})(globalThis);
