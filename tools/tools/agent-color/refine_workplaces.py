"""Local refinement around the hand-tuned palette: maximise the worst-case CIEDE2000 across normal/protan/deutan/tritan,
keeping each slot inside its named hue family, chroma >= floor, and the contrast constraints (white text on light
`from` >= 4.5, mustard ink >= 7; dark accents >= 4.5 on the proposed dark surface #17191F)."""
import random, itertools, json
from colorlib import *
from wp_eval import report, NAMES
HUE = [(262, 280), (142, 162), (356, 370), (74, 88), (306, 328), (236, 254), (186, 205), (10, 26)]
CMIN = [0.15, 0.12, 0.15, 0.13, 0.15, 0.11, 0.075, 0.14]
START = {
 'light': [(0.46,0.22,268),(0.53,0.15,150),(0.585,0.21,364),(0.81,0.155,80),(0.585,0.21,316),(0.56,0.17,248),(0.465,0.09,195),(0.44,0.17,16)],
 'dark':  [(0.70,0.15,272),(0.85,0.16,150),(0.77,0.15,360),(0.86,0.15,84),(0.77,0.15,318),(0.82,0.10,244),(0.70,0.12,193),(0.64,0.17,20)],
}
def worst(cols):
    w = 1e9
    for v in ('normal', 'protan', 'deutan', 'tritan'):
        s = cols if v == 'normal' else [cvd(c, v) for c in cols]
        for a, b in itertools.combinations(range(8), 2): w = min(w, ciede2000(s[a], s[b]))
    return w
def ok(cols, theme):
    for i, c in enumerate(cols):
        if theme == 'light':
            if i == 3:
                if contrast('#1A1200', c) < 7: return False
            elif contrast('#FFFFFF', c) < 4.5: return False
        elif contrast(c, '#17191F') < 4.5: return False
        if oklch(c)[1] < CMIN[i] - 0.01: return False
    return True
LR = {'light': [(.44,.52),(.50,.58),(.54,.62),(.79,.85),(.52,.62),(.52,.60),(.44,.52),(.40,.48)],
      'dark':  [(.66,.74),(.78,.86),(.72,.80),(.84,.88),(.68,.80),(.74,.84),(.66,.76),(.60,.68)]}
CMAX = 0.23
res = {}
for theme, p in START.items():
    random.seed(7)
    cols = [from_oklch(L, C, H % 360) for L, C, H in p]
    best = worst(cols) if ok(cols, theme) else -1
    for it in range(5000):
        q = list(p); i = random.randrange(8); L, C, H = q[i]
        lo, hi = LR[theme][i]
        q[i] = (min(hi, max(lo, L + random.gauss(0, 0.015))), min(CMAX, max(CMIN[i], C + random.gauss(0, 0.01))), min(HUE[i][1], max(HUE[i][0], H + random.gauss(0, 2))))
        qc = [from_oklch(L, C, H % 360) for L, C, H in q]
        if not ok(qc, theme): continue
        s = worst(qc)
        if s >= best: p, best, cols = q, s, qc
    res[theme] = dict(worst=round(best, 2), cols=cols, lch=[fmt_lch(c) for c in cols], report=report(cols, 3))
    print(theme, round(best, 2), cols, [fmt_lch(c) for c in cols]); print(res[theme]['report'])
json.dump(res, open('out/workplaces_refined.json', 'w'), indent=1)
