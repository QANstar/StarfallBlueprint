"""Validate Water God registrations and all resource file encodings."""
import json
from pathlib import Path
from PIL import Image
from generate_best_progression_resources import NAMES
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/main/resources'
ART = RES/'assets/starfall_blueprint'
def main():
    js=list(RES.rglob('*.json'))
    png=list(RES.rglob('*.png'))
    for path in js: json.loads(path.read_text(encoding='utf-8'))
    for path in png:
        with Image.open(path) as image: image.verify()
    definitions=json.loads((ART/'best_progression.json').read_text(encoding='utf-8'))
    assert set(definitions)==set(NAMES)
    for language in ('zh_cn','en_us'):
        names=json.loads((ART/f'lang/{language}.json').read_text(encoding='utf-8'))
        for key in NAMES:
            assert f'item.starfall_blueprint.{key}' in names, (language,key)
            model=json.loads((ART/f'models/item/{key}.json').read_text(encoding='utf-8'))
            texture=model['textures']['layer0'].split(':',1)[1]
            assert (ART/f'textures/{texture}.png').is_file(), key
    print(f'Validated {len(js)} JSON, {len(png)} PNG and {len(NAMES)} three-platform item references.')
if __name__ == '__main__': main()
