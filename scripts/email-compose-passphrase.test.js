// Regression coverage for tokenized compose cancellation. Licensed under LICENSE.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp'), 'utf8');
const start = source.indexOf('    async function cancelEmail()');
assert.ok(start >= 0, 'production cancellation handler exists');
const handler = source.slice(start, source.indexOf('\n    function showAdditionalParamOption()', start));

function fixture(fetch, type = 'DIRECT') {
  const events = [];
  const classes = new Set(['d-none']);
  const form = { submit() { events.push('submit'); } };
  const button = { disabled: false };
  const controls = {
    emailComposeForm: form, transactionType: { value: type }, btnCancel: button,
    emailCancelError: { classList: { add: value => classes.add(value), remove: value => classes.delete(value) } }
  };
  const context = vm.createContext({
    document: { getElementById: id => controls[id] },
    window: { close: () => events.push('close') },
    URLSearchParams,
    FormData: class {
      constructor(value) { assert.equal(value, form); }
      *[Symbol.iterator]() {
        yield ["OWASP_CSRFTOKEN", "synthetic-csrf-token"];
        yield ["emailPDFPasswordToken", "opaque-compose-token"];
      }
    },
    fetch
  });
  vm.runInContext(handler, context);
  return { cancel: () => context.cancelEmail(), events, classes, button, form };
}

test('direct cancellation waits for server cleanup before closing', async () => {
  let acknowledge;
  const f = fixture((url, options) => {
    assert.ok(url.endsWith('/email/emailSendAction?method=cancel'));
    assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'same-origin');
    assert.ok(options.body instanceof URLSearchParams);
    assert.equal(options.body.get('OWASP_CSRFTOKEN'), 'synthetic-csrf-token');
    assert.equal(options.body.get('emailPDFPasswordToken'), 'opaque-compose-token');
    return new Promise(resolve => { acknowledge = resolve; });
  });
  const pending = f.cancel();
  assert.deepEqual(f.events, []);
  assert.equal(f.button.disabled, true);
  acknowledge({ status: 204 });
  await pending;
  assert.deepEqual(f.events, ['close']);
  assert.equal(f.button.disabled, false);
});

for (const status of [200, 401, 500]) {
  test(`HTTP ${status} cancellation response keeps the window open and permits retry`, async () => {
    const f = fixture(async () => ({ status }));
    await f.cancel();
    assert.deepEqual(f.events, []);
    assert.equal(f.classes.has('d-none'), false);
    assert.equal(f.button.disabled, false);
  });
}

test('network failure leaves an explicit cancellation error', async () => {
  const f = fixture(async () => { throw new Error('offline'); });
  await f.cancel();
  assert.deepEqual(f.events, []);
  assert.equal(f.classes.has('d-none'), false);
  assert.equal(f.button.disabled, false);
});

test('eForm cancellation posts normally so the server can redirect to its trusted context', async () => {
  const f = fixture(() => assert.fail('eForm cancellation must use navigation'), 'EFORM');
  await f.cancel();
  assert.deepEqual(f.events, ['submit']);
  assert.ok(f.form.action.endsWith('/email/emailSendAction?method=cancel'));
});
