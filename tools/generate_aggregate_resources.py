"""Native pixel materials and split 2x2 geometry for quarry and roller crusher."""
from pathlib import Path
import json
from PIL import Image,ImageDraw
from generate_industrial_models import element,exposed_faces,FACE_AXES
ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'src/main/resources/assets/starfall_blueprint'
MOD='starfall_blueprint'

def write(path,data):
    path=ART/path;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def geometry(kind,active):
    parts=[]
    def box(a,b,material):parts.append(element(a,b,material))
    for x in (1,26):
        for z in (1,26):box((x,0,z),(x+5,6,z+5),'steel')
    box((0,3,0),(32,5,32),'dark')
    box((1,5,1),(31,7,31),'paint')
    if kind=='quarry':
        for x in (3,26):
            for z in (5,24):box((x,6,z),(x+3,14,z+3),'steel')
        for z in (6,24):box((4,12,z),(28,14,z+3),'steel')
        box((13,5,13),(19,14,19),'drill')
        for y,w in ((5,10),(7,8),(9,6)):box((16-w/2,y,16-w/2),(16+w/2,y+1.5,16+w/2),'drill')
        box((11,13,11),(21,16,21),'copper')
        box((1,7,14),(7,12,24),'paint')
        box((5,5,27),(27,8,32),'steel')
        box((7,8,28),(25,9,31),'stone')
    else:
        for x in (1,29):box((x,7,2),(x+2,16,30),'paint')
        for z in (1,29):box((3,12,z),(29,16,z+2),'paint')
        for x in (7,20):
            material='roller_left'if x==7 else'roller_right'
            box((x,6,6),(x+5,12,26),material)
            for z in range(7,26,4):box((x-1,10,z),(x+6,13,z+2),material)
        box((1,6,10),(6,12,23),'copper')
        box((2,10,0),(30,12,2),'hazard')
        box((11,5,26),(21,8,32),'dark')
    box((5,7,0),(12,12,3),'steel')
    box((6,8,0),(11,11,.1),'screen')
    box((13,8,0),(15,10,1),'lamp_on' if active else 'lamp')
    return exposed_faces(parts)

def main():
    from generate_machine_motion import main as generate_machine_motion
    generate_machine_motion()
    materials={'steel':'#667986','dark':'#26343e','copper':'#bd8958','drill':'#85949b','stone':'#777b7c','screen':'#376a73','lamp':'#746851','lamp_on':'#9ce9ac','hazard':'#d2a34d'}
    folder=ART/'textures/block/aggregate';folder.mkdir(parents=True,exist_ok=True)
    for name,color in materials.items():
        im=Image.new('RGBA',(32,32),color);d=ImageDraw.Draw(im)
        d.rectangle((0,0,31,31),outline='#1e2932');d.line((1,1,30,1),fill='#c5d0ca')
        if name in {'drill','hazard'}:
            for x in range(-32,64,8):d.line((x,0,x+32,32),fill='#36444f' if name=='drill' else '#282d30',width=3)
        elif name=='stone':
            for n in range(45):d.rectangle(((n*11)%31,(n*17)%31,(n*11)%31+1,(n*17)%31+1),fill='#a6a39b' if n%2 else '#565f66')
        elif name=='screen':
            for y,w in [(7,15),(15,21),(23,10)]:d.line((5,y,5+w,y),fill='#ace5bb',width=2)
        else:
            for x,y in ((3,3),(28,3),(3,28),(28,28)):d.rectangle((x-1,y-1,x+1,y+1),fill='#28363e');d.point((x,y),fill='#d0d6cc')
        im.save(folder/f'{name}.png')
    for kind,paint in [('quarry','#3f777f'),('crusher','#a76b3a')]:
        im=Image.new('RGBA',(32,32),paint);d=ImageDraw.Draw(im);d.rectangle((0,0,31,31),outline='#22323b');d.line((1,1,30,1),fill='#c6c8ad');d.line((4,9,27,9),fill='#273d45');d.line((4,23,27,23),fill='#273d45');im.save(folder/f'{kind}_paint.png')
        tex={k:f'{MOD}:block/aggregate/{k}'for k in materials};tex['paint']=f'{MOD}:block/aggregate/{kind}_paint';tex['particle']=tex['paint']
        for active in (False,True):
            tex={**tex,'drill':f'{MOD}:block/motion/quarry_drill_'+('on'if active else'idle')}
            for roller in ('roller_left','roller_right'):tex[roller]=f'{MOD}:block/motion/{roller}_'+('on'if active else'idle')
            parts=geometry(kind,active)
            for part in range(4):
                ox=(part&1)*16;oz=(part>>1)*16;clipped=[]
                for p in parts:
                    a=[max(p['from'][0],ox),p['from'][1],max(p['from'][2],oz)];b=[min(p['to'][0],ox+16),p['to'][1],min(p['to'][2],oz+16)]
                    if any(b[i]<=a[i]for i in range(3)):continue
                    faces={}
                    for face,value in p['faces'].items():
                        axis,positive,_,_=FACE_AXES[face]
                        if abs((b if positive else a)[axis]-p['to'if positive else'from'][axis])<1e-6:faces[face]=value
                    if faces:clipped.append({'from':[a[0]-ox,a[1],a[2]-oz],'to':[b[0]-ox,b[1],b[2]-oz],'faces':faces})
                write(Path(f'models/block/{kind}_{part}{"_on"if active else""}.json'),{'parent':'minecraft:block/block','textures':tex,'elements':clipped})
            if not active:
                whole=[]
                for p in parts:whole.append({**p,'from':[p['from'][0]-8,p['from'][1],p['from'][2]-8],'to':[p['to'][0]-8,p['to'][1],p['to'][2]-8]})
                write(Path(f'models/block/{kind}.json'),{'parent':'minecraft:block/block','textures':tex,'elements':whole,'display':{'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.45,.45,.45]},'ground':{'scale':[.25,.25,.25]},'thirdperson_righthand':{'scale':[.3,.3,.3]}}})
        write(Path(f'blockstates/{kind}.json'),{'variants':{f'facing={face},lit={str(active).lower()},part={part}':{'model':f'{MOD}:block/{kind}_{part}{"_on"if active else""}','y':rot}for face,rot in [('north',0),('east',90),('south',180),('west',270)]for active in (False,True)for part in range(4)}})
        write(Path(f'models/item/{kind}.json'),{'parent':f'{MOD}:block/{kind}'})
        icon=Image.new('RGBA',(32,32));d=ImageDraw.Draw(icon)
        d.polygon([(2,12),(19,5),(30,12),(30,25),(13,31),(2,24)],fill=paint,outline='#1c2f38');d.polygon([(2,12),(19,5),(30,12),(13,19)],fill='#a6b6b8');d.line((3,22,13,28,29,23),fill='#293c45',width=3)
        if kind=='quarry':d.rectangle((13,8,18,20),fill='#69747e');d.rectangle((10,15,21,18),fill='#d4b16a');d.line((11,7,23,12),fill='#486c75',width=3)
        else:
            for x in (9,17):d.line((x,12,x+6,16),fill='#384752',width=4);d.point((x+1,11),fill='#d4d9c8')
        d.rectangle((16,21,21,24),fill='#83c6b4');(ART/'textures/item').mkdir(parents=True,exist_ok=True);icon.save(ART/'textures/item'/f'{kind}.png')
        for lang in ('zh_cn','en_us'):
            path=ART/'lang'/f'{lang}.json';names=json.loads(path.read_text(encoding='utf-8'));names[f'block.{MOD}.{kind}']=({'quarry':'电动采石机','crusher':'辊式破碎机'}if lang=='zh_cn'else{'quarry':'Electric Quarry','crusher':'Roller Crusher'})[kind]
            for index,zh,en in [(9,'大型机器结构不完整','Incomplete machine footprint'),(10,'需要四格石质地基','Needs four stone foundation blocks'),(11,'采石机前方需要留空','Clear the two front work spaces')]:names[f'gui.{MOD}.industry.state.{index}']=zh if lang=='zh_cn'else en
            path.write_text(json.dumps(names,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    path=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json';tag=json.loads(path.read_text(encoding='utf-8'));tag['values']=list(dict.fromkeys(tag['values']+[f'{MOD}:quarry',f'{MOD}:crusher']));path.write_text(json.dumps(tag,indent=2)+'\n',encoding='utf-8')
    data=ROOT/'src/main/resources/data'/MOD
    for key in ('qm-m01','qm-m02'):
        recipe='catalogue_'+key
        assert (data/'recipe'/f'{recipe}.json').exists(),recipe
        path=data/'advancement/recipes'/f'{recipe}.json';path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps({'criteria':{'materials':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':MOD+':iron_plate'}]}}},'rewards':{'recipes':[MOD+':'+recipe]}},indent=2)+'\n',encoding='utf-8')
    print('Generated two distinct 2x2 machines, 16 split models, item models and localizations.')

if __name__=='__main__':main()
