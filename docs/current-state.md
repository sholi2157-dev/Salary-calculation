# Combined 2.0.4 release — 2026-10-21

Owner authorized both platforms, merge and public release. All functional changes merged:
Android PR #8 → codex/android-local-distribution; Web PR #9 → codex/download-and-management-20261020.
Full Hebrew changelog: `docs/releases/2.0.4.md`. Current evidence: `docs/shift-entry-20261021.md`.

Android tested source 18ae87f9cc1285b5fc2767511a6d950859a8e00d, run 39668563320 SUCCESS:
141 Debug + 110 Release tests; real signed previous-release update, preserved data, fresh launch,
Release UI/keyboard/default-workplace/plan checks and clean crash logs. Package/certificate unchanged.
Selected APK 2.0.4 (37), SHA256 54cef6ca017da5ce09bb9a3974990cc951eaf0b38d58bb7ec89e423bf967e1ee.

Web tested source 96e3e4bb52a6a693fb728c682295934b724fa7c3, run 39669064791 SUCCESS:
92 tests, shared fixtures and complete v2/download/sharing browser suites. Production deployment
 dpl_52v5KTd2H9dEggr8oeHsWTzkCrG6 promoted to the existing stable origin; build digest 8d45814d204db702.
Public Android 2.0.4 (37) is now live at release android-v2.0.4, with the exact verified APK above.
Publication + actual online manual-check/download/Android-installer/retained-data verification:
run 39670906841 SUCCESS (attempt 2). First verify-live attempt failed before starting Android
because the runner downloaded a corrupt Emulator ZIP; only the failed verification job was retried.
Published APK bytes and signing identity were never changed or rebuilt.
Public website full v2/download/sharing verification: run 39670920652 SUCCESS.
Post-merge Web checks: run 39670920676 SUCCESS (same runtime files, 92 tests).

Permanent URLs:
- https://salary-calculation-eta.vercel.app/
- https://github.com/sholi2157-dev/Salary-calculation/releases/download/android-v2.0.4/Salary2.0.4.apk

Remaining manual limits: WhatsApp recipient/rendering on a physical Android phone and Safari/iPhone native file sharing.
Automated fixtures and OS-sharing mocks validate payloads, image generation and cancellation; no messages were sent.

Earlier history follows; it does not override this owner-approved scope or release state.

# Sharing review — 2026-10-21

Owner requested coordinated Android and Web sharing changes. Review branches: `codex/sharing-web-20261021` and `codex/sharing-android-20261021`; draft PRs #9 Web and #8 Android. Contract: `docs/v2-sharing.md`; exact source/test evidence and remaining manual gates: `docs/sharing-20261021.md`.

Technical copy is separate from recipient-ready sharing; multiple workplaces share technical blocks. Worker search is corrected. Excel/CSV use the same 14 columns and preserve unknown amounts as empty cells. Monthly/yearly images include money; Web image sharing is offered only when native file sharing works, with no download/text substitution. Full JSON backups and saved financial data are unchanged.

Verification: Web run 39609131883 SUCCESS (build, all 89 tests, 28 shared examples, actual UI handlers, PNG, layouts and data preservation); Android run 39609123653 SUCCESS (136 Debug + 106 Release tests, unsigned builds, identity gates). Screenshots and the generated PNG were visually reviewed. No merge, production promotion, signed APK or public distribution. Physical WhatsApp/Safari and a signed update/data-preservation gate remain before delivery. Existing public APK and website remain unchanged.

# Management follow-up — 2026-10-20

Public download request measurement is now live from source `fd6a7418fe08102c64bc367759d6d6c072cf495f`. The private management Site was redesigned (version 2), and its Vercel totals query was repaired. Preserve the existing Site identity, D1 binding, recorder/hash/encryption secrets and encrypted user cookie. See the top of `docs/download-and-management-20261020.md` for exact verification and limits.

# Latest delivery — 2026-10-20

Public Android download guide and the owner-private Hebrew management dashboard are recorded in `docs/download-and-management-20261020.md`. App source is `bb17078deb57d9630ac7c0227722395229318899` on `codex/download-and-management-20261020`, published to the same eta origin. Existing 2.0.3 GitHub Release is reused. Vercel traffic in the private dashboard still requires the owner to connect a token there; GitHub download counts are live. No APK rebuild or data migration.

# Current Web track — 2026-10-06

## October 14 audit fixes — production delivery complete

Vercel team access was restored and the owner-authorized website release completed. Public origin remains https://salary-calculation-eta.vercel.app. Production deployment `dpl_A7zWpmd7PTFsaorrZKEQzDZhvGWn` is READY, exact source `562a7a568558d6fba22bd6aac2fe173ed85ff971`; public build-info confirms it. Build ID `562a7a568558d6fba22bd6aac2fe173ed85ff971-bdf044743a70b60b`; CSS digest unchanged at `5dfa8e49be8f564899abd359860b166546867b9929770a98c5d32f93b1af650a`. Build/deployment logs show success; no error/fatal runtime logs found for this deployment in the initial post-release window (not a long-term monitoring claim).

Previously verified application evidence: run38117622906, 46 unit tests, build and all five browser suites passed. New actual-public-origin run38595391853/job115960660993 SUCCESS, test harness commit `18ff9f9c23ea99fa2aa9ebe746136189b2ff1bab`: RC15 defaults/category draft, currency fallback/override, active timer/reload/cancel/finish, Enter/layout plus cross-tab edit/delete conflict preservation at 360/390/768/1440. Artifact11908872883 (`web-live-browser-evidence`). Test-only changes enable the existing audit suite against PUBLIC_SITE_URL; no app source changes after the reviewed audit. Disposable synthetic contexts and blocked analytics; no user records accessed/reset.

Rollback deployment: `dpl_2P1wBDybjGLCZmi28E7h3rYnD651`, same origin. Browser storage, design and feature gates retained. Android RC16/GitHub update feed/existing Drive delivery already completed separately. Physical iOS/Safari/native-share/live-AI checks and the general simultaneous whole-snapshot localStorage-write limitation remain as recorded in the audit. Earlier scope and blocked-release notes below are historical.


## October 11 audit review branch

Audit fixes are isolated on `codex/web-audit-20261011`. Read `docs/audit-20261011.md` for findings, exact verification and remaining gates. These are review changes, not a new delivered APK or production deployment. RC15 remains the public baseline. Earlier scope sections below are historical and must be read together with the latest completion notes.


## Latest completed owner request — RC15 parity, 2026-10-09

Current public website remains https://salary-calculation-eta.vercel.app, exact tested/deployed source d4feadd0cdf053906c70d6b76e6b9651c57e455e and Vercel deployment dpl_2P1wBDybjGLCZmi28E7h3rYnD651. Development/evidence branch is codex/web-rc15-live. Read docs/rc15-feature-parity.md and docs/rc15-web-live.json before new work. Build,43 unit tests and all4 browser suites passed run37993152040. Actual public-origin UI then passed run37993523248 at360/390/768/1440 with isolated synthetic data and analytics traffic blocked. Same origin/design/storage/analytics loader retained; no protection settings or main merge changed.

Web now has RC14's default currency fallback for unconfigured categories, active currency editing without timer restart, confirmed cancellation without history, and current-timer reads after finish confirmation. Both platforms gained on-demand default category selection/add and compact currency/rate fields with centered standalone category choices. Android RC15/code23 is published and its real previous RC14 button/download/Androidinstaller/retained-data/no-self-update gate passed run37994406008. Existing Drive APK link/ACL was preserved and replaced with the verifiedRC15 binary. Do not repeat completed work or mistake a later metadata-only preview for the public deployment.


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

## Android screenshot alignment — 2026-10-06

Owner confirmed the repeat-open service-worker repair works, then requested
closer category/settings/history/filter alignment with current Android images.
Category settings now use horizontal edit/remove chips, compact name/rate
inputs, and retain the category currency control/default editor. Filters use a
bottom sheet with separate date/category/payment rows and Android-style actions.
Selection uses a compact inline count/total/select-all/clear/share/close toolbar,
checkboxes beside amounts and a fixed red/amber/green action shelf above nav.
Leaving history for home clears only transient selection, including back/forward;
filters/search and all saved financial data remain unchanged.

System/feedback mirrors Android's feedback type/message/email-draft flow,
addressed to its existing feedback recipient. Only Web build and browser details
are included, never work records. A mailto draft opens only on the user's click;
there is no automatic sending. Android update/notification/personal-key mechanics
remain platform exceptions. Account/AI flags, removed tutorial replay, financial
schemas and the working redirect-safe service worker/repair page are unchanged.

Validation: build, 42 unit tests; mobile, parity and controlled-update browser
suites; 360/390/768/1440 RTL layouts, new selection navigation/feedback tests,
horizontal eight-category chips and exact existing 22/8 preservation, drafts,
timer, backup/import, offline/repeat-open repair. No application JS errors.
Production release uses the previously authorized verified-artifact alias path
at the same permanent eta origin; no merge or production branch mutation.
Recoverable prior source: 682aaeceb83a81b48137eac07ad3c7f33d5aade5.

## Traffic measurement preparation — 2026-10-06

Owner requested visitor statistics for the published eta origin. Added the
standard Vercel Web Analytics loader, scoped to salary-calculation-eta.vercel.app.
No work-record events, custom financial payloads or application storage changes.
The existing build hashes HTML into the worker version, preserving safe updates.

Validation: build, all 42 unit tests and mobile/parity/update browser suites pass,
including exact 22/8 records, drafts/timer, offline, repeat-open recovery.
This branch is prepared only: Web Analytics must first be enabled in the Vercel
dashboard, then the updated artifact deployed to the same eta origin.
Current production remains 67f4e1a. No claim of collected traffic or live metrics.
The connected tools have no analytics-enable operation; browser fallback requires
owner approval under the current browser capability instructions.
