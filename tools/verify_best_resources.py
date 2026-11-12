"""Validate current Best resources without mirroring Java or legacy implementation details."""
from generate_best_progression_resources import NAMES
from verify_best_progression_resources import main as verify_resources, ART
import json

def main():
    verify_resources()
    catalogue=json.loads((ART/'catalogue.json').read_text(encoding='utf-8'))
    assert 'best_heavy_mech' not in catalogue['items'], 'Removed chassis must not return to the catalogue'
    for key in ('best_mech','best_mech_heavy_drill_arm','best_mech_heavy_excavator_arm'):
        assert key in catalogue['items'], key
        model=json.loads((ART/f'models/item/{key}.json').read_text(encoding='utf-8'))
        for texture in model.get('textures',{}).values():
            if texture.startswith('starfall_blueprint:'):
                assert (ART/('textures/'+texture.split(':',1)[1]+'.png')).is_file(), (key,texture)
    for lang in ('zh_cn','en_us'):
        names=json.loads((ART/f'lang/{lang}.json').read_text(encoding='utf-8'))
        assert not any('best_heavy_mech' in k or 'best_heavy_engineering_mech' in k or 'advanced_engineering' in k for k in names)
    print('Current three-chassis resources and retained engineering accessories validated.')
if __name__ == '__main__': main()
