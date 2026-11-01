import sys
from PIL import Image, ImageDraw
out=sys.argv[1]; files=sys.argv[2:]; H=900
ims=[]
for f in files:
    im=Image.open(f).convert('RGB'); r=H/im.height; im=im.resize((int(im.width*r),H)); d=ImageDraw.Draw(im); d.rectangle([0,0,im.width,22],fill='yellow'); d.text((4,4),f.split('/')[-1][4:],fill='black'); ims.append(im)
W=sum(i.width for i in ims)+10*len(ims); g=Image.new('RGB',(W,H),'white'); x=0
for i in ims: g.paste(i,(x,0)); x+=i.width+10
g.save(out)
