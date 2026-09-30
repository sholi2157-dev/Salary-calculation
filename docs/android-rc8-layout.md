# Android RC8: report/IME footer

Scope: `codex/android-local-distribution`, Android only. Website, accounts,
Firebase initialization, sync, stored financial data and permanent signing are unchanged.

## Baseline verified before editing

- Remote HEAD `e7b305cfc25bcc673bbd8e53f80beff6b7033506`.
- User-supplied RC7 is byte-identical to run `36659201636` candidate B:
  versionName `1.5-rc7`, versionCode `14`, package
  `com.aistudio.worktracker.qztvdw.distribution`.
- APK SHA-256 `8d97b5d79f8e1a585bf7ad9cb9c6acea6c80036ad0593a8080c7bbd7f75d45c7`.
- Permanent certificate SHA-256
  `ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`.
- Actual CI logs/evidence: Debug60/60, Release60/60, original update gate and UI passed.
- RC7 already removed scroll-triggered focus/keyboard dismissal; preserved.
- Reviewed the supplied 24.35-second recording `1000356716.mp4` across its timeline.
- Older local worktree at c93bb2a has an uncommitted documentation change; untouched.
  This work uses an isolated worktree at the remote Android branch HEAD.

## Structure

The outer Scaffold owns IME padding once. Its content consumes scaffold padding;
system-bar insets are separate from IME ownership. The dashboard is a weighted
vertical scroll viewport followed by a measured footer, not an overlay. Save is
outside the scroll viewport, so reaching it never requires scrolling against
bring-into-view. No fixed keyboard height, zero-height hiding, scroll-triggered
blur, delayed focus repair, or device-specific offsets.

The footer contains Save while the report is open (except AI mode), followed by
one AnimatedVisibility for the live-shift action and navigation together. The
latter collapse when IME appears. No placeholder remains. Main navigation does
not react to scroll. History retains its original scroll-driven scaffold bar,
including navigation-bar insets and reclaimed list space during scrolling.

Save validates and accepts one submission, then hides IME/clears focus, calls the
unchanged persistence callback and closes the submitted report. A synchronous
submission latch prevents repeated taps from saving the same report twice; an
explicit new report resets it. Existing amount calculations are moved unchanged.
Done only hides IME/clears focus; text and report state are preserved. Native
Compose bring-into-view remains responsible for focused fields.

Reference: https://developer.android.com/develop/ui/compose/system/insets-ui

## Verification

Initial implementation `51a3a2c4e8817eb9ee33f564e1e5d5d9d0b00325`, code15:
run `36665936726`, job `109730550801` succeeded, including real signed RC7→RC8
update, unchanged exact synthetic snapshots, prior DistributionUiTest, and new
ReportKeyboardTest (actual IME visibility, repeated opening/Done, focus during
scroll, clock/manual/group/AI, settings-app round trip, one pointer save,
report reopening, main/history navigation and history mid-drag space recovery).

Final candidate code16 additionally exercises pointer double-tap saves in clock
and group modes, and starting a live shift/cancelling its stop dialog. Final
results must be recorded after its own CI completes; code15 is not delivered.

## Owner gate and subsequent release authorization

Deliver only the final verified signed RC APK. Stop for owner's physical phone
review of keyboard, typing, scrolling, Save, bottom area, clock/manual/history.
No public Release or stable metadata before a clear approval of that APK.
The owner explicitly authorized, after that approval, stable1.5 promotion with
new versionCode, repeated release gates, same package/certificate, update metadata
and existing GitHub distribution, download/updater checks and a permanent link.
Do not ask for that authorization again; do not mark an RC as stable. Test that
future stable build over the delivered RC8 binary as well. No launch-video work.

## Final delivered RC8 verification — 2026-09-30 UTC

- Tested source `912acceca3ee9d62ae4ebd91596196950bcd33a6`.
- Run `36666569653`, job `109732388175`: SUCCESS. Actual logs and downloaded
  artifact `11075559765` inspected; source/hash/package/certificate checked locally.
- Version `1.5-rc8`, code `16`; intermediate signed code15 was never delivered.
- APK SHA-256 `f0fccf4325203502543f7143eb7f773854c2959d24b16c4f466d203e3b57d7a7`.
- Package and permanent certificate equal the RC7 values above.
- Debug60/60 and Release60/60: zero failures/errors/skips. Preview/release and
  release-instrumentation compilation passed. Existing CI also ran its unchanged
  JS31/build gates; no website files or implementation were changed.
- API35 disposable emulator: RC7 code14 installed, synthetic data seeded,
  force-stop/restart/updater validation, actual `adb install -r` code16, exact
  before/after snapshot equality, edit/export and unchanged identities passed.
  No uninstall, clear/reset, destructive migration or real user data operation.
- DistributionUiTest and ReportKeyboardTest passed. Real IME visibility and focus
  across scrolling, six keyboard open/Done cycles, clock/manual/group fields,
  AI draft/Done, Android Settings round trip, direct one-tap save with IME,
  clock/group rapid double taps producing exactly one entry each, report
  close/reopen, history mid-drag reclaim/return, main/history navigation,
  starting a live shift and cancelling stop were all exercised.
- Crash log empty. Actual screenshots inspected: Save entirely above the IME;
  live/navigation hidden as one group and returned below Save when IME closed.
- No public release, stable update manifest, merge, website deployment, account
  activation or video work. Await owner's physical APK review.
