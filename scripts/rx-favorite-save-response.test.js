/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/EditFavorites2.jsp', 'utf8');
const start = jsp.indexOf('            function ajaxUpdateRow(rowId)');
const end = jsp.indexOf('            function deleteRow(rowId)', start);
assert.ok(start > 0 && end > start, 'Favorite save handler is available');
const source = jsp.slice(start, end).replaceAll('${carlos:forJavaScript(ctx)}', '/carlos');

function harness(fetchResponse) {
  const values = { FavoriteId: '42', FavoriteName: "O'Neil & favorite", CustomName: 'Synthetic drug',
    TakeMin: '1', TakeMax: '1', FrequencyCode: 'OD', Duration: '7', DurationUnit: 'W',
    Quantity: '7', Repeat: '0', Nosubs: '', Prn: '', Special: 'Synthetic instructions' };
  const form = Object.fromEntries(Object.entries(values).map(([name, value]) => [`fld${name}3`, { value, checked: false }]));
  form.customInstr3 = { checked: false };
  const banner = { style: { display: 'block' } }, requests = [], alerts = [], errors = [];
  const context = {
    URLSearchParams, alert: message => alerts.push(message), console: { error: message => errors.push(message) },
    document: { forms: { DispForm: form }, getElementById: id => { assert.equal(id, 'saveSuccess_3'); return banner; },
      querySelector: () => ({ value: 'synthetic-csrf' }) },
    fetch: (url, options) => { requests.push({ url, options }); return fetchResponse(); },
  };
  vm.runInNewContext(source, context);
  return { save: () => context.ajaxUpdateRow(3), banner, requests, alerts, errors, form };
}

test('favorite save hides stale success and waits for response completion before showing saved', async () => {
  let finishBody, bodyReads = 0;
  const body = new Promise(resolve => { finishBody = resolve; });
  const s = harness(async () => ({ status: 204, redirected: false, text: () => { bodyReads++; return body; } }));
  const saving = s.save();
  assert.equal(s.banner.style.display, 'none');
  await Promise.resolve();
  assert.equal(bodyReads, 1);
  assert.equal(s.banner.style.display, 'none', 'headers alone do not establish completed success');
  const request = s.requests[0];
  assert.equal(request.options.method, 'post');
  assert.equal(request.options.headers['CSRF-TOKEN'], 'synthetic-csrf');
  assert.equal(new URLSearchParams(request.options.body).get('favoriteName'), "O'Neil & favorite");
  finishBody('');
  await saving;
  assert.equal(s.banner.style.display, 'block');
  assert.deepEqual(s.alerts, []);
});

for (const [status, redirected] of [[200, true], [200, false], [204, true], [400, false], [403, false], [404, false], [409, false], [500, false]]) {
  test(`favorite save rejects HTTP ${status}, redirected=${redirected}, without retaining prior success`, async () => {
    let consumed = false;
    const s = harness(async () => ({ status, redirected, text: async () => { consumed = true; return '<html>Not a save confirmation</html>'; } }));
    await s.save();
    assert.equal(consumed, true);
    assert.equal(s.banner.style.display, 'none');
    assert.deepEqual(s.alerts, [`Server Error ${status}`]);
  });
}

for (const failure of ['network', 'response body']) {
  test(`favorite ${failure} failure leaves a visible error and no stale saved banner`, async () => {
    const rejected = () => Promise.reject(new Error('synthetic transfer interruption'));
    const s = harness(failure === 'network' ? rejected : async () => ({ status: 204, redirected: false, text: rejected }));
    await s.save();
    assert.equal(s.banner.style.display, 'none');
    assert.equal(s.errors.length, 1);
    assert.deepEqual(s.alerts, ['An error occurred while saving. Please refresh and try again.']);
  });
}

test('invalid editing values hide prior saved confirmation without sending a request', async () => {
  const s = harness(() => { throw new Error('must not send'); });
  s.form.fldFavoriteName3.value = '';
  await s.save();
  assert.equal(s.banner.style.display, 'none');
  assert.equal(s.requests.length, 0);
  assert.deepEqual(s.alerts, ['Please enter a favorite name.']);
});
