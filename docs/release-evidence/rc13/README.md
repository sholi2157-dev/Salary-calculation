# RC13 signed owner-review evidence

Signed/tested source: ae489c9655b1684f38be74ef0b12ff2c19e4e68e.
Version 1.5-rc13 / code21. Final documentation commit does not change Android code.

Final run 37415910804 / job112114352377: SUCCESS.
https://github.com/sholi2157-dev/Salary-calculation/actions/runs/37415910804
Candidate artifact11390889607; unit evidence artifact11390084992.
APK SHA256: 74da79a77d99e1e6d98ddca0c4b5b9b6079032cd191e57353ac2344d4c385da1.
Package and permanent certificate verified independently in apk-verification.json.
Original RC12 hash pinned and checked; originalA is never reconstructed.

Consolidated source7f69fb2: Debug80/Release64, all passed. Final focused
sourceae489c9: Debug9/Release4, zero failures/errors/skips. Actual XML counts
recorded in unit-tests.json; no broad repeated audit after isolated UI/state fixes.

Six original RC12→RC13 stages all OK: seed, offline restart, updater, upgrade
verification, normal UI/draft replay, real keyboard/Save. restart/updater/verify
snapshots match exactly. Empty upgrade users are classified in state tests.
Two separate fresh-emulator stages OK: nine actual screen/target coach marks,
completion/recreate/replay/skip/recreate, then force-stopped process cold launch.
No DB/timer mutations. Spotlight presses blocked. Both crash logs empty.
360dp device History target and label above system navigation, API target above
sticky Save/Cancel, cards clear of targets. Nine settled device PNGs in candidate
artifact; rendered 360/390/fontScale1.3 PNGs in run37415040598 unit artifact11390868044.
Targets/text inspected visually. Normal Home/Form/History/AI regression tested.

Physical owner phone installation, TalkBack, OEM cutouts/keyboard/animation pacing,
and actual voice/live AI remain manual review. No claim of personal-device data
verification; preservation was proven on synthetic emulator data. No public Release.

Exact rollback branch backup/android-rc12-before-onboarding-20261006 at
6207796903515b97dcb147ae029d4b12e8f635fe. Restore UI with a future HIGHER-code signed
update; never downgrade/uninstall/clear data/replace the permanent key.
