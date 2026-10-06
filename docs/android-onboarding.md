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

Final signed source: ae489c9655b1684f38be74ef0b12ff2c19e4e68e.
Run37415910804/job112114352377 SUCCESS; candidate artifact11390889607.
APK SHA256 74da79a77d99e1e6d98ddca0c4b5b9b6079032cd191e57353ac2344d4c385da1.
Independent APK v2 RSA signature, complete content digest, manifest and original
permanent certificate verified after download. Detailed reports: docs/release-evidence/rc13/.

Consolidated regression run37413045161/source7f69fb2: Debug80/Release64 all passed.
Final focused run: Debug9/Release4, zero failures/errors/skips. State and Compose
coverage includes fresh/empty upgrade/restored data, completion/skip/replay,
Back/Next, immediate Activity save after replay dismissal, 360/390dp and inherited
fontScale1.3, RTL order, target geometry, blocked taps, exact unchanged snapshots.
Controller Saver reads current index directly, avoiding stale asynchronous mirrors.
Fullscreen dialog is constrained to host viewport and uses host safeDrawing insets.
API target is above sticky Settings actions; History and label above Android bars.

Final original RC12→RC13 signed update gate: six stages passed, exact synthetic
snapshots preserved, offline start/updater/normal UI/real IME/Save verified. Normal
Settings feedback and group form drafts survive replay. Separate fresh signed
emulator: all nine steps, complete/recreate, replay/skip/recreate, force-stop and
cold launch passed. No saved demo shift, timer mutation or share dispatch. Both
crash logs empty. Native and rendered target/text layouts visually inspected.
Physical phone, TalkBack, OEM cutout/keyboard/animation timing and live voice/AI
remain owner review. No public/stable Release published.

Rollback: restore onboarding UI/source from the named baseline through a NEW,
higher-versionCode signed update. Do not install a lower-code APK, uninstall,
clear data, replace the key or revert schema/financial data.
