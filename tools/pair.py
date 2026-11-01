import sys, glob, os
from PIL import Image, ImageDraw
src, out = sys.argv[1], sys.argv[2]; os.makedirs(out, exist_ok=True)
names = sorted({os.path.basename(f)[len('light-'):] for f in glob.glob(src + '/light-*.png')})
for n in names:
    ims = [Image.open(f'{src}/{s}-{n}') for s in ('light','dark') if os.path.exists(f'{src}/{s}-{n}')]
    if 'full' in n: continue
    ims = [i.resize((i.width//2, i.height//2)) for i in ims]
    w = sum(i.width for i in ims) + 10*(len(ims)-1); h = max(i.height for i in ims)
    c = Image.new('RGB', (w, h), (128,128,128)); x = 0
    for i in ims: c.paste(i, (x, 0)); x += i.width + 10
    c.save(f'{out}/{n}')
print(len(names))
