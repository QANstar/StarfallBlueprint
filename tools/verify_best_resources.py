"""Static Best boundary checks; --regenerate also verifies byte-stable full generation."""
from pathlib import Path
import hashlib
import json
import runpy
import sys

ROOT = Path(__file__).resolve().parents[1]
ATLAS = ROOT.parent / 'RE_BFL/04_游戏设定/产线图册'
ART = ROOT / 'src/main/resources/assets/starfall_blueprint'
DATA = ROOT / 'src/main/resources/data/starfall_blueprint'
sys.path.insert(0, str(ATLAS))
from best_expansion import BEST_NAMES, ASSEMBLY, ASSEMBLY_KNOWLEDGE, POWERED_EQUIPMENT, LEARNING
from generate_best_resources import BEST_UI_MESSAGES


# Only the explicitly migrated SG-E1/SG-E2 names and parameters may differ from old snapshots.
# Every exception must equal the current canonical generator value; other translations remain exact.
WATER_GOD_TRANSLATION_MIGRATIONS = {'entity.starfall_blueprint.best_engineering_mech', 'hud.starfall_blueprint.mech.title', 'tooltip.starfall_blueprint.best_heavy_mech', 'message.starfall_blueprint.best_action_26', 'ui.starfall_blueprint.best_hint_5', 'ui.starfall_blueprint.best_material_action_26', 'mech.starfall_blueprint.platform.standard', 'tooltip.starfall_blueprint.best_mech_radiator', 'item.starfall_blueprint.best_mech', 'ui.starfall_blueprint.best_action_27', 'ui.starfall_blueprint.best_action_26', 'ui.starfall_blueprint.best_material_action_27', 'tooltip.starfall_blueprint.best_mech_generator_core', 'message.starfall_blueprint.best_mech_heavy_drill_arm_assembled', 'tooltip.starfall_blueprint.best_mech_cruise_wheels', 'mech.starfall_blueprint.platform.heavy', 'message.starfall_blueprint.best_heavy_mech_assembled', 'item.starfall_blueprint.best_heavy_mech', 'ui.starfall_blueprint.best_hint_3', 'message.starfall_blueprint.best_action_27', 'item.starfall_blueprint.best_mech_heavy_drill_arm', 'entity.starfall_blueprint.best_heavy_engineering_mech', 'message.starfall_blueprint.best_heavy_mech_cost', 'mech.starfall_blueprint.arm.heavy_only', 'message.starfall_blueprint.best_mech_assembled', 'tooltip.starfall_blueprint.best_mech_drive'}

def preserved_translation(key, old, names, lang):
    if names.get(key) == old:
        return True
    if key not in WATER_GOD_TRANSLATION_MIGRATIONS:
        return False
    index = 0 if lang == 'zh_cn' else 1
    from generate_best_resources import MECH_MESSAGES
    expected = {}
    for entry, pair in MECH_MESSAGES.items():
        prefix, suffix = entry.split('.', 1)
        expected[f'{prefix}.starfall_blueprint.{suffix}'] = pair[index]
    for item_key, pair in BEST_NAMES.items():
        prefix = 'block' if item_key == 'best_workbench' else 'item'
        expected[f'{prefix}.starfall_blueprint.{item_key}'] = pair[index]
    expected['entity.starfall_blueprint.best_engineering_mech'] = BEST_NAMES['best_mech'][index]
    expected['entity.starfall_blueprint.best_heavy_engineering_mech'] = BEST_NAMES['best_heavy_mech'][index]
    return key in expected and names.get(key) == expected[key]


def snapshot():
    paths = [ATLAS / 'production-data.json', ATLAS / 'index.html', ATLAS.parents[1] / '星落蓝图说明手册.html']
    paths += list((ROOT / 'src/main/resources').rglob('*'))
    paths += list((ATLAS / 'item-art').glob('*.png'))
    return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file()}


if '--regenerate' in sys.argv:
    before = snapshot()
    runpy.run_path(str(ATLAS / 'build_atlas.py'), run_name='__main__')
    sys.path.insert(0, str(ROOT / 'tools'))
    runpy.run_path(str(ROOT / 'tools/sync_catalogue.py'), run_name='__main__')
    runpy.run_path(str(ROOT / 'tools/generate_catalogue_resources.py'), run_name='__main__')
    after = snapshot()
    differences = [key for key in before.keys() | after.keys() if before.get(key) != after.get(key)]
    assert not differences, differences
    print(f'PASS: full generation is byte-stable across {len(after)} atlas/resource files.')

canonical = json.loads((ATLAS / 'production-data.json').read_text(encoding='utf-8'))
synced = json.loads((ART / 'catalogue.json').read_text(encoding='utf-8'))
for section in ('items', 'recipes', 'catalogue'):
    assert canonical[section] == synced[section], section
recipes = canonical['recipes']; items = canonical['items']
bench = next(r for r in recipes if r['id'] == 'BEST-M01')
assert bench['station'] == 'workbench'
assert sum(x['qty'] for x in bench['inputs']) == 9
assert len([x for x in bench['craft']['cells'] if x]) == 9
assert json.loads((DATA / 'recipe/catalogue_best-m01.json').read_text())['recipe'] == 'BEST-M01'
for key, inputs in ASSEMBLY.items():
    assert not any(any(o['item'] == key for o in r['outputs']) for r in recipes), key
    metadata = items[key]['manual_assembly']
    assert metadata['station'] == 'best_workbench' and not metadata['automatic'] and metadata['personal_learning']
    assert metadata['inputs'] == [dict(item=k, qty=q) for k, q in inputs]
    assert metadata['requires'] == ASSEMBLY_KNOWLEDGE[key]
    if key in POWERED_EQUIPMENT:
        assert items[key]['energy_storage']['capacity'] == 200000
        assert items[key]['manual_repair']['durability'] == 200
        cooling = items[key]['manual_cooling']
        assert cooling['requires'] == ASSEMBLY_KNOWLEDGE[key]
        assert cooling['main_hand'] and cooling['personal_learning']
        assert (cooling['material'], cooling['count'], cooling['heat_reduction'], cooling['unlock_at_or_below']) == ('water_bucket', 1, 400, 400)
        assert cooling['returns'] == dict(item='bucket', qty=1)
        assert cooling['reject_zero_heat'] and not cooling['failure_consumes']
        assert not cooling['changes_energy'] and not cooling['changes_durability']
    for path in (DATA / 'recipe').glob('*.json'):
        text = path.read_text(encoding='utf-8')
        assert f'starfall_blueprint:{key}' not in text, path
assert items['best_workbench']['personal_learning'] == LEARNING
assert LEARNING['field_servicing']['requires'] == ['engineering_cutting']
assert LEARNING['field_servicing']['inputs'] == [dict(item='load', qty=2), dict(item='conductor', qty=1), dict(item='board', qty=1)]
kit = items['best_service_kit']
assert (kit['max_stack_size'], kit['max_durability'], kit['durability_system'], kit['broken_behavior']) == (1, 32, 'vanilla', 'disappear')
assert not any(key in kit for key in ('energy_storage', 'thermal', 'manual_repair', 'operation'))
repair = kit['field_repair']
assert (repair['activation'], repair['target_hand'], repair['material'], repair['count'], repair['durability_restored'], repair['kit_damage']) == ('main_hand_use', 'offhand', 'steel', 1, 200, 1)
assert repair['targets'] == list(POWERED_EQUIPMENT) and repair['requires'] == ['field_servicing']
assert repair['require_damaged_target'] and not repair['failure_consumes']
assert not any(repair[k] for k in ('changes_energy', 'changes_heat', 'clears_overheat'))
for key, pair in BEST_NAMES.items():
    assert (ATLAS / 'item-art' / f'{key}.png').read_bytes() == (ART / 'textures/item' / f'{key}.png').read_bytes()
    assert items[key]['artwork'].startswith('data:image/png;base64,')
    assert (ART / 'models/item' / f'{key}.json').is_file()
    for lang, index in [('zh_cn',0), ('en_us',1)]:
        names = json.loads((ART / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
        prefix = 'block' if key == 'best_workbench' else 'item'
        assert names[f'{prefix}.starfall_blueprint.{key}'] == pair[index]
assert items['best_frame']['equipment_slot'] == 'legs' and items['best_frame']['armor_bonus'] == 0
from PIL import Image
with Image.open(ART / 'textures/item/best_service_kit.png') as image:
    assert image.size == (32, 32) and image.mode == 'RGBA'
    assert 0 < sum(image.getchannel('A').histogram()[1:]) < 32 * 32
for lang in ('zh_cn', 'en_us'):
    names = json.loads((ART / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
    for prefix, suffixes in [('tooltip', ('usage', 'cost')), ('message', ('knowledge', 'target', 'exhausted', 'steel', 'repaired'))]:
        for suffix in suffixes:
            assert names[f'{prefix}.starfall_blueprint.best_service_kit.{suffix}']
# Light Mobility is a separate persistent personal boolean, never research level four.
learning = LEARNING['light_mobility']
assert learning['requires'] == ['field_servicing']
assert learning['independent'] and learning['minimum_research_level'] == 3
assert (learning['storage_key'], learning['storage_type'], learning['persistence']) == ('BestLightMobility', 'boolean', 'player_saved_data_and_death_clone')
assert learning['inputs'] == [dict(item='load', qty=2), dict(item='conductor', qty=1), dict(item='board', qty=1)]
module = items['best_stride_module']
assert ASSEMBLY['best_stride_module'] == [('steel', 4), ('load', 2), ('steel_fix', 2), ('board', 1)]
assert ASSEMBLY_KNOWLEDGE['best_stride_module'] == ['field_servicing', 'light_mobility']
assert (module['item_type'], module['max_stack_size'], module['max_durability']) == ('ordinary', 1, 0)
assert module['attribute_modifiers'] == []
assert not any(key in module for key in ('energy_storage', 'thermal', 'manual_repair', 'operation', 'durability_system', 'equipment_slot'))
stride = items['best_frame']['stride_module']
assert (stride['item'], stride['max_installed'], stride['station']) == ('best_stride_module', 1, 'best_workbench')
assert stride['main_hand'] and stride['check_current_wearer_knowledge_every_tick']
assert stride['requires'] == ['field_servicing', 'light_mobility'] and stride['minimum_research_level'] == 3
assert (stride['installed_storage_key'], stride['enabled_storage_key'], stride['storage_type']) == ('BestStrideInstalled', 'BestStrideEnabled', 'boolean')
assert stride['installed_after_install'] and not stride['enabled_after_install']
assert stride['install'] == dict(consumes=1, reject_already_installed=True, failure_consumes=False)
assert stride['modes'] == ['sprint', 'stride'] and stride['default_mode'] == 'sprint' and stride['mode_switch_station_only']
assert stride['uninstall'] == dict(returns=dict(item='best_stride_module', qty=1), require_inventory_space=True, failure_changes_nothing=True)
assert stride['preserves'] == ['energy', 'heat', 'overheat', 'wear', 'custom_name', 'work_ticks', 'all_other_item_data']
assert not stride['resets_work_ticks'] and stride['persistence'] == 'item_custom_data'
operation = stride['operation']
assert (operation['attribute'], operation['attribute_addition'], operation['vanilla_height'], operation['resulting_height']) == ('minecraft:generic.step_height', 0.4, 0.6, 1.0)
assert (operation['energy_per_tick'], operation['heat_per_tick'], operation['natural_cooling_per_tick'], operation['net_heat_per_tick'], operation['durability_per_work_ticks']) == (24, 4, 2, 2, 100)
assert operation['charges_while_stationary']
assert operation['requires'] == ['grounded', 'ready', 'enabled', 'installed', 'wearer_light_mobility', 'wearer_level_3']
assert operation['excludes'] == ['sneaking', 'sprinting', 'water', 'flying', 'gliding', 'riding']
assert operation['mutually_exclusive_with'] == 'ground_sprint_speed_bonus'
assert operation['cancel_on'] == ['exhausted', 'overheated', 'worn_out', 'unequipped', 'missing_personal_knowledge', 'mode_disabled', 'ineligible_movement']
assert not any(operation[key] for key in ('changes_jump', 'changes_flight', 'changes_fall_damage', 'changes_armor'))
assert items['best_frame']['operation'] == dict(ground_sprint_speed_bonus=0.15, energy_per_tick=16, heat_per_tick=6, durability_per_work_ticks=100)
with Image.open(ART / 'textures/item/best_stride_module.png') as image:
    assert image.size == (32, 32) and image.mode == 'RGBA'
    assert 0 < sum(image.getchannel('A').histogram()[1:]) < 32 * 32
for lang in ('zh_cn', 'en_us'):
    names = json.loads((ART / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
    for owner, suffixes in [('best_stride_module', ('usage', 'cost')), ('best_frame', ('stride', 'stride_idle'))]:
        for suffix in suffixes:
            assert names[f'tooltip.starfall_blueprint.{owner}.{suffix}']
for doc in (ROOT / 'docs/BEST_ENGINEERING.md', ATLAS.parent / '18_贝斯特轻型工程首批.md'):
    text = doc.read_text(encoding='utf-8')
    assert '216/216' in text and '3/3项真实NeoForge服务端专项通过' in text
    assert '地面站立待命也照常耗能' in text and '不增加第四研究等级' in text
    assert '静态生成检查不代替运行时验收' in text
    assert '客户端双页菜单、原生穿戴和多人实机仍待验收' in text
# Diagnostics is a parallel personal boolean, with a reusable ordinary read-only tool.
learning = LEARNING['diagnostics']
assert learning['requires'] == ['field_servicing'] and learning['minimum_research_level'] == 3
assert learning['independent'] and learning['storage_key'] == 'BestDiagnostics'
assert (learning['storage_type'], learning['persistence']) == ('boolean', 'player_saved_data_and_death_clone')
assert learning['inputs'] == [dict(item='load', qty=1), dict(item='conductor', qty=2), dict(item='board', qty=1)]
tool = items['best_diagnostic_tool']
assert ASSEMBLY['best_diagnostic_tool'] == [('steel', 2), ('conductor', 1), ('sensor', 1), ('board', 1), ('sheath', 1)]
assert ASSEMBLY_KNOWLEDGE['best_diagnostic_tool'] == ['field_servicing', 'diagnostics']
assert (tool['item_type'], tool['max_stack_size'], tool['max_durability']) == ('ordinary', 1, 0)
assert tool['attribute_modifiers'] == []
assert not any(key in tool for key in ('energy_storage', 'thermal', 'manual_repair', 'operation', 'durability_system', 'equipment_slot'))
readings = tool['diagnostics']
assert (readings['activation'], readings['target_hand']) == ('main_hand_use', 'offhand')
assert readings['targets'] == list(POWERED_EQUIPMENT)
assert readings['requires'] == ['field_servicing', 'diagnostics'] and readings['minimum_research_level'] == 3
assert readings['read_only'] and readings['reusable']
assert readings['readings'] == ['energy', 'capacity', 'heat', 'overheat', 'wear', 'battery_isolated']
assert readings['frame_readings'] == ['stride_installed', 'stride_mode', 'wearer_light_mobility_available']
assert not any(readings[key] for key in ('changes_energy', 'changes_heat', 'changes_wear', 'changes_overheat', 'changes_item_data'))
for key in POWERED_EQUIPMENT:
    isolation = items[key]['battery_isolation']
    assert isolation['station'] == 'best_workbench' and isolation['main_hand']
    assert isolation['requires'] == ['field_servicing', 'diagnostics'] and isolation['minimum_research_level'] == 3
    assert (isolation['storage_key'], isolation['storage_type'], isolation['default']) == ('BestBatteryIsolated', 'boolean', False)
    assert (isolation['isolate_action'], isolation['connect_action']) == (15, 16)
    assert isolation['stops_powered_operation'] and isolation['rejects_all_charging'] and isolation['rejects_energy_capability_receive']
    assert all(isolation[entry] for entry in ('allows_manual_repair', 'allows_field_repair', 'allows_manual_cooling'))
    assert not isolation['reconnect_adds_energy'] and isolation['persistence'] == 'item_custom_data'
    assert isolation['preserves'] == ['energy', 'heat', 'overheat', 'wear', 'custom_name', 'work_ticks', 'all_other_item_data']
assert items['best_workbench']['diagnostics_actions'] == dict(learn=13, assemble=14, isolate=15, connect=16)
with Image.open(ART / 'textures/item/best_diagnostic_tool.png') as image:
    assert image.size == (32, 32) and image.mode == 'RGBA'
    assert 0 < sum(image.getchannel('A').histogram()[1:]) < 32 * 32
for lang in ('zh_cn', 'en_us'):
    names = json.loads((ART / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
    for suffix in ('best_diagnostics_learned', 'best_diagnostics_required', 'best_battery_isolated', 'best_battery_connected',
                   'best_already_isolated', 'best_already_connected', 'best_diagnostic_target', 'best_state_yes', 'best_state_no',
                   'best_diagnostic_hint', 'best_diagnostic_assembled', 'best_diagnostics_cost', 'best_diagnostic_tool_cost',
                   'best_isolate_cost', 'best_connect_cost', 'best_action_13', 'best_action_14', 'best_action_15', 'best_action_16',
                   'best_page_0', 'best_page_1', 'best_page_2', 'best_next_page', 'best_diagnostics_hint'):
        assert names[f'message.starfall_blueprint.{suffix}']
    for suffix, argument_count in (('best_diagnostic_report', 11), ('best_diagnostic_frame', 3), ('best_diagnostics_progress', 3)):
        value = names[f'message.starfall_blueprint.{suffix}']
        assert all(f'%{i}$s' in value for i in range(1, argument_count + 1))
for doc in (ROOT / 'docs/BEST_ENGINEERING.md', ATLAS.parent / '18_贝斯特轻型工程首批.md'):
    text = doc.read_text(encoding='utf-8')
    assert '5/5通过' in text and 'BestDiagnostics' in text and 'BestBatteryIsolated' in text
    assert '本节不宣称本轮服务端或客户端通过' in text
assert (len(items), len(recipes), len(canonical['catalogue'])) == (317, 245, 183)
armor = ART / 'textures/models/armor/best_frame_layer_2.png'
assert armor.read_bytes() == (ATLAS / 'item-art/best_frame_layer_2.png').read_bytes()
with Image.open(armor) as image:
    assert image.size == (64,32) and image.mode == 'RGBA'
    alpha_histogram = image.getchannel('A').histogram()
    assert 0 < sum(alpha_histogram[1:]) < 512, 'Keep the external frame open and transparent'
    assert image.crop((0,20,16,32)).getbbox() and image.crop((16,20,40,27)).getbbox()
assert items['best_frame']['equipment_texture'].endswith('models/armor/best_frame_layer_2.png')
assert len(json.loads((ART / 'blockstates/best_workbench.json').read_text())['variants']) == 4
# Rideable mech has its own storage/interaction schema; never extend the two wearable power targets.
assert POWERED_EQUIPMENT == ('best_frame', 'best_cutter')
learning = LEARNING['mech_operation']
assert learning['independent'] and learning['minimum_research_level'] == 3
assert learning['requires'] == ['field_servicing', 'light_mobility', 'diagnostics']
assert (learning['storage_key'], learning['storage_type'], learning['persistence']) == ('BestMechOperation', 'boolean', 'player_saved_data_and_death_clone')
assert learning['inputs'] == [dict(item='load',qty=4), dict(item='conductor',qty=4), dict(item='sensor',qty=2), dict(item='board',qty=2)]
mech = items['best_mech']
assert ASSEMBLY['best_mech'] == [('steel',32), ('load',16), ('conductor',8), ('board',4), ('steel_fix',16), ('head',2), ('sensor',2), ('sheath',8)]
assert not any(entry['item']=='s01' for entry in mech['manual_assembly']['inputs'])
assert ASSEMBLY_KNOWLEDGE['best_mech'] == ['field_servicing', 'light_mobility', 'diagnostics', 'mech_operation']
assert mech['max_stack_size'] == 1 and mech['item_type'] == 'packed_entity'
assert mech['entity_id'] == 'starfall_blueprint:best_engineering_mech'
assert mech['entity_dimensions'] == dict(width=1.8, height=3.2, rider_seat_height=2.2)
assert mech['energy_storage']['capacity'] == 20000000 and mech['energy_storage']['initially_empty']
assert mech['energy_storage']['packed_base_transfer_per_tick'] == 5000
assert mech['thermal'] == dict(overheat=1000, resume=400, cooling_per_tick=2)
assert mech['max_wear'] == 2400
assert all(mech['packed_state'][key] for key in ('initially_empty','occupied_pack_rejected','isolation_required','full_inventory_rejected'))
assert set(mech['packed_state']['preserves']) >= {'health','energy','heat','wear','isolated','work_ticks'}
operation = mech['operation']
assert (operation['energy_per_commanded_movement_tick'],operation['heat_per_commanded_movement_tick'],operation['wear_per_work_ticks']) == (400,4,100)
assert not any(operation[key] for key in ('flight','jump','unmanned_consumption','idle_consumption'))
drill = mech['drill']
assert (drill['networking_action'],drill['energy'],drill['heat'],drill['wear'],drill['cooldown_ticks']) == (0,32000,120,1,20)
assert drill['harvest_tier']=='iron' and drill['ordinary_drops']
assert drill['deny']==['block_entities','unbreakable','protected_blocks']
assert mech['field_service']['repair'] == dict(material='steel',count=1,wear_restored=200,health_restored=5)
assert mech['field_service']['cooling'] == dict(material='water_bucket',count=1,heat_reduction=400,returns='bucket')
assert items['best_workbench']['mech_actions'] == dict(learn=17,assemble=18,radiator=19,drive=20,drill_arm=21,excavator_arm=22,reinforced_body=23,generator_core=24,cruise_wheels=25,heavy_mech=26,heavy_drill_arm=27,heavy_excavator_arm=28)
for path, size in [(ART / 'textures/item/best_mech.png',(32,32)),(ART / 'textures/entity/best_engineering_mech.png',(128,128))]:
    with Image.open(path) as image:
        assert image.size == size and image.mode == 'RGBA'
        assert len(image.getcolors(size[0]*size[1])) > 5
for lang in ('zh_cn','en_us'):
    names=json.loads((ART / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
    assert names['entity.starfall_blueprint.best_engineering_mech']
    for suffix, translations in BEST_UI_MESSAGES.items():
        assert names[f'ui.starfall_blueprint.best_{suffix}'] == translations[0 if lang == 'zh_cn' else 1]
    for suffix in ('title','health','energy','heat','wear','ready','destroyed','isolated','thermal_locked','worn_out','unqualified','no_power','drill_power','no_arm','work_power','arm','hot','low_power','worn','request','standby','controls'):
        assert names[f'hud.starfall_blueprint.mech.panel.{suffix}']
    assert names['key.starfall_blueprint.mech_drill'] and names['key.categories.starfall_blueprint']
    for key in ('title','energy','heat','wear','isolation','drill','wait','controls'):
        assert names[f'hud.starfall_blueprint.mech.{key}']
    for key in ('best_page_3','best_action_17','best_action_18','best_mech_learning_cost','best_mech_assembly_cost','best_mech_learned','best_mech_assembled','best_mech_required','best_mech_hint'):
        assert names[f'message.starfall_blueprint.{key}']
    for key,count in (('best_mech_progress',4),('best_mech_report',9),('best_mech_power',2),('best_mech_charging',2)):
        assert all(f'%{i}$s' in names[f'message.starfall_blueprint.{key}'] for i in range(1,count+1))
    for key in ('resources','structure','charge','deploy','isolation'):
        assert names[f'tooltip.starfall_blueprint.best_mech.{key}']
for doc in (ROOT / 'docs/BEST_ENGINEERING.md', ATLAS.parent / '18_贝斯特轻型工程首批.md'):
    text=doc.read_text(encoding='utf-8')
    assert 'BestMechOperation' in text and '32,000 XH' in text and '20,000,000 XH' in text
    assert '客户端预览不扣料、不学习、不改变装备状态' in text
    assert '冷却到600仍保持热锁提示，须降至400恢复' in text
    assert 'G键输入、骑乘手感与多人实机仍须另行验收' in text
# Engineering arms share existing knowledge and work checks; no new recipes or power storage.
assert len(LEARNING) == 7
assert ASSEMBLY['best_mech_drill_arm'] == [('steel',8), ('conductor',4), ('head',2), ('board',1), ('steel_fix',4)]
assert ASSEMBLY['best_mech_excavator_arm'] == [('steel',8), ('load',4), ('conductor',2), ('steel_fix',4), ('sheath',2)]
slot = mech['engineering_arm_slot']
assert (slot['storage_key'], slot['max_installed'], slot['independent_of']) == ('EngineeringArm', 1, 'UtilityModule')
assert (slot['factory_default'], slot['missing_legacy_value'], slot['explicit_empty'], slot['unknown_value']) == ('drill','drill','none','none')
assert slot['conditions'] == ['isolated', 'unoccupied', 'alive', 'stationary', 'grounded', 'nonfluid']
assert slot['requires'] == ASSEMBLY_KNOWLEDGE['best_mech']
assert not slot['install']['hot_swap'] and slot['install']['require_empty_slot']
assert slot['remove']['inventory_slots'] == 36 and slot['remove']['require_empty_main_inventory_slot']
assert not any(slot['remove'][k] for k in ('consumes_pickaxe','damages_pickaxe','failure_falls_through_to_isolation'))
assert 'EngineeringArm' in mech['packed_state']['preserves']
work = mech['engineering_work']
assert work['networking_action'] == 0 and work['server_chooses_installed_arm'] and work['success_only_costs']
assert work['no_arm_allows_movement'] and not work['no_arm_allows_work'] and not work['automatic_area_operation']
assert work['deny'] == ['block_entities', 'fluids', 'unbreakable', 'protected_blocks']
for key, energy, heat, tool, tag in [('best_mech_drill_arm',32000,120,'minecraft:iron_pickaxe','minecraft:mineable/pickaxe'), ('best_mech_excavator_arm',12000,45,'minecraft:iron_shovel','minecraft:mineable/shovel')]:
    arm = items[key]
    assert (arm['real_item'],arm['max_stack_size'],arm['max_durability'],arm['item_type']) == (True,1,0,'ordinary')
    assert not any(k in arm for k in ('energy_storage','thermal','durability_system','equipment_slot'))
    assert ASSEMBLY_KNOWLEDGE[key] == ASSEMBLY_KNOWLEDGE['best_mech']
    spec = arm['engineering_arm']
    assert (spec['energy'],spec['heat'],spec['wear'],spec['cooldown_ticks'],spec['tool'],spec['mineable_tag']) == (energy,heat,1,20,tool,tag)
    assert arm['manual_assembly']['output_count'] == 1
    with Image.open(ART / 'textures/item' / f'{key}.png') as image:
        assert image.size == (32,32) and image.mode == 'RGBA'
        assert 0 < sum(image.getchannel('A').histogram()[1:]) < 1024
assert (ART / 'textures/item/best_mech_drill_arm.png').read_bytes() != (ART / 'textures/item/best_mech_excavator_arm.png').read_bytes()
for lang in ('zh_cn','en_us'):
    names = json.loads((ART / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
    for suffix in ('best_action_21','best_action_22','best_mech_drill_arm_cost','best_mech_excavator_arm_cost','best_mech_drill_arm_assembled','best_mech_excavator_arm_assembled'):
        assert names[f'message.starfall_blueprint.{suffix}']
    for suffix in ('none','installed','controls','failed'):
        assert names[f'mech.starfall_blueprint.arm.{suffix}']
    assert names['mech.starfall_blueprint.arm.installed'].count('%s') == 1
    assert names['hud.starfall_blueprint.mech.panel.arm'].count('%s') == 1
    assert '%1$s' in names['hud.starfall_blueprint.mech.panel.standby'] and '%2$s' in names['hud.starfall_blueprint.mech.panel.standby']
    for key in ('best_mech_drill_arm','best_mech_excavator_arm','best_mech_arm_install'):
        assert names[f'tooltip.starfall_blueprint.{key}']
for doc in (ROOT / 'docs/BEST_ENGINEERING.md', ATLAS.parent / '18_贝斯特轻型工程首批.md'):
    text = doc.read_text(encoding='utf-8')
    assert 'EngineeringArm' in text and 'MEC-06部分实现' in text and '12,000 XH' in text
# Body/cargo is one incremental MEC-04 step; manual-only and independent of both old slots.
assert len(BEST_NAMES) == 17 and len(LEARNING) == 7
body = items['best_mech_reinforced_body']
assert ASSEMBLY['best_mech_reinforced_body'] == [('steel',24), ('load',12), ('conductor',8), ('steel_fix',16), ('sheath',8)]
assert ASSEMBLY_KNOWLEDGE['best_mech_reinforced_body'] == ASSEMBLY_KNOWLEDGE['best_mech']
assert (body['real_item'], body['item_type'], body['max_stack_size'], body['max_durability']) == (True, 'ordinary', 1, 0)
assert body['manual_assembly']['output_count'] == 1 and body['attribute_modifiers'] == []
assert not any(k in body for k in ('energy_storage', 'thermal', 'durability_system', 'equipment_slot'))
slot = mech['body_slot']
assert (slot['storage_key'], slot['max_installed'], slot['independent_of']) == ('Body', 1, ['UtilityModule','EngineeringArm'])
assert all(slot[k] == '' for k in ('default','missing_legacy_value','unknown_value','standard_value'))
assert slot['reinforced_value'] == 'best_mech_reinforced_body'
assert slot['minimum_research_level'] == 3 and slot['requires'] == ASSEMBLY_KNOWLEDGE['best_mech']
assert slot['conditions'] == ['isolated','unoccupied','alive','stationary','grounded','nonfluid']
assert slot['install'] == dict(activation='crouch_main_hand_body_use_on_actual_mech', consumes=1,
    require_standard_body=True, failure_changes_nothing=True, heals=False)
removal = slot['remove']
assert removal['activation'] == 'crouch_main_hand_diagnostic_offhand_minecraft_iron_block_use_on_actual_mech'
assert not removal['consumes_iron_block'] and removal['returns'] == dict(item='best_mech_reinforced_body',qty=1)
assert removal['inventory_slots'] == 36 and removal['require_empty_main_inventory_slot']
assert (removal['reject_energy_above'], removal['reject_health_above'], removal['require_empty_cargo_slots']) == (20000000,100,list(range(9,18)))
assert removal['failure_changes_nothing'] and not removal['discards_excess'] and not removal['failure_falls_through_to_isolation']
assert set(slot['preserves']) >= {'exact_health','energy','UtilityModule','EngineeringArm','Cargo','all_other_state'}
assert mech['body_profiles'] == dict(
    standard=dict(capacity=20000000,max_health=100,cargo_slots=9,movement_speed=0.22,drive_speed=0.28,movement_energy=400,drive_movement_energy=600,movement_heat=4,drive_movement_heat=6),
    reinforced=dict(capacity=30000000,max_health=160,cargo_slots=18,movement_speed=0.18,drive_speed=0.23,movement_energy=600,drive_movement_energy=900,movement_heat=6,drive_movement_heat=8))
assert mech['thermal'] == dict(overheat=1000,resume=400,cooling_per_tick=2) and mech['max_wear'] == 2400
assert set(mech['packed_state']['preserves']) >= {'Body','Cargo','UtilityModule','EngineeringArm'}
cargo = mech['cargo']
assert cargo['storage_key'] == 'Cargo' and cargo['initially_empty'] and cargo['actual_inventory']
assert (cargo['standard_slots'],cargo['reinforced_slots']) == (9,18)
assert cargo['open']['activation'] == 'crouch_main_hand_minecraft_chest_use_on_actual_mech'
assert cargo['open']['maximum_distance'] == 8 and cargo['open']['recheck_while_open']
assert cargo['open']['conditions'] == slot['conditions'] and cargo['open']['requires'] == slot['requires']
assert not any(cargo[k] for k in ('new_learning','automatic_pickup','packed_mech_nesting','downgrade_discards_excess'))
assert not cargo['open']['consumes_chest'] and cargo['persistence'] == ['entity_nbt','packed_item','deploy']
assert cargo['death'] == dict(drop_contents_separately=True,clear_dead_packed_item=True,respects_entity_drops_rule=True)
for key, speed, energy, heat, cooling in [('best_mech_radiator',0.18,600,6,4),('best_mech_drive',0.23,900,8,2)]:
    profile=items[key]['utility_module']['body_profiles']['reinforced']
    assert (profile['movement_speed'],profile['energy_per_actual_ground_movement_tick'],profile['heat_per_actual_ground_movement_tick'],profile['passive_cooling_per_tick']) == (speed,energy,heat,cooling)
with Image.open(ART/'textures/item/best_mech_reinforced_body.png') as image:
    assert image.size == (32,32) and image.mode == 'RGBA'
    assert 0 < sum(image.getchannel('A').histogram()[1:]) < 1024
    assert len(image.getcolors(1024)) > 5
body_bytes=(ART/'textures/item/best_mech_reinforced_body.png').read_bytes()
assert all(body_bytes != (ART/'textures/item'/f'{key}.png').read_bytes() for key in BEST_NAMES if key != 'best_mech_reinforced_body')
for lang in ('zh_cn','en_us'):
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    for prefix,suffixes in [('message',('best_action_23','best_mech_reinforced_body_cost','best_mech_reinforced_body_assembled')),
            ('ui',('best_action_23','best_material_action_23','mech_cargo_hint')),
            ('tooltip',('best_mech_reinforced_body','best_mech_body_install')),
            ('mech',('body.none','body.installed','body.controls','body.failed'))]:
        for suffix in suffixes: assert names[f'{prefix}.starfall_blueprint.{suffix}']
    assert names['mech.starfall_blueprint.body.installed'].count('%s') == 1
    assert names['hud.starfall_blueprint.mech.panel.body'].count('%s') == 1
    assert names['menu.starfall_blueprint.best_mech_cargo'] == ('机甲货舱' if lang == 'zh_cn' else 'Mech cargo')
    if lang == 'zh_cn': assert names['ui.starfall_blueprint.mech_cargo_hint'] == '隔离整备 · 禁止存入收纳机甲'
for doc in (ROOT/'docs/BEST_ENGINEERING.md',ATLAS.parent/'18_贝斯特轻型工程首批.md',ATLAS.parent/'02_科技树设定/02_贝斯特机械科技树.md'):
    text=doc.read_text(encoding='utf-8')
    assert all(s in text for s in ('2026-10-30','MEC-04容量步骤','精确当前生命','9..17','30,000,000 XH','不进行UI测试'))
# Core is manual-only, independent, and retains paid fuel through every persistence boundary.
core = items['best_mech_generator_core']
assert ASSEMBLY['best_mech_generator_core'] == [('steel',16),('load',8),('conductor',8),('board',4),('sheath',8),('steel_fix',8)]
assert ASSEMBLY_KNOWLEDGE['best_mech_generator_core'] == ASSEMBLY_KNOWLEDGE['best_mech']
assert (core['real_item'],core['item_type'],core['max_stack_size'],core['max_durability']) == (True,'ordinary',1,0)
assert core['attribute_modifiers'] == [] and core['manual_assembly']['output_count'] == 1
assert not any(k in core for k in ('energy_storage','thermal','durability_system','equipment_slot'))
slot = mech['core_slot']
assert (slot['storage_key'],slot['max_installed'],slot['independent_of']) == ('Core',1,['UtilityModule','Body','EngineeringArm'])
assert all(slot[k] == '' for k in ('default','missing_legacy_value','unknown_value'))
assert slot['conditions'] == ['isolated','unoccupied','alive','stationary','grounded','nonfluid']
assert slot['requires'] == ASSEMBLY_KNOWLEDGE['best_mech'] and slot['minimum_research_level'] == 3
assert slot['install'] == dict(activation='crouch_main_hand_core_use_on_actual_mech',consumes=1,require_empty_slot=True,hot_swap=False,failure_changes_nothing=True)
removal = slot['remove']
assert removal['activation'] == 'crouch_main_hand_diagnostic_offhand_minecraft_furnace_use_on_actual_mech'
assert not removal['consumes_furnace'] and removal['require_fuel_ticks_zero']
assert removal['returns'] == dict(item='best_mech_generator_core',qty=1)
assert removal['require_empty_main_inventory_slot'] and removal['inventory_slots'] == 36
assert removal['failure_changes_nothing'] and not removal['failure_falls_through_to_isolation']
generation = mech['solid_fuel_generation']
assert (generation['fuel_storage_key'],generation['work_storage_key']) == ('CoreFuelTicks','CoreWorkTicks')
assert generation['fuel_source'] == 'active_cargo_only'
assert (generation['standard_cargo_slots'],generation['reinforced_cargo_slots']) == (9,18)
assert generation['fuel_rule'] == 'vanilla_furnace_burn_time_positive_and_no_crafting_remainder'
assert generation['rejects'] == ['lava_bucket','fuel_containers_with_crafting_remainder']
assert generation['consume_one_only_when_runnable_and_fuel_zero']
assert (generation['energy_per_operating_tick'],generation['heat_per_operating_tick'],generation['fuel_ticks_per_operating_tick'],generation['wear_per_operating_ticks']) == (1000,2,1,1000)
assert generation['internal_generation'] and generation['bypass_external_receiver_budget'] and not generation['requires_rider']
assert generation['requires'] == ['alive','connected','not_thermal_locked','not_worn_out','capacity_room_at_least_1000']
assert generation['pause_on'] == ['isolated','capacity_room_below_1000','thermal_lock','worn_out','dead']
assert not any(generation[k] for k in ('pause_consumes_fuel','pause_decrements_fuel_ticks','pause_increments_work_ticks','random_explosion','automatic_pickup'))
assert generation['phase_order'] == ['existing_cooling','core_generation','movement']
assert generation['persistence'] == ['entity_nbt','packed_item','deploy','dead_packed_item']
assert set(mech['packed_state']['preserves']) >= {'Core','CoreFuelTicks','CoreWorkTicks'}
assert (mech['energy_storage']['entity_receive_per_tick'],mech['energy_storage']['packed_base_transfer_per_tick']) == (512,5000)
assert items['best_workbench']['tabs'] == ['workshop','mobility','service','mech','arms','core','locomotion']
assert len(LEARNING) == 7
with Image.open(ART/'textures/item/best_mech_generator_core.png') as image:
    assert image.size == (32,32) and image.mode == 'RGBA'
    assert 0 < sum(image.getchannel('A').histogram()[1:]) < 1024
    assert len(image.getcolors(1024)) > 5
core_bytes = (ART/'textures/item/best_mech_generator_core.png').read_bytes()
assert all(core_bytes != (ART/'textures/item'/f'{key}.png').read_bytes() for key in BEST_NAMES if key != 'best_mech_generator_core')
for lang in ('zh_cn','en_us'):
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    for prefix,suffixes in [('message',('best_page_5','best_action_24','best_mech_generator_core_cost','best_mech_generator_core_assembled')),
            ('ui',('best_action_24','best_material_action_24','best_tab_5','best_hint_5')),
            ('tooltip',('best_mech_generator_core','best_mech_core_install')),
            ('mech',('core.none','core.installed','core.controls','core.failed','core.fuel'))]:
        for suffix in suffixes: assert names[f'{prefix}.starfall_blueprint.{suffix}']
    for key in ('mech.starfall_blueprint.core.installed','mech.starfall_blueprint.core.fuel','hud.starfall_blueprint.mech.panel.core'):
        assert names[key].count('%s') == 1
for doc in (ROOT/'docs/BEST_ENGINEERING.md',ATLAS.parent/'18_贝斯特轻型工程首批.md',ATLAS.parent/'02_科技树设定/02_贝斯特机械科技树.md'):
    text=doc.read_text(encoding='utf-8')
    assert text.index('## 强化机体与真实货舱') < text.index('## 固燃发电核心')
    assert all(s in text for s in ('2026-10-30','MEC-03/05','CoreFuelTicks','CoreWorkTicks','minecraft:furnace','冷却→核心发电→移动','1000 XH','不进行UI测试'))
boundary = ROOT.parent/'core_resources_preservation.json'
assert boundary.is_file(), 'Capture the protected-resource boundary before core generation'
saved=json.loads(boundary.read_text(encoding='utf-8'))
for filename,digest in saved['files'].items():
    assert hashlib.sha256(Path(filename).read_bytes()).hexdigest() == digest,filename
for lang,entries in saved['lang'].items():
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    assert all(preserved_translation(k, v, names, lang) for k,v in entries.items()),lang
    assert set(saved['existing_keys'][lang]) <= names.keys(),lang
print(f'PASS: solid-fuel core boundaries; preserved {len(saved["files"])} both-repo remote/Best art hashes and {sum(map(len,saved["lang"].values()))} remote bilingual values; all prior keys retained.')

# Fresh proof for both repositories, existing arm art and all preexisting translation key presence.
boundary = ROOT.parent/'body_resources_preservation.json'
assert boundary.is_file(), 'Capture the protected-resource boundary before generation'
saved=json.loads(boundary.read_text(encoding='utf-8'))
for filename,digest in saved['files'].items():
    assert hashlib.sha256(Path(filename).read_bytes()).hexdigest() == digest,filename
for lang,entries in saved['lang'].items():
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    assert all(preserved_translation(k, v, names, lang) for k,v in entries.items()),lang
    assert set(saved['existing_keys'][lang]) <= names.keys(),lang
print(f'PASS: reinforced body/cargo boundaries; preserved {len(saved["files"])} both-repo resource/arm hashes and {sum(map(len,saved["lang"].values()))} remote bilingual values; all existing translation keys retained.')

# Optional persisted pre-generation boundary: preserves the merged Lingchuan resources and keys.
boundary = ROOT.parent / 'arm_resources_preservation.json'
if boundary.is_file():
    saved = json.loads(boundary.read_text(encoding='utf-8'))
    for filename, digest in saved['files'].items():
        assert hashlib.sha256(Path(filename).read_bytes()).hexdigest() == digest, filename
    for lang, entries in saved['lang'].items():
        names = json.loads((ART / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
        assert all(preserved_translation(k, v, names, lang) for k,v in entries.items()), lang
    print(f'PASS: preserved {len(saved["files"])} Lingchuan resource hashes and {sum(map(len,saved["lang"].values()))} bilingual keys.')
print(f'PASS: Best manual-only assembly, historical servicing/mobility/diagnostics boundaries, independent Mech Operation, packed state and server-authoritative ride/drill metadata; synced original art/models/bilingual names; {len(items)} items / {len(recipes)} recipes / {len(canonical["catalogue"])} catalogue entries.')

# Cruise wheels preserve the existing walking profiles and all independent module state.
wheels = items['best_mech_cruise_wheels']
assert ASSEMBLY['best_mech_cruise_wheels'] == [('steel',12),('load',6),('conductor',4),('steel_fix',8),('sheath',4)]
assert ASSEMBLY_KNOWLEDGE['best_mech_cruise_wheels'] == ASSEMBLY_KNOWLEDGE['best_mech']
assert (wheels['real_item'],wheels['item_type'],wheels['max_stack_size'],wheels['max_durability']) == (True,'ordinary',1,0)
assert wheels['attribute_modifiers'] == [] and wheels['manual_assembly']['output_count'] == 1
assert not any(k in wheels for k in ('energy_storage','thermal','durability_system','equipment_slot','operation'))
assert len(LEARNING) == 7 and len(BEST_NAMES) == 17
slot = mech['legs_slot']
assert (slot['storage_key'],slot['max_installed'],slot['independent_of']) == ('Legs',1,['Body','EngineeringArm','UtilityModule','Core'])
assert all(slot[k] == '' for k in ('default','missing_legacy_value','unknown_value','walking_value'))
assert slot['wheels_value'] == 'best_mech_cruise_wheels' and not slot['legacy_missing_grants_item']
assert slot['requires'] == ASSEMBLY_KNOWLEDGE['best_mech'] and slot['minimum_research_level'] == 3
assert slot['conditions'] == ['isolated','unoccupied','alive','stationary','grounded','nonfluid']
assert slot['install'] == dict(activation='crouch_main_hand_wheels_use_on_actual_mech',consumes=1,require_empty_slot=True,hot_swap=False,failure_changes_nothing=True)
removal = slot['remove']
assert removal['activation'] == 'crouch_main_hand_diagnostic_offhand_minecraft_minecart_use_on_actual_mech'
assert not removal['consumes_minecart'] and removal['returns'] == dict(item='best_mech_cruise_wheels',qty=1)
assert removal['require_empty_main_inventory_slot'] and removal['inventory_slots'] == 36
assert removal['failure_changes_nothing'] and not removal['failure_falls_through_to_isolation']
assert set(slot['preserves']) >= {'exact_health','energy','heat','wear','isolated','work_ticks','custom_name','Body','EngineeringArm','UtilityModule','Core','CoreFuelTicks','CoreWorkTicks','Cargo','all_other_state'}
assert slot['persistence'] == ['entity_nbt','packed_item','deploy','death_drop'] and 'Legs' in mech['packed_state']['preserves']
performance = mech['cruise_wheels']
assert (performance['speed_multiplier'],performance['movement_energy_numerator'],performance['movement_energy_denominator'],performance['movement_heat_reduction']) == (1.25,3,4,1)
assert (performance['walking_step_height'],performance['wheels_step_height']) == (1.0,0.6)
assert all(performance[k] for k in ('preserves_unrelated_step_modifiers','half_block_passes','full_block_blocked','charges_only_actual_ground_movement'))
assert performance['wear_per_work_ticks'] == 100
assert not any(performance[k] for k in ('changes_cooling','changes_arm_work','changes_other_resources','flight','jump','automatic_pickup'))
for body,base,drive,energy,drive_energy,heat,drive_heat in [('standard',0.275,0.35,300,450,3,5),('reinforced',0.225,0.2875,450,675,5,7)]:
    assert performance['profiles'][body] == dict(base_speed=base,drive_speed=drive,base_energy=energy,drive_energy=drive_energy,base_heat=heat,drive_heat=drive_heat)
assert items['best_workbench']['locomotion_actions'] == dict(cruise_wheels=25)
assert items['best_workbench']['tab_layout'] == dict(window_width=300,window_height=272,x=8,width=38,stride=41,last_x=254,last_end=292,inventory_unchanged=True)
assert [BEST_UI_MESSAGES[f'tab_{i}'][1] for i in range(7)] == ['Build','Move','Care','Mech','Arms','Core','Wheels']
with Image.open(ART/'textures/item/best_mech_cruise_wheels.png') as image:
    assert image.size == (32,32) and image.mode == 'RGBA'
    assert 0 < sum(image.getchannel('A').histogram()[1:]) < 1024
    assert len(image.getcolors(1024)) > 5
wheel_bytes = (ART/'textures/item/best_mech_cruise_wheels.png').read_bytes()
assert all(wheel_bytes != (ART/'textures/item'/f'{key}.png').read_bytes() for key in BEST_NAMES if key != 'best_mech_cruise_wheels')
for lang in ('zh_cn','en_us'):
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    for prefix,suffixes in [('message',('best_page_6','best_action_25','best_mech_cruise_wheels_cost','best_mech_cruise_wheels_assembled')),
            ('ui',('best_action_25','best_material_action_25','best_tab_6','best_hint_6')),
            ('tooltip',('best_mech_cruise_wheels','best_mech_legs_install')),
            ('mech',('legs.none','legs.installed','legs.controls','legs.failed'))]:
        for suffix in suffixes: assert names[f'{prefix}.starfall_blueprint.{suffix}']
    assert names['mech.starfall_blueprint.legs.installed'].count('%s') == 1
for doc in (ROOT/'docs/BEST_ENGINEERING.md',ATLAS.parent/'18_贝斯特轻型工程首批.md',ATLAS.parent/'02_科技树设定/02_贝斯特机械科技树.md'):
    text=doc.read_text(encoding='utf-8')
    assert text.index('## 固燃发电核心') < text.index('## 巡航轮组')
    assert all(v in text for v in ('2026-11-02','MEC-04腿部槽','MEC-15','0.2875','675','minecraft:minecart','CoreFuelTicks','七标签','宽38','步距41','不进行UI测试'))
boundary=ROOT.parent/'wheels_resources_preservation.json'
saved=json.loads(boundary.read_text(encoding='utf-8'))
for filename,digest in saved['files'].items():
    assert hashlib.sha256(Path(filename).read_bytes()).hexdigest() == digest,filename
for lang,entries in saved['lang'].items():
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    assert all(preserved_translation(k, v, names, lang) for k,v in entries.items()),lang
    assert set(saved['existing_keys'][lang]) <= names.keys(),lang
report=dict(date='2026-11-02',status='PASS',chain='build_atlas→sync_catalogue→generate_catalogue_resources→verify_best_resources',
    chain_runs=1,items=len(items),recipes=len(recipes),catalogue=len(canonical['catalogue']),best_items=len(BEST_NAMES),
    preserved_files=len(saved['files']),preserved_remote_translation_values=sum(map(len,saved['lang'].values())),
    existing_translation_keys={lang:len(keys) for lang,keys in saved['existing_keys'].items()},
    coverage=['manual action25 and exact materials','independent Legs and guarded install/remove','four movement profiles and step-height metadata','original 32RGBA art and bilingual keys','both-repository prior remote resources and all Best art','dated guides and tree'],
    runtime_tested=False,ui_tested=False)
# The prior cruise-wheels report remains a record of that batch; this run writes the heavy report below.
print(f'PASS: cruise wheels; {len(items)} items / {len(recipes)} recipes / {len(canonical["catalogue"])} catalogue / {len(BEST_NAMES)} Best; preserved {len(saved["files"])} remote/Best files and all old translation keys. No UI or runtime claims.')

# First heavy MEC-09 platform: distinct manual-only item/entity and unchanged shared services.
heavy = items['best_heavy_mech']
assert ASSEMBLY['best_heavy_mech'] == [('steel',64),('load',32),('conductor',16),('board',8),('steel_fix',32),('head',2),('sensor',4),('sheath',16)]
assert ASSEMBLY_KNOWLEDGE['best_heavy_mech'] == ASSEMBLY_KNOWLEDGE['best_mech'] + ['advanced_engineering']
assert len(LEARNING) == 7 and len(BEST_NAMES) == 17
assert (heavy['id'],heavy['item_type'],heavy['max_stack_size']) == ('best_heavy_mech','packed_entity',1)
assert heavy['entity_id'] == 'starfall_blueprint:best_heavy_engineering_mech'
assert heavy['entity_dimensions'] == dict(width=2.6,height=4.4,rider_seat_height=3.1)
assert heavy['factory_defaults'] == dict(energy=0,arm='drill',cargo=[],core='',fuel_ticks=0,core_work_ticks=0)
assert heavy['max_health'] == 300 and heavy['energy_storage']['capacity'] == 60000000
assert (heavy['energy_storage']['entity_receive_per_tick'],heavy['energy_storage']['packed_base_transfer_per_tick']) == (512,5000)
assert heavy['energy_storage']['same_tick_transfer_budget'] and heavy['energy_storage']['source_caps_apply']
assert heavy['thermal'] == dict(overheat=1000,resume=400,cooling_per_tick=4) and heavy['max_wear'] == 2400
assert heavy['body_profiles']['heavy'] == dict(capacity=60000000,max_health=300,cargo_slots=27,movement_speed=0.26,drive_speed=0.34,movement_energy=1600,drive_movement_energy=2400,movement_heat=10,drive_movement_heat=14)
assert heavy['cruise_wheels']['profiles']['heavy'] == dict(base_speed=0.325,drive_speed=0.425,base_energy=1200,drive_energy=1800,base_heat=9,drive_heat=13)
assert (heavy['cruise_wheels']['speed_multiplier'],heavy['cruise_wheels']['movement_energy_numerator'],heavy['cruise_wheels']['movement_energy_denominator'],heavy['cruise_wheels']['movement_heat_reduction']) == (1.25,3,4,1)
assert (heavy['cruise_wheels']['walking_step_height'],heavy['cruise_wheels']['wheels_step_height']) == (1.0,0.6)
assert all(heavy['deployment'].values())
assert not heavy['body_slot']['compatible'] and heavy['body_slot']['explicit_rejection']
assert heavy['body_slot']['failure_changes_nothing'] and not heavy['body_slot']['failure_falls_through_to_isolation']
assert items['best_mech_reinforced_body']['body_upgrade']['rejects_heavy']
for slot in ('utility_slot','core_slot','legs_slot'):
    assert heavy[slot] == mech[slot],slot
assert {k:v for k,v in heavy['engineering_arm_slot'].items() if k != 'allowed_items'} == {k:v for k,v in mech['engineering_arm_slot'].items() if k != 'allowed_items'}
assert {k:v for k,v in heavy['engineering_work'].items() if k != 'heavy_area_operation'} == mech['engineering_work']
assert heavy['field_service'] == mech['field_service']
assert heavy['cargo']['slots'] == 27 and heavy['cargo']['actual_inventory']
assert heavy['cargo']['open'] == mech['cargo']['open'] and not heavy['cargo']['packed_mech_nesting']
assert heavy['cargo']['rejected_packed_items'] == ['best_mech','best_heavy_mech']
assert heavy['cargo']['death']['contents_before_dead_packed_item']
assert not heavy['solid_fuel_generation']['cannot_sustain_walking_alone']
assert (heavy['solid_fuel_generation']['energy_per_operating_tick'],heavy['solid_fuel_generation']['heat_per_operating_tick'],heavy['solid_fuel_generation']['wear_per_operating_ticks']) == (2000,2,1000)
assert heavy['solid_fuel_generation']['fuel_rule'] == mech['solid_fuel_generation']['fuel_rule']
assert heavy['solid_fuel_generation']['energy_per_operating_tick'] > heavy['cruise_wheels']['profiles']['heavy']['base_energy']
assert heavy['packed_state']['item'] == heavy['packed_state']['death_item'] == 'best_heavy_mech'
assert set(heavy['packed_state']['preserves']) >= set(mech['packed_state']['preserves'])
assert heavy['packed_state']['destroyed_health_cannot_deploy'] and not any(heavy['weapons'].values())
assert items['best_workbench']['mech_page_actions'] == [17,18,19,20,23,26]
assert len(items['best_workbench']['tabs']) == 7
for key,field in [('best_mech_radiator','utility_module'),('best_mech_drive','utility_module'),('best_mech_drill_arm','engineering_arm'),('best_mech_excavator_arm','engineering_arm'),('best_mech_generator_core','generator_core'),('best_mech_cruise_wheels','locomotion_module')]:
    assert items[key][field]['compatible_platforms'] == ['best_mech','best_heavy_mech']
for key,speed,energy,heat,cooling in [('best_mech_radiator',0.26,1600,10,6),('best_mech_drive',0.34,2400,14,4)]:
    assert items[key]['utility_module']['body_profiles']['heavy'] == dict(movement_speed=speed,energy_per_actual_ground_movement_tick=energy,heat_per_actual_ground_movement_tick=heat,passive_cooling_per_tick=cooling)
for path,size in [(ART/'textures/item/best_heavy_mech.png',(32,32)),(ART/'textures/entity/best_heavy_engineering_mech.png',(128,128))]:
    with Image.open(path) as image:
        assert image.size == size and image.mode == 'RGBA'
        assert len(image.getcolors(size[0]*size[1])) > 5
heavy_art=(ART/'textures/item/best_heavy_mech.png').read_bytes()
assert all(heavy_art != (ART/'textures/item'/f'{key}.png').read_bytes() for key in BEST_NAMES if key != 'best_heavy_mech')
for lang in ('zh_cn','en_us'):
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    for prefix,suffixes in [('message',('best_action_26','best_heavy_mech_cost','best_heavy_mech_assembled')),('ui',('best_action_26','best_material_action_26')),('tooltip',('best_heavy_mech',)),('mech',('platform.heavy','platform.standard','body.heavy_rejected')),('entity',('best_heavy_engineering_mech',))]:
        for suffix in suffixes: assert names[f'{prefix}.starfall_blueprint.{suffix}']
for doc in (ROOT/'docs/BEST_ENGINEERING.md',ATLAS.parent/'18_贝斯特轻型工程首批.md',ATLAS.parent/'02_科技树设定/02_贝斯特机械科技树.md'):
    text=doc.read_text(encoding='utf-8')
    assert all(v in text for v in ('MEC-09首个平台','60,000,000 XH','1600','2400','27格','动作26','CoreFuelTicks','不进行UI测试'))
saved=json.loads((ROOT.parent/'heavy_resources_preservation.json').read_text(encoding='utf-8'))
for filename,digest in saved['files'].items():
    assert hashlib.sha256(Path(filename).read_bytes()).hexdigest() == digest,filename
for lang,entries in saved['lang'].items():
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    assert all(preserved_translation(k, v, names, lang) for k,v in entries.items()),lang
    assert set(saved['existing_keys'][lang]) <= names.keys(),lang
report=dict(date='2026-11-02',status='PASS',chain='build_atlas→sync_catalogue→generate_catalogue_resources→verify_best_resources',chain_runs=1,
    items=len(items),recipes=len(recipes),catalogue=len(canonical['catalogue']),best_items=len(BEST_NAMES),preserved_files=len(saved['files']),
    exact_prior_translation_values=sum(map(len,saved['lang'].values())),existing_translation_keys={lang:len(keys) for lang,keys in saved['existing_keys'].items()},
    coverage=['action26 exact manual materials and existing knowledge','independent heavy packed identity and factory defaults','heavy footprint and movement metadata','27 cargo slots and death ordering','shared service guards, fuel and state preservation','light body rejection','original item/entity art and bilingual names','prior remote and Best resource hashes, every old translation value','dated guide and MEC-09 boundaries'],runtime_tested=False,ui_tested=False,build_run=False)
# Preserve the prior heavy-platform report; write this batch below.
print(f'PASS: first heavy platform; {len(items)} items / {len(recipes)} recipes / {len(canonical["catalogue"])} catalogue / {len(BEST_NAMES)} Best; {len(saved["files"])} protected files and {sum(map(len,saved["lang"].values()))} exact old translations retained. No UI/build/runtime claims.')


# MEC-06 heavy-only 3x3 drill: static canonical/resources contract, no runtime claim.
key = 'best_mech_heavy_drill_arm'
arm = items[key]
spec = arm['engineering_arm']
assert ASSEMBLY[key] == [('steel',24),('load',12),('conductor',8),('head',8),('board',4),('steel_fix',12),('sheath',8)]
assert ASSEMBLY_KNOWLEDGE[key] == ASSEMBLY_KNOWLEDGE['best_mech']
assert (arm['real_item'],arm['item_type'],arm['max_stack_size'],arm['max_durability']) == (True,'ordinary',1,0)
assert not any(k in arm for k in ('energy_storage','thermal','durability_system','equipment_slot'))
assert arm['manual_assembly']['output_count'] == 1 and arm['attribute_modifiers'] == []
assert not any(k == entry['item'] for recipe in recipes for entry in recipe['outputs'] for k in (key,))
assert spec['compatible_platforms'] == ['best_heavy_mech']
assert (spec['manual_action'],spec['networking_action'],spec['energy'],spec['heat'],spec['wear'],spec['cooldown_ticks']) == (27,0,48000,35,1,40)
assert spec['tool'] == 'minecraft:diamond_pickaxe' and spec['harvest_tier'] == 'diamond'
assert spec['cost_basis'] == 'per_successful_block' and spec['cooldown_basis'] == 'per_action_first_success'
assert spec['ordinary_drops'] and spec['success_only_costs'] and not spec['single_block']
assert spec['stops_on'] == ['insufficient_energy','thermal_lock','worn_out']
target = spec['targeting']
assert target['shape'] == '3x3_plane_perpendicular_to_hit_face' and target['max_blocks'] == 9
assert all(target[k] for k in ('center_first','center_success_required','invalid_center_aborts_batch','invalid_center_spends_nothing','recheck_each_block'))
assert not any(target[k] for k in ('invalid_center_sets_cooldown','all_nine_guaranteed','vein_mining'))
assert target['neighbor_failures'] == 'skip_independently'
assert target['reach'] == 'min(5, player_block_interaction_range)'
assert target['block_checks'] == ['protection','BreakEvent','actual_player_reach','loaded_chunk','world_border','no_block_entity','nonfluid','breakable','correct_tool']
assert key not in mech['engineering_arm_slot']['allowed_items'] and key in heavy['engineering_arm_slot']['allowed_items']
for platform in (mech, heavy):
    slot = platform['engineering_arm_slot']
    assert key in slot['known_items'] and slot['heavy_only_items'] == [key, 'best_mech_heavy_excavator_arm']
    assert slot['light_loaded_heavy_arm'] == dict(retain_identity=True,removable=True,allows_install=False,allows_work=False,free_conversion=False)
    assert slot['install']['platform_compatibility_required']
    assert platform['engineering_work']['shared_cooldown_max_ticks'] == 40
    assert platform['engineering_work']['cooldown_persistence'] == ['arm_swap','entity_nbt','packed_item','deploy','death_drop']
assert items['best_workbench']['arms_page_actions'] == [21,22,27,28]
new_art = (ART/'textures/item'/f'{key}.png').read_bytes()
assert new_art == (ATLAS/'item-art'/f'{key}.png').read_bytes()
assert all(new_art != (ART/'textures/item'/f'{old}.png').read_bytes() for old in BEST_NAMES if old != key)
with Image.open(ART/'textures/item'/f'{key}.png') as image:
    assert image.size == (32,32) and image.mode == 'RGBA'
    assert 0 < sum(image.getchannel('A').histogram()[1:]) < 1024
for lang in ('zh_cn','en_us'):
    names = json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    for prefix,suffixes in [('item',(key,)),('message',('best_action_27',key+'_cost',key+'_assembled')),('ui',('best_action_27','best_material_action_27')),('tooltip',(key,key+'_install')),('mech',('arm.heavy_only','arm.heavy_required')),('hud',('mech.panel.incompatible_arm','mech.panel.heavy_standby','mech.panel.work_cost_per_block'))]:
        for suffix in suffixes: assert names[f'{prefix}.starfall_blueprint.{suffix}']
    assert all(f'%{i}$s' in names['hud.starfall_blueprint.mech.panel.heavy_standby'] for i in (1,2))
for doc in (ROOT/'docs/BEST_ENGINEERING.md',ATLAS.parent/'18_贝斯特轻型工程首批.md',ATLAS.parent/'02_科技树设定/02_贝斯特机械科技树.md'):
    text=doc.read_text(encoding='utf-8')
    assert all(v in text for v in ('MEC-06重型工具增量','动作27','48,000 XH','热35','40刻','BreakEvent','最多9块','中心','min(5, player_block_interaction_range)','不进行UI测试或构建'))
saved=json.loads((ROOT.parent/'heavy_drill_resources_preservation.json').read_text(encoding='utf-8'))
for filename,digest in saved['files'].items():
    assert hashlib.sha256(Path(filename).read_bytes()).hexdigest() == digest,filename
for lang,entries in saved['lang'].items():
    names=json.loads((ART/'lang'/f'{lang}.json').read_text(encoding='utf-8'))
    assert all(preserved_translation(k, v, names, lang) for k,v in entries.items()),lang
    assert set(saved['existing_keys'][lang]) <= names.keys(),lang
report=dict(date='2026-11-02',status='PASS',chain='build_atlas→sync_catalogue→generate_catalogue_resources→verify_best_resources',chain_runs=1,
    items=len(items),recipes=len(recipes),catalogue=len(canonical['catalogue']),best_items=len(BEST_NAMES),preserved_files=len(saved['files']),
    exact_prior_translation_values=sum(map(len,saved['lang'].values())),existing_translation_keys={lang:len(keys) for lang,keys in saved['existing_keys'].items()},
    coverage=['action27 exact manual materials and existing knowledge','heavy-only known arm and light removal-only metadata','center-first bounded 3x3 plane and per-block independent checks','successful-block costs and first-success shared40 cooldown metadata','unchanged old20 arms and service state','original 32RGBA art and bilingual keys','all prior heavy/wheels/Best/remote resource hashes and exact translation values','dated MEC-06 tool increment, no full weapons claim'],runtime_tested=False,ui_tested=False,build_run=False)
# Historical heavy-drill report remains unchanged; current checks follow below.
print(f'PASS: heavy drill resources; {len(items)} items / {len(recipes)} recipes / {len(canonical["catalogue"])} catalogue / {len(BEST_NAMES)} Best; {len(saved["files"])} protected files and {sum(map(len,saved["lang"].values()))} exact old translations retained. No UI/build/runtime claims.')


# Water God actual engineering upgrade contract (static metadata only).
upgrade = heavy['upgrade']
assert upgrade['action'] == 26 and upgrade['node_id'] == 'best:advanced_engineering'
assert upgrade['storage_key'] == LEARNING['advanced_engineering']['storage_key'] == 'BestAdvancedEngineering'
assert LEARNING['advanced_engineering']['requires'] == ['mech_operation']
assert LEARNING['advanced_engineering']['experience_levels'] == 8
assert LEARNING['advanced_engineering']['inputs'] == [dict(item='load',qty=8),dict(item='conductor',qty=4),dict(item='sensor',qty=2),dict(item='board',qty=4)]
assert LEARNING['advanced_engineering']['first_unlock_rewards']['items'] == [dict(item='steel_fix',qty=8)]
assert LEARNING['advanced_engineering']['first_unlock_rewards']['experience_points'] == 80
assert heavy['manual_assembly']['operation'] == 'upgrade' and heavy['manual_assembly']['source_required'] == ['isolated','alive']
assert not heavy['manual_assembly']['separate_factory_assembly']
assert all(upgrade[k] for k in ('consume_and_replace_main_hand','preserve_all_item_components','preserve_all_custom_data','preserve_absolute_health','failure_changes_nothing'))
assert upgrade['source_missing_health'] == dict(standard=100,reinforced=160)
assert upgrade['legacy_target_missing_health'] == 300
assert not any(upgrade[k] for k in ('heals','refills_energy','clears_heat','clears_wear','existing_advanced_requires_new_node_to_drive'))
assert upgrade['reinforced_body']['returns'] == dict(item='best_mech_reinforced_body',qty=1)
assert upgrade['reinforced_body']['require_empty_main_inventory_slot'] and upgrade['reinforced_body']['inventory_slots'] == 36
assert heavy['solid_fuel_generation']['requires'][-1] == 'capacity_room_at_least_2000'
assert heavy['solid_fuel_generation']['pause_on'][1] == 'capacity_room_below_2000'
assert (len(items),len(recipes),len(canonical['catalogue']),len(BEST_NAMES)) == (317,245,183,17)
print('PASS: Water God upgrade metadata, seven Best progression nodes, SG-E1/SG-E2 names and unchanged registry counts. No build/UI/runtime claims.')


# Canglan area excavation increment (resource assertions only).
area = items['best_mech_heavy_excavator_arm']
assert ASSEMBLY['best_mech_heavy_excavator_arm'] == [('steel',16),('load',8),('conductor',4),('steel_fix',8),('sheath',4),('head',2)]
assert (area['item_type'],area['max_stack_size'],area['max_durability']) == ('ordinary',1,0)
area_spec = area['engineering_arm']
assert (area_spec['manual_action'],area_spec['energy'],area_spec['heat'],area_spec['wear'],area_spec['cooldown_ticks']) == (28,18000,18,1,40)
assert area_spec['tool'] == 'minecraft:iron_shovel' and area_spec['mineable_tag'] == 'minecraft:mineable/shovel'
assert area_spec['compatible_platforms'] == ['best_heavy_mech']
assert area_spec['targeting'] == items['best_mech_heavy_drill_arm']['engineering_arm']['targeting']
assert area_spec['success_only_costs'] and area_spec['shared_cooldown']
assert area['manual_assembly']['requires'] == ASSEMBLY_KNOWLEDGE['best_mech']
