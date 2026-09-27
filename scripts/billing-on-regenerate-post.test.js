/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingONMRI.jsp'), 'utf8');
const start = jsp.indexOf('function recreate(');
const end = jsp.indexOf('var providerBillCenterMap', start);
assert.ok(start >= 0 && end > start);
const source = jsp.slice(start, end)
  .replace(/<carlos:encode[^>]+\/>/g, 'Missing security token; reload the page')
  .replace(/\$\{pageContext.request.contextPath\}/g, '/carlos');

function regenerate(token, confirmed = true) {
  const submissions = [];
  const errors = [];
  const appended = [];
  vm.runInNewContext(`${source}\nrecreate('42');`, {
    confirm: () => confirmed,
    alert: (message) => errors.push(message),
    document: {
      querySelector: () => token,
      forms: [{ billcenter: Object.assign([{ value: '4' }], { selectedIndex: 0 }), useProviderMOH: { checked: true } }],
      body: { appendChild(form) { appended.push(form); } },
      createElement(tag) {
        return { tag, children: [], appendChild(child) { this.children.push(child); }, submit() { submissions.push(this); } };
      },
    },
  });
  return { submissions, errors, appended };
}

test('R submits the selected disk and current token together by POST', () => {
  const result = regenerate({ value: 'session-token' });
  assert.equal(result.submissions.length, 1);
  assert.deepEqual(result.errors, []);
  const form = result.submissions[0];
  assert.equal(form.method, 'post');
  assert.equal(form.action, '/carlos/billing/CA/ON/ViewOnregenreport');
  assert.deepEqual(Object.fromEntries(form.children.map((input) => [input.name, input.value])),
    { diskId: '42', billcenter: '4', useProviderMOH: true, 'CSRF-TOKEN': 'session-token' });
});

test('R reports missing or empty tokens without submitting a mutation', () => {
  for (const token of [null, { value: '' }]) {
    const result = regenerate(token);
    assert.equal(result.submissions.length, 0);
    assert.equal(result.appended.length, 0);
    assert.deepEqual(result.errors, ['Missing security token; reload the page']);
  }
});

test('cancelling regeneration creates no form and sends no request', () => {
  const result = regenerate({ value: 'session-token' }, false);
  assert.equal(result.submissions.length, 0);
  assert.equal(result.appended.length, 0);
  assert.deepEqual(result.errors, []);
});
