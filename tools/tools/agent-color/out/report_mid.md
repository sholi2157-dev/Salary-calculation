
### 5a. Workplace palette (proposed)
Slot order and names are unchanged, so the stored per-place colour index (`color:<category>` prefs) keeps its meaning — no data migration. Mustard keeps its exact identity colour `#F5B82E` (the search showed it does not limit separation). Each slot was moved only inside its named hue family, with lightness used as the second channel for the hue-adjacent pairs (indigo dark/purple light, rose light/burgundy dark, green light/teal dark).

| metric | current | proposed |
|---|---|---|
| worst CIEDE2000, light `from`, over normal+protan+deutan+tritan | **1.4** (indigo/purple, protan) | **8.5** (purple/blue, deutan) |
| worst CIEDE2000, dark accent | **0.7** (rose/burgundy, tritan) | **8.7** (rose/burgundy, tritan) |
| indigo/purple normal vision (light) | 6.2 | 16.6 |
| rose/burgundy normal vision (dark) | 3.8 | 14.6 |
| brand accent vs workplace #1 | 0.0 (identical) | 5.5 light / 5.0 dark |
| green place vs success (light) | 2.4 | 14.1 |
| white text on teal `from` | 4.20 (fail) | 6.6 |
| `from` chroma range (excl. mustard) | 0.096–0.248 | 0.075–0.225 (teal is gamut-limited at L 0.44) |

Honest limits: 8 categorical hues with three violets cannot reach ΔE00 ≥ 10 for every CVD type; ~9 is the practical ceiling found by the search under the "stay in your named family" constraint. The place letter avatar + name must remain the primary cue (colour never alone) — it already is in lists. Trade-offs to review physically: green becomes a purer "leaf" green (h 143, away from paid emerald h 165 — intentional), dark purple drifts toward orchid (h 327), dark burgundy becomes a clear red (keep ≥12.8 from danger). Brand vs workplace #1 only reaches ΔE 5.5 by colour; the real fix is by **role**: focus/toast/dots/switches no longer use indigo, so indigo on screen means "brand" only in FAB/glow/logo.

The proposed sets also fix dark-theme avatar sinking: use `accentDark` (or the light gradient with a 1px `line` ring) for dark avatars consistently; current light `from` on dark surface is 2.67–3.0.

### 5b. Usage rules that go with the tokens
- `fill` replaces `sunk` (ghost buttons, tracks, inputs). Light value unchanged.
- `ink3` (subtle/placeholder/meta) only on `bg`/`surface`/`surfaceRaised`, never on `fill` (it is 4.06 there in light).
- `line` is decorative only (dividers, card hairlines). Any edge that is the only cue for a control (outline button, input, switch-off track, unselected radio) uses `lineStrong`.
- Selected (chips, tabs, days, currency, filter segments, place chips when not colour-coded): `selectedBg` + 2px `selectedEdge` + `selectedInk`, identical recipe in both themes. Place chips may use the workplace accent, but then all *other* selections use the neutral recipe (fixes W2's five recipes).
- Primary button = `primary`/`onPrimary` (ink inverted). In dark it is `#EDF0F6`, not pure white; if the owner finds the light Save bar too loud in dark, the alternative is `primary = accent` / `onPrimary = accentInk` in dark only (6.81:1).
- Switch on = `controlOn` (ink), knob `controlKnob`; off = `lineStrong` track. Paid green is never used for controls.
- Focus ring: `focus`, 2px + 2px gap of the underlying surface, `:focus-visible` only (no programmatic focus of the first sheet button on touch — W9).
- Toast/snackbar action: `barAction`; danger confirm: `danger` + `onDanger` (dark-text in dark).
- Glow: alpha .12 light / .24 dark (was .22/.40) so captions under it keep ≥4.5.
- Pressed overlay `pressed` (ink 10%/12%), hover `hover` (ink 5%/7%), disabled = `disabledInk` on `disabledBg` (no opacity hacks).

### 5c. Token table
