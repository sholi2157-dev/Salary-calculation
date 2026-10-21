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
