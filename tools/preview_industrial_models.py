"""Render the actual generated cuboid geometry and textures for asset review (Pillow)."""
from pathlib import Path
import json, math
from PIL import Image, ImageDraw, ImageFont, ImageEnhance
from generate_industrial_models import ROOT, ART

LABELS = [
    ('plant_cultivator','植物培养机'),('refiner','公共精制站'),('recycler','拆解回收机'),('bio_processor','生辉处理器'),
    ('autocrafter','自动合成台'),('auto_furnace','并列冶炼炉'),('preparation_bench','整备台'),('alloy_furnace','合金混料炉'),
    ('g01','余烬机组'),('g02','风力发电机'),('g03','水轮发电机'),('g04','光伏发电机'),
    ('g05','生辉发电机'),('g06','熔流发电机'),('g07','地热发电机'),('s02','储能电池组'),
    ('pump','水泵'),('dew','凝露器'),('water_tank','储水罐'),('rain_collector','集雨器'),
    ('cable_inventory','绝缘电缆 · 4/16'),('pipe_inventory','流体管 · 6/16'),('item_duct_inventory','物品导管 · 6/16'),('fluid_valve_inventory','流体阀 · 红石关闭'),
    ('item_extractor_inventory','物品抽取器 · 固定流向'),('item_router_inventory','物品分流器'),('terminal','检测终端'),('supply_crate','补给箱')]

def font(size):
    for path in ['C:/Windows/Fonts/msyh.ttc','C:/Windows/Fonts/simhei.ttf']:
        if Path(path).exists():return ImageFont.truetype(path,size)
    return ImageFont.load_default()

def render(key,scale=9):
    model=json.loads((ART/f'models/block/{key}.json').read_text(encoding='utf-8'))
    canvas=Image.new('RGBA',(250,232));polygons=[]
    def project(p):
        x,y,z=p;return ((x+z-16)*.78*scale+125,(x-z)*.36*scale-y*.84*scale+170)
    for element in model['elements']:
        x,y,z=element['from'];X,Y,Z=element['to']
        faces={
            'up':([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],1.12),
            'north':([(x,Y,z),(X,Y,z),(X,y,z),(x,y,z)],.87),
            'east':([(X,Y,z),(X,Y,Z),(X,y,Z),(X,y,z)],.68),
        }
        for face,(vertices,brightness)in faces.items():
            if face not in element['faces']:continue
            tex=model['textures'][element['faces'][face]['texture'][1:]]
            texture=Image.open(ART/'textures'/f'{tex.split(":")[1]}.png').convert('RGBA')
            uv=element['faces'][face].get('uv',{'up':(x,z,X,Z),'north':(16-X,16-Y,16-x,16-y),'east':(16-Z,16-Y,16-z,16-y)}[face])
            texture=texture.crop(tuple(value*2 for value in uv)).resize((32,32),Image.Resampling.NEAREST)
            polygons.append((sum(p[0]-p[2]+p[1]*.7 for p in vertices)/4,[project(p)for p in vertices],texture,brightness))
    for _,points,texture,brightness in sorted(polygons,key=lambda p:p[0]):
        # The two projected edge vectors form an affine texture mapping.
        p0,p1,p2,p3=points
        ax,ay=p1[0]-p0[0],p1[1]-p0[1];bx,by=p3[0]-p0[0],p3[1]-p0[1]
        det=ax*by-ay*bx
        if abs(det)<.01:continue
        coefficients=(32*by/det,-32*bx/det,32*(bx*p0[1]-by*p0[0])/det,
                      -32*ay/det,32*ax/det,32*(ay*p0[0]-ax*p0[1])/det)
        layer=texture.transform(canvas.size,Image.Transform.AFFINE,coefficients,resample=Image.Resampling.NEAREST)
        layer=ImageEnhance.Brightness(layer).enhance(brightness)
        mask=Image.new('L',canvas.size);ImageDraw.Draw(mask).polygon(points,fill=255)
        mask=Image.composite(layer.getchannel('A'),Image.new('L',canvas.size),mask)
        canvas.paste(layer,(0,0),mask)
    return canvas

def main():
    width,height=1120,70+7*260
    sheet=Image.new('RGB',(width,height),'#e5e7dc');d=ImageDraw.Draw(sheet)
    d.rectangle((0,0,width,65),fill='#293f43');d.text((24,16),'星落蓝图 · 按用途重做机器与管线',font=font(25),fill='#f2efd9')
    for i,(key,label)in enumerate(LABELS):
        x=16+(i%4)*276;y=72+(i//4)*260
        d.rounded_rectangle((x,y,x+264,y+248),8,fill='#f5f4e9',outline='#c7cdbd')
        sheet.paste(render(key),(x+7,y-5),render(key))
        d.text((x+12,y+213),label,font=font(17),fill='#344d48')
    out=ROOT/'docs/industrial-redesign.png';sheet.save(out);print(out)

if __name__=='__main__':main()
