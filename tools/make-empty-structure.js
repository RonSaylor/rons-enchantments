// Writes an empty (all-air) structure template used as the game test arena.
// Usage: node make-empty-structure.js <out.nbt> <sizeX> <sizeY> <sizeZ>
// DataVersion 3955 = Minecraft 1.21.1 (SharedConstants.WORLD_VERSION).
const fs = require('fs');
const zlib = require('zlib');
const [out, sx, sy, sz] = process.argv.slice(2);
const parts = [];
const u8 = (v) => parts.push(Buffer.from([v]));
const i16 = (v) => { const b = Buffer.alloc(2); b.writeInt16BE(v); parts.push(b); };
const i32 = (v) => { const b = Buffer.alloc(4); b.writeInt32BE(v); parts.push(b); };
const str = (s) => { const b = Buffer.from(s, 'utf8'); i16(b.length); parts.push(b); };
const TAG_END = 0, TAG_INT = 3, TAG_STRING = 8, TAG_LIST = 9, TAG_COMPOUND = 10;
const named = (type, name) => { u8(type); str(name); };

named(TAG_COMPOUND, '');
named(TAG_INT, 'DataVersion'); i32(3955);
named(TAG_LIST, 'size'); u8(TAG_INT); i32(3); i32(+sx); i32(+sy); i32(+sz);
named(TAG_LIST, 'palette'); u8(TAG_COMPOUND); i32(1);
  named(TAG_STRING, 'Name'); str('minecraft:air'); u8(TAG_END);
named(TAG_LIST, 'blocks'); u8(TAG_COMPOUND); i32(0);
named(TAG_LIST, 'entities'); u8(TAG_COMPOUND); i32(0);
u8(TAG_END);
fs.writeFileSync(out, zlib.gzipSync(Buffer.concat(parts)));
console.log(`wrote ${out}`);
