/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/admin/manageCSSStyles.jsp'), 'utf8');
// nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- Executes only the inline handler from this fixed, checked-in JSP fixture; no user-supplied HTML is accepted or rendered.
const source = jsp.match(/<script type="text\/javascript">([\s\S]*?)<\/script\s*>/i)[1]
  .replace(/<fmt:message key="([^"]+)"\s*\/>/g, '$1');
function setup() {
  const nodes = {};
  for (const id of ['style', 'styleText', 'styleName', 'editStyle', 'method', 'example', 'apply-btn',
    'font-size', 'font-style', 'font-variant', 'font-weight', 'text-decoration', 'color', 'background-color']) {
    nodes[id] = {value:'', style:{}, selectedIndex:0, options:[{value:'', text:''}]};
  }
  nodes.style.options = [{value:'-1', text:'None'}, {value:'color:#123456;background-color:#ffffff', text:'Saved'}];
  const alerts = [];
  const context = vm.createContext({document:{getElementById:id => nodes[id] || null}, alert:msg => alerts.push(msg), setInterval() {}});
  vm.runInContext(source, context);
  context.init();
  return {nodes, context, alerts};
}
test('Save preserves a manually entered colour when its picker is empty', () => {
  const {nodes, context} = setup();
  nodes.styleName.value = 'Manual';
  nodes.styleText.value = 'color:#123456;text-decoration:underline;';
  assert.equal(context.checkfields(), true);
  assert.equal(nodes.styleText.value, 'color:#123456;text-decoration:underline;');
  assert.equal(nodes.method.value, 'save');
});
test('editing a style reads its last declaration without a trailing semicolon', () => {
  const {nodes, context} = setup();
  nodes.style.selectedIndex = 1;
  context.edit();
  assert.equal(nodes.color.value, '#123456');
  assert.equal(nodes['background-color'].value, '#ffffff');
});
test('loading a saved style does not let the next picker poll overwrite a manual edit', () => {
  const {nodes, context} = setup();
  nodes.style.selectedIndex = 1;
  context.edit();
  nodes.styleText.value = 'color:#abcdef;';
  context.checkColours();
  assert.equal(nodes.styleText.value, 'color:#abcdef;');
});
test('changing text colour preserves the complete background colour declaration', () => {
  const {nodes, context} = setup();
  nodes.styleText.value = 'background-color:#ffffff; color:#123456';
  context.addStyle('color', {value:'#abcdef'});
  assert.equal(nodes.styleText.value, 'background-color:#ffffff;color:#abcdef;');
});

test('Save incorporates a popup picker change without an onchange event', () => {
  const {nodes, context} = setup();
  nodes.styleName.value = 'Popup';
  nodes.color.value = '#abcdef';
  assert.equal(context.checkfields(), true);
  assert.equal(nodes.styleText.value, 'color:#abcdef;');
});
test('clearing a picker explicitly removes only its declaration', () => {
  const {nodes, context} = setup();
  nodes.style.selectedIndex = 1;
  context.edit();
  nodes.color.value = '';
  context.checkColours();
  assert.equal(nodes.styleText.value, 'background-color:#ffffff;');
});
