"""Static resource checks complement engine GameTests; no Minecraft installation needed."""
import json
from pathlib import Path
from PIL import Image

root=Path(__file__).resolve().parents[1]/'src/main/resources'
count=0
for p in root.rglob('*.json'):
    json.loads(p.read_text(encoding='utf-8'));count+=1
for p in root.rglob('*.png'):
    with Image.open(p) as im:
        im.verify()
for p in (root/'assets/starfall_blueprint/models').rglob('*.json'):
    data=json.loads(p.read_text(encoding='utf-8'))
    refs=list(data.get('textures',{}).values())
    for ref in refs:
        if ref.startswith('starfall_blueprint:'):
            assert (root/'assets/starfall_blueprint/textures'/(ref.split(':',1)[1]+'.png')).exists(),(p,ref)
for p in (root/'assets/starfall_blueprint/blockstates').glob('*.json'):
    for variant in json.loads(p.read_text())['variants'].values():
        ref=variant['model'].split(':',1)[1]
        assert (root/'assets/starfall_blueprint/models'/(ref+'.json')).exists(),(p,ref)
assert (root/'data/starfall_blueprint/recipe/preparation_bench.json').exists()
assert (root/'data/starfall_blueprint/neoforge/biome_modifier/meteor_phase.json').exists()
print(f'PASS: {count} JSON files, all PNGs and local model/texture links.')
