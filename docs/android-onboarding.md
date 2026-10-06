# Android RC13 first-launch onboarding

Exact clean baseline: 6207796903515b97dcb147ae029d4b12e8f635fe, RC12/code20.
Local and remote rollback branch: backup/android-rc12-before-onboarding-20261006.
The supplied APK matches the original RC12 Actions artifact SHA256
23d5dd54f256ba865c6b5d8e976ae7c2d3226213fbc4a64d2aa608240f64f811.

Scope: Android only. No payroll/schema/money/provider/signing/package/website changes.
RC13/code21. Existing permanent Actions identity only; original signed RC12→RC13
update gate, never reconstructed A. No public/stable release requested.

Nine steps: welcome; new report/live-timer distinction; real compact form; actual
AI input/explicit parse/review/save explanation; History; expanded actual shift
share button (in-memory labelled example when empty); categories/default currencies;
Settings→system/feedback→personal API key; finish.

Version1 device-local SharedPreferences state, committed on complete or skip.
Installation classified before Room opens. Existing installation timestamps,
restored database/DataStore/timer/setup evidence suppress a forced initial tour.
Replay never resets completion or payroll preferences. Interrupted initial tour
reopens until completed/skipped; Activity state preserves its current step.

Tutorial renders existing components in a separate read-only composition inside
a system-inset-aware modal. Normal app stays composed underneath, preserving drafts,
scroll and selection. Targets register measured clipped bounds and bring-into-view
requests. Bottom card is measured to reserve space on pointing steps; text scrolls
at larger font sizes. Missing targets disable Next until visible; skip/back remain.
Dimmed/hole UI consumes touches and clears background accessibility semantics.
Normal save/start/category callbacks are replaced with no-ops; share intents also
explicitly disabled in tutorial composition. No real API key displayed or changed.
First-run notification request deferred to existing live-shift permission flow;
optional AI setup is introduced by tutorial instead of interrupting startup.

Focused verification passed on 6c946d6ff8841d579e7de4e7a3c0c1d109c3a9a9:
4 state tests and 4 Compose tests (complete 360dp/390dp tours, actual inherited
fontScale1.3 and Settings replay). Real target geometry, blocked spotlight taps,
Back/Next, completion/skip/replay persistence and unchanged DB snapshots passed.
API target is checked above the sticky Settings actions. Rendered nine-step
sequences and enlarged text were visually inspected; initial overlay collision
was corrected. Normal Settings/form drafts remain composed during replay.

Signed original RC12 update, normal offline/UI/real IME, signed fresh-install
emulator and independent downloaded APK verification pending final gates.
Physical phone/TalkBack/animation pacing remain owner review after automated gates.

Rollback: restore onboarding UI/source from the named baseline through a NEW,
higher-versionCode signed update. Do not install a lower-code APK, uninstall,
clear data, replace the key or revert schema/financial data.
