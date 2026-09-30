# Salary-calculation — agent rules

## Source of truth
Before changing anything in this repository:
1. Read this file.
2. Read `docs/current-state.md`.
3. Read the scope document named there for the active track.
4. Inspect the actual branch, HEAD, working tree, recent commits and relevant CI. Do not repeat work that is already complete.

Current user instructions always override older historical notes.

## Active priority
Until the user explicitly says otherwise, finish the Android app first.

Active Android branch:
`codex/android-local-distribution`

Paused website/sync branch:
`codex/preserve-app-sync` / PR #1

While Android finalization is active:
- Android only.
- Do not modify the website, Vercel, Firebase, accounts, authentication or cloud sync.
- Do not mix commits from the website/sync branch into the Android release track.
- Do not merge branches or publish a public release unless the user explicitly authorizes that step.

## Android release identity — never break
Permanent distribution package:
`com.aistudio.worktracker.qztvdw.distribution`

Rules:
- Never change the package/application ID for the distributed app.
- Never generate or substitute a new signing identity.
- Never commit, upload, print or expose keystores, private keys, passwords or encoded signing material.
- Reuse the existing owner-controlled permanent signing identity through the configured GitHub Actions secrets.
- Every delivered update must have a versionCode greater than every previously delivered build.
- Verify package, versionCode/versionName, certificate fingerprint and APK SHA-256 on every release candidate.
- Never claim update compatibility from a successful build alone; exercise a real signed A→B/update gate.

## User data safety
Preserve all existing user data and behavior.
- Never uninstall, clear app data, reset storage or use destructive migration as a shortcut.
- Never recommend uninstalling to solve an update/install problem.
- Use synthetic data in automated tests; never modify the user's live records.
- Preserve historical shifts, stored amounts, currencies, categories, worker/group data, payment state, preferences and migration identities.
- Category deletion must not delete historical shifts; affected shifts move to the configured default category while stored financial data remains unchanged.
- Do not silently recalculate historical stored amounts when metadata such as category name/rate/default changes.

## Product behavior to preserve
The detailed product contract lives in `docs/preservation-and-sync.md`.
Important durable rules include:
- RTL-first Android UI with Heebo.
- Main/history navigation behavior must remain intact.
- Currency is stored per shift; ILS and USD totals stay separated.
- Configurable default category; `עצמאי` is not mandatory.
- Search has separate clear and close actions.
- Long-press multi-select and Android Share Sheet behavior remain native.
- Drafts must survive expected UI transitions.
- AI uses the user's own provider key only; never embed owner credentials.
- Android local distribution remains usable without accounts, Firebase or cloud sync.

## How to work
For each task:
1. Confirm the requested scope and completion condition.
2. Inspect the current implementation and existing tests before editing.
3. Prefer the smallest change that fixes the root cause.
4. Keep unrelated good fixes intact.
5. Use an isolated Codex task/worktree when practical.
6. Run the relevant unit, build, instrumentation/update and regression gates.
7. Inspect actual CI logs/results; do not infer success from a push.
8. If a physical-device check is still required, say so clearly.

For long or risky work, create/update an execution plan before implementation. Keep plans task-specific rather than bloating this file.

## Delivery and documentation
At the end of a completed task:
- Commit and push the finished work to the intended branch.
- Update `docs/current-state.md` when the project state actually changes.
- Update the relevant evidence/scope document with verified facts only.
- Report the exact commit SHA, tests run, CI result, build identity, artifact/release location and any remaining manual gate.
- Do not leave important finished work only in an uncommitted Codex workspace.

## Distribution
For owner testing, deliver only a verified signed APK from the permanent distribution identity.

For friend/public distribution, use the repository's stable GitHub Release/update path rather than a temporary Codex workspace artifact. Preserve the same package/signing identity so future APKs install as updates. The Android installer remains user-controlled; no silent installation.

## Current sequencing
The intended sequence is:
1. Finish and physically approve the Android app.
2. Resolve any final Android-only issues.
3. Promote the approved build to the stable 1.5 release using the existing permanent identity and release/update mechanism.
4. Create the launch/demo video.
5. Distribute the app link together with the video.
6. Only then resume website work on its separate branch.
