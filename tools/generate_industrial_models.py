"""Purpose-built, original industrial models. No external artwork or copied mod assets.

Run alone, or after the catalogue generator. Models stay within their block space.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/starfall_blueprint'
MOD = 'starfall_blueprint'
CONDUITS = {'cable': 'power', 'busjoint': 'power', 'energy_junction': 'power',
            'pipe': 'fluid', 'fluid_valve': 'fluid',
            'item_duct': 'item', 'item_extractor': 'item', 'item_router': 'item'}
PALETTE = {
    'steel': ('#687b80', '#a1b0ae', '#34474e'), 'ivory': ('#bec5ae', '#e7e8cf', '#778978'),
    'dark': ('#293d43', '#4d656c', '#19282d'), 'copper': ('#ac704a', '#e2ad72', '#624536'),
    'brass': ('#b59954', '#ecce86', '#77633c'), 'rubber': ('#303a3e', '#505b5c', '#19262d'),
    'green': ('#4f8c60', '#8dc982', '#2d6144'), 'red': ('#a54d3d', '#e59363', '#623e37'),
    'water': ('#387b98', '#77c8ca', '#245c7b'), 'soil': ('#684e39', '#91784a', '#483b30'),
    'plank': ('#92704a', '#c1a16c', '#574734'), 'coil': ('#b86b3d', '#f0b476', '#633b2b'),
    'solar': ('#243e64', '#527c9b', '#192d45'), 'screen': ('#213c3e', '#54796e', '#142b31'),
    'lamp': ('#426659', '#759476', '#26453d'), 'lamp_on': ('#a0edb1', '#e0ffd0', '#53ac88'),
    'fire': ('#392d2b', '#654135', '#231f25'), 'fire_on': ('#d36f2b', '#ffe39a', '#8d3e29'),
}

def write(relative, value):
    if relative.startswith('models/block/') and value.get('elements'):
        value = {**value, 'elements': exposed_faces(value['elements'])}
    path = ART / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def texture(name, colors):
    base, light, dark = colors
    im = Image.new('RGBA', (32, 32), base); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 31, 31), outline=dark); d.line((1, 1, 30, 1), fill=light)
    if name == 'solar':
        for y in range(2, 32, 8):
            for x in range(2, 32, 8):
                d.rectangle((x, y, x+5, y+5), fill='#2e517a', outline=light)
                d.line((x+1,y+1,x+4,y+1), fill='#799daf')
    elif name == 'coil':
        for y in range(2,32,4):
            d.line((1,y,30,y),fill=light,width=2);d.line((1,y+2,30,y+2),fill=dark)
    elif name == 'plank':
        for x in (0,8,16,24):
            d.line((x,0,x,31),fill=dark);d.line((x+2,3,x+2,28),fill=light)
    elif name in {'water','soil','green','fire','fire_on'}:
        for n in range(35):
            x,y=(n*17+3)%30+1,(n*11+7)%29+1
            d.rectangle((x,y,min(x+2,30),y+1),fill=light if n%2 else dark)
    elif name.startswith('lamp'):
        for y in range(3,30,5):d.line((2,y,29,y),fill=light)
    else:
        d.line((1,30,30,30),fill=dark)
        for x,y in ((3,3),(28,3),(3,28),(28,28)):
            d.rectangle((x-1,y-1,x+1,y+1),fill=dark);d.point((x-1,y-1),fill=light)
        if name == 'screen':
            for i,width in enumerate((17,11,20)):
                d.line((5,8+i*6,5+width,8+i*6),fill='#8fc9a1',width=2)
    path=ART/'textures/block/industrial'/f'{name}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
    return im

def element(a,b,material,faces=None):
    return {'from':list(a),'to':list(b),'faces':{face:{'texture':'#'+material}for face in (faces or ['down','up','north','south','west','east'])}}

FACE_AXES = {'down':(1,0,0,2),'up':(1,1,0,2),'north':(2,0,0,1),
             'south':(2,1,0,1),'west':(0,0,2,1),'east':(0,1,2,1)}

def subtract_rectangle(rect, cover):
    """Subtract a projected solid from a face without changing its texture scale."""
    a,b,c,d=rect;A,B,C,D=cover
    left,bottom,right,top=max(a,A),max(b,B),min(c,C),min(d,D)
    if left>=right-1e-7 or bottom>=top-1e-7:return [rect]
    return [r for r in [(a,b,left,d),(right,b,c,d),(left,b,right,bottom),(left,top,right,d)]
            if r[2]-r[0]>1e-7 and r[3]-r[1]>1e-7]

def exposed_faces(elements):
    """Discard buried surfaces and resolve coplanar faces with last-part priority.

    Intersecting boxes otherwise emit the same visible plane twice (z-fighting).
    Cutout glass must not hide geometry behind its transparent pixels.
    """
    result=[]
    for i,part in enumerate(elements):
        for face,attributes in part['faces'].items():
            if attributes['texture']=='#rotor':
                result.append({**part,'faces':{face:dict(attributes)}});continue
            axis,positive,u,v=FACE_AXES[face]
            plane=part['to' if positive else 'from'][axis]
            rectangles=[(part['from'][u],part['from'][v],part['to'][u],part['to'][v])]
            for j,other in enumerate(elements):
                if i==j or any(f['texture']in {'#glass','#rotor'} for f in other['faces'].values()):continue
                low,high=other['from'][axis],other['to'][axis]
                # A solid hides the outward side of this face, or a later part
                # owns the identical outward plane. Merely touching edges do not.
                hidden=(low-1e-7<=plane<high-1e-7 if positive else low+1e-7<plane<=high+1e-7)
                duplicate=j>i and abs(plane-(high if positive else low))<1e-7
                if not(hidden or duplicate):continue
                cover=(other['from'][u],other['from'][v],other['to'][u],other['to'][v])
                rectangles=[piece for rect in rectangles for piece in subtract_rectangle(rect,cover)]
                if not rectangles:break
            for a,b,c,d in rectangles:
                start,end=list(part['from']),list(part['to'])
                start[u],start[v],end[u],end[v]=a,b,c,d
                result.append({**part,'from':start,'to':end,'faces':{face:dict(attributes)}})
    return result

def build_machine(key,active=False):
    pieces=[]
    def box(a,b,m):pieces.append(element(a,b,m))
    def base(m='steel'):
        box((1,1,1),(15,3,15),m)
        for x in (2,12):
            for z in (2,12):box((x,0,z),(x+2,2,z+2),'dark')
    def lamp(x=12,y=5,z=0.9):box((x,y,z),(x+1,y+1,z+.5),'lamp_on'if active else'lamp')
    def frame():
        for x in (1,14):
            for z in (1,14):box((x,3,z),(x+1,15,z+1),'steel')
    def tank(x0,x1,z0,z1,height=13,liquid='water'):
        box((x0,3,z0),(x1,height,z1),'ivory')
        box((x0+.5,5,z0-.1),(x1-.5,height-1,z0+.3),liquid)
        box((x0-.3,3,z0-.3),(x1+.3,4,z1+.3),'steel')
        box((x0-.3,height-1,z0-.3),(x1+.3,height,z1+.3),'steel')
    if key=='plant_cultivator':
        base('ivory');box((2,3,2),(14,5,14),'dark');box((3,5,3),(13,6,13),'soil');frame()
        box((1,14,1),(15,16,15),'ivory');box((3,13.8,4),(13,14.1,12),'lamp_on'if active else'lamp')
        for x,z,h in ((5,5,11),(10,9,12),(6,11,10)):
            box((x,6,z),(x+.7,h,z+.7),'green');box((x-1.8,h-2,z-.4),(x+2,h-1,z+1),'green')
            box((x-.4,h-1,z-1.6),(x+.6,h,z+1.7),'green')
        for a,b in [((2,6,1.5),(14,14,1.6)),((2,6,14.4),(14,14,14.5)),((1.5,6,2),(1.6,14,14)),((14.4,6,2),(14.5,14,14))]:box(a,b,'glass')
        box((11,3,.7),(14,5,1.3),'screen');lamp(12,4,.4)
    elif key in {'refiner','bio_processor','g05'}:
        base();tank(2,8,3,12,15,'green'if key!='refiner'else'water')
        if key=='refiner':
            box((10,3,4),(14,12,12),'copper');box((9,8,3),(15,10,13),'steel');box((8,11,6),(11,13,9),'dark')
            box((10,12,6),(13,15,10),'coil')
        elif key=='bio_processor':
            tank(10,14,5,12,11,'green');box((7,12,7),(12,14,9),'copper');box((2,14,5),(8,16,10),'dark')
        else:
            box((9,3,3),(15,9,13),'dark');box((10,5,2),(14,8,14),'coil');box((10,10,9),(12,15,11),'copper')
        lamp()
    elif key=='recycler':
        base('red');box((3,3,4),(13,9,13),'steel');box((2,8,2),(14,12,14),'dark')
        for x in range(3,14,3):box((x,11,3),(x+1.5,13,13),'steel')
        for a,b in [((1,12,1),(3,16,15)),((13,12,1),(15,16,15)),((3,12,1),(13,16,3)),((3,12,13),(13,16,15))]:box(a,b,'red')
        box((4,3,1),(12,6,4),'dark');lamp()
    elif key=='autocrafter':
        base('brass');box((2,3,2),(14,8,14),'dark');box((1,8,1),(15,10,15),'plank')
        for x in (4,7,10):
            for z in (4,7,10):box((x,10,z),(x+2,10.4,z+2),'ivory')
        for x in (2,13):box((x,10,12),(x+1,15,14),'steel')
        box((2,14,11),(14,16,13),'copper');box((7,12,6),(9,15,12),'steel');box((6,11,5),(10,12,8),'brass');lamp()
    elif key=='press':
        base('dark');box((3,3,4),(13,6,13),'steel')
        for x in (1,13):box((x,3,4),(x+2,15,13),'brass')
        box((1,13,4),(15,15,13),'copper');box((4,8,4),(7.5,11,12),'roller_left');box((8.5,8,4),(12,11,12),'roller_right')
        box((3,4,1),(13,6,3),'dark');box((4,5,0),(12,5.4,4),'ivory');lamp(12,4,1)
    elif key in {'auto_furnace','g01'}:
        base('dark');box((2,3,2),(14,13,14),'steel'if key=='auto_furnace'else'copper')
        for x in ((3,9)if key=='auto_furnace'else(4,)):
            width=4 if key=='auto_furnace'else 8
            box((x,5,1.7),(x+width,10,2.2),'dark');box((x+.5,6,1.5),(x+width-.5,9,1.8),'fire_on'if active else'fire')
            box((x+.5,10,1.4),(x+width-.5,10.6,2),'steel')
        box((10,13,10),(13,16,13),'dark');box((2,12,2),(14,14,5),'dark');lamp(12,4,1.5)
    elif key in {'g02','g03','g06'}:
        base();box((6,3,6),(10,10,10),'steel');box((4,5,5),(12,9,11),'copper')
        if key=='g02':
            box((7,8,7),(9,16,9),'steel')
            rotor=element((2.5,5,4.6),(13.5,16,4.6),'rotor',faces=['north','south'])
            for face in rotor['faces'].values():face['uv']=[0,0,16,16]
            pieces.append(rotor);box((6.5,9,4.2),(9.5,12,5.2),'brass')
        else:
            box((1,5,7),(15,7,9),'steel')
            box((3,4,3),(5,13,13),'dark');box((11,4,3),(13,13,13),'dark')
            for y,z in ((3,5),(5,2),(10,2),(13,5),(13,10),(10,13),(5,13)):
                box((4,y,z),(12,y+2,z+1.4),'copper'if key=='g06'else'plank')
            if key=='g06':box((1,3,1),(15,4,15),'fire')
        lamp(7,4,5.7)
    elif key=='g04':
        base();box((6,3,6),(10,10,10),'steel')
        box((1,10,1),(15,11,15),'dark');box((1.5,11,1.5),(14.5,11.3,14.5),'solar')
        for x in (1,14):box((x,10,1),(x+.5,12,15),'steel')
        lamp(7,3,.8)
    elif key=='g07':
        base('dark');box((3,3,3),(13,6,13),'copper')
        for x in (3,7,11):box((x,6,3),(x+1.5,13,13),'coil')
        box((2,13,2),(14,15,14),'steel');box((6,14,5),(10,16,11),'dark');lamp(12,4,2.5)
    elif key=='s02':
        base();box((2,3,2),(14,5,14),'dark')
        for x in (3,9):
            for z in (3,9):
                box((x,5,z),(x+4,13,z+4),'ivory');box((x+.8,13,z+.8),(x+3.2,15,z+3.2),'copper')
        box((3,14,4),(13,15,5),'brass');box((3,14,10),(13,15,11),'brass');lamp(12,4,1.5)
    elif key in {'pump','dew'}:
        base('water');tank(2,7,4,12,11)
        if key=='pump':
            box((8,3,4),(14,8,12),'dark');box((9,4,2),(13,7,14),'coil');box((4,0,6),(6,4,10),'copper');box((4,9,7),(13,11,9),'copper')
        else:
            box((10,3,4),(12,15,6),'copper');box((10,3,11),(12,15,13),'copper')
            for y in (7,10,13):box((8,y,3),(14,y+1,14),'steel')
            box((2,12,2),(8,13,14),'ivory')
        lamp(12,3,1)
    elif key=='supply_crate':
        base('plank');box((1,3,1),(15,14,15),'plank');box((1,13,1),(15,16,15),'steel')
        for x in (3,11):box((x,3,.8),(x+2,15,1.2),'steel')
        box((6,8,.5),(10,10,1),'brass')
    elif key=='terminal':
        base();box((5,3,7),(11,9,13),'steel');box((2,8,5),(14,16,9),'ivory')
        box((3,9,4.8),(13,15,5.1),'screen');box((3,5,2),(13,6,7),'dark');lamp(12,9,4.5)
    elif key=='damper':
        box((0,0,0),(16,16,16),'rubber')
        for i in range(1,16,3):box((i,1,0),(i+1,15,.3),'dark')
    elif key=='preparation_bench':
        base();box((2,3,3),(14,8,14),'dark');box((1,8,1),(15,10,15),'steel')
        box((2,10,3),(8,11,13),'water');box((9,10,6),(13,13,10),'copper');box((10,12,5),(12,16,7),'steel');lamp(12,5,2.5)
    elif key=='alloy_furnace':
        base('dark');box((2,3,2),(14,12,14),'copper');box((1,11,1),(15,13,15),'steel')
        box((4,5,1.8),(12,10,2.2),'dark');box((5,6,1.6),(11,9,1.9),'fire_on'if active else'fire')
        box((5,13,5),(11,14,11),'dark');box((11,13,10),(14,16,13),'dark');lamp(12,4,1.5)
    elif key in {'water_tank','rain_collector'}:
        base();tank(2,14,2,14,14)
        if key=='rain_collector':
            for a,b in [((0,14,0),(16,16,2)),((0,14,14),(16,16,16)),((0,14,2),(2,16,14)),((14,14,2),(16,16,14))]:box(a,b,'steel')
            box((2,14,2),(14,14.5,14),'water')
        else:box((2,14,2),(14,16,14),'ivory')
        box((6,3,0),(10,6,2),'copper')
    elif key=='supply_terminal':
        base('plank');box((2,3,3),(14,7,14),'plank');box((6,7,10),(10,14,12),'steel')
        box((2,11,8),(14,16,10),'ivory');box((3,12,7.8),(13,15,8.2),'screen')
    elif key=='blueprint_builder':
        base('brass');box((2,3,3),(14,7,14),'dark');box((3,7,5),(13,9,14),'copper')
        box((6,9,6),(10,12,12),'steel');box((5,11,3),(11,15,11),'ivory');box((6,12,0),(10,14,6),'brass')
        box((6.5,12.5,0),(9.5,13.5,.2),'dark');box((3,7,2),(13,8,5),'blueprint')
        box((2,8,1),(3,9,6),'ivory');box((13,8,1),(14,9,6),'ivory');lamp(12,4,2.6)
    else:raise ValueError(key)
    textures={name:f'{MOD}:block/industrial/{name}'for name in [*PALETTE,'glass','blueprint']}
    textures['particle']=textures['steel']
    if key=='g02':textures['rotor']=f'{MOD}:block/motion/wind_'+('on'if active else'idle')
    if key=='press':
        for roller in ('roller_left','roller_right'):textures[roller]=f'{MOD}:block/motion/{roller}_'+('on'if active else'idle')
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':textures,'elements':pieces}

def conduit_models(key,family):
    material='copper' if key=='item_extractor' else {'power':'rubber','fluid':'copper','item':'steel'}[family]
    accent={'power':'brass','fluid':'water','item':'plank'}[family]
    low,high=(6,10)if key=='cable'else(5,11)
    if key=='busjoint':low,high=5,11
    controller=key not in {'cable','pipe','item_duct','busjoint'}
    textures={'body':f'{MOD}:block/industrial/{material}','accent':f'{MOD}:block/industrial/{accent}',
              'dark':f'{MOD}:block/industrial/dark','lamp':f'{MOD}:block/industrial/lamp','particle':f'{MOD}:block/industrial/{material}'}
    def model(elements):return {'parent':'minecraft:block/block','textures':textures,'elements':elements}
    core=[element((low,low,low),(high,high,high),'body')]
    if controller:
        core=[element((4,4,4),(12,12,12),'body'),element((5,12,5),(11,13,11),'accent')]
        if key=='fluid_valve':
            core += [element((6,12,6),(10,16,10),'dark'),element((3,15,7),(13,16,9),'accent'),element((7,15,3),(9,16,13),'accent')]
        else:
            # Raised arrow physically shows the fixed forward direction (north).
            core += [element((7,13,4),(9,13.5,10),'dark'),element((5,13,4),(11,13.5,6),'dark')]
            if key in {'item_router','energy_junction'}:
                core += [element((3,13,7),(13,13.5,9),'dark'),element((3,13,5),(5,13.5,11),'dark'),element((11,13,5),(13,13.5,11),'dark')]
    else:core.append(element((low-.4,6.5,low-.4),(high+.4,9.5,high+.4),'accent'))
    write(f'models/block/{key}.json',model(core))
    arm=[element((low,low,0),(high,high,4 if controller else low),'body'),element((low-.5,low-.5,1),(high+.5,high+.5,3),'accent')]
    write(f'models/block/{key}_arm.json',model(arm))
    display=[*core,*arm,element((low,low,12 if controller else high),(high,high,16),'body'),element((low-.5,low-.5,13),(high+.5,high+.5,15),'accent')]
    write(f'models/block/{key}_inventory.json',model(display))
    multipart=[{'apply':{'model':f'{MOD}:block/{key}'}}]
    # Controller cores rotate with placement; the six branches remain in world space.
    if controller:
        multipart=[{'when':{'facing':face},'apply':{'model':f'{MOD}:block/{key}','y':y}}for face,y in [('north',0),('east',90),('south',180),('west',270)]]
    for face,rotation in {'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270},'up':{'x':270},'down':{'x':90}}.items():
        multipart.append({'when':{face:'true'},'apply':{'model':f'{MOD}:block/{key}_arm',**rotation}})
    write(f'blockstates/{key}.json',{'multipart':multipart})
    write(f'models/item/{key}.json',{'parent':f'{MOD}:block/{key}_inventory'})

def main():
    from generate_machine_motion import main as generate_machine_motion
    generate_machine_motion()
    for name,colors in PALETTE.items():texture(name,colors)
    glass=Image.new('RGBA',(32,32));d=ImageDraw.Draw(glass)
    d.rectangle((0,0,31,31),outline='#8ebbb5',width=2)
    d.line((4,16,16,4),fill='#d9efda',width=2);d.line((5,24,24,5),fill='#adcfc7')
    glass.save(ART/'textures/block/industrial/glass.png')
    paper=Image.new('RGBA',(32,32),'#376887');d=ImageDraw.Draw(paper)
    for n in range(2,32,4):d.line((n,0,n,31),fill='#477b96');d.line((0,n,31,n),fill='#477b96')
    d.rectangle((6,7,24,24),outline='#e6f3d7',width=2);d.rectangle((13,13,24,24),outline='#e6f3d7');d.line((6,17,13,17),fill='#e6f3d7',width=2)
    paper.save(ART/'textures/block/industrial/blueprint.png')
    catalogue=json.loads((ART/'catalogue.json').read_text(encoding='utf-8'))
    machines={k for k in catalogue['items'] if (ART/'blockstates'/f'{k}.json').exists() and k in {
        'plant_cultivator','refiner','recycler','g01','g02','g03','g04','g05','g06','g07','pump','dew','s02','bio_processor','autocrafter','auto_furnace','supply_crate','terminal','damper'}}
    machines.add('press')
    for key in sorted(machines|{'preparation_bench','alloy_furnace','water_tank','rain_collector','supply_terminal','blueprint_builder'}):
        for active in (False,True):write(f'models/block/{key}{"_on"if active else""}.json',build_machine(key,active))
        if key in machines or key in {'preparation_bench','alloy_furnace'}:
            write(f'blockstates/{key}.json',{'variants':{f'facing={face},lit={str(active).lower()}':{'model':f'{MOD}:block/{key}{"_on"if active else""}','y':y}
                for face,y in [('north',0),('east',90),('south',180),('west',270)]for active in (False,True)}})
        elif key=='blueprint_builder':write(f'blockstates/{key}.json',{'variants':{f'facing={face}':{'model':f'{MOD}:block/{key}','y':y}for face,y in [('north',0),('east',90),('south',180),('west',270)]}})
        else:write(f'blockstates/{key}.json',{'variants':{'':{'model':f'{MOD}:block/{key}'}}})
        write(f'models/item/{key}.json',{'parent':f'{MOD}:block/{key}'})
    for key,family in CONDUITS.items():conduit_models(key,family)
    # Liquids render through the fluid renderer, but still need an empty block model.
    for key in ('warm','wastewater','bio','returnwater'):
        write(f'blockstates/{key}_fluid.json',{'variants':{'':{'model':f'{MOD}:block/{key}_fluid'}}})
        write(f'models/block/{key}_fluid.json',{'textures':{'particle':'minecraft:block/water_still'},'elements':[]})
    print(f'Generated {len(machines)+5} distinct machines and {len(CONDUITS)} connected conduits.')

if __name__=='__main__':main()
