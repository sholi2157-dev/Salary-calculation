# Automatic website updates — 2026-10-21

Owner contract:
- The unchanged public URL serves fresh HTML on online opening/refresh.
- No update banner, confirm/later buttons, settings check or visible website version.
- Background code installation never navigates an open form or interrupts a timer.
- Preserve offline browsing, drafts, all financial precision, dormant accounts and data.

Implementation:
- Network-first navigation with complete installed-shell fallback offline/on server failure.
- Built documents pin scripts, stylesheet and fonts to their build identity; an
  unavailable asset cannot silently fall back to another build's cached asset.
- Complete shells install atomically and activate automatically. A failed partial
  install is discarded; only obsolete code caches are pruned, retaining the prior
  complete shell. No financial/account storage is read or rewritten by the updater.
- Register once per document. Registration already checks the worker; later online
  and visibility events request an update without racing an immediate second register.
- Removed explicit update manager, banner styles/markup, check action and version footer.

Expanded 2.0.5 parity:
- Manual/group/job durations use separately selectable hours/minutes and clock-style
  digit keypad (24:00 allowed for duration; time-of-day remains 00:00–23:59).
- Removed normal-form typing/tapping instructions and example note text.
- Opening a rounded imported duration without typing retains exact stored precision.

One-time legacy migration: an old cache-first worker may serve its last cached page
while the automatic replacement installs. No update button is required; after
installation the next navigation uses the new behavior. Existing legacy forms are
not forcibly reloaded.

Verification:
- Local build +91 units pass, diff checks pass.
- Real Chromium passes exact previously deployed worker→A→B→C at one unchanged
  origin, no forced reload, 22 shifts/8 categories/saved amounts/extensions,
  draft/timer/dormant-account retention, offline reload, failed partial install,
  version/settings UI removal and the next ordinary opening.
- Existing v2 (360–1440 light/dark), download and sharing browser suites passed on
  runtime source44414f7361edce481668e80b3336e2f3e87d8300. Earlier combined CI failures
  were the old help-title selector and asynchronous truthiness polling in the new
  migration harness; the latter now explicitly awaits each predicate and settles
  the initial registration before simulating another deployment.
- Temporary tracing/browser-export diagnostics removed. Final exact-source CI,
  stage, promotion and live verification recorded after completion.

Offline gate refinement: the older CI Playwright 1.56 driver did not consistently
apply page offline emulation to worker-owned navigation fetches (the server was
still reachable). The local verified driver is 1.62.1; CI now uses that version.
The A/B/C local server also cuts its socket during offline scenarios, exercising
an actual worker fetch failure independently of driver network emulation.
