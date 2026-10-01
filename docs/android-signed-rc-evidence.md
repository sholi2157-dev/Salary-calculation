# Signed local Android RC evidence

## RC10 interface polish completed — 2026-09-30

Owner-review candidate: `1.5-rc10`, versionCode `18`.
Tested/signed source: `068c7fada2dad09c59c0b240c4ddb4e5bad18d20`.
[Actions run 36786550537](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/36786550537), job 110129210875: SUCCESS.
Package: `com.aistudio.worktracker.qztvdw.distribution`.
APK SHA-256: `93d696b1324a8460e41a998afb52f01d174a23483ae33263ab03c5d4ebc788ee`.
Certificate SHA-256: `ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`.
Permanent owner signing, RSA3072/v2 verified. Downloaded APK bytes, signature,
certificate fingerprint, package and version independently verified locally.

### Final-source verification

- Debug72/72 (Work* plus existing rendered Stage A/History tests and the two
  new InterfacePolishTest cases), Release60/60 (Work*), zero failures/errors/skips.
  Counts independently recomputed from downloaded JUnit XML and matched JSON.
- Debug/Release compilation, release instrumentation build, local-only gates and
  permanent signing passed. No website tests or production test-host changes.
- Two new360dp/390dp RTL rendered tests cover readable paid/pending labels,
  fully visible/disjoint48dp action targets, pointer callbacks and worker-payment
  updates preserving stored earnings/currency. Inspected all29 rendered PNGs
  and13 API35 emulator screenshots, including actual keyboard/Save and live shift.
- Actual original RC9/code17 APK, pinned SHA
  `c63dd258bc328e25204498f593b9704399723e9166c5169839e5bc4e9d751734`,
  upgraded to RC10/code18 with `adb install -r`, without uninstall or clearing data.
- Seed/restart/updater/verify, DistributionUiTest and ReportKeyboardTest:
  six instrumentation phases, each OK(1 test). Offline starts before/after passed.
- Exact synthetic before/after snapshot equality. SHA-256 of both JSON files:
  `c8605641fbebcf13b217e6c826ea0a563c88e446f759c92e08a3ea580d9a061f`.
  Three shifts, separate ILS79.97/USD443.12 totals, categories, workers, payment
  states, preferences and pending-journal identities preserved. Post-update edit,
  backup/re-import and duplicate-save checks passed. No live user data accessed.
- Final crash-log.txt is empty (0 bytes).

Raw evidence: [release-evidence/rc10](release-evidence/rc10/).
[Signed candidate/evidence artifact](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/36786550537/artifacts/11130291390).
Rendered/unit artifact: 11130665251.
Owner delivery: `SalaryRC10.apk`, exact candidate-b.apk bytes,19,182,227 bytes.

### Recovery and manual gate

Pre-change source HEAD `3910e9689dcafa0a2dcf2fb90dff3eb46f6a076f` is preserved
and verified on `backup/android-rc9-before-interface-polish-20260930`.
Individual UI refinements can be reversed independently; restore the RC9 UI
through a newly signed higher-code update, preserving package, signer and data.
Do not downgrade the installed APK or reset/force-push the release branch.
Implementation scope and disclosed failed intermediate checks:
[android-interface-polish.md](android-interface-polish.md).

Owner physical-phone review remains: actual OEM keyboard/gestures, scrolling,
press/expansion timing, live timer, real microphone/speech recognition and live AI
with the owner's own key. Emulator animations are disabled, so motion quality
is a physical review item. No Stable/public release, feed update or cloud work.

## RC9 Stage B completed — 2026-09-30

Owner-review candidate: `1.5-rc9`, versionCode `17`.
Tested/signed source: `87c288cd3f18c44669a550a0d9b5a944dd5902e3`.
Starting Stage A HEAD: `a83fa7e2cccac6278ac996f69ffd8a1137083c6d`, verified on
`codex/android-local-distribution` with a clean tree and all Stage A commits present.
No public releases existed and latest distributed code was 16; code17 was unused.

Package remains `com.aistudio.worktracker.qztvdw.distribution`.
APK SHA-256: `c63dd258bc328e25204498f593b9704399723e9166c5169839e5bc4e9d751734`.
Certificate SHA-256: `ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`.
Permanent owner Actions signing only, RSA3072/v2 verified; no new key or secrets
exposed. Downloaded APK hash and embedded DER certificate fingerprint independently
recomputed locally and compared to RC8 and CI reports.

### Consolidated validation and focused follow-up

- Run `36778187395`, source `abe8b757886e0ef8d2ca28763e1c70df53a69987`:
  Debug70/70 (Work* plus 10 existing Stage A/History tests), Release60/60 (Work*),
  zero failures/errors/skips. Debug compilation, unsigned Release compilation,
  release instrumentation compilation and local-only/package gates passed.
  Unit evidence artifact `11126731854`. No website/JS tests were run.
- That run's real RC8→RC9 install, four update fixture phases, exact JSON equality,
  updater and DistributionUiTest passed. ReportKeyboardTest then failed because
  the six-entry fixture could fit in compact History at 360dp: no real scrolling
  meant navigation correctly remained visible. This was a test-fixture failure,
  not an app failure. Twenty synthetic scroll fixtures are now added only after
  update preservation assertions. The old changing-viewport assertion now matches
  Stage A's stable viewport; active control assertions match its hidden form state.
- Final run `36779054455`, job `110104223678`: SUCCESS. Focused existing UI tests
  only (10/10) rerun with recording enabled because initial capture calls produced
  no PNGs without the Roborazzi recording property. All 25 generated renders
  visually inspected: Home, Clock/Manual/Group, AI recording/processing/error/review
  at 360dp/390dp; History final item and toolbar/ILS/USD selection at both widths.
  Visual/unit artifact `11127157426`. No repeat broad Debug/Release suite; app code
  is identical to the consolidated-test source (only instrumentation/tooling changed).
- Final signed Release and release test APK compiled. API35 emulator, 360dp,
  actual IME, pointer saves/double taps, app Settings round trip, mode/draft
  recreation, active-shift start/cancel, genuine History movement with stable
  viewport, returned navigation, complete final card and long press passed.
  DistributionUiTest and ReportKeyboardTest each OK(1 test).
- Simulated platform IME/gesture insets at 360dp/390dp passed in StageAInsetsTest;
  actual emulator uses its default three-button navigation. No claim of physical
  gesture-navigation/keyboard animation or physical microphone verification.

### Required real RC8 update and retained data

Original RC8 artifact from run `36666569653`, artifact `11075559765`:
version16, SHA-256 `f0fccf4325203502543f7143eb7f773854c2959d24b16c4f466d203e3b57d7a7`.
Pinned hash validated before installation, with the same package/certificate.
Real `adb install -r` RC9/code17 returned Success. No uninstall, clear/reset,
destructive migration or owner's records used.

Seed/restart/updater/verify phases each OK(1 test). Exact pre/post update snapshots
match, SHA-256 `f8536bc306fb98c03a6c70c7fa638d8fc22d4c15494a5eb07396e5b9bf84f798`.
Preserved three representative shifts (manual ILS, overnight USD, group USD),
categories/rates, stored non-recalculated amounts, dates/hours/notes, paid/unpaid,
worker directory/group rates/payment, local default category/currency/category
currency preferences and pending journal identities. Re-import adds zero;
post-update edit/export preserves historical amounts. ILS79.97/USD443.12 retained.
In-memory form drafts were not claimed to survive package replacement/process death;
expected mode-transition/activity-recreation draft behavior is covered separately.

Offline starts before/after upgrade passed. Final crash-log.txt is empty (0 bytes):
no unexpected crashes/fatal exceptions or install/update errors in the final run.

Raw verified synthetic evidence: [release-evidence/rc9](release-evidence/rc9/).
[Signed candidate/evidence artifact](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/36779054455/artifacts/11126034909).
Signed candidate also delivered as `Salary-Calculation-1.5-rc9-v17.apk`.
No APK/private key is committed to Git. No UI implementation/redesign, financial,
storage, share/backup, provider/key, timer, cloud/account or website change.
No Stable release, feed update, friend distribution, merge or launch video.

### Owner physical-phone gate

Review actual OEM keyboard/gesture navigation, scrolling/focus/Save and all report
modes; physical microphone, installed speech-recognition service, editable voice
transcript and live parsing/review with the owner's own key. AI tests use simulated
speech/provider states and a real missing-key error; no live owner-key call made.
Physical installer confirmation and actual existing-user data review remain owner
checks. Stop here; Stable promotion requires explicit approval of this RC.


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


## RC11 UI Skills owner review — 2026-10-01 UTC

- Source b8818c5da483ed9819f7f2abd576b8e61c07f0b0, version1.5-rc11/code19.
- Run36806582196/job110192154238 SUCCESS; artifact11137173981.
- Same permanent distribution package and certificate. Independent local v2
  signature/content-digest, manifest/source and SHA verification passed.
- APK SHA25668d0d70af713e69f88ede0e573dc00113493f095851a7d44ce39296fd754d506.
- Debug72/Release60, no failures/errors/skips. Original RC10/code18 to RC11/code19
  signed installation, exact synthetic snapshot equality, offline/updater/UI/real
  keyboard/Save/duplicate-save checks passed. Crash log0 bytes.
-29 rendered PNGs and13 emulator PNGs inspected; actual coverage/limits documented
  in android-ui-skills.md. Motion/physical OEM/speech/live AI remain owner checks.
- Pre-change remote backup branch backup/android-rc10-before-ui-skills-20261001,
  exact HEAD25f7066cbe058af226ba46befc990f2d8b30b929. Restore UI via a HIGHER-code
  signed update; no uninstall/downgrade/reset or forced release-branch rewrite.
- Owner APK SalaryRC11.apk; original source/APK recovery archive
  Salary-RC10-before-ui-skills.zip. Raw evidence docs/release-evidence/rc11/.
- No stable/public release, feed change, website/cloud work or launch video.
