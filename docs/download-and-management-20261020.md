# Management redesign and anonymous request measurement — follow-up

Current public application source: fd6a7418fe08102c64bc367759d6d6c072cf495f; production dpl_D1FAZC5V2dbdHBkLvAH4KCt416F3 at the same eta origin. GitHub run 39498787812 passed all 87 unit tests and current v2/download browser suites. HEAD download returned 302 without setting a measurement cookie; build-info confirmed exact source.

Private dashboard source: 3cf48a45cbf4f3621fae246119ebd47ebf545266; published private version 2, deployment appgdep_6ad7da876cf881918c55891696dd8324. URL and owner-only audience are unchanged. Four sections: overview, website, application, connection/help. Added period selection (24h/7/14/28 days), real independent panels for sources/countries/devices/browsers/OS/pages, previous-period comparison within retention, trend metric toggle, CSV export, explicit loading/partial-error states and phone bottom navigation. UI fixtures exist only in the development server with a conspicuous demo banner; hosted Worker uses live upstream data and actual database only.

Connection diagnosis: actual owner POST /api/connect succeeded twice before the report; subsequent /api/web returned 502. Previous code serialized a literal by=[] dimension for totals. Totals now use the official visits/count endpoint (with since/until); optional aggregate panels use Promise.allSettled and fail independently. Existing encrypted cookie and encryption key were preserved. /api/connection allows the owner to check the existing key and provides safe error/status information. No real Vercel token was obtained by the agent. Actual authenticated owner-cookie Vercel reads still require the owner to reopen the dashboard; do not claim these were verified through service access, which intentionally supplies no visitor identity or browser cookie.

New measurement: Vercel /api/download creates a random first-party HttpOnly Secure SameSite=Lax browser cookie and sends only an HMAC of the identifier, random request ID and APK version to the private recorder. No names, IPs or financial data are inserted. HEAD, preview requests and known bots do not record; recorder outage does not prevent the download. The protected recorder requires both Sites platform service access and a separate secret key. Existing app distribution/first-launch/home/settings entry points are retained. Disclosure in the download guide was updated.

Sites manifest now binds D1 DB, with append-only schema migration 0000_download_requests. The actual database and table were verified after deployment. Totals distinguish distinct browser hashes, first-ever requests and repeat requests, including repeat requests for newer versions. This is an estimate of browsers, NOT people/accounts/installations. History before activation cannot be reconstructed; GitHub lifetime counts remain separate and include all sources/updates/repeats. At verification, hosted GitHub stats returned 96 requests across seven versions and the new database correctly returned 0 recorded requests. No synthetic events were inserted in production.

Runtime secret names (never retrieve/print values): Sites CREDENTIAL_ENCRYPTION_KEY preserved; DOWNLOAD_RECORDER_KEY added. Vercel DOWNLOAD_STATS_SITE_URL, DOWNLOAD_STATS_SITE_TOKEN, DOWNLOAD_RECORDER_KEY and DOWNLOAD_HASH_KEY configured only via native environment tools. Measurement start is stored as DOWNLOAD_MEASUREMENT_STARTED_AT. Do not rotate hashing/encryption/recorder keys during ordinary UI changes. Existing platform service credential continuity was checked; same Site bearer remains valid. Native Site access is still owner-only.

Verification: exact source build, 87 unit tests, GitHub Chromium 360–1440 light/dark v2 flows, real SQLite unique/repeat/idempotency migration checks, credential encryption/identity/tamper/CSRF checks, missing-connection handling, partial upstream error isolation and managed browser phone QA at 360/390 with labeled fixtures. Hosted HTML, GitHub counts and real empty D1 queries returned 200 using supported service access. Android physical installation remains outside this web-only change.

---

# Android downloads and private management — 2026-10-20

Owner authorized public download UI and a separate owner-private Hebrew dashboard.

## Public site
- Keep https://salary-calculation-eta.vercel.app and existing browser storage.
- Source branch: codex/download-and-management-20261020.
- Application source: bb17078deb57d9630ac7c0227722395229318899. Production deployment: dpl_EuuQEEuLFP1gcaQE4jGLwPUpxNzj.
- Download entry is available before first-launch onboarding, on Home and in Settings.
- /api/download redirects to the latest stable published Android APK using GitHub Releases metadata. /api/android-release supplies version/size. Verified 2.0.3 fallback remains usable when the metadata service is unavailable.
- Existing public Android Release was reused, not rebuilt. Local attached APK matches release digest 10baa2e4b1bf70ce75e61d48ef0ae57ba6dd69b1e2609eef825f117674a8a283.
- No Android source, signing, package, databases, financial data, account flags or existing analytics loader was changed.
- Previous production for rollback: dpl_EzPUFNqwsoPXhWSffuWBWA19SPjj (source 70fd200c5f6db4347eb25e432bd62fc97c5f6a1d); first download release dpl_21QDuroPn5tvJWbbs67XdLCjmeYJ (27d229d).

## Private dashboard
- URL: https://salary-calculation-management.sholi2157.chatgpt.site
- Sites project ID: appgprj_6ad7d1f7cf0c8191a974e1518472683c. Reuse this exact project, do not create a replacement.
- Dashboard source is its Sites-backed Git repository, not this salary app repository. Source commit: 2a3768b622720ac0f90c0de14cf6851e04ee3085. Deployment appgdep_6ad7d369f2608191beb6de4782abf66b succeeded.
- Platform access policy is owner-only, with no external visitors. Anonymous API request returned 401; supported owner service access returned HTML 200 and /api/downloads 200 with live GitHub data.
- GitHub counts all uploaded APK assets across published stable Android releases, excluding release.json. They include updates, repeated downloads and activity outside the website, not unique users or installations. Verified snapshot: total 94 across seven releases; 2.0.3 had 12. Counts are not hardcoded.
- Site stats adapter uses Vercel Web Analytics API for the existing team/project; no scraping or financial-record access. Refresh is every minute while open; GitHub data is cached up to five minutes.
- IMPORTANT remaining user step: connect a user-created Vercel token inside the private dashboard. Vercel's existing connector does not expose an app-runtime analytics credential. /api/web correctly reports not_connected until then. Live Vercel analytics reads have NOT been verified with a real token. Never claim those metrics are already connected.
- A generated CREDENTIAL_ENCRYPTION_KEY is stored as a secret in Sites runtime, never source. Dashboard connection validates token via Vercel and saves it AES-GCM encrypted in an HttpOnly Secure SameSite=Strict cookie, bound to the platform user ID, max 30 days. Token never appears in page responses or localStorage. Connect/disconnect require authenticated identity and same Origin. Secret values must never be pasted into chat or source.
- Optional VERCEL_ANALYTICS_TOKEN runtime secret is supported for a future server-side configuration. Do not obtain credentials from browser storage or provider token-list APIs.
- The dashboard does not identify APK downloaders or record application usage. Its traffic reporting remains limited by Vercel plan retention; no ongoing background snapshot job was created.

## Verification
- 84 unit tests and build passed locally and in GitHub Actions.
- First complete web CI run 39494994181 succeeded: current v2 full browser flows plus home/settings download guide, 360/390/768/1440, light/dark, stored-data preservation. Browser evidence artifact 12296564844.
- Follow-up run 39495601074 also succeeded, including fresh-browser direct download before onboarding and return to the untouched launch screen.
- Current old mobile.cjs/parity.cjs suites target the retired pre-v2 DOM; current v2.cjs was used for the actual live architecture. Local browser binaries are absent; real Chromium tests ran on GitHub Actions.
- Dashboard security tests cover encryption roundtrip, other-user/tampered cookie rejection, CSRF, anonymous mutation denial, unconfigured status, true aggregation (no summing daily visitors), and download-count exclusion of JSON assets. Upstream Vercel responses in those tests are synthetic, not evidence of live Vercel access.
- Private hosted GitHub data and anonymous rejection were verified. Desktop dashboard styling was inspected in managed browser preview; its preview network could not fetch GitHub, whereas the published Worker fetched live counts successfully. Physical Android permission/installer screens remain device-dependent and were not exercised here.
