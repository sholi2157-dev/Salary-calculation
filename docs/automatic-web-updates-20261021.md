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
- Keep one registration promise per document and explicitly check its script on
  load/online/visibility. Registering the same URL/options can return an existing
  registration without checking for a new script; explicit update is necessary.
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
- Temporary tracing/browser-export diagnostics removed. The gate exercises a real
  next opening in a second tab while the first tab's draft remains unchanged,
  queries the actual document controller, and confirms an unavailable C asset was
  really requested before asserting B's offline fallback.
- Existing-registration behavior checked against W3C register algorithm:
  https://www.w3.org/TR/service-workers/#register-algorithm
- Final exact-source CI, stage, promotion and live verification recorded after completion.

Offline gate refinement: the older CI Playwright 1.56 driver did not consistently
apply page offline emulation to worker-owned navigation fetches (the server was
still reachable). The local verified driver is 1.62.1; CI now uses that version.
The A/B/C local server also cuts its socket during offline scenarios, exercising
an actual worker fetch failure independently of driver network emulation.

Final source2f8f9cf5d223d05f0ea528d89ed2f6cdb8a68fc6, run39680237024 SUCCESS:
91 units, fixture byte parity, real worker migration/offline/failure gate, all
v2/download/sharing browser flows. Staged production build digestb6de91227b349d3d
matched the local bundle, then the same build (no rebuild) was promoted as
dpl_6qGopS2toz4byUV3mZuEi8qWAShj to salary-calculation-eta.vercel.app.
Public alias/build info confirmed. Rollback dpl_52v5KTd2H9dEggr8oeHsWTzkCrG6.
Actual public-origin browser verification is triggered by docs/rc15-web-live.json.
