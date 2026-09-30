/* SPDX-License-Identifier: GPL-2.0-or-later */
// Pins the #3977 response contract that lab-embedded-pdf-playwright-checks.js asserts on a
// live deployment, so a weakened assertion fails here without one.
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  buildMessage, headerMap, assertInlinePdfResponse, assertRefusal, PDF, HTML_PAYLOAD,
} = require('./lab-embedded-pdf-playwright-checks');

const GOOD_HEADERS = {
  'content-type': 'application/pdf',
  'content-disposition': 'inline; filename="Lab-12.pdf"',
  'x-content-type-options': 'nosniff',
  'cache-control': 'no-store',
  'content-security-policy': "default-src 'none'; frame-ancestors 'self'; sandbox",
};

test('accepts the hardened inline PDF response', () => {
  assertInlinePdfResponse(200, GOOD_HEADERS, PDF);
});

test('rejects an inline response missing any hardening header', () => {
  for (const header of ['x-content-type-options', 'cache-control', 'content-security-policy']) {
    const headers = { ...GOOD_HEADERS };
    delete headers[header];
    assert.throws(() => assertInlinePdfResponse(200, headers, PDF), undefined, header);
  }
  assert.throws(() => assertInlinePdfResponse(200,
    { ...GOOD_HEADERS, 'content-security-policy': "default-src 'none'; frame-ancestors 'self'" }, PDF), /sandbox/);
});

test('rejects an attachment, a wrong type or non-PDF bytes on the inline route', () => {
  assert.throws(() => assertInlinePdfResponse(200,
    { ...GOOD_HEADERS, 'content-disposition': 'attachment; filename="Lab-12.pdf"' }, PDF), /inline/);
  assert.throws(() => assertInlinePdfResponse(200, { ...GOOD_HEADERS, 'content-type': 'text/html' }, PDF), /application\/pdf/);
  assert.throws(() => assertInlinePdfResponse(200, GOOD_HEADERS, HTML_PAYLOAD), /not a PDF/);
  assert.throws(() => assertInlinePdfResponse(404, GOOD_HEADERS, PDF), /404/);
});

test('a refusal must have the expected status, no body and no PDF type', () => {
  assertRefusal(415, {}, Buffer.alloc(0), 415, 'x');
  assert.throws(() => assertRefusal(200, {}, Buffer.alloc(0), 415, 'x'), /answered 200/);
  assert.throws(() => assertRefusal(415, { 'content-type': 'application/pdf' }, Buffer.alloc(0), 415, 'x'), /application\/pdf/);
  assert.throws(() => assertRefusal(415, {}, Buffer.from('<html>'), 415, 'x'), /body/);
});

test('the seeded lab mixes a text result, the PDF and a non-PDF ED payload', () => {
  const message = buildMessage('PW3977-abc', 'FAKE-PWX');
  const segments = message.split('\r').filter(Boolean);
  assert.equal(segments.filter(line => line.startsWith('OBR|')).length, 3);
  assert.ok(segments.some(line => line.startsWith('OBX|1|NM|GLU^')));
  assert.ok(message.includes(`^TEXT^PDF^Base64^${PDF.toString('base64')}`));
  assert.ok(message.includes(`^TEXT^HTML^Base64^${HTML_PAYLOAD.toString('base64')}`));
  assert.equal(PDF.subarray(0, 5).toString('ascii'), '%PDF-');
});

test('keeps both CSP headers the front door sends', () => {
  const headers = headerMap([
    { name: 'Content-Type', value: 'application/pdf' },
    { name: 'Content-Security-Policy', value: "default-src 'none'; frame-ancestors 'self'; sandbox" },
    { name: 'content-security-policy', value: "frame-ancestors 'self'; base-uri 'self'; object-src 'none'" },
  ]);
  assert.match(headers['content-security-policy'], /sandbox/);
  assert.match(headers['content-security-policy'], /object-src 'none'/);
  assertInlinePdfResponse(200, { ...GOOD_HEADERS, 'content-security-policy': headers['content-security-policy'] }, PDF);
});
