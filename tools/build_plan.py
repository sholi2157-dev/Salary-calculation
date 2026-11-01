# Builds ux-audit/plan/index.html — unified implementation plan (Hebrew) + codex-prompts.md
import html, os, json
BASE = os.path.join(os.path.dirname(__file__), '..', 'plan')
os.makedirs(BASE, exist_ok=True)
E = html.escape

# (id, prio, stream, platform, title, sources, owner, phase, depends)
M = [
 ('M-01','P0','נתונים','ווב','טיוטה פרטית עוברת בין חשבונות באותו דפדפן','CW-13','אני','1','—'),
 ('M-02','P0','נתונים','ווב','רשומות ישנות בלי id מקבלות אותו מזהה: מחיקת אחת מוחקת את שתיהן','D01','אני','1','—'),
 ('M-03','P0','נתונים','ווב','שינוי שם או מחיקה של מקום מחלון ישן דורסים משמרת שנוספה בחלון אחר','D02','אני','1','M-04'),
 ('M-04','P0','נתונים','ווב','מחיקה, ייבוא, מקומות ותוכניות נכתבים בכמה כתיבות לא אטומיות ובלי rollback','D03','אני','1','החלטה D-15'),
 ('M-05','P0','שרת','שרת (משפיע על שתי הפלטפורמות)','מחיקה דרך עוזר AI מצליחה גם כשיומן השחזור לא נשמר','S-CUR-01','אני','1','—'),
 ('M-06','P0','כסף וקלט','שתיהן','ערכים משתנים בשקט: 25:00→20:00, ‏99:99→9:09, סיום לפני התחלה = 23 שעות, Infinity/שלילי/פסיק','R-01, CW-03, CW-04, CW-14','אני','2','D-5'),
 ('M-07','P1','כסף וקלט','ווב (אנדרואיד תקין)','בונוס קבוצתי שמיועד לעובדים נעלם','CW-01 (CAL-01)','אני','2','D-6'),
 ('M-08','P1','כסף וקלט','שתיהן','חוזה עיגול: הסכום בתצוגה שונה מהסכום שנשמר, ואובדן אגורה (149.985→149.98; ‏1.03 מול 1.02; ‏123.33 מול 123.46)','CAL-02, CAL-05','אני','2','D-5'),
 ('M-09','P1','כסף וקלט','שתיהן','תעריף טיימר: "40,5" נשמר כ-405, ערך לא תקין עובר בשקט לברירת המחדל, ‎-5 בווב לא עושה כלום','AND-C05, R-02','אני','2','M-06'),
 ('M-10','P1','שרת','שרת','עוזר MCP: עריכת שעה מוחקת הפסקה, ותעריף 0 לשכר חסר מתעלם מבונוס','S-CUR-06, S-CUR-07','אני','2','D-5'),
 ('M-11','P1','נתונים','ווב','קובץ תוכניות פגום נבלע ונדרס בלי עותק לשחזור','D04','אני','1','M-04'),
 ('M-12','P1','נתונים','ווב','שחזור מגיבוי: העדפות לא מיובאות, ובזמן תקלה ההוראה מפנה למסך שלא נגיש','D05, CW-15','אני','1','M-04'),
 ('M-13','P1','נתונים','שתיהן','"מחיקת כל הנתונים" משאירה טיימר, טיוטה ושם, ורשומות מהענן חוזרות','D08, D09','אני','1','D-10'),
 ('M-14','P1','נתונים','ווב + שרת','שם מקום כמו "constructor" מפיל את מסך הבית ומשבש סיכומים','CW-11 (S-CUR-05)','אני','1','—'),
 ('M-15','P1','תכנון וזמן','ווב (לבדוק באנדרואיד)','משמרות קבועות ישנות מ-62 יום לא נרשמות וגם לא מוצגות כממתינות','CW-05','אני','3','D-11'),
 ('M-16','P1','תכנון וזמן','שתיהן','"סיום הסדרה" משאיר משמרת עתידית של היום','CW-06, AND-C04','אני','3','—'),
 ('M-17','P1','תכנון וזמן','שתיהן','בדיקת חפיפה לא רואה משמרת מאתמול שחוצה חצות','CW-07, CAL-04','אני','3','—'),
 ('M-18','P1','תכנון וזמן','שתיהן','מחיקה או שינוי שם של מקום לא מעדכנים את הטיימר הפעיל','CW-09, AND-C02','אני','3','M-04'),
 ('M-19','P1','תכנון וזמן','אנדרואיד','ההיסטוריה לא מתעדכנת אחרי חצות','AND-C03','אני','3','—'),
 ('M-20','P1','תכנון וזמן','שתיהן','טווח תאריכים כולל יום נוסף ביום המעבר לשעון קיץ','CW-16, AND-C01','Codex','גל 1','—'),
 ('M-21','P1','ייבוא/יצוא','ווב','שדה שעה בגיבוי מעביר נוסחה ל-CSV, או מפיל את היצוא','S-CUR-02','Codex','גל 1','—'),
 ('M-22','P1','ייבוא/יצוא','ווב + שרת','חלונית ההורדה מציגה 2.0.3, התג "android-v2.2.1-code46" לא מזוהה, וה-fallback ישן','S-CUR-03, R-22','Codex','גל 1','—'),
 ('M-23','P1','שרת','שרת','refresh token לא קשור ללקוח, ואין rotation','S-CUR-04','אני','6','—'),
 ('M-24','P1','כסף וקלט','ווב','הוספת שם עובד לא מעדכנת את מצב כפתור השמירה','CW-12','אני','2','—'),
 ('M-25','P1','שערי בדיקה','אנדרואיד CI','7 בדיקות קיימות (NotificationJobs/GeminiCurrency) לא נבחרות בשער','QA-01','Codex','גל 1','—'),
 ('M-26','P1','שערי בדיקה','אנדרואיד CI','שער השדרוג לא יוצר נתוני 2.x עשירים לפני השדרוג','QA-02','אני','0 + 7','—'),
 ('M-27','P1','שערי בדיקה','ווב CI','השוואת fixtures מול יעד ישן חוסמת את שלב הדפדפן (לכן web-v2 נכשל על 69e430e)','QA-03','Codex','גל 1','—'),
 ('M-28','P1','עיצוב: יסודות','שתיהן','פלטה אחת לבהיר ולכהה: עומק, ניגודיות, הפרדת מותג ממקומות, הכרטיס החי, פלטת מקומות','R-03, R-05, R-06, R-07, R-08, R-32','אני','4','D-1, D-4'),
 ('M-29','P1','עיצוב: יסודות','שתיהן','טיפוגרפיה: גופן אחד, ספרות ברוחב אחיד, סולם של 8 מדרגות, woff2, ₪ אחיד','R-09, R-10, R-37, R-39, R-40','אני','4','D-2'),
 ('M-30','P1','עיצוב: רכיבים','שתיהן','"נבחר" אחד, 3 גדלי כפתור, 4 רדיוסים, כפתור מחיקה זהה, סט אייקונים אחד, מצב מושבת','R-04, R-23, R-25, R-26, R-27, R-41, R-46','אני','5','D-3'),
 ('M-31','P1','התאמה','ווב','להעביר לווב את שינויי 2.2.3: כרטיס חי עם שעון גדול, הגדרות לפי קטגוריות, התראות לפי סוג','R-11','אני','5','M-28, M-30'),
 ('M-32','P1','RTL','אנדרואיד','טווח שעות מוצג הפוך בשורות התכנון','R-12','אני','5','—'),
 ('M-33','P1','נגישות','ווב','רוחב 320px, מלכודת פוקוס, ציור מחדש ששומר פוקוס, סיכום חודשי, סמנטיקה, מטרות מגע, rem','R-13..R-17, R-42, R-44, R-45','אני','5','M-30'),
 ('M-34','P1','נגישות','אנדרואיד','מטרות מגע של 48dp, הגדלת גופן בלי חיתוך, סמנטיקה ל-TalkBack','R-16, R-17, R-18','אני','5','M-30'),
 ('M-35','P1','מסכים','ווב','שלושה כפתורי סגירה בחיפוש','R-19','אני','5','—'),
 ('M-36','P1','תוכן','שתיהן','פורמט אחד למשך (ש:דד) ולתאריך','R-20','אני','5','D-9'),
 ('M-37','P1','רכיבים','שתיהן','משוב אחיד: הודעה אחת עם ביטול, בלי Toast של המערכת, ביטול גם לסימון מרובה כשולם','R-21','אני','5','M-30'),
 ('M-38','P1','תוכן','שתיהן','שפה אחידה: פנייה ניטרלית אחת, מונחים ופורמט תאריך','R-24','אני','5','D-12'),
 ('M-39','P2','כסף וקלט','שתיהן','רווח מקבוצה לא מתאזן עם חוב המעסיק והתשלום לעובד (‏0.33 מול 0.34)','CAL-03','אני','2','D-5'),
 ('M-40','P2','מסכים','ווב','כפתור "השלמת תעריף" בבית מושפע מסינון ישן בהיסטוריה','CW-08','אני','5','—'),
 ('M-41','P2','כסף וקלט','ווב','תאריך סיום סדרה לפני ההתחלה מציג תצוגה מקדימה ושומר סדרה ריקה','CW-10','אני','2','—'),
 ('M-42','P2','ייבוא/יצוא','ווב','תוכנית מיובאת יכולה להפנות למקום שלא קיים','D06','אני','6','—'),
 ('M-43','P2','ייבוא/יצוא','ווב','CSV מעגל שעות, וייבוא חוזר שלו יוצר כפילות','D07','אני','6','D-13'),
 ('M-44','P2','ייבוא/יצוא','אנדרואיד','מונה הייבוא מדווח על יותר הוספות ממה שנוספו','D10','Codex','גל 1','—'),
 ('M-45','P2','תכנון וזמן','אנדרואיד','בטיימר יש פער של שנייה או אגורה בין התצוגה לשמירה','AND-C07','אני','3','D-5'),
 ('M-46','P2','מסכים','שתיהן','מסכים: כפילויות בבית, פרטי קבוצה, הגדרות, מקומות, עורך, מצבים ריקים, מחשב, מיקום ההודעה','R-28..R-31, R-33..R-36','אני','5','M-30'),
 ('M-47','P2','כסף וקלט','ווב','לבדוק: שעות בסיכום החודשי (46) מול שורת הסיכום (49.25)','R-43','אני','2','—'),
 ('M-48','P2','החלטת מוצר','שתיהן','קבלנות קבוצתית שבה החלק האישי 0 נחסמת בעורך','AND-C06','אני','2','D-7'),
]

def mrows():
    out=[]
    for i,p,s,pl,t,src,o,ph,dep in M:
        oc = 'own-me' if o=='אני' else 'own-cx'
        out.append(f'<tr id="{i}"><td class="mono">{i}</td><td><span class="pri {p}">{p}</span></td><td>{E(s)}</td><td>{E(pl)}</td><td>{E(t)}</td><td class="mono small">{E(src)}</td><td><span class="own {oc}">{E(o)}</span></td><td class="mono">{E(ph)}</td><td class="small">{E(dep)}</td></tr>')
    return '\n'.join(out)
cnt = lambda p: sum(1 for m in M if m[1]==p)
mine = sum(1 for m in M if m[6]=='אני'); cx = len(M)-mine

DEC = [
 ('D-1','פלטת "Kis 2" לבהיר ולכהה','הבהיר כמעט לא משתנה. הכהה נבנה מאותה משפחה כחלחלה (גוון 264): רקע #0F1216, כרטיס #181B20, טקסט #EDF0F6. ‏181 זוגות ניגודיות נבדקו ונמצאו 0 כשלים. צבעי המקומות מכווננים בתוך המשפחה של כל צבע, בלי המרת נתונים.','לאשר כמו שהיא, ולבדוק בעין על הטלפון 3 צבעים: ירוק, סגול כהה ובורדו כהה','דוח העיצוב §3'),
 ('D-2','טיפוגרפיה: Heebo בלבד','גופן אחד, ספרות ברוחב אחיד, ‏₪ מאותו עיצוב כמו הספרות. סולם של 8 מדרגות. במקום כ-1MB, כ-40KB woff2 בווב וכ-228KB באנדרואיד. מחייב לעדכן את AGENTS.md ואת preservation-and-sync.md, שקובעים היום Rubik + Secular One.','לאשר. החלופה השנייה: IBM Plex Sans Hebrew','דוח העיצוב §4'),
 ('D-3','מערכת רכיבים','שני דפוסי "נבחר" בלבד (אריח/צ׳יפ, ומקטע/לשונית), 3 גדלי כפתור (56/48/40), 4 רדיוסים (8/12/16/24 ועוד מלא), מתג דולק בצבע הדיו, כפתור מחיקה מלא וזהה בשתי הפלטפורמות, סט אייקוני SVG אחד, ושימוש באינדיגו רק לרגעי מותג.','לאשר','דוח העיצוב §5'),
 ('D-4','כפתור ראשי במצב כהה','#EDF0F6 (כמעט לבן, ניגודיות 16) או אינדיגו #9398FF עם טקסט כהה (6.81).','להתחיל עם #EDF0F6 ולהחליט אחרי מבט בטלפון','—'),
 ('D-5','חוזה עיגול כספי','לחשב בעשרוני מדויק (אגורות שלמות), לעגל חצי כלפי מעלה לכל משמרת, ולהשתמש באותה פונקציה לתצוגה ולשמירה בשתי הפלטפורמות. סכומים שכבר נשמרו לא מחושבים מחדש לעולם, גם כשהם "לא מתאימים" לחוק החדש.','לאשר','CAL-02, CAL-03, CAL-05'),
 ('D-6','חלוקת בונוס קבוצתי','כשהבונוס מיועד ל"העובדים" או ל"כולם": (א) כל עובד מקבל את מלוא הסכום שהוזן, או (ב) הסכום מתחלק שווה בין העובדים. כדאי להתאים להתנהגות של withBonusTarget באנדרואיד, שהיום עובדת.','להעתיק את התנהגות האנדרואיד לווב (אחדות בין הפלטפורמות)','CW-01'),
 ('D-7','קבלנות קבוצתית עם חלק אישי 0','לאפשר למי שרק מארגן צוות לשמור משמרת עם חלק אישי 0 (הקוד באנדרואיד מתעד את זה כמותר).','לאפשר, עם ניסוח ברור','AND-C06'),
 ('D-8','שאלות מוצר מ-Codex','ביום מעבר לשעון קיץ: לפי שעון הקיר או לפי הזמן שעבר בפועל? האם עמלת צוות נכללת בסיכום האישי? משמרת עתידית מאוחר יותר היום: לספור אותה בהיסטוריה או לא?','שעון קיר; עמלה כן, עם שורה מפורשת; עתידית = מתוכננת עד שעת ההתחלה','REPORT §דיוק'),
 ('D-9','פורמט משך ותאריך','משך ש:דד בכל מקום ("2:30 שעות", ולא "2.5"). תאריך "יום ד׳ 4.11", ובשנה אחרת "4.11.25". ‏CSV שומר גם עמודת שעות עשרונית.','לאשר','R-20, R-24'),
 ('D-10','משמעות "מחיקת כל הנתונים"','למחוק גם טיימר פעיל, טיוטות ושם פרופיל. בחשבון מחובר: פעולת מחיקה ברמת החשבון בצד השרת (epoch), כך שגם רשומות שעוד לא הגיעו למכשיר יימחקו. להציג הצלחה רק אחרי אישור מהשרת.','לאשר','D08, D09'),
 ('D-11','משמרות קבועות אחרי היעדרות ארוכה','אחרי יותר מ-62 יום: להציג את הימים כממתינים לאישור במקום לרשום אותם אוטומטית.','להציג כממתינים (בטוח יותר מרישום אוטומטי)','CW-05'),
 ('D-12','שפה','פנייה ניטרלית אחת בכל האפליקציה ("מוסיפים", "בוחרים"), כמו ברוב הטקסטים היום, ולא "הזן"/"הוסף". "דוח" בלי גרשיים. "לא שולם" ולא "ממתין".','לאשר','R-24'),
 ('D-13','מעמד ה-CSV','לסמן אותו בממשק כ"טבלה לאקסל", לא כגיבוי. ייבוא חוזר של CSV יזהה כפילויות.','לאשר','D07'),
 ('D-14','מספר גרסה','2.3.0, קוד 49 (מעל ה-RC 2.2.3 קוד 48). זו הגרסה המאוחדת היחידה.','לאשר','—'),
 ('D-15','אחסון בווב','לא לעבור ל-IndexedDB בגרסה הזו. במקום זה: יחידת commit אחת לכל פעולה (snapshot יחיד או journal ב-localStorage, עם rollback), כדי לשמור על פורמט הנתונים הקיים ותאימות לאחור.','לאשר (פחות סיכון)','D03'),
 ('D-16','ענפים והרשאות','ליצור ענפי שחרור ותגי גיבוי (פירוט בהמשך) ולדחוף אליהם. דחיפה לענף שאינו ענף הסשן דורשת את אישורך המפורש.','לאשר','—'),
 ('D-17','עדכון מסמכי הפרויקט','לעדכן את AGENTS.md (העדיפות כבר אינה "אנדרואיד בלבד", והגופנים) ואת docs/current-state.md, שעדיין מתאר את 2.0.8.','לאשר','—'),
]
def drows():
    return '\n'.join(f'<tr><td class="mono">{a}</td><td><b>{E(b)}</b><div class="det">{E(c)}</div></td><td>{E(d)}</td><td class="small">{E(e)}</td></tr>' for a,b,c,d,e in DEC)

PROMPTS = [
 ('C-1','QA-03 — שער הבדיקה של הווב','release/2.3-web',
 """Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first.
Base branch: release/2.3-web (do not use main). Create branch: codex/qa03-web-fixture-gate.

Problem (audit finding QA-03): the "Web 2.0 (כיס) checks" workflow (.github/workflows/web-v2.yml) compares the website's shared expected-result fixtures against an older public Android target, so on the newest web commit the fixture step fails before the browser test stage runs (run 40990885116 failed on 69e430e).

Task:
1. Make the fixture comparison target the Android release branch paired with this web branch: release/2.3-android. Keep a separate, clearly named step for backward compatibility against the published tag android-v2.2.1-code46 that reports the difference without blocking the browser stage.
2. The browser stage must run even when the backward-compatibility comparison reports a difference. Do not delete, skip or weaken any existing test.
3. Touch only .github/workflows/web-v2.yml (and a helper script under scripts/ only if strictly needed).

Acceptance: run the workflow with workflow_dispatch on your branch. Fixture comparison against release/2.3-android passes, the browser stage runs, and 119/119 node tests pass. Report the run URL and result.
Constraints: no application code changes, no version bump, no deploy, no merge. Open a PR into release/2.3-web and stop. Reply in Hebrew with the commit SHA, the PR link and the CI result."""),
 ('C-2','QA-01 — בדיקות אנדרואיד שלא רצות','release/2.3-android',
 """Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first (permanent package com.aistudio.worktracker.qztvdw.distribution; never touch signing material).
Base branch: release/2.3-android. Create branch: codex/qa01-android-test-filters.

Problem (audit finding QA-01): seven existing unit tests (NotificationJobs* and GeminiCurrency* test classes under app/src/test) are never selected by the --tests filters in .github/workflows/android-v2.yml and the "Consolidated regression" step of .github/workflows/android-v2-rc.yml.

Task:
1. List the exact test classes that exist but are not matched by any filter in either workflow.
2. Add them to both workflows' filters (Debug and Release logic tests, the same way the other logic tests are listed).
3. If any of them fail, do NOT change the tests or the app. Report the failure output instead.
4. Touch only those two workflow files.

Acceptance: workflow_dispatch of android-v2.yml on your branch succeeds, and scripts/summarize-android-tests.py shows the higher test count. Report the before/after counts.
Constraints: no app code changes, no signing, no RC, no publishing, no merge. Open a PR into release/2.3-android and stop. Reply in Hebrew with the SHA, PR, run URL and test counts."""),
 ('C-3','S-CUR-03 + R-22 — גרסת ההורדה','release/2.3-web',
 """Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first.
Base branch: release/2.3-web. Create branch: codex/download-version-label.

Problem (audit findings S-CUR-03 and R-22):
- web/kis/download.js shows the hardcoded "גרסה 2.0.3" and falls back to the 2.0.3 APK (lines 4, 12, 30).
- lib/server/android-release.cjs falls back to 2.0.5 and returns the version from the tag by stripping only "android-v", so the current tag "android-v2.2.1-code46" becomes "2.2.1-code46". download.js:28 then rejects it (it accepts only digits and dots), so the label never updates.

Task:
1. In android-release.cjs, parse tags of the form android-v<semver>[-code<N>] into {version:'2.2.1', code:46}. Keep the response shape backward compatible (version stays a plain semver string; code is an optional new field).
2. Remove the hardcoded old version from download.js. While loading, show a neutral label ("בודק גרסה…"). On failure, show "הגרסה האחרונה" and link to the stable releases/latest page, never to an old APK. Keep existing Hebrew wording style and existing CSS classes (no visual redesign; another task owns styling).
3. Add node tests in web-tests/ for tag parsing (with and without -code, malformed tags) and for the failure label.

Acceptance: all existing node tests pass, the new tests pass, and the web-v2 workflow succeeds.
Constraints: touch only web/kis/download.js, lib/server/android-release.cjs and new or updated tests. No deploy, no merge. Open a PR into release/2.3-web. Reply in Hebrew with the SHA, PR, tests and CI result."""),
 ('C-4','S-CUR-02 — שדות זמן בגיבוי וב-CSV','release/2.3-web',
 """Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first. Use synthetic data only.
Base branch: release/2.3-web. Create branch: codex/import-time-fields.

Problem (audit finding S-CUR-02):
- web/transfer.js (around lines 98–105) validates money fields but passes startTime/endTime through without checking type or format.
- web/v2/share.js (around lines 135–145) neutralizes spreadsheet formulas in name, note and currency but not in time cells, and assumes every cell is a string.
- A backup with startTime:'=1+1' exports a live formula. A backup with startTime:123 makes the export throw "s.includes is not a function".

Task:
1. On import, accept startTime/endTime only as null or a valid "HH:mm" string (00:00–23:59). Reject an invalid row with a clear Hebrew message in the existing import check summary. Never silently change the stored amount or hours.
2. In CSV/TSV export, coerce every cell to a string and apply the existing formula neutralization to every text cell, including times.
3. Keep share.js output for valid data byte-identical (the Android-shared fixtures in web-tests/fixtures/v2-share-fixtures.json must still match).
4. Add node tests: formula in a time field, numeric time, valid times unchanged, round trip of a valid backup unchanged.

Acceptance: 119 existing node tests plus the new tests pass, and the fixture comparison is unchanged.
Constraints: touch only web/transfer.js, web/v2/share.js and tests. No UI redesign, no deploy, no merge. Open a PR into release/2.3-web. Reply in Hebrew with the SHA, PR and test results."""),
 ('C-5','CW-16 + AND-C01 — גבול טווח ביום שעון קיץ','שני הענפים (שני PR נפרדים)',
 """Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first. Synthetic data only.

Problem (audit finding CW-16/AND-C01): the history date-range filter computes the end of the range by adding a fixed 24h in milliseconds. On the day clocks change (e.g. 27/03/2026 in Asia/Jerusalem), filtering only 27/03 also includes 28/03.
- Web: web/kis/history.js around line 33.
- Android: the equivalent range computation used by the history filter (search for the code path that builds the range end from the selected end date in app/src/main/java/com/example/ui/kis/KisHistoryLogic.kt or KisHistory.kt).

Task (two separate branches and PRs):
A) Base release/2.3-web, branch codex/dst-range-web: compute "the next calendar day at 00:00 local" with setDate(+1) instead of +86400000. Add node tests run with TZ=Asia/Jerusalem covering both DST transitions, month/year ends, and the last day included / next day excluded.
B) Base release/2.3-android, branch codex/dst-range-android: same fix with java.time (LocalDate.plusDays(1).atStartOfDay(zone)), and a JVM unit test with zone Asia/Jerusalem for the same cases. Add the new test class to the workflow test filters only if it isn't matched by an existing pattern.

Constraints: change only the range-boundary computation and tests. Do not touch UI layout or styling (another task owns it). No version bump, no RC, no merge. Open both PRs. Reply in Hebrew with both SHAs, PRs and test results."""),
 ('C-6','D10 — מונה הייבוא באנדרואיד','release/2.3-android',
 """Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first. Synthetic data only.
Base branch: release/2.3-android. Create branch: codex/import-count.

Problem (audit finding D10): app/src/main/java/com/example/data/WorkDao.kt (around lines 183–216) can report more imported records than were actually inserted. For example, a row with an existing seriesKey and different hours is correctly not inserted, but it is still counted.

Task:
1. Count successful inserts separately from skipped or conflicting rows and return both.
2. Show the user "נוספו X · דולגו Y" (keep the existing message style; if Y=0 show only the added count).
3. Never change existing records or amounts.
4. Add a JVM/Robolectric unit test: repeated import of the same backup (adds 0), same seriesKey with different hours (skipped), and existing amounts unchanged.

Acceptance: android-v2.yml (workflow_dispatch) is green with the new test included.
Constraints: touch only WorkDao.kt, the minimal caller that shows the message, and tests. No Room schema change, no version bump, no signing, no merge. Open a PR into release/2.3-android. Reply in Hebrew with the SHA, PR and results."""),
]
def prompt_blocks():
    out=[]
    for i,(cid,title,base,text) in enumerate(PROMPTS):
        out.append(f'''<div class="prompt"><div class="ph"><b>{cid} · {E(title)}</b><span class="small">בסיס: <span class="mono">{E(base)}</span></span><button class="copy" type="button" data-t="p{i}">העתקה</button></div><pre id="p{i}" dir="ltr">{E(text)}</pre></div>''')
    return '\n'.join(out)

UPG = [
 ('RC16 (1.x) → 2.3.0','השער הקיים, מורחב','3 רשומות ישנות, קטגוריות, מטבע ברירת מחדל','כל הרשומות זהות ביט לביט בסכום ובשעות. אין קריסה. החתימה והחבילה זהות'),
 ('2.2.1 ציבורית (קוד 46) → 2.3.0','חדש (QA-02)','נתוני 2.x עשירים שנוצרים בתוך 2.2.1: קבלנות, צוות, בונוס, החזר הוצאות, שכר חסר, סדרות וחריגים, מחיקות, סכום מיובא חריג, ₪ ו-$, צבעים וסדר מקומות, שם, טיימר פעיל','השוואת snapshot של DB והעדפות לפני ואחרי (install -r). הטיימר נסגר פעם אחת. דוחות ו-round-trip של הגיבוי זהים'),
 ('2.2.3 RC (קוד 48) → 2.3.0','חדש','כמו השורה הקודמת, למקרה שה-RC כבר מותקן אצלך','כנ״ל, ובנוסף הגדרות ההתראות לפי סוג נשמרות'),
 ('2.0.8 (קוד 41) → 2.3.0','חדש, קצר','דגימת נתונים','קפיצה של כמה גרסאות בלי איבוד'),
 ('התקנה נקייה 2.3.0','הקיים','—','מסך פתיחה, הדרכה, הרשאת התראות, אין קריסה'),
 ('ווב: נתונים מהאתר החי (da7a7c3) → build חדש','חדש','localStorage אמיתי-סינתטי: רשומות legacy בלי id, user_work_shifts, תוכניות, טיוטה, שני חשבונות','כל המפתחות נשמרים, עותק checkpoint נוצר, ה-service worker מתעדכן, וחזרה ל-build הישן קוראת את הנתונים'),
]
def upgrows():
    return '\n'.join(f'<tr><td><b>{E(a)}</b></td><td>{E(b)}</td><td class="small">{E(c)}</td><td class="small">{E(d)}</td></tr>' for a,b,c,d in UPG)

page = f'''<!DOCTYPE html>
<html lang="he" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>תוכנית שחרור מאוחד 2.3</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Heebo:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
/* One reading column; register and prompt blocks scroll in place. Tokens = the proposed "Kis 2" palette. */
:root {{
  --bg:#F1F3F8; --surface:#FFFFFF; --fill:#E4E8F0; --line:#D2D8E3; --ink:#1B2335; --ink2:#5A6478; --accent:#5145E8;
  --p0:#BD2F28; --p0s:#FEE7E4; --p1:#9A5201; --p1s:#FFEBC7; --p2:#1B65B9; --p2s:#E2EEFE; --ok:#007453; --oks:#D5F6E6;
  --font:'Heebo', 'Assistant', system-ui, sans-serif; color-scheme: light;
}}
@media (prefers-color-scheme: dark) {{ :root:not([data-theme="light"]) {{
  --bg:#0F1216; --surface:#181B20; --fill:#282C34; --line:#32373F; --ink:#EDF0F6; --ink2:#B3B9C5; --accent:#A2A8FE;
  --p0:#FF9183; --p0s:#47211E; --p1:#F6B75C; --p1s:#3E2B0F; --p2:#7EBBFA; --p2s:#192F46; --ok:#45D09E; --oks:#103627; color-scheme: dark; }} }}
:root[data-theme="dark"] {{
  --bg:#0F1216; --surface:#181B20; --fill:#282C34; --line:#32373F; --ink:#EDF0F6; --ink2:#B3B9C5; --accent:#A2A8FE;
  --p0:#FF9183; --p0s:#47211E; --p1:#F6B75C; --p1s:#3E2B0F; --p2:#7EBBFA; --p2s:#192F46; --ok:#45D09E; --oks:#103627; color-scheme: dark; }}
* {{ box-sizing:border-box; }}
html,body {{ margin:0; background:var(--bg); color:var(--ink); font:400 16px/1.6 var(--font); }}
.wrap {{ max-width:1080px; margin:0 auto; padding:24px 16px 80px; }}
header.top {{ display:grid; gap:6px; padding:8px 0 20px; border-bottom:1px solid var(--line); }}
h1 {{ font-size:34px; line-height:1.15; margin:0; font-weight:800; letter-spacing:-.01em; text-wrap:balance; }}
h2 {{ font-size:24px; line-height:1.25; margin:44px 0 12px; font-weight:700; display:flex; gap:10px; align-items:baseline; text-wrap:balance; }}
h2 .n {{ color:var(--accent); font-size:18px; font-weight:600; font-variant-numeric:tabular-nums; }}
h3 {{ font-size:19px; margin:24px 0 8px; font-weight:700; }}
p, li {{ max-width:74ch; }}
.meta, .small {{ color:var(--ink2); font-size:14px; }}
.small {{ font-size:13px; }}
.mono {{ font-variant-numeric:tabular-nums; direction:ltr; unicode-bidi:isolate; font-size:13px; }}
nav.toc {{ display:flex; flex-wrap:wrap; gap:6px 8px; margin-top:16px; }}
nav.toc a {{ color:var(--ink); text-decoration:none; background:var(--surface); border:1px solid var(--line); border-radius:10px; padding:6px 10px; font-size:14px; }}
a:focus-visible, button:focus-visible {{ outline:2px solid var(--accent); outline-offset:2px; }}
.cards {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:12px; margin:16px 0; }}
.card {{ background:var(--surface); border:1px solid var(--line); border-radius:14px; padding:14px 16px; }}
.card .k {{ font-size:13px; color:var(--ink2); }} .card .v {{ font-size:28px; font-weight:700; font-variant-numeric:tabular-nums; line-height:1.2; }}
.tbl {{ overflow-x:auto; border:1px solid var(--line); border-radius:14px; background:var(--surface); margin:12px 0; }}
table {{ border-collapse:collapse; width:100%; font-size:14px; }}
th, td {{ text-align:start; vertical-align:top; padding:10px 12px; border-bottom:1px solid var(--line); }}
th {{ font-weight:600; font-size:13px; color:var(--ink2); background:var(--fill); }}
tr:last-child td {{ border-bottom:0; }}
.det {{ font-size:13px; color:var(--ink2); margin-top:4px; max-width:62ch; }}
.reg td:nth-child(5) {{ min-width:260px; }}
.pri, .own {{ display:inline-block; font-size:12px; font-weight:700; padding:2px 8px; border-radius:999px; white-space:nowrap; }}
.pri.P0 {{ color:var(--p0); background:var(--p0s); }} .pri.P1 {{ color:var(--p1); background:var(--p1s); }} .pri.P2 {{ color:var(--p2); background:var(--p2s); }}
.own-me {{ color:var(--ink); background:var(--fill); }} .own-cx {{ color:var(--ok); background:var(--oks); }}
.note {{ border-inline-start:4px solid var(--accent); background:var(--surface); padding:12px 16px; border-radius:10px; margin:14px 0; }}
.warn {{ border-inline-start-color:var(--p1); }} .crit {{ border-inline-start-color:var(--p0); }}
.phase {{ display:grid; grid-template-columns:120px 1fr; gap:14px; padding:16px 0; border-bottom:1px solid var(--line); }}
.phase .when {{ font-weight:700; color:var(--accent); }}
.phase .gate {{ font-size:13px; color:var(--ok); margin-top:6px; }}
.prompt {{ background:var(--surface); border:1px solid var(--line); border-radius:14px; margin:12px 0; overflow:hidden; }}
.ph {{ display:flex; flex-wrap:wrap; gap:8px 12px; align-items:center; padding:10px 14px; border-bottom:1px solid var(--line); }}
.ph b {{ flex:1 1 220px; }}
.copy {{ font:600 14px var(--font); color:var(--ink); background:var(--fill); border:0; border-radius:10px; min-height:40px; padding:0 14px; cursor:pointer; }}
pre {{ margin:0; padding:14px; font:13px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace; white-space:pre-wrap; overflow-x:auto; text-align:left; }}
ul.tight li {{ margin:4px 0; }}
code {{ font-size:13px; background:var(--fill); padding:1px 6px; border-radius:6px; direction:ltr; unicode-bidi:isolate; }}
@media (max-width:560px) {{ h1 {{ font-size:27px; }} .phase {{ grid-template-columns:1fr; gap:4px; }} }}
</style>
</head>
<body>
<div class="wrap">
<header class="top">
  <h1>תוכנית יישום מאוחדת לשחרור 2.3.0</h1>
  <div class="meta">1 בנובמבר 2026 · מאחדת את הביקורת הטכנית של Codex/Astra (37 ממצאים, שאלת מוצר אחת ו-3 שערי בדיקה) ואת ביקורת העיצוב והחוויה (46 ממצאים). שתי הביקורות בדקו בדיוק את אותם commits: אנדרואיד <span class="mono">fd5a2b6</span>, ווב <span class="mono">69e430e</span>. לא שונה קוד, ולא בוצע commit, push או פרסום.</div>
  <nav class="toc" aria-label="תוכן">
    <a href="#sum">סיכום</a><a href="#register">רשימה מאוחדת</a><a href="#seq">רצף ותלויות</a><a href="#decisions">החלטות לאישור</a>
    <a href="#codex">משימות ל-Codex</a><a href="#tests">בדיקות ושדרוג</a><a href="#branches">ענפים וחזרה</a><a href="#risks">סיכונים</a>
  </nav>
</header>

<h2 id="sum"><span class="n">1</span>תמונת מצב</h2>
<p>שתי הביקורות משלימות זו את זו כמעט בלי חפיפה. Codex מצא בעיקר כשלים בשכבת הנתונים, בחישובים ובשרת. אני מצאתי בעיקר בעיות בשכבת התצוגה, בחוויה ובנגישות. החפיפות שאוחדו: אימות קלט (R-01/R-02 עם CW-03/04/14 ו-AND-C05), גרסת ההורדה (R-22 עם S-CUR-03), ושני חישובי עיגול (CAL-02 עם CAL-05). שתי הביקורות מסכימות במסקנה: <b>לא לפרסם עדיין</b>. את כל הממצאים אפשר לתקן במסגרת שחרור אחד.</p>
<div class="cards">
  <div class="card"><div class="k">פריטים ברשימה המאוחדת</div><div class="v">{len(M)}</div><div class="small">מתוך 87 ממצאים גולמיים, אחרי איחוד כפילויות. ממצאי עיצוב קטנים אוגדו למשפחות</div></div>
  <div class="card"><div class="k">P0 / P1 / P2</div><div class="v">{cnt('P0')} / {cnt('P1')} / {cnt('P2')}</div><div class="small">כל P0 עוסקים בנתונים ובכסף</div></div>
  <div class="card"><div class="k">אצלי / אצל Codex</div><div class="v">{mine} / {cx}</div><div class="small">ל-Codex רק משימות קטנות, מבודדות ובקבצים נפרדים</div></div>
  <div class="card"><div class="k">החלטות שמחכות לך</div><div class="v">{len(DEC)}</div><div class="small">D-1 עד D-4 עיצוב, D-5 עד D-13 מוצר וכסף, D-14 עד D-17 תהליך</div></div>
</div>
<div class="note crit"><b>העיקרון המנחה:</b> נתונים וכסף קודם, עיצוב אחר כך. סכום שכבר נשמר לא מחושב מחדש לעולם. כל שינוי בפורמט האחסון יהיה תוספתי ותואם לאחור. כל שינוי בדרך אחסון או ניווט ייבדק בשדרוג אמיתי מהגרסה הקודמת.</div>
<div class="note warn"><b>מה מתעדכן ב-AGENTS.md:</b> הקובץ עדיין קובע "אנדרואיד בלבד, לא לגעת בווב/Firebase/חשבונות", ו-docs/current-state.md מתאר את 2.0.8. ההוראה הנוכחית שלך (שחרור מאוחד של שניהם) גוברת. אעדכן את המסמכים בשלב 0, באישורך (D-17).</div>

<h2 id="register"><span class="n">2</span>רשימת ממצאים מאוחדת</h2>
<p class="small">"מקורות" מפנה למזהים בשתי הביקורות: CW, D, S-CUR, CAL, AND, QA הם של Codex, ו-R של ביקורת העיצוב. המשפחות שאוחדו (למשל M-30 או M-46) כוללות את כל תתי-הממצאים מהדוח המקורי. הפירוט המלא נשאר בשני הדוחות.</p>
<div class="tbl"><table class="reg">
<thead><tr><th>מזהה</th><th>עדיפות</th><th>זרם</th><th>פלטפורמה</th><th>נושא</th><th>מקורות</th><th>מבצע</th><th>שלב</th><th>תלות</th></tr></thead>
<tbody>{mrows()}</tbody></table></div>

<h2 id="seq"><span class="n">3</span>רצף, תלויות ותנאי מעבר</h2>
<p>הסדר נקבע לפי שלושה כללים. (1) שכבת הנתונים מתוקנת לפני כל שינוי תצוגה בקבצים המשותפים (editor.js, settings.js, store.js), כדי שהשינויים לא יתערבבו ויהיה אפשר לבדוק כל אחד בנפרד. (2) חוזה העיגול מוכרע לפני שנוגעים בחישוב. (3) הטוקנים לפני הרכיבים, והרכיבים לפני המסכים. כל שלב נגמר ב-PR נפרד לענף השחרור, עם בדיקות ירוקות ונקודת חזרה.</p>
<div class="phase"><div class="when">שלב 0<br><span class="small">הכנה</span></div><div>
  אישור ההחלטות, ענפים ותגי גיבוי, ועדכון AGENTS.md ו-current-state.<br>
  <b>מערך נתונים סינתטי "מוזהב"</b> שמשמש את כל השלבים:
  <ul class="tight">
    <li>ווב: מחולל localStorage, כולל רשומות legacy בלי id ושני חשבונות.</li>
    <li>אנדרואיד: גיבוי v3 עשיר ומחולל בתוך instrumentation.</li>
  </ul>
  <b>הקלטת ההתנהגות הנוכחית</b> כבדיקות snapshot, כדי לזהות שינוי לא מכוון.<br>
  <b>Codex מתחיל גל 1</b>, במקביל ובקבצים שאני לא נוגע בהם.<br>
  שער השדרוג העשיר (M-26) נבנה כבר עכשיו, כי הוא מגן על כל השלבים.
  <div class="gate">תנאי מעבר: ענפים קיימים. ‏119/119 בדיקות ווב וכל בדיקות האנדרואיד ירוקות על ענפי השחרור, בלי שום שינוי בקוד.</div></div></div>
<div class="phase"><div class="when">שלב 1<br><span class="small">שלמות נתונים (P0)</span></div><div>
  M-04 קודם, כי הוא תשתית לשאר: יחידת commit אחת לכל פעולה, עם rollback. אחריו M-02, M-03, M-11, M-12, M-14, M-01, M-13 ו-M-05 (שרת).<br>
  לכל ממצא: בדיקה שמשחזרת את הכשל, ואז התיקון.<br>
  לכל נקודת כתיבה: הזרקת כשל בכל שלב בדרך.
  <div class="gate">תנאי מעבר: כל רגרסיות D01–D04 ו-CW-13 של Codex עוברות. ‏round-trip של גיבוי זהה. נתוני האתר החי נטענים בלי שינוי.</div></div></div>
<div class="phase"><div class="when">שלב 2<br><span class="small">כסף וקלט</span></div><div>
  פונקציית חישוב אחת לתצוגה ולשמירה (M-08, M-39), ו-parser אחד למספרים ולשעות בשתי הפלטפורמות (M-06, M-09).<br>
  אחר כך M-07, M-10, M-24, M-41, M-47 ו-M-48.
  <div class="gate">תנאי מעבר: האורקל של Codex (20,160 צירופים) עם 0 סטיות לפי החוזה שאושר. fixtures משותפים זהים בין הפלטפורמות. בדיקה שמוכיחה שאף סכום היסטורי לא השתנה.</div></div></div>
<div class="phase"><div class="when">שלב 3<br><span class="small">תכנון, זמן וטיימר</span></div><div>
  M-15 עד M-19 ו-M-45. ‏M-20 מגיע מ-Codex.
  <div class="gate">תנאי מעבר: בדיקות חצות, שעון קיץ (שני הכיוונים), 63, 90 ו-365 יום, ומחיקה בזמן שטיימר רץ.</div></div></div>
<div class="phase"><div class="when">שלב 4<br><span class="small">יסודות העיצוב</span></div><div>
  קובץ טוקנים אחד (JSON) שממנו נוצרים kis.css ו-KisTheme.kt, עם הפלטה, הטיפוגרפיה, ‏tnum ו-woff2 (M-28, M-29).<br>
  אפשר להתחיל במקביל לשלבים 2–3, כי הקבצים נפרדים.
  <div class="gate">תנאי מעבר: ניגודיות של כל זוג טוקנים ≥ 4.5/3. צילומי Playwright ו-Roborazzi בבהיר ובכהה נסקרים ומאושרים על ידך לפני שממשיכים.</div></div></div>
<div class="phase"><div class="when">שלב 5<br><span class="small">רכיבים, מסכים, נגישות</span></div><div>
  ב-M-30 קודם (רכיבים). אחר כך M-31, שמעביר לווב את שינויי 2.2.3. אחר כך המסכים, השפה והפורמטים: M-32, M-35 עד M-38, M-40 ו-M-46. בסוף הנגישות: M-33 ו-M-34.
  <div class="gate">תנאי מעבר: axe ללא הפרות serious. ‏320–1440 בלי גלילה אופקית. גודל גופן 1.3 ו-2.0 באנדרואיד בלי חיתוך. כל יכולות 2.2.3 שנבדקו עדיין קיימות.</div></div></div>
<div class="phase"><div class="when">שלב 6<br><span class="small">ייבוא, יצוא, שרת</span></div><div>
  M-42, M-43 ו-M-23. משימות C-3 ו-C-4 של Codex מתמזגות כאן.
  <div class="gate">תנאי מעבר: round-trip בין אנדרואיד לווב, ובדיקות OAuth מבודדות.</div></div></div>
<div class="phase"><div class="when">שלב 7<br><span class="small">אימות מאוחד</span></div><div>
  סט בדיקות אחד מלא, RC חתום 2.3.0 (קוד 49), מטריצת השדרוג (סעיף 6), ו-Preview של האתר ב-Vercel.<br>
  בסוף: בדיקה שלך על הטלפון, תיקון רק של מה שתמצא, ופרסום באישור נפרד.
  <div class="gate">תנאי פרסום: כל השערים ירוקים, לוג הקריסות נקי, ואישור מפורש ממך.</div></div></div>
<p class="small">הערכה גסה לכל השלבים: כ-25–35 סשנים של עבודה (אני לא יכול לתת אומדן מדויק יותר כרגע). הזמן תלוי בעיקר במהירות האישורים בין השלבים ובזמני ה-CI.</p>

<h2 id="decisions"><span class="n">4</span>החלטות שדורשות את אישורך</h2>
<div class="tbl"><table>
<thead><tr><th>מזהה</th><th>החלטה</th><th>ההמלצה שלי</th><th>מקור</th></tr></thead>
<tbody>{drows()}</tbody></table></div>
<p class="small">שאלה נוספת: האם ה-RC של 2.2.3 (קוד 48) מותקן כרגע על הטלפון שלך? אם כן, השדרוג ממנו ייבדק כשער חובה (הוא כבר נכלל בסעיף 6 ליתר ביטחון).</p>

<h2 id="codex"><span class="n">5</span>משימות ל-Codex (מוכנות להעתקה)</h2>
<p>בחרתי שש משימות קטנות, עם גבולות ברורים וסיכון נמוך. כל אחת נוגעת רק בקבצים שאני לא אשנה באותו זמן (workflows, ‏download.js, ‏android-release.cjs, ‏transfer.js/share.js, ‏WorkDao, וחישוב גבול הטווח). Codex פותח PR לענף השחרור ולא ממזג. אני בודק ומשלב.</p>
<div class="note warn"><b>מתי לשלוח:</b> רק אחרי שאאשר שענפי <span class="mono">release/2.3-web</span> ו-<span class="mono">release/2.3-android</span> קיימים (בשלב 0). ‏C-5 נוגע ב-history.js, ולכן צריך לשלוח אותו בגל הראשון, לפני שאתחיל לשנות את מסך ההיסטוריה.</div>
{prompt_blocks()}
<p class="small">כל הפרומפטים נמצאים גם בקובץ <span class="mono">ux-audit/plan/codex-prompts.md</span>.</p>

<h2 id="tests"><span class="n">6</span>תוכנית בדיקות ורגרסיה</h2>
<h3>שכבות הבדיקה</h3>
<ul class="tight">
  <li><b>יחידה, קיים:</b> ‏119 בדיקות ווב. באנדרואיד ‏147 Debug ו-112 Release, ועוד 7 שיתווספו (QA-01).</li>
  <li><b>רגרסיה לכל ממצא:</b> בדיקה אחת לכל באג ששוחזר, לפי רשימת הרגרסיות של Codex. רק ממצאים חדשים, בלי כפילויות.</li>
  <li><b>אורקל כספי:</b> הרתמה של Codex (‏current-calculations/oracle.cjs) על הקוד החדש, ו-fixtures משותפים בין הפלטפורמות.</li>
  <li><b>הזרקת כשלים:</b> quota וחריגה בכל נקודת commit, רענון אחרי כשל, ושני חלונות (מירוצים בין טאבים).</li>
  <li><b>ממשק בווב:</b> סקריפטי Playwright מהביקורת הזו יהפכו לבדיקות עם assertions: בהיר וכהה, ‏320 עד 1440, axe, פוקוס, מקלדת ומסע מלא (הוספה → עריכה → מקום → תשלום → דוח → שיתוף → רענון). הם ירוצו ב-CI של web-v2.</li>
  <li><b>ממשק באנדרואיד:</b> צילומי Roborazzi בבהיר ובכהה, ובגודל גופן 1.0, 1.3 ו-2.0, ובדיקות instrumentation באמולטור של CI.</li>
  <li><b>חשבונות וסנכרון:</b> בידוד A↔B (טיוטות, מחיקה כוללת) מול Firebase emulator ב-CI (‏firebase.test.json כבר קיים). אין שימוש בחשבונות אמיתיים.</li>
  <li><b>לוג קריסות:</b> logcat נקי בכל ריצת אמולטור.</li>
</ul>
<h3>מטריצת שדרוג (חובה לפני פרסום)</h3>
<div class="tbl"><table>
<thead><tr><th>מסלול</th><th>מצב</th><th>נתונים לפני השדרוג</th><th>מה נבדק</th></tr></thead>
<tbody>{upgrows()}</tbody></table></div>
<p class="small">בכל מסלול אנדרואיד: חבילה <span class="mono">com.aistudio.worktracker.qztvdw.distribution</span>, אותו certificate fingerprint, ‏versionCode 49 גבוה מכל קוד קודם, ו-SHA-256 של ה-APK. השדרוג תמיד ב-install -r ולעולם לא מחיקה והתקנה מחדש. Room נשאר schema 11, בלי migration בגרסה הזו.</p>
<h3>מה שמור לך בבדיקה על הטלפון</h3>
<ul class="tight"><li>מבט כולל בבהיר ובכהה, ובעיקר בשלושת צבעי המקומות שסומנו.</li><li>Heebo בגדלים קטנים בעברית.</li><li>טיימר אמיתי וההתראה שלו.</li><li>שדרוג מעל הגרסה שמותקנת אצלך, עם הנתונים האמיתיים שלך (אני לא נוגע בהם).</li><li>מסך הבית וההיסטוריה עם הנתונים שלך.</li></ul>

<h2 id="branches"><span class="n">7</span>ענפים, שמירת עבודה ותוכנית חזרה</h2>
<div class="tbl"><table>
<thead><tr><th>מה</th><th>שם</th><th>נקודת מוצא</th></tr></thead>
<tbody>
<tr><td>תג גיבוי לאנדרואיד</td><td class="mono">backup/pre-2.3-android</td><td class="mono">fd5a2b6 (2.2.3 RC, כולל כל השיפורים שלא פורסמו)</td></tr>
<tr><td>תג גיבוי לווב</td><td class="mono">backup/pre-2.3-web</td><td class="mono">69e430e</td></tr>
<tr><td>ענף שחרור לאנדרואיד</td><td class="mono">release/2.3-android</td><td class="mono">fd5a2b6</td></tr>
<tr><td>ענף שחרור לווב</td><td class="mono">release/2.3-web</td><td class="mono">69e430e</td></tr>
<tr><td>עבודה לפי שלב</td><td class="mono">work/2.3-&lt;שלב&gt;-&lt;נושא&gt;</td><td>מתוך ענף השחרור, ומתמזג אליו ב-PR</td></tr>
<tr><td>משימות Codex</td><td class="mono">codex/&lt;נושא&gt;</td><td>מתוך ענף השחרור, PR בלבד</td></tr>
</tbody></table></div>
<ul class="tight">
  <li><b>שימור עבודה שלא פורסמה:</b> שני ענפי השחרור יוצאים מהראשים החדשים ביותר, ולא מ-main. לפני כל שלב בודקים ש-<span class="mono">git merge-base --is-ancestor</span> עדיין מכיל את fd5a2b6 ואת 69e430e. נשמרת רשימת בדיקה של יכולות 2.2.x ו-2.2.3 (כרטיסי הגדרות, התראות לפי סוג, שעון חי, צוות קבלנות, סיכום טכני ועוד) שחייבות להישאר.</li>
  <li><b>ענפי האנדרואיד והווב נפרדים</b> (הם נפרדים כ-170 commits). לא ממזגים ביניהם. מה שמשותף להם (הטוקנים וה-fixtures) נוצר מאותו קובץ מקור ונבדק בהשוואה.</li>
  <li><b>חזרה בווב:</b> מתעדים את ה-deployment הנוכחי בפרודקשן לפני הפרסום. חזרה מיידית אליו דרך Vercel. פורמט האחסון נשאר תואם, כך שגם ה-build הישן יקרא את הנתונים.</li>
  <li><b>חזרה באנדרואיד:</b> אי אפשר לחזור לגרסה קודמת של APK. לכן "חזרה" היא 2.3.1 עם revert, ולכן אין שינוי schema ואין מחיקת מפתחות העדפות בגרסה הזו. כל RC הוא artifact פרטי לבדיקה שלך, והפרסום נעשה רק דרך ערוץ העדכון הקיים, באישור.</li>
  <li><b>חזרה בקוד:</b> כל שלב הוא PR נפרד שאפשר לבטל ב-revert. תגי הגיבוי לא נמחקים.</li>
  <li><b>הענף של הסשן הזה</b> (<span class="mono">ccr-8d981fff-bp08oy</span>) הוא ברירת המחדל שלי לדחיפה. דחיפה לענפים אחרים תתבצע רק באישורך המפורש (D-16).</li>
</ul>

<h2 id="risks"><span class="n">8</span>סיכונים עיקריים וטיפול</h2>
<div class="tbl"><table>
<thead><tr><th>סיכון</th><th>טיפול</th></tr></thead>
<tbody>
<tr><td>שינוי מזהי legacy (M-02) שובר סנכרון, בחירה או טיוטות</td><td>מזהה נוסף רק לשורות שאין להן id, נשמר פעם אחת, ונבדק במעבר מאתר חי וחזרה. בלי dedup אוטומטי.</td></tr>
<tr><td>יחידת commit חדשה (M-04) פוגעת בביצועים או בנתונים קיימים</td><td>הפורמט נשאר כמו שהוא, ונבדק עם 10,000 רשומות ועם quota.</td></tr>
<tr><td>חוזה עיגול חדש משנה סכומים קיימים</td><td>חל רק על חישובים חדשים. בדיקה משווה את כל הסכומים השמורים לפני ואחרי.</td></tr>
<tr><td>עיצוב מחדש מוחק יכולת קיימת</td><td>רשימת יכולות 2.2.3, מסע מלא אוטומטי, וצילומי השוואה.</td></tr>
<tr><td>מחיקה כוללת בחשבון (M-13) מוחקת נתוני חשבון אחר</td><td>מוגבלת לבעלים, נבדקת מול emulator עם שני חשבונות, וההצלחה מוצגת רק אחרי אישור מהשרת.</td></tr>
<tr><td>שינויי שרת (MCP/OAuth) משפיעים על פרודקשן</td><td>נבדקים ב-Preview, והפריסה מתבצעת רק באישור. ‏refresh tokens קיימים ממשיכים לעבוד עד שיפוגו.</td></tr>
<tr><td>אין אמולטור בסביבה שלי</td><td>כל בדיקות האנדרואיד רצות ב-GitHub Actions (כולל אמולטור). להפעלת workflows אבקש את אישורך. כדי שאוכל לקרוא צילומים ישירות, צריך לאשר את הדומיין blob.core.windows.net.</td></tr>
</tbody></table></div>
<p class="small">הקבצים: ‏<span class="mono">ux-audit/plan/</span> (התוכנית והפרומפטים), ‏<span class="mono">ux-audit/report/</span> (דוח העיצוב), ‏<span class="mono">ux-audit/codex-audit/</span> (חבילת Codex כפי שהתקבלה). כל אלה מוחרגים מ-git ונשמרים רק בקונטיינר הזה.</p>
</div>
<script>
document.querySelectorAll('button.copy').forEach(b => b.addEventListener('click', async () => {{
  const t = document.getElementById(b.dataset.t).textContent;
  try {{ await navigator.clipboard.writeText(t); b.textContent = 'הועתק ✓'; }} catch (e) {{ b.textContent = 'סמנו והעתיקו ידנית'; }}
  setTimeout(() => {{ b.textContent = 'העתקה'; }}, 2200);
}}));
</script>
</body>
</html>'''
open(os.path.join(BASE, 'index.html'), 'w', encoding='utf-8').write(page)
md = '# Codex task prompts (2.3.0)\n\nSend only after release/2.3-web and release/2.3-android exist.\n\n' + '\n\n'.join(f'## {c} — {t}\nBase: {b}\n\n```\n{x}\n```' for c,t,b,x in PROMPTS)
open(os.path.join(BASE, 'codex-prompts.md'), 'w', encoding='utf-8').write(md)
json.dump([dict(zip(['id','prio','stream','platform','title','sources','owner','phase','depends'], m)) for m in M], open(os.path.join(BASE,'unified-register.json'),'w',encoding='utf-8'), ensure_ascii=False, indent=1)
print('ok', len(M), 'items', len(DEC), 'decisions')
