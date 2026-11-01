import csv,json,pathlib,re
root=pathlib.Path('/workspace/salary-audit')
text=(root/'current-android.md').read_text()
findings=[]
for section in text.split('### ')[1:]:
 if not section.startswith('AND-C'):continue
 head=section.splitlines()[0]; ident,title=head.split(' — ',1)
 sev=re.search(r'\*\*חומרה:\*\* (\w+)',section).group(1)
 priority=re.search(r'\*\*קדימות:\*\* (P\d)',section).group(1)
 findings.append(dict(id=ident,severity=sev,platform='Android 2.2.3 / fd5a2b6',title=title,status='הוכחת קוד; ללא הרצת Android',priority=priority,files=re.findall(r'`([^`]+\.kt[^`]*)`',section),details_he=section.split('\n## ')[0]))
(root/'current-android-findings.json').write_text(json.dumps(findings,ensure_ascii=False,indent=2))
rows=[]
def add(feature,actions,path,status='נבדק בקוד בלבד',finding=''):
 for a in actions.split('|'):rows.append(dict(id=f'AND-P{len(rows)+1:03}',feature=feature,action=a,status=status,execution_path=path,finding=finding))
add('ניווט','בית|היסטוריה|הגדרות|הוספה|חזרה ממסך עריכה|חיפוש בהיסטוריה מקיצור|פתיחת סיכום מהתראה','MainActivity.handleIntent → ShellRequest → KisApp → pager/sheets')
add('יצירה ועריכה','שעון רגיל|חציית חצות|הפסקה|ידני 1:40|בחירת יום|בחירת מקום|תעריף חסר|מטבע|בונוס עצמי|הערות|שמירה כפולה|ביטול עריכה|מחיקת עריכה באישור|עריכת הערה שומרת דיוק','KisEditor → EntryDraft.check/toEntry → WorkViewModel.saveNew/Edited → WorkEntryEdits → repository → entries')
add('קבוצה','הוספת עובד|הסרת עובד|שעות עובד|בונוס עובד|בונוס כולם/אני/צוות|תעריפי מעסיק/עובד|הצעות שמות|מחיקת עובד מספרייה','KisEditor.GroupBlock → WorkerDraft → WorkPay.encodeWorkers → saveNew/Edited')
add('קבלנות','סכום ידוע|סכום חסר|זמן אופציונלי|החזר הוצאות|צוות באותו סכום|צוות בסכום שונה|הוספת עובד ב-IME','KisJobGroup → EntryDraft → V2Rules → WorkPay → ViewModel')
add('קבלנות','חלק אישי אפס בצוות','KisJobGroup → EntryDraft.check → V2Rules.evaluate',finding='AND-C06')
add('היסטוריה','פתיחת פרטים|תשלום עצמי|תשלום לעובד|חיפוש קטגוריה/הערה/עובד/סכום|ניקוי חיפוש|סגירת חיפוש|מיון תאריך|מיון הוספה|בחירת רבים|סמן הכול|מחיקה ואישור|ביטול מחיקה|תשלום קבוצתי|סינון מטבע|סינון מקום|סינון ללא שכר|השלמת תעריף אחד|השלמת תעריף רבים','KisHistory/KisDetail → KisHistoryLogic → ViewModel → DAO → entries')
add('היסטוריה','טווח תאריכים בשעון קיץ','KisHistoryLogic.apply → startOfDay → to+86400000',finding='AND-C01')
add('היסטוריה','מעבר חצות בלי שינוי נתונים','KisHistory.remember(entries) מול KisHome.remember(entries,now)',finding='AND-C03')
add('טיימר','התחלה|אישור למשמרת קצרה|סיום במסך|ביטול באישור|שינוי מטבע|סיום בהתראה|idempotency בסיום כפול','KisHome → ViewModel → WorkShiftState/ShiftForegroundService → DAO.finishTimer receipt')
add('טיימר','קלט תעריף שגוי','StartLiveSheet → toDoubleOrNull fallback → WorkShiftState.require',finding='AND-C05')
add('טיימר','סיום לאחר שינוי/מחיקת מקום','Category DAO מול WorkShiftState prefs → finishTimer',finding='AND-C02')
add('טיימר','גבול מילישניות בשמירה','KisHome elapsed מול ViewModel elapsed',finding='AND-C07')
add('תכנון','יצירת סדרה|התנגשות סדרות|רישום אוטומטי|אישור עבדתי|לא עבדתי|שחזור יום|שינוי שעות יום אחד|שינוי שעות מהיום|השלמת תעריף סדרה|שימור ימים קודמים','KisPlan/KisEditor → V2Plan → ViewModel → DAO → PlanJobs')
add('תכנון','סיום סדרה לפני משמרת היום','SeriesSheet → ViewModel.endSeries → inclusive untilDate',finding='AND-C04')
add('הגדרות','מקום חדש|שינוי שם|שינוי תעריף|שינוי מטבע|מחיקת מקום|מחיקת מקום אחרון|בחירת ברירת מחדל|סידור מקומות|צבע מקום|ערכת צבע|רטט|הדרכה חוזרת','KisSettingsScreen → KisSettings/WorkViewModel → prefs/DAO')
add('התראות','בקשת הרשאה|טיימר פעיל on/off|סיכום חודשי on/off|תכנון on/off|תעריף חסר on/off|עדכון on/off|הכול on/off|שעות שקט|רשימת התראות|נקודה אדומה|פתיחה וסימון צפייה','KisInbox → PlanSettings/DataStore → PlanJobs/UpdateJobs')
add('דוחות ושיתוף','סיכום בית לפי מטבע|סיכום חודשי|סיכום שנתי|ארכיון סיכומים|העתקת משמרת|שיתוף משמרת|העתקת עובד|שיתוף עובד|דוח למעסיק|העתקת דוח מעסיק|Excel לפי סינון|תמונה לסיכום','WorkPay/V2Summary/WorkShareText → KisDetail/History/Story → clipboard/share intent')
add('עדכונים','בדיקה ידנית|כשל רשת|אישור התקנה|הורדה ואחוזים|ביטול הורדה|בדיקת hash|בדיקת package/version/certificate|פתיחת המתקין','KisUpdate → WorkUpdates → OkHttp → archive validation → ACTION_VIEW')
add('AI','הקלדת תיאור|מפתח חסר|הקלטה|ביטול הקלטה|הרשאת מיקרופון|סקירת תוצאה|עריכה לפני אישור|שמירה לאחר אישור','KisAi → PersonalAiKey/GeminiParser → AiShiftReview → addShifts')
add('הרצת מכשיר','שדרוג APK עם נתוני משתמש סינתטיים|התקנה טרייה|רקע/נעילה/Doze|סגירת תהליך ושחזור|שינוי שעון/אזור זמן|IME עברית פיזי|Share Sheet פיזי|הורדת עדכון אמיתי ללא התקנת ייצור','נדרש emulator/ADB או מכשיר; CI קודם אינו הרצה חדשה','חסום בסביבת הבדיקה')
with (root/'current-android-coverage.csv').open('w') as f:
 w=csv.DictWriter(f,fieldnames=rows[0].keys());w.writeheader();w.writerows(rows)
counts={}
for r in rows:counts[r['status']]=counts.get(r['status'],0)+1
(root/'current-android-coverage-summary.json').write_text(json.dumps(dict(total=len(rows),status_counts=counts,methodology='מסלולים ידועים שנקראו בקוד, ללא ספירת מעבר בהרצת Android; בדיקות Java אינן UI'),ensure_ascii=False,indent=2))
print(json.dumps({'findings':len(findings),'coverage':len(rows),'statuses':counts},ensure_ascii=False))
