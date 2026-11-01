import itertools, sys
from colorlib import *
VIS = ('normal', 'protan', 'deutan', 'tritan')
NAMES = ['indigo', 'green', 'rose', 'mustard', 'purple', 'blue', 'teal', 'burgundy']
def report(cols, k=3):
    out = {}
    for v in VIS:
        s = cols if v == 'normal' else [cvd(c, v) for c in cols]
        ps = sorted((ciede2000(s[a], s[b]), NAMES[a], NAMES[b]) for a, b in itertools.combinations(range(8), 2))
        out[v] = [(round(d, 1), a, b) for d, a, b in ps[:k]]
    return out
def lchset(spec):
    return [from_oklch(*x) for x in spec]
if __name__ == '__main__':
    from tokens_current import WORKPLACES
    print('current from', report([w[1] for w in WORKPLACES]))
    print('current aDk ', report([w[4] for w in WORKPLACES]))
