"""Validates every proposed token pair (WCAG), workplace CVD separation, collisions, cross-theme identity; emits
out/proposed_contrast.md, out/proposed_tokens.md, out/kis-tokens.css, out/KisTokens.kt, out/proposed.json."""
import json, itertools, os
from colorlib import *
from tokens_proposed import LIGHT, DARK, workplaces, NH
from wp_eval import report
import tokens_current as CUR

os.makedirs('out', exist_ok=True)
WPP = workplaces()

def val(t, k, over='bg'):
    v = t[k]
    if isinstance(v, tuple):
        base = t[over] if not isinstance(t[over], tuple) else val(t, over)
        return rgb2hex(composite(hex2rgb(v[0])[:3] + (v[1],), hex2rgb(base)))
    return v

rows = []
def chk(th, kind, fl, f, bl, b, req):
    r = contrast(f, b); rows.append((th, kind, fl, f, bl, b, round(r, 2), req, r >= req))

for th, t in (('light', LIGHT), ('dark', DARK)):
    surf = {k: val(t, k) for k in ('bg', 'surface', 'surfaceRaised', 'fill', 'selectedBg', 'track')}
    surf['nav'] = val(t, 'nav'); surf['bar'] = val(t, 'bar'); surf['live'] = t['live']
    surf['glowPeak'] = val(t, 'glow')
    for txt in ('ink', 'ink2'):
        for s in ('bg', 'surface', 'surfaceRaised', 'fill', 'glowPeak'):
            chk(th, 'text', txt, t[txt], s, surf[s], 4.5)
    for s in ('bg', 'surface', 'surfaceRaised'):  # ink3 = placeholder/meta on page & cards only, never on fill
        chk(th, 'text', 'ink3', t['ink3'], s, surf[s], 4.5)
    for sem in ('success', 'warning', 'danger', 'info', 'accentText'):
        for s in ('bg', 'surface', 'surfaceRaised', 'fill'):
            chk(th, 'text', sem, t[sem], s, surf[s], 4.5)
    for sem in ('success', 'warning', 'danger', 'info'):
        chk(th, 'text', sem, t[sem], sem + 'Soft', t[sem + 'Soft'], 4.5)
        if 'on' + sem.capitalize() in t:
            chk(th, 'text', 'on' + sem.capitalize(), t['on' + sem.capitalize()], sem + ' (solid)', t[sem], 4.5)
    chk(th, 'text', 'accentText', t['accentText'], 'accentSoft', t['accentSoft'], 4.5)
    chk(th, 'text', 'accentInk', t['accentInk'], 'accent (FAB)', t['accent'], 4.5)
    chk(th, 'text', 'onPrimary', t['onPrimary'], 'primary', t['primary'], 4.5)
    chk(th, 'text', 'selectedInk', t['selectedInk'], 'selectedBg', surf['selectedBg'], 4.5)
    chk(th, 'text', 'ink', t['ink'], 'track', surf['track'], 4.5)
    chk(th, 'text', 'navInk', t['navInk'], 'nav', surf['nav'], 4.5)
    chk(th, 'text', 'navOnInk', t['navOnInk'], 'navOn', t['navOn'], 4.5)
    chk(th, 'text', 'onBar', t['onBar'], 'bar', surf['bar'], 4.5)
    chk(th, 'text', 'barAction', t['barAction'], 'bar', surf['bar'], 4.5)
    chk(th, 'text', 'onLive', t['onLive'], 'live', t['live'], 4.5)
    chk(th, 'text', 'warning', t['warning'], 'warningSoft', t['warningSoft'], 4.5)
    for s in ('bg', 'surface', 'fill'):
        chk(th, 'ui', 'focus', t['focus'], s, surf[s], 3.0)
        chk(th, 'ui', 'lineStrong', t['lineStrong'], s, surf[s], 3.0)
    chk(th, 'ui', 'selectedEdge', t['selectedEdge'], 'track', surf['track'], 3.0)
    chk(th, 'ui', 'controlOn', t['controlOn'], 'surface', surf['surface'], 3.0)
    chk(th, 'ui', 'controlKnob', t['controlKnob'], 'controlOn', t['controlOn'], 3.0)
    chk(th, 'ui', 'lineStrong (switch off)', t['lineStrong'], 'surface', surf['surface'], 3.0)
    chk(th, 'ui', 'selectedBg vs track', surf['selectedBg'], 'track', surf['track'], 1.0)
    chk(th, 'info', 'disabledInk', t['disabledInk'], 'surface', surf['surface'], 2.0)
    for n, fr, to, on, ad, adi in WPP:
        if th == 'light':
            chk(th, 'text', n + '.on', on, n + '.from', fr, 4.5)
            chk(th, 'text', n + '.on', on, n + '.to', to, 4.5)
            if n != 'mustard': chk(th, 'text', n + '.from as text', fr, 'surface', surf['surface'], 4.5)
        else:
            chk(th, 'text', n + '.accentDarkInk', adi, n + '.accentDark', ad, 4.5)
            for s in ('bg', 'surface', 'surfaceRaised'):
                chk(th, 'text', n + '.accentDark as text', ad, s, surf[s], 4.5)

fails = [r for r in rows if not r[-1]]
with open('out/proposed_contrast.md', 'w') as f:
    f.write('| theme | kind | fg | bg | ratio | need | ok |\n|---|---|---|---|---|---|---|\n')
    for r in rows: f.write(f'| {r[0]} | {r[1]} | {r[2]} `{r[3]}` | {r[4]} `{r[5]}` | {r[6]} | {r[7]} | {"OK" if r[8] else "**FAIL**"} |\n')
print('pairs checked', len(rows), 'fails', len(fails))
for r in fails: print(r)

# Workplace separation (current vs proposed)
wp = {
    'current_from': report([w[1] for w in CUR.WORKPLACES], 3), 'current_dark': report([w[4] for w in CUR.WORKPLACES], 3),
    'proposed_from': report([w[1] for w in WPP], 3), 'proposed_dark': report([w[4] for w in WPP], 3),
}
for k, v in wp.items(): print(k, 'worst', min(x[0][0] for x in v.values()), v)
# collisions with semantics / brand
coll = {}
for th, t, idx in (('light', LIGHT, 1), ('dark', DARK, 4)):
    for n, *c in WPP:
        col = c[idx - 1]
        for s in ('success', 'warning', 'danger', 'accent'):
            coll[f'{th}:{n}~{s}'] = round(ciede2000(col, t[s]), 1)
    for n, *c in CUR.WORKPLACES:
        col = c[idx - 1]
        tt = CUR.LIGHT if th == 'light' else CUR.DARK
        for s, sk in (('success', 'paid'), ('warning', 'pending'), ('danger', 'danger'), ('accent', 'glow')):
            coll[f'CUR {th}:{n}~{s}'] = round(ciede2000(col, tt[sk]), 1)
low = {k: v for k, v in coll.items() if v < 15}
print('collisions <15', low)

# identity: neutral hue/chroma per theme
ident = {th: {k: fmt_lch(val(t, k)) for k in ('bg', 'surface', 'surfaceRaised', 'fill', 'line', 'lineStrong', 'ink', 'ink2', 'ink3')} for th, t in (('light', LIGHT), ('dark', DARK))}
print(json.dumps(ident, indent=0))
json.dump(dict(rows=rows, wp=wp, coll=coll, ident=ident, workplaces=WPP), open('out/proposed.json', 'w'), indent=1)

# ---- emit CSS ----
def css_val(v):
    if isinstance(v, tuple):
        r, g, b, _ = hex2rgb(v[0]); return f'rgb({r} {g} {b} / {v[1]:.2f})'
    return v
def kebab(k): return ''.join('-' + c.lower() if c.isupper() else c for c in k).replace('ink2', 'ink-2').replace('ink3', 'ink-3')
with open('out/kis-tokens.css', 'w') as f:
    for sel, t in ((':root', LIGHT), (':root[data-theme="dark"]', DARK)):
        f.write(sel + ' {\n')
        for k, v in t.items(): f.write(f'  --{kebab(k)}: {css_val(v)};\n')
        if t is DARK: f.write('  color-scheme: dark;\n')
        f.write('}\n')
    f.write('/* Workplaces: --wp-N-from / -to / -on (light) and --wp-N-acc / -acc-ink (dark) */\n:root {\n')
    for i, (n, fr, to, on, ad, adi) in enumerate(WPP):
        f.write(f'  --wp-{i}-from: {fr}; --wp-{i}-to: {to}; --wp-{i}-on: {on}; --wp-{i}-acc: {ad}; --wp-{i}-acc-ink: {adi}; /* {n} */\n')
    f.write('}\n')
# ---- emit Kotlin ----
def kc(v):
    if isinstance(v, tuple):
        a = round(v[1] * 255); return f'Color(0x{a:02X}{v[0][1:].upper()})'
    return f'Color(0xFF{v[1:].upper()})'
with open('out/KisTokens.kt', 'w') as f:
    f.write('// Proposed "Kis 2" tokens (generated by ux-audit/tools/agent-color/validate_proposed.py). Proposal only.\n')
    f.write('@Immutable\ndata class KisTokens(\n    val dark: Boolean,\n')
    keys = list(LIGHT.keys())
    for k in keys: f.write(f'    val {k}: Color,\n')
    f.write(')\n\n')
    for name, t, d in (('KisLight2', LIGHT, 'false'), ('KisDark2', DARK, 'true')):
        f.write(f'val {name} = KisTokens(\n    dark = {d},\n')
        f.write(''.join(f'    {k} = {kc(t[k])},\n' for k in keys))
        f.write(')\n\n')
    f.write('val Workplaces2 = listOf(\n')
    for n, fr, to, on, ad, adi in WPP:
        f.write(f'    Workplace("{n}", {kc(fr)}, {kc(to)}, {kc(on)}, {kc(ad)}, {kc(adi)}),\n')
    f.write(')\n')
# ---- emit token table ----
with open('out/proposed_tokens.md', 'w') as f:
    f.write('| token | light | light OKLCH | dark | dark OKLCH |\n|---|---|---|---|---|\n')
    for k in LIGHT:
        a, b = LIGHT[k], DARK[k]
        fa = (f'`{a[0]}` @{a[1]:.2f}' if isinstance(a, tuple) else f'`{a}`'); fb = (f'`{b[0]}` @{b[1]:.2f}' if isinstance(b, tuple) else f'`{b}`')
        la = fmt_lch(a[0] if isinstance(a, tuple) else a); lb = fmt_lch(b[0] if isinstance(b, tuple) else b)
        f.write(f'| {k} | {fa} | {la} | {fb} | {lb} |\n')
    f.write('\n| # | workplace | from (light) | to (light) | on | accentDark | accentDarkInk |\n|---|---|---|---|---|---|---|\n')
    for i, (n, fr, to, on, ad, adi) in enumerate(WPP):
        f.write(f'| {i} | {n} | `{fr}` {fmt_lch(fr)} | `{to}` | `{on}` | `{ad}` {fmt_lch(ad)} | `{adi}` |\n')
