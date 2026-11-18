import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

const root = new URL('../src/main/resources/', import.meta.url);
const mod = 'starfall_blueprint';
const asset = `assets/${mod}`;
const data = `data/${mod}`;
function write(path, content) {
  const file = fileURLToPath(new URL(path, root));
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, content);
}
const json = (path, value) => write(`${path}.json`, JSON.stringify(value, null, 2) + '\n');
const id = name => `${mod}:esper_${name}`;
const mc = name => `minecraft:${name}`;
const sides = ['north', 'south', 'west', 'east', 'up', 'down'];
const part = (from, to, texture, cap = texture) => ({
  from, to, faces: Object.fromEntries(sides.map(face => [face, {
    texture: '#' + (['up', 'down'].includes(face) ? cap : texture)
  }]))
});
const base = [
  part([0, 0, 0], [16, 2, 16], 'frame', 'shell'),
  part([1, 2, 1], [15, 4, 15], 'shell', 'frame'),
  part([3, 3, 0], [13, 4, 16], 'circuit')
];
const frame = [
  part([1, 4, 1], [3, 15, 3], 'shell', 'frame'),
  part([13, 4, 1], [15, 15, 3], 'shell', 'frame'),
  part([1, 4, 13], [3, 15, 15], 'shell', 'frame'),
  part([13, 4, 13], [15, 15, 15], 'shell', 'frame')
];
const models = {
  awakener: [...base, ...frame,
    part([3, 5, 3], [13, 7, 13], 'frame', 'circuit'),
    part([5, 7, 5], [11, 12, 11], 'resonant'),
    part([6, 12, 6], [10, 14, 10], 'emitter'),
    part([3, 14, 3], [13, 16, 5], 'shell', 'frame'),
    part([3, 14, 11], [13, 16, 13], 'shell', 'frame'),
    part([3, 14, 5], [5, 16, 11], 'shell', 'frame'),
    part([11, 14, 5], [13, 16, 11], 'shell', 'frame')],
  compute_unit: [...base,
    part([2, 4, 2], [14, 14, 14], 'frame'),
    ...[5, 8, 11].map(y => part([3, y, 1], [13, y + 1, 15], 'circuit')),
    part([1, 13, 1], [15, 15, 15], 'shell', 'vent'),
    part([5, 15, 5], [11, 16, 11], 'frame', 'circuit')],
  launcher: [...base, ...frame,
    part([4, 4, 4], [12, 7, 12], 'frame', 'circuit'),
    part([5, 7, 5], [11, 12, 11], 'circuit', 'frame'),
    ...[[3, 3, 13, 5], [3, 11, 13, 13], [3, 5, 5, 11], [11, 5, 13, 11]]
      .map(([x, z, xx, zz]) => part([x, 12, z], [xx, 14, zz], 'shell', 'emitter'))]
};
const recipes = {
  awakener: ['QAQ/ICI/ORO', { Q: 'smooth_quartz', A: 'amethyst_block', I: 'iron_ingot',
    C: 'comparator', O: 'obsidian', R: 'redstone_block' }],
  compute_unit: ['IRI/QCQ/IRI', { I: 'iron_ingot', R: 'redstone', Q: 'smooth_quartz', C: 'comparator' }],
  launcher: ['I I/ORO/ICI', { I: 'iron_block', O: 'obsidian', R: 'lightning_rod', C: id('compute_unit') }],
  satellite: ['G G/CRC/IFI', { G: 'glass_pane', C: 'copper_ingot', R: 'comparator', I: 'iron_ingot', F: 'firework_rocket' }],
  terminal: [' G /IRI/ICI', { G: 'glass_pane', I: 'copper_ingot', R: 'compass', C: 'redstone' }],
  retuner: [' A /RCR/ E ', { A: 'amethyst_shard', R: 'redstone', C: 'comparator', E: 'echo_shard' }]
};
for (const [key, [shape, items]] of Object.entries(recipes)) {
  const name = 'esper_' + key;
  json(`${data}/recipe/${name}`, { type: mc('crafting_shaped'), category: 'redstone',
    pattern: shape.split('/'), key: Object.fromEntries(Object.entries(items)
      .map(([k, v]) => [k, { item: v.includes(':') ? v : mc(v) }])), result: { id: id(key) } });
  const criterion = key === 'awakener' ? 'amethyst_shard' : 'redstone';
  json(`${data}/advancement/recipes/baiwan/${name}`, { parent: mc('recipes/root'),
    criteria: { materials: { trigger: mc('inventory_changed'), conditions: { items: [{ items: mc(criterion) }] } },
      recipe: { trigger: mc('recipe_unlocked'), conditions: { recipe: id(key) } } },
    requirements: [['materials', 'recipe']], rewards: { recipes: [id(key)] } });
  if (models[key]) {
    json(`${asset}/blockstates/${name}`, { variants: { '': { model: `${mod}:block/${name}` } } });
    json(`${asset}/models/block/${name}`, {
      parent: mc('block/block'),
      textures: { particle: mc('block/quartz_block_bottom'), shell: mc('block/quartz_block_bottom'),
        frame: mc('block/polished_deepslate'), circuit: mc('block/oxidized_copper'),
        resonant: mc('block/amethyst_block'), emitter: mc('block/sea_lantern'), vent: mc('block/observer_top') },
      elements: models[key]
    });
    json(`${asset}/models/item/${name}`, { parent: `${mod}:block/${name}` });
    json(`${data}/loot_table/blocks/${name}`, { type: mc('block'), pools: [{ rolls: 1,
      entries: [{ type: mc('item'), name: id(key) }], conditions: [{ condition: mc('survives_explosion') }] }] });
  } else {
    json(`${asset}/models/item/${name}`, { parent: mc('item/generated'), textures: { layer0: `${mod}:item/${name}` } });
  }
}
const palette = { '.': [0, 0, 0, 0], K: [21, 29, 45, 255], W: [225, 245, 238, 255],
  S: [130, 172, 175, 255], C: [80, 230, 209, 255], B: [53, 111, 135, 255],
  V: [170, 107, 244, 255], G: [251, 185, 108, 255] };
function chunk(type, bytes) {
  const body = Buffer.concat([Buffer.from(type), bytes]);
  let crc = -1;
  for (const value of body) {
    crc ^= value;
    for (let j = 0; j < 8; j++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
  }
  const out = Buffer.alloc(body.length + 8);
  out.writeUInt32BE(bytes.length); body.copy(out, 4); out.writeUInt32BE((~crc) >>> 0, out.length - 4);
  return out;
}
function icon(name, art) {
  const rows = art.trim().split(/\s+/);
  if (rows.length !== 16 || rows.some(row => row.length !== 16)) throw Error('Invalid icon ' + name);
  const pixels = Buffer.from(rows.flatMap(row => [0, ...Array.from(row).flatMap(pixel => palette[pixel])]));
  const header = Buffer.alloc(13);
  header.writeUInt32BE(16, 0); header.writeUInt32BE(16, 4); header[8] = 8; header[9] = 6;
  write(`${asset}/textures/item/esper_${name}.png`, Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header),
    chunk('IDAT', deflateSync(pixels)), chunk('IEND', Buffer.alloc(0))
  ]));
}
icon('terminal', `
................
.........KK.....
.........CK.....
....KKKKKKK.....
...KWSSWSWWK....
...KSKKKKKWK....
...KSKCCCKWK....
...KSKCVCKWK....
...KSKBBCKWK....
...KSKKKKKWK....
...KSWWWSWSK....
...KSGBKCVSK....
...KSSSSSSSK....
....KKKKKKK.....
................
................`);
icon('satellite', `
................
......KKKK......
.....KWWSSK.....
......KCKK......
..KK..KSK..KK...
.KCCKKSWSKKCCK..
.KCBKSWVSBKCBK..
.KCCKSWCSBKCCK..
.KBBKKSWSKKBBK..
..KK..KSK..KK...
......KWK.......
.....KGGSK......
.....KKKKK......
......GGG.......
.......G........
................`);
icon('retuner', `
................
.......KK.......
......KVVK......
.....KVWVVK.....
....KVCVVSVK....
...KVCVVSVVVK...
..KVCVVSVVVVVK..
.KVCVVSVVWVVVVK.
.KVVVSVVWVVVCVK.
..KVSVVWVVVCVK..
...KVVWVVVCVK...
....KVVVVCVK....
.....KVVCVK.....
......KVVK......
.......KK.......
................`);
json(`${data}/tags/block/baiwan_facilities`, { values: Object.keys(models).map(id) });
json(`${data}/dimension/esper_domain`, {
  type: mod + ':esper_domain', generator: { type: mc('flat'),
    settings: { biome: mc('the_void'), features: false, lakes: false, structure_overrides: [], layers: [] } }
});
json(`${data}/dimension_type/esper_domain`, { ultrawarm: false, natural: false,
  piglin_safe: false, respawn_anchor_works: false, bed_works: false, has_raids: false,
  has_skylight: true, has_ceiling: false, coordinate_scale: 1, ambient_light: .35,
  fixed_time: 13000, min_y: 0, height: 256, logical_height: 256,
  infiniburn: '#minecraft:infiniburn_overworld', effects: mc('overworld'),
  monster_spawn_block_light_limit: 0, monster_spawn_light_level: 0 });
write('data/starfall_blueprint_esper/structure/empty.nbt', readFileSync(new URL(`${data}/structure/empty.nbt`, root)));
