"""Rebuild the non-consumed resonance foundations from canonical source metadata."""
from pathlib import Path
import hashlib
import json
import random
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ART=RES/'assets/starfall_blueprint'
MOD='starfall_blueprint'

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def main():
    catalogue=json.loads((ART/'catalogue.json').read_text(encoding='utf-8'))
    bases={key:value for key,value in catalogue['items'].items() if 'resonance_source' in value or 'phase_matrix_output' in value}
    for key,definition in bases.items():
        matrix='phase_matrix_output' in definition
        source=definition['phase_matrix_output'] if matrix else definition['resonance_source'].split(':',1)[1]
        rng=random.Random(int.from_bytes(hashlib.sha256(source.encode()).digest()[:8],'big'))
        dark=any(word in source for word in ('deepslate','blackstone','basalt','obsidian','debris'))
        nether=any(word in source for word in ('nether','debris'))
        base=(57,59,65) if dark else (115,70,61) if nether else (112,117,114)
        image=Image.new('RGBA',(32,32));pixels=image.load()
        for y in range(32):
            for x in range(32):
                shade=rng.randrange(-15,16)
                pixels[x,y]=tuple(max(0,min(255,c+shade)) for c in base)+(255,)
        draw=ImageDraw.Draw(image)
        accent=next((color for word,color in [('diamond','#65d4d2'),('emerald','#62bf78'),('redstone','#cf4d4b'),('lapis','#566bc8'),('gold','#d8b451'),('iron','#c9a18b'),('copper','#cc936b'),('rutile','#bd805e'),('borate','#d0c5a3'),('meteor','#70c5bb'),('coal','#292d35')] if word in source),'#a1aaa3')
        for x,y in ((5,5),(23,5),(5,23),(23,23)):
            draw.rectangle((x-2,y-2,x+3,y+3),fill=accent,outline='#34424a')
        draw.rectangle((0,0,31,31),outline='#293c43',width=2)
        draw.rectangle((2,2,29,29),outline='#a1ac9b')
        draw.line((0,15,31,15),fill='#365b60',width=2)
        draw.line((15,0,15,31),fill='#365b60',width=2)
        draw.polygon(((15,8),(23,15),(15,23),(8,15)),fill='#253d49',outline='#9ad8ce')
        draw.polygon(((15,11),(19,15),(15,20),(12,15)),fill='#69b9b3')
        draw.line((14,12,14,17),fill='#b8efe1')
        if matrix:
            draw.rectangle((1,1,30,30),outline='#d6b36d',width=2)
            draw.rectangle((5,5,26,26),outline='#35536b',width=2)
            for x,y in ((4,4),(25,4),(4,25),(25,25)):
                draw.rectangle((x,y,x+2,y+2),fill='#e2cea1')
            draw.line((9,23,22,23),fill='#d6b36d',width=2)
        path=ART/'textures/block'/f'{key}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
        write(ART/'models/block'/f'{key}.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'{MOD}:block/{key}'}})
        write(ART/'models/item'/f'{key}.json',{'parent':f'{MOD}:block/{key}'})
        write(ART/'blockstates'/f'{key}.json',{'variants':{'':{'model':f'{MOD}:block/{key}'}}})
        write(RES/'data'/MOD/'loot_table/blocks'/f'{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'{MOD}:{key}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for tag in ('mineable/pickaxe','needs_iron_tool'):
        path=RES/'data/minecraft/tags/block'/f'{tag}.json'
        data=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace':False,'values':[]}
        data['values']=list(dict.fromkeys(data['values']+[f'{MOD}:{key}' for key in bases]))
        write(path,data)
    for key in ('ore_meteor_phase','deepslate_ore_meteor_phase','rutile_ore','borate_ore'):
        write(ART/'models/item'/f'{key}.json',{'parent':f'{MOD}:block/{key}'})
    for lang in ('zh_cn','en_us'):
        path=ART/'lang'/f'{lang}.json';names=json.loads(path.read_text(encoding='utf-8'))
        for key,definition in bases.items():
            names[f'block.{MOD}.{key}']=definition['name'] if lang=='zh_cn' else key.replace('_',' ').title()
        names[f'block.{MOD}.quarry']='地脉共振仪' if lang=='zh_cn' else 'Geological Resonator'
        names[f'gui.{MOD}.industry.state.10']='需要四块同种共振基底' if lang=='zh_cn' else 'Needs four identical resonance bases'
        names[f'gui.{MOD}.industry.state.11']='共振仪前方需要留空' if lang=='zh_cn' else 'Clear the two front work spaces'
        for index,zh,en in [(12,'目标区域禁止钻采','Extraction denied at target'),(13,'需要广谱共振升级组件','Needs broad-spectrum resonance upgrade'),(14,'旧模组矿基底已退役，请使用凝矿阵','Old mod ore bases retired; use a phase array')]:
            names[f'gui.{MOD}.industry.state.{index}']=zh if lang=='zh_cn' else en
        write(path,names)
    print(f'Generated {len(bases)} resonance foundation blocks, drops, ore block items and names.')

if __name__=='__main__':main()
