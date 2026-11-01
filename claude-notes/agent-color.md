# Agent: colour-system audit — Light vs Dark identity ("כיס")

Scope: read-only audit of the token sources (`KisTheme.kt` KisLight/KisDark/Workplaces, `kis.css` :root + dark blocks,
`ui.js` PLACES), the rendered web census (`notes/explore-light_dark.json`), and the screenshot pairs in `shots/pairs`.
All numbers are produced by the scripts in `tools/agent-color/` (pure Python, no deps):

| script | what it does |
|---|---|
| `colorlib.py` | WCAG 2.x contrast + alpha compositing, OKLab/OKLCH (Ottosson), CIELAB + CIEDE2000, Machado-2009 CVD matrices (severity 1.0, linear sRGB) |
| `tokens_current.py` | current tokens (Android ARGB alpha converted; web and Android are value-identical) |
| `audit_current.py` | contrast matrix, OKLCH identity, workplace ΔE + CVD, semantics, census aggregation → `out/current.json`, `out/current.txt` |
| `optimize_workplaces.py`, `refine_workplaces.py`, `wp_eval.py` | search for a CVD-separated workplace palette inside each named hue family |
| `tokens_proposed.py` | the proposed token system (defined in OKLCH) |
| `validate_proposed.py` | validates 181 proposed pairs, emits `out/proposed_contrast.md`, `out/proposed_tokens.md`, `out/kis-tokens.css`, `out/KisTokens.kt` |
| `swatches.py` | `shots/agents/palette-current-vs-proposed.png` |

Re-run: `cd ux-audit/tools/agent-color && python3 audit_current.py && python3 validate_proposed.py && python3 swatches.py`.

## 0. Web ↔ Android token parity
Values are identical (Android `nav 0xE6FFFFFF` = web `rgba(255,255,255,.90)`, `bar 0xF2FFFFFF` = `.95`). Differences are in
*usage*, not values:
- Web FAB = `--glow` with `#fff` glyph → in dark `#FFF on #8F87FF = 2.97:1` (fails 3:1 even for the 28px glyph). Android FAB tints the glyph with `Workplaces[0].accentInk(dark)` = `#120F3A on #8F87FF = 6.13:1` (passes). Web should copy Android.
- Web `.btn.danger` uses `#fff` on `--danger`; in dark that is `#FFF on #FF8A7A = 2.29:1` (settings erase confirm, `ui.js:112` confirm sheet). Fails AA badly.
- Web focus ring = `3px solid var(--glow)`; Android has no equivalent token.
- Web dark avatars/list blocks keep the **light** `from→to` gradient (seen in `05-history`, `06b-places` dark), while the editor uses `accentDark`. Both platforms mix the two families in dark.
- `kis.css` duplicates the dark block (media query + `[data-theme=dark]`) — drift risk (already W25).

## 1. Contrast matrix (current)
Census check: the rendered web uses a disciplined set — light text colours are 9 values (ink 1047 nodes, muted 528, white 164, navInk 62 …), dark 10 values (white 1159, B3B3B3 590 …). So the matrix below covers practically every real pair.
The census also shows tokens outside the theme: `#0D1015` logo tile (37 nodes, blue-black hue ~255 in **both** themes — in dark it is the only tinted near-black next to pure-grey surfaces), frosted `surface/0.6` & `/0.55` panels, and `ink/0.12`, `ink/0.25` borders.

### 1a. Failures (current)
| theme | kind | fg | bg | ratio | need | note |
|---|---|---|---|---|---|---|
| light | text | danger `#C2362B` | sunk `#E4E8F0` | **4.43** | 4.5 | axe-confirmed (detail sheet "מחיקה") |
| light | text | paid `#0B7F60` | bg `#F1F3F8` | **4.48** | 4.5 | new: paid text directly on page |
| light | text | paid `#0B7F60` | sunk `#E4E8F0` | **4.05** | 4.5 | new: paid on ghost/sunk chips |
| light | text | muted `#5A6478` | glow peak `#CECDF4` | **3.87** | 4.5 | new: top-of-page radial glow (`--glow-a .22`) under header captions; worst-case point |
| dark | text | muted `#B3B3B3` | glow peak `#444171` | **4.47** | 4.5 | same, dark (`--glow-a .40`) |
| light | text | white | teal.from `#0E8A86` | **4.20** | 4.5 | new: teal workplace chip/avatar/selected place |
| light | text/ui | mustard.from `#F5B82E` | surface | 1.78 | 3/4.5 | only if used as text/dot on white (dot in legend, ring) |
| dark | text | `#FFF` | danger `#FF8A7A` | **2.29** | 4.5 | web `.btn.danger` |
| dark | glyph | `#FFF` | glow `#8F87FF` (FAB) | **2.97** | 3.0 | web FAB |
| light | ui | line `#D2D8E3` | surface / bg | 1.43 / 1.29 | 3.0 | outline buttons (`.btn.outline`), inputs, switch-off track — 1.4.11 |
| dark | ui | line `#303030` | surface / bg | 1.26 / 1.42 | 3.0 | same in dark |
| light | ui | switch knob `#FFF` | switch-off track `#D2D8E3` | 1.43 | 3.0 | off-state switch practically invisible on white cards |
| light | ui | disabled (`opacity .45`) ink | surface | 2.75 | (exempt) | ok legally, but no explicit token |
| dark | ui | indigo/burgundy/purple `from` avatar | surface `#1E1E1E` | 2.67 / 2.69 / 3.00 | 3.0 | light gradients reused in dark sink into the surface |

Passes worth noting: focus ring `#5145E8` 6.2:1 on white / 5.6:1 on bg; dark `#8F87FF` 6.3 / 5.6. Toast action `#8F87FF` on dark bar `#2C2E33` = 4.58 (barely). Pending on pendingSoft: light 5.10, dark 8.41. Dark white ink on `#121212` = **18.7:1** (above the ~15–16 comfort ceiling → halation on OLED; muted `#B3B3B3` 8.9).

Full current matrix (148 pairs) is at the end of this file (Appendix A).

## 2. Identity analysis (OKLCH)
| token | light hex | OKLCH (L C h) | dark hex | OKLCH (L C h) |
|---|---|---|---|---|
| bg | `#F1F3F8` | 0.964 0.007 269 | `#121212` | 0.182 **0.000** — |
| surface | `#FFFFFF` | 1.000 0 — | `#1E1E1E` | 0.235 0.000 — |
| sunk | `#E4E8F0` | 0.930 0.012 265 | `#2A2A2A` | 0.285 0.000 — |
| line | `#D2D8E3` | 0.881 0.017 263 | `#303030` | 0.309 0.000 — |
| ink | `#1B2335` | 0.258 0.036 266 | `#FFFFFF` | 1.000 0 — |
| muted | `#5A6478` | 0.502 0.034 264 | `#B3B3B3` | 0.767 0.000 — |
| navInk | `#465066` | 0.431 0.039 266 | `#B3B3B3` | 0.767 0.000 — |
| bar | `#FFFFFF`@.95 | — | `#2C2E33` | 0.301 **0.009 268** |
| nav | `#FFFFFF`@.90 | — | `#181818` | 0.209 0.000 — |

Findings:
- **Light is one coherent cool family**: every neutral sits at hue 263–269 with chroma rising from 0.007 (bg) to 0.036 (ink) — a proper tinted ramp. **Dark has chroma 0.000 on every neutral** (Material "#121212 grey"). Mean neutral chroma: light 0.021 vs dark 0.000; hue mismatch is therefore total, not a shade.
- The only tinted dark tokens are accidental: `bar #2C2E33` (h 268, C .009) and `editInk/onBar #F1F3F7` (h 265). So dark already contains the light hue in two places → the toast/bar looks bluish against grey cards, and there are two whites (`#FFFFFF` ink vs `#F1F3F7` editInk/onBar).
- The indigo page glow (`--glow` radial) tints the top of dark pages violet (`#444171` at peak) over pure-grey cards — visible in every dark pair screenshot: blue-violet sky over neutral grey slabs.
- **Elevation logic**
  - Light: bg 0.964 → surface 1.000 (+0.036, plus shadow) and sunk 0.930 below bg — consistent "cards float, controls recess".
  - Dark: bg 0.182 → surface 0.235 → sunk 0.285 → line 0.309. Steps are good (+0.05 L per level), but **`sunk` is above `surface` in dark and below it in light** — the same token means "recessed" in light and "raised" in dark. Controls using it (ghost buttons, tracks) are recessed in one theme and raised in the other. Rename to a neutral role (`fill`) with per-theme values (what the proposal does).
  - Dark **nav `#181818` (L 0.209) is darker than cards (0.235)** — the floating bottom bar sits *below* content elevation, inverted vs light (white glass on top). The bar `#2C2E33` (0.301) is correctly raised, so nav and toast disagree.
  - Dark selected states use full white fills (`--pick-bg/--tab-on: var(--ink)` → `#FFFFFF`, primary button white, nav-on white, live card white): the brightest objects on a dark screen are selection chrome, not content. Light selection = white tile + ink edge. The two themes express "selected" with **opposite** visual logic (W2/W5 confirmed by token analysis: `--pick-bg` light = surface, dark = ink).

## 3. Workplace palette
OKLCH of current `from`: indigo 0.513/0.234/277, green 0.532/0.112/163, rose 0.588/0.201/5, mustard 0.818/0.157/82, purple 0.547/0.248/293, blue 0.567/0.185/258, teal 0.573/0.096/191, burgundy 0.515/0.176/11.
Chroma is unbalanced: 0.096 (teal) to 0.248 (purple) — teal/green read dull next to neon purple/indigo.

**Brand accent `#5145E8` = workplace #1 `from` exactly (CIEDE2000 = 0.0)**, and dark brand `#8F87FF` = workplace #1 `accentDark` (0.0). Every indigo place is visually "the app's own colour": FAB, focus ring, glow, notification dot, toast action and recap banner all read as "this place".

Closest pairs, CIEDE2000 (≈ <10 hard to tell apart as small swatches, <5 near-identical):

| set | normal | protanopia | deuteranopia | tritanopia |
|---|---|---|---|---|
| light `from` | indigo/purple **6.2**, rose/burgundy 8.7, green/teal 13.5 | indigo/purple **1.4**, purple/blue 7.2, indigo/blue 8.2 | indigo/purple **3.8**, purple/blue **3.8**, green/burgundy 6.1 | green/teal **5.7**, rose/burgundy 7.7, blue/teal 8.9 |
| light `to` (name side) | indigo/purple **3.7**, rose/burgundy **4.7** | indigo/purple **2.2** | green/rose **0.8**, indigo/purple 2.6 | green/teal **1.6** |
| dark `accentDark` | rose/burgundy **3.8**, indigo/purple 7.2 | indigo/purple **2.5**, purple/blue 4.3 | purple/blue **0.8**, indigo/blue 4.1 | rose/burgundy **0.7**, green/teal **2.6** |

Worst case current: **1.4 (light) / 0.7 (dark)**. The suspected pairs are all confirmed; additionally the *dark ends* (`to`) are near-identical in deutan (green/rose 0.8) and tritan (green/teal 1.6), and rose/burgundy is near-identical in dark even with normal vision (3.8). 9+ places also repeat indigo (W13).
Collisions with semantics (normal vision): green place ↔ paid green **2.4** (light) / 6.8 (dark); mustard place ↔ pending amber 5.6 (dark); burgundy ↔ danger 13.1–13.9.

## 4. Semantic colours
| | light hex | OKLCH | dark hex | OKLCH |
|---|---|---|---|---|
| paid | `#0B7F60` | 0.531 0.105 168 | `#34C79A` | 0.743 0.138 167 |
| pending | `#9A5000` | 0.511 0.124 57 | `#FFC266` | 0.852 0.129 76 |
| danger | `#C2362B` | 0.544 0.179 29 | `#FF8A7A` | 0.758 0.145 29 |
| pendingSoft | `#FFEBC7` | 0.947 0.052 82 | `#3A2D12` | 0.305 0.046 84 |

- Light is well balanced: L 0.51–0.54, hues 57/168/29 well apart. But all three sit right at the 4.5 edge on `sunk` (paid 4.05, danger 4.43 fail).
- Dark lightness spread is 0.743–0.852 (Δ0.11): pending is visibly brighter/"louder" than paid and danger; and **pending shifts hue by +19° between themes** (57 → 76: brown-orange in light, yellow-amber in dark) — the only semantic that changes family across themes. Paid and danger keep hue (±1°) — good.
- Danger chroma drops 0.179 → 0.145 in dark (fine) but there is no `onDanger`: solid danger buttons use white (fails in dark, 2.29).
- Missing: success/danger/info *soft* fills, `info`, `on*` colours. Switch-ON uses `--paid` (money semantics on a generic control, W4).

## 5. Proposed semantic token system ("Kis 2")
Principles: (1) one neutral hue (264) for both themes — dark neutrals get C 0.010–0.019, enough to read as "the same navy family" without looking blue; (2) role names that mean the same thing in both themes (`fill`, not `sunk`); (3) selection = brightest neutral + ink edge in both themes (no white slabs in dark); (4) actions are ink (as KisTheme's own header says), brand indigo is reserved for brand moments (FAB, glow, focus, bar action), switch-on is ink not money-green; (5) light theme values that already work are kept verbatim (bg, surface, fill, line, ink, ink2, warningSoft, brand accent) so the light change is near-invisible; (6) every text pair ≥4.5, UI ≥3, validated.

**Validation: 181 pairs checked, 0 failures** (full table Appendix B). Key numbers:
- light: ink/bg 14.13, ink2/fill 4.85, success/fill 4.72 (was 4.05), danger/fill 4.75 (was 4.43), warning/fill 4.77, accentText/fill 5.08, lineStrong/bg 3.57 & /fill 3.23 (was 1.29), white on solid danger 5.83, ink2 on glow peak ≥4.5 with glow alpha .22→.12.
- dark: ink/bg **16.45** (was 18.7 → less halation), ink2/bg 9.53, ink2/fill 7.11, success 9.63, warning 10.59, danger 8.61 on bg; FAB glyph `accentInk` on accent **6.81** (was 2.97); onDanger on danger **8.23** (was 2.29); lineStrong/bg 4.12 & /fill 3.07; selectedInk on selectedBg 8.06; barAction on bar 6.79 (was 4.58).
- dark semantic lightness now L 0.770 / 0.820 / 0.770 (success / warning / danger) instead of 0.743 / 0.852 / 0.758; light 0.495 / 0.515 / 0.528. Pending hue drift reduced from 19° to 16° (58 light → 74 dark; amber cannot stay at 58° at L 0.82 without turning orange-brown; this is the perceptual minimum).
- Dark elevation (L): bg 0.181 → surface 0.221 → surfaceRaised 0.263 (sheets, nav) → fill 0.293 (controls) → line 0.335 → selectedBg 0.40. Nav now = surfaceRaised @ .92, i.e. above cards (fixes inversion). Bar/toast/live card = 0.315 (raised, not white).

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
| white text on teal `from` | 4.20 (fail) | 7.53 |
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
| token | light | light OKLCH | dark | dark OKLCH |
|---|---|---|---|---|
| bg | `#F1F3F8` | oklch(0.964 0.007 269) | `#0F1216` | oklch(0.181 0.010 256) |
| surface | `#FFFFFF` | oklch(1.000 0.000 90) | `#181B20` | oklch(0.221 0.011 261) |
| surfaceRaised | `#FFFFFF` | oklch(1.000 0.000 90) | `#21252B` | oklch(0.263 0.013 258) |
| fill | `#E4E8F0` | oklch(0.930 0.012 265) | `#282C34` | oklch(0.293 0.016 264) |
| line | `#D2D8E3` | oklch(0.881 0.016 263) | `#32373F` | oklch(0.335 0.016 260) |
| lineStrong | `#778093` | oklch(0.599 0.031 265) | `#717681` | oklch(0.565 0.018 266) |
| ink | `#1B2335` | oklch(0.257 0.036 266) | `#EDF0F6` | oklch(0.955 0.009 265) |
| ink2 | `#5A6478` | oklch(0.502 0.034 264) | `#B3B9C5` | oklch(0.785 0.018 264) |
| ink3 | `#636C7E` | oklch(0.530 0.030 264) | `#959CA8` | oklch(0.691 0.019 261) |
| disabledInk | `#9EA5B2` | oklch(0.720 0.020 263) | `#5F636C` | oklch(0.499 0.015 267) |
| disabledBg | `#E4E8F0` | oklch(0.930 0.012 265) | `#21252B` | oklch(0.263 0.013 258) |
| accent | `#5145E8` | oklch(0.513 0.234 277) | `#9398FF` | oklch(0.720 0.149 280) |
| accentInk | `#FFFFFF` | oklch(1.000 0.000 90) | `#15153A` | oklch(0.221 0.070 279) |
| accentSoft | `#E8ECFF` | oklch(0.946 0.026 276) | `#2C2E56` | oklch(0.321 0.070 279) |
| accentText | `#5145E8` | oklch(0.513 0.234 277) | `#A2A8FE` | oklch(0.761 0.123 280) |
| focus | `#5145E8` | oklch(0.513 0.234 277) | `#A2A8FE` | oklch(0.761 0.123 280) |
| success | `#007453` | oklch(0.495 0.104 165) | `#45D09E` | oklch(0.770 0.140 165) |
| successSoft | `#D5F6E6` | oklch(0.945 0.040 165) | `#103627` | oklch(0.301 0.051 164) |
| onSuccess | `#FFFFFF` | oklch(1.000 0.000 90) | `#011C11` | oklch(0.201 0.041 164) |
| warning | `#9A5201` | oklch(0.515 0.123 58) | `#F6B75C` | oklch(0.821 0.130 74) |
| warningSoft | `#FFEBC7` | oklch(0.947 0.052 82) | `#3E2B0F` | oklch(0.305 0.050 74) |
| onWarning | `#FFFFFF` | oklch(1.000 0.000 90) | `#241500` | oklch(0.211 0.044 76) |
| danger | `#BD2F28` | oklch(0.528 0.180 28) | `#FF9183` | oklch(0.771 0.135 28) |
| dangerSoft | `#FEE7E4` | oklch(0.946 0.026 27) | `#47211E` | oklch(0.300 0.059 26) |
| onDanger | `#FFFFFF` | oklch(1.000 0.000 90) | `#2B0E0B` | oklch(0.210 0.049 28) |
| info | `#1B65B9` | oklch(0.510 0.150 255) | `#7EBBFA` | oklch(0.775 0.111 250) |
| infoSoft | `#E2EEFE` | oklch(0.945 0.025 256) | `#192F46` | oklch(0.299 0.051 251) |
| selectedBg | `#FFFFFF` | oklch(1.000 0.000 90) | `#424853` | oklch(0.400 0.020 263) |
| selectedInk | `#1B2335` | oklch(0.257 0.036 266) | `#EDF0F6` | oklch(0.955 0.009 265) |
| selectedEdge | `#1B2335` | oklch(0.257 0.036 266) | `#EDF0F6` | oklch(0.955 0.009 265) |
| track | `#E4E8F0` | oklch(0.930 0.012 265) | `#282C34` | oklch(0.293 0.016 264) |
| controlOn | `#1B2335` | oklch(0.257 0.036 266) | `#EDF0F6` | oklch(0.955 0.009 265) |
| controlKnob | `#FFFFFF` | oklch(1.000 0.000 90) | `#0F1216` | oklch(0.181 0.010 256) |
| primary | `#1B2335` | oklch(0.257 0.036 266) | `#EDF0F6` | oklch(0.955 0.009 265) |
| onPrimary | `#FFFFFF` | oklch(1.000 0.000 90) | `#0F1216` | oklch(0.181 0.010 256) |
| pressed | `#1B2335` @0.10 | oklch(0.257 0.036 266) | `#EDF0F6` @0.12 | oklch(0.955 0.009 265) |
| hover | `#1B2335` @0.05 | oklch(0.257 0.036 266) | `#EDF0F6` @0.07 | oklch(0.955 0.009 265) |
| scrim | `#070B14` @0.45 | oklch(0.150 0.021 264) | `#000000` @0.62 | oklch(0.000 0.000 0) |
| nav | `#FFFFFF` @0.90 | oklch(1.000 0.000 90) | `#21252B` @0.92 | oklch(0.263 0.013 258) |
| navInk | `#465066` | oklch(0.431 0.039 266) | `#B3B9C5` | oklch(0.785 0.018 264) |
| navOn | `#1B2335` | oklch(0.257 0.036 266) | `#EDF0F6` | oklch(0.955 0.009 265) |
| navOnInk | `#FFFFFF` | oklch(1.000 0.000 90) | `#0F1216` | oklch(0.181 0.010 256) |
| bar | `#FFFFFF` @0.95 | oklch(1.000 0.000 90) | `#2D323A` | oklch(0.316 0.016 260) |
| onBar | `#1B2335` | oklch(0.257 0.036 266) | `#EDF0F6` | oklch(0.955 0.009 265) |
| barAction | `#5145E8` | oklch(0.513 0.234 277) | `#B0B7FF` | oklch(0.800 0.102 280) |
| live | `#1B2335` | oklch(0.257 0.036 266) | `#2D323A` | oklch(0.316 0.016 260) |
| onLive | `#FFFFFF` | oklch(1.000 0.000 90) | `#EDF0F6` | oklch(0.955 0.009 265) |
| glow | `#5145E8` @0.12 | oklch(0.513 0.234 277) | `#9398FF` @0.24 | oklch(0.720 0.149 280) |

| # | workplace | from (light) | to (light) | on | accentDark | accentDarkInk |
|---|---|---|---|---|---|---|
| 0 | indigo | `#393ECA` oklch(0.459 0.211 273) | `#150A6B` | `#FFFFFF` | `#7D9BFF` oklch(0.711 0.150 270) | `#0E142A` |
| 1 | green | `#117D15` oklch(0.514 0.164 143) | `#013E03` | `#FFFFFF` | `#44E994` oklch(0.830 0.180 156) | `#021C0D` |
| 2 | rose | `#C50E70` oklch(0.540 0.215 357) | `#690039` | `#FFFFFF` | `#FA74A5` oklch(0.730 0.170 359) | `#260C16` |
| 3 | mustard | `#F5B82E` oklch(0.818 0.157 82) | `#D18F1F` | `#1A1200` | `#FFC732` oklch(0.857 0.163 86) | `#1E1400` |
| 4 | purple | `#9F4AE4` oklch(0.590 0.225 306) | `#5B2187` | `#FFFFFF` | `#C874C8` oklch(0.680 0.150 327) | `#200E21` |
| 5 | blue | `#0777A9` oklch(0.540 0.116 237) | `#003D59` | `#FFFFFF` | `#8BC7FE` oklch(0.809 0.100 247) | `#031729` |
| 6 | teal | `#065E63` oklch(0.439 0.073 201) | `#012527` | `#FFFFFF` | `#18AFA4` oklch(0.681 0.115 187) | `#001B19` |
| 7 | burgundy | `#8B032A` oklch(0.406 0.160 16) | `#33000A` | `#FFFFFF` | `#F05963` oklch(0.666 0.186 20) | `#270C0D` |

### 5d. CSS custom properties
```css
:root {
  --bg: #F1F3F8;
  --surface: #FFFFFF;
  --surface-raised: #FFFFFF;
  --fill: #E4E8F0;
  --line: #D2D8E3;
  --line-strong: #778093;
  --ink: #1B2335;
  --ink-2: #5A6478;
  --ink-3: #636C7E;
  --disabled-ink: #9EA5B2;
  --disabled-bg: #E4E8F0;
  --accent: #5145E8;
  --accent-ink: #FFFFFF;
  --accent-soft: #E8ECFF;
  --accent-text: #5145E8;
  --focus: #5145E8;
  --success: #007453;
  --success-soft: #D5F6E6;
  --on-success: #FFFFFF;
  --warning: #9A5201;
  --warning-soft: #FFEBC7;
  --on-warning: #FFFFFF;
  --danger: #BD2F28;
  --danger-soft: #FEE7E4;
  --on-danger: #FFFFFF;
  --info: #1B65B9;
  --info-soft: #E2EEFE;
  --selected-bg: #FFFFFF;
  --selected-ink: #1B2335;
  --selected-edge: #1B2335;
  --track: #E4E8F0;
  --control-on: #1B2335;
  --control-knob: #FFFFFF;
  --primary: #1B2335;
  --on-primary: #FFFFFF;
  --pressed: rgb(27 35 53 / 0.10);
  --hover: rgb(27 35 53 / 0.05);
  --scrim: rgb(7 11 20 / 0.45);
  --nav: rgb(255 255 255 / 0.90);
  --nav-ink: #465066;
  --nav-on: #1B2335;
  --nav-on-ink: #FFFFFF;
  --bar: rgb(255 255 255 / 0.95);
  --on-bar: #1B2335;
  --bar-action: #5145E8;
  --live: #1B2335;
  --on-live: #FFFFFF;
  --glow: rgb(81 69 232 / 0.12);
}
:root[data-theme="dark"] {
  --bg: #0F1216;
  --surface: #181B20;
  --surface-raised: #21252B;
  --fill: #282C34;
  --line: #32373F;
  --line-strong: #717681;
  --ink: #EDF0F6;
  --ink-2: #B3B9C5;
  --ink-3: #959CA8;
  --disabled-ink: #5F636C;
  --disabled-bg: #21252B;
  --accent: #9398FF;
  --accent-ink: #15153A;
  --accent-soft: #2C2E56;
  --accent-text: #A2A8FE;
  --focus: #A2A8FE;
  --success: #45D09E;
  --success-soft: #103627;
  --on-success: #011C11;
  --warning: #F6B75C;
  --warning-soft: #3E2B0F;
  --on-warning: #241500;
  --danger: #FF9183;
  --danger-soft: #47211E;
  --on-danger: #2B0E0B;
  --info: #7EBBFA;
  --info-soft: #192F46;
  --selected-bg: #424853;
  --selected-ink: #EDF0F6;
  --selected-edge: #EDF0F6;
  --track: #282C34;
  --control-on: #EDF0F6;
  --control-knob: #0F1216;
  --primary: #EDF0F6;
  --on-primary: #0F1216;
  --pressed: rgb(237 240 246 / 0.12);
  --hover: rgb(237 240 246 / 0.07);
  --scrim: rgb(0 0 0 / 0.62);
  --nav: rgb(33 37 43 / 0.92);
  --nav-ink: #B3B9C5;
  --nav-on: #EDF0F6;
  --nav-on-ink: #0F1216;
  --bar: #2D323A;
  --on-bar: #EDF0F6;
  --bar-action: #B0B7FF;
  --live: #2D323A;
  --on-live: #EDF0F6;
  --glow: rgb(147 152 255 / 0.24);
  color-scheme: dark;
}
/* Workplaces: --wp-N-from / -to / -on (light) and --wp-N-acc / -acc-ink (dark) */
:root {
  --wp-0-from: #393ECA; --wp-0-to: #150A6B; --wp-0-on: #FFFFFF; --wp-0-acc: #7D9BFF; --wp-0-acc-ink: #0E142A; /* indigo */
  --wp-1-from: #117D15; --wp-1-to: #013E03; --wp-1-on: #FFFFFF; --wp-1-acc: #44E994; --wp-1-acc-ink: #021C0D; /* green */
  --wp-2-from: #C50E70; --wp-2-to: #690039; --wp-2-on: #FFFFFF; --wp-2-acc: #FA74A5; --wp-2-acc-ink: #260C16; /* rose */
  --wp-3-from: #F5B82E; --wp-3-to: #D18F1F; --wp-3-on: #1A1200; --wp-3-acc: #FFC732; --wp-3-acc-ink: #1E1400; /* mustard */
  --wp-4-from: #9F4AE4; --wp-4-to: #5B2187; --wp-4-on: #FFFFFF; --wp-4-acc: #C874C8; --wp-4-acc-ink: #200E21; /* purple */
  --wp-5-from: #0777A9; --wp-5-to: #003D59; --wp-5-on: #FFFFFF; --wp-5-acc: #8BC7FE; --wp-5-acc-ink: #031729; /* blue */
  --wp-6-from: #065E63; --wp-6-to: #012527; --wp-6-on: #FFFFFF; --wp-6-acc: #18AFA4; --wp-6-acc-ink: #001B19; /* teal */
  --wp-7-from: #8B032A; --wp-7-to: #33000A; --wp-7-on: #FFFFFF; --wp-7-acc: #F05963; --wp-7-acc-ink: #270C0D; /* burgundy */
}
```

### 5e. Kotlin (Compose)
```kotlin
// Proposed "Kis 2" tokens (generated by ux-audit/tools/agent-color/validate_proposed.py). Proposal only.
@Immutable
data class KisTokens(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val fill: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val disabledInk: Color,
    val disabledBg: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val accentText: Color,
    val focus: Color,
    val success: Color,
    val successSoft: Color,
    val onSuccess: Color,
    val warning: Color,
    val warningSoft: Color,
    val onWarning: Color,
    val danger: Color,
    val dangerSoft: Color,
    val onDanger: Color,
    val info: Color,
    val infoSoft: Color,
    val selectedBg: Color,
    val selectedInk: Color,
    val selectedEdge: Color,
    val track: Color,
    val controlOn: Color,
    val controlKnob: Color,
    val primary: Color,
    val onPrimary: Color,
    val pressed: Color,
    val hover: Color,
    val scrim: Color,
    val nav: Color,
    val navInk: Color,
    val navOn: Color,
    val navOnInk: Color,
    val bar: Color,
    val onBar: Color,
    val barAction: Color,
    val live: Color,
    val onLive: Color,
    val glow: Color,
)

val KisLight2 = KisTokens(
    dark = false,
    bg = Color(0xFFF1F3F8),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    fill = Color(0xFFE4E8F0),
    line = Color(0xFFD2D8E3),
    lineStrong = Color(0xFF778093),
    ink = Color(0xFF1B2335),
    ink2 = Color(0xFF5A6478),
    ink3 = Color(0xFF636C7E),
    disabledInk = Color(0xFF9EA5B2),
    disabledBg = Color(0xFFE4E8F0),
    accent = Color(0xFF5145E8),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE8ECFF),
    accentText = Color(0xFF5145E8),
    focus = Color(0xFF5145E8),
    success = Color(0xFF007453),
    successSoft = Color(0xFFD5F6E6),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFF9A5201),
    warningSoft = Color(0xFFFFEBC7),
    onWarning = Color(0xFFFFFFFF),
    danger = Color(0xFFBD2F28),
    dangerSoft = Color(0xFFFEE7E4),
    onDanger = Color(0xFFFFFFFF),
    info = Color(0xFF1B65B9),
    infoSoft = Color(0xFFE2EEFE),
    selectedBg = Color(0xFFFFFFFF),
    selectedInk = Color(0xFF1B2335),
    selectedEdge = Color(0xFF1B2335),
    track = Color(0xFFE4E8F0),
    controlOn = Color(0xFF1B2335),
    controlKnob = Color(0xFFFFFFFF),
    primary = Color(0xFF1B2335),
    onPrimary = Color(0xFFFFFFFF),
    pressed = Color(0x1A1B2335),
    hover = Color(0x0D1B2335),
    scrim = Color(0x73070B14),
    nav = Color(0xE6FFFFFF),
    navInk = Color(0xFF465066),
    navOn = Color(0xFF1B2335),
    navOnInk = Color(0xFFFFFFFF),
    bar = Color(0xF2FFFFFF),
    onBar = Color(0xFF1B2335),
    barAction = Color(0xFF5145E8),
    live = Color(0xFF1B2335),
    onLive = Color(0xFFFFFFFF),
    glow = Color(0x1F5145E8),
)

val KisDark2 = KisTokens(
    dark = true,
    bg = Color(0xFF0F1216),
    surface = Color(0xFF181B20),
    surfaceRaised = Color(0xFF21252B),
    fill = Color(0xFF282C34),
    line = Color(0xFF32373F),
    lineStrong = Color(0xFF717681),
    ink = Color(0xFFEDF0F6),
    ink2 = Color(0xFFB3B9C5),
    ink3 = Color(0xFF959CA8),
    disabledInk = Color(0xFF5F636C),
    disabledBg = Color(0xFF21252B),
    accent = Color(0xFF9398FF),
    accentInk = Color(0xFF15153A),
    accentSoft = Color(0xFF2C2E56),
    accentText = Color(0xFFA2A8FE),
    focus = Color(0xFFA2A8FE),
    success = Color(0xFF45D09E),
    successSoft = Color(0xFF103627),
    onSuccess = Color(0xFF011C11),
    warning = Color(0xFFF6B75C),
    warningSoft = Color(0xFF3E2B0F),
    onWarning = Color(0xFF241500),
    danger = Color(0xFFFF9183),
    dangerSoft = Color(0xFF47211E),
    onDanger = Color(0xFF2B0E0B),
    info = Color(0xFF7EBBFA),
    infoSoft = Color(0xFF192F46),
    selectedBg = Color(0xFF424853),
    selectedInk = Color(0xFFEDF0F6),
    selectedEdge = Color(0xFFEDF0F6),
    track = Color(0xFF282C34),
    controlOn = Color(0xFFEDF0F6),
    controlKnob = Color(0xFF0F1216),
    primary = Color(0xFFEDF0F6),
    onPrimary = Color(0xFF0F1216),
    pressed = Color(0x1FEDF0F6),
    hover = Color(0x12EDF0F6),
    scrim = Color(0x9E000000),
    nav = Color(0xEB21252B),
    navInk = Color(0xFFB3B9C5),
    navOn = Color(0xFFEDF0F6),
    navOnInk = Color(0xFF0F1216),
    bar = Color(0xFF2D323A),
    onBar = Color(0xFFEDF0F6),
    barAction = Color(0xFFB0B7FF),
    live = Color(0xFF2D323A),
    onLive = Color(0xFFEDF0F6),
    glow = Color(0x3D9398FF),
)

val Workplaces2 = listOf(
    Workplace("indigo", Color(0xFF393ECA), Color(0xFF150A6B), Color(0xFFFFFFFF), Color(0xFF7D9BFF), Color(0xFF0E142A)),
    Workplace("green", Color(0xFF117D15), Color(0xFF013E03), Color(0xFFFFFFFF), Color(0xFF44E994), Color(0xFF021C0D)),
    Workplace("rose", Color(0xFFC50E70), Color(0xFF690039), Color(0xFFFFFFFF), Color(0xFFFA74A5), Color(0xFF260C16)),
    Workplace("mustard", Color(0xFFF5B82E), Color(0xFFD18F1F), Color(0xFF1A1200), Color(0xFFFFC732), Color(0xFF1E1400)),
    Workplace("purple", Color(0xFF9F4AE4), Color(0xFF5B2187), Color(0xFFFFFFFF), Color(0xFFC874C8), Color(0xFF200E21)),
    Workplace("blue", Color(0xFF0777A9), Color(0xFF003D59), Color(0xFFFFFFFF), Color(0xFF8BC7FE), Color(0xFF031729)),
    Workplace("teal", Color(0xFF065E63), Color(0xFF012527), Color(0xFFFFFFFF), Color(0xFF18AFA4), Color(0xFF001B19)),
    Workplace("burgundy", Color(0xFF8B032A), Color(0xFF33000A), Color(0xFFFFFFFF), Color(0xFFF05963), Color(0xFF270C0D)),
)
```

## 6. Swatch sheet
`shots/agents/palette-current-vs-proposed.png` (1550×2050): four panels (current/proposed × light/dark), each with a mini UI mock (card, paid/delete/info text, pending pill, segmented control, Save/Share/FAB/switch), the neutral ramp with OKLCH chroma/hue under every swatch (dark current shows "C0 (grey)" on every chip), semantic + brand, workplaces (light gradient + dark accent), CVD strips and nav/toast.

## 7. Summary of new findings (beyond findings-web-pass1)
1. Paid green fails on page bg (4.48) and on fill (4.05); teal place fails white text (4.20); muted under the top glow fails (3.87 light / 4.47 dark).
2. Web dark `.btn.danger` white on `#FF8A7A` = 2.29; web dark FAB 2.97 (Android already solves the FAB with accentDarkInk).
3. Every control edge relying on `line` is 1.26–1.43:1 (outline buttons, inputs, switch-off) — no `lineStrong` exists.
4. Dark neutral chroma = 0.000 across the board vs light 0.007–0.036 at h 263–269; dark nevertheless has two tinted tokens (`bar`, `onBar/editInk`) and a tinted logo tile → mixed identity inside the dark theme itself.
5. `sunk` semantics invert between themes (recessed in light, raised in dark); dark nav is *below* card elevation.
6. Brand = workplace #1 exactly (ΔE00 0.0) in both themes.
7. Workplace worst-case CVD ΔE00 1.4 (light) / 0.7 (dark); dark `to` ends collapse (green/rose 0.8 deutan, green/teal 1.6 tritan).
8. Pending is the only semantic that changes hue family across themes (+19°) and the brightest dark semantic (L .852).
9. Dark ink pure white = 18.7:1 (halation); proposed 16.45.

## Appendix A — current contrast matrix (148 pairs)
| theme | kind | fg | bg | ratio | result |
|---|---|---|---|---|---|
| light | text | ink | bg | 14.13 | OK |
| light | text | ink | surface | 15.69 | OK |
| light | text | ink | sunk | 12.78 | OK |
| light | text | ink | nav(over bg) | 15.56 | OK |
| light | text | ink | glow-top(bg) | 10.21 | OK |
| light | text | muted | bg | 5.36 | OK |
| light | text | muted | surface | 5.95 | OK |
| light | text | muted | sunk | 4.85 | OK |
| light | text | muted | nav(over bg) | 5.9 | OK |
| light | text | muted | glow-top(bg) | 3.87 | FAIL |
| light | text | paid | bg | 4.48 | FAIL |
| light | text | paid | surface | 4.98 | OK |
| light | text | paid | sunk | 4.05 | FAIL |
| light | text | danger | bg | 4.91 | OK |
| light | text | danger | surface | 5.45 | OK |
| light | text | danger | sunk | 4.43 | FAIL |
| light | text | pending | bg | 5.37 | OK |
| light | text | pending | surface | 5.96 | OK |
| light | text | pending | sunk | 4.86 | OK |
| light | text | pending | pendingSoft | 5.1 | OK |
| light | text | navInk | nav(over bg) | 8.01 | OK |
| light | text | navOnInk | navOn | 15.69 | OK |
| light | text | onBar | bar(over bg) | 15.57 | OK |
| light | text | glow (toast action) | bar(over bg) | 6.19 | OK |
| light | text | onInk (primary btn) | ink (primary btn) | 14.13 | OK |
| light | text | editInk | edit | 15.69 | OK |
| light | text | #FFFFFF (danger btn) | danger | 5.45 | OK |
| light | text | #FFFFFF (FAB +, 28px) | glow (FAB) | 6.24 | OK |
| light | text | ink disabled (.45) | surface | 2.75 | FAIL |
| light | ui | focus ring (glow) | bg | 5.62 | OK |
| light | ui | line (outline btn/input edge) | bg | 1.29 | FAIL |
| light | ui | focus ring (glow) | surface | 6.24 | OK |
| light | ui | line (outline btn/input edge) | surface | 1.43 | FAIL |
| light | ui | switch ON track (paid) | surface | 4.98 | OK |
| light | ui | switch OFF track (line) | surface | 1.43 | FAIL |
| light | ui | switch knob #FFF | switch OFF track (line) | 1.43 | FAIL |
| light | ui | surface card edge | bg | 1.11 | OK |
| light | text | indigo.on | indigo.from | 6.24 | OK |
| light | text | indigo.on | indigo.to | 14.65 | OK |
| light | text | indigo.from as text | surface | 6.24 | OK |
| light | ui | indigo.from chip | surface | 6.24 | OK |
| light | text | green.on | green.from | 4.94 | OK |
| light | text | green.on | green.to | 12.26 | OK |
| light | text | green.from as text | surface | 4.94 | OK |
| light | ui | green.from chip | surface | 4.94 | OK |
| light | text | rose.on | rose.from | 4.62 | OK |
| light | text | rose.on | rose.to | 13.26 | OK |
| light | text | rose.from as text | surface | 4.62 | OK |
| light | ui | rose.from chip | surface | 4.62 | OK |
| light | text | mustard.on | mustard.from | 10.42 | OK |
| light | text | mustard.on | mustard.to | 6.3 | OK |
| light | text | mustard.from as text | surface | 1.78 | FAIL |
| light | ui | mustard.from chip | surface | 1.78 | FAIL |
| light | text | purple.on | purple.from | 5.56 | OK |
| light | text | purple.on | purple.to | 14.91 | OK |
| light | text | purple.from as text | surface | 5.56 | OK |
| light | ui | purple.from chip | surface | 5.56 | OK |
| light | text | blue.on | blue.from | 4.62 | OK |
| light | text | blue.on | blue.to | 13.01 | OK |
| light | text | blue.from as text | surface | 4.62 | OK |
| light | ui | blue.from chip | surface | 4.62 | OK |
| light | text | teal.on | teal.from | 4.2 | FAIL |
| light | text | teal.on | teal.to | 12.39 | OK |
| light | text | teal.from as text | surface | 4.2 | FAIL |
| light | ui | teal.from chip | surface | 4.2 | OK |
| light | text | burgundy.on | burgundy.from | 6.19 | OK |
| light | text | burgundy.on | burgundy.to | 15.2 | OK |
| light | text | burgundy.from as text | surface | 6.19 | OK |
| light | ui | burgundy.from chip | surface | 6.19 | OK |
| dark | text | ink | bg | 18.73 | OK |
| dark | text | ink | surface | 16.67 | OK |
| dark | text | ink | sunk | 14.35 | OK |
| dark | text | ink | nav(over bg) | 17.76 | OK |
| dark | text | ink | glow-top(bg) | 9.37 | OK |
| dark | text | ink | tile(ink10% over bg) | 14.35 | OK |
| dark | text | muted | bg | 8.93 | OK |
| dark | text | muted | surface | 7.95 | OK |
| dark | text | muted | sunk | 6.85 | OK |
| dark | text | muted | nav(over bg) | 8.47 | OK |
| dark | text | muted | glow-top(bg) | 4.47 | FAIL |
| dark | text | muted | tile(ink10% over bg) | 6.85 | OK |
| dark | text | paid | bg | 8.73 | OK |
| dark | text | paid | surface | 7.77 | OK |
| dark | text | paid | sunk | 6.69 | OK |
| dark | text | danger | bg | 8.18 | OK |
| dark | text | danger | surface | 7.28 | OK |
| dark | text | danger | sunk | 6.27 | OK |
| dark | text | pending | bg | 11.74 | OK |
| dark | text | pending | surface | 10.45 | OK |
| dark | text | pending | sunk | 9.0 | OK |
| dark | text | pending | pendingSoft | 8.41 | OK |
| dark | text | navInk | nav(over bg) | 8.47 | OK |
| dark | text | navOnInk | navOn | 18.73 | OK |
| dark | text | onBar | bar(over bg) | 12.23 | OK |
| dark | text | glow (toast action) | bar(over bg) | 4.58 | OK |
| dark | text | onInk (primary btn) | ink (primary btn) | 18.73 | OK |
| dark | text | editInk | edit | 12.92 | OK |
| dark | text | #FFFFFF (danger btn) | danger | 2.29 | FAIL |
| dark | text | #FFFFFF (FAB +, 28px) | glow (FAB) | 2.97 | FAIL |
| dark | text | ink disabled (.45) | surface | 4.4 | OK |
| dark | ui | focus ring (glow) | bg | 6.31 | OK |
| dark | ui | line (outline btn/input edge) | bg | 1.42 | FAIL |
| dark | ui | focus ring (glow) | surface | 5.62 | OK |
| dark | ui | line (outline btn/input edge) | surface | 1.26 | FAIL |
| dark | ui | switch ON track (paid) | surface | 7.77 | OK |
| dark | ui | switch OFF track (line) | surface | 1.26 | FAIL |
| dark | ui | switch knob #FFF | switch OFF track (line) | 13.2 | OK |
| dark | ui | surface card edge | bg | 1.12 | OK |
| dark | text | indigo.accentDarkInk | indigo.accentDark | 6.13 | OK |
| dark | text | indigo.accentDark as text | surface | 5.62 | OK |
| dark | text | indigo.accentDark as text | bg | 6.31 | OK |
| dark | text | indigo.on (avatar gradient in dark) | indigo.from | 6.24 | OK |
| dark | ui | indigo.from avatar vs surface | surface | 2.67 | FAIL |
| dark | text | green.accentDarkInk | green.accentDark | 10.7 | OK |
| dark | text | green.accentDark as text | surface | 9.43 | OK |
| dark | text | green.accentDark as text | bg | 10.6 | OK |
| dark | text | green.on (avatar gradient in dark) | green.from | 4.94 | OK |
| dark | ui | green.from avatar vs surface | surface | 3.37 | OK |
| dark | text | rose.accentDarkInk | rose.accentDark | 6.67 | OK |
| dark | text | rose.accentDark as text | surface | 6.21 | OK |
| dark | text | rose.accentDark as text | bg | 6.98 | OK |
| dark | text | rose.on (avatar gradient in dark) | rose.from | 4.62 | OK |
| dark | ui | rose.from avatar vs surface | surface | 3.61 | OK |
| dark | text | mustard.accentDarkInk | mustard.accentDark | 10.84 | OK |
| dark | text | mustard.accentDark as text | surface | 10.89 | OK |
| dark | text | mustard.accentDark as text | bg | 12.23 | OK |
| dark | text | mustard.on (avatar gradient in dark) | mustard.from | 10.42 | OK |
| dark | ui | mustard.from avatar vs surface | surface | 9.35 | OK |
| dark | text | purple.accentDarkInk | purple.accentDark | 6.95 | OK |
| dark | text | purple.accentDark as text | surface | 6.46 | OK |
| dark | text | purple.accentDark as text | bg | 7.26 | OK |
| dark | text | purple.on (avatar gradient in dark) | purple.from | 5.56 | OK |
| dark | ui | purple.from avatar vs surface | surface | 3.0 | FAIL |
| dark | text | blue.accentDarkInk | blue.accentDark | 6.65 | OK |
| dark | text | blue.accentDark as text | surface | 6.79 | OK |
| dark | text | blue.accentDark as text | bg | 7.63 | OK |
| dark | text | blue.on (avatar gradient in dark) | blue.from | 4.62 | OK |
| dark | ui | blue.from avatar vs surface | surface | 3.61 | OK |
| dark | text | teal.accentDarkInk | teal.accentDark | 9.23 | OK |
| dark | text | teal.accentDark as text | surface | 9.03 | OK |
| dark | text | teal.accentDark as text | bg | 10.14 | OK |
| dark | text | teal.on (avatar gradient in dark) | teal.from | 4.2 | FAIL |
| dark | ui | teal.from avatar vs surface | surface | 3.97 | OK |
| dark | text | burgundy.accentDarkInk | burgundy.accentDark | 6.83 | OK |
| dark | text | burgundy.accentDark as text | surface | 6.28 | OK |
| dark | text | burgundy.accentDark as text | bg | 7.06 | OK |
| dark | text | burgundy.on (avatar gradient in dark) | burgundy.from | 6.19 | OK |
| dark | ui | burgundy.from avatar vs surface | surface | 2.69 | FAIL |

## Appendix B — proposed contrast validation (181 pairs, 0 failures)
| theme | kind | fg | bg | ratio | need | ok |
|---|---|---|---|---|---|---|
| light | text | ink `#1B2335` | bg `#F1F3F8` | 14.13 | 4.5 | OK |
| light | text | ink `#1B2335` | surface `#FFFFFF` | 15.69 | 4.5 | OK |
| light | text | ink `#1B2335` | surfaceRaised `#FFFFFF` | 15.69 | 4.5 | OK |
| light | text | ink `#1B2335` | fill `#E4E8F0` | 12.78 | 4.5 | OK |
| light | text | ink `#1B2335` | glowPeak `#DEDEF6` | 11.87 | 4.5 | OK |
| light | text | ink2 `#5A6478` | bg `#F1F3F8` | 5.36 | 4.5 | OK |
| light | text | ink2 `#5A6478` | surface `#FFFFFF` | 5.95 | 4.5 | OK |
| light | text | ink2 `#5A6478` | surfaceRaised `#FFFFFF` | 5.95 | 4.5 | OK |
| light | text | ink2 `#5A6478` | fill `#E4E8F0` | 4.85 | 4.5 | OK |
| light | text | ink2 `#5A6478` | glowPeak `#DEDEF6` | 4.5 | 4.5 | OK |
| light | text | ink3 `#636C7E` | bg `#F1F3F8` | 4.76 | 4.5 | OK |
| light | text | ink3 `#636C7E` | surface `#FFFFFF` | 5.28 | 4.5 | OK |
| light | text | ink3 `#636C7E` | surfaceRaised `#FFFFFF` | 5.28 | 4.5 | OK |
| light | text | success `#007453` | bg `#F1F3F8` | 5.22 | 4.5 | OK |
| light | text | success `#007453` | surface `#FFFFFF` | 5.8 | 4.5 | OK |
| light | text | success `#007453` | surfaceRaised `#FFFFFF` | 5.8 | 4.5 | OK |
| light | text | success `#007453` | fill `#E4E8F0` | 4.72 | 4.5 | OK |
| light | text | warning `#9A5201` | bg `#F1F3F8` | 5.28 | 4.5 | OK |
| light | text | warning `#9A5201` | surface `#FFFFFF` | 5.86 | 4.5 | OK |
| light | text | warning `#9A5201` | surfaceRaised `#FFFFFF` | 5.86 | 4.5 | OK |
| light | text | warning `#9A5201` | fill `#E4E8F0` | 4.77 | 4.5 | OK |
| light | text | danger `#BD2F28` | bg `#F1F3F8` | 5.25 | 4.5 | OK |
| light | text | danger `#BD2F28` | surface `#FFFFFF` | 5.83 | 4.5 | OK |
| light | text | danger `#BD2F28` | surfaceRaised `#FFFFFF` | 5.83 | 4.5 | OK |
| light | text | danger `#BD2F28` | fill `#E4E8F0` | 4.75 | 4.5 | OK |
| light | text | info `#1B65B9` | bg `#F1F3F8` | 5.24 | 4.5 | OK |
| light | text | info `#1B65B9` | surface `#FFFFFF` | 5.82 | 4.5 | OK |
| light | text | info `#1B65B9` | surfaceRaised `#FFFFFF` | 5.82 | 4.5 | OK |
| light | text | info `#1B65B9` | fill `#E4E8F0` | 4.74 | 4.5 | OK |
| light | text | accentText `#5145E8` | bg `#F1F3F8` | 5.62 | 4.5 | OK |
| light | text | accentText `#5145E8` | surface `#FFFFFF` | 6.24 | 4.5 | OK |
| light | text | accentText `#5145E8` | surfaceRaised `#FFFFFF` | 6.24 | 4.5 | OK |
| light | text | accentText `#5145E8` | fill `#E4E8F0` | 5.08 | 4.5 | OK |
| light | text | success `#007453` | successSoft `#D5F6E6` | 5.01 | 4.5 | OK |
| light | text | onSuccess `#FFFFFF` | success (solid) `#007453` | 5.8 | 4.5 | OK |
| light | text | warning `#9A5201` | warningSoft `#FFEBC7` | 5.01 | 4.5 | OK |
| light | text | onWarning `#FFFFFF` | warning (solid) `#9A5201` | 5.86 | 4.5 | OK |
| light | text | danger `#BD2F28` | dangerSoft `#FEE7E4` | 4.93 | 4.5 | OK |
| light | text | onDanger `#FFFFFF` | danger (solid) `#BD2F28` | 5.83 | 4.5 | OK |
| light | text | info `#1B65B9` | infoSoft `#E2EEFE` | 4.96 | 4.5 | OK |
| light | text | accentText `#5145E8` | accentSoft `#E8ECFF` | 5.31 | 4.5 | OK |
| light | text | accentInk `#FFFFFF` | accent (FAB) `#5145E8` | 6.24 | 4.5 | OK |
| light | text | onPrimary `#FFFFFF` | primary `#1B2335` | 15.69 | 4.5 | OK |
| light | text | selectedInk `#1B2335` | selectedBg `#FFFFFF` | 15.69 | 4.5 | OK |
| light | text | ink `#1B2335` | track `#E4E8F0` | 12.78 | 4.5 | OK |
| light | text | navInk `#465066` | nav `#FEFEFE` | 8.01 | 4.5 | OK |
| light | text | navOnInk `#FFFFFF` | navOn `#1B2335` | 15.69 | 4.5 | OK |
| light | text | onBar `#1B2335` | bar `#FEFEFF` | 15.57 | 4.5 | OK |
| light | text | barAction `#5145E8` | bar `#FEFEFF` | 6.19 | 4.5 | OK |
| light | text | onLive `#FFFFFF` | live `#1B2335` | 15.69 | 4.5 | OK |
| light | text | warning `#9A5201` | warningSoft `#FFEBC7` | 5.01 | 4.5 | OK |
| light | ui | focus `#5145E8` | bg `#F1F3F8` | 5.62 | 3.0 | OK |
| light | ui | lineStrong `#778093` | bg `#F1F3F8` | 3.57 | 3.0 | OK |
| light | ui | focus `#5145E8` | surface `#FFFFFF` | 6.24 | 3.0 | OK |
| light | ui | lineStrong `#778093` | surface `#FFFFFF` | 3.97 | 3.0 | OK |
| light | ui | focus `#5145E8` | fill `#E4E8F0` | 5.08 | 3.0 | OK |
| light | ui | lineStrong `#778093` | fill `#E4E8F0` | 3.23 | 3.0 | OK |
| light | ui | selectedEdge `#1B2335` | track `#E4E8F0` | 12.78 | 3.0 | OK |
| light | ui | controlOn `#1B2335` | surface `#FFFFFF` | 15.69 | 3.0 | OK |
| light | ui | controlKnob `#FFFFFF` | controlOn `#1B2335` | 15.69 | 3.0 | OK |
| light | ui | lineStrong (switch off) `#778093` | surface `#FFFFFF` | 3.97 | 3.0 | OK |
| light | ui | selectedBg vs track `#FFFFFF` | track `#E4E8F0` | 1.23 | 1.0 | OK |
| light | info | disabledInk `#9EA5B2` | surface `#FFFFFF` | 2.48 | 2.0 | OK |
| light | text | indigo.on `#FFFFFF` | indigo.from `#393ECA` | 7.73 | 4.5 | OK |
| light | text | indigo.on `#FFFFFF` | indigo.to `#150A6B` | 16.31 | 4.5 | OK |
| light | text | indigo.from as text `#393ECA` | surface `#FFFFFF` | 7.73 | 4.5 | OK |
| light | text | green.on `#FFFFFF` | green.from `#117D15` | 5.29 | 4.5 | OK |
| light | text | green.on `#FFFFFF` | green.to `#013E03` | 12.41 | 4.5 | OK |
| light | text | green.from as text `#117D15` | surface `#FFFFFF` | 5.29 | 4.5 | OK |
| light | text | rose.on `#FFFFFF` | rose.from `#C50E70` | 5.72 | 4.5 | OK |
| light | text | rose.on `#FFFFFF` | rose.to `#690039` | 12.65 | 4.5 | OK |
| light | text | rose.from as text `#C50E70` | surface `#FFFFFF` | 5.72 | 4.5 | OK |
| light | text | mustard.on `#1A1200` | mustard.from `#F5B82E` | 10.42 | 4.5 | OK |
| light | text | mustard.on `#1A1200` | mustard.to `#D18F1F` | 6.78 | 4.5 | OK |
| light | text | purple.on `#FFFFFF` | purple.from `#9F4AE4` | 4.59 | 4.5 | OK |
| light | text | purple.on `#FFFFFF` | purple.to `#5B2187` | 10.44 | 4.5 | OK |
| light | text | purple.from as text `#9F4AE4` | surface `#FFFFFF` | 4.59 | 4.5 | OK |
| light | text | blue.on `#FFFFFF` | blue.from `#0777A9` | 4.98 | 4.5 | OK |
| light | text | blue.on `#FFFFFF` | blue.to `#003D59` | 11.59 | 4.5 | OK |
| light | text | blue.from as text `#0777A9` | surface `#FFFFFF` | 4.98 | 4.5 | OK |
| light | text | teal.on `#FFFFFF` | teal.from `#065E63` | 7.53 | 4.5 | OK |
| light | text | teal.on `#FFFFFF` | teal.to `#012527` | 16.21 | 4.5 | OK |
| light | text | teal.from as text `#065E63` | surface `#FFFFFF` | 7.53 | 4.5 | OK |
| light | text | burgundy.on `#FFFFFF` | burgundy.from `#8B032A` | 9.79 | 4.5 | OK |
| light | text | burgundy.on `#FFFFFF` | burgundy.to `#33000A` | 18.34 | 4.5 | OK |
| light | text | burgundy.from as text `#8B032A` | surface `#FFFFFF` | 9.79 | 4.5 | OK |
| dark | text | ink `#EDF0F6` | bg `#0F1216` | 16.45 | 4.5 | OK |
| dark | text | ink `#EDF0F6` | surface `#181B20` | 15.12 | 4.5 | OK |
| dark | text | ink `#EDF0F6` | surfaceRaised `#21252B` | 13.48 | 4.5 | OK |
| dark | text | ink `#EDF0F6` | fill `#282C34` | 12.26 | 4.5 | OK |
| dark | text | ink `#EDF0F6` | glowPeak `#2F324E` | 10.9 | 4.5 | OK |
| dark | text | ink2 `#B3B9C5` | bg `#0F1216` | 9.53 | 4.5 | OK |
| dark | text | ink2 `#B3B9C5` | surface `#181B20` | 8.77 | 4.5 | OK |
| dark | text | ink2 `#B3B9C5` | surfaceRaised `#21252B` | 7.82 | 4.5 | OK |
| dark | text | ink2 `#B3B9C5` | fill `#282C34` | 7.11 | 4.5 | OK |
| dark | text | ink2 `#B3B9C5` | glowPeak `#2F324E` | 6.32 | 4.5 | OK |
| dark | text | ink3 `#959CA8` | bg `#0F1216` | 6.79 | 4.5 | OK |
| dark | text | ink3 `#959CA8` | surface `#181B20` | 6.25 | 4.5 | OK |
| dark | text | ink3 `#959CA8` | surfaceRaised `#21252B` | 5.57 | 4.5 | OK |
| dark | text | success `#45D09E` | bg `#0F1216` | 9.63 | 4.5 | OK |
| dark | text | success `#45D09E` | surface `#181B20` | 8.85 | 4.5 | OK |
| dark | text | success `#45D09E` | surfaceRaised `#21252B` | 7.89 | 4.5 | OK |
| dark | text | success `#45D09E` | fill `#282C34` | 7.18 | 4.5 | OK |
| dark | text | warning `#F6B75C` | bg `#0F1216` | 10.59 | 4.5 | OK |
| dark | text | warning `#F6B75C` | surface `#181B20` | 9.74 | 4.5 | OK |
| dark | text | warning `#F6B75C` | surfaceRaised `#21252B` | 8.68 | 4.5 | OK |
| dark | text | warning `#F6B75C` | fill `#282C34` | 7.9 | 4.5 | OK |
| dark | text | danger `#FF9183` | bg `#0F1216` | 8.61 | 4.5 | OK |
| dark | text | danger `#FF9183` | surface `#181B20` | 7.92 | 4.5 | OK |
| dark | text | danger `#FF9183` | surfaceRaised `#21252B` | 7.06 | 4.5 | OK |
| dark | text | danger `#FF9183` | fill `#282C34` | 6.42 | 4.5 | OK |
| dark | text | info `#7EBBFA` | bg `#0F1216` | 9.28 | 4.5 | OK |
| dark | text | info `#7EBBFA` | surface `#181B20` | 8.53 | 4.5 | OK |
| dark | text | info `#7EBBFA` | surfaceRaised `#21252B` | 7.61 | 4.5 | OK |
| dark | text | info `#7EBBFA` | fill `#282C34` | 6.92 | 4.5 | OK |
| dark | text | accentText `#A2A8FE` | bg `#0F1216` | 8.56 | 4.5 | OK |
| dark | text | accentText `#A2A8FE` | surface `#181B20` | 7.87 | 4.5 | OK |
| dark | text | accentText `#A2A8FE` | surfaceRaised `#21252B` | 7.01 | 4.5 | OK |
| dark | text | accentText `#A2A8FE` | fill `#282C34` | 6.38 | 4.5 | OK |
| dark | text | success `#45D09E` | successSoft `#103627` | 6.82 | 4.5 | OK |
| dark | text | onSuccess `#011C11` | success (solid) `#45D09E` | 9.16 | 4.5 | OK |
| dark | text | warning `#F6B75C` | warningSoft `#3E2B0F` | 7.61 | 4.5 | OK |
| dark | text | onWarning `#241500` | warning (solid) `#F6B75C` | 10.02 | 4.5 | OK |
| dark | text | danger `#FF9183` | dangerSoft `#47211E` | 6.4 | 4.5 | OK |
| dark | text | onDanger `#2B0E0B` | danger (solid) `#FF9183` | 8.23 | 4.5 | OK |
| dark | text | info `#7EBBFA` | infoSoft `#192F46` | 6.75 | 4.5 | OK |
| dark | text | accentText `#A2A8FE` | accentSoft `#2C2E56` | 5.86 | 4.5 | OK |
| dark | text | accentInk `#15153A` | accent (FAB) `#9398FF` | 6.81 | 4.5 | OK |
| dark | text | onPrimary `#0F1216` | primary `#EDF0F6` | 16.45 | 4.5 | OK |
| dark | text | selectedInk `#EDF0F6` | selectedBg `#424853` | 8.06 | 4.5 | OK |
| dark | text | ink `#EDF0F6` | track `#282C34` | 12.26 | 4.5 | OK |
| dark | text | navInk `#B3B9C5` | nav `#202329` | 7.99 | 4.5 | OK |
| dark | text | navOnInk `#0F1216` | navOn `#EDF0F6` | 16.45 | 4.5 | OK |
| dark | text | onBar `#EDF0F6` | bar `#2D323A` | 11.29 | 4.5 | OK |
| dark | text | barAction `#B0B7FF` | bar `#2D323A` | 6.79 | 4.5 | OK |
| dark | text | onLive `#EDF0F6` | live `#2D323A` | 11.29 | 4.5 | OK |
| dark | text | warning `#F6B75C` | warningSoft `#3E2B0F` | 7.61 | 4.5 | OK |
| dark | ui | focus `#A2A8FE` | bg `#0F1216` | 8.56 | 3.0 | OK |
| dark | ui | lineStrong `#717681` | bg `#0F1216` | 4.12 | 3.0 | OK |
| dark | ui | focus `#A2A8FE` | surface `#181B20` | 7.87 | 3.0 | OK |
| dark | ui | lineStrong `#717681` | surface `#181B20` | 3.79 | 3.0 | OK |
| dark | ui | focus `#A2A8FE` | fill `#282C34` | 6.38 | 3.0 | OK |
| dark | ui | lineStrong `#717681` | fill `#282C34` | 3.07 | 3.0 | OK |
| dark | ui | selectedEdge `#EDF0F6` | track `#282C34` | 12.26 | 3.0 | OK |
| dark | ui | controlOn `#EDF0F6` | surface `#181B20` | 15.12 | 3.0 | OK |
| dark | ui | controlKnob `#0F1216` | controlOn `#EDF0F6` | 16.45 | 3.0 | OK |
| dark | ui | lineStrong (switch off) `#717681` | surface `#181B20` | 3.79 | 3.0 | OK |
| dark | ui | selectedBg vs track `#424853` | track `#282C34` | 1.52 | 1.0 | OK |
| dark | info | disabledInk `#5F636C` | surface `#181B20` | 2.87 | 2.0 | OK |
| dark | text | indigo.accentDarkInk `#0E142A` | indigo.accentDark `#7D9BFF` | 6.95 | 4.5 | OK |
| dark | text | indigo.accentDark as text `#7D9BFF` | bg `#0F1216` | 7.16 | 4.5 | OK |
| dark | text | indigo.accentDark as text `#7D9BFF` | surface `#181B20` | 6.58 | 4.5 | OK |
| dark | text | indigo.accentDark as text `#7D9BFF` | surfaceRaised `#21252B` | 5.87 | 4.5 | OK |
| dark | text | green.accentDarkInk `#021C0D` | green.accentDark `#44E994` | 11.35 | 4.5 | OK |
| dark | text | green.accentDark as text `#44E994` | bg `#0F1216` | 11.92 | 4.5 | OK |
| dark | text | green.accentDark as text `#44E994` | surface `#181B20` | 10.96 | 4.5 | OK |
| dark | text | green.accentDark as text `#44E994` | surfaceRaised `#21252B` | 9.77 | 4.5 | OK |
| dark | text | rose.accentDarkInk `#260C16` | rose.accentDark `#FA74A5` | 7.07 | 4.5 | OK |
| dark | text | rose.accentDark as text `#FA74A5` | bg `#0F1216` | 7.25 | 4.5 | OK |
| dark | text | rose.accentDark as text `#FA74A5` | surface `#181B20` | 6.66 | 4.5 | OK |
| dark | text | rose.accentDark as text `#FA74A5` | surfaceRaised `#21252B` | 5.94 | 4.5 | OK |
| dark | text | mustard.accentDarkInk `#1E1400` | mustard.accentDark `#FFC732` | 11.66 | 4.5 | OK |
| dark | text | mustard.accentDark as text `#FFC732` | bg `#0F1216` | 12.04 | 4.5 | OK |
| dark | text | mustard.accentDark as text `#FFC732` | surface `#181B20` | 11.07 | 4.5 | OK |
| dark | text | mustard.accentDark as text `#FFC732` | surfaceRaised `#21252B` | 9.87 | 4.5 | OK |
| dark | text | purple.accentDarkInk `#200E21` | purple.accentDark `#C874C8` | 5.92 | 4.5 | OK |
| dark | text | purple.accentDark as text `#C874C8` | bg `#0F1216` | 6.07 | 4.5 | OK |
| dark | text | purple.accentDark as text `#C874C8` | surface `#181B20` | 5.58 | 4.5 | OK |
| dark | text | purple.accentDark as text `#C874C8` | surfaceRaised `#21252B` | 4.98 | 4.5 | OK |
| dark | text | blue.accentDarkInk `#031729` | blue.accentDark `#8BC7FE` | 10.1 | 4.5 | OK |
| dark | text | blue.accentDark as text `#8BC7FE` | bg `#0F1216` | 10.46 | 4.5 | OK |
| dark | text | blue.accentDark as text `#8BC7FE` | surface `#181B20` | 9.62 | 4.5 | OK |
| dark | text | blue.accentDark as text `#8BC7FE` | surfaceRaised `#21252B` | 8.58 | 4.5 | OK |
| dark | text | teal.accentDarkInk `#001B19` | teal.accentDark `#18AFA4` | 6.58 | 4.5 | OK |
| dark | text | teal.accentDark as text `#18AFA4` | bg `#0F1216` | 6.89 | 4.5 | OK |
| dark | text | teal.accentDark as text `#18AFA4` | surface `#181B20` | 6.34 | 4.5 | OK |
| dark | text | teal.accentDark as text `#18AFA4` | surfaceRaised `#21252B` | 5.65 | 4.5 | OK |
| dark | text | burgundy.accentDarkInk `#270C0D` | burgundy.accentDark `#F05963` | 5.52 | 4.5 | OK |
| dark | text | burgundy.accentDark as text `#F05963` | bg `#0F1216` | 5.65 | 4.5 | OK |
| dark | text | burgundy.accentDark as text `#F05963` | surface `#181B20` | 5.19 | 4.5 | OK |
| dark | text | burgundy.accentDark as text `#F05963` | surfaceRaised `#21252B` | 4.63 | 4.5 | OK |
