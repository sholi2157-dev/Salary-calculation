# 2.0 stage C (Android): weekly plans, auto-record, reminders, quiet hours

Branch `claude/v2-android`. Owner approved stage C after rc3 ("נראה טוב ומצוין") and chose to keep everything inside the
existing screens (no new page). Delivered as signed `2.0-rc4` / code 28 (see "Build" below once verified).

## What the user gets
- **Editor, new clock shift:** "חוזרת כל שבוע" switch → weekdays (default: the shift's weekday) and "עד" date (default: +8 weeks).
  Live preview: "N משמרות ייכנסו ללוח · M חופפות למשמרת קבועה ולא ייווצרו". Save button reads "יצירת הסדרה".
- **Home:** "עוד N מתוכננות" under the week numbers (text only, never added to sums) and "הבא בתור": ended plans waiting
  to be recorded first, then the next three. A planned card has a dashed avatar, "מתוכנן"/"ממתין לאישור", a muted
  amount (or "?" when the rate is unknown), and opens to עבדתי / שעות / לא עבדתי / שיתוף (each with undo where it changes data).
- **History:** "עבדתי / מתוכנן · N" toggle (shown only when plans exist). The planned view lists plans only; totals,
  search, filters, copy and Excel are hidden there, so planned shifts are never summed, copied or exported.
- **Hours:** "רק הפעם" (that day only) or "מעכשיו והלאה" (the plan is split on that day).
- **Settings → עבודה → משמרות קבועות:** each plan with days, hours, end date, "עוד N"; change rate from today; end the plan;
  auto-record switch. **Settings → התראות:** monthly summary, missing-rate reminder (both off until turned on; on Android 13+
  the permission is requested then, and a refusal keeps the switch off with an explanation), quiet hours (default 22:00–07:00).
- **Share a planned shift:** "פרטי משמרת מתוכננת…" with "צפי (עוד לא בוצעה)" instead of an amount to pay
  (`v2-share-fixtures.json` case `planned_shift_is_marked_and_not_to_pay`).

## Rules (domain/V2Plan.kt, expected results in `app/src/test/resources/v2-plan.json`)
- Occurrences are computed from `shift_series` + `series_exceptions`, never stored as shifts. Key `seriesId@YYYY-MM-DD`.
- Recording writes a normal shift with that unique `seriesKey` through an IGNORE insert in one transaction: the app,
  the daily job and a re-imported backup can never record the same day twice.
- Undo of an auto-record deletes those rows and marks the day `hold`, so it is not recorded again by itself.
- An unknown rate stays unknown (`payKnown = false`), never ₪0. Times use the local calendar (daylight saving safe);
  an end at or before the start ends the next day.
- A new rate applies from today; days that already ended and wait keep the old rate (pinned per day); recorded shifts
  never change. Splitting moves later cancellations/holds/rate pins to the new plan; a split on the first day replaces the plan.
- Deleting or renaming a workplace moves its plans the same way as its shifts.

## Data, backup and background work
- Room 9 tables already existed (stage A). Backup format 3 now also carries `series` and `seriesExceptions`; when no
  plans exist the backup stays byte-identical to format 2. Import adds plans by id and skips days already recorded.
- One daily WorkManager job (`PlanWorker`, unique, KEEP) records ended plans and sends due reminders. The app also
  records on start and on every return, with "N משמרות קבועות הסתיימו ונרשמו · ביטול".
- Reminders: monthly summary once per month (for the previous month, only when it has shifts); missing rate at most
  once a week. A reminder due in quiet hours is not dropped: a one-time job runs when quiet hours end. Tapping opens
  History (summary) or selects the shifts without a rate and opens the bulk rate sheet. New channel "תזכורות וסיכומים".

## Deviations from the plan (deliberate)
- Plan and reminder settings are kept on the device (SharedPreferences `kis_plan`), not in the backup's local
  preferences: older versions reject backups that carry preference names they do not know.
- "חוזרת כל שבוע" is offered for clock shifts only (a weekly plan needs start and end); a break or notes are not part of a plan.
- Days that overlap a saved timed shift or another plan are recorded as cancelled exceptions, not created.

## Tests
- `V2PlanTest`: shared fixtures (occurrences, toConfirm, toEntry, preview, split, rate from today), Israel DST,
  and real Room: double recording → one row, undo hold, backup v2/v3 round trip and re-import no-op, workplace delete
  moves plans, split on the first day.
- `V2ShareTest`: planned share text. `KisUiTest`: series from the editor with preview, Home next-up and History
  planned view (never in totals/copy), Settings plan and reminder switches.
- Device (`PlanAutoRecordTest`, run in the signed RC gate after the update/data checks): a synthetic plan whose days
  ended is recorded once with four concurrent recorders plus the daily job, shows on Home, and is removed again.
