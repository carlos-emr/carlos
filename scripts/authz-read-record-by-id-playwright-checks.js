#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Records fetched BY ID (lab, HRM report, document, consultation, tickler, patient lists) against
 * the role matrix (coverage-plan area: authorization, "authz-read" sweep).
 *
 * User path: a receptionist, a nurse and a login with no sec objects sign in through the login
 * form. The URLs are the ones the Lab inbox (lab display / print), HRM list, Document Report
 * (page images, info), Consultation list, Tickler list/edit, prevention and prescribing pages and
 * the chart's Note Browser request for a record id or a patient id.
 *
 * Asserted: for each record the role lacks the object for (_lab, _hrm, _edoc, _con, _tickler,
 * _prevention, _rx) the route answers 403 to GET and HEAD for all three logins (pinned; the
 * full-privilege login is the control and receives the record: lab accession number, document
 * description, document description, previewDocs list and tickler text are read back as proof). The LAST step lists
 * the routes that serve another domain's data anyway: the Note Browser, reached from the E-Chart
 * (_eChart), lists the patient's DOCUMENTS to a nurse who holds no _edoc.
 *
 * Fixtures: one owned patient with one seeded row per chart domain, the newest routed demo lab and
 * HRM report (read only), three throwaway logins; all owned rows removed and verified.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture, seedPatientDomains, cleanupAll } = require('./lib/authz-read-fixture');
const { probe, classify, refusedByApp, refusedHead, urlFor, signIn, ledger } = require('./lib/authz-read-probe');
const { patientQuery } = require('./lib/authz-read-routes');

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  let ids;
  // The two teardown actions are independent: a login row that will not delete must not leave the seeded chart rows behind.
  s.cleanup(() => cleanupAll(() => fixture.cleanup(), () => { if (ids) ids.remove(); }));
  const tag = `${marker}-OPEN`;
  const lab = sql.rows(`SELECT plr.lab_no, plr.demographic_no, hti.accessionNum FROM patientLabRouting plr
    JOIN hl7TextInfo hti ON hti.lab_no=plr.lab_no WHERE plr.lab_type='HL7' AND plr.demographic_no>0
    AND hti.accessionNum IS NOT NULL AND hti.accessionNum<>'' ORDER BY plr.id DESC LIMIT 1`)[0];
  const hrm = sql.rows(`SELECT d.hrmDocumentId, d.demographicNo FROM HRMDocumentToDemographic d ORDER BY d.id DESC LIMIT 1`)[0];
  const sessions = {};
  const needles = [`${tag}-doc`, `${tag}-consult`, `${tag}-tickler`, ...(lab ? [lab[2]] : [])];
  const get = (who, route, method = 'GET') => probe(who === 'full' ? s.context : sessions[who].context, urlFor(config, route), { needles, method });

  const routes = () => [
    ...(lab ? [[`lab/CA/ALL/ViewLabDisplay?segmentID=${lab[0]}&providerNo=${provider}`, lab[2]],
      [`lab/ViewDemographicLab?demographicNo=${lab[1]}`, null], [`lab/CA/ALL/ViewLabDisplayAjax?segmentID=${lab[0]}&providerNo=${provider}`, lab[2]]] : []),
    ...(hrm ? [[`hospitalReportManager/ViewDocList?demographic_no=${hrm[1]}`, null], [`hospitalReportManager/Display?id=${hrm[0]}`, null]] : []),
    [`documentManager/ManageDocument?method=viewDocumentInfo&doc_no=${ids.doc}`, `${tag}-doc`],
    [`documentManager/ViewShowDocument?segmentID=${ids.doc}&providerNo=${provider}`, `${tag}-doc`],
    [`documentManager/ViewDocumentReport?function=demographic&functionid=${patient}`, `${tag}-doc`],
    [`encounter/ViewConsultation?${patientQuery(patient)}`, null],
    [`encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${patient}`, null],
    [`encounter/oscarConsultationRequest/printPdf2?requestId=${ids.consult}&${patientQuery(patient)}`, null],
    [`tickler/ViewTicklerEdit?tickler_no=${ids.tickler}`, `${tag}-tickler`],
    [`tickler/ListTicklers?${patientQuery(patient)}`, `${tag}-tickler`],
    [`prevention/ViewPreventionIndex?${patientQuery(patient)}`, null],
    [`previewDocs?method=fetchConsultDocuments&${patientQuery(patient)}&requestId=${ids.consult}`, `${tag}-doc`],
  ];

  await s.step('an owned patient is seeded with one row per chart domain and three restricted logins sign in', async () => {
    // `register` hands over the ids object before the first insert, so cleanup also covers a seed that fails midway.
    seedPatientDomains({ sql, demo: patient, tag, provider, register: seeded => { ids = seeded; } });
    h.assert(fixture.roleHoldsNothing('er_clerk'), 'er_clerk is expected to hold no sec object');
    const heldBy = role => fixture.rolePrivileges(role).map(entry => entry.split(':')[0]);
    for (const role of ['receptionist', 'nurse']) {
      h.assert(!heldBy(role).some(object => ['_lab', '_hrm', '_edoc', '_con', '_tickler', '_prevention', '_rx'].includes(object)),
        `${role} now holds one of the objects this check relies on it lacking`);
    }
    for (const role of ['receptionist', 'nurse', 'er_clerk']) sessions[role] = await signIn(s, fixture.addLogin(role));
  });

  await s.step('controls: the full-privilege login is served each record, and the proof strings are in the answer', async () => {
    const wrong = [];
    for (const [route, proof] of routes()) {
      const result = await get('full', route);
      if (classify(result) !== 'served') wrong.push(`${route.split('?')[0]} -> ${result.status}`);
      else if (proof && !result.found.includes(proof)) wrong.push(`${route.split('?')[0]} lacks its proof string`);
    }
    h.assert(!wrong.length, `The record controls failed: ${wrong.join('; ')}`);
  });

  await s.step('every by-id record route is refused for receptionist, nurse and er_clerk (403, or the /securityError redirect of a view)', async () => {
    const wrong = [];
    for (const who of ['receptionist', 'nurse', 'er_clerk']) {
      for (const [route] of routes()) {
        const result = await get(who, route);
        if (!refusedByApp(result) || result.found.length) wrong.push(`${who} ${route.split('?')[0]} -> ${result.status}`);
      }
    }
    h.assert(!wrong.length, `A record route was not refused: ${wrong.join('; ')}`);
  });

  await s.step('HEAD on the same routes is refused', async () => {
    const wrong = [];
    for (const who of ['receptionist', 'nurse', 'er_clerk']) {
      for (const [route] of routes()) {
        const result = await get(who, route, 'HEAD');
        if (!refusedHead(result)) wrong.push(`${who} ${route.split('?')[0]} -> ${result.status}`);
      }
    }
    h.assert(!wrong.length, `HEAD was served: ${wrong.join('; ')}`);
  });

  await s.step('the chart\'s Note Browser lists documents only to a role holding _edoc (lists every route that serves another domain)', async () => {
    const open = ledger();
    const route = `casemgmt/ViewNoteBrowser?${patientQuery(patient)}`;
    const control = await get('full', route);
    h.assert(control.status === 200 && control.found.includes(`${tag}-doc`), 'The Note Browser control did not list the owned document');
    const asNurse = await get('nurse', route);
    if (asNurse.status === 200 && asNurse.found.includes(`${tag}-doc`)) open.add('nurse: casemgmt/ViewNoteBrowser lists document descriptions', 'holds no _edoc');
    open.assertEmpty('The Note Browser served documents to a role lacking _edoc');
  });
}

if (require.main === module) runWorkflow('authz-read-record-by-id', workflow, { openMaster: false });
module.exports = { workflow };
