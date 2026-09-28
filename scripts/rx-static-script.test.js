/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/StaticScript2.jsp'), 'utf8');
const start = jsp.indexOf('async function staticScriptCsrfToken(');
assert.ok(start >= 0);
const source = jsp.slice(start, jsp.indexOf('</script>', start))
  .replace(/<c:set\b[\s\S]*?<\/c:set>/g, '')
  .replace(/<carlos:encode\b[\s\S]*?\/>/g, 'fixture')
  .replace(/<%=[\s\S]*?%>/g, '/carlos')
  .replace(/\$\{[^}]+\}/g, '/carlos');
function setup(prompt = 'Synthetic favorite') {
  const requests = [], alerts = [], location = { href: 'history' };
  const context = vm.createContext({
    window: { prompt: () => prompt, location }, location,
    document: { querySelector: () => ({ value: 'csrf-fixture' }) },
    alert: message => alerts.push(message), oscarLog: () => {},
    fetch: (url, options) => new Promise((resolve, reject) => requests.push({ url, ...options, resolve, reject })),
  });
  vm.runInContext(source, context);
  return { context, requests, alerts, location };
}
test('cancelling the favorite prompt does not send a request', async () => {
  const s = setup(null);
  await s.context.addFavorite2(1, 'Drug');
  assert.equal(s.requests.length, 0);
  assert.equal(s.location.href, 'history');
});
for (const success of [false, true]) {
  test(`favorite navigation requires a successful protected request (${success})`, async () => {
    const s = setup("Quote ' & value");
    const done = s.context.addFavorite2(1, 'Drug');
    await Promise.resolve();
    assert.equal(s.requests.length, 1);
    assert.equal(s.requests[0].method, 'POST');
    assert.equal(s.requests[0].headers['CSRF-TOKEN'], 'csrf-fixture');
    assert.equal(new URLSearchParams(s.requests[0].body).get('favoriteName'), "Quote ' & value");
    assert.equal(s.location.href, 'history');
    s.requests[0].resolve({ ok: success, status: success ? 200 : 403, text: async () => '' });
    await done;
    assert.equal(s.alerts.length, success ? 0 : 1);
    assert.equal(s.location.href === 'history', !success);
  });
}
for (const outcome of ['success', 'forbidden', 'redirect', 'network']) {
  test(`re-prescribe uses one atomic protected write and stays on failure (${outcome})`, async () => {
    const s = setup();
    const done = s.context.reRxDrugSearch3(7);
    await Promise.resolve();
    assert.equal(s.requests.length, 1);
    const request = s.requests[0];
    assert.match(request.url, /\/rx\/rePrescribe2\?method=saveReRxDrugIdToStash$/);
    assert.equal(request.method, 'POST');
    assert.equal(request.headers['CSRF-TOKEN'], 'csrf-fixture');
    assert.equal(new URLSearchParams(request.body).get('drugId'), '7');
    assert.equal(new URLSearchParams(request.body).get('demographicNo'), 'fixture');
    assert.equal(s.location.href, 'history');
    if (outcome === 'network') request.reject(new Error('synthetic network failure'));
    else request.resolve({ ok: outcome !== 'forbidden', redirected: outcome === 'redirect', status: 200, text: async () => '' });
    await done;
    assert.equal(s.requests.length, 1);
    assert.equal(s.alerts.length, outcome === 'success' ? 0 : 1);
    assert.equal(s.location.href === 'history', outcome !== 'success');
    if (outcome === 'success') assert.match(s.location.href, /\/rx\/choosePatient\?demographicNo=fixture$/);
  });
}
