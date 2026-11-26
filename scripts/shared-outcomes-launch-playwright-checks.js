#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Schedule > Dashboard > Common Provider Dashboard > Shared Outcomes popup (#5053).
 * Requires a disposable install configured with these synthetic properties:
 * shared_outcomes_dashboard_url=https://shared-outcomes.invalid/
 * shared_outcomes_dashboard_host=https://shared-outcomes.invalid
 * shared_outcomes_dashboard_key=carlos-5053-fixture
 * shared_outcomes_dashboard_salt=8f3c21ab56789def1234567890abcdef
 * shared_outcomes_dashboard_clinic_id=TEST_CLINIC_5053
 * shared_outcomes_dashboard_send_url=http://127.0.0.1:18335/send   (#5335)
 * Set SHARED_OUTCOMES_TEST_FIXTURE=true after configuring them and restarting.
 * All dashboard requests are fulfilled by Playwright; no external service is contacted.
 * The send URL is the one destination the application itself calls: this script listens on
 * 127.0.0.1:${SHARED_OUTCOMES_SEND_PORT:-18335} (change the property to match) and answers it, so the
 * refresh path that publishes provider metrics (POST /web/dashboard/OutcomesDashboard, #5335) is
 * exercised end to end without leaving the machine.
 * The throwaway login, its grants, a synthetic indicator template and its patients, and login side
 * effects are removed by cleanup.
 */
const http = require('node:http');
const { pbkdf2Sync, createDecipheriv } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { authenticatedProbe } = require('./lib/authenticated-probe');

const ORIGIN = 'https://shared-outcomes.invalid';
const SALT = '8f3c21ab56789def1234567890abcdef';
const SEND_PORT = Number(process.env.SHARED_OUTCOMES_SEND_PORT || 18335);
const REFRESH_PATH = '/web/dashboard/OutcomesDashboard';
// _dashboardManager write is what DashboardManager.getIndicatorLibrary requires, and the refresh reads the library.
const GRANTS = [['_dashboardDisplay', 'r'], ['_dashboardCommonLink', 'r'], ['_dashboardDrilldown', 'r'], ['_dashboardManager', 'w']];

/** One indicator scoped to the owned provider's patients, with the shared-dashboard name, data id and label mapping. */
function sharedIndicatorXml(marker) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<indicatorTemplateXML>
  <author>CARLOS EMR Playwright</author>
  <uid></uid>
  <heading>
    <category>${marker} Category</category>
    <subCategory>${marker} Owned patients</subCategory>
    <name>${marker} Shared status</name>
    <definition>${marker} owned patients by status</definition>
    <framework>CARLOS EMR</framework>
    <frameworkVersion>01-01-2026</frameworkVersion>
    <notes>Synthetic indicator owned by one check run</notes>
  </heading>
  <indicatorQuery>
    <version>01-01-2026</version>
    <params><parameter id="provider" name="provider_no" value="loggedInProvider" /></params>
    <query>SELECT SUM(IF(d.patient_status = 'AC', 1, 0)) AS "Active", SUM(IF(d.patient_status = 'AC', 0, 1)) AS "Not active" FROM demographic d WHERE d.provider_no = '\${provider}' AND d.last_name = '${marker}'</query>
  </indicatorQuery>
  <drillDownQuery>
    <version>01-01-2026</version>
    <params><parameter id="provider" name="provider_no" value="loggedInProvider" /></params>
    <displayColumns>
      <column id="demographic" name="d.demographic_no" title="Patient Id" primary="true" />
    </displayColumns>
    <exportColumns>
      <column id="demographic" name="d.demographic_no" title="Patient Id" primary="true" />
    </exportColumns>
    <query>SELECT d.* FROM demographic d WHERE d.provider_no = '\${provider}' AND d.last_name = '${marker}'</query>
  </drillDownQuery>
  <sharedMetricSetName>${marker} Shared</sharedMetricSetName>
  <sharedMetricDataId>Status</sharedMetricDataId>
  <sharedMappings><sharedMapping fromLabel="Active" toLabel="Shared active"/></sharedMappings>
</indicatorTemplateXML>`;
}

/** Loopback stand-in for the shared dashboard's metric intake: records what the application posts. */
function startSendListener() {
  const received = [];
  const listener = { received, status: 200 };
  listener.server = http.createServer((req, res) => {
    const chunks = [];
    req.on('data', chunk => chunks.push(chunk));
    req.on('end', () => {
      received.push({ method: req.method, url: req.url, form: new URLSearchParams(Buffer.concat(chunks).toString('utf8')) });
      res.writeHead(listener.status, { 'Content-Type': 'text/plain' });
      res.end('accepted');
    });
  });
  listener.ready = new Promise((resolve, reject) => {
    listener.server.once('error', reject);
    listener.server.listen(SEND_PORT, '127.0.0.1', resolve);
  });
  listener.close = () => new Promise(resolve => listener.server.close(resolve));
  return listener;
}

async function waitFor(predicate, message, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error(message);
}

async function workflow(s) {
  const fixture = throwawayLoginFixture({ sql: s.sql, marker: s.marker, provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    if (!fixture.providerNo) return;
    const provider = h.sqlString(fixture.providerNo);
    const owned = h.sqlString(s.marker);
    s.sql.execute([
      `DELETE FROM indicatorTemplate WHERE name LIKE ${h.sqlString(s.marker + '%')}`,
      `DELETE FROM dashboard WHERE name=${owned}`,
      `DELETE FROM demographic WHERE last_name=${owned} AND provider_no=${provider}`,
      `DELETE FROM secObjPrivilege WHERE roleUserGroup=${provider}
        AND objectName IN (${GRANTS.map(g => h.sqlString(g[0])).join(',')})`,
    ].join(';'));
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${provider})
      + (SELECT COUNT(*) FROM indicatorTemplate WHERE name LIKE ${h.sqlString(s.marker + '%')})
      + (SELECT COUNT(*) FROM dashboard WHERE name=${owned})
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${owned})`) === '0',
      'Owned dashboard fixtures were not removed');
  });
  const listener = startSendListener();
  s.cleanup(() => listener.close());
  await listener.ready;
  fixture.create();
  const provider = h.sqlString(fixture.providerNo);
  for (const [object, right] of GRANTS) {
    s.sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${provider},${h.sqlString(object)},${h.sqlString(right)},0,${h.sqlString(s.provider)})`);
  }
  // Three owned patients: two Active, one not, so the published metric set is Active 2 / Not active 1.
  for (const [first, status] of [['Alpha', 'AC'], ['Bravo', 'AC'], ['Charlie', 'IN']]) {
    s.sql.execute(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(s.marker)},${h.sqlString(first)},'1980','01','02','F',${h.sqlString(status)},${provider},'ON','ON','NR',NOW())`);
  }
  const dashboardId = s.sql.value(`INSERT INTO dashboard (name,description,creator,edited,active,locked)
    VALUES (${h.sqlString(s.marker)},'Synthetic dashboard',${provider},NOW(),1,0); SELECT LAST_INSERT_ID()`);
  s.sql.execute(`INSERT INTO indicatorTemplate (dashboardId,name,category,subCategory,framework,frameworkVersion,
      definition,notes,template,active,locked,shared)
    VALUES (${dashboardId},${h.sqlString(s.marker + ' Shared status')},${h.sqlString(s.marker + ' Category')},
      ${h.sqlString(s.marker + ' Owned patients')},'CARLOS EMR','2026-01-01','Synthetic','Synthetic',
      ${h.sqlString(sharedIndicatorXml(s.marker))},1,0,1)`);

  const context = await h.newContext(s.context.browser(), s.config, { serviceWorkers: 'block' });
  context.on('page', page => h.wireStrictPage(page, 'shared-outcomes', s.recorder));
  let launchUrl;
  let launcher;
  let outcomesWindow;
  await context.route('**/*', async route => {
    const destination = new URL(route.request().url());
    if (destination.origin === s.config.baseUrl.origin) return route.continue();
    // A mistaken fixture URL must never reach a real dashboard or other outside service.
    if (destination.origin !== ORIGIN) return route.abort('blockedbyclient');
    if (!route.request().isNavigationRequest()) return route.fulfill({ status: 204, body: '' });
    launchUrl = destination;
    await route.fulfill({ contentType: 'text/html', body:
      '<!doctype html><title>Shared Outcomes test stub</title><p>Shared Outcomes test stub</p>' });
  });
  const schedule = await h.login(context, { ...s.config, testUser: fixture.username }, s.recorder);

  await s.step('Common Dashboard launches an encrypted provider contract without external calls', async () => {
    const link = schedule.locator('a[onclick*="/web/dashboard/display/sharedOutcomesDashboard"]').first();
    // The link lives in the schedule's Dashboard dropdown.
    await schedule.locator('#dashboardList').hover();
    const dashboardPromise = context.waitForEvent('page', {
      predicate: async page => { const opener = await page.opener(); return opener && opener !== schedule; },
      timeout: 20000,
    });
    const [launcherPage, dashboard] = await Promise.all([
      ui.clickOpensPopup(schedule, link, { context, recorder: s.recorder, label: 'shared-outcomes-launcher', timeout: 20000 }),
      dashboardPromise,
    ]);
    h.assert(dashboard, 'The dashboard popup did not open');
    launcher = launcherPage;
    outcomesWindow = dashboard;
    await dashboard.getByText('Shared Outcomes test stub', { exact: true }).waitFor();
    // Assert the launch contract. Cross-origin opener messaging is governed separately by COOP.
    h.assert(launchUrl && launchUrl.searchParams.get('version') === '1.1', 'The launch protocol version changed');
    h.assert([...launchUrl.searchParams.keys()].sort().join(',') === 'encodedParams,version',
      'The launch URL exposes unexpected plaintext parameters');

    // Spring Encryptors.stronger: PBKDF2-HMAC-SHA1 (1024), AES-256-GCM,
    // 16-byte IV followed by ciphertext and its 16-byte authentication tag.
    const bytes = Buffer.from(launchUrl.searchParams.get('encodedParams').replace(/ /g, '+'), 'base64');
    const key = pbkdf2Sync('carlos-5053-fixture', Buffer.from(SALT, 'hex'), 1024, 32, 'sha1');
    const decipher = createDecipheriv('aes-256-gcm', key, bytes.subarray(0, 16), { authTagLength: 16 });
    decipher.setAuthTag(bytes.subarray(-16));
    const payload = JSON.parse(Buffer.concat([decipher.update(bytes.subarray(16, -16)), decipher.final()]).toString('utf8'));
    h.assert(payload.user.role === 'provider', 'Dashboard role must be the external protocol value provider');
    h.assert(payload.user.username === fixture.providerNo, 'The launch identifies another user');
    h.assert(payload.clinic.identifier === 'TEST_CLINIC_5053' && payload.clinic.application === 'oscar',
      'The clinic identity contract changed');
  });

  // ---- The refresh path (#5335): the dashboard asks the launcher for a metric-set refresh, the launcher posts it
  // to the Struts route, and the application publishes the provider's indicator to the configured send URL.
  const metricName = `${s.marker} Shared`;
  const submitQuery = (marker, username = fixture.providerNo) => ({
    outcomes: { interaction: 'submitQuery', request: { username, queryList: [metricName] }, onSuccess: { refreshed: marker } },
  });
  const refreshUrl = new URL(`${s.config.baseUrl.pathname.replace(/\/$/, '')}${REFRESH_PATH}`, s.config.baseUrl.origin);
  // The stand-in dashboard records every message the launcher posts to it and answers CONNECT like the real one.
  const installDashboardListener = () => outcomesWindow.evaluate(() => {
    window.__fromLauncher = [];
    window.addEventListener('message', event => {
      window.__fromLauncher.push(event.data);
      if (event.data && event.data.response === 'CONNECT' && window.opener) {
        window.opener.postMessage({ outcomes: { interaction: 'CONNECTED' } }, '*');
      }
    });
    return !!window.opener;
  });
  const dashboardMessages = () => outcomesWindow.evaluate(() => window.__fromLauncher);
  const refreshRequests = [];
  const wireRefreshRequests = () => {
    launcher.on('response', async response => {
      const url = new URL(response.url());
      if (url.pathname.endsWith('/OutcomesDashboard')) {
        refreshRequests.push({ url, method: response.request().method(), status: response.status(),
          body: await response.text().catch(() => '') });
      }
    });
  };

  await s.step('the dashboard window keeps its opener, so it can ask the launcher to refresh', async () => {
    h.assert(launcher && outcomesWindow, 'The launch step did not leave both windows open');
    wireRefreshRequests();
    const opener = await installDashboardListener();
    // Site-wide COOP same-origin cuts window.opener for a popup on another origin (finding 77), which would
    // make every CONNECT/submitQuery message of this protocol undeliverable.
    h.assert(opener, 'The shared outcomes dashboard window has no window.opener: the site-wide '
      + 'Cross-Origin-Opener-Policy cuts the CONNECT/submitQuery channel, so a refresh can never reach the launcher');
    await waitFor(async () => (await dashboardMessages()).some(m => m && m.response === 'CONNECT'),
      'The launcher never sent CONNECT to the dashboard window');
  });

  await s.step('a refresh posts to the Struts route and publishes the owned metric set to the send URL', async () => {
    const before = listener.received.length;
    await outcomesWindow.evaluate(message => window.opener.postMessage(message, '*'), submitQuery('first'));
    await waitFor(() => refreshRequests.length === 1, `The launcher sent no request to ${refreshUrl.pathname}`);
    const [request] = refreshRequests;
    h.assert(request.url.pathname === refreshUrl.pathname && request.url.search === '',
      `The launcher called ${request.url.pathname}${request.url.search}, not ${refreshUrl.pathname}`);
    h.assert(request.method === 'POST', `The refresh was sent as ${request.method}, not POST`);
    h.assert(request.status === 200, `The refresh answered HTTP ${request.status}: ${request.body}`);
    h.assert(JSON.parse(request.body).published === 1, `The refresh did not report one published metric set: ${request.body}`);

    h.assert(listener.received.length === before + 1,
      `The send URL received ${listener.received.length - before} posts; expected one (is shared_outcomes_dashboard_send_url `
      + `http://127.0.0.1:${SEND_PORT}/send?)`);
    const post = listener.received[listener.received.length - 1];
    h.assert(post.method === 'POST' && post.url === '/send', `Unexpected delivery ${post.method} ${post.url}`);
    h.assert(post.form.get('version') === '1.1', 'The metric delivery protocol version changed');
    const data = JSON.parse(post.form.get('data'));
    h.assert(data.clinic.identifier === 'TEST_CLINIC_5053' && data.clinic.application === 'oscar', 'The clinic identity contract changed');
    h.assert(data.metricOwner.username === fixture.providerNo, 'The metric set names another provider');
    h.assert(data.metricSet.name === metricName, `The metric set is named ${data.metricSet.name}`);
    const points = Object.fromEntries(data.metricSet.metricData.map(d => [d.name[0].label, [d.name[0].identifier, d.value]]));
    h.assert(JSON.stringify(points) === JSON.stringify({ 'Shared active': ['Status', 2], 'Not active': ['Status', 1] }),
      `The published values/labels are ${JSON.stringify(points)}`);
    await waitFor(async () => (await dashboardMessages()).some(m => m && m.refreshed === 'first'),
      'The dashboard window was never told the refresh succeeded');
  });

  await s.step('a refresh the destination rejects answers 502 and is not reported to the dashboard as done', async () => {
    const before = listener.received.length;
    listener.status = 500;
    try {
      await outcomesWindow.evaluate(message => window.opener.postMessage(message, '*'), submitQuery('failed'));
      await waitFor(() => refreshRequests.length === 2, 'The second refresh request was never sent');
    } finally { listener.status = 200; }
    h.assert(refreshRequests[1].status === 502 && JSON.parse(refreshRequests[1].body).error === 'publish_failed',
      `A rejected delivery answered HTTP ${refreshRequests[1].status} ${refreshRequests[1].body}`);
    h.assert(listener.received.length > before, 'The rejected metric set never reached the send URL');
    await new Promise(resolve => setTimeout(resolve, 500));
    h.assert(!(await dashboardMessages()).some(m => m && m.refreshed === 'failed'),
      'The dashboard was told a failed refresh succeeded');
  });

  await s.step('a message from any other origin cannot trigger a refresh', async () => {
    const requests = refreshRequests.length;
    const posts = listener.received.length;
    // The launcher's own origin is not the dashboard's, so the page itself is the "other origin" here.
    await launcher.evaluate(message => window.postMessage(message, '*'), submitQuery('forged'));
    await new Promise(resolve => setTimeout(resolve, 1500));
    h.assert(refreshRequests.length === requests && listener.received.length === posts,
      'A message from a foreign origin triggered a refresh or a publication');
  });

  await s.step('direct requests: only an authenticated, CSRF-carrying POST publishes; the old URL is gone', async () => {
    const posts = listener.received.length;
    // The probes must run as the fixture login that owns the grants, not the suite's default account.
    const asFixture = { config: s.config, context };
    const owned = `name LIKE ${h.sqlString(s.marker + '%')}`;
    const rows = () => s.sql.value(`SELECT COUNT(*) FROM indicatorTemplate WHERE ${owned}`);
    const data = Buffer.from(JSON.stringify({ username: fixture.providerNo, queryList: [metricName] })).toString('base64');
    const get = await authenticatedProbe(asFixture, h.appUrl(s.config.baseUrl, REFRESH_PATH),
      { params: { data }, maxRedirects: 0, failOnStatusCode: false });
    await h.assertRefused(s, { response: get, table: 'indicatorTemplate', where: owned, before: rows(),
      label: `GET ${REFRESH_PATH}` });
    // A POST from the session's cookies alone, without CSRFGuard's token, must be refused by the CSRF filter.
    const cookie = (await context.cookies()).filter(c => c.name === 'JSESSIONID').map(c => `${c.name}=${c.value}`).join('; ');
    const noToken = await context.request.fetch(refreshUrl.href, { method: 'POST', form: { data },
      maxRedirects: 0, failOnStatusCode: false, headers: { Cookie: cookie } });
    await h.assertRefused(s, { response: noToken, table: 'indicatorTemplate', where: owned, before: rows(),
      label: `POST ${REFRESH_PATH} without a CSRF token` });
    const oldUrl = await authenticatedProbe(asFixture, h.appUrl(s.config.baseUrl, '/OutcomesDashboard'),
      { maxRedirects: 0, failOnStatusCode: false });
    h.assert(oldUrl.status() === 404, `The pre-#5335 URL /OutcomesDashboard answered HTTP ${oldUrl.status()}, not 404`);
    h.assert(listener.received.length === posts, 'A refused request still published a metric set');
  });
}

if (require.main === module) runWorkflow('shared-outcomes-launch', workflow, {
  openPatient: false,
  preflight() {
    if (process.env.SHARED_OUTCOMES_TEST_FIXTURE !== 'true') {
      throw new h.SkipCheck('Configure the documented synthetic Shared Outcomes properties on a disposable install');
    }
  },
});
module.exports = { workflow };
