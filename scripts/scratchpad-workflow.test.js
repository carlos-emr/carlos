/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { consumeExpectedFailure, isSave } = require('./scratchpad-workflow-playwright-checks');
const url = 'https://localhost/carlos/Scratch';
const since = { responses: 0, console: 0 };
function fixture() {
  return {
    badResponses: [{url, status: 409, method: 'POST'}],
    consoleIssues: [{location: {url}, text: 'Failed to load resource: the server responded with a status of 409 (Conflict)'}],
  };
}

test('negative workflow assertion consumes only its exact expected HTTP failure', () => {
  const recorder = fixture();
  const unrelated = {location: {url}, text: 'TypeError: application broke'};
  recorder.consoleIssues.push(unrelated);
  consumeExpectedFailure(recorder, url, 409, since);
  assert.deepEqual(recorder.badResponses, []);
  assert.deepEqual(recorder.consoleIssues, [unrelated]);
});
for (const change of ['wrong status', 'wrong endpoint', 'extra error', 'missing error']) {
  test(`negative workflow assertion fails closed: ${change}`, () => {
    const recorder = fixture();
    if (change === 'wrong status') recorder.badResponses[0].status = 500;
    if (change === 'wrong endpoint') recorder.badResponses[0].url += '/other';
    if (change === 'extra error') recorder.badResponses.push({url, status: 500});
    if (change === 'missing error') recorder.badResponses.length = 0;
    assert.throws(() => consumeExpectedFailure(recorder, url, 409, since));
  });
}

test('response matching cannot mistake a history deletion or unrelated request for a save', () => {
  const response = (path, method, data) => ({url: () => path, request: () => ({method: () => method, postData: () => data})});
  assert.equal(isSave(response(url, 'POST', 'id=7&scratchpad=text')), true);
  assert.equal(isSave(response(url, 'POST', 'method=delete&id=7')), false);
  assert.equal(isSave(response(url, 'GET', null)), false);
  assert.equal(isSave(response(url + '/other', 'POST', 'id=7')), false);
});
