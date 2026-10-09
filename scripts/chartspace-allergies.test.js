/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const SOURCE_PATH = path.join(__dirname, '..', 'src', 'main', 'webapp', 'js', 'chartspace', 'chartspace-allergies.js');
const allergies = require(SOURCE_PATH);

function fakeResponse({ ok = true, status = 200, contentType = 'application/json;charset=UTF-8', body = {} } = {}) {
  return {
    ok,
    status,
    headers: { get: (name) => (name.toLowerCase() === 'content-type' ? contentType : null) },
    json: async () => body
  };
}

test('toViewModel maps the three server states', () => {
  const ok = allergies.toViewModel({
    status: 'OK',
    items: [{ description: 'Penicillin', severityCode: '3', reaction: 'Hives', startDate: '2020-03-15' }]
  });
  assert.equal(ok.state, 'OK');
  assert.deepEqual(ok.items, [
    { description: 'Penicillin', reaction: 'Hives', startDate: '2020-03-15', severityLevel: 'severe' }
  ]);
  assert.deepEqual(allergies.toViewModel({ status: 'EMPTY', items: [] }), { state: 'EMPTY', items: [] });
  assert.deepEqual(allergies.toViewModel({ status: 'NO_ACCESS', items: [] }), { state: 'NO_ACCESS', items: [] });
});

test('toViewModel turns anything unexpected into ERROR', () => {
  [null, undefined, '<!DOCTYPE html>', 42, {}, { status: 'WAT' }, { status: 'ERROR' }].forEach((input) => {
    assert.deepEqual(allergies.toViewModel(input), { state: 'ERROR', items: [] });
  });
});

test('toViewModel treats an OK response without a usable items array as ERROR', () => {
  assert.equal(allergies.toViewModel({ status: 'OK' }).state, 'ERROR');
  assert.equal(allergies.toViewModel({ status: 'OK', items: 'x' }).state, 'ERROR');
});

test('toViewModel maps severity codes and falls back to unknown', () => {
  const level = (code) => allergies.toViewModel({
    status: 'OK',
    items: [{ description: 'x', severityCode: code, reaction: '', startDate: '' }]
  }).items[0].severityLevel;
  assert.equal(level('3'), 'severe');
  assert.equal(level('2'), 'moderate');
  assert.equal(level('1'), 'mild');
  assert.equal(level('5'), 'none');
  assert.equal(level('4'), 'unknown');
  assert.equal(level(''), 'unknown');
  assert.equal(level(null), 'unknown');
  assert.equal(level(undefined), 'unknown');
});

test('toViewModel coerces missing row fields to empty strings', () => {
  const vm = allergies.toViewModel({ status: 'OK', items: [{ description: null }] });
  assert.deepEqual(vm.items[0], { description: '', reaction: '', startDate: '', severityLevel: 'unknown' });
});

test('exposes the block id', () => {
  assert.equal(allergies.id, 'allergies');
});

test('load resolves ERROR for non-JSON or non-ok responses and network failures', async () => {
  const error = { status: 'ERROR' };
  assert.deepEqual(
    await allergies.load('/carlos', 2, async () => fakeResponse({ contentType: 'text/html' })), error);
  assert.deepEqual(
    await allergies.load('/carlos', 2, async () => fakeResponse({ ok: false, status: 403 })), error);
  assert.deepEqual(
    await allergies.load('/carlos', 2, async () => { throw new Error('offline'); }), error);
  assert.deepEqual(
    await allergies.load('/carlos', 2, async () => ({
      ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => { throw new SyntaxError('bad'); }
    })), error);
});

test('load requests the read-only endpoint with the encoded id and returns the body', async () => {
  const calls = [];
  const body = { status: 'EMPTY', items: [] };
  const result = await allergies.load('/carlos', 2, async (url, init) => {
    calls.push({ url, init });
    return fakeResponse({ body });
  });
  assert.deepEqual(result, body);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/carlos/encounter/chartspace/allergies?demographicNo=2');
  assert.equal(calls[0].init.credentials, 'same-origin');
  assert.equal(calls[0].init.headers.Accept, 'application/json');
  assert.ok(!calls[0].init.method || calls[0].init.method === 'GET');
});

test('load encodes the demographic id in the query string', async () => {
  let url;
  await allergies.load('/carlos', '2&x=1', async (u) => { url = u; return fakeResponse(); });
  assert.equal(url, '/carlos/encounter/chartspace/allergies?demographicNo=2%26x%3D1');
});

// Behavioural render tests: load the block against the fake DOM. The module
// captures `window` as its root at require time, so it is re-required fresh.
const { FakeDocument, byTag, byClass } = require('./chartspace-fake-dom.js');

function withDom(fn) {
  const doc = new FakeDocument();
  global.window = { document: doc };
  delete require.cache[require.resolve(SOURCE_PATH)];
  try {
    return fn(require(SOURCE_PATH), doc);
  } finally {
    delete global.window;
    delete require.cache[require.resolve(SOURCE_PATH)];
  }
}

const I18N = {
  msgEmpty: 'No records',
  msgNoAccess: 'You do not have access to this section',
  msgError: 'This section could not be loaded',
  severity: { severe: 'Severe', moderate: 'Moderate', mild: 'Mild', none: 'No reaction', unknown: 'Unknown severity' }
};

test('render shows a markup-looking description as text and never creates elements from it', () => {
  withDom((block, doc) => {
    const body = doc.createElement('div');
    const payload = '<img src=x onerror=alert(1)>';
    block.render(body, block.toViewModel({
      status: 'OK', items: [{ description: payload, severityCode: '3', reaction: '<b>x</b>', startDate: '' }]
    }), I18N);
    const name = byClass(body, 'cs-allergy-name');
    assert.equal(name.length, 1);
    assert.equal(name[0].textContent, payload);
    assert.equal(byClass(body, 'cs-allergy-reaction')[0].textContent, '<b>x</b>');
    assert.equal(byTag(body, 'img').length, 0);
    assert.equal(byTag(body, 'b').length, 0);
  });
});

test('render gives every row its severity as a text label', () => {
  withDom((block, doc) => {
    const body = doc.createElement('div');
    block.render(body, block.toViewModel({
      status: 'OK',
      items: ['3', '2', '1', '5', '9'].map((code) => ({ description: 'd' + code, severityCode: code }))
    }), I18N);
    const rows = byTag(body, 'li');
    assert.equal(rows.length, 5);
    assert.deepEqual(rows.map((r) => byClass(r, 'cs-sev')[0].textContent),
      ['Severe', 'Moderate', 'Mild', 'No reaction', 'Unknown severity']);
  });
});

test('render replaces previous content and shows NO_ACCESS as text with a decorative icon', () => {
  withDom((block, doc) => {
    const body = doc.createElement('div');
    body.textContent = 'Loading…';
    block.render(body, { state: 'NO_ACCESS', items: [] }, I18N);
    assert.equal(body.textContent, I18N.msgNoAccess);
    const icons = byClass(body, 'cs-icon');
    assert.equal(icons.length, 1);
    assert.equal(icons[0].getAttribute('aria-hidden'), 'true');
    assert.equal(byClass(body, 'cs-state-no-access').length, 1);
  });
});

test('render source uses no innerHTML-style sinks (secondary static check)', () => {
  const source = fs.readFileSync(SOURCE_PATH, 'utf8');
  ['innerHTML', 'outerHTML', 'insertAdjacentHTML', 'document.write'].forEach((sink) => {
    assert.ok(!source.includes(sink), `source must not use ${sink}`);
  });
  assert.ok(source.includes('textContent'));
});
