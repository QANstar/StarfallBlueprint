"""Generate Best engineering and field service resources; invoked last by catalogue generation."""
from pathlib import Path
import importlib.util
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/starfall_blueprint'
DATA = ROOT / 'src/main/resources/data/starfall_blueprint'
ATLAS = ROOT.parent / 'RE_BFL/04_游戏设定/产线图册'
MOD = 'starfall_blueprint'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')


def main():
    spec = importlib.util.spec_from_file_location('best_expansion', ATLAS / 'best_expansion.py')
    best = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(best)
    best.draw_best_art(ATLAS / 'item-art', best.BEST_NAMES)
    for key in best.BEST_NAMES:
        target = ART / 'textures/item' / f'{key}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ATLAS / 'item-art' / f'{key}.png', target)
        if key != 'best_workbench':
            model = dict(parent='minecraft:item/handheld' if key == 'best_cutter' else 'minecraft:item/generated',
                         textures=dict(layer0=f'{MOD}:item/{key}'))
            write(ART / 'models/item' / f'{key}.json', model)
    armor = ART / 'textures/models/armor/best_frame_layer_2.png'
    armor.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ATLAS / 'item-art/best_frame_layer_2.png', armor)
    # Purpose-built open workbench geometry, with a vice, control face and tool shelf.
    faces = {face: dict(texture='#surface', uv=[0,0,16,16]) for face in ('north','south','east','west','up','down')}
    def box(start, end, texture='surface'):
        return dict(from_=start, to=end, faces={face: dict(data, texture='#'+texture) for face, data in faces.items()})
    elements = [box([0,10,0],[16,12,16], 'top'),
                box([1,0,1],[4,10,4]), box([12,0,1],[15,10,4]),
                box([1,0,12],[4,10,15]), box([12,0,12],[15,10,15]),
                box([3,2,3],[13,4,14]), box([3,5,1],[13,9,3], 'control'),
                box([2,12,4],[6,14,9]), box([3,14,5],[5,16,8])]
    for element in elements:
        element['from'] = element.pop('from_')
    write(ART / 'models/block/best_workbench.json', dict(
        parent='minecraft:block/block', ambientocclusion=True,
        textures=dict(surface=f'{MOD}:block/preparation_side', top=f'{MOD}:block/preparation_top',
                      control=f'{MOD}:item/best_workbench', particle=f'{MOD}:block/preparation_side'), elements=elements))
    write(ART / 'models/item/best_workbench.json', dict(parent=f'{MOD}:block/best_workbench'))
    write(ART / 'blockstates/best_workbench.json', dict(variants={
        f'facing={face}': dict(model=f'{MOD}:block/best_workbench', y=rotation)
        for face, rotation in [('north',0),('east',90),('south',180),('west',270)]}))
    write(DATA / 'loot_table/blocks/best_workbench.json', dict(type='minecraft:block', pools=[dict(
        rolls=1, conditions=[dict(condition='minecraft:survives_explosion')],
        entries=[dict(type='minecraft:item', name=f'{MOD}:best_workbench')])]))
    path = ROOT / 'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(path.read_text(encoding='utf-8'))
    tag['values'] = list(dict.fromkeys(tag['values']+[f'{MOD}:best_workbench']))
    write(path, tag)
    for lang, index in [('zh_cn',0),('en_us',1)]:
        path = ART / 'lang' / f'{lang}.json'
        names = json.loads(path.read_text(encoding='utf-8'))
        for key, pair in best.BEST_NAMES.items():
            prefix = 'block' if key == 'best_workbench' else 'item'
            names[f'{prefix}.{MOD}.{key}'] = pair[index]
            if key == 'best_workbench':
                names.pop(f'item.{MOD}.{key}', None)
        messages = {
            'tooltip.usage': ('主手持整备包，副手持受损骨架／钻切器，右键维修；须学习野外整备',
                              'Hold kit in main hand and damaged frame/cutter in offhand; right-click to repair. Requires Field Servicing.'),
            'tooltip.cost': ('消耗1普通钢与1次整备包耐久，恢复200工务耐久；不补能、不降温',
                             'Consumes 1 ordinary steel and 1 kit durability to restore 200 equipment durability; energy and heat stay unchanged.'),
            'message.knowledge': ('请先学习野外整备', 'Learn Field Servicing first.'),
            'message.target': ('副手需持受损的骨架或钻切器', 'Hold a damaged frame or drill-cutter in your offhand.'),
            'message.exhausted': ('整备包已耗尽', 'The service kit is exhausted.'),
            'message.steel': ('背包缺少可用的普通钢', 'No usable ordinary steel in inventory.'),
            'message.repaired': ('维修完成；电量与温度保持原状', 'Repair complete; energy and temperature are unchanged.'),
        }
        for entry, pair in messages.items():
            prefix, suffix = entry.split('.')
            names[f'{prefix}.{MOD}.best_service_kit.{suffix}'] = pair[index]
        mobility_messages = {
            'tooltip.best_stride_module.usage': ('仅在工务装配台人工制造；主手骨架安装1件，默认疾跑档；需本人三级野外整备与轻载通行',
                                                'Assemble manually at the workbench; install one on a main-hand frame. Defaults to Sprint mode. Requires personal Field Servicing (level 3) and Light Mobility.'),
            'tooltip.best_stride_module.cost': ('跨步档高度＋0.4；地面站定也耗24XH／刻、发热4（自然冷2→净2），100工作刻磨损1',
                                               'Stride mode: +0.4 step height. Standing ready on ground also costs 24 XH/t and 4 heat/t (2 net after cooling); 1 wear per 100 work ticks.'),
            'tooltip.best_frame.stride': ('跨步与疾跑＋15%互斥；地面、非潜行／疾跑／水／飞行／滑翔／骑乘且就绪时生效；不改跳跃、摔伤或护甲',
                                         'Stride and +15% sprint assist are exclusive. Requires ready, grounded, non-sneaking, non-sprinting, out of water, non-flying, non-gliding and unmounted. Jumping, fall damage and armor stay unchanged.'),
            'tooltip.best_frame.stride_idle': ('跨步档地面站定仍耗能；工位切回疾跑档或脱下停用。拆回需背包空间，保留骨架电热、磨损、名字与工作刻',
                                              'Stride mode consumes power even while standing on ground. Switch to Sprint at the workbench or unequip to stop. Removal requires inventory space and preserves frame energy, heat, wear, name and work ticks.'),
        }
        for entry, pair in mobility_messages.items():
            prefix, owner, suffix = entry.split('.')
            names[f'{prefix}.{MOD}.{owner}.{suffix}'] = pair[index]
        write(path, names)
    (ROOT / 'docs/BEST_ENGINEERING.md').write_text(best.BEST_DOC, encoding='utf-8', newline='\n')
    print(f'Generated {len(best.BEST_NAMES)} Best textures/models/names, public bench resources and engineering/field service/light mobility documentation.')


if __name__ == '__main__':
    main()
