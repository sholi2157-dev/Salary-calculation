# Salary Calculation 2.0 — design direction prototypes

Synthetic data only. Not product code; nothing here ships.

| Direction | Source | Private preview |
|---|---|---|
| A · סרגל (ruler, precise, light) | `a-sargel.html` | https://claude.ai/artifact/LZkfyig9i6dh9DgRrhEAZy |
| B · כיס (wallet, colour per workplace, keypad) | `b-kis.html` | https://claude.ai/artifact/X5t7iAtAo38mSroQMC5ykT |
| C · יומן (journal, dark-first, sentence entry) | `c-yoman.html` | https://claude.ai/artifact/JC43BdeeBZxSHbxBrKyBWe |

`shared/core.js` holds the common behaviour (time rounding, overnight, unknown rate, bonus, recurring series, auto-confirm with undo, summaries) so the directions differ only in design.
Build: `python3 build.py` inlines the shared engine into `dist/`.
