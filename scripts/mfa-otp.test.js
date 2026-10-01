/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Reed-Solomon correction in scripts/lib/mfa-otp.js.
 *
 * With mfa.registration.qrcode.logo.path configured, MfaManagerImpl overlays a
 * logo on the centre of the enrolment QR and relies on QR error correction to
 * carry the hidden modules. The decoder used to drop the EC codewords, so a
 * covered module became corrupt text or a throw. These tests pin the block
 * corrector directly (encode, damage, correct) and end to end: a QR built here
 * from scratch -- an independent encoder, not the decoder run backwards -- is
 * rendered to a PNG, its centre is blanked or flipped like a logo, and it must
 * still decode; damage past the EC capacity must throw, never decode wrongly.
 *
 * The RFC 6238 SHA-1 vectors at the end pin totp() itself, and wrongTotp()
 * is checked against every window the server accepts.
 *
 * Every payload is synthetic. The secret below is a fixed test value, not a
 * credential for any account.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { deflateSync, crc32 } = require('node:zlib');
const { correctBlock, decodeMatrix, decodeQrPng, parseOtpauthUrl, totp, wrongTotp } = require('./lib/mfa-otp');

// ---- an independent GF(256) / RS encoder (QR: 0x11D, roots alpha^0..) ------

const EXP = []; const LOG = [];
for (let i = 0, x = 1; i < 255; i++) { EXP[i] = x; LOG[x] = i; x <<= 1; if (x & 0x100) x ^= 0x11d; }
const mul = (a, b) => (a && b ? EXP[(LOG[a] + LOG[b]) % 255] : 0);

/** Generator coefficients, highest degree first: prod (x - alpha^i), i < ecLength. */
function generator(ecLength) {
  let poly = [1];
  for (let i = 0; i < ecLength; i++) {
    const next = new Array(poly.length + 1).fill(0);
    for (let j = 0; j < poly.length; j++) { next[j] ^= poly[j]; next[j + 1] ^= mul(poly[j], EXP[i]); }
    poly = next;
  }
  return poly;
}

/** The EC codewords for `data`: the remainder of data(x) * x^ecLength mod g(x). */
function rsEncode(data, ecLength) {
  const g = generator(ecLength);
  const remainder = [...data, ...new Array(ecLength).fill(0)];
  for (let i = 0; i < data.length; i++) {
    const factor = remainder[i];
    if (factor) for (let j = 0; j < g.length; j++) remainder[i + j] ^= mul(g[j], factor);
  }
  return remainder.slice(data.length);
}

/** Deterministic pseudo-random bytes so a failure reproduces exactly. */
function rng(seed) {
  let state = seed >>> 0;
  return () => { state = (Math.imul(state, 1664525) + 1013904223) >>> 0; return state >>> 24; };
}

function distinctPositions(next, count, length) {
  const picked = new Set();
  while (picked.size < count) picked.add(next() % length);
  return [...picked];
}

function damage(block, positions, next) {
  const out = block.slice();
  for (const i of positions) out[i] ^= 1 + (next() % 255); // never XOR 0: always a real error
  return out;
}

test('RS encoder agrees with the published QR vector (ISO 18004 annex I, 1-M "01234567")', () => {
  const data = [0x10, 0x20, 0x0c, 0x56, 0x61, 0x80, 0xec, 0x11, 0xec, 0x11, 0xec, 0x11, 0xec, 0x11, 0xec, 0x11];
  assert.deepEqual(rsEncode(data, 10), [0xa5, 0x24, 0xd4, 0xc1, 0xed, 0x36, 0xc7, 0x87, 0x2c, 0x55]);
});

test('an undamaged block is returned unchanged (and as a copy)', () => {
  const data = Array.from(Buffer.from('otpauth://totp/x'));
  const block = [...data, ...rsEncode(data, 10)];
  const out = correctBlock(block, 10);
  assert.deepEqual(out, block);
  assert.notEqual(out, block);
});

for (const [dataLength, ecLength] of [[19, 7], [16, 10], [43, 24], [11, 22], [15, 28], [68, 18]]) {
  test(`corrects every error count up to floor(${ecLength}/2) in a ${dataLength}+${ecLength} block`, () => {
    const next = rng(dataLength * 1000 + ecLength);
    for (let trial = 0; trial < 40; trial++) {
      const data = Array.from({ length: dataLength }, next);
      const block = [...data, ...rsEncode(data, ecLength)];
      for (let errors = 1; errors <= ecLength >> 1; errors++) {
        const broken = damage(block, distinctPositions(next, errors, block.length), next);
        assert.deepEqual(correctBlock(broken, ecLength), block, `${errors} errors, trial ${trial}`);
      }
    }
  });
}

test('errors in the EC codewords themselves, and at both ends of the block, are corrected', () => {
  const data = Array.from(Buffer.from('CARLOS EMR enrolment test vector'));
  const block = [...data, ...rsEncode(data, 16)];
  const next = rng(7);
  const ends = [0, block.length - 1, data.length, data.length - 1];
  assert.deepEqual(correctBlock(damage(block, ends, next), 16), block);
  const ecOnly = Array.from({ length: 8 }, (_, i) => data.length + 2 * i);
  assert.deepEqual(correctBlock(damage(block, ecOnly, next), 16), block);
});

test('more errors than floor(ec/2) throw a clear error and never return a block', () => {
  for (const [dataLength, ecLength] of [[16, 10], [43, 24], [15, 28]]) {
    const next = rng(ecLength);
    for (let trial = 0; trial < 60; trial++) {
      const data = Array.from({ length: dataLength }, next);
      const block = [...data, ...rsEncode(data, ecLength)];
      const errors = (ecLength >> 1) + 1 + (trial % 4);
      const broken = damage(block, distinctPositions(next, errors, block.length), next);
      assert.throws(() => correctBlock(broken, ecLength), /the QR block could not be corrected/,
        `${errors} errors in a ${dataLength}+${ecLength} block, trial ${trial}`);
    }
  }
});

test('the uncorrectable message names the capacity but not the codewords', () => {
  const data = [1, 2, 3, 4, 5, 6, 7, 8];
  const block = [...data, ...rsEncode(data, 4)];
  const broken = damage(block, [0, 1, 2, 3, 4], rng(1));
  assert.throws(() => correctBlock(broken, 4), (error) => {
    assert.match(error.message, /4 EC codewords repair at most 2/);
    return true;
  });
});

// ---- an independent QR encoder (byte mode) for end-to-end cases --------------

// [ecCodewordsPerBlock, [blockCount, dataCodewords], ...] from ISO 18004 table 9.
const PARAMETERS = {
  '1-L': [7, [1, 19]],
  '4-H': [16, [4, 9]],
  '5-M': [24, [2, 43]],
  '5-H': [22, [2, 11], [2, 12]],
  '7-Q': [18, [2, 14], [4, 15]],
  '10-M': [26, [4, 43], [1, 44]],
};
const LEVEL_BITS = { L: 1, M: 0, Q: 3, H: 2 };
const ALIGNMENT = { 1: [], 4: [6, 26], 5: [6, 30], 7: [6, 22, 38], 10: [6, 28, 50] };
const MASK = [
  (r, c) => (r + c) % 2 === 0, (r) => r % 2 === 0, (r, c) => c % 3 === 0, (r, c) => (r + c) % 3 === 0,
  (r, c) => (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0, (r, c) => ((r * c) % 2) + ((r * c) % 3) === 0,
  (r, c) => (((r * c) % 2) + ((r * c) % 3)) % 2 === 0, (r, c) => (((r + c) % 2) + ((r * c) % 3)) % 2 === 0,
];

function bch(value, generatorPoly, generatorDegree) {
  let remainder = value << generatorDegree;
  const top = 31 - Math.clz32(generatorPoly);
  while (remainder && 31 - Math.clz32(remainder) >= top) remainder ^= generatorPoly << (31 - Math.clz32(remainder) - top);
  return (value << generatorDegree) | remainder;
}

/** matrix[row][col] === true for dark, exactly as the server's zxing writer would lay it out. */
function encodeQr(text, version, level, maskIndex) {
  const [ecLength, ...groups] = PARAMETERS[`${version}-${level}`];
  const dataBlocks = groups.flatMap(([count, length]) => new Array(count).fill(length));
  const capacity = dataBlocks.reduce((a, b) => a + b, 0);
  const bytes = Array.from(Buffer.from(text, 'utf8'));
  const bits = [];
  const push = (value, n) => { for (let i = n - 1; i >= 0; i--) bits.push((value >> i) & 1); };
  push(4, 4);
  push(bytes.length, version < 10 ? 8 : 16);
  for (const b of bytes) push(b, 8);
  assert.ok(bits.length <= capacity * 8, 'test payload too long for the chosen version/level');
  push(0, Math.min(4, capacity * 8 - bits.length));
  while (bits.length % 8) bits.push(0);
  const codewords = [];
  for (let i = 0; i < bits.length; i += 8) codewords.push(parseInt(bits.slice(i, i + 8).join(''), 2));
  for (let pad = 0; codewords.length < capacity; pad++) codewords.push(pad % 2 ? 0x11 : 0xec);

  let offset = 0;
  const blocks = dataBlocks.map((length) => {
    const data = codewords.slice(offset, offset += length);
    return { data, ec: rsEncode(data, ecLength) };
  });
  const stream = [];
  for (let i = 0; i < Math.max(...dataBlocks); i++) for (const b of blocks) if (i < b.data.length) stream.push(b.data[i]);
  for (let i = 0; i < ecLength; i++) for (const b of blocks) stream.push(b.ec[i]);

  const size = 17 + 4 * version;
  const matrix = Array.from({ length: size }, () => new Array(size).fill(false));
  const fixed = Array.from({ length: size }, () => new Array(size).fill(false));
  const set = (r, c, dark) => { matrix[r][c] = dark; fixed[r][c] = true; };
  for (const [r0, c0] of [[0, 0], [0, size - 7], [size - 7, 0]]) {
    for (let r = -1; r <= 7; r++) for (let c = -1; c <= 7; c++) {
      const rr = r0 + r; const cc = c0 + c;
      if (rr < 0 || cc < 0 || rr >= size || cc >= size) continue;
      const ring = Math.max(Math.abs(r - 3), Math.abs(c - 3));
      set(rr, cc, ring !== 2 && ring !== 4);
    }
  }
  // Alignment before timing, as zxing's MatrixUtil does: a centre already taken
  // is a finder; (6, n) and (n, 6) centres sit ON the timing lines and must stay.
  for (const r of ALIGNMENT[version]) for (const c of ALIGNMENT[version]) {
    if (fixed[r][c]) continue;
    for (let dr = -2; dr <= 2; dr++) for (let dc = -2; dc <= 2; dc++) set(r + dr, c + dc, Math.max(Math.abs(dr), Math.abs(dc)) !== 1);
  }
  for (let i = 8; i < size - 8; i++) {
    if (!fixed[6][i]) set(6, i, i % 2 === 0);
    if (!fixed[i][6]) set(i, 6, i % 2 === 0);
  }
  const format = bch((LEVEL_BITS[level] << 3) | maskIndex, 0x537, 10) ^ 0x5412;
  const formatBit = (i) => ((format >> i) & 1) === 1; // i = 14 is the most significant
  const firstCopy = [[8, 0], [8, 1], [8, 2], [8, 3], [8, 4], [8, 5], [8, 7], [8, 8], [7, 8], [5, 8], [4, 8], [3, 8], [2, 8], [1, 8], [0, 8]];
  firstCopy.forEach(([r, c], i) => set(r, c, formatBit(14 - i)));
  for (let i = 0; i < 7; i++) set(size - 1 - i, 8, formatBit(14 - i));
  for (let i = 0; i < 8; i++) set(8, size - 8 + i, formatBit(7 - i));
  set(size - 8, 8, true); // the dark module
  if (version >= 7) {
    const info = bch(version, 0x1f25, 12);
    for (let i = 0; i < 18; i++) {
      const dark = ((info >> i) & 1) === 1;
      set(Math.floor(i / 3), size - 11 + (i % 3), dark);
      set(size - 11 + (i % 3), Math.floor(i / 3), dark);
    }
  }

  let bit = 0; let upward = true;
  const totalBits = stream.length * 8;
  for (let col = size - 1; col > 0; col -= 2) {
    if (col === 6) col--;
    for (let k = 0; k < size; k++) {
      const row = upward ? size - 1 - k : k;
      for (const c of [col, col - 1]) {
        if (fixed[row][c]) continue;
        const dark = bit < totalBits && ((stream[bit >> 3] >> (7 - (bit & 7))) & 1) === 1;
        bit++;
        matrix[row][c] = dark !== MASK[maskIndex](row, c);
      }
    }
    upward = !upward;
  }
  return matrix;
}

/** A grayscale PNG of `matrix` at `scale` pixels per module with a 4-module quiet zone. */
function toPng(matrix, scale = 4) {
  const quiet = 4;
  const side = (matrix.length + 2 * quiet) * scale;
  const raw = Buffer.alloc((side + 1) * side, 0xff);
  for (let y = 0; y < side; y++) {
    raw[y * (side + 1)] = 0;
    for (let x = 0; x < side; x++) {
      const row = Math.floor(y / scale) - quiet; const col = Math.floor(x / scale) - quiet;
      if (row >= 0 && col >= 0 && row < matrix.length && col < matrix.length && matrix[row][col]) raw[y * (side + 1) + 1 + x] = 0;
    }
  }
  const chunk = (type, data) => {
    const head = Buffer.alloc(8); head.writeUInt32BE(data.length, 0); head.write(type, 4, 'ascii');
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([Buffer.from(type, 'ascii'), data])) >>> 0, 0);
    return Buffer.concat([head, data, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(side, 0); ihdr.writeUInt32BE(side, 4); ihdr[8] = 8; ihdr[9] = 0;
  return Buffer.concat([Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

/** A logo-shaped hole: every module whose centre lies within `radius` modules of the middle. */
function centreDisc(matrix, radius, paint) {
  const out = matrix.map(row => row.slice());
  const middle = (matrix.length - 1) / 2;
  let touched = 0;
  for (let r = 0; r < matrix.length; r++) for (let c = 0; c < matrix.length; c++) {
    if ((r - middle) ** 2 + (c - middle) ** 2 <= radius ** 2) { out[r][c] = paint(out[r][c]); touched++; }
  }
  return { matrix: out, touched };
}

const OTPAUTH = 'otpauth://totp/CARLOS:mfa-throwaway?secret=JBSWY3DPEHPK3PXP&issuer=CARLOS';

test('the test encoder round-trips through the decoder with no damage, across versions, levels and masks', () => {
  const cases = [['1-L', 'otpauth://totp/x'], ['4-H', 'otpauth://totp/a?s=1'], ['5-M', OTPAUTH],
    ['5-H', 'otpauth://totp/CARLOS:a?s=B'], ['7-Q', OTPAUTH], ['10-M', OTPAUTH + '&period=30&digits=6&algorithm=SHA1']];
  cases.forEach(([key, text], i) => {
    const [version, level] = key.split('-');
    for (const mask of [i % 8, (i + 3) % 8]) {
      const matrix = encodeQr(text, Number(version), level, mask);
      assert.equal(decodeMatrix(matrix), text, `${key} mask ${mask}`);
      assert.equal(decodeQrPng(toPng(matrix)), text, `${key} mask ${mask} via PNG`);
    }
  });
});

test('a logo-sized blank disc in the centre (as MfaManagerImpl draws it, level M) still decodes', () => {
  const clean = encodeQr(OTPAUTH, 5, 'M', 2);
  // QrCodeUtils.addLogoToQRCode caps the logo at a fifth of the image width;
  // a radius of 3.5 modules covers a 7-module disc in a 37-module code.
  const { matrix, touched } = centreDisc(clean, 3.5, () => false);
  assert.ok(touched >= 30, 'the disc must actually cover modules');
  assert.notEqual(JSON.stringify(matrix), JSON.stringify(clean), 'the disc must change some modules');
  const png = 'data:image/png;base64,' + toPng(matrix).toString('base64');
  const text = decodeQrPng(png);
  assert.equal(text, OTPAUTH);
  assert.equal(parseOtpauthUrl(text).secret, 'JBSWY3DPEHPK3PXP');
});

test('flipped modules in the centre, within EC capacity, still decode at every level', () => {
  for (const [key, mask] of [['5-M', 0], ['5-H', 5], ['7-Q', 6], ['10-M', 3]]) {
    const [version, level] = key.split('-');
    const text = key === '5-H' ? 'otpauth://totp/CARLOS:a?s=B' : OTPAUTH;
    const { matrix } = centreDisc(encodeQr(text, Number(version), level, mask), 2.5, dark => !dark);
    assert.equal(decodeMatrix(matrix), text, key);
  }
});

test('centre damage beyond EC capacity throws instead of returning text', () => {
  const clean = encodeQr(OTPAUTH, 5, 'M', 4);
  const { matrix } = centreDisc(clean, 11, dark => !dark);
  assert.throws(() => decodeMatrix(matrix), /QR block \d of 2: the QR block could not be corrected/);
  assert.throws(() => decodeQrPng(toPng(matrix)), /could not be corrected/);
});

test('a truncated or IEND-less PNG is refused with the module\'s own error, never a RangeError', () => {
  const png = toPng(encodeQr(OTPAUTH, 5, 'M', 1));
  const iend = png.length - 12;
  const cases = [
    ['cut inside a chunk header', png.subarray(0, 8 + 6), /no IEND chunk/],
    ['cut inside the IDAT data', png.subarray(0, iend - 20), /a chunk runs past the end/],
    ['IEND removed', png.subarray(0, iend), /no IEND chunk/],
    ['chunk length overstated', (() => { const copy = Buffer.from(png); copy.writeUInt32BE(0xffffff00, 8); return copy; })(), /a chunk runs past the end/],
  ];
  for (const [name, buffer, message] of cases) {
    assert.throws(() => decodeQrPng(buffer), error => !(error instanceof RangeError) && message.test(error.message), name);
  }
});

// ---- RFC 6238 code generation --------------------------------------------------

// RFC 6238 Appendix B, SHA-1: the ASCII seed "12345678901234567890" (Base32
// below) at each listed Unix time gives these eight-digit codes.
const RFC6238_SECRET = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
const RFC6238_SHA1 = [
  [59, '94287082'], [1111111109, '07081804'], [1111111111, '14050471'],
  [1234567890, '89005924'], [2000000000, '69279037'], [20000000000, '65353130'],
];

test('totp reproduces the RFC 6238 SHA-1 vectors', () => {
  for (const [seconds, code] of RFC6238_SHA1) {
    assert.equal(totp(RFC6238_SECRET, { timeMs: seconds * 1000, digits: 8 }), code, `T=${seconds}`);
    // The six-digit code CARLOS uses is the same truncation, mod 10^6.
    assert.equal(totp(RFC6238_SECRET, { timeMs: seconds * 1000 }), code.slice(-6), `T=${seconds}, 6 digits`);
  }
});

test('totp steps by 30 s and offsetSteps moves to the neighbouring window', () => {
  const at = 1111111111 * 1000;
  assert.equal(totp(RFC6238_SECRET, { timeMs: at, offsetSteps: -1 }), totp(RFC6238_SECRET, { timeMs: at - 30000 }));
  assert.equal(totp(RFC6238_SECRET, { timeMs: at, offsetSteps: 1 }), totp(RFC6238_SECRET, { timeMs: at + 30000 }));
});

test('wrongTotp is a six-digit code outside every window Login2Action accepts', () => {
  // T=59 is skipped: two steps back from it is before the Unix epoch.
  for (const [seconds] of RFC6238_SHA1.filter(([t]) => t >= 60)) {
    const timeMs = seconds * 1000;
    const wrong = wrongTotp(RFC6238_SECRET, timeMs);
    assert.match(wrong, /^\d{6}$/);
    for (const offsetSteps of [-2, -1, 0, 1, 2]) {
      assert.notEqual(wrong, totp(RFC6238_SECRET, { timeMs, offsetSteps }), `T=${seconds} offset ${offsetSteps}`);
    }
  }
});
