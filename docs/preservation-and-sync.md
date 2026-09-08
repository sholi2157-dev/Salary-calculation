# Preservation baseline and integration status

## Mandatory preservation rules

Read this document and root AGENTS.md before changes. Work on `codex/preserve-app-sync` and PR #1 in the existing `sholi2157-dev/Salary-calculation` repository and existing Vercel project `salary-calculation`. Do not merge or change production as though sync is complete.

The installed Android app is the primary product. Preserve its screens, animated indigo/violet background, navigation, manual/group/AI entry, history, filters and foreground timer. Reference: user recording reviewed 2026-09-07; original source baseline `20c6022e3d35e8eea9beda3380c442208a18515a`. The user installed the earlier preview, imported their original data successfully and reported matching appearance; this is useful feedback, not proof of complete parity.

The user's reported backup contains **22 shifts and 8 categories**. Never reset, delete or replace user storage, discard shifts, enable destructive Room migrations, or recommend uninstalling to fix an update. Do not upload signing keys or secrets. Use synthetic records for tests. No live user database was accessed or modified in this continuation.

## Implemented

- Optional sign-in; no simulated successful authentication.
- Currency preserved when editing shifts.
- Versioned JSON transfer retains saved amounts, dates, currency, group/category/worker metadata and decimal rates. Legacy missing currency means shekels.
- Atomic validation/import and multiset deduplication retain genuine repeated shifts without duplicating repeated imports. This is transfer deduplication, not live-sync conflict resolution.
- Header-based Hebrew/English table import handles reordered CSV/TSV/semicolon/pipe columns, the original clipboard export, Excel dates/decimal commas and quoted multiline notes. Invalid or ambiguous input requires correction; no claim that every possible format is supported.
- Review before saving imports and AI proposals.
- Saved category defaults feed manual, timer and AI entry.
- Web local atomic snapshot, JSON/CSV export, currency-separated totals, overnight ranges, timer drafts, group workers/rates/payment editing, month/payment filters.
- Vercel configuration removed only the unsupported `public: true` property; other settings and headers retained.
- Android debug and preview use standard AGP debug signing; preview has a separate package suffix/launcher label.

## Personal Gemini keys — current user decision

The user explicitly replaced the previous model-selection/shared-key design. Use **Gemini 3.5 Flash only**, with a key the user enters personally.

- No owner Gemini key is embedded in any build, including debug/preview. No model selector or GPT route.
- Android encrypts the entered key with AES-GCM and an Android Keystore key, writing atomically to `noBackupFilesDir`. It is outside Android backups and shift exports. Removal only removes this credential.
- The website keeps the personal key in page memory only. Refresh/close requires re-entry. It is not stored in localStorage, sessionStorage, cookies, exports or the account.
- Requests send the personal key in an HTTPS header directly to Google's fixed Gemini endpoint. Our shared AI server endpoint returns 410 and never falls back to an owner/server credential.
- Without a personal key, AI stops with a clear message. Ordinary entry/import/export continues.
- Each key uses its Google project's quota. Separate users need keys from their own separate projects to separate quota/billing; new keys in the owner's same project do not achieve this.
- This prevents distributing the owner's key; it does not make a personal client-held credential immune to compromised devices, browser extensions or injected scripts. Real keys must never be pasted into chat or test fixtures.
- No real-key/provider call or physical-device Keystore round-trip has been verified this session. UI confirmation means a key was saved/activated locally, not that Google accepted it.
- Earlier installed APKs may still contain an old build-time key. Removing this from new code does not revoke an old key or erase old binaries. If previously distributed, the owner should rotate it privately after validating the replacement.

Official references:
- https://ai.google.dev/gemini-api/docs/api-key
- https://ai.google.dev/gemini-api/docs/rate-limits
- https://developer.android.com/privacy-and-security/keystore

## Verified continuation, 2026-09-08

Tested code commit: `d10d5f1de057494519029919df4a4dce9dd422e1`.

Web checks passed locally and in GitHub Actions; Android passed in GitHub Actions:
- `npm test`: 13/13 transfer and personal-key tests.
- `npm run build`.
- `gradle :app:testDebugUnitTest --tests 'com.example.Work*Test' :app:assemblePreview --console=plain`: BUILD SUCCESSFUL; testDebugUnitTest, assemblePreview and validateSigningPreview completed.
- Actions: https://github.com/sholi2157-dev/Salary-calculation/actions/runs/34218243894
- APK: https://github.com/sholi2157-dev/Salary-calculation/actions/runs/34218243894/artifacts/10052833666

Android ran in GitHub Actions. The earlier local Gradle installation had a corrupt distribution JAR and could not compile the project locally.

Vercel reported successful deployment for that commit:
https://vercel.com/sholi/salary-calculation/ETgBu1xU5yGPETV11udUe7tdGzpu

Browser opened the authenticated preview successfully:
https://salary-calculation-git-codex-preserve-app-sync-sholi.vercel.app/

Browser checks passed with **synthetic** data:
- Imported 22 shifts across 8 categories: 44 hours and 2,200 shekels.
- Reload retained entries; repeated import proposed 0 new / 22 existing records.
- Selecting an imported category after reload supplied its saved hourly rate of 50.
- Missing personal key prevents AI; synthetic key activation does not claim provider success; refresh forgets that key.
- Desktop layout visually inspected. This is not full Android/web parity or mobile-device certification.
- Browser-extension metadata errors were observed; they were not application errors.

The synthetic fixture is not the user's actual backup; its successful test must not be described as verification of the real 22/8 backup.

## Signing and installation limitations

No fixed test keystore was published. Historical notes claiming otherwise were incorrect: automatic approval review rejected that proposal. The missing root debug.keystore configuration was removed.

Runner-generated debug certificates may differ between APK builds. Successful assembly/validateSigning does not prove in-place update compatibility with an installed preview. The earlier phone screenshot only says installation failed; it does not establish the precise cause. Do not uninstall/reset to work around this, do not request signing-key uploads, and do not claim the phone update is fixed.

## Firebase and cloud-sync gates

`CLOUD_SYNC_ENABLED` remains false. Firebase/cloud sync is not enabled or verified. The existing Firebase project was reported as `workshiftsapp`; prior screenshots showed an Android registration named `AndroidManifest.xml`, which does not match the app package. Do not delete registrations or cloud records. Correct project registrations, OAuth fingerprints, domains and deployed rules still require verification.

Before enabling sync, implement stable cross-device IDs, user-scoped local storage, queued offline operations, deletion propagation, conflict handling and one-time legacy import. Current Room Int IDs are local to an installation.

Required end-to-end tests: Android-to-web and web-to-Android edits, payment status, deletion, offline/reconnect, separate accounts, permission denials and repeated imports. No production/sync release until these pass. Vercel tool access to team sholi previously returned 403; deployment success was checked through the GitHub Vercel status and the actual browser preview.

Previous verified baseline: commit `658b8be7c66d7162d05d2a0fb36eea1ccf64faa9`, Android run 34189434959. This document supersedes earlier instructions to use GPT/catalog selection, shared owner-key AI or reinstall a preview.
