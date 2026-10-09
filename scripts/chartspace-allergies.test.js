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

test('render never writes markup: no innerHTML-style sinks in the source', () => {
  const source = fs.readFileSync(SOURCE_PATH, 'utf8');
  ['innerHTML', 'outerHTML', 'insertAdjacentHTML', 'document.write'].forEach((sink) => {
    assert.ok(!source.includes(sink), `source must not use ${sink}`);
  });
  assert.ok(source.includes('textContent'));
});
