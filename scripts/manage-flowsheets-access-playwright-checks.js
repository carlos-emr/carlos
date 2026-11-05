#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Run exclusively on a disposable deployment: EXCLUSIVE=1 npm run test:manage-flowsheets-access-playwright
 * Administration > System Management > Manage Flowsheets: who is offered the page, what it lets
 * them do (issue #4623, findings-log row 267).
 *
 * The seeded doctor role holds _admin.flowsheet READ only (database/mysql/migration/{on,bc}/V1.0.2).
 * The Administration left nav and manageFlowsheets.jsp admit that right, so the page has to open
 * for it; ManageFlowsheets2Action used to demand WRITE for every request and answered the link with
 * HTTP 403. Viewing the list is a read; enabling or disabling a flowsheet, and uploading a custom
 * one, are writes.
 *
 * User path: sign in as a plain doctor > Administration (opened by its address; see
 * openManageFlowsheets for why not the day sheet's link) > System Management > Manage Flowsheets
 * (the page loads in the Administration shell's frame).
 *
 * Asserted:
 *  - a throwaway login holding exactly the seeded doctor role opens the page from the nav, sees the
 *    flowsheet list and an Edit link per row, and is offered NO enable/disable link and NO upload
 *    form (a control the login cannot use is not shown);
 *  - a doctor role that also carries an explicit no-rights (`o`) row on _admin.misc still gets the
 *    list: the page defers to the action's per-object decision instead of a second, combined check;
 *  - the same login's direct POST of enable and of disable (a valid CSRF token, so the action's own
 *    privilege check is what answers) is refused with the application's HTTP 403 and the Flowsheet
 *    table is unchanged;
 *  - the same login given _admin.flowsheet WRITE (a row keyed by its own provider number, as the
 *    Assign Role/Rights to Object editor stores a per-provider grant) is offered the controls,
 *    disables one flowsheet through the page (the row reads false and offers Enable, the stored
 *    flag is 0) and enables it again (stored flag 1), through the real PRG redirect.
 *
 * Fixtures: one throwaway doctor login (own provider and security rows), one per-provider
 * _admin.flowsheet grant and, for a few seconds, one `o` row on _admin.misc for the doctor role.
 * The toggled flowsheet's own Flowsheet row is restored (or removed, when the toggle had to create
 * it) and the whole table is compared before and after. Mutates: Flowsheet, secObjPrivilege; do
 * not run it concurrently with another check that lists or toggles flowsheets or signs in.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { frameAt, submitInFrame } = require('./lib/admin-frame');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { csrfToken } = require('./lib/front-door-checks');

const ROUTE = 'admin/ManageFlowsheets';
const OBJECT = '_admin.flowsheet';
const TIMEOUT = 30000;
// CARLOS's own filters put this header on every response; a WAF or proxy block page lacks it, so a
// 403 that carries it is the application's authorization decision.
const APPLICATION_HEADER = 'x-permitted-cross-domain-policies';

async function workflow(s) {
  const { sql, config, recorder, marker } = s;
  h.assert(process.env.EXCLUSIVE === '1', 'Toggling a flowsheet changes clinic-wide state: run with EXCLUSIVE=1 on a disposable deployment');
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const flowsheetTable = () => JSON.stringify(sql.rows('SELECT name,enabled,external FROM Flowsheet ORDER BY name,id'));
  const tableBefore = flowsheetTable();

  // A fresh session each time: the menu's oscarSec tags read roles captured at login.
  async function throwawaySession(label) {
    const context = await h.newContext(s.context.browser(), config);
    context.on('page', page => h.wireStrictPage(page, label, recorder));
    const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label });
    return { context, schedule };
  }
  // The Administration shell is opened by its address, which is what the day sheet's Administration
  // link opens. That link is not a reliable way in for the seeded doctor: its security tag names
  // thirteen objects and OscarRoleObjectPrivilege keeps ONE privilege per role, so the doctor's
  // `_admin.reporting` `o` row can mask `_admin.flowsheet` `r` depending on the order the database
  // returns the rows. The shell's own left nav lists Manage Flowsheets from two objects only.
  async function openManageFlowsheets(session, label) {
    const admin = await session.context.newPage();
    await h.gotoApp(admin, config.baseUrl, '/administration');
    await admin.locator('#adminNav').waitFor({ state: 'attached', timeout: TIMEOUT });
    const link = admin.locator(`#adminNav a.xlink[rel$="/${ROUTE}"]`).first();
    await link.waitFor({ state: 'attached', timeout: TIMEOUT });
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const frame = await frameAt(admin, ROUTE, { timeout: TIMEOUT });
    await h.assertNotErrorPage(frame, label);
    return { admin, frame };
  }
  const toggleControls = frame => frame.locator('a[onclick^="submitFlowsheetAction("]');
  const uploadForm = frame => frame.locator('form[action$="/admin/ManageFlowsheetsUpload"]');

  let flowsheet;
  let rowCount;

  await s.step('the seeded doctor role holds Manage Flowsheets read and no administration write', async () => {
    const rows = sql.rows(`SELECT objectName,privilege FROM secObjPrivilege WHERE roleUserGroup='doctor'
      AND objectName IN ('_admin','_admin.misc','${OBJECT}') ORDER BY objectName`);
    h.assert(JSON.stringify(rows) === JSON.stringify([[OBJECT, 'r']]),
      `The seeded doctor role no longer matches this check's premise (expected only ${OBJECT} r): ${JSON.stringify(rows)}`);
    fixture.create({ roleNames: ['doctor'] });
    h.assert(sql.value(`SELECT GROUP_CONCAT(role_name) FROM secUserRole WHERE provider_no=${h.sqlString(fixture.providerNo)}`) === 'doctor',
      'The throwaway login does not hold exactly the doctor role');
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${h.sqlString(fixture.providerNo)}`) === '0',
      'The throwaway login already carries a per-provider grant');
  });

  await s.step('a read-only doctor opens Manage Flowsheets from the Administration nav and sees the list without write controls', async () => {
    const session = await throwawaySession('manage-flowsheets-reader');
    try {
      const { admin, frame } = await openManageFlowsheets(session, 'manage-flowsheets-reader');
      await frame.locator('table.table').waitFor({ state: 'attached' });
      rowCount = await frame.locator('table.table tbody tr').count();
      h.assert(rowCount > 0, 'Manage Flowsheets listed no flowsheet to a read-only doctor');
      const edits = frame.locator('a[href*="/encounter/oscarMeasurements/adminFlowsheet/ViewEditFlowsheet?"]');
      h.assert(await edits.count() === rowCount, 'The list does not offer an Edit link on every row');
      const href = await edits.first().getAttribute('href');
      flowsheet = new URL(href, config.baseUrl).searchParams.get('flowsheet');
      h.assert(flowsheet, 'The first Edit link names no flowsheet');
      h.assert(await toggleControls(frame).count() === 0, 'A read-only doctor was offered an Enable or Disable link');
      h.assert(await frame.locator('#flowsheetActionForm').count() === 0, 'A read-only doctor received the enable/disable form');
      h.assert(await uploadForm(frame).count() === 0 && await frame.locator('input[name="flowsheet_file"]').count() === 0,
        'A read-only doctor was offered the custom flowsheet upload');
      h.assert(await admin.locator(`#adminNav a.xlink[rel$="/${ROUTE}"]`).count() === 1, 'The nav no longer lists Manage Flowsheets once');
    } finally { await session.context.close(); }
  });

  await s.step('an explicit no-rights row on _admin.misc beside the doctor\'s read does not turn the page away', async () => {
    // The page used to re-check _admin, _admin.misc and _admin.flowsheet in ONE security tag, and
    // OscarRoleObjectPrivilege keeps a single privilege per role across those objects, so a
    // role's `_admin.misc` `o` row could mask its `_admin.flowsheet` `r` and bounce a reader the
    // action had admitted. The action's decision is the only one now. The row is clinic-wide for
    // the few seconds this step needs and is removed in `finally`, and again by cleanup.
    const where = `roleUserGroup='doctor' AND objectName='_admin.misc'`;
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE ${where}`) === '0', 'The doctor role already carries a _admin.misc row');
    const remove = () => {
      sql.execute(`DELETE FROM secObjPrivilege WHERE ${where} AND privilege='o' AND provider_no=${h.sqlString(s.provider)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE ${where}`) === '0', 'The _admin.misc no-rights row was not removed');
    };
    s.cleanup(remove);
    sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES ('doctor','_admin.misc','o',0,${h.sqlString(s.provider)})`);
    const session = await throwawaySession('manage-flowsheets-reader-masked');
    try {
      const { frame } = await openManageFlowsheets(session, 'manage-flowsheets-reader-masked');
      await frame.locator('table.table').waitFor({ state: 'attached' });
      h.assert(await frame.locator('table.table tbody tr').count() === rowCount, 'The masked reader sees a different flowsheet list');
      h.assert(await toggleControls(frame).count() === 0 && await uploadForm(frame).count() === 0,
        'The masked reader was offered write controls');
    } finally {
      await session.context.close();
      remove();
    }
  });

  await s.step('the read-only doctor\'s direct enable and disable are refused with 403 and change nothing', async () => {
    const session = await throwawaySession('manage-flowsheets-reader-post');
    try {
      const token = await csrfToken(session.schedule, config.baseUrl);
      for (const method of ['disable', 'enable']) {
        const response = await session.context.request.post(h.appUrl(config.baseUrl, `/${ROUTE}`), {
          form: { method, name: flowsheet, 'CSRF-TOKEN': token }, maxRedirects: 0, failOnStatusCode: false });
        const status = response.status();
        const fromApplication = Object.prototype.hasOwnProperty.call(response.headers(), APPLICATION_HEADER);
        await response.dispose();
        h.assert(status === 403 && fromApplication,
          `A read-only doctor's ${method} answered HTTP ${status}${fromApplication ? '' : ' (not from the application)'}, expected the application's 403`);
        h.assert(flowsheetTable() === tableBefore, `A refused ${method} changed the Flowsheet table`);
      }
    } finally { await session.context.close(); }
  });

  await s.step('a doctor holding Manage Flowsheets write is offered the controls and can disable and enable a flowsheet', async () => {
    const providerNo = h.sqlString(fixture.providerNo);
    const name = h.sqlString(flowsheet);
    const before = sql.rows(`SELECT id,enabled,external FROM Flowsheet WHERE name=${name} ORDER BY id`);
    s.cleanup(() => {
      sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${providerNo} AND objectName='${OBJECT}'`);
      // The toggle creates a tracking row when the flowsheet had none; put the table back exactly.
      if (before.length === 0) sql.execute(`DELETE FROM Flowsheet WHERE name=${name}`);
      else sql.execute(`UPDATE Flowsheet SET enabled=${before[0][1]} WHERE id=${before[0][0]}`);
      h.assert(flowsheetTable() === tableBefore, 'The Flowsheet table was not restored');
      h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${providerNo}`) === '0',
        'The per-provider grant was not removed');
    });
    sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${providerNo},'${OBJECT}','w',0,${h.sqlString(s.provider)})`);
    const session = await throwawaySession('manage-flowsheets-writer');
    try {
      const { admin, frame: opened } = await openManageFlowsheets(session, 'manage-flowsheets-writer');
      h.assert(await opened.locator('table.table tbody tr').count() === rowCount, 'The writer sees a different flowsheet list');
      h.assert(await toggleControls(opened).count() === rowCount, 'The writer is not offered one Enable/Disable link per flowsheet');
      h.assert(await uploadForm(opened).count() === 1, 'The writer is not offered the custom flowsheet upload');
      const row = frame => frame.locator('tbody tr').filter({
        has: frame.locator(`a[href*="/ViewEditFlowsheet?flowsheet=${encodeURIComponent(flowsheet)}&"]`) });
      const stored = () => sql.value(`SELECT enabled FROM Flowsheet WHERE name=${name} ORDER BY id LIMIT 1`);

      const disable = row(opened).locator('a[onclick^="submitFlowsheetAction(\'disable\'"]');
      h.assert(await disable.count() === 1, `${flowsheet} does not offer Disable while it is enabled`);
      const { response: disabled, frame: afterDisable } = await submitInFrame(admin, disable, ROUTE);
      h.assert(disabled.status() === 302, `Disable answered HTTP ${disabled.status()}, expected the PRG redirect`);
      h.assert(stored() === '0', 'Disable did not store the flowsheet as disabled');
      await h.assertNotErrorPage(afterDisable, 'manage flowsheets after disable');
      h.assert(await row(afterDisable).locator('a[onclick^="submitFlowsheetAction(\'enable\'"]').count() === 1,
        `${flowsheet} does not offer Enable once disabled`);

      const enable = row(afterDisable).locator('a[onclick^="submitFlowsheetAction(\'enable\'"]');
      const { response: enabled, frame: afterEnable } = await submitInFrame(admin, enable, ROUTE);
      h.assert(enabled.status() === 302, `Enable answered HTTP ${enabled.status()}, expected the PRG redirect`);
      h.assert(stored() === '1', 'Enable did not store the flowsheet as enabled');
      h.assert(await row(afterEnable).locator('a[onclick^="submitFlowsheetAction(\'disable\'"]').count() === 1,
        `${flowsheet} does not offer Disable once enabled again`);
    } finally { await session.context.close(); }
  });
}

if (require.main === module) runWorkflow('manage-flowsheets-access', workflow, { openPatient: false, openMaster: false });
module.exports = { workflow };
