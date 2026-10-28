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
assert (len(items), len(recipes), len(canonical['catalogue'])) == (305, 245, 171)
armor = ART / 'textures/models/armor/best_frame_layer_2.png'
assert armor.read_bytes() == (ATLAS / 'item-art/best_frame_layer_2.png').read_bytes()
with Image.open(armor) as image:
    assert image.size == (64,32) and image.mode == 'RGBA'
    alpha_histogram = image.getchannel('A').histogram()
    assert 0 < sum(alpha_histogram[1:]) < 512, 'Keep the external frame open and transparent'
    assert image.crop((0,20,16,32)).getbbox() and image.crop((16,20,40,27)).getbbox()
assert items['best_frame']['equipment_texture'].endswith('models/armor/best_frame_layer_2.png')
assert len(json.loads((ART / 'blockstates/best_workbench.json').read_text())['variants']) == 4
print('PASS: Best nine-cell bench, three-level manual assembly, field kit durability/repair and bucket cooling metadata, independent Light Mobility boolean, stride module install/modes/removal/standing cost and preserved frame data, no public equipment recipes, synced art/models/names; 305 items / 245 recipes / 171 catalogue entries.')
