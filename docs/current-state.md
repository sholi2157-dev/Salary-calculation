# Current project state

Last updated: 2026-09-30

## Repository and active track
Repository: `sholi2157-dev/Salary-calculation`

Current active track: Android local distribution only.

Branch: `codex/android-local-distribution`

Current branch HEAD before this documentation update:
`f3d248233d89eb924b690ad9048c5ad4befa4467`

The website/sync work is intentionally paused on:
`codex/preserve-app-sync` / PR #1

Do not resume or modify website, Firebase, accounts or cloud sync until the user explicitly switches the project back to that phase.

## Latest Android candidate
Latest verified candidate:
- Version: `1.5-rc8`
- versionCode: `16`
- Package: `com.aistudio.worktracker.qztvdw.distribution`
- Tested app source: `912acceca3ee9d62ae4ebd91596196950bcd33a6`
- Evidence commit: `f3d248233d89eb924b690ad9048c5ad4befa4467`
- APK SHA-256: `f0fccf4325203502543f7143eb7f773854c2959d24b16c4f466d203e3b57d7a7`
- Permanent certificate SHA-256: `ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`

Latest distribution workflow:
- Run: `36666569653`
- Result: SUCCESS
- Artifact ID: `11075559765`

Verified gates on RC8 include:
- Debug unit tests: 60/60
- Release unit tests: 60/60
- Preview/release/release-instrumentation compilation
- Signed RC7 code14 → RC8 code16 in-place update on API 35
- Exact synthetic data snapshot preservation before/after update
- Edit/export after update
- Package and permanent certificate continuity
- Keyboard/focus/scroll/save regression coverage
- Duplicate-tap save protection
- Main/history navigation and history reclaim behavior
- Live-shift start/cancel-stop coverage
- Empty crash log

See `docs/android-rc8-layout.md` for the detailed evidence.

## Immediate next gate
RC8 still requires the owner's physical-phone review.

The physical review should focus on:
- keyboard opening/closing
- typing and focus while scrolling
- Save visibility and one-tap save
- bottom navigation/live-shift area
- clock/manual/group report forms
- main/history behavior
- ordinary update/install behavior on the real phone

Do not mark RC8 stable merely because CI passed.

If the owner reports a problem, fix only that Android issue, preserve all prior working behavior, increment versionCode, rerun the release/update gates, and deliver a new signed RC for physical review.

## After physical approval
The user has already authorized the subsequent stable 1.5 promotion once the Android candidate is physically approved.

That promotion must:
- keep the same permanent package and certificate
- use a new higher versionCode
- rerun release/update gates
- test the stable build over the physically approved RC binary
- prepare the stable update metadata
- publish through the existing GitHub Release/update path
- verify the download/updater/Android installer round trip
- produce a permanent link suitable for the owner and friends

Do not perform the stable/public release before physical approval.

## Launch sequence
After the Android stable release is ready:
1. Create a short launch/demo video showing the main app flows and explaining why/how to use it.
2. Share the permanent app download link together with the video.
3. Then return to the website project.

## Relevant documents
Read these as needed:
- `AGENTS.md` — durable operating rules
- `docs/android-rc8-layout.md` — current RC8 implementation/evidence
- `docs/android-local-distribution.md` — signing, update and distribution architecture
- `docs/android-signed-rc-evidence.md` — signed RC history/evidence
- `docs/preservation-and-sync.md` — full historical product/data contract
- `docs/website-android-handoff.md` — use only when website work resumes

## Codex handoff rule
A new Codex task should not rely on chat memory. It should derive project context from the repository:
`AGENTS.md` → `docs/current-state.md` → the active scope/evidence documents.

Before edits, always verify that those documents still match the actual branch HEAD and CI.
