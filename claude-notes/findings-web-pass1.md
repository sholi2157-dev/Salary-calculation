# Web pass 1 findings (390px, light+dark, synthetic seed) — screenshots in shots/web, pairs in shots/pairs
W1 Dark theme neutral grey (#121212/#1E1E1E) vs light cool navy-tinted (#F1F3F8/#1B2335): not same identity. (kis.css :root vs dark)
W2 Four+ different "selected" visuals on one editor screen: place chip filled workplace color; tab white raised; day white+ink border; currency white raised; filter-place outline+ink border. In DARK: editor tab selected = white, filter segments selected = dark (#121212) -> contradictory.
W3 Indigo #5145E8 is brand accent (FAB, glow, focus, recap banner, notif color) AND workplace #1 color -> brand vs data confusion. FAB indigo while primary buttons ink, switches green(paid).
W4 Switch ON uses --paid (money green) = semantic misuse.
W5 Live card inverted: light -> dark ink slab, dark -> white slab (glaring). Contradicts Android comment "never a white slab in dark mode".
W6 Web live card: money big ₪0.00 + small clock; Android 2.2.3 (unreleased) made clock big/money small -> parity gap. Web settings = flat list w/ one notifications switch; Android 2.2.3 = category cards + per-type switches -> parity gap.
W7 Duration formats mixed: "4:00 שעות" (editor) vs "2.5 שעות", "3.75 שעות", "65.25 שעות", "21.25 ש׳" (lists/summary).
W8 Search: native type=search cancel (×, browser-blue) + custom × + "סגירה" chip = 3 dismiss controls. (history.js:75-76, no ::-webkit-search-cancel-button rule)
W9 Auto focus ring (3px glow) on first button when sheets/editor open on touch (עריכה/ביטול/השלמת תעריף) — looks like a selected state.
W10 Group detail: headline ₪350 vs "ההכנסה שלי ₪530" & "לקבל מהמעסיק ₪980": unclear which is the main number. 10 buttons wall (per worker לא שולם/שיתוף/העתקה). Outline chip "לא שולם" = another button style.
W11 Settings: "הורדת גיבוי" wraps to 2 lines in 3-col grid at 390. "ייבוא נתונים" is the only primary (dark) button in backup group = wrong emphasis. Info dots on almost every row. "משוב ותקלות" first group.
W12 Action chips (כתיבה/מסעדה/ניהול/פתיחה/הצגה) styled like selection chips — action vs selection ambiguity.
W13 Workplace palette: 8 colors; 9th place repeats indigo (מוקד לילה = מסעדה). Near-duplicate hue pairs: indigo/purple, rose/burgundy, green/teal.
W14 Places sheet: 9 identical "עריכה" outline buttons + drag handles — row itself could be tappable.
W15 Home: today card mostly empty vs all-time card with 3 lines -> unbalanced; pending pill half-width right-aligned vs full-width cards; planned shift appears in both "הבא בתור" and "אחרונים".
W16 Recap banner = only saturated gradient block; same brightness in dark -> loudest element in dark.
W17 Editor time ruler overnight 20:30–00:30 drawn as two fragments (start near 21 at left, end marker at 00 right) — hard to read; axis labels 24..00 right-to-left.
W18 Editor header: "קבוצה" as top-left action — unclear; tabs שעון/ידני/קבלנות.
W19 Empty search: summary card "0 משמרות • 0 שעות —" shown above empty state; copy sheet offers copy of 0 shifts.
W20 Android download sheet: fallback text "גרסה 2.0.3 · קישור גיבוי ישיר" + fallback APK 2.0.3 (download.js:4,12,30); sheet has × close unlike other sheets (grab handle only); big empty gap.
W21 Toast floats mid-screen overlapping list; action text indigo.
W22 Series sheet shows "רישום אוטומטי" switch when no series exist.
W23 Fonts: 4 Rubik TTF ~830KB + Heebo 122KB + Secular One 84KB, no woff2/subset; Heebo loaded as fallback.
W24 Inline style="" heavy: account.js 27, editor.js 30, home.js 19, settings.js 18, history.js 14 -> tokens bypassed (e.g. hero font-size:44px inline in detail).
W25 Dark tokens duplicated twice in kis.css (media + data-theme) — drift risk.
