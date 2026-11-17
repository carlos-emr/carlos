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

  const probeRequests = [];
  const context = {
    cfg_isrc: IMAGE_SRC,
    ImgArray: [],
    inputs: {},
    cacheData: {},
    console: { log() {} },
    // The per-provider existence probe. A request never answers on its own here: tests that drive
    // it call respond() or fail() on the recorded request, the way the browser calls onload or
    // onerror. Tests that only need the "no signature on file" branch set carlosProviderStampProbe
    // directly, which is what a 204 answer does.
    probeRequests,
    XMLHttpRequest: function XMLHttpRequest() {
      const request = { method: '', url: '', sent: false };
      probeRequests.push(request);
      this.status = 0;
      this.open = (method, url) => { request.method = method; request.url = url; };
      this.send = () => { request.sent = true; };
      request.respond = (status) => { this.status = status; if (this.onload) { this.onload(); } };
      request.fail = () => { if (this.onerror) { this.onerror(); } };
    },
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

// Every freshly picked stamp carries a VALUELESS marker that bindStampFallbacks() consumes, so a
// stamp that cannot load is handled whichever file it is. The fallback filename rides module state,
// never the markup (it would otherwise be DOM text flowing into an image URL).
const stamp = (file) => `<img src="${IMAGE_SRC}${file}" data-carlos-stamp="1" width="200" height="100" />`;

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

test('the stamp carries a fallback marker so a missing signature still degrades', () => {
  // The probe cannot always answer first: on a form_html predating the hidden identity inputs the
  // provider number only arrives with the APCache lookup, so the first Stamp click can be issued
  // before any probe completes. The marker is what bindStampFallbacks() turns into a load-error
  // handler, making the stamps.js / stamp.png fallback a guarantee rather than a race.
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  ctx.cacheData = { current_user: 'Jones, Amy' };
  ctx.ImgArray = ['Jones|amy_sig.png'];
  const html = ctx.pickStamp();
  assert.match(html, /consult_sig_999998\.png/);
  assert.match(html, /data-carlos-stamp="1"/);
  // The filename must NOT be in the markup: bindStampFallbacks() scans the whole editor document,
  // which after a reopen holds the restored saved letter, so a filename read back out of an
  // attribute would be attacker-influenced DOM text flowing into an image URL.
  assert.ok(!html.includes('amy_sig.png'),
    'the fallback filename must not round-trip through the document');
  assert.equal(ctx.carlosPendingStampFallback, 'amy_sig.png');
});

test('a stamp that already fell back is still marked but has nothing further to try', () => {
  // The marker lets bindStampFallbacks() remove a clinic stamp that does not exist either, instead
  // of leaving a broken image in the letter; with no pending filename it never retries.
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  ctx.carlosProviderStampProbe = { file: 'consult_sig_999998.png', missing: true };
  ctx.carlosPendingStampFallback = 'stale.png';
  const html = ctx.pickStamp();
  assert.equal(html, stamp('stamp.png'));
  assert.equal(ctx.carlosPendingStampFallback, '');
});

test('the signer role drives both the stamp and the salutation name', () => {
  // These were decided separately, which printed one clinician's signature above another's name.
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998', doctor_provider_no: '111111' };
  assert.equal(ctx.stampSignerRole(), 'user');
  assert.equal(ctx.stampProviderNumber(), '999998');

  ctx.inputs = { user_ohip_no: '42', user_id: '999998', doctor_provider_no: '111111' };
  assert.equal(ctx.stampSignerRole(), 'mrp');
  assert.equal(ctx.stampProviderNumber(), '111111');

  ctx.inputs = {};
  ctx.cacheData = {};
  assert.equal(ctx.stampSignerRole(), '');
  assert.equal(ctx.stampProviderNumber(), '');
});

test('the existence probe asks the signature endpoint instead of loading the image', () => {
  // Loading consult_sig_<n>.png to learn whether it exists answered 404 on every letter opened for
  // a signer with no signature on file: a failed request and a console error on each open. The
  // provider signature endpoint reads the same eForm image directory and answers 204 for that.
  const ctx = loadStampFunctions();
  const images = [];
  ctx.Image = function Image() { images.push(this); };
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  ctx.probeProviderStamp();
  ctx.probeProviderStamp();
  assert.deepEqual(ctx.probeRequests.map((request) => [request.method, request.url, request.sent]),
    [['GET', '../provider/providerSignatureImage?providerNo=999998', true]],
    'one request per signer, to the endpoint');
  assert.equal(images.length, 0, 'the probe must not load the signature image itself');
});

test('a signer with no signature on file gets the clinic stamp outright', () => {
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  ctx.probeProviderStamp();
  ctx.probeRequests[0].respond(204);
  assert.equal(ctx.pickStamp(), stamp('stamp.png'));
});

test('a signature on file keeps the signer\'s own stamp', () => {
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  ctx.probeProviderStamp();
  ctx.probeRequests[0].respond(200);
  assert.equal(ctx.pickStamp(), stamp('consult_sig_999998.png'));
});

test('a refused or failed probe leaves the choice to the stamp image itself', () => {
  // Only "none on file" is an answer about the file. A refusal, a server error or a lost
  // connection is not, so the signer's own stamp is still tried, and bindStampFallbacks() handles
  // it if it does not load.
  for (const answer of [(request) => request.respond(403), (request) => request.respond(500),
    (request) => request.fail()]) {
    const ctx = loadStampFunctions();
    ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
    ctx.probeProviderStamp();
    answer(ctx.probeRequests[0]);
    assert.equal(ctx.pickStamp(), stamp('consult_sig_999998.png'));
  }
});

test('an answer about a signer the letter no longer uses is ignored', () => {
  // On a form_html without the hidden identity inputs the signer only becomes known with the
  // APCache lookup, which probes again; a late answer about the first signer must not decide the
  // second signer's stamp.
  const ctx = loadStampFunctions();
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  ctx.probeProviderStamp();
  ctx.inputs = { user_ohip_no: '54321', user_id: '123456' };
  ctx.probeProviderStamp();
  ctx.probeRequests[0].respond(204);
  assert.equal(ctx.pickStamp(), stamp('consult_sig_123456.png'));
});

test('the stamp URL falls back to the extensionless route when cfg_isrc is unset', () => {
  // cfg_isrc defaults to the empty string; an empty base would make the stamp a bare relative
  // filename resolved against the action path.
  const ctx = loadStampFunctions();
  ctx.cfg_isrc = '';
  ctx.inputs = { user_ohip_no: '54321', user_id: '999998' };
  assert.equal(ctx.pickStamp(),
    '<img src="../eform/displayImage?imagefile=consult_sig_999998.png"'
    + ' data-carlos-stamp="1" width="200" height="100" />');
});

test('the upgrade migration applies the same form_html edit as the updates/ script', () => {
  // The updates/ script is only replayed by fresh demo-data loads and the O19 importer; a package
  // upgrade runs Flyway alone. An install whose letter predates the inputs therefore depends on
  // the Flyway copy, and the two must not drift: a different anchor or guard would patch one path
  // and not the other.
  const root = path.join(__dirname, '..', 'database', 'mysql');
  const updateStatement = (file) => {
    const sql = fs.readFileSync(path.join(root, file), 'utf8');
    const match = sql.match(/UPDATE eform[\s\S]*?;/);
    assert.ok(match, `${file} has no UPDATE eform statement`);
    return match[0].replace(/\s+/g, ' ');
  };
  const legacy = updateStatement('updates/update-2026-09-20-rtl-provider-stamp-fields.sql');
  const flyway = updateStatement('migration/common/V1.0.41__rtl_provider_stamp_fields.sql');
  assert.equal(flyway, legacy);
  for (const id of ['user_id', 'user_ohip_no', 'doctor_provider_no']) {
    assert.ok(flyway.includes(`id="${id}"`), `migration does not add ${id}`);
  }
  assert.ok(flyway.includes(`NOT LIKE '%id="user_ohip_no"%'`), 'migration is not idempotent');
});

test('the letterhead, stamp and closing-salutation lookups only request configured AP keys', () => {
  // efmformapconfig_lookup reports a key apconfig.xml does not define as a "could not be filled
  // in" banner; the dead legacy "stamp_name" key put that banner on every Stamp click.
  const source = fs.readFileSync(EDIT_CONTROL_2_JS, 'utf8');
  const apconfig = fs.readFileSync(path.join(__dirname,
    '../src/main/resources/oscar/eform/apconfig.xml'), 'utf8');
  const configured = new Set(Array.from(apconfig.matchAll(/<ap-name>([^<]+)<\/ap-name>/g), (m) => m[1].trim()));
  for (const name of ['letterhead', 'stamp', '_ClosingSalutation']) {
    const mapping = source.match(new RegExp(`name: "${name}",\\s*values: \\[([^\\]]*)\\]`));
    assert.ok(mapping, `${name} mapping not found in editControl2.js`);
    const keys = Array.from(mapping[1].matchAll(/"([^"]+)"/g), (m) => m[1]);
    assert.ok(keys.length > 0, `${name} mapping lists no keys`);
    for (const key of keys) {
      assert.ok(configured.has(key), `${name} requests AP key "${key}", which apconfig.xml does not define`);
    }
  }
});

// ---------------------------------------------------------------------------------------------
// bindStampFallbacks(): what happens when an inserted stamp image does not load.
//
// A clinic that has not uploaded a signature for the signer, and has no stamps.js entry or shared
// stamp.png, used to get a broken-image box (two 404s) in the letter every time Stamp or Closing
// Salutation was pressed. The stamp is now swapped once for the clinic fallback and, if that does
// not load either, taken out of the letter with a notice saying where a signature can be added.
// ---------------------------------------------------------------------------------------------

function loadStampBinding() {
  const ctx = loadStampFunctions();
  const source = fs.readFileSync(EDIT_CONTROL_2_JS, 'utf8');
  const start = source.indexOf('function stampImagesIn(');
  const end = source.indexOf('function block(blockElements)');
  assert.ok(start >= 0 && end > start, 'bindStampFallbacks() was not found in editControl2.js');
  ctx.notices = [];
  ctx.showLetterStatus = (id, message, failed) => ctx.notices.push({ id, message, failed });
  vm.runInContext(source.slice(start, end), ctx);
  return ctx;
}

function fakeStampImage(file) {
  const listeners = {};
  const parent = {
    children: [],
    removeChild(child) { this.children = this.children.filter((node) => node !== child); child.parentNode = null; },
  };
  const image = {
    attributes: { 'data-carlos-stamp': '1' },
    complete: false,
    naturalWidth: 0,
    srcHistory: [`${IMAGE_SRC}${file}`],
    get src() { return this.srcHistory[this.srcHistory.length - 1]; },
    set src(value) { this.srcHistory.push(value); },
    removeAttribute(name) { delete this.attributes[name]; },
    addEventListener(type, listener) { (listeners[type] = listeners[type] || []).push(listener); },
    removeEventListener(type, listener) {
      listeners[type] = (listeners[type] || []).filter((candidate) => candidate !== listener);
    },
    fire(type) { (listeners[type] || []).slice().forEach((listener) => listener.call(image, { type })); },
    parentNode: parent,
  };
  parent.children.push(image);
  return image;
}

test('a provider stamp that does not load is swapped for the clinic stamp', () => {
  const ctx = loadStampBinding();
  ctx.carlosPendingStampFallback = 'amy_sig.png';
  const image = fakeStampImage('consult_sig_999998.png');

  ctx.bindStampFallbacks([image]);
  image.fire('error');

  assert.equal(image.src, `${IMAGE_SRC}amy_sig.png`);
  assert.ok(image.parentNode, 'the swapped stamp stays in the letter');
  assert.equal(image.attributes['data-carlos-stamp'], undefined, 'the marker never reaches the stored letter');
  assert.equal(ctx.carlosPendingStampFallback, '', 'the fallback is consumed by this insertion');
});

test('a stamp that cannot load at all is taken out of the letter and the reason shown', () => {
  const ctx = loadStampBinding();
  ctx.carlosPendingStampFallback = 'stamp.png';
  const image = fakeStampImage('consult_sig_999998.png');
  const parent = image.parentNode;

  ctx.bindStampFallbacks([image]);
  image.fire('error');
  image.fire('error');

  assert.deepEqual(image.srcHistory, [`${IMAGE_SRC}consult_sig_999998.png`, `${IMAGE_SRC}stamp.png`]);
  assert.equal(parent.children.length, 0, 'no broken image is left in the letter');
  assert.equal(ctx.notices.length, 1);
  assert.equal(ctx.notices[0].id, 'rtl-stamp-status');
  assert.equal(ctx.notices[0].failed, true);
  assert.match(ctx.notices[0].message, /Preferences > Signature Stamp/);
});

test('a stamp with nothing left to fall back to is removed on its first failure', () => {
  const ctx = loadStampBinding();
  ctx.carlosPendingStampFallback = '';
  const image = fakeStampImage('stamp.png');
  const parent = image.parentNode;

  ctx.bindStampFallbacks([image]);
  image.fire('error');

  assert.equal(image.srcHistory.length, 1, 'nothing is retried');
  assert.equal(parent.children.length, 0);
  assert.equal(ctx.notices.length, 1);
});

test('a stamp that already failed from the cache is handled at once', () => {
  const ctx = loadStampBinding();
  ctx.carlosPendingStampFallback = 'amy_sig.png';
  const image = fakeStampImage('consult_sig_999998.png');
  image.complete = true;

  ctx.bindStampFallbacks([image]);

  assert.equal(image.src, `${IMAGE_SRC}amy_sig.png`);
});

test('a stamp that loads clears an earlier missing-stamp notice', () => {
  const ctx = loadStampBinding();
  const image = fakeStampImage('consult_sig_999998.png');

  ctx.bindStampFallbacks([image]);
  image.fire('load');

  assert.deepEqual(ctx.notices, [{ id: 'rtl-stamp-status', message: '', failed: false }]);
  assert.ok(image.parentNode);
});

test('an untrusted fallback filename is never put in an image URL', () => {
  // The pending filename comes from ImgArray (stamps.js) or the stamp.png default; anything that is
  // not a plain basename is ignored and the stamp is removed instead of being pointed at it.
  const ctx = loadStampBinding();
  ctx.carlosPendingStampFallback = '../../etc/passwd';
  const image = fakeStampImage('consult_sig_999998.png');

  ctx.bindStampFallbacks([image]);
  image.fire('error');

  assert.equal(image.srcHistory.length, 1);
  assert.equal(image.parentNode, null);
});
