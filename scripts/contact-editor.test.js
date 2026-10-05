/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/demographic/addEditContact.jsp'), 'utf8');
const script = [...jsp.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)]
  .map(match => match[1]).find(source => source.includes('function onSave('));
assert.ok(script, 'The contact editor script is missing');

function setup(locale, includeLastName = true) {
  const properties = fs.readFileSync(path.join(__dirname, `../src/main/resources/oscarResources_${locale}.properties`), 'utf8');
  const message = key => {
    const line = properties.split('\n').find(line => line.startsWith(key + '='));
    assert.ok(line, `Missing ${locale} message ${key}`);
    return line.slice(key.length + 1).replace(/\\u([\da-f]{4})/gi, (_, value) => String.fromCharCode(parseInt(value, 16)));
  };
  const variables = {};
  for (const [, key, variable] of jsp.matchAll(/<fmt:message key="([^"]+)" var="([^"]+)"\s*\/>/g)) variables[variable] = message(key);
  // Model the JSP message substitution and the declared JavaScript encoder context. The live
  // browser check exercises the real tag implementation; raw fmt output must fail parsing here.
  const source = script.replace(/<fmt:message key='([^']+)'\s*\/>/g, (_, key) => message(key))
    .replace(/<carlos:encode value='\$\{([^}]+)\}' context='javaScriptBlock'\s*\/>/g, (_, variable) => {
      assert.equal(typeof variables[variable], 'string');
      return JSON.stringify(variables[variable]).slice(1, -1).replaceAll('<', '\\u003c');
    });
  const events = [];
  const last = { value: 'Contact', focus() { events.push('focus'); }, select() { events.push('select'); } };
  const first = { value: 'Synthetic' };
  const elements = { 'contact.firstName': first };
  if (includeLastName) elements['contact.lastName'] = last;
  const context = vm.createContext({ document: { forms: [{ elements }] }, focus() {}, alert: value => events.push(value) });
  vm.runInContext(source, context);
  return { context, events, last, first, message };
}

for (const locale of ['en', 'es', 'fr', 'pl', 'pt_BR']) {
  test(`contact editor parses translated quotes, focuses the real field and preserves validation text (${locale})`, () => {
    const s = setup(locale);
    assert.equal(s.context.onSave(), true);
    s.context.setfocus();
    assert.deepEqual(s.events.splice(0), ['focus', 'select']);
    assert.equal(s.context.checkAllFields(), true);
    s.last.value = '';
    assert.equal(s.context.checkAllFields(), false);
    assert.deepEqual(s.events.splice(0), [s.message('demographic.contactForm.msgLastNameRequired')]);
    s.last.value = 'Contact';
    s.first.value = '';
    assert.equal(s.context.checkAllFields(), false);
    assert.deepEqual(s.events, [s.message('demographic.contactForm.msgFirstNameRequired')]);
  });
}

test('contact focus tolerates an unavailable last-name control', () => {
  const s = setup('en', false);
  assert.doesNotThrow(() => s.context.setfocus());
  assert.deepEqual(s.events, []);
});
