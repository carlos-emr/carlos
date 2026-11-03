/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/web/inboxhub/InboxhubListMode.jsp'), 'utf8');
const start = jsp.indexOf('    function reportWindow(');
const end = jsp.indexOf('    // Function to remove only <a> tags', start);
assert.ok(start >= 0 && end > start, 'reportWindow function must be present');
const reportWindow = jsp.slice(start, end);

test('inbox popup preserves encoded duplicate IDs and patient search parameters', () => {
  let opened;
  let focused = false;
  const popup = { opener: null, focus() { focused = true; } };
  const context = { self: {}, window: { open(url) { opened = url; return popup; } } };
  vm.createContext(context);
  vm.runInContext(reportWindow, context);
  const link = '/carlos/hospitalReportManager/Display?inWindow=true&duplicateLabIds=40%2C41&id=42'
    + '&firstName=Anne+Marie+%26+Zo%C3%AB&lastName=O%27Test%2FExample';

  context.reportWindow(link);

  assert.equal(opened, link);
  const parameters = new URL(opened, 'https://localhost').searchParams;
  assert.equal(parameters.get('duplicateLabIds'), '40,41');
  assert.equal(parameters.get('firstName'), 'Anne Marie & Zoë');
  assert.equal(parameters.get('lastName'), "O'Test/Example");
  assert.equal(parameters.get('id'), '42');
  assert.equal(popup.opener, context.self);
  assert.equal(focused, true);
});

test('inbox popup tolerates a blocked window', () => {
  const context = { window: { open() { return null; } } };
  vm.createContext(context);
  vm.runInContext(reportWindow, context);
  assert.doesNotThrow(() => context.reportWindow('/carlos/hospitalReportManager/Display?id=42', 600, 900));
});
