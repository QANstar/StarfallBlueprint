"""Original 32px pixel art: material identity comes from silhouette, facets and palette.

Only texture PNGs are changed. No recipes, models, machine artwork or worldgen.
Run after the legacy resource generators; also called by each legacy entrypoint.
"""
from pathlib import Path
import hashlib
import random
from PIL import Image, ImageDraw, ImageFont, ImageColor

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/starfall_blueprint/textures'
# shadow, shade, body, light, glint; deliberate material families rather than hash colors.
PALETTES = {
    'meteor': ('#17363f','#236f76','#39b5ae','#9ae7d1','#e5fff0'),
    'titan': ('#402b2c','#89503e','#c57b4b','#eebf86','#fff0ce'),
    'boron': ('#454029','#8a9759','#c1ce88','#e9efbe','#fffde3'),
    'moon': ('#292d50','#555995','#969bdb','#ced7ff','#f5f4ff'),
    'iron': ('#29343e','#556c7c','#94acba','#d0e0e5','#f5ffff'),
    'copper': ('#462926','#8f4931','#d9874c','#f2bb7a','#ffedbb'),
    'steel': ('#222a3a','#384b65','#637b99','#a5bed1','#e5f2ff'),
    'ceramic': ('#534039','#9e7d60','#d8bf91','#f1e1b9','#fff7df'),
    'rubber': ('#222d2a','#3b5144','#72866c','#a6b89a','#dce5c4'),
    'violet': ('#352b49','#625178','#a883b1','#dbc3da','#fff0ed'),
    'carbon': ('#1c2028','#30353d','#505862','#7d8790','#b7c4ce'),
}

# Every currently registered solid material, including reserved registry assets.
SPECS = {
    'raw_meteor_phase':('raw','meteor'), 'stable_crystal':('crystal','meteor'),
    'refined':('cut','meteor'), 'wet_tailings':('wet','meteor'),
    'rutile':('needles','titan'), 'titan':('powder','titan'),
    'borate':('rosette','boron'), 'boron':('powder','boron'),
    'moon':('raw','moon'), 'lunar':('crystal','moon'),
    'rutile_tail':('tail','titan'), 'borate_tail':('tail','boron'),
    'moon_tail':('tail','moon'), 'refine_tail':('wet','violet'),
    'reclaim_copper':('scrap','copper'), 'reclaim_crystal':('shards','meteor'),
    'residue':('tail','carbon'), 'iron_plate':('plate','iron'),
    'copper_plate':('plate','copper'), 'mixed_steel_billet':('billet','carbon'),
    'ordinary_steel':('bars','steel'), 'ceramic':('tiles','ceramic'),
    'insulation':('roll','rubber'), 'binder':('jar','boron'),
    'isolation_substrate':('laminate','copper'), 'control_board':('board','meteor'),
    'circuit_breaker':('breaker','ceramic'), 'ceramic_sheath':('tube','ceramic'),
    'iron_fasteners':('bolts','iron'), 'steel_fasteners':('bolts','steel'),
    'sealed_box':('box','iron'), 'coated_box':('box','violet'),
    'lined_box':('box','moon'), 'machine_frame':('frame','steel'),
    'carbon_portion':('shards','carbon'), 'quartz_portion':('shards','ceramic'),
    'conductor':('copperboard','copper'), 'load':('bracket','steel'),
    'laminar':('laminate','steel'), 'coating':('jar','violet'),
    'sensor':('sensor','meteor'), 'anima':('crystal','violet'),
    'anima_basic':('cut','violet'), 'directed':('wafer','meteor'),
    'wear':('wafer','titan'), 'liner':('roll','moon'),
    'efficient_connector':('connector','copper'), 'tool_brace':('bracket','iron'),
    'shield_lining':('shield','meteor'),
}

def sprite(name, kind, family):
    p=PALETTES[family]; im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    rng=random.Random(int.from_bytes(hashlib.sha256(name.encode()).digest()[:8],'big'))
    def poly(points, color=2):d.polygon(points,fill=p[color],outline=p[0])
    def crystal(x,y,w,h):
        poly([(x,y),(x+w//2,y-h),(x+w,y-2),(x+w-2,y+h//2),(x+3,y+h//2+2)])
        d.polygon([(x+1,y),(x+w//2,y-h+2),(x+w//2,y+h//2),(x+3,y+h//2)],fill=p[3])
        d.polygon([(x+w//2,y-h+3),(x+w-2,y-1),(x+w-3,y+h//2),(x+w//2,y+h//2)],fill=p[1])
        d.line((x+2,y,x+w//2,y-h+2),fill=p[4]);d.line((x+w//2,y-h+3,x+w//2,y+h//2),fill=p[2])
    if kind in {'raw','needles','rosette'}:
        rock=[(2,16),(5,8),(11,6),(17,7),(24,10),(29,19),(25,27),(14,30),(5,26)]
        d.polygon(rock,fill='#50545a',outline='#252a32');d.polygon([(3,17),(7,10),(14,8),(16,15),(11,23),(5,23)],fill='#858381')
        d.polygon([(12,24),(18,13),(25,14),(28,21),(24,26),(15,28)],fill='#353f49')
        for _ in range(24):
            x,y=rng.randrange(5,26),rng.randrange(11,26)
            if im.getpixel((x,y))[3]:d.point((x,y),fill=rng.choice(['#9a9690','#333942','#626a6d']))
        if kind=='raw':crystal(8,15,10,9);crystal(17,19,8,9)
        elif kind=='needles':
            for x,y,w,h in [(7,18,5,14),(12,20,6,13),(18,22,5,15),(22,22,4,10)]:crystal(x,y,w,h)
        else:
            for x,y,w,h in [(5,18,10,5),(13,13,10,6),(15,24,11,5),(7,24,8,4)]:crystal(x,y,w,h)
    elif kind in {'crystal','cut','shards'}:
        if kind=='crystal':crystal(3,22,11,12);crystal(12,16,14,14);crystal(23,24,7,9)
        elif kind=='cut':
            poly([(4,12),(11,4),(23,5),(29,14),(23,25),(14,29),(4,23)])
            d.polygon([(11,5),(22,6),(24,14),(14,17),(6,12)],fill=p[3]);d.polygon([(14,18),(24,15),(22,24),(14,27)],fill=p[1]);d.line((5,12,14,17,14,27),fill=p[4]);d.line((11,5,14,17,24,14,22,6),fill=p[2])
        else:
            for x,y,w,h in [(3,18,8,8),(12,11,8,9),(19,23,10,9),(7,27,9,7)]:crystal(x,y,w,h)
    elif kind=='powder':
        # Processed powder in a folded open paper pouch; tails remain bare rock piles.
        d.polygon([(5,11),(25,11),(28,27),(23,30),(8,30),(3,26)],fill='#b5a68b',outline='#49443e')
        d.polygon([(7,14),(23,14),(25,27),(9,28),(6,25)],fill='#e0d5b5')
        d.line((7,15,9,26,23,26),fill='#f6edcc');d.line((24,15,26,26),fill='#82775f')
        poly([(5,12),(10,8),(14,9),(18,4),(23,9),(26,12),(22,16),(9,16)],2)
        for _ in range(40):
            x,y=rng.randrange(7,24),rng.randrange(7,16)
            if im.getpixel((x,y))[:3] in [ImageColor.getrgb(c) for c in p]:d.point((x,y),fill=p[rng.choice([1,2,3,3])])
        d.line((5,13,10,17,22,17,26,13),fill='#84775c')
        d.rectangle((12,20,20,24),fill=p[1]);d.line((14,21,18,21),fill=p[3])
    elif kind in {'tail','wet'}:
        poly([(2,24),(6,17),(13,19),(18,12),(24,16),(30,26),(22,29),(7,29)],1)
        for _ in range(85):
            x,y=rng.randrange(4,29),rng.randrange(15,29)
            if im.getpixel((x,y))[3]:d.rectangle((x,y,x+1,y+1),fill=p[rng.choice([1,2,2,3])])
        if kind=='wet':
            d.ellipse((3,24,28,29),fill='#365c65',outline=p[0]);d.line((8,26,15,26),fill='#86c9c4');d.line((20,27,24,27),fill='#548e99')
        elif kind=='tail':
            for x,y in [(7,22),(18,18),(23,25)]:d.polygon([(x,y),(x+3,y-2),(x+5,y+1),(x+1,y+2)],fill='#72716a',outline='#383b3d')
    elif kind in {'plate','wafer','copperboard'}:
        poly([(2,15),(21,6),(30,13),(11,24),(2,20)],1)
        poly([(2,15),(21,6),(30,13),(11,22)],3)
        d.line((3,15,11,21,29,13),fill=p[4]);d.line((12,23,28,15),fill=p[2])
        if kind=='plate':
            d.line((7,15,17,10),fill=p[2]);d.line((15,18,23,14),fill=p[2]);d.line((20,9,24,12),fill=p[4])
        elif kind=='wafer':
            for x,y in [(10,16),(16,13),(22,10)]:d.line((x,y,x+5,y+3),fill=p[1])
            d.line((9,16,22,10),fill=p[4]);d.line((12,18,25,12),fill=p[2])
            if name=='wear':d.polygon([(10,17),(14,15),(18,17),(14,20)],fill=p[0])
        else:
            d.line((7,15,15,11,21,15,26,12),fill='#447969',width=2)
            d.line((12,19,16,17,12,14),fill='#526d55',width=2)
            for x,y in [(7,15),(21,15),(12,19)]:d.rectangle((x,y,x+1,y+1),fill='#fff1bd')
    elif kind in {'laminate','tiles','bars','billet'}:
        poly([(3,12),(21,5),(29,10),(29,23),(11,29),(3,24)],1)
        poly([(3,12),(21,5),(29,10),(11,18)],3);poly([(4,14),(11,20),(28,13),(28,22),(11,27),(4,23)],2)
        d.line((4,12,11,17,27,10),fill=p[4]);d.line((11,19,11,26),fill=p[0])
        if kind=='plate':
            for x,y in [(7,14),(23,10),(15,17)]:d.line((x,y,x+2,y-1),fill=p[1])
            d.line((13,23,25,19),fill=p[3])
        elif kind=='laminate':
            for y in (21,24):d.line((4,y-2,11,y+2,28,y-4),fill='#d8ac66')
            d.line((15,12,21,10),fill=p[1])
        elif kind=='tiles':
            d.line((12,10,21,14,21,24),fill=p[0]);d.line((8,17,24,11),fill=p[1]);d.line((13,22,17,21),fill=p[4])
        elif kind=='bars':
            for y in (17,22):d.line((4,y-3,11,y+2,28,y-4),fill=p[0]);d.line((12,y+1,27,y-4),fill=p[3])
        else:
            for x,y in [(7,18),(17,21),(21,15)]:d.line((x,y,x+3,y-1),fill=p[0])
            d.rectangle((13,16,16,17),fill=p[3])
    elif kind in {'roll','tube','ring','coil'}:
        if kind in {'roll','tube'}:
            poly([(5,12),(17,5),(28,11),(28,22),(16,29),(5,23)],2)
            d.ellipse((3,10,19,28),fill=p[3],outline=p[0]);d.ellipse((7,14,15,24),fill=p[0]);d.arc((6,13,16,25),70,290,fill=p[1],width=2)
            d.line((18,10,25,13,25,22),fill=p[4]);d.line((18,26,27,21),fill=p[1])
            if kind=='tube':d.line((21,12,21,24),fill=p[1])
        else:
            for offset in ([7,4,1] if kind=='coil' else [3]):
                d.ellipse((3,offset+5,28,offset+22),fill=p[2],outline=p[0]);d.ellipse((9,offset+9,22,offset+18),fill=(0,0,0,0),outline=p[1]);d.arc((5,offset+7,26,offset+20),190,300,fill=p[4],width=2)
            if kind=='coil':d.line((27,20,29,26,25,29),fill=p[3],width=2)
    elif kind in {'jar','box','breaker','sensor','connector','board','frame','shield','bracket'}:
        if kind=='jar':
            d.rounded_rectangle((6,8,26,28),radius=3,fill=p[1],outline=p[0]);d.rectangle((7,5,25,10),fill='#aab5b7',outline='#35434b');d.line((9,6,23,6),fill='#e2e9df');d.rectangle((8,14,24,22),fill=p[3]);d.rectangle((13,16,19,20),fill=p[2]);d.line((9,12,9,24),fill=p[4])
        elif kind=='frame':
            # Open struts, not an opaque cube: large windows survive 16px GUI scaling.
            for a,b in [((21,4),(21,20)),((4,9),(21,4)),((21,20),(28,24)),((4,9),(4,24)),((4,24),(12,29)),((12,29),(28,24)),((28,9),(28,24)),((12,14),(12,29)),((4,9),(12,14)),((12,14),(28,9)),((21,4),(28,9))]:
                d.line((*a,*b),fill=p[0],width=5);d.line((*a,*b),fill=p[2],width=3)
            d.line((4,8,12,13,28,8),fill=p[4]);d.line((12,15,12,27),fill=p[3])
            for x,y in [(4,9),(12,14),(28,9),(12,28),(28,23)]:d.rectangle((x-1,y-1,x+1,y+1),fill='#d2b581')
        elif kind=='shield':
            poly([(5,4),(26,4),(25,19),(16,29),(6,20)],1);d.polygon([(8,7),(23,7),(22,18),(16,25),(9,18)],fill=p[3]);d.line((15,8,15,23),fill=p[4]);d.line((10,12,21,12),fill=p[2])
        elif kind=='bracket':
            poly([(5,3),(13,3),(13,13),(28,13),(28,22),(17,22),(17,29),(5,29)],2);d.line((6,4,11,4,11,16,26,16),fill=p[4]);d.ellipse((7,20,12,25),fill=p[0]);d.ellipse((20,17,24,21),fill=p[0])
        else:
            poly([(3,9),(22,4),(29,9),(29,24),(10,29),(3,24)],1);poly([(4,9),(22,5),(28,9),(10,14)],3);poly([(11,15),(28,11),(28,23),(11,27)],2)
            d.line((4,10,10,14,10,27),fill=p[4])
            if kind=='board':
                d.rectangle((12,13,21,22),fill='#26373d',outline='#aab5a4')
                for y in (14,17,20):d.line((22,y,26,y-1),fill='#ebc675');d.line((8,y+2,11,y+1),fill='#ebc675')
                d.rectangle((15,15,18,19),fill='#6d8588')
            elif kind=='breaker':
                d.rectangle((13,12,24,23),fill='#323d45',outline=p[4]);d.rectangle((16,12,21,17),fill='#d86e4f');d.rectangle((16,19,21,22),fill='#83aa71')
            elif kind=='sensor':
                d.ellipse((12,11,26,24),fill='#233f53',outline='#c2b88c');d.ellipse((15,13,23,21),fill='#439dca');d.ellipse((16,14,19,16),fill='#d3faff')
            elif kind=='connector':
                for x in (5,23):d.rectangle((x,12,x+3,23),fill='#d4b077',outline=p[0])
                d.rectangle((12,15,20,22),fill='#3c9b94',outline='#d6ddbb')
            else:
                d.line((5,21,10,25,28,20),fill=p[0],width=2);d.rectangle((15,18,20,23),fill='#e1c98c',outline=p[0]);d.point((17,20),fill=p[0])
    elif kind=='bolts':
        for x,y in [(4,4),(18,10),(5,21)]:
            d.rectangle((x+3,y+2,x+5,y+7),fill=p[2]);d.line((x+3,y+4,x+6,y+4),fill=p[0]);d.line((x+3,y+6,x+6,y+6),fill=p[0]);poly([(x,y+1),(x+3,y-1),(x+7,y),(x+8,y+3),(x+5,y+5),(x+1,y+4)],3);d.line((x+2,y+1,x+5,y+2),fill=p[0]);d.point((x+1,y+1),fill=p[4])
    elif kind=='scrap':
        for x,y in [(4,7),(16,3),(12,19)]:
            poly([(x,y),(x+10,y+2),(x+8,y+10),(x+2,y+8)],2);d.line((x+2,y+1,x+8,y+3),fill=p[4]);d.line((x+3,y+4,x+7,y+5),fill='#43796b',width=2)
    else:raise ValueError(kind)
    return im

def ore(family,deep=False):
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im);rng=random.Random(402 if deep else 401)
    # Broad fractured stone layers, with restrained fine grain instead of flat fill.
    for y in range(32):
        for x in range(32):
            base=(54 if deep else 113)+rng.randrange(-8,9)+(y//4%2)*5
            im.putpixel((x,y),(base,base+2,base+3,255))
    for x,y,w in [(1,3,12),(15,6,14),(2,17,10),(14,22,17),(0,29,13)]:
        d.line((x,y,x+w,y-1),fill='#30373c' if deep else '#555b5b');d.line((x+1,y+1,x+w,y),fill='#62696c' if deep else '#969c97')
    p=PALETTES[family]
    for x,y in [(3,5),(19,2),(12,14),(23,23),(2,25)]:
        if family=='titan':points=[(x,y+5),(x+2,y),(x+4,y+2),(x+5,y-1),(x+7,y+2),(x+5,y+7)]
        elif family=='boron':points=[(x,y+2),(x+3,y),(x+7,y+2),(x+7,y+5),(x+4,y+7),(x,y+5)]
        else:points=[(x,y+3),(x+3,y),(x+5,y+2),(x+7,y+1),(x+8,y+5),(x+4,y+7),(x+1,y+6)]
        d.polygon(points,fill=p[0]);d.polygon([(x+1,y+3),(x+3,y+1),(x+5,y+3),(x+5,y+5),(x+2,y+5)],fill=p[2]);d.line((x+1,y+3,x+3,y+1,x+4,y+2),fill=p[3]);d.point((x+3,y+2),fill=p[4]);d.line((x+5,y+5,x+6,y+3),fill=p[1])
    return im

def core():
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    d.polygon([(16,1),(28,8),(28,24),(16,31),(4,24),(4,8)],fill='#302347',outline='#e7c879')
    d.line((5,9,16,3,27,9,27,23,16,29,5,23,5,9),fill='#9676cc',width=2)
    d.polygon([(16,6),(24,11),(24,21),(16,26),(8,21),(8,11)],fill='#7158b2',outline='#e3e5ff')
    d.line((10,17,12,13,15,13,18,19,21,19,23,15),fill='#f7edbc',width=2)
    d.line((10,17,12,20,15,20,19,13,21,13,23,15),fill='#f7edbc',width=2)
    for x,y in [(16,1),(4,8),(28,8),(4,24),(28,24),(16,31)]:d.point((x,y),fill='#fffbd5')
    return im

def generate():
    (ART/'item').mkdir(parents=True,exist_ok=True)
    for name,(kind,family) in SPECS.items():sprite(name,kind,family).save(ART/'item'/f'{name}.png')
    for name,family,deep in [('ore_meteor_phase','meteor',False),('deepslate_ore_meteor_phase','meteor',True),('rutile_ore','titan',False),('borate_ore','boron',False),('moon_ore','moon',False)]:ore(family,deep).save(ART/'block'/f'{name}.png')
    core().save(ART/'item/creative_construction_core.png')
    print(f'Refined {len(SPECS)} material icons, 5 ore textures and creative core (32px).')

def preview():
    names=list(SPECS)+['creative_construction_core','ore_meteor_phase','deepslate_ore_meteor_phase','rutile_ore','borate_ore','moon_ore']
    columns=8;width=176;height=126
    sheet=Image.new('RGB',(columns*width,((len(names)+columns-1)//columns)*height),'#243039');d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',13)
    import json
    labels=json.loads((ART.parent/'lang/zh_cn.json').read_text(encoding='utf-8'))
    for index,name in enumerate(names):
        x=index%columns*width;y=index//columns*height
        path=ART/('block' if name.endswith('_ore') or 'ore_meteor' in name else 'item')/f'{name}.png'
        im=Image.open(path).convert('RGBA').resize((80,80),Image.Resampling.NEAREST)
        d.rectangle((x+42,y+4,x+125,y+87),fill='#44505a');sheet.paste(im,(x+44,y+6),im)
        label=labels.get('item.starfall_blueprint.'+name,labels.get('block.starfall_blueprint.'+name,name))
        d.text((x+width//2,y+91),label,font=font,anchor='mt',fill='#ebeadb')
        d.text((x+width//2,y+109),name[:26],font=ImageFont.load_default(),anchor='mt',fill='#a1bbc5')
    out=ROOT/'docs/material-textures.png';sheet.save(out)

if __name__=='__main__':generate();preview()
