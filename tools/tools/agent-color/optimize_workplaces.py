"""Random-search tuning of the 8 workplace hues inside their named families (slot order/identity preserved, so the
stored colour index of every place keeps meaning). Objective: maximise the worst CIEDE2000 distance between any two
slots under normal vision + protan/deutan/tritan simulation, with hard contrast constraints and a chroma-balance term."""
import random, itertools, json, sys
from colorlib import *

FAM = [  # name, H range, light L range, dark L range
    ('indigo', (266, 280), (0.46, 0.54), (0.66, 0.74)),
    ('green', (145, 165), (0.48, 0.58), (0.74, 0.84)),
    ('rose', (352, 368), (0.52, 0.62), (0.68, 0.78)),
    ('mustard', (72, 88), (0.78, 0.84), (0.82, 0.88)),
    ('purple', (296, 318), (0.48, 0.60), (0.68, 0.78)),
    ('blue', (236, 256), (0.50, 0.60), (0.70, 0.80)),
    ('teal', (186, 210), (0.44, 0.54), (0.72, 0.82)),
    ('burgundy', (8, 26), (0.38, 0.48), (0.60, 0.70)),
]
CMIN = 0.13
VIS = ('normal', 'protan', 'deutan', 'tritan')

def build(params):
    return [from_oklch(L, C, H % 360) for (L, C, H) in params]

def score(cols, dark, surf):
    worst = 1e9
    sims = {v: (cols if v == 'normal' else [cvd(c, v) for c in cols]) for v in VIS}
    for v in VIS:
        for a, b in itertools.combinations(range(len(cols)), 2):
            worst = min(worst, ciede2000(sims[v][a], sims[v][b]) * (1.0 if v == 'normal' else 1.4))
    pen = 0
    for i, c in enumerate(cols):
        if dark:
            if contrast(c, surf) < 4.6: pen += 50
        else:
            req_on = '#1A1200' if FAM[i][0] == 'mustard' else '#FFFFFF'
            if contrast(req_on, c) < (7.0 if FAM[i][0] == 'mustard' else 4.6): pen += 50
    chroma = [oklch(c)[1] for i, c in enumerate(cols) if FAM[i][0] != 'mustard']
    pen += 25 * (max(chroma) - min(chroma)) - 20 * sum(chroma) / len(chroma)
    return worst - pen

def run(dark, seed=1, iters=6000):
    random.seed(seed)
    surf = '#17191F'
    Lr = 3 if dark else 2
    p = [((f[Lr][0] + f[Lr][1]) / 2, 0.16, (f[1][0] + f[1][1]) / 2) for f in FAM]
    best = score(build(p), dark, surf)
    for it in range(iters):
        q = list(p); i = random.randrange(8); f = FAM[i]
        L, C, H = q[i]
        L = min(f[Lr][1], max(f[Lr][0], L + random.gauss(0, 0.02)))
        C = min(0.24, max(CMIN, C + random.gauss(0, 0.015)))
        H = min(f[1][1], max(f[1][0], H + random.gauss(0, 3)))
        q[i] = (L, C, H)
        s = score(build(q), dark, surf)
        if s >= best: p, best = q, s
    return p, best

if __name__ == '__main__':
    out = {}
    for dark in (False, True):
        bp, bs = None, -1e9
        for seed in range(4):
            p, s = run(dark, seed)
            if s > bs: bp, bs = p, s
        cols = build(bp)
        out['dark' if dark else 'light'] = dict(score=bs, cols=cols, lch=[fmt_lch(c) for c in cols])
        print('dark' if dark else 'light', round(bs, 2), cols)
    json.dump(out, open('out/workplaces_opt.json', 'w'), indent=1)
