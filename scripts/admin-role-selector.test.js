#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/admin/providerPrivilege.jsp'), 'utf8');
const handler = jsp.match(/        function onChangeSelect\(\) \{[\s\S]*?\n        \}/)[0];
for (const role of ['', 'doctor', '0']) {
  test(`role selection updates the provider selector without removed object controls: ${role}`, () => {
    const primary = {value: role};
    const provider = {style: {}};
    const fields = {roleUserGroup: primary, roleUserGroup1: provider};
    const document = {forms: {namedItem(name) {
      assert.equal(name, 'myform2');
      return {elements: {namedItem: name => fields[name]}};
    }}};
    const context = vm.createContext({document, newBrowser: true});
    vm.runInContext(handler, context);
    context.onChangeSelect();
    assert.equal(provider.style.backgroundColor, role ? 'silver' : 'white');
    assert.equal(provider.style.color, role ? 'silver' : 'black');
    primary.value = '';
    context.onChangeSelect();
    assert.equal(provider.style.backgroundColor, 'white');
    assert.equal(provider.style.color, 'black');
  });
}
