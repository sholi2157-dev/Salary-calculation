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
