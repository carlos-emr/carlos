/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {assert} = require('./playwright-harness');

function validateCapacityResponse(headers, data) {
  assert(/^application\/json(?:\s*;|$)/i.test(headers['content-type'] || ''), 'Stored capacity response was not JSON');
  const retry = headers['retry-after'] || '';
  assert(/^[1-9][0-9]?$/.test(retry) && Number(retry) <= 60, 'Stored capacity response has an invalid retry delay');
  assert(data && data.success === false && data.accepted === false && data.retryable === true,
    'Stored capacity response did not guarantee pre-acceptance refusal');
}

function createCapacityLedger({endpoint, label, recorder, validateRequest, finalStatus = 200}) {
  const beforeResponses = new Set(recorder.badResponses), beforeConsole = new Set(recorder.consoleIssues);
  const requests = new Map();
  let body, token, busy = 0, completed = 0;
  function request(value, injected = false) {
    assert(value.url() === endpoint && value.method() === 'POST', 'Unexpected stored capacity request destination');
    validateRequest(value);
    const nextBody = value.postData(), nextToken = value.headers()['csrf-token'];
    assert(typeof nextBody === 'string' && nextToken, 'Stored mutation must retain its body and CSRF header');
    if (body === undefined) { body = nextBody; token = nextToken; }
    assert(nextBody === body && nextToken === token, 'Stored retry changed its frozen body or CSRF token');
    assert(!requests.has(value), 'Stored request was intercepted twice');
    requests.set(value, {injected, responded: false});
  }
  async function response(value) {
    const record = requests.get(value.request());
    assert(record && !record.responded, 'Stored response does not match exactly one guarded request');
    record.responded = true;
    const data = await value.json();
    if (value.status() === 503) {
      validateCapacityResponse(value.headers(), data); busy++;
    } else {
      assert(!record.injected && value.status() === finalStatus, 'Unexpected stored mutation outcome');
      assert(finalStatus === 409
        ? data && data.success === false && data.accepted === false && data.retryable === false && data.sourceChanged === true
        : data && data.success === true && data.accepted === true,
      'Stored mutation did not confirm the expected final outcome');
      completed++;
    }
  }
  function finish(injectedExpected) {
    assert(completed === 1 && [...requests.values()].every(value => value.responded)
      && requests.size === busy + 1, 'Stored mutation has missing responses or duplicate final outcomes');
    assert([...requests.values()].filter(value => value.injected).length === injectedExpected,
      'Stored capacity check did not inject the intended refusals');
    const expectedResponses = recorder.badResponses.filter(entry => !beforeResponses.has(entry)
      && entry.label === label && entry.url === endpoint && entry.method === 'POST' && entry.status === 503
      && /^application\/json(?:\s*;|$)/i.test(entry.contentType || ''));
    assert(expectedResponses.length === busy, 'Recorded capacity failures differ from validated responses');
    const expectedConsole = recorder.consoleIssues.filter(entry => !beforeConsole.has(entry)
      && entry.label === label && entry.type === 'error' && entry.location?.url === endpoint
      && /^Failed to load resource:.*\b503\b/.test(entry.text || ''));
    assert(expectedConsole.length <= busy, 'Unexpected additional stored capacity console errors');
    recorder.badResponses = recorder.badResponses.filter(entry => !expectedResponses.includes(entry));
    recorder.consoleIssues = recorder.consoleIssues.filter(entry => !expectedConsole.includes(entry));
  }
  return {request, response, finish, attempts: () => requests.size};
}

/** Inject only explicit refusals; every completed mutation still goes to the installed server. */
async function withStoredCapacityCheck(options, work) {
  const {page, endpoint, assertUnchanged, onFailure, inject = 0} = options;
  const ledger = createCapacityLedger(options), active = new Set(), validations = new Set(), handlers = new Set(), errors = [];
  let injected = 0, installed = false, drained = false;
  const matches = request => request.url() === endpoint && request.method() === 'POST';
  const started = request => {if (matches(request)) active.add(request);};
  const finished = request => active.delete(request);
  const failed = request => {if (active.delete(request)) errors.push(new Error('Stored mutation transport failed'));};
  function track(promise, set) {
    const observed = Promise.resolve(promise).catch(error => {errors.push(error);}).finally(() => set.delete(observed));
    set.add(observed); return observed;
  }
  const observed = response => {if (matches(response.request())) track(ledger.response(response), validations);};
  const route = value => track((async () => {
    try {
      const synthetic = injected < inject;
      ledger.request(value.request(), synthetic);
      if (synthetic) {
        assertUnchanged(); injected++;
        await value.fulfill({status: 503, contentType: 'application/json',
          headers: {'Retry-After': '1', 'Cache-Control': 'no-store'},
          body: JSON.stringify({success: false, accepted: false, retryable: true, error: 'Document server is busy; waiting is safe'})});
      } else {
        // Keep the context's exact owned-document guard on the real request path.
        await value.fallback();
      }
    } catch (error) {
      errors.push(error);
      await value.abort('blockedbyclient');
    }
  })(), handlers);
  async function drain() {
    const deadline = Date.now() + 120000;
    while (active.size || handlers.size || validations.size) {
      assert(Date.now() < deadline, 'Stored capacity requests did not drain');
      await new Promise(resolve => setTimeout(resolve, 20));
    }
    drained = true;
    if (errors.length) throw errors[0];
  }
  page.on('request', started); page.on('requestfinished', finished); page.on('requestfailed', failed); page.on('response', observed);
  try {
    await page.route(endpoint, route); installed = true;
    await work(ledger);
    await drain();
    ledger.finish(inject);
  } catch (error) {
    onFailure();
    // Stop future retry timers before draining any request that may already be accepted.
    await page.evaluate(() => window.dispatchEvent(new Event('pagehide'))).catch(() => {});
    await drain().catch(drainError => {errors.push(drainError);});
    throw error;
  } finally {
    if (!drained) onFailure();
    if (installed && drained) await page.unroute(endpoint, route);
    if (drained) {
      page.off('request', started); page.off('requestfinished', finished); page.off('requestfailed', failed); page.off('response', observed);
    }
  }
}
module.exports = {validateCapacityResponse, createCapacityLedger, withStoredCapacityCheck};
