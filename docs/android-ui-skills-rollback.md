# RC12: restore the complete pre-UI-Skills interface

Owner requested the previous version on 2026-10-01 UTC.
Android only. Starting clean branch codex/android-local-distribution at
b81d40e4deae0fdc00daf563d96e0ff241975c9a. Remote HEAD and backup were fetched
and verified. RC11 run36806582196/job110192154238 is successful; existing evidence
was inspected, not rerun during preparation.

## Exact restoration

All 8 Android UI source files changed by RC11 are restored byte-for-byte from
backup/android-rc10-before-ui-skills-20261001 at
25f7066cbe058af226ba46befc990f2d8b30b929. That source was the prior delivered RC10:
animated dark purple/navy background, translucent cards, original palette,
original typography/summary/form/AI presentation and original Save appearance.
No additional design choices or partial rollback. All earlier RC10 refinements,
compact forms, native History action targets, speech-review-save flow and data
behavior remain exactly as they were before the UI Skills task.

Source-byte equality with the backup checked for MainActivity, AiShiftExperience,
CompactReportFields, HistoryToolbar, WorkRefinements and Color/Theme/Type.
No database, money, timer, drafts, import/export, provider/key, accounts/cloud or
website changes. Package/signing identity unchanged. Neither phone data access
nor uninstall/reset/downgrade is used.

## Installable restoration

Build 1.5-rc12/versionCode 20, greater than the delivered RC11/code19. Use the
existing permanent Actions signing secrets and normal owner-only artifact path.
The previous binary is the exact original RC11 from run36806582196, pinned SHA256
68d0d70af713e69f88ede0e573dc00113493f095851a7d44ce39296fd754d506.
Gate tests RC11/code19 to RC12/code20 with unchanged synthetic data, native UI,
real keyboard and crash-log review. This is old UI in a new signed update, not
installation of the older-code RC10 APK. No stable release/feed publication.

## Verification status

UI restoration/source equality passed. Standard diff check reports only two
pre-existing trailing-space lines restored from RC10 Theme.kt; preserve exact
bytes and pass the remaining checks with blank-at-eol disabled. Downloaded JUnit
reports confirm Debug 72/72 and Release 60/60, zero failures/errors/skips.
All 29 rendered PNGs inspected: restored purple/navy canvas, translucent cards,
original summary/form/AI/history styles; no new clipping observed. This covers
360dp/390dp component layouts, not a physical-device motion review.
Signed source: `6a46483278d74439a5ec535d78dceaae196fe055`.
Actions run `36808449941` / job `110197933791`: SUCCESS; actual logs inspected.
Candidate artifact `11138556573`; unit/render artifact `11139180588`.

Original signed RC11/code19 to signed RC12/code20 installation passed without
uninstalling or clearing data. All six instrumentation stages passed: seed,
restart, updater, verify, UI and real keyboard. Exact synthetic before/after JSON
matches byte-for-byte, SHA256
`4fa2e3913aed78da5e0967f1c2d1368f6e42a31ae80cb364008f87f2d1793296`.
Stored amounts, currencies, categories, worker JSON, preferences and identities
preserved; offline restart, updater, reachable Save and duplicate-save checks
passed. Crash log is empty. No owner's real records accessed.

All 13 current emulator PNGs inspected, including Home, currency summary, forms,
History, settings dialog and keyboard/Save. Two captures are the launcher and
one Home-labelled capture shows a timer confirmation; filename alone does not
establish screen coverage. No new clipping observed in actual app captures.
CI disables animations; physical motion timing, speech/live AI remain owner checks.

Downloaded APK independently verified: v2 cryptographic signature/content digest,
embedded source, manifest, permanent package/certificate and SHA256 match CI.
Version `1.5-rc12`, code `20`, package
`com.aistudio.worktracker.qztvdw.distribution`, certificate SHA256
`ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`.
APK SHA256:
`23d5dd54f256ba865c6b5d8e976ae7c2d3226213fbc4a64d2aa608240f64f811`.
Owner file `SalaryRC12.apk` saved for delivery. Raw evidence: `release-evidence/rc12/`.
No public release, feed publication, website/cloud work or video.
Owner physical appearance review remains after delivery.
