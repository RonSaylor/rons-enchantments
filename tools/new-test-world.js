// Usage: node new-test-world.js <source level.dat> <new level.dat> <world name>
// Clones a world's settings (seed, game rules) into a fresh world: new name, no player, time 0, cheats on.
// Make a fresh copy of a world's level.dat: new name, no player, time reset, commands on.
const fs = require("fs"), zlib = require("zlib");
const [src, dst, name] = process.argv.slice(2);
const buf = zlib.gunzipSync(fs.readFileSync(src));
let o = 0;
function str() { const n = buf.readUInt16BE(o); o += 2; const s = buf.toString("utf8", o, o + n); o += n; return s; }
function payload(t) {
  switch (t) {
    case 1: return buf.readInt8(o++);
    case 2: { const v = buf.readInt16BE(o); o += 2; return v; }
    case 3: { const v = buf.readInt32BE(o); o += 4; return v; }
    case 4: { const v = buf.readBigInt64BE(o); o += 8; return v; }
    case 5: { const v = buf.readFloatBE(o); o += 4; return v; }
    case 6: { const v = buf.readDoubleBE(o); o += 8; return v; }
    case 7: { const n = buf.readInt32BE(o); o += 4; const v = buf.subarray(o, o + n); o += n; return v; }
    case 8: return str();
    case 9: { const et = buf.readInt8(o++); const n = buf.readInt32BE(o); o += 4; const items = []; for (let i = 0; i < n; i++) items.push(payload(et)); return { et, items }; }
    case 10: { const m = new Map(); for (;;) { const tt = buf.readInt8(o++); if (tt === 0) break; const k = str(); m.set(k, { t: tt, v: payload(tt) }); } return m; }
    case 11: { const n = buf.readInt32BE(o); o += 4; const a = []; for (let i = 0; i < n; i++) { a.push(buf.readInt32BE(o)); o += 4; } return a; }
    case 12: { const n = buf.readInt32BE(o); o += 4; const a = []; for (let i = 0; i < n; i++) { a.push(buf.readBigInt64BE(o)); o += 8; } return a; }
  }
  throw new Error("tag " + t);
}
const rootType = buf.readInt8(o++); const rootName = str(); const root = payload(rootType);
const out = [];
function w(b) { out.push(b); }
function wstr(s) { const b = Buffer.from(s, "utf8"); const h = Buffer.alloc(2); h.writeUInt16BE(b.length); w(h); w(b); }
function wp(t, v) {
  const b = Buffer.alloc(8);
  switch (t) {
    case 1: b.writeInt8(v); return w(b.subarray(0, 1));
    case 2: b.writeInt16BE(v); return w(b.subarray(0, 2));
    case 3: b.writeInt32BE(v); return w(b.subarray(0, 4));
    case 4: b.writeBigInt64BE(v); return w(b);
    case 5: b.writeFloatBE(v); return w(b.subarray(0, 4));
    case 6: b.writeDoubleBE(v); return w(b);
    case 7: b.writeInt32BE(v.length); w(b.subarray(0, 4)); return w(Buffer.from(v));
    case 8: return wstr(v);
    case 9: { w(Buffer.from([v.et & 0xff])); b.writeInt32BE(v.items.length); w(Buffer.from(b.subarray(0, 4))); v.items.forEach(i => wp(v.et, i)); return; }
    case 10: { for (const [k, e] of v) { w(Buffer.from([e.t])); wstr(k); wp(e.t, e.v); } return w(Buffer.from([0])); }
    case 11: { b.writeInt32BE(v.length); w(Buffer.from(b.subarray(0, 4))); for (const x of v) { const c = Buffer.alloc(4); c.writeInt32BE(x); w(c); } return; }
    case 12: { b.writeInt32BE(v.length); w(Buffer.from(b.subarray(0, 4))); for (const x of v) { const c = Buffer.alloc(8); c.writeBigInt64BE(x); w(c); } return; }
  }
}
const data = root.get("Data").v;
data.set("LevelName", { t: 8, v: name });
data.delete("Player");
data.set("Time", { t: 4, v: 0n });
data.set("DayTime", { t: 4, v: 0n });
data.set("allowCommands", { t: 1, v: 1 });
console.log("GameType", data.get("GameType")?.v, "seed", data.get("WorldGenSettings")?.v.get("seed")?.v);
w(Buffer.from([rootType])); wstr(rootName); wp(rootType, root);
fs.writeFileSync(dst, zlib.gzipSync(Buffer.concat(out)));
console.log("wrote", dst);
