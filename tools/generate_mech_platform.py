"""Generate only maintenance-platform assets; merge its own language keys.
Run from any directory with the project's Python/Pillow environment.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
NS = 'starfall_blueprint'
NAME = 'mech_maintenance_platform'
def save(relative, data):
    p = ROOT / relative
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
base = f'assets/{NS}'
save(f'{base}/blockstates/{NAME}.json', {'variants': {'': {'model': f'{NS}:block/{NAME}'}}})
save(f'{base}/models/block/{NAME}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{NAME}'}})
save(f'{base}/models/item/{NAME}.json', {'parent': f'{NS}:block/{NAME}'})
im = Image.new('RGBA', (32,32), '#26394b')
d = ImageDraw.Draw(im)
d.rectangle((1,1,30,30), outline='#6c8594', width=2)
for i in range(4,29,8):
    d.line((i,4,i,27), fill='#405d6e', width=2)
d.rectangle((10,10,21,21), fill='#142937', outline='#53cfdf', width=2)
for x,y in [(4,4),(26,4),(4,26),(26,26)]: d.rectangle((x,y,x+1,y+1),fill='#e2b65a')
p=ROOT/f'{base}/textures/block/{NAME}.png'
p.parent.mkdir(parents=True,exist_ok=True)
im.save(p)
save(f'data/{NS}/recipe/{NAME}.json', {'type':'minecraft:crafting_shaped','pattern':['SSS','CEC','SSS'],'key':{'S':{'item':f'{NS}:ordinary_steel'},'C':{'item':f'{NS}:control_board'},'E':{'item':f'{NS}:best_frame'}},'result':{'id':f'{NS}:{NAME}','count':1}})
for locale, values in {
 'en_us': {f'block.{NS}.{NAME}':'Mech Maintenance Platform (3×3)',f'message.{NS}.platform_denied':'Only the platform owner may access supplies.',f'message.{NS}.platform_status':'Platform: %s FE / %s steel / complete: %s'},
 'zh_cn': {f'block.{NS}.{NAME}':'机甲维护平台（3×3）',f'message.{NS}.platform_denied':'仅平台所有者可操作物资。',f'message.{NS}.platform_status':'平台：%s FE / %s 钢材 / 完整：%s'}
}.items():
 p=ROOT/f'{base}/lang/{locale}.json'
 data=json.loads(p.read_text(encoding='utf-8')) if p.exists() else {}
 data.update(values)
 save(f'{base}/lang/{locale}.json',data)
