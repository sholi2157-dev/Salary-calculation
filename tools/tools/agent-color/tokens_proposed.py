"""Proposed 'Kis 2' semantic tokens. Every value is defined in OKLCH (hue 264 neutral family shared by both themes)
and converted to sRGB hex. Alpha tokens are (hex, alpha)."""
from colorlib import from_oklch as K

NH = 264  # neutral hue shared by light and dark
LIGHT = {
    # neutrals
    'bg': '#F1F3F8', 'surface': '#FFFFFF', 'surfaceRaised': '#FFFFFF', 'fill': '#E4E8F0',
    'line': '#D2D8E3', 'lineStrong': K(0.60, 0.030, NH),
    'ink': '#1B2335', 'ink2': '#5A6478', 'ink3': K(0.530, 0.030, NH), 'disabledInk': K(0.72, 0.020, NH), 'disabledBg': '#E4E8F0',
    # brand
    'accent': '#5145E8', 'accentInk': '#FFFFFF', 'accentSoft': K(0.945, 0.030, 277), 'accentText': '#5145E8', 'focus': '#5145E8',
    # semantic
    'success': K(0.495, 0.104, 165), 'successSoft': K(0.945, 0.040, 165), 'onSuccess': '#FFFFFF',
    'warning': K(0.515, 0.123, 58), 'warningSoft': '#FFEBC7', 'onWarning': '#FFFFFF',
    'danger': K(0.528, 0.180, 28), 'dangerSoft': K(0.945, 0.030, 25), 'onDanger': '#FFFFFF',
    'info': K(0.510, 0.150, 255), 'infoSoft': K(0.945, 0.025, 255),
    # interaction
    'selectedBg': '#FFFFFF', 'selectedInk': '#1B2335', 'selectedEdge': '#1B2335', 'track': '#E4E8F0',
    'controlOn': '#1B2335', 'controlKnob': '#FFFFFF',
    'primary': '#1B2335', 'onPrimary': '#FFFFFF',
    'pressed': ('#1B2335', 0.10), 'hover': ('#1B2335', 0.05),
    # overlays
    'scrim': (K(0.15, 0.02, NH), 0.45), 'nav': ('#FFFFFF', 0.90), 'navInk': '#465066', 'navOn': '#1B2335', 'navOnInk': '#FFFFFF',
    'bar': ('#FFFFFF', 0.95), 'onBar': '#1B2335', 'barAction': '#5145E8',
    'live': '#1B2335', 'onLive': '#FFFFFF', 'glow': ('#5145E8', 0.12),
}
DARK = {
    'bg': K(0.180, 0.010, NH), 'surface': K(0.220, 0.012, NH), 'surfaceRaised': K(0.262, 0.014, NH), 'fill': K(0.292, 0.016, NH),
    'line': K(0.335, 0.016, NH), 'lineStrong': K(0.565, 0.018, NH),
    'ink': K(0.955, 0.008, NH), 'ink2': K(0.785, 0.018, NH), 'ink3': K(0.690, 0.020, NH), 'disabledInk': K(0.500, 0.015, NH), 'disabledBg': K(0.262, 0.014, NH),
    'accent': K(0.720, 0.150, 280), 'accentInk': K(0.220, 0.070, 280), 'accentSoft': K(0.320, 0.070, 280), 'accentText': K(0.760, 0.130, 280), 'focus': K(0.760, 0.130, 280),
    'success': K(0.770, 0.140, 165), 'successSoft': K(0.300, 0.050, 165), 'onSuccess': K(0.200, 0.040, 165),
    'warning': K(0.820, 0.130, 74), 'warningSoft': K(0.305, 0.050, 74), 'onWarning': K(0.210, 0.045, 74),
    'danger': K(0.770, 0.135, 28), 'dangerSoft': K(0.300, 0.060, 25), 'onDanger': K(0.210, 0.050, 28),
    'info': K(0.775, 0.110, 250), 'infoSoft': K(0.300, 0.050, 250),
    'selectedBg': K(0.400, 0.020, NH), 'selectedInk': K(0.955, 0.008, NH), 'selectedEdge': K(0.955, 0.008, NH), 'track': K(0.292, 0.016, NH),
    'controlOn': K(0.955, 0.008, NH), 'controlKnob': K(0.180, 0.010, NH),
    'primary': K(0.955, 0.008, NH), 'onPrimary': K(0.180, 0.010, NH),
    'pressed': (K(0.955, 0.008, NH), 0.12), 'hover': (K(0.955, 0.008, NH), 0.07),
    'scrim': ('#000000', 0.62), 'nav': (K(0.262, 0.014, NH), 0.92), 'navInk': K(0.785, 0.018, NH), 'navOn': K(0.955, 0.008, NH), 'navOnInk': K(0.180, 0.010, NH),
    'bar': K(0.315, 0.016, NH), 'onBar': K(0.955, 0.008, NH), 'barAction': K(0.800, 0.110, 280),
    'live': K(0.315, 0.016, NH), 'onLive': K(0.955, 0.008, NH), 'glow': (K(0.720, 0.150, 280), 0.24),
}
# Workplaces: slot order and names unchanged (stored colour index keeps meaning). light `from` / `to` / on ; dark accent / ink.
WP_LIGHT = [(.46, .21, 273), (.515, .165, 143), (.54, .215, 357), None, (.59, .225, 306), (.54, .12, 237), (.44, .075, 201), (.405, .16, 16)]
WP_DARK = [(.71, .15, 270), (.83, .18, 156), (.73, .17, 359), (.856, .165, 86), (.68, .15, 327), (.81, .10, 247), (.68, .115, 187), (.665, .185, 20)]
WP_NAMES = ['indigo', 'green', 'rose', 'mustard', 'purple', 'blue', 'teal', 'burgundy']

def workplaces():
    out = []
    for i, n in enumerate(WP_NAMES):
        if WP_LIGHT[i] is None:   # mustard keeps its identity colour; dark ink on it
            fr, to, on = '#F5B82E', K(0.700, 0.140, 74), '#1A1200'
        else:
            L, C, H = WP_LIGHT[i]
            fr, to, on = K(L, C, H % 360), K(L - 0.20, C * 0.72, H % 360), '#FFFFFF'
        L, C, H = WP_DARK[i]
        ad, adi = K(L, C, H % 360), K(0.200, 0.045, H % 360)
        out.append((n, fr, to, on, ad, adi))
    return out
