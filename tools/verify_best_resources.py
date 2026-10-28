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
from best_expansion import BEST_NAMES, ASSEMBLY


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
    assert items[key]['energy_storage']['capacity'] == 200000
    assert items[key]['manual_repair']['durability'] == 200
    for path in (DATA / 'recipe').glob('*.json'):
        text = path.read_text(encoding='utf-8')
        assert f'starfall_blueprint:{key}' not in text, path
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
armor = ART / 'textures/models/armor/best_frame_layer_2.png'
assert armor.read_bytes() == (ATLAS / 'item-art/best_frame_layer_2.png').read_bytes()
with Image.open(armor) as image:
    assert image.size == (64,32) and image.mode == 'RGBA'
    alpha_histogram = image.getchannel('A').histogram()
    assert 0 < sum(alpha_histogram[1:]) < 512, 'Keep the external frame open and transparent'
    assert image.crop((0,20,16,32)).getbbox() and image.crop((16,20,40,27)).getbbox()
assert items['best_frame']['equipment_texture'].endswith('models/armor/best_frame_layer_2.png')
assert len(json.loads((ART / 'blockstates/best_workbench.json').read_text())['variants']) == 4
print('PASS: Best public bench nine-cell bill, personal-only assembly, zero equipment recipes, synced art/models/names/metadata.')
