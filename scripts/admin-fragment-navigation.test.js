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
  const start = source.lastIndexOf(openingTag, markerAt);
  const end = source.indexOf('</script>', markerAt);
  assert.ok(markerAt >= 0 && start >= 0 && end > markerAt,
    'The billing simulation initialization script was not found');
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


test('the encounter header offers exactly one link to the clinical calculators', () => {
  // phc007 reported two calculator links in the chart header: this page carried a second
  // anchor to the same route, labelled identically, differing only in passing sex/age in the
  // query string. One entry point, and it is the demo= form (see the URL assertions below).
  const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp'), 'utf8');
  const withoutComments = source.replace(/<%--[\s\S]*?--%>/g, '');
  const calculatorAnchors = withoutComments.match(/<a\b[^>]*\/encounter\/ViewCalculators/g) || [];
  assert.equal(calculatorAnchors.length, 1,
    `Expected one calculators anchor in the encounter header, found ${calculatorAnchors.length}`);
});

test('calculator header navigation keeps patient attributes out of both popup and fallback URLs', () => {
  const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp'), 'utf8');
  const anchor = source.match(/<a href="([^"]*\/encounter\/ViewCalculators[^"]*)"[\s\S]*?onclick="([^"]*)"/);
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
