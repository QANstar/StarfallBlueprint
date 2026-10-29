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
        diagnostics_messages = {
            'best_diagnostics_learned': ('已学习工务诊断', 'Field Diagnostics learned.'),
            'best_diagnostics_required': ('需要本人三级野外整备与工务诊断', 'Requires your Field Servicing (level 3) and Field Diagnostics.'),
            'best_battery_isolated': ('电池已隔离：停止动力与全部补能，储能和其它状态保留', 'Battery isolated: powered operation and all charging stopped; stored energy and other state preserved.'),
            'best_battery_connected': ('电池已接通；电量与其它状态保持原状', 'Battery connected; energy and other state unchanged.'),
            'best_already_isolated': ('电池已经隔离', 'Battery is already isolated.'),
            'best_already_connected': ('电池已经接通', 'Battery is already connected.'),
            'best_diagnostic_target': ('副手需持轻型助力骨架或工程钻切器', 'Hold an assist frame or drill-cutter in your offhand.'),
            'best_diagnostic_report': ('%1$s｜电量 %2$s/%3$s XH｜热量 %4$s（停机 %5$s／恢复 %6$s）｜过热锁 %7$s｜工务耐久 %8$s/%9$s｜电池隔离 %10$s｜本人使用知识 %11$s', '%1$s | Energy %2$s/%3$s XH | Heat %4$s (stop %5$s / resume %6$s) | Heat lock %7$s | Durability %8$s/%9$s | Battery isolated %10$s | Personal operation knowledge %11$s'),
            'best_diagnostic_frame': ('跨步组件安装 %1$s｜跨步档启用 %2$s｜本人轻载知识可用 %3$s', 'Stride module installed %1$s | Stride mode enabled %2$s | Personal Light Mobility available %3$s'),
            'best_state_yes': ('是', 'Yes'),
            'best_state_no': ('否', 'No'),
            'best_diagnostic_hint': ('主手诊断器右键检查副手骨架／钻切器；只读，不维修、补能或降温', 'Use this tool in your main hand to inspect an offhand frame/cutter. Read-only: no repair, charging or cooling.'),
            'best_diagnostic_assembled': ('已装配工务诊断器', 'Field Diagnostic Tool assembled.'),
            'best_diagnostics_cost': ('需本人三级野外整备；承相结构嵌材1、导相铜复材板2、控制板1；独立知识，不要求轻载通行', 'Requires personal Field Servicing (level 3); 1 load insert, 2 conductive plates, 1 control board. Independent knowledge; no Light Mobility prerequisite.'),
            'best_diagnostic_tool_cost': ('需本人三级与工务诊断；普通钢2、导相铜复材板1、感相计量预坯1、控制板1、陶瓷护套1', 'Requires level 3 and Field Diagnostics; 2 steel, 1 conductive plate, 1 sensing preform, 1 control board, 1 ceramic sheath.'),
            'best_isolate_cost': ('需本人三级与工务诊断；主手骨架／钻切器；停止动力与全部补能，保留储能与其它数据', 'Requires level 3 and Field Diagnostics; main-hand frame/cutter. Stops powered operation and all charging; preserves stored energy and other data.'),
            'best_connect_cost': ('需本人三级与工务诊断；主手骨架／钻切器；仅接通，不补电、不维修、不降温', 'Requires level 3 and Field Diagnostics; main-hand frame/cutter. Reconnects only; no charging, repair or cooling.'),
            'best_action_13': ('学习工务诊断', 'Learn Field Diagnostics'),
            'best_action_14': ('制造工务诊断器', 'Assemble Diagnostic Tool'),
            'best_action_15': ('隔离主手设备电池', 'Isolate Main-Hand Battery'),
            'best_action_16': ('接通主手设备电池', 'Connect Main-Hand Battery'),
            'best_page_0': ('工务', 'Work'),
            'best_page_1': ('轻载', 'Stride'),
            'best_page_2': ('诊断', 'Diag'),
            'best_next_page': ('下一页', 'Next Page'),
            'best_diagnostics_progress': ('研究 %1$s/3｜轻载 %2$s｜诊断 %3$s', 'Research %1$s/3 | Mobility %2$s | Diagnostics %3$s'),
            'best_diagnostics_hint': ('诊断器主手右键检查副手；隔离仅在工位操作，维修冷却保留隔离', 'Tool: main hand; device: offhand. Isolate at bench.'),
            'best_isolation_target': ('请将骨架或钻切器持于主手', 'Hold a frame or drill-cutter in your main hand.'),
        }
        for entry, pair in diagnostics_messages.items():
            names[f'message.{MOD}.{entry}'] = pair[index]
        write(path, names)
    (ROOT / 'docs/BEST_ENGINEERING.md').write_text(best.BEST_DOC, encoding='utf-8', newline='\n')
    print(f'Generated {len(best.BEST_NAMES)} Best textures/models/names, public bench resources and engineering/field service/light mobility/diagnostics documentation.')


if __name__ == '__main__':
    main()
