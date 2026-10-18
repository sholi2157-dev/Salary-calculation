# 2.0 stage C (Android): weekly plans, auto-record, reminders, quiet hours

Branch `claude/v2-android`. Owner approved stage C after rc3 ("נראה טוב ומצוין") and chose to keep everything inside the
existing screens (no new page). Delivered as signed `2.0-rc4` / code 28 (see "Build" below).

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

## Independent review (three agents: data, screens, stability) — fixed before the RC
- A day recorded ahead and then split "from now on" could be recorded again under the new plan → split now skips recorded days.
- A deleted recorded plan day came back by itself → deleting marks the day skipped; undoing the delete clears it.
- Clashes were checked only for the 4 weeks on screen → checked over the whole new range, from the database at save time.
- Rate pins, import (day changes only for plans the import added), decode guards (real dates from 2000, from ≤ until).
- Undo never removes a shift edited meanwhile; manual "עבדתי" undo holds the day.
- Notification tap keeps the open app (SINGLE_TOP), opens last month, and selects shifts without a rate after they load.
- Worker: rethrows cancellation, logs, stops retrying after 3 attempts; idle while a signed-in account is active.
- Screens: 2×2 full-size plan buttons, seven equal day toggles, the plan follows the editor date and needs at least one
  created day, notes/break "not kept" note, Back leaves the planned view, plans tick each minute, 48dp chips.
- Notification icon: replaced in rc5 by the app mark (see below).

## Build (verified)
- Source `ec0aca5ee653c8a9fb68534a310c35d2b4db566e`; dev CI run 38954859534 SUCCESS.
- Signed RC run 38955233764 SUCCESS: 123 Debug + 103 Release unit tests (0 failures); identity check "same package,
  same permanent certificate, versionCode 28 > 27 > 24"; real RC16 → delivered rc3 (exact artifact, SHA-256 checked) →
  rc4 update with byte-identical data snapshot; device UI, keyboard, currency, `PlanAutoRecordTest`; fresh install; empty crash log.
- APK `candidate-b.apk` SHA-256 `bc140859fa345851966333ea0c21bd81d04c790b4f24fdb19981a4817e25f71d`,
  package `com.aistudio.worktracker.qztvdw.distribution`, `2.0-rc4` / 28. Private artifact `salary-2.0-rc4-candidate`
  (id 12070554598). Not published; no Release, no update feed change.
- Remaining manual gate: the owner's phone (update over rc3, create a weekly plan, see it on Home/History, a reminder).

## rc5 — one look, done vs planned, profile, recent notifications (owner review of rc4)
- One purple glow (`KisGlow`, `Workplaces[0]`) on Home, History, Settings and the editor. The editor no longer has a
  full workplace-colour block: plain page, ink text; the workplace colour marks only the chosen place and the ruler span.
- Done vs planned by calendar day (`KisHistoryLogic.upcoming/done`, the rule Home already used): a shift on a later day
  is planned. History's "עבדתי / מתוכנן" toggle is always there; "עבדתי" (totals, copy, Excel, selection) holds done
  shifts only; "מתוכנן" lists future shifts and weekly-plan days by date, with an explanation when empty. Cards of
  future shifts carry "מתוכנן", a dashed avatar and a muted amount; Home "הבא בתור" includes them. Display only: no
  stored data changes.
- Notifications (timer and reminders) use the app mark `ic_stat_kis` tinted purple instead of the system red circle.
- Profile name (Settings → פרופיל, device-only like the plan settings): greeting "ערב טוב, שם" and my own line in the
  group report to the contractor ("שולי: …" instead of "החלק שלי"); never at the top of a message. Shared case
  `group_employer_report_with_profile_name`.
- "התראות אחרונות" (last 20 sent reminders) and "החודש שלך" month summary (done shifts only, money per currency, top
  place, change from the previous month, share). Opened from the monthly reminder, the list, and History's month view.
- Website note for stage E: an active shift is kept in the browser as its start time and keeps counting while the tab is
  closed; there is no persistent device notification. Reminders show inside the site while it is open; reminders while it
  is closed need a push server and are tied to accounts/sync, so they need separate approval.
- Build (verified): source `8f779517a6dfd17a47ef54b2f266888625749c17`, dev CI run 39082269505 SUCCESS; signed RC run
  39082570832 SUCCESS: 127 Debug + 103 Release unit tests, "same package, same permanent certificate, versionCode
  29 > 28 > 24", real RC16 → delivered rc4 (exact artifact) → rc5 update with identical data, device UI/keyboard/
  currency/`PlanAutoRecordTest`, fresh install, empty crash log. APK SHA-256
  `c68a00a8b000ab6c12dba688780ffccd83266dfe9397c80a8c20ce17a4ff7225`, `2.0-rc5` / 29, private artifact
  `salary-2.0-rc5-candidate` (id 12119622614). Not published. Manual gate: owner's phone.
