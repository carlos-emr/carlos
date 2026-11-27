/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {parseFragment} = require('parse5');
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
  // The JSP emits each value through ${ carlos:forJavaScript(...) } inside a double-quoted string
  // (#6202). JSON string escaping plus OWASP's "\\/" (so "</script>" cannot close the block)
  // stands in for the server-side encoder here.
  const js = value => JSON.stringify(String(value)).slice(1, -1).replace(/\//g, '\\/');
  registry = registry.replace(/<c:forEach\b[^>]*>([\s\S]*?)<\/c:forEach>/g, (_, body) =>
    services.map(service => body
      .replace(/\$\{\s*carlos:forJavaScript\(\s*consultationService\.serviceId\s*\)\s*\}/g, () => js(service.id))
      .replace(/\$\{\s*carlos:forJavaScript\(\s*consultationService\.serviceDesc\s*\)\s*\}/g,
        () => js(service.description))).join('\n'));
  assert.ok(!/\$\{\s*consultationService\./.test(registry), 'A service value is emitted without carlos:forJavaScript');
  function scriptBodies(node) {
    const children = node.childNodes || [];
    if (node.tagName === 'script') return [children.map(child => child.value || '').join('')];
    return children.flatMap(scriptBodies);
  }
  const scripts = scriptBodies(parseFragment(registry));
  const form = Object.fromEntries(['annotation', 'phone', 'fax', 'address', 'service'].map(name =>
    [name, {value: name === 'service' ? '0' : 'FAKE previous value', focus() {}}]));
  const options = [{value: '41', disabled: false}];
  options.selectedIndex = 0;
  form.specialist = {options, focus() {}, get value() {
    return this.options[this.options.selectedIndex]?.value ?? '';
  }};
  const member = {note: 'FAKE referral annotation', phoneNum: '555-0100', specFax: '555-0101',
    specAddress: 'FAKE synthetic address', service: role};
  const alerts = [];
  const context = vm.createContext({document: {EctConsultationFormRequest2Form: form,
    getElementById(id) { return id === 'specialist' ? form.specialist : null; }},
    healthCareTeam: {41: member}, specialistFaxNumber: '', faxUpdates: 0,
    updateFaxButton() { context.faxUpdates++; }, alert(message) { alerts.push(message); },
    ShowSpin() {}, HideSpin() {}});
  vm.runInContext(scripts.join('\n'), context);
  for (const name of ['getSpecialist', 'checkFormHCT', 'checkForm']) {
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
    vm.runInContext(source.slice(functionStart, cursor)
      .replace('<fmt:message key="Errors.service.noServiceSelected"/>', 'Please select a service'), context);
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

test('a service description with quotes, backticks or ${ is data, not script (#6202)', () => {
  const description = 'FAKE "Cardio" `${globalThis.pwned = 1}` \\ </script>';
  const f = fixture([{id: '13', description}], description);
  f.choose();
  assert.equal(f.context.pwned, undefined);
  assert.equal(f.context.consultationServices[0].description, description);
  assert.equal(f.form.service.value, '13');
  assertContact(f);
});

test('health care team values are JavaScript-encoded strings, never template literals (#6202)', () => {
  const start = source.indexOf('// Read the associated services and specialties');
  const end = source.indexOf('function getSpecialist(', start);
  assert.ok(start > 0 && end > start, 'Missing JSP health care team registry');
  const registry = source.slice(start, end);
  const assignments = registry.match(/^\s*(?:contact\.\w+|healthCareTeam\[.*?\])\s*=\s*(?!contact;|\{\}).*$/gm) || [];
  assert.ok(assignments.length >= 12, 'Expected the per-contact field assignments');
  for (const line of assignments) {
    assert.ok(!line.includes('`'), 'Template literal in: ' + line.trim());
    assert.match(line, /"\$\{ carlos:forJavaScript\([^)]*\) \}"/, 'Unencoded value in: ' + line.trim());
  }
});

test('the empty-team placeholder or no selection is refused before submit (#6202)', () => {
  const f = fixture([], 'FAKE unmatched role');
  f.choose();
  const placeholder = [{value: '', disabled: true}];
  placeholder.selectedIndex = 0;
  f.form.specialist.options = placeholder;
  assert.equal(f.context.checkForm('Submit Consultation Request', 'EctConsultationFormRequest2Form'), false);
  const none = [{value: '41', disabled: false}];
  none.selectedIndex = -1;
  f.form.specialist.options = none;
  assert.equal(f.context.checkForm('Update Consultation Request', 'EctConsultationFormRequest2Form'), false);
  assert.deepEqual(f.alerts, Array(2).fill(
    'Please select a Consultant. Or add a Consultant using edit Health Care Team.'));
});
