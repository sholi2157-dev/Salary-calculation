# Android continuation — 2026-09-14

This checkpoint belongs to the Android conversation. Read AGENTS.md and
preservation-and-sync.md as well. Website work is owned by another conversation.

## Saved implementation, not yet Android-verified

Local code commit: `28c9bf4ea71f5ba24d163d75af8e28c191bbfed0`.
Based on remote `c32b6f4622a9d8c06b89c413784e661c5ca44d6f` on
`codex/preserve-app-sync`, existing PR #1.

- Expanded Android spreadsheet clipboard parsing: header aliases, explicit sep
  declarations, omitted trailing empty cells, duration seconds, AM/PM clocks,
  whole Excel serial dates and minute-exact Excel clock fractions. Ambiguous
  numbers/dates, conflicting currencies and malformed quotes still require correction.
  No claim to understand every possible input. Ten regression tests added.
- Edit forms preserve decimal hours and rates. ViewModel reads the original row
  and preserves createdAt and saved earnings unless hours or rate changed.
  Imported net hours and inferred breaks survive unchanged edits, including
  overnight ranges. Four preservation tests added.
- Timer Skip selects the category default; the launcher shortcut reads the saved
  category from Room instead of relying on a not-yet-loaded StateFlow.
- Import review shows category count, hours and paid/unpaid totals by currency.
  These help comparison; they do not prove exact equality with the original backup.
- Foreground timer completion no longer falls back to a shared mock account.
  Every Firestore entry point checks the sync flag and authenticated UID.
  Missing Firebase configuration is no longer marked available when initialization
  returns null. Three cloud access gate tests added.

## Verification and publication

- `npm test`: 21 passed locally; `npm run build`: passed.
- `git diff --check`: passed before the code commit.
- Android command attempted but unavailable locally: `gradle: command not found`.
- Code has NOT been pushed. Automatic approval review rejected git push twice.
  The second rejection followed verification through the authenticated GitHub
  connector that this is the user's public repository with push permission and
  existing PR #1. Review still requires explicit approval to publish these new
  changes to that public destination. Do not bypass via a different write tool.
- No new Android CI run or APK exists for this commit. Do not describe Android
  tests, device installation, or the modified flows as verified.
- `public/web/` is untracked generated build output from earlier web checks;
  it is not part of this Android commit.

## Next steps after publication approval

1. Fetch and preserve any newer remote commits; do not force-push.
2. Push the Android commit to the existing branch/PR and inspect actual CI logs
   for `gradle :app:testDebugUnitTest --tests 'com.example.Work*Test' :app:assemblePreview --console=plain`.
3. Fix failures, then update the verified results in preservation-and-sync.md.
4. Physical-device edit/swipe/credential persistence and installation still need
   verification. Existing preview builds have differing public signing fingerprints;
   assembly success is not proof of an in-place update. Never uninstall/reset or
   upload private signing keys to bypass this.

## Remaining product scope

Google sign-in remains gated off; email/password auth is absent. The local Room
database is shared, IDs are local integers, and remote sync lacks the complete
account isolation, offline mutation queue, tombstones and conflict handling needed
for safe release. Firebase Android configuration, OAuth client/certificate setup,
provider enablement and deployed rules also require verification. Do not merely
flip CLOUD_SYNC_ENABLED. No user's live data or Firebase configuration was changed.

The pasted historical request mentioned Gemini/GPT model selection, but the later
repository decision is personal Gemini-only credentials. No model/provider changes
were made here. Optional encrypted local key setup and swipe navigation already
exist; neither has been newly device-verified in this continuation.


## Publication block resolved

User explicitly approved publication on 2026-09-14. Published commit `7cb9ac293ff513d89a795550bb18496733d4a2eb` contains the same code and checkpoint tree. Android Work* tests and assemblePreview passed in run 34814354694; actual logs inspected. The historical blocked status and next steps 1–3 above are superseded. See preservation-and-sync.md for verified results and remaining device/account gates.


## Android account-storage and offline journal continuation — 2026-09-14

Resumed from remote `04a7c39f18ed4ec8d2c896e8dce01d7b3d43033f`. Confirmed the
previous Android fixes were already published and tested; did not repeat them.

Implementation commit: `6c7b7eb9757b449d34d85e8ed3fcc29e500d01e0`.
CI configuration commit: `9a076bbd3e94824af66528f8cf7cf2ea2ce164f2`.

### Implemented

- Room v5-to-v6 additive migration creates a local sync journal and backfills stable,
  random 128-bit IDs for existing shifts, categories and workers. The business tables
  and their IDs/amounts/metadata are not rewritten or replaced.
- SQLite triggers journal every local insert/update/delete in the same transaction,
  including direct DAO writes by the foreground service and atomic backup imports.
  Replacement of a row retains its stable identity. Deletion retains a tombstone.
- Pending mutations read the payload and revision in a single Room transaction.
  Acknowledgement uses revision/remote-version checks, so an old response cannot clear
  a newer edit/deletion. A conflict stores the remote candidate separately and pauses
  that record; no automatic overwriting or completed conflict-resolution UI is claimed.
- Added a separate account-database factory using SHA-256-derived filenames.
  Opening an account does not copy, claim or remove guest data. Default categories are
  seeded synchronously into the correct database instead of using the guest singleton.
- Seven Robolectric regression tests cover 22-shift/8-category synthetic migration and
  reopen, rollback atomicity, stable IDs through replacement, bulk category changes,
  worker writes, repeated imports with genuine duplicates, late acknowledgements,
  tombstones, conflict preservation and separate account databases.
- The first connector branch update did not produce an Actions run. Added a push
  trigger limited to the existing working branch, retaining PR/manual triggers and
  concurrency control. No permissions expansion or secret/signing changes.

### Remaining implementation — do not enable cloud sync yet

The UI/ViewModel still uses the original guest database. The new account factory is
a tested storage primitive, not completed runtime account isolation. Before enabling
accounts, bind repository/observers, settings, exports, drafts, undo, AI proposals and
active timers to the originating account; prevent delayed operations crossing a switch.
Implement reviewed one-time legacy adoption with a durable completion marker.

The journal has no network sender or remote-apply consumer yet. The old
FirestoreSyncManager still uses installation-local Int IDs and MUST NOT be enabled
against this journal. Implement a versioned stable-ID protocol, idempotent delivery,
remote tombstones, restart/reconnect scheduling, conflict review/resolution and
cross-device category/worker references. Keep local Int IDs out of remote identity.

Email/password screens/authentication remain absent; Google sign-in and
CLOUD_SYNC_ENABLED remain gated off. Firebase registration/OAuth fingerprints,
providers and deployed per-account rules are unverified. No Firebase administrative
connector, local Android runtime, physical device or browser automation was available
in this turn. End-to-end Android/web tests remain outstanding. Personal AI keys must
stay outside this journal and cloud payloads.

The seven tests use only synthetic data, not the user's backup. No device update,
production merge, real data access, credential upload or Firebase configuration change.

### Verified checks

Tested source: `9a076bbd3e94824af66528f8cf7cf2ea2ce164f2`.
- Actions run https://github.com/sholi2157-dev/Salary-calculation/actions/runs/34870187633:
  success. Actual job 104064413424 logs inspected.
- `npm test`: 21 passed, 0 failed; `npm run build`: passed.
- `gradle :app:testDebugUnitTest --tests 'com.example.Work*Test' :app:assemblePreview --console=plain`:
  BUILD SUCCESSFUL in 4m 26s; both tasks completed. This includes WorkSyncJournalTest.
- Test reports artifact: https://github.com/sholi2157-dev/Salary-calculation/actions/runs/34870187633/artifacts/10358494455
- Preview and installation metadata artifact: https://github.com/sholi2157-dev/Salary-calculation/actions/runs/34870187633/artifacts/10359425298
- Vercel commit status: success, https://vercel.com/sholi/salary-calculation/E5ZRL86L7EiMRs9tGhu1DYtL8L5e
- No browser or physical-device tests in this turn. No local Gradle/npm execution:
  only the GitHub Actions results above are claimed.
- Kotlin emitted two non-blocking inferred intersection-type warnings in the synthetic
  migration fixture's mixed-type arrayOf arguments; future cleanup can specify arrayOf<Any>.
- The duplicate push run 34870181723 was cancelled by concurrency control when the
  PR run started. Only the completed successful PR run is used as verification.

Preview package/version remain `com.aistudio.worktracker.qztvdw.preview`, code 2,
name 1.1-preview. Public signing certificate SHA-256:
`9599ba27e3b207ded718ca85f4bbe9722b19a342577c73b6775120ec825d5e0d`.
It differs from the previous build; this is not proof of in-place phone update
compatibility. No APK installation is requested or claimed fixed.
