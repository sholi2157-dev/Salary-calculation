# גיבוי git מלא (bundle)

הקובץ `pre-2.3-backup.bundle` מכיל את כל ההיסטוריה של ארבע נקודות הגיבוי:

| ref בתוך ה-bundle | commit | מה זה |
|---|---|---|
| refs/tags/backup/pre-2.3-android-fd5a2b6 | fd5a2b6 | אנדרואיד 2.2.3 (קוד 48), לא פורסמה |
| refs/tags/backup/pre-2.3-web-69e430e | 69e430e | האתר החדש ביותר, לא פורסם |
| refs/tags/backup/public-android-2.2.1-26af6aa | 26af6aa | ענף 2.2.1 הציבורית |
| refs/tags/backup/live-web-da7a7c3 | da7a7c3 | המקור של האתר החי |

**למה bundle ולא תגים ב-GitHub:** בסשן הזה ה-proxy של GitHub חוסם דחיפת תגים (HTTP 403), וענפי גיבוי היו יוצרים Preview אוטומטי ב-Vercel.

שחזור: `git clone pre-2.3-backup.bundle restored && cd restored && git tag -l`. ‏`git bundle verify` עבר.
