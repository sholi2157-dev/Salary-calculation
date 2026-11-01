"""Self-contained colour maths: WCAG 2.x contrast, alpha compositing, OKLab/OKLCH, CIELAB + CIEDE2000,
Machado 2009 CVD simulation (severity 1.0, applied in linear sRGB). No third-party deps."""
import math

def hex2rgb(h):
    h = h.strip().lstrip('#')
    if len(h) == 3: h = ''.join(c * 2 for c in h)
    if len(h) == 8:  # Android ARGB (AARRGGBB) is converted by callers; here RRGGBBAA (CSS)
        return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (int(h[6:8], 16) / 255,)
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (1.0,)

def rgb2hex(rgb):
    return '#' + ''.join(f'{max(0, min(255, round(c))):02X}' for c in rgb[:3])

def composite(fg, bg):
    """fg may carry alpha (r,g,b,a); bg must be opaque."""
    a = fg[3] if len(fg) > 3 else 1.0
    return tuple(fg[i] * a + bg[i] * (1 - a) for i in range(3)) + (1.0,)

def s2l(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def l2s(c):
    c = max(0.0, min(1.0, c))
    return 255 * (12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055)

def lum(rgb):
    r, g, b = (s2l(c) for c in rgb[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def contrast(a, b):
    """WCAG contrast ratio; a may be translucent and is composited over b."""
    if isinstance(a, str): a = hex2rgb(a)
    if isinstance(b, str): b = hex2rgb(b)
    if len(a) > 3 and a[3] < 1: a = composite(a, b)
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

# ---- OKLab ----
def rgb2oklab(rgb):
    r, g, b = (s2l(c) for c in rgb[:3])
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l, m, s = (math.copysign(abs(x) ** (1 / 3), x) for x in (l, m, s))
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)

def oklab2rgb(L, a, b, clip=True):
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    lin = (r, g, bb)
    if not clip and any(x < -1e-4 or x > 1 + 1e-4 for x in lin): return None
    return tuple(l2s(x) for x in lin)

def oklch(h):
    rgb = hex2rgb(h) if isinstance(h, str) else h
    L, a, b = rgb2oklab(rgb)
    C = math.hypot(a, b)
    H = math.degrees(math.atan2(b, a)) % 360
    return L, C, H

def from_oklch(L, C, H):
    """Gamut-map by reducing chroma until in sRGB."""
    while C >= 0:
        a, b = C * math.cos(math.radians(H)), C * math.sin(math.radians(H))
        rgb = oklab2rgb(L, a, b, clip=False)
        if rgb: return rgb2hex(rgb)
        C -= 0.002
    return rgb2hex(oklab2rgb(L, 0, 0))

def de_ok(x, y):
    a, b = rgb2oklab(hex2rgb(x) if isinstance(x, str) else x), rgb2oklab(hex2rgb(y) if isinstance(y, str) else y)
    return 100 * math.dist(a, b)

# ---- CIELAB / CIEDE2000 ----
def rgb2lab(rgb):
    r, g, b = (s2l(c) for c in rgb[:3])
    X = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    Y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b)
    Z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    f = lambda t: t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116
    fx, fy, fz = f(X), f(Y), f(Z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)

def ciede2000(c1, c2):
    if isinstance(c1, str): c1 = hex2rgb(c1)
    if isinstance(c2, str): c2 = hex2rgb(c2)
    L1, a1, b1 = rgb2lab(c1); L2, a2, b2 = rgb2lab(c2)
    C1, C2 = math.hypot(a1, b1), math.hypot(a2, b2)
    Cb = (C1 + C2) / 2
    G = 0.5 * (1 - math.sqrt(Cb ** 7 / (Cb ** 7 + 25 ** 7)))
    a1p, a2p = (1 + G) * a1, (1 + G) * a2
    C1p, C2p = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1p = math.degrees(math.atan2(b1, a1p)) % 360 if C1p else 0
    h2p = math.degrees(math.atan2(b2, a2p)) % 360 if C2p else 0
    dLp, dCp = L2 - L1, C2p - C1p
    dh = h2p - h1p
    if C1p * C2p == 0: dh = 0
    elif dh > 180: dh -= 360
    elif dh < -180: dh += 360
    dHp = 2 * math.sqrt(C1p * C2p) * math.sin(math.radians(dh / 2))
    Lbp, Cbp = (L1 + L2) / 2, (C1p + C2p) / 2
    if C1p * C2p == 0: hbp = h1p + h2p
    elif abs(h1p - h2p) <= 180: hbp = (h1p + h2p) / 2
    elif h1p + h2p < 360: hbp = (h1p + h2p + 360) / 2
    else: hbp = (h1p + h2p - 360) / 2
    T = (1 - 0.17 * math.cos(math.radians(hbp - 30)) + 0.24 * math.cos(math.radians(2 * hbp))
         + 0.32 * math.cos(math.radians(3 * hbp + 6)) - 0.20 * math.cos(math.radians(4 * hbp - 63)))
    dth = 30 * math.exp(-((hbp - 275) / 25) ** 2)
    Rc = 2 * math.sqrt(Cbp ** 7 / (Cbp ** 7 + 25 ** 7))
    Sl = 1 + 0.015 * (Lbp - 50) ** 2 / math.sqrt(20 + (Lbp - 50) ** 2)
    Sc, Sh = 1 + 0.045 * Cbp, 1 + 0.015 * Cbp * T
    Rt = -math.sin(math.radians(2 * dth)) * Rc
    return math.sqrt((dLp / Sl) ** 2 + (dCp / Sc) ** 2 + (dHp / Sh) ** 2 + Rt * (dCp / Sc) * (dHp / Sh))

# ---- CVD (Machado, Oliveira & Fernandes 2009, severity 1.0) ----
MACHADO = {
    'protan': ((0.152286, 1.052583, -0.204868), (0.114503, 0.786281, 0.099216), (-0.003882, -0.048116, 1.051998)),
    'deutan': ((0.367322, 0.860646, -0.227968), (0.280085, 0.672501, 0.047413), (-0.011820, 0.042940, 0.968881)),
    'tritan': ((1.255528, -0.076749, -0.178779), (-0.078411, 0.930809, 0.147602), (0.004733, 0.691367, 0.303900)),
}

def cvd(h, kind):
    rgb = hex2rgb(h) if isinstance(h, str) else h
    lin = [s2l(c) for c in rgb[:3]]
    M = MACHADO[kind]
    out = [sum(M[i][j] * lin[j] for j in range(3)) for i in range(3)]
    return rgb2hex([l2s(x) for x in out])

def argb(a):
    """Android 0xAARRGGBB literal string -> CSS #RRGGBBAA."""
    a = a.upper().replace('0X', '').lstrip('#')
    return '#' + a[2:] + a[:2] if len(a) == 8 else '#' + a

def fmt_lch(h):
    L, C, H = oklch(h)
    return f'oklch({L:.3f} {C:.3f} {H:.0f})'
