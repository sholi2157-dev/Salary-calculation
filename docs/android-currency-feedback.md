# RC14 currency, cancellation and feedback

Baseline: 51ca2908349681141f17461399615ea00f88b870; RC13/code21.
Android-only owner-requested update, candidate 1.5-rc14/code22.

- Saved shift editor passes explicitly selected currency into the existing
  edit pipeline. Currency-only corrections preserve stored earnings and metadata;
  they do not convert monetary values at an exchange rate.
- Timer setup shows currency, preserves an explicit choice, and passes it through
  the start callback. Missing category currency falls back to the saved global
  default. Existing category currency remains an explicit override.
- Active timer currency updates persist without replacing start/rate/category.
- Cancel prompts before discarding. Only confirmation records an atomic timer
  discard and stops service; prior entries remain. Stale timer callbacks cannot
  cancel a newer timer or resurrect a discarded timer.
- Settings section toggling is confined to its header. Dialog scrim accepts
  pointer taps only, and feedback uses multiline/default IME, preventing Enter
  from activating ancestor click targets.

Verified owner-review candidate: signed source
4267f5e0083f1b945e7d3642d803d15d222f0bdf, run37888789674/job113684763786 SUCCESS,
artifact11598265150. APK SHA256
588e058c21f3a1a42de88d7a33028ebce2c2ec302ce3deb5d8b6d6874854f627.
Independent downloaded APK v2 RSA signature, complete content digest, package,
versionCode22 and permanent certificate verified locally.

Consolidated regression at743bea: Debug84/Release67, zero failures/errors/skips.
Final exact signed source: focused Debug9/Release4, zero failures/errors/skips.
Nine native stages passed: seed, restart, real signed updater validation,
in-place RC13-to-RC14 update verification, existing UI, currency/feedback,
real keyboard, fresh onboarding and completed cold launch. Exact synthetic
upgrade snapshots match; both crash logs empty. Native tests prove global USD
fallback with an unconfigured category, active currency persistence without
restarting, cancel warning/continue/confirm with no stored shift, currency-only
editing with unchanged imported amount, and hardware Enter inserts a newline
without closing feedback or Settings.

Initial native test exposed an Espresso unfocused Activity root after replay;
changed its close action to a real Settings outside tap with a dismissal assertion.
The next native run reproduced feedback collapse: actual ManagementScreen cards
also had ancestor click handlers. These are now confined to headers. The original
Enter regression assertion was retained and passes. No test bypass or data reset.

Owner file SalaryRC14.apk saved; the existing Drive file was updated in place,
retaining its URL and all existing individual reader/owner permissions. No public
or anyone permission added. Drive file ID1jDiOB-ByrQFs3CBzHehJEZSgl9ZKvCY_;
Library file libfile_e2aaf24de1dc8191861792a429ddafe4.
Evidence: docs/release-evidence/rc14/verified-apk.json and validation.json.
No stable/latest feed or public publication included. Owner physical-phone review
remains required after signed candidate delivery.
