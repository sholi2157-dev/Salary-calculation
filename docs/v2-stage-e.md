# 2.0 stage E: the website ("כיס") — preview only

Branch `claude/v2-web` (from `codex/web-rc15-live`, the published site). Nothing here is deployed to production, aliased
or merged; Vercel builds a Preview per push. Publishing is stage F and needs the owner's explicit approval.

## What changed
- **One look with the app.** `web/kis/kis.css` carries the same tokens as `KisTheme.kt` (light/dark/auto, 8 workplace
  colours, the purple glow on every page, floating pill navigation). Fonts Rubik + Secular One (OFL, `docs/licenses`);
  ₪/$ always in Rubik. 360–1440px, RTL.
- **Same rules as Android.** `web/v2/rules.js`, `pay.js`, `plan.js`, `share.js` are ports of `V2Rules`, `WorkPay`/`WorkMoney`,
  `V2Plan`, `WorkShareText`, tested on the very same fixture files (`web-tests/fixtures/`, checked byte-for-byte against
  `claude/v2-android` by `scripts/check-fixtures.cjs` in CI) under several time zones.
- **Backup both ways.** `web/transfer.js` reads formats 1–3 and writes format 2 byte-identical to before when no 2.0 data
  exists, format 3 otherwise (kind, payKnown, bonus, seriesKey, autoConfirmed, worker bonus, series, seriesExceptions),
  so an Android 2.0 backup imports here and the site's backup imports in the app. Import never duplicates.
- **Screens** (`web/kis/`): Home (week/today/all time per currency, live shift, "הבא בתור", latest, the month's pocket);
  History (all dates by default, one "סינון ומיון" panel with a live count, a "מוצג: …" line, done vs planned, search with
  clear and close, multi-select with share/copy/paid/rate completion/delete-with-undo, Excel and WhatsApp copy);
  Add/Edit (clock with keypad, ruler and ±15/30, hours, job, group, unknown rate never 0, bonus, break, weekly repeat
  with preview; edit as a framed pop-up; a new draft survives a reload); Settings (profile name, workplaces with colour,
  default currency, theme, weekly plans, reminders and recent notifications, backup/import, update check); "החודש שלך".
- **Weekly plans** stored in `work_series_v1`; ended days are recorded on open and on return to the tab, once per
  `seriesKey` (re-read from storage just before writing), with undo that holds the day.

## Existing visitors' data
Read exactly as before (`WorkRuntime.loadGuest`: `work_complete_backup`, else the legacy keys); opening the site never
rewrites it; the one-time checkpoint runs before the first write. The live timer key and shape are unchanged. Accounts
and cloud sync stay off (`WEB_ACCOUNTS_ENABLED=false`) and are not loaded. The old page is kept as
`web/legacy-index.html` for reference only.

## On the web, by design
- An active shift is stored as its start time: it keeps counting while the tab is closed; no persistent notification.
- Reminders (monthly summary, missing rate) appear inside the site when it is opened; reminders while it is closed need
  a push server tied to accounts/sync and separate approval.
- Updates: the existing update manager activates a new version only when no edit is open; a new draft is already saved.

## Verification
- `npm test`: 72 unit tests (rules, plans, sharing, backup v3, and every earlier test).
- `browser-tests/v2.cjs` (CI `web-v2.yml`): stored data shown unchanged, add with keypad, unknown rate then completion,
  edit keeps the stored amount, future shift planned and not summed, the filter panel, weekly plan recorded once across
  a reload, Android v3 backup import and export, live shift and draft survive a reload, no horizontal scroll at
  360/390/768/1440 light and dark, no page errors.
