# Automatic website updates and cumulative 2.0.5 — 2026-10-21

## Published behavior

- Same public origin: https://salary-calculation-eta.vercel.app/
- Online opening/refresh requests fresh HTML. No update banner, confirmation/later
  buttons, settings check action or visible website version/commit.
- Background worker activation never reloads an open document. New drafts/timers
  and existing unsaved forms remain open; financial/account storage is untouched.
- Scripts, stylesheet and fonts are pinned to each document's build identity.
  A missing asset cannot silently fall back to another build's cached asset.
- Complete offline shells install atomically and activate automatically. Offline
  navigation/server failure uses the complete installed shell. Partial failed
  installs are discarded. Only obsolete code caches are pruned (previous retained).
- Register once per document and explicitly check the script on load/online/visible.
  Existing matching registration can return without checking the script (W3C):
  https://www.w3.org/TR/service-workers/#register-algorithm

## Entry parity

- Manual/group/job duration has separately selectable hours/minutes and the clock
  digit keypad. Duration admits 24:00; time-of-day stays 00:00–23:59.
- Removed ordinary typing/tapping instructions, example note text and version UI.
  Labels, accessibility, validation, onboarding and financial explanations remain.
- Opening/closing a rounded imported duration without typing retains exact hours
  and amounts. Quick-hour presets clear the old digit buffer, matching Android.
- Normal and API-failure APK download paths point directly to verified 2.0.5
  (19485124 bytes). Download measurement/configuration is unchanged. Keep the
  fallback aligned with the verified APK when publishing later Android releases.

## One-time old-worker migration

An old cache-first worker can serve its last cached page while the automatic
replacement installs. No update button is required; after installation the next
navigation uses the new behavior. We do not forcibly reload existing legacy forms.

## Verified release

Runtime source: 660e82ccc696bff62783d1614d8959f210cd4276.
Deployment: dpl_2YJqFTLj8Hm9Rpk2kjYZU8UuoGVk.
Build ID: 660e82ccc696bff62783d1614d8959f210cd4276-ac045d488ac19009.
Same tested production build promoted without rebuild; stable alias verified.
Rollback: dpl_6qGopS2toz4byUV3mZuEi8qWAShj.

- Consolidated CI 39682231966 SUCCESS: 91 unit tests, shared fixture byte parity,
  exact old deployed worker→A→B→C on one origin, new opening, no forced reload,
  22 shifts/8 categories/saved amounts/extensions/dormant data, draft/timer,
  true offline socket failure, failed C install and version/settings UI removal.
- Full v2 (360–1440/light+dark), download and sharing browser suites pass, including
  selectable minutes, imported precision and open-pad→quick preset→confirm.
- Targeted resolver/redirect/measurement: 6 tests pass (forced API failure included).
- Public-origin CI 39683042974 SUCCESS: actual UI/download/sharing/PNG, real worker,
  new draft and timer on fresh/offline reload, exact saved amount/extension retention.
- Metadata regression CI 39683043000 SUCCESS. Public release metadata returns 2.0.5.

Harness limits: native worker preparation explicitly requests an update job when
browser navigation checks coalesce; callback scheduling is separately unit-tested.
This gate asserts installation/activation/isolation and new-document behavior, not
background detection latency. Earlier harness selector/async polling/offline
emulation failures were fixed before final green release; temporary traces removed.

Android 2.0.5 / code 38 is separately verified and public. It includes all 2.0.4 changes:
https://github.com/sholi2157-dev/Salary-calculation/releases/download/android-v2.0.5/Salary2.0.5.apk
SHA256: c45eef0c182f42e6273082c59280076271433761da41cda5c1cc5d56b4409860.
Candidate 39676466946, publication 39678748525, visible red-bell 39679679694: SUCCESS.
APK never rebuilt/re-signed during website work. Full Hebrew changes: docs/releases/2.0.5.md.
