/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/billing/CA/ON');
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
    vm.runInContext(script, context);
    const pending = context[action]();
    await Promise.resolve(); assert.equal(requests.length, 0);
    tokenInput.value = 'synthetic-token'; resolve(); await pending;
    assert.equal(requests.length, 1);
    assert.equal(requests[0].data['CSRF-TOKEN'], 'synthetic-token');
    assert.equal(requests[0].headers, undefined, 'CSRFGuard already adds the XHR header');
  });
  test(`${action} fails visibly without submitting when token bootstrap fails`, async () => {
    const alerts = []; let sent = false;
    const context = vm.createContext({
      window: { csrfTokenReady: Promise.reject(new Error('fixture fetch failure')) },
      document: { getElementById: () => ({ value: 'Synthetic' }), querySelector: () => ({ value: '' }) },
      $: { ajax: () => { sent = true; } }, alert: text => alerts.push(text),
    });
    vm.runInContext(script, context); await context[action]();
    assert.equal(sent, false); assert.equal(alerts.length, 1); assert.match(alerts[0], /Reload and try again/);
  });
}
