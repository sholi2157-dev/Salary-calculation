# Salary Calculation 2.0 — design direction prototypes

Synthetic data only. Not product code; nothing here ships.

| Direction | Source | Private preview |
|---|---|---|
| A · סרגל — not chosen; its ruler moved into B | `archive/a-sargel.html` | https://claude.ai/artifact/LZkfyig9i6dh9DgRrhEAZy |
| B · כיס (wallet, colour per workplace, keypad) | `b-kis.html` | https://claude.ai/artifact/X5t7iAtAo38mSroQMC5ykT |
| C · יומן — dropped by owner after round 1; kept for reference | `archive/c-yoman.html` (round-1 engine build: `archive/c-yoman.dist.html`) | https://claude.ai/artifact/JC43BdeeBZxSHbxBrKyBWe |

`shared/core.js` holds the common behaviour (time rounding, overnight, unknown rate, bonus, recurring series, auto-confirm with undo, summaries) so the directions differ only in design.
Build: `python3 build.py` inlines the shared engine into `dist/`.

## Round 2 (owner feedback on round 1)
- C dropped. A and B now follow the current app's structure: summary → start shift → recent; clock / manual / group modes.
- Fixed: "other date" did not open on phones; currency now visible as ₪/$ in the main form; start/stop/cancel shift is prominent and persists across reloads.
- A: the ruler is display-only (accidental drags); times use the phone's own picker plus ±15.
- B: calmer palette, docked bottom bar, smaller radii to reduce the template look.
- Engine: hours-only ("ידני") entries supported end to end.

## Owner decision after round 2
Owner preferred the round-1 look. A and B are restored to round 1 (commit 3b8f2d6) with only two invisible bug fixes: the "other date" picker now opens on phones, and A's advanced fields no longer overflow. Round-2 files are kept in `archive/round2/` for reference. Owner especially liked B's bottom navigation bar.

## Round 3 — owner chose B (כיס)
- A's ruler added to B: drag handles only after a deliberate move (stray taps do nothing), 15-minute snap, −30/−15/+15/+30.
- Tapping a shift opens a details card (edit, share, copy, delete, paid toggle). Edit mode is visibly different: ink header, "עריכת משמרת", a "לפני" line, "שמירת שינויים", and a leave-without-saving confirmation.
- History selection: share, copy, paid, rate completion, delete with undo. Share text isolates times/amounts so they never flip in Hebrew (WhatsApp).
- Usability: keypad opens only when a field is tapped; save always visible with the reason when disabled; ₪/$ next to the rate; שעון/ידני/קבוצה modes; undo right after saving; start/stop/cancel shift persists.
