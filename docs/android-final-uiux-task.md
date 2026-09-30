# Final Android UI/UX and targeted quality pass

Status: STAGE A IMPLEMENTATION COMPLETE; STAGE B RELEASE VALIDATION DEFERRED
Baseline: RC8, versionName `1.5-rc8`, versionCode `16`
Branch: `codex/android-local-distribution`
Scope: Android only

Stage A completion/evidence: `docs/android-stage-a-uiux.md`. The owner explicitly
split implementation from release delivery: no version changes, signed RC, release
gates or public release during Stage A. The delivery requirements below belong to
Stage B and must not start automatically.

## Required preparation
Before changing code:
1. Read `AGENTS.md` and `docs/current-state.md`.
2. Verify the actual branch, HEAD, recent commits and relevant CI.
3. Read `docs/android-rc8-layout.md` and the relevant Android distribution/evidence docs.
4. Inspect the current implementation, not only old documentation.
5. Review every screenshot and video attached to the Codex task in full before visual changes. Those new attachments are the visual source of truth. Do not use older app recordings in the repository as design reference when they conflict.
6. If the required current visual attachments are missing, stop before UI implementation and request them.

Do not repeat a completed audit.
Do not touch website, Vercel, Firebase, Accounts or Cloud Sync.
Do not redesign unrelated areas.
Do not undo working RC8 fixes.

## 1. Home bottom area / active-shift action / space usage
The current `התחל משמרת פעילה` area behaves like a large fixed bottom shelf and wastes vertical space.

Target:
- Main content should use the screen almost down to the `ראשי / היסטוריה` navigation.
- No large permanent dead space.
- Recent-shift cards must not be cut off.
- The last recent shift must be fully viewable.
- The active-shift action should feel integrated, compact and stable.
- It may shrink/hide/reposition/overlay carefully during scrolling or while a new-report form is open, if that is the best Compose solution.
- It must not cover content or look like a workaround.
- While a report form is open, it must not interfere with filling/saving the form.
- Preserve or improve the RC8 keyboard/IME behavior. Do not reintroduce focus jumps, inaccessible Save, keyboard flicker or content trapped behind IME.

Choose the best Compose structure; do not blindly follow a fixed layout prescription.

## 2. History top area density
Remove `יומן עבודה (N)` completely.

Move `סה״כ מוצג: …` into that top control area.

Combine the history controls into one compact RTL toolbar:
- displayed total,
- filter control,
- existing sort/copy/all/related actions.

Do not remove functionality.
Avoid a separate full-height `סינון` row, unnecessary title or duplicate information.
Maximize vertical room for the shift list.

Selection mode must still clearly show `נבחרו X משמרות` and its actions.

## 3. Compact “new report” forms
Reduce vertical height substantially while keeping touch targets/readability.

Apply a consistent compact design to clock/manual/group modes and shared form pieces.

Clock mode:
- one compact row for date | start | end,
- another compact row for break | rate | currency,
- compact, natural RTL category/employer control,
- notes remain full width.

Manual mode:
- compactly arrange date, hours, rate, currency and category without leaving empty holes based on clock-mode structure.

Group mode:
- avoid full-width giant rectangles for small values,
- preserve readability, touch targets and RTL,
- work at 360dp and 390dp.

Do not change payroll calculations, breaks, overnight behavior, rates, currencies, workers, draft persistence or validation semantics.

## 4. Multi-select history: show each shift amount
In selection mode, each selected/selectable row must show the stored amount and correct currency for that shift in addition to category/employer and date.

Example concept:
`חיידר אידיש · ₪320.00`

Use the app's existing financial meaning for group shifts.
Do not recalculate historical data or change financial semantics.
Mixed ILS/USD must remain correct.

## 5. History scrolling behavior
Fix both problems shown in the attached current history video.

Top:
- a small overscroll gesture at the true top must not hide the bottom navigation.
- collapse only on real content scrolling in the direction that actually moves the list.
- do not rely only on `isScrollInProgress`.
- use stable LazyListState/scroll-direction/canScroll state or equivalent.

Bottom:
- when the final item is reached and bottom navigation returns, the full last card must remain visible, readable, tappable and long-pressable.
- use dynamic bottom content inset/padding based on the actual navigation area.
- no huge fixed padding or device-specific hardcode.
- work both when nav is hidden and when visible.

General:
- nav visible at rest,
- may hide during real list scrolling,
- returns when scrolling ends,
- no flicker, top overscroll collapse, covered last item or aggressive list jump.

## 6. AI entry flow redesign
Redesign the AI add-shift experience within the existing dark/navy/indigo/purple language.

Core flow:
`Voice or typed text → editable transcript/text → explicit AI parse action → visible processing state → review result → user saves`

Requirements:
- one obvious free-text input area,
- clear microphone action,
- polished listening state with restrained pulse/wave/glow and stop/cancel,
- speech result must land in a normal editable text field before AI parsing,
- user can read/correct/delete/add text,
- explicit action such as `פענח עם AI` or a better Hebrew label,
- processing state should be visually intentional, not only a generic tiny spinner,
- preserve text on parse failure and show a friendly inline error,
- successful parse must still go through the existing review mechanism before saving.

Do not change:
- AI provider,
- selected AI model,
- personal API-key mechanism/security,
- Firebase/server/cloud sync,
- key storage.
Never add an owner key to source.

## 7. RTL and quality
The changed UI must feel native to Hebrew:
- natural RTL,
- sensible icon/dropdown placement,
- readable times/numbers/currency,
- consistent spacing,
- comfortable touch targets,
- no English-default alignment artifacts.

Verify layouts at least at 360dp and 390dp, including gesture navigation and open keyboard for affected screens.

## 8. Targeted code-quality pass
As part of implementing the areas above, inspect the relevant Android UI/state code you touch for:
- duplicate state or logic,
- duplicate composables/helpers,
- dead code,
- obsolete workarounds from prior iterations,
- brittle hard-coded spacing/height tricks,
- needless complexity,
- inconsistent naming or structure.

Clean such issues when the cleanup is clearly safe and helps the changed flow.
Do not perform a repository-wide audit or refactor.
Do not alter business logic or stable persistence/release architecture just for cleanliness.
If unrelated technical debt is discovered, record it separately and leave it unchanged.

## 9. Preserve existing behavior
Do not change package, signing identity, database identity, stored financial semantics, backup format, import/export, share contents, updater behavior, foreground timer logic, payment status, category deletion semantics, historical data or personal API-key security.

No uninstall, clear data, reset or destructive migration.
The new RC must install over RC8 and preserve data.

## 10. Lean validation plan
Follow `AGENTS.md` testing discipline.

Do not run giant suites after every edit.
Use targeted checks during Stage A implementation. Its final focused validation
is recorded in `docs/android-stage-a-uiux.md`; the consolidated release pass below
belongs to Stage B.

At minimum, the final release-candidate validation must cover the changed behavior:
- Home: bottom-space behavior, recent shifts, report open/close, scroll, active shift, keyboard open/closed.
- Forms: clock/manual/group/AI, compact 360dp fit, pickers, rate/currency/break/category/notes/save.
- History: top overscroll, normal/fast/slow scrolling, last item with nav returned, long press, selection amount/currency, filters/sort/search/paid/delete.
- AI: typed input, voice, editable transcript, cancel/finish recording, processing, success/failure, review/save, keyboard.
- no duplicate save.
- real signed RC8 → new RC update with preserved data.
- crash log.

Add automated tests only where existing coverage does not protect the new/reproduced behavior. Prefer a few strong regression tests over many redundant tests.

## 11. Stage B delivery — deferred
Only after the owner authorizes Stage B:
1. Increase versionCode above 16.
2. Use a new RC versionName, not Stable.
3. Keep the same permanent signing identity.
4. Run one consolidated set of relevant Android release gates.
5. Verify the real RC8 → new RC signed update/data-preservation path.
6. Inspect affected screens visually in emulator; save useful screenshots if practical.
7. Produce a signed APK for owner download/testing.
8. Do not publish publicly, do not create a public stable Release, and do not update the public stable feed.
9. Commit and push the completed work.
10. Update `docs/current-state.md` and relevant evidence with verified facts only.

Stop after delivering the APK and a short summary containing:
- what changed,
- versionName/versionCode,
- commit SHA,
- tests/gates actually run,
- whether RC8 → new RC preserved data,
- any remaining physical-phone checks.

Do not work on the launch video yet. Stable release and launch video happen only after explicit owner approval of the new RC.
