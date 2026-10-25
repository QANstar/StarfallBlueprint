"""L02 uses whole items. Steel mixing is a machine recipe, never fractional crafting."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
MOD = 'starfall_blueprint'
ART = RES / 'assets' / MOD
DATA = RES / 'data' / MOD

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')

materials = {
    'machine_frame': ('基础机架', 'Basic Machine Frame', '#80989b'),
    'iron_plate': ('铁板', 'Iron Plate', '#a7bcc1'),
    'copper_plate': ('铜板', 'Copper Plate', '#cb9166'),
    'iron_fasteners': ('铁制固定件组', 'Iron Fasteners', '#a4b5b7'),
    'mixed_steel_billet': ('混合钢坯', 'Mixed Steel Billet', '#727f85'),
    'ordinary_steel': ('普通钢基材', 'Ordinary Steel', '#92a6b5'),
    'ceramic': ('普通陶瓷', 'Ordinary Ceramic', '#d8c7a0'),
    'insulation': ('普通绝缘基材', 'Insulation', '#b5b69b'),
    'binder': ('普通粘结剂', 'Binder', '#a8ad63'),
    'isolation_substrate': ('铜／硼隔离基底', 'Isolation Substrate', '#d8b797'),
    'control_board': ('基础控制板', 'Basic Control Board', '#52968a'),
    'circuit_breaker': ('断路保护器', 'Circuit Breaker', '#ac9e78'),
    'ceramic_sheath': ('陶瓷护套', 'Ceramic Sheath', '#d9c5a2'),
    'steel_fasteners': ('钢制固定件组', 'Steel Fasteners', '#8c9da9'),
    'sealed_box': ('普通封存盒', 'Sealed Box', '#7c959c'),
    'carbon_portion': ('碳料余份', 'Carbon Portion', '#5e6669'),
    'quartz_portion': ('石英余份', 'Quartz Portion', '#e4d8c7'),
}
for name, (zh, en, color) in materials.items():
    im = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(im)
    if name=='machine_frame':
        d.rectangle((2,2,13,13), fill='#273d43', outline='#91a5a6')
        d.rectangle((5,5,10,10), fill=(0,0,0,0))
        d.line((3,3,12,3), fill='#c1d0ca')
        for x,y in [(3,3),(12,3),(3,12),(12,12)]: d.point((x,y), '#d4a36b')
    elif name.endswith('portion'):
        d.polygon([(2, 10), (5, 6), (8, 8), (11, 4), (14, 11), (10, 14), (3, 13)], fill=color, outline='#263a42')
        d.line((5, 10, 7, 9), fill='#bac7c1')
    elif 'fasteners' in name:
        for x, y in [(2, 3), (8, 7), (3, 10)]:
            d.rectangle((x, y, x+4, y+2), fill=color, outline='#30444c')
            d.line((x+2, y-1, x+2, y+3), fill=color)
    else:
        d.polygon([(2, 5), (11, 2), (14, 5), (14, 11), (5, 14), (2, 11)], fill=color, outline='#2b424b')
        d.line((3, 6, 11, 3, 13, 5), fill='#e0e4cf')
        d.line((5, 9, 12, 7), fill='#46616a', width=2)
        if name in ('control_board', 'circuit_breaker', 'isolation_substrate'):
            d.rectangle((6, 6, 9, 9), fill='#263b41', outline='#dab77b')
            d.line((4, 11, 11, 9), fill='#dfb878')
    path = ART / 'textures/item' / f'{name}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    write(ART / 'models/item' / f'{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MOD}:item/{name}'}})

for lang, index in [('zh_cn', 0), ('en_us', 1)]:
    path = ART / 'lang' / f'{lang}.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update({f'item.{MOD}.{name}': labels[index] for name, labels in materials.items()})
    data[f'tooltip.{MOD}.portion_mass'] = '旧版余料：%s M（不能代替整件原料）' if index == 0 else 'Legacy portion: %s M (not a whole ingredient)'
    values = {
        f'block.{MOD}.alloy_furnace': ('合金混料炉', 'Alloy Mixing Furnace'),
        f'gui.{MOD}.alloy.material': ('铁锭   碳料', 'Iron    Carbon'),
        f'gui.{MOD}.alloy.state.0': ('就绪', 'Ready'),
        f'gui.{MOD}.alloy.state.1': ('混料中', 'Mixing'),
        f'gui.{MOD}.alloy.state.2': ('需要3铁+1碳', 'Needs input'),
        f'gui.{MOD}.alloy.state.3': ('缺少燃料', 'Needs fuel'),
        f'gui.{MOD}.alloy.state.4': ('出料已满', 'Output full'),
    }
    data.update({key: value[index] for key, value in values.items()})
    write(path, data)

def ingredient(name):
    return {'item': name if ':' in name else f'{MOD}:{name}'}

def shapeless(name, inputs, count=1):
    write(DATA / 'recipe' / f'{name}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [ingredient(i) for i in inputs], 'result': {'id': f'{MOD}:{name}', 'count': count}})

# Integer batches avoid vanilla iron-nugget and heavy-pressure-plate recipe collisions.
write(DATA / 'recipe/iron_plate.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'pattern': ['I', 'I'], 'key': {'I': ingredient('minecraft:iron_ingot')}, 'result': {'id': f'{MOD}:iron_plate', 'count': 2}})
shapeless('copper_plate', ['minecraft:copper_ingot'])
shapeless('machine_frame', ['minecraft:iron_ingot'] * 4 + ['minecraft:copper_ingot'] * 2 + ['minecraft:cobblestone'] * 2 + ['iron_fasteners'])
write(DATA / 'recipe/iron_fasteners.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'pattern': ['III'], 'key': {'I': ingredient('minecraft:iron_ingot')}, 'result': {'id': f'{MOD}:iron_fasteners', 'count': 3}})
write(DATA / 'recipe/mixed_steel_billet.json', {'type': f'{MOD}:alloy_mixing',
    'base': ingredient('minecraft:iron_ingot'), 'base_count': 3,
    'additive': {'tag': 'minecraft:coals'}, 'additive_count': 1,
    'result': {'id': f'{MOD}:mixed_steel_billet', 'count': 3}, 'duration': 200})
write(DATA / 'recipe/isolation_substrate.json', {'type': f'{MOD}:isolation_substrate', 'category': 'misc'})
for output, source in [('ordinary_steel', f'{MOD}:mixed_steel_billet'), ('ceramic', 'minecraft:clay_ball')]:
    write(DATA / 'recipe' / f'{output}.json', {'type': 'minecraft:smelting', 'category': 'misc',
        'ingredient': ingredient(source), 'result': {'id': f'{MOD}:{output}'}, 'experience': 0.0, 'cookingtime': 200})
shapeless('insulation', ['minecraft:paper', 'boron'], 2)
shapeless('binder', ['minecraft:wheat', 'minecraft:clay_ball'], 2)
write(DATA / 'recipe/control_board.json', {'type': f'{MOD}:catalogue_crafting', 'recipe': 'ST-10'})
shapeless('circuit_breaker', ['minecraft:iron_ingot', 'minecraft:copper_ingot', 'minecraft:redstone', 'insulation'])
shapeless('ceramic_sheath', ['ceramic', 'titan'], 2)
shapeless('steel_fasteners', ['ordinary_steel'])
shapeless('sealed_box', ['minecraft:iron_ingot', 'minecraft:iron_ingot', 'minecraft:glass', 'insulation'])
for name in materials:
    if name.endswith('_portion'):
        continue
    write(DATA / 'advancement/recipes' / f'{name}.json', {'criteria': {'obtained': {'trigger': 'minecraft:inventory_changed',
        'conditions': {'items': [{'items': 'minecraft:iron_ingot'}]}}}, 'rewards': {'recipes': [f'{MOD}:{name}']}})
write(DATA / 'recipe/alloy_furnace.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'pattern': ['ICI', 'SFS', 'SSS'], 'key': {'I': ingredient('minecraft:iron_ingot'),
        'C': ingredient('minecraft:copper_ingot'), 'S': ingredient('minecraft:cobblestone'),
        'F': ingredient('minecraft:furnace')}, 'result': {'id': f'{MOD}:alloy_furnace'}})
write(DATA / 'advancement/recipes/alloy_furnace.json', {'criteria': {'obtained': {'trigger': 'minecraft:inventory_changed',
    'conditions': {'items': [{'items': 'minecraft:iron_ingot'}]}}}, 'rewards': {'recipes': [f'{MOD}:alloy_furnace']}})
# The front is drawn separately so the appliance is recognizable from the prep bench.
for active in [False, True]:
    im=Image.new('RGBA', (16,16), '#586269'); d=ImageDraw.Draw(im)
    d.rectangle((0,0,15,15), outline='#25363b'); d.line((1,1,14,1), fill='#9aa6aa')
    d.rectangle((3,3,12,7), fill='#26363c', outline='#c29262')
    d.rectangle((4,4,6,6), fill='#bdc9c6'); d.rectangle((9,4,11,6), fill='#4d4840')
    d.rectangle((3,10,12,14), fill='#231f23', outline='#899496')
    if active:
        d.rectangle((4,11,11,13), fill='#d96c30'); d.line((6,12,9,12), fill='#ffe19b')
    name='alloy_front_on' if active else 'alloy_front'
    path=ART/'textures/block'/f'{name}.png'; path.parent.mkdir(parents=True, exist_ok=True); im.save(path)
    write(ART/'models/block'/f'alloy_furnace{"_on" if active else ""}.json', {
        'parent':'minecraft:block/orientable','textures':{'front':f'{MOD}:block/{name}',
        'side':f'{MOD}:block/preparation_side','top':f'{MOD}:block/preparation_top'}})
write(ART/'models/item/alloy_furnace.json', {'parent':f'{MOD}:block/alloy_furnace'})
write(ART/'blockstates/alloy_furnace.json', {'variants': {
    f'facing={facing},lit={str(active).lower()}': {'model':f'{MOD}:block/alloy_furnace{"_on" if active else ""}', 'y':rotation}
    for facing,rotation in [('north',0),('east',90),('south',180),('west',270)] for active in [False,True]}})
tag=RES/'data/minecraft/tags/block/mineable/pickaxe.json'
tag_data=json.loads(tag.read_text(encoding='utf-8'))
if f'{MOD}:alloy_furnace' not in tag_data['values']: tag_data['values'].append(f'{MOD}:alloy_furnace')
write(tag,tag_data)
print('Generated whole-item L02 recipes, alloy mixing furnace and artwork.')
from generate_material_textures import generate as generate_material_textures
generate_material_textures()
