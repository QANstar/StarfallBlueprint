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
  satellite: ['G G/CRC/IFI', { G: 'glass_pane', C: 'copper_ingot', R: 'comparator', I: 'iron_ingot', F: 'firework_rocket' }],
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
}

const textures = { particle: mc('block/quartz_block_bottom'), shell: mc('block/quartz_block_bottom'),
  frame: mc('block/polished_deepslate'), circuit: mc('block/oxidized_copper'),
  resonant: mc('block/amethyst_block'), emitter: mc('block/sea_lantern'), vent: mc('block/observer_top') };
const feet = [
  part([0, 0, 0], [32, 3, 32], 'frame', 'shell'),
  part([1, 3, 1], [31, 5, 31], 'shell', 'frame'),
  part([3, 5, 3], [29, 7, 29], 'frame')
];
const corners = [[2, 2], [2, 26], [26, 2], [26, 26]];
const large = {
  awakener: [...feet, ...corners.flatMap(([x, z]) => [
    part([x, 5, z], [x + 4, 29, z + 4], 'shell'),
    part([x, 10, z], [x + 4, 12, z + 4], 'frame'),
    part([x, 23, z], [x + 4, 25, z + 4], 'emitter')
  ]),
  part([7, 7, 7], [25, 10, 25], 'shell', 'circuit'),
  part([9, 10, 9], [23, 12, 23], 'frame', 'emitter'),
  part([12, 12, 12], [20, 24, 20], 'resonant'),
  part([11, 15, 11], [21, 16, 21], 'emitter'),
  part([11, 20, 11], [21, 21, 21], 'emitter'),
  part([8, 25, 8], [24, 27, 24], 'circuit', 'emitter'),
  part([1, 28, 1], [31, 31, 6], 'shell'), part([1, 28, 26], [31, 31, 31], 'shell'),
  part([1, 28, 6], [6, 31, 26], 'shell'), part([26, 28, 6], [31, 31, 26], 'shell')],
  compute_unit: [...feet,
    part([3, 7, 3], [29, 27, 29], 'frame', 'vent'),
    ...[8, 12, 16, 20, 24].flatMap(y => [
      part([2, y, 5], [30, y + 1, 27], 'shell'),
      part([5, y, 2], [27, y + 1, 30], 'shell'),
      part([9, y + 1, 2], [23, y + 2, 3], y === 16 ? 'emitter' : 'circuit'),
      part([9, y + 1, 29], [23, y + 2, 30], y === 16 ? 'emitter' : 'circuit')
    ]),
    part([1, 27, 1], [31, 29, 31], 'shell', 'frame'),
    part([5, 29, 5], [27, 31, 27], 'vent', 'vent'),
    part([12, 31, 12], [20, 32, 20], 'circuit', 'emitter')]
};
for (const [key, elements] of Object.entries(models)) {
  const name = 'esper_' + key, block = `${mod}:block/${name}`;
  const variants = { 'part=0': { model: block } };
  json(`${asset}/models/block/${name}`, { parent: mc('block/block'), textures, elements });
  if (large[key]) {
    for (let i = 0; i < 8; i++) {
      const offset = [(i & 1) * 16, ((i >> 2) & 1) * 16, ((i >> 1) & 1) * 16];
      const elements = large[key].flatMap(box => {
        const from = box.from.map((n, a) => Math.max(0, n - offset[a]));
        const to = box.to.map((n, a) => Math.min(16, n - offset[a]));
        if (from.some((n, a) => to[a] <= n)) return [];
        const faces = Object.fromEntries(Object.entries(box.faces).filter(([face]) => {
          const a = ['west', 'east'].includes(face) ? 0 : ['down', 'up'].includes(face) ? 1 : 2;
          return ['west', 'down', 'north'].includes(face) ? box.from[a] >= offset[a] : box.to[a] <= offset[a] + 16;
        }));
        return [{ from, to, faces }];
      });
      variants['part=' + (i + 1)] = { model: block + '_' + (i + 1) };
      json(`${asset}/models/block/${name}_${i + 1}`, { parent: mc('block/block'), textures, elements });
    }
  } else {
    for (let i = 1; i <= 8; i++) variants['part=' + i] = { model: block };
  }
  json(`${asset}/blockstates/${name}`, { variants });
  json(`${asset}/models/item/${name}`, { parent: block });
  json(`${data}/loot_table/blocks/${name}`, { type: mc('block'), pools: [{ rolls: 1,
    entries: [{ type: mc('item'), name: id(key) }], conditions: [{ condition: mc('survives_explosion') }] }] });
}
for (const key of ['satellite', 'retuner', 'maximizer']) {
  const name = 'esper_' + key;
  json(`${asset}/models/item/${name}`, { parent: mc('item/generated'), textures: { layer0: `${mod}:item/${name}` } });
}
json(`${asset}/models/item/esper_terminal`, { parent: `${mod}:item/hj_terminal` });
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
icon('maximizer', `
................
......KKKK......
.....KWWWWK.....
....KWCGCWK.....
...KWCGGGCWK....
..KWCGGGGGCWK...
..KCGGWGWGGCK...
.KWGGWGGGWGGWK..
.KWGGWGGGWGGWK..
..KCGGWGWGGCK...
..KWCGGGGGCWK...
...KWCGGGCWK....
....KWWWWWK.....
.....KSSSK......
......KKK.......
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
