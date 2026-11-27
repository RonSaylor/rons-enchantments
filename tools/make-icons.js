// Renders the 512x512 mod icons (axe / pickaxe / hoe) with signed-distance shapes and writes PNGs.
// Usage: node tools/make-icons.js <outDir>, then copy <mod>.png to <mod>/src/main/resources/icon.png
const fs = require("fs"), zlib = require("zlib"), path = require("path");
const N = 512, SS = 3;

// ---------- geometry helpers (unit square, y down)
const add = (a, b) => [a[0] + b[0], a[1] + b[1]];
const mul = (a, k) => [a[0] * k, a[1] * k];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1];
const len = a => Math.hypot(a[0], a[1]);
function segDist(p, a, b) { const pa = sub(p, a), ba = sub(b, a); const h = Math.max(0, Math.min(1, dot(pa, ba) / dot(ba, ba))); return len(sub(pa, mul(ba, h))); }
function polySdf(p, v) {
  let d = dot(sub(p, v[0]), sub(p, v[0])), s = 1;
  for (let i = 0, j = v.length - 1; i < v.length; j = i, i++) {
    const e = sub(v[j], v[i]), w = sub(p, v[i]);
    const b = sub(w, mul(e, Math.max(0, Math.min(1, dot(w, e) / dot(e, e)))));
    d = Math.min(d, dot(b, b));
    const c1 = p[1] >= v[i][1], c2 = p[1] < v[j][1], c3 = e[0] * w[1] > e[1] * w[0];
    if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) s = -s;
  }
  return s * Math.sqrt(d);
}
function roundRect(p, half, r) { const q = [Math.abs(p[0] - 0.5) - half + r, Math.abs(p[1] - 0.5) - half + r]; return len([Math.max(q[0], 0), Math.max(q[1], 0)]) + Math.min(Math.max(q[0], q[1]), 0) - r; }
function sparkle(p, c, r) { const x = Math.abs(p[0] - c[0]) / r, y = Math.abs(p[1] - c[1]) / r; return Math.sqrt(x) + Math.sqrt(y) < 1; }

// ---------- tool layout: handle from bottom-left to top-right
const H0 = [0.24, 0.80], H1 = [0.70, 0.34];
const d = mul(sub(H1, H0), 1 / len(sub(H1, H0)));   // along handle, toward the head
const pp = [d[1], -d[0]];                            // perpendicular, pointing up-left
const H = t => add(H0, mul(sub(H1, H0), t));
const P = (base, a, b) => add(add(base, mul(pp, a)), mul(d, b)); // base + a*perp + b*along

const heads = {
  felling: () => { const T = H(0.86); return [P(T, -0.05, 0.07), P(T, 0.06, 0.07), P(T, 0.25, 0.15), P(T, 0.27, 0.04), P(T, 0.25, -0.16), P(T, 0.06, -0.07), P(T, -0.05, -0.07)]; },
  veinbreaking: () => { const T = H(1.0); return [P(T, -0.31, -0.15), P(T, -0.2, -0.01), P(T, -0.1, 0.05), P(T, 0, 0.07), P(T, 0.1, 0.05), P(T, 0.2, -0.01), P(T, 0.31, -0.15), P(T, 0.17, -0.08), P(T, 0, -0.05), P(T, -0.17, -0.08)]; },
  harvesting: () => { const T = H(1.0); return [P(T, -0.05, 0.05), P(T, 0.26, 0.05), P(T, 0.28, -0.13), P(T, 0.19, -0.13), P(T, 0.19, -0.04), P(T, -0.05, -0.05)]; },
};

function shade(p, name) {
  const bg = roundRect(p, 0.5, 0.12);
  if (bg > 0) return [0, 0, 0, 0];
  const t = p[1];
  let col = [76 + (30 - 76) * t, 29 + (27 - 29) * t, 149 + (75 - 149) * t]; // #4c1d95 -> #1e1b4b
  const glow = Math.max(0, 1 - len(sub(p, [0.55, 0.45])) / 0.5) * 0.35;      // soft enchanting glow
  col = col.map((c, i) => c + ([168, 85, 247][i] - c) * glow);
  for (const [c, r] of [[[0.20, 0.22], 0.07], [[0.84, 0.76], 0.055], [[0.62, 0.83], 0.035], [[0.36, 0.40], 0.03]])
    if (sparkle(p, c, r)) col = [233, 213, 255];
  const hd = segDist(p, H0, H1) - 0.035;
  if (hd < 0) col = hd > -0.012 ? [74, 45, 22] : (dot(sub(p, H0), pp) > 0 ? [168, 112, 64] : [133, 86, 46]);
  const sd = polySdf(p, heads[name]());
  if (sd < 0) {
    const lit = dot(sub(p, H1), [-0.6, -0.8]);                               // light from the top-left
    col = sd > -0.014 ? [18, 92, 96] : (lit > 0.02 ? [140, 245, 232] : [70, 205, 200]);
  }
  return [...col, 255];
}

function crc32(buf) { let c, crc = ~0; for (const b of buf) { c = (crc ^ b) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crc = (crc >>> 8) ^ c; } return ~crc >>> 0; }
function chunk(type, data) { const l = Buffer.alloc(4); l.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([l, td, c]); }

const out = process.argv[2];
for (const name of Object.keys(heads)) {
  const raw = Buffer.alloc(N * (N * 4 + 1));
  for (let y = 0; y < N; y++) {
    raw[y * (N * 4 + 1)] = 0;
    for (let x = 0; x < N; x++) {
      const acc = [0, 0, 0, 0];
      for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
        const c = shade([(x + (sx + 0.5) / SS) / N, (y + (sy + 0.5) / SS) / N], name);
        acc[0] += c[0] * c[3]; acc[1] += c[1] * c[3]; acc[2] += c[2] * c[3]; acc[3] += c[3];
      }
      const o = y * (N * 4 + 1) + 1 + x * 4, a = acc[3];
      raw[o] = a ? Math.round(acc[0] / a) : 0; raw[o + 1] = a ? Math.round(acc[1] / a) : 0; raw[o + 2] = a ? Math.round(acc[2] / a) : 0; raw[o + 3] = Math.round(a / (SS * SS));
    }
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(N, 0); ihdr.writeUInt32BE(N, 4); ihdr[8] = 8; ihdr[9] = 6;
  const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
  fs.writeFileSync(path.join(out, name + ".png"), png);
  console.log(name, png.length, "bytes");
}
