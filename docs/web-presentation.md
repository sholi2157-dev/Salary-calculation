# Website presentation ownership

The Android recording is the visual reference; the website intentionally omits
AI and running-shift controls. Existing business rules are not presentation code.

- `index.html`: accessible screen/dialog structure and stable DOM IDs; the one
  report form moves between home and edit without replacing its handlers.
- `web/app.css`: sole stylesheet, mobile-first tokens, shared controls and
  component sections. No historical override blocks, text glow or retired
  timer styles. Change the owning component rule, not the end of the file.
- `web/presentation.js`: icons, shift-card and category-row DOM rendering.
  Event callbacks call the established orchestration functions.
- `web/parity.js`: UI state and interaction orchestration. Existing category
  drafts, selection, dismiss, share and persistence paths remain intact.
- `web/accounts.js`, `cloud-sync.js`, `transfer.js`, `categories.js`,
  `selection.js`, `sharing.js`: unchanged account/data/business contracts.
- `scripts/build-web.cjs`: copies sources into public; HTML asset versions and
  build-info commit/CSS digest establish what was actually deployed.

All IDs and input event hooks were retained during consolidation. Native web
select/date/time controls intentionally remain for keyboard and mobile support.
Import keeps the additional file/download and review controls absent from the
Android recording. Guest test fixtures must not reach live Firebase.

Validation: npm build/test, browser-tests/mobile.cjs (360/390px synthetic guest
flows), required existing Android CI gate, then actual Preview at phone width.
