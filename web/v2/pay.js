(function (root) {
'use strict';
/*
 * The single place that answers "how much did this shift pay?" — port of the Android
 * data/WorkPay.kt plus WorkMoney.kt (format / summary / report).
 *
 * Entries are the shared web/Android JSON entry objects:
 *  {category, date(ms), isTimeRange, startTime, endTime, hours, hourlyRate, totalEarnings, isPaid, notes,
 *   currency, isGroupShift, employerRate, workerRate, groupWorkersJson,
 *   kind ('shift'|'job', default 'shift'), payKnown (default true), bonus (default 0),
 *   seriesKey (default null), autoConfirmed (default false)}
 * Missing 2.0 fields read as their defaults, so pre-2.0 entries behave exactly as before.
 *
 * Since 2.0 a shift may have no known rate yet (or a job no known amount). Then totalEarnings
 * is only a 0 placeholder: it is never shown as pay and never summed.
 */

// ---- Java / Android org.json semantics ----

/** java.lang.Double.valueOf(String) for decimal literals (trims ASCII control/space like Java). */
function javaParseDouble(text) {
 const s = String(text).replace(/^[\x00-\x20]+|[\x00-\x20]+$/g, '');
 const m = /^([+-]?)(NaN|Infinity|(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)[fFdD]?$/.exec(s);
 if (!m) return null;
 if (m[2] === 'NaN') return NaN;
 const v = Number(m[2]);
 return m[1] === '-' ? -v : v;
}
/** Android org.json JSON.toDouble: numbers as they are, numeric strings parsed, anything else null. */
function jsonDouble(v) {
 if (typeof v === 'number') return v;
 if (typeof v === 'string') return javaParseDouble(v);
 return null;
}
const has = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
const isNull = (o, k) => !has(o, k) || o[k] === null;
function optString(o, k, fallback = '') {
 if (!has(o, k) || o[k] === undefined) return fallback;
 const v = o[k];
 if (v === null) return 'null'; // Android: String.valueOf(JSONObject.NULL)
 return typeof v === 'string' ? v : typeof v === 'object' ? JSON.stringify(v) : String(v);
}
function optDouble(o, k, fallback) { const v = has(o, k) ? jsonDouble(o[k]) : null; return v === null ? fallback : v; }
function optBoolean(o, k, fallback) {
 const v = has(o, k) ? o[k] : undefined;
 if (typeof v === 'boolean') return v;
 if (typeof v === 'string') { const l = v.toLowerCase(); if (l === 'true') return true; if (l === 'false') return false; }
 return fallback;
}
function getDouble(o, k) { const v = has(o, k) ? jsonDouble(o[k]) : null; if (v === null) throw Error('JSONObject["' + k + '"] is not a number'); return v; }

// ---- Number text ----

/**
 * Java String.format(Locale.US, "%.2f" / "%,.2f"): HALF_UP applied to the shortest decimal
 * representation (Double.toString), so 2.675 → "2.68" (unlike JS toFixed, which gives "2.67").
 */
function fixed2(amount, grouping = false) {
 if (Number.isNaN(amount)) return 'NaN';
 if (!Number.isFinite(amount)) return amount < 0 ? '-Infinity' : 'Infinity';
 const negative = amount < 0 || Object.is(amount, -0);
 const m = /^(\d+)(?:\.(\d+))?(?:e([+-]\d+))?$/.exec(String(Math.abs(amount)));
 const frac = m[2] || '';
 let digits = BigInt(m[1] + frac);
 const exp = Number(m[3] || 0) - frac.length + 2; // value * 100 = digits * 10^exp
 if (exp >= 0) digits *= 10n ** BigInt(exp);
 else {
  const div = 10n ** BigInt(-exp), rem = digits % div;
  digits /= div;
  if (rem * 2n >= div) digits += 1n;
 }
 const text = digits.toString().padStart(3, '0');
 let whole = text.slice(0, -2);
 if (grouping) whole = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
 return (negative ? '-' : '') + whole + '.' + text.slice(-2);
}

/** BigDecimal(h).setScale(2, HALF_UP).stripTrailingZeros().toPlainString(): 8 → "8", 7.5 → "7.5", 7.1+0.2 → "7.3", 2.97 → "2.97". */
function hours(h) {
 // toFixed rounds the exact binary value half away from zero, which is exactly BigDecimal(double) + HALF_UP.
 let s = Math.abs(h) < 1e21 ? h.toFixed(2) : BigInt(Math.round(h)).toString();
 if (s.includes('.')) s = s.replace(/0+$/, '').replace(/\.$/, '');
 return s === '-0' ? '0' : s;
}

// ---- WorkPay ----

const KIND_SHIFT = 'shift', KIND_JOB = 'job';
const payKnownOf = e => e.payKnown !== false;
const bonusOf = e => Number(e.bonus ?? 0);
const isJob = e => (e.kind ?? KIND_SHIFT) === KIND_JOB;

/** Real pay, or null while the rate/amount is missing. */
const pay = e => payKnownOf(e) ? e.totalEarnings : null;
const known = entries => entries.filter(payKnownOf);
const pending = entries => entries.filter(e => !payKnownOf(e));

/** Same rounding the app has always stored, so new rows match historical ones. */
function amount(h, rate, bonus = 0) { return Math.round((h * rate + bonus) * 100) / 100; }

/** "תעריף חסר" for a shift, "סכום חסר" for a job. */
const missingLabel = e => isJob(e) ? 'סכום חסר' : 'תעריף חסר';

/** Per-currency totals of known pay only (insertion ordered object). A currency appears only if it has known pay. */
function totals(entries) {
 const out = {};
 for (const e of known(entries)) out[e.currency] = (out[e.currency] ?? 0) + e.totalEarnings;
 return out;
}

/**
 * Group shift workers from groupWorkersJson: [{name, hours, isPaid, bonus, workerRate, employerRate, source}].
 * Unknown keys stay in `source`. Any malformed element makes the whole list empty (as on Android).
 */
function workers(e) {
 const json = e.groupWorkersJson ?? '';
 if (!String(json).trim()) return [];
 try {
  const array = JSON.parse(json);
  if (!Array.isArray(array)) return [];
  return array.map(o => {
   if (!o || typeof o !== 'object' || Array.isArray(o)) throw Error('not an object');
   const h = optDouble(o, 'hours', 0), b = optDouble(o, 'bonus', 0);
   return {
    name: optString(o, 'name', ''), hours: Number.isFinite(h) ? h : 0, isPaid: optBoolean(o, 'isPaid', false),
    bonus: Number.isFinite(b) && b > 0 ? b : 0,
    workerRate: isNull(o, 'workerRate') ? null : getDouble(o, 'workerRate'),
    employerRate: isNull(o, 'employerRate') ? null : getDouble(o, 'employerRate'),
    source: o,
   };
  });
 } catch { return []; }
}

/** A worker without their own rate follows the shift rate, so it is unknown while that is. */
const workerRate = (w, e) => w.workerRate ?? e.workerRate ?? (payKnownOf(e) ? e.hourlyRate : null);
const employerRate = (w, e) => w.employerRate ?? e.employerRate ?? (payKnownOf(e) ? e.hourlyRate : null);
/** What the worker should receive: their hours at the worker rate plus their own bonus. */
function workerPay(w, e) { const r = workerRate(w, e); return r == null ? null : amount(w.hours, r, w.bonus); }
/** What the employer pays for this worker. A worker's bonus is passed through, not profit. */
function employerPay(w, e) { const r = employerRate(w, e); return r == null ? null : amount(w.hours, r, w.bonus); }

/** {employerTotal, profit, myNet}, or null while any part is unknown: a partial group total would be misleading. */
function group(e) {
 const own = pay(e);
 if (own == null) return null;
 let employer = 0, profit = 0;
 for (const w of workers(e)) {
  const er = employerRate(w, e); if (er == null) return null;
  const wr = workerRate(w, e); if (wr == null) return null;
  employer += amount(w.hours, er, w.bonus);
  profit += w.hours * (er - wr);
 }
 const r = v => Math.round(v * 100) / 100;
 return { employerTotal: r(own + employer), profit: r(profit), myNet: r(own + profit) };
}

/** Back to groupWorkersJson; keys of `source` keep their place, bonus is written only when > 0. */
function encodeWorkers(list) {
 return JSON.stringify(list.map(w => {
  const o = JSON.parse(JSON.stringify(w.source ?? {}));
  o.name = w.name; o.hours = w.hours; o.isPaid = w.isPaid;
  if (w.bonus > 0) o.bonus = w.bonus; else delete o.bonus;
  return o;
 }));
}

// ---- WorkMoney ----

function format(value, currency) { return currency + fixed2(value, true); }
function summary(entries) { return Object.entries(totals(entries)).map(([c, v]) => format(v, c)).join('\n') || '0.00'; }
/** Legacy WorkMoney rate readers (worker JSON object + entry), kept for parity with Android callers. */
function moneyWorkerRate(worker, entry) { return !isNull(worker, 'workerRate') ? getDouble(worker, 'workerRate') : entry.workerRate ?? entry.hourlyRate; }
function moneyEmployerRate(worker, entry) { return !isNull(worker, 'employerRate') ? getDouble(worker, 'employerRate') : entry.employerRate ?? entry.hourlyRate; }
function pendingLine(rows) {
 const n = pending(rows).length;
 return n === 0 ? '' : '\n' + (n === 1 ? 'משמרת אחת עוד בלי תעריף (לא נספרה)' : n + ' משמרות עוד בלי תעריף (לא נספרו)');
}
const sumHours = rows => rows.reduce((s, r) => s + r.hours, 0);

/** WhatsApp summary: per category (or for one worker), hours and one amount per currency. Unknown pay is never "0.00". */
function report(entries, workerName = null) {
 if (workerName == null) {
  const groups = new Map();
  for (const e of entries) { if (!groups.has(e.category)) groups.set(e.category, []); groups.get(e.category).push(e); }
  return [...groups].map(([name, rows]) =>
   name + ' | ' + hours(sumHours(rows)) + ' שעות' + (Object.keys(totals(rows)).length === 0 ? '' : '\n' + summary(rows)) + pendingLine(rows)).join('\n\n');
 }
 const rows = entries.flatMap(entry => workers(entry).filter(w => w.name === workerName).map(w => {
  const p = workerPay(w, entry);
  return { ...entry, hours: w.hours, totalEarnings: p ?? 0, isPaid: w.isPaid, payKnown: p != null };
 }));
 return workerName + ' | ' + hours(sumHours(rows)) + ' שעות\n' + (Object.keys(totals(rows)).length === 0 ? '' : summary(rows) + '\n') +
  rows.map(r => r.category + ': ' + (r.payKnown ? format(r.totalEarnings, r.currency) : 'תעריף חסר') + ' — ' + (r.isPaid ? 'שולם' : 'ממתין')).join('\n');
}

const api = { KIND_SHIFT, KIND_JOB, isJob, payKnown: payKnownOf, bonus: bonusOf, pay, known, pending, amount, missingLabel, totals,
 workers, workerRate, employerRate, workerPay, employerPay, group, encodeWorkers,
 format, summary, report, moneyWorkerRate, moneyEmployerRate, fixed2, hours, javaParseDouble };
if (typeof module !== 'undefined') module.exports = api; else root.KisPay = api;
})(globalThis);
