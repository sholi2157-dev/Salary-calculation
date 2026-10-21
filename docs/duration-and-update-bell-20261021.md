# 2.0.5: duration editing, concise UI and quiet update bell

Owner authorized Android + Web changes and public release as one cumulative Android
update (includes all 2.0.4 features; older installed versions update directly).

Plan:
1. Remove always-visible instructions about typing/tapping from normal forms and
   keypad. Preserve field labels, accessibility, real validation, onboarding and
   explanations of non-obvious financial consequences.
2. Reuse clock digit editing for duration H:MM in manual/group/job entry, including
   selecting minutes without replacing hours. Keep duration bounds (24:00 allowed;
   time-of-day remains 00:00–23:59) and stored financial precision.
3. Remove automatic update dialogs. Check quietly on foreground/resume, while
   foreground periodically and on bell opening; cache results offline. Red bell
   dot and update card until installed. Explicit user starts installer.
4. Consolidated relevant Android gates, permanent signed identity and real older
   release → new update with exact data preservation; publish those exact bytes.
5. Website automatic navigation updates + matching concise duration UX; browser
   validation, stage and promote same build at the existing origin.

No real user data, keys, schema reset, uninstall or signing replacement.

Additional invariant: opening/closing a duration without typing must not mark an
imported exact decimal duration as changed. A dedicated UI regression guards it.
Obsolete in-progress candidates are superseded by the latest candidate; public
release remains a separate exact-artifact publication step.

Android exact candidate source 979e5b2b60933c02f98ab886384f8e3367589b78:
run 39676466946 SUCCESS. 144 Debug + 112 Release tests; real signed RC16 →
2.0.3 → 2.0.5 update with byte-identical data snapshots, UI/keyboard/defaults/plans,
fresh launch and clean crash logs. Code 38, permanent package/certificate unchanged.
APK SHA256 c45eef0c182f42e6273082c59280076271433761da41cda5c1cc5d56b4409860.
Exact tested APK selected for publication, never rebuilt. Publication workflow
adds test-only verification on the installed APK: synthetic cached future release,
actual red-dot pixel, bell card, no automatic dialog, resume and offline retention.

Publication/live install verification run39678748525 SUCCESS: anonymous exact APK,
actual RC16 UI manual download/installer confirmation, installed code38, unchanged
synthetic data and no same-version offer. Red-dot pixel, quiet cold start/resume
and offline future-release card tests pass. Screenshot review found the bell-card
capture preceded its sheet animation; test-only follow-up waits for the card and
update button to be displayed before capture. APK/source/signature unchanged;
[verify-update] reruns verification only, never republishing or rebuilding the APK.

Visible-card follow-up run39679679694 SUCCESS: publication skipped, only the exact
existing APK was verified again. Card and update button asserted displayed before
screenshot; red pixel, quiet startup/resume, offline notice and preserved data pass.
Permanent public release android-v2.0.5 is live; no APK rebuild/re-sign/republication.

Visual QA note: red-dot and duration screenshots are clear. Although the card and
button visibility assertions pass, the separate OS screenshot still lags on an
early sheet-animation frame; do not present it as a complete card rendering.
The standard ModalBottomSheet implementation was inspected and is unchanged.
