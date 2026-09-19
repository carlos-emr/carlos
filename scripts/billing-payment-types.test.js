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
