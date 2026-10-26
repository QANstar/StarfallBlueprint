"""Final override: a genuine two-layer 2x2x2 phase condensation array."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
from generate_industrial_models import element, exposed_faces, FACE_AXES

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/starfall_blueprint'
MOD = 'starfall_blueprint'
KIND = 'phase_extractor'


def write(relative, data):
    path = ART / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def geometry(active):
    boxes = []
    def box(a, b, material):
        boxes.append(element(a, b, material))
    box((0, 0, 0), (32, 3, 32), 'dark')
    box((1, 3, 1), (31, 5, 31), 'steel')
    box((1, 29, 1), (31, 32, 31), 'steel')
    for x in (1, 28):
        for z in (1, 28):
            box((x, 5, z), (x+3, 29, z+3), 'frame')
    # A suspended reaction core bridges the two coil levels; there is no drill.
    box((12, 6, 12), (20, 27, 20), 'core_on' if active else 'core')
    for y in (8, 21):
        for dy in (0, 3):
            box((5, y+dy, 5), (27, y+dy+2, 8), 'coil')
            box((5, y+dy, 24), (27, y+dy+2, 27), 'coil')
            box((5, y+dy, 8), (8, y+dy+2, 24), 'coil')
            box((24, y+dy, 8), (27, y+dy+2, 24), 'coil')
    for z in (3, 27):
        box((8, 15, z), (24, 18, z+2), 'frame')
    box((3, 5, 0), (12, 12, 3), 'frame')
    box((4, 6, 0), (11, 11, .2), 'screen')
    box((22, 5, 0), (28, 7, 2), 'core_on' if active else 'core')
    return exposed_faces(boxes)


def split(parts, part):
    origin = ((part & 1)*16, (part >> 2)*16, ((part >> 1) & 1)*16)
    clipped = []
    for piece in parts:
        a = [max(piece['from'][i], origin[i]) for i in range(3)]
        b = [min(piece['to'][i], origin[i]+16) for i in range(3)]
        if any(b[i] <= a[i] for i in range(3)):
            continue
        faces = {}
        for face, value in piece['faces'].items():
            axis, positive, _, _ = FACE_AXES[face]
            if abs((b if positive else a)[axis] - piece['to' if positive else 'from'][axis]) < 1e-6:
                faces[face] = value
        if faces:
            clipped.append({'from': [a[i]-origin[i] for i in range(3)],
                            'to': [b[i]-origin[i] for i in range(3)], 'faces': faces})
    return clipped


def main():
    palette = {'dark': '#263642', 'steel': '#8dacae', 'frame': '#365d75',
               'coil': '#bd8556', 'core': '#416f7d', 'core_on': '#9af0dc', 'screen': '#295558'}
    for name, color in palette.items():
        image = Image.new('RGBA', (32, 32), color)
        draw = ImageDraw.Draw(image)
        draw.rectangle((0, 0, 31, 31), outline='#1d303d')
        draw.line((1, 1, 30, 1), fill='#c7d6ca')
        if name == 'coil':
            for y in range(3, 30, 5):
                draw.line((1, y, 30, y), fill='#efd19c', width=2)
                draw.line((1, y+2, 30, y+2), fill='#664b42')
        elif name.startswith('core'):
            draw.polygon(((16, 3), (28, 16), (16, 29), (3, 16)), outline='#c8fff0', width=2)
            draw.line((16, 3, 16, 29), fill='#7acfc9', width=2)
        elif name == 'screen':
            for y, width in ((7, 18), (15, 11), (23, 20)):
                draw.line((5, y, 5+width, y), fill='#b1efc2', width=2)
        path = ART / 'textures/block/phase_array' / f'{name}.png'
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
    textures = {name: f'{MOD}:block/phase_array/{name}' for name in palette}
    textures['particle'] = textures['frame']
    for active in (False, True):
        pieces = geometry(active)
        for part in range(8):
            write(f'models/block/{KIND}_{part}{"_on" if active else ""}.json',
                  {'parent': 'minecraft:block/block', 'textures': textures, 'elements': split(pieces, part)})
        # Item geometry remains complete and within Minecraft's allowed element coordinates.
        whole = [{**p, 'from': [p['from'][0]-8, p['from'][1]-8, p['from'][2]-8],
                  'to': [p['to'][0]-8, p['to'][1]-8, p['to'][2]-8]} for p in pieces]
        write(f'models/block/{KIND}{"_on" if active else ""}.json',
              {'parent': 'minecraft:block/block', 'textures': textures, 'elements': whole,
               'display': {'gui': {'rotation': [25, 225, 0], 'scale': [.38, .38, .38]},
                           'ground': {'scale': [.22, .22, .22]},
                           'thirdperson_righthand': {'scale': [.28, .28, .28]}}})
    write(f'blockstates/{KIND}.json', {'variants': {
        f'facing={face},lit={str(active).lower()},part={part}':
        {'model': f'{MOD}:block/{KIND}_{part}{"_on" if active else ""}', 'y': rotation}
        for face, rotation in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]
        for active in (False, True) for part in range(8)}})
    write(f'models/item/{KIND}.json', {'parent': f'{MOD}:block/{KIND}'})
    print('Generated phase array: 64 variants, 16 split models and complete two-layer item model.')


if __name__ == '__main__':
    main()
