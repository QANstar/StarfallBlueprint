"""Check the handbook's discrete recipes against the mod's actual datapack recipes."""
from collections import Counter
import json
from pathlib import Path

project = Path(__file__).resolve().parents[1]
atlas = project.parent / 'RE_BFL/04_游戏设定/产线图册'
data = json.loads((atlas / 'production-data.json').read_text(encoding='utf-8'))
items = data['items']
recipes = {r['id']: r for r in data['recipes']}
for r in recipes.values():
    for x in r['inputs'] + r['outputs']:
        if items[x['item']]['unit'] not in ('mB', 'XH'):
            assert isinstance(x['qty'], int) and x['qty'] > 0, r['id']
    if r['station'] == 'workbench' and r['kind'] != '贸易':
        assert len(r['craft']['cells']) == 9
        assert all(c is None or c['qty'] == 1 for c in r['craft']['cells']), r['id']
        assert sum(x['qty'] for x in r['inputs']) <= 9
    if r['station'] == 'furnace':
        assert len(r['inputs']) == 1, r['id']

mod = 'starfall_blueprint:'
aliases = dict(iron_plate='iron_plate', copper_plate='copper_plate', iron_fasteners='fix',
    mixed_steel_billet='steel_pre', ordinary_steel='steel', ceramic='ceramic', insulation='insulation',
    binder='binder', isolation_substrate='substrate', control_board='board', circuit_breaker='breaker',
    ceramic_sheath='sheath', steel_fasteners='steel_fix', sealed_box='box', machine_frame='machine_frame',
    alloy_furnace='alloy_furnace', preparation_bench='prep', water_tank='tank', rain_collector='rain_collector')
vanilla = dict(iron_ingot='iron', copper_ingot='copper', cobblestone='stone', furnace='furnace',
    redstone='redstone', glass='glass', clay_ball='clay', paper='paper', wheat='wheat', slime_ball='slime',
    planks='plank')
def key(ingredient):
    name = ingredient.get('item', ingredient.get('tag', ''))
    namespace, name = name.split(':')
    return aliases[name] if namespace == 'starfall_blueprint' else vanilla[name]

bindings = dict(iron_plate='ST-01', copper_plate='ST-02', iron_fasteners='ST-03', ordinary_steel='ST-05',
    ceramic='ST-06', insulation='ST-07', binder='ST-08', control_board='ST-10', circuit_breaker='ST-11',
    ceramic_sheath='ST-12', steel_fasteners='ST-13', sealed_box='ST-14', machine_frame='MC-C01',
    alloy_furnace='MC-M01', preparation_bench='PC-M03', water_tank='EN-T01')
resource = project / 'src/main/resources/data/starfall_blueprint/recipe'
for name, rid in bindings.items():
    raw = json.loads((resource / f'{name}.json').read_text(encoding='utf-8'))
    if raw['type'] == 'minecraft:crafting_shaped':
        inputs = Counter(key(raw['key'][symbol]) for row in raw['pattern'] for symbol in row if symbol != ' ')
    elif raw['type'] == 'minecraft:crafting_shapeless':
        inputs = Counter(key(i) for i in raw['ingredients'])
    elif raw['type'] == 'minecraft:smelting': inputs = Counter({key(raw['ingredient']): 1})
    else: raise AssertionError(raw['type'])
    assert inputs == Counter({x['item']: x['qty'] for x in recipes[rid]['inputs']}), (rid, inputs)
    result = raw['result']
    assert recipes[rid]['outputs'] == [dict(item=aliases[result['id'].removeprefix(mod)], qty=result.get('count', 1))], rid

alloy = json.loads((resource / 'mixed_steel_billet.json').read_text(encoding='utf-8'))
assert alloy['type'] == mod + 'alloy_mixing'
assert alloy['base_count'] == 3 and alloy['additive_count'] == 1 and alloy['duration'] == 200
assert recipes['ST-04']['station'] == 'alloy_furnace'
assert recipes['ST-04']['inputs'] == [dict(item='iron', qty=3), dict(item='coal', qty=1)]
assert recipes['ST-04']['outputs'] == [dict(item='steel_pre', qty=3)]
assert recipes['ST-09']['inputs'] == [dict(item='copper_plate', qty=1), dict(item='quartz', qty=1)]
assert recipes['ST-09']['outputs'] == [dict(item='substrate', qty=2)]
print(f'PASS: {len(recipes)} discrete catalogue recipes; {len(bindings)} vanilla recipes match mod inputs/outputs; alloy and substrate contracts match.')
