/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {isOwnedUnlinkAnchor, metadataEnabled, recorderSnapshot, reconcileFault, actionIntent} = require('./lib/document-metadata-installed');
const identity = {document: '42', patient: '77', provider: '999998'};
function ledger(mode = 'rejected500') {
  const intent = actionIntent('queue', identity, 'https://owned.invalid/inboxManage', 'pin');
  const request = {url: () => intent.endpoint, method: () => 'POST', resourceType: () => 'fetch', failure: () => ({errorText: 'net::ERR_FAILED'})};
  const old = {label: 'old', status: 500, url: intent.endpoint, method: 'POST', contentType: 'application/json'};
  const recorder = {badResponses: [old], requestFailures: [], consoleIssues: []};
  const before = recorderSnapshot(recorder);
  if (mode === 'rejected500') recorder.badResponses.push({...old, label: 'owned'});
  if (mode === 'lostBeforeForward') recorder.requestFailures.push({label: 'owned', url: intent.endpoint, resourceType: 'fetch', errorText: 'net::ERR_FAILED'});
  return {recorder, before, label: 'owned', intent, mode, counts: {calls: 1, forwarded: 0, settled: true}, requests: new Set([request]),
    responses: mode === 'lostBeforeForward' ? [] : [{request, status: mode === 'html200' ? 200 : 500}], failures: mode === 'lostBeforeForward' ? [request] : []};
}
test('metadata phase is explicit and cannot alias a different config spelling', () => {
  assert.equal(metadataEnabled('false'), false); assert.equal(metadataEnabled('true'), true);
  for (const value of ['TRUE', '', '1', null]) assert.throws(() => metadataEnabled(value));
});
for (const mode of ['rejected500', 'html200', 'lostBeforeForward']) test(mode + ' reconciles only its validated single pre-forward fault', () => {
  const value = ledger(mode); reconcileFault(value);
  assert.equal(value.recorder.badResponses.length, 1); assert.equal(value.recorder.badResponses[0].label, 'old');
  assert.equal(value.recorder.requestFailures.length, 0);
});
for (const issue of ['forwarded', 'duplicate', 'wrong request', 'wrong status', 'missing recorder evidence']) test('fault ledger rejects ' + issue, () => {
  const value = ledger();
  if (issue === 'forwarded') value.counts.forwarded = 1;
  if (issue === 'duplicate') value.requests.add({});
  if (issue === 'wrong request') value.responses[0].request = {};
  if (issue === 'wrong status') value.responses[0].status = 503;
  if (issue === 'missing recorder evidence') value.recorder.badResponses.pop();
  assert.throws(() => reconcileFault(value));
});
test('fault reconciliation leaves unrelated endpoint/label/console errors for strict failure', () => {
  const value = ledger();
  const foreign = {label: 'owned', url: 'https://owned.invalid/other', status: 500};
  const wrongLabel = {...value.recorder.badResponses.at(-1), label: 'other'};
  const consoleError = {label: 'owned', location: {url: value.intent.endpoint}, text: 'Unrelated JS exception', type: 'error'};
  value.recorder.badResponses.push(foreign, wrongLabel); value.recorder.consoleIssues.push(consoleError);
  reconcileFault(value); assert(value.recorder.badResponses.includes(foreign)); assert(value.recorder.badResponses.includes(wrongLabel));
  assert(value.recorder.consoleIssues.includes(consoleError));
});
test('queue and unlink keep exact document/provider/session scope and contain no metadata replay', () => {
  const queue = actionIntent('queue', identity, 'https://owned.invalid/queue', 'pin');
  assert.deepEqual([...new URLSearchParams(queue.body)], [['method', 'updateDocStatusInQueue'], ['docid', '42'], ['CSRF-TOKEN', 'pin']]);
  const unlink = actionIntent('unlink', identity, 'https://owned.invalid/manage', 'pin');
  assert.equal(new URLSearchParams(unlink.body).get('providerNo'), identity.provider);
  assert(Object.isFrozen(queue)); assert(Object.isFrozen(unlink));
});

test('unlink anchor checks decimal identities with the original exact literal handler contract', () => {
  for (const value of ["removeLink('DOC','42','999998',this);return false;",
      "  removeLink('DOC',  '42',\t'999998', this);return false;  "]) {
    assert.equal(isOwnedUnlinkAnchor(value, '42', '999998'), true);
  }
  assert.equal(isOwnedUnlinkAnchor("removeLink('DOC','42','009',this);return false;", '42', '009'), true);
});
test('unlink anchor refuses other identities, appended code and malformed handlers', () => {
  const valid = "removeLink('DOC','42','999998',this);return false;";
  for (const value of [null, undefined, 42, valid.replace("'42'", "'43'"),
      valid.replace("'999998'", "'999999'"), valid.replace("'42'", "'042'"),
      valid.replace("'DOC'", "'doc'"), valid.replace('return false;', ''),
      valid + 'alert(1);', 'alert(1);' + valid, valid.replace('this', 'other')]) {
    assert.equal(isOwnedUnlinkAnchor(value, '42', '999998'), false);
  }
});
test('unlink identities are compared literally and never interpreted as regular expressions', () => {
  const value = "removeLink('DOC','42','999998',this);return false;";
  for (const identity of ['.*', '(a+)+$', '42|43', "42');alert(1);//"])
    assert.equal(isOwnedUnlinkAnchor(value, identity, '999998'), false);
  assert.equal(isOwnedUnlinkAnchor(value, '42', '99999.'), false);
});
