/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/tickler/ticklerSuggestedText.jsp'), 'utf8');
const script = [...jsp.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\b[^>]*>/gi)]
  .map(match => match[1]).find(code => code.includes('function addToList('))
  .replace(/<fmt:message\b[^>]*\/>/g, 'Empty');
const option = (value, text = value) => ({ value, text, className: 'existing-style', selected: true });
function editor(value = '  Follow up <tomorrow>  ') {
  const select = () => ({ options: [option('0', '')], selectedIndex: 0,
    add(item) { this.options.push(item); }, remove(index) { this.options.splice(index, 1); } });
  const lists = { activeText: select(), inactiveText: select() };
  const input = { value, focused: false, focus() { this.focused = true; } };
  const context = { document: {
    getElementsByName: name => [lists[name]], getElementById: () => input,
    createElement: () => option('', ''),
  } };
  vm.createContext(context); vm.runInContext(script, context);
  return { context, lists, input };
}
for (const list of ['activeText', 'inactiveText']) {
  test(`the first suggestion in an empty ${list} retains its exact text and removes the placeholder`, () => {
    const { context, lists, input } = editor();
    context.addToList(list, 'newTextSuggest');
    assert.equal(lists[list].options.length, 1);
    assert.equal(lists[list].options[0].value, '  Follow up <tomorrow>  ');
    assert.equal(lists[list].options[0].text, '  Follow up <tomorrow>  ');
    assert.equal(input.value, '');
  });
}
for (const value of ['', ' \t ']) {
  test(`blank text ${JSON.stringify(value)} does not replace the empty-list placeholder`, () => {
    const { context, lists, input } = editor(value);
    context.addToList('activeText', 'newTextSuggest');
    assert.deepEqual(lists.activeText.options.map(x => x.value), ['0']);
    assert.equal(input.value, value);
    assert.equal(input.focused, true);
  });
}
test('moving the only real suggestion leaves a placeholder and preserves the destination value', () => {
  const { context, lists } = editor();
  lists.activeText.options = [option('11', 'Existing suggestion')];
  context.swap('activeText', 'inactiveText');
  assert.deepEqual(lists.activeText.options.map(x => x.value), ['0']);
  assert.deepEqual(lists.inactiveText.options.map(x => x.value), ['11']);
  assert.equal(lists.inactiveText.options[0].text, 'Existing suggestion');
  assert.equal(lists.inactiveText.options[0].className, 'existing-style');
});
