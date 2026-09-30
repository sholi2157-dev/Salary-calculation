# Current project state

Last updated: 2026-09-30

## Repository and active track
Repository: `sholi2157-dev/Salary-calculation`

Current active track: Android local distribution only.

Branch: `codex/android-local-distribution`

The website/sync work is intentionally paused on:
`codex/preserve-app-sync` / PR #1

Do not resume or modify website, Firebase, accounts or cloud sync until the user explicitly switches the project back to that phase.

## Latest verified Android baseline
Latest delivered/verified baseline:
- Version: `1.5-rc8`
- versionCode: `16`
- Package: `com.aistudio.worktracker.qztvdw.distribution`
- Tested app source: `912acceca3ee9d62ae4ebd91596196950bcd33a6`
- Evidence commit: `f3d248233d89eb924b690ad9048c5ad4befa4467`
- APK SHA-256: `f0fccf4325203502543f7143eb7f773854c2959d24b16c4f466d203e3b57d7a7`
- Permanent certificate SHA-256: `ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`

Latest RC8 distribution workflow:
- Run: `36666569653`
- Result: SUCCESS
- Artifact ID: `11075559765`

Verified RC8 evidence includes Debug 60/60, Release 60/60, signed RC7→RC8 update/data-preservation, keyboard/focus/save coverage, duplicate-save protection, navigation/history behavior and empty crash log.

See `docs/android-rc8-layout.md` for detail.

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

## Next stage — deferred, requires the owner's instruction

Stop after Stage A. Stage B has not started. The delivered baseline remains RC8/code16.

Stage B will cover the new RC version/build/signing, release gates, actual signed
RC8 → new RC update/data preservation, crash evidence and owner physical review.
Physical keyboard animation/flicker, microphone/service recognition and live AI
with the owner's personal key are not established by Robolectric/inset simulation.
No emulator/hardware microphone or live provider request was used in Stage A.
Do not promote RC8 directly to stable or start Stage B automatically.

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
