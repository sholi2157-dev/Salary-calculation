(function (root) {
'use strict';
// Hebrew dates and the weekly portion (owner feedback 2.3): "כ״ח חשוון (8/11)" everywhere, "שבוע פרשת וירא" in History.
// The calendar is the fixed arithmetic one (Reingold & Dershowitz); the civil day is used (no sunset rule). Same code in
// app/src/main/java/com/example/data/HebrewDate.kt; both are checked against web-tests/fixtures/hebrew-dates.json (made with @hebcal/core).
const EPOCH = -1373427, UNIX_RD = 719163;
const isLeap = y => (7 * y + 1) % 19 < 7;
function elapsed(y) { const m = Math.floor((235 * y - 234) / 19), p = 12084 + 13753 * m; const d = 29 * m + Math.floor(p / 25920); return (3 * (d + 1)) % 7 < 3 ? d + 1 : d; }
function correction(y) { const a = elapsed(y - 1), b = elapsed(y), c = elapsed(y + 1); return c - b === 356 ? 2 : b - a === 382 ? 1 : 0; }
const newYear = y => EPOCH + elapsed(y) + correction(y);
const yearDays = y => newYear(y + 1) - newYear(y);
// Months as in the books: 1 ניסן … 6 אלול, 7 תשרי … 12 אדר (אדר א׳ in a leap year), 13 אדר ב׳.
function monthDays(y, m) {
  if ([2, 4, 6, 10, 13].includes(m)) return 29;
  if (m === 12) return isLeap(y) ? 30 : 29;
  if (m === 8) return [355, 385].includes(yearDays(y)) ? 30 : 29;
  if (m === 9) return [353, 383].includes(yearDays(y)) ? 29 : 30;
  return 30;
}
const last = y => isLeap(y) ? 13 : 12;
function toRd(y, m, d) {
  let r = newYear(y) + d - 1;
  if (m < 7) { for (let i = 7; i <= last(y); i++) r += monthDays(y, i); for (let i = 1; i < m; i++) r += monthDays(y, i); }
  else for (let i = 7; i < m; i++) r += monthDays(y, i);
  return r;
}
/** [year, month, day] of a civil date given as local millis. */
function fromMillis(ms) {
  const dt = new Date(ms), rd = Math.floor(Date.UTC(dt.getFullYear(), dt.getMonth(), dt.getDate()) / 86400000) + UNIX_RD;
  let y = Math.floor((rd - EPOCH) / (35975351 / 98496)) - 1;
  while (newYear(y + 1) <= rd) y++;
  let m = rd < toRd(y, 1, 1) ? 7 : 1;
  while (rd > toRd(y, m, monthDays(y, m))) m++;
  return [y, m, rd - toRd(y, m, 1) + 1];
}
const ONES = ['', 'א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ז', 'ח', 'ט'], TENS = ['', 'י', 'כ', 'ל', 'מ', 'נ', 'ס', 'ע', 'פ', 'צ'], HUND = ['', 'ק', 'ר', 'ש', 'ת', 'תק', 'תר', 'תש', 'תת', 'תתק'];
/** 28 → "כ״ח", 15 → "ט״ו", 5787 → "תשפ״ז". */
function gematria(n) {
  n %= 1000;
  let s = HUND[Math.floor(n / 100)], r = n % 100;
  s += r === 15 ? 'טו' : r === 16 ? 'טז' : TENS[Math.floor(r / 10)] + ONES[r % 10];
  return s.length > 1 ? s.slice(0, -1) + '\u05F4' + s.slice(-1) : s + '\u05F3';
}
const MONTHS = ['', 'ניסן', 'אייר', 'סיוון', 'תמוז', 'אב', 'אלול', 'תשרי', 'חשוון', 'כסלו', 'טבת', 'שבט', 'אדר', 'אדר ב׳'];
const monthName = (y, m) => m === 12 && isLeap(y) ? 'אדר א׳' : MONTHS[m];
/** "כ״ח חשוון". */
function day(ms) { const [y, m, d] = fromMillis(ms); return gematria(d) + ' ' + monthName(y, m); }
/** "כ״ח חשוון (8/11)"; another year adds both years: "כ״ח חשוון תשפ״ו (8/11/2025)". */
function label(ms, withYear = new Date(ms).getFullYear() !== new Date().getFullYear()) {
  const dt = new Date(ms), [y] = fromMillis(ms);
  return day(ms) + (withYear ? ' ' + gematria(y) : '') + ' (' + dt.getDate() + '/' + (dt.getMonth() + 1) + (withYear ? '/' + dt.getFullYear() : '') + ')';
}
const FIRST = Date.UTC(2024, 0, 6), ALPHA = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#%&';
const NAMES = ["פרשת שמות","פרשת וארא","פרשת בא","פרשת בשלח","פרשת יתרו","פרשת משפטים","פרשת תרומה","פרשת תצווה","פרשת כי תשא","פרשת ויקהל","פרשת פקודי","פרשת ויקרא","פרשת צו","פרשת שמיני","פרשת תזריע","פרשת מצורע","פסח","פרשת אחרי מות","פרשת קדשים","פרשת אמור","פרשת בהר","פרשת בחוקותי","פרשת במדבר","פרשת נשא","פרשת בהעלותך","פרשת שלח לך","פרשת קורח","פרשת חוקת","פרשת בלק","פרשת פינחס","פרשת מטות-מסעי","פרשת דברים","פרשת ואתחנן","פרשת עקב","פרשת ראה","פרשת שופטים","פרשת כי תצא","פרשת כי תבוא","פרשת נצבים-וילך","פרשת האזינו","יום כיפור","סוכות","פרשת בראשית","פרשת נח","פרשת לך לך","פרשת וירא","פרשת חיי שרה","פרשת תולדות","פרשת ויצא","פרשת וישלח","פרשת וישב","פרשת מקץ","פרשת ויגש","פרשת ויחי","פרשת תזריע-מצורע","פרשת אחרי מות-קדשים","פרשת בהר-בחוקותי","פרשת נצבים","פרשת וילך","פרשת ויקהל-פקודי","שבועות","פרשת חוקת-בלק","ראש השנה","פרשת מטות","פרשת מסעי"];
const WEEKS = { diaspora: '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMyNOPQzTUVWXYZabc!dffghijklmnopqr0123456789ABCDEFGHIJKLMyNOPQzTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMyNOPQzTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMyNOPQzTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCDEFHGIJKLMNOPQRST#%VWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCDEFHGIJKLMNOPQRST#%VWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMyNOPQzTUVWXYZabc!dffghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGGHIJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCGDstJuMNOPQRSTUVWXYZabvwdfghijklmno',
  israel: '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCGDstJuMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabc!dffghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabc!dffghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCDEFHGIJKLMNOPQRST#%VWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCDEFHGIJKLMNOPQRST#%VWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabc!dffghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr012345678xBCGDstJKLMNOPQRSTUVWXYZabvwdfghijklmnopqr0123456789ABCDEFGHIJKLMNOPQRST#%VWXYZabvwdfghijklmnopqr012345678xBCGDstJuMNOPQRSTUVWXYZabcdefghijklmnopqr0123456789ABCGDstJuMNOPQRSTUVWXYZabvwdfghijklmno' };
const inIsrael = () => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone === 'Asia/Jerusalem'; } catch { return false; } };
/** The portion read on the Shabbat that ends the week starting [weekStartMs] (Sunday): "פרשת וירא", "פסח". Null outside the table. */
function portion(weekStartMs, israel = inIsrael()) {
  const dt = new Date(weekStartMs), sat = Date.UTC(dt.getFullYear(), dt.getMonth(), dt.getDate() + 6);
  const i = Math.round((sat - FIRST) / 604800000), s = WEEKS[israel ? 'israel' : 'diaspora'];
  return i >= 0 && i < s.length ? NAMES[ALPHA.indexOf(s[i])] : null;
}
const api = { fromMillis, gematria, monthName, day, label, portion, isLeap };
if (typeof module !== 'undefined') module.exports = api; else root.KisHebrew = api;
})(globalThis);
