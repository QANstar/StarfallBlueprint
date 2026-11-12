"""Additive resources for the three Water God chassis and upgrade components."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/starfall_blueprint'
NAMES = {
'best_combat_mech': ('SG-C 激湍战斗机', 'SG-C Jituan Combat Mech'),
'best_universal_mech': ('SG-X 归海全能机', 'SG-X Guihai Universal Mech'),
'best_canglan_engineering': ('沧澜·工程扩展', 'Canglan Engineering Extension'),
'best_canglan_mobility': ('沧澜·机动驱动', 'Canglan Mobility Drive'),
'best_canglan_endurance': ('沧澜·长时作业', 'Canglan Endurance'),
'best_nutao_fire_control': ('怒涛·协同火控', 'Nutao Coordinated Fire Control'),
'best_nutao_armor': ('怒涛·结构防护', 'Nutao Structural Armor'),
'best_nutao_sustained': ('怒涛·持续输出', 'Nutao Sustained Fire'),
'best_zhenyuan_module': ('镇渊最终强化组件', 'Zhenyuan Final Upgrade'),
'best_tianpu_module': ('天瀑最终强化组件', 'Tianpu Final Upgrade'),
'best_changchuan_module': ('长川最终强化组件', 'Changchuan Final Upgrade'),
'best_engineering_confluence': ('工程汇流总成', 'Engineering Confluence Assembly'),
'best_combat_confluence': ('武装汇流总成', 'Combat Confluence Assembly'),
'best_fire_control': ('基础火控器', 'Basic Fire Controller'),
'best_load_armor': ('承相装甲组件', 'Load-phase Armor Assembly'),
'best_weapon_radiator': ('武器散热组件', 'Weapon Radiator Assembly'),
}
def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
def main():
    definitions = {}
    for index, (key, pair) in enumerate(NAMES.items()):
        definitions[key] = dict(id=key,name=pair[0],unit='件',icon='gear',note='水神计划三机与实物组件；工务装配台制造',external=False,vanilla=False,mass=100,category='贝斯特机械',subcategory='水神计划',recipes=[],uses=[])
        write(ART / f'models/item/{key}.json', {'parent':'minecraft:item/generated','textures':{'layer0':f'starfall_blueprint:item/{key}'}})
        image = Image.new('RGBA',(32,32))
        draw = ImageDraw.Draw(image)
        accent = ['#53c7bf','#e5a365','#829ee5'][index % 3]
        if index < 2:
            draw.rectangle((11,3,20,9), fill='#334552', outline=accent)
            draw.rectangle((7,10,24,21),fill='#334552',outline=accent)
            for x in (3,25): draw.rectangle((x,10,x+3,23),fill='#687887',outline=accent)
            for x in (8,19): draw.rectangle((x,22,x+4,29),fill='#687887',outline=accent)
            draw.rectangle((13,5,18,6),fill=accent)
        else:
            draw.rectangle((5,5,26,26),fill='#273744',outline='#9dafbb',width=2)
            draw.rectangle((10,10,21,21),fill=accent,outline='#e2efed')
            for offset in (8,15,22):
                draw.rectangle((offset,2,offset+1,5),fill=accent)
                draw.rectangle((offset,26,offset+1,29),fill=accent)
            # Distinct glyph per component, stable across regeneration.
            for bit in range(4):
                if index & (1 << bit): draw.rectangle((8+bit*5,23,10+bit*5,24),fill=accent)
        target = ART / f'textures/item/{key}.png'
        target.parent.mkdir(parents=True,exist_ok=True)
        image.save(target)
    source = Image.open(ART/'textures/entity/best_engineering_mech.png').convert('RGBA')
    for chassis, tint in [('best_combat_mech',(205,115,85)),('best_universal_mech',(75,190,175))]:
        texture = source.copy()
        pixels = texture.load()
        for y in range(texture.height):
            for x in range(texture.width):
                r,g,b,a = pixels[x,y]
                if r > 140 and g > 95 and b < 100:
                    pixels[x,y] = (*tint,a)
        texture.save(ART/f'textures/entity/{chassis}.png')
    write(ART/'best_progression.json', definitions)
    for lang, i in [('zh_cn',0),('en_us',1)]:
        path = ART/f'lang/{lang}.json'
        names = json.loads(path.read_text(encoding='utf-8'))
        for key, pair in NAMES.items(): names[f'item.starfall_blueprint.{key}'] = pair[i]
        names['item.starfall_blueprint.best_mech'] = ('SG-E 涓流工程机','SG-E Juanliu Engineering Mech')[i]
        names['entity.starfall_blueprint.best_engineering_mech'] = names['item.starfall_blueprint.best_mech']
        for suffix, pair in [('best_combat_mech',NAMES['best_combat_mech']),('best_universal_mech',NAMES['best_universal_mech'])]: names[f'entity.starfall_blueprint.{suffix}'] = pair[i]
        names['key.starfall_blueprint.mech_fire'] = ('机甲火控射击','Mech weapon fire')[i]
        names['hud.starfall_blueprint.mech.fire_controls'] = ('[%1$s] 射击 · [%2$s] 主作业','[%1$s] Fire · [%2$s] Primary action')[i]
        write(path,names)
    for filename in ('BEST_ENGINEERING.md','BEST_WATER_GOD_PLAN.md','PROGRESSION.md'):
        path=ROOT/'docs'/filename
        if path.exists():
            text=path.read_text(encoding='utf-8')
            if 'BEST_THREE_MECHS.md' not in text:
                heading, separator, body=text.partition('\n')
                path.write_text(heading+'\n\n> 2026-11-12：现行三机组件成长见[三机指南](BEST_THREE_MECHS.md)。下文旧代际晋升与23节点说明为历史记录。\n'+body,encoding='utf-8')
    print(f'Generated {len(NAMES)} Water God definitions, models, icons and translations.')
if __name__ == '__main__': main()
