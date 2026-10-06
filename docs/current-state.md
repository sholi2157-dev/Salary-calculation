# Current Web track — 2026-10-06

The owner explicitly resumed the Web project. Android remains unchanged at
`codex/android-local-distribution` HEAD `b9dcba6fadfa17a370fa7b3fb8aa284a13eec1be`.
Signed RC13 is the product source of truth; attached APK digest and packaged
source revision are recorded in `docs/web-rc13-parity.md`.

Active implementation branch: `codex/web-android-rc13-parity`, isolated from
existing paused PR #1/`codex/preserve-app-sync`. Pre-rebuild web HEAD
`05794abf0b01a3e3c69d83123c48824fc7b87def` is backed up remotely at
`backup/web-before-rc13-parity-20261006`. Main is unchanged.

Read `docs/web-rc13-parity.md` for the active contract, full parity matrix,
rollback, test evidence, data/AI security boundaries and deployment gate.
Local build, 36 unit tests, existing mobile browser suite, new four-width
browser parity/22-8 migration/offline suite passed. No Android build/source change.
Physical browser native share/keyboard and live account sync remain unverified.
Web AI is isolated behind a disabled flag pending a secure server adapter.
Do not claim production replaced before a verified safe preview and promotion.

Application commit `e30160a3111c2648a1d6627a994ccfd1899f02f8` is deployed READY:
https://salary-calculation-3kk40j9ez-sholi.vercel.app/
Draft review PR: https://github.com/sholi2157-dev/Salary-calculation/pull/2
Web CI run 37437505046 passed build, 36 tests and both real-browser suites.
Live preview desktop/mobile save/reload also verified. Supplied production alias
and connected project's listed production alias returned 404; no promotion or
origin change was performed. See deployment path in the parity document.

Focused follow-up: public accounts are disabled with a preserved feature switch;
controlled same-origin Web updates now preserve drafts/timers/data. Read
`docs/web-local-updates.md` for detection/lifecycle tests and exact production
alias diagnosis. Retain the verified `salary-calculation-eta.vercel.app` origin;
its old deployment artifact must be replaced only after rollout approval.
No promotion, merge, account-data deletion or Android change is authorized here.

## Screenshot design correction — 2026-10-06

Compared the supplied current Android settings/home/history screenshots with
MainActivity's settings accordion and FormSurface. Settings now opens with all
sections closed, permits one expanded section, and uses an outlined tutorial
row, navy section surfaces and a charcoal-to-navy dialog. Removed the black
footer rectangle; sticky actions blend into the dialog, retaining accessible
Save/Cancel. Category/currency/backup titles follow Android. The web equivalent
of the system section exposes the existing safe update check; Android-only
notifications, personal AI keys and Android feedback mechanics are not added.
Shift cards use translucent near-black surfaces, navy category avatars and
visible payment status text. No financial schemas, storage, Android code,
account flags or worker lifecycle changes.

Validation: build and 42 unit tests; mobile, parity and controlled-update browser
suites. Parity verifies settings closed/reopen/exclusive expansion at 360,390,
768,1440 pixels; existing 22/8 preservation, drafts/timer, offline and update
coverage retained. Browser screenshots inspected against the attached images.
Prior state remains recoverable at commit 69adb78462025093461dfb0fa5f7e0efaa6229b7.
Production and original draft PR #1 remain untouched.

## Public release follow-up — 2026-10-06

Owner approved the screenshot correction and requested a distribution-ready
permanent link, superseding the prior no-production-promotion gate after preview
verification. Removed Settings' tutorial replay control and the final tutorial
text promising that control. First-launch onboarding remains. Its cloned bottom
navigation now stays at the viewport bottom beneath the coach card instead of
being raised 265px through the screen. Added real-browser bounds assertions and
welcome screenshots at mobile/desktop widths. No storage/Android changes.

Validation: build, 42 tests, mobile/parity/update suites, exact 22/8 fixture,
drafts/timer/offline/update preservation. Preview must pass before promoting the
same artifact to the existing salary-calculation-eta.vercel.app production origin.
Prior recoverable source: 7fb35841029f0fd3a5a6af8b00e25c65f055f831.
