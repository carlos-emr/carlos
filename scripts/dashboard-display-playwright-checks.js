#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * User path: Schedule > Dashboard (#dashboardList) > <owned dashboard> > indicator
 * "options" > Indicator Info / Drill Down > Export, Actions > Assign Tickler and
 * Add To Disease Registry > Dashboard (back).
 *
 * Asserts the indicator panel's plotted counts (DisplayIndicator) equal the same
 * counts computed by SQL for the owned scope, the drilldown table (DrilldownDisplay)
 * lists exactly the owned patients with their stored status, the Export
 * (ExportResults) downloads CSV bytes equal to the SQL rows, the drilldown bulk
 * actions (AssignTickler, BulkPatientAction) write tickler/dxresearch rows only for
 * the checked owned patients, and the mutators refuse GET. Application defects met on the way are
 * recorded (their exact HTTP failure/console error consumed) and asserted together in the last
 * step, so every provable step is proven first; the check fails while any of them stands.
 *
 * Fixtures: no dashboard ships with the demo data and the Dashboard Manager the
 * Administration menu links to has no route, so the dashboard and one indicator
 * template are seeded by SQL. A throwaway login (lib/throwaway-login-fixture.js)
 * holds per-provider _dashboardDisplay/_dashboardDrilldown/_dashboardManager grants
 * (no role holds them on the demo data); its three FAKE- patients are the
 * indicator's whole "loggedInProvider" scope. Cleanup removes the grants, the
 * dashboard, the template, the ticklers/dxresearch/messages written for the owned
 * patients, the patients and the login, and asserts they are gone.
 *
 * Coverage plan: dashboard-display (Schedule top bar > Dashboard).
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const TIMEOUT = 20000;
const GRANTS = [['_dashboardDisplay', 'x'], ['_dashboardDrilldown', 'x'], ['_dashboardManager', 'r'], ['_dashboardChgUser', 'r']];
const DX_CODE = '250';

function templateXml(marker) {
  const scope = "FROM demographic d WHERE d.provider_no = '${provider}'";
  return `<?xml version="1.0" encoding="UTF-8"?>
<indicatorTemplateXML>
  <author>CARLOS EMR Playwright</author>
  <uid></uid>
  <heading>
    <category>${marker} Category</category>
    <subCategory>${marker} Owned patients</subCategory>
    <name>${marker} Patient status</name>
    <definition>${marker} patients of the logged-in provider by status</definition>
    <framework>CARLOS EMR</framework>
    <frameworkVersion>01-01-2026</frameworkVersion>
    <notes>Synthetic indicator owned by one check run</notes>
  </heading>
  <indicatorQuery>
    <version>01-01-2026</version>
    <params><parameter id="provider" name="provider_no" value="loggedInProvider" /></params>
    <query>SELECT SUM(IF(d.patient_status = 'AC', 1, 0)) AS "Active", SUM(IF(d.patient_status = 'AC', 0, 1)) AS "Not active" ${scope}</query>
  </indicatorQuery>
  <drillDownQuery>
    <version>01-01-2026</version>
    <params><parameter id="provider" name="provider_no" value="loggedInProvider" /></params>
    <displayColumns>
      <column id="demographic" name="d.demographic_no" title="Patient Id" primary="true" />
      <column id="lastName" name="d.last_name" title="Last Name" primary="false" />
      <column id="status" name="d.patient_status" title="Status" primary="false" />
    </displayColumns>
    <exportColumns>
      <column id="demographic" name="d.demographic_no" title="Patient Id" primary="true" />
      <column id="lastName" name="d.last_name" title="Last Name" primary="false" />
      <column id="status" name="d.patient_status" title="Status" primary="false" />
    </exportColumns>
    <drillDownActions>
      <action id="tickler" name="Assign Tickler" />
      <action id="dxUpdate" name="Add To Disease Registry" value="${DX_CODE}" />
    </drillDownActions>
    <query>SELECT d.* ${scope} ORDER BY d.demographic_no</query>
  </drillDownQuery>
</indicatorTemplateXML>`;
}

async function workflow(s) {
  const { sql, marker, recorder } = s;
  const M = h.sqlString(marker);
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  const patients = [];
  let dashboardId;
  let indicatorId;
  s.cleanup(() => {
    // fixture.create() failed before choosing a provider number: nothing below
    // was created, and an empty provider_no/roleUserGroup would match shared rows.
    if (!fixture.providerNo) return;
    const P = h.sqlString(fixture.providerNo);
    const owned = patients.length ? patients.join(',') : '0';
    const ownedMessages = `SELECT message FROM messagelisttbl WHERE provider_no=${P}`;
    // Child rows are verified by the parent ids captured before the parents are deleted;
    // a subquery through the deleted tickler/messagelisttbl rows would always count zero.
    const ticklerIds = sql.value(`SELECT GROUP_CONCAT(tickler_no) FROM tickler WHERE demographic_no IN (${owned})`) || '0';
    const messageIds = sql.value(`SELECT GROUP_CONCAT(message) FROM messagelisttbl WHERE provider_no=${P}`) || '0';
    sql.execute([
      `DELETE FROM tickler_update WHERE tickler_no IN (SELECT tickler_no FROM tickler WHERE demographic_no IN (${owned}))`,
      `DELETE FROM tickler_comments WHERE tickler_no IN (SELECT tickler_no FROM tickler WHERE demographic_no IN (${owned}))`,
      `DELETE FROM tickler_link WHERE tickler_no IN (SELECT tickler_no FROM tickler WHERE demographic_no IN (${owned}))`,
      `DELETE FROM tickler WHERE demographic_no IN (${owned})`,
      `DELETE FROM dxresearch WHERE demographic_no IN (${owned})`,
      `DELETE FROM msgDemoMap WHERE messageID IN (SELECT * FROM (${ownedMessages}) m)`,
      `DELETE FROM messagetbl WHERE messageid IN (SELECT * FROM (${ownedMessages}) m)`,
      `DELETE FROM messagelisttbl WHERE provider_no=${P}`,
      `DELETE FROM indicatorTemplate WHERE name LIKE ${h.sqlString(marker + '%')}`,
      `DELETE FROM dashboard WHERE name=${M}`,
      `DELETE FROM secObjPrivilege WHERE roleUserGroup=${P} AND objectName IN (${GRANTS.map(g => h.sqlString(g[0])).join(',')})`,
      `DELETE FROM demographicExt WHERE demographic_no IN (${owned})`,
      `DELETE FROM demographicArchive WHERE demographic_no IN (${owned})`,
      `DELETE FROM demographic WHERE demographic_no IN (${owned}) AND last_name=${M}`,
    ].join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler_update WHERE tickler_no IN (${ticklerIds}))
      + (SELECT COUNT(*) FROM tickler_comments WHERE tickler_no IN (${ticklerIds}))
      + (SELECT COUNT(*) FROM tickler_link WHERE tickler_no IN (${ticklerIds}))
      + (SELECT COUNT(*) FROM tickler WHERE demographic_no IN (${owned}) OR tickler_no IN (${ticklerIds}))
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${owned}))
      + (SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${messageIds}))
      + (SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${messageIds}))
      + (SELECT COUNT(*) FROM messagelisttbl WHERE provider_no=${P})
      + (SELECT COUNT(*) FROM indicatorTemplate WHERE name LIKE ${h.sqlString(marker + '%')})
      + (SELECT COUNT(*) FROM dashboard WHERE name=${M})
      + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${P})
      + (SELECT COUNT(*) FROM demographicExt WHERE demographic_no IN (${owned}))
      + (SELECT COUNT(*) FROM demographicArchive WHERE demographic_no IN (${owned}))
      + (SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${owned}))`) === '0',
    'Owned dashboard fixtures were not all removed');
  });

  fixture.create();
  const P = h.sqlString(fixture.providerNo);
  for (const [object, right] of GRANTS) {
    sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${P},${h.sqlString(object)},${h.sqlString(right)},0,${h.sqlString(s.provider)})`);
  }
  for (const [first, status] of [['Alpha', 'AC'], ['Bravo', 'AC'], ['Charlie', 'IN']]) {
    const id = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${M},${h.sqlString(first)},'1980','01','02','F',${h.sqlString(status)},${P},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A synthetic dashboard patient was not created');
    patients.push(id);
  }
  dashboardId = sql.value(`INSERT INTO dashboard (name,description,creator,edited,active,locked)
    VALUES (${M},'Synthetic dashboard',${P},NOW(),1,0); SELECT LAST_INSERT_ID()`);
  indicatorId = sql.value(`INSERT INTO indicatorTemplate (dashboardId,name,category,subCategory,framework,frameworkVersion,
      definition,notes,template,active,locked,shared)
    VALUES (${dashboardId},${h.sqlString(marker + ' Patient status')},${h.sqlString(marker + ' Category')},
      ${h.sqlString(marker + ' Owned patients')},'CARLOS EMR','2026-01-01','Synthetic','Synthetic',
      ${h.sqlString(templateXml(marker))},1,0,0); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(dashboardId) && /^[1-9]\d*$/.test(indicatorId), 'The dashboard fixture was not created');

  const ctx = await h.newContext(s.context.browser(), s.config);
  ctx.setDefaultTimeout(TIMEOUT);
  ctx.on('page', page => h.wireStrictPage(page, 'dashboard-user', recorder));
  const schedule = await h.login(ctx, { ...s.config, testUser: fixture.username }, recorder, { label: 'dashboard-schedule' });
  const [alpha, bravo, charlie] = patients;
  let dashboard;
  let plotted;
  let bulkToken;
  const missingPatient = '2147483647';
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${missingPatient}`) === '0',
    'The nonexistent-patient test ID is already in use');

  // Application defects are asserted in the LAST step so every provable step is proven first.
  const defects = [];
  function deferFailure(label, urlPattern, status, description) {
    const index = recorder.badResponses.findIndex(entry => entry.label === label && entry.status === status && urlPattern.test(entry.url));
    if (index < 0) return false;
    recorder.badResponses.splice(index, 1);
    for (let i = recorder.consoleIssues.length - 1; i >= 0; i--) {
      const entry = recorder.consoleIssues[i];
      if (entry.label === label && urlPattern.test(entry.location.url || '') && entry.text.includes(`status of ${status} (`)) {
        recorder.consoleIssues.splice(i, 1);
      }
    }
    if (description) defects.push(description);
    return true;
  }
  function deferConsole(label, pattern, description) {
    const matched = recorder.consoleIssues.filter(entry => entry.label === label && pattern.test(entry.text));
    if (!matched.length) return false;
    recorder.consoleIssues.splice(0, recorder.consoleIssues.length, ...recorder.consoleIssues.filter(entry => !matched.includes(entry)));
    if (description) defects.push(description);
    return true;
  }
  async function eventually(query, expected, timeout = 8000) {
    const deadline = Date.now() + timeout;
    do {
      if (sql.value(query) === expected) return true;
      await new Promise(resolve => setTimeout(resolve, 200));
    } while (Date.now() < deadline);
    return false;
  }
  async function openDashboard(label) {
    await schedule.locator('#dashboardList .dashboardBtn').hover();
    const link = schedule.locator('#dashboardList .dashboardDropdown a', { hasText: marker });
    h.assert(await link.count() === 1, 'The Dashboard menu does not list the owned dashboard exactly once');
    const page = await ui.clickOpensPopup(schedule, link, { context: ctx, recorder, label, timeout: TIMEOUT });
    await page.waitForLoadState('load');
    h.assert((await page.locator('.dashboardHeading h2').innerText()).trim() === marker, 'The dashboard window shows another dashboard');
    return page;
  }
  async function drillDown(page) {
    await page.locator(`#indicatorId_${indicatorId} .indicatorPanelContainer`).waitFor();
    await page.locator(`#indicatorId_${indicatorId} .dropdown-toggle`).click();
    const [response] = await Promise.all([
      page.waitForResponse(r => /\/web\/dashboard\/display\/DrilldownDisplay$/.test(new URL(r.url()).pathname)
        && r.request().method() === 'POST' && r.request().resourceType() === 'document'),
      page.locator(`#getDrilldown_${indicatorId}`).click(),
    ]);
    await page.waitForLoadState('load');
    return response;
  }
  const ownedRows = () => sql.rows(`SELECT demographic_no,last_name,patient_status FROM demographic WHERE provider_no=${P} ORDER BY demographic_no`);

  await s.step('the Dashboard menu lists the owned dashboard and opens it in a window', async () => {
    dashboard = await openDashboard('dashboard-display');
    h.assert(await dashboard.locator('.categoryPanel .card-header', { hasText: `${marker} Category` }).count() === 1,
      'The owned indicator category panel is missing');
  });

  await s.step('the indicator panel plots the owned-scope counts SQL computes', async () => {
    const panel = dashboard.locator(`#indicatorId_${indicatorId} .indicatorPanelContainer`);
    await panel.waitFor();
    h.assert((await panel.locator('.indicatorHeading').innerText()).trim() === `${marker} Patient status`, 'The indicator heading is wrong');
    plotted = await dashboard.locator(`#graphPlots_${indicatorId}`).inputValue();
    const plots = JSON.parse(plotted);
    const [[active, inactive]] = sql.rows(`SELECT SUM(patient_status='AC'),SUM(patient_status<>'AC') FROM demographic WHERE provider_no=${P}`);
    h.assert(JSON.stringify(plots) === JSON.stringify([[['Active', Number(active)], ['Not active', Number(inactive)]]]),
      'Indicator counts differ from the owned-scope SQL counts');
    h.assert(await dashboard.locator(`#graphCanvas_${indicatorId}`).evaluate(c => c.width > 0 && c.height > 0), 'The indicator chart was not drawn');
  });

  await s.step('Indicator Info shows the stored definition', async () => {
    await dashboard.locator(`#indicatorId_${indicatorId} .dropdown-toggle`).click();
    await dashboard.locator(`#indicatorId_${indicatorId} .dropdown-menu a`, { hasText: 'Indicator Info' }).click();
    const modal = dashboard.locator(`#indicatorInfo_${indicatorId}`);
    await modal.waitFor({ state: 'visible' });
    h.assert((await modal.locator('.modal-body').innerText()).includes(`${marker} patients of the logged-in provider by status`),
      'Indicator Info does not show the stored definition');
    await modal.locator('.modal-footer button', { hasText: 'Close' }).click();
    await modal.waitFor({ state: 'hidden' });
  });

  await s.step('Drill Down lists exactly the owned patients with their stored status', async () => {
    const response = await drillDown(dashboard);
    h.assert(response.status() === 200, `Drill Down answered HTTP ${response.status()}`);
    await dashboard.locator('#drilldownTable tbody tr').first().waitFor();
    const shown = await dashboard.locator('#drilldownTable tbody tr').evaluateAll(rows => rows.map(row =>
      [row.querySelector('input.patientChecked').id, row.cells[3].textContent.trim(), row.cells[4].textContent.trim()]));
    h.assert(JSON.stringify(shown.sort()) === JSON.stringify(ownedRows().sort()), 'The drilldown rows differ from the owned patients in SQL');
  });

  await s.step('Export downloads CSV bytes equal to the owned SQL rows', async () => {
    const outcome = await ui.clickDownloadsOrOpens(dashboard, dashboard.locator(`#exportResults_${indicatorId}`),
      { context: ctx, recorder, label: 'dashboard-export', timeout: TIMEOUT });
    h.assert(outcome.kind === 'download', 'Export did not download a file');
    const csv = fs.readFileSync(await outcome.download.path(), 'utf8');
    const expected = ['Patient Id,Last Name,Status', ...ownedRows().map(row => row.join(','))].join('\n') + '\n';
    h.assert(csv === expected, 'The exported CSV differs from the owned SQL rows');
    h.assert(/\.csv$/.test(outcome.download.suggestedFilename()), 'The export is not offered as a .csv file');
  });

  const ticklers = id => `SELECT COUNT(*) FROM tickler WHERE demographic_no=${id} AND message LIKE ${h.sqlString(`%${marker} recall%`)}
    AND status='A' AND priority='High' AND task_assigned_to=${P} AND creator=${P}`;
  let ticklerForm = null;
  await s.step('Actions > Assign Tickler requests the tickler form for exactly the checked owned patients', async () => {
    for (const id of [alpha, bravo]) await dashboard.locator(`input.patientChecked[id="${id}"]`).check();
    await dashboard.locator('#actionMenuLink').click();
    const [request] = await Promise.all([
      dashboard.waitForRequest(r => /\/web\/dashboard\/display\/AssignTickler$/.test(new URL(r.url()).pathname) && r.method() === 'POST'),
      dashboard.locator('#assignTicklerChecked').click(),
    ]);
    h.assert(new URLSearchParams(request.postData() || '').get('demographics') === `${alpha},${bravo}`,
      'The tickler form request does not carry exactly the checked patients');
    const modal = dashboard.locator('#assignTickler');
    await modal.waitFor({ state: 'visible' });
    const form = modal.locator('#ticklerAddForm');
    if (await form.waitFor({ timeout: 5000 }).then(() => true, () => false)) {
      ticklerForm = form;
      return;
    }
    // Each cause seen is recorded; at least one must explain the missing form.
    const doubled = deferFailure('dashboard-display', /\/carlos\/carlos\/web\/dashboard\/display\/AssignTickler/, 404,
      'Drilldown > Actions > Assign Tickler answers HTTP 404 and shows "Request failed": the link href already carries the context path and drilldownDisplayController.js sendData() prepends ctx again');
    if (doubled) deferConsole('dashboard-display', /^Drilldown request failed/, null);
    const purify = deferConsole('dashboard-display', /DOMPurify is required/,
      'Drilldown > Actions > Assign Tickler shows "Unable to display content safely": DrilldownDisplay.jsp does not load DOMPurify');
    h.assert(doubled || purify, 'The Assign Tickler dialog showed no tickler form');
    await modal.locator('.modal-footer button', { hasText: 'Close' }).click();
    await modal.waitFor({ state: 'hidden' });
  });

  if (ticklerForm) {
    await s.step('Assign Tickler saves one tickler per checked owned patient and none for the unchecked one', async () => {
      const form = ticklerForm;
      h.assert(await form.locator('input[name="demographics"]').inputValue() === `${alpha},${bravo}`,
        'The tickler form does not carry exactly the checked patients');
      await form.locator('select[name="ticklerCategoryId"]').selectOption({ index: 0 });
      await form.locator('select[name="taskAssignedTo"]').selectOption(fixture.providerNo);
      await form.locator('select[name="priority"]').selectOption('High');
      await form.locator('input[name="serviceDate"]').fill('12-31-2030');
      await form.locator('input[name="serviceTime"]').fill('10:30 AM');
      await form.locator('textarea[name="messageAppend"]').fill(`${marker} recall`);
      const [response] = await Promise.all([
        dashboard.waitForResponse(r => /\/web\/dashboard\/display\/AssignTickler$/.test(new URL(r.url()).pathname) && r.request().method() === 'POST'),
        dashboard.locator('#saveTicklerBtn').click(),
      ]);
      const request = response.request();
      h.assert(Boolean((await request.allHeaders())['csrf-token']
        || new URLSearchParams(request.postData() || '').get('CSRF-TOKEN')), 'The successful tickler save carried no CSRF token');
      h.assert((await response.json()).success === 'true', 'Assign Tickler did not acknowledge the save');
      const submitted = new URLSearchParams(request.postData() || '');
      h.assert(Boolean(submitted.get('ticklerSubmission')), 'The save has no server-issued operation key');
      const replay = async (body) => ctx.request.post(request.url(), {
        data: body.toString(), headers: {'Content-Type': 'application/x-www-form-urlencoded',
          'CSRF-TOKEN': (await request.allHeaders())['csrf-token'] || submitted.get('CSRF-TOKEN')},
      });
      // Repeat the exact operation concurrently as copied tabs/lost-response retries would.
      const retries = await Promise.all([replay(submitted), replay(submitted)]);
      for (const retry of retries) {
        h.assert(retry.status() === 200 && (await retry.json()).success === 'true', 'An identical retry lost its cached success');
        await retry.dispose();
      }
      const changed = new URLSearchParams(submitted);
      changed.set('messageAppend', `${marker} altered retry`);
      const refused = await replay(changed);
      h.assert(refused.status() === 409, 'An operation key accepted a changed tickler payload');
      await refused.dispose();

      for (const id of [alpha, bravo]) await expectValue(sql, ticklers(id), '1', 'A checked patient did not receive exactly one tickler');
      h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${charlie}`) === '0', 'The unchecked patient received a tickler');
      h.assert(sql.value(`SELECT DATE_FORMAT(service_date,'%Y-%m-%d %H:%i') FROM tickler WHERE demographic_no=${alpha}`) === '2030-12-31 10:30',
        'The tickler service date/time was not stored as entered');
    });
  }

  const dxRows = id => `SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${id} AND dxresearch_code='${DX_CODE}'
    AND coding_system='icd9' AND status='A' AND providerNo=${P}`;
  await s.step('Add To Disease Registry confirms ICD9 250 and its stored description; the unchecked patient stays unregistered', async () => {
    await dashboard.locator('#actionMenuLink').click();
    await dashboard.locator('#addToDiseaseRegistryChecked').click();
    const modal = dashboard.locator('#modalConfirmAddToDiseaseRegistry');
    await modal.waitFor({ state: 'visible' });
    await modal.locator('#icd9code').filter({ hasText: DX_CODE }).waitFor();
    h.assert((await modal.locator('#icd9description').innerText()).trim()
      === sql.value(`SELECT description FROM icd9 WHERE icd9='${DX_CODE}'`), 'The confirmation shows the wrong ICD9 description');
    const [registryRequest] = await Promise.all([
      dashboard.waitForRequest(r => r.method() === 'POST' && /BulkPatientAction$/.test(new URL(r.url()).pathname)),
      modal.locator('#confirmAddToDiseaseRegistry').click(),
    ]);
    h.assert(Boolean((await registryRequest.allHeaders())['csrf-token']
      || new URLSearchParams(registryRequest.postData() || '').get('CSRF-TOKEN')), 'The registry confirmation carried no CSRF token');
    if (!await eventually(`SELECT (${dxRows(alpha)})=1 AND (${dxRows(bravo)})=1`, '1')) {
      const misrouted = deferFailure('dashboard-display', /\/web\/dashboard\/display\/DrilldownDisplay$/, 500, null);
      const counts = [alpha, bravo].map(id => sql.value(dxRows(id)));
      defects.push(misrouted && counts.every(count => count === '0')
        ? 'Drilldown > Actions > Add To Disease Registry > Confirm writes no dxresearch rows: drilldownDisplayController.js posts to $(this).attr("href"), which the <button> lacks, so the XHR goes to DrilldownDisplay (HTTP 500)'
        : `Drilldown > Actions > Add To Disease Registry > Confirm did not write exactly one ICD9 ${DX_CODE} row per checked patient (found ${counts.join(' and ')})`);
      if (await modal.isVisible()) await modal.locator('.modal-footer button', { hasText: 'Cancel' }).click();
      await modal.waitFor({ state: 'hidden' });
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${charlie}`) === '0', 'The unchecked patient was registered');
  });

  await s.step('Concurrent registry and exclusion requests create one current entry and audit only inserted diagnoses', async () => {
    const token = await dashboard.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    h.assert(token.length > 0, 'The drilldown has no CSRF token for the concurrency check');
    bulkToken = token;
    const post = async form => {
      const response = await ctx.request.post(h.appUrl(s.config.baseUrl, '/web/dashboard/display/BulkPatientAction'),
        {form: {'CSRF-TOKEN': token, ...form}, maxRedirects: 0});
      h.assert(response.status() === 200, `Concurrent ${form.method} returned HTTP ${response.status()}`);
      await response.dispose();
    };
    // Remove only the synthetic diagnosis created above, then race two fresh additions.
    sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${alpha} AND dxresearch_code='${DX_CODE}'
      AND coding_system='icd9' AND providerNo=${P}`);
    const audits = `SELECT COUNT(*) FROM log WHERE provider_no=${P} AND action='add' AND content='DX'`;
    const before = Number(sql.value(audits));
    const diagnosis = {method: 'addToDiseaseRegistry', patientIds: `${missingPatient},${alpha}`, dxUpdateICD9Code: DX_CODE};
    await Promise.all([post(diagnosis), post(diagnosis)]);
    h.assert(sql.value(dxRows(alpha)) === '1', 'Concurrent requests created duplicate active diagnoses');
    h.assert(await eventually(audits, String(before + 1)), 'The inserted diagnosis has no unique ADD audit entry');
    await post(diagnosis);
    h.assert(sql.value(audits) === String(before + 1), 'Skipping an existing diagnosis created a false ADD audit entry');
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE provider_no=${P} AND action='add' AND content='DX' AND contentId='null'`) === '0',
      'A skipped diagnosis was audited with a null content ID');
    const messages = `SELECT COUNT(*) FROM messagelisttbl WHERE provider_no=${P}`;
    const messagesBefore = Number(sql.value(messages));
    const exclusion = {method: 'excludePatients', patientIds: `${missingPatient},${alpha}`, indicatorId};
    await Promise.all([post(exclusion), post(exclusion)]);
    await post(exclusion);
    const identifier = `${marker} Patient status|${marker} Owned patients|${marker} Category`;
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${alpha} AND provider_no=${P}
      AND key_val='excludeIndicator' AND value=${h.sqlString(identifier)}`) === '1',
      'Repeated or concurrent exclusions created duplicate current rows');
    h.assert(sql.value(messages) === String(messagesBefore + 1),
      'Skipped exclusions sent a false success notification');
  });

  await s.step('the Dashboard button returns to the dashboard with the same counts', async () => {
    await Promise.all([dashboard.waitForURL(/DashboardDisplay/), dashboard.locator('.backtoDashboardBtn').click()]);
    await dashboard.locator(`#indicatorId_${indicatorId} .indicatorPanelContainer`).waitFor();
    h.assert((await dashboard.locator('.dashboardHeading h2').innerText()).trim() === marker, 'Back did not return to the owned dashboard');
    h.assert(await dashboard.locator(`#graphPlots_${indicatorId}`).inputValue() === plotted, 'The reloaded dashboard plots different counts');
  });

  await s.step('A partial inactive update reports failure and audits only the patient it changed', async () => {
    const audits = id => `SELECT COUNT(*) FROM log WHERE provider_no=${P} AND action='update'
      AND demographic_no=${id} AND data='patient_status: IN'`;
    const before = Number(sql.value(audits(alpha)));
    const messages = `SELECT COUNT(*) FROM messagelisttbl WHERE provider_no=${P}`;
    const messagesBefore = Number(sql.value(messages));
    const response = await ctx.request.post(h.appUrl(s.config.baseUrl, '/web/dashboard/display/BulkPatientAction'),
      {form: {'CSRF-TOKEN': bulkToken, method: 'setPatientsInactive', patientIds: `${missingPatient},${alpha}`}, maxRedirects: 0});
    h.assert(response.status() === 400, `Partial inactive update answered HTTP ${response.status()}, expected 400`);
    await response.dispose();
    h.assert(sql.value(`SELECT patient_status FROM demographic WHERE demographic_no=${alpha}`) === 'IN',
      'The valid patient after the missing ID was not updated');
    h.assert(await eventually(audits(alpha), String(before + 1)), 'The successful inactive update has no audit entry');
    h.assert(sql.value(audits(missingPatient)) === '0', 'The nonexistent patient has a false update audit');
    h.assert(sql.value(messages) === String(messagesBefore + 1), 'The successful subset was not notified once');
  });

  await s.step('AssignTickler refuses a GET save with 405 and writes no tickler', async () => {
    const response = await ctx.request.get(h.appUrl(s.config.baseUrl, `/web/dashboard/display/AssignTickler?method=saveTickler`
      + `&demographics=${charlie}&ticklerCategoryId=1&taskAssignedTo=${fixture.providerNo}&priority=High&serviceDate=12-31-2030`
      + `&serviceTime=10:30%20AM&message=&messageAppend=${encodeURIComponent(marker)}%20recall`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `GET AssignTickler saveTickler answered HTTP ${response.status()}, expected 405`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${charlie}`) === '0', 'A GET save created a tickler');
  });

  await s.step('Missing-CSRF POSTs are refused before tickler or bulk patient mutations', async () => {
    const snapshot = () => JSON.stringify([ownedRows(), sql.rows(`SELECT
      (SELECT COUNT(*) FROM tickler WHERE demographic_no IN (${patients.join(',')})),
      (SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${patients.join(',')})),
      (SELECT COUNT(*) FROM demographicExt WHERE demographic_no IN (${patients.join(',')}))`)]);
    const before = snapshot();
    const requests = [
      ['BulkPatientAction', {method: 'addToDiseaseRegistry', patientIds: charlie, dxUpdateICD9Code: DX_CODE}],
      ['BulkPatientAction', {method: 'excludePatients', patientIds: charlie, indicatorId}],
      ['BulkPatientAction', {method: 'setPatientsInactive', patientIds: alpha}],
      ['AssignTickler', {method: 'saveTickler', demographics: charlie, ticklerCategoryId: '1',
        taskAssignedTo: fixture.providerNo, priority: 'High', serviceDate: '12-31-2030',
        serviceTime: '10:30 AM', messageAppend: `${marker} recall`}],
    ];
    for (const [route, form] of requests) {
      const response = await ctx.request.post(h.appUrl(s.config.baseUrl, `/web/dashboard/display/${route}`),
        {form, maxRedirects: 0});
      h.assert(response.status() === 403, `Missing-CSRF ${form.method} answered HTTP ${response.status()}`);
      await response.dispose();
      h.assert(snapshot() === before, `Missing-CSRF ${form.method} changed owned patient data`);
    }
  });

  await s.step('no deferred dashboard defect remains (plain-user drill down, GET-refusing BulkPatientAction)', async () => {
    const dx = await ctx.request.get(h.appUrl(s.config.baseUrl,
      `/web/dashboard/display/BulkPatientAction?method=addToDiseaseRegistry&dxUpdateICD9Code=${DX_CODE}&patientIds=${charlie}`), { maxRedirects: 0 });
    const written = sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${charlie}`);
    if (dx.status() !== 405 || written !== '0') {
      defects.push(`GET web/dashboard/display/BulkPatientAction?method=addToDiseaseRegistry answered HTTP ${dx.status()} and wrote ${written} dxresearch row(s); a mutator must refuse GET with 405 and write nothing`);
    }
    await dashboard.close();
    sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${P} AND objectName='_dashboardChgUser'`);
    const plain = await openDashboard('dashboard-plain');
    h.assert(await plain.locator('#providerNo').count() === 0, 'The provider switcher is still offered without _dashboardChgUser');
    const response = await drillDown(plain);
    if (response.status() === 403) {
      h.assert(deferFailure('dashboard-plain', /DrilldownDisplay/, 403,
        'Dashboard > options > Drill Down answers HTTP 403 (CSRF token missing) for a user without _dashboardChgUser: DashboardDisplay.jsp renders no form, so sendData() finds no CSRF-TOKEN input'),
      'The refused drill down was not recorded');
    } else {
      h.assert(response.status() === 200, `Drill Down answered HTTP ${response.status()}`);
      await plain.locator('#drilldownTable tbody tr').first().waitFor();
    }
    h.assert(defects.length === 0, `Application defects: ${defects.join(' | ')}`);
  });
}

if (require.main === module) runWorkflow('dashboard-display', workflow, { openPatient: false });
module.exports = { workflow };
