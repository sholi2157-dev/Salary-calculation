# Typography audit and recommendation for "כיס" (web + Android)

Scope: read-only audit. Specimen tooling is in `ux-audit/tools/agent-type/` (`specimen.js`, `reco.js`, `feat.py`, `metrics.json`). Screenshots are in `ux-audit/shots/agents/type-*.png`.
Evidence: font binaries inspected with fontTools (GSUB features, hmtx digit advances, cmap, fvar). Digits measured in Chromium with canvas `measureText` and DOM width (`1111` vs `8888`, with and without `tabular-nums`). Rendered census from `notes/explore-light_dark.json` (70 captured states).

---

## 1. Current state: what is actually rendered

### 1.1 Web (`src/web/web/kis/kis.css`)
- Families: `--text: Rubik, Heebo, system-ui`. `--display: 'Secular One', Rubik, Heebo`. Shipped as **6 raw TTFs, about 900 KB** (4 static Rubik files at about 203 KB each, Secular One 81 KB, Heebo VF 119 KB). There is no woff2, no subsetting and no preload. Heebo is downloaded but in practice never used, because Rubik always resolves first.
- **25 distinct rendered font sizes** across the census: 8.68, 9.3, 10, 11, **11.78**, 12, 13, **13.64**, 14, 15, 16, **16.12**, **18.6**, 19, 22, 24, **24.8**, 26, **27.28**, 28, 30, **37.2**, 40, 44, 60.
  The fractional values (bold) come from `.money .cur { font-size: .62em }` applied to every amount size: 19×.62=11.78, 22×.62=13.64, 30×.62=18.6, 60×.62=37.2, and so on. The currency sign therefore becomes its own "size" at every amount level.
- About 40 hard-coded `font:`/`font-size` declarations in kis.css. Only 4 have a semantic class (`.page-title`, `.section-title`, `.amount`, `.hero`). Display sizes in use: 21, 22, 24, 26, 30, 34, 36, 40, 44, 54, 60 and several `clamp()` values. That is 11+ display sizes for one display face.
- Weights (rendered element count): **600 = 2350 (52.8%)**, 400 = 1560 (35%), 700 = 294, 500 = 250. More than half of all text is semibold: chips, links, segmented buttons, nav, week-heads, pills, toasts, nudges and day buttons. Emphasis therefore carries no signal, and screens read as uniformly heavy (see `shots/pairs/05-history.png`).
- **Numerals: no `font-variant-numeric` anywhere in kis.css.** Rubik's default digits are *proportional* (fontTools: Rubik Regular advances range from 428 to 636 units; `1111` = 171 px vs `8888` = 254 px at 100 px). Secular One digits are proportional too, and the font **has no `tnum` feature at all** (its GSUB has only `case/lnum/onum`). Result: the column of amounts in history does not align, and the live clock and time-box jitter as digits change.
- Line-heights are absolute px per rule (36/30, 28/22, 29/26, 62/60, 17/13, 19/14, 23/16). The body is 16/1.45. For Hebrew (no ascender/descender pairs like Latin, but with tall final letters ך ן ף ץ ק that hang low), 1.45–1.5 for body text is right. Display lines of 1.03–1.1 (62/60) are tight but fine for single-line numbers.
- Letter-spacing: only `.hero` (−0.5 px) and `.story .huge` (−0.03em). Secular One headings at 22–30 px have no tracking.

### 1.2 Android (`ui/kis/KisTheme.kt`, `KisType`)
| Token | Family | Size/LH | Weight | Uses |
|---|---|---|---|---|
| hero | Secular One | 60/62, −0.5sp | 400 | 10 |
| page | Secular One | 30/36 | 400 | 5 |
| amount | Secular One | 26/29 | 400 | 11 |
| title | Secular One | 22/28 | 400 | 47 |
| body | Rubik | 16/23 | 400 | 52 |
| label | Rubik | 14/19 | 600 | 73 |
| caption | Rubik | 13/17 | 400 | 74 |
| micro | Rubik | 11/13 | 600 | 5 |

- In addition, **23 distinct inline `fontSize` overrides** across ui/kis: 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 22, 24, 26, 30, 34, 40, 52, 54, 60, 64, 110, 130. Weight overrides: SemiBold ×22, Medium ×12, Bold ×9, Normal ×3. The token set is bypassed constantly.
- `fontFeatureSettings = "tnum"` appears in KisKit `Money` (line 117), KisEditor (584, 630, 829) and KisHome (259, 262). **Wherever the base style is `KisType.hero/amount` (Secular One), `tnum` is a no-op** because the font has no such feature. Hero amounts, editor time digits and the editor amount field are all still proportional. It only works where the base is Rubik (`KisHome` 259/262).
- Material `Typography` is remapped to Rubik (fine, but it adds a third parallel scale).
- minSdk 24. Compose `FontVariation` on a variable font needs API 26+, which matters for the loading strategy (§5).

### 1.3 Character assessment (see `type-a.png`, `shots/pairs/01-home.png`)
- **Secular One** has a single weight. It is a heavy, soft-cornered, ink-trap-free display face with a casual, poster/signage voice (it is a Hebrew adaptation in the spirit of "friendly headline" fonts). In Latin and digits its 7, 3 and 2 have a hand-made, slightly cartoonish swing. It is excellent for a launch splash and wrong for a ledger: it reads as "fun app", not "my money is safe here". It draws ₪ badly (₪ is 109 units wide vs 83 in Rubik, and heavy), which is why the code already patches ₪ into Rubik. The result is a two-font amount, `₪` in Rubik 600 at .62em next to digits in Secular One. At 60 px, the visible family switch inside one number is the clearest "amateur" signal on the home screen.
- **Rubik** has slightly rounded corners and very wide, geometric Latin and digits (its "0" is almost circular). That is the "rounded / childish" impression the owner feels, amplified by semibold being the default emphasis. Its Hebrew is decent but wide and bubbly at 600–700.
- **Mixed Hebrew/Latin**: "Tech Support · $120" in Rubik is noticeably wider and rounder than the Hebrew around it. Specimen `type-d.png` shows how un-mature Rubik-only display looks.
- **₪ vs digits**: at .62em with a separate family, ₪ sits on the baseline but its cap is at about 45% of digit height, and its weight differs from the digits (Rubik 600 vs Secular 400-heavy). That inconsistency is visible in `type-a.png`.

---

## 2. Specimen

Same UI snippets (page title "היסטוריה", caption, hero ₪3,070.75, tiles ₪300/$480, section "הבא בתור", card row "יום ד׳ 4.11 · 5 שעות · מסעדה · 20:30–00:30" with chip "לא שולם", mixed "Tech Support · $120", a tabular stress column ₪1,111.11/₪8,888.88/…, button "שמירה", paragraph, and 11/12/13 px captions), at 390 px, in light and dark:

| File | Pairing |
|---|---|
| `type-a.png` | a) current Rubik + Secular One (app TTFs) |
| `type-b.png` | b) Heebo only (VF) |
| `type-c.png` | c) Assistant only (VF) |
| `type-d.png` | d) Rubik only, weights for display (VF) |
| `type-e.png` | e) Noto Sans Hebrew (VF) |
| `type-f.png` | f) IBM Plex Sans Hebrew (static 400–700) |
| `type-g.png` | g) Heebo text + Rubik display |
| `type-h.png` | h) Open Sans Hebrew (VF) |
| `type-i.png` | i) Heebo + Frank Ruhl Libre (serif) for amounts and titles |
| `type-strip-light.png`, `type-strip-dark.png` | desktop strip, all 9 side by side (title + 44 px amount + meta line) |
| **`type-reco.png`** | recommended system, light + dark |
| **`type-reco-vs-current-light.png` / `-dark.png`** | current vs recommended, same content |
| **`type-reco-scale.png`** | the 8-step scale rendered |

Fonts came from npm `@fontsource(-variable)/*` (Google Fonts builds, OFL). Google Fonts CSS was also reachable (HTTP 200).

---

## 3. Evaluation

Digit metrics are measured in Chromium at 100 px. "spread" = max − min digit advance in default rendering.

| | Hebrew quality / maturity | Weights | Digits default | tnum avail. | ₪ glyph | Latin harmony | woff2 (heb+latin) | Android | 11–13 px |
|---|---|---|---|---|---|---|---|---|---|
| a Rubik + Secular One | Secular informal, Rubik rounded | Secular 1 weight; Rubik static 4 | **proportional** (Rubik spread 20.8; Secular 14.8) | Rubik yes / **Secular no** | Secular bad; Rubik OK | wide geometric Latin | ~900 KB TTF today | 5 TTFs bundled | OK |
| b **Heebo** | clean, neutral, contemporary; Israeli standard for gov/fintech-style UIs | VF 100–900 | **tabular by default** (spread 0) | n/a (already tabular) | good, proportional to digits, same stroke | Latin = Roboto: matches Android system UI | **11 + 29 = 40 KB** | 1 VF (119 KB) or static instances (~57 KB each) | very good, open counters |
| c Assistant | light and airy, a bit narrow and informal | VF 200–800 | **proportional, '1' narrower** (spread 4.1) | **no** | OK | Source Sans Latin, good | 7 + 21 KB | VF | good, but small |
| d Rubik only | still rounded, bubbly at 600+ | VF 300–900 | proportional | yes | OK | wide | 9 + 34 KB | VF | OK |
| e Noto Sans Hebrew | solid, slightly mechanical, "system" | VF 100–900 | tabular | n/a | good | Noto Sans Latin | 11 + 19 KB | VF | good |
| f IBM Plex Sans Hebrew | **most distinctive and "institutional"**, crisp, slightly technical | static 100–700 (8 files) | tabular, wide (240) | n/a | ₪ tall (ascent ≈ digit height), reads loud | Plex Latin, excellent | ~5 + 18 KB *per weight* (×4) | 4 static TTFs | good, wide |
| g Heebo + Rubik display | inherits Rubik's roundness in headings, so it does not fix the main issue | VF | Heebo tab / Rubik prop | partly | – | two Latins clash | 2 families | 2 | – |
| h Open Sans Hebrew | mature, neutral, slightly old-fashioned (2010s web) | VF 300–800 | tabular | yes | good, but its ₪ sits tall | Open Sans Latin | 16 + 47 KB | VF | good |
| i Heebo + Frank Ruhl Libre | dignified "bank statement" serif, but newspaper-like in a mobile app; serif numerals at 19 px in rows look fussy | VF | Frank proportional | yes | Frank ₪ small/light | mismatched | 2 families | 2 | serif weak at small sizes |

Observations from the screenshots:
- `type-b` (Heebo): the hero reads calm and confident, and ₪ and digits look like one word. The tabular column aligns perfectly (`1,111.11` = `8,888.88` width). The chip "לא שולם" at 11 px stays crisp. "Tech Support" blends in, because Heebo's Latin is Roboto, which Android users see everywhere.
- `type-f` (Plex): the most "serious" look. It is a valid runner-up if the owner wants a more distinctive brand voice. Costs: no variable font, wider digits (hero ₪3,070.75 needs about 8% more width, a risk in the 2–4 column tiles that already use `cqi` shrink hacks), and a louder ₪.
- `type-c` (Assistant): elegant Hebrew, but the digits cannot be made tabular, so it is disqualified for a money app.
- `type-i`: dignified but off-genre. It also adds a second family again.

---

## 4. Recommendation: one family, Heebo (variable), for both platforms

**Why Heebo**
1. **Mature and neutral, not cute.** Straight terminals, moderate width, no rounded corners. It reads as fintech or government, the "trustworthy" register the owner asked for. Hierarchy comes from size and weight (VF 100–900), not from a second "fun" face.
2. **Numbers are correct by construction.** All digits share one advance (1151/2048 units), so amounts, totals, times, the live clock and editor digits never jitter and columns align, even if a `tnum` flag is forgotten somewhere.
3. **One family solves ₪.** ₪ comes from the same design, weight and stroke as the digits. The Secular-vs-Rubik patch and its fractional sizes disappear.
4. **Latin harmony.** Latin is Roboto, the Android system face, so "Tech Support", "$120" and "PDF" sit naturally.
5. **Cost.** About 40 KB woff2 for Hebrew + Latin on web (vs about 900 KB today). On Android, 1 VF TTF (119 KB, already in the repo at `web/fonts/heebo.ttf`) replaces 5 TTFs (~900 KB).
6. **Already present**, OFL, maintained on Google Fonts, Hebrew designed by Oded Ezer. No new licensing.

Any second family is unnecessary: with a VF, 700 at −0.02em gives display contrast without changing voice.

### 4.1 Type scale (8 steps; px on web == sp on Android)

| Token | Size / LH | Weight | Tracking | Numerals | Used for |
|---|---|---|---|---|---|
| `hero` | 52 / 56 | 700 | −0.02em (−1px) | tabular | home "השבוע" total, live clock, editor time digits (44–52 via scale-down only) |
| `page` | 28 / 34 | 700 | −0.01em | – | page titles (היסטוריה, הגדרות), sheet hero titles |
| `amount` | 24 / 30 | 600 | −0.01em | tabular | tile amounts, summary card totals, editor amount field |
| `title` | 20 / 26 | 700 | −0.005em | – | section titles (הבא בתור), sheet h2, empty-state h3, coach card. **Row pay amounts use `title` at weight 600 + tabular** |
| `body` | 16 / 24 | 400 (strong: 600) | 0 | tabular in data | body, row title (600), buttons (600), inputs |
| `label` | 14 / 20 | 500 | 0 | – | chips, seg buttons, nav, links, pills, toasts, week-head |
| `caption` | 13 / 18 | 400 | 0 | tabular | meta lines ("5 שעות · מסעדה · 20:30–00:30"), helper text |
| `micro` | 11 / 14 | 500 | +0.01em | – | tags ("לא שולם", "מתוכנן"), ruler ticks (min size, no 10 px) |

Expressive exception, explicitly outside the scale: the launch intro and the monthly "story" (`.story .huge/.big`, `launch-words`) may keep fluid `clamp()` sizes, using Heebo 800 with −0.03em tracking. These screens are posters, not UI.

**Weight policy:** 400 for reading, 500 for UI labels, 600 for emphasis (row titles, buttons, amounts), 700 for headings and hero. This moves chips, nav, seg, links and week-heads from 600 to 500, which brings the share of 600 from about 53% to roughly 20–25% and restores emphasis.

**Line-height:** body 1.5 (24/16) for Hebrew paragraphs. UI single lines use the table values. Never below 1.08 on display numbers.

### 4.2 Numeric style
- Web: `.money, .hero, .amount, .pay, .clock, .time-box b, .caption, [data-num] { font-variant-numeric: tabular-nums; }`. Heebo is tabular by default anyway; declare it for safety in case the fallback is used.
- Android: keep `fontFeatureSettings = "tnum"` in `Money` and the editor. With Heebo it becomes harmless, and it becomes effective if a fallback ever appears. Remove the misleading assumption that it works on Secular One.
- Times: render ranges as an LTR-isolated unit (`20:30–00:30`, en-dash, no spaces), as now.

### 4.3 Currency
- ₪/$ in the **same family (Heebo)**, weight 500 (one step lighter than the 600/700 digits, so the symbol recedes), placed before the number in an LTR-isolated run as today.
- Size: **0.66em at hero/page sizes (≥ 28px)**, **0.8em at amount/title sizes (≤ 24px)**, **1em inside caption/body text** (e.g. "· $120"). Gap `margin-inline-end: .08em`. Two fixed ratios instead of one .62em multiplier. On web, define them as two classes (`.cur`, `.cur.sm`) so the census no longer explodes into fractional sizes. Note: the 0.72em ratio used in the reco screenshot reads slightly large at 52 px; 0.66em is the recommended value.
- Baseline: default alphabetic baseline. Heebo's ₪ is drawn on the baseline at Hebrew x-height (58 vs 73 for digits at 100 px), so no shift is needed.

### 4.4 Mapping: current → new token

**Web (kis.css)**
| Current | New |
|---|---|
| `.page-title` 30/36 Secular | `page` 28/34 700 |
| `.section-title h2`, `.sheet h2`, `.empty h3` 22/28 Secular; `.coach-card h2` 21/27; `.android-download-sheet h2` 24 | `title` 20/26 700 |
| `.hero` 60/62 Secular | `hero` 52/56 700 |
| `.amount` 26/29 Secular; `.tiles .amount` overrides; `.mini-tile b` 20/600 | `amount` 24/30 600 (keep the cqi shrink for the tiles but cap it at 24) |
| `.shift .pay` 19/22 Secular | `title` size, weight 600, tabular |
| `.live .clock` 40/44, `.time-box b` 44/48 Secular | `hero` family/weight at 44/48 (one size, 700, tabular) |
| body 16/1.45; `.intro-sub`/`.story p` 18 | `body` 16/24 (story copy stays expressive) |
| `.label` 14/19 600; `.link` 14/600; `.chip` 14/600; `.seg button` 13/600; `.nav button` 14/600; `.modes button` 14/600; `.toast` 14/600; `.week-head` 13/600; `.install-help summary` | `label` 14/20 **500** (seg moves 13 → 14) |
| `.btn` 16/600, `.btn.small` 15 | `body` 600 (small button 16 as well; 15 removed) |
| `.caption`, `.shift .sub`, `.legend`, `.story-entry small`, `.save-bar p` 13–14 | `caption` 13/18 400 |
| `.tag` 11/600/13; `.ruler .ticks` 10; `.days button small` 12; `.apk-entry` 12/11 | `micro` 11/14 500 (10 and 12 px removed) |
| `.money .cur` .62em Rubik 600 | `.cur` 0.66em / `.cur.sm` 0.8em, Heebo 500 |
| `.launch-words` 54, `.intro-head` 36, `.launch-ask` 34, `.intro-done` 26, `.story .big/.huge/h3` | poster exception: Heebo 800, −0.03em, keep sizes |

**Android (KisType)**
| Current | New |
|---|---|
| `hero` Secular 60/62 | `hero` Heebo W700 52/56, −0.02em, tnum |
| `page` Secular 30/36 | `page` Heebo W700 28/34, −0.01em |
| `amount` Secular 26/29 | `amount` Heebo W600 24/30, −0.01em, tnum |
| `title` Secular 22/28 | `title` Heebo W700 20/26 |
| `body` Rubik 16/23 | `body` Heebo W400 16/24 |
| `label` Rubik 14/19 SemiBold | `label` Heebo **W500** 14/20 |
| `caption` Rubik 13/17 | `caption` Heebo W400 13/18 |
| `micro` Rubik 11/13 SemiBold | `micro` Heebo W500 11/14, +0.01em |
| (new) | `pay` = `title.copy(fontWeight = W600, fontFeatureSettings = "tnum")` |
| Material `Typography` remap to Rubik | remap to Heebo (same helper) |
| inline sizes 10/12/15/17/18/19/34/54/64 | fold into the nearest token. KisHome 30-sp time → `hero` scaled. 110/130 sp story/launch → poster exception |

---

## 5. Loading strategy

**Web**
- Replace the 6 TTFs with Heebo VF woff2 split into two `unicode-range` subsets: Hebrew (U+0590–05FF, U+20AA ₪, U+200C–2010, U+FB1D–FB4F) about 11 KB, and Latin about 29 KB. Use the Google/fontsource builds (better compressed than a naive fontTools subset of the bundled TTF, which gave 84 KB).
- `@font-face { font-family: Heebo; font-weight: 100 900; font-display: swap; src: url(heebo-hebrew.woff2) format('woff2'); unicode-range: … }`, the same for Latin.
- `<link rel="preload" as="font" type="font/woff2" crossorigin href="fonts/heebo-hebrew.woff2">`: preload only the Hebrew subset. Latin loads on demand.
- Fallback with metric override, to stop the layout shift on swap: `@font-face { font-family: HeeboFallback; src: local('Arial'); size-adjust: ~104%; ascent-override: 107%; descent-override: 29%; }` (tune once), and `--text: Heebo, HeeboFallback, system-ui, sans-serif`.
- Remove `--display` (or alias it to `--text`) and remove the Rubik and Secular One files from the web bundle.

**Android**
- Option A (simplest, all API levels, recommended because minSdk = 24): generate 4 static instances from the VF (400/500/600/700, ~57 KB each, verified with `fontTools.varLib.instancer`) → `res/font/heebo_regular.ttf … heebo_bold.ttf`. Then `val Heebo = FontFamily(Font(R.font.heebo_regular, W400), …)`. Total ~228 KB vs ~900 KB today.
- Option B (API 26+ only, or accept the default instance on 24/25): a single `res/font/heebo_vf.ttf` (119 KB) with `Font(R.font.heebo_vf, weight = W600, variationSettings = FontVariation.Settings(FontVariation.weight(600)))`. On API 24–25 every weight renders as the default 400. That is acceptable for no screen, so prefer A unless minSdk rises.
- Delete `secular_one.ttf` and `rubik_*.ttf` from `res/font` only after every reference has moved. Keep OFL text in `docs/licenses` (Heebo OFL replaces Rubik/Secular One entries). This contradicts the AGENTS.md product rule "Rubik for text and Secular One for display; ₪/$ always render in Rubik", so **the owner must explicitly approve the change and AGENTS.md / `docs/preservation-and-sync.md` must be updated with it.**

---

## 6. Why this is superior, in one paragraph
One neutral, well-drawn Hebrew grotesque with a full variable weight range gives a calm, adult, "bank-grade" voice. Hierarchy comes from 8 deliberate sizes and 4 purposeful weights instead of 25 sizes, 53% semibold and a playful second family. Money becomes typographically trustworthy: digits are fixed-width so totals and times never wobble and columns align, ₪ shares the digits' design, and the visible font switch inside every amount is gone. It also cuts font payload by about 95% on web and about 75% on Android, matches Android's own Latin (Roboto), and removes the no-op `tnum` trap on Secular One.

## 7. Risks and open items
- Visual identity shift: Secular One's headings are a recognisable part of "כיס" 2.0, and the launch/story screens may feel less festive. That is mitigated by the Heebo 800 poster exception.
- Width changes: Heebo digits are narrower than Rubik/Secular at equal size, which helps tiles. Re-check the `cqi` tile shrink and the editor time-box after implementation.
- AGENTS.md and `docs/preservation-and-sync.md` currently mandate Rubik + Secular One, so this needs explicit owner approval and a doc update. It must be checked on a physical device (Hebrew hinting at 11 sp, dark theme).
- Runner-up if the owner wants more character: IBM Plex Sans Hebrew (`type-f.png`), with the same scale. Costs: 4 static files, wider digits, louder ₪.
