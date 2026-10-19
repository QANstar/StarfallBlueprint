"""Rebuild original pixel art, localizations, recipes and prototype worldgen. Pillow only."""
from pathlib import Path
import json, random, gzip, struct
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
MOD = 'starfall_blueprint'
ART = RES / 'assets' / MOD
DATA = RES / 'data' / MOD

def js(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)

def machine_face(face, lit=False):
    im = Image.new('RGBA', (16,16), '#36454c'); d = ImageDraw.Draw(im)
    d.rectangle((0,0,15,15), outline='#1b282e'); d.line((1,1,14,1),fill='#718086')
    for x,y in [(2,2),(13,2),(2,13),(13,13)]: d.point((x,y), '#a3afb0')
    if face == 'top':
        d.rectangle((4,3,11,12),fill='#182b30',outline='#8a9c98')
        d.rectangle((5,4,10,10),fill='#347d80'); d.line((6,5,9,5),fill='#8ce0d2')
        d.line((4,13,11,13),fill='#cda56d')
    elif face == 'side':
        for y in range(5,12,2): d.line((4,y,11,y),fill='#15262c')
        d.rectangle((3,3,12,3),fill='#ba9468')
    else:
        d.rectangle((3,4,12,10),fill='#122329',outline='#718b8a')
        d.rectangle((4,5,7,8),fill='#6ddbc7' if lit else '#315f60')
        d.line((9,6,11,6),fill='#d7bc75'); d.point((9,8),'#81a99f')
        d.rectangle((4,12,11,13),fill='#18272c')
        d.point((5,12), '#f5b75f' if lit else '#68574a')
    return im

textures = {}
for face in ['top','side','front']:
    textures['preparation_'+face] = machine_face(face)
textures['preparation_front_on'] = machine_face('front',True)
im=machine_face('side');d=ImageDraw.Draw(im);d.rectangle((4,3,11,12),fill='#183e4d',outline='#9cb3b0');d.line((5,4,5,10),fill='#77a5ad');d.line((10,6,10,11),fill='#3d6777');textures['water_tank']=im
im=machine_face('top');d=ImageDraw.Draw(im);d.rectangle((3,3,12,12),fill='#485c62',outline='#a0b3ad');d.rectangle((5,5,10,10),fill='#1a353e');textures['tank_top']=im
im=Image.new('RGBA',(16,16),'#79634e');d=ImageDraw.Draw(im)
for y in [1,5,9,13]:d.line((0,y,15,y),fill='#493e36')
d.rectangle((0,0,15,1),fill='#b4b9a8');d.rectangle((0,14,15,15),fill='#a7b8b3');textures['rain_collector']=im
for deep in [False,True]:
    im = Image.new('RGBA',(16,16)); d=ImageDraw.Draw(im); rng=random.Random(43)
    for y in range(16):
        for x in range(16):
            c=rng.randint(48,72) if deep else rng.randint(100,131)
            d.point((x,y),(c,c+2,c+3,255))
    for x,y in [(3,3),(9,2),(6,8),(12,11),(2,12)]:
        d.rectangle((x-1,y,x+1,y+1),fill='#163b43'); d.point((x,y),'#71d8c9'); d.point((x+1,y+1),'#368a9c')
    textures['deepslate_ore_meteor_phase' if deep else 'ore_meteor_phase'] = im
for name,im in textures.items(): save(im,ART/'textures/block'/f'{name}.png')

def item(name):
    im=Image.new('RGBA',(16,16)); d=ImageDraw.Draw(im)
    if name=='raw_meteor_phase':
        d.polygon([(2,6),(5,2),(11,3),(14,8),(11,13),(4,14),(1,10)],fill='#354950',outline='#172a32')
        d.polygon([(5,5),(8,3),(10,6),(8,11),(5,9)],fill='#55bfae');d.line((6,5,7,8),fill='#b7f4da')
        d.line((10,8,12,10),fill='#6c8290')
    elif name=='stable_crystal':
        d.polygon([(4,5),(8,1),(12,5),(11,12),(7,15),(3,11)],fill='#1b6d80',outline='#173d52')
        d.polygon([(8,2),(8,12),(4,10),(5,5)],fill='#a0e8d4');d.polygon([(9,3),(11,5),(10,11),(8,13)],fill='#4ac8bd')
        d.point((6,5),'#effff1')
    elif name=='wet_tailings':
        d.polygon([(3,4),(12,4),(13,13),(3,14),(2,7)],fill='#6c7466',outline='#293b3e')
        d.rectangle((4,6,11,9),fill='#8d9c8e');d.line((4,4,11,4),fill='#b2bcaa');d.point((7,7),'#4a968a')
        d.line((4,12,10,12),fill='#484e49')
    elif name=='efficient_connector':
        d.rectangle((2,5,13,11),fill='#9c6c45',outline='#382e2b');d.rectangle((4,4,11,5),fill='#dfaf71')
        d.rectangle((6,6,9,10),fill='#4fcab4');d.line((2,7,4,7),fill='#f5d0a0');d.line((11,9,13,9),fill='#f5d0a0')
    elif name=='tool_brace':
        d.polygon([(3,2),(7,2),(7,6),(12,6),(12,11),(8,11),(8,9),(3,9)],fill='#9caeb2',outline='#354b55')
        d.rectangle((4,4,5,7),fill='#d7ece4');d.rectangle((8,7,10,9),fill='#5acbb7')
    elif name=='shield_lining':
        d.polygon([(3,2),(12,2),(12,9),(8,14),(3,9)],fill='#7b9194',outline='#2d414b')
        d.polygon([(5,4),(10,4),(10,8),(8,11),(5,8)],fill='#6bd1bc');d.line((5,4,9,4),fill='#ddf4db')
    elif name=='ore_indicator':
        d.rectangle((4,3,12,13),fill='#314a55',outline='#152c38');d.rectangle((5,4,11,10),fill='#96c3ac')
        d.line((6,8,8,6,10,8),fill='#2b6c6a',width=1);d.point((8,11),'#e2b677');d.line((3,2,3,6),fill='#d7ab6a')
    else:
        d.polygon([(3,2),(11,1),(13,3),(13,13),(5,15),(2,12),(2,3)],fill='#325361',outline='#142b35')
        d.rectangle((4,3,10,11),fill='#507c80');d.line((5,13,11,12),fill='#d2d6b8');d.rectangle((6,5,9,8),fill='#83d4bc')
    return im

names=['raw_meteor_phase','stable_crystal','wet_tailings','efficient_connector','tool_brace','shield_lining','ore_indicator','field_manual']
for name in names:
    save(item(name),ART/'textures/item'/f'{name}.png')
    js(ART/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'{MOD}:item/{name}'}})
for name in ['ore_meteor_phase','deepslate_ore_meteor_phase']:
    js(ART/'models/block'/f'{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'{MOD}:block/{name}'}})
    js(ART/'blockstates'/f'{name}.json',{'variants':{'':{'model':f'{MOD}:block/{name}'}}})
for lit in [False,True]:
    model='preparation_bench'+('_on' if lit else '')
    js(ART/'models/block'/f'{model}.json',{'parent':'minecraft:block/orientable','textures':{
        'top':f'{MOD}:block/preparation_top','front':f'{MOD}:block/preparation_front'+('_on' if lit else ''),
        'side':f'{MOD}:block/preparation_side'}})
js(ART/'blockstates/preparation_bench.json',{'variants':{f'facing={facing},lit={str(lit).lower()}':{'model':f'{MOD}:block/preparation_bench'+('_on' if lit else ''),'y':rot} for lit in [False,True] for facing,rot in [('north',0),('east',90),('south',180),('west',270)]}})
js(ART/'models/item/preparation_bench.json',{'parent':f'{MOD}:block/preparation_bench'})
js(ART/'models/block/water_tank.json',{'parent':'minecraft:block/cube_bottom_top','textures':{'side':f'{MOD}:block/water_tank','top':f'{MOD}:block/tank_top','bottom':f'{MOD}:block/tank_top'}})
def box(start,end,texture):return {'from':start,'to':end,'faces':{side:{'texture':texture} for side in ['north','south','east','west','up','down']}}
js(ART/'models/block/rain_collector.json',{'parent':'minecraft:block/block','textures':{'wood':f'{MOD}:block/rain_collector','metal':f'{MOD}:block/tank_top','particle':f'{MOD}:block/rain_collector'},'elements':[
    box([3,0,3],[13,4,13],'#metal'),box([1,4,1],[15,5,15],'#metal'),box([0,4,0],[16,14,2],'#wood'),box([0,4,14],[16,14,16],'#wood'),box([0,4,2],[2,14,14],'#wood'),box([14,4,2],[16,14,14],'#wood')]})
for n in ['water_tank','rain_collector']:
    js(ART/'blockstates'/f'{n}.json',{'variants':{'':{'model':f'{MOD}:block/{n}'}}})
    js(ART/'models/item'/f'{n}.json',{'parent':f'{MOD}:block/{n}'})

gui=Image.new('RGBA',(256,256));d=ImageDraw.Draw(gui)
d.rectangle((0,0,175,239),fill='#213039',outline='#101b23');d.rectangle((2,2,173,237),outline='#5e777b')
d.rectangle((4,4,171,23),fill='#304952');d.line((8,24,167,24),fill='#65bba8')
for x,y in [(20,44),(20,80),(116,44),(146,44),(146,80)]+[(8+c*18,158+r*18) for r in range(3) for c in range(9)]+[(8+c*18,216) for c in range(9)]:
    d.rectangle((x-1,y-1,x+16,y+16),fill='#101f28',outline='#587277');d.line((x,y+16,x+16,y+16),fill='#859794')
d.rectangle((52,48,102,57),fill='#101f28',outline='#486b6c');d.polygon([(102,44),(109,53),(102,61)],fill='#75a8a2')
d.rectangle((79,69,92,95),fill='#10212c',outline='#5e7b85');d.line((7,110,168,110),fill='#3c5459');d.line((7,142,168,142),fill='#3c5459')
save(gui,ART/'textures/gui/preparation_bench.png')

zh={
 'itemGroup.starfall_blueprint':'星落蓝图 · 基础工业',
 'block.starfall_blueprint.ore_meteor_phase':'陨相矿', 'block.starfall_blueprint.deepslate_ore_meteor_phase':'深层陨相矿',
 'block.starfall_blueprint.preparation_bench':'整备台',
 'block.starfall_blueprint.water_tank':'常压储水罐','block.starfall_blueprint.rain_collector':'营地集雨器',
 'message.starfall_blueprint.water':'工艺水：%s / 4000 mB',
 'tooltip.starfall_blueprint.mass':'每件干量：%s M', 'tooltip.starfall_blueprint.grade':'G%s%% · 完整度 I%s · 通用',
 'tooltip.starfall_blueprint.quality':'P90%% · 完整度 I%s · %s 通用晶料', 'tooltip.starfall_blueprint.tailings':'一批湿尾砂；含250mB工艺水，不可冒充晶料。',
 'message.starfall_blueprint.scan.found':'附近检测到微弱陨相信号', 'message.starfall_blueprint.scan.empty':'附近未检测到陨相信号',
 'gui.starfall_blueprint.ore':'原矿','gui.starfall_blueprint.fuel':'燃料','gui.starfall_blueprint.product':'晶料','gui.starfall_blueprint.tail':'尾砂',
 'gui.starfall_blueprint.state.0':'就绪 · 本批已完成','gui.starfall_blueprint.state.1':'整备中 · 工艺 PC-R01',
 'gui.starfall_blueprint.state.2':'待机：放入陨相原矿','gui.starfall_blueprint.state.3':'缺水：水桶右键补入',
 'gui.starfall_blueprint.state.4':'欠能：添加煤或木炭','gui.starfall_blueprint.state.5':'堵塞：清空晶料或尾砂槽',
 'gui.starfall_blueprint.state.6':'来料不符：检查矿物品质',
 'advancements.starfall_blueprint.root.title':'星落蓝图','advancements.starfall_blueprint.root.description':'从共同原矿开始建设营地',
 'advancements.starfall_blueprint.crystal.title':'第一批通用晶料','advancements.starfall_blueprint.crystal.description':'在整备台用水与燃料完成公共整备',
 'advancements.starfall_blueprint.connector.title':'省下每一星衡','advancements.starfall_blueprint.connector.description':'制造省能接头；右键装入整备台可节省10%加工能耗',
}
for name,label in zip(names,['陨相原矿','通用稳相晶料','陨相湿尾砂','基础省能接头','工具加固扣','盾面衬片','近距寻矿指示片','营地工务手册']): zh[f'item.{MOD}.{name}']=label
en={k:v for k,v in zh.items()}
en.update({f'item.{MOD}.{name}':label for name,label in zip(names,['Raw Meteor Phase Ore','Stable Common Crystal','Wet Meteor Tailings','Efficient Connector','Tool Brace','Shield Lining','Nearby Ore Indicator','Camp Field Manual'])})
en.update({'itemGroup.starfall_blueprint':'Starfall Blueprint · Basic Industry',f'block.{MOD}.preparation_bench':'Preparation Bench',f'block.{MOD}.ore_meteor_phase':'Meteor Phase Ore',f'block.{MOD}.deepslate_ore_meteor_phase':'Deepslate Meteor Phase Ore',
'block.starfall_blueprint.water_tank':'Atmospheric Water Tank','block.starfall_blueprint.rain_collector':'Camp Rain Collector','message.starfall_blueprint.water':'Process water: %s / 4000 mB',
'tooltip.starfall_blueprint.mass':'Dry mass per item: %s M','tooltip.starfall_blueprint.grade':'G%s%% / I%s / common','tooltip.starfall_blueprint.quality':'P90%% / I%s / %s common crystal','tooltip.starfall_blueprint.tailings':'One batch; retains 250 mB process water.',
'message.starfall_blueprint.scan.found':'Weak meteor-phase signal nearby','message.starfall_blueprint.scan.empty':'No meteor-phase signal nearby',
'gui.starfall_blueprint.ore':'Ore','gui.starfall_blueprint.fuel':'Fuel','gui.starfall_blueprint.product':'Crystal','gui.starfall_blueprint.tail':'Tail',
'gui.starfall_blueprint.state.0':'Ready / batch complete','gui.starfall_blueprint.state.1':'Processing / PC-R01','gui.starfall_blueprint.state.2':'Idle: insert raw ore','gui.starfall_blueprint.state.3':'Water: use water bucket','gui.starfall_blueprint.state.4':'Energy: add coal / charcoal','gui.starfall_blueprint.state.5':'Output blocked','gui.starfall_blueprint.state.6':'Invalid raw ore quality',
'advancements.starfall_blueprint.root.title':'Starfall Blueprint','advancements.starfall_blueprint.root.description':'Build a camp with common raw ore','advancements.starfall_blueprint.crystal.title':'First Common Crystal','advancements.starfall_blueprint.crystal.description':'Process ore with fuel and water','advancements.starfall_blueprint.connector.title':'Every Starbalance Counts','advancements.starfall_blueprint.connector.description':'Craft and install a connector to save 10% processing energy'})
js(ART/'lang/zh_cn.json',zh);js(ART/'lang/en_us.json',en)

def ingredient(item): return {'item':item if ':' in item else f'{MOD}:{item}'}
js(DATA/'recipe/preparation_bench.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['III','SCS','S S'],'key':{'I':ingredient('minecraft:iron_ingot'),'C':ingredient('minecraft:copper_ingot'),'S':ingredient('minecraft:cobblestone')},'result':{'id':f'{MOD}:preparation_bench','count':1}})
js(DATA/'recipe/field_manual.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[ingredient('minecraft:paper'),ingredient('minecraft:cobblestone')],'result':{'id':f'{MOD}:field_manual','count':1}})
js(DATA/'recipe/water_tank.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':[' G ','GIG',' G '],'key':{'G':ingredient('minecraft:glass'),'I':ingredient('minecraft:iron_ingot')},'result':{'id':f'{MOD}:water_tank','count':1}})
js(DATA/'recipe/rain_collector.json',{'type':f'{MOD}:rain_collector','category':'misc'})
for kind in ['connector','indicator','tool_brace','shield_lining','reinforce_tool','reinforce_shield']:
    js(DATA/'recipe'/f'{kind}.json',{'type':f'{MOD}:{kind}','category':'misc'})
for path in ['mineable/pickaxe','needs_iron_tool']:
    js(RES/'data/minecraft/tags/block'/f'{path}.json',{'replace':False,'values':[f'{MOD}:ore_meteor_phase',f'{MOD}:deepslate_ore_meteor_phase']+([f'{MOD}:preparation_bench',f'{MOD}:water_tank',f'{MOD}:rain_collector'] if path=='mineable/pickaxe' else [])})
js(DATA/'worldgen/configured_feature/meteor_phase.json',{'type':'minecraft:ore','config':{'size':4,'discard_chance_on_air_exposure':0.5,'targets':[
    {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':f'{MOD}:ore_meteor_phase'}},
    {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'},'state':{'Name':f'{MOD}:deepslate_ore_meteor_phase'}}]}})
js(DATA/'worldgen/placed_feature/meteor_phase.json',{'feature':f'{MOD}:meteor_phase','placement':[
    {'type':'minecraft:rarity_filter','chance':4},{'type':'minecraft:in_square'},
    {'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':-64},'max_inclusive':{'absolute':32}}},
    {'type':'minecraft:biome'}]})
js(DATA/'neoforge/biome_modifier/meteor_phase.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':f'{MOD}:meteor_phase','step':'underground_ores'})

def achievement(name,item,parent=None):
    data={'display':{'icon':{'id':f'{MOD}:{item}'},'title':{'translate':f'advancements.{MOD}.{name}.title'},'description':{'translate':f'advancements.{MOD}.{name}.description'},'frame':'task','show_toast':True,'announce_to_chat':False},
          'criteria':{'obtained':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':f'{MOD}:{item}'}]}}}}
    if parent: data['parent']=f'{MOD}:{parent}'
    else: data['display']['background']='minecraft:textures/gui/advancements/backgrounds/stone.png'
    js(DATA/'advancement'/f'{name}.json',data)
achievement('root','raw_meteor_phase');achievement('crystal','stable_crystal','root');achievement('connector','efficient_connector','crystal')
for name,trigger in [('preparation_bench','minecraft:iron_ingot'),('field_manual','minecraft:paper'),('water_tank','minecraft:glass')]:
    js(DATA/'advancement/recipes'/f'{name}.json',{'criteria':{'obtained':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':trigger}]}}},'rewards':{'recipes':[f'{MOD}:{name}']}})

# Tiny empty GameTest template, generated without third-party NBT dependencies.
def utf(s):
    b=s.encode();return struct.pack('>H',len(b))+b
def tag(t,n,payload):return bytes([t])+utf(n)+payload
nbt=b'\x0a\x00\x00'+tag(3,'DataVersion',struct.pack('>i',3955))+tag(9,'size',b'\x03'+struct.pack('>i',3)+struct.pack('>iii',5,5,5))+tag(9,'palette',b'\x0a'+struct.pack('>i',1)+tag(8,'Name',utf('minecraft:air'))+b'\x00')+tag(9,'blocks',b'\x0a'+struct.pack('>i',0))+tag(9,'entities',b'\x0a'+struct.pack('>i',0))+b'\x00'
p=DATA/'structure/empty.nbt';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(gzip.compress(nbt,mtime=0))

# Review sheet at nearest-neighbor scale: no resampling blur in shipped assets.
sheet=Image.new('RGB',(1000,620),'#14232c');d=ImageDraw.Draw(sheet)
try: font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',13); title=ImageFont.truetype('C:/Windows/Fonts/consolab.ttf',26)
except OSError: font=title=ImageFont.load_default()
d.text((30,20),'STARFALL BLUEPRINT / CAMP INDUSTRY',font=title,fill='#c3e9db')
d.text((30,60),'Original 16px assets / slate steel + copper + phase teal',font=font,fill='#8aa8ad')
for idx,(name,im) in enumerate(list(textures.items())+[(n,item(n)) for n in names]):
    col=idx%7;row=idx//7;x=30+col*138;y=115+row*156
    sheet.paste(im.resize((80,80),Image.Resampling.NEAREST),(x,y),im.resize((80,80),Image.Resampling.NEAREST))
    label=name.replace('preparation_','bench_').replace('meteor_phase','meteor').replace('efficient_connector','connector')
    for line,part in enumerate([label[:16],label[16:]]):d.text((x,y+88+line*18),part,font=font,fill='#9db8b8')
save(sheet,ROOT/'docs/art-preview.png')
save(gui.crop((0,0,176,240)).resize((528,720),Image.Resampling.NEAREST),ROOT/'docs/gui-preview.png')
print('Generated textures, models, recipes, languages, advancements, worldgen and test template.')
