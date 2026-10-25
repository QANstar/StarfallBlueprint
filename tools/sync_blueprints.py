"""Bundle the exact same portable designs downloaded from the offline handbook."""
from pathlib import Path
import json, shutil
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT.parent/'RE_BFL/04_游戏设定/产线图册/blueprints'
TARGET=ROOT/'src/main/resources/assets/starfall_blueprint/construction_blueprints'
TARGET.mkdir(parents=True,exist_ok=True)
names=[]
for path in sorted(SOURCE.glob('L*.sfbp.json')):
    data=json.loads(path.read_text(encoding='utf-8'))
    assert data['format']=='starfall_blueprint' and data['version']==1
    assert 1<=len(data['blocks'])<=512
    assert len(path.read_text(encoding='utf-8'))<=60000
    shutil.copy2(path,TARGET/path.name);names.append(path.name)
(TARGET/'index.json').write_text(json.dumps(names,indent=2)+'\n',encoding='utf-8')
print(f'Bundled {len(names)} handbook blueprints.')
