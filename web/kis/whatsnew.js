/*
 * FB23-F11: after the site has moved to a new release, one calm card on Home with the main things the user can now do
 * ("מה חדש"). Highlights only: no bug fixes, internal changes or a full changelog. The content is versioned with the
 * release: one short list per version; a version with no entry shows nothing. Keep the same wording as the Android app's
 * list (app/src/main/java/com/example/ui/kis/KisWhatsNew.kt) for the same version.
 */
(function (root) {
'use strict';
/** This site's release (bump at publish, with the Android versionName). */
const VERSION = '2.3.0';
// 2.3.0: placeholder draft; the final wording may be replaced at publish time. 3–5 short lines, features only.
const NOTES = {
  '2.3.0': [
    'תאריך עברי בכל מקום, וההיסטוריה מסודרת לפי פרשת השבוע',
    'הוספת משמרת פשוטה יותר: תעריף, ״+״ לבונוס, החזר הוצאות או הפסקה, והערה',
    'פרטי תשלום בפרופיל (Zelle, ביט או בנק) שאפשר לצרף לכל דוח',
    'שיתוף משמרת קבוצתית בשתי אפשרויות, עם העתקה בלחיצה',
    'הגדרות פשוטות יותר, והסיכומים שלך בכרטיס החשבון',
  ],
};
const KEY = 'whatsNewSeen';

/**
 * What to show, or null. [seen] is the version this browser last recorded (null before this existed); [returning] says
 * the browser was used before this release (the 2.0 launch is behind it), so a first visit never shows anything.
 */
function decide(seen, version, returning, notes = NOTES) {
  if (seen === version || (seen == null && !returning)) return null;
  const list = notes[version];
  return list && list.length ? list : null;
}
/** Read once on open. Nothing to show is recorded at once, so it never shows later. */
function pending(store, returning) {
  const list = decide(store.local[KEY] ?? null, VERSION, returning);
  if (!list) store.setLocal(KEY, VERSION, true);
  return list;
}
function seen(store) { store.setLocal(KEY, VERSION, true); }
function cardHtml(list) {
  const esc = root.KisUi.esc;
  return `<section class="card whats-new" data-testid="whats-new" aria-labelledby="whats-new-title">
    <h2 id="whats-new-title" style="margin:0;font:1.25rem/1.6rem var(--display)">מה חדש</h2>
    <p class="caption" style="margin:2px 0 8px">עודכן לגרסה ${esc(VERSION)}. הנתונים שלך נשארו כמו שהם.</p>
    <ul>${list.map(x => `<li>${esc(x)}</li>`).join('')}</ul>
    <button class="btn primary" style="width:100%;margin-top:8px" data-a="whats-new-ok" data-testid="whats-new-ok">קדימה!</button></section>`;
}
const api = { VERSION, NOTES, KEY, decide, pending, seen, cardHtml };
root.KisWhatsNew = api;
if (typeof module !== 'undefined') module.exports = api;
})(globalThis);
