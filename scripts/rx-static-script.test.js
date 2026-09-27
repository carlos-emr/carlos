/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/StaticScript2.jsp'), 'utf8');
const start = jsp.indexOf('function postStaticScript(');
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
    alert: message => alerts.push(message), oscarLog: () => {},
    CarlosAjax: { request: (url, options) => requests.push({ url, ...options }) },
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
    assert.equal(s.requests.length, 1);
    assert.equal(s.requests[0].method, 'post');
    assert.equal(new URLSearchParams(s.requests[0].parameters).get('favoriteName'), "Quote ' & value");
    assert.equal(s.location.href, 'history');
    s.requests[0][success ? 'onSuccess' : 'onFailure']({ status: success ? 200 : 403 });
    await done;
    assert.equal(s.alerts.length, success ? 0 : 1);
    assert.equal(s.location.href === 'history', !success);
  });
}
for (const failingStep of [0, 1, -1]) {
  test(`re-prescribe waits for both protected writes and retains the page on failure (${failingStep})`, async () => {
    const s = setup();
    const done = s.context.reRxDrugSearch3(7);
    assert.equal(s.requests.length, 1);
    assert.match(s.requests[0].url, /\/rx\/WriteScript$/);
    assert.equal(s.location.href, 'history');
    s.requests[0][failingStep === 0 ? 'onFailure' : 'onSuccess']({});
    await Promise.resolve();
    if (failingStep !== 0) {
      assert.equal(s.requests.length, 2);
      assert.match(s.requests[1].url, /\/rx\/rePrescribe2/);
      assert.equal(s.location.href, 'history');
      s.requests[1][failingStep === 1 ? 'onFailure' : 'onSuccess']({});
    }
    await done;
    assert.equal(s.requests.length, failingStep === 0 ? 1 : 2);
    assert.equal(s.alerts.length, failingStep === -1 ? 0 : 1);
    assert.equal(s.location.href === 'history', failingStep !== -1);
  });
}
