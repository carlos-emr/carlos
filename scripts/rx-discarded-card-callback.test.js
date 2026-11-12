/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { consumeDiscardedCallbackFailure: consume } = require('./rx-discarded-card-callback-playwright-checks');
const callback = 'https://127.0.0.1/carlos/rx/UpdateScript?demographicNo=own';
const wrapper = 'https://127.0.0.1/carlos/csrfguard';
const message = 'Failed to load resource: the server responded with a status of 409 ()';
function setup(location = wrapper) {
  const request = { method: () => 'POST', url: () => callback };
  return { request, response: { request: () => request, status: () => 409 },
    recorder: { badResponses: [{ status: 409, method: 'POST', url: callback }],
      consoleIssues: [{ text: message, location: { url: location } }], requestFailures: [], pageErrors: [] },
    mark: { responses: 0, console: 0, failures: 0 } };
}
function run(f) { consume(f.recorder, f.mark, f.request, f.response); }

test('the unique captured409 accounts for its exact same-origin CSRF wrapper resource message', () => {
  const f = setup(); run(f); assert.equal(f.recorder.badResponses.length, 0); assert.equal(f.recorder.consoleIssues.length, 0);
});
test('the ordinary callback-located409 console message is still accepted', () => {
  const f = setup(callback); run(f); assert.equal(f.recorder.consoleIssues.length, 0);
});
test('200 or another refusal status cannot masquerade as the expected conflict', () => {
  for (const status of [200, 400, 404, 500]) {
    const f = setup(); f.response.status = () => status;
    assert.throws(() => run(f), /HTTP 409/); assert.equal(f.recorder.badResponses.length, 1);
  }
});
test('a different request object with the same URL cannot consume the captured callback receipt', () => {
  const f = setup(); f.response.request = () => ({ method: () => 'POST', url: () => callback });
  assert.throws(() => run(f), /captured/); assert.equal(f.recorder.consoleIssues.length, 1);
});
test('missing response evidence cannot excuse a wrapper console error', () => {
  const f = setup(); f.recorder.badResponses = [];
  assert.throws(() => run(f), /only failing response/); assert.equal(f.recorder.consoleIssues.length, 1);
});
test('an additional409 or different HTTP failure prevents any consumption', () => {
  for (const status of [409, 400, 500]) {
    const f = setup(); f.recorder.badResponses.push({status, method:'POST', url:callback});
    assert.throws(() => run(f), /only failing response/); assert.equal(f.recorder.badResponses.length, 2); assert.equal(f.recorder.consoleIssues.length, 1);
  }
});
test('the recorded response must name the exact captured URL, including its owned-patient query', () => {
  const f = setup(); f.recorder.badResponses[0].url = callback.replace('=own','=another');
  assert.throws(() => run(f), /only failing response/); assert.equal(f.recorder.consoleIssues.length, 1);
});
test('another origin/path/query or status message remains a strict browser failure', () => {
  for (const location of ['https://elsewhere.invalid/carlos/csrfguard', wrapper+'/other', wrapper+'?other=true', 'https://127.0.0.1/other/csrfguard']) {
    const f = setup(location); run(f); assert.equal(f.recorder.consoleIssues.length, 1);
  }
  const f = setup(); f.recorder.consoleIssues[0].text = message.replace('409', '500'); run(f); assert.equal(f.recorder.consoleIssues.length, 1);
});
test('multiple matching resource console failures cannot be excused by one response', () => {
  const f = setup(); f.recorder.consoleIssues.push({text:message,location:{url:callback}});
  assert.throws(() => run(f), /multiple browser console failures/); assert.equal(f.recorder.badResponses.length, 1);
});
test('earlier window errors remain, and an unrelated new JavaScript exception remains', () => {
  const f = setup();
  f.recorder.badResponses.unshift({status:409,method:'POST',url:callback}); f.mark.responses=1;
  f.recorder.consoleIssues.unshift({text:message,location:{url:wrapper}}); f.mark.console=1;
  f.recorder.consoleIssues.push({text:'Unrelated TypeError',location:{url:wrapper}});
  run(f); assert.equal(f.recorder.badResponses.length,1); assert.equal(f.recorder.consoleIssues.length,2);
  assert.equal(f.recorder.consoleIssues[1].text,'Unrelated TypeError');
});
test('request failures and page errors are never consumed by a409 resource receipt', () => {
  const f = setup(); f.recorder.requestFailures.push({errorText:'ERR_FAILED'}); f.recorder.pageErrors.push({text:'TypeError'});
  run(f); assert.equal(f.recorder.requestFailures.length,1); assert.equal(f.recorder.pageErrors.length,1);
});
