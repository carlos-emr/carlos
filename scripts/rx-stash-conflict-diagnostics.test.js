/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { consumeExpectedConflict } = require('./rx-stash-patient-isolation-playwright-checks');

const baseUrl = 'https://example.test/carlos';
const responseUrl = `${baseUrl}/rx/WriteScript?parameterValue=updateSaveAllDrugs`;
const response = { url() { return responseUrl; } };
function warning(url, text = 'Failed to load resource: the server responded with a status of 409 ()') {
  return { label: 'owned-workflow', type: 'error', text, location: { url } };
}
function recorder(consoleIssues) {
  return {
    badResponses: [{ label: 'owned-workflow', method: 'POST', status: 409, url: responseUrl }],
    consoleIssues,
  };
}

for (const url of [responseUrl, `${baseUrl}/csrfguard`]) {
  test(`consumes one verified conflict and its native warning at ${url}`, () => {
    const log = recorder([warning(url)]);
    consumeExpectedConflict(log, response, { responses: 0, console: 0 }, baseUrl);
    assert.deepEqual(log, { badResponses: [], consoleIssues: [] });
  });
}

test('keeps unrelated locations, origins, contexts, messages, labels and older warnings strict', () => {
  const issues = [
    warning(`${baseUrl}/csrfguard`), // Before the deliberate failure window.
    warning('https://other.test/carlos/csrfguard'),
    warning('https://example.test/other/csrfguard'),
    warning(`${baseUrl}/another-endpoint`),
    warning(`${baseUrl}/csrfguard`, 'Failed to load resource: the server responded with a status of 500 ()'),
    warning(`${baseUrl}/csrfguard`, 'Application error: status of 409 ()'),
    { ...warning(`${baseUrl}/csrfguard`), label: 'another-workflow' },
  ];
  const log = recorder([...issues, warning(`${baseUrl}/csrfguard`)]);
  consumeExpectedConflict(log, response, { responses: 0, console: 1 }, baseUrl);
  assert.deepEqual(log.consoleIssues, issues);
});

test('does not consume a warning without exactly one matching failed POST', () => {
  const log = recorder([warning(`${baseUrl}/csrfguard`)]);
  log.badResponses.push({ method: 'POST', status: 500, url: `${baseUrl}/other` });
  assert.throws(() => consumeExpectedConflict(log, response, { responses: 0, console: 0 }, baseUrl));
  assert.equal(log.badResponses.length, 2);
  assert.equal(log.consoleIssues.length, 1);
});

test('one expected failed POST permits at most one native warning', () => {
  const log = recorder([warning(responseUrl), warning(`${baseUrl}/csrfguard`)]);
  consumeExpectedConflict(log, response, { responses: 0, console: 0 }, baseUrl);
  assert.equal(log.consoleIssues.length, 1);
});
