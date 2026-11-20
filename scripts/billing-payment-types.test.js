/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/billing/CA/ON');
const csrfScript = fs.readFileSync(path.join(__dirname, '../src/main/webapp/billing/CA/ON/payment-type-csrf.js'), 'utf8');
const jsp = fs.readFileSync(path.join(root, 'editBillingPaymentType.jsp'), 'utf8');
const script = jsp.match(/<script type="text\/javascript">\s*([\s\S]*?)<\/script>/)[1].replace(/<carlos:encode[\s\S]*?\/>/g, 'fixture');
for (const action of ['createType', 'saveType']) {
  test(`${action} waits for bootstrap and sends one protected form parameter without a manual header`, async () => {
    let resolve;
    const ready = new Promise(r => { resolve = r; });
    const requests = [];
    const tokenInput = { value: '' };
    const context = vm.createContext({
      window: { csrfTokenReady: ready },
      document: { getElementById: () => ({ value: 'Synthetic' }), querySelector: () => tokenInput },
      $: { ajax: request => requests.push(request) }, alert: () => assert.fail('unexpected alert'),
    });
    vm.runInContext(csrfScript, context); vm.runInContext(script, context);
    const pending = context[action]();
    await context[action](); // repeated activation while token retrieval is pending
    await Promise.resolve(); assert.equal(requests.length, 0);
    tokenInput.value = 'synthetic-token'; resolve(); await pending;
    assert.equal(requests.length, 1);
    assert.equal(requests[0].data['CSRF-TOKEN'], 'synthetic-token');
    assert.equal(requests[0].headers, undefined, 'CSRFGuard already adds the XHR header');
    await context[action]();
    assert.equal(requests.length, 1, 'pending AJAX blocks another activation');
    requests[0].complete();
    await context[action]();
    assert.equal(requests.length, 2, 'completion permits another request');
  });
  test(`${action} fails visibly without submitting when token bootstrap fails`, async () => {
    const alerts = []; let sent = false;
    const context = vm.createContext({
      window: { csrfTokenReady: Promise.reject(new Error('fixture fetch failure')) },
      document: { getElementById: () => ({ value: 'Synthetic' }), querySelector: () => ({ value: '' }) },
      $: { ajax: () => { sent = true; } }, alert: text => alerts.push(text),
    });
    vm.runInContext(csrfScript, context); vm.runInContext(script, context); await context[action]();
    assert.equal(sent, false); assert.equal(alerts.length, 1); assert.match(alerts[0], /Reload and try again/);
  });
}

for (const tokenInput of [null, { value: '' }]) {
  test(`missing token fails visibly after bootstrap (${tokenInput === null ? 'absent input' : 'empty input'})`, async () => {
    const alerts = [];
    const context = vm.createContext({ window: {}, document: { querySelector: () => tokenInput }, alert: text => alerts.push(text) });
    vm.runInContext(csrfScript, context);
    assert.equal(await context.paymentTypeCsrfToken(), null);
    assert.equal(alerts.length, 1);
  });
}
for (const rejected of [false, true]) {
  test(`delete ${rejected ? 'rejects failed bootstrap' : 'waits for the token and sends the selected id'}`, async () => {
    let resolve; let reject; let handler;
    const ready = new Promise((yes,no) => { resolve = yes; reject = no; });
    const requests = []; const alerts = [];
    const tokenInput = { value: '' };
    const doc = { querySelector: () => tokenInput };
    function jquery() { return { ready: callback => callback(), on: (_event,_selector,callback) => { handler = callback; }, DataTable: () => {} }; }
    jquery.ajax = request => requests.push(request);
    const context = vm.createContext({ window: { csrfTokenReady: ready }, document: doc, jQuery: jquery, alert: text => alerts.push(text) });
    const source = fs.readFileSync(path.join(root, 'manageBillingPaymentType.jsp'), 'utf8');
    const code = deleteScript(source);
    assert.ok(code, 'the actual delete handler must be executed');
    vm.runInContext(csrfScript, context); vm.runInContext(code, context);
    let prevented = false;
    const pending = handler({ preventDefault() { prevented = true; }, target: { getAttribute: () => '17' } });
    await handler({ preventDefault() {}, target: { getAttribute: () => '17' } });
    await Promise.resolve(); assert.equal(requests.length, 0); assert.equal(prevented, true);
    if (rejected) reject(new Error('fixture')); else { tokenInput.value = 'synthetic-token'; resolve(); }
    await pending;
    if (rejected) { assert.equal(requests.length, 0); assert.equal(alerts.length, 1); }
    else {
      assert.equal(requests.length, 1); assert.equal(requests[0].data.paymentTypeId, '17');
      assert.equal(requests[0].data['CSRF-TOKEN'], 'synthetic-token'); assert.equal(requests[0].headers, undefined);
      assert.equal(alerts.length, 0);
      await handler({ preventDefault() {}, target: { getAttribute: () => '17' } });
      assert.equal(requests.length, 1, 'pending delete is not repeated');
      requests[0].complete();
      await handler({ preventDefault() {}, target: { getAttribute: () => '17' } });
      assert.equal(requests.length, 2);
    }
  });
}

for (const result of [null, {}, { ret: '1', reason: 'Already exists' }, { ret: '0' }, { ret: 0 }]) {
  test(`save response ${JSON.stringify(result)} only navigates after explicit success`, () => {
    const alerts = []; let navigated = 0;
    const context = vm.createContext({ alert: text => alerts.push(text), history: { back: () => navigated++ } });
    vm.runInContext(csrfScript, context); context.paymentTypeSaveResult(result);
    const success = result && (result.ret === '0' || result.ret === 0);
    assert.equal(navigated, success ? 1 : 0); assert.equal(alerts.length, 1);
    assert.equal(alerts[0], success ? 'Success' : result?.reason || 'Payment type was not saved.');
  });
}
test('transport failure produces readable text and does not navigate', () => {
  const alerts = [];
  const context = vm.createContext({ alert: text => alerts.push(text), history: { back: () => assert.fail('unexpected navigation') } });
  vm.runInContext(csrfScript, context);
  context.paymentTypeRequestFailed(null, 'timeout', null);
  context.paymentTypeRequestFailed(null, '', new Error('synthetic failure'));
  context.paymentTypeRequestFailed(null, '', '');
  assert.deepEqual(alerts, ['timeout', 'Error: synthetic failure', 'Unknown error happened!']);
});

function deleteScript(source) {
  return [...source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\b[^>]*>/gi)]
    .map(match => match[1]).find(value => value.includes('data-paymentTypeId'));
}

function deleteFixture() {
  let handler;
  const requests = []; const alerts = [];
  const location = { href: 'payment-list' };
  function jquery() {
    return { ready: callback => callback(), on: (_event, _selector, callback) => { handler = callback; } };
  }
  jquery.ajax = request => requests.push(request);
  const context = vm.createContext({
    window: { csrfTokenReady: Promise.resolve() },
    document: { querySelector: () => ({ value: 'synthetic-token' }) },
    jQuery: jquery, alert: text => alerts.push(text), location,
  });
  vm.runInContext(csrfScript, context);
  vm.runInContext(deleteScript(fs.readFileSync(path.join(root, 'manageBillingPaymentType.jsp'), 'utf8')), context);
  return {
    requests, alerts, location,
    click: () => handler({ preventDefault() {}, target: { getAttribute: () => '17' } }),
  };
}

for (const result of [null, {}, { ret: 1, reason: 'This payment type has been used in some payment!' },
  { ret: '1', reason: 'Invalid paymentTypeId' }, { ret: '0invalid' }, { ret: '0' }, { ret: 0 }]) {
  test(`delete response ${JSON.stringify(result)} only redirects after explicit success`, async () => {
    const fixture = deleteFixture();
    await fixture.click();
    fixture.requests[0].success(result);
    const success = result && (result.ret === '0' || result.ret === 0);
    assert.equal(fixture.location.href, success
      ? '${pageContext.request.contextPath}/billing/CA/ON/managePaymentType' : 'payment-list');
    assert.equal(fixture.alerts.length, 1);
    if (result?.reason) assert.equal(fixture.alerts[0], 'Failed to delete the payment type, reason:' + result.reason);
    if (success) assert.match(fixture.alerts[0], /deleting the payment type/);
    // jQuery runs complete after the success callback, including application errors.
    fixture.requests[0].complete();
    await fixture.click();
    assert.equal(fixture.requests.length, 2, 'application errors must not leave delete locked');
  });
}

test('delete transport failure shows the server reason, stays on the list and permits retry', async () => {
  const fixture = deleteFixture();
  await fixture.click();
  fixture.requests[0].error({ responseJSON: { reason: 'Synthetic delete failure' }, status: 503 }, 'error', 'Unavailable');
  assert.deepEqual(fixture.alerts, ['Synthetic delete failure']);
  assert.equal(fixture.location.href, 'payment-list');
  fixture.requests[0].complete();
  await fixture.click();
  assert.equal(fixture.requests.length, 2);
});

for (const closing of ['</SCRIPT >', '</script data-ignored="fixture">']) {
  test(`script extraction accepts HTML closing tag variation ${closing}`, () => {
    const source = fs.readFileSync(path.join(root, 'manageBillingPaymentType.jsp'), 'utf8');
    assert.equal(deleteScript(source.replaceAll('</script>', closing)), deleteScript(source));
  });
}

test('transport failure prefers server reason, then error detail, then HTTP status', () => {
  const alerts = [];
  const context = vm.createContext({ alert: text => alerts.push(text) });
  vm.runInContext(csrfScript, context);
  context.paymentTypeRequestFailed({ responseJSON: { reason: 'Already exists' }, status: 409 }, 'error', 'Conflict');
  context.paymentTypeRequestFailed({ status: 403 }, 'error', 'Forbidden');
  context.paymentTypeRequestFailed({ status: 503 }, 'error', '');
  assert.deepEqual(alerts, ['Already exists', 'Forbidden', 'HTTP 503']);
});
test('failed token bootstrap releases the request guard for retry', async () => {
  const input = { value: '' };
  const context = vm.createContext({ window: {}, document: { querySelector: () => input }, alert() {} });
  vm.runInContext(csrfScript, context);
  assert.equal(await context.paymentTypeBeginRequest(), null);
  input.value = 'synthetic-token';
  assert.equal(await context.paymentTypeBeginRequest(), 'synthetic-token');
});

// csrf-token.jspf installs window.csrfTokenReady on DOMContentLoaded. A click on an
// already-rendered button can beat that, and the handler must wait rather than report
// a bootstrap failure. The listener order below is the hostile one: the token helper
// registers first, so the fragment's listener only installs the promise afterwards.
test('token retrieval waits for DOMContentLoaded when bootstrap has not installed its promise', async () => {
  const alerts = []; const input = { value: '' }; const listeners = [];
  const window = { csrfTokenReady: null };
  const document = {
    readyState: 'loading',
    querySelector: () => input,
    addEventListener: (event, callback) => { if (event === 'DOMContentLoaded') listeners.push(callback); },
  };
  const context = vm.createContext({ window, document, alert: text => alerts.push(text), Promise });
  vm.runInContext(csrfScript, context);
  const pending = context.paymentTypeCsrfToken();
  await Promise.resolve();
  assert.equal(listeners.length, 1, 'the helper must wait for the bootstrap event');
  assert.equal(alerts.length, 0, 'a pending bootstrap is not a failure');
  listeners.push(() => { // the fragment's own listener, registered after the helper's
    input.value = 'synthetic-token';
    window.csrfTokenReady = Promise.resolve();
  });
  document.readyState = 'interactive';
  for (const listener of [...listeners]) listener();
  assert.equal(await pending, 'synthetic-token');
  assert.equal(alerts.length, 0);
});

// A rejected window.csrfTokenReady stays rejected for the life of the document, so
// releasing the in-flight guard alone never makes a failed bootstrap retryable. The
// helper must refetch, the way efmformmanager.jsp does.
function csrfRetryContext(fetchCsrfToken, scriptSource = 'https://host/carlos/billing/CA/ON/payment-type-csrf.js') {
  const input = { value: '' };
  const alerts = [];
  const context = vm.createContext({
    window: { csrfTokenReady: Promise.reject(new Error('fixture bootstrap failure')) },
    document: {
      readyState: 'complete',
      querySelector: () => input,
      currentScript: { src: scriptSource },
    },
    fetchCsrfToken,
    alert: text => alerts.push(text),
  });
  vm.runInContext(csrfScript, context);
  return { context, input, alerts };
}

test('a rejected bootstrap is recovered by refetching the token', async () => {
  const requested = [];
  const { context, input, alerts } = csrfRetryContext(async contextPath => {
    requested.push(contextPath);
    input.value = 'refetched-token';
  });
  assert.equal(await context.paymentTypeBeginRequest(), 'refetched-token');
  assert.deepEqual(requested, ['https://host/carlos'], 'the context path comes from the script URL');
  assert.equal(alerts.length, 0, 'a recovered bootstrap is not reported as a failure');
  context.paymentTypeRequestComplete();
  assert.equal(await context.paymentTypeBeginRequest(), 'refetched-token', 'the guard still releases');
});

test('a refetch that also fails reports the failure and submits nothing', async () => {
  const { context, alerts } = csrfRetryContext(() => Promise.reject(new Error('fixture refetch failure')));
  assert.equal(await context.paymentTypeBeginRequest(), null);
  assert.deepEqual(alerts, ['Security token unavailable. Reload and try again.']);
});

for (const [source, expectedPrefix] of [
  ['https://host/billing/CA/ON/payment-type-csrf.js', 'https://host'],
  ['/billing/CA/ON/payment-type-csrf.js', ''],
]) {
  test(`root deployment recovers failed bootstrap with script source ${source}`, async () => {
    const prefixes = [];
    const { context, input, alerts } = csrfRetryContext(async prefix => {
      prefixes.push(prefix);
      input.value = 'root-token';
    }, source);
    assert.equal(await context.paymentTypeBeginRequest(), 'root-token');
    assert.deepEqual(prefixes, [expectedPrefix]);
    assert.equal(alerts.length, 0);
  });
}

test('no refetch is attempted when the script URL does not reveal a context path', async () => {
  const alerts = [];
  const context = vm.createContext({
    window: { csrfTokenReady: Promise.reject(new Error('fixture bootstrap failure')) },
    document: { readyState: 'complete', querySelector: () => ({ value: '' }) },
    fetchCsrfToken: () => assert.fail('refetch needs a context path'),
    alert: text => alerts.push(text),
  });
  vm.runInContext(csrfScript, context);
  assert.equal(await context.paymentTypeCsrfToken(), null);
  assert.equal(alerts.length, 1);
});
