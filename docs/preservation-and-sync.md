# Preservation baseline and integration status

The installed Android app is the primary product. Preserve its existing screens,
animated indigo/violet background, navigation, forms, group workers, AI entry,
history, selection, filters and foreground timer. The reference is the user-supplied
screen recording reviewed on 2026-09-07. Source baseline: commit
20c6022e3d35e8eea9beda3380c442208a18515a.

## Implemented in this branch

- Remove the forced sign-in screen. Optional sign-in remains in Settings.
- Never report a simulated login as an authenticated Firebase user.
- Keep the existing currency when editing a shift.
- Versioned JSON transfer preserves currency, saved earnings, group data, category
  rates and workers. Legacy records without currency are shekels.
- JSON import validates before a single database transaction and performs a multiset
  merge so repeated imports do not duplicate identical records. This is transfer
  deduplication, not conflict resolution for edited records or live sync.
- Remove destructive Room fallback; unsupported database migrations fail rather
  than erasing the database.
- Preview Android build has a separate package suffix and launcher label.

## Not yet enabled or verified

CLOUD_SYNC_ENABLED intentionally remains false. Do not enable it merely after
entering a Firebase key. Before enabling, implement and test user-scoped local
storage, stable cross-device record identifiers, queued offline operations,
deletion propagation, conflict handling and one-time legacy import. Current Room
IDs are installation-local Int values. Website timestamps cannot be used as these
IDs. Existing synchronization code is not production-ready.

Firebase project `workshiftsapp` already exists and Google sign-in is enabled.
The supplied screenshot shows an Android registration with package name
`AndroidManifest.xml`; it does not match `com.aistudio.worktracker.qztvdw`.
Correct Android and web registration/configuration, Google OAuth certificate
fingerprints, authorized website domains and deployed Firestore rules are still
required. Do not delete the existing registration or cloud records.

Vercel project is `salary-calculation`; keep its existing repository integration.
The connected Vercel tool returned no teams during this session. Do not create a
replacement project. Changes remain on a branch; production stays untouched.

## Release gates

1. Android preview compiles and transfer tests pass.
2. User compares preview to installed app; no claim of pixel identity until this.
3. Firebase rules prove that one account cannot read/write another account's data.
4. Verify mobile-to-web and web-to-mobile edits, payment status, deletes, offline
   reconnection, account changes and repeated imports using synthetic records.
5. Only then activate cloud sync and publish the production update.

Local Android build attempt was blocked before compilation by network access to
Gradle plugin repositories. The Android preview workflow is intended to run the
actual tests/build on GitHub; its outcome must be checked, never assumed.

## User feedback and next milestones (2026-09-07)

The user installed preview from PR #1, imported the original JSON successfully,
and reports that the appearance and reviewed behaviors look identical. This is
positive manual evidence, not proof of every feature's parity.

Current fixes: header-based clipboard table import (original 7-column export,
11-column CSV, reordered Hebrew/English columns, TSV/CSV/semicolon/pipe, quoted
multiline notes, dates and currency), atomic validation/import with row errors;
category defaults applied on initial form load, category/default changes and
opening the live-shift dialog, rather than taking the last historical shift rate.

Outstanding requirements explicitly requested by user:
- Website uses the same import contract and fixtures. No silent field guessing.
- Import preview with mapped columns, totals per currency and uncertain cells;
  support additional real-world layouts based on samples. Optional AI parsing
  may propose rows, but requires review before committing financial records.
- Settings support both Gemini and OpenAI GPT. Use actual API model IDs from
  official provider catalogs; the current hard-coded labels do not establish
  model availability. Refresh a server-maintained model catalog, so adding a
  model does not require replacing the installed Android app. Preserve user
  selection and expose retirement/unavailability explicitly. Authenticate the
  backend and store provider keys there. Preserve existing AI entry behavior
  until the replacement is connected and verified end to end.
- Regression comparison: regular/manual/overnight and group shifts, category
  rate after restart, editing currency/payment, filters, live timer, AI entry,
  JSON/table import, export with multiline notes. Compare aggregate totals per
  currency and original saved amounts; do not claim 100% identity from browsing.

## Continuation implementation, 2026-09-08

Android import/default/model changes at f2cb0fd913a5c6a0d5e73fd2476f8d99b918b1c6
passed GitHub Actions run 34171740810. Final follow-up adds import and AI review
before saving, and uses the explicitly public, fixed **test** signing key for preview
builds. The earlier preview used an ephemeral runner key: Android may refuse an
in-place update. Export the full JSON backup first, remove only the old preview
if necessary, install the new preview and re-import. Never remove the original app.
A private CI signing secret must be configured before promising repeatable in-place preview updates.

Implemented web changes (existing Vercel project, not a replacement Site):
- Header-based import for clipboard/file, original amounts/dates/currencies,
  multiline CSV and JSON group/category/worker preservation, multiset deduplication.
- Import review showing every incoming row, incoming/new totals per currency.
- Atomic complete local snapshot; CSV and full JSON export; category defaults;
  currency-aware totals and ordinary edit/payment/delete behavior.
- Removed simulated cloud login and automatic sample records.
- Optional real Firebase authentication for AI only; cloud sync remains disabled.
- AI model selector, refresh, natural language/table proposals and review.

Server `/api/ai` supports Gemini generateContent and OpenAI Responses API.
Authentication verifies Firebase ID tokens through the project API and requires
an explicit UID allowlist. Provider keys are server-side only. Catalog overrides
permit adding models without a client app update. Requests do not persist prompts
in the application; OpenAI requests set store:false. Live provider calls have NOT
been tested with real keys. Catalog 'available' means a key is configured, not a
successful live entitlement test. Unavailable/retired model errors are surfaced;
there is no silent model substitution. No new credentials were provisioned.

Server configuration required (set privately in existing Vercel project):
- FIREBASE_PROJECT_ID, FIREBASE_WEB_API_KEY, FIREBASE_AUTH_DOMAIN, FIREBASE_WEB_APP_ID
- AI_ALLOWED_UIDS (comma-separated explicit account UIDs; empty denies all)
- GEMINI_API_KEY and OPENAI_API_KEY
- Optional AI_MODELS_JSON array of {id, name, provider, model}; id=provider:model.
Android build AI_SERVICE_URL must point to the deployed HTTPS `/api/ai` endpoint.
Without it, existing direct Gemini is preserved and GPT reports not configured.
Do not put OpenAI/server keys in Android BuildConfig or public web files.
The pre-existing direct Gemini build-key architecture remains a legacy limitation.

Official model/API sources checked:
https://developers.openai.com/api/docs/models
https://ai.google.dev/gemini-api/docs/models
https://ai.google.dev/api/models

Local verification: node --test web-tests/*.test.cjs (12 tests initially passing),
node scripts/build-web.cjs, and syntax check of the inline web script.
Vercel list_teams returned [] again. No deployment or production merge performed.
Website is still not full Android feature parity: foreground timer, complete group
editing, advanced history/filter interactions and cross-device sync remain gates.
Do not present the web changes or configurable AI as a verified live synced app.

Final build 34172214385 compiled but failed signing because debug.keystore was not tracked. An attempt to commit a newly generated test key was rejected by automatic approval review because it would expose private signing material. No key was published. The safer fix restores the original runner-generated debug signing config. Do not retry publishing signing material. Stable preview updates require a privately configured CI signing secret; until then an export/reinstall of the preview may be needed. Vercel get_project for team sholi returned 403.

Android final run 34172685477 passed build and all selected Work* tests for commit 1cf78f60c003476fd77d7cf5f00d762660eccab7. The subsequent web-only continuation adds local active timer (wall-clock based, draft kept until save), overnight time-range input, group worker/rate/payment editing and financial detail matching the Android group formula, plus month/payment filters. These web interactions have static syntax/reference checks only, not browser parity certification. Full cross-device sync and visual/behavior parity remain unfinished.
