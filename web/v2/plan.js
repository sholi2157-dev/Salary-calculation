(function (root) {
'use strict';
/*
 * Weekly plans (2.0 stage C) — port of the Android domain/V2Plan.kt.
 * Planned occurrences are computed from a series and its exceptions and are never stored as
 * shifts; confirming one writes a normal shift whose seriesKey ("seriesId@YYYY-MM-DD") is unique,
 * so the same occurrence can never be recorded twice. Expected results: web-tests/fixtures/v2-plan.json.
 *
 * Series:    {id, category, daysMask, startTime, endTime, hourlyRate (number|null), currency, fromDate, untilDate, createdAt}
 * Exception: {seriesId, date, cancelled=false, hold=false, startTime=null, endTime=null, rateOverridden=false, hourlyRate=null}
 * Occurrence:{key, seriesId, category, date, start, end, rate, currency, edited, hold, status:'PLANNED'|'AWAITING'}
 * Browser load order: rules.js, pay.js, then this file.
 */
const node = typeof module !== 'undefined';
const Rules = node ? require('./rules.js') : root.KisRules;
const Pay = node ? require('./pay.js') : root.KisPay;

const PLANNED = 'PLANNED', AWAITING = 'AWAITING';
const key = (seriesId, date) => seriesId + '@' + date;
const dateOfKey = k => k.slice(k.lastIndexOf('@') + 1);
const seriesOfKey = k => k.lastIndexOf('@') < 0 ? k : k.slice(0, k.lastIndexOf('@'));
const maxStr = (a, b) => a >= b ? a : b;
const minStr = (a, b) => a <= b ? a : b;
const cmp = (a, b) => a < b ? -1 : a > b ? 1 : 0;

/** Weekdays (0 = Sunday) of a mask, ascending. */
function days(mask) { const out = []; for (let d = 0; d <= 6; d++) if (mask & (1 << d)) out.push(d); return out; }
function mask(list) { let m = 0; for (const d of list) if (Number.isInteger(d) && d >= 0 && d <= 6) m |= 1 << d; return m; }

/** An exception with every field present (SeriesException defaults). */
function exception(x) {
 return { seriesId: x.seriesId, date: x.date, cancelled: x.cancelled ?? false, hold: x.hold ?? false, startTime: x.startTime ?? null,
  endTime: x.endTime ?? null, rateOverridden: x.rateOverridden ?? false, hourlyRate: x.hourlyRate ?? null };
}

const pad = (n, w = 2) => String(n).padStart(w, '0');
/** Local calendar day of a moment, "YYYY-MM-DD". */
function dateOf(millis) { const d = new Date(millis); return pad(d.getFullYear(), 4) + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }

function localFields(t) { const d = new Date(t); return [d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()].join(); }

/**
 * Wall-clock time on a local date, like a lenient java.util.Calendar: a time inside a spring-forward gap moves
 * forward, and an ambiguous fall-back time resolves to the later (standard-time) instant as Calendar does
 * (JS Date alone would pick the earlier one).
 */
function startMillis(date, start) {
 const [y, m, d] = date.split('-').map(Number);
 const minutes = Rules.toMinutes(start);
 const h = Math.trunc(minutes / 60), mi = minutes % 60;
 const v = new Date(2000, 0, 1, 0, 0, 0, 0);
 v.setFullYear(y, m - 1, d);
 v.setHours(h, mi, 0, 0);
 let t = v.getTime();
 const wall = localFields(t);
 for (const shift of [7200000, 3600000, 1800000]) if (localFields(t + shift) === wall) { t += shift; break; }
 return t;
}

/** The end of an occurrence; an end at or before the start is on the next day. */
function endMillis(date, start, end) {
 const next = Rules.toMinutes(end) <= Rules.toMinutes(start);
 const day = next ? Rules.civilFromDays(Rules.daysFromCivil(date) + 1) : date;
 return startMillis(day, end);
}

/** Every occurrence between from and until (inclusive) that is neither cancelled nor already recorded. `confirmed` is a Set or array of keys. */
function occurrences(series, exceptions, confirmed, from, until, now) {
 const done = confirmed instanceof Set ? confirmed : new Set(confirmed);
 const byKey = new Map(exceptions.map(x => [key(x.seriesId, x.date), x]));
 const out = [];
 for (const s of series) {
  const lo = maxStr(from, s.fromDate), hi = minStr(until, s.untilDate);
  if (lo > hi) continue;
  for (const date of Rules.seriesDates(days(s.daysMask), lo, hi)) {
   const k = key(s.id, date);
   const ex = byKey.get(k);
   if (done.has(k) || ex?.cancelled === true) continue;
   const start = ex?.startTime ?? s.startTime;
   const end = ex?.endTime ?? s.endTime;
   const rate = ex?.rateOverridden === true ? ex.hourlyRate ?? null : s.hourlyRate ?? null;
   out.push({ key: k, seriesId: s.id, category: s.category, date, start, end, rate, currency: s.currency,
    edited: ex != null && (ex.startTime != null || ex.endTime != null || ex.rateOverridden === true), hold: ex?.hold === true,
    status: endMillis(date, start, end) <= now ? AWAITING : PLANNED });
  }
 }
 return out.sort((a, b) => cmp(a.date, b.date) || cmp(a.start, b.start));
}

/** Occurrences that ended and may be recorded by themselves (not held after an undo). */
const toConfirm = list => list.filter(o => o.status === AWAITING && !o.hold);

/** The shift an occurrence becomes (web/Android entry JSON). An unknown rate stays unknown (never ₪0). */
function toEntry(o, auto, createdAt = Date.now()) {
 const h = Rules.span(o.start, o.end).minutes / 60;
 const date = startMillis(o.date, o.start);
 return { category: o.category, date, createdAt, isTimeRange: true, startTime: o.start, endTime: o.end, hours: h,
  hourlyRate: o.rate ?? 0, totalEarnings: o.rate != null ? Pay.amount(h, o.rate) : 0, isPaid: false, notes: '', currency: o.currency,
  isGroupShift: false, employerRate: null, workerRate: null, groupWorkersJson: '',
  kind: 'shift', payKnown: o.rate != null, bonus: 0, seriesKey: o.key, autoConfirmed: auto };
}

/** Minute ranges on the same day overlap (an overnight end runs into the next day). */
function overlaps(s1, e1, s2, e2) {
 const range = (s, e) => { const a = Rules.toMinutes(s); let b = Rules.toMinutes(e); if (b <= a) b += 1440; return [a, b]; };
 const [a1, b1] = range(s1, e1), [a2, b2] = range(s2, e2);
 return a1 < b2 && a2 < b1;
}

function groupBy(list, f) { const m = new Map(); for (const x of list) { const k = f(x); if (!m.has(k)) m.set(k, []); m.get(k).push(x); } return m; }

/** The dates a new series would create; dates that overlap a saved timed shift or another plan are not created. Returns {dates, clashes, created}. */
function preview(dayList, from, until, start, end, saved, planned) {
 const dates = from <= until ? Rules.seriesDates(dayList, from, until) : [];
 const timed = groupBy(saved.filter(e => e.isTimeRange && Rules.isTime(e.startTime) && Rules.isTime(e.endTime)), e => dateOf(e.date));
 const plans = groupBy(planned, o => o.date);
 const clashes = dates.filter(d => (timed.get(d) || []).some(e => overlaps(start, end, e.startTime, e.endTime)) ||
  (plans.get(d) || []).some(o => overlaps(start, end, o.start, o.end)));
 const clash = new Set(clashes);
 return { dates, clashes, created: dates.filter(d => !clash.has(d)) };
}

/**
 * "From now on": the old series ends the day before `date` (or disappears when `date` is its first day) and a new
 * one starts there with the changes; later exceptions move to the new series so nothing is orphaned.
 * opts: {start=s.startTime, end=s.endTime, rate=s.hourlyRate (null allowed), now=Date.now(), recorded=[] (keys)}.
 * Returns {oldId, old (series|null), new (series), moved (exceptions), dropped (exceptions)}.
 */
function split(s, exceptions, date, newId, opts = {}) {
 const start = opts.start ?? s.startTime, end = opts.end ?? s.endTime;
 const rate = 'rate' in opts ? opts.rate ?? null : s.hourlyRate;
 const now = opts.now ?? Date.now();
 const recorded = opts.recorded ? [...opts.recorded] : [];
 const dayBefore = Rules.civilFromDays(Rules.daysFromCivil(date) - 1);
 const old = date <= s.fromDate ? null : { ...s, untilDate: dayBefore };
 const next = { ...s, id: newId, startTime: start, endTime: end, hourlyRate: rate, fromDate: maxStr(date, s.fromDate), createdAt: now };
 const later = exceptions.filter(x => x.seriesId === s.id && x.date >= date).map(exception);
 // Hours changed from now on: per-day hour overrides would hide the change, so only cancellations/holds move.
 const kept = new Map(later.map(x => ({ ...x, seriesId: newId, startTime: null, endTime: null }))
  .filter(x => x.cancelled || x.hold || x.rateOverridden).map(x => [x.date, x]));
 // Days of the old plan that were already recorded must not come back under the new plan's keys.
 const done = recorded.filter(k => seriesOfKey(k) === s.id).map(dateOfKey).filter(d => d >= date);
 const moved = new Map(kept);
 for (const d of done) moved.set(d, { ...(kept.get(d) ?? exception({ seriesId: newId, date: d })), cancelled: true });
 const movedList = [...moved.values()].sort((a, b) => cmp(a.date, b.date));
 return { oldId: s.id, old, new: next, moved: movedList, dropped: later };
}

/**
 * A new rate for a series from `today` on. Occurrences that already ended and wait to be recorded keep the old
 * rate (pinned per day), and recorded shifts never change. Returns {series, changes}.
 */
function applyRateFromToday(s, exceptions, rate, today, waiting) {
 const mine = new Map(exceptions.filter(x => x.seriesId === s.id).map(x => [x.date, exception(x)]));
 const pins = waiting.filter(o => o.seriesId === s.id && o.date < today).map(o => {
  const x = mine.get(o.date) ?? exception({ seriesId: s.id, date: o.date });
  return x.rateOverridden ? x : { ...x, rateOverridden: true, hourlyRate: s.hourlyRate ?? null };
 });
 // Planned days that had their own rate now follow the new one, as the prototype does.
 const cleared = [...mine.values()].filter(x => x.date >= today && x.rateOverridden).map(x => ({ ...x, rateOverridden: false, hourlyRate: null }));
 return { series: { ...s, hourlyRate: rate }, changes: [...pins, ...cleared] };
}

const api = { PLANNED, AWAITING, key, dateOfKey, days, mask, exception, dateOf, startMillis, endMillis, occurrences, toConfirm, toEntry,
 overlaps, preview, split, applyRateFromToday };
if (node) module.exports = api; else root.KisPlan = api;
})(globalThis);
