"""Draws shots/agents/palette-current-vs-proposed.png: current vs proposed, light and dark, with a mini UI mock,
neutral ramp, semantic colours, workplace gradients and CVD strips."""
import os
from PIL import Image, ImageDraw, ImageFont
from colorlib import *
import tokens_current as C
import tokens_proposed as P

F = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'; FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
f11, f13, f16, f22, f30 = (ImageFont.truetype(F, s) for s in (11, 13, 16, 22, 30))
b13, b16, b22 = (ImageFont.truetype(FB, s) for s in (13, 16, 22))
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'shots', 'agents', 'palette-current-vs-proposed.png')

def rgb(h, over=None):
    if isinstance(h, tuple):
        return tuple(round(x) for x in composite(hex2rgb(h[0])[:3] + (h[1],), hex2rgb(over))[:3])
    c = hex2rgb(h)
    if c[3] < 1 and over: c = composite(c, hex2rgb(over))
    return tuple(round(x) for x in c[:3])

def grad(d, box, a, b):
    x0, y0, x1, y1 = box
    ca, cb = rgb(a), rgb(b)
    for x in range(x0, x1):
        t = (x - x0) / max(1, x1 - x0 - 1)
        d.line([(x, y0), (x, y1)], fill=tuple(round(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)))

def panel(title, t, wps, dark, proposed):
    W, H = 760, 980
    g = lambda k: t[k]
    if proposed:
        T = dict(bg=g('bg'), surface=g('surface'), raised=g('surfaceRaised'), fill=g('fill'), line=g('line'), lineStrong=g('lineStrong'),
                 ink=g('ink'), muted=g('ink2'), subtle=g('ink3'), success=g('success'), warning=g('warning'), danger=g('danger'),
                 warnSoft=g('warningSoft'), primary=g('primary'), onPrimary=g('onPrimary'), accent=g('accent'), accentInk=g('accentInk'),
                 nav=g('nav'), navOn=g('navOn'), navOnInk=g('navOnInk'), navInk=g('navInk'), bar=g('bar'), onBar=g('onBar'),
                 selBg=g('selectedBg'), selEdge=g('selectedEdge'), track=g('track'), info=g('info'))
    else:
        T = dict(bg=g('bg'), surface=g('surface'), raised=g('surface'), fill=g('sunk'), line=g('line'), lineStrong=g('line'),
                 ink=g('ink'), muted=g('muted'), subtle=g('muted'), success=g('paid'), warning=g('pending'), danger=g('danger'),
                 warnSoft=g('pendingSoft'), primary=g('ink'), onPrimary=g('onInk'), accent=g('glow'), accentInk='#FFFFFF',
                 nav=g('nav'), navOn=g('navOn'), navOnInk=g('navOnInk'), navInk=g('navInk'), bar=g('bar'), onBar=g('onBar'),
                 selBg=(g('ink') if dark else g('surface')), selEdge=g('ink'), track=g('sunk'), info=g('glow'))
    bg = T['bg']
    im = Image.new('RGB', (W, H), rgb(bg)); d = ImageDraw.Draw(im)
    label = rgb(T['ink']); mut = rgb(T['muted'])
    d.text((24, 18), title, font=b22, fill=label)
    # --- mini UI mock ---
    d.rounded_rectangle((24, 60, 736, 300), 22, fill=rgb(T['surface']), outline=rgb(T['line']))
    d.text((48, 78), 'This week', font=f13, fill=mut)
    d.text((48, 96), 'ILS 3,070.75', font=f30, fill=label)
    d.text((48, 138), 'paid  ILS 1,200', font=b16, fill=rgb(T['success']))
    d.text((230, 138), 'delete', font=b16, fill=rgb(T['danger']))
    d.text((340, 138), 'info', font=b16, fill=rgb(T['info']))
    d.rounded_rectangle((48, 172, 330, 210), 19, fill=rgb(T['warnSoft']))
    d.text((66, 182), '1 shift awaits a rate', font=b13, fill=rgb(T['warning']))
    # segmented control
    d.rounded_rectangle((360, 172, 712, 210), 12, fill=rgb(T['track']))
    d.rounded_rectangle((364, 176, 480, 206), 10, fill=rgb(T['selBg']), outline=rgb(T['selEdge']), width=2)
    selInk = T['ink'] if proposed or not dark else T['onPrimary']
    d.text((394, 183), 'Clock', font=b13, fill=rgb(selInk))
    d.text((510, 183), 'Manual', font=f13, fill=mut); d.text((620, 183), 'Job', font=f13, fill=mut)
    d.rounded_rectangle((48, 226, 380, 280), 16, fill=rgb(T['primary'])); d.text((176, 244), 'Save', font=b16, fill=rgb(T['onPrimary']))
    d.rounded_rectangle((392, 226, 560, 280), 16, fill=rgb(T['fill'])); d.text((440, 244), 'Share', font=b16, fill=label)
    d.ellipse((580, 226, 634, 280), fill=rgb(T['accent'])); d.text((598, 236), '+', font=f30, fill=rgb(T['accentInk']))
    # switch
    d.rounded_rectangle((652, 238, 712, 270), 16, fill=rgb(T['lineStrong']))
    d.ellipse((656, 242, 680, 266), fill=(255, 255, 255) if not (proposed and dark) else rgb(T['ink']))
    # --- neutral ramp ---
    y = 320; d.text((24, y), 'Neutrals', font=b16, fill=label); y += 26
    neut = ['bg', 'surface', 'raised', 'fill', 'line', 'lineStrong', 'muted', 'subtle', 'ink']
    sw = 78
    for i, k in enumerate(neut):
        x = 24 + i * (sw + 2)
        col = T[k]; hexs = rgb2hex(rgb(col, bg))
        d.rectangle((x, y, x + sw, y + 64), fill=rgb(col, bg), outline=rgb(T['line']))
        L, Cc, Hh = oklch(hexs)
        d.text((x + 2, y + 68), k, font=f11, fill=label); d.text((x + 2, y + 82), hexs, font=f11, fill=mut)
        d.text((x + 2, y + 96), f'C{Cc:.3f} h{Hh:.0f}' if Cc > 0.002 else 'C0 (grey)', font=f11, fill=mut)
    # --- semantic ---
    y = 476; d.text((24, y), 'Semantic + brand', font=b16, fill=label); y += 26
    sems = [('success', T['success']), ('warning', T['warning']), ('danger', T['danger']), ('info', T['info']), ('accent', T['accent']), ('warnSoft', T['warnSoft'])]
    for i, (k, col) in enumerate(sems):
        x = 24 + i * 118
        d.rounded_rectangle((x, y, x + 110, y + 50), 10, fill=rgb(col, bg))
        r = contrast(col if not isinstance(col, tuple) else rgb2hex(rgb(col, bg)), T['surface'] if not isinstance(T['surface'], tuple) else '#000')
        d.text((x + 2, y + 54), f'{k} {rgb2hex(rgb(col, bg))}', font=f11, fill=label)
        d.text((x + 2, y + 68), f'{r:.2f}:1 on surface', font=f11, fill=mut)
    # --- workplaces ---
    y = 600; d.text((24, y), 'Workplaces  (top: light from->to gradient, bottom: dark accent)', font=b16, fill=label); y += 26
    for i, (n, fr, to, on, ad, adi) in enumerate(wps):
        x = 24 + i * 89
        grad(d, (x, y, x + 84, y + 50), fr, to); d.text((x + 6, y + 16), n[:8], font=b13, fill=rgb(on))
        d.rounded_rectangle((x, y + 56, x + 84, y + 96), 8, fill=rgb(ad)); d.text((x + 6, y + 68), n[:8], font=b13, fill=rgb(adi))
    # CVD strips
    y = 760
    d.text((24, y), 'CVD simulation of workplace colours (light from / dark accent)', font=b16, fill=label); y += 24
    for kind in ('protan', 'deutan', 'tritan'):
        d.text((24, y + 6), kind, font=f13, fill=mut)
        for i, w in enumerate(wps):
            x = 100 + i * 40
            d.rectangle((x, y, x + 36, y + 26), fill=rgb(cvd(w[1], kind)))
            d.rectangle((x + 340, y, x + 376, y + 26), fill=rgb(cvd(w[4], kind)))
        y += 32
    # nav pill
    y = 900
    d.rounded_rectangle((24, y, 420, y + 60), 30, fill=rgb(T['nav'], bg), outline=rgb(T['line']))
    d.rounded_rectangle((30, y + 6, 140, y + 54), 24, fill=rgb(T['navOn'])); d.text((60, y + 20), 'Home', font=b13, fill=rgb(T['navOnInk']))
    d.text((170, y + 20), 'History', font=f13, fill=rgb(T['navInk'])); d.text((280, y + 20), 'Settings', font=f13, fill=rgb(T['navInk']))
    d.rounded_rectangle((440, y, 736, y + 60), 30, fill=rgb(T['bar'], bg)); d.text((470, y + 20), 'Shift deleted   UNDO', font=b13, fill=rgb(T['onBar']))
    return im

wp_cur = C.WORKPLACES; wp_new = P.workplaces()
panels = [panel('CURRENT  - Light', C.LIGHT, wp_cur, False, False), panel('PROPOSED - Light', P.LIGHT, wp_new, False, True),
          panel('CURRENT  - Dark (pure grey)', C.DARK, wp_cur, True, False), panel('PROPOSED - Dark (hue 264 tinted)', P.DARK, wp_new, True, True)]
W, H = panels[0].size
sheet = Image.new('RGB', (W * 2 + 30, H * 2 + 90), (40, 40, 44))
d = ImageDraw.Draw(sheet)
d.text((20, 20), 'Kis palette - current vs proposed (left: current, right: proposed). Generated by tools/agent-color/swatches.py', font=b16, fill=(240, 240, 240))
for i, p in enumerate(panels):
    sheet.paste(p, (10 + (i % 2) * (W + 10), 60 + (i // 2) * (H + 10)))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
sheet.save(OUT); print(OUT, sheet.size)
