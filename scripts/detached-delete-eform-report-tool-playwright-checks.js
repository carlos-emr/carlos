#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * EForm Report Tool remove (detached-delete risk sweep, issue #4129).
 * User path: Schedule > Administration > "EForm Report Tool" (admin/ViewEformReportTool in the
 *   #dynamic-content iframe) > Add New > Name + Choose EForm > Save (ws/rs/reporting/
 *   eformReportTool/add); then the row's "(Remove)" > confirm (ws/rs/reporting/eformReportTool/
 *   remove), once dismissed and once accepted.
 * Asserts: Save stores exactly one EFormReportTool row for the chosen eForm and creates its ERT_
 *   report table; the refreshed list shows it (fails on 2026.08: eformReportTool/list answers 500,
 *   ClassCastException Long->BigInteger at EFormReportToolDaoImpl.getNumRecords:192, so the Remove
 *   steps after it cannot run until that is fixed); a dismissed confirm changes nothing; an accepted confirm
 *   asks once, deletes exactly that row, drops exactly that report table, and the list stops
 *   showing it. The manager finds the row and the DAO drops the table and removes the row by id
 *   (remove-by-id, the safe shape of the detached-delete pattern); a regression to removing the
 *   entity the manager found would fail the last step.
 * Fixtures: the report tool row and its ERT_FAKEPW<hex>... table are created through the UI (the
 *   name must be [A-Za-z0-9_], so it carries the run's hex rather than the FAKE- marker). Cleanup
 *   drops any table and deletes any row carrying that name and asserts both are gone.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, marker } = s;
  const name = `FAKEPW${marker.slice(-14)}`;
  h.assert(/^[A-Za-z0-9_]+$/.test(name), 'The report tool name must be a plain identifier');
  const tablePattern = h.sqlString(`ERT\\_${name}%`);
  const ownedTables = () => sql.rows(`SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name LIKE ${tablePattern} ORDER BY table_name`).map(row => row[0]);
  const ownedRows = () => sql.rows(`SELECT id, tableName, eformId FROM EFormReportTool WHERE name=${h.sqlString(name)} ORDER BY id`);

  // A step that times out on the add response does not cancel the request the page already sent:
  // the server can still create the row and its table afterwards. Remember that the add left the
  // browser, so cleanup can wait for that late write instead of checking an instant too early.
  let addSent = false;
  s.context.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/ws/rs/reporting/eformReportTool/add')) addSent = true;
  });
  const removeOwned = () => {
    for (const table of ownedTables()) {
      // name is validated against an alphanumeric/underscore allowlist above.
      // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
      h.assert(new RegExp(`^ERT_${name}[0-7]*$`).test(table), 'Refusing to drop a table this check does not own');
      sql.execute(`DROP TABLE IF EXISTS \`${table}\``);
    }
    sql.execute(`DELETE FROM EFormReportTool WHERE name=${h.sqlString(name)}`);
  };
  s.cleanup(async () => {
    if (addSent) {
      // Reconcile the uncertain outcome: wait until the row and its table both exist (the add
      // finished) or the window closes, then remove; a final pass catches anything created since.
      const deadline = Date.now() + 15000;
      while (Date.now() < deadline && !(ownedRows().length > 0 && ownedTables().length > 0)) {
        await new Promise(resolve => setTimeout(resolve, 500));
      }
    }
    removeOwned();
    if (addSent) {
      await new Promise(resolve => setTimeout(resolve, 2000));
      removeOwned();
    }
    h.assert(ownedTables().length === 0 && ownedRows().length === 0, 'Owned report tool rows or tables were not removed');
  });
  h.assert(ownedTables().length === 0 && ownedRows().length === 0, 'A report tool with the owned name already exists');

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'eform-report-tool-administration', timeout: TIMEOUT });
  const link = admin.getByRole('link', { name: 'EForm Report Tool', exact: true, includeHidden: true }).first();
  if (await link.count() === 0) throw new h.SkipCheck('Administration offers no EForm Report Tool link to this login (_admin.eformreporttool)');
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The EForm Report Tool iframe did not load');
  await frame.locator('#btnAdd').waitFor();
  const listRow = () => frame.locator('#listTable tbody tr').filter({ hasText: name });

  let row;
  let listed;
  await s.step('Add New > Save stores one report tool for the chosen eForm and creates its report table', async () => {
    await frame.locator('#btnAdd').click();
    const form = frame.locator('#new-report');
    await form.waitFor({ state: 'visible' });
    const eform = frame.locator('#eformReportToolEformId option').first();
    await eform.waitFor({ state: 'attached' });
    const eformId = await eform.getAttribute('value');
    h.assert(/^[1-9]\d*$/.test(eformId || ''), 'Choose EForm offers no eForm');
    await frame.locator('#eformReportToolName').fill(name);
    await frame.locator('#eformReportToolEformId').selectOption(eformId);
    // Save's success handler re-reads the list; keep that response for the next step.
    listed = admin.waitForResponse(r => r.request().method() === 'GET'
      && new URL(r.url()).pathname.endsWith('/ws/rs/reporting/eformReportTool/list'), { timeout: TIMEOUT });
    listed.catch(() => {});
    const [added] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/ws/rs/reporting/eformReportTool/add')),
      frame.getByRole('button', { name: 'Save', exact: true }).click(),
    ]);
    h.assert(added.ok(), `Save answered HTTP ${added.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM EFormReportTool WHERE name=${h.sqlString(name)}`, '1', 'Save did not store one report tool');
    const rows = ownedRows();
    h.assert(rows.length === 1 && rows[0][2] === eformId, 'Save did not store exactly one report tool for the chosen eForm');
    row = rows[0];
    h.assert(JSON.stringify(ownedTables()) === JSON.stringify([row[1]]), 'Save did not create exactly the stored report table');
  });

  await s.step('the refreshed list shows the new report tool with its table name', async () => {
    // On 2026.08 the list answers 500 once any report tool exists (getNumRecords casts COUNT(*)
    // to BigInteger; Hibernate 7 returns Long), so the new tool never appears and cannot be removed.
    const response = await listed;
    h.assert(response.ok(), `The report tool list answered HTTP ${response.status()} once a report tool exists`);
    await listRow().waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert((await listRow().innerText()).includes(row[1]), 'The list does not show the stored report table name');
  });

  const removeLink = () => listRow().locator('a', { hasText: '(Remove)' });

  await s.step('(Remove) dismissed at the confirm changes nothing', async () => {
    const dialogs = await h.withExpectedDialogs(admin, () => removeLink().click(), { accept: false });
    h.assert(dialogs.length === 1, 'Remove did not ask for confirmation exactly once');
    h.assert(JSON.stringify(ownedRows()) === JSON.stringify([row]) && JSON.stringify(ownedTables()) === JSON.stringify([row[1]]),
      'A dismissed Remove changed the report tool');
    h.assert(await listRow().count() === 1, 'A dismissed Remove dropped the row from the list');
  });

  await s.step('(Remove) accepted deletes exactly the owned row, drops its table and leaves the list', async () => {
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      await removeLink().click();
      await listRow().waitFor({ state: 'detached', timeout: TIMEOUT });
    });
    h.assert(dialogs.length === 1, 'Remove did not ask for confirmation exactly once');
    await expectValue(sql, `SELECT COUNT(*) FROM EFormReportTool WHERE id=${row[0]}`, '0', 'Remove did not delete the report tool row');
    h.assert(ownedRows().length === 0, 'Remove left a report tool row with the owned name');
    h.assert(ownedTables().length === 0, 'Remove did not drop the owned report table');
  });
}

if (require.main === module) runWorkflow('detached-delete-eform-report-tool', workflow, { openPatient: false });
module.exports = { workflow };
