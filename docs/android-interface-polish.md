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

## Final verification

Source `068c7fada2dad09c59c0b240c4ddb4e5bad18d20`; Actions run36786550537, job110129210875: SUCCESS.
Debug72/72 and Release60/60 with zero failures/errors/skips. Full signed original
RC9/code17→RC10/code18 update, exact synthetic data comparison, updater/offline,
UI and real keyboard/Save gates passed. Crash log0 bytes. Downloaded APK
signature, permanent certificate, package, version and SHA independently verified.
All29 rendered PNGs and13 emulator screenshots inspected.

Evidence, identity, raw reports and owner gate: `docs/android-signed-rc-evidence.md`
and `docs/release-evidence/rc10/`. Final artifact is owner review only.
Physical-phone motion/keyboard/voice/live personal-key AI review remains; CI
disables emulator animations and does not access the owner's existing records.

The actual RC9 Heebo font already has equal1151 digit advances; no font change
was needed. Reserved width/LTR numeric presentation provides the layout change.

## Disclosed intermediate failures

- Run36783808081/source1f142712: existing Debug70 passed; two new UI tests
  initially used merged semantics for the action-row tag. Corrected the finder.
- Run36784485302/source71145bd2: existing Release60 passed; new UI tests were
  unintentionally selected by Work* in Release without a Compose test host.
  Renamed to InterfacePolishTest and kept rendered checks in Debug. Production
  manifest/dependencies unchanged.
- Run36785104448/source812e983a exposed clipped48dp group actions. Flattened
  icons into individual wrapping children and checked separation on both axes.
- Run36785707286/source4efb9980 still exposed clipping. Expanded screenshots
  revealed the root cause: local legacy `fun FlowRow` renders a horizontal
  LazyRow rather than wrapping. Qualified native Compose FlowRow explicitly;
  left the unrelated category helper unchanged.
- Run36786248719/source44bb4ad4 required ExperimentalLayoutApi opt-in for native
  FlowRow. Added the local opt-in. The final complete run above passed.

Failed intermediate results are not counted as passes. Because production UI
changed while addressing the reproduced layout bug, final-source consolidated
checks were run anew. Temporary focused-continuation tooling was removed.
