# Signed local Android RC evidence

Owner confirmed permanent signing creation and two off-Codespaces backups on
2026-09-28 America/New_York. No signing key was generated or changed by this run.

Permanent package: `com.aistudio.worktracker.qztvdw.distribution`.
Public signing certificate SHA-256:
`ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`.
RSA3072, APK signature scheme v2 verified. Private signing material is only used
by the ephemeral Actions runner; source and evidence contain no signing secrets.

## Initial actual signed A→B proof

Source b9053e399ab14492824654c2b3b760ee79069e8d.
Actions run36515233428 attempt2 / job109248547720 succeeded.
Candidate A code6 and B code7, both versionName1.5-rc1 and same permanent signer.
API35 disposable emulator: installA, seed, force-stop/restart, installB with
`adb install -r`, verify, edit, backup. No data clear/uninstall.
All three instrumentation stages passed; crash log empty; offline launches passed.
Debug58/58, Release58/58, JavaScript31/31 and builds passed.
APK B SHA256 independently matched downloaded bytes to CI report:
`a43cffd1bb1a09b92a3ac87c0bd3e5b9e33a2b59156b334beb7737b0048c14ca`.
This initial binary is superseded by the final candidate below and is not the
owner install recommendation.

## Final candidate

Source: `2d6a3f9e3d24a04a3d5777253e225096b0dd9c93`.
[Actions run36519639666](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/36519639666),
job109249463729: SUCCESS. Actual logs and downloaded evidence inspected.
Owner install file: `salary-local-1.5-rc1-v7.apk` (exact candidate-b.apk bytes).
versionCode7, versionName1.5-rc1; minSdk24, targetSdk36.
APK SHA-256 (independently recomputed from downloaded bytes):
`35e078511b64d47a36977e1dffb5de374f9d1e8452b0fd20735ba3bd1a278910`.
Public certificate is the permanent fingerprint above, identical in A6 and B7.

- Required Debug58/58 and Release58/58, zero failures/errors/skips.
- JavaScript31/31 and build; Preview, unsigned Release, release instrumentation
  compilation, local-only gates and signed A/B builds passed.
- API35 seed/restart/updater/verify: four phases each OK(1 test).
- Offline app launch before/after update: Status:ok; crash log empty.
- A6→B7 `adb install -r` succeeded without clearing data.
- Exact JSON before/after update SHA-256 (both files independently compared):
  `56efd0c8bf58a29653204fdf470c007b9c766cf84518e0d33f02505886186f41`.
- Updater uses actual signed B bytes with synthetic HTTP responses. Passed newer
  version detection, SHA/package/version/signer validation, content-URI read and
  installer intent resolution; rejected bad hash and wrong version, removed
  partial download files, ignored same version/404, handled503 without data changes.
- Post-update edit and backup passed, saved historical amounts unchanged.

Durable raw synthetic evidence is in [release-evidence/rc1](release-evidence/rc1/).
[Candidate+evidence Actions artifact](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/36519639666/artifacts/11012461131)
expires2026-12-28; owner APK is also supplied directly in this session.
No APK or private signing material is committed to Git.

## Scope of retained synthetic data

- Three shifts: manual ILS, overnight clock range USD, and group USD.
- Dates, start/end, net hours including half-hour break, notes, stored earnings,
  hourly rates, category labels, paid/unpaid state and per-shift currency.
- Three categories including initial default, category rates, one worker directory
  row, group worker hours/payment and distinct worker/employer rate overrides.
- Local default category, default currency and category currency preference.
- Exact backup snapshot and pending sync-journal identities compared across update.
- Re-import adds zero duplicates; post-update edit preserves the saved earnings.
- Separate totals: ILS79.97 and USD443.12. Stored values intentionally differ from
  rate×hours to detect unwanted historical recalculation.

## Remaining owner/public release gates

Install only final candidate B side by side with the debug-signed Preview.
Export the preview JSON outside the app, import with review, compare counts and
all fields/totals per currency after restart, and keep the preview/source backup.
No existing user data was accessed during CI. Synthetic import/update evidence
does not substitute for verifying the owner's actual backup.

Review home/history/edit/search, ILS/USD shares, native Share Sheet, category
rename/delete/default, timer/background notification, draft/rotation, and external
backup save/import. Confirm normal Android unknown-source/install prompts.

Updater tests use the production parser/download/hash/package/version/signature
code with synthetic transport; they do not claim a live GitHub Release download
or a tapped physical-device installer confirmation. Complete the trusted release
hosting/download/installer round-trip before public distribution. No public
Release, tag, merge, account activation, Firebase setup or sync rollout authorized.

Future versions must use the same key/package and code>=8 after B7 is delivered.
