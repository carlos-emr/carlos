/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {parseConsultSignatureScenarios, readSignatureState, describePdf, assertSignaturePdf} = require('./consultation-signature-playwright-checks');

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

test('explicit signed fixtures cannot pass when the PDF contains only an unrelated logo', () => {
  const state = {imageWidth: 173, imageHeight: 53};
  assert.throws(() => assertSignaturePdf({images: [{width: 20, height: 8}]}, state, 'explicit'), /signature's dimensions/);
  assert.throws(() => assertSignaturePdf({images: []}, state, 'explicit'), /signature's dimensions/);
  assert.doesNotThrow(() => assertSignaturePdf({images: [{width: 20, height: 8}, {width: 173, height: 53}]}, state, 'explicit'));
});

/** Execute the JSP's actual signature handlers against a small image/controls DOM. */
function signatureControls(signatureImg, newSignature, retryManualSignature = false) {
  const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp'), 'utf8');
  const elements = {
    signatureImg: {value: signatureImg}, newSignature: {value: newSignature},
    signatureProviderNo: {value: '800001'}, signatureImgTag: {naturalWidth: 500, naturalHeight: 100, getAttribute: () => '/existing-provider-preview'},
    signatureFrame: {style: {}}, signatureShow: {style: {}},
  };
  const context = vm.createContext({document: {getElementById: id => elements[id]}, counter: 0,
    isSignatureSaved: false, encodeURIComponent});
  for (const name of ['isStoredSignatureId', 'hasPendingManualSignature', 'updateSignatureProvider', 'showSignatureImage']) {
    const start = source.indexOf(`function ${name}(`);
    assert.ok(start > 0, `Missing ${name}`);
    let depth = 0;
    let end = source.indexOf('{', start);
    do {
      if (source[end] === '{') depth++;
      if (source[end] === '}') depth--;
      end++;
    } while (depth > 0 && end < source.length);
    const code = source.slice(start, end).replace(/<%=[\s\S]*?%>/g, expression => {
      if (expression.includes('imageUrl')) return '/carlos/imageRenderingServlet?source=signature_preview&signatureRequestId=8000011792450000000';
      if (expression.includes('storedImgUrl')) return '/carlos/imageRenderingServlet?source=signature_stored&digitalSignatureId=';
      if (expression.includes('request.getContextPath()')) return '/carlos';
      if (expression.includes('retryManualSignature')) return String(retryManualSignature);
      throw new Error(`Unexpected JSP expression in ${name}`);
    });
    vm.runInContext(code, context);
  }
  return {elements, context};
}

test('a pending drawing loads its preview and survives provider initialization in manual mode', () => {
  const {elements, context} = signatureControls('8000011792450000000', 'true');
  context.showSignatureImage();
  assert.match(elements.signatureImgTag.src, /source=signature_preview.*8000011792450000000/);
  elements.signatureImgTag.onload();
  assert.equal(elements.newSignature.value, 'true');
  assert.equal(elements.signatureShow.style.display, 'block');
  assert.equal(elements.signatureFrame.style.display, 'none');
  assert.equal(context.isSignatureSaved, true);
  context.updateSignatureProvider('800002');
  assert.match(elements.signatureImgTag.src, /source=signature_preview/);
  assert.equal(elements.signatureImg.value, '8000011792450000000');
  assert.equal(elements.newSignature.value, 'true');
});

test('an uncaptured retry or failed preview keeps the manual pad available and is not marked signed', () => {
  for (const missing of ['placeholder', 'error']) {
    const {elements, context} = signatureControls('8000011792450000000', 'true');
    context.showSignatureImage();
    if (missing === 'placeholder') {
      elements.signatureImgTag.naturalWidth = elements.signatureImgTag.naturalHeight = 1;
      elements.signatureImgTag.onload();
    } else elements.signatureImgTag.onerror();
    assert.equal(elements.newSignature.value, 'true');
    assert.equal(elements.signatureFrame.style.display, 'block');
    assert.equal(elements.signatureShow.style.display, 'none');
    assert.equal(context.isSignatureSaved, false);
  }
});

test('an unsigned manual retry keeps the pad without requesting a missing preview or a stamp', () => {
  const {elements, context} = signatureControls('', 'true', true);
  context.showSignatureImage();
  assert.equal(elements.signatureImgTag.src, undefined);
  assert.equal(elements.newSignature.value, 'true');
  assert.equal(elements.signatureFrame.style.display, 'block');
  assert.equal(elements.signatureShow.style.display, 'none');
  assert.equal(context.isSignatureSaved, false);
});

test('stored signatures and provider stamps retain their respective preview sources', () => {
  for (const [id, source] of [['25', 'source=signature_stored&digitalSignatureId=25'], ['', 'providerSignatureImage?providerNo=800001']]) {
    const {elements, context} = signatureControls(id, 'false');
    context.showSignatureImage();
    assert.ok(elements.signatureImgTag.src.includes(source));
    elements.signatureImgTag.onload();
    assert.equal(elements.newSignature.value, 'false');
    assert.equal(elements.signatureShow.style.display, 'block');
    assert.equal(elements.signatureFrame.style.display, 'none');
  }
});
