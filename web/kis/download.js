/* Android distribution only. Never reads or changes work records. */
(function (root) {
'use strict';
const FALLBACK = 'https://github.com/sholi2157-dev/Salary-calculation/releases';
function open(onClose) {
  const U = root.KisUi;
  U.sheet('android-download-sheet', (el, close) => {
    el.classList.add('android-download-sheet');
    el.innerHTML = `<div class="title-row"><h2>חישוב שכר בכיס שלך</h2><button class="icon-btn" aria-label="סגירת ההורדה" data-close>${U.ICON.close}</button></div>
      <p class="caption">האפליקציה לאנדרואיד · ללא הרשמה וללא בקשת גישה</p>
      <a class="btn primary apk-download" href="/api/download" data-testid="apk-download"><span class="ic" aria-hidden="true">${U.ICON.download}</span> הורדת האפליקציה</a>
      <p class="caption" data-release aria-live="polite">בודק גרסה…</p>
      <p class="caption" data-progress aria-live="polite"></p>
      <ol class="install-steps">
        <li><strong>מורידים ופותחים</strong><p>לוחצים על הורדה. אם הדפדפן מבקש לאשר את הקובץ מהאתר שלנו, בוחרים ״הורדה בכל זאת״ או ״שמירה״. בסיום פותחים את הקובץ מההתראה או מתיקיית ההורדות.</p></li>
        <li><strong>מאשרים התקנה מהדפדפן</strong><p>אם מופיעה בקשת הרשאה: ״הגדרות״ ← ״אפשר ממקור זה״ עבור הדפדפן שממנו הורדתם. חוזרים למסך ההתקנה.</p></li>
        <li><strong>מתקינים ומתחילים</strong><p>בוחרים ״התקנה״ ואז ״פתיחה״. אחרי ההתקנה אפשר לבטל שוב את ההרשאה ״אפשר ממקור זה״.</p></li>
      </ol>
      <details class="install-help"><summary>ההתקנה נחסמה או הופיעה אזהרה?</summary><p>הנוסח משתנה בין מכשירים. אם מוצעת סריקה של Play Protect, אפשר לבצע אותה. אם מוצגת אזהרה שהאפליקציה מזיקה או חסימה של המכשיר, עצרו ובדקו את ההודעה המדויקת. אין צורך לכבות את Play Protect או את הגנות המכשיר באופן גורף.</p><p>במכשירי Samsung ייתכן שחוסם אוטומטי מונע התקנה. בדקו את ההודעה וההגדרה המתאימה במכשיר לפני שמשנים אותה.</p></details>
      <details class="install-help"><summary>כבר התקנתי, או שאני משתמש באייפון</summary><p>כבר התקנתם? מתקינים את העדכון מעל הגרסה הקיימת, בלי להסיר אותה. אם העדכון נכשל, אל תמחקו את האפליקציה כדי לפתור זאת.</p><p>הנתונים באתר ובאפליקציה נשמרים בנפרד. להעברה משתמשים ב״גיבוי ונתונים״ בהגדרות.</p><p>באייפון משתמשים באתר; קובץ APK מיועד לאנדרואיד בלבד.</p></details>
      <p class="caption">ללא עלות · בקשות ההורדה נספרות באמצעות מזהה דפדפן אנונימי, ללא שמות או נתוני שכר</p>
      <button class="btn ghost" data-close>המשך שימוש באתר</button>`;
    el.querySelectorAll('[data-close]').forEach(b => b.onclick = close);
    const a = el.querySelector('[data-testid=apk-download]');
    a.onclick = () => { el.querySelector('[data-progress]').textContent = a.href === FALLBACK ? 'עמוד ההורדות הרשמי נפתח בדפדפן.' : 'ההורדה נפתחת בדפדפן. בסיומה פותחים את הקובץ וממשיכים לפי השלבים.'; };
    if (/iPhone|iPad|iPod/.test(navigator.userAgent)) el.querySelector('[data-progress]').textContent = 'נראה שזה מכשיר אפל. אפשר להמשיך להשתמש באתר כאן.';
    Promise.resolve().then(() => fetch('/api/android-release', { cache: 'no-store', signal: AbortSignal.timeout(7000) }))
      .then(r => { if (!r.ok) throw Error(); return r.json(); }).then(r => {
        if (!el.isConnected) return;
        // Accept older API responses with a code suffix as well as the clean version field.
        const version = typeof r?.version === 'string' && /^(\d+\.\d+(?:\.\d+)?)(?:-code[1-9]\d*)?$/.exec(r.version);
        if (r?.fallback || !version) throw Error('Release information unavailable');
        const size = typeof r.size === 'number' && Number.isFinite(r.size) && r.size > 0 ? ' · ' + (r.size / 1048576).toFixed(1) + ' MB' : '';
        el.querySelector('[data-release]').textContent = 'גרסה ' + version[1] + size;
      }).catch(() => { if (el.isConnected) {
        a.href = FALLBACK;
        el.querySelector('[data-release]').textContent = 'לא ניתן לבדוק את הגרסה כרגע · עמוד ההורדות הרשמי';
      } });
  }, { onClose });
}
root.KisDownload = { open };
})(globalThis);
