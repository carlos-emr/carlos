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
 *   pharmacies/                          GET   _rx r            (refused with AccessDeniedException)
 *   forms/allEForms                      GET   _eform r         (refused with SecurityException)
 *
 * The last two refuse by throwing rather than with a ForbiddenException; the REST surfaces map
 * both exception types to 403, so they must not end in the generic HTTP 500 error page.
 *
 * The OAuth surface (/ws/services) registers the same services and the same 403 mappers. The
 * receptionist authorizes an out-of-band OAuth 1.0a token through the consent page, and its signed
 * calls must be refused there exactly as its session calls are on /ws/rs.
 *
 * Logins: the full-privilege test login is the control; throwaway `receptionist` (holds
 * _demographic, lacks _eChart/_admin/_appDefinition/templates/_rx/_eform) and `doctor` (chart,
 * templates, _rx and _eform; lacks _admin/_appDefinition) logins come from lib/authz-read-fixture.js. A refusal only counts
 * when it is HTTP 403 written by the application (its own response header), so a WAF block or the
 * generic 500 error page cannot pass for an authorization decision.
 *
 * Fixtures: the workflow's owned FAKE- patient, two throwaway logins, a patient-level |o| lock on
 * _eChart$<patient> for the doctor login, one encounter template and one consent type named after
 * the run marker (the consent type is created through the API by the control login), and one
 * out-of-band OAuth ServiceClient with the tokens and nonces issued to it. Cleanup
 * removes every owned row and asserts it. The template endpoints are called the way the encounter
 * client calls them (explicit paging, an existing template name): without paging or with an
 * unknown name they fail with HTTP 500 for every caller, which says nothing about authorization.
 * Environment: the common contract (BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*).
 */
const crypto = require('crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');
const { oauthHeader, pct } = require('./oauth-rest-surfaces-playwright-checks');

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
  return readResult(await context.request.fetch(h.appUrl(config.baseUrl, `/ws/rs/${route}`), options));
}

/** One signed OAuth 1.0a GET on /ws/services from a cookie-less request context. */
async function signedRest(request, config, signing, route) {
  const url = h.appUrl(config.baseUrl, `/ws/services/${route}`);
  return readResult(await request.fetch(url, {
    method: 'GET', maxRedirects: 0, timeout: 60000, failOnStatusCode: false,
    headers: { Accept: 'application/json', Authorization: oauthHeader({ method: 'GET', url, ...signing }) },
  }));
}

async function readResult(response) {
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
  // Registered after the login fixture, so it runs first: the tokens name the receptionist.
  const consumerKey = crypto.randomBytes(12).toString('hex');
  const consumerSecret = crypto.randomBytes(12).toString('hex');
  s.cleanup(() => {
    const key = h.sqlString(consumerKey);
    const owned = `(SELECT id FROM ServiceClient WHERE clientKey=${key})`;
    sql.execute(`DELETE FROM ServiceAccessToken WHERE clientId IN ${owned};
      DELETE FROM ServiceRequestToken WHERE clientId IN ${owned};
      DELETE FROM ServiceOAuthNonce WHERE consumerKey=${key};
      DELETE FROM ServiceClient WHERE clientKey=${key}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ServiceClient WHERE clientKey=${key})
        + (SELECT COUNT(*) FROM ServiceOAuthNonce WHERE consumerKey=${key})`) === '0',
    'The OAuth client fixture or its nonces were not removed');
  });
  const templateName = `${marker}-tpl`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM encountertemplate WHERE encountertemplate_name=${h.sqlString(templateName)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM encountertemplate WHERE encountertemplate_name=${h.sqlString(templateName)}`) === '0',
      'The owned encounter template was not removed');
  });
  sql.execute(`INSERT INTO encountertemplate (encountertemplate_name,createdatetime,encountertemplate_value,creator)
    VALUES (${h.sqlString(templateName)},NOW(),${h.sqlString(`${marker} template text`)},${h.sqlString(provider)})`);
  const TEMPLATE_CALLS = [
    ['POST', 'recordUX/searchTemplates?startIndex=0&itemsToReturn=5', { name: marker }],
    ['POST', 'recordUX/template', { name: templateName }],
  ];
  // Guards that refuse by throwing (AccessDeniedException / SecurityException), not ForbiddenException.
  const THROWING_GUARDS = ['pharmacies/', 'forms/allEForms'];
  const servesTemplate = result => result.status === 200
    && (result.json?.templates || []).some(template => template.encounterTemplateName === templateName);
  const consentRows = () => sql.value(`SELECT COUNT(*) FROM consentType WHERE name=${h.sqlString(consentName)}`);
  const newConsentType = () => ({ name: consentName, description: `${marker} consent type`, type: consentName, active: true });

  const logins = {};
  const contexts = { full: s.context };
  const call = (who, method, route, body) => rest(contexts[who], config, method, route, body);

  await s.step('the receptionist and doctor roles hold exactly the objects this check relies on', async () => {
    const receptionist = fixture.rolePrivileges('receptionist').map(entry => entry.split(':')[0]);
    const doctor = fixture.rolePrivileges('doctor').map(entry => entry.split(':')[0]);
    h.assert(receptionist.includes('_demographic'), 'receptionist no longer holds _demographic');
    for (const object of ['_eChart', '_admin', '_appDefinition', '_newCasemgmt.templates', '_rx', '_eform']) {
      h.assert(!receptionist.includes(object), `receptionist now holds ${object}`);
    }
    for (const object of ['_demographic', '_eChart', '_newCasemgmt.templates', '_rx', '_eform']) {
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
    for (const [method, route, body] of TEMPLATE_CALLS) {
      const templates = await call('full', method, route, body);
      if (!servesTemplate(templates)) wrong.push(describe('full', method, route.split('?')[0], templates));
    }
    for (const route of THROWING_GUARDS) {
      const result = await call('full', 'GET', route);
      if (result.status !== 200 || !result.json) wrong.push(describe('full', 'GET', route, result));
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

  await s.step('the receptionist reads the consent catalogue but is refused (403) the chart, templates, Rx, eForm and admin endpoints', async () => {
    const wrong = [];
    const types = await call('receptionist', 'GET', 'consentService/consentTypes');
    if (types.status !== 200) wrong.push(`${describe('receptionist', 'GET', 'consentService/consentTypes', types)} (holds _demographic)`);
    const refusals = [
      ['GET', 'app/getApps/'],
      ['GET', `recordUX/${patient}/print?printOps=${encodeURIComponent(PRINT_OPTIONS)}`],
      ['GET', `recordUX/${patient}/getAllergies`],
      ...TEMPLATE_CALLS,
      ...THROWING_GUARDS.map(route => ['GET', route]),
      ['POST', 'consentService/consentType', newConsentType()],
    ];
    for (const [method, route, body] of refusals) {
      const result = await call('receptionist', method, route, body);
      if (!(result.status === 403 && result.fromApp) || result.pdf) wrong.push(describe('receptionist', method, route.split('?')[0], result));
    }
    h.assert(consentRows() === '0', 'The refused consent-type POST still stored a row');
    h.assert(!wrong.length, `Expected an application 403: ${wrong.join('; ')}`);
  });

  await s.step('on /ws/services the receptionist\'s signed OAuth token is refused (403) the same endpoints', async () => {
    // uri='oob': the verifier is shown on the consent page instead of redirected.
    h.insertId(sql, `INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime)
        VALUES(${h.sqlString(`${marker}-oauth`)},${h.sqlString(consumerKey)},
        ${h.sqlString(consumerSecret)},'oob',3600)`, 'ServiceClient');
    const anonymous = await h.newContext(s.context.browser(), config);
    try {
      const api = anonymous.request;
      const form = async response => Object.fromEntries(new URLSearchParams((await response.text()).trim()));
      const initiateUrl = h.appUrl(config.baseUrl, `/ws/oauth/initiate?scope=${pct('provider.read')}`);
      let response = await api.post(initiateUrl, {
        failOnStatusCode: false,
        headers: { Authorization: oauthHeader({ method: 'POST', url: initiateUrl, consumerKey, consumerSecret, extra: { oauth_callback: 'oob' } }) },
      });
      h.assert(response.status() === 200, `Signed POST /ws/oauth/initiate answered HTTP ${response.status()}`);
      const requestToken = await form(response);
      h.assert(requestToken.oauth_token && requestToken.oauth_token_secret, '/ws/oauth/initiate returned no request token');

      // The receptionist approves the request token in its own browser session.
      const consent = await contexts.receptionist.newPage();
      let verifier;
      try {
        const page = await consent.goto(
          h.appUrl(config.baseUrl, `/ws/oauth/authorize?oauth_token=${pct(requestToken.oauth_token)}`), { waitUntil: 'load' });
        h.assert(page && page.status() === 200, `The receptionist's consent page answered HTTP ${page && page.status()}`);
        const [approval] = await Promise.all([
          consent.waitForResponse(resp => resp.request().method() === 'POST'
            && new URL(resp.url()).pathname.endsWith('/ws/oauth/authorize'), { timeout: 30000 }),
          consent.locator('#scopeForm button[type="submit"]').click(),
        ]);
        h.assert(approval.status() === 200, `Approving the request token answered HTTP ${approval.status()}`);
        verifier = (await form(approval)).oauth_verifier;
        h.assert(verifier, 'Approving the request token showed no oauth_verifier');
      } finally {
        await consent.close();
      }

      const tokenUrl = h.appUrl(config.baseUrl, '/ws/oauth/token');
      response = await api.post(tokenUrl, {
        failOnStatusCode: false,
        headers: {
          Authorization: oauthHeader({
            method: 'POST', url: tokenUrl, consumerKey, consumerSecret, token: requestToken.oauth_token,
            tokenSecret: requestToken.oauth_token_secret, extra: { oauth_verifier: verifier },
          }),
        },
      });
      h.assert(response.status() === 200, `Signed POST /ws/oauth/token answered HTTP ${response.status()}`);
      const accessToken = await form(response);
      const signing = { consumerKey, consumerSecret, token: accessToken.oauth_token, tokenSecret: accessToken.oauth_token_secret };

      // Control: the token authenticates, as the receptionist.
      const info = await signedRest(api, config, signing, 'oauth/info');
      h.assert(info.status === 200 && String(info.json?.login) === String(logins.receptionist.providerNo),
        `${describe('receptionist (OAuth)', 'GET', 'oauth/info', info)}; expected its own provider number`);
      const wrong = [];
      for (const route of ['app/getApps/', `recordUX/${patient}/getAllergies`, ...THROWING_GUARDS]) {
        const result = await signedRest(api, config, signing, route);
        if (!(result.status === 403 && result.fromApp)) wrong.push(describe('receptionist (OAuth)', 'GET', route, result));
      }
      h.assert(!wrong.length, `Expected an application 403 on /ws/services: ${wrong.join('; ')}`);
    } finally {
      await anonymous.close();
    }
  });

  await s.step('the doctor reads the chart, templates, pharmacies and eForms but is refused (403) the admin-only endpoints', async () => {
    const wrong = [];
    const print = await call('doctor', 'GET', `recordUX/${patient}/print?printOps=${encodeURIComponent(PRINT_OPTIONS)}`);
    if (print.status !== 200 || !print.pdf) wrong.push(`${describe('doctor', 'GET', 'recordUX/{demo}/print', print)} (holds _eChart)`);
    const allergies = await call('doctor', 'GET', `recordUX/${patient}/getAllergies`);
    if (allergies.status !== 200) wrong.push(`${describe('doctor', 'GET', 'recordUX/{demo}/getAllergies', allergies)} (holds _eChart)`);
    for (const [method, route, body] of TEMPLATE_CALLS) {
      const templates = await call('doctor', method, route, body);
      if (!servesTemplate(templates)) wrong.push(`${describe('doctor', method, route.split('?')[0], templates)} (holds templates)`);
    }
    for (const route of THROWING_GUARDS) {
      const result = await call('doctor', 'GET', route);
      if (result.status !== 200) wrong.push(`${describe('doctor', 'GET', route, result)} (holds the object)`);
    }
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
module.exports = { workflow, rest, signedRest };
