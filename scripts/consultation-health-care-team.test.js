/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');
const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp'), 'utf8');

function fixture(services, role) {
  const start = source.indexOf('// Read the Health Care Team from the pageScope');
  const end = source.indexOf('// Read the associated services', start);
  assert.ok(start > 0 && end > start, 'Missing JSP service registry');
  // Render only the registry's JSTL collection conditions/loop. Both the emitted
  // JavaScript and the selection/validation functions remain the production code.
  let registry = source.slice(start, end).replace(/<c:if\b[^>]*>([\s\S]*?)<\/c:if>/g,
    (_, body) => services.length ? body : '');
  registry = registry.replace(/<c:forEach\b[^>]*>([\s\S]*?)<\/c:forEach>/g, (_, body) =>
    services.map(service => body.replace(/\$\{\s*consultationService.serviceId\s*\}/g, service.id)
      .replace(/\$\{\s*consultationService.serviceDesc\s*\}/g, service.description)).join('\n'));
  const scripts = [...registry.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\s*>/gi)].map(match => match[1]);
  const form = Object.fromEntries(['annotation', 'phone', 'fax', 'address', 'service'].map(name =>
    [name, {value: name === 'service' ? '0' : 'FAKE previous value', focus() {}}]));
  form.specialist = {options: {selectedIndex: 0}, focus() {}};
  const member = {note: 'FAKE referral annotation', phoneNum: '555-0100', specFax: '555-0101',
    specAddress: 'FAKE synthetic address', service: role};
  const alerts = [];
  const context = vm.createContext({document: {EctConsultationFormRequest2Form: form},
    healthCareTeam: {41: member}, specialistFaxNumber: '', faxUpdates: 0,
    updateFaxButton() { context.faxUpdates++; }, alert(message) { alerts.push(message); }});
  vm.runInContext(scripts.join('\n'), context);
  for (const name of ['getSpecialist', 'checkFormHCT']) {
    const functionStart = source.indexOf('function ' + name + '(');
    assert.ok(functionStart > 0, 'Missing production function ' + name);
    let cursor = source.indexOf('{', functionStart);
    let depth = 0;
    do {
      if (source[cursor] === '{') depth++;
      if (source[cursor] === '}') depth--;
      cursor++;
    } while (depth > 0 && cursor < source.length);
    assert.equal(depth, 0, 'Unclosed production function ' + name);
    vm.runInContext(source.slice(functionStart, cursor), context);
  }
  return {context, form, member, alerts, choose() { context.getSpecialist({value: '41'}); }};
}

function assertContact(f) {
  assert.equal(f.form.annotation.value, f.member.note);
  assert.equal(f.form.phone.value, f.member.phoneNum);
  assert.equal(f.form.fax.value, f.member.specFax);
  assert.equal(f.form.address.value, f.member.specAddress);
  assert.equal(f.context.specialistFaxNumber, f.member.specFax);
  assert.ok(f.context.faxUpdates > 0);
  assert.equal(f.context.checkFormHCT(), true);
  assert.deepEqual(f.alerts, []);
}

test('a team member with no matching services keeps the HCT sentinel and can be submitted', () => {
  const f = fixture([], 'FAKE unmatched role');
  f.choose();
  assert.equal(f.form.service.value, '0');
  assertContact(f);
});

test('an unmatched role keeps the HCT sentinel when other services are available', () => {
  const f = fixture([{id: '13', description: 'Cardiology'}], 'FAKE unmatched role');
  f.choose();
  assert.equal(f.form.service.value, '0');
  assertContact(f);
});

for (const role of ['', null]) {
  test('a team member with ' + (role === null ? 'a NULL' : 'an empty') + ' role retains the HCT sentinel', () => {
    const f = fixture([], role);
    f.choose();
    assert.equal(f.form.service.value, '0');
    assertContact(f);
  });
}

test('a matching role retains its exact consultation service id', () => {
  const f = fixture([{id: '13', description: 'Cardiology'}], 'Cardiology');
  f.choose();
  assert.equal(f.form.service.value, '13');
  assertContact(f);
});

test('selecting an unmatched member after a matching member clears the previous service id', () => {
  const f = fixture([{id: '13', description: 'Cardiology'}], 'Cardiology');
  f.choose();
  assert.equal(f.form.service.value, '13');
  f.member.service = 'FAKE unmatched role';
  f.choose();
  assert.equal(f.form.service.value, '0');
  assertContact(f);
});
