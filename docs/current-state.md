# Current project state

Last updated: 2026-10-06

## Repository and active track
Repository: `sholi2157-dev/Salary-calculation`

Current active track: Android local distribution only.

Branch: `codex/android-local-distribution`

The website/sync work is intentionally paused on:
`codex/preserve-app-sync` / PR #1

Do not resume or modify website, Firebase, accounts or cloud sync until the user explicitly switches the project back to that phase.

## Active task: first-launch Android onboarding — RC13

Owner requested an interactive first-launch tutorial and Settings replay.
Exact pre-change clean HEAD: 6207796903515b97dcb147ae029d4b12e8f635fe.
Rollback branch: `backup/android-rc12-before-onboarding-20261006`.
Latest verified owner-review APK: `1.5-rc13` / code `21`, signed source
`ae489c9655b1684f38be74ef0b12ff2c19e4e68e`.
Final run37415910804/job112114352377 SUCCESS; artifact11390889607.
APK SHA256 `74da79a77d99e1e6d98ddca0c4b5b9b6079032cd191e57353ac2344d4c385da1`.
Consolidated Debug80/Release64 passed; final focused Debug9/Release4 passed.
Original RC12 signed upgrade: all six stages passed with exact synthetic data
preservation, normal UI/draft replay/real IME. Separate signed fresh emulator:
nine steps, completion/skip/replay/recreate/cold process launch passed. Crash logs
empty; downloaded signature/digest/package/version independently verified.
Version1 local state suppresses forced onboarding for established users. Replay
is in Settings. Scope/evidence: `docs/android-onboarding.md`, `docs/release-evidence/rc13/`.
Owner file `SalaryRC13.apk`; physical phone/TalkBack/live AI review remains.
Owner authorized a friend download link on 2026-10-06. Exact verified RC13 APK
published as prerelease `android-v1.5-rc13` (run37487018264); anonymous download
and exact SHA256 verified. Direct permanent link:
https://github.com/sholi2157-dev/Salary-calculation/releases/download/android-v1.5-rc13/SalaryRC13.apk
Stable update feed/Latest remains unchanged. No rebuild or app source changes.
Public metadata: `docs/release-evidence/rc13/public-download.json`.
No website/cloud/storage schema/provider/signing identity changes.

## Previous completed task: restore RC10 interface as RC12 — 2026-10-01 UTC

Owner requested the previous version. All 8 UI files restored byte-for-byte from
RC10 backup `25f7066cbe058af226ba46befc990f2d8b30b929`; old animated purple/navy
background, translucent cards, original typography/palette/Save are restored.
Latest verified candidate: `1.5-rc12` / code `20`, signed source
`6a46483278d74439a5ec535d78dceaae196fe055`.
Run `36808449941` / job `110197933791`: SUCCESS; artifact `11138556573`.
APK SHA256 `23d5dd54f256ba865c6b5d8e976ae7c2d3226213fbc4a64d2aa608240f64f811`.
Permanent package/certificate unchanged. Debug 72/72, Release 60/60; original
signed RC11/code19 to RC12/code20 update preserves exact synthetic data. All six
instrumentation stages, offline/updater/real keyboard/Save checks passed; crash
log empty. Downloaded APK independently verified; all 29 rendered and 13 emulator
PNGs inspected with coverage limits recorded. Owner file `SalaryRC12.apk`.
Scope/evidence: `docs/android-ui-skills-rollback.md`, `docs/release-evidence/rc12/`.
Physical owner review remains. No public/stable release or cloud/website changes.

## Previous UI Skills task — RC11

RC11/code19 is completed and signed from the exact RC10 HEAD25f7066.
Pre-change remote backup: `backup/android-rc10-before-ui-skills-20261001` at
`25f7066cbe058af226ba46befc990f2d8b30b929`, verified by read-back.
Scope/evidence/recovery: `docs/android-ui-skills.md` and `docs/release-evidence/rc11/`.

Latest candidate: 1.5-rc11/code19, source b8818c5da483ed9819f7f2abd576b8e61c07f0b0.
Run36806582196/job110192154238 SUCCESS; candidate artifact11137173981.
APK SHA25668d0d70af713e69f88ede0e573dc00113493f095851a7d44ce39296fd754d506.
Package/permanent certificate unchanged. Debug72 and Release60 passed; real signed
original RC10 to RC11 upgrade preserved exact synthetic data, updater/UI/real IME
and duplicate-save checks passed; crash log empty. All29 rendered/13 emulator
PNGs inspected with the disclosed coverage limits. Downloaded APK signature,
content digest and metadata independently verified. Owner APK SalaryRC11.apk.
Physical owner review remains. No stable/public distribution or cloud/website work.

## Previous verified Android owner-review candidate — RC10

- Version: `1.5-rc10`, versionCode `18`
- Tested/signed source: `068c7fada2dad09c59c0b240c4ddb4e5bad18d20`
- Package: `com.aistudio.worktracker.qztvdw.distribution`
- APK SHA-256: `93d696b1324a8460e41a998afb52f01d174a23483ae33263ab03c5d4ebc788ee`
- Permanent certificate SHA-256: `ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`
- Final run `36786550537` / job `110129210875`: SUCCESS
- Signed artifact `11130291390`; owner APK `SalaryRC10.apk`

All eight reviewed interface refinements implemented with a recovery checkpoint.
Consolidated Debug72/72 and Release60/60; signed real original RC9/code17 to
RC10/code18 update, exact synthetic-state preservation, updater, real IME/Save,
duplicate-save and UI checks passed. Crash log empty. Independently verified
downloaded APK bytes/signature/package/version; inspected rendered/emulator PNGs.
Owner physical review remains, including animation timing and real speech/live AI.

Recovery branch: `backup/android-rc9-before-interface-polish-20260930`, exact
pre-change HEAD `3910e9689dcafa0a2dcf2fb90dff3eb46f6a076f`. Revert individual
UI changes or restore the RC9 UI through a new higher-code signed update.
Details: `docs/android-interface-polish.md`, `docs/android-signed-rc-evidence.md`
and `docs/release-evidence/rc10/`. No Stable/public release or cloud work.

Previous delivered baseline: RC9/code17, signed source
`87c288cd3f18c44669a550a0d9b5a944dd5902e3`, run36779054455, artifact11126034909,
APK SHA256 `c63dd258bc328e25204498f593b9704399723e9166c5169839e5bc4e9d751734`.

## Stage A History checkpoint — 2026-09-30

The owner requested a small Android-only checkpoint covering sections 2 and 4 of
`docs/android-final-uiux-task.md`, with no release build or version changes.

Implemented:
- Removed `יומן עבודה (N)` and combined the displayed total, filter badge,
  copy/export menu, sort menu and existing currency cycle into a compact RTL toolbar.
- Kept the selection count and all selection actions; narrow widths wrap the
  controls rather than crowding them.
- Selection rows show `WorkMoney.format(entry.totalEarnings, entry.currency)`.
  Group shifts use the same stored amount as the normal History card. No historical
  amount recalculation or financial/persistence changes were made.
- Removed two unused History menu-state variables and reused the existing money formatter.

Targeted verification:
- `:app:testDebugUnitTest --tests com.example.HistoryCompactUiTest`: 2 tests passed,
  covering 360dp/390dp RTL layout, toolbar bounds/height, ILS/USD totals, stored
  selection amounts (including a group shift with deliberately different rates),
  row selection, filters, currency cycling, all sort options and both copy actions.
- Debug app/test compilation passed as prerequisites of those two tests.
- Rendered both widths with Roborazzi and visually inspected contrast, RTL and amount readability.
- `git diff --check` passed.

RC8/code16 remains the delivered baseline. No new APK was built or signed, no
release gates were run, and release identity/configuration was left unchanged.
The checkpoint commit uses `[skip ci]` to avoid triggering the existing broad
build/signing workflow for this small UI chunk.

History checkpoint: `86268d40568d8b00c109fc5232f860a9402eabec`.
Do not redo these changes. The remaining Stage A work is recorded below.
Physical-device approval remains part of the later consolidated RC review.

## Stage A checkpoint A — 2026-09-30

Commit: `0aa82083ad3848f07a6787603c997e1169e74a5d` (pushed).

Home live-shift action now scrolls with the content and is hidden while the report
is expanded. Save retains its own measured space below the scroll viewport.
Clock date/start/end share one row; break/rate/currency share another. Manual and
group reuse the compact fields without empty clock placeholders. Notes remain
full width and group worker handling/calculations remain unchanged.

History navigation observes actual list position changes, remains visible on top
overscroll and returns at rest/end. Its measured height reserves list space without
resizing the viewport; selection actions also reserve their measured space.
The previous History toolbar and stored multi-select amounts are preserved.

Verification: `:app:testDebugUnitTest --tests com.example.StageALayoutTest`
passed 3 tests (360dp/390dp forms, draft mode switching, reachable Save/last recent
card, genuine History movement/top overscroll/final-card long press). Debug app
and test compilation passed. Six Roborazzi renders were inspected. `git diff --check`
passed. At checkpoint A, AI redesign and final polish remained pending checkpoint B.
No release gates, signed build or version changes were performed.

## Stage A completed — 2026-09-30

The remaining Android UI/UX implementation is complete. Checkpoint B includes:
- Deliberate AI typing/voice → editable transcript → explicit parse → processing →
  review → explicit save. Speech callbacks cannot parse/save. Cancel restores the
  pre-recording draft; interruptions preserve received text and ignore late results.
- Inline AI errors retain the text and mode, with correction/retry. The existing
  provider/model/personal-key mechanism and `addShifts` financial behavior are unchanged.
- Hebrew RTL polish, readable numeric fields/currency isolation, comfortable mode
  targets, and focused cleanup of duplicated form/AI UI and dead group state/unused stop callback.
- No change to the delivered RC8 version, signing/release configuration, storage,
  backup/import/export, updater, backend or website.

Final focused validation: 8/8 tests passed across `StageALayoutTest`,
`AiShiftExperienceTest`, `StageAInsetsTest`. This includes real Compose pointer/list
movement and platform-dispatched IME/gesture insets at 360dp/390dp, preserved drafts,
reachable Save/AI parse, speech callback simulation, errors/retry and explicit
review/save. App and test Debug compilation passed; generated form, AI and inset
renders were visually inspected. `git diff --check` passed. Details and limits:
`docs/android-stage-a-uiux.md`.

Both Stage A checkpoint messages use `[skip ci]`; no heavy release gate was run.
Checkpoint A was verified to have no Actions runs. Checkpoint B is the commit
containing this completion note (do not embed its own hash into its tree).

## Stage B complete — 2026-09-30

Owner authorized Stage B. Exact starting HEAD/clean tree and Stage A completion
verified. Only distribution metadata, validation tooling/test fixtures and verified
evidence changed. Stage A app/UI and all business/persistence semantics preserved.

Signed RC9 is delivered for physical-phone review. All required release/update
and synthetic-data gates passed; see the latest candidate section above and the
RC9 signed evidence. Stop here: physical OEM keyboard/gesture/speech/live AI and
actual owner installation/data review remain. No Stable/feed/friend distribution,
website/account/cloud work or launch video started.

## After the new RC is physically approved
Only after the owner explicitly says the new RC is satisfactory:
- promote the approved code to stable 1.5,
- keep the same permanent package and certificate,
- use a higher versionCode,
- run the minimum required stable release/update gates,
- publish through the existing GitHub Release/update path,
- verify the download/updater/Android installer round trip,
- produce a permanent link suitable for the owner and friends.

## Launch sequence
After stable release:
1. Create the launch/demo video.
2. Share the permanent app download link together with the video.
3. Then return to the website project.

## Relevant documents
- `AGENTS.md` — durable operating rules and lean testing discipline
- `docs/android-final-uiux-task.md` — completed Stage A scope; deferred Stage B delivery
- `docs/android-stage-a-uiux.md` — focused Stage A implementation/evidence and limits
- `docs/android-rc8-layout.md` — RC8 implementation/evidence
- `docs/android-local-distribution.md` — signing, update and distribution architecture
- `docs/android-signed-rc-evidence.md` — signed RC history/evidence
- `docs/preservation-and-sync.md` — full historical product/data contract
- `docs/website-android-handoff.md` — use only when website work resumes

## Codex handoff rule
A new Codex task should not rely on chat memory. It should derive project context from:
`AGENTS.md` → `docs/current-state.md` → `docs/android-final-uiux-task.md` → relevant evidence docs.

Before edits, always verify that those documents still match the actual branch HEAD, current code and relevant CI.

