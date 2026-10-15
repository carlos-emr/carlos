#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration pages whose route checks a WEAKER privilege than the object that gates their menu
 * entry (coverage-plan area: authorization, "authz-read" sweep).
 *
 * User path: Schedule > Administration (admin.jsp / leftNav.jspf) lists each item inside a
 * <security:oscarSec objectName=...> block -- Field Note Report (_admin.fieldnote), Customize
 * Measurements (_admin / _admin.measurements), Billing > Payment Received (_admin.invoices / _admin /
 * _admin.billing). A doctor, a nurse and the one seeded role built for Field Notes sign in through
 * the login form and request those pages. (The E-Form Manager is deliberately NOT a defect: its page
 * splits edit/export for _eform w from delete for _admin.eform w.)
 *
 * Asserted: the Setup* routes of the measurement configuration, the eForm generator / visual editor
 * and the Administration panel refuse these logins with the application's own 403 (admin/ViewAdmin is exempt
 * for the doctor while its role holds _admin.flowsheet, which that gate accepts; a 403 counts only
 * when it carries the application's response header, so a WAF block does not; pinned, with the
 * full-privilege login serving the same URLs as the control; the generator/editor's HTTP 500 is
 * listed by the last step); and, in the LAST step, every page below must be refused to a login
 * lacking the menu object and served to the role that holds it:
 *   - eform/fieldNoteReport/* check only _eform r: any eForm reader (doctor) opens the Field Note
 *     Report, its Word download and the field-note eForm picker, while the "Field Note Admin" role
 *     (holds _admin.fieldnote only) is refused;
 *   - encounter/oscarMeasurements/View* configuration pages sit behind the chart gate (_eChart r),
 *     so a nurse, who holds neither _admin nor _admin.measurements, opens them although their
 *     Setup* siblings answer 403;
 *   - billing/CA/ON/BillingONPayment (Payment Received report) checks _tasks r.
 *
 * Fixtures: three throwaway logins (own provider rows); nothing else. Reads only; removed and
 * verified by authzReadFixture.cleanup().
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { probe, classify, refusedByApp, refusedHead, urlFor, signIn, ledger } = require('./lib/authz-read-probe');

const FIELD_NOTE = ['eform/fieldNoteReport/fieldnotereport', 'eform/fieldNoteReport/fieldnotereportdetail?residentId=0&residentName=x',
  'eform/fieldNoteReport/fieldnoteselect'];
const MEASUREMENT_DOORS = ['encounter/oscarMeasurements/ViewViewMeasurementMap', 'encounter/oscarMeasurements/ViewCustomization',
  'encounter/oscarMeasurements/ViewDisplayMeasurementTypes', 'encounter/oscarMeasurements/ViewDisplayMeasurementStyleSheet',
  'encounter/oscarMeasurements/ViewEditMeasurementGroup', 'encounter/oscarMeasurements/ViewSelectMeasurementGroup',
  'encounter/oscarMeasurements/ViewNewMeasurementMap', 'encounter/oscarMeasurements/ViewRemapMeasurementMap'];
const MEASUREMENT_SETUP = ['encounter/oscarMeasurements/SetupDisplayMeasurementTypes',
  'encounter/oscarMeasurements/SetupDisplayMeasurementStyleSheet', 'encounter/oscarMeasurements/SetupGroupList'];
const BILLING_PAYMENT = 'billing/CA/ON/BillingONPayment?startDateText=2000-01-01&endDateText=2030-12-31';
// Refused for these logins by design. The eForm generator / editor answer with the generic HTTP 500 error page rather
// than a 403: tolerated by the pinning steps (nothing is served) and listed as an open gate by the last step.
const ADMIN_ONLY = ['eform/eformGenerator', 'eform/visualEformEditor', 'admin/ViewAdmin', 'administration'];

async function workflow(s) {
  const { sql, marker, provider, config } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const sessions = {};
  // Gates that answer HTTP 5xx (the generic error page) instead of a refusal: nothing was served, so the
  // pinning steps let them through and the LAST step lists them (a deliberate refusal is a 403).
  const errorGates = [];
  // Login/route pairs the role matrix authorizes, so they are not pinned as refusals: the seeded doctor role holds
  // _admin.flowsheet r, which ViewAdmin2Action accepts (the Administration index gate does not list it).
  const authorized = new Set();
  const get = (who, route) => probe(who === 'full' ? s.context : sessions[who].context, urlFor(config, route));

  await s.step('doctor, nurse and Field Note Admin logins hold the objects this check assumes and sign in', async () => {
    const doctor = fixture.rolePrivileges('doctor');
    const nurse = fixture.rolePrivileges('nurse');
    if (doctor.some(entry => /^_admin\.flowsheet:/.test(entry))) authorized.add('doctor admin/ViewAdmin');
    h.assert(!doctor.some(entry => /^_admin\.(eform|fieldnote):/.test(entry)), 'doctor now holds _admin.eform or _admin.fieldnote');
    h.assert(doctor.includes('_eform:w'), 'doctor no longer holds _eform w');
    h.assert(!nurse.some(entry => /^_admin(\.measurements)?:/.test(entry)) && nurse.includes('_eChart:x'),
      'nurse no longer matches the seeded role matrix');
    h.assert(fixture.rolePrivileges('Field Note Admin').join() === '_admin.fieldnote:x', 'Field Note Admin changed');
    for (const role of ['doctor', 'nurse', 'Field Note Admin']) sessions[role] = await signIn(s, fixture.addLogin(role));
  });

  await s.step('controls: the full-privilege login is served every page this check requests', async () => {
    const unserved = [];
    for (const route of [...FIELD_NOTE, ...MEASUREMENT_DOORS, ...MEASUREMENT_SETUP, BILLING_PAYMENT, ...ADMIN_ONLY]) {
      if (classify(await get('full', route)) !== 'served') unserved.push(route);
    }
    h.assert(!unserved.length, `The control login was not served: ${unserved.join(', ')}`);
  });

  await s.step('the Setup* measurement routes, the eForm generator/editor and the Administration panel refuse doctor and nurse', async () => {
    const wrong = [];
    for (const who of ['doctor', 'nurse']) {
      for (const route of [...MEASUREMENT_SETUP, ...ADMIN_ONLY]) {
        if (authorized.has(`${who} ${route}`)) continue;
        const result = await get(who, route);
        // An explicit application refusal (403 / securityError), not merely "not served": a 404 or login bounce would pass otherwise.
        if (result.status >= 500 && /^Error Page/i.test(result.lead || '')) errorGates.push(`${who} ${route} -> ${result.status}`);
        else if (!refusedByApp(result)) wrong.push(`${who} ${route} -> ${result.status}`);
      }
    }
    h.assert(!wrong.length, `Not refused for a login lacking the object: ${wrong.join('; ')}`);
  });

  await s.step('HEAD on the pinned routes is refused too', async () => {
    const wrong = [];
    for (const who of ['doctor', 'nurse']) {
      for (const route of [...MEASUREMENT_SETUP, ...ADMIN_ONLY]) {
        if (authorized.has(`${who} ${route}`)) continue;
        const result = await probe(sessions[who].context, urlFor(config, route), { method: 'HEAD' });
        // classify() cannot judge HEAD (empty body reads as 'empty'), so check the refusal itself; a GET-only route may answer 405.
        if (result.status >= 500 && errorGates.some(gate => gate.startsWith(`${who} ${route} ->`))) continue; // the last step lists this route's GET error page
        if (!refusedHead(result)) wrong.push(`${who} ${route} -> ${result.status}`);
      }
    }
    h.assert(!wrong.length, `HEAD was not refused: ${wrong.join('; ')}`);
  });

  await s.step('the Field Note Admin role is refused everywhere it is not meant to reach (Administration panel, patient data)', async () => {
    const wrong = [];
    for (const route of ['admin/ViewAdmin', 'administration', 'tickler/ViewTicklerMain', 'demographic/DemographicSearch', 'hospitalReportManager/ViewDocList']) {
      const result = await get('Field Note Admin', route);
      if (!refusedByApp(result)) wrong.push(`${route} -> ${result.status}`);
    }
    h.assert(!wrong.length, `Field Note Admin was not refused: ${wrong.join(', ')}`);
  });

  await s.step('administration pages are refused without their menu object and served with it (lists every route that is not)', async () => {
    const open = ledger();
    for (const route of FIELD_NOTE) {
      if (classify(await get('doctor', route)) === 'served') open.add(`doctor: ${route.split('?')[0]}`, 'Field Note Report needs _admin.fieldnote');
    }
    const intended = await get('Field Note Admin', FIELD_NOTE[0]);
    if (classify(intended) !== 'served') open.add('Field Note Admin: eform/fieldNoteReport/fieldnotereport', `the role holding _admin.fieldnote gets HTTP ${intended.status}`);
    for (const route of MEASUREMENT_DOORS) {
      if (classify(await get('nurse', route)) === 'served') open.add(`nurse: ${route.split('/').pop()}`, 'needs _admin / _admin.measurements');
    }
    if (classify(await get('nurse', BILLING_PAYMENT)) === 'served') open.add('nurse: billing/CA/ON/BillingONPayment', 'holds no billing object');
    for (const gate of errorGates) open.add(gate, 'the gate ends in the generic error page, not a deliberate 403 refusal');
    open.assertEmpty('Administration pages were served without the object that gates their menu entry');
  });
}

if (require.main === module) runWorkflow('authz-read-admin-objects', workflow, { openPatient: false, openMaster: false });
module.exports = { workflow };
