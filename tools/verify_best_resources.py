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
assert (len(items), len(recipes), len(canonical['catalogue'])) == (300 + len(BEST_NAMES), 245, 166 + len(BEST_NAMES))
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
assert items['best_workbench']['mech_actions'] == dict(learn=17,assemble=18,radiator=19,drive=20)
for path, size in [(ART / 'textures/item/best_mech.png',(32,32)),(ART / 'textures/entity/best_engineering_mech.png',(128,128))]:
    with Image.open(path) as image:
        assert image.size == size and image.mode == 'RGBA'
        assert len(image.getcolors(size[0]*size[1])) > 5
for lang in ('zh_cn','en_us'):
    names=json.loads((ART / 'lang' / f'{lang}.json').read_text(encoding='utf-8'))
    assert names['entity.starfall_blueprint.best_engineering_mech']
    for suffix, translations in BEST_UI_MESSAGES.items():
        assert names[f'ui.starfall_blueprint.best_{suffix}'] == translations[0 if lang == 'zh_cn' else 1]
    for suffix in ('title','health','energy','heat','wear','ready','destroyed','isolated','thermal_locked','worn_out','unqualified','no_power','drill_power','hot','low_power','worn','request','standby','controls'):
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
print(f'PASS: Best manual-only assembly, historical servicing/mobility/diagnostics boundaries, independent Mech Operation, packed state and server-authoritative ride/drill metadata; synced original art/models/bilingual names; {len(items)} items / {len(recipes)} recipes / {len(canonical["catalogue"])} catalogue entries.')
