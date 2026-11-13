/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { assertPopupScope } = require('./page-health-master-record-playwright-checks');
const HOST = 'https://fixture.invalid/carlos/demographic/DemographicEdit?demographic_no=42';
const targets = [
  '/billing/CA/ON/ViewBillingONHistory?demographic_no=42',
  '/billing/CA/ON/billingShortcutPg1View?billRegion=ON&appointment_no=0&demographic_no=42&apptProvider_no=none',
  '/billing/CA/BC/reprocessBill?demographicNo=42&lastName=O%27Neil%26Co',
  '/billing?demographic_no=42',
  '/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=42',
  '/rx/choosePatient?demographicNo=42', '/encounter/IncomingEncounter?demographicNo=42',
  '/prevention/ViewPreventionIndex?demographic_no=42', '/tickler/ViewTicklerMain?demoview=42',
  '/form/forwardshortcutname?formname=AR1&demographic_no=42',
  '/form/forwardshortcutname?formname=AR2&demographic_no=42',
  '/mod/docmgmtComp/DocList?demographic_no=42',
  '/documentManager/ViewDocumentReport?functionid=42',
  '/documentManager/ViewDocumentBrowser?functionid=42&categorykey=private',
];
const links = () => targets.map((path, index) => ({ href: '/carlos' + path,
  url: new URL('/carlos' + path, HOST).href, label: `fixture link ${index}` }));
test('every clinical and billing popup retains one patient parameter, including conditional routes', () => {
  assert.doesNotThrow(() => assertPopupScope(links(), HOST));
});
for (const [name, change] of [
  ['inert href', link => { link.href = '#'; }],
  ['script href', link => { link.href = 'javascript:void(0)'; }],
  ['off-host target', link => { link.url = link.url.replace('fixture.invalid', 'outside.invalid'); }],
  ['different patient', link => { link.url = link.url.replace('=42', '=43'); }],
  ['missing patient', link => { link.url = link.url.replace('demographic_no=42', 'other=42'); }],
  ['ambiguous patient', link => { link.url += '&demographic_no=43'; }],
]) {
  test(`popup contract refuses ${name}`, () => {
    const items = links(); change(items[0]);
    assert.throws(() => assertPopupScope(items, HOST));
  });
}
