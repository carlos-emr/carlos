/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('loading OHIP simulation initializes its own form without hijacking shell navigation', () => {
  const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingOHIPsimulation.jsp'), 'utf8');
  const scripts = [...source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)];
  const script = scripts.find(match => match[1].includes("registerFormSubmit('serviceform'"));
  assert.ok(script, 'The billing simulation initialization script was not found');
  const registered = [], dates = [], listeners = [];
  vm.runInNewContext(script[1], {
    registerFormSubmit: (...args) => registered.push(args),
    flatpickr: selector => dates.push(selector),
    document: { querySelectorAll: () => [{addEventListener: (...args) => listeners.push(args)}] },
  });
  assert.deepEqual(registered, [['serviceform', 'dynamic-content']]);
  assert.deepEqual(dates, ['#xml_vdate', '#xml_appointment_date']);
  assert.equal(listeners.length, 0, 'A billing fragment attached an extra shell navigation handler');
});
