#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Dashboard ▸ indicator ▸ Drill Down ▸ Export: the CSV a clinic hands to a quality program, against the rows SQL returns.
 * User path: Schedule ▸ Dashboard (#dashboardList) ▸ owned dashboard ▸ indicator "options" ▸ Drill Down ▸ Export
 * (web/dashboard/display/ExportResults).
 * dashboard-display proves the CSV equals the rows for three plain ASCII patients. Real patient data has accents,
 * quotes, line breaks and empty fields, and every one of those is a way for a hand-built CSV writer to lose data.
 *
 * Asserts: the header line is the template's export titles; the file ends with a newline; a patient whose first
 * name has an accent (UTF-8 is more bytes than characters) arrives complete, with the last row intact and the
 * Content-Length equal to the body; and, parsed as RFC 4180, every owned row equals the SQL row: a quote inside a name
 * without a comma, a line break inside an address, and an empty (NULL) e-mail each arrive as the stored value.
 * Fixtures: a throwaway login with the dashboard grants, three FAKE- patients (first names Zoë / Ro"b / Plain; Ro"b
 * has a two-line address and no e-mail), one dashboard and one indicator template carrying the marker; cleanup removes
 * all of it and asserts it. Reads only.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const x = require('./lib/export-content-helpers');

const TIMEOUT = 20000;
const GRANTS = [['_dashboardDisplay', 'x'], ['_dashboardDrilldown', 'x'], ['_dashboardManager', 'r'], ['_dashboardChgUser', 'r']];
const q = h.sqlString;

function templateXml(marker) {
  // Restricted to the run's own patients (last name = the marker): a legacy demographic row that happens to carry the
  // throwaway provider number must not appear in the drill-down or the CSV.
  const scope = `FROM demographic d WHERE d.provider_no = '\${provider}' AND d.last_name = '${marker}'`;
  const columns = [['demographic', 'd.demographic_no', 'Patient Id', true], ['firstName', 'd.first_name', 'First Name', false],
    ['lastName', 'd.last_name', 'Last Name', false], ['email', 'd.email', 'Email', false], ['address', 'd.address', 'Address', false]];
  const list = columns.map(([id, name, title, primary]) => `<column id="${id}" name="${name}" title="${title}" primary="${primary}" />`).join('\n      ');
  return `<?xml version="1.0" encoding="UTF-8"?>
<indicatorTemplateXML>
  <author>CARLOS EMR Playwright</author>
  <uid></uid>
  <heading>
    <category>${marker} Category</category>
    <subCategory>${marker} Owned patients</subCategory>
    <name>${marker} Patient list</name>
    <definition>${marker} patients of the logged-in provider</definition>
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
      ${list}
    </displayColumns>
    <exportColumns>
      ${list}
    </exportColumns>
    <query>SELECT d.* ${scope} ORDER BY d.demographic_no</query>
  </drillDownQuery>
</indicatorTemplateXML>`;
}

async function workflow(s) {
  const { sql, marker, recorder } = s;
  const M = q(marker);
  const scratch = x.scratchDir();
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  const patients = [];
  let grantsOwned = false; // set only after proving the throwaway role group had no privileges of its own
  let dashboardId;
  let indicatorId;
  s.cleanup(() => {
    if (!fixture.providerNo) return;
    const P = q(fixture.providerNo);
    const owned = patients.length ? patients.join(',') : '0';
    sql.execute([
      `DELETE FROM indicatorTemplate WHERE name LIKE ${q(`${marker}%`)}`,
      `DELETE FROM dashboard WHERE name=${M}`,
      `DELETE FROM demographicArchive WHERE demographic_no IN (${owned})`,
      `DELETE FROM demographic WHERE demographic_no IN (${owned}) AND last_name=${M}`,
    ].join(';'));
    if (grantsOwned) sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${P} AND objectName IN (${GRANTS.map(g => q(g[0])).join(',')})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM indicatorTemplate WHERE name LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM dashboard WHERE name=${M}) + ${grantsOwned ? `(SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${P})` : '0'}
      + (SELECT COUNT(*) FROM demographicArchive WHERE demographic_no IN (${owned}))
      + (SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${owned}))`) === '0', 'Owned dashboard fixtures were not all removed');
  });

  fixture.create();
  const P = q(fixture.providerNo);
  // The fixture allocator does not look at secObjPrivilege: never grant into, or later delete from, a role group that
  // already has rows (a legacy group named like the random throwaway provider number).
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${P}`) === '0',
    'The throwaway provider number is already a role group with privileges; run the check again');
  grantsOwned = true;
  for (const [object, right] of GRANTS) {
    sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${P},${q(object)},${q(right)},0,${q(s.provider)})`);
  }
  const rows = [
    ['Zoë', 'zoë@example.invalid', 'plain address'],
    ['Ro"b', null, 'line one\nline two'],
    ['Plain', null, 'single'],
  ];
  for (const [first, email, address] of rows) {
    const id = sql.value(`INSERT INTO demographic (last_name,first_name,email,address,year_of_birth,month_of_birth,date_of_birth,sex,
        patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${M},${q(first)},${email === null ? 'NULL' : q(email)},${q(address)},'1980','01','02','F','AC',${P},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A synthetic dashboard patient was not created');
    patients.push(id);
  }
  dashboardId = sql.value(`INSERT INTO dashboard (name,description,creator,edited,active,locked)
    VALUES (${M},'Synthetic dashboard',${P},NOW(),1,0); SELECT LAST_INSERT_ID()`);
  indicatorId = sql.value(`INSERT INTO indicatorTemplate (dashboardId,name,category,subCategory,framework,frameworkVersion,
      definition,notes,template,active,locked,shared)
    VALUES (${dashboardId},${q(`${marker} Patient list`)},${q(`${marker} Category`)},${q(`${marker} Owned patients`)},'CARLOS EMR','2026-01-01',
      'Synthetic','Synthetic',${q(templateXml(marker))},1,0,0); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(dashboardId) && /^[1-9]\d*$/.test(indicatorId), 'The dashboard fixture was not created');

  const ctx = await h.newContext(s.context.browser(), s.config);
  ctx.setDefaultTimeout(TIMEOUT);
  ctx.on('page', page => h.wireStrictPage(page, 'dashboard-csv-user', recorder));
  const schedule = await h.login(ctx, { ...s.config, testUser: fixture.username }, recorder, { label: 'dashboard-csv-schedule' });
  let dashboard;
  let file;

  await s.step('the Dashboard menu opens the owned dashboard and Drill Down lists the three patients', async () => {
    await schedule.locator('#dashboardList .dashboardBtn').hover();
    const link = schedule.locator('#dashboardList .dashboardDropdown a', { hasText: marker });
    h.assert(await link.count() === 1, 'The Dashboard menu does not list the owned dashboard exactly once');
    dashboard = await ui.clickOpensPopup(schedule, link, { context: ctx, recorder, label: 'dashboard-csv', timeout: TIMEOUT });
    await dashboard.waitForLoadState('load');
    await dashboard.locator(`#indicatorId_${indicatorId} .indicatorPanelContainer`).waitFor();
    await dashboard.locator(`#indicatorId_${indicatorId} .dropdown-toggle`).click();
    const [response] = await Promise.all([
      dashboard.waitForResponse(r => /\/web\/dashboard\/display\/DrilldownDisplay$/.test(new URL(r.url()).pathname)
        && r.request().method() === 'POST' && r.request().resourceType() === 'document'),
      dashboard.locator(`#getDrilldown_${indicatorId}`).click(),
    ]);
    h.assert(response.status() === 200, `Drill Down answered HTTP ${response.status()}`);
    await dashboard.waitForLoadState('load');
    await dashboard.locator('#drilldownTable tbody tr').first().waitFor();
    h.assert(await dashboard.locator('#drilldownTable tbody tr').count() === 3, 'The drilldown does not list the three owned patients');
  });

  await s.step('Export downloads a CSV that starts with the template titles', async () => {
    file = await x.saveDownload(dashboard, scratch, () => dashboard.locator(`#exportResults_${indicatorId}`).click(), { route: /\/ExportResults$/ });
    h.assert(file.status === 200 && /\.csv$/.test(file.name), 'Export did not answer a .csv attachment');
    h.assert(file.bytes.toString('utf8').split('\n')[0] === 'Patient Id,First Name,Last Name,Email,Address',
      'The CSV header line is not the template\'s export titles');
  });

  await s.step('the CSV is complete and equals the SQL rows: accents, a quote, a line break and empty values arrive as stored', async () => {
    const problems = [];
    const text = file.bytes.toString('utf8');
    const declared = Number(file.headers['content-length']);
    if (!text.endsWith('\n')) problems.push('the CSV does not end with a newline: its last row is cut off (setContentLength counts characters, the body is bytes)');
    if (Number.isFinite(declared) && declared !== file.bytes.length) problems.push(`Content-Length ${declared} differs from the ${file.bytes.length} bytes received`);
    const parsed = x.parseCsv(text).filter(r => r.some(c => c !== ''));
    const expected = sql.rows(`SELECT demographic_no, first_name, last_name, IFNULL(email,''), address FROM demographic
      WHERE provider_no=${P} AND last_name=${M} ORDER BY demographic_no`).map(r => r.map(c => (c === null ? '' : c)));
    const titles = parsed[0] || [];
    const kinds = new Set();
    expected.forEach((row, i) => {
      const got = parsed[i + 1] || [];
      row.forEach((value, c) => {
        if (got[c] === value) return;
        if (i === expected.length - 1 && !text.endsWith('\n')) return; // the cut-off row is reported above
        if (got[c] === 'null') kinds.add(`an empty ${titles[c]} is exported as the word null`);
        else if (/\n/.test(value)) kinds.add(`a line break inside ${titles[c]} breaks the row structure`);
        else if (/"/.test(value)) kinds.add(`a quote inside ${titles[c]} is doubled but the field is not quoted ("${got[c]}")`);
        else kinds.add(`${titles[c]} differs from the stored value`);
      });
    });
    if (parsed.length - 1 !== expected.length) problems.push(`the CSV parses to ${parsed.length - 1} data rows, SQL has ${expected.length}`);
    problems.push(...kinds);
    h.assert(!problems.length, `The dashboard CSV does not equal the stored rows: ${problems.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('export-content-dashboard-csv', workflow, { openPatient: false });
module.exports = { workflow };
