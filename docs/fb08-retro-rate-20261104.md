# FB-08: owner-approved retroactive rate rules

## Scope

Current branch: `claude/2.3-FB-08-retro-rate-android`; PR #55.
Starting HEAD: `e80b3cc9e34d6c9657210b2ce14a117f037db349`; working tree was clean.
Previous PR CI: Android preview run 41913648724 succeeded.

Owner decisions replace the original open questions in the PR:
- ON updates only unpaid plain hourly shifts at this place, in the selected currency,
  whose known rate is exactly the previous place rate OR whose rate is unknown.
- Set the new rate, amount from existing `WorkPay.money`, and `payKnown = true`.
- Paid shifts, team shifts, jobs and different known rates remain unchanged.
- Plans and a running timer keep their own rates unless explicitly edited individually.
- OFF stays the default. No preview or confirmation is introduced.
- Existing guarded undo restores rate, amount and original `payKnown`; rows paid or
  changed since the update are skipped. The workplace itself keeps the new rate.

The current Android branch contains the older website, without `web/v2/rules.js`
or the FB-08 switch. The separate Web counterpart is PR #54 on
`claude/2.3-FB-08-retro-rate-web`; its original `KisRules.retroRate`, settings write
and `restoreRates` were inspected read-only. No Web branch is pushed or merged by
this task. This document records the common intended contract, not a claim that
that separate branch has already been updated.

## Validation

- Local `npm test`: 31/31 passed (the web tests available on this branch).
- Local `npm run build`: passed.
- `git diff --check`: passed.
- Android ran in CI only: [Android preview 41927913224](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/41927913224)
  succeeded on source `301306491e505ada5e9e0aff4ec495da7b81e788`.
  `Work*Test`: 116 passed, zero failures/errors/skips; all seven `WorkRetroRateTest`
  cases passed (downloaded HTML report inspected). Debug and Preview assembly passed.
  CI Web tests: 31/31; existing Firebase-emulator integration checks: 2/2.
  All PR checks, including Vercel, succeeded for that source.
- Test reports: Actions artifact `android-test-results` (13369984320).
  CI also generated `salary-android-updated` (13370064288) and `salary-trial-1.4`
  (13370467979); these are development builds, not owner distribution candidates.
  No distribution package/version/signature change was made or signed-update gate claimed.
- `WorkRetroRateTest` now covers missing-rate completion and exact undo, paid/missing
  paid, team/missing team, other rates including near-equal rates, OFF preservation,
  unchanged weekly-plan/live-timer rates, existing money rounding and guarded undo.
- No shared fixture changed. No Android tests/builds run locally. No signed release
  candidate is delivered; release identity/version and physical update gates are outside scope.
