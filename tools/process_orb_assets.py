from PIL import Image, ImageEnhance, ImageDraw, ImageFilter
import argparse
import numpy as np, json
from pathlib import Path

PROJ = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description='Build original or remade Project ORB assets')
parser.add_argument('--remade', action='store_true', help='use source_remade and stage in processed_remade')
args = parser.parse_args()
SOURCE_NAME = 'source_remade' if args.remade else 'source_final'
OUTPUT_NAME = 'processed_remade' if args.remade else 'processed'
SRC_ROOT = PROJ / 'assets' / SOURCE_NAME
OUT = PROJ / 'assets' / OUTPUT_NAME
if not (SRC_ROOT / 'characters/player/orb_roxa_spritesheet.png').is_file():
    raise FileNotFoundError(f'Missing original sprite sheets: {SRC_ROOT}')
OUT.mkdir(parents=True, exist_ok=True)


def global_bg(im):
    a=np.array(im.convert('RGB'))
    strips=np.concatenate([
        a[:16].reshape(-1,3), a[-16:].reshape(-1,3),
        a[:,:16].reshape(-1,3), a[:,-16:].reshape(-1,3)
    ],axis=0)
    return np.median(strips,axis=0)


def remove_dark_bg(crop, bg):
    arr=np.array(crop.convert('RGBA'))
    rgb=arr[...,:3].astype(np.float32)
    mx=rgb.max(axis=2); mn=rgb.min(axis=2); sat=mx-mn
    dist=np.linalg.norm(rgb-bg.reshape(1,1,3),axis=2)
    # foreground seed. Dark outlines are recovered by dilation.
    seed=((dist>27) & ((mx>45)|(sat>24))).astype(np.uint8)*255
    # Keep the pixel-art silhouette and dark outline without an OpenCV dependency.
    mask=Image.fromarray(seed,'L').filter(ImageFilter.MaxFilter(5))
    arr[...,3]=np.asarray(mask)
    return Image.fromarray(arr,'RGBA')


def clean_generated_alpha(crop, actor=None):
    """Preserve generated transparency while dropping low-alpha color fringes."""
    arr=np.array(crop.convert('RGBA'))
    alpha=arr[...,3]
    alpha[alpha<140]=0
    alpha[alpha>=140]=255
    if actor == 'enemies/diamond':
        rgb=arr[...,:3]
        red_noise=(rgb[...,0]>rgb[...,1]*1.42) & (rgb[...,0]>80)
        alpha[red_noise]=0
    arr[...,3]=alpha
    return Image.fromarray(arr,'RGBA')


def trim(im, pad=3):
    bbox=im.getchannel('A').getbbox()
    if not bbox: return im
    l,t,r,b=bbox
    return im.crop((max(0,l-pad),max(0,t-pad),min(im.width,r+pad),min(im.height,b+pad)))


def place_canvas(im, canvas=(224,224), bottom_pad=12, center_x=True):
    im=trim(im)
    cw,ch=canvas
    maxw,maxh=cw-10,ch-10
    if im.width>maxw or im.height>maxh:
        scale=min(maxw/im.width,maxh/im.height)
        im=im.resize((max(1,round(im.width*scale)),max(1,round(im.height*scale))),Image.Resampling.NEAREST)
    out=Image.new('RGBA',canvas,(0,0,0,0))
    x=(cw-im.width)//2 if center_x else 5
    y=max(2,ch-bottom_pad-im.height)
    out.alpha_composite(im,(x,y))
    return out


def extract_sheet(sheet_path, animations, dest_base, canvas=(224,224)):
    src=Image.open(sheet_path)
    has_alpha=src.mode=='RGBA' and src.getchannel('A').getextrema()[0]<255
    bg=global_bg(src)
    result={}
    for anim, spec in animations.items():
        boxes=spec['boxes']
        duration=spec.get('duration',0.1)
        anim_canvas=spec.get('canvas',canvas)
        outdir=OUT/dest_base/anim
        outdir.mkdir(parents=True,exist_ok=True)
        paths=[]
        for idx,box in enumerate(boxes,1):
            # The boss orb row overlaps adjacent poses, so its projectiles are
            # rendered by gameplay. Use the clean charging poses for its body.
            if not args.remade and str(dest_base) == 'boss' and anim == 'orbs':
                box = boss['powerup']['boxes'][idx - 1]
            min_top = {
                'enemies/triangle': {'walk': 255, 'dash': 455},
                'enemies/square': {'walk': 260, 'dash': 450, 'hurt': 840, 'death': 1050},
                'enemies/diamond': {'idle': 60, 'hover': 255, 'dash': 460, 'hurt': 850, 'death': 1050},
                'enemies/hexagon': {'walk': 270, 'dash': 455, 'hurt': 850, 'death': 1050},
            }.get(str(dest_base).replace('\\', '/'), {}).get(anim)
            if anim == 'hover' and str(dest_base).replace('\\', '/') == 'enemies/diamond' and idx > 1:
                min_top = None
            if min_top is not None:
                box = (box[0], max(box[1], min_top), box[2], box[3])
            crop=src.crop(box)
            cut=clean_generated_alpha(crop, str(dest_base).replace('\\','/')) if has_alpha else remove_dark_bg(crop,bg)
            out=place_canvas(cut,anim_canvas,bottom_pad=spec.get('bottom_pad',12))
            if args.remade and str(dest_base).replace('\\','/') == 'player/orb' and anim == 'attack' and idx == 4:
                # The source sheet places the finishing bolt in a separate cell.
                bolt=trim(clean_generated_alpha(src.crop((760,645,910,790))),1)
                bolt=bolt.resize((48,48),Image.Resampling.NEAREST)
                out.alpha_composite(bolt,(162,104))
            if args.remade and str(dest_base).replace('\\','/') == 'enemies/hexagon' and anim == 'attack' and idx == 4:
                # Keep the body while the projectile leaves the right-hand side.
                orb=trim(clean_generated_alpha(src.crop((785,665,945,795))),1)
                orb=orb.resize((45,45),Image.Resampling.NEAREST)
                out.alpha_composite(orb,(170,110))
            fname=f'{anim}_{idx:02d}.png'
            out.save(outdir/fname)
            paths.append(str((Path(OUTPUT_NAME)/dest_base/anim/fname).as_posix()))
        # horizontal strip for inspection / alternate engine use
        strip=Image.new('RGBA',(anim_canvas[0]*len(paths),anim_canvas[1]),(0,0,0,0))
        for i,pth in enumerate(paths):
            fr=Image.open(PROJ/'assets'/pth).convert('RGBA')
            strip.alpha_composite(fr,(i*anim_canvas[0],0))
        strip_name=f'{anim}_sheet.png'
        strip.save(OUT/dest_base/strip_name)
        result[anim]={'frames':paths,'duration':duration,'canvas':list(anim_canvas)}
    return result

# helper: center box

def cb(cx,cy,w,h):
    return (int(cx-w/2),int(cy-h/2),int(cx+w/2),int(cy+h/2))

# Player
player={
'idle': {'duration':0.18,'boxes':[cb(x,138,150,150) for x in [282,468,655,837]]},
'walk': {'duration':0.09,'boxes':[cb(x,322,150,155) for x in [272,435,600,765,938,1098]]},
'dash': {'duration':0.055,'boxes':[cb(275,510,160,165),cb(450,510,205,165),cb(650,508,220,170),cb(850,508,255,170),cb(1090,508,210,170)]},
'attack': {'duration':0.07,'boxes':[(200,635,350,790),(375,605,585,800),(590,635,755,795),(590,635,755,795)] if args.remade else [(205,640,330,770),(355,610,545,770),(560,640,675,770),(1105,640,1225,770)]},
'hurt': {'duration':0.09,'boxes':[cb(x,905,190,190) for x in [275,465,650,850]]},
'death': {'duration':0.12,'boxes':[cb(x,1105,190,205) for x in [270,465,640,815,1000]]},
}
manifest={'player':extract_sheet(SRC_ROOT/'characters/player/orb_roxa_spritesheet.png',player,Path('player/orb'))}

# Triangle
tri={
'idle': {'duration':0.18,'boxes':[cb(x,130,150,150) for x in [340,520,710,895]]},
'walk': {'duration':0.11,'boxes':[cb(x,315,155,155) for x in [190,360,540,715,890,1065]]},
'dash': {'duration':0.06,'boxes':[cb(x,510,200,170) for x in [140,335,535,735,930,1135]]},
'attack': {'duration':0.08,'boxes':[(55,650,185,795),(235,645,410,795),(425,645,590,795),(595,610,785,800),(800,635,1020,795),(1080,650,1215,795)]},
'hurt': {'duration':0.10,'boxes':[cb(x,905,185,180) for x in [335,510,705,890]]},
'death': {'duration':0.12,'boxes':[cb(x,1100,210,200) for x in [310,490,700,920,1110]]},
}
manifest['triangle']=extract_sheet(SRC_ROOT/'characters/enemies/inimigo_triangulo_vermelho_spritesheet.png',tri,Path('enemies/triangle'))

# Square
sq={
'idle': {'duration':0.18,'boxes':[cb(x,137,145,150) for x in [370,540,710,880]]},
'walk': {'duration':0.11,'boxes':[cb(x,323,150,155) for x in [190,360,535,705,880,1055]]},
'dash': {'duration':0.06,'boxes':[cb(x,510,205,170) for x in [210,410,620,850,1050]]},
'attack': {'duration':0.08,'boxes':[(135,650,280,820),(315,630,545,820),(545,650,790,820),(800,650,970,820),(985,650,1160,820)] if args.remade else [(185,640,310,765),(355,625,540,765),(565,640,670,765),(790,640,925,765),(955,640,1080,765)]},
'hurt': {'duration':0.10,'boxes':[cb(x,905,190,185) for x in [330,520,715,910]]},
'death': {'duration':0.12,'boxes':[cb(x,1110,200,205) for x in [180,380,600,835,1065]]},
}
manifest['square']=extract_sheet(SRC_ROOT/'characters/enemies/inimigo_quadrado_azul_spritesheet.png',sq,Path('enemies/square'))

# Diamond
Dia={
'idle': {'duration':0.18,'boxes':[cb(x,125,140,155) for x in [120,275,425,575]]},
'hover': {'duration':0.11,'boxes':[cb(x,320,145,165) for x in [145,305,460,620,780,940,1095]]},
'dash': {'duration':0.06,'boxes':[cb(x,525,200,175) for x in [130,335,535,755,955,1135]]},
'attack': {'duration':0.09,'boxes':[(75,650,185,800),(235,650,395,800),(405,650,585,800),(585,650,790,800),(790,650,1010,800),(1065,650,1220,800)]},
'hurt': {'duration':0.10,'boxes':[(60,850,185,985),(235,850,390,985),(390,850,550,985),(570,850,730,985),(755,850,865,985)]},
'death': {'duration':0.12,'boxes':[cb(x,1110,190,205) for x in [120,290,470,640,820,1000]]},
}
manifest['diamond']=extract_sheet(SRC_ROOT/'characters/enemies/inimigo_diamante_amarelo_spritesheet.png',Dia,Path('enemies/diamond'))

# Hexagon regular
Hex={
'idle': {'duration':0.18,'boxes':[cb(x,132,160,155) for x in [275,500,740,980]]},
'walk': {'duration':0.11,'boxes':[cb(x,325,160,160) for x in [165,350,535,720,905,1095]]},
'dash': {'duration':0.06,'boxes':[cb(x,520,220,175) for x in [160,360,590,840,1090]]},
'attack': {'duration':0.09,'boxes':[(105,650,225,785),(310,650,465,785),(515,650,745,785),(515,650,745,785),(985,650,1150,785)]},
'hurt': {'duration':0.10,'boxes':[cb(x,910,200,185) for x in [260,490,740,970]]},
'death': {'duration':0.12,'boxes':[(90,1050,220,1190),(265,1050,405,1190),(425,1050,600,1190),(610,1050,925,1190),(965,1050,1195,1190)]},
}
manifest['hexagon']=extract_sheet(SRC_ROOT/'characters/enemies/inimigo_hexagono_verde_spritesheet.png',Hex,Path('enemies/hexagon'))

# Boss
boss={
'idle': {'duration':0.16,'canvas':(384,384),'boxes':[(25,40,245,230),(270,40,490,230),(515,40,735,230),(765,40,985,230),(1000,40,1225,230)],'bottom_pad':25},
'powerup': {'duration':0.11,'canvas':(384,384),'boxes':[(25,280,245,465),(270,280,490,465),(515,280,735,465),(765,280,985,465),(1000,280,1225,465)],'bottom_pad':25},
'orbs': {'duration':0.10,'canvas':(384,384),'boxes':[(25,490,225,690),(235,490,465,690),(465,490,720,690),(715,490,970,690),(990,490,1230,690)] if args.remade else [(35,505,205,680),(280,505,425,680),(500,505,640,680),(730,505,870,680),(960,505,1120,680)],'bottom_pad':25},
'beam': {'duration':0.10,'canvas':(384,384),'boxes':[(20,710,250,920),(260,710,500,920),(505,710,745,920),(750,710,995,920),(995,710,1235,920)],'bottom_pad':25},
'enraged': {'duration':0.12,'canvas':(384,384),'boxes':[(20,980,220,1190),(220,980,410,1190),(410,980,610,1190)],'bottom_pad':25},
'death': {'duration':0.14,'canvas':(384,384),'boxes':[(640,980,835,1190),(835,980,1025,1190),(1030,980,1238,1190)],'bottom_pad':25},
}
manifest['boss']=extract_sheet(SRC_ROOT/'boss/chefao_geometrico_spritesheet.png',boss,Path('boss'))

# Environment crop helper
TILE_SRC=Image.open(SRC_ROOT/'environment/tilesets/tileset_plataforma_ruinas_flutuantes.png')
TILE_HAS_ALPHA=TILE_SRC.mode=='RGBA' and TILE_SRC.getchannel('A').getextrema()[0]<255
TBG=global_bg(TILE_SRC)

def crop_tile(name,box,sub='world'):
    crop=TILE_SRC.crop(box)
    cut=clean_generated_alpha(crop) if TILE_HAS_ALPHA else remove_dark_bg(crop,TBG)
    if name == 'portal':
        # A dangling island from the row above overlaps this crop's top-right.
        clean=np.array(cut)
        clean[:18,:,3]=0
        cut=Image.fromarray(clean,'RGBA')
    cut=trim(cut,2)
    d=OUT/sub
    d.mkdir(parents=True,exist_ok=True)
    cut.save(d/f'{name}.png')
    return str((Path(OUTPUT_NAME)/sub/f'{name}.png').as_posix())

world={}
# core tiles
world['ground_grass']=crop_tile('ground_grass',(30,24,121,128),'world/tiles')
world['ground_grass_alt']=crop_tile('ground_grass_alt',(134,24,225,128),'world/tiles')
world['ground_edge_left']=crop_tile('ground_edge_left',(237,25,311,128),'world/tiles')
world['ground_edge_right']=crop_tile('ground_edge_right',(324,25,397,128),'world/tiles')
world['ground_stone']=crop_tile('ground_stone',(30,149,121,242),'world/tiles')
world['ground_stone_moss']=crop_tile('ground_stone_moss',(135,149,225,242),'world/tiles')
world['rune_magenta']=crop_tile('rune_magenta',(30,263,121,355),'world/tiles')
world['rune_blue']=crop_tile('rune_blue',(135,263,225,355),'world/tiles')
world['tech_blue']=crop_tile('tech_blue',(135,378,225,472),'world/tiles')
world['float_island_large']=crop_tile('float_island_large',(904,25,1075,148),'world/tiles')
world['float_platform_magenta']=crop_tile('float_platform_magenta',(905,158,1072,255),'world/tiles')
world['float_platform_blue']=crop_tile('float_platform_blue',(1090,159,1240,255),'world/tiles')
world['float_platform_green']=crop_tile('float_platform_green',(1265,158,1423,255),'world/tiles')
world['spikes_magenta']=crop_tile('spikes_magenta',(30,509,155,573),'world/hazards')
world['spikes_blue']=crop_tile('spikes_blue',(178,509,311,573),'world/hazards')
world['crystals_magenta']=crop_tile('crystals_magenta',(329,478,474,574),'world/decor')
world['crystals_blue']=crop_tile('crystals_blue',(490,477,627,574),'world/decor')
world['flowers']=crop_tile('flowers',(1012,497,1235,576),'world/decor')
world['grass_rocks']=crop_tile('grass_rocks',(1264,477,1426,581),'world/decor')
world['pillar_magenta']=crop_tile('pillar_magenta',(30,599,127,771),'world/decor')
world['pillar_blue']=crop_tile('pillar_blue',(157,599,251,771),'world/decor')
world['ruin_chunk']=crop_tile('ruin_chunk',(289,599,390,770),'world/decor')
world['pillar_ruined']=crop_tile('pillar_ruined',(409,599,507,770),'world/decor')
world['arch_magenta']=crop_tile('arch_magenta',(524,596,709,772),'world/decor')
world['arch_stone']=crop_tile('arch_stone',(732,595,892,772),'world/decor')
world['spring_magenta']=crop_tile('spring_magenta',(930,634,1007,713),'world/props')
world['spring_blue']=crop_tile('spring_blue',(1030,634,1107,690),'world/props')
world['checkpoint']=crop_tile('checkpoint',(1107,600,1203,773),'world/props')
world['portal']=crop_tile('portal',(1211,590,1423,774),'world/props')
world['suspended_magenta']=crop_tile('suspended_magenta',(29,780,177,898),'world/props')
world['suspended_blue']=crop_tile('suspended_blue',(203,780,359,898),'world/props')
world['suspended_wood']=crop_tile('suspended_wood',(389,780,553,898),'world/props')
world['banner_magenta']=crop_tile('banner_magenta',(572,795,658,906),'world/decor')
world['banner_blue']=crop_tile('banner_blue',(669,795,757,906),'world/decor')
world['ladder']=crop_tile('ladder',(773,794,841,903),'world/props')
world['crate']=crop_tile('crate',(855,809,936,899),'world/props')
world['stone_crate']=crop_tile('stone_crate',(950,809,1031,899),'world/props')
world['barrel']=crop_tile('barrel',(1045,809,1120,899),'world/props')
world['crystal_pedestal']=crop_tile('crystal_pedestal',(1124,808,1174,900),'world/props')
world['sign']=crop_tile('sign',(1187,803,1284,901),'world/props')
world['rocks_large']=crop_tile('rocks_large',(1285,809,1425,901),'world/decor')
world['door_blue']=crop_tile('door_blue',(900,918,1008,1054),'world/props')
world['wall_rune']=crop_tile('wall_rune',(577,918,689,1054),'world/decor')
world['wall_cracked']=crop_tile('wall_cracked',(1013,918,1121,1054),'world/decor')
world['crystal_rock']=crop_tile('crystal_rock',(1125,918,1230,1054),'world/decor')
world['rock_cluster']=crop_tile('rock_cluster',(1235,918,1425,1054),'world/decor')
manifest['world']=world

# background
bgdir=OUT/'background'
bgdir.mkdir(parents=True,exist_ok=True)
bg=Image.open(SRC_ROOT/'environment/backgrounds/background_ruinas_flutuantes_parallax.png').convert('RGB')
bg.save(bgdir/'ruins_full.png')
# darkened copy used behind gameplay for legibility, nearest-resample only if resized later by renderer
ImageEnhance.Brightness(bg).enhance(0.84).save(bgdir/'ruins_gameplay.png')
manifest['background']={'full':f'{OUTPUT_NAME}/background/ruins_full.png','gameplay':f'{OUTPUT_NAME}/background/ruins_gameplay.png'}

uidir=OUT/'ui'; uidir.mkdir(parents=True,exist_ok=True)
def pixel_ring(path,size,outer,inner):
    im=Image.new('RGBA',(size,size),(0,0,0,0)); d=ImageDraw.Draw(im)
    c=size//2
    # stepped/pixel ring using rectangles/lines
    d.rectangle((8,c-2,size-8,c+2),fill=outer)
    d.rectangle((c-2,8,c+2,size-8),fill=outer)
    d.rectangle((16,16,size-16,size-16),outline=outer,width=4)
    d.rectangle((24,24,size-24,size-24),outline=inner,width=3)
    d.rectangle((c-4,c-4,c+4,c+4),fill=inner)
    im.save(path)
if args.remade:
    atlas=Image.open(SRC_ROOT/'effects/effects_atlas.png').convert('RGBA')
    ui_atlas=Image.open(SRC_ROOT/'ui/ui_atlas.png').convert('RGBA')
    for name,box in {
        'menu_frame':(25,48,607,628),
        'button_frame':(642,296,1240,508),
        'hud_frame':(25,805,608,1062),
        'boss_bar_frame':(642,855,1242,1030),
    }.items():
        trim(clean_generated_alpha(ui_atlas.crop(box)),1).save(uidir/f'{name}.png')
    fxdir=OUT/'effects'; fxdir.mkdir(parents=True,exist_ok=True)
    effect_cells={
        'player_shot':(0,0), 'enemy_shot':(1,0), 'boss_shot':(2,0),
        'hit_burst':(0,1), 'dash_trail':(1,1), 'void_burst':(2,1),
        'life_orb':(0,2), 'guide_arrow':(1,2), 'weakpoint_marker':(2,2),
    }
    for name,(col,row) in effect_cells.items():
        cell=atlas.crop((col*341,row*341,(col+1)*341,(row+1)*341))
        sprite=trim(clean_generated_alpha(cell),2)
        target=uidir if name in ('life_orb','guide_arrow','weakpoint_marker') else fxdir
        sprite.save(target/f'{name}.png')
    # Deterministic pixel reticles stay sharp at the small size used in combat.
    aim=Image.new('RGBA',(64,64),(0,0,0,0)); d=ImageDraw.Draw(aim)
    navy=(8,14,44,255); cyan=(111,231,255,255); white=(240,251,255,255)
    for box in [(28,1,35,15),(28,48,35,62),(1,28,15,35),(48,28,62,35)]:
        d.rectangle(box,fill=navy)
    for box in [(30,3,33,14),(30,49,33,60),(3,30,14,33),(49,30,60,33)]:
        d.rectangle(box,fill=cyan)
    d.rectangle((27,27,36,36),fill=navy)
    d.rectangle((30,30,33,33),fill=white)
    aim.save(uidir/'crosshair.png')
    target=Image.new('RGBA',(64,64),(0,0,0,0)); d=ImageDraw.Draw(target)
    d.polygon([(32,3),(61,32),(32,61),(3,32)],fill=(10,18,47,150),outline=navy,width=5)
    d.polygon([(32,10),(54,32),(32,54),(10,32)],outline=(255,211,80,255),width=6)
    d.polygon([(32,20),(44,32),(32,44),(20,32)],outline=white,width=4)
    d.rectangle((29,29,35,35),fill=(255,110,215,255))
    target.save(uidir/'weakpoint_marker.png')
else:
    pixel_ring(uidir/'weakpoint_marker.png',64,(255,72,210,255),(255,238,105,255))
    pixel_ring(uidir/'crosshair.png',48,(210,228,255,235),(82,218,255,255))
    arr=Image.new('RGBA',(48,72),(0,0,0,0));d=ImageDraw.Draw(arr)
    d.polygon([(24,2),(44,28),(32,28),(32,64),(16,64),(16,28),(4,28)],fill=(255,224,80,255),outline=(82,32,100,255))
    arr.save(uidir/'guide_arrow.png')
manifest['ui']={'weakpoint':f'{OUTPUT_NAME}/ui/weakpoint_marker.png','crosshair':f'{OUTPUT_NAME}/ui/crosshair.png','guide_arrow':f'{OUTPUT_NAME}/ui/guide_arrow.png'}

# manifest
(OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')

# preview contact sheet from a handful of processed files
samples=[
('ORB idle',OUT/'player/orb/idle/idle_01.png'),('ORB run',OUT/'player/orb/walk/walk_03.png'),
('ORB dash',OUT/'player/orb/dash/dash_04.png'),('ORB attack',OUT/'player/orb/attack/attack_02.png'),
('Triangle',OUT/'enemies/triangle/idle/idle_01.png'),('Square',OUT/'enemies/square/idle/idle_01.png'),
('Diamond',OUT/'enemies/diamond/hover/hover_02.png'),('Hexagon',OUT/'enemies/hexagon/idle/idle_01.png'),
('Boss',OUT/'boss/idle/idle_01.png'),('Boss beam',OUT/'boss/beam/beam_03.png'),
('Platform',OUT/'world/tiles/ground_grass.png'),('Portal',OUT/'world/props/portal.png'),
]
prev=Image.new('RGB',(1200,860),(10,13,24));dr=ImageDraw.Draw(prev)
dr.text((25,18),'PROJECT ORB - ASSETS PROCESSADOS',fill=(220,225,255))
x,y=25,65
for i,(label,p) in enumerate(samples):
    card=Image.new('RGBA',(270,180),(20,24,42,255))
    a=Image.open(p).convert('RGBA')
    sc=min(210/max(1,a.width),130/max(1,a.height))
    aa=a.resize((max(1,int(a.width*sc)),max(1,int(a.height*sc))),Image.Resampling.NEAREST)
    card.alpha_composite(aa,((270-aa.width)//2,8))
    ImageDraw.Draw(card).text((8,152),label,fill=(220,225,255,255))
    prev.paste(card.convert('RGB'),(x,y))
    x+=290
    if (i+1)%4==0:
        x=25; y+=195
prev.save(PROJ/('ASSETS_REFEITOS_PREVIEW.png' if args.remade else 'ASSETS_PROCESSADOS_PREVIEW.png'))
print('processed',sum(1 for _ in OUT.rglob('*.png')),'PNGs')
