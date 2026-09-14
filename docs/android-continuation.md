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
