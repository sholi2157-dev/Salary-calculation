# RC13 Android → browser implementation

## Exact references and rollback

- Android source: `b9dcba6fadfa17a370fa7b3fb8aa284a13eec1be`, branch `codex/android-local-distribution`.
- Attached RC13 APK SHA-256: `74da79a77d99e1e6d98ddca0c4b5b9b6079032cd191e57353ac2344d4c385da1`.
- Its packaged Git revision is `ae489c9655b1684f38be74ef0b12ff2c19e4e68e`, matching the signed RC13 evidence; APK contents/identity examined statically, not run in an Android emulator here.
- Pre-rebuild web: `05794abf0b01a3e3c69d83123c48824fc7b87def`.
- Remote recovery branch: `backup/web-before-rc13-parity-20261006` at that exact commit.
- Main/production branch remains `20c6022e3d35e8eea9beda3380c442208a18515a`.
- Implementation branch: `codex/web-android-rc13-parity`. No Android source/configuration changes.

Restore by deploying the backup branch to the **same existing Vercel project and origin**. Do not erase browser storage or switch the production domain as a migration shortcut. The rebuild keeps compatible financial formats, so a code rollback retains the guest snapshot.

## Presentation and structure

Existing data, sync and import modules are reused. The Android-specific screen composition is replaced: summary carousel, collapsed report, active-shift row, compact grouped fields, history toolbar/filter overlay, selected totals and read-only onboarding. Former inline orchestration now lives in `web/app.js`; `web/experience.js` owns the browser translation and `web/runtime.js` contains tested storage/timer contracts. `web/app.css` remains the owning stylesheet with source-derived colors, Heebo font copied from Android and reduced-motion support.

The browser uses the same narrow product surface on desktop (680px, Android's own maximum). It does not invent dashboard panels. Date/time inputs remain native but have readable `dd.MM`/24-hour presentation labels, preventing platform date clipping at 360px. Save reserves its own reachable footer above navigation; content can scroll into the reserved space.

## Parity matrix

| Android feature | Browser implementation | Status | Notes |
| --- | --- | --- | --- |
| Hebrew RTL, Heebo, animated navy/violet background, glass/form palette | Local Heebo asset and source-derived tokens/components | MATCHED | Visually inspected browser renders; no claim of pixel-perfect APK rendering |
| Main/history tabs and RTL swipes | Same tabs, horizontal pointer navigation, browser Back/Forward | WEB-NATIVE EQUIVALENT | Browser history is additive; nested summary gestures remain isolated |
| General + per-currency summary carousel | Same counts/hours/today, ILS/USD pages with income/paid/unpaid/week/month | MATCHED | Money is never mixed or converted |
| Collapsed new report and recent entries | Collapsed by default, preserved draft, recent three cards | MATCHED | Explicit Save clears submitted draft |
| Clock/manual/group reports, overnight time and breaks | Compact date/start/end and break/rate/currency rows, group workers/rates | MATCHED | Manual break field follows Android visibility; manual hours remain explicit |
| Editing, notes, payment, deletion | Same stored fields; confirmations and cancelled edits | MATCHED | Metadata-only changes preserve historical earnings |
| Active shift | Timestamp-based owner-scoped timer, selected category/rate/currency and confirmed finish | WEB-NATIVE EQUIVALENT | Continues elapsed time across reload/sleep; no Android foreground service/OS alert |
| History toolbar and filter sheet | Compact visible total, filters overlay/badge, sort/currency/copy menu | WEB-NATIVE EQUIVALENT | Filter overlay is a browser dialog |
| Search, clear vs close, category/notes/amount/workers | Separate clear/close; current filters compose without changing source | MATCHED | Legacy date/rate search retained as an additive convenience |
| Date/payment/category/currency filters and sort | All/month/inclusive date range, paid/unpaid, currencies, three sort orders | MATCHED | Native month/date input |
| Long-press selection, displayed per-row amounts, selected totals | Long touch plus explicit desktop selection; same currency-separated selection totals | WEB-NATIVE EQUIVALENT | Explicit desktop entry; bulk payment retained from previous web |
| Individual/group/worker/history/selected sharing | Android Hebrew content and saved per-worker rate overrides | WEB-NATIVE EQUIVALENT | Native Web Share then clipboard then text dialog; does not send automatically |
| Categories, rename, default, delete/reassign, currency | Same configurable default/rates/currency; add/delete available from report and Settings | MATCHED | Delete changes category labels only; no financial recalculation |
| Settings and direct default currency | Accordions plus direct currency control; legacy accounts secondary | MATCHED | Existing optional web account support preserved to avoid hiding old users' data |
| Backup/export/import preview | Same v2 data; JSON/CSV/TSV/file and clipboard; validated dedup preview | WEB-NATIVE EQUIVALENT | Android local category/currency defaults also translate on explicit restore |
| Fresh onboarding/skip/replay | Nine read-only steps with highlighted clones of actual product components | WEB-NATIVE EQUIVALENT | AI/API steps replaced by applicable modes/backup; existing users aren't forced through tour |
| Local/offline operation | Same-origin local snapshots; shell cache after first online load | WEB-NATIVE EQUIVALENT | Optional accounts/API responses never enter the shell cache |
| AI typing/voice/review/personal key | Feature flag off; no key input/client credential | INTENTIONAL PLATFORM EXCEPTION | Temporary security gate; Android AI is unchanged |
| APK installation/updater/foreground notification/wake-lock | Browser updates/application shell and elapsed-time restoration | INTENTIONAL PLATFORM EXCEPTION | No simulated Android permission/update mechanics |

## Data compatibility

Financial data stays in `work_complete_backup`, or existing account-scoped WorkCloud stores. Older `user_work_shifts`, `work_transfer_meta` and `work_default_currency` are read without being deleted or rewritten. No schema conversion/recalculation runs at startup. First explicit save creates the complete snapshot from the loaded legacy records.

`work_pre_rc13_snapshot_v1` archives exact old guest storage bytes once and is never overwritten. Data IDs, unknown local entry extensions, category identities, historical amounts, worker JSON and currencies remain intact when editing. Existing account stores/wire schema/sync modules are unchanged. Existing guest data is never implicitly adopted into an account.

New report drafts and active shifts use separate owner-scoped keys. A timer completion uses a stable shift ID and persists the shift before removing the timer; interrupted finishes are idempotent on retry. Legacy timer keys are untouched. Export retains the established v2 envelope plus optional `webPreferences` and compatible `androidLocalPreferences`; only category/currency preferences are exported, never API keys or notification/device secrets. Import preferences remain an explicit checkbox.

Malformed snapshots are not silently reset or replaced; writes are blocked. Browser storage remains tied to the exact origin. A preview has separate storage and **does not contain the owner's production shifts**.

## AI security and isolated enablement gate

`WEB_AI_ENABLED=false` and `/api/config` advertises `webAiEnabled:false`. `/api/ai` remains the safe retired 410 endpoint. No provider key is entered, persisted, exported or bundled. The historical personal-key client is excluded from the build; Android retains its encrypted personal-key flow.

To enable Web AI later: implement an authenticated server/serverless provider adapter and protected credential/configuration storage, explicit quota/abuse controls, bounded prompts/timeouts and validated structured responses; then add the RC13 editable-transcript → parse/loading → review → explicit-save flow. Keep Gemini 3.5 Flash unless the owner explicitly chooses another model. Never copy a key into public configuration, return it from `/api/config`, or store it in localStorage. Do not change shifts/categories/persistence to enable AI. Live provider calls are not verified.

## Verification evidence

Local `npm run build`, `npm test` (36/36) and `git diff --check` passed. `browser-tests/mobile.cjs` passed at 390/360px with synthetic records: shifts, currencies, group/worker shares, payment, edit/cancel/reload, search/clear/close, categories/default/rename/delete, bulk deletion/cancel, backup/export/import/TSV.

`browser-tests/parity.cjs` verifies fresh nine-step completion/skip/replay without saving sample data, compact fields, draft reload, overnight USD save, filters/selected totals, Back/Forward, timer/reload/finish and four widths (360/390/768/1440). Separate exact 22-shift/8-category synthetic fixtures cover both current and old local storage, saved financial amounts, worker rate/payment metadata, unknown extension fields and note editing/reload. Offline reload after first online load is tested. No user data or real provider key is used.

Physical phone keyboard/native share sheets and live account authentication/sync remain unverified; they are not inferred from browser simulations. Android was not modified or rebuilt. CI and live preview evidence are recorded after verified remote results.

## Deployment

Reuse the existing `salary-calculation` Vercel project and Git integration. No production branch change, merge or domain switch. The supplied `https://salary-calculation-eta.vercel.app` returned 404 NOT_FOUND in a real browser; it cannot serve as a successful current-production baseline. Connected Vercel project inspection returned 403 for the `sholi` team; there is no locally authenticated Vercel CLI. Git preview status and actual preview are verified separately if the existing integration deploys the new branch.
