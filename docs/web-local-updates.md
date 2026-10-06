# Local public release and controlled Web updates — 2026-10-06

Focused Web follow-up from `0b914ea743c09b3b175ee7685bcd3eaa97eba080`, same
`codex/web-android-rc13-parity` branch. Android, financial formats and prior parity
features are unchanged. No merge or production promotion.

## Accounts switch

`web/features.js` is the single source of `WEB_ACCOUNTS_ENABLED=false`.
The Settings account markup remains in an inert template, never inserted while
disabled. Neither `web/cloud-sync.js` nor `web/accounts.js` is loaded or precached
in the public mode. The account module also returns before any DOM, storage or
SDK access if invoked while disabled. Auth/sync entry points are guarded.
`/api/config` reports accounts/sync false and returns no Firebase registration.

No Firebase SDK is loaded, Auth/Firestore is not initialized, no account timer or
sync listener is registered, and an old persisted Firebase login cannot change
`currentUserId` from the local guest dataset. Existing account stores, Firebase
Auth persistence/IndexedDB and cloud documents are neither read nor erased. The
account module, wire formats and existing sync tests remain available. Re-enable
only by changing the release switch and verifying the optional account integration
again; existing stores are still present. No guest adoption occurs.

## Detection and notice

Build output adds `buildId` (commit plus a digest of the owning shell source) to
`/build-info.json` and generates `web/build.js`. Any deployed commit or shell
change yields a different worker/cache identity. No keys/configuration are in
build provenance. Preview uses its own origin; production will use its permanent
origin, not a preview checker or changing preview domain.

A startup check, foreground return and visible-tab five-minute interval fetch
`/build-info.json?check=<timestamp>` with `cache:no-store`, omitted credentials
and an eight-second timeout. Foreground/online events respect the same five-minute
throttle. Registration uses `updateViaCache:none`; checks also call `update()`.
Worker installation completion can trigger an immediate readiness check.
Offline, invalid response and same-version checks do not display a new notice.

A different server build displays the compact RTL “גרסה חדשה זמינה” notice with
“עדכן עכשיו” and “אחר כך”. It uses the existing palette/type/buttons and stays in
the layout above the content, leaving Save and bottom navigation accessible.
“אחר כך” remembers that build only in this tab's memory and changes no storage;
the same build remains dismissed until a reload, while a subsequent version can
notify again. There is no automatic reload or browser alert.

## Controlled worker and data safety

Each worker precaches its complete shell into its own build cache with reload
requests. Failed installs remove only their partial code cache and never activate.
Navigation and shell assets come from that same cache, avoiding new HTML/old JS
mixing. `/build-info.json` and `/sw.js` are network-only/no-store; API, Firebase,
external responses and credentials are never cached. Vercel also supplies no-store
headers for provenance/worker and revalidation headers for HTML and Web assets.

An installed replacement waits. “עדכן עכשיו” verifies that the candidate worker
matches the available build, preserves the supported report draft, and sends
`SKIP_WAITING`. `controllerchange` reloads exactly once only in the tab that asked
for the update. Initial installation and another tab's activation do not cause
unsolicited reloads. If another tab already activated the correct worker, the
requesting tab can reload directly. Installation/activation failures retain the
old usable page and allow retry; candidate/activation waits are bounded.

Open edits or any open dialog (including Settings/import/category) delay the
update instead of submitting/discarding user work. The supported report draft is
saved and verified; storage failures prevent activation/reload. No pending settings
are automatically committed. A timestamp-based active shift is not stopped,
removed or recalculated: its persisted timer resumes after reload. No update code
clears localStorage or IndexedDB or changes financial snapshots, preferences,
account stores, migration checkpoints or backups. Old code caches alone expire.

The previous RC13 worker could mix network HTML with cached JS. A compatibility
check offers a one-time controlled shell activation even if the newly fetched
HTML already reports the new build. Its legacy draft saver is verified against
form values/group rows before proceeding. This transition is also tested.

## Verification

- `npm run build`, `npm test`: **42/42** passed.
- Existing `browser-tests/mobile.cjs`: passed; its obsolete account-section
  expectation now asserts the section is absent.
- Existing `browser-tests/parity.cjs`: passed 360/390/768/1440px, exact current and
  legacy 22-shift/8-category fixtures, CRUD/history/currency/selection/sharing,
  timer/drafts/reload and offline shell.
- New `browser-tests/updates.cjs`: real worker releases A/B/C/D on one unchanged
  test origin at 360/390px. Same-build absence, later dismissal, delayed Settings
  edit, explicit activation, exactly one reload, subsequent shell replacement,
  unchanged **all localStorage bytes**, old account/login sentinels, active shift,
  saved draft, latest offline shell, no cached provenance/API, and no account
  SDK/config/sync requests or application JS errors passed. Also verifies the
  exact completed RC13 worker's transition into the new worker.
- Six unit tests cover switch/config/early module guard, throttling/identities,
  dismissal, controlled reload, blocked/late edits, retry and legacy activation.
- GitHub Web workflow includes all three browser suites and screenshot artifacts.
  Screenshots `update-360.png` and `update-390.png` inspected; synthetic data only.

## Permanent production origin — exact diagnosis and rollout gate

Retain **https://salary-calculation-eta.vercel.app** as the permanent public URL.
Vercel's project domain list confirms it is verified, assigned to the existing
`prj_W8eD12XnlqJ5wVEsnTm43yIHgYnu` project, with no redirect, preview branch or
custom-environment assignment. No new domain is needed and no alias was changed.

Alias inspection proves it currently points to
`dpl_HqZsJt4sQAFh99bcd7XEifWWFFaH`, an August 28 initial Android-only repository
import (`cf598b76…`). That deployment is marked READY but returns NOT_FOUND, and
its file-tree inspection returns “File tree not found”. The next `main` production
attempt `dpl_52VkZygkH8oAjtbvoodxiaVLGaWi` (`20c6022e…`) failed validation because
old `vercel.json` contained unsupported `public`. This property is already absent
from the tested Web branch; existing Git preview builds succeed. The issue is a
stale/missing production artifact, not an unassigned domain.

Resolution is prepared: after approval, promote the verified Web deployment
through the existing Vercel project's production flow (or merge approved Web
changes to its configured production branch), keeping the existing verified eta
alias. Then verify its `/build-info.json`, home, offline/update flow and saved
records on that same origin. Do not redirect users to the sholi/preview aliases,
transfer storage across domains, or clear caches/data manually. Future releases
must use the identical eta origin. An HTTP 404 cannot be made into a working Web
release without replacing its deployment; the owner's explicit “do not promote
production yet” gate therefore leaves that serving issue pending rollout.
