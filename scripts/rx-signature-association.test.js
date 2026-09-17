/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp'), 'utf8');
const flush = () => new Promise(setImmediate);
const confirmed = () => ({ ok: true, redirected: false, headers: { get: () => 'written' } });
function setup() {
  const requests = [];
  const warning = { hidden: true };
  let disabled;
  let refreshes = 0;
  const context = vm.createContext({ URL, Promise,
    window: { location: { href: 'https://127.0.0.1/carlos/rx/ViewScript2' } },
    document: { getElementById: () => warning },
    signatureAssociationSequence: 0, signatureAssociationQueue: Promise.resolve(),
    signatureAssociationPending: false, signatureAssociationFailed: false,
    isSignatureSaved: false, hasStoredSignature: false,
    faxSubmissionPending: false, faxQueued: false, faxSubmissionUncertain: false,
    faxPreviewReloading: false, hasPreview: true, hasFaxNumber: true,
    hasFaxSenderAccount: true, canFaxScript: true,
    cancelPendingFax() {}, getCsrfToken: () => 'fixture',
    setFaxControlsDisabled(value) { disabled = value; },
    refreshImage() { refreshes++; },
    fetch(url, options) { return new Promise((resolve, reject) => requests.push({ url, options, resolve, reject })); },
  });
  for (const [name, next] of [['shouldDisableFaxControls', 'resetFailedFaxSubmission'],
    ['updateSignatureAssociationControls', 'associateSavedSignature'],
    ['associateSavedSignature', 'setDigitalSignatureToRx'],
    ['setDigitalSignatureToRx', 'toggleFaxButtons']]) {
    const start = jsp.indexOf(`function ${name}(`);
    const end = jsp.indexOf(`function ${next}(`, start + 1);
    assert(start >= 0 && end > start);
    vm.runInContext(jsp.slice(start, end).replace(/<%[\s\S]*?%>/g, '/carlos'), context);
  }
  const save = (id) => context.associateSavedSignature({ storedImageUrl: `/image?digitalSignatureId=${id}` }, '45');
  return { context, requests, warning, save, disabled: () => disabled, refreshes: () => refreshes };
}

test('fax remains locked until the signature association is explicitly confirmed', async () => {
  const s = setup();
  const pending = s.save('12');
  assert.equal(s.disabled(), true);
  assert.equal(s.context.isSignatureSaved, false);
  await flush();
  assert.equal(s.requests.length, 1);
  s.requests[0].resolve(confirmed());
  await pending;
  assert.equal(s.context.isSignatureSaved, true);
  assert.equal(s.context.hasStoredSignature, true);
  assert.equal(s.disabled(), false);
  assert.equal(s.refreshes(), 1);
});

for (const scenario of ['no-preview', 'missing-frame', 'loading-frame', 'missing-fields', 'inaccessible-frame', 'ready']) {
  test(`confirmed signature remains saved with the real preview refresh (${scenario})`, async () => {
    const s = setup();
    const signature = {};
    const imageField = {};
    s.context.hasPreview = scenario !== 'no-preview';
    s.context.counter = 0;
    s.context.frames = {};
    if (scenario === 'loading-frame') s.context.frames.preview = { document: null };
    if (scenario === 'missing-fields') s.context.frames.preview = { document: { getElementById: () => null } };
    if (scenario === 'inaccessible-frame') s.context.frames.preview = { get document() { throw new Error('fixture access denial'); } };
    if (scenario === 'ready') s.context.frames.preview = { document: {
      getElementById: id => id === 'signature' ? signature : id === 'imgFile' ? imageField : null,
    } };
    const start = jsp.indexOf('function refreshImage(');
    const end = jsp.indexOf('function sendFax(', start);
    assert(start >= 0 && end > start);
    vm.runInContext(jsp.slice(start, end).replace(/<%[\s\S]*?%>/g, '/carlos'), s.context);
    const pending = s.save('12');
    await flush();
    s.requests[0].resolve(confirmed());
    await pending;
    assert.equal(s.context.isSignatureSaved, true);
    assert.equal(s.context.signatureAssociationFailed, false);
    assert.equal(s.context.signatureAssociationPending, false);
    if (scenario === 'no-preview') assert.equal(s.disabled(), true, 'no preview still cannot be faxed');
    if (scenario === 'ready') {
      assert.match(signature.src, /rand=1$/);
      assert.match(imageField.value, /signature_/);
    }
  });
}

for (const failure of ['http', 'redirect', 'missing-marker', 'network']) {
  test(`signature ${failure} failure keeps fax locked even with an earlier stored signature`, async () => {
    const s = setup();
    s.context.hasStoredSignature = true;
    const pending = s.save('12');
    await flush();
    if (failure === 'network') s.requests[0].reject(new Error('fixture'));
    else s.requests[0].resolve({ ok: failure !== 'http', redirected: failure === 'redirect',
      headers: { get: () => failure === 'missing-marker' ? null : 'written' } });
    await pending;
    assert.equal(s.context.isSignatureSaved, false);
    assert.equal(s.context.signatureAssociationFailed, true);
    assert.equal(s.disabled(), true);
    assert.equal(s.warning.hidden, false);
  });
}

test('repeated signature saves are serialized and only the latest completion unlocks fax', async () => {
  const s = setup();
  const first = s.save('12');
  const last = s.save('13');
  await flush();
  assert.equal(s.requests.length, 1);
  s.requests[0].resolve(confirmed());
  await first;
  await flush();
  assert.equal(s.requests.length, 2);
  assert.equal(s.disabled(), true);
  assert.match(s.requests[1].options.body, /digitalSignatureId=13/);
  s.requests[1].resolve(confirmed());
  await last;
  assert.equal(s.disabled(), false);
  assert.equal(s.refreshes(), 1);
});

test('invalid signature identifiers fail closed without sending an association request', async () => {
  const s = setup();
  await s.save('0');
  assert.equal(s.requests.length, 0);
  assert.equal(s.disabled(), true);
  assert.equal(s.warning.hidden, false);
});

test('an in-flight or already queued fax cannot have its signature association changed', async () => {
  for (const flag of ['faxSubmissionPending', 'faxQueued', 'faxSubmissionUncertain']) {
    const s = setup();
    s.context[flag] = true;
    assert.equal(await s.save('12'), false);
    assert.equal(s.requests.length, 0);
  }
});

test('direct fax submission returns before touching the preview while signature save is pending or failed', () => {
  for (const flag of ['signatureAssociationPending', 'signatureAssociationFailed']) {
    const s = setup();
    s.context[flag] = true;
    s.context.document.getElementById = () => { throw new Error('must not touch fax preview'); };
    const start = jsp.indexOf('function sendFax(');
    const end = jsp.indexOf('function unloadMess(', start);
    assert(start >= 0 && end > start);
    vm.runInContext(jsp.slice(start, end).replace(/<%[\s\S]*?%>/g, 'fixture'), s.context);
    assert.equal(s.context.sendFax(false), false);
  }
});
