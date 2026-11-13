/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {createInjectedResponseEvidence} = require('./lib/injected-response-evidence');
const {createRecorder, assertStrictPage} = require('./lib/playwright-harness');
const label = 'legacy-document-capacity', url = 'https://127.0.0.1/carlos/documentManager/SplitDocument';
function setup() {
  const recorder = createRecorder(), evidence = createInjectedResponseEvidence(recorder, label);
  function response(request, status, responseUrl = url) {
    const entry = { label, url: responseUrl, status, method: request.method(), resourceType: 'fetch' };
    recorder.badResponses.push(entry);
    evidence.observe({ request: () => request, status: () => status, url: () => responseUrl });
    return entry;
  }
  function consoleError(status, location = url) {
    recorder.consoleIssues.push({ label, type: 'error', text: `Failed to load resource: the server responded with a status of ${status} ()`, location: {url: location} });
  }
  return { recorder, evidence, response, consoleError };
}
const request = () => ({ method: () => 'POST' });

test('exact intercepted request and observed response remove only their expected diagnostic', () => {
  const h = setup(), r = request();h.evidence.expect(r, 503);h.response(r, 503);h.consoleError(503);
  h.evidence.removeExpectedDiagnostics();assertStrictPage(h.recorder);
});
test('late response and console events at the cleanup boundary remain attributable', async () => {
  const h = setup(), r = request();h.evidence.expect(r, 503);
  await new Promise(resolve => setImmediate(() => {h.response(r, 503);h.consoleError(503);resolve();}));
  h.evidence.removeExpectedDiagnostics();assertStrictPage(h.recorder);
});
test('an unrelated identical URL and status is still a strict failure', () => {
  const h = setup(), injected = request(), unrelated = request();
  h.evidence.expect(injected, 503);h.response(injected, 503);h.consoleError(503);
  h.response(unrelated, 503);h.consoleError(503);
  h.evidence.removeExpectedDiagnostics();
  assert.throws(() => assertStrictPage(h.recorder), /HTTP 503|console error/);
  assert.equal(h.recorder.badResponses.length, 1);assert.equal(h.recorder.consoleIssues.length, 1);
});
test('one observed injected response cannot excuse two console errors', () => {
  const h = setup(), r = request();h.evidence.expect(r, 503);h.response(r, 503);
  h.consoleError(503);h.consoleError(503);h.evidence.removeExpectedDiagnostics();
  assert.equal(h.recorder.consoleIssues.length, 1);
  assert.throws(() => assertStrictPage(h.recorder), /console error/);
});
test('a resource-like console error carrying additional diagnostics is never removed', () => {
  const h = setup(), r = request();h.evidence.expect(r, 503);h.response(r, 503);h.consoleError(503);
  h.recorder.consoleIssues[0].text += ' ReferenceError: application failed';
  h.evidence.removeExpectedDiagnostics();
  assert.throws(() => assertStrictPage(h.recorder), /ReferenceError/);
});
test('unrelated status, location, page error and request failure are never removed', () => {
  const h = setup(), r = request();h.evidence.expect(r, 503);h.response(r, 503);
  h.response(request(), 500);h.consoleError(503, url + '/other');
  h.recorder.pageErrors.push({label, text:'Unexpected application error'});
  h.recorder.requestFailures.push({label, resourceType:'fetch', errorText:'connection failed'});
  h.evidence.removeExpectedDiagnostics();
  assert.throws(() => assertStrictPage(h.recorder), /Unexpected application error/);
  assert.equal(h.recorder.badResponses.length, 1);assert.equal(h.recorder.consoleIssues.length, 1);
});
test('registration alone cannot excuse a failed request without its real response', () => {
  const h = setup();h.evidence.expect(request(), 503);
  assert.throws(() => h.evidence.removeExpectedDiagnostics(), /no observed/);
});
test('wrong actual status or missing recorder evidence refuses accounting', () => {
  const wrong = setup(), r = request();wrong.evidence.expect(r, 503);wrong.response(r, 500);
  assert.throws(() => wrong.evidence.removeExpectedDiagnostics(), /exact injected response/);
  const missing = setup(), another = request();missing.evidence.expect(another, 503);
  missing.evidence.observe({request:()=>another,status:()=>503,url:()=>url});
  assert.throws(() => missing.evidence.removeExpectedDiagnostics(), /no exact recorder evidence/);
});
