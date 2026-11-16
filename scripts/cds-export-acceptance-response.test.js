/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {readAcceptanceResponse} = require('./ci/cds-export-acceptance-response');
const response = (body, type = 'application/json; charset=UTF-8') => ({
  status: () => 202, headers: () => ({'content-type': type}), body: async () => Buffer.from(body)
});

test('acceptance JSON is parsed unchanged and only safe metadata is retained', async () => {
  const diagnostic = {};
  const body = '{"ok":true,"privateFixture":"FAKE confidential value"}';
  assert.deepEqual(await readAcceptanceResponse(response(body), diagnostic), JSON.parse(body));
  assert.deepEqual(diagnostic, {httpStatus:202, contentType:'JSON', bodyAvailable:true, bodyBytes:Buffer.byteLength(body)});
  assert(!JSON.stringify(diagnostic).includes('confidential'));
});

test('malformed and HTML acceptance bodies remain failures without retaining their contents', async () => {
  for (const [body, type, category] of [['<html>FAKE private</html>','text/html','HTML'], ['\ufeff{"ok":true}','application/json','JSON']]) {
    const diagnostic = {};
    await assert.rejects(readAcceptanceResponse(response(body,type),diagnostic), SyntaxError);
    assert.deepEqual(diagnostic, {httpStatus:202, contentType:category, bodyAvailable:true,
      bodyBytes:Buffer.byteLength(body), errorCategory:'INVALID_JSON'});
    assert(!JSON.stringify(diagnostic).includes('private'));
  }
});

test('body read failures retain a bounded category and propagate the original failure', async () => {
  for (const [message, category] of [
    ['Protocol error: No resource with given identifier found; FAKE secret','BODY_UNAVAILABLE'],
    ['Target page, context or browser has been closed; FAKE secret','CONTEXT_CLOSED'],
    ['Timed out; FAKE secret','BODY_TIMEOUT'], ['FAKE secret transport failure','BODY_READ_ERROR']
  ]) {
    const diagnostic = {};
    const failure = new Error(message);
    const input = {...response(''), body:async () => {throw failure;}};
    await assert.rejects(readAcceptanceResponse(input,diagnostic), error => error === failure);
    assert.deepEqual(diagnostic, {httpStatus:202, contentType:'JSON', bodyAvailable:false, errorCategory:category});
    assert(!JSON.stringify(diagnostic).includes('secret'));
  }
});

test('unrecognized and absent content types do not copy header values into diagnostics', async () => {
  for (const [type, category] of [['application/x-FAKE-private','OTHER'],['','MISSING'],['text/plain','TEXT']]) {
    const diagnostic = {};
    await readAcceptanceResponse(response('{}',type),diagnostic);
    assert.equal(diagnostic.contentType,category);
    assert(!JSON.stringify(diagnostic).includes('private'));
  }
});
