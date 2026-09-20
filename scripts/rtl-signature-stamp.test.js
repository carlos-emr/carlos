/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */

/*
 * Rich Text Letter signature stamp selection.
 *
 * pickStamp() decides which image signs a letter. Before 2026.09 it knew only ImgArray -- the
 * hand-maintained "doctor|SignatureFile.png" list a clinic uploads as stamps.js -- and a single
 * shared stamp.png, so a multi-provider clinic without a stamps.js signed every letter with the
 * same image. It now prefers the provider's own consult_sig_<provider_no>.png, applying the same
 * delegation rule as sign() in visualEformEditor.jsp.
 *
 * The functions are executed out of the real asset rather than restated here: an assertion on the
 * source text would pass against a rewrite that no longer behaves this way.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const EDIT_CONTROL_2_JS = path.join(__dirname,
  '../src/main/webapp/WEB-INF/eform-assets/editControl2.js');
const IMAGE_SRC = '../eform/displayImage.do?imagefile=';

function loadStampFunctions() {
  const source = fs.readFileSync(EDIT_CONTROL_2_JS, 'utf8');
  const start = source.indexOf('\tvar MIN_BILLING_PROVIDER_OHIP_NO');
  const end = source.indexOf('\t// Flag read by efmshowform_data');
  assert.ok(start >= 0 && end > start, 'The signature stamp block was not found in editControl2.js');

  const context = {
    cfg_isrc: IMAGE_SRC,
    ImgArray: [],
    inputs: {},
    cacheData: {},
    console: { log() {} },
    // The per-provider existence probe. Never resolves on its own here; tests that care about the
    // "no signature on file" branch set carlosProviderStampProbe directly, which is what the real
    // onerror handler does.
    Image: function Image() { this.src = ''; },
  };
  context.document = {
    getElementById: (id) => (context.inputs[id] === undefined ? null : { value: context.inputs[id] }),
  };
  context.cache = {
    contains: (key) => Object.prototype.hasOwnProperty.call(context.cacheData, key),
    isEmpty: (key) => !context.cacheData[key],
    get: (key) => context.cacheData[key],
  };
  vm.createContext(context);
  vm.runInContext(source.slice(start, end), context);
  return context;
}

const stamp = (file) => `<img src="${IMAGE_SRC}${file}" width="200" height="100" />`;

test('a billing practitioner signs a letter with their own signature', () => {
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998', doctor_provider_no: '111111' };
  assert.equal(ctx.pickStamp(), stamp('consult_sig_999998.png'));
});

test('a non-billing account signs with the patient MRP signature', () => {
  // Residents, nurses, clerical staff and room/resource pseudo-providers are given ohip numbers
  // below the threshold precisely so they can hold a schedule without billing. A letter typed
  // under one of those logins is written under the MRP's direction.
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '42', user_id: '999998', doctor_provider_no: '111111' };
  assert.equal(ctx.pickStamp(), stamp('consult_sig_111111.png'));
});

test('the billing threshold is exclusive', () => {
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '1000', user_id: '999998', doctor_provider_no: '111111' };
  assert.equal(ctx.pickStamp(), stamp('consult_sig_111111.png'));
});

test('the AP cache supplies the identity when the form carries no hidden inputs', () => {
  // An install whose stored form_html predates update-2026-09-20-rtl-provider-stamp-fields.sql,
  // or a clinic that customized the Rich Text Letter row. The same AP keys are listed on the
  // "stamp" and "_ClosingSalutation" mappings so a lookup fetches them.
  const ctx = loadStampFunctions();
  ctx.cacheData = {
    current_user_ohip_no: '54321', current_user_id: '999998', doctor_provider_no: '111111',
  };
  assert.equal(ctx.pickStamp(), stamp('consult_sig_999998.png'));
});

test('a provider with no signature on file falls back to the clinic stamps.js entry', () => {
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  ctx.cacheData = { current_user: 'Jones, Amy' };
  ctx.ImgArray = ['Jones|amy_sig.png'];
  ctx.carlosProviderStampProbe = { file: 'consult_sig_999998.png', missing: true };
  assert.equal(ctx.pickStamp(), stamp('amy_sig.png'));
});

test('stamps.js prefers the current user over the patient MRP', () => {
  const ctx = loadStampFunctions();
  ctx.cacheData = { doctor: 'Smith, John', current_user: 'Jones, Amy' };
  ctx.ImgArray = ['Jones|amy_sig.png', 'Smith|john_sig.png'];
  assert.equal(ctx.pickStamp(), stamp('amy_sig.png'));
});

test('an install with neither signatures nor stamps.js still gets the shared stamp', () => {
  // The pre-2026.09 behaviour, which has to keep working untouched.
  const ctx = loadStampFunctions();
  assert.equal(ctx.pickStamp(), stamp('stamp.png'));
});

test('a padded provider number resolves the same as the server would', () => {
  // ConsultationSignatureService.isNumericProviderNo() trims before testing \d+. If the client
  // did not, a padded AP output would fall back to stamp.png for a provider who has a signature
  // file the server is perfectly willing to serve.
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: ' 54321 ', user_id: ' 999998 ' };
  assert.equal(ctx.pickStamp(), stamp('consult_sig_999998.png'));
});

test('a provider number that is not digits never reaches the image URL', () => {
  // The value becomes an imagefile= query parameter that the eForm image route treats as a path
  // component. Anything but digits means the AP returned something unexpected.
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '../../etc/passwd' };
  assert.equal(ctx.pickStamp(), stamp('stamp.png'));
});

test('the existence probe asks for the file the stamp will use', () => {
  const ctx = loadStampFunctions();
  const requested = [];
  ctx.Image = function Image() {
    Object.defineProperty(this, 'src', { set: (value) => requested.push(value) });
  };
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  ctx.probeProviderStamp();
  assert.deepEqual(requested, [`${IMAGE_SRC}consult_sig_999998.png`]);
});

test('the stamp URL falls back to the extensionless route when cfg_isrc is unset', () => {
  // cfg_isrc defaults to the empty string; an empty base would make the stamp a bare relative
  // filename resolved against the action path.
  const ctx = loadStampFunctions();
  ctx.cfg_isrc = '';
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  assert.equal(ctx.pickStamp(),
    '<img src="../eform/displayImage?imagefile=consult_sig_999998.png" width="200" height="100" />');
});
