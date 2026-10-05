/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {parseConsultSignatureScenarios, readSignatureState, describePdf} = require('./consultation-signature-playwright-checks');

test('no external IDs selects the owned fixture; an incomplete explicit fixture fails', () => {
  assert.deepEqual(parseConsultSignatureScenarios({}), []);
  assert.throws(() => parseConsultSignatureScenarios({CONSULT_DEMOGRAPHIC_NO: '1'}), /requestId/);
});

test('explicit requests retain signed expectations and accept a deliberate unsigned scenario', () => {
  assert.deepEqual(parseConsultSignatureScenarios({CONSULT_REQUEST_ID: '12', CONSULT_DEMOGRAPHIC_NO: '3'}),
    [{name: 'default', requestId: '12', demographicNo: '3', expectSignature: true}]);
  assert.deepEqual(parseConsultSignatureScenarios({CONSULT_SIGNATURE_SCENARIOS: JSON.stringify([
    {name: 'stored', consultRequestId: 12}, {name: 'unsigned', requestId: '13', expectSignature: false},
  ])}).map(s => s.expectSignature), [true, false]);
});

test('malformed explicit scenarios cannot silently fall back to an owned or unsigned fixture', () => {
  for (const input of ['broken', '[]', '{}', '[null]', '[[]]', '[{"requestId":0}]',
    '[{"requestId":"1;DELETE"}]', '[{"requestId":1,"demographicNo":"bad"}]',
    '[{"requestId":1,"name":" "}]', '[{"requestId":1,"expectSignature":"false"}]']) {
    assert.throws(() => parseConsultSignatureScenarios({CONSULT_SIGNATURE_SCENARIOS: input}));
  }
});

const signed = {imageLoaded: true, padVisible: false, newSignature: 'false', signatureImg: '25'};
const unsigned = {imageLoaded: false, padVisible: true, newSignature: 'true', signatureImg: '', signatureProviderNo: '800001'};

test('a missing expected signature fails with fixture guidance and the intended 15-second timeout', async () => {
  const timeout = new Error('Timeout');
  const page = {async waitForFunction(fn, arg, options) {
    assert.equal(arg, null);
    assert.deepEqual(options, {timeout: 15000});
    throw timeout;
  }, async evaluate() { return unsigned; }};
  await assert.rejects(readSignatureState(page, {name: 'required', requestId: '12', expectSignature: true}), error => {
    assert.match(error.message, /request 12.*stored signature=none.*manual pad=true/);
    assert.match(error.message, /expectSignature:false/);
    assert.equal(error.cause, timeout);
    return true;
  });
});

test('unsigned scenarios require the pad and empty saved signature; signed scenarios require the image', async () => {
  for (const state of [signed, {...unsigned, padVisible: false}, {...unsigned, signatureImg: '25'}, {...unsigned, newSignature: 'false'}]) {
    await assert.rejects(readSignatureState({async evaluate() { return state; }}, {name: 'unsigned', expectSignature: false}), /unsigned request/);
  }
  assert.equal(await readSignatureState({async evaluate() { return unsigned; }}, {expectSignature: false}), unsigned);
  const page = {async waitForFunction() {}, async evaluate() { return signed; }};
  assert.equal(await readSignatureState(page, {expectSignature: true}), signed);
  await assert.rejects(readSignatureState({...page, async evaluate() { return {...signed, padVisible: true}; }},
    {name: 'signed', expectSignature: true}), /signed request/);
});

test('PDF stamp dimensions must belong to the same image, rather than a logo or another object', () => {
  const bytes = Buffer.from('%PDF-1.4\n1 0 obj << /Subtype /Image /Width 20 /Height 8 >> stream\nx\nendstream\nendobj\n'
    + '2 0 obj << /Width 173 /Height 53 >> stream\n/Subtype /Image\nendstream\nendobj\n'
    + '3 0 obj << /Subtype /Image /DecodeParms << /Predictor 15 /Columns 173 >> /Height 53 /Width 173 >> endobj');
  assert.deepEqual(describePdf(bytes).images, [{width: 20, height: 8}, {width: 173, height: 53}]);
});
