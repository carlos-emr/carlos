/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * One-time passwords for checks that enrol a throwaway login in MFA.
 *
 * CARLOS shows the enrolment secret only as a QR code (mfa_registration.jsp
 * renders a server-made PNG of the otpauth:// URL) and stores it encrypted, so a
 * check that must answer the challenge reads the QR exactly as a phone would and
 * computes RFC 6238 codes from it. Both halves are deliberately small and
 * dependency-free:
 *
 *  - decodeQrPng(): the image comes from zxing's QRCodeWriter scaled by an integer
 *    factor -- axis-aligned, noise-free, square modules -- so it is sampled on its
 *    module grid. Each block is then run through Reed-Solomon correction, because
 *    the grid is NOT always intact: with mfa.registration.qrcode.logo.path set,
 *    MfaManagerImpl punches a logo through the centre and relies on the code's own
 *    error correction (level M, ~15%) to carry the modules it hides. A block with
 *    more damage than its EC codewords can repair throws rather than decoding, and
 *    the corrected block must re-check to zero syndromes, so a misread fails loudly
 *    instead of yielding a plausible wrong secret.
 *  - totp(): HMAC-SHA1, 30 s step, 6 digits -- the parameters of the
 *    TimeBasedOneTimePasswordGenerator defaults Login2Action validates against.
 *
 * Never log the secret or a code: they are live credentials for the throwaway.
 */
const { createHmac } = require('node:crypto');
const { inflateSync } = require('node:zlib');

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function base32Decode(text) {
  const clean = String(text).replace(/=+$/, '').toUpperCase();
  let bits = 0;
  let value = 0;
  const out = [];
  for (const char of clean) {
    const index = BASE32.indexOf(char);
    if (index < 0) throw new Error('The MFA secret is not Base32');
    value = (value << 5) | index;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(out);
}

/** RFC 6238 code for a Base32 secret at `timeMs` (default now), `offsetSteps` steps away. */
function totp(secretBase32, { timeMs = Date.now(), offsetSteps = 0, stepSeconds = 30, digits = 6 } = {}) {
  const counter = Math.floor(timeMs / 1000 / stepSeconds) + offsetSteps;
  const message = Buffer.alloc(8);
  message.writeBigUInt64BE(BigInt(counter));
  const hmac = createHmac('sha1', base32Decode(secretBase32)).update(message).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const binary = hmac.readUInt32BE(offset) & 0x7fffffff;
  return String(binary % (10 ** digits)).padStart(digits, '0');
}

/**
 * A 6-digit code that is NOT accepted now: Login2Action allows the previous,
 * current and next step, so the wrong code avoids all three (and a margin).
 */
function wrongTotp(secretBase32, timeMs = Date.now()) {
  const valid = new Set([-2, -1, 0, 1, 2].map(offsetSteps => totp(secretBase32, { timeMs, offsetSteps })));
  for (let candidate = Number(totp(secretBase32, { timeMs })) + 500000; ; candidate += 1) {
    const code = String(candidate % 1000000).padStart(6, '0');
    if (!valid.has(code)) return code;
  }
}

// ---- PNG -> luminance -------------------------------------------------------

function decodePng(buffer) {
  const signature = '89504e470d0a1a0a';
  if (buffer.subarray(0, 8).toString('hex') !== signature) throw new Error('The QR image is not a PNG');
  let offset = 8;
  let header = null;
  let palette = null;
  const idat = [];
  // Every chunk (length, type, data, CRC) must fit in the buffer and the image must end
  // with IEND, so truncated input is refused here rather than as a RangeError.
  for (let ended = false; !ended;) {
    if (offset + 12 > buffer.length) throw new Error('The QR PNG is truncated: it has no IEND chunk');
    const length = buffer.readUInt32BE(offset);
    if (length > buffer.length - offset - 12) throw new Error('The QR PNG is truncated: a chunk runs past the end of the image');
    const type = buffer.toString('ascii', offset + 4, offset + 8);
    const data = buffer.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      if (length < 13) throw new Error('The QR PNG has an unsupported header');
      header = { width: data.readUInt32BE(0), height: data.readUInt32BE(4), bitDepth: data[8], colorType: data[9], interlace: data[12] };
    } else if (type === 'PLTE') palette = data;
    else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') ended = true;
    offset += 12 + length;
  }
  if (!header || header.interlace !== 0 || header.width < 1 || header.height < 1 || header.width > 10000 || header.height > 10000) {
    throw new Error('The QR PNG has an unsupported header');
  }
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[header.colorType];
  const { width, height, bitDepth, colorType } = header;
  if (!channels || ![1, 2, 4, 8].includes(bitDepth) || (bitDepth < 8 && ![0, 3].includes(colorType))) {
    throw new Error('The QR PNG uses an unsupported pixel format');
  }
  if (colorType === 3 && (!palette || palette.length % 3 !== 0)) throw new Error('The QR PNG has no usable palette');
  let raw;
  try { raw = inflateSync(Buffer.concat(idat)); } catch { throw new Error('The QR PNG image data is corrupt'); }
  const bitsPerPixel = channels * bitDepth;
  const stride = Math.ceil((width * bitsPerPixel) / 8);
  if (raw.length < height * (stride + 1)) throw new Error('The QR PNG image data is truncated');
  const bpp = Math.max(1, bitsPerPixel >> 3);
  const pixels = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const left = x >= bpp ? pixels[y * stride + x - bpp] : 0;
      const up = y > 0 ? pixels[(y - 1) * stride + x] : 0;
      const upLeft = y > 0 && x >= bpp ? pixels[(y - 1) * stride + x - bpp] : 0;
      let predictor = 0;
      if (filter === 1) predictor = left;
      else if (filter === 2) predictor = up;
      else if (filter === 3) predictor = (left + up) >> 1;
      else if (filter === 4) {
        const p = left + up - upLeft;
        const pa = Math.abs(p - left); const pb = Math.abs(p - up); const pc = Math.abs(p - upLeft);
        predictor = pa <= pb && pa <= pc ? left : (pb <= pc ? up : upLeft);
      } else if (filter !== 0) throw new Error('The QR PNG uses an unknown filter');
      pixels[y * stride + x] = (line[x] + predictor) & 0xff;
    }
  }
  const sample = (x, y) => {
    if (bitDepth < 8) {
      const bitOffset = x * bitDepth;
      const byte = pixels[y * stride + (bitOffset >> 3)];
      const value = (byte >> (8 - bitDepth - (bitOffset & 7))) & ((1 << bitDepth) - 1);
      if (colorType === 3) return luminance(palette[value * 3], palette[value * 3 + 1], palette[value * 3 + 2]);
      return Math.round((value * 255) / ((1 << bitDepth) - 1));
    }
    const at = y * stride + x * channels;
    if (colorType === 0) return pixels[at];
    if (colorType === 4) return pixels[at + 1] === 0 ? 255 : pixels[at];
    if (colorType === 3) return luminance(palette[pixels[at] * 3], palette[pixels[at] * 3 + 1], palette[pixels[at] * 3 + 2]);
    if (colorType === 6 && pixels[at + 3] === 0) return 255;
    return luminance(pixels[at], pixels[at + 1], pixels[at + 2]);
  };
  return { width, height, dark: (x, y) => sample(x, y) < 128 };
}

function luminance(r, g, b) {
  return (r * 299 + g * 587 + b * 114) / 1000;
}

// ---- Reed-Solomon over GF(256) -----------------------------------------------
//
// QR arithmetic: GF(2^8) built on the primitive polynomial x^8+x^4+x^3+x^2+1
// (0x11D) with alpha = 2, and a generator whose roots are alpha^0..alpha^(ec-1).
// A block's first codeword is its highest-degree coefficient, so codeword i of
// an n-codeword block is the coefficient of x^(n-1-i). Polynomials below are
// arrays indexed by degree (poly[0] is the constant term). Field addition is XOR.

const GF_EXP = new Uint8Array(512);
const GF_LOG = new Uint8Array(256);
for (let i = 0, x = 1; i < 255; i++) {
  GF_EXP[i] = x;
  GF_LOG[x] = i;
  x <<= 1;
  if (x & 0x100) x ^= 0x11d;
}
// Doubled so a product's log sum (at most 254 + 254) indexes without a modulo.
for (let i = 255; i < 512; i++) GF_EXP[i] = GF_EXP[i - 255];

function gfMul(a, b) {
  return a && b ? GF_EXP[GF_LOG[a] + GF_LOG[b]] : 0;
}

function gfDiv(a, b) {
  if (!b) throw new Error('division by zero in GF(256)');
  return a ? GF_EXP[GF_LOG[a] + 255 - GF_LOG[b]] : 0;
}

function polyEval(poly, x) {
  let value = 0;
  for (let i = poly.length - 1; i >= 0; i--) value = gfMul(value, x) ^ poly[i];
  return value;
}

/** S_j = c(alpha^j) for j = 0..ecLength-1; all zero exactly when c is a codeword. */
function syndromes(block, ecLength) {
  const result = new Array(ecLength).fill(0);
  for (let j = 0; j < ecLength; j++) {
    let value = 0;
    for (const codeword of block) value = gfMul(value, GF_EXP[j]) ^ codeword;
    result[j] = value;
  }
  return result;
}

/**
 * Correct one block (data codewords followed by its `ecLength` EC codewords) and
 * return the corrected copy; the input is not modified. Up to floor(ecLength / 2)
 * wrong codewords are repaired -- Berlekamp-Massey for the error locator, Chien
 * search for where, Forney for by how much. Anything beyond that throws: the
 * locator comes out too long, its roots do not all land inside the block, or the
 * "corrected" block still fails its syndromes. Messages never carry codeword
 * values, which for an enrolment QR are pieces of a live secret.
 */
function correctBlock(block, ecLength) {
  const fail = (why) => new Error(`the QR block could not be corrected (${why}; ${ecLength} EC codewords repair at most ${ecLength >> 1})`);
  const syndrome = syndromes(block, ecLength);
  if (syndrome.every(s => s === 0)) return block.slice();

  // Berlekamp-Massey: the shortest LFSR (error locator lambda, lambda[0] = 1)
  // that generates the syndrome sequence. Its length is the error count.
  let lambda = [1]; let previous = [1];
  let errors = 0; let shift = 1; let previousDiscrepancy = 1;
  for (let n = 0; n < ecLength; n++) {
    let discrepancy = syndrome[n];
    for (let i = 1; i <= errors; i++) discrepancy ^= gfMul(lambda[i] || 0, syndrome[n - i]);
    if (discrepancy === 0) { shift++; continue; }
    const scale = gfDiv(discrepancy, previousDiscrepancy);
    const next = lambda.slice();
    for (let i = 0; i < previous.length; i++) {
      while (next.length <= i + shift) next.push(0);
      next[i + shift] ^= gfMul(scale, previous[i]);
    }
    if (2 * errors <= n) {
      previous = lambda;
      errors = n + 1 - errors;
      previousDiscrepancy = discrepancy;
      shift = 1;
    } else shift++;
    lambda = next;
  }
  if (2 * errors > ecLength) throw fail(`at least ${errors} codewords are wrong`);

  // Chien search: codeword i carries x^(n-1-i), whose locator is X = alpha^(n-1-i);
  // it is in error exactly when lambda(X^-1) = 0.
  const n = block.length;
  const positions = [];
  for (let i = 0; i < n; i++) {
    const degree = n - 1 - i;
    if (polyEval(lambda, GF_EXP[(255 - degree) % 255]) === 0) positions.push(i);
  }
  if (positions.length !== errors) throw fail(`the error locator has ${positions.length} roots in the block, not ${errors}`);

  // Forney (first root alpha^0): magnitude = X * omega(X^-1) / lambda'(X^-1), where
  // omega = S(x) * lambda(x) mod x^ecLength. In characteristic 2 the formal
  // derivative keeps only the odd-degree terms of lambda.
  const omega = new Array(ecLength).fill(0);
  for (let i = 0; i < lambda.length; i++) {
    for (let j = 0; i + j < ecLength; j++) omega[i + j] ^= gfMul(lambda[i], syndrome[j]);
  }
  const derivative = lambda.map((coefficient, i) => (i % 2 === 1 ? coefficient : 0)).slice(1);
  const corrected = block.slice();
  for (const i of positions) {
    const degree = n - 1 - i;
    const inverse = GF_EXP[(255 - degree) % 255];
    const denominator = polyEval(derivative, inverse);
    if (denominator === 0) throw fail('the error locator has a repeated root');
    corrected[i] ^= gfMul(GF_EXP[degree], gfDiv(polyEval(omega, inverse), denominator));
  }
  if (!syndromes(corrected, ecLength).every(s => s === 0)) throw fail('the corrected block still fails its syndromes');
  return corrected;
}

// ---- QR grid -> text --------------------------------------------------------

// [ecCodewordsPerBlock, [blockCount, dataCodewords], ...] per version 1-10, level order L, M, Q, H.
const EC_BLOCKS = [
  null,
  { L: [7, [1, 19]], M: [10, [1, 16]], Q: [13, [1, 13]], H: [17, [1, 9]] },
  { L: [10, [1, 34]], M: [16, [1, 28]], Q: [22, [1, 22]], H: [28, [1, 16]] },
  { L: [15, [1, 55]], M: [26, [1, 44]], Q: [18, [2, 17]], H: [22, [2, 13]] },
  { L: [20, [1, 80]], M: [18, [2, 32]], Q: [26, [2, 24]], H: [16, [4, 9]] },
  { L: [26, [1, 108]], M: [24, [2, 43]], Q: [18, [2, 15], [2, 16]], H: [22, [2, 11], [2, 12]] },
  { L: [18, [2, 68]], M: [16, [4, 27]], Q: [24, [4, 19]], H: [28, [4, 15]] },
  { L: [20, [2, 78]], M: [18, [4, 31]], Q: [18, [2, 14], [4, 15]], H: [26, [4, 13], [1, 14]] },
  { L: [24, [2, 97]], M: [22, [2, 38], [2, 39]], Q: [22, [4, 18], [2, 19]], H: [26, [4, 14], [2, 15]] },
  { L: [30, [2, 116]], M: [22, [3, 36], [2, 37]], Q: [20, [4, 16], [4, 17]], H: [24, [4, 12], [4, 13]] },
  { L: [18, [2, 68], [2, 69]], M: [26, [4, 43], [1, 44]], Q: [24, [6, 19], [2, 20]], H: [28, [6, 15], [2, 16]] },
];
const ALIGNMENT = [null, [], [6, 18], [6, 22], [6, 26], [6, 30], [6, 34], [6, 22, 38], [6, 24, 42], [6, 26, 46], [6, 28, 50]];
const EC_LEVEL_BITS = { 1: 'L', 0: 'M', 3: 'Q', 2: 'H' };
const MASKS = [
  (i, j) => (i + j) % 2 === 0,
  i => i % 2 === 0,
  (i, j) => j % 3 === 0,
  (i, j) => (i + j) % 3 === 0,
  (i, j) => (Math.floor(i / 2) + Math.floor(j / 3)) % 2 === 0,
  (i, j) => ((i * j) % 2) + ((i * j) % 3) === 0,
  (i, j) => (((i * j) % 2) + ((i * j) % 3)) % 2 === 0,
  (i, j) => (((i + j) % 2) + ((i * j) % 3)) % 2 === 0,
];

function formatCodeword(data) {
  let remainder = data << 10;
  for (let bit = 14; bit >= 10; bit--) if (remainder & (1 << bit)) remainder ^= 0x537 << (bit - 10);
  return ((data << 10) | remainder) ^ 0x5412;
}

function popcount(value) {
  let count = 0;
  for (let v = value; v; v &= v - 1) count++;
  return count;
}

/** Sample the image on its module grid: matrix[row][col] is true for a dark module. */
function sampleGrid(image) {
  let top = -1; let left = image.width; let right = -1;
  for (let y = 0; y < image.height; y++) {
    for (let x = 0; x < image.width; x++) {
      if (!image.dark(x, y)) continue;
      if (top < 0) top = y;
      left = Math.min(left, x);
      right = Math.max(right, x);
    }
  }
  if (top < 0) throw new Error('The QR image has no dark modules');
  let run = 0;
  while (left + run < image.width && image.dark(left + run, top)) run++;
  const module = run / 7;
  const dimension = Math.round((right - left + 1) / module);
  if (!Number.isInteger(module) || (dimension - 17) % 4 !== 0) throw new Error('The QR image is not on an integer module grid');
  const matrix = [];
  for (let row = 0; row < dimension; row++) {
    matrix.push([]);
    for (let col = 0; col < dimension; col++) {
      matrix[row].push(image.dark(left + col * module + Math.floor(module / 2), top + row * module + Math.floor(module / 2)));
    }
  }
  return matrix;
}

function decodeMatrix(matrix) {
  const dimension = matrix.length;
  const version = (dimension - 17) / 4;
  if (version < 1 || version > 10) throw new Error(`QR version ${version} is outside the supported range`);
  // Format information around the top-left finder, most significant bit first.
  const cells = [];
  for (let col = 0; col <= 5; col++) cells.push([8, col]);
  cells.push([8, 7], [8, 8], [7, 8]);
  for (let row = 5; row >= 0; row--) cells.push([row, 8]);
  const read = cells.reduce((bits, [row, col]) => (bits << 1) | (matrix[row][col] ? 1 : 0), 0);
  let best = null;
  for (let data = 0; data < 32; data++) {
    const distance = popcount(formatCodeword(data) ^ read);
    if (!best || distance < best.distance) best = { data, distance };
  }
  if (best.distance > 3) throw new Error('The QR format information is unreadable');
  const level = EC_LEVEL_BITS[best.data >> 3];
  const mask = MASKS[best.data & 7];

  const reserved = Array.from({ length: dimension }, () => new Array(dimension).fill(false));
  const region = (row, col, height, width) => {
    for (let r = row; r < row + height; r++) for (let c = col; c < col + width; c++) reserved[r][c] = true;
  };
  region(0, 0, 9, 9);
  region(0, dimension - 8, 9, 8);
  region(dimension - 8, 0, 8, 9);
  const centres = ALIGNMENT[version];
  for (let a = 0; a < centres.length; a++) {
    for (let b = 0; b < centres.length; b++) {
      const last = centres.length - 1;
      if ((a === 0 && (b === 0 || b === last)) || (a === last && b === 0)) continue;
      region(centres[a] - 2, centres[b] - 2, 5, 5);
    }
  }
  region(9, 6, dimension - 17, 1);
  region(6, 9, 1, dimension - 17);
  if (version > 6) {
    region(0, dimension - 11, 6, 3);
    region(dimension - 11, 0, 3, 6);
  }

  const codewords = [];
  let current = 0; let bitsRead = 0; let upward = true;
  for (let col = dimension - 1; col > 0; col -= 2) {
    if (col === 6) col--;
    for (let count = 0; count < dimension; count++) {
      const row = upward ? dimension - 1 - count : count;
      for (let offset = 0; offset < 2; offset++) {
        const c = col - offset;
        if (reserved[row][c]) continue;
        current = (current << 1) | ((matrix[row][c] !== mask(row, c)) ? 1 : 0);
        if (++bitsRead === 8) { codewords.push(current); current = 0; bitsRead = 0; }
      }
    }
    upward = !upward;
  }

  // Codewords are interleaved: every block's first data codeword, then every
  // block's second, ... (shorter blocks drop out of the last round), and then the
  // EC codewords the same way -- every block carries the same number of those.
  const [ecLength, ...groups] = EC_BLOCKS[version][level];
  const blocks = groups.flatMap(([count, data]) => Array.from({ length: count }, () => ({ data, bytes: [], ec: [] })));
  const longest = Math.max(...blocks.map(block => block.data));
  const total = blocks.reduce((sum, block) => sum + block.data + ecLength, 0);
  if (codewords.length < total) throw new Error('The QR code holds fewer codewords than its version and level require');
  let index = 0;
  for (let i = 0; i < longest; i++) for (const block of blocks) if (i < block.data) block.bytes.push(codewords[index++]);
  for (let i = 0; i < ecLength; i++) for (const block of blocks) block.ec.push(codewords[index++]);
  const data = blocks.flatMap((block, number) => {
    try {
      return correctBlock([...block.bytes, ...block.ec], ecLength).slice(0, block.data);
    } catch (error) {
      throw new Error(`QR block ${number + 1} of ${blocks.length}: ${error.message}`);
    }
  });

  let bit = 0;
  const take = (n) => {
    let value = 0;
    for (let i = 0; i < n; i++, bit++) value = (value << 1) | ((data[bit >> 3] >> (7 - (bit & 7))) & 1);
    return value;
  };
  const bytes = [];
  for (;;) {
    if (bit + 4 > data.length * 8) break;
    const mode = take(4);
    if (mode === 0) break;
    if (mode !== 4) throw new Error('The QR code uses a segment mode other than byte mode');
    const length = take(version < 10 ? 8 : 16);
    for (let i = 0; i < length; i++) bytes.push(take(8));
  }
  return Buffer.from(bytes).toString('utf8');
}

/** Decode a server-generated QR code PNG (a Buffer or a data:image/png;base64 URL) to its text. */
function decodeQrPng(source) {
  const buffer = Buffer.isBuffer(source)
    ? source
    : Buffer.from(String(source).replace(/^data:image\/png;base64,/, ''), 'base64');
  return decodeMatrix(sampleGrid(decodePng(buffer)));
}

/**
 * Parse an otpauth://totp/ URL into { issuer, account, secret }; throws on
 * anything else so a misread QR fails loudly instead of yielding a bad secret.
 */
function parseOtpauthUrl(text) {
  const url = new URL(text);
  if (url.protocol !== 'otpauth:' || url.host !== 'totp') throw new Error('The QR code is not an otpauth TOTP URL');
  const label = decodeURIComponent(url.pathname.replace(/^\//, ''));
  const colon = label.indexOf(':');
  const secret = url.searchParams.get('secret') || '';
  if (!/^[A-Z2-7]+=*$/.test(secret) || base32Decode(secret).length < 10) throw new Error('The otpauth URL carries no usable secret');
  return {
    issuer: url.searchParams.get('issuer'),
    labelIssuer: colon >= 0 ? label.slice(0, colon) : null,
    account: colon >= 0 ? label.slice(colon + 1) : label,
    secret,
  };
}

module.exports = {
  correctBlock,
  decodeMatrix,
  decodeQrPng,
  parseOtpauthUrl,
  totp,
  wrongTotp,
};
