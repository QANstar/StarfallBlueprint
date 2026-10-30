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


MECH_MESSAGES = {
 'message.best_mech_report': ('工程机甲｜电量 %1$s/%2$s XH｜热 %3$s/1000｜磨损 %4$s/2400｜结构 %5$s｜隔离 %6$s｜热锁 %7$s（400恢复）｜工时 %8$s/100｜作业冷却 %9$s/20刻', 'Engineering Mech | Energy %1$s/%2$s XH | Heat %3$s/1000 | Wear %4$s/2400 | Structure %5$s | Isolated %6$s | Heat lock %7$s (recover 400) | Work ticks %8$s/100 | Work cooldown %9$s/20 ticks'),
 'message.best_mech_power': ('机甲电源隔离：%1$s｜电量 %2$s XH', 'Mech battery isolated: %1$s | Energy %2$s XH'),
 'message.best_mech_charging': ('机甲蓄能 %1$s/%2$s XH', 'Mech charging %1$s/%2$s XH'),
 'tooltip.best_mech.resources': ('%1$s/%2$s XH｜热 %3$s｜磨损 %4$s', '%1$s/%2$s XH | Heat %3$s | Wear %4$s'),
 'tooltip.best_mech.structure': ('结构 %1$s｜电池隔离 %2$s', 'Structure %1$s | Battery isolated %2$s'),
 'tooltip.best_mech.charge': ('潜行按住右键瞄准基地储能块：≤5000 XH/刻', 'Sneak + hold use on base storage: up to 5000 XH/t'),
 'tooltip.best_mech.deploy': ('非潜行右键地面部署；须3×3承托净空', 'Use on ground to deploy; needs 3x3 support/clearance'),
 'tooltip.best_mech.isolation': ('主手机甲、副手诊断器：非潜行对空气右键切换隔离', 'Mech in main hand, diagnostic offhand: use in air to toggle isolation'),
 'message.best_page_3': ('机甲', 'Mech'),
 'message.best_action_17': ('学习机甲驾驶', 'Learn Mech Operation'),
 'message.best_action_18': ('装配工程机甲', 'Assemble Mech'),
 'message.best_action_19': ('制造散热模组', 'Assemble Radiator Module'),
 'message.best_action_20': ('制造强化驱动模组', 'Assemble Reinforced Drive'),
 'message.best_mech_radiator_cost': ('需本人三级、轻载、诊断与驾驶；普通钢6、导相板4、陶瓷护套4、钢制固定件4', 'Requires your level 3, Mobility, Diagnostics and Mech Operation; 6 steel, 4 conductive plates, 4 ceramic sheaths, 4 steel fasteners.'),
 'message.best_mech_drive_cost': ('需本人三级、轻载、诊断与驾驶；普通钢8、承相嵌材4、导相板4、控制板2、钢制固定件4', 'Requires your level 3, Mobility, Diagnostics and Mech Operation; 8 steel, 4 load inserts, 4 conductive plates, 2 boards, 4 steel fasteners.'),
 'message.best_mech_radiator_assembled': ('已制造散热模组', 'Radiator Module assembled.'),
 'message.best_mech_drive_assembled': ('已制造强化驱动模组', 'Reinforced Drive Module assembled.'),
 'mech.module.none': ('通用槽：空', 'Utility slot: empty'),
 'mech.module.installed': ('通用槽：%s', 'Utility slot: %s'),
 'mech.module.controls': ('潜行主手模组右键安装；潜行主手诊断器＋副手钢制固定件拆回（不耗固定件）；无固定件时潜行诊断切换隔离', 'Sneak-use a main-hand module to install; sneak-use a main-hand diagnostic tool with an offhand steel fastener to remove (fastener retained). Without the fastener, sneak-diagnose toggles isolation.'),
 'mech.module.failed': ('装拆失败：需本人驾驶全知识，机甲隔离、空载、存活、停稳落地且不在流体；安装须空槽，拆回须主背包空间', 'Module change rejected: requires all personal mech knowledge and an isolated, unoccupied, living, stationary mech on dry ground. Installation needs an empty slot; removal needs main-inventory room.'),
 'tooltip.best_mech_radiator': ('自然散热4／刻（基础2）；标准机体速度0.22、移动400 XH／刻热4；强化机体速度0.18、600 XH／刻热6', 'Passive cooling 4/t (base 2); standard body: speed 0.22, 400 XH/t, 4 heat/t; reinforced body: speed 0.18, 600 XH/t, 6 heat/t.'),
 'tooltip.best_mech_drive': ('标准机体速度0.28、移动600 XH／刻热6；强化机体速度0.23、900 XH／刻热8；自然散热2／刻', 'Standard body: speed 0.28, movement 600 XH/t and 6 heat/t; reinforced body: speed 0.23, 900 XH/t and 8 heat/t. Passive cooling 2/t.'),
 'tooltip.best_mech_module_install': ('仅1个互斥通用槽；主手持模组潜行右键已部署机甲安装，需驾驶全知识、隔离空载、存活、停稳落地且不在流体；先拆后换', 'One exclusive utility slot. Sneak-use a main-hand module on a deployed mech; requires all mech knowledge, isolation, no passengers, alive and stationary on dry ground. Remove before swapping.'),
 'message.best_mech_learning_cost': ('需三级、轻载与诊断；承相4、导相4、感相2、控制板2', 'Requires level 3, Mobility and Diagnostics; 4 load inserts, 4 conductive plates, 2 sensing preforms, 2 boards.'),
 'message.best_mech_assembly_cost': ('需三级、轻载、诊断与驾驶；钢32、承相16、导相8、板4、固定件16、磨头2、感相2、护套8；不耗能匣，出厂空电', 'Requires level 3, Mobility, Diagnostics and Mech Operation; 32 steel, 16 load inserts, 8 conductive plates, 4 boards, 16 fasteners, 2 drill heads, 2 sensors, 8 sheaths. No cells consumed; starts empty.'),
 'message.best_mech_learned': ('已学习机甲驾驶', 'Mech Operation learned.'),
 'message.best_mech_assembled': ('已装配工程机甲原型；储能为空', 'Engineering Mech Prototype assembled; energy starts empty.'),
 'message.best_mech_required': ('需要本人三级、轻载通行、工务诊断与机甲驾驶', 'Requires your level 3, Light Mobility, Diagnostics and Mech Operation.'),
 'message.best_mech_progress': ('研究%1$s｜轻%2$s 诊%3$s 驾%4$s', 'R%1$s | Mob %2$s Diag %3$s Pilot %4$s'),
 'message.best_mech_hint': ('WASD驾驶；G工程臂作业；隔离空载后可收纳', 'WASD drive; G arm action; isolate before packing.'),
 'hud.mech.title': ('工程机甲原型', 'Engineering Mech Prototype'),
 'hud.mech.energy': ('电量 %1$s / %2$s XH', 'Energy %1$s / %2$s XH'),
 'hud.mech.heat': ('热量 %1$s / %2$s；恢复≤%3$s', 'Heat %1$s / %2$s; recover at %3$s'),
 'hud.mech.wear': ('磨损 %1$s / %2$s', 'Wear %1$s / %2$s'),
 'hud.mech.isolation': ('电池隔离：%1$s', 'Battery isolated: %1$s'),
 'hud.mech.drill': ('[%1$s] 工程臂单点作业 · 20刻冷却', '[%1$s] Single-block arm action · 20-tick cooldown'),
 'hud.mech.wait': ('[%1$s] 请求等待 %2$s 刻（服务端校验）', '[%1$s] Request wait: %2$s ticks (server checked)'),
 'hud.mech.controls': ('WASD驾驶 · Shift下车 · 无跳跃', 'WASD drive / Shift dismount / no jump'),
 'key.mech_drill': ('工程臂作业', 'Engineering arm action'),
 'key.categories': ('星落蓝图', 'Starfall Blueprint'),
}


MECH_MESSAGES.update({
 'hud.mech.panel.title': ('工程机甲', 'ENGINEERING MECH'),
 'hud.mech.panel.health': ('机体', 'Hull'),
 'hud.mech.panel.energy': ('电量', 'Energy'),
 'hud.mech.panel.heat': ('热量', 'Heat'),
 'hud.mech.panel.wear': ('磨损', 'Wear'),
 'hud.mech.panel.ready': ('运行 · 系统就绪', 'ONLINE · Systems ready'),
 'hud.mech.panel.destroyed': ('停机 · 机体损毁', 'OFFLINE · Hull destroyed'),
 'hud.mech.panel.isolated': ('停机 · 电池已隔离', 'ISOLATED · Battery disconnected'),
 'hud.mech.panel.thermal_locked': ('热锁停机 · 冷却至400恢复', 'HEAT LOCK · Cool to 400'),
 'hud.mech.panel.worn_out': ('停机 · 磨损耗尽', 'WORN OUT · Service required'),
 'hud.mech.panel.unqualified': ('停机 · 驾驶知识不足', 'OFFLINE · Training required'),
 'hud.mech.panel.no_power': ('停机 · 移动电量不足', 'NO POWER · Movement disabled'),
 'hud.mech.panel.drill_power': ('运行 · 作业电量不足', 'ONLINE · Low work energy'),
 'hud.mech.panel.hot': ('运行 · 接近高热', 'ONLINE · High heat'),
 'hud.mech.panel.low_power': ('运行 · 电量偏低', 'ONLINE · Low energy'),
 'hud.mech.panel.worn': ('运行 · 接近磨损上限', 'ONLINE · High wear'),
 'hud.mech.panel.request': ('[%1$s] 作业请求等待 %2$s 刻', '[%1$s] Work request wait: %2$s ticks'),
 'hud.mech.panel.standby': ('[%1$s] 工程臂作业 · %2$s XH', '[%1$s] Arm action · %2$s XH'),
 'hud.mech.panel.controls': ('%1$s 移动 · %2$s 下车', '%1$s move · %2$s dismount'),
})


MECH_MESSAGES.update({
 'message.best_page_4': ('工程臂', 'Tool arms'),
 'message.best_action_21': ('制造钻切工程臂', 'Assemble drilling arm'),
 'message.best_action_22': ('制造挖掘工程臂', 'Assemble excavator arm'),
 'message.best_mech_drill_arm_cost': ('需本人三级、轻载、诊断与驾驶；钢8、导相4、磨头2、控制板1、钢制固定件4', 'Requires your level 3, Mobility, Diagnostics and Mech Operation; 8 steel, 4 conductive plates, 2 drill heads, 1 board, 4 steel fasteners.'),
 'message.best_mech_excavator_arm_cost': ('需本人三级、轻载、诊断与驾驶；钢8、承相4、导相2、钢制固定件4、护套2', 'Requires your level 3, Mobility, Diagnostics and Mech Operation; 8 steel, 4 load inserts, 2 conductive plates, 4 steel fasteners, 2 sheaths.'),
 'message.best_mech_drill_arm_assembled': ('已制造钻切工程臂', 'Drilling arm assembled.'),
 'message.best_mech_excavator_arm_assembled': ('已制造挖掘工程臂', 'Excavator arm assembled.'),
 'tooltip.best_mech_drill_arm': ('铁镐级单点钻切：32,000 XH、热120、磨损1、20刻冷却；仅成功作业计费', 'Iron-pickaxe single-block drilling: 32,000 XH, 120 heat, 1 wear, 20-tick cooldown; charged only on success.'),
 'tooltip.best_mech_excavator_arm': ('仅铁铲可采方块单点挖掘：12,000 XH、热45、磨损1、20刻冷却；普通掉落', 'Single-block excavation of shovel-mineable blocks only: 12,000 XH, 45 heat, 1 wear, 20-tick cooldown; normal drops.'),
 'tooltip.best_mech_arm_install': ('独立工程臂槽最多1件；完整驾驶知识，隔离空载、存活、停稳落地且非流体，潜行主手持臂安装空槽；先拆后换', 'One separate arm slot. All mech knowledge required; sneak-use a main-hand arm on an empty slot of an isolated, unoccupied, living, stationary mech on dry ground. Remove before swapping.'),
 'mech.arm.none': ('工程臂：空', 'Tool arm: empty'),
 'mech.arm.installed': ('工程臂：%s', 'Tool arm: %s'),
 'mech.arm.controls': ('潜行主手工程臂右键安装；主手诊断器＋副手原版铁镐潜行拆回（铁镐不耗不损）；无臂仍可驾驶', 'Sneak-use a main-hand arm to install; sneak-use a main-hand diagnostic tool + offhand vanilla iron pickaxe to remove (pickaxe retained and undamaged). Driving works without an arm.'),
 'mech.arm.failed': ('工程臂装拆失败：需完整驾驶知识，机甲隔离、空载、存活、停稳落地且非流体；安装须空槽，拆回须36格主背包有空格', 'Arm change rejected: all mech knowledge and an isolated, unoccupied, living, stationary mech on dry ground required. Install into an empty slot; removal needs an empty slot in the 36-slot main inventory.'),
 'hud.mech.panel.no_arm': ('运行 · 未安装工程臂，仍可移动', 'ONLINE · No tool arm installed; movement available'),
 'hud.mech.panel.work_power': ('运行 · 作业电量不足', 'ONLINE · Insufficient work energy'),
 'hud.mech.panel.arm': ('%s', '%s'),
})

MECH_MESSAGES.update({
 'message.best_action_23': ('制造强化机体', 'Assemble reinforced body'),
 'message.best_mech_reinforced_body_cost': ('需本人三级、轻载、诊断与驾驶；普通钢24、承相12、导相8、钢制固定件16、护套8', 'Requires your level 3, Mobility, Diagnostics and Mech Operation; 24 steel, 12 load inserts, 8 conductive plates, 16 steel fasteners, 8 sheaths.'),
 'message.best_mech_reinforced_body_assembled': ('已制造强化机体', 'Reinforced Mech Body assembled.'),
 'tooltip.best_mech_reinforced_body': ('容量30M XH、生命上限160、货舱18格；基础／强化驱动速度0.18／0.23，移动600／900 XH与热6／8每刻；磨损2400、热1000／400、自然冷却2／散热4，工程臂不变', '30M XH capacity, 160 max health, 18 cargo slots; base/drive speed 0.18/0.23, movement 600/900 XH and 6/8 heat per tick. Wear 2400, heat 1000/400, cooling 2/radiator 4; tool arms unchanged.'),
 'tooltip.best_mech_body_install': ('独立机体槽；本人完整驾驶知识，隔离空载存活停稳落地非流体时潜行主手安装，保留精确生命与全部状态，不治疗；诊断器主手＋铁块副手潜行拆回，铁块不耗，36格主背包须空格；电量>20M、生命>100或货格9..17非空拒绝', 'Separate body slot. With all mech knowledge, sneak-use in main hand on an isolated, unoccupied, living, stationary mech on dry ground; preserves exact health and all state, without healing. Sneak-use diagnostic + offhand iron block to remove (block retained); needs an empty main-inventory slot. Rejects energy >20M, health >100 or occupied cargo slots 9..17.'),
 'mech.body.none': ('标准机体', 'Standard body'),
 'mech.body.installed': ('机体：%s', 'Body: %s'),
 'mech.body.controls': ('潜行主手强化机体安装；主手诊断器＋副手铁块潜行拆回（铁块不耗）；拆回须主背包空格、电量≤20M、生命≤100且货格9..17为空', 'Sneak-use a main-hand reinforced body to install; diagnostic + offhand iron block to remove (block retained). Removal needs an empty main-inventory slot, energy ≤20M, health ≤100 and empty cargo slots 9..17.'),
 'mech.body.failed': ('机体整备失败：须本人驾驶全知识、隔离空载存活停稳落地非流体；安装仅标准机体，拆回须主背包空格且电量≤20M、生命≤100、货格9..17为空；状态不变', 'Body change rejected: all mech knowledge and an isolated, unoccupied, living, stationary mech on dry ground required. Install only on a standard body; removal needs an empty main-inventory slot, energy ≤20M, health ≤100 and empty cargo slots 9..17. State preserved.'),
 'menu.best_mech_cargo': ('机甲货舱', 'Mech cargo'),
 'ui.mech_cargo_hint': ('隔离整备 · 禁止存入收纳机甲', 'Isolated service · No packed mechs'),
 'hud.mech.panel.body': ('机体：%s', 'Body: %s'),
})


BEST_UI_MESSAGES = {
 'tab_0': ('工务', 'Workshop'), 'tab_1': ('轻载', 'Mobility'),
 'tab_2': ('诊断', 'Service'), 'tab_3': ('机甲', 'Mech'),
 'tab_4': ('工程臂', 'Tool arms'),
 'material_action_23': ('强化机体', 'Reinforced body'),
 'material_action_21': ('钻切工程臂', 'Drilling arm'), 'material_action_22': ('挖掘工程臂', 'Excavator arm'),
 'hint_4': ('独立工程臂槽；先拆后换。诊断器主手＋铁镐副手潜行拆回，须主背包空格', 'Separate arm slot; remove before swapping. Sneak-use diagnostic + offhand iron pickaxe to remove; main inventory needs an empty slot.'),
 'knowledge_research': ('工务知识：%s', 'Engineering: %s'),
 'knowledge_light': ('轻载通行：%s', 'Light mobility: %s'),
 'knowledge_diagnostics': ('装备诊断：%s', 'Diagnostics: %s'),
 'knowledge_mech': ('机甲驾驶：%s', 'Mech operation: %s'),
 'research_0': ('未学习', 'Unlearned'), 'research_1': ('基础工务', 'Basic engineering'),
 'research_2': ('工程钻切', 'Engineering cutting'), 'research_3': ('野外整备', 'Field service'),
 'learned': ('已学习', 'Learned'), 'unlearned': ('未学习', 'Unlearned'),
 'material_action_17': ('驾驶学习', 'Operation study'), 'material_action_18': ('机甲装配', 'Mech assembly'),
 'material_action_19': ('散热模组', 'Radiator module'), 'material_action_20': ('强化驱动', 'Reinforced drive'),
 'materials_for': ('%s · %s', '%s · %s'), 'material_count': ('%s/%s', '%s/%s'),
 'cost_entry': ('%s：已有 %s / 所需 %s', '%s: have %s / need %s'),
 'no_materials': ('无需材料', 'No materials needed'),
 'hint_0': ('维修/冷却持主手；整备包主手、装备副手', 'Repair/cool: hold equipment. Service: kit in main hand, gear in offhand.'),
 'hint_1': ('装卸/切档持主手骨架；跨步档站立也耗能', 'Hold frame to modify; stride mode uses energy while standing.'),
 'hint_2': ('隔离/接通时，将装备持于主手', 'Hold equipment in your main hand to isolate or reconnect.'),
 'hint_3': ('数量为已有/所需；悬停学习、装配、模组或强化机体切换材料表', 'Have/need shown; hover study, assembly, modules or reinforced body to inspect materials.'),
 'detail_4': ('恢复200点工务耐久；电量与温度不变', 'Restores 200 engineering durability; preserves charge and heat.'),
 'detail_7': ('降低400温度并返还空桶', 'Removes 400 heat and returns an empty bucket.'),
 'detail_10': ('安装后跨步档默认关闭', 'Stride mode is off after installation.'),
 'detail_11': ('跨步档站立也持续耗能', 'Stride mode consumes energy even while standing.'),
 'detail_12': ('返还跨步组件；电量、温度与耐久不变', 'Returns the stride module; preserves charge, heat and durability.'),
 'ready': ('可执行', 'Ready'), 'already_learned': ('已学习', 'Already learned'),
 'unavailable': ('无法进行此操作', 'Action unavailable'),
 'requires_research_1': ('前置未满足：先学习基础工务', 'Requires basic engineering'),
 'requires_research_2': ('前置未满足：先学习工程钻切', 'Requires engineering cutting'),
 'requires_research_3': ('前置未满足：先学习野外整备', 'Requires field service'),
 'requires_light': ('前置未满足：先学习轻载通行', 'Requires light mobility'),
 'requires_diagnostics': ('前置未满足：先学习装备诊断', 'Requires diagnostics'),
 'requires_mech_basics': ('前置未满足：需野外整备、轻载通行与装备诊断', 'Requires field service, light mobility and diagnostics'),
 'requires_mech': ('前置未满足：需机甲驾驶及全部基础知识', 'Requires mech operation and all supporting knowledge'),
 'target_equipment': ('目标不符：将骨架或钻切器持于主手', 'Hold a frame or cutter in your main hand'),
 'target_frame': ('目标不符：脱下骨架并持于主手', 'Remove the frame and hold it in your main hand'),
 'target_worn': ('目标不符：主手需持有待修装备', 'Hold worn equipment in your main hand'),
 'target_hot': ('目标不符：主手需持有待冷却装备', 'Hold hot equipment in your main hand'),
 'already_isolated': ('目标不符：电池已隔离', 'Battery is already isolated'),
 'already_connected': ('目标不符：电池已接通', 'Battery is already connected'),
 'already_installed': ('目标不符：骨架已安装跨步组件', 'Frame already has a stride module'),
 'not_installed': ('目标不符：骨架尚未安装跨步组件', 'Frame has no stride module'),
 'missing_materials': ('材料不足（污染或耗尽材料不可用）', 'Missing materials (contaminated or depleted items cannot be used)'),
 'space_output': ('背包不足：需空间存放成品', 'Make room in your inventory for the output'),
 'space_bucket': ('背包不足：需空间存放空桶', 'Make room in your inventory for the empty bucket'),
 'space_module': ('背包不足：需空间存放拆回的组件', 'Make room in your inventory for the removed module'),
}
for action, labels in enumerate([
 ('学习基础工务', 'Study basic engineering'), ('学习工程钻切', 'Study engineering cutting'),
 ('装配轻型助力骨架', 'Assemble assist frame'), ('装配工程钻切器', 'Assemble cutter'),
 ('维修主手装备', 'Repair held equipment'), ('学习野外整备', 'Study field service'),
 ('装配便携整备包', 'Assemble service kit'), ('冷却主手装备', 'Cool held equipment'),
 ('学习轻载通行', 'Study light mobility'), ('装配跨步组件', 'Assemble stride module'),
 ('安装主手骨架组件', 'Install stride module'), ('切换骨架跨步档', 'Toggle stride mode'),
 ('拆回骨架组件', 'Remove stride module'), ('学习装备诊断', 'Study diagnostics'),
 ('装配诊断工具', 'Assemble diagnostic tool'), ('隔离装备电池', 'Isolate battery'),
 ('接通装备电池', 'Reconnect battery'), ('学习机甲驾驶', 'Study mech operation'),
 ('装配工程机甲', 'Assemble engineering mech'),
 ('制造散热模组', 'Assemble radiator module'), ('制造强化驱动模组', 'Assemble reinforced drive'),
 ('制造钻切工程臂', 'Assemble drilling arm'), ('制造挖掘工程臂', 'Assemble excavator arm'),
 ('制造强化机体', 'Assemble reinforced body'),
]):
    BEST_UI_MESSAGES[f'action_{action}'] = labels
MECH_MESSAGES.update({f'ui.best_{key}': value for key, value in BEST_UI_MESSAGES.items()})


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
    # Original 128px metal atlas. Model UV islands select chassis, strut, glass and drill panels.
    from PIL import Image, ImageDraw
    texture = Image.new('RGBA', (128, 128), '#596b75')
    draw = ImageDraw.Draw(texture)
    for y in range(0, 128, 8):
        for x in range(0, 128, 8):
            draw.rectangle((x,y,x+7,y+7), fill='#596b75' if (x+y)//8%2 else '#627782')
            draw.line((x,y,x+7,y), fill='#81969f')
            draw.point((x+1,y+2), fill='#b8cbd0')
    draw.rectangle((0,24,37,43), fill='#274650')
    draw.rectangle((5,29,17,41), fill='#397d88', outline='#75d9c0')
    draw.line((6,30,15,30,6,39), fill='#b9eee4', width=1)
    draw.rectangle((19,29,31,41), fill='#1b2b35', outline='#81969f')
    for y in range(31,41,3): draw.line((21,y,29,y), fill='#e4ae51')
    for x in range(48,108,6):
        draw.polygon([(x,0),(x+3,0),(x+8,13),(x+5,13)],fill='#e4ae51')
    for y in (50,76):
        draw.rectangle((28,y,67,y+3),fill='#1b2b35')
        for x in range(29,66,5):draw.line((x,y,x+3,y+3),fill='#e4ae51',width=2)
    draw.rectangle((64,84,97,100),fill='#485962',outline='#b8cbd0')
    for x in range(66,96,5):draw.line((x,86,x,98),fill='#1b2b35')
    for y in (87,91,95):draw.line((100,y,123,y+4),fill='#d5e2e3',width=2)
    draw.rectangle((100,104,127,116),fill='#b8cbd0')
    for y in range(105,116,3):draw.line((100,y,127,y+3),fill='#344852',width=2)
    entity_texture = ART / 'textures/entity/best_engineering_mech.png'
    entity_texture.parent.mkdir(parents=True, exist_ok=True)
    texture.save(entity_texture)
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
        for entry, pair in MECH_MESSAGES.items():
            prefix, suffix = entry.split('.', 1)
            if entry == 'key.categories':
                names['key.categories.starfall_blueprint'] = pair[index]
            else:
                names[f'{prefix}.{MOD}.{suffix}'] = pair[index]
        names[f'entity.{MOD}.best_engineering_mech'] = best.BEST_NAMES['best_mech'][index]
        write(path, names)
    (ROOT / 'docs/BEST_ENGINEERING.md').write_text(best.BEST_DOC, encoding='utf-8', newline='\n')
    print(f'Generated {len(best.BEST_NAMES)} Best textures/models/names, public bench resources and engineering/field service/light mobility/diagnostics documentation.')


if __name__ == '__main__':
    main()
