# RC14 currency, cancellation and feedback

Baseline: 51ca2908349681141f17461399615ea00f88b870; RC13/code21.
Android-only owner-requested update, candidate 1.5-rc14/code22.

- Saved shift editor passes explicitly selected currency into the existing
  edit pipeline. Currency-only corrections preserve stored earnings and metadata;
  they do not convert monetary values at an exchange rate.
- Timer setup shows currency, preserves an explicit choice, and passes it through
  the start callback. Missing category currency falls back to the saved global
  default. Existing category currency remains an explicit override.
- Active timer currency updates persist without replacing start/rate/category.
- Cancel prompts before discarding. Only confirmation records an atomic timer
  discard and stops service; prior entries remain. Stale timer callbacks cannot
  cancel a newer timer or resurrect a discarded timer.
- Settings section toggling is confined to its header. Dialog scrim accepts
  pointer taps only, and feedback uses multiline/default IME, preventing Enter
  from activating ancestor click targets.

Validation pending: consolidated regression, permanent signed original RC13 to
RC14 update/data gate, and native currency/cancellation/edit/Enter regression.
No stable/latest feed or public publication included. Owner physical-phone review
remains required after signed candidate delivery.
