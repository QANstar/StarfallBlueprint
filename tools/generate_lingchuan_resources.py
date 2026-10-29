"""Deterministic original pixel assets and data for the Black Dog armour route."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/starfall_blueprint'
DATA = RES / 'data/starfall_blueprint'
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
def image(path, im):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)

STATIONS = {
    'lc_alloy_forge': ('层相合金熔铸炉', 'Laminar Alloy Forge'),
    'lc_parts_fabricator': ('装甲部件制造台', 'Armour Parts Fabricator'),
    'lc_armor_assembler': ('装甲组合台', 'Armour Assembly Gantry'),
    'lc_armor_charger': ('装甲充电台', 'Armour Charging Dock'),
    'lc_research_station': ('装甲研发台', 'Armour Research Station'),
}
PARTS = [('head','头部','Head'),('chest','胸部','Chest'),('core','能量核心','Energy Core'),('legs','腿部','Legs'),('feet','脚部','Feet'),('hands','手部','Hands'),('back','背部','Back'),('shoulders','肩部','Shoulders')]
EXTRA = {
    'core_compressed': ('压缩能源核心 II', 'Compressed Core II'),
    'core_reactor': ('微型反应堆核心 III', 'Micro Reactor Core III'),
    'head_night': ('夜视观测头部', 'Night Optics Head'),
    'head_aquatic': ('水下呼吸头部', 'Aquatic Support Head'),
    'legs_sprint': ('疾跑驱动腿部', 'Sprint Drive Legs'),
    'feet_jump': ('跃升驱动脚部', 'Jump Drive Feet'),
    'back_flight': ('矢量飞行背部', 'Vector Flight Back'),
    'chest_shield': ('能量护盾胸部', 'Energy Shield Chest'),
    'chest_heavy': ('重层防御胸部', 'Heavy Plating Chest'),
    'back_cloak': ('光学隐身背部', 'Optical Cloak Back'),
    'hands_laser': ('激光武器手部', 'Laser Emitter Hands'),
    'shoulders_missile': ('飞弹发射肩部', 'Missile Pod Shoulders'),
    'hands_cannon': ('贯穿采掘炮手部', 'Penetrating Mining Cannon Hands'),
    'shoulders_turret': ('浮游炮肩部', 'Floating Turret Shoulders'),
}
RESEARCH = [
    ('laminar_metallurgy','层相冶金','Laminar Metallurgy'),('blackdog_basics','黑狗基础装甲','Black Dog Basics'),
    ('compressed_core','压缩能源 II','Compressed Core II'),('micro_reactor','微型反应堆 III','Micro Reactor III'),
    ('night_optics','夜视观测','Night Optics'),('aquatic_support','水下呼吸','Aquatic Support'),
    ('sprint_drive','疾跑驱动','Sprint Drive'),('jump_drive','跃升驱动','Jump Drive'),
    ('flight_drive','矢量飞行','Vector Flight'),('energy_shield','能量护盾','Energy Shield'),
    ('heavy_plating','重层防御','Heavy Plating'),('optical_cloak','光学隐身','Optical Cloak'),
    ('laser_emitter','激光武器','Laser Emitter'),('missile_pod','肩部飞弹','Missile Pod'),
    ('mining_cannon','贯穿采掘炮','Mining Cannon'),('floating_turret','浮游炮','Floating Turret')]
names = {'laminar_crystal': ('层相晶', 'Laminar Crystal'), 'laminar_alloy': ('层相合金', 'Laminar Alloy'),
         'blackdog_watch': ('夜巡 · 黑狗武装腕表', 'Nightwatch · Black Dog Armour Watch'),
         'blackdog_fire_control': ('黑狗火控终端', 'Black Dog Fire Control')}
names.update(STATIONS)
for key, zh, en in PARTS: names['blackdog_'+key+'_basic'] = ('黑狗 · 基础'+zh, 'Black Dog · Basic '+en)
for key, pair in EXTRA.items(): names['blackdog_'+key] = ('黑狗 · '+pair[0], 'Black Dog · '+pair[1])
for key,zh,en in PARTS:
    if key!='core':
        names['blackdog_'+key+'_reinforced']=('黑狗 · II级强化'+zh+'基底','Black Dog · Tier II '+en+' Chassis')
        names['blackdog_'+key+'_elite']=('黑狗 · III级精锐'+zh+'基底','Black Dog · Tier III '+en+' Chassis')
    names['blackdog_'+key+'_basic']=('黑狗 · I级基础'+zh+('' if key=='core' else '基底'),'Black Dog · Tier I '+en+('' if key=='core' else ' Chassis'))
COMPONENTS=[('night','夜视观测','Night Vision'),('breathing','水下呼吸','Water Breathing'),('sprint','疾跑驱动','Sprint Drive'),('jump','跃升驱动','Jump Drive'),('flight','矢量推进','Vector Flight'),('shield','能量护盾','Energy Shield'),('heavy','重层防御','Heavy Plating'),('cloak','光学隐身','Optical Cloak'),('laser','激光发射','Laser Emitter'),('missile','飞弹发射','Missile Pod'),('cannon','贯穿采掘','Mining Cannon'),('turret','浮游炮','Floating Turret')]
for key,zh,en in COMPONENTS:names['blackdog_component_'+key]=(zh+'组件',en+' Component')
for key, zh, en in PARTS:
    if key in ('head','chest','legs','feet'): names['blackdog_armor_'+key] = ('黑狗战甲 · '+zh, 'Black Dog Armour · '+en)
definitions = {key: {'id': key, 'name': pair[0], 'unit':'件','icon':'machine' if key in STATIONS else 'gear', 'note':'岭川黑狗模块化武装', 'external':False, 'vanilla':False, 'mass':100, 'category':'岭川黑狗战甲','subcategory':'专用机器' if key in STATIONS else '装甲部件','recipes':[],'uses':[]} for key,pair in names.items()}
write(ASSETS / 'lingchuan.json', definitions)
template = RES / 'data/starfall_blueprint_lingchuan/structure/empty.nbt'
template.parent.mkdir(parents=True, exist_ok=True)
template.write_bytes((RES / 'data/starfall_blueprint/structure/empty.nbt').read_bytes())

BLACK = (23,25,33,255); EDGE = (66,71,85,255); RED = (181,39,58,255); GLOW = (255,87,97,255)
for index, key in enumerate(names):
    if key in STATIONS: continue
    im=Image.new('RGBA',(16,16)); d=ImageDraw.Draw(im)
    if key=='blackdog_watch':
        d.rectangle((5,0,10,15),fill=BLACK);d.rectangle((3,4,12,11),fill=EDGE);d.rectangle((4,5,11,10),fill=BLACK);d.rectangle((6,6,9,9),fill=RED);d.line((7,6,7,8,9,8),fill=GLOW)
    elif key.startswith('laminar_'):
        d.polygon([(2,6),(11,2),(14,5),(14,10),(5,14),(2,11)],fill=EDGE,outline=BLACK)
        for y in (6,8,10):d.line((3,y,12,y-3),fill=GLOW if key.endswith('crystal') else RED,width=1)
        d.line((4,12,13,8),fill=(127,136,153,255))
    else:
        part=key.replace('blackdog_armor_','blackdog_').split('_')[1]
        if part=='head':
            d.polygon([(5,1),(10,1),(12,3),(12,11),(10,14),(5,14),(3,11),(3,3)],fill=EDGE)
            d.polygon([(5,3),(10,3),(11,5),(10,11),(8,13),(5,11),(4,5)],fill=BLACK)
            d.line((4,6,6,7),fill=GLOW);d.line((9,7,11,6),fill=GLOW);d.line((6,12,9,12),fill=(64,69,82,255))
        elif part=='chest':
            d.polygon([(1,3),(5,1),(10,1),(14,3),(12,7),(11,14),(4,14),(3,7)],fill=EDGE);d.rectangle((4,3,11,12),fill=BLACK)
            d.line((4,4,6,5),fill=RED);d.line((9,5,11,4),fill=RED);d.rectangle((7,5,8,8),fill=GLOW)
        elif part in ('legs','feet'):d.rectangle((3,2,12,6),fill=EDGE);d.rectangle((3,5,6,14),fill=BLACK);d.rectangle((9,5,12,14),fill=BLACK);d.line((4,7,4,12),fill=RED);d.line((10,7,10,12),fill=RED)
        elif part=='core':d.ellipse((2,2,13,13),fill=EDGE,outline=BLACK);d.ellipse((4,4,11,11),fill=RED);d.rectangle((6,5,9,10),fill=GLOW)
        elif part=='back':d.rectangle((4,2,11,13),fill=EDGE);d.rectangle((2,4,5,12),fill=BLACK);d.rectangle((10,4,13,12),fill=BLACK);d.rectangle((6,4,9,10),fill=RED);d.rectangle((3,12,4,14),fill=GLOW);d.rectangle((11,12,12,14),fill=GLOW)
        elif part=='shoulders':d.rectangle((1,4,5,11),fill=EDGE);d.rectangle((10,4,14,11),fill=EDGE);d.rectangle((5,3,10,5),fill=BLACK);d.rectangle((2,5,4,7),fill=RED);d.rectangle((11,5,13,7),fill=RED)
        else:d.rectangle((2,3,6,12),fill=EDGE);d.rectangle((9,3,13,12),fill=EDGE);d.rectangle((3,5,5,9),fill=RED);d.rectangle((10,5,12,9),fill=RED)
        # Upgrade insignia differs from the basic module silhouette.
        if not key.endswith('basic') and not key.startswith('blackdog_armor_'):
            d.rectangle((12,12,15,15),fill=RED);d.point((13,13),fill=GLOW)
    if key.startswith('blackdog_component_'):
        im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);ability=key.removeprefix('blackdog_component_')
        d.rectangle((3,3,12,12),fill=BLACK,outline=EDGE)
        for n in (4,7,10):
            d.rectangle((n,1,n+1,2),fill=EDGE);d.rectangle((n,13,n+1,14),fill=EDGE)
            d.rectangle((1,n,2,n+1),fill=EDGE);d.rectangle((13,n,14,n+1),fill=EDGE)
        if ability=='night':d.ellipse((4,6,7,9),outline=GLOW);d.ellipse((8,6,11,9),outline=GLOW)
        elif ability=='breathing':
            d.ellipse((5,7,9,11),outline=GLOW);d.rectangle((9,4,10,5),fill=GLOW);d.point((6,4),fill=GLOW)
        elif ability in ('sprint','jump'):d.line([(5,5),(9,7),(5,10)] if ability=='sprint' else [(5,9),(7,5),(10,9)],fill=GLOW,width=2)
        elif ability=='flight':d.polygon([(4,5),(7,8),(11,5),(9,10),(7,9),(5,10)],fill=RED)
        elif ability=='shield':d.polygon([(5,4),(10,4),(10,8),(7,11),(5,8)],outline=GLOW)
        elif ability=='heavy':
            for n in (5,7,9):d.line((5,n,10,n),fill=GLOW)
        elif ability=='cloak':
            for n in (5,7,9):d.point((n,n),fill=GLOW);d.point((n,11-n),fill=RED)
        elif ability=='laser':d.line((4,10,11,4),fill=GLOW,width=2)
        elif ability=='missile':d.polygon([(5,10),(6,6),(10,4),(10,9),(7,10)],fill=RED);d.point((10,4),fill=GLOW)
        elif ability=='cannon':d.ellipse((4,4,11,11),outline=GLOW);d.rectangle((7,6,8,9),fill=RED)
        else:d.line((5,5,10,10),fill=GLOW);d.line((5,10,10,5),fill=GLOW);d.rectangle((7,6,8,9),fill=RED)
    image(ASSETS / f'textures/item/{key}.png',im)
    write(ASSETS / f'models/item/{key}.json', {'parent':'minecraft:item/generated','textures':{'layer0':f'starfall_blueprint:item/{key}'}})

for style in ('casing','panel','vent','screen','core'):
    im=Image.new('RGBA',(16,16),BLACK);d=ImageDraw.Draw(im)
    d.rectangle((0,0,15,15),outline=EDGE);d.rectangle((2,2,13,13),outline=(41,45,57,255))
    for xy in ((1,1),(14,1),(1,14),(14,14)):d.point(xy,fill=(132,128,133,255))
    if style=='panel':d.rectangle((3,5,12,6),fill=RED);d.rectangle((3,9,8,10),fill=RED)
    if style=='vent':
        for y in range(3,13,3):d.rectangle((3,y,12,y+1),fill=(7,8,12,255));d.point((12,y),fill=RED)
    if style=='screen':
        d.rectangle((3,3,12,11),fill=(48,17,29,255));d.line((4,9,6,6,8,8,11,5),fill=GLOW);d.rectangle((4,12,6,12),fill=RED)
    if style=='core':d.ellipse((3,3,12,12),fill=RED);d.ellipse((5,5,10,10),fill=GLOW)
    image(ASSETS/f'textures/block/lc_{style}.png',im)
def cuboid(a,b,texture):
    return {'from':a,'to':b,'faces':{face:{'texture':'#'+texture} for face in ('north','south','east','west','up','down')}}
for key in STATIONS:
    variants={}
    for part in range(8):
        upper=part>=4; right=part%2; rear=(part//2)%2
        elements=[]
        if key=='lc_alloy_forge':
            elements=[cuboid([0,0,0],[16,16,16],'casing'),cuboid([1,2,0],[15,14,1],'core' if upper else 'vent')]
        else:
            if not upper:
                elements += [cuboid([0,0,0],[16,4,16],'casing'),cuboid([1,4,1],[15,13,15],'vent'),cuboid([0,13,0],[16,16,16],'panel')]
            else:
                px=12 if right else 0;pz=12 if rear else 0
                elements += [cuboid([px,0,pz],[px+4,16,pz+4],'casing'),cuboid([0,13,0],[16,16,16],'panel')]
                if key=='lc_research_station':elements += [cuboid([2,1,5],[14,12,9],'screen')]
                if key=='lc_parts_fabricator':elements += [cuboid([6,0,6],[10,11,10],'core')]
                if key=='lc_armor_charger':elements += [cuboid([4,0,7],[12,10,9],'core')]
        model=f'{key}_part{part}'
        write(ASSETS/f'models/block/{model}.json',{'textures':{**{s:f'starfall_blueprint:block/lc_{s}' for s in ('casing','panel','vent','screen','core')},'particle':'starfall_blueprint:block/lc_casing'},'elements':elements})
        for facing, rotation in [('north',0),('east',90),('south',180),('west',270)]:
            for lit in ('false','true'):variants[f'facing={facing},lit={lit},part={part}']={'model':f'starfall_blueprint:block/{model}','y':rotation}
    write(ASSETS/f'blockstates/{key}.json',{'variants':variants})
    write(ASSETS/f'models/item/{key}.json',{'parent':f'starfall_blueprint:block/{key}_part0'})
    write(DATA/f'recipe/{key}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['SCS','BDB','SFS'],'key':{
        'S':{'item':'starfall_blueprint:ordinary_steel'},'C':{'item':'starfall_blueprint:control_board'},'B':{'item':'starfall_blueprint:stable_crystal'},
        'D':{'item':'minecraft:diamond' if key in ('lc_alloy_forge','lc_research_station') else 'starfall_blueprint:laminar_alloy'},'F':{'item':'starfall_blueprint:machine_frame'}},
        'result':{'id':f'starfall_blueprint:{key}','count':1}})
    write(DATA/f'loot_table/blocks/{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'starfall_blueprint:{key}'}]}]})

# Complete original 64x32 humanoid armour UV sheets, using a consistent black/red panel motif.
for layer in (1,2):
    im=Image.new('RGBA',(64,32),(0,0,0,0));d=ImageDraw.Draw(im)
    areas=[(0,0,32,16),(16,16,40,32),(40,16,64,32),(0,16,16,32)]
    for x1,y1,x2,y2 in areas:
        d.rectangle((x1,y1,x2-1,y2-1),fill=BLACK)
        for x in range(x1+2,x2,4):d.line((x,y1+1,x,y2-2),fill=(35,39,49,255))
        d.line((x1+1,y1+2,x2-2,y1+2),fill=EDGE)
        d.line((x1+1,y1+5,x2-2,y1+5),fill=RED)
        d.line((x1+2,y2-4,x2-3,y2-4),fill=RED)
    if layer==1:
        d.rectangle((8,5,15,6),fill=GLOW);d.polygon([(21,21),(26,21),(24,26)],fill=GLOW)
    image(ASSETS/f'textures/models/armor/blackdog_layer_{layer}.png',im)

im=Image.new('RGBA',(128,128),BLACK);d=ImageDraw.Draw(im)
for y in range(0,128,16):
    for x in range(0,64,16):
        d.rectangle((x,y,x+15,y+15),outline=(57,61,73,255));d.line((x+2,y+13,x+13,y+13),fill=(103,29,41,255))
        d.rectangle((x+3,y+3,x+12,y+11),fill=(29,32,42,255))
d.rectangle((0,64,63,127),fill=(30,35,46,255))
d.line((0,64,63,64),fill=(46,52,67,255));d.line((0,64,0,127),fill=(40,46,59,255))
d.rectangle((64,0,95,127),fill=RED)
for y in range(0,128,8):d.line((65,y+2,94,y+2),fill=GLOW)
d.rectangle((96,0,127,127),fill=(52,60,76,255))
for y in range(0,128,4):d.line((97,y,126,y),fill=(76,86,105,255))
image(ASSETS/'textures/models/armor/blackdog_exosuit.png',im)

ZH = {
    'itemGroup.starfall_blueprint.best':'星落蓝图 · 贝斯特工务', 'itemGroup.starfall_blueprint.lingchuan':'星落蓝图 · 岭川黑狗战甲',
    'lc.research_cost':'电力解锁：%s XH；或材料解锁：层相合金 ×%s + 通用晶料 ×%s',
    'lc.research_requires':'全部前置：%s', 'lc.no_prerequisite':'无',
    'lc.research_locked':'前置科技未解锁；请在装甲研发台查看科技树', 'lc.materials_missing':'背包材料不足；悬停按钮查看用量',
    'lc.power_missing':'电力不足或武器正在冷却', 'lc.output_full':'请先取走输出；首次组装时腕表槽必须为空',
    'lc.researched':'已解锁：%s', 'lc.incompatible':'八部位不完整、等级超过核心或负载超限；换件一次只放一件',
    'lc.watch_missing':'请在腕表槽放入已收回的完整武装腕表', 'lc.watch_empty':'空白腕表没有战甲；需在装甲组合台组装八个部位',
    'lc.already_deployed':'请先收回当前战甲', 'lc.armor_slots_full':'请先清空头、胸、腿、脚四个装备栏',
    'lc.deployed':'黑狗战甲已展开 · 右键腕表整套收回', 'lc.recalled':'黑狗战甲已收回腕表',
    'lc.watch_hint':'右键：整套穿戴／收回；部件只能回组合台替换，展开时不能丢弃腕表',
    'lc.hud':'黑狗 · %s / %s XH · 核心 %s 阶', 'lc.core_budget':'核心负载上限：%s；电量同时承担主动防护与能力续航',
    'lc.module_spec':'%s · %s 阶 · 负载 %s', 'lc.module_install':'在装甲组合台对应部位槽安装／换回旧部件',
    'lc.ability.passive':'基础结构：仅提供护甲；核心缓慢回充，八个基础部件总负载 8',
    'lc.ability.night':'夜视：80 XH/秒', 'lc.ability.breathing':'水下呼吸：120 XH/秒',
    'lc.ability.sprint':'疾跑：移速 +25%，300 XH/秒', 'lc.ability.jump':'跃升：跳跃强度 +0.25，150 XH/秒',
    'lc.ability.flight':'飞行：双击跳跃起飞，100 XH/刻；断电会失去飞行',
    'lc.ability.shield':'能量护盾：吸收 80% 来袭伤害，每点 500 XH；剩余电量限制吸收',
    'lc.ability.heavy':'重层防御：有电时护甲额外 +6', 'lc.ability.cloak':'光学隐身：800 XH/秒；同时隐藏战甲外观',
    'lc.ability.laser':'激光：火控终端射击，1200 XH/发，8伤害，10刻冷却',
    'lc.ability.missile':'飞弹：火控终端射击，5000 XH/发，爆炸强度1，40刻冷却',
    'lc.ability.cannon':'贯穿采掘炮：8000 XH/发 + 每破坏方块1000 XH，12格直线采掘，40刻冷却',
    'lc.ability.turret':'浮游炮：12格内自动射击可见敌对生物，每秒最多1次，600 XH/发、4伤害',
    'lc.require_deployed':'请先展开完整黑狗战甲', 'lc.weapon_missing':'当前配置没有激光、飞弹或采掘炮模块',
    'lc.fire_control_hint':'装备战甲后右键开火；潜行右键切换已安装的武器；采掘遵守领地和方块破坏事件',
    'lc.fire_control_cost':'层相合金 ×2 + 控制板 ×2 + 红石 ×4；10000 XH；需激光武器科技',
    'lc.manufacture_cost':'从背包取料：层相合金 ×%s + 层相晶 ×%s + 控制板 ×%s；%s XH',
    'lc.page':'部件 %s / %s >', 'lc.payment_toggle':'切换研发支付', 'lc.payment_hint':'点击切换：工位电力 或 背包材料。只收取当前选择的一项费用。',
    'lc.payment_materials':'当前支付：背包材料（不扣电）', 'lc.payment_energy':'当前支付：工位电力（不扣材料）',
    'lc.forge_crystal':'定相层相晶 ×2', 'lc.forge_alloy':'熔铸层相合金 ×4',
    'lc.forge_crystal_cost':'通用晶料 ×4 + 下界石英 ×4 + 紫水晶碎片 ×2；10000 XH',
    'lc.forge_alloy_cost':'层相晶 ×2 + 下界合金锭 ×1 + 普通钢 ×4；40000 XH',
    'lc.assemble':'首次组装 · 10000 XH', 'lc.refit':'替换部件 · 5000 XH',
    'lc.assemble_hint':'八部位槽各放一件，腕表槽与输出槽为空；制造一只保存整套部件的夜巡腕表',
    'lc.refit_hint':'腕表放右侧腕表槽，左侧只放需要替换的部件；旧部件返回原槽，保留电量并按新核心容量裁定',
    'lc.assembler_instruction':'八部位逐槽装入；换件时在右侧放入收回的腕表',
    'lc.budget_hint':'核心 I／II／III：负载 8／18／28；部件等级不得超过核心',
    'lc.charger_instruction':'将已收回的腕表放入右侧腕表槽；连接公共电网',
    'lc.charger_rate':'每刻最多补充 4096 XH；工位接收电力上限 2048 XH/刻',
    'lc.station_power':'工位：%s / 2000000 XH', 'lc.watch_slot':'腕表','lc.output_slot':'输出',
}
EN = {
    'itemGroup.starfall_blueprint.best':'Starfall · Best Engineering','itemGroup.starfall_blueprint.lingchuan':'Starfall · Lingchuan Black Dog',
    'lc.research_cost':'Unlock with %s XH OR %s Laminar Alloy + %s Stable Crystal',
    'lc.research_requires':'All prerequisites: %s','lc.no_prerequisite':'None',
    'lc.research_locked':'Research prerequisites are missing','lc.materials_missing':'Missing inventory materials; hover the button for costs',
    'lc.power_missing':'Not enough energy, or weapon cooling down','lc.output_full':'Empty the output; initial assembly also needs an empty watch bay',
    'lc.researched':'Unlocked: %s','lc.incompatible':'Missing parts, incompatible tier or load over budget; insert one module per bay',
    'lc.watch_missing':'Insert a complete, recalled armour watch','lc.watch_empty':'Blank watch: assemble all eight parts at the gantry',
    'lc.already_deployed':'Recall the current suit first','lc.armor_slots_full':'Empty all four armour slots first',
    'lc.deployed':'Black Dog deployed; use the watch again to recall','lc.recalled':'Black Dog recalled',
    'lc.watch_hint':'Use: deploy / recall the whole suit. Refit at the gantry. Deployed watch cannot be dropped.',
    'lc.hud':'Black Dog · %s / %s XH · Core tier %s','lc.core_budget':'Core load budget: %s; energy also powers protection',
    'lc.module_spec':'%s · Tier %s · Load %s','lc.module_install':'Install or swap in the matching gantry bay',
    'lc.ability.passive':'Passive armour; core regenerates slowly. Eight basic parts use 8 load.',
    'lc.ability.night':'Night vision: 80 XH/s','lc.ability.breathing':'Water breathing: 120 XH/s',
    'lc.ability.sprint':'Sprint: +25%% speed, 300 XH/s','lc.ability.jump':'Jump: +0.25 strength, 150 XH/s',
    'lc.ability.flight':'Flight: double-tap jump, 100 XH/t; power loss disables flight',
    'lc.ability.shield':'Shield: absorbs up to 80%% damage, 500 XH per point', 'lc.ability.heavy':'Heavy plating: +6 armour while powered',
    'lc.ability.cloak':'Cloak: 800 XH/s; conceals the worn armour too',
    'lc.ability.laser':'Laser: 1200 XH, 8 damage, 10t cooldown', 'lc.ability.missile':'Missile: 5000 XH, explosion power 1, 40t cooldown',
    'lc.ability.cannon':'Mining cannon: 8000 XH + 1000/block, 12-block line, 40t cooldown',
    'lc.ability.turret':'Floating turret: visible hostiles within 12 blocks, 600 XH, 4 damage, once per second',
    'lc.require_deployed':'Deploy a complete Black Dog suit first','lc.weapon_missing':'Install a laser, missile or mining cannon module',
    'lc.fire_control_hint':'Use to fire; sneak-use to cycle installed weapons. Mining respects block protection events.',
    'lc.fire_control_cost':'2 Laminar Alloy + 2 Control Boards + 4 Redstone; 10000 XH; laser research',
    'lc.manufacture_cost':'From inventory: %s Laminar Alloy + %s Laminar Crystal + %s Control Board; %s XH',
    'lc.page':'Parts %s / %s >','lc.payment_toggle':'Toggle payment','lc.payment_hint':'Choose workstation energy OR inventory materials; never both.',
    'lc.payment_materials':'Payment: materials only','lc.payment_energy':'Payment: energy only',
    'lc.forge_crystal':'Form Laminar Crystal x2','lc.forge_alloy':'Cast Laminar Alloy x4',
    'lc.forge_crystal_cost':'4 Stable Crystal + 4 Quartz + 2 Amethyst Shards; 10000 XH',
    'lc.forge_alloy_cost':'2 Laminar Crystal + 1 Netherite Ingot + 4 Ordinary Steel; 40000 XH',
    'lc.assemble':'Assemble · 10000 XH','lc.refit':'Refit · 5000 XH',
    'lc.assemble_hint':'Fill all eight bays; empty the watch and output bays. Produces one complete Nightwatch.',
    'lc.refit_hint':'Insert a recalled watch and only the replacement modules; old modules return to those bays. Charge is retained up to the new core capacity.',
    'lc.assembler_instruction':'Eight matching bays; insert the recalled watch on the right to refit',
    'lc.budget_hint':'Core I/II/III: 8/18/28 load; no module may exceed the core tier',
    'lc.charger_instruction':'Insert a recalled watch on the right; connect to the public power grid',
    'lc.charger_rate':'Up to 4096 XH/t charging; power input is limited to 2048 XH/t',
    'lc.station_power':'Station: %s / 2000000 XH','lc.watch_slot':'Watch','lc.output_slot':'Out',
}
ZH.update({
    'lc.watch_configuration':'基底护甲 %s · 总负载 %s/%s · 已装组件 %s',
    'lc.chassis_interfaces':'基底接口容量：%s；功能来自可独立装卸的组件',
    'lc.component_install':'在装甲组合台的组件页安装或拆卸',
    'lc.legacy_component':'旧版一体部件；已装腕表会自动转换为基底＋独立组件',
    'lc.incompatible':'基底不完整、组件重复、等级不匹配或接口／核心负载超限',
    'lc.assembler_instruction':'八部位选择 I／II／III 级基底；功能组件在组件页安装',
    'lc.budget_hint':'基底接口 2／4／6；核心总负载 12／24／40',
    'lc.assembly_tab_base':'查看基底换装', 'lc.assembly_tab_components':'管理功能组件',
    'lc.component_tab_hint':'在基底与组件视图间切换；腕表保留在原槽',
    'lc.component_inputs':'待安装组件', 'lc.component_installed':'腕表已装 · 点击拆回',
    'lc.component_remove_hint':'%s：点击拆回左侧空槽，1000 XH',
    'lc.assemble_hint':'先放入八个基底，可在组件页加入功能组件；腕表和输出槽须为空',
    'lc.refit_hint':'放入已收回腕表；基底页放替换基底，组件页放新增组件。旧基底退回原槽，已装组件可逐个拆回。',
    'lc.ability.passive':'基底仅提供结构、护甲和组件接口，不自带特殊能力',
    'lc.canvas_title':'黑狗武装 · 技术档案', 'lc.canvas_progress':'研发进度 %s / %s',
    'lc.canvas_overview':'全览', 'lc.canvas_hint':'滚轮缩放 · 按住拖动画布 · 单击选择 · R 定位起点',
    'lc.canvas_done':'已完成', 'lc.canvas_available':'可研发', 'lc.canvas_locked':'前置未满足',
    'lc.canvas_node':'技术节点 LC-%s', 'lc.canvas_prerequisites':'全部前置', 'lc.canvas_payment':'研发费用',
    'lc.canvas_material_cost':'层相合金 ×%s\n通用晶料 ×%s', 'lc.canvas_energy_cost':'%s XH 工位电力',
    'lc.canvas_materials':'支付：材料 · 点击切换', 'lc.canvas_energy':'支付：电力 · 点击切换',
    'lc.canvas_research':'确认研发',
})
EN.update({
    'lc.watch_configuration':'Chassis armour %s · Load %s/%s · Components %s',
    'lc.chassis_interfaces':'Chassis interface capacity: %s; functions use detachable components',
    'lc.component_install':'Install or remove on the assembly component page',
    'lc.legacy_component':'Legacy integrated part; assembled watches migrate to chassis + components',
    'lc.incompatible':'Missing chassis, duplicate component, incompatible tier or interface/core overload',
    'lc.assembler_instruction':'Choose tier I/II/III chassis; install functions on the component page',
    'lc.budget_hint':'Interfaces 2/4/6; total core load 12/24/40',
    'lc.assembly_tab_base':'Chassis view', 'lc.assembly_tab_components':'Function components',
    'lc.component_tab_hint':'Switch between chassis and component views; watch stays in its bay',
    'lc.component_inputs':'Components to install','lc.component_installed':'Installed · click to remove',
    'lc.component_remove_hint':'%s: remove into an empty input bay, 1000 XH',
    'lc.assemble_hint':'Insert eight chassis; optionally add components. Watch and output bays must be empty.',
    'lc.refit_hint':'Insert a recalled watch; replace chassis and add components. Old chassis return to their bays; components can be removed separately.',
    'lc.ability.passive':'Chassis provides armour and component interfaces; no built-in special ability',
    'lc.canvas_title':'BLACK DOG / RESEARCH','lc.canvas_progress':'Research %s / %s',
    'lc.canvas_overview':'Fit all','lc.canvas_hint':'Wheel: zoom · Drag: pan · Click: select · R: home',
    'lc.canvas_done':'COMPLETE','lc.canvas_available':'AVAILABLE','lc.canvas_locked':'LOCKED',
    'lc.canvas_node':'Research LC-%s','lc.canvas_prerequisites':'All prerequisites','lc.canvas_payment':'Research cost',
    'lc.canvas_material_cost':'Laminar Alloy x%s\nStable Crystal x%s','lc.canvas_energy_cost':'%s XH station energy',
    'lc.canvas_materials':'Pay: materials · toggle','lc.canvas_energy':'Pay: energy · toggle','lc.canvas_research':'Research selected',
})
DESCRIPTIONS=[
    ('在专用熔铸炉制造层相晶和层相合金，为黑狗武装提供材料。','Form laminar crystal and alloy at the dedicated forge.'),
    ('开放八部位I级基底和基础核心。每个基底有2点组件接口，核心总负载12。','Unlock eight tier I chassis and a basic core. Each body part has 2 interface points. Core load budget: 12.'),
    ('开放II级强化基底和压缩核心。基底接口提高至4，支持II级组件；核心总负载24。','Unlock tier II chassis with 4 interface points and tier II functions. Core load budget: 24.'),
    ('开放III级精锐基底和微型反应堆。基底接口6，支持III级组件；核心总负载40。','Unlock tier III chassis with 6 interface points and tier III functions. Core load budget: 40.'),
    ('制造夜视观测组件，改善暗处视野。需要I级头部基底，占1接口。','Improves vision in darkness. Requires tier I head chassis and 1 interface point.'),
    ('制造水下呼吸组件，支持水下活动。需要I级头部基底，占1接口。','Provides water breathing. Requires tier I head chassis and 1 interface point.'),
    ('制造疾跑驱动组件，安装在腿部基底。持续疾跑消耗核心电量。','Install sprint assistance on the leg chassis; running consumes core power.'),
    ('制造跃升驱动组件，安装在脚部基底，提高跳跃能力。','Install jump assistance on the feet chassis.'),
    ('制造矢量推进组件，安装在II级以上背部基底，占3接口。双击跳跃起飞。','Flight component requires a tier II back chassis and 3 interface points. Double-tap jump to fly.'),
    ('制造能量护盾组件，消耗核心电力吸收伤害。需要II级胸部基底，占2接口。','Absorbs damage using core power. Requires tier II chest chassis and 2 interface points.'),
    ('制造重层防御组件，提高装甲防御。需要II级胸部基底，占2接口。','Reinforces armour protection. Requires tier II chest chassis and 2 interface points.'),
    ('制造光学隐身组件，遮蔽玩家与战甲外观。需要III级背部基底，占3接口。','Conceals the wearer and armour. Requires tier III back chassis and 3 interface points.'),
    ('制造激光发射组件及火控终端。手部接口2，武器独立切换。','Unlock laser components and fire control. Uses 2 hand interfaces; weapons are selected independently.'),
    ('制造飞弹发射组件。需要II级肩部基底，占2接口；通过火控终端发射。','Missile launcher requires tier II shoulders and 2 interface points. Operated through fire control.'),
    ('制造贯穿采掘组件，用于连续采掘和攻击。需要III级手部基底，占3接口。','Penetrating mining cannon requires tier III hands and 3 interface points.'),
    ('制造浮游炮组件，自动攻击可见敌对目标。需要III级肩部基底，占3接口。','Automatically engages visible hostile targets. Requires tier III shoulders and 3 interface points.'),
]
for (key,_,_),pair in zip(RESEARCH,DESCRIPTIONS):
    ZH['lc.research_description.'+key]=pair[0];EN['lc.research_description.'+key]=pair[1]
for key,zh,en in PARTS:
    ZH['lc.part.'+key]=zh;EN['lc.part.'+key]=en
    ZH['lc.short_part.'+key]={'head':'头','chest':'胸','core':'核','legs':'腿','feet':'脚','hands':'手','back':'背','shoulders':'肩'}[key]
    EN['lc.short_part.'+key]=en[:2]
for key,zh,en in RESEARCH:ZH['research.starfall_blueprint.'+key]=zh;EN['research.starfall_blueprint.'+key]=en
for key,(zh,en) in names.items():
    prefix='block' if key in STATIONS else 'item'
    ZH[f'{prefix}.starfall_blueprint.{key}']=zh;EN[f'{prefix}.starfall_blueprint.{key}']=en
for locale,extra in [('zh_cn',ZH),('en_us',EN)]:
    path=ASSETS/f'lang/{locale}.json';lang=json.loads(path.read_text(encoding='utf-8'));lang.update(extra);write(path,lang)
for tag in ('mineable/pickaxe','needs_iron_tool'):
    path=RES/f'data/minecraft/tags/block/{tag}.json'
    value=json.loads(path.read_text()) if path.exists() else {'replace':False,'values':[]}
    value['values']=list(dict.fromkeys(value['values']+[f'starfall_blueprint:{key}' for key in STATIONS]));write(path,value)
print(f'Generated {len(names)} Lingchuan definitions, five eight-part machines, armour UV sheets and two languages')
