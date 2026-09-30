import sys; sys.path.insert(0,'tools')
import orb_hostiles as H
from PIL import Image, ImageDraw
orb=Image.open('assets/sprites/player/idle_01.png').convert('RGBA')
order=["idle","move","charge","attack","hurt","death","appear","glitch"]
Z=3
names=sys.argv[1:] or ["triangle","square","diamond","hexagon"]
for bg,tag in (((28,22,64,255),'roxo'),((214,210,226,255),'claro')):
    rows=[]
    for n in names:
        an=H.build(n)
        for k in order: rows.append((n,k,an[k]))
    W=260+(73*Z+6)*8; Hh=len(rows)*(73*Z+8)+10
    s=Image.new('RGBA',(W,Hh),bg); d=ImageDraw.Draw(s); y=6
    fg=(255,255,255,255) if tag=='roxo' else (0,0,0,255)
    for n,k,frames in rows:
        d.text((6,y+8),f"{n} {k}",fill=fg)
        if k=="idle":
            o=orb.resize((32*Z,32*Z),Image.NEAREST); s.alpha_composite(o,(150,y+73*Z-32*Z-8))
        x=260
        for f in frames:
            im=Image.fromarray(f,'RGBA').resize((73*Z,73*Z),Image.NEAREST); s.alpha_composite(im,(x,y)); x+=73*Z+6
        y+=73*Z+8
    s.save(f'tmp/c_{tag}.png')
