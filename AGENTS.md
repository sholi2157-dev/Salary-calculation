# Salary-calculation — agent rules

## Source of truth
Before changing anything in this repository:
1. Read this file.
2. Read `docs/current-state.md`.
3. Read the active task/scope document named there.
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
- Verify package, versionCode/versionName, certificate fingerprint and APK SHA-256 on every delivered release candidate.
- Never claim update compatibility from a successful build alone; for a delivered RC, exercise the real signed update gate from the previous delivered RC.

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

## Code quality
When working in an Android area, inspect the relevant touched code for:
- duplicated logic/state/layout code,
- obsolete workarounds left from earlier fixes,
- dead/unreachable code,
- brittle hard-coded layout values,
- inconsistent naming or helpers,
- unnecessary complexity that makes the touched flow harder to maintain.

Clean these only when the cleanup is low-risk and directly related to the files/flow being changed.
Do not turn a focused task into a repository-wide rewrite, architecture migration or speculative refactor.
If unrelated technical debt is found, note it rather than changing it.

## Testing discipline — quality, not test volume
Do not perform broad repeated audits or run large test suites after every small edit.

Use this order:
1. Before editing, inspect existing coverage and prior CI so completed checks are not repeated without reason.
2. During implementation, run the smallest targeted checks needed for the changed area.
3. Add a new automated test only when it protects a new behavior, a reproduced bug, or a release-critical invariant that is not already covered. Do not add duplicate tests just to increase test count.
4. After all changes for a release candidate are complete, run one consolidated set of relevant Android regression/release gates.
5. For a delivered signed RC, include the real previous-RC → new-RC update/data-preservation gate and inspect crash output.
6. Do not try to replace the owner's physical-phone review with endless emulator permutations.

Avoid redundant reruns when neither code nor relevant environment changed.
If a check already passed on the exact commit, use that evidence instead of rerunning it.
The goal is strong evidence with the minimum useful set of tests, not maximum test quantity.

## How to work
For each task:
1. Confirm the requested scope and completion condition.
2. Inspect the current implementation and existing tests before editing.
3. Prefer the smallest coherent change that fixes the root cause.
4. Keep unrelated good fixes intact.
5. Use an isolated Codex task/worktree when practical.
6. Follow the testing discipline above.
7. Inspect actual CI logs/results; do not infer success from a push.
8. If a physical-device check is still required, say so clearly.

For long or risky work, create/update an execution plan before implementation. Keep plans task-specific rather than bloating this file.

## Delivery and documentation
At the end of a completed task:
- Commit and push the finished work to the intended branch.
- Update `docs/current-state.md` when the project state actually changes.
- Update the relevant evidence/scope document with verified facts only.
- Report the exact commit SHA, tests run, CI result, build identity, artifact location and any remaining manual gate.
- Do not leave important finished work only in an uncommitted Codex workspace.

## Distribution
For owner testing, deliver only a verified signed APK from the permanent distribution identity.

For friend/public distribution, use the repository's stable GitHub Release/update path rather than a temporary Codex workspace artifact. Preserve the same package/signing identity so future APKs install as updates. The Android installer remains user-controlled; no silent installation.

## Current sequencing
The intended sequence is:
1. Complete the final Android UI/UX + targeted code-quality task.
2. Deliver a new signed RC over RC8 and let the owner test it physically.
3. Resolve only any issues found in that physical review.
4. Promote the explicitly approved build to stable 1.5 using the existing permanent identity and release/update mechanism.
5. Create the launch/demo video.
6. Distribute the app link together with the video.
7. Only then resume website work on its separate branch.
