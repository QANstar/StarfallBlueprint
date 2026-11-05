"""Original deterministic 32px hardware sprites and clipped 2x2x2 workstation geometry.

Run after generate_lingchuan_resources.py to preserve these workshop refinements.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/starfall_blueprint'
PARTS = ['head', 'chest', 'core', 'legs', 'feet', 'hands', 'back', 'shoulders']
COLORS = ['#b73950', '#ec4864', '#ff7185']
DARK, METAL, EDGE, LIGHT = '#111117', '#292832', '#5e505d', '#b7a5b0'

def write(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def sprite(key, part, tier=1):
    im = Image.new('RGBA', (32, 32)); d = ImageDraw.Draw(im); accent = COLORS[tier-1]
    def poly(points, fill=METAL): d.polygon(points, fill=fill, outline=EDGE)
    def rect(box, fill=METAL): d.rectangle(box, fill=fill, outline=EDGE)
    if part == 'head':
        poly([(10,3),(21,3),(26,9),(24,24),(19,29),(12,29),(7,24),(5,9)])
        poly([(10,7),(21,7),(23,12),(21,23),(16,27),(10,23),(8,12)], DARK)
        d.line([(8,13),(13,15)], fill=accent, width=3);d.line([(18,15),(23,13)], fill=accent, width=3)
        d.line([(13,23),(18,23)], fill=LIGHT);d.rectangle((14,5,17,7),fill=accent)
    elif part == 'chest':
        poly([(8,4),(12,6),(19,6),(23,4),(29,10),(25,17),(24,28),(7,28),(6,17),(2,10)])
        poly([(9,9),(15,12),(22,9),(22,20),(19,25),(12,25),(9,20)], DARK)
        d.line([(8,10),(13,12)],fill=accent,width=2);d.line([(18,12),(23,10)],fill=accent,width=2)
        rect((14,12,17,20),accent);d.line((10,23,21,23),fill=EDGE,width=2)
    elif part == 'core':
        poly([(11,2),(20,2),(27,9),(27,22),(20,29),(11,29),(4,22),(4,9)])
        d.ellipse((7,5,24,26),fill=DARK,outline=LIGHT,width=2);d.ellipse((11,9,20,22),fill=accent)
        d.rectangle((14,6,17,25),fill=LIGHT);d.rectangle((14,12,17,19),fill=accent)
    elif part == 'legs':
        rect((6,4,25,10));poly([(7,9),(14,11),(13,28),(5,28)],DARK);poly([(17,11),(24,9),(26,28),(18,28)],DARK)
        for x in (7,20):rect((x,15,x+5,20));d.line((x+1,22,x+1,26),fill=accent,width=2)
        d.rectangle((13,5,18,7),fill=accent)
    elif part == 'feet':
        for x in (3,18):
            poly([(x+2,5),(x+9,5),(x+10,20),(x+12,23),(x+12,28),(x,28),(x,22),(x+2,19)])
            d.rectangle((x+3,9,x+8,17),fill=DARK);d.line((x+4,10,x+4,16),fill=accent,width=2);d.line((x+1,26,x+11,26),fill=LIGHT)
    elif part == 'hands':
        for x in (3,18):
            poly([(x+2,4),(x+9,4),(x+11,10),(x+9,24),(x+7,28),(x+1,26),(x,13)],DARK)
            rect((x+1,12,x+9,21));d.rectangle((x+3,13,x+6,18),fill=accent)
            for y in (23,25):d.line((x+2,y,x+7,y),fill=EDGE)
    elif part == 'back':
        rect((10,4,21,26),DARK)
        for x in (3,22):
            poly([(x+2,5),(x+6,8),(x+6,23),(x+4,28),(x,25),(x,10)])
            d.rectangle((x+2,11,x+4,22),fill=accent);d.rectangle((x+1,24,x+5,26),fill=LIGHT)
        d.rectangle((14,8,17,23),fill=accent)
    elif part == 'shoulders':
        for x in (2,18):
            poly([(x+3,5),(x+10,6),(x+12,12),(x+10,22),(x,20),(x,11)])
            poly([(x+2,12),(x+9,12),(x+8,18),(x+2,18)],DARK);d.line((x+2,9,x+9,10),fill=accent,width=2)
        d.line((13,7,18,7),fill=LIGHT,width=2)
    else:
        colors={'night':'#9eebc0','breathing':'#6bcde5','sprint':'#a5e898','jump':'#b4db79','flight':'#79d8d2','shield':'#7db7f0','heavy':'#c1cbd6','cloak':'#c29aee','laser':'#ff7d99','missile':'#ffae6e','cannon':'#65daf4','turret':'#f4cf7b'}
        # Hardware silhouettes distinguish function; every component shares the suit's black/red finish.
        accent='#ed526c'
        if part=='night':
            rect((3,10,28,24),DARK)
            for x in (5,18):d.ellipse((x,11,x+9,21),fill=METAL,outline=accent,width=2);d.ellipse((x+3,14,x+6,18),fill=accent)
            rect((12,5,19,10))
        elif part=='breathing':
            for x in (7,18):rect((x,6,x+6,25));d.rectangle((x+2,9,x+4,22),fill=accent)
            d.line([(7,7),(7,3),(23,3),(23,7)],fill=LIGHT,width=2);d.line((12,26,22,26),fill=accent,width=2)
        elif part in ('sprint','jump'):
            poly([(6,7),(13,4),(24,12),(24,24),(17,28),(6,20)],DARK)
            points=[(10,10),(17,15),(10,21)] if part=='sprint' else [(9,19),(16,10),(23,19)]
            d.line(points,fill=accent,width=4);d.line((11,25,21,25),fill=LIGHT)
        elif part=='flight':
            for x in (4,20):rect((x,6,x+7,23));d.rectangle((x+2,10,x+5,20),fill=DARK);d.polygon([(x+1,24),(x+6,24),(x+4,30)],fill=accent)
            rect((12,4,19,17),DARK);d.line((15,6,15,14),fill=accent,width=2)
        elif part=='shield':
            poly([(6,6),(16,3),(26,6),(25,19),(16,29),(7,19)],DARK);d.line([(10,9),(16,7),(22,9),(21,18),(16,23),(11,18),(10,9)],fill=accent,width=2);d.line((16,10,16,20),fill=LIGHT,width=2)
        elif part=='heavy':
            for y in (5,12,19):poly([(5,y),(25,y),(27,y+6),(8,y+9)],METAL)
            d.line((8,6,23,6),fill=LIGHT,width=2);d.line((11,26,23,26),fill=accent,width=2)
        elif part=='cloak':
            poly([(16,2),(28,16),(16,30),(4,16)],DARK)
            for y in (8,13,18,23):d.line((12,y,20,y),fill=accent,width=2)
            d.line((10,12,10,20),fill=EDGE,width=2);d.line((23,12,23,20),fill=LIGHT)
        elif part=='laser':
            poly([(4,21),(11,14),(17,17),(12,27),(6,28)],DARK);poly([(10,13),(20,3),(25,8),(16,19)])
            d.line((16,13,25,4),fill=accent,width=3);d.line((24,2,29,7),fill=LIGHT,width=2)
        elif part=='missile':
            for x,y in ((4,9),(16,3)):
                poly([(x+3,y),(x+7,y+4),(x+7,y+17),(x+1,y+20),(x,y+7)],DARK);d.rectangle((x+2,y+7,x+5,y+13),fill=accent);d.line((x+2,y+18,x+4,y+23),fill=accent,width=2)
        elif part=='cannon':
            poly([(3,8),(22,3),(27,7),(27,22),(9,28),(3,24)],DARK)
            for x in (8,18):rect((x,8,x+5,24));d.line((x+2,11,x+2,20),fill=accent,width=2)
            d.line((10,5,23,5),fill=LIGHT,width=2)
        elif part=='turret':
            poly([(1,13),(11,8),(20,8),(30,13),(26,21),(5,21)],DARK);rect((12,5,19,19));d.rectangle((14,8,17,14),fill=accent)
            for x in (7,22):rect((x,16,x+3,27));d.rectangle((x+1,24,x+2,29),fill=accent)
        elif part in ('watch','armed_watch'):
            rect((11,0,20,31),DARK);poly([(7,7),(24,7),(27,11),(25,24),(6,24),(4,11)])
            rect((8,10,23,22),DARK);d.line([(10,17),(14,13),(17,19),(22,12)],fill='#ff7085' if part=='armed_watch' else '#d83b56',width=2)
        elif part=='fire_control':
            poly([(3,6),(24,6),(29,10),(27,15),(17,17),(14,29),(7,28),(9,17),(3,15)],DARK)
            rect((4,8,25,13));d.line((6,8,24,8),fill=accent,width=2);d.rectangle((26,9,29,12),fill=LIGHT)
            poly([(12,17),(17,17),(16,23),(13,24)],DARK);d.line((9,22,12,22),fill=accent,width=2)
            for x in (7,12,17):d.rectangle((x,3,x+2,5),fill=METAL)
    # Every chassis tier has a distinct colour edge and readable number of pips.
    if part in PARTS:
        for n in range(tier):d.rectangle((2+n*4,29,4+n*4,31),fill=accent)
    d.point((4,4),fill=LIGHT)
    im.save(ASSETS / f'textures/item/{key}.png')

for tier in range(1,4):
    for part in PARTS:
        key='blackdog_core_'+['basic','compressed','reactor'][tier-1] if part=='core' else 'blackdog_'+part+'_'+['basic','reinforced','elite'][tier-1]
        sprite(key,part,tier)
for ability in ['night','breathing','sprint','jump','flight','shield','heavy','cloak','laser','missile','cannon','turret']:
    sprite('blackdog_component_'+ability,ability)
for part in ['watch','armed_watch','fire_control']:sprite('blackdog_'+part,part)
for part in ['head','chest','legs','feet']:sprite('blackdog_armor_'+part,part,3)
write(ASSETS/'models/item/blackdog_armed_watch.json',{'parent':'minecraft:item/generated','textures':{'layer0':'starfall_blueprint:item/blackdog_armed_watch'}})

# A compact physical fire-control grip, not a tablet: trigger, knuckle guard, paired sensor rails and red status seam.
control=[]
def control_box(a,b,texture):control.append({'from':a,'to':b,'faces':{f:{'texture':'#'+texture} for f in ['up','down','north','south','east','west']}})
for a,b,t in [([5,7,4],[11,11,13],'body'),([5.5,2,10],[9.5,7,13],'grip'),([4,8,-1],[12,10,7],'body'),([5,8.4,-2],[7,9.6,3],'trim'),([9,8.4,-2],[11,9.6,3],'trim'),([5.5,9,-2.2],[6.5,9.3,-2],'red'),([9.5,9,-2.2],[10.5,9.3,-2],'red'),([4.8,10.2,3],[11.2,11,11],'trim'),([4.7,9.4,4],[5,10,11],'red'),([11,9.4,4],[11.3,10,11],'red'),([8,5.5,6.5],[9,7,7.5],'red'),([7,3.5,6],[10,4.3,11],'trim'),([9.3,4.2,6],[10,7,6.8],'trim'),([5.3,12,7],[10.7,12.7,9],'trim'),([6,11,8],[7,12.4,9],'body'),([9,11,8],[10,12.4,9],'body'),([6.5,4,13],[8.5,5,13.25],'red')]:control_box(a,b,t)
textures={t:f'starfall_blueprint:item/blackdog_control_{t}' for t in ['body','trim','grip','red']}
for kind,color in [('body','#202027'),('trim','#4b414c'),('grip','#121217'),('red','#d93a57')]:
    im=Image.new('RGBA',(16,16),color);d=ImageDraw.Draw(im)
    if kind!='red':
        d.line((0,0,15,0),fill='#75616d');d.line((0,1,0,15),fill='#40333e')
        for y in range(4,16,4):d.line((2,y,13,y),fill='#392d38' if kind=='grip' else '#28232c')
    else:d.line((0,7,15,7),fill='#ff8a9b',width=2)
    im.save(ASSETS/f'textures/item/blackdog_control_{kind}.png')
write(ASSETS/'models/item/blackdog_fire_control_held.json',{'textures':textures,'elements':control,'display':{
    'firstperson_righthand':{'rotation':[0,-90,-8],'translation':[-.4,3,-.4],'scale':[.52,.52,.52]},
    'firstperson_lefthand':{'rotation':[0,90,8],'translation':[-.4,3,-.4],'scale':[.52,.52,.52]},
    'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,-1,1],'scale':[.65,.65,.65]},
    'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,-1,1],'scale':[.65,.65,.65]},
    'ground':{'translation':[0,3,0],'scale':[.5,.5,.5]},'fixed':{'rotation':[0,90,0],'scale':[.7,.7,.7]}}})
write(ASSETS/'models/item/blackdog_fire_control.json',{'loader':'neoforge:separate_transforms','base':{'parent':'starfall_blueprint:item/blackdog_fire_control_held'},'perspectives':{'gui':{'parent':'minecraft:item/generated','textures':{'layer0':'starfall_blueprint:item/blackdog_fire_control'}}}})

def machine(key,style):
    shapes=[]
    def box(a,b,t='casing'):shapes.append((a,b,t))
    box([0,0,0],[32,4,32]);box([3,4,3],[29,5,29],'panel')
    for x in (1,29):
        box([x,4,27],[x+2,31,30]);box([x,7,26.5],[x+2,28,27],'light')
        box([x-1,0,1],[x+2,4.5,6],'vent')
    box([0,29,26],[32,32,32]);box([3,29,25.5],[29,30,26],'light')
    if style=='assembler':
        for x in (3,25):
            box([x,10,17],[x+4,13,28],'panel');box([x,11,14],[x+4,20,18]);box([x+1,18,11],[x+3,20,17],'light')
        box([2,5,2],[8,12,7]);box([2,12,1],[11,13,8],'screen')
        for z in (9,22):box([5,5,z],[27,5.3,z+1],'light')
    elif style=='charger':
        for x in (4,25):
            box([x,5,20],[x+3,24,24],'panel');box([x+.5,6,19.5],[x+2.5,23,20],'light')
        box([12,5,25],[20,23,28],'core');box([1,5,1],[7,9,7]);box([1,9,1],[8,10,8],'screen')
        for x in (8,22):box([x,5,6],[x+2,5.4,21],'light')
    else:
        box([2,4,9],[30,11,29],'vent');box([1,11,4],[31,13,30],'panel')
        box([3,19,23],[29,22,27]);box([13,15,18],[19,23,24],'core');box([15,13,18],[17,16,20],'light')
        box([3,13,5],[11,14,10],'screen');box([4,13,14],[11,15,20]);box([22,13,11],[27,14,20],'light')
        for x in (4,13,22):box([x,5,8.5],[x+6,8,9],'panel')
    for part in range(8):
        ox=(part&1)*16;oy=(part>>2)*16;oz=((part>>1)&1)*16;elements=[]
        for a,b,t in shapes:
            low=[max(a[0],ox),max(a[1],oy),max(a[2],oz)];high=[min(b[0],ox+16),min(b[1],oy+16),min(b[2],oz+16)]
            if any(low[i]>=high[i] for i in range(3)):continue
            origin=[ox,oy,oz];elements.append({'from':[low[i]-origin[i] for i in range(3)],'to':[high[i]-origin[i] for i in range(3)],'faces':{f:{'texture':'#'+t} for f in ['up','down','north','south','east','west']}})
        write(ASSETS/f'models/block/{key}_part{part}.json',{'textures':{**{t:f'starfall_blueprint:block/lc_{t}' for t in ['casing','panel','vent','screen','core']},'light':f'starfall_blueprint:block/workshop_{style}_light','particle':'starfall_blueprint:block/lc_casing'},'elements':elements})
    # Inventory machine silhouette shows the complete machine, rather than one quarter of its pedestal.
    textures={**{t:f'starfall_blueprint:block/lc_{t}' for t in ['casing','panel','vent','screen','core']},'light':f'starfall_blueprint:block/workshop_{style}_light'}
    write(ASSETS/f'models/item/{key}.json',{'textures':textures,'elements':[{'from':[v/2 for v in a],'to':[v/2 for v in b],'faces':{f:{'texture':'#'+t} for f in ['up','down','north','south','east','west']}} for a,b,t in shapes],'display':{'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.7,.7,.7]}}})
    im=Image.new('RGBA',(16,16),{'assembler':'#da6d94','charger':'#6cd9da','fabricator':'#e9b872'}[style]);d=ImageDraw.Draw(im)
    for y in range(0,16,4):d.line((0,y,15,y),fill='#e8e9ef')
    im.save(ASSETS/f'textures/block/workshop_{style}_light.png')

for key,style in [('lc_armor_assembler','assembler'),('lc_armor_charger','charger'),('lc_parts_fabricator','fabricator')]:machine(key,style)
for lang,strings in {
    'zh_cn':{'search':'搜索部件或组件…','category.0':'全部','category.1':'基底 / 核心','category.2':'功能 / 终端','manufacture':'制造所选','select_product':'选择左侧条目查看配方','drag_hint':'拖动外围配件到对应部位连接 · 右键已装配件拆回','load':'核心负载  %s / %s','staging':'配件暂存 / 拆回','rotation_hint':'拖动模型可旋转','insert_watch':'放入腕表以展示战甲','charging':'核心充能','component_hint':'可用 %s · 拖至部位连接'},
    'en_us':{'search':'Search chassis or components…','category.0':'All','category.1':'Chassis / Core','category.2':'Systems','manufacture':'Manufacture','select_product':'Select a product for its recipe','drag_hint':'Drag a system onto its chassis · Right-click to detach','load':'Core load  %s / %s','staging':'Staging / Returns','rotation_hint':'Drag model to rotate','insert_watch':'Insert a watch to display armour','charging':'Core charging','component_hint':'Available: %s · Drag to connect'}
}.items():
    strings.update({'assemble':'组装' if lang=='zh_cn' else 'Assemble','refit':'换装' if lang=='zh_cn' else 'Refit','drag_hint':'拿起背包配件，放到对应部位连接 · 右键已装配件拆回' if lang=='zh_cn' else 'Place an owned system on its chassis · Right-click installed systems to detach'})
    target=ASSETS/f'lang/{lang}.json';data=json.loads(target.read_text(encoding='utf-8'));data.update({'lc.workshop.'+k:v for k,v in strings.items()});write(target,data)
    data['item.starfall_blueprint.blackdog_fire_control']='黑狗 · 战术火控握柄' if lang=='zh_cn' else 'Black Dog Tactical Fire Grip';write(target,data)
print('Generated 43 hardware sprites, three complete workstation models and workshop labels.')
