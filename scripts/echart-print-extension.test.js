/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterLayout.jsp'), 'utf8');
const start = jsp.indexOf('function addPrintOption(');
const end = jsp.indexOf('<%', start);
const code = jsp.slice(start, end);

test('failed extension registration reports the failure and adds no unusable print option', () => {
  let options;
  const alerts = [];
  const jQuery = () => { throw new Error('A failed registration must not change the dialog'); };
  jQuery.ajax = value => { options = value; };
  const context = vm.createContext({jQuery, ctx: '/carlos', alert: value => alerts.push(value)});
  vm.runInContext(code, context);
  context.addPrintOption('Custom printer', 'customBean');
  assert.equal(options.type, 'POST');
  assert.equal(options.data.name, 'Custom printer');
  assert.equal(options.data.bean, 'customBean');
  options.error();
  assert.equal(alerts.length, 1);
  assert.match(alerts[0], /Unable to register/);
});

test('re-registering an existing print option does not duplicate its controls', () => {
  let options;
  const jQuery = () => { throw new Error('An existing option must not be appended again'); };
  jQuery.ajax = value => { options = value; };
  const context = vm.createContext({jQuery, ctx: '/carlos',
    document: {getElementById: id => id === 'extPrintCustom' ? {} : null}});
  vm.runInContext(code, context);
  context.addPrintOption('Custom', 'customBean');
  options.success();
});
