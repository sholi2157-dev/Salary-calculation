# Website parity and Android follow-up — 2026-09-22

Scope: website source only; existing accounts and records_v1 sync remain enabled.
No Android source, signing, installation, production deployment or real user data
was changed. PR #1 remains a draft on codex/preserve-app-sync.

## Website behavior

Heebo replaces the previous fonts; purple surfaces, SVG actions, expandable
history cards, settings accordions and dark icon toasts follow the supplied video
and Android source. Website AI and background timers remain absent.

Category selection in shift forms is restricted to existing categories. Category
changes are staged in settings, with an explicit default and delete confirmation.
Rename/delete only change the historical category label: saved earnings, rates,
currency, workers, notes, date and payment state remain intact. Normal modal
backdrops discard drafts; confirmation dialogs require a decision.

Amounts always use the entry currency. Totals are separated by currency, including
group sharing, copied summaries and exports. Share uses the device Share API,
then clipboard or a selectable text dialog; tests capture output without sending.
Bulk mode supports long touch, selection and payment/deletion, without sharing.
Search clear keeps the field open; closing resets the query and filter.

## Compatibility boundary

The shared records_v1 envelope (schema 1), payload formatVersion 2 and stable
syncId semantics are unchanged. Tombstones, retry/offline queue, conflicts and
explicit guest adoption remain. The store now matches a renamed category by its
existing _syncId before its name, preserving identity instead of recreating it.

Current Android WorkCategory/WorkBackup encode only category name and defaultRate.
Unknown additions would be discarded on an Android rewrite. Therefore website
defaultCategory and categoryCurrencies are **not added to the shared payload**.
They live in an account/guest-scoped webPreferences snapshot, alongside mainCurrency.
JSON backups include this optional extension; import applies it only on explicit
selection. These preferences do not yet sync between devices or to Android.
Existing category names/rates and saved shift currencies still use existing sync.

For cross-device category defaults/currencies, coordinate an additive Android
Room migration and WorkBackup/sync encoder/decoder support first. Preserve legacy
defaults (initial עצמאי, ILS for old category rates), unknown extension data and
stable category IDs. Test both old/new clients editing and restoring backups
before placing these preferences on the shared wire. No unilateral migration was
performed here.

## Exact Android checks to carry forward

References below are MainActivity.kt in the current source; line numbers may move.

- Adopt Heebo and the website's separate search clear/close behavior as appropriate.
- Audit hardcoded ILS in recent/main cards and hourly labels (around 3001, 3065,
  3149, 3246), personal share (4552–4553), group share (4583–4595), category rate
  display (4952), and the edit-rate label (5897). Use each saved entry's currency.
- History sums at 3405 and the chosen symbol at 3809 can label mixed-currency
  totals as ILS. Report builders around 193–227 have the same issue. Partition
  money by currency; do not relabel or add USD and ILS.
- Verify both add/edit save callbacks persist the selected currency, reopening
  and restarting preserve it, and changing main/category defaults never rewrites
  historical amounts. Test individual and group entries in each currency.
- Personal group sharing must contain only the user's saved share; the document
  action includes everyone. Remove hardcoded user names and currency from reports.
- Coordinate configurable default category and category currency support as
  described above. Deleting a category must reassign labels without recalculation.

These are source findings and requested follow-up, not claims of fixes in Android.

## Repeatable website verification

Run npm test and npm run build. browser-tests/mobile.cjs uses a fresh disposable
Chromium context and synthetic entries only. Install Playwright in a test runtime
and a compatible Chromium, then run:

    PLAYWRIGHT_MODULE=/path/to/playwright CHROMIUM_PATH=/path/to/chromium node browser-tests/mobile.cjs

The suite starts its own local server, blocks Firebase SDK loading, and captures
share/clipboard output. It covers ILS/USD save/reload, group totals/share, staged
settings/cancel, category rename/deletion/default protection, edit/backdrop,
long-touch and bulk delete accept/cancel, payment, overnight clock range,
search/focus/close, backup download, invalid import and duplicate preview, TSV,
and 390/360px widths. A shortened 420px viewport simulates keyboard space; it is
not a physical phone keyboard test. Native Share Sheet, real account login and
physical Android↔website sync remain unverified. Emulator tests cover synthetic
account separation, offline retry/conflicts/deletion and category identity/currency.

See preservation-and-sync.md for verified commit, CI and Preview evidence.
