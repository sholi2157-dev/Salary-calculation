# Feedback relay (Google Apps Script)

"משוב ותקלות" is the first section of Settings. Its row opens a sheet (Android: FeedbackReport.kt, site: web/kis/settings.js); "שליחה" POSTs one JSON report to a
Google Apps Script web app deployed from the owner's Google account, which mails it to
`sholi2157+salaryfeedback@gmail.com` with the report as a `.json` attachment and the optional screenshot as an image.
The app and site hold no mail password and no server of their own.

Report fields: `token`, `kind`, `message` (the user's text), `app` (platform, version, code), `device` (model,
Android version, locale, time zone, screen size), `state` (a fixed allowlist of counts and switches), `events`
(DiagnosticLog: fixed event names such as `open`, `page`, `editor`, `crash` with class and app code lines, never
an exception message, typed text, amount, workplace, name, email or key) and `screenshot` (a picked image,
re-encoded to JPEG of at most 1600 px, which also drops its location data).

The relay source is `docs/feedback-relay/Code.gs` (40 mails a day cap, shared token, 6 MB limit). Until
`FeedbackReport.URL` holds the deployed `/exec` address, the form keeps the old mail-app draft.

## Website destination change (2026-11-03; local review only)

The website still uses the existing Google Apps Script POST relay and explicit
mail-draft fallback. Its single destination is now
`israelshimon1313-vzptcg@mail.grokbot.com`. The address is not rendered in the
website UI (the user's mail application necessarily shows its draft recipient).
Android's existing recipient and report handling remain unchanged.

**Required owner action before releasing this website change:** Israel must copy
the updated `docs/feedback-relay/Code.gs` into the existing Apps Script project and
update its existing web-app deployment, retaining its `/exec` URL and existing
mail authorization. In Project Settings → Script Properties, set `FEEDBACK_TO` to
`israelshimon1313-vzptcg@mail.grokbot.com`, or leave it unset to use that exact
documented default. Script Properties are Apps Script's server-side equivalent of
environment configuration; no Vercel/Firebase setting, new provider, password or
secret is needed. A client-supplied recipient is ignored. Updating only the website
cannot change the already deployed relay's old recipient. This work has not
accessed or changed that deployment or sent real email.

Website emails contain JSON in the body and `feedback.json` attachment:
`schema`, `kind`, `message`, `timestamp` (UTC ISO 8601), `app.platform` (`web`),
`app.version` (the existing commit-plus-shell-digest build ID), `screen`
(`settings/feedback`), `language` (UI language), and `contactEmail` only when typed
in the optional field. `message` is a JSON string: newlines, quotes and apparent
field delimiters are escaped, so an assistant can parse its boundaries reliably.
No account email, user agent, URL/query, payroll records/counts, wages, diagnostics,
tokens, storage or screenshots are included in mail. The existing public relay
marker is used only for POST compatibility and discarded before email generation;
it is not an authentication secret. Both web paths share the same payload builder.

Limits: message 2,000 UTF-16 code units after normalization/control stripping;
contact email 254 characters with a single-address validation. The relay repeats
validation, builds a fresh allowlisted object, fixes the subject, and ignores
unknown submitted fields, headers, recipients and attachments. The existing
locked 40-mails-per-day limit remains shared across relay clients (not a
per-user guarantee); mail drafts require manual sending and are outside relay
quota. The destination is validated as one address even in server configuration.
Screenshots were removed from web feedback because they can contain payroll or
personal information. No application records or financial storage are changed.

Review/PR setup note: the Apps Script update above is required before website
release; unit tests use mocked mail only and do not prove inbox delivery. No merge,
push, deployment, live preview, CI run, Vercel, Firebase or secret changes are part
of this local change.

Local verification on `work/codex-feedback-dest-web`:
- `GITHUB_SHA=$(git rev-parse HEAD) npm run build`: passed. The first plain
  `npm run build` attempt hit sandbox `spawnSync /bin/sh EPERM` while discovering
  Git HEAD; the retry uses the build's existing provenance input, with no build
  script change.
- `npm test`: 42 test files passed, zero failed, after the successful build.
  The initial attempt before a complete build had one navigation/build-output
  failure; it passed after the build retry.
- `node web-tests/feedback.test.cjs`: 8 tests passed, zero failed, including the
  real send handler with mocked transport and legacy draft handler.
- `git diff --check`: passed.
- `node browser-tests/mobile.cjs` and `node browser-tests/parity.cjs`: could not
  start (`Cannot find module 'playwright'`). No dependency was added.
- Live preview, inbox delivery and new CI evidence: not run; this request allows
  a local commit only and prohibits pushing/deployment. Android was not built.
