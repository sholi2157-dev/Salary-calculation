(function (root) {
'use strict';
/*
 * Salary Calculation 2.0 ("כיס") pure rules — a line-by-line port of the Android
 * app/src/main/java/com/example/domain/V2Rules.kt. No DOM, storage or UI.
 * Both platforms are checked against web-tests/fixtures/v2-rules.json (copied byte-for-byte
 * from the Android test resources; scripts/check-fixtures.cjs guards drift).
 * Times are "HH:MM" strings / minutes of day; dates are "YYYY-MM-DD".
 *
 * Kotlin semantics replicated on purpose:
 *  - Int division truncates toward zero (Math.trunc), `%` keeps the dividend's sign (same as JS).
 *  - String.toIntOrNull: optional sign + ASCII digits, null on Int overflow.
 *  - Int arithmetic in toMinutes wraps like a 32-bit Int.
 *  - roundToLong() == Math.round (half up toward +infinity), same as JS Math.round.
 *  - round2 adds Math.ulp(1.0) (= Number.EPSILON = 2^-52) before rounding.
 */
const LONG_SHIFT_MINUTES = 16 * 60;
const INT_MAX = 2147483647, INT_MIN = -2147483648;

/** Kotlin String.toIntOrNull (radix 10, ASCII digits). */
function toIntOrNull(text) {
 if (!/^[+-]?\d+$/.test(text)) return null;
 const n = Number(text);
 return n > INT_MAX || n < INT_MIN ? null : n;
}

/** Kotlin String.toDoubleOrNull for decimal literals (Java float syntax: "1.", ".5", "1e3", "2d", "NaN", "Infinity"). Hex floats are not supported. */
function toDoubleOrNull(text) {
 const s = String(text);
 const m = /^([+-]?)(NaN|Infinity|(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)[fFdD]?$/.exec(s);
 if (!m) return null;
 if (m[2] === 'NaN') return NaN;
 const v = Number(m[2]);
 return m[1] === '-' ? -v : v;
}

/** Tolerates malformed stored times (old imports): anything unreadable counts as 0 instead of crashing. */
function toMinutes(time) {
 const parts = String(time).trim().split(':');
 const h = parts[0] === undefined ? null : toIntOrNull(parts[0].trim());
 const m = parts[1] === undefined ? null : toIntOrNull(parts[1].trim().slice(0, 2));
 return (Math.imul(h ?? 0, 60) + (m ?? 0)) | 0;
}

/** "H:MM" / "HH:MM" within a day. */
function isTime(time) {
 if (time == null) return false;
 const t = String(time).trim();
 if (!/^\d{1,2}:\d{2}$/.test(t)) return false;
 const [h, m] = t.split(':').map(Number);
 return h < 24 && m < 60;
}

const pad = (n, w) => String(n).padStart(w, '0');

function fromMinutes(minutes) {
 const m = ((minutes % 1440) + 1440) % 1440;
 return pad(Math.trunc(m / 60), 2) + ':' + pad(m % 60, 2);
}

/** Suggested end time: the current time rounded down to the previous 30-minute boundary. */
function roundedEnd(nowMinutes) { return fromMinutes(Math.trunc(nowMinutes / 30) * 30); }

/** Two-decimal rounding with the same epsilon guard the existing app and web use. */
function round2(value) { return Math.floor((value + Number.EPSILON) * 100 + 0.5) / 100; }

/** An end earlier than the start means the shift crosses midnight; the date stays the start day. */
function span(start, end, breakMinutes = 0) {
 const s = toMinutes(start);
 let e = toMinutes(end);
 if (e === s) return { minutes: 0, overnight: false, invalid: true, long: false };
 const overnight = e < s;
 if (overnight) e += 1440;
 const minutes = e - s - breakMinutes;
 if (minutes <= 0) return { minutes: 0, overnight, invalid: true, long: false };
 return { minutes, overnight, invalid: false, long: minutes > LONG_SHIFT_MINUTES };
}

const MODES = ['CLOCK', 'HOURS', 'JOB', 'GROUP'];
const BONUS_TARGETS = ['ALL', 'ME', 'OTHERS'];

function number(text) {
 const t = String(text ?? '').trim() || '0';
 const v = toDoubleOrNull(t);
 return v === null ? NaN : v;
}

/**
 * Draft (what the user has typed; strings mirror form fields, blank = not entered):
 *  {mode:'CLOCK'|'HOURS'|'JOB'|'GROUP', start=null, end=null, breakMinutes=0, hours='', rate=null,
 *   rateUnknown=false, bonus=0, bonusTarget='ME'|'ALL'|'OTHERS', amount='', amountUnknown=false}
 * Returns {errors, minutes, bonus, total, canSave}.
 * Unknown pay is never zero: total stays null until the rate (or job amount) is known.
 */
function evaluate(input) {
 const d = {
  mode: String(input.mode).toUpperCase(), start: input.start ?? null, end: input.end ?? null,
  breakMinutes: input.breakMinutes ?? 0, hours: input.hours ?? '', rate: input.rate ?? null,
  rateUnknown: input.rateUnknown ?? false, bonus: input.bonus ?? 0,
  bonusTarget: String(input.bonusTarget ?? 'ME').toUpperCase(), amount: input.amount ?? '',
  amountUnknown: input.amountUnknown ?? false,
 };
 if (!MODES.includes(d.mode)) throw Error('Unknown mode ' + input.mode);
 if (!BONUS_TARGETS.includes(d.bonusTarget)) throw Error('Unknown bonus target ' + input.bonusTarget);
 const errors = [];
 const result = (minutes, bonus, total) => ({ errors, minutes, bonus, total, canSave: errors.length === 0 });
 if (d.mode === 'JOB') {
  const amount = d.amountUnknown ? null : number(d.amount);
  let h = number(d.hours); if (Number.isNaN(h)) h = 0;
  if (!d.amountUnknown && !(amount !== null && amount > 0)) errors.push('הזן סכום, או סמן "לא יודע עדיין"');
  if (h > 24) errors.push('יותר מ-24 שעות ביום');
  const minutes = h > 0 ? Math.round(h * 60) : null;
  const total = amount !== null && amount > 0 ? round2(amount) : null;
  return result(minutes, 0, total);
 }
 let minutes = null;
 let invalid = false;
 if (d.mode === 'HOURS') {
  const h = number(d.hours);
  if (!(h > 0)) errors.push('הזן מספר שעות'); else if (h > 24) errors.push('יותר מ-24 שעות ביום');
  if (h > 0) minutes = Math.round(h * 60);
 } else {
  if (d.start === null) errors.push('בחר שעת התחלה');
  if (d.start !== null && d.end !== null) {
   const sp = span(d.start, d.end, d.breakMinutes);
   minutes = sp.minutes;
   invalid = sp.invalid;
   if (sp.invalid) errors.push(sp.minutes === 0 && toMinutes(d.start) === toMinutes(d.end) ? 'שעת ההתחלה והסיום זהות' : 'ההפסקה ארוכה מהמשמרת');
  }
 }
 const rate = d.rateUnknown ? null : d.rate;
 if (!d.rateUnknown && !(rate !== null && rate >= 0)) errors.push('הזן תעריף, או סמן "תעריף חסר"');
 const myBonus = d.mode === 'GROUP' && d.bonusTarget === 'OTHERS' ? 0 : d.bonus;
 const base = minutes !== null && !invalid && rate !== null ? minutes / 60 * rate : null;
 return result(minutes, myBonus, base === null ? null : round2(base + myBonus));
}

/** Quiet hours: a notification due inside the window waits until it ends; it is never dropped. */
function quietDelay(atMinutes, enabled, from, to) {
 if (!enabled) return null;
 const f = toMinutes(from), t = toMinutes(to);
 const inside = f === t ? false : f < t ? atMinutes >= f && atMinutes < t : atMinutes >= f || atMinutes < t;
 return inside ? to : null;
}

// ---- Dates (proleptic Gregorian, days since 1970-01-01) ----

function daysFromCivil(date) {
 const [yy, mm, dd] = String(date).split('-').map(s => {
  const n = toIntOrNull(s); if (n === null) throw Error('Invalid date ' + date); return n;
 });
 const y = mm <= 2 ? yy - 1 : yy;
 const era = Math.trunc((y >= 0 ? y : y - 399) / 400);
 const yoe = y - era * 400;
 const doy = Math.trunc((153 * (mm + (mm > 2 ? -3 : 9)) + 2) / 5) + dd - 1;
 const doe = yoe * 365 + Math.trunc(yoe / 4) - Math.trunc(yoe / 100) + doy;
 return era * 146097 + doe - 719468;
}

function civilFromDays(days) {
 const z = days + 719468;
 const era = Math.trunc((z >= 0 ? z : z - 146096) / 146097);
 const doe = z - era * 146097;
 const yoe = Math.trunc((doe - Math.trunc(doe / 1460) + Math.trunc(doe / 36524) - Math.trunc(doe / 146096)) / 365);
 const doy = doe - (365 * yoe + Math.trunc(yoe / 4) - Math.trunc(yoe / 100));
 const mp = Math.trunc((5 * doy + 2) / 153);
 const d = doy - Math.trunc((153 * mp + 2) / 5) + 1;
 const m = mp + (mp < 10 ? 3 : -9);
 const y = yoe + era * 400 + (m <= 2 ? 1 : 0);
 return (y < 0 ? '-' + pad(-y, 4) : pad(y, 4)) + '-' + pad(m, 2) + '-' + pad(d, 2);
}

/** 0 = Sunday … 6 = Saturday, matching the app's existing weekday order. */
function weekday(date) { return (((daysFromCivil(date) + 4) % 7) + 7) % 7; }

/** The dates a weekly series produces between from and until, inclusive. `days` is a Set or array of weekdays. */
function seriesDates(days, from, until) {
 const set = days instanceof Set ? days : new Set(days);
 const out = [];
 let d = daysFromCivil(from);
 const last = daysFromCivil(until);
 while (d <= last) {
  const date = civilFromDays(d);
  if (set.has(weekday(date))) out.push(date);
  d++;
 }
 return out;
}

// ---- Bulk rate completion ----

/** PayRecord: {id, isJob, start, end, breakMinutes, hours, rate, bonus}. */
function recordMinutes(r) {
 return r.start != null && r.end != null ? span(r.start, r.end, r.breakMinutes ?? 0).minutes : Math.round((r.hours ?? 0) * 60);
}

/** Writes only to shifts whose rate is missing. Known amounts and fixed-price jobs never change. Returns {updated:[{id,total}], skipped}. */
function bulkRate(records, rate) {
 const targets = records.filter(r => r.rate == null && !r.isJob);
 return { updated: targets.map(r => ({ id: r.id, total: round2(recordMinutes(r) / 60 * rate + (r.bonus ?? 0)) })), skipped: records.length - targets.length };
}

const api = { LONG_SHIFT_MINUTES, MODES, BONUS_TARGETS, toIntOrNull, toDoubleOrNull, toMinutes, isTime, fromMinutes, roundedEnd, round2, span,
 evaluate, quietDelay, daysFromCivil, civilFromDays, weekday, seriesDates, recordMinutes, bulkRate };
if (typeof module !== 'undefined') module.exports = api; else root.KisRules = api;
})(globalThis);
