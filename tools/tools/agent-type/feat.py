import sys,glob,os
sys.path.insert(0,'/home/user/Salary-calculation/ux-audit/tools/agent-type/py')
from fontTools.ttLib import TTFont
for p in sys.argv[1:]:
  f=TTFont(p); cm=f.getBestCmap(); hm=f['hmtx']
  feats=sorted({r.FeatureTag for r in f['GSUB'].table.FeatureList.FeatureRecord}) if 'GSUB' in f else []
  digs=[hm[cm[ord(c)]][0] for c in '0123456789' if ord(c) in cm]
  upm=f['head'].unitsPerEm
  axes=[(a.axisTag,a.minValue,a.maxValue) for a in f['fvar'].axes] if 'fvar' in f else None
  sh=cm.get(0x20AA); hb=cm.get(0x05D0)
  os2=f['OS/2']
  print(os.path.basename(p), os.path.getsize(p)//1024,'KB upm',upm,'axes',axes,'feats',[x for x in feats if x in('tnum','pnum','lnum','onum','zero','case','ss01','ss02','kern')],'digits',set(digs),'₪',sh is not None, 'א',hb is not None,'xH',getattr(os2,'sxHeight',None),'capH',getattr(os2,'sCapHeight',None), 'nglyphs',len(f.getGlyphOrder()))
