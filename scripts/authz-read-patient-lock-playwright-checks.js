#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient lock (|o| on _demographic$N / _eChart$N) against READ routes that take a patient or a
 * record id (coverage-plan area: authorization, "authz-read" sweep).
 *
 * User path: a doctor login that an administrator has locked out of one patient (Administration >
 * Assign Role/Rights writes the same secObjPrivilege rows) signs in through the login form and
 * reaches the patient's data through the routes the chart, the consultation attach popup, the
 * tickler list, the billing page, the lab inbox and the document tools call.
 *
 * Asserted: (1) the lock is a real gate -- the same routes serve the unlocked patient to this
 * login and the full-privilege test login serves the locked patient's data (positive controls);
 * (2) routes that honour the lock answer 403 for the locked patient (master record, document
 * report/pages, drug profile, E-Chart print, labels, pharmacy) -- pinned; (3) the LAST step lists
 * every route that serves the locked patient's data anyway, found by seeding every chart domain
 * with a per-domain string and looking for that string in the answer.
 *
 * Fixtures: two owned patients (open and locked), one seeded row per chart domain for each
 * (note, allergy, rx, prevention, measurement, tickler, eform, document, consult, appointment, dx,
 * CPP), one throwaway doctor login with the lock rows keyed to its provider number, and a lock for
 * the same login on the patient of a demo HL7 lab (lock row only; the demo rows are only read).
 * Cleanup removes and verifies every owned row. Reads only.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture, seedPatientDomains, cleanupAll } = require('./lib/authz-read-fixture');
const { patientQuery } = require('./lib/authz-read-routes');
const { probe, classify, forbiddenByApp, refusedHead, urlFor, signIn, ledger } = require('./lib/authz-read-probe');

async function workflow(s) {
  const { sql, marker, provider, config } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  const lockedName = `${marker}-LOCKED`;
  // A first name that occurs nowhere else, so a search-result row can be told from the echoed keyword.
  const lockedFirst = `Lk${marker.slice(-8)}`;
  let locked; let openIds; let lockedIds; let doctor;
  // Independent teardown actions: one failing must not leave the other owned rows behind.
  s.cleanup(() => cleanupAll(
    () => { if (lockedIds) lockedIds.remove(); },
    () => { if (openIds) openIds.remove(); },
    () => fixture.cleanup(),
    () => {
      if (!locked) return;
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${locked} AND last_name=${h.sqlString(lockedName)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${locked}`) === '0', 'The locked patient was not removed');
    },
  ));

  const lab = sql.rows(`SELECT plr.lab_no, plr.demographic_no, hti.accessionNum FROM patientLabRouting plr
    JOIN hl7TextInfo hti ON hti.lab_no=plr.lab_no WHERE plr.lab_type='HL7' AND plr.demographic_no>0
    AND hti.accessionNum IS NOT NULL AND hti.accessionNum<>'' ORDER BY plr.id DESC LIMIT 1`)[0];

  await s.step('a doctor login is locked out of one owned patient (and the patient of a demo lab) and signs in', async () => {
    locked = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
        provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(lockedName)},${h.sqlString(lockedFirst)},'1980','01','02','F','AC',${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(locked), 'The locked patient fixture was not created');
    // `register` hands each ids object to cleanup before its first insert (a seed that fails midway is still removed).
    seedPatientDomains({ sql, demo: s.patient, tag: `${marker}-OPEN`, provider, register: ids => { openIds = ids; } });
    seedPatientDomains({ sql, demo: locked, tag: lockedName, provider, register: ids => { lockedIds = ids; } });
    const login = fixture.addLogin('doctor');
    h.assert(fixture.lockPatient(login, locked).length === 2, 'The patient lock rows were not written');
    if (lab) fixture.lockPatient(login, lab[1]);
    doctor = await signIn(s, login);
  });

  const full = () => s.context; // the unlocked full-privilege test login
  const DOMAINS = ['note', 'allergy', 'rx', 'prev', 'meas', 'tickler', 'eform', 'doc', 'consult', 'appt', 'dx', 'cpp', 'pharmacy'];
  const needles = [lockedName, marker, lockedFirst, 'Workflow', ...DOMAINS.flatMap(d => [`${marker}-OPEN-${d}`, `${lockedName}-${d}`]), ...(lab ? [lab[2], `segmentID=${lab[0]}`] : [])];
  const get = (context, route, extra = []) => probe(context, urlFor(config, route), { needles: [...needles, ...extra] });
  // What proves a response is THIS patient's data (not a generic page, an empty state or the static shell): the
  // seeded domain string for record routes, the patient's name for pages that render it (demoName upper-cases it).
  const tagOf = ids => (ids === lockedIds ? lockedName : `${marker}-OPEN`);
  const nameOf = ids => (ids === lockedIds ? lockedName : marker);
  const PROOF = {
    master: ids => nameOf(ids),
    docReport: ids => `${tagOf(ids)}-doc`,
    docPage: ids => `${tagOf(ids)}-doc`,
    docShow: ids => `${tagOf(ids)}-doc`,
    rxPatient: ids => nameOf(ids),
    rxScript: ids => nameOf(ids),
    chartPrint: ids => `${tagOf(ids)}-rx`,
    label: ids => nameOf(ids),
    demoName: ids => nameOf(ids).toUpperCase(),
    pharmacy: ids => `${tagOf(ids)}-pharmacy`,
  };
  const served = async (context, ids, key) => {
    const result = await get(context, routes(ids)[key], [PROOF[key](ids)]);
    return { result, ok: classify(result) === 'served' && result.found.includes(PROOF[key](ids)) };
  };
  const routes = ids => ({
    master: `demographic/DemographicEdit?demographic_no=${ids.demo}`,
    docReport: `documentManager/ViewDocumentReport?function=demographic&functionid=${ids.demo}`,
    docPage: `documentManager/ManageDocument?method=viewDocumentInfo&doc_no=${ids.doc}`,
    docShow: `documentManager/ViewShowDocument?segmentID=${ids.doc}&providerNo=${provider}`,
    rxPatient: `rx/choosePatient?${patientQuery(ids.demo)}`,
    rxScript: `rx/ViewStaticScript2?${patientQuery(ids.demo)}`,
    chartPrint: `OscarChartPrint?demographicNo=${ids.demo}`,
    label: `demographic/printDemoLabelAction?demographic_no=${ids.demo}`,
    demoName: `documentManager/ManageDocument?method=getDemoNameAjax&demo_no=${ids.demo}`,
    pharmacy: `rx/managePharmacy?method=getPharmacyFromDemographic&demographicNo=${ids.demo}`,
  });
  const pinned = ['master', 'docReport', 'docPage', 'docShow', 'rxPatient', 'rxScript', 'chartPrint', 'label', 'demoName', 'pharmacy'];

  await s.step('controls: the full-privilege login is served the locked patient and this doctor is served the open patient', async () => {
    const wrong = [];
    for (const key of pinned) {
      // A control counts only when the answer carries this route's fixture marker, so a generic page or an empty state cannot pass.
      const asFull = await served(full(), lockedIds, key);
      if (!asFull.ok) wrong.push(`full login ${key} -> ${asFull.result.status}/${classify(asFull.result)} (no fixture marker)`);
      const asDoctorOpen = await served(doctor.context, openIds, key);
      if (!asDoctorOpen.ok) wrong.push(`doctor/open ${key} -> ${asDoctorOpen.result.status}/${classify(asDoctorOpen.result)} (no fixture marker)`);
    }
    h.assert(!wrong.length, `The lock fixture is not discriminating: ${wrong.join('; ')}`);
  });

  await s.step('routes that honour the lock answer 403 for the locked patient (master record, documents, prescribing, chart print, labels)', async () => {
    const wrong = [];
    for (const key of pinned) {
      const result = await get(doctor.context, routes(lockedIds)[key]);
      if (!forbiddenByApp(result)) wrong.push(`${key} -> ${result.status}/${classify(result)}`);
    }
    h.assert(!wrong.length, `A locked patient was not refused on: ${wrong.join('; ')}`);
  });

  await s.step('HEAD on the same routes is refused too', async () => {
    const wrong = [];
    for (const key of pinned) {
      const result = await probe(doctor.context, urlFor(config, routes(lockedIds)[key]), { method: 'HEAD' });
      if (!refusedHead(result)) wrong.push(`${key} -> ${result.status}/${classify(result)}`);
    }
    h.assert(!wrong.length, `HEAD served a locked patient on: ${wrong.join('; ')}`);
  });

  // Routes the unlocked patient is served on, each with the string proving WHICH data came back.
  const leaks = [
    ['previewDocs consult attach list', (ids, demoNo) => `previewDocs?method=fetchConsultDocuments&${patientQuery(demoNo)}&requestId=${ids.consult}`, 'doc'],
    ['previewDocs eForm attach list', (ids, demoNo) => `previewDocs?method=fetchEFormDocuments&${patientQuery(demoNo)}`, 'doc'],
    ['tickler list (JSON)', (ids, demoNo) => `tickler/ListTicklers?${patientQuery(demoNo)}`, 'tickler'],
    ['add tickler form (patient name)', (ids, demoNo) => `tickler/ViewAddTickler?${patientQuery(demoNo)}`, 'name'],
    ['billing patient lookup (JSON demographic record)', (ids, demoNo) => `BillingONReview?${patientQuery(demoNo)}`, 'name'],
    ['enrollment history (patient name)', (ids, demoNo) => `demographic/ViewEnrollmentHistory?${patientQuery(demoNo)}`, 'name'],
    ['label print settings (patient name)', (ids, demoNo) => `demographic/ViewDemographicLabelPrintSetting?${patientQuery(demoNo)}`, 'name'],
    ['patient search results (name, DOB, address, HIN, chart no.)', (ids, demoNo) => `demographic/DemographicSearch?search_mode=search_name&keyword=${encodeURIComponent(demoNo === openIds.demo ? marker : lockedName)}&ptstatus=active&displaymode=Search&orderby=last_name&limit1=0&limit2=20&dboperation=search_titlename`, 'search'],
    ['note revision history by note id', (ids, demoNo) => `CaseManagementEntry?method=history&noteId=${ids.note}&${patientQuery(demoNo)}`, 'note'],
    ['document description search (all patients)', () => `documentManager/ManageDocument?method=searchDocumentDescriptions&term=${encodeURIComponent(marker)}`, 'doc'],
  ];

  await s.step('controls for the lock-bypass candidates: each one serves the OPEN patient\'s data to this doctor', async () => {
    const wrong = [];
    for (const [name, build, kind] of leaks) {
      const result = await get(doctor.context, build(openIds, openIds.demo));
      const needle = kind === 'name' ? marker : kind === 'search' ? 'Workflow' : `${marker}-OPEN-${kind}`;
      if (!(result.status === 200 && result.found.includes(needle))) wrong.push(`${name} -> ${result.status}`);
    }
    h.assert(!wrong.length, `Candidate routes did not serve the open patient: ${wrong.join('; ')}`);
  });

  // Each lab URL with the string that only that lab's answer contains (the accession number; the list links its segment).
  const labProofs = () => [
    [`lab/CA/ALL/ViewLabDisplay?segmentID=${lab[0]}&providerNo=${provider}`, lab[2]],
    [`lab/CA/ALL/PrintPDF?segmentID=${lab[0]}&providerNo=${provider}`, lab[2]],
    [`lab/ViewDemographicLab?demographicNo=${lab[1]}`, `segmentID=${lab[0]}`],
  ];

  if (lab) {
    await s.step('a lab for the locked demo patient is refused (controls: the full login is served it)', async () => {
      const unserved = [];
      for (const [url, proof] of labProofs()) {
        const asFull = await get(full(), url, [proof]);
        if (classify(asFull) !== 'served' || !asFull.found.includes(proof)) unserved.push(url.split('?')[0]);
      }
      h.assert(!unserved.length, `The lab controls were not served to the full login: ${unserved.join(', ')}`);
    });
  }

  // Last step: every route that still serves the locked patient. Asserted together.
  await s.step('no read route serves the locked patient\'s data to the locked doctor (lists every route that does)', async () => {
    const open = ledger();
    for (const [name, build, kind] of leaks) {
      const result = await get(doctor.context, build(lockedIds, lockedIds.demo));
      if (result.status === 200 && result.found.includes(kind === 'name' ? lockedName : kind === 'search' ? lockedFirst : `${lockedName}-${kind}`)) open.add(name, `HTTP ${result.status}`);
    }
    // The note id belongs to the locked patient; naming an unlocked patient in the same request must not help.
    const viaOpen = await get(doctor.context, `CaseManagementEntry?method=history&noteId=${lockedIds.note}&demographicNo=${openIds.demo}`);
    if (viaOpen.status === 200 && viaOpen.found.includes(`${lockedName}-note`)) open.add('note revision history by note id naming an unlocked patient', 'the note id alone decides');
    if (lab) {
      const names = ['lab report display', 'lab report PDF', 'patient lab list'];
      for (const [index, [url, proof]] of labProofs().entries()) {
        const result = await get(doctor.context, url, [proof]);
        if (result.status === 200 && result.found.includes(proof)) open.add(names[index], `demo lab, HTTP ${result.status}`);
      }
    }
    open.assertEmpty('Routes served a locked patient\'s data to the locked login');
  });
}

if (require.main === module) runWorkflow('authz-read-patient-lock', workflow, { openMaster: false });
module.exports = { workflow };
