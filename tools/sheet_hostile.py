import sys; sys.path.insert(0,'tools')
from orb_hostiles import build  # folha de contato de um inimigo hostil (uso: py tools/sheet_hostile.py)
from PIL import Image, ImageDraw
an=build("triangle")
orb=Image.open('assets/sprites/player/idle_01.png').convert('RGBA')
Z=4; order=["idle","move","charge","attack","hurt","death","appear","glitch"]
W=16+ (73*Z+8)*8 +200; H=sum(73*Z+30 for _ in order)+20
for bg,nm in (((28,22,64,255),'escuro'),((214,210,226,255),'claro')):
    s=Image.new('RGBA',(W,H),bg); d=ImageDraw.Draw(s); y=10
    for k in order:
        d.text((8,y+4),k,fill=(255,255,255,255) if nm=='escuro' else (0,0,0,255))
        x=200
        if k=="idle":
            o=orb.resize((32*Z,32*Z),Image.NEAREST); s.alpha_composite(o,(70,y+73*Z-32*Z-10*Z))
        for f in an[k]:
            im=Image.fromarray(f,'RGBA').resize((73*Z,73*Z),Image.NEAREST); s.alpha_composite(im,(x,y+20)); x+=73*Z+8
        y+=73*Z+30
    s.save(f'tmp/tri_{nm}.png')
