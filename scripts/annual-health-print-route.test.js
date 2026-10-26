/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/form/formannualfemale.jsp'), 'utf8');
const handler = jsp.match(/onclick="([^"]*popupPage\(700,950,'[^']*formannualfemaleprint[^']*'\))"/)[1];
for (const contextPath of ['', '/carlos', '/nested/carlos']) {
  test(`Print Page opens the gated form route under context ${contextPath || '/'}`, () => {
    const calls = [];
    const rendered = handler
      .replaceAll('<%= request.getContextPath() %>', contextPath)
      .replaceAll('<%=demoNo%>', '123')
      .replaceAll('<%=formId%>', '456')
      .replaceAll('<%=provNo%>', '789');
    vm.runInNewContext(rendered, { popupPage: (...args) => calls.push(args) });
    assert.equal(calls.length, 1);
    assert.deepEqual(calls[0].slice(0, 2), [700, 950]);
    // The form's <base> is the application root, even when opened via a nested action.
    const url = new URL(calls[0][2], `https://example.test${contextPath}/`);
    assert.equal(url.origin, 'https://example.test');
    assert.equal(url.pathname, `${contextPath}/form/formannualfemaleprint`);
    assert.deepEqual([...url.searchParams], [
      ['demographic_no', '123'], ['formId', '456'], ['provNo', '789'],
    ]);
  });
}
