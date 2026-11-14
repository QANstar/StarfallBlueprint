"""Check Haijia asset references, recipes, models and bilingual coverage."""
from pathlib import Path
import json
from PIL import Image
R=Path(__file__).resolve().parents[1]/'src/main/resources'
A=R/'assets/starfall_blueprint';D=R/'data/starfall_blueprint'
manifest=json.loads((A/'haijia_manifest.json').read_text(encoding='utf-8'))
for locale in ['zh_cn','en_us']:
 lang=json.loads((A/f'lang/{locale}.json').read_text(encoding='utf-8'))
 for key in manifest['items']:assert 'item.starfall_blueprint.hj_'+key in lang,(locale,key)
for key in manifest['items']:
 path=A/f'models/item/hj_{key}.json';assert path.is_file(),path
 data=json.loads(path.read_text(encoding='utf-8'))
 if data.get('parent','').startswith('starfall_blueprint:'):assert (A/('models/'+data['parent'].split(':')[1]+'.json')).exists(),path
for path in list(A.glob('models/block/hj_*.json'))+list(A.glob('models/item/hj_*.json')):
 data=json.loads(path.read_text(encoding='utf-8'))
 for texture in data.get('textures',{}).values():
  if texture.startswith('starfall_blueprint:'):
   asset=A/('textures/'+texture.split(':')[1]+'.png');assert asset.exists(),(path,asset);Image.open(asset).verify()
 for element in data.get('elements',[]):
  assert all(-16<=x<=32 for x in element['from']+element['to']),path
  assert all(element['from'][i]<element['to'][i] for i in range(3)),path
structures=json.loads((A/'haijia_structures.json').read_text(encoding='utf-8'))
for key,spec in structures.items():
 variants=json.loads((A/f'blockstates/hj_{key}.json').read_text(encoding='utf-8'))['variants'];w,h,d=spec['size']
 assert len(variants)==400,key
 for part in range(w*h*d):
  for facing in ['north','east','south','west']:
   model=variants[f'facing={facing},part={part}']['model'].split(':')[1]
   assert (A/f'models/{model}.json').exists(),(key,part,facing)
for key,spec in manifest['recipes'].items():
 data=json.loads((D/f'recipe/hj_{key}.json').read_text(encoding='utf-8'))
 assert [x['item'] for x in data['ingredients']]==spec['inputs'],key
 assert data['result']['count']==spec['count'] and len(data['ingredients'])<=9,key
for path in D.rglob('hj_*.json'):json.loads(path.read_text(encoding='utf-8'))
print(f'PASS: {len(manifest["items"])} items, {len(manifest["recipes"])} public assembly recipes, bilingual names, texture references, voxel geometry, worldgen and recipe JSON.')
