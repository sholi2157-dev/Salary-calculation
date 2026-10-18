(function (root) {
'use strict';
/*
 * Every text the app shares or copies — exact port of the Android data/WorkShareText.kt.
 * Expected outputs: web-tests/fixtures/v2-share-fixtures.json (shared with Android V2ShareTest).
 * Amounts are always the stored ones (KisPay), never recalculated here, and a missing rate or
 * amount is said in words, never shown as 0. No hidden bidi marks are ever added.
 * Browser load order: rules.js, pay.js, plan.js, then this file.
 */
const node = typeof module !== 'undefined';
const Pay = node ? require('./pay.js') : root.KisPay;
const Rules = node ? require('./rules.js') : root.KisRules;
const Plan = node ? require('./plan.js') : root.KisPlan;

const pad = n => String(n).padStart(2, '0');
/** dd/MM/yyyy of the local calendar day. */
function date(millis) { const d = new Date(millis); return pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + String(d.getFullYear()).padStart(4, '0'); }
function money(value, currency) { return currency + Pay.fixed2(value); }
const hours = Pay.hours;
const TOTAL = 'סה"כ לתשלום';
const bonusOf = Pay.bonus;

/** One personal shift, or my own part of a group shift (nothing about the others). */
function personal(e) {
 if (Pay.isJob(e)) return job(e);
 const pay = Pay.pay(e);
 const out = [];
 out.push('היי, להלן פרטי המשמרת שלי מיום ' + date(e.date) + ':');
 out.push('קטגוריה: ' + e.category);
 out.push('שעות עבודה: ' + hours(e.hours) + ' שעות');
 out.push('תעריף שעתי: ' + (pay == null ? 'עדיין לא ידוע' : money(e.hourlyRate, e.currency)));
 if (bonusOf(e) > 0) out.push('בונוס: ' + money(bonusOf(e), e.currency));
 out.push(TOTAL + ': ' + (pay != null ? money(pay, e.currency) : 'ממתין להשלמת תעריף'));
 return out.join('\n');
}

/** A fixed-price job: no hourly rate; the working time only if it was entered. */
function job(e) {
 const pay = Pay.pay(e);
 const notes = String(e.notes ?? '');
 const out = [];
 out.push('היי, להלן פרטי העבודה שלי מיום ' + date(e.date) + ':');
 if (notes.trim()) out.push('עבודה: ' + notes.trim());
 out.push('קטגוריה: ' + e.category);
 if (e.hours > 0) out.push('זמן עבודה: ' + hours(e.hours) + ' שעות');
 if (bonusOf(e) > 0) out.push('בונוס: ' + money(bonusOf(e), e.currency));
 out.push(TOTAL + ': ' + (pay != null ? money(pay, e.currency) : 'ממתין להשלמה'));
 return out.join('\n');
}

/**
 * For the employer of a group shift: my part and every worker at the employer rate (a team bonus
 * is passed through, as in KisPay.group). Never the worker rate or my margin.
 */
function employer(e, ownName = null) {
 const own = Pay.pay(e);
 const list = Pay.workers(e);
 const lines = list.map(w => Pay.employerPay(w, e));
 const total = own == null || lines.some(l => l == null) ? null : Math.round((own + lines.reduce((s, l) => s + l, 0)) * 100) / 100;
 const name = ownName == null ? '' : String(ownName).trim();
 const out = [];
 out.push('היי, להלן סיכום שעות עבודה ליום ' + date(e.date) + ':');
 out.push('**' + TOTAL + ' (כולל כולם): ' + (total != null ? money(total, e.currency) : 'עוד חסר תעריף') + '**');
 out.push('---');
 out.push('פירוט:');
 // With a profile name the report lists me by name like everyone else ("שולי: …"); otherwise "החלק שלי".
 out.push((name || 'החלק שלי') + ': ' + hours(e.hours) + ' שעות (' + (own != null ? money(own, e.currency) : 'תעריף חסר') + ')');
 list.forEach((w, i) => out.push(w.name + ': ' + hours(w.hours) + ' שעות (' + (lines[i] != null ? money(lines[i], e.currency) : 'תעריף חסר') + ')'));
 out.push('---');
 return out.join('\n');
}

/** For one worker only (a KisPay.workers(e) item): their hours, their pay at the worker rate (with their bonus) and whether it was paid. */
function worker(e, w) {
 const pay = Pay.workerPay(w, e);
 return [
  'היי ' + w.name + ', להלן פירוט שעות עבודה שלך מיום ' + date(e.date) + ':',
  'עבדת ' + hours(w.hours) + ' שעות. מגיע לך: ' + (pay != null ? money(pay, e.currency) + (w.bonus > 0 ? ' (כולל בונוס ' + money(w.bonus, e.currency) + ')' : '') : 'עוד לא ידוע') + '.',
  'סטטוס תשלום: ' + (w.isPaid ? 'שולם' : 'ממתין'),
 ].join('\n');
}

/** A planned shift (KisPlan occurrence): clearly marked as planned; the amount is only an estimate, never "to pay". */
function planned(o) {
 const h = Rules.span(o.start, o.end).minutes / 60;
 return [
  'היי, להלן פרטי משמרת מתוכננת ליום ' + date(Plan.startMillis(o.date, '12:00')) + ':',
  'קטגוריה: ' + o.category,
  'שעות: ' + o.start + '–' + o.end + ' (' + hours(h) + ' שעות)',
  'תעריף שעתי: ' + (o.rate != null ? money(o.rate, o.currency) : 'עדיין לא ידוע'),
  'צפי (עוד לא בוצעה): ' + (o.rate != null ? money(Pay.amount(h, o.rate), o.currency) : 'ממתין להשלמת תעריף'),
 ].join('\n');
}

/** WhatsApp summary of exactly the given rows: per category, hours and one amount per currency. */
function summary(entries, workerName = null) { return Pay.report(entries, workerName); }

const excelHeaders = ['קטגוריה', 'תאריך', 'שעות', 'תעריף שעתי', 'שכר לתשלום', 'סטטוס', 'הערות', 'מטבע', 'סוג', 'שכר ידוע', 'בונוס'];

/** Tab-separated table for Excel / Google Sheets. RC16 columns first, 2.0 columns only appended. */
function excel(entries) {
 const n = v => Pay.fixed2(v);
 const cell = s => /[\t\n\r"]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
 const rows = entries.map(e => {
  const knownPay = Pay.payKnown(e), isJob = Pay.isJob(e);
  return [
   e.category, date(e.date), n(e.hours),
   !knownPay || isJob ? '' : n(e.hourlyRate),
   !knownPay ? '' : n(e.totalEarnings),
   e.isPaid ? 'שולם' : 'ממתין',
   String(e.notes ?? ''), e.currency,
   isJob ? 'קבלנות' : e.isGroupShift ? 'משמרת קבוצתית' : 'משמרת',
   knownPay ? 'כן' : 'חסר',
   bonusOf(e) > 0 ? n(bonusOf(e)) : '',
  ].map(cell).join('\t');
 });
 return [excelHeaders.join('\t'), ...rows].join('\n');
}

const api = { date, money, hours, personal, job, employer, worker, planned, summary, excel };
if (node) module.exports = api; else root.KisShare = api;
})(globalThis);
