# Salary-calculation Web maintenance

Current owner request supersedes historical scope notes in older documents.
Read `docs/current-state.md`, `docs/web-rc13-parity.md` and
`docs/preservation-and-sync.md` before editing.

- Active Web rebuild branch: `codex/web-android-rc13-parity`. Keep the existing
  `codex/preserve-app-sync`/draft PR #1 and production branch intact.
- Android RC13 on `codex/android-local-distribution` is authoritative for current
  product behavior/design. Do not modify Android implementation, package,
  signing, versions or distribution as part of Web work.
- The exact previous web state is backed up remotely at
  `backup/web-before-rc13-parity-20261006` (`05794abf`). Never overwrite the only
  valid copy. Preserve the existing Vercel project/origin; verify a safe preview
  before any production promotion.
- Never reset storage, replace a database, silently recalculate stored amounts,
  adopt guest records into an account, or discard records/unknown worker metadata.
  Use synthetic fixtures for tests, including the realistic 22 shifts/8 categories.
  Category deletion reassigns historical labels to the configured default.
- Hebrew RTL, Heebo, compact fields, separate search clear/close, long-touch
  selection, currency-separated totals, sharing, local operation, timer and
  onboarding follow current Android. Keep browser platform exceptions isolated.
- No provider key in client code/configuration/storage/backups. Web AI stays
  feature-flagged off until the secure server adapter is verified. Never restore
  the historical owner-key endpoint. Preserve Android's existing personal-key flow.
- Keep legacy optional accounts/wire data compatible; no mandatory login or new
  cloud requirement. Never claim live account/Android sync from unit tests alone.
- Web validation: `npm run build`, `npm test`, `browser-tests/mobile.cjs`,
  `browser-tests/parity.cjs`, `git diff --check`, then inspect actual CI logs and
  live preview. Browser contexts use synthetic data only. Android-only builds
  are not required for a Web-only change; no Android file is changed here.
- `PLAYWRIGHT_MODULE` and `CHROMIUM_PATH` can point to installed QA dependencies.
  `.github/workflows/web-parity.yml` provides repeatable browser evidence.
- `web/app.css` owns styling. Preserve component/data contracts rather than
  adding a generic dashboard or unrelated infrastructure. Update the service
  worker shell cache version when releasing changed cached files.
- Commit/push finished work and record exact source/test/deployment status.
  Report blocked checks honestly. Never print or commit secrets/credentials.
