"""Generate only additive mech support/weapon item resources. Safe beside other generators."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/starfall_blueprint'
NAMES = {
'best_mech_wireless_charger':('无线充能组件','Wireless Charging Module'),
'best_mech_heavy_fusion_core':('重核聚变充能核心','Heavy Fusion Charging Core'),
'best_mech_heavy_fusion_fuel':('重核聚变燃料芯','Heavy Fusion Fuel Cell'),
'best_mech_repair_module':('自修维护模块','Repair Module'),
'best_mech_friendly_repair_module':('友机维修模块','Friendly Mech Repair Module'),
'best_mech_maintenance_drone':('维护无人机组件','Maintenance Drone'),
'best_mech_healing_drone':('治疗无人机组件','Healing Drone'),
'best_mech_medical_supply':('机甲医疗物资','Mech Medical Supply'),
'best_mech_melee_weapon':('机甲近战刃','Mech Blade'),
'best_mech_machine_gun':('机甲机枪','Mech Machine Gun'),
'best_mech_sniper':('机甲狙击炮','Mech Sniper'),
'best_mech_laser':('机甲激光器','Mech Laser'),
'best_mech_explosive_launcher':('机甲爆破发射器','Mech Explosive Launcher'),
'best_mech_attack_drone':('攻击无人机组件','Attack Drone'),
'best_mech_defense_drone':('防御无人机组件','Defense Drone'),
'best_mech_bullet':('机枪弹','Mech Bullet'),
'best_mech_sniper_round':('狙击弹','Mech Sniper Round'),
'best_mech_explosive_round':('爆破弹','Mech Explosive Round'),
}
def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def main():
    definitions = {}
    for index,(key,pair) in enumerate(NAMES.items()):
        definitions[key] = dict(id=key,name=pair[0],unit='件',icon='gear',note='机甲维护/武装实物组件',external=False,vanilla=False,mass=100,category='贝斯特机械',subcategory='机甲系统',recipes=[],uses=[])
        write(ART/f'models/item/{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'starfall_blueprint:item/{key}'}})
        image=Image.new('RGBA',(32,32)); d=ImageDraw.Draw(image)
        accent='#67d6ad' if index<4 else '#e6aa63'
        d.rectangle((5,5,26,26),fill='#293a47',outline=accent,width=2)
        d.rectangle((13,8,18,23),fill=accent); d.rectangle((8,13,23,18),fill=accent)
        for bit in range(4):
            if index & (1<<bit): d.rectangle((6+bit*5,27,8+bit*5,29),fill=accent)
        image.save(ART/f'textures/item/{key}.png')
        ingredients=[{'item':'starfall_blueprint:ordinary_steel'},{'item':'starfall_blueprint:conductor'},{'item':'minecraft:redstone'},{'item':'minecraft:iron_ingot'}]
        count=1
        if key=='best_mech_heavy_fusion_core':
            ingredients=[{'item':i} for i in ['minecraft:nether_star','minecraft:netherite_ingot','minecraft:netherite_ingot','starfall_blueprint:control_board','starfall_blueprint:control_board','starfall_blueprint:conductor','starfall_blueprint:conductor','starfall_blueprint:ordinary_steel','starfall_blueprint:ordinary_steel']]
        elif key=='best_mech_heavy_fusion_fuel':
            ingredients=[{'item':i} for i in ['minecraft:nether_star','minecraft:netherite_ingot','starfall_blueprint:conductor','starfall_blueprint:conductor','minecraft:diamond']]
        elif key.endswith(('bullet','sniper_round','explosive_round')):
            iron, powder, count = (4,4,4) if 'explosive' in key else (4,2,4) if 'sniper' in key else (7,2,16)
            ingredients=[{'item':'minecraft:iron_ingot'}]*iron+[{'item':'minecraft:gunpowder'}]*powder
        elif key=='best_mech_medical_supply':
            ingredients=[{'item':'minecraft:paper'},{'item':'minecraft:honey_bottle'},{'item':'minecraft:glistering_melon_slice'}]; count=4
        else:
            ingredients += [{'item':'starfall_blueprint:ordinary_steel'}]*2
            if 'drone' in key or 'laser' in key or 'sniper' in key: ingredients += [{'item':'minecraft:diamond'},{'item':'minecraft:redstone'}]
        write(ROOT/f'src/main/resources/data/starfall_blueprint/recipe/{key}.json',{'type':'minecraft:crafting_shapeless','category':'equipment','ingredients':ingredients,'result':{'id':f'starfall_blueprint:{key}','count':count}})
    write(ART/'best_systems.json',definitions)
    for lang,index in [('zh_cn',0),('en_us',1)]:
        path=ART/f'lang/{lang}.json'; data=json.loads(path.read_text(encoding='utf-8'))
        for key,pair in NAMES.items(): data[f'item.starfall_blueprint.{key}']=pair[index]
        data['mech.starfall_blueprint.support.controls']=(
            '维护：隔离停稳后潜行持组件右键安装；潜行主手诊断器＋副手钢制固定件拆卸。货舱放钢材/医疗物资；每2秒最多服务1友方，耗3000–4000 XH及1份物资。',
            'Support: isolate and park, then sneak-use a component to install; sneak-use a main-hand diagnostic tool with off-hand steel fasteners to remove. Cargo steel/medical supplies: one ally per 2s, 3000–4000 XH and one supply per treatment.')[index]
        write(path,data)
    from generate_best_visuals import write_assets
    write_assets()
if __name__=='__main__': main()
