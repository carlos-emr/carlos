/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * The replay a CSRF or authorization probe sends must be the page's own request with exactly one
 * thing changed: the token (removed, or the replaying session's), and the marker it is aimed at.
 * A replay that dropped a field, re-encoded the body or kept the original token would make the
 * probe answer a different question, so buildReplay() is pinned here without a browser.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { buildReplay, tokenCarriers, encoding, hasFilePart } = require('./lib/mutation-replay');

const ORIGIN = 'https://localhost';
const PAGE_TOKEN = 'PAGETOKEN-1234-ABCD';
const OTHER_TOKEN = 'OTHERTOKEN-5678-EFGH';

function captured({ path = '/carlos/tickler/DbTicklerAdd', query = '', body = [], headers = {}, contentType = 'application/x-www-form-urlencoded', postData } = {}) {
  const form = new URLSearchParams();
  for (const [k, v] of body) form.append(k, v);
  return {
    path, query: new URLSearchParams(query), body: form, contentType,
    headers: { 'content-type': contentType, ...headers }, postData: postData === undefined ? form.toString() : postData,
  };
}

test('shouldDropEveryTokenCarrier_forTheTokenlessReplay', () => {
  const request = captured({
    query: `a=1&CSRF-TOKEN=${PAGE_TOKEN}`,
    body: [['ticklerMessage', 'FAKE-PW1-TA-UI'], ['CSRF-TOKEN', PAGE_TOKEN], ['empty', '']],
    headers: { 'csrf-token': PAGE_TOKEN, 'x-requested-with': 'XMLHttpRequest' },
  });
  const replay = buildReplay(request, { origin: ORIGIN, token: null, overrides: { ticklerMessage: 'FAKE-PW1-TA-NT' } });
  assert.equal(replay.url, 'https://localhost/carlos/tickler/DbTicklerAdd?a=1');
  assert.equal(replay.headers['csrf-token'], undefined);
  assert.equal(replay.headers['x-requested-with'], 'XMLHttpRequest', 'the AJAX marker stays: only the token may differ');
  const sent = new URLSearchParams(replay.data);
  assert.equal(sent.has('CSRF-TOKEN'), false);
  assert.equal(sent.get('ticklerMessage'), 'FAKE-PW1-TA-NT');
  assert.equal(sent.get('empty'), '', 'an empty field is kept: a legacy action treats absent and empty differently');
  assert.equal(replay.headers['content-type'], 'application/x-www-form-urlencoded');
});

test('shouldCarryTheReplayingSessionsToken_whereThePageCarriedItsOwn', () => {
  const both = captured({ body: [['x', '1'], ['CSRF-TOKEN', PAGE_TOKEN]], headers: { 'csrf-token': PAGE_TOKEN } });
  const replay = buildReplay(both, { origin: ORIGIN, token: OTHER_TOKEN });
  assert.equal(replay.headers['csrf-token'], OTHER_TOKEN);
  assert.deepEqual(new URLSearchParams(replay.data).getAll('CSRF-TOKEN'), [OTHER_TOKEN]);
  assert.ok(!replay.data.includes(PAGE_TOKEN) && !replay.url.includes(PAGE_TOKEN), 'the page\'s own token never travels');

  const headerOnly = captured({ body: [['x', '1']], headers: { 'csrf-token': PAGE_TOKEN } });
  const headerReplay = buildReplay(headerOnly, { origin: ORIGIN, token: OTHER_TOKEN });
  assert.equal(headerReplay.headers['csrf-token'], OTHER_TOKEN);
  assert.equal(new URLSearchParams(headerReplay.data).has('CSRF-TOKEN'), false);

  const queryOnly = captured({ query: `CSRF-TOKEN=${PAGE_TOKEN}&m=1`, body: [['x', '1']] });
  const queryReplay = buildReplay(queryOnly, { origin: ORIGIN, token: OTHER_TOKEN });
  assert.equal(new URL(queryReplay.url).searchParams.get('CSRF-TOKEN'), OTHER_TOKEN);
  assert.equal(new URLSearchParams(queryReplay.data).has('CSRF-TOKEN'), false);
});

test('shouldFallBackToTheBodyParameter_whenAFormCarriedNoToken', () => {
  const replay = buildReplay(captured({ body: [['x', '1']] }), { origin: ORIGIN, token: OTHER_TOKEN });
  assert.equal(new URLSearchParams(replay.data).get('CSRF-TOKEN'), OTHER_TOKEN);
});

test('shouldApplyOverrides_toRepeatedAndDeletedFields', () => {
  const request = captured({ body: [['keep', 'a'], ['many', '1'], ['many', '2'], ['gone', 'x']] });
  const replay = buildReplay(request, { origin: ORIGIN, overrides: { many: ['3', '4'], gone: null, added: 'y' } });
  const sent = new URLSearchParams(replay.data);
  assert.deepEqual(sent.getAll('many'), ['3', '4']);
  assert.equal(sent.has('gone'), false);
  assert.equal(sent.get('added'), 'y');
  assert.equal(sent.get('keep'), 'a');
});

test('shouldReplaceJsonProperties_andKeepTheJsonContentType', () => {
  const request = captured({ contentType: 'application/json', postData: JSON.stringify({ message: 'UI', keep: 1 }), body: [] });
  assert.equal(encoding(request), 'json');
  const replay = buildReplay(request, { origin: ORIGIN, token: null, overrides: { message: 'NT' } });
  assert.deepEqual(JSON.parse(replay.data), { message: 'NT', keep: 1 });
  assert.equal(replay.headers['content-type'], 'application/json');
});

test('shouldResendMultipartTextFields_andRefuseAFilePart', () => {
  const type = 'multipart/form-data; boundary=XyZ';
  const text = captured({ contentType: type, body: [['subject', 'UI'], ['CSRF-TOKEN', PAGE_TOKEN]], postData: '--XyZ\r\nContent-Disposition: form-data; name="subject"\r\n\r\nUI\r\n--XyZ--' });
  const replay = buildReplay(text, { origin: ORIGIN, token: null, overrides: { subject: 'NT' } });
  assert.deepEqual(replay.multipart, [['subject', 'NT']]);
  const file = captured({ contentType: type, body: [['subject', 'UI']],
    postData: '--XyZ\r\nContent-Disposition: form-data; name="upload"; filename="a.pdf"\r\n\r\n%PDF\r\n--XyZ--' });
  assert.equal(hasFilePart(file), true);
  assert.throws(() => buildReplay(file, { origin: ORIGIN }), /file part cannot be replayed/);
});

test('shouldReportWhereThePageCarriedItsToken', () => {
  assert.deepEqual(tokenCarriers(captured({ body: [['CSRF-TOKEN', PAGE_TOKEN]] })), { header: false, body: true, query: false });
  assert.deepEqual(tokenCarriers(captured({ headers: { 'csrf-token': PAGE_TOKEN } })), { header: true, body: false, query: false });
});

test('shouldRejectAMalformedToken_soAProbeCannotSendGarbageAndCallItAValidToken', () => {
  for (const token of ['', 'a b', '<script>', 'x'.repeat(200)]) {
    assert.throws(() => buildReplay(captured({ body: [['x', '1']] }), { origin: ORIGIN, token }), /the token must be/, JSON.stringify(token));
  }
});
