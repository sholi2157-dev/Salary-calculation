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
