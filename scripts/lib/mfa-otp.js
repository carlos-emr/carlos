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
 *    module grid and decoded WITHOUT Reed-Solomon correction. Any misread therefore
 *    yields garbage that the caller's otpauth parse rejects; it can never yield a
 *    plausible wrong secret.
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
  while (offset < buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const type = buffer.toString('ascii', offset + 4, offset + 8);
    const data = buffer.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      header = { width: data.readUInt32BE(0), height: data.readUInt32BE(4), bitDepth: data[8], colorType: data[9], interlace: data[12] };
    } else if (type === 'PLTE') palette = data;
    else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    offset += 12 + length;
  }
  if (!header || header.interlace !== 0) throw new Error('The QR PNG has an unsupported header');
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[header.colorType];
  const { width, height, bitDepth, colorType } = header;
  if (!channels || ![1, 2, 4, 8].includes(bitDepth) || (bitDepth < 8 && ![0, 3].includes(colorType))) {
    throw new Error('The QR PNG uses an unsupported pixel format');
  }
  const raw = inflateSync(Buffer.concat(idat));
  const bitsPerPixel = channels * bitDepth;
  const stride = Math.ceil((width * bitsPerPixel) / 8);
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

  const [, ...groups] = EC_BLOCKS[version][level];
  const blocks = groups.flatMap(([count, data]) => Array.from({ length: count }, () => ({ data, bytes: [] })));
  const longest = Math.max(...blocks.map(block => block.data));
  let index = 0;
  for (let i = 0; i < longest; i++) for (const block of blocks) if (i < block.data) block.bytes.push(codewords[index++]);
  const data = blocks.flatMap(block => block.bytes);

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
  decodeQrPng,
  parseOtpauthUrl,
  totp,
  wrongTotp,
};
