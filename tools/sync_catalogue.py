"""Export canonical handbook definitions for the gameplay registry; no completion claims."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
ATLAS=ROOT.parent/'RE_BFL/04_游戏设定/产线图册'
data=json.loads((ATLAS/'production-data.json').read_text(encoding='utf-8'))
out=ROOT/'src/main/resources/assets/starfall_blueprint/catalogue.json'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps({k:data[k] for k in ('items','recipes','catalogue')},ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
existing={'ST-01','ST-02','ST-03','ST-04','ST-05','ST-06','ST-07','ST-08','ST-09','ST-10','ST-11','ST-12','ST-13','ST-14',
          'MC-C01','MC-M01','PC-M03','EN-T01','SV-M03','CB-01','CB-02','CB-03','CB-04','CB-05','UP-COMPOSITE'}
recipes=ROOT/'src/main/resources/data/starfall_blueprint/recipe'
reserved={'moon','lunar','moon_tail','liner','lined_box','anima','anima_basic','staff_blank'}
for r in data['recipes']:
    if r['station']=='workbench' and r['kind']!='贸易' and not r['id'].startswith('V-') and r['id'] not in existing:
        definition={'type':'starfall_blueprint:catalogue_crafting','recipe':r['id']}
        if any(entry['item'] in reserved for side in ('inputs','outputs') for entry in r[side]):
            definition['neoforge:conditions']=[{'type':'neoforge:false'}]
        (recipes/f"catalogue_{r['id'].lower()}.json").write_text(json.dumps(definition,indent=2)+'\n',encoding='utf-8',newline='\n')
print(f"Exported {len(data['catalogue'])} catalogue entries and canonical recipes.")
glass=next(r for r in data['recipes'] if r['id']=='PM-10')
(recipes/'boroglass.json').write_text(json.dumps({'type':'starfall_blueprint:alloy_mixing','base':{'item':'minecraft:glass'},'base_count':3,'additive':{'item':'starfall_blueprint:boron'},'additive_count':1,'result':{'id':'starfall_blueprint:boroglass','count':3},'duration':300},indent=2)+'\n',encoding='utf-8',newline='\n')
