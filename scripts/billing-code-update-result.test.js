/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingCodeUpdate.jsp'), 'utf8');
// Execute the update-result script from this fixed repository fixture.
const scripts = [...jsp.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\b[^>]*>/gi)];
const resultScript = scripts.at(-1)[1];

test('description update returns to the search without accessing or refreshing the unsaved bill', () => {
  const navigations = [];
  const self = {};
  Object.defineProperty(self, 'opener', { get() { throw new Error('The unsaved correction must stay untouched'); } });
  vm.runInNewContext(resultScript, { history: { go: direction => navigations.push(direction) }, self });
  assert.deepEqual(navigations, [-1]);
});
