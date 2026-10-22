#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Query by Example least privilege (issue #3372). User path: Schedule ▸ Administration ▸ Reports ▸
 * Query By Example (the /administration shell loads oscarReport/RptByExample into #myFrame).
 * Proves that the database, not only the application validator, keeps prohibited data out of a
 * report on a real deployment. Queries run as the read-only carlos_report account, which MariaDB
 * grants SELECT only on the tables carlos_report_grants.sql lists as reportable.
 * Asserts:
 *  - the page opens without the "reporting account unavailable" warning;
 *  - a reportable table still answers, and the rows match what MariaDB returns;
 *  - a table created after the account was provisioned, which neither the grant list nor the
 *    application's restricted-table list knows, is refused by the database: the access-denied
 *    alert, no result table, the planted secret nowhere in the page, no history row;
 *  - every hidden column the grant list names (casemgmt_note.password,
 *    DigitalSignature.signatureImage) is refused by name and through SELECT *, while the table's
 *    other columns still answer;
 *  - in MariaDB: the reporting account exists and holds SELECT only, on no restricted table, no
 *    hidden column and no table with hidden columns as a whole, with no schema-wide, global or
 *    role grant.
 * Fixtures: the session's patient; one throwaway table named for the run (dropped in cleanup,
 * which asserts it is gone) and the history rows carrying the marker.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 20000;
const REPORT_USER = 'carlos_report';
const DENIED_KEY = 'oscarReport.RptByExample.MsgAccessDenied';
const DENIED_FALLBACK = 'The database refused this query: it reads a table or column that is not available for '
  + 'reporting, such as login credentials, keys or system settings. If the query uses *, name the columns you need instead.';
// The grant list, from the source tree or, on a packaged install, from the deployed webapp.
const GRANT_LISTS = [
  path.join(__dirname, '..', 'src', 'main', 'resources', 'db', 'reporting', 'carlos_report_grants.sql'),
  '/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/db/reporting/carlos_report_grants.sql',
];
const ROW = /^\('([A-Za-z0-9_]+)', '(reportable|restricted)', (?:NULL|'([A-Za-z0-9_,]+)'), (?:NULL|'[^']+')\)[,;]$/;

function bundleMessage(key, fallback) {
  try {
    const bundle = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'oscarResources_en.properties'), 'utf8');
    const line = bundle.split('\n').find(candidate => candidate.startsWith(`${key}=`));
    return line ? line.slice(key.length + 1).trim() : fallback;
  } catch (error) { return fallback; }
}

/** {restricted: [table], hidden: [[table, column]]} from the first grant list that exists. */
function readGrantList() {
  const file = GRANT_LISTS.find(candidate => fs.existsSync(candidate));
  h.assert(file, `No Query by Example grant list found (looked in ${GRANT_LISTS.join(', ')})`);
  const restricted = [];
  const hidden = [];
  for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
    const row = ROW.exec(line);
    if (!row) continue;
    if (row[2] === 'restricted') restricted.push(row[1]);
    else if (row[3]) for (const column of row[3].split(',')) hidden.push([row[1], column]);
  }
  const hides = (table, column) => hidden.some(([t, c]) => t === table && c === column);
  h.assert(restricted.includes('security') && hides('casemgmt_note', 'password') && hides('DigitalSignature', 'signatureImage'),
    `The grant list ${file} did not parse into the expected restricted tables and hidden columns`);
  return {restricted, hidden};
}

async function clickAndNavigate(page, frame, locator) {
  const navigated = page.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: TIMEOUT});
  navigated.catch(() => {});
  await locator.click();
  await navigated;
  await frame.waitForLoadState('networkidle', {timeout: TIMEOUT});
}

async function workflow(s) {
  const {sql, provider, patient, marker, context} = s;
  const providerLiteral = h.sqlString(provider);
  const deniedText = bundleMessage(DENIED_KEY, DENIED_FALLBACK);
  const {restricted, hidden} = readGrantList();
  // Lower-case and hyphen-free, so the query needs no quoting the validator could read differently.
  const table = `qbe_unclassified_${marker.replace(/^FAKE-PW/, '').toLowerCase()}`;
  const secret = `${marker}-SECRET-API-TOKEN`;
  const historyCount = () => sql.value(`SELECT COUNT(*) FROM reportByExamples WHERE providerNo=${providerLiteral}
    AND (query LIKE ${h.sqlString('%' + marker + '%')} OR query LIKE ${h.sqlString('%' + table + '%')})`);

  s.cleanup(() => {
    sql.execute(`DROP TABLE IF EXISTS \`${table}\``);
    h.assert(sql.value(`SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=${h.sqlString(table)}`) === '0',
      'The throwaway table was not dropped');
    sql.execute(`DELETE FROM reportByExamples WHERE providerNo=${providerLiteral}
      AND (query LIKE ${h.sqlString('%' + marker + '%')} OR query LIKE ${h.sqlString('%' + table + '%')})`);
    h.assert(historyCount() === '0', 'Owned query history rows were not removed');
  });

  await s.step('MariaDB: the reporting account holds SELECT on reportable tables only', async () => {
    h.assert(Number(sql.value(`SELECT COUNT(*) FROM mysql.global_priv WHERE User=${h.sqlString(REPORT_USER)}`)) >= 1,
      `The ${REPORT_USER} account does not exist; provision it (carlos-ctl db-users, or qbe-reporting-account in the devcontainer)`);
    // Lower-cased on both sides: a server with lower_case_table_names stores grants that way.
    const lowerList = names => names.map(name => h.sqlString(name.toLowerCase())).join(',');
    h.assert(sql.value(`SELECT COUNT(*) FROM mysql.tables_priv WHERE User=${h.sqlString(REPORT_USER)}
      AND LOWER(Table_name) IN (${lowerList(restricted)})`) === '0', `The ${REPORT_USER} account is granted a restricted table`);
    for (const [hiddenTable, column] of hidden) {
      h.assert(sql.value(`SELECT COUNT(*) FROM mysql.columns_priv WHERE User=${h.sqlString(REPORT_USER)}
        AND LOWER(Table_name)=${h.sqlString(hiddenTable.toLowerCase())} AND Column_name=${h.sqlString(column)}`) === '0',
      `The ${REPORT_USER} account is granted the hidden column ${hiddenTable}.${column}`);
    }
    // A table-wide SELECT on a table with hidden columns reads them too; those tables may only be
    // granted column by column.
    h.assert(sql.value(`SELECT COUNT(*) FROM mysql.tables_priv WHERE User=${h.sqlString(REPORT_USER)}
      AND LOWER(Table_name) IN (${lowerList(hidden.map(([hiddenTable]) => hiddenTable))}) AND Table_priv<>''`) === '0',
    `The ${REPORT_USER} account is granted every column of a table that has hidden columns`);
    h.assert(sql.value(`SELECT COUNT(*) FROM mysql.tables_priv WHERE User=${h.sqlString(REPORT_USER)}
      AND (Table_priv NOT IN ('', 'Select') OR Column_priv NOT IN ('', 'Select') OR Db<>DATABASE())`) === '0',
    `The ${REPORT_USER} account holds a table privilege other than SELECT on this schema`);
    h.assert(sql.value(`SELECT COUNT(*) FROM mysql.db WHERE User=${h.sqlString(REPORT_USER)}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM mysql.roles_mapping WHERE User=${h.sqlString(REPORT_USER)}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM mysql.global_priv WHERE User=${h.sqlString(REPORT_USER)}
        AND COALESCE(JSON_VALUE(Priv, '$.access'), 0) <> 0`) === '0',
    `The ${REPORT_USER} account holds a schema-wide, global or role privilege`);
    h.assert(Number(sql.value(`SELECT COUNT(*) FROM mysql.tables_priv WHERE User=${h.sqlString(REPORT_USER)} AND Db=DATABASE()
      AND Table_name='demographic'`)) >= 1, `The ${REPORT_USER} account cannot read demographic; the grant list was never applied`);
  });

  // Created after the grants were applied and listed nowhere: what a future migration's table is
  // until someone classifies it.
  sql.execute(`CREATE TABLE \`${table}\` (id INT PRIMARY KEY, api_token VARCHAR(80))`);
  sql.execute(`INSERT INTO \`${table}\` VALUES (1, ${h.sqlString(secret)})`);

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder: s.recorder, label: 'qbe-least-privilege-administration', timeout: TIMEOUT});
  const link = admin.getByRole('link', {name: 'Query By Example', exact: true, includeHidden: true});
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The Query By Example frame did not load');
  await frame.locator('#queryForm').waitFor({timeout: TIMEOUT});
  const runQuery = async text => {
    await frame.locator('#sql').fill(text);
    await clickAndNavigate(admin, frame, frame.locator('#queryForm button[type="submit"]'));
  };
  const visibleAlerts = () => frame.locator('.alert[role="alert"]:visible');
  const assertDenied = async (label, before) => {
    const alerts = visibleAlerts();
    h.assert(await alerts.count() === 1 && await frame.locator('#queryAccessDenied').isVisible()
      && (await alerts.first().innerText()).trim() === deniedText,
    `${label}: the access-denied message was not the only alert; the database may have answered`);
    h.assert(await frame.locator('table#results').count() === 0, `${label}: a result table was rendered`);
    h.assert(!(await frame.content()).includes(secret), `${label}: the planted secret reached the page`);
    h.assert(historyCount() === before, `${label}: a refused query was recorded in history`);
  };

  await s.step('the page opens without the reporting-account warning', async () => {
    h.assert(await frame.locator('#queryReportingUnavailable').count() === 0,
      'Query by Example says its reporting account is unavailable');
  });

  await s.step('a reportable table answers through the reporting account', async () => {
    const query = `SELECT demographic_no, last_name FROM demographic WHERE demographic_no=${patient}
      AND last_name=${h.sqlString(marker)}`;
    await runQuery(query);
    h.assert(await visibleAlerts().count() === 0, 'A reportable query rendered an alert');
    const rows = await frame.locator('table#results tr:has(td)').evaluateAll(trs => trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent)));
    h.assert(JSON.stringify(rows) === JSON.stringify(sql.rows(query)), 'Rendered rows differ from what MariaDB returns');
    h.assert(rows.length === 1 && rows[0][1] === marker, 'The reportable query did not return the owned patient');
  });

  await s.step('a table nobody classified is refused by the database, not shown', async () => {
    const before = historyCount();
    await runQuery(`SELECT * FROM ${table}`);
    await assertDenied('unclassified table', before);
    await runQuery(`SELECT d.last_name FROM demographic d WHERE d.demographic_no=${patient}
      AND EXISTS (SELECT 1 FROM ${table} t WHERE t.api_token IS NOT NULL)`);
    await assertDenied('unclassified table in a subquery', before);
  });

  await s.step('every hidden column is refused by name and through *, the other columns still answer', async () => {
    const before = historyCount();
    for (const [hiddenTable, column] of hidden) {
      await runQuery(`SELECT ${column} FROM ${hiddenTable} WHERE 1=0`);
      await assertDenied(`hidden column ${hiddenTable}.${column} by name`, before);
    }
    for (const hiddenTable of new Set(hidden.map(([name]) => name))) {
      await runQuery(`SELECT * FROM ${hiddenTable} WHERE 1=0`);
      await assertDenied(`hidden columns of ${hiddenTable} through *`, before);
    }
    // The marker literal ties the history row this successful run writes to the cleanup.
    await runQuery(`SELECT note_id, observation_date, ${h.sqlString(marker)} AS run
      FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(await visibleAlerts().count() === 0, 'Named, granted casemgmt_note columns were refused');
  });
}
if (require.main === module) runWorkflow('report-query-by-example-least-privilege', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
