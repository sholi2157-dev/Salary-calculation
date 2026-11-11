# Feedback and audit status for 2.4

Checked 2026-11-10 against `release/2.4-web` (`d0f9317`) and `release/2.4-android` (`7002c60`).
IDs only, with a one-line paraphrase. No user text, names, emails or screenshots. The decision history is in
the owner's decision log, which is not in this repo.

Status values: **shipped** (version) · **partly** · **open** · **owner** (needs an owner decision) · **obsolete**.
W = web, A = Android.

## User feedback

| ID | Paraphrase | Status | Evidence (W / A) |
|---|---|---|---|
| FB-01a | Currency shown after saving | owner decided no change (Nov 4) | — |
| FB-01b | Cancel a running shift | shipped 2.2 | W `web/kis/app.js` `cancelLive` (undo toast) / A `WorkViewModel.cancelActiveShift`, `WorkLiveCancelTest` |
| FB-01c | Enter in the note field must not close anything | shipped; 2.4 adds regression tests | W `browser-tests/note-enter.cjs` / A `WorkNoteEnterTest` (new line, editor stays open, saved amount unchanged) |
| FB-02 | Copy the hours of one employer | shipped | W `web/kis/history.js` `copySheet` over the place-filtered rows / A `KisHistory.kt` copy and Excel over the filtered rows |
| FB-03 | Today's numbers in the top cards | shipped | W `web/kis/home.js` `tiles` ("היום") / A `KisHome.kt` `StatTile("היום")` |
| FB-04 | Top cards layout | owner decided keep (Nov 4) | — |
| FB-05 | Report repeats the dates | shipped | W `web/v2/share.js` `copy` (dates only for one shift) / A `WorkShareText.kt` (same rule); `v2-share-fixtures.json` |
| FB-06 | Rate after every shift in the technical report | shipped | Same functions: no rate line under breakdown rows; fixture-pinned |
| FB-07a | Big duration clock on the active shift | shipped 2.3.0 (M-31) | W `home.js` `liveCard` + `.live .clock` / A `KisHome.kt` `live_card` |
| FB-07b | Notifications for the active shift | shipped on Android; web exception | A `ShiftForegroundService` (ongoing notification). Web has no native background notifications (documented platform exception) |
| FB-08 | Retroactive rate switch | shipped 2.3.0 | W `fb08-retro-rate.test.cjs`, `browser-tests/fb08-retro-rate.cjs` / A `WorkRetroRateTest` |
| FB-09 | One place's total across months | shipped (History: all dates + place filter); 2.4 adds regression tests | W `web-tests/fb09-place-total.test.cjs` / A `WorkFeedbackCategoryTotalTest` |
| F1 | Settings as accordion cards | shipped 2.3.0 | `m30-components.test.cjs` (F1) / `KisUiTest` (F1) |
| F2 | Softer light mode | shipped 2.3.1 (smoke grey) | `m28-palette.test.cjs` / `WorkPaletteTest` |
| F3 | One style for detail actions | shipped 2.3.0 | `browser-tests/m30-components.cjs` (F3) / `WorkComponentsTest` |
| F4 | Auto-register on the plan card | shipped 2.3.0 | `m46-ui.test.cjs` (F4) / `KisUiTest` (F4) |
| F5 | Group button back top-left | shipped 2.3.0 (switch reverted) | `browser-tests/r33-group-switch.cjs` / `V2JobTeamTest` |
| F6 | Details refresh after save | shipped 2.3.0 | W `editor.js` save → `onSaved(id)` reopens the detail / A `KisApp.kt` editor `onSaved` |
| F7 | Selected currency reads as selected | shipped 2.3.0 | `browser-tests/m30-components.cjs` (F7) / `WorkComponentsTest` |
| F8 | Clean toast | shipped 2.3.0 (M-37) | `browser-tests/m30-components.cjs` (F8) / `KisNoticesTest` |
| F9 | Truthful notification master switch | shipped; web subtitle fixed in 2.4 task 02 (#93), Android (#92) | `m31-notify.test.cjs` / `WorkSettingsTruthTest` |
| F10 | No ⓘ icons | shipped 2.3.0 | `browser-tests/m30-components.cjs` (F10) / `WorkWordingTest` |
| F11 | What's new after every update | shipped 2.3.0 | `fb23-whats-new.test.cjs` / `WorkWhatsNewTest` |
| F12 | Onboarding once per new account | shipped 2.3.1 | `browser-tests/f12-account-tour.cjs` / `WorkOnboardingTest` |
| Round 2 (Nov 9) | Four extras, two report rows, recaps and payment details in the account, date choice | shipped 2.3.0/2.3.1 | `browser-tests/date-choice.cjs` / `HebrewDateTest` |
| Pipeline since Nov 9 | No new item logged | — | — |

## Unified 2.3 plan (M-01…M-48)

| ID | Status | Evidence |
|---|---|---|
| M-01 | shipped 2.3.0 | W `draft-isolation.test.cjs` (CW-13); web only |
| M-02, M-03, M-04, M-11 | shipped 2.3.0 | W `store.js` single commit, `store-atomicity.test.cjs` (D01–D04); web only |
| M-05, M-10 | shipped 2.3.0 | `assistant-undo-log.test.cjs` (S-CUR-01/06/07); server |
| M-06 | shipped 2.3.0 | `m06-infinity.test.cjs` / `WorkM06StoredRateTest` |
| M-07 | shipped 2.3.0 | `m07-hours-group.test.cjs`, `group-bonus-target.test.cjs` |
| M-08 | shipped 2.3.0 | `money-contract.json` (shared fixture) / `WorkPay.kt` |
| M-09 | shipped 2.3.0 | `browser-tests/m2-input.cjs` / `WorkTimerCurrencyTest` |
| M-12 | shipped 2.3.0 | `browser-tests/m1-data.cjs` (D05) |
| M-13 | shipped 2.3.0 | `erase-two-actions.test.cjs`, `browser-tests/m13-erase.cjs` / `WorkSyncEngineTest` |
| M-14 | shipped 2.3.0 | `reserved-names.test.cjs` (CW-11) |
| M-15 | shipped 2.3.0 | `browser-tests/m3-plan-review.cjs` / `V2PlanEdgesTest` (CW-05) |
| M-16, M-17 | shipped 2.3.0 | `plan-time-edges.test.cjs` / `V2PlanEdgesTest` (CW-06/07, AND-C04) |
| M-18 | shipped 2.3.0 | `store-atomicity.test.cjs` (CW-09) / `WorkTimerCurrencyTest` (AND-C02) |
| M-19 | shipped 2.3.0 | A `KisHistory.kt` day rollover (AND-C03) |
| M-20 | shipped 2.3.0 | `web/v2/rules.js` / `V2HistoryRangeDstTest` |
| M-21 | shipped 2.3.0 | `web/v2/share.js` `textCell`, `web/transfer.js` formula guard |
| M-22 | shipped 2.3.0 | `download-version.test.cjs` |
| M-23 | shipped 2.3.0 | `assistant-refresh.test.cjs` |
| M-24 | shipped 2.3.0 | `browser-tests/cw12-worker-name.cjs` |
| M-25 | shipped 2.4 task 04 (#91) | Android CI test selection |
| M-26 | shipped 2.3.0 | `RichUpgradeTest` (QA-02) |
| M-27 | shipped 2.4 task 00 (#89) | Web fixture gate against `release/2.4-android` |
| M-28 | shipped 2.3.1 | `m28-palette.test.cjs` / `WorkPaletteTest` |
| M-29 | partly / obsolete | Even-width digits and one ₪ done; "one font" superseded by the Heebo rejection (D-2): keep Rubik + Secular One |
| M-30 | shipped 2.3.0 | `m30-components.test.cjs` / `WorkComponentsTest` |
| M-31 | shipped 2.3.0 | `m31-notify.test.cjs` |
| M-32 | shipped 2.3.0 | A `KisFormat.timeRange` (LTR isolate) used in `KisPlan.kt` |
| M-33 | shipped 2.3.0 | `browser-tests/mobile.cjs` (320 px, focus, keyboard) |
| M-34 | shipped 2.3.0 | `KisUiTest` (touch targets, semantics) |
| M-35 | shipped 2.3.0 | `kis.css` search clear/close; `browser-tests/mobile.cjs` |
| M-36 | shipped 2.3.0 | `web/kis/ui.js` duration / `KisKit.kt` |
| M-37 | shipped 2.3.0 | `m37-paid-undo.test.cjs` / `KisNoticesTest` |
| M-38 | partly → **owner** | Human copy done (`m38-wording.test.cjs` / `WorkWordingTest`). Payment status in the technical report, Excel and CSV still says "ממתין" (pinned in `v2-share-fixtures.json`) |
| M-39 | **owner** (deferred CAL-03) | Group profit vs owed vs worker pay, 0.33 / 0.34; `group-reconcile.json` pins today's behaviour. Pay rule, D-5 |
| M-40 | shipped 2.3.0 | `pending-from-home.test.cjs` (CW-08) |
| M-41 | shipped 2.3.0 | `browser-tests/cw10-series-until.cjs` |
| M-42 | shipped 2.3.0 | `import-series-category.test.cjs` |
| M-43 | shipped 2.3.0 | `csv-roundtrip.test.cjs` / `WorkTableExactTimeTest`, `WorkTableMoneyTest` |
| M-44 | shipped 2.3.0 | A `WorkDao.importBackup` counts only real inserts; `WorkAuditRegressionTest` |
| M-45 | shipped 2.3.0 | A `WorkPay.kt` timer preview = save (AND-C07) |
| M-46 | shipped 2.3.0 | `browser-tests` M-46 checks / `WorkScreenItemsTest` |
| M-47 | **open** (check) | Monthly recap hours vs the summary row; recap rounds ≥10 h to whole hours (`web/kis/summary.js`). Size S (half a day, both platforms, display only) |
| M-48 | shipped 2.3.0 | `group-bonus-target.test.cjs` / `V2JobTeamTest` (AND-C06) |

## Proposals (not implemented)

1. **M-38 "ממתין" in reports and tables.** Recommendation: change only the human technical report to "לא שולם",
   and keep "ממתין" in Excel/CSV because older versions import that word. Needs the owner's yes and a shared
   fixture update on both platforms.
2. **M-39 (CAL-03).** Recommendation: keep today's behaviour (each amount rounded once with `money()`) and show the
   one-agora difference as-is. Any change is a pay rule and needs the owner.
3. **M-47.** Recommendation: show recap hours with the same duration format as the summary row (display only, no
   stored data). Schedule as a small task after checking it with synthetic data.
