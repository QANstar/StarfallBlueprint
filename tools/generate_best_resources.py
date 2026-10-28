"""Regenerate only the first Best batch; invoked last by catalogue resource generation."""
from pathlib import Path
import importlib.util
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/starfall_blueprint'
DATA = ROOT / 'src/main/resources/data/starfall_blueprint'
ATLAS = ROOT.parent / 'RE_BFL/04_游戏设定/产线图册'
MOD = 'starfall_blueprint'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')


def main():
    spec = importlib.util.spec_from_file_location('best_expansion', ATLAS / 'best_expansion.py')
    best = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(best)
    best.draw_best_art(ATLAS / 'item-art', best.BEST_NAMES)
    for key in best.BEST_NAMES:
        target = ART / 'textures/item' / f'{key}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ATLAS / 'item-art' / f'{key}.png', target)
        if key != 'best_workbench':
            model = dict(parent='minecraft:item/handheld' if key == 'best_cutter' else 'minecraft:item/generated',
                         textures=dict(layer0=f'{MOD}:item/{key}'))
            write(ART / 'models/item' / f'{key}.json', model)
    armor = ART / 'textures/models/armor/best_frame_layer_2.png'
    armor.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ATLAS / 'item-art/best_frame_layer_2.png', armor)
    # Purpose-built open workbench geometry, with a vice, control face and tool shelf.
    faces = {face: dict(texture='#surface', uv=[0,0,16,16]) for face in ('north','south','east','west','up','down')}
    def box(start, end, texture='surface'):
        return dict(from_=start, to=end, faces={face: dict(data, texture='#'+texture) for face, data in faces.items()})
    elements = [box([0,10,0],[16,12,16], 'top'),
                box([1,0,1],[4,10,4]), box([12,0,1],[15,10,4]),
                box([1,0,12],[4,10,15]), box([12,0,12],[15,10,15]),
                box([3,2,3],[13,4,14]), box([3,5,1],[13,9,3], 'control'),
                box([2,12,4],[6,14,9]), box([3,14,5],[5,16,8])]
    for element in elements:
        element['from'] = element.pop('from_')
    write(ART / 'models/block/best_workbench.json', dict(
        parent='minecraft:block/block', ambientocclusion=True,
        textures=dict(surface=f'{MOD}:block/preparation_side', top=f'{MOD}:block/preparation_top',
                      control=f'{MOD}:item/best_workbench', particle=f'{MOD}:block/preparation_side'), elements=elements))
    write(ART / 'models/item/best_workbench.json', dict(parent=f'{MOD}:block/best_workbench'))
    write(ART / 'blockstates/best_workbench.json', dict(variants={
        f'facing={face}': dict(model=f'{MOD}:block/best_workbench', y=rotation)
        for face, rotation in [('north',0),('east',90),('south',180),('west',270)]}))
    write(DATA / 'loot_table/blocks/best_workbench.json', dict(type='minecraft:block', pools=[dict(
        rolls=1, conditions=[dict(condition='minecraft:survives_explosion')],
        entries=[dict(type='minecraft:item', name=f'{MOD}:best_workbench')])]))
    path = ROOT / 'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(path.read_text(encoding='utf-8'))
    tag['values'] = list(dict.fromkeys(tag['values']+[f'{MOD}:best_workbench']))
    write(path, tag)
    for lang, index in [('zh_cn',0),('en_us',1)]:
        path = ART / 'lang' / f'{lang}.json'
        names = json.loads(path.read_text(encoding='utf-8'))
        for key, pair in best.BEST_NAMES.items():
            prefix = 'block' if key == 'best_workbench' else 'item'
            names[f'{prefix}.{MOD}.{key}'] = pair[index]
            if key == 'best_workbench':
                names.pop(f'item.{MOD}.{key}', None)
        write(path, names)
    (ROOT / 'docs/BEST_ENGINEERING.md').write_text(best.BEST_DOC, encoding='utf-8', newline='\n')
    print('Generated three Best textures/models/names, public bench block resources and manual assembly documentation.')


if __name__ == '__main__':
    main()
