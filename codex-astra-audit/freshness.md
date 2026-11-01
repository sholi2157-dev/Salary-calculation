# זיהוי גרסאות עדכניות — 30 באוקטובר 2026

## מסקנת בסיס הביקורת

העותק המקומי ב־main/work אינו הגרסה העדכנית: SHA `38143cdccc2b315aee6df49ead83d0da03cf0b20` מתאריך 22/10. נבדקו כל 41 ראשי הענפים שמוחזרים מ־GitHub (עמוד בגודל 100, פחות מ־100 תוצאות), כולל כל ענפי Claude, תאריכי commits, הודעות, אבות, 30 ריצות CI אחרונות ו־PRים. לא בוצעו checkout, merge, reset, push או שינוי במאגר המקורי.

|שכבה|ענף ו־SHA|מעמד|
|---|---|---|
|Android עדכני מאומת לביקורת|claude/project-thread-zs8886 — fd5a2b656efef3dee2f69881760ed72e44133804|2.2.3 / code48; מועמד חתום לבדיקת בעלים, CI הצליח; לא פורסם לציבור|
|Android ציבורי|codex/android-local-distribution — 26af6aacc2f4c8a9a243244c339a50a2fcdb3464|תג android-v2.2.1-code46; קוד האפליקציה זהה ל־155b7731eea6ba2d2d24fcb5521be784557eb899 שממנו נבנה APK|
|Web החדש ביותר של Claude|claude/project-thread-c50fyf-web — 69e430e2e75d330e00671e2b2d03a4180d2e4327|מימוש 2.2.2; build ו־119 בדיקות עברו, בדיקת התאמת fixtures נכשלה ובדיקות דפדפן דולגו|
|Web בעל CI מלא ירוק, מקביל לקוד החי|codex/download-and-management-20261020 — b6ccbc1a1e7c112663d979aa9789bac8b5134f00|CI 40565050815 הצליח; השינוי ביחס ל־SHA החי הוא קובץ workflow בלבד|
|Web בפריסה החיה|codex/download-and-management-20261020 — da7a7c34785845a4a9584ce796156492811f5a67|Vercel deployment dpl_2iuLxKCYzxUa3jBc4QyKcB3wyE5r; אומת בקריאת metadata על ידי הסוקר הראשי|

המלצה: לבקר את Android2.2.3 כמימוש האחרון, ולסמן בנפרד ממצאים שמתקיימים גם ב־2.2.1 הציבורי. לבקר את Web69e430 כקוד האחרון ולבצע בדיקות דפדפן מבודדות להשלמת החסר; לשמור את b6ccbc1/da7a7c3 כבסיס השוואה לפריסה. אין לתאר קוד69e430 כמוצר שכבר פרוס.

## הוכחת Android

ריצה [41037296327](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/41037296327), job124001184043, הסתיימה בהצלחה ב־30/10 10:04 UTC. נבדקו שמות השלבים ותוצאותיהם ב־API: רגרסיית Debug/Release, יצירת מועמד חתום, בדיקת זהות, עדכון אמולטור עם שימור נתונים והתקנה טרייה. זהו תיעוד CI קיים, ולא הרצה חדשה בסביבת הביקורת. אין להסיק ממנו שכל מסע משתמש מכוסה.

Artifact: `salary-2.2.3-code48-candidate`, id12968965970, ‏25,529,290 bytes, לא פג תוקף. digest של **ארכיון artifact** (אינו hash של APK): `e017a70dbc35b5f78b9e03bba64f6d2d5c8868caa61e8d1e85b123608b3310d7`.

שרשרת הקוד כוללת 2.2.2 ואז 2.2.3 עם תיקוני מצב טיימר בין שירות למשתמש מחובר, תזכורות, מתגי התראות ומסכי הגדרות. commits קודמים נכשלו בחלק מריצות ה־RC; ראש הענף האחרון עבר. עצם הודעת commit אינה הוכחה לתיקון מלא — זו סיבה לבדוק שוב את אותם מסלולים.

ה־Release האחרון ב־GitHub הוא [android-v2.2.1-code46](https://github.com/sholi2157-dev/Salary-calculation/releases/tag/android-v2.2.1-code46). תיאורו מציין source155b7731, checksumAPK `c62691480f7cdb81259af5d8179328ec3620c20a1fc367e4916740035d6d8882`. תג ההפצה מצביע על26af6aa, וה־compare מוכיח שרק docs/android-update-release.json השתנה בין source הבנייה לתג. השוואה זו אינה בדיקה בינארית עצמאית של APK שהורד.

## הוכחת Web והסבר הכשל

ריצה [40990885116](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/40990885116), job123852737419:
- `npm run build && npm test` הצליח; בלוג בפועל: tests119, pass119, fail0.
- `scripts/check-fixtures.cjs . origin/codex/android-local-distribution` נכשל exit1 רק ב־v2-share-fixtures.json.
- v2-rules.json, v2-plan.json, v2-summary.json התאימו.
- התקנת Playwright ובדיקות הדפדפן דולגו.

בדיקת diff מראה שהשינוי החדש מסיר שורת תעריף מכל משמרת בפירוט של סיכום מרובה, משאיר אותה במשמרת יחידה, מעדכן fixture בהתאם ומציג intro2.2.2. הוא מושווה ב־CI לענף Android הציבורי2.2.1, ולא למועמדAndroid2.2.2/2.2.3. לכן הכשל הוא אי־התאמה בין מסלולי גרסה שונים; הוא אינו כשלעצמו הוכחה לתקלה בחישוב שכר. בדיקות דפדפן של ראש Web החדש עדיין נדרשות.

compare בין da7a7c3 החי ל־b6ccbc1 הירוק מחזיר שינוי אחד בלבד: .github/workflows/web-v2.yml, החלפת יעד השוואת fixtures מ־claude/v2-android ל־codex/android-local-distribution. קוד היישום החי והירוק זהה לפי עץ Git; יש עדיין להבחין בין זהות מקור לבין אימות כל קובץ שנמסר מה־CDN.

## מגבלות ואי־ודאות

- ייתכן שקיימת עבודה לא מחויבת במחשב Claude או APK שנמסר מחוץ ל־GitHub; אין ראיה לגביהם. הביקורת יכולה להתחייב רק לראשי הענפים ולתוצרי ההפצה הנגישים.
- ענפים פתוחים אינם בהכרח בלתי גמורים, וענף main אינו authoritative למוצר. בדקנו ancestry והשוואות קבצים במקום להסתמך על שם.
- נתוני Vercel בדוח זה נמסרו מתוך בדיקה מקבילה של הסוקר הראשי; רשומת המקור המקומית freshness.json כוללת ראיות GitHub בלבד.
- מגבלת רשת shell מנעה fetch רגיל; נעשה שימוש ב־GitHub connector GET בלבד. זוהי בדיקת refs/commits מרחוק, לא טענה ש־git fetch הצליח.
- הממצאים המוקדמים על Android המקומי הישן ועל Webmain אינם ניתנים לשיוך אוטומטי לגרסאות אלה. חובה לאמת מחדש כל ממצא לפני הכללתו ברשימת תקלות נוכחית.

ראיות מכונה: freshness.json — כל ראשי הענפים, הודעות commits, ריצות, שלבי jobs, השוואות קבצים, metadata הפצה וקטע לוג כשל.