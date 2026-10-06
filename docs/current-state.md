# Current Web track — 2026-10-06

The owner explicitly resumed the Web project. Android remains unchanged at
`codex/android-local-distribution` HEAD `b9dcba6fadfa17a370fa7b3fb8aa284a13eec1be`.
Signed RC13 is the product source of truth; attached APK digest and packaged
source revision are recorded in `docs/web-rc13-parity.md`.

Active implementation branch: `codex/web-android-rc13-parity`, isolated from
existing paused PR #1/`codex/preserve-app-sync`. Pre-rebuild web HEAD
`05794abf0b01a3e3c69d83123c48824fc7b87def` is backed up remotely at
`backup/web-before-rc13-parity-20261006`. Main is unchanged.

Read `docs/web-rc13-parity.md` for the active contract, full parity matrix,
rollback, test evidence, data/AI security boundaries and deployment gate.
Local build, 36 unit tests, existing mobile browser suite, new four-width
browser parity/22-8 migration/offline suite passed. No Android build/source change.
Physical browser native share/keyboard and live account sync remain unverified.
Web AI is isolated behind a disabled flag pending a secure server adapter.
Do not claim production replaced before a verified safe preview and promotion.

Application commit `e30160a3111c2648a1d6627a994ccfd1899f02f8` is deployed READY:
https://salary-calculation-3kk40j9ez-sholi.vercel.app/
Draft review PR: https://github.com/sholi2157-dev/Salary-calculation/pull/2
Web CI run 37437505046 passed build, 36 tests and both real-browser suites.
Live preview desktop/mobile save/reload also verified. Supplied production alias
and connected project's listed production alias returned 404; no promotion or
origin change was performed. See deployment path in the parity document.
