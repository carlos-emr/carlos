#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Print / PDF / download routes against the role matrix (coverage-plan area: authorization,
 * "authz-read" sweep).
 *
 * User path: a receptionist, a nurse and a login with no sec objects sign in through the login form
 * and request the PDFs the chart, the consultation, the lab inbox, HRM, prevention and the
 * prescription pad print or download (Rx > Print, Consultation > Print, lab Print, HRM Print,
 * Preventions > Print, Master Record > Print label).
 *
 * Asserted: every print/PDF route whose object the role lacks answers 403 (pinned, each with the
 * full-privilege login not refused on the same URL as the control, and served the PDF where the route
 * has a seeded record); the label and chart PDFs are served
 * to the roles that hold _demographic. In the LAST step the E-Chart print (OscarChartPrint, "print
 * echart" in the audit log) -- which assembles the master record, allergies, prescriptions,
 * preventions, ticklers, disease registry and notes -- must not hand the prescription and allergy
 * sections to a receptionist or a nurse: it checks only _demographic r, never _eChart, _rx or _allergy.
 *
 * Fixtures: one owned patient with one seeded row per chart domain (each carrying a per-domain
 * string, read back from the PDF text with pdftotext), one synthetic PATHL7 lab routed to that patient
 * (the export-content-lab-print fixture shape), three throwaway logins; all removed and verified. Reads only. OscarChartPrint has no UI caller (it is reachable only by URL).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture, seedPatientDomains, cleanupAll } = require('./lib/authz-read-fixture');
const { probe, classify, forbiddenByApp, refusedByApp, refusedHead, urlFor, signIn, ledger } = require('./lib/authz-read-probe');
const { requirePoppler, directory } = require('./lib/stored-pdf-documents');
const { patientQuery } = require('./lib/authz-read-routes');

const LAB_SERVICE = 'CARLOSAUTHZ';

/** A minimal synthetic PATHL7 ORU^R01 (FAKE names, all-9s HIN) for the owned patient's lab PDF route. */
function labMessage(marker, accession, stamp) {
  return [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|AUTHZ${stamp}|P|2.3|||ER|AL`,
    `PID||9999999999|9999999999||${marker}^FAKE||19800101|F`,
    `ORC|RE||${accession}|||||||||99999^FAKE-ORDERING^DOC`,
    `OBR|1||${accession}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE-ORDERING^DOC||||||${stamp}||CHEM4|F|||99999^FAKE-ORDERING^DOC`,
    `OBX|1|NM|2345-7^Glucose Random||5.2|mmol/L|3.3-7.7|N|||F|||${stamp}`,
  ].join('\r');
}

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  // The document routes need a stored file to serve: the seeded PDF goes into the application's document store (SKIP when it is not configured).
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR', 'EDOC_NAV_DOCUMENT_STORE', 'LAB_UPLOAD_DOCUMENT_STORE');
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  let ids;
  let labNo = null;
  const removeLab = () => {
    if (!labNo) return;
    const lab = Number(labNo);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${lab} AND serviceName=${h.sqlString(LAB_SERVICE)}`) === '1', 'Owned lab identity changed');
    sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7'; DELETE FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${lab}; DELETE FROM hl7TextMessage WHERE lab_id=${lab} AND serviceName=${h.sqlString(LAB_SERVICE)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${lab}) + (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${lab})`) === '0', 'Owned lab rows remain');
  };
  // Independent teardown actions: a login row that will not delete must not leave the seeded rows or file behind.
  s.cleanup(() => cleanupAll(() => fixture.cleanup(), () => { if (ids) ids.remove(); }, removeLab));
  const sessions = {};
  const tag = `${marker}-OPEN`;
  const needles = ['rx', 'allergy', 'appt', 'note', 'tickler', 'doc'].map(domain => `${tag}-${domain}`);
  const get = (who, route) => probe(who === 'full' ? s.context : sessions[who].context, urlFor(config, route), { needles });

  await s.step('an owned patient is seeded with one row per chart domain and three restricted logins sign in', async () => {
    // `register` hands over the ids object before the first write, so cleanup also covers a seed that fails midway.
    seedPatientDomains({ sql, demo: patient, tag, provider, documentStore: store, register: seeded => { ids = seeded; } });
    // An owned lab, so the lab PDF route is always probed and never reads another patient's chart data.
    const accession = `AUTHZ-${marker.slice(-10)}`;
    const now = new Date();
    const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}100000`;
    const base64 = Buffer.from(labMessage(marker, accession, stamp), 'utf8').toString('base64');
    labNo = sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
      VALUES (0, ${h.sqlString(base64)}, 'PATHL7', ${h.sqlString(LAB_SERVICE)}, NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(labNo), 'The fixture lab did not insert');
    sql.execute(`INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority, requesting_client,
        discipline, last_name, first_name, report_status, accessionNum, filler_order_num, sending_facility)
      VALUES (${labNo}, 'F', '9999999999', 'A', 1, NOW(), 'R', 'FAKE-ORDERING, DOC', 'CHEM', ${h.sqlString(marker)}, 'FAKE', 'F', ${h.sqlString(accession)}, ${h.sqlString(accession)}, 'CW');
      INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES (${h.sqlString(provider)}, ${labNo}, 'N', 'HL7');
      INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created) VALUES (${patient}, ${labNo}, 'HL7', NOW())`);
    h.assert(fixture.roleHoldsNothing('er_clerk'), 'er_clerk is expected to hold no sec object');
    const receptionist = fixture.rolePrivileges('receptionist');
    const nurse = fixture.rolePrivileges('nurse');
    h.assert(!receptionist.some(entry => /^_(eChart|rx|allergy|prevention|tickler|con|lab|edoc|hrm):/.test(entry)), 'receptionist now holds a clinical object');
    h.assert(nurse.includes('_eChart:x') && !nurse.some(entry => /^_(rx|allergy|prevention|tickler|con|lab|edoc|hrm):/.test(entry)), 'nurse no longer matches the seeded role matrix');
    for (const role of ['receptionist', 'nurse', 'er_clerk']) sessions[role] = await signIn(s, fixture.addLogin(role));
  });

  const printRoutes = () => [
    `prevention/printPrevention?${patientQuery(patient)}`,
    `rx/ViewPrint?${patientQuery(patient)}`,
    `rx/ViewPrintDrugProfile2?${patientQuery(patient)}`,
    `encounter/oscarConsultationRequest/printPdf2?requestId=${ids.consult}&${patientQuery(patient)}`,
    `documentManager/ManageDocument?method=viewDocPage&doc_no=${ids.doc}&curPage=1`,
    `documentManager/ManageDocument?method=display&doc_no=${ids.doc}`,
    'hospitalReportManager/PrintHRMReport?hrmReportId=1',
    'report/DownloadLetter',
    `lab/CA/ALL/PrintPDF?segmentID=${labNo}&providerNo=${provider}`,
  ];

  await s.step('control: the full-privilege login is served the PDF routes that have data (including the stored document\'s own file and page image), and the label/chart PDFs are served to _demographic holders', async () => {
    const unserved = [];
    for (const route of [`documentManager/ManageDocument?method=viewDocumentInfo&doc_no=${ids.doc}`, `prevention/printPrevention?${patientQuery(patient)}`,
      `encounter/oscarConsultationRequest/printPdf2?requestId=${ids.consult}&${patientQuery(patient)}`, `lab/CA/ALL/PrintPDF?segmentID=${labNo}&providerNo=${provider}`]) {
      if (classify(await get('full', route)) !== 'served') unserved.push(route.split('?')[0]);
    }
    // The binary document routes the restricted logins are refused on below: the same URLs must deliver the file to the full login.
    const file = await get('full', `documentManager/ManageDocument?method=display&doc_no=${ids.doc}`);
    if (!(file.pdf && file.found.includes(`${tag}-doc`))) unserved.push(`document file (display) -> ${file.status}`);
    const image = await get('full', `documentManager/ManageDocument?method=viewDocPage&doc_no=${ids.doc}&curPage=1`);
    if (!(image.status === 200 && /^image\//.test(image.type) && image.length > 0)) unserved.push(`document page image (viewDocPage) -> ${image.status}`);
    // Every URL the restricted logins must be refused on is NOT refused to the full login, so an always-403 route
    // (HRM print, letter download: their gate runs before any record lookup) cannot pass the sweep below.
    for (const route of printRoutes()) {
      const result = await get('full', route);
      if (refusedByApp(result)) unserved.push(`${route.split('?')[0]} refused to the full login -> ${result.status}`);
    }
    for (const who of ['receptionist', 'nurse']) {
      const label = await get(who, `demographic/printDemoLabelAction?${patientQuery(patient)}`);
      if (!label.pdf) unserved.push(`${who} label`);
    }
    h.assert(!unserved.length, `Controls were not served: ${unserved.join(', ')}`);
  });

  await s.step('print and PDF routes outside the role\'s objects answer 403 to receptionist, nurse and er_clerk', async () => {
    const wrong = [];
    for (const who of ['receptionist', 'nurse', 'er_clerk']) {
      for (const route of printRoutes()) {
        const result = await get(who, route);
        // The consult/lab/HRM/document PDFs need _con / _lab / _hrm / _edoc, which none of these roles holds.
        if (!forbiddenByApp(result)) wrong.push(`${who} ${route.split('?')[0]} -> ${result.status}/${classify(result)}`);
      }
    }
    h.assert(!wrong.length, `A print route was not refused: ${wrong.join('; ')}`);
  });

  await s.step('HEAD on the same routes is refused', async () => {
    const wrong = [];
    for (const who of ['receptionist', 'nurse', 'er_clerk']) {
      for (const route of printRoutes()) {
        const result = await probe(sessions[who].context, urlFor(config, route), { method: 'HEAD' });
        if (!refusedHead(result)) wrong.push(`${who} ${route.split('?')[0]} -> ${result.status}/${classify(result)}`);
      }
    }
    h.assert(!wrong.length, `HEAD was served: ${wrong.join('; ')}`);
  });

  await s.step('a login with no sec object is refused the E-Chart print (control: the full login gets the PDF with every domain)', async () => {
    const asFull = await get('full', `OscarChartPrint?demographicNo=${patient}`);
    h.assert(asFull.pdf && asFull.found.includes(`${tag}-rx`) && asFull.found.includes(`${tag}-allergy`), 'The full-privilege login did not receive the chart PDF with prescriptions and allergies');
    const refused = await get('er_clerk', `OscarChartPrint?demographicNo=${patient}`);
    h.assert(forbiddenByApp(refused), `OscarChartPrint answered HTTP ${refused.status} to a login with no sec objects`);
  });

  await s.step('the E-Chart print hands only the sections a role holds the object for (lists every section that leaks)', async () => {
    const open = ledger();
    for (const who of ['receptionist', 'nurse']) {
      const result = await get(who, `OscarChartPrint?demographicNo=${patient}`);
      // Markers are checked whatever the content type (probe() extracts them from HTML/text as well as PDFs); only the whole-chart verdict needs a real PDF.
      if (result.found.includes(`${tag}-rx`)) open.add(`${who}: prescriptions in the chart PDF`, 'holds no _rx');
      if (result.found.includes(`${tag}-allergy`)) open.add(`${who}: allergies in the chart PDF`, 'holds no _allergy');
      if (who === 'receptionist' && result.pdf) open.add('receptionist: the whole chart PDF', 'holds no _eChart');
    }
    open.assertEmpty('OscarChartPrint served chart sections to a role lacking the object');
  });
}

if (require.main === module) runWorkflow('authz-read-print-export', workflow, { openMaster: false, preflight: () => requirePoppler('pdftotext') });
module.exports = { workflow };
