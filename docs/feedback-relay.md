# Feedback relay (Google Apps Script)

"משוב ותקלות" is the first section of Settings. Its row opens a sheet; "שליחה" POSTs one JSON report to a
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
