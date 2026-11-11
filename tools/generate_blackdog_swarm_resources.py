"""Resources for the detachable Black Dog guided missile swarm module."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/starfall_blueprint'
KEY = 'blackdog_component_swarm'

def write(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

path = ASSETS / 'lingchuan.json'
definitions = json.loads(path.read_text(encoding='utf-8'))
definitions[KEY] = dict(definitions['blackdog_component_missile'], id=KEY, name='追猎蜂群飞弹组件')
write(path, definitions)
write(ASSETS / f'models/item/{KEY}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'starfall_blueprint:item/{KEY}'}})
im = Image.new('RGBA', (32, 32))
d = ImageDraw.Draw(im)
d.rounded_rectangle((3, 6, 28, 27), radius=3, fill='#25232e', outline='#796070', width=2)
for x, y in [(7, 10), (14, 7), (21, 10)]:
    d.polygon([(x, y+3), (x+2, y), (x+4, y+3), (x+4, y+12), (x, y+12)], fill='#eebc86', outline='#c94766')
    d.rectangle((x+1, y+5, x+3, y+8), fill='#eee7e2')
    d.line((x+2, y+13, x+2, y+16), fill='#ff7958', width=2)
for a,b in [((2,2),(10,2)),((2,2),(2,9)),((22,2),(30,2)),((30,2),(30,9))]:
    d.line((a,b), fill='#f36a89', width=2)
im.save(ASSETS / f'textures/item/{KEY}.png')

for locale, values in {
    'zh_cn': {
        'lc.armed_watch_hint': '创造预装：III阶八部位 · 全部13种组件（含追猎蜂群）· 初始满电1,000,000 XH',
        f'item.starfall_blueprint.{KEY}': '追猎蜂群飞弹组件',
        'lc.ability.swarm': '长按火控终端右键瞄准生物，0.8秒锁定；准星移远或遮挡取消。锁定后松开发射16枚追踪飞弹，间隔0.15秒，消耗16000 XH。',
        'lc.system.swarm': '追猎蜂群',
        'key.starfall_blueprint.blackdog_swarm': '黑狗：追猎蜂群',
        'lc.swarm_searching': '追猎蜂群 · 搜索目标',
        'lc.swarm_locking': '追猎蜂群 · 锁定中 %s%%',
        'lc.swarm_locked': '追猎蜂群 · 已锁定 · 松开右键发射',
    },
    'en_us': {
        'lc.armed_watch_hint': 'Creative preset: eight tier III bases, all 13 components including Huntsman Swarm, starts with 1,000,000 XH',
        f'item.starfall_blueprint.{KEY}': 'Huntsman Missile Swarm Component',
        'lc.ability.swarm': 'Hold terminal use to aim at any living target. Lock in 0.8s; looking away or blocked sight cancels. Release to launch 16 homing missiles, 0.15s apart. Costs 16000 XH.',
        'lc.system.swarm': 'Huntsman Swarm',
        'key.starfall_blueprint.blackdog_swarm': 'Black Dog: Huntsman Swarm',
        'lc.swarm_searching': 'Huntsman Swarm: searching',
        'lc.swarm_locking': 'Huntsman Swarm: locking %s%%',
        'lc.swarm_locked': 'Huntsman Swarm: locked - release to fire',
    },
}.items():
    path = ASSETS / f'lang/{locale}.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update(values)
    write(path, data)

print('Generated Black Dog swarm component, icon, model and translations')
