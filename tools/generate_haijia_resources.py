"""Deterministic Haijia models, pixel materials, bilingual strings and industrial recipes."""
from pathlib import Path
import json, random, math
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/starfall_blueprint'
D=ROOT/'src/main/resources/data/starfall_blueprint'
N={
'relay':('回声·导相无线输能塔','Echo Wireless Relay'),
'assembler':('织星·卫星装配台','Starweaver Assembly Bench'),
'launchpad':('天阶·轨道发射台','Skyway Launch Pad'),
'console':('环港·星座调度台','Ringport Constellation Console'),
 'terminal':('领航者·卫星便携终端','Navigator Satellite Terminal'),
'coil':('谐振导相线圈','Resonant Phase Coil'),'processor':('星载航电处理器','Orbital Avionics Processor'),
'solar_wing':('折叠太阳翼','Folding Solar Wing'),'bus':('通用卫星平台','Satellite Bus'),
 'thruster':('离子姿控组件','Ion Attitude Thruster'),'launch_vehicle':('天阶运载火箭','Skyway Launch Vehicle'),
 'laser_array':('聚束激光阵列','Collimated Laser Array'),'tungsten_ore':('黑钨矿','Wolframite Ore'),
 'tungsten_ingot':('钨锭','Tungsten Ingot'),'tungsten_rod':('天矛钨棒','Sky Lance Tungsten Rod'),
 'weather_array':('气象干涉阵列','Weather Control Array'),'airframe':('海隼机身总成','Peregrine Airframe'),
 'engine':('导相涵道引擎','Phase Ducted Engine'),'missile':('海隼航空导弹','Peregrine Aircraft Missile'),
 'fighter':('HF-01 海隼战斗机','HF-01 Peregrine Fighter'),
 'bomb':('海隼航空炸弹','Peregrine Gravity Bomb'),
 'sat_charge_upgrade':('曙光·高通量加强卫星','Dawn High-throughput Upgrade Satellite'),
 'sat_laser_upgrade':('天棱·聚能加强卫星','Prism Focus Upgrade Satellite'),
 'sat_kinetic_upgrade':('天矛·重载加强卫星','Sky Lance Heavy-payload Upgrade Satellite'),
 'creative_terminal':('创造·领航者全功能终端','Creative Navigator — All Satellite Services'),
 'creative_fighter':('创造·海隼无限供能战斗机','Creative Peregrine — Unlimited Power & Missiles'),
 'sat_charge':('曙光·充能卫星','Dawn Charging Satellite'),'sat_laser':('天棱·激光卫星','Prism Laser Satellite'),
 'sat_kinetic':('天矛·动能武器卫星','Sky Lance Kinetic Satellite'),'sat_weather':('云舵·气象卫星','Cloudhelm Weather Satellite')}
def write(p,d):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def save(p,img):p.parent.mkdir(parents=True,exist_ok=True);img.save(p)
def cube(lo,hi,texture='hull'):
 return {'from':lo,'to':hi,'faces':{face:{'texture':'#'+texture} for face in ['north','south','east','west','up','down']}}
T={'hull':'#b7c9d8','navy':'#183552','glass':'#6dbce3','orange':'#f2a04c','solar':'#274b91'}
for index,(name,base) in enumerate(T.items()):
 img=Image.new('RGBA',(32,32),base);p=ImageDraw.Draw(img);rng=random.Random(index)
 for y in range(32):
  for x in range(32):
   if rng.random()<.12:p.point((x,y),fill=tuple(max(0,min(255,c+rng.randint(-10,10))) for c in img.getpixel((x,y))[:3])+(255,))
 p.rectangle((0,0,31,31),outline='#0c213b');p.line((1,2,30,2),fill='#e5f2fa' if name=='hull' else '#75a4c5')
 if name=='solar':
  for k in range(0,32,8):p.line((k,0,k,31),fill='#709ed0');p.line((0,k,31,k),fill='#709ed0')
 elif name=='navy':
  for y in (8,12,16,20):p.line((5,y,26,y),fill='#0c213b')
 elif name=='hull':
  for x,y in [(3,4),(28,4),(3,28),(28,28)]:p.rectangle((x,y,x+1,y+1),fill='#415978')
 save(A/f'textures/block/hj_{name}.png',img)
tex={key:f'starfall_blueprint:block/hj_{key}' for key in T};tex['particle']=tex['hull']
# Full-size structures. The same boxes drive block models and server collision shapes.
sizes={'relay':[2,3,2],'assembler':[3,3,3],'launchpad':[5,4,5],'console':[3,2,2]}
blocks={
 'relay':[cube([1,0,1],[31,6,31],'navy'),cube([4,6,4],[28,11,28]),cube([12,11,12],[20,43,20],'glass'),cube([6,15,6],[26,18,26],'orange'),cube([3,30,3],[29,33,29],'navy'),cube([1,41,1],[31,44,31]),cube([7,44,7],[25,47,25],'glass')],
 'assembler':[cube([0,0,0],[48,6,48],'navy'),cube([4,6,7],[44,14,43]),cube([7,14,10],[41,17,40],'navy'),cube([13,17,16],[35,19,36],'glass'),cube([2,6,3],[7,46,9]),cube([41,6,3],[46,46,9]),cube([2,41,3],[46,47,10],'navy'),cube([20,33,6],[28,43,13],'orange'),cube([21,31,12],[27,36,28],'orange'),cube([21,23,24],[27,34,30]),cube([18,22,22],[30,25,33],'navy'),cube([3,6,42],[45,11,46],'orange'),cube([6,17,8],[12,22,13],'glass')],
 'launchpad':[cube([0,0,0],[80,4,80],'navy'),cube([2,4,2],[78,6,78]),cube([7,6,7],[73,7,73],'navy'),cube([19,7,19],[61,8,24],'orange'),cube([19,7,56],[61,8,61],'orange'),cube([19,7,24],[24,8,56],'orange'),cube([56,7,24],[61,8,56],'orange'),cube([28,7,28],[52,10,52]),cube([33,10,33],[47,11,47],'navy'),cube([4,6,63],[11,62,72]),cube([4,57,15],[11,64,72],'navy'),cube([11,41,49],[23,46,56],'orange'),cube([11,24,49],[23,29,56],'orange'),cube([4,14,63],[12,17,73],'orange'),cube([4,35,63],[12,38,73],'orange'),cube([4,56,63],[12,59,73],'orange')],
 'console':[cube([0,0,0],[48,4,32],'navy'),cube([4,4,7],[44,18,27]),cube([1,18,2],[47,21,31],'navy'),cube([3,21,23],[45,31,27]),cube([5,23,22.5],[22,29.5,23],'glass'),cube([25,23,22.5],[43,29.5,23],'glass'),cube([5,21,6],[31,21.5,17],'glass'),cube([35,21,6],[43,22,16],'orange'),cube([9,5,5],[13,14,7],'navy'),cube([17,5,5],[21,14,7],'navy'),cube([25,5,5],[29,14,7],'navy')]
}
write(A/'haijia_structures.json',{key:{'size':sizes[key],'boxes':[{'from':e['from'],'to':e['to']} for e in elements]} for key,elements in blocks.items()})
for key,elements in blocks.items():
 w,h,d=sizes[key];variants={}
 for part in range(w*h*d):
  px=part%w; pz=(part//w)%d; py=part//(w*d);offset=[px*16,py*16,pz*16];clipped=[]
  for el in elements:
   lo=[max(el['from'][i],offset[i])-offset[i] for i in range(3)];hi=[min(el['to'][i],offset[i]+16)-offset[i] for i in range(3)]
   if all(lo[i]<hi[i] for i in range(3)):
    clipped.append(cube(lo,hi,el['faces']['up']['texture'][1:]))
  model=f'hj_{key}_part_{part}'
  write(A/f'models/block/{model}.json',{'parent':'minecraft:block/block','ambientocclusion':True,'textures':tex,'elements':clipped})
  for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:variants[f'facing={facing},part={part}']={'model':f'starfall_blueprint:block/{model}','y':angle}
 # Unreachable part states use the empty model so all serialized blockstates have a model.
 for part in range(w*h*d,100):
  for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:variants[f'facing={facing},part={part}']={'model':f'starfall_blueprint:block/hj_{key}_empty'}
 write(A/f'models/block/hj_{key}_empty.json',{'textures':tex,'elements':[]})
 write(A/f'blockstates/hj_{key}.json',{'variants':variants})
 scale=16/max(w*16,h*16,d*16)
 miniature=[cube([v*scale for v in e['from']],[v*scale for v in e['to']],e['faces']['up']['texture'][1:]) for e in elements]
 write(A/f'models/block/hj_{key}.json',{'parent':'minecraft:block/block','textures':tex,'elements':miniature})
 write(A/f'models/item/hj_{key}.json',{'parent':f'starfall_blueprint:block/hj_{key}'})
 write(D/f'loot_table/blocks/hj_{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'starfall_blueprint:hj_{key}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
# In-inventory satellites have actual bodies, distinct payloads and folded solar arrays.
for key in ['sat_charge','sat_laser','sat_kinetic','sat_weather']:
 e=[cube([5,5,5],[11,11,11]),cube([0,7,4],[5,8,12],'solar'),cube([11,7,4],[16,8,12],'solar'),cube([7,11,7],[9,15,9],'orange')]
 if key=='sat_charge':e += [cube([1,5,1],[4,6,15],'solar'),cube([12,5,1],[15,6,15],'solar')]
 if key=='sat_laser':e += [cube([6,1,6],[10,5,10],'glass'),cube([5,0,5],[11,1,11],'orange')]
 if key=='sat_kinetic':e += [cube([x,1,6],[x+1,6,7],'navy') for x in range(5,11,2)]
 if key=='sat_weather':e += [cube([3,12,3],[13,13,13],'glass')]
 write(A/f'models/item/hj_{key}.json',{'textures':tex,'elements':e,'display':{'gui':{'rotation':[25,35,0],'translation':[0,0,0],'scale':[.85,.85,.85]},'ground':{'scale':[.4,.4,.4]}}})
for target in ['charge','laser','kinetic']:
 write(A/f'models/item/hj_sat_{target}_upgrade.json',{'parent':f'starfall_blueprint:item/hj_sat_{target}','textures':{'hull':'starfall_blueprint:block/hj_orange'}})
# Pixel inventory art: distinct silhouettes, never placeholder recolours.
for index,(key,names) in enumerate(N.items()):
 if key in blocks or key.startswith('sat_') or key=='tungsten_ore':continue
 shape=key.removeprefix('creative_')
 img=Image.new('RGBA',(32,32));p=ImageDraw.Draw(img);silver='#bdcfdf';navy='#193752';orange='#f5a34e';blue='#79d8ff'
 if shape=='coil':
  p.ellipse((5,4,26,27),fill=navy,outline=silver,width=2)
  for y in range(7,26,4):p.arc((7,y-2,24,y+5),0,320,fill=orange,width=2)
  p.rectangle((12,11,19,21),fill=blue)
 elif shape=='solar_wing':
  for x in [2,17]:
   p.rectangle((x,4,x+12,26),fill='#234c91',outline=silver)
   for y in range(8,26,5):p.line((x,y,x+12,y),fill=blue)
   p.line((x+6,4,x+6,26),fill=silver)
  p.rectangle((14,12,17,20),fill=orange)
 elif shape in ['tungsten_rod','missile','launch_vehicle','bomb']:
  w=4 if shape=='tungsten_rod' else 7
  p.polygon([(16,1),(16-w,8),(16-w,26),(16+w,26),(16+w,8)],fill=silver,outline=navy)
  p.rectangle((16-w,20,16+w,22),fill=orange)
  if shape!='tungsten_rod':p.polygon([(16-w,19),(4,29),(28,29),(16+w,19)],fill=navy);p.rectangle((13,27,19,31),fill=orange)
 elif shape=='fighter' or shape=='airframe':
  p.polygon([(16,1),(20,14),(30,23),(28,26),(20,22),(22,30),(16,27),(10,30),(12,22),(4,26),(2,23),(12,14)],fill=silver,outline=navy)
  p.rectangle((14,10,17,18),fill=blue);p.rectangle((13,26,19,28),fill=orange)
 elif shape=='terminal':
  p.rounded_rectangle((5,2,26,29),radius=3,fill=silver,outline=navy,width=2);p.rectangle((8,6,23,22),fill=navy)
  p.ellipse((10,8,21,19),outline=blue);p.line((9,20,22,8),fill=orange);p.rectangle((12,25,19,26),fill=orange)
 elif shape=='engine' or shape=='thruster':
  p.polygon([(8,3),(23,3),(26,22),(22,27),(9,27),(5,22)],fill=silver,outline=navy);p.ellipse((8,16,23,29),fill=navy,outline=orange,width=2);p.ellipse((12,20,19,26),fill=blue)
 elif shape=='tungsten_ingot':p.polygon([(3,15),(10,8),(25,8),(29,20),(23,25),(5,25)],fill='#7a8a9f',outline=navy);p.line((5,17,25,17),fill=silver)
 else:
  p.rectangle((5,5,26,26),fill=silver,outline=navy,width=2);p.rectangle((9,9,22,22),fill=navy,outline=orange)
  if shape=='laser_array':p.ellipse((10,10,21,21),fill=blue,outline=silver);p.line((16,2,16,29),fill=blue)
  elif shape=='weather_array':p.arc((8,4,25,21),10,170,fill=blue,width=2);p.arc((4,0,29,25),10,170,fill=orange,width=2)
  elif shape=='processor':
   p.rectangle((12,12,19,19),fill=blue)
   for k in range(6,27,5):p.line((k,2,k,5),fill=orange);p.line((k,26,k,29),fill=orange)
  else:p.rectangle((11,11,20,20),fill=blue);p.line((2,16,29,16),fill=orange,width=2)
 if key.startswith('creative_'):
  p.polygon([(26,1),(27,5),(31,6),(27,8),(26,12),(24,8),(20,6),(24,5)],fill=orange,outline='#fff0a5')
 save(A/f'textures/item/hj_{key}.png',img)
 write(A/f'models/item/hj_{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'starfall_blueprint:item/hj_{key}'}})
# Aircraft material atlas: subtle seams, glazed canopy and local identification accents.
img=Image.new('RGBA',(256,256),'#182c42');p=ImageDraw.Draw(img)
palette=['#c7d3df','#243c55','#245475','#101925','#768b9e','#efa34f','#82dcf2','#e3eaf0']
for i,color in enumerate(palette):
 x=(i%4)*64;y=(i//4)*64;p.rectangle((x,y,x+63,y+63),fill=color)
 if i in (0,1,4,7):
  seam='#a6b5c4' if i in (0,7) else '#344e68' if i==1 else '#536e83'
  p.line((x+8,y+5,x+8,y+58),fill=seam);p.line((x+8,y+46,x+58,y+46),fill=seam)
  for q in [12,32,52]:p.point((x+11,y+q),fill=seam)
 if i==2:
  p.polygon([(x+7,y+2),(x+14,y+2),(x+48,y+60),(x+41,y+60)],fill='#497e9c')
  p.line((x+3,y+5,x+60,y+5),fill='#91b8ca',width=1)
save(A/'textures/entity/hj_peregrine.png',img)
# Natural tungsten source, iron-tool ore, smelting then industrial rod assembly.
img=Image.new('RGBA',(16,16),'#59616c');p=ImageDraw.Draw(img)
for x,y in [(2,3),(8,2),(11,7),(4,10),(10,12)]:p.rectangle((x,y,x+2,y+2),fill='#202d41');p.point((x,y),fill='#d3be89')
save(A/'textures/block/hj_tungsten_ore.png',img)
write(A/'models/block/hj_tungsten_ore.json',{'parent':'minecraft:block/cube_all','textures':{'all':'starfall_blueprint:block/hj_tungsten_ore'}})
write(A/'models/item/hj_tungsten_ore.json',{'parent':'starfall_blueprint:block/hj_tungsten_ore'})
write(A/'blockstates/hj_tungsten_ore.json',{'variants':{'':{'model':'starfall_blueprint:block/hj_tungsten_ore'}}})
write(D/'loot_table/blocks/hj_tungsten_ore.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'starfall_blueprint:hj_tungsten_ore'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for tag in ['mineable/pickaxe','needs_iron_tool']:
 path=ROOT/f'src/main/resources/data/minecraft/tags/block/{tag}.json';data=json.loads(path.read_text()) if path.exists() else {'replace':False,'values':[]}
 for key in list(blocks)+['tungsten_ore']:
  value=f'starfall_blueprint:hj_{key}'
  if value not in data['values']:data['values'].append(value)
 write(path,data)
configured=json.loads((D/'worldgen/configured_feature/rutile.json').read_text());configured['config']['size']=4
for target in configured['config']['targets']:target['state']['Name']='starfall_blueprint:hj_tungsten_ore'
write(D/'worldgen/configured_feature/hj_tungsten.json',configured)
placed=json.loads((D/'worldgen/placed_feature/rutile.json').read_text());placed['feature']='starfall_blueprint:hj_tungsten';placed['placement'][0]['count']=4;placed['placement'][2]['height']['min_inclusive']['absolute']=-48;placed['placement'][2]['height']['max_inclusive']['absolute']=16
write(D/'worldgen/placed_feature/hj_tungsten.json',placed)
write(D/'neoforge/biome_modifier/hj_tungsten.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':'starfall_blueprint:hj_tungsten','step':'underground_ores'})
write(D/'recipe/hj_tungsten_smelting.json',{'type':'minecraft:smelting','ingredient':{'item':'starfall_blueprint:hj_tungsten_ore'},'result':{'id':'starfall_blueprint:hj_tungsten_ingot'},'experience':.7,'cookingtime':200})
write(D/'recipe/hj_tungsten_blasting.json',{'type':'minecraft:blasting','ingredient':{'item':'starfall_blueprint:hj_tungsten_ore'},'result':{'id':'starfall_blueprint:hj_tungsten_ingot'},'experience':.7,'cookingtime':100})
# Up to nine real ingredients per recipe: compatible with the existing automatic crafting table.
RECIPES={
'coil':(2,['conductor']*3+['copper_plate']*2+['insulation']*2),
'processor':(1,['control_board']*2+['sensor']+['conductor']+['minecraft:quartz']*2+['minecraft:gold_ingot']),
'solar_wing':(1,['minecraft:daylight_detector']*2+['conductor']*2+['iron_plate']*2+['boroglass']),
'bus':(1,['machine_frame']*2+['hj_processor']*2+['hj_coil']*2+['steel_fasteners']*2+['load']),
 'thruster':(1,['ordinary_steel']*2+['hj_coil']*2+['hj_processor']+['minecraft:blaze_powder']*2+['ceramic']*2),
 'launch_vehicle':(1,['machine_frame']*2+['hj_thruster']*2+['hj_processor']+['ordinary_steel']*2+['minecraft:firework_rocket']*2),
 'laser_array':(1,['hj_coil']*2+['minecraft:diamond']+['minecraft:quartz']*2+['hj_processor']+['conductor']*2),
 'tungsten_rod':(1,['hj_tungsten_ingot']*6+['ordinary_steel']+['hj_processor']),
 'weather_array':(1,['hj_processor']*2+['sensor']*2+['minecraft:lightning_rod']+['hj_coil']*2+['minecraft:amethyst_shard']),
 'airframe':(1,['machine_frame']*3+['load']*2+['ordinary_steel']*2+['steel_fasteners']*2),
 'engine':(1,['hj_thruster']*2+['conductor']*2+['ordinary_steel']*2+['hj_processor']+['ceramic']*2),
 'bomb':(4,['minecraft:tnt']*2+['ordinary_steel']*2+['hj_processor']+['minecraft:gunpowder']*2),
 'missile':(4,['hj_processor']+['ordinary_steel']*2+['minecraft:gunpowder']*3+['minecraft:firework_rocket']*2),
 'relay':(1,['hj_coil']*4+['hj_processor']+['machine_frame']+['conductor']*2+['ceramic']),
 'assembler':(1,['machine_frame']*2+['hj_processor']*2+['conductor']*2+['steel_fasteners']*2+['minecraft:crafting_table']),
 'launchpad':(1,['machine_frame']*2+['ordinary_steel']*2+['hj_processor']*2+['hj_coil']*2+['minecraft:dispenser']),
 'console':(1,['hj_processor']*3+['machine_frame']+['sensor']*2+['boroglass']+['conductor']*2),
 'terminal':(1,['hj_processor']*2+['hj_coil']+['sensor']+['conductor']*2+['boroglass'])}
# Resolve catalogue aliases using existing Java registered resource ids.
alias={'load':'load','sensor':'sensor','conductor':'conductor','boroglass':'boroglass'}
def rid(k):return k if ':' in k else 'starfall_blueprint:'+alias.get(k,k)
for key,(count,inputs) in RECIPES.items():write(D/f'recipe/hj_{key}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':rid(k)} for k in inputs],'result':{'id':f'starfall_blueprint:hj_{key}','count':count}})
# Common text values, deliberately shared across GUI, research and tooltips.
STR={
 'tab':('星落蓝图 · 海岬轨道与航空','Starfall Blueprint · Haijia Orbit & Aviation'),'station':('海岬轨道工位','Haijia Orbital Station'),'terminal':N['terminal'],
 'assembler':N['assembler'],'launchpad':N['launchpad'],'console':N['console'],
 'relay.status':('储能 %s XH · %s 条中继连接','Energy %s XH / %s links'),'relay.selected':('已选择首个输能塔，右键另一座完成连线','First relay selected; use another relay to link'),
 'relay.linked':('输能塔已双向连线','Relays linked in both directions'),'relay.cleared':('已清除这座输能塔的全部连线','All links on this relay cleared'),
 'relay.invalid':('连接失败：距离须≤64格，每塔最多8条连接','Link refused: maximum 64 blocks and 8 links per relay'),
 'operation_failed':('操作未完成：检查知识、材料、背包空间、电力和发射净空','Operation refused: check research, materials, inventory, energy and launch clearance'),
 'assembly_cost':('装配用电：%s XH','Assembly energy: %s XH'),'station_energy':('工位储能 %s kXH','Station energy %s kXH'),
 'fleet':('充%s 束%s 棒%s 气%s','Pwr%s Las%s Kin%s Wx%s'),'fleet_energy':('星座共享储能：%s kXH','Fleet energy: %s kXH'),'fleet_ammo':('轨道钨棒：%s','Orbital rods: %s'),
 'cooldowns':('冷却：激光%s秒 / 钨棒%s秒 / 天气%s秒','Cooldown: laser %ss / rod %ss / weather %ss'),'active':('服务启用：充%s / 束%s / 棒%s / 气%s','Active: power %s / laser %s / rod %s / weather %s'),
 'terminal_hint':('潜行右键打开终端；常规右键使用所选能力','Sneak-use opens terminal; use activates selected ability'),
 'strike.blocked':('无法打击：检查露天目标、192格射程、主世界、卫星启用、能量、弹药和冷却','Strike unavailable: check open sky, 192-block range, Overworld, active satellite, energy, ammo and cooldown'),
 'strike.marked':('目标已标记，%s秒后轨道打击','Target marked; orbital impact in %s seconds'),
 'weather.blocked':('气象控制不可用：需主世界、启用云舵卫星、400kXH与120秒冷却','Weather unavailable: Overworld, active Cloudhelm, 400 kXH and 120-second cooldown required'),
 'weather.changed':('云舵已更新全局天气，持续10分钟','Cloudhelm updated global weather for 10 minutes'),
 'reload':('补给钨棒（钨棒＋火箭各1）','Supply rod (1 rod + 1 rocket)'),
 'weather.clear':('晴天','Clear'),'weather.rain':('降雨','Rain'),'weather.thunder':('雷暴','Storm'),
 'fighter_title':N['fighter'],'flight_status':('结构%s / 电量%sk / 油门%s%% / 弹药%s','Hull %s / Energy %sk / Throttle %s%% / Ammo %s'),
 'flight_controls':('W/S油门 空格/Shift俯仰 Q/E滚转 A/D偏航','W/S throttle Space/Shift pitch Q/E roll A/D yaw'),
 'flight_weapons':('[%s]激光 [%s]导弹 [%s]上下机','[%s] Laser [%s] Missile [%s] Enter/Exit'),
 'keys':('海岬飞行控制','Haijia Flight Controls'),
 'key.enter':('上下海隼战斗机','Enter/Exit Peregrine'),'key.roll_left':('飞机左滚转','Roll left'),'key.roll_right':('飞机右滚转','Roll right'),'key.laser':('飞机激光','Aircraft laser'),'key.missile':('飞机导弹','Aircraft missile')}
STR.update({
 'structure_incomplete':('结构不完整，请拆除后在足够空间重新放置','Incomplete structure; replace in a clear area'),
 'ui.energy':('星座能源 %s / %s kXH','Fleet energy %s / %s kXH'),
 'ui.satellite_summary':('基础卫星 %s 颗','%s base satellites'),
 'ui.charge_title':('随身设备充能','Equipment Charging'),
 'ui.charge_enable':('开启随身充能','Enable equipment charging'),
 'ui.charge_disable':('暂停随身充能','Pause equipment charging'),
 'ui.charging_on':('已开启 · 携带终端自动充电','Enabled: charges while carried'),
 'ui.charging_off':('已暂停 · 点击下方开启','Paused: enable below'),
 'ui.no_charge_sat':('尚未发射曙光卫星','No Dawn satellite deployed'),
 'ui.charge_count':('曙光 %s 颗 · %s kXH/秒','Dawn %s / %s kXH per second'),
 'ui.charge_hint':('控制曙光卫星向背包、护甲与副手设备充电。开启后携带终端即可；无需手持。','Enable Dawn to charge inventory, armor and offhand equipment while carrying a terminal.'),
 'ui.select_laser':('选择轨道激光','Select orbital laser'),
 'ui.select_rod':('选择钨棒打击','Select kinetic strike'),
 'ui.laser_status':('天棱%s · 加强%s/8 · %s秒','Prism %s / Upgrade %s/8 / %ss'),
 'ui.rod_status':('钨棒%s · 加强%s/8 · %s秒','Rods %s / Upgrade %s/8 / %ss'),
 'ui.laser_hint':('选择后关闭界面，右键瞄准位置。消耗250 kXH，预警3秒，不检查室内或露天。','Select, close this screen and use at the aim point. 250 kXH; 3-second warning; works indoors.'),
 'ui.rod_hint':('选择后关闭界面，右键瞄准位置。消耗600 kXH与1根钨棒，预警5秒。补给必须在发射台发射天矛卫星，每颗携带8根。','Select, close and use at the aim point. 600 kXH + 1 rod, 5-second warning. Launch another Sky Lance satellite to supply 8 rods.'),
 'ui.weather_title':('天气控制','Weather'),
 'ui.no_weather_sat':('气象未部署','No weather sat'),
 'ui.weather_wait':('天气%s秒','Weather %ss'),
 'ui.weather_hint':('立即改变主世界天气。消耗400 kXH，三种天气共享120秒冷却。','Change Overworld weather now. 400 kXH; shared 120-second cooldown.'),
 'ui.target_hint':('选武器 → 关闭界面 → 右键标记；潜行右键返回','Select weapon > close > use to mark; sneak-use returns'),
 'ui.supply_by_satellite':('钨棒耗尽？再发射一颗天矛卫星，补给8根。','Out of rods? Launch another Sky Lance for 8 more.'),
 'ui.launch_cost':('每次消耗对应卫星1、运载火箭1与200 kXH。发射平台上方须净空；天矛每次补充8根钨棒。','Each launch costs 1 satellite, 1 launch vehicle and 200 kXH. Clear space above the pad. Each Sky Lance supplies 8 rods.')
})
STR['strike.blocked']=('无法打击：检查192格内目标、主世界、能量、弹药和冷却','Strike unavailable: check a target within 192 blocks, Overworld, energy, ammo and cooldown')
STR.update({
 'key.boost':('飞机强推力（按住）','Aircraft afterburner (hold)'),
 'flight.weapon.0':('重束激光炮','Heavy Beam Laser'),
 'flight.weapon.1':('追踪导弹','Homing Missile'),
 'flight.weapon.2':('重力航空炸弹','Gravity Bomb'),
 'flight.weapon_status':('当前：%s · 炸弹%s枚','Selected: %s / Bombs: %s'),
 'flight.mouse_controls':('滚轮切武器 · 左键自动瞄准开火','Wheel: weapon / Left: auto-aim fire'),
 'flight.runway':('速度%s km/h · 滑跑后抬头 · Ctrl强推力','%s km/h / Taxi, then pitch up / Ctrl boost'),
 'flight.boosting':('强推力开启 · 速度%s km/h','AFTERBURNER / %s km/h'),
 'flight.lock_hint':('左键开火自动捕捉准星附近目标','Fire to acquire a target near the reticle'),
 'flight.locking':('正在锁定 %s · %s%%','Locking %s / %s%%'),
 'flight.locked':('已锁定 %s · %s%%','LOCKED %s / %s%%'),
 'ui.upgrade_level':('载荷加强等级：%s / 8','Payload upgrade: %s / 8'),
 'ui.upgrade_cost':('先部署对应基础卫星；每类加强最多8级。每次消耗加强卫星1、运载火箭1、200 kXH。','Deploy the base satellite first. Up to 8 upgrades per type; each launch costs 1 upgrade satellite, 1 launch vehicle and 200 kXH.'),
 'launch.upgrade.charge':('发射曙光加强卫星 · 提升充能速度','Launch Dawn upgrade / Faster charging'),
 'launch.upgrade.laser':('发射天棱加强卫星 · 提升伤害与范围','Launch Prism upgrade / Damage & radius'),
 'launch.upgrade.kinetic':('发射天矛加强卫星 · 提升伤害范围并补8根','Launch Sky Lance upgrade / Damage, radius, 8 rods')
})
research=[('power','导相谐振输能','Resonant Wireless Power','连接输能塔并覆盖附近机器；制作线圈和航电处理器','Link relay towers and power nearby machines; manufacture coils and avionics'),('orbit','卫星装配与入轨','Satellite Assembly & Launch','建造织星装配台、天阶发射台、环港调度台和领航者终端','Build assembly, launch and command stations and a portable terminal'),('charge','曙光星座供能','Dawn Constellation Power','重复发射升级；携带终端向全部用电装备分配星座能量','Repeated launches increase capacity; carrying a terminal charges equipped energy items'),('laser','天棱轨道束流','Prism Orbital Beam','标记露天目标，预警3秒后发射高能激光','Mark an open-sky target; fire an orbital beam after a 3-second warning'),('kinetic','天矛动能投射','Sky Lance Kinetic Strike','发射携带8根钨棒的卫星；标记后5秒进行大范围打击','Launch a satellite with 8 rods; impact after a 5-second warning'),('weather','云舵气象调控','Cloudhelm Weather Control','调节主世界全局晴天、降雨或雷暴','Set Overworld clear skies, rain or thunder'),('flight','海隼航空制造','Peregrine Aviation','制造并驾驶HF-01，装备激光和实物航空导弹','Manufacture and fly the HF-01 with laser weapons and physical missiles')]
for key,zh,en,zd,ed in research:STR['research.'+key]=(zh,en);STR['research_desc.'+key]=(zd,ed)
for key in ['charge','laser','kinetic','weather']:STR['launch.'+key]=('发射'+N['sat_'+key][0],'Launch '+N['sat_'+key][1]);STR['toggle.'+key]=(N['sat_'+key][0].split('·')[0]+'开关','Toggle '+key)
STR['launch.kinetic']=('发射天矛 / 补给钨棒 +8','Launch Sky Lance / Supply 8 rods')
for i,pair in enumerate([('管理','View'),('激光','Laser'),('钨棒','Rod'),('晴天','Clear'),('降雨','Rain')]):STR['mode.'+str(i)]=pair
for lang,n in [('zh_cn',0),('en_us',1)]:
 path=A/f'lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8'))
 for key,pair in N.items():data[f'item.starfall_blueprint.hj_{key}']=pair[n];data[f'block.starfall_blueprint.hj_{key}']=pair[n]
 for key,pair in STR.items():data['hj.'+key]=pair[n]
 data['growth.starfall_blueprint.category.haijia']=('海岬轨道','Haijia')[n];data['entity.starfall_blueprint.hj_peregrine']=N['fighter'][n];data['entity.starfall_blueprint.hj_strike']=('轨道支援','Orbital Support')[n]
 write(path,data)
# Inspectable preview of actual voxel block models and item icons.
canvas=Image.new('RGB',(1000,500),'#0e1d32');draw=ImageDraw.Draw(canvas)
for i,(key,elements) in enumerate(blocks.items()):
 ox=120+i*250;oy=195
 def project(v):x,y,z=[q/max(sizes[key]) for q in v];return (ox+(x-z)*5,oy+(x+z)*2.5-y*5)
 for el in sorted(elements,key=lambda e:sum(e['from'])-e['from'][1]*2):
  x,y,z=el['from'];X,Y,Z=el['to'];color=T[el['faces']['up']['texture'][1:]]
  for points in [[(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],[(X,y,z),(X,y,Z),(X,Y,Z),(X,Y,z)],[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)]]:draw.polygon([project(v) for v in points],fill=color,outline='#172b47')
 draw.text((ox-60,225),key.upper(),fill='#d5e9fc')
icons=[k for k in N if (A/f'textures/item/hj_{k}.png').exists()]
for i,key in enumerate(icons):
 x=28+(i%10)*98;y=285+(i//10)*95;icon=Image.open(A/f'textures/item/hj_{key}.png').resize((58,58),Image.Resampling.NEAREST);canvas.paste(icon,(x,y),icon);draw.text((x,y+62),key[:14],fill='#acd5f0')
save(ROOT/'docs/haijia-assets.png',canvas)
write(A/'haijia_manifest.json',{'items':list(N),'recipes':{k:{'count':v[0],'inputs':[rid(x) for x in v[1]]} for k,v in RECIPES.items()}})
print(f'Generated {len(N)} Haijia items, {len(RECIPES)+2} public recipes, 4 voxel stations, satellite models, aircraft texture and preview.')
