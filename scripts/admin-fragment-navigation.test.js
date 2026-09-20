/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('loading OHIP simulation initializes its own form without hijacking shell navigation', () => {
  const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingOHIPsimulation.jsp'), 'utf8');
  const marker = "registerFormSubmit('serviceform'";
  const markerAt = source.indexOf(marker);
  const openingTag = '<script type="text/javascript">';
  // These offsets only extract a script from the checked-in JSP for this VM test; no HTML is emitted.
  // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag
  const start = source.lastIndexOf(openingTag, markerAt);
  const end = source.indexOf('</script>', markerAt);
  assert.ok(markerAt >= 0 && start >= 0 && end > markerAt,
    'The billing simulation initialization script was not found');
  // Slice the same trusted source; the numeric offsets are not browser input.
  // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag
  const script = source.slice(start + openingTag.length, end);
  const registered = [], dates = [], listeners = [];
  vm.runInNewContext(script, {
    registerFormSubmit: (...args) => registered.push(args),
    flatpickr: selector => dates.push(selector),
    document: { querySelectorAll: () => [{addEventListener: (...args) => listeners.push(args)}] },
  });
  assert.deepEqual(registered, [['serviceform', 'dynamic-content']]);
  assert.deepEqual(dates, ['#xml_vdate', '#xml_appointment_date']);
  assert.equal(listeners.length, 0, 'A billing fragment attached an extra shell navigation handler');
});


test('calculator header navigation keeps patient attributes out of both popup and fallback URLs', () => {
  const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp'), 'utf8');
  const anchor = source.match(/<a href="([^"]*\/encounter\/ViewCalculators[^"]*)"\s+onclick="([^"]*)"/);
  assert.ok(anchor, 'The calculator header link was not found');
  const fixture = {ctx: '/carlos', popupDemographicNo: '12345', popupPatientSex: 'F', popupPatientAge: '55'};
  const render = value => value.replace(/\$\{carlos:for\w+\((\w+)\)\}/g, (_, name) => {
    assert.ok(Object.hasOwn(fixture, name), `Unexpected calculator interpolation: ${name}`);
    return fixture[name];
  }).replaceAll('&amp;', '&');
  const popups = [];
  const prevented = vm.runInNewContext('(function () {' + render(anchor[2]) + '})()', {
    window: {open: (...args) => popups.push(args)},
  });
  assert.equal(prevented, false, 'Opening the popup must prevent a second navigation');
  assert.equal(popups.length, 1);
  assert.equal(popups[0][1], 'ClinicalCalculators');
  for (const target of [render(anchor[1]), popups[0][0]]) {
    const url = new URL(target, 'https://example.test');
    assert.equal(url.pathname, '/carlos/encounter/ViewCalculators');
    assert.equal(url.searchParams.has('sex'), false, 'Patient sex must not be serialized into a navigation URL');
    assert.equal(url.searchParams.has('age'), false, 'Patient age must not be serialized into a navigation URL');
    assert.equal(url.searchParams.get('demo'), fixture.popupDemographicNo,
      'The menu must resolve demographics for the originating chart, not a later shared-session patient');
  }
});


test('encounter copy controls use native non-submitting buttons for keyboard activation', () => {
  const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp'), 'utf8');
  for (const id of ['patient-hin', 'patient-phone', 'patient-cell-phone', 'patient-email']) {
    const tag = source.match(new RegExp('<button[^>]*id="' + id + '"[^>]*>'));
    assert.ok(tag, `Missing native copy button: ${id}`);
    assert.match(tag[0], /type="button"/, 'Copy must never submit the surrounding form');
    assert.match(tag[0], /onclick="copyToClip\(/, 'Native keyboard activation must invoke the copy handler');
    assert.doesNotMatch(tag[0], /onkey(?:down|up)=/, 'Use native Enter/Space behavior without duplicate activation');
  }
});
