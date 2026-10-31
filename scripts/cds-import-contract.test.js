/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const client = require('../src/main/webapp/js/demographic/cds-import.js');

function response(value, header = '2') {
  const headers = {'Content-Type': 'application/json'};
  if (header !== undefined) headers[client.API_VERSION_HEADER] = header;
  return new Response(JSON.stringify(value), {headers});
}

test('browser contract2 requires agreeing response header and numeric envelope before typed use', async () => {
  assert.equal(client.API_VERSION, 2);
  const value = {apiVersion: 2, ok: true, summary: {resumeRequested: false}};
  assert.deepEqual(await client.apiResponse(response(value)), value);
  for (const changed of [1, '2', null, undefined]) {
    await assert.rejects(client.apiResponse(response({...value, apiVersion: changed})), /IMPORT_CLIENT_REFRESH_REQUIRED/);
  }
  for (const header of ['1', '2, 2', '']) {
    await assert.rejects(client.apiResponse(response(value, header)), /IMPORT_CLIENT_REFRESH_REQUIRED/);
  }
  await assert.rejects(client.apiResponse(response([value])), /IMPORT_CLIENT_REFRESH_REQUIRED/);
});

test('an old backend is refused and its body cancelled before any response body is consumed', async () => {
  let reads = 0; let cancelled = false;
  const stream = new ReadableStream({pull() { reads++; }, cancel() { cancelled = true; }}, {highWaterMark: 0});
  await assert.rejects(client.apiResponse(new Response(stream, {headers: {'Content-Type': 'application/json'}})),
    /IMPORT_CLIENT_REFRESH_REQUIRED/);
  assert.equal(reads, 0);
  assert.equal(cancelled, true);
});

test('a legacy success cannot become a clean response merely by adding the new HTTP header', async () => {
  await assert.rejects(client.apiResponse(response({ok: true, summary: {cleanComplete: true}})),
    /IMPORT_CLIENT_REFRESH_REQUIRED/);
});
