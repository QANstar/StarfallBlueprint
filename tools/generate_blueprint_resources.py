"""Recipes, names and handheld artwork for the construction blueprint tools."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'src/main/resources/assets/starfall_blueprint'
DATA=ROOT/'src/main/resources/data/starfall_blueprint'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

for lang in ('zh_cn','en_us'):
    path=ART/'lang'/f'{lang}.json';names=json.loads(path.read_text(encoding='utf-8'))
    for key,zh,en in [('block.starfall_blueprint.blueprint_builder','蓝图施工器','Blueprint Constructor'),('item.starfall_blueprint.construction_plan','设计图','Construction Plan'),('item.starfall_blueprint.blueprint_surveyor','蓝图测绘笔','Blueprint Surveyor'),('item.starfall_blueprint.creative_construction_core','创造施工核心','Creative Construction Core')]:names[key]=zh if lang=='zh_cn' else en
    write(path,names)
for key in ('construction_plan','blueprint_surveyor'):
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    if key=='construction_plan':
        d.rectangle((2,1,13,14),fill='#376887',outline='#d8dec2');d.line((4,4,11,4,11,11,5,11,5,7,9,7),fill='#d7eee0');d.rectangle((1,1,3,3),fill='#d8dec2');d.rectangle((12,12,14,14),fill='#d8dec2')
    else:d.line((3,13,12,4),fill='#af905b',width=3);d.polygon([(10,2),(14,6),(12,8),(8,4)],fill='#83bac2');d.polygon([(2,15),(3,11),(6,14)],fill='#425a66')
    path=ART/'textures/item'/f'{key}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
    write(ART/'models/item'/f'{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'starfall_blueprint:item/{key}'}})
write(DATA/'recipe/construction_plan.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':'minecraft:paper'},{'item':'minecraft:blue_dye'}],'result':{'id':'starfall_blueprint:construction_plan','count':1}})
write(DATA/'recipe/blueprint_surveyor.json',{'type':'minecraft:crafting_shapeless','category':'equipment','ingredients':[{'item':'minecraft:stick'},{'item':'minecraft:iron_ingot'},{'item':'minecraft:blue_dye'}],'result':{'id':'starfall_blueprint:blueprint_surveyor','count':1}})
write(DATA/'recipe/blueprint_builder.json',{'type':'minecraft:crafting_shaped','category':'redstone','pattern':['ICI','BDF','IRI'],'key':{'I':{'item':'minecraft:iron_ingot'},'C':{'item':'minecraft:copper_ingot'},'B':{'item':'starfall_blueprint:control_board'},'D':{'item':'minecraft:dispenser'},'F':{'item':'starfall_blueprint:machine_frame'},'R':{'item':'minecraft:redstone'}},'result':{'id':'starfall_blueprint:blueprint_builder','count':1}})
for key in ('construction_plan','blueprint_surveyor','blueprint_builder'):
    write(DATA/'advancement/recipes'/f'{key}.json',{'criteria':{'materials':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:blue_dye'}]}}},'rewards':{'recipes':[f'starfall_blueprint:{key}']}})
path=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json';data=json.loads(path.read_text(encoding='utf-8'));data['values']=list(dict.fromkeys(data['values']+['starfall_blueprint:blueprint_builder']));write(path,data)
print('Generated blueprint tool recipes, names and artwork.')
write(ART/'models/item/creative_construction_core.json',{'parent':'minecraft:item/generated','textures':{'layer0':'starfall_blueprint:item/creative_construction_core'}})
from generate_material_textures import generate
generate()
