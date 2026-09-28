# Release readiness — 2026-09-28

Decision: **NOT READY** for the requested combined Web/Android user release.
Audited application HEAD: 1feeee6cc15391b47fd9bafabe01678a49310f12.
Branch codex/preserve-app-sync; PR #1 remains Draft. Audited Preview:
https://salary-calculation-i2ptedfa5-sholi.vercel.app/
No UI, business logic, data, Firebase configuration/rules, Android source or
production changes were made during this audit.

## Evidence and coverage

| Area | Actual result | Limit |
| --- | --- | --- |
| Guest CRUD, paid/unpaid, manual/clock/group | PASS, disposable mobile browser | Not signed-in live Firebase |
| Categories/default, reassignment/history preservation | PASS, unit + mobile flows | Default/currency prefs are local, not shared |
| ILS/USD, group and personal/worker share | PASS, saved totals and captured share output | Native physical Share Sheet not exercised |
| Search/filter/sort/multi-select | PASS, existing unit/mobile suite; delete accept/cancel | Synthetic records only |
| JSON/CSV/TSV/Excel paste, backup/restore/dedup | PASS, unit/mobile suite with preview validation | Not user backup or destructive migration |
| Refresh after saved guest changes | PASS | Account re-login not verified live |
| Offline open-page save, reconnect and reload | PASS in targeted disposable Chromium test | Guest only |
| Offline reload/start | FAIL: app unavailable after network-disabled reload | No service worker/offline shell; account startup fetches /api/config |
| Account isolation, conflicts, retry/lost ACK, tombstones | PASS in unit tests and Firebase Emulator | Does not attest deployed live rules or Android device |
| Live website email/password sign-in | FAILED attempt; generic website error | Cause not established; no repeated attempt |
| Registration, logout, password-reset completion | NOT VERIFIED live | Test inbox/account provisioning needed; reset receipt and password change require inbox/user handling |
| Two live users, permission-denied cross-user access | NOT VERIFIED | No authenticated live test session established |
| Live Web ↔ installed Android | NOT VERIFIED | No controllable installed Android device in this environment |
| Android installation/update continuity | NOT VERIFIED | Current preview uses CI debug signing; stable distribution/update identity is unresolved |

Local npm test rerun: 31/31; npm run build passed. Existing
browser-tests/mobile.cjs rerun passed at 390/360px, including edit/backdrop,
category delete/default, both currencies, group/worker share, bulk delete
accept/cancel, backup/restore and 420px keyboard-space simulation.
Targeted offline test: setOffline(true), save synthetic manual 2h x ILS40,
reload unavailable, setOffline(false), reload restored the same ILS80 record.
This is a guest browser/network test, not a live-account sync claim.

Current-HEAD CI verified from actual logs: run36454558048/job109038053143,
web31/31, Firebase emulator1/1, required Work* tests + assemblePreview
BUILD SUCCESSFUL6m23s, Android51 tests zero failures/errors/skips; additional
debug build20s. Java exists locally; Gradle/Firebase CLI dependencies were not
installed locally; the current-HEAD CI provides those required results.

## Concrete release gates

1. Establish two dedicated live test accounts with controlled inboxes; identify
   why the observed email/password attempt failed. Verify register/login/logout,
   reload/re-login, reset-mail delivery and reset completion. Never reuse the
   owner's real records or ask for passwords in chat.
2. Run the live cross-platform matrix below, including deployed Firebase owner
   rules. Emulator success and compatible payloads are insufficient evidence.
3. Android financial correctness: MainActivity.kt still hardcodes ILS in recent/
   main cards (3001,3065,3149,3246) and personal/group share (4552–4595), and sums
   mixed currencies at3405 with a single chosen symbol at3809. Fix on Android;
   test persisted USD/ILS separately, including edit/restart and all exports.
4. Category default/currency contract: website preferences are local/optional
   backup only; Android WorkCategory contains name/defaultRate and drops unknown
   metadata. Coordinate additive persistence + backup/sync support and old/new
   compatibility before promising identical defaults on both platforms.
5. Offline availability: open-page edits persist, but reopening/reloading without
   network is not supported reliably. Implement/test an offline startup path,
   including account configuration and identity restoration; preserve queued data.
6. Android distribution: select the intended package and stable protected signing
   process; verify first install and non-destructive update on test phones with
   existing data. Current .preview debug-signed CI output is not evidence of a
   maintainable release/update path. Never resolve a signature mismatch by reset.

## Exact Android/live handoff

Keep existing records_v1 schema, syncId, operation/version, tombstones and queues.
Use accounts A/B and disposable fixtures; record device/build SHA, UID label,
record syncId, expected/observed version, pending/conflict state and timestamps.

- A on Web: create manual ILS 2h x40=80 and USD 2h x45.75=91.50;
  Android A must display each saved currency/amount and the same sync IDs.
- Android A: change notes/hours through normal editing; Web A receives it.
- Both directions: change payment status and verify convergence after reload.
- Add clock-range overnight with break and a group with distinct employer/worker
  rates; verify own, worker and group totals without currency mixing.
- Delete only a fixture on each platform; inspect tombstone and confirm no
  resurrection after reconnect/restart on the other device.
- Disconnect Android, edit fixture, restart if supported, reconnect; repeat Web
  open-page and offline-reload cases. Confirm exactly one update/no duplicate.
- Edit same fixture on both devices while disconnected; verify both versions are
  retained, conflict choice is explicit and chosen version converges. Repeat
  edit-vs-delete and lost-ack/retry scenarios on disposable fixtures.
- B must see no A entries; authenticated B reads/writes to A paths must fail.
  Switch A→B while requests are in flight; no stale A rows may appear under B.
- Verify guest data is not adopted without confirmation. Export/restore fixtures
  and compare dates/hours/workers/status/amount/rate/currency and dedup behavior.

## Production gate (not executed)

After all blockers close and the owner explicitly approves: verify the exact
candidate SHA and existing salary-calculation project under sholi, production
Firebase configuration/authorized domains/rules, and the existing production
alias. Deploy/promote that verified candidate using the existing Vercel workflow;
run live smoke tests and retain the previous deployment for rollback. No new
project, forced push, PR merge or production change is authorized by this audit.
A stable production URL alone would not resolve the blockers above.
