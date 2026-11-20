/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/provider/providerpreference.jsp'), 'utf8');
const script = jsp.slice(jsp.indexOf('function submitQuickLinkAction('), jsp.indexOf('/**\n * Adds a quick link'));

test('quick-link POST form exists at page load outside the preferences form for CSRFGuard injection', () => {
  const match = jsp.match(/<form id="quickLinkActionForm"[^>]*(?:%>[^>]*)?>[\s\S]*?<\/form>/);
  assert.ok(match);
  assert.match(match[0], /method="post"/);
  assert.match(match[0], /\/provider\/ViewProviderPreferenceQuickLinks/);
  assert.ok(jsp.lastIndexOf('</form>', match.index) > jsp.lastIndexOf('<form', match.index - 1));
  for (const name of ['action', 'name', 'url']) assert.ok(match[0].includes(`name="${name}"`));
});

for (const action of ['add', 'remove']) {
  test(`${action} preserves the injected token and literal values when submitting`, () => {
    const fields = Object.fromEntries(['action', 'name', 'url', 'CSRF-TOKEN'].map(name => [name, { value: name === 'CSRF-TOKEN' ? 'test-token' : 'old value' }]));
    let submitted = 0;
    const form = { elements: { namedItem: name => fields[name] }, submit() { submitted++; } };
    const context = { document: { getElementById(id) { assert.equal(id, 'quickLinkActionForm'); return form; } } };
    vm.createContext(context); vm.runInContext(script, context);
    const url = action === 'add' ? 'https://example.invalid/?a=A%2BB&b=1' : '';
    context.submitQuickLinkAction(action, "Fixture & 'link'", url);
    assert.equal(submitted, 1);
    assert.equal(fields['CSRF-TOKEN'].value, 'test-token');
    assert.equal(fields.action.value, action);
    assert.equal(fields.name.value, "Fixture & 'link'");
    assert.equal(fields.url.value, url);
  });
}
