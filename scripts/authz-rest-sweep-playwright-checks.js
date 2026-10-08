#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * JAX-RS REST endpoints authorize the caller, not only authenticate them (issue #2798).
 *
 * The session REST surface (/ws/rs) used to admit any logged-in user to every service method; the
 * #2798 sweep added SecurityInfoManager.hasPrivilege gates. This check drives the installed
 * application through real logins and asserts the gates hold end to end (CXF routing, the
 * AuthenticationInInterceptor, the service guard, the HTTP status), using the endpoints the
 * release/2026.08 port changed:
 *
 *   consentService/consentTypes          GET   _demographic r
 *   consentService/consentType           POST  _admin w        (the refused POST must write nothing)
 *   app/getApps/                         GET   _appDefinition r
 *   recordUX/{demo}/print                GET   _eChart r, patient-scoped (a PDF of the whole chart)
 *   recordUX/{demo}/getAllergies         GET   _eChart r, patient-scoped (fullSummary shortcut)
 *   recordUX/searchTemplates, /template  POST  _newCasemgmt.templates r
 *   status/checkIfAuthed                 GET   any authenticated caller; answers with its own provider
 *
 * Logins: the full-privilege test login is the control; throwaway `receptionist` (holds
 * _demographic, lacks _eChart/_admin/_appDefinition/templates) and `doctor` (chart and templates,
 * lacks _admin/_appDefinition) logins come from lib/authz-read-fixture.js. A refusal only counts
 * when it is HTTP 403 written by the application (its own response header), so a WAF block or the
 * generic 500 error page cannot pass for an authorization decision.
 *
 * Fixtures: the workflow's owned FAKE- patient, two throwaway logins, a patient-level |o| lock on
 * _eChart$<patient> for the doctor login, and one consent type named after the run marker that
 * the control login creates through the API. Cleanup removes every owned row and asserts it.
 * Environment: the common contract (BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');

const APPLICATION_HEADER = 'x-permitted-cross-domain-policies';
const PRINT_OPTIONS = JSON.stringify({ printType: 'all', cpp: true, selectedList: [] });

/** One REST call through a login's browser context; never follows redirects. */
async function rest(context, config, method, route, body) {
  const options = {
    method, maxRedirects: 0, timeout: 60000, failOnStatusCode: false,
    headers: { Accept: route.includes('/print') ? 'application/pdf' : 'application/json' },
  };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.data = JSON.stringify(body);
  }
  const response = await context.request.fetch(h.appUrl(config.baseUrl, `/ws/rs/${route}`), options);
  const bytes = await response.body();
  const text = bytes.toString('utf8');
  let json = null;
  try { json = JSON.parse(text); } catch { /* not JSON: PDFs, error pages, empty 403 bodies */ }
  return {
    status: response.status(),
    fromApp: Object.prototype.hasOwnProperty.call(response.headers(), APPLICATION_HEADER),
    pdf: bytes.subarray(0, 5).toString() === '%PDF-',
    json,
    text,
  };
}

const describe = (who, method, route, result) => `${who} ${method} ${route} -> HTTP ${result.status}`;

async function workflow(s) {
  const { sql, marker, provider, config, patient } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const consentName = `${marker}-consent`.slice(0, 50);
  s.cleanup(() => {
    sql.execute(`DELETE FROM consentType WHERE name=${h.sqlString(consentName)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM consentType WHERE name=${h.sqlString(consentName)}`) === '0',
      'The owned consent type was not removed');
  });
  const consentRows = () => sql.value(`SELECT COUNT(*) FROM consentType WHERE name=${h.sqlString(consentName)}`);
  const newConsentType = () => ({ name: consentName, description: `${marker} consent type`, type: consentName, active: true });

  const logins = {};
  const contexts = { full: s.context };
  const call = (who, method, route, body) => rest(contexts[who], config, method, route, body);

  await s.step('the receptionist and doctor roles hold exactly the objects this check relies on', async () => {
    const receptionist = fixture.rolePrivileges('receptionist').map(entry => entry.split(':')[0]);
    const doctor = fixture.rolePrivileges('doctor').map(entry => entry.split(':')[0]);
    h.assert(receptionist.includes('_demographic'), 'receptionist no longer holds _demographic');
    for (const object of ['_eChart', '_admin', '_appDefinition', '_newCasemgmt.templates']) {
      h.assert(!receptionist.includes(object), `receptionist now holds ${object}`);
    }
    for (const object of ['_demographic', '_eChart', '_newCasemgmt.templates']) {
      h.assert(doctor.includes(object), `doctor no longer holds ${object}`);
    }
    for (const object of ['_admin', '_appDefinition']) {
      h.assert(!doctor.includes(object), `doctor now holds ${object}`);
    }
    for (const role of ['receptionist', 'doctor']) {
      logins[role] = fixture.addLogin(role);
      contexts[role] = (await signIn(s, logins[role])).context;
    }
  });

  await s.step('an anonymous caller is refused with 401 before any REST service runs', async () => {
    const anonymous = await h.newContext(s.context.browser(), config);
    try {
      const wrong = [];
      for (const route of ['status/checkIfAuthed', 'consentService/consentTypes', 'app/getApps/',
        `recordUX/${patient}/getAllergies`]) {
        const result = await rest(anonymous, config, 'GET', route);
        if (result.status !== 401) wrong.push(describe('anonymous', 'GET', route, result));
      }
      h.assert(!wrong.length, `Not refused with 401: ${wrong.join('; ')}`);
    } finally {
      await anonymous.close();
    }
  });

  await s.step('controls: the full-privilege login is served every guarded endpoint', async () => {
    const wrong = [];
    const types = await call('full', 'GET', 'consentService/consentTypes');
    if (types.status !== 200 || !Array.isArray(types.json?.content)) wrong.push(describe('full', 'GET', 'consentService/consentTypes', types));
    const apps = await call('full', 'GET', 'app/getApps/');
    if (apps.status !== 200 || !Array.isArray(apps.json)) wrong.push(describe('full', 'GET', 'app/getApps/', apps));
    const allergies = await call('full', 'GET', `recordUX/${patient}/getAllergies`);
    if (allergies.status !== 200 || !allergies.json) wrong.push(describe('full', 'GET', 'recordUX/{demo}/getAllergies', allergies));
    const print = await call('full', 'GET', `recordUX/${patient}/print?printOps=${encodeURIComponent(PRINT_OPTIONS)}`);
    if (print.status !== 200 || !print.pdf) wrong.push(describe('full', 'GET', 'recordUX/{demo}/print', print));
    for (const route of ['recordUX/searchTemplates', 'recordUX/template']) {
      const templates = await call('full', 'POST', route, { name: `${marker}-none` });
      if (templates.status !== 200) wrong.push(describe('full', 'POST', route, templates));
    }
    h.assert(!wrong.length, `The control login was not served: ${wrong.join('; ')}`);
  });

  await s.step('the control login (holds _admin w) can add a consent type through the API', async () => {
    h.assert(consentRows() === '0', 'A consent type with this run marker already exists');
    const added = await call('full', 'POST', 'consentService/consentType', newConsentType());
    h.assert(added.status === 200 && added.json?.status === 'SUCCESS',
      `Adding a consent type as the control login answered HTTP ${added.status}`);
    h.assert(consentRows() === '1', 'The consent type the control login added was not stored');
    sql.execute(`DELETE FROM consentType WHERE name=${h.sqlString(consentName)}`);
  });

  await s.step('every login confirms its own authentication and is told only its own provider number', async () => {
    const expected = { full: provider, receptionist: logins.receptionist.providerNo, doctor: logins.doctor.providerNo };
    for (const [who, providerNo] of Object.entries(expected)) {
      const result = await call(who, 'GET', 'status/checkIfAuthed');
      h.assert(result.status === 200 && result.json?.body === providerNo,
        `${describe(who, 'GET', 'status/checkIfAuthed', result)}; expected its own provider number`);
    }
  });

  await s.step('the receptionist reads the consent catalogue but is refused (403) the chart, templates and admin endpoints', async () => {
    const wrong = [];
    const types = await call('receptionist', 'GET', 'consentService/consentTypes');
    if (types.status !== 200) wrong.push(`${describe('receptionist', 'GET', 'consentService/consentTypes', types)} (holds _demographic)`);
    const refusals = [
      ['GET', 'app/getApps/'],
      ['GET', `recordUX/${patient}/print?printOps=${encodeURIComponent(PRINT_OPTIONS)}`],
      ['GET', `recordUX/${patient}/getAllergies`],
      ['POST', 'recordUX/searchTemplates', { name: '' }],
      ['POST', 'recordUX/template', { name: '' }],
      ['POST', 'consentService/consentType', newConsentType()],
    ];
    for (const [method, route, body] of refusals) {
      const result = await call('receptionist', method, route, body);
      if (!(result.status === 403 && result.fromApp) || result.pdf) wrong.push(describe('receptionist', method, route.split('?')[0], result));
    }
    h.assert(consentRows() === '0', 'The refused consent-type POST still stored a row');
    h.assert(!wrong.length, `Expected an application 403: ${wrong.join('; ')}`);
  });

  await s.step('the doctor reads the chart and templates but is refused (403) the admin-only endpoints', async () => {
    const wrong = [];
    const print = await call('doctor', 'GET', `recordUX/${patient}/print?printOps=${encodeURIComponent(PRINT_OPTIONS)}`);
    if (print.status !== 200 || !print.pdf) wrong.push(`${describe('doctor', 'GET', 'recordUX/{demo}/print', print)} (holds _eChart)`);
    const allergies = await call('doctor', 'GET', `recordUX/${patient}/getAllergies`);
    if (allergies.status !== 200) wrong.push(`${describe('doctor', 'GET', 'recordUX/{demo}/getAllergies', allergies)} (holds _eChart)`);
    const templates = await call('doctor', 'POST', 'recordUX/searchTemplates', { name: '' });
    if (templates.status !== 200) wrong.push(`${describe('doctor', 'POST', 'recordUX/searchTemplates', templates)} (holds templates)`);
    for (const [method, route, body] of [['GET', 'app/getApps/'], ['POST', 'consentService/consentType', newConsentType()]]) {
      const result = await call('doctor', method, route, body);
      if (!(result.status === 403 && result.fromApp)) wrong.push(describe('doctor', method, route, result));
    }
    h.assert(consentRows() === '0', 'The refused consent-type POST still stored a row');
    h.assert(!wrong.length, `Unexpected answer: ${wrong.join('; ')}`);
  });

  await s.step('a patient-level _eChart lock withholds the chart print and summary from the doctor', async () => {
    fixture.lockPatient(logins.doctor, patient, ['_eChart']);
    const wrong = [];
    for (const route of [`recordUX/${patient}/print?printOps=${encodeURIComponent(PRINT_OPTIONS)}`, `recordUX/${patient}/getAllergies`]) {
      const result = await call('doctor', 'GET', route);
      if (!(result.status === 403 && result.fromApp) || result.pdf) wrong.push(describe('doctor (locked out)', 'GET', route.split('?')[0], result));
    }
    h.assert(!wrong.length, `The locked patient's chart was not refused: ${wrong.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('authz-rest-sweep', workflow, { openMaster: false });
module.exports = { workflow, rest };
