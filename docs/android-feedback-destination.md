# Android feedback destination — 2026-11-03

Scope: `work/codex-feedback-dest-android`, baseline
`fa811e62a143670df8e626c9c24b01f58a86d497`. Commit only; no push, merge,
deployment, new provider, secrets, version/signing changes or APK delivery.

## Implementation and PR deployment prerequisite

The existing Android form POSTs to the owner's Google Apps Script `/exec` endpoint;
its failure fallback is an `ACTION_SENDTO` mail draft. The relay previously hardcoded
the old inbox, so changing an Android recipient alone would not change direct delivery.
Both mechanisms are retained. Android drafts now target
`israelshimon1313-vzptcg@mail.grokbot.com`. `docs/feedback-relay/Code.gs` adds an
Android-only route with that same server-side default; the website's existing
route and client code are unchanged.

**Israel must update the existing Apps Script deployment before distributing this
Android change.** Copy the revised `docs/feedback-relay/Code.gs` into the existing
Apps Script project. In Project Settings → Script Properties, set
`ANDROID_FEEDBACK_TO=israelshimon1313-vzptcg@mail.grokbot.com` (or leave it absent
for the identical documented default). This is Apps Script's server configuration
rather than a hosting environment variable; no new credential is needed. Update
the existing web-app deployment to its new version, preserving its `/exec` URL and
current authorization/access settings. No such action was performed in this task.
The old deployed script would still send to the old inbox, so the deployment is a
required rollout gate. The supervisor should include this prerequisite in the PR.

After deployment, verify with synthetic text that direct Android submission reaches
only the new inbox and that mail fallback creates a draft with the same destination
and JSON body. The Android UI itself never displays or copies the destination;
the external email composer necessarily shows its recipient. Sending a fallback
draft remains controlled by the user.

## Payload and protection

The complete email body is JSON (`android-feedback-v1`); `message` is the delimited,
escaped user-text field. Context: `kind`, `app.platform`, installed `app.version`,
`app.code`, `app.buildType`, `screen=settings/feedback`, active resource `language`,
and `sentAt` (UTC Unix milliseconds). `contactEmail` exists only for nonempty text
explicitly typed in the optional field. Both message and contact draft survive form
folding via the existing Settings saveable state.

No database/state access, payroll, shifts, wages, account identity, device details,
logs or screenshots are included. The existing public relay marker is added only
to the HTTP envelope; it is absent from email and fallback body. User-entered text
is never interpreted as headers, recipient, subject or reply-to. Subject is fixed;
mailto query components are URI-encoded. Server reconstructs an allowlisted body
instead of mailing the request. No attachment is created for Android.

Message: 4,000 UTF-16 code units; contact: 254. Controls and invisible formatting
characters are stripped, retaining LF in multiline message text. The server rejects
oversized/empty messages and removes CR/LF from contact. The existing locked shared
40-submission/day relay limit remains in force; it includes failed attempts after
authentication. Manual email drafts bypass the relay quota; suggested use is at most
one draft per minute and users must explicitly send them in their mail client.
As with any free-text form, text explicitly entered by users is included as typed
subject to sanitation; app-owned private data is never appended.

## Actual verification

- `node scripts/test-android-feedback-relay.cjs`: passed; executes the actual Apps
  Script under mocked Google services, including `doPost`, correct destination,
  payload allowlist, excluded private fields/transport token, optional contact,
  control stripping, 4,000-character boundary, header injection, invalid configured
  recipient, token rejection and the 40/day gate. No real email sent.
- `npm test`: exit 0; 8 tests, 8 passed, 0 failed/skipped.
- `npm run build`: exit 0; output `node scripts/build-web.cjs`. Run against an
  isolated temporary copy of the unchanged web inputs with the baseline SHA supplied
  as build provenance, so generated website files in this branch were untouched.
- `gradle :app:testDebugUnitTest --tests com.example.AndroidFeedbackTest --tests
  com.example.WorkRefinementTest --console=plain`: could not run, exit 127:
  `gradle: command not found`. Java and Gradle are absent; no wrapper is in the repo.
  Android unit tests were added but have not been compiled/executed here.
- GitHub CI inspection attempted with `gh run list`: `error connecting to
  api.github.com`. No new remote CI run exists from this unpushed change.
- `git diff --check`: passed.

Remaining gates: Android compilation/unit tests in the configured build environment,
relay deployment and synthetic end-to-end inbox verification, physical-device review
of submission and email fallback. No signed release/build identity or artifact was
produced; package, signing configuration and version remain unchanged.
