#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/admin/keygen/keyManager.jsp'), 'utf8');
for (const contextPath of ['', '/carlos', '/clinic/carlos']) {
  test(`key manager handlers keep the application context ${contextPath || '(root)'}`, () => {
    const rendered = jsp.replaceAll('${carlos:forJavaScriptAttribute(pageContext.request.contextPath)}', contextPath)
      .replaceAll('${carlos:forJavaScriptBlock(pageContext.request.contextPath)}', contextPath);
    const update = rendered.match(/    function updateMatchingProcessionalSpecialist\(\) \{[\s\S]*?\n    \}/)[0];
    const create = rendered.match(/value="Create New Key" onclick="([^"]+)"/)[1];
    const values = {selectKeyList: {value: 'owned-service'}, selectProfessionalSpecialistList: {value: '42'}};
    let submitted;
    let confirmation;
    const document = {getElementById: id => values[id]};
    const context = vm.createContext({document, getSelectListValue: select => select.value,
      alert: text => { confirmation = text; },
      jQuery: {post(url, params, done) { submitted = {url, params}; done({}); }}});
    vm.runInContext(update, context);
    context.updateMatchingProcessionalSpecialist();
    assert.equal(submitted.url, `${contextPath}/admin/ViewKeygenUpdateMatchingProfessionalSpecialist`);
    assert.equal(submitted.params.serviceName, 'owned-service');
    assert.equal(submitted.params.professionalSpecialistId, '42');
    assert.equal(confirmation, 'Changes saved.');
    vm.runInContext(create, context);
    assert.equal(document.location, `${contextPath}/admin/ViewKeygenCreateKey`);
  });
}
