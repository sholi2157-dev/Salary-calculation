# RC12: restore the complete pre-UI-Skills interface

Owner requested the previous version on2026-09-30 America/New_York.
Android only. Starting clean branch codex/android-local-distribution at
b81d40e4deae0fdc00daf563d96e0ff241975c9a. Remote HEAD and backup were fetched
and verified. RC11 run36806582196/job110192154238 is successful; existing evidence
was inspected, not rerun during preparation.

## Exact restoration

All8 Android UI source files changed by RC11 are restored byte-for-byte from
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

Build1.5-rc12/versionCode20, greater than the delivered RC11/code19. Use the
existing permanent Actions signing secrets and normal owner-only artifact path.
The previous binary is the exact original RC11 from run36806582196, pinned SHA256
68d0d70af713e69f88ede0e573dc00113493f095851a7d44ce39296fd754d506.
Gate tests RC11/code19 to RC12/code20 with unchanged synthetic data, native UI,
real keyboard and crash-log review. This is old UI in a new signed update, not
installation of the older-code RC10 APK. No stable release/feed publication.

## Verification status

UI restoration/source equality passed. Standard diff check reports only two
pre-existing trailing-space lines restored from RC10 Theme.kt; preserve exact
bytes and pass the remaining checks with blank-at-eol disabled. Signed build,
consolidated tests, actual upgrade and downloaded APK verification pending.
Owner physical appearance review remains after delivery.
