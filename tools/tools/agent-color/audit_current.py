"""Audit of the current Kis palette: contrast matrix, OKLCH identity, workplace deltaE + CVD, semantic balance, census.
Writes out/current.json and prints markdown fragments used in notes/agent-color.md."""
import json, os, itertools, re, collections
from colorlib import *
from tokens_current import LIGHT, DARK, GLOW_A, WORKPLACES

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'out'); os.makedirs(OUT, exist_ok=True)
res = {}

def solid(t, name, over='bg'):
    """Resolve a token to an opaque colour (translucent tokens are composited over `over`)."""
    c = hex2rgb(t[name] if name in t else name)
    return rgb2hex(composite(c, hex2rgb(t[over]))) if c[3] < 1 else rgb2hex(c)

def mix(fg, bg, a):
    return rgb2hex(composite(hex2rgb(fg)[:3] + (a,), hex2rgb(bg)))

rows = []
def check(theme, kind, fg_label, fg, bg_label, bg, req):
    r = contrast(fg, bg)
    rows.append(dict(theme=theme, kind=kind, fg=fg_label, fgv=fg, bg=bg_label, bgv=bg, ratio=round(r, 2), req=req, ok=r >= req))

for th, t in (('light', LIGHT), ('dark', DARK)):
    S = {k: solid(t, k) for k in ('bg', 'surface', 'sunk', 'pendingSoft', 'edit')}
    S['nav(over bg)'] = solid(t, 'nav'); S['bar(over bg)'] = solid(t, 'bar')
    S['bar(over surface)'] = solid(t, 'bar', 'surface')
    S['glow-top(bg)'] = mix(t['glow'], t['bg'], GLOW_A[th])
    if th == 'dark':
        S['tile(ink10% over bg)'] = mix(t['ink'], t['bg'], .10)
    for txt in ('ink', 'muted'):
        for s in ('bg', 'surface', 'sunk', 'nav(over bg)', 'glow-top(bg)') + (('tile(ink10% over bg)',) if th == 'dark' else ()):
            check(th, 'text', txt, t[txt], s, S[s], 4.5)
    for txt in ('paid', 'danger', 'pending'):
        for s in ('bg', 'surface', 'sunk'):
            check(th, 'text', txt, t[txt], s, S[s], 4.5)
    check(th, 'text', 'pending', t['pending'], 'pendingSoft', S['pendingSoft'], 4.5)
    check(th, 'text', 'navInk', t['navInk'], 'nav(over bg)', S['nav(over bg)'], 4.5)
    check(th, 'text', 'navOnInk', t['navOnInk'], 'navOn', t['navOn'], 4.5)
    check(th, 'text', 'onBar', t['onBar'], 'bar(over bg)', S['bar(over bg)'], 4.5)
    check(th, 'text', 'glow (toast action)', t['glow'], 'bar(over bg)', S['bar(over bg)'], 4.5)
    check(th, 'text', 'onInk (primary btn)', t['onInk'], 'ink (primary btn)', t['ink'], 4.5)
    check(th, 'text', 'editInk', t['editInk'], 'edit', t['edit'], 4.5)
    check(th, 'text', '#FFFFFF (danger btn)', '#FFFFFF', 'danger', t['danger'], 4.5)
    check(th, 'text', '#FFFFFF (FAB +, 28px)', '#FFFFFF', 'glow (FAB)', t['glow'], 3.0)
    check(th, 'text', 'ink disabled (.45)', mix(t['ink'], t['surface'], .45), 'surface', S['surface'], 3.0)
    # UI components (3:1)
    for s in ('bg', 'surface'):
        check(th, 'ui', 'focus ring (glow)', t['glow'], s, S[s], 3.0)
        check(th, 'ui', 'line (outline btn/input edge)', t['line'], s, S[s], 3.0)
    check(th, 'ui', 'switch ON track (paid)', t['paid'], 'surface', S['surface'], 3.0)
    check(th, 'ui', 'switch OFF track (line)', t['line'], 'surface', S['surface'], 3.0)
    check(th, 'ui', 'switch knob #FFF', '#FFFFFF', 'switch OFF track (line)', t['line'], 3.0)
    check(th, 'ui', 'surface card edge', S['surface'], 'bg', S['bg'], 1.0)
    for name, fr, to, on, ad, adi in WORKPLACES:
        if th == 'light':
            check(th, 'text', f'{name}.on', on, f'{name}.from', fr, 4.5)
            check(th, 'text', f'{name}.on', on, f'{name}.to', to, 4.5)
            check(th, 'text', f'{name}.from as text', fr, 'surface', S['surface'], 4.5)
            check(th, 'ui', f'{name}.from chip', fr, 'surface', S['surface'], 3.0)
        else:
            check(th, 'text', f'{name}.accentDarkInk', adi, f'{name}.accentDark', ad, 4.5)
            check(th, 'text', f'{name}.accentDark as text', ad, 'surface', S['surface'], 4.5)
            check(th, 'text', f'{name}.accentDark as text', ad, 'bg', S['bg'], 4.5)
            check(th, 'text', f'{name}.on (avatar gradient in dark)', on, f'{name}.from', fr, 4.5)
            check(th, 'ui', f'{name}.from avatar vs surface', fr, 'surface', S['surface'], 3.0)
    res[th + '_surfaces'] = S
res['contrast'] = rows

# ---- identity (OKLCH) ----
ident = {}
for th, t in (('light', LIGHT), ('dark', DARK)):
    ident[th] = {k: (t[k], *[round(x, 4) for x in oklch(solid(t, k))]) for k in
                 ('bg', 'surface', 'sunk', 'line', 'ink', 'muted', 'navInk', 'bar', 'nav', 'edit', 'pendingSoft')}
res['identity'] = ident

# ---- workplaces ----
def pairs(cols, names, f):
    return sorted(((f(a, b), na, nb) for (na, a), (nb, b) in itertools.combinations(zip(names, cols), 2)))
names = [w[0] for w in WORKPLACES]
wp = {}
for label, idx in (('from', 1), ('to', 2), ('accentDark', 4)):
    cols = [w[idx] for w in WORKPLACES]
    d = {'lch': {n: [round(x, 3) for x in oklch(c)] for n, c in zip(names, cols)}}
    for vis in ('normal', 'protan', 'deutan', 'tritan'):
        cc = cols if vis == 'normal' else [cvd(c, vis) for c in cols]
        d[vis] = [(round(x, 1), a, b) for x, a, b in pairs(cc, names, ciede2000)][:6]
        d[vis + '_ok'] = [(round(x, 1), a, b) for x, a, b in pairs(cc, names, de_ok)][:4]
    wp[label] = d
wp['brand_vs_wp1'] = ciede2000('#5145E8', WORKPLACES[0][1])
res['workplaces'] = wp

# ---- semantics ----
sem = {}
for th, t in (('light', LIGHT), ('dark', DARK)):
    sem[th] = {k: (t[k], [round(x, 3) for x in oklch(t[k])]) for k in ('paid', 'pending', 'danger', 'pendingSoft')}
res['semantic'] = sem

# ---- census aggregation ----
cen = json.load(open(os.path.join(HERE, '..', '..', 'notes', 'explore-light_dark.json')))['census']
agg = {'light': collections.defaultdict(collections.Counter), 'dark': collections.defaultdict(collections.Counter)}
def norm(s):
    m = re.match(r'rgba?\(([\d.]+), ([\d.]+), ([\d.]+)(?:, ([\d.]+))?\)', s)
    if not m: return s
    r, g, b = (float(x) for x in m.groups()[:3]); a = m.group(4)
    h = rgb2hex((r, g, b))
    return h if a is None else (f'{h}@{float(a):.2f}' if float(a) > 0 else 'transparent')
for page, d in cen.items():
    th = 'light' if page.startswith('light') else 'dark'
    if page.endswith('-full'): continue
    for prop in ('color', 'bg', 'border'):
        for k, v in d.get(prop, {}).items(): agg[th][prop][norm(k)] += v
res['census'] = {th: {p: c.most_common() for p, c in v.items()} for th, v in agg.items()}
json.dump(res, open(os.path.join(OUT, 'current.json'), 'w'), indent=1, ensure_ascii=False)

# ---- print ----
print('## contrast failures')
for r in rows:
    if not r['ok']: print(f"| {r['theme']} | {r['kind']} | {r['fg']} {r['fgv']} | {r['bg']} {r['bgv']} | {r['ratio']} | {r['req']} |")
print('## all')
for r in rows: print(f"| {r['theme']} | {r['kind']} | {r['fg']} | {r['bg']} | {r['ratio']} | {'OK' if r['ok'] else 'FAIL'} |")
print('## identity')
for th in ident:
    for k, v in ident[th].items(): print(th, k, v)
print('## workplaces'); print(json.dumps(wp, indent=0)[:6000])
print('## semantic', json.dumps(sem))
print('## census')
for th in agg:
    for p in agg[th]: print(th, p, agg[th][p].most_common(25))
