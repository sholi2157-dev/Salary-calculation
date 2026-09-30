# Android interface polish — RC10 owner review

Owner authorized the eight reviewed proposals on 2026-09-30, requesting a backup
and an updated installable APK, without experiment switches. Android only.

## Recovery checkpoint

Before editing, remote `codex/android-local-distribution` HEAD was
`3910e9689dcafa0a2dcf2fb90dff3eb46f6a076f`. The dedicated branch
`backup/android-rc9-before-interface-polish-20260930` was created and read back:
its HEAD is exactly that SHA. It preserves all RC9 code and documentation.
RC9 signed artifact: run36779054455, artifact11126034909, versionCode17,
SHA256 c63dd258bc328e25204498f593b9704399723e9166c5169839e5bc4e9d751734.
This is a source/build recovery checkpoint, not a copy of the owner's phone data.

If the owner rejects an individual change, reverse only that UI change. If the
owner requests the original interface, restore the RC9 UI files from the backup
branch and build a new candidate with versionCode greater than every delivered
candidate. Keep the active release identity/version tooling; never reset/force-push
the release branch or install an older-code APK as the rollback mechanism.

## Scoped implementation

1. Active-shift use no longer dims actionable content/navigation to 15% opacity.
   Remove the now-obsolete idle pointer observer; keep the existing Dashboard
   focus-alpha parameter for test/component compatibility.
2. Recent-shift date/hours/details are 12sp regular instead of 9–10sp/light.
3. History personal/group action buttons and worker share use explicit 48dp
   layout/hit areas. The action row wraps as a FlowRow at narrow widths; group
   worker names take remaining width. Payment controls have minimum48dp height.
4. Live timer and earnings use Heebo tabular digits, LTR numeric presentation and
   minimum reserved width. Summary figures/history total use tabular digits too.
5. Save shares its interaction source with the press modifier. Press scale is
   0.96 with critically damped spring. Bottom tabs use native interaction feedback.
6. New-report expansion has one 180ms-enter/120ms-exit height animation; remove
   the overlapping animateContentSize on its parent. Existing Save footer remains.
7. Worker add/remove and share accessible labels are accurate Hebrew. Existing
   share intents and text payloads are preserved.
8. Closed History/recent cards display paid/pending text as well as its color.

No payroll/amount logic, records, schema, draft/backup format, provider/key,
timer service, package or signing identity changes. No new font/dependency/style
system, account/Firebase activation, website edits, Stable/feed publication.

## Validation plan and current status

Local environment has no full prior Git checkout, SDK or Gradle executable.
Source was fetched at the pinned remote SHA; modifications are prepared against
the existing Git tree through the authorized GitHub connector. No stale worktree
or previous code changes are overwritten.

Shell syntax checks passed for release scripts. Add two focused rendered UI tests
at360dp/390dp to exercise wrapped group actions, disjoint minimum48dp hit areas,
paid/pending text, real pointer dispatch and worker-payment updates preserving
stored earnings/currency. Existing History/form/AI/inset regressions are reused.

Use one existing consolidated Android Actions run for compilation, Debug/Release
tests, PNG evidence and permanent signing. Upgrade from the actual RC9/code17
artifact (pinned hash), not a same-source reconstruction, to RC10/code18; retain
exact synthetic snapshot/journal comparison and the real IME/UI gates. No website
tests are added. Inspect actual results, rendered layouts and crash log before
delivery. Pending: Actions execution and owner physical review.

## Initial run and focused continuation

Run36783808081 / source1f142712aca6b0f96853fb28ecf6185ceeaa6f71 compiled
Debug app/tests and passed all70 pre-existing Debug tests. The two new tests failed
only because their FlowRow tag is in the unmerged semantics tree of the clickable
card; no production code failure was observed at that point. Correct the finder
and rerun only those two Debug tests, then the not-yet-run Release suite/build,
signing and actual upgrade gates. Do not repeat the successful70 Debug tests.
All25 generated existing form/AI/History/inset PNGs were visually inspected.

The bundled RC9 Heebo font was inspected from the real APK: all digits already
have equal advance1151. No typeface change is needed; reserved width/LTR amount
presentation are the substantive live-number layout refinements. The `tnum`
feature request is harmless on this font (which has no separate tnum GSUB feature).

The first focused continuation run36784485302 / source71145bd28f4ac2ab8a31b816ed89550ce3d68f3a
passed all60 existing Release tests and compiled unsigned Release/instrumentation.
The new UI tests were unintentionally included in the Work* Release selector,
whose manifest does not register the Compose test host ComponentActivity; both
failed before rendering. Rename the UI test to InterfacePolishTest, keep it in the
explicit Debug selection alongside the existing rendered tests, and do not change
the production manifest/dependencies to accommodate a test host. Gradle scheduled
Release tests before Debug, so the corrected focused Debug test has not yet run.

Continuation checks prove production/release source equality against the initial
UI source, download the immutable prior reports and explicitly retain only70
passed Debug /60 passed Release tests. The two known failed test-harness records
in each baseline are excluded and disclosed, never counted as passes. The fresh
focused Debug results must pass2/2 before signing/update continues. This avoids
rerunning unchanged suites. See scripts/summarize-interface-polish-tests.py.
