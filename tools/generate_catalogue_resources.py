"""Generate original catalogue sprites, block models, names and recipe discoveries."""
from pathlib import Path
import json, hashlib
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]; RES=ROOT/'src/main/resources'; MOD='starfall_blueprint'; ART=RES/'assets'/MOD; DATA=RES/'data'/MOD
catalogue=json.loads((ART/'catalogue.json').read_text(encoding='utf-8'))
machines={'refiner','recycler','g01','g02','g03','g04','pump','dew','cable','pipe','s02','bio_processor','g05','autocrafter','auto_furnace','supply_crate','energy_junction','item_duct','item_extractor','item_router','fluid_valve','terminal','busjoint','damper'}
existing={'ore','common','tail','joint','brace','shield','finder','prep','tank','rain_collector','alloy_furnace','iron_plate','copper_plate','fix','steel_pre','steel','ceramic','insulation','binder','substrate','board','breaker','sheath','steel_fix','box','machine_frame'}
fluids={'warm':'#bca88a','wastewater':'#837a61','bio':'#a6bf59','returnwater':'#699997'}
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def save(im,path):path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
def sprite(key):
 h=hashlib.sha256(key.encode()).digest();color=(75+h[0]//2,95+h[1]//2,95+h[2]//2,255)
 im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
 if key in machines:
  d.rectangle((1,1,14,14),fill='#56686b',outline='#1c343b');d.line((2,2,13,2),fill='#b9cac5');d.rectangle((3,4,12,11),fill='#213b40',outline=color)
  if key in {'g01','g02','g03','g04','g05','s02','cable','energy_junction','busjoint'}:d.polygon([(9,3),(5,8),(8,8),(6,12),(11,6),(8,6)],fill='#e4bc6e')
  elif key in {'pump','dew','pipe','fluid_valve'}:d.polygon([(8,4),(5,9),(6,11),(10,11),(11,9)],fill='#68b5bf')
  elif key in {'autocrafter','item_router','item_extractor','item_duct','supply_crate'}:
   for x in [4,7,10]:
    for y in [5,8]:d.rectangle((x,y,x+1,y+1),fill=color)
  else:d.polygon([(7,4),(10,6),(9,10),(6,11),(4,8)],fill=color)
  d.rectangle((3,13,5,13),fill='#d3ad78');d.point((12,13),'#4fc4a6')
 elif key=='sample_case':
  d.rectangle((3,4,12,14),fill='#94714a',outline='#293e42');d.arc((5,1,10,7),180,360,fill='#b8a077');d.rectangle((5,7,10,11),fill='#4f7770');d.line((4,5,11,5),fill='#d7b276')
 elif key=='work_goggles':
  d.rectangle((1,6,14,10),fill='#6b5141');d.rectangle((2,5,6,11),fill='#78aca6',outline='#cbb58a');d.rectangle((9,5,13,11),fill='#78aca6',outline='#cbb58a');d.point((3,6),'#d9f7e5');d.point((10,6),'#d9f7e5')
 elif key in {'sample_pick','rescue_hook','staff_blank'}:
  d.line((4,14,11,3),fill='#a6875f',width=2)
  if key=='sample_pick':d.line((3,3,12,3,14,7),fill='#b8d2c8',width=2);d.point((7,3),'#67d7bf')
  elif key=='rescue_hook':d.arc((7,1,14,8),180,470,fill='#baccc5',width=2)
  else:d.polygon([(8,3),(10,1),(13,3),(12,6),(9,6)],fill='#87bba6',outline='#355a59')
 elif key in {'head','basic_jig','direction_jig','pressure_module','laminate_jig','clean_module','facet_jig'}:
  d.rectangle((2,4,13,12),fill='#677e82',outline='#253a41');d.rectangle((5,6,10,10),fill='#203b3b');d.line((3,3,5,7,10,7,12,3),fill=color,width=2)
 elif key in fluids:
  d.polygon([(8,1),(3,8),(3,11),(6,14),(10,14),(13,11),(13,8)],fill=fluids[key],outline='#31454a');d.line((6,5,5,9),fill='#d0e8d7')
 elif key=='power':d.polygon([(9,1),(3,9),(7,9),(5,15),(14,6),(9,6)],fill='#e7be76',outline='#5e6550')
 elif key.endswith('_tail') or key in {'residue','reclaim_copper','reclaim_crystal'}:
  d.polygon([(2,7),(5,3),(10,4),(14,10),(12,14),(3,14)],fill=color,outline='#34484a');d.point((6,7),'#d2d5b1');d.point((10,10),'#8a9b8a')
 else:
  d.polygon([(2,6),(7,2),(13,4),(14,10),(9,14),(3,12)],fill=color,outline='#254148');d.line((4,6,8,3,11,5),fill='#c9e1d1');d.line((8,5,7,12),fill='#4b807c')
 return im

for key in catalogue['catalogue']:
 if key in existing:continue
 im=sprite(key);save(im,ART/'textures/item'/f'{key}.png')
 if key in machines:
  save(im,ART/'textures/block'/f'{key}_front.png')
  lit=im.copy();d=ImageDraw.Draw(lit);d.rectangle((10,12,13,13),fill='#81e5c2');save(lit,ART/'textures/block'/f'{key}_front_on.png')
  for active in [False,True]:
   write(ART/'models/block'/f'{key}{"_on" if active else ""}.json',{'parent':'minecraft:block/orientable','textures':{'front':f'{MOD}:block/{key}_front{"_on" if active else ""}','side':f'{MOD}:block/preparation_side','top':f'{MOD}:block/preparation_top'}})
  write(ART/'blockstates'/f'{key}.json',{'variants':{f'facing={face},lit={str(active).lower()}':{'model':f'{MOD}:block/{key}{"_on" if active else ""}','y':rotation}for face,rotation in [('north',0),('east',90),('south',180),('west',270)]for active in [False,True]}})
  write(ART/'models/item'/f'{key}.json',{'parent':f'{MOD}:block/{key}'})
 elif key=='boroglass':
  glass=Image.new('RGBA',(16,16),(144,190,184,35));d=ImageDraw.Draw(glass);d.rectangle((0,0,15,15),outline='#729f9f');d.line((2,5,5,2),fill='#cae7db');save(glass,ART/'textures/block/boroglass.png')
  write(ART/'models/block/boroglass.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'{MOD}:block/boroglass'}})
  write(ART/'blockstates/boroglass.json',{'variants':{'':{'model':f'{MOD}:block/boroglass'}}})
  write(ART/'models/item/boroglass.json',{'parent':f'{MOD}:block/boroglass'})
  write(DATA/'loot_table/blocks/boroglass.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'{MOD}:boroglass'}]}]})
 elif key not in fluids and key!='power':write(ART/'models/item'/f'{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'{MOD}:item/{key}'}})

for key,color in fluids.items():
 im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);d.polygon([(2,4),(13,4),(11,14),(4,14)],fill='#afc2c4',outline='#30434c');d.ellipse((3,3,12,7),fill=color,outline='#c4d8d0');save(im,ART/'textures/item'/f'{key}_bucket.png')
 write(ART/'models/item'/f'{key}_bucket.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'{MOD}:item/{key}_bucket'}})

rope=Image.new('RGBA',(16,16));d=ImageDraw.Draw(rope);d.rectangle((6,0,9,15),fill='#b19b70');d.line((7,0,7,15),fill='#ddc69b');save(rope,ART/'textures/block/rescue_rope.png')
write(ART/'models/block/rescue_rope.json',{'parent':'minecraft:block/cross','textures':{'cross':f'{MOD}:block/rescue_rope'}})
write(ART/'blockstates/rescue_rope.json',{'variants':{'':{'model':f'{MOD}:block/rescue_rope'}}})
for lang in ['zh_cn','en_us']:
 path=ART/'lang'/f'{lang}.json';text=json.loads(path.read_text(encoding='utf-8'))
 for key in catalogue['catalogue']:
  if key in existing:continue
  name=catalogue['items'][key]['name'] if lang=='zh_cn' else key.replace('_',' ').title()
  text[f'{"block" if key in machines or key=="boroglass" else "item"}.{MOD}.{key}']=name
  if key in fluids:
   text[f'fluid_type.{MOD}.{key}']=name;text[f'item.{MOD}.{key}_bucket']=name+('桶'if lang=='zh_cn'else' Bucket')
 for i,label in enumerate(['就绪','加工中','缺少原料/模板','缺少工艺水','等待供能','出料已满','缺少可用夹具','来料品质不合格','红石/手动停机']):text[f'gui.{MOD}.industry.state.{i}']=label if lang=='zh_cn' else ['Ready','Processing','Needs inputs/template','Needs water','Needs energy','Output full','Needs tool','Invalid quality','Stopped'][i]
 write(path,text)
tag=RES/'data/minecraft/tags/block/mineable/pickaxe.json';content=json.loads(tag.read_text(encoding='utf-8'));content['values']=list(dict.fromkeys(content['values']+[f'{MOD}:{k}'for k in machines]));write(tag,content)
write(RES/'data/minecraft/tags/block/dampens_vibrations.json',{'replace':False,'values':[f'{MOD}:damper']})
for r in catalogue['recipes']:
 if r['station']=='workbench' and r['kind']!='贸易' and not r['id'].startswith('V-'):
  filename=f"catalogue_{r['id'].lower()}";recipe_file=DATA/'recipe'/f'{filename}.json'
  if recipe_file.exists():write(DATA/'advancement/recipes'/f'{filename}.json',{'criteria':{'materials':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:iron_ingot'}]}}},'rewards':{'recipes':[f'{MOD}:{filename}']}})
print('Generated complete registered catalogue artwork, names, block models and recipe discoveries.')

# Three auxiliary ores use existing Minecraft dimensions: Shuyuan=Overworld,
# Chenghai resource region=End. Existing chunks remain unchanged.
for key,dimension in [('rutile','overworld'),('borate','overworld'),('moon','end')]:
 im=Image.new('RGBA',(16,16),'#767977' if dimension=='overworld' else '#d9d9a6');d=ImageDraw.Draw(im)
 color={'rutile':'#b9775a','borate':'#bdbaa1','moon':'#a7b8dc'}[key]
 for x,y in [(2,3),(9,2),(6,8),(12,11),(2,12)]:d.rectangle((x,y,x+2,y+1),fill=color)
 save(im,ART/'textures/block'/f'{key}_ore.png')
 write(ART/'models/block'/f'{key}_ore.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'{MOD}:block/{key}_ore'}})
 write(ART/'blockstates'/f'{key}_ore.json',{'variants':{'':{'model':f'{MOD}:block/{key}_ore'}}})
 targets=[{'target':{'predicate_type':'minecraft:tag_match','tag':f'minecraft:{t}'},'state':{'Name':f'{MOD}:{key}_ore'}}for t in ['stone_ore_replaceables','deepslate_ore_replaceables']] if dimension=='overworld' else [{'target':{'predicate_type':'minecraft:block_match','block':'minecraft:end_stone'},'state':{'Name':f'{MOD}:{key}_ore'}}]
 write(DATA/'worldgen/configured_feature'/f'{key}.json',{'type':'minecraft:ore','config':{'size':5 if key!='moon' else 4,'discard_chance_on_air_exposure':0.25,'targets':targets}})
 write(DATA/'worldgen/placed_feature'/f'{key}.json',{'feature':f'{MOD}:{key}','placement':[{'type':'minecraft:count','count':6},{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':-32 if dimension=='overworld' else 8},'max_inclusive':{'absolute':80}}},{'type':'minecraft:biome'}]})
 write(DATA/'neoforge/biome_modifier'/f'{key}.json',{'type':'neoforge:add_features','biomes':f'#minecraft:is_{dimension}','features':f'{MOD}:{key}','step':'underground_ores'})
 for tagname in ['mineable/pickaxe','needs_iron_tool']:
  path=RES/'data/minecraft/tags/block'/f'{tagname}.json';tagdata=json.loads(path.read_text(encoding='utf8'))if path.exists()else{'replace':False,'values':[]};tagdata['values']=list(dict.fromkeys(tagdata['values']+[f'{MOD}:{key}_ore']));write(path,tagdata)
 for lang in ['zh_cn','en_us']:
  path=ART/'lang'/f'{lang}.json';names=json.loads(path.read_text(encoding='utf8'));names[f'block.{MOD}.{key}_ore']=catalogue['items'][key]['name']if lang=='zh_cn'else key.title()+' Ore';write(path,names)
write(DATA/'recipe/reinforce_wear.json',{'type':f'{MOD}:reinforce_wear','category':'equipment'})
