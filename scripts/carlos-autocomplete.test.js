/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

function render(kind, result, term) {
  let options;
  const widget = {};
  const escapeText = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
  const input = { length: 1, autocomplete(arg) {
    if (arg === 'instance') return widget;
    options = arg;
    return this;
  } };
  function jQuery(selector) {
    if (selector === '#search') return input;
    assert.ok(['<li>', '<div>'].includes(selector));
    return { content: '', text(value) { this.content = escapeText(value); return this; },
      html(value) { this.content = value; return this; },
      append(child) { this.content += child.content; return this; }, appendTo() { return this; } };
  }
  jQuery.ajax = request => request.success({ results: [result] });
  const context = { jQuery, console, document: { createElement() {
    return { textContent: '', get innerHTML() { return escapeText(this.textContent); } };
  } } };
  vm.createContext(context);
  for (const file of ['demographicProviderAutocomplete.js', 'carlosAutocomplete.js']) {
    vm.runInContext(fs.readFileSync('src/main/webapp/js/' + file, 'utf8'), context);
  }
  context[kind]('#search', '/carlos', null);
  let item;
  options.source({ term }, results => { item = results[0]; });
  return widget._renderItem({}, item).content;
}

test('provider highlights render as markup while names stay escaped', () => {
  const html = render('initProviderAutocomplete', { providerNo: '42', firstName: 'Test', lastName: '<img src=x onerror=alert(1)>' }, 'test');
  assert.ok(html.includes("<span class='match'>Test</span>"));
  assert.ok(html.includes('&lt;img src=x onerror=alert(1)&gt;'));
  assert.ok(!html.includes('<img'));
});

test('patient highlights render as markup while every dynamic field stays escaped', () => {
  const html = render('initDemographicAutocomplete', { demographicNo: '100', formattedName: 'Test <script>x</script>',
    fomattedDob: '<b>date</b>', status: '<img src=x>' }, 'test');
  assert.ok(html.includes("<span class='match'>Test</span>"));
  for (const text of ['&lt;script&gt;x&lt;/script&gt;', '&lt;b&gt;date&lt;/b&gt;', '&lt;img src=x&gt;']) assert.ok(html.includes(text));
  assert.ok(!/<script|<img|<b>/.test(html));
});
