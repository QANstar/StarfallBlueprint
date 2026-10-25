"""Shared, low-rate vanilla atlas animations. No entities, renderers or per-frame packets."""
from pathlib import Path
import json,math
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'src/main/resources/assets/starfall_blueprint'
FOLDER=ART/'textures/block/motion'

def save_frames(name,frames):
    size=frames[0].width;strip=Image.new('RGBA',(size,size*len(frames)))
    for index,frame in enumerate(frames):strip.paste(frame,(0,index*size))
    strip.save(FOLDER/f'{name}_on.png');frames[0].save(FOLDER/f'{name}_idle.png')
    (FOLDER/f'{name}_on.png.mcmeta').write_text(json.dumps({'animation':{'width':size,'height':size,'frametime':2,'interpolate':False}},indent=2)+'\n',encoding='utf-8')

def scrolling(name,direction,palette):
    frames=[]
    for frame in range(8):
        im=Image.new('RGBA',(32,32));pixels=im.load()
        for y in range(32):
            for x in range(32):
                band=(x+y+direction*frame*2)%16
                color=palette[0]if band<3 else palette[1]if band<7 else palette[2]
                pixels[x,y]=(*color,255)
        frames.append(im)
    save_frames(name,frames)

def main():
    FOLDER.mkdir(parents=True,exist_ok=True)
    frames=[]
    # Four identical blades repeat after 90 degrees; six frames avoid fourfold duplication.
    for frame in range(6):
        im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
        for blade in range(4):
            angle=math.radians(frame*15+blade*90);co,si=math.cos(angle),math.sin(angle)
            pts=[(round(32+x*co-y*si),round(32+x*si+y*co))for x,y in [(-3,5),(-5,25),(1,28),(4,8)]]
            d.polygon(pts,fill='#d4d7be',outline='#405663');d.line((pts[0],pts[1],pts[2]),fill='#f1efd6',width=2)
        d.ellipse((27,27,37,37),fill='#ab8957',outline='#34464f');frames.append(im)
    save_frames('wind',frames)
    scrolling('quarry_drill',1,[(190,203,206),(62,83,96),(115,136,147)])
    scrolling('roller_left',1,[(178,193,190),(45,64,73),(100,124,133)])
    scrolling('roller_right',-1,[(178,193,190),(45,64,73),(100,124,133)])
    for lang,label in [('zh_cn','电动辊压机'),('en_us','Electric Rolling Press')]:
        path=ART/'lang'/f'{lang}.json';names=json.loads(path.read_text(encoding='utf-8'));names['block.starfall_blueprint.press']=label;path.write_text(json.dumps(names,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    icon=Image.new('RGBA',(32,32));d=ImageDraw.Draw(icon)
    d.polygon([(2,10),(20,4),(30,10),(30,26),(12,31),(2,24)],fill='#6a7e86',outline='#253943');d.polygon([(4,10),(19,6),(28,11),(12,17)],fill='#c1c9ba')
    d.rectangle((5,9,8,23),fill='#b98d54');d.rectangle((24,10,27,25),fill='#b98d54');d.rectangle((5,7,26,10),fill='#8c6945')
    for x in (10,18):d.ellipse((x,12,x+6,20),fill='#354b57',outline='#9eb8b9')
    d.polygon([(5,24),(19,20),(26,23),(12,28)],fill='#d9e5dc',outline='#79929c')
    icon.save(ART/'textures/item/press.png')
    data=ROOT/'src/main/resources/data/starfall_blueprint';recipe='catalogue_pr-m01'
    if (data/'recipe'/f'{recipe}.json').exists():
        path=data/'advancement/recipes'/f'{recipe}.json';path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps({'criteria':{'plate':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'starfall_blueprint:iron_plate'}]}}},'rewards':{'recipes':['starfall_blueprint:'+recipe]}},indent=2)+'\n',encoding='utf-8')
    path=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json';tag=json.loads(path.read_text(encoding='utf-8'));tag['values']=list(dict.fromkeys(tag['values']+['starfall_blueprint:press']));path.write_text(json.dumps(tag,indent=2)+'\n',encoding='utf-8')
    print('Generated four shared 10fps atlas animations, stopped frames and press artwork.')

if __name__=='__main__':main()
