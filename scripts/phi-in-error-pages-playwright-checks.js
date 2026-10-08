#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient data in error responses (coverage-plan area: PHI in logs and error bodies).
 *
 * error-sanitization provokes two 500s. Nothing provoked the other statuses a clinic's users and
 * scanners see all day -- 400, 403, 404 and 405 -- once per route family, with a request that
 * CARRIES a patient. This does: for each of the ten route families (demographic, chart, Rx, lab,
 * document, tickler, billing, eForm, messenger, administration) and the REST surface, it sends
 * requests that put an owned FAKE patient's health number, name and demographic_no into the query
 * (and, for the REST resources, the path), the form body and the Referer, and reads the answer.
 *
 *   400  the family's own validation refusing a value (often the HIN or the name, sent as the bad value,
 *        which is where an exception message that echoes its input would show);
 *   403  a login with no sec objects, a doctor locked out of this patient (|o| on _demographic$N and
 *        _eChart$N), or, where the family's Struts package answers a refusal with a 500 (finding 199:
 *        eForm and messenger; billing has a view that gates with the securityError page), a POST without
 *        a CSRF token;
 *   404  an action name the family does not map, and a missing resource;
 *   405  a GET to a mutator route, which HttpMethodGuardFilter refuses before the action runs.
 *
 * Asserted, in order: the fixtures and the leak detector work (the full login's Master Record and REST
 * answers DO carry the patient's data); every family was answered 400, 403, 404 and 405 by the
 * APPLICATION -- the front door's page (nginx, ModSecurity) is not a CARLOS body, so it is classified,
 * listed and never counted as reached; the probes wrote nothing for the patient; no response body of
 * any probe contains the HIN (plain, or as NNNN-NNN-NNN or NNNN NNN NNN), the patient's FAKE- names,
 * address or e-mail, or the demographic_no as a number of its own; and, on a Debian install, the server
 * log holds none of them either (below).
 *
 * Reporting. A failure names the family, the status, the route (numbers masked) and the KIND of data
 * found (hin, name, demographic_no); never the body and never the value. PHI_ERROR_PAGES_SHOW_CONTEXT=1 adds the
 * 40 characters around a hit with the fixture values replaced by placeholders, for a developer
 * diagnosing a hit on a disposable install.
 *
 * The server log. On a Debian install (CARLOS_LOG_JOURNAL_UNIT names the unit, as suite-env.sh sets it) the
 * check finishes by running scripts/deb-server-log-phi-scan.sh over its own window, looking for exactly this
 * run's HIN, names, address and e-mail at every log level, and fails if any of it was logged. A request carrying
 * a patient can be answered with a clean page and still be written to the journal in full (finding 204). Without
 * that variable there is no journal to read: the body steps still run, and if they all pass the check ends as a
 * SKIP that says the log was not judged, never as a pass about a log it could not see.
 *
 * Pair: the same scan also runs after a whole suite. Hand it a HIN by exporting PHI_FIXTURE_HIN (10 digits) to this
 * check and passing the same value as the scan's --hin, or let the check draw its own.
 *
 * Fixtures: the workflow's owned synthetic patient (updated with the HIN, first name, address and
 * e-mail), one er_clerk login (no sec objects) and one doctor login locked out of the patient. Reads
 * only, plus the CSRF-rejected and validation-rejected POSTs, which write nothing; a step compares the
 * patient's rows before and after. Cleanup removes the logins, the audit rows about the patient and
 * the patient, and asserts them gone.
 */
const { randomInt } = require('node:crypto');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture, cleanupAll } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');
const { csrfToken } = require('./lib/front-door-checks');
const { PATIENT_TABLES } = require('./lib/xss-poison-helpers');

const SCAN = path.join(__dirname, 'deb-server-log-phi-scan.sh');
const STATUSES = [400, 403, 404, 405];
const FORM = { 'Content-Type': 'application/x-www-form-urlencoded' };

/*
 * Each probe: { status, as, method?, path, query?, form?, token? }.
 *   as     'full' (the test login), 'clerk' (no sec objects) or 'locked' (doctor locked out of the patient)
 *   query  / form  parameters; the placeholders {N} {HIN} {LAST} {FIRST} are filled with the fixture
 *   token  a POST sends the session's CSRF token (so CSRFGuard lets it reach the action); without it
 *          CSRFGuard answers 403 itself
 * Every probe carries the patient in more than one place so an error page that echoes any of them shows.
 * Routes were chosen by reading the Struts maps and the actions' sendError calls, then confirmed live.
 */
const PATIENT = { demographic_no: '{N}', hin: '{HIN}', last_name: '{LAST}' };
const FAMILIES = [
  {
    name: 'demographic',
    probes: [
      // The label action rejects an identifier it cannot read; the HIN is the bad identifier.
      { status: 400, path: 'demographic/printDemoLabelAction', query: { ...PATIENT, demographic_no: '{HIN}', demographicNo: '{N}' } },
      { status: 403, as: 'locked', path: 'demographic/DemographicEdit', query: PATIENT },
      { status: 403, as: 'clerk', path: 'demographic/DemographicEdit', query: PATIENT },
      { status: 404, path: 'demographic/NoSuchDemographicAction', query: PATIENT },
      { status: 405, path: 'demographic/DeleteRelation', query: PATIENT },
    ],
  },
  {
    name: 'chart',
    probes: [
      // method=print validates the dates before it writes a byte; the name and the HIN are the bad dates.
      { status: 400, path: 'CaseManagementEntry', query: { method: 'print', demographicNo: '{N}', hin: '{HIN}', pType: 'dates', pStartDate: '{LAST}', pEndDate: '{HIN}' } },
      { status: 403, as: 'locked', path: 'OscarChartPrint', query: { demographicNo: '{N}', hin: '{HIN}', last_name: '{LAST}' } },
      { status: 403, as: 'clerk', path: 'OscarChartPrint', query: { demographicNo: '{N}', hin: '{HIN}', last_name: '{LAST}' } },
      { status: 404, path: 'encounter/NoSuchEncounterAction', query: { demographicNo: '{N}', hin: '{HIN}', last_name: '{LAST}' } },
      { status: 405, path: 'CaseManagementEntry', query: { method: 'save', demographicNo: '{N}', hin: '{HIN}', last_name: '{LAST}' } },
    ],
  },
  {
    name: 'rx',
    probes: [
      { status: 400, path: 'rx/choosePatient', query: { demographic_no: '{LAST}', hin: '{HIN}' } },
      { status: 403, as: 'locked', path: 'rx/choosePatient', query: PATIENT },
      { status: 403, as: 'clerk', path: 'rx/choosePatient', query: PATIENT },
      { status: 404, path: 'rx/NoSuchRxAction', query: PATIENT },
      { status: 405, path: 'rx/deleteRx', query: { demographicNo: '{N}', hin: '{HIN}', last_name: '{LAST}' } },
    ],
  },
  {
    name: 'lab',
    probes: [
      { status: 400, path: 'lab/DownloadEmbeddedDocumentFromLab', query: { labNo: '{LAST}', ...PATIENT } },
      { status: 403, as: 'clerk', path: 'lab/ViewDemographicLab', query: PATIENT },
      { status: 404, path: 'lab/NoSuchLabAction', query: PATIENT },
      { status: 405, path: 'lab/CA/ALL/createLabLabel', query: PATIENT },
    ],
  },
  {
    name: 'document',
    probes: [
      { status: 400, path: 'documentManager/ViewDocumentReport', query: { function: '{LAST}', functionid: '{N}', hin: '{HIN}' } },
      { status: 403, as: 'locked', path: 'documentManager/ManageDocument', query: { method: 'getDemoNameAjax', demo_no: '{N}', hin: '{HIN}', last_name: '{LAST}' } },
      { status: 403, as: 'clerk', path: 'documentManager/ManageDocument', query: { method: 'getDemoNameAjax', demo_no: '{N}', hin: '{HIN}', last_name: '{LAST}' } },
      { status: 404, path: 'documentManager/NoSuchDocumentAction', query: PATIENT },
      { status: 405, path: 'documentManager/addEditDocument', query: { function: 'demographic', functionid: '{N}', hin: '{HIN}', last_name: '{LAST}' } },
    ],
  },
  {
    name: 'tickler',
    probes: [
      // The add action validates before it persists; the name is the bad demographic_no. CSRF token so it gets that far.
      { status: 400, method: 'POST', token: true, path: 'tickler/DbTicklerAdd', form: { demographic_no: '{LAST}', hin: '{HIN}', ticklerMessage: '{LAST}' } },
      { status: 403, as: 'clerk', path: 'tickler/ListTicklers', query: PATIENT },
      { status: 404, path: 'tickler/NoSuchTicklerAction', query: PATIENT },
      { status: 405, path: 'tickler/AddTickler', query: PATIENT },
    ],
  },
  {
    name: 'billing',
    probes: [
      { status: 400, path: 'billing/CA/ON/ViewBillingCalendarPopup', query: { year: '{HIN}', month: '{LAST}', demographic_no: '{N}' } },
      { status: 403, as: 'clerk', path: 'BillingONReview', query: PATIENT },
      { status: 404, path: 'billing/CA/ON/NoSuchBillingAction', query: PATIENT },
      { status: 405, path: 'billing/CA/ON/createPaymentType', query: PATIENT },
    ],
  },
  {
    name: 'eform',
    probes: [
      // No imagefile: the action refuses before it reads a file.
      { status: 400, path: 'eform/displayImage', query: PATIENT },
      // The package maps no SecurityException result (finding 199), so a role gate answers 500; CSRFGuard's 403 is the family's 403.
      { status: 403, method: 'POST', path: 'eform/addEForm', form: PATIENT },
      { status: 404, path: 'eform/NoSuchEformAction', query: PATIENT },
      { status: 404, path: 'eform/displayImage', query: { imagefile: '{HIN}.png', demographic_no: '{N}', last_name: '{LAST}' } },
      { status: 405, path: 'eform/addEForm', query: PATIENT },
    ],
  },
  {
    name: 'messenger',
    probes: [
      // Doc2PDF names a patient before it touches the compose session; the name is the bad patient number.
      { status: 400, method: 'POST', token: true, path: 'messenger/Doc2PDF', form: { demographicNoParam: '{LAST}', hin: '{HIN}' } },
      { status: 403, method: 'POST', path: 'messenger/CreateMessage', form: PATIENT },
      { status: 404, path: 'messenger/NoSuchMessengerAction', query: PATIENT },
      { status: 405, path: 'messenger/CreateMessage', query: PATIENT },
    ],
  },
  {
    name: 'admin',
    probes: [
      { status: 400, path: 'admin/DemographicMergeRecord', query: { limit1: '{HIN}', keyword: '{LAST}', search_mode: 'search_name', demographic_no: '{N}' } },
      { status: 403, as: 'clerk', path: 'admin/SecuritySearchResults', query: { keyword: '{LAST}', hin: '{HIN}', demographic_no: '{N}' } },
      { status: 404, path: 'admin/NoSuchAdminAction', query: PATIENT },
      { status: 405, path: 'admin/SecurityDelete', query: PATIENT },
    ],
  },
  {
    // Not one of the ten families the plan names: the REST surface answers in JSON, a different renderer
    // than errorpage.jsp, so a body there is worth reading too. It has no 400 probe: the routes tried
    // that carry a patient answer 404 or 500 to bad input (see the notes), so none is listed.
    name: 'rest',
    optional: [400],
    probes: [
      { status: 403, as: 'locked', path: 'ws/rs/demographics/{N}', query: { hin: '{HIN}', name: '{LAST}' } },
      { status: 403, as: 'clerk', path: 'ws/rs/demographics/{N}', query: { hin: '{HIN}', name: '{LAST}' } },
      { status: 404, path: 'ws/rs/demographics/{N}999', query: { hin: '{HIN}', name: '{LAST}' } },
      { status: 404, path: 'ws/rs/nosuchresource/{N}', query: { hin: '{HIN}', name: '{LAST}' } },
      { status: 405, method: 'POST', token: true, path: 'ws/rs/demographics/{N}', form: { hin: '{HIN}' } },
    ],
  },
];

const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;

/** What a body can be judged "CARLOS wrote this" by: its header, or the two pages the application renders. */
function originOf({ status, headers, body }) {
  if (h.isWafPage(status, body)) return 'waf';
  if (Object.prototype.hasOwnProperty.call(headers, h.APPLICATION_HEADER)) return 'application';
  if (/<title>\s*Error Page\s*<\/title>/i.test(body)) return 'application';
  if (/Security Exception/i.test(body) && /insufficient privileges/i.test(body)) return 'application';
  return 'other';
}

/**
 * Read one response: status, who wrote it, the kinds of patient data in its body, the body's size. There is
 * deliberately no catch around the body read: a body that cannot be read has not been shown clean, so the
 * rejection reaches the caller, which records the probe as unreadable.
 */
async function readAnswer(response, find) {
  const body = await response.text();
  const headers = {};
  for (const [name, value] of Object.entries(response.headers())) headers[name.toLowerCase()] = value;
  const actual = response.status();
  return { body, actual, origin: originOf({ status: actual, headers, body }), kinds: find(body), bytes: body.length };
}

/**
 * The detector for one fixture. Returns the KINDS of data a body carries: 'hin', 'name', 'demographic_no'.
 * demographic_no counts only as a number of its own (not inside a longer number, word or UUID), so a request
 * reference or a timestamp cannot match by accident; the other needles are distinctive FAKE- strings.
 */
function detector({ hin, names, patient }) {
  const hinForms = [hin, `${hin.slice(0, 4)}-${hin.slice(4, 7)}-${hin.slice(7)}`, `${hin.slice(0, 4)} ${hin.slice(4, 7)} ${hin.slice(7)}`];
  const number = new RegExp(`(?<![0-9A-Za-z])${patient}(?![0-9A-Za-z])`);
  return function find(body) {
    const text = String(body || '').replace(UUID, '');
    const kinds = [];
    if (hinForms.some(form => text.includes(form))) kinds.push('hin');
    if (names.some(name => text.includes(name))) kinds.push('name');
    if (number.test(text)) kinds.push('demographic_no');
    return kinds;
  };
}

/** The 40 characters around the first hit with the fixture values replaced, for PHI_ERROR_PAGES_SHOW_CONTEXT=1. */
function maskedContext(body, { hin, names, patient }) {
  let text = String(body || '').replace(UUID, '<uuid>');
  const at = [hin, ...names].map(value => text.indexOf(value)).filter(index => index >= 0).sort((a, b) => a - b)[0]
    ?? text.search(new RegExp(`(?<![0-9A-Za-z])${patient}(?![0-9A-Za-z])`));
  if (at === undefined || at < 0) return '';
  text = text.slice(Math.max(0, at - 40), at + 80);
  for (const name of names) text = text.split(name).join('<name>');
  return text.split(hin).join('<hin>').replace(new RegExp(`(?<![0-9A-Za-z])${patient}(?![0-9A-Za-z])`, 'g'), '<demographic_no>')
    .replace(/\s+/g, ' ');
}

/** The route as printed: no query string, and the patient's number and every other number masked. */
function describeRoute(path) {
  return path.replace(/\{N\}\d*/g, '{n}').replace(/\/\d+/g, '/{n}');
}

async function workflow(s) {
  const { sql, marker, provider, config, patient } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  const hex = marker.replace(/^FAKE-PW/, '');
  const first = `FAKE-PWFirst${hex.slice(0, 8)}`;
  const address = `${marker} Fixture Lane`;
  const email = `fake-pw${hex}@example.invalid`;
  const values = { N: String(patient), LAST: marker, FIRST: first };
  const names = [marker, first, address, email];
  const showContext = process.env.PHI_ERROR_PAGES_SHOW_CONTEXT === '1';
  // The scan's window opens a little before the first probe, in the form journalctl and date both read.
  const windowStart = `${new Date(Date.now() - 2000).toISOString().slice(0, 19).replace('T', ' ')} UTC`;
  const journalUnit = process.env.CARLOS_LOG_JOURNAL_UNIT || '';
  if (journalUnit) h.assert(/^[A-Za-z0-9][A-Za-z0-9_.@-]*\.service$/.test(journalUnit), 'CARLOS_LOG_JOURNAL_UNIT must name one systemd .service unit');

  let hin = process.env.PHI_FIXTURE_HIN || '';
  if (hin) h.assert(/^[0-9]{10}$/.test(hin), 'PHI_FIXTURE_HIN must be 10 digits');
  else {
    for (let attempt = 0; attempt < 20 && !hin; attempt++) {
      const candidate = String(randomInt(1000000000, 9999999999));
      if (sql.value(`SELECT COUNT(*) FROM demographic WHERE hin=${h.sqlString(candidate)}`) === '0') hin = candidate;
    }
    h.assert(hin, 'No unused synthetic HIN was found');
  }
  values.HIN = hin;
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE hin=${h.sqlString(hin)} AND demographic_no<>${patient}`) === '0',
    'PHI_FIXTURE_HIN is already the health number of another patient');
  const find = detector({ hin, names, patient });
  const fill = text => text.replace(/\{(N|HIN|LAST|FIRST)\}/g, (_, key) => values[key]);

  let clerk; let doctor; let token;
  s.cleanup(() => cleanupAll(
    () => fixture.cleanup(),
    () => {
      // Reads of the Master Record and the refusals write audit rows about the patient.
      sql.execute(`DELETE FROM log WHERE demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE demographic_no=${patient}`) === '0', 'Owned audit rows were not removed');
    },
  ));

  /** Every response the probes got: { family, status, as, method, route, actual, origin, kinds, bytes }. */
  const seen = [];
  const contexts = () => ({ full: s.context, clerk: clerk.context, locked: doctor.context });

  async function send(family, probe) {
    const query = new URLSearchParams(Object.entries(probe.query || {}).map(([key, value]) => [key, fill(value)])).toString();
    const url = h.appUrl(config.baseUrl, `/${fill(probe.path)}${query ? `?${query}` : ''}`);
    const method = probe.method || 'GET';
    const options = {
      method,
      maxRedirects: 0,
      timeout: 40000,
      failOnStatusCode: false,
      // A browser sends the page that linked here: the chart's own URL carries the patient.
      headers: { Referer: h.appUrl(config.baseUrl, `/demographic/DemographicEdit?demographic_no=${patient}&hin=${hin}`) },
    };
    if (method === 'POST') {
      const form = new URLSearchParams(Object.entries(probe.form || {}).map(([key, value]) => [key, fill(value)]));
      if (probe.token) form.set('CSRF-TOKEN', token);
      options.data = form.toString();
      options.headers = { ...options.headers, ...FORM };
    }
    const row = {
      family, status: probe.status, as: probe.as || 'full', method, route: describeRoute(probe.path),
      actual: 0, origin: 'none', kinds: [], bytes: 0,
    };
    try {
      const response = await contexts()[row.as].request.fetch(url, options);
      // An unreadable body throws into the catch below, which marks the row unreadable; it is never read as clean.
      const answer = await readAnswer(response, find);
      row.actual = answer.actual;
      row.origin = answer.origin;
      row.kinds = answer.kinds;
      row.bytes = answer.bytes;
      if (row.kinds.length && showContext) row.context = maskedContext(answer.body, { hin, names, patient });
    } catch (error) {
      row.origin = 'error';
      // A Playwright message can quote the URL, and the URL carries the fixture: print the first line without any URL or fixture value.
      let message = String(error.message).split('\n')[0].replace(/https?:\/\/\S+/g, '<url>');
      for (const value of [hin, ...names]) message = message.split(value).join('<fixture>');
      row.error = message.slice(0, 80);
    }
    seen.push(row);
    const reached = row.actual === row.status && row.origin === 'application';
    const who = method === 'POST' && !probe.token ? `${row.as}, no CSRF token` : row.as;
    console.log(`  probe phi-in-error-pages: ${family} ${row.status} (${who}) ${method} ${row.route} -> ${row.actual || 'no response'} `
      + `${row.origin}${reached ? '' : ' (not the status provoked)'}${row.kinds.length ? `; CARRIES ${row.kinds.join(', ')}` : ''}`);
    return row;
  }

  const patientRows = () => sql.value(`SELECT CONCAT_WS('|', ${PATIENT_TABLES.filter(([table]) => table !== 'log')
    .map(([table, column]) => `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${patient})`).join(', ')},
    (SELECT MD5(CONCAT_WS('|', last_name, first_name, hin, address, email, patient_status, lastUpdateDate)) FROM demographic WHERE demographic_no=${patient}))`);
  let rowsBefore;

  await s.step('fixtures: an owned patient with a synthetic HIN and FAKE- names, a login with no sec objects and a doctor locked out of the patient', async () => {
    sql.execute(`UPDATE demographic SET hin=${h.sqlString(hin)}, first_name=${h.sqlString(first)}, address=${h.sqlString(address)},
      email=${h.sqlString(email)}, hc_type='ON' WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND hin=${h.sqlString(hin)}`) === '1',
      'The patient fixture did not take its health number');
    h.assert(fixture.roleHoldsNothing('er_clerk'), 'er_clerk is expected to hold no sec object');
    clerk = await signIn(s, fixture.addLogin('er_clerk'));
    const login = fixture.addLogin('doctor');
    h.assert(fixture.lockPatient(login, patient).length === 2, 'The patient lock rows were not written');
    doctor = await signIn(s, login);
    token = await csrfToken(s.schedule, config.baseUrl);
  });

  let ghost = null;
  s.cleanup(() => {
    if (ghost) sql.execute(`DELETE FROM demographic WHERE demographic_no=${ghost} AND last_name=${h.sqlString(marker)} AND first_name='Ghost'`);
  });
  await s.step('control: an error body that echoes a request value is caught (the REST 404 for a demographic_no that has just been deleted)', async () => {
    ghost = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
        provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(marker)},'Ghost','1980','01','02','F','AC',${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(ghost), 'The throwaway demographic row was not created');
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${ghost} AND last_name=${h.sqlString(marker)} AND first_name='Ghost'`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${ghost}`) === '0', 'The throwaway demographic row was not removed');
    const response = await s.context.request.fetch(h.appUrl(config.baseUrl, `/ws/rs/demographics/${ghost}`), { maxRedirects: 0, failOnStatusCode: false, timeout: 40000 });
    const body = await response.text();
    h.assert(response.status() === 404, `The REST read of a deleted demographic_no answered HTTP ${response.status()}, not 404`);
    h.assert(detector({ hin, names, patient: ghost })(body).includes('demographic_no'),
      'The 404 for a deleted demographic_no no longer repeats the number, so this control no longer proves the detector can see an echo in an error body; pick another echoing route');
    ghost = null;
  });

  await s.step('control: the full login Master Record and REST answers carry the patient HIN, name and demographic_no, which the detector finds', async () => {
    const wanted = ['hin', 'name', 'demographic_no'];
    for (const [route, kinds] of [[`demographic/DemographicEdit?demographic_no=${patient}`, wanted], [`ws/rs/demographics/${patient}`, wanted]]) {
      const response = await s.context.request.fetch(h.appUrl(config.baseUrl, `/${route}`), { maxRedirects: 0, failOnStatusCode: false, timeout: 40000 });
      const body = await response.text();
      const found = find(body);
      h.assert(response.status() === 200, `The control ${describeRoute(route.split('?')[0])} answered HTTP ${response.status()}`);
      h.assert(kinds.every(kind => found.includes(kind)),
        `The control ${describeRoute(route.split('?')[0])} did not show ${kinds.filter(kind => !found.includes(kind)).join(', ')} of the fixture, so the detector cannot be trusted to see a leak there`);
    }
    rowsBefore = patientRows();
  });

  for (const family of FAMILIES) {
    await s.step(`${family.name}: 400, 403, 404 and 405 are provoked and each answer is recorded`, async () => {
      for (const probe of family.probes) await send(family.name, probe);
      h.assert(seen.some(row => row.family === family.name), `${family.name}: no probe was sent`);
    });
  }

  await s.step('the probes wrote nothing for the patient (rows keyed to it and the demographic row itself are unchanged)', async () => {
    h.assert(patientRows() === rowsBefore, 'A probe changed rows belonging to the patient, so a mutator route did not refuse');
  });

  await s.step('every family was answered 400, 403, 404 and 405 by the application itself (a front-door page is listed, never counted)', async () => {
    const missing = [];
    for (const family of FAMILIES) {
      const cells = STATUSES.map(status => {
        const rows = seen.filter(row => row.family === family.name && row.status === status);
        const reached = rows.some(row => row.actual === status && row.origin === 'application');
        if (!reached && !(family.optional || []).includes(status)) {
          const got = rows.map(row => `${row.as} ${row.route} -> ${row.actual || 'none'} ${row.origin}`);
          missing.push(`${family.name} ${status}${got.length ? ` (got ${got.join('; ')})` : ' (no probe)'}`);
        }
        return reached ? String(status) : `${status}${(family.optional || []).includes(status) ? '(none provoked)' : '(MISSED)'}`;
      });
      console.log(`  coverage phi-in-error-pages: ${family.name.padEnd(11)} ${cells.join(' ')}`);
    }
    const waf = seen.filter(row => row.origin === 'waf');
    if (waf.length) console.log(`  NOTE phi-in-error-pages: ${waf.length} response(s) were the WAF's block page, not a CARLOS body: ${waf.map(row => `${row.family} ${row.status} ${row.route}`).join('; ')}`);
    const empty = seen.filter(row => row.origin !== 'error' && row.bytes === 0);
    if (empty.length) console.log(`  NOTE phi-in-error-pages: ${empty.length} response(s) had an empty body, which no leak can hide in: ${empty.map(row => `${row.family} ${row.status} ${row.route} -> ${row.actual}`).join('; ')}`);
    const other = seen.filter(row => row.origin === 'other' || row.origin === 'error');
    if (other.length) console.log(`  NOTE phi-in-error-pages: ${other.length} response(s) came from neither the application nor the WAF (the proxy or the container): ${other.map(row => `${row.family} ${row.status} ${row.route} -> ${row.actual || 'none'}`).join('; ')}`);
    const leaking = seen.filter(row => row.kinds.length).length;
    // A probe whose answer or body could not be read says nothing about leaks, whatever the other probes of its cell showed.
    const unread = seen.filter(row => row.origin === 'error');
    h.assert(!unread.length, `${unread.length} probe(s) got no readable answer, so their bodies were never judged: ${unread.map(row => `${row.family} ${row.status} ${row.as} ${row.method} ${row.route} (${row.error})`).join('; ')}`);
    h.assert(!missing.length, `Statuses the application was not seen to answer: ${missing.join('; ')}${leaking ? ` (${leaking} response(s) also carried patient data; the last step names them)` : ''}`);
    console.log(`  coverage phi-in-error-pages: ${seen.length} responses read, ${new Set(seen.map(row => row.family)).size} families`);
  });

  await s.step('no error response carries the patient HIN, FAKE- name or demographic_no', async () => {
    const leaks = seen.filter(row => row.kinds.length);
    h.assert(!leaks.length, `${leaks.length} of ${seen.length} error responses carried patient data: ${leaks.map(row => `${row.family} ${row.status} ${row.as} ${row.method} ${row.route} (HTTP ${row.actual}, ${row.origin}) carries ${row.kinds.join(' and ')}${row.context ? ` in "${row.context}"` : ''}`).join('; ')}`);
  });

  if (!journalUnit) {
    // A pass here would say the log is clean when it was never read, and the manifest's expectedFailure (finding 204)
    // would then read "unexpected pass". Every body step above has passed; the run ends as a skip that says why.
    throw new h.SkipCheck('every error body was clean, but CARLOS_LOG_JOURNAL_UNIT is not set, so there is no journal to read '
      + 'and the server log was not judged (set it to the carlos-emr systemd unit, as suite-env.sh does)');
  }
  let scan;
  await s.step('the PHI log scan reads this run window (journal and Tomcat log) after the probes', async () => {
    await new Promise(resolve => setTimeout(resolve, 2500)); // log4j hands events to its appender asynchronously
    const args = [SCAN, '--since', windowStart, '--unit', journalUnit, '--only-given', '--hin', hin,
      ...names.flatMap(name => ['--marker', name])];
    const result = spawnSync('bash', args, { encoding: 'utf8', timeout: 60000 });
    scan = { status: result.status, out: String(result.stdout || '').trim() };
    // The scan prints counts and logger names only, so its output may be quoted. Exit 2 is "could not read the log".
    h.assert(scan.status === 0 || scan.status === 1, `The PHI log scan could not run (exit ${scan.status}): ${String(result.stderr || '').split('\n')[0].slice(0, 200)}`);
    const read = /journal (\d+) line\(s\)/.exec(scan.out);
    h.assert(read && Number(read[1]) > 0, 'The PHI log scan read no journal line in this run window, so it cannot say the log is clean');
  });

  // Finding 204. The scan reports counts and logger names, never the matching text, so its output is safe to quote.
  await s.step('the server log holds none of this run HIN, FAKE- names, address or e-mail', async () => {
    h.assert(scan && scan.status === 0, `The server log holds this run patient data:\n${scan ? scan.out : 'no scan result'}`);
  });
}

if (require.main === module) runWorkflow('phi-in-error-pages', workflow, { openMaster: false });
module.exports = { workflow, detector, maskedContext, originOf, readAnswer, describeRoute, FAMILIES };
