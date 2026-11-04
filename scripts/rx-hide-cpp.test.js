/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// RxHideCpp (share/javascript/rx-hide-cpp.js): the medication profile's Hide from CPP checkbox
// reports a refused or failed save and shows the stored state again (#5001).
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const RxHideCpp = require('../src/main/webapp/share/javascript/rx-hide-cpp');

function harness(answer, { checked = true } = {}) {
  const checkbox = { checked, disabled: false };
  const requests = [];
  const alerts = [];
  const reloads = [];
  const saving = RxHideCpp.save({
    checkbox, url: '/carlos/rx/hideCpp', prescriptId: '42', csrfToken: 'synthetic-csrf',
    failureMessage: 'not saved', alert: message => alerts.push(message), reload: () => reloads.push(checkbox.checked),
    fetch: (url, options) => { requests.push({ url, options }); return answer(); },
  });
  return { checkbox, requests, alerts, reloads, saving };
}

const ok = (text = 'ok\n') => async () => ({ ok: true, status: 200, redirected: false, text: async () => text });

test('a stored change keeps the new state and raises nothing', async () => {
  const s = harness(ok());
  assert.equal(s.checkbox.disabled, true, 'the box is held while the save is in flight');
  assert.equal(await s.saving, true);
  assert.deepEqual(s.checkbox, { checked: true, disabled: false });
  assert.deepEqual(s.alerts, []);
  assert.deepEqual(s.reloads, [], 'a stored change needs no re-read');
  const [request] = s.requests;
  assert.equal(request.url, '/carlos/rx/hideCpp');
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.headers['CSRF-TOKEN'], 'synthetic-csrf');
  assert.equal(request.options.credentials, 'same-origin');
  assert.deepEqual(Object.fromEntries(new URLSearchParams(request.options.body)), { method: 'update', prescriptId: '42', value: 'true' });
});

test('unhiding posts false and keeps the box clear', async () => {
  const s = harness(ok(), { checked: false });
  assert.equal(await s.saving, true);
  assert.equal(s.checkbox.checked, false);
  assert.equal(new URLSearchParams(s.requests[0].options.body).get('value'), 'false');
});

const failures = {
  'HTTP 403': async () => ({ ok: false, status: 403, redirected: false, text: async () => 'Forbidden' }),
  'HTTP 500': async () => ({ ok: false, status: 500, redirected: false, text: async () => '' }),
  'a redirect to the login page': async () => ({ ok: true, status: 200, redirected: true, text: async () => '<html>' }),
  'an unexpected body': ok('<html>error</html>'),
  'a network failure': async () => { throw new TypeError('Failed to fetch'); },
  'a fetch that throws synchronously': () => { throw new TypeError('blocked'); },
};
for (const [name, answer] of Object.entries(failures)) {
  for (const checked of [true, false]) {
    test(`${name} restores the stored ${checked ? 'unchecked' : 'checked'} state and says so`, async () => {
      const s = harness(answer, { checked });
      assert.equal(await s.saving, false);
      assert.deepEqual(s.checkbox, { checked: !checked, disabled: false });
      assert.deepEqual(s.alerts, ['not saved']);
      // A lost or unreadable answer can follow a committed save: the list is re-read from the database.
      assert.deepEqual(s.reloads, [!checked], 'the list is re-read once, after the box shows the likely stored state');
    });
  }
}

test('ListDrugs.jsp saves every Hide CPP box through RxHideCpp with a translated message', () => {
  const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/ListDrugs.jsp', 'utf8');
  assert.match(jsp, /RxHideCpp\.save\(\{/);
  assert.match(jsp, /reload: typeof refreshDrugProfile === 'function' \? refreshDrugProfile : null/);
  assert.match(jsp, /failureMessage: '\$\{carlos:forJavaScript\(msg_hideCppSaveFailed\)\}'/);
  assert.match(jsp, /<fmt:message key="SearchDrug\.msgHideCPPSaveFailed" var="msg_hideCppSaveFailed"\/>/);
  assert.doesNotMatch(jsp, /fetch\('\$\{carlos:forJavaScript\(ctx\)\}\/rx\/hideCpp'/, 'no fire-and-forget fetch remains');
  const page = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp', 'utf8');
  assert.match(page, /share\/javascript\/rx-hide-cpp\.js/);
});
