#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * EForm Report Tool ▸ (Populate) and (Mark Latest): the two actions that fill a report tool's ERT_
 * table from saved eForms and flag each patient's most recent one. detached-delete-eform-report-tool
 * covers Add and Remove; nothing pressed Populate or Mark Latest.
 *
 * User path: Schedule ▸ Administration ▸ EForm Report Tool (admin/ViewEformReportTool, in the
 * #dynamic-content iframe) ▸ Add New ▸ Name + Choose EForm ▸ Save; the row's (Populate)
 * (ws/rs/reporting/eformReportTool/populate); (Mark Latest) (…/markLatest); (Populate) again.
 * Asserted: Save makes a table with a column per saved field; Populate copies every saved instance
 * with its patient, date, provider and values, and the list shows the date and the table's record
 * count; Mark Latest flags only the patient's most recent instance and the list shows it; a second
 * Populate picks up an instance saved since and clears the flags; a deleted instance is never copied.
 * Fixtures: an owned eForm template, the owned FAKE- patient and four eform_data instances (one
 * deleted, one saved after the first Populate) with eform_values, seeded by SQL; the report tool and
 * its ERT_FAKEPW<hex> table are created through the UI. The report tool is removed with the page's own
 * (Remove), which drops its table, while the browser is still open (the harness closes it before cleanup
 * runs); cleanup then deletes the instances, their values and the template, and asserts all are gone.
 * There is no SQL DROP: a report tool the page could not remove fails the run and is left for a look.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const SERVICE = '/ws/rs/reporting/eformReportTool/';

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const name = `FAKEPW${marker.slice(-14)}`;
  h.assert(/^[A-Za-z0-9_]+$/.test(name), 'The report tool name must be a plain identifier');
  const ownedTables = () => sql.rows(`SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name LIKE ${h.sqlString(`ERT\\_${name}%`)} ORDER BY table_name`).map(row => row[0]);
  const ownedTool = () => sql.rows(`SELECT id, tableName, dateLastPopulated IS NOT NULL, latestMarked FROM EFormReportTool
    WHERE name=${h.sqlString(name)}`);
  let fid;
  s.cleanup(async () => {
    if (fid) {
      sql.execute(`DELETE FROM eform_values WHERE fid=${fid}; DELETE FROM eform_data WHERE fid=${fid};
        DELETE FROM eform WHERE fid=${fid} AND form_name=${h.sqlString(marker)}`);
    }
    h.assert(ownedTables().length === 0 && ownedTool().length === 0, 'The owned report tool or its table was not removed');
    h.assert(!fid || sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE fid=${fid}) + (SELECT COUNT(*) FROM eform_data WHERE fid=${fid})
      + (SELECT COUNT(*) FROM eform_values WHERE fid=${fid})`) === '0', 'The owned eForm, its instances or values were not removed');
  });
  h.assert(ownedTables().length === 0 && ownedTool().length === 0, 'A report tool with the owned name already exists');

  fid = sql.value(`INSERT INTO eform (form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
      showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES (${h.sqlString(marker)},'','Report tool fixture',CURDATE(),CURTIME(),${h.sqlString(provider)},1,
      '<html><body><form><input type="text" name="metric"><input type="text" name="remark"></form></body></html>',0,0,'',0,1);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'The owned eForm was not created');
  // One instance per shape: deleted (status 0, oldest), two current ones, and one saved only after the first Populate.
  const saveInstance = (label, date, time, status) => {
    const fdid = sql.value(`INSERT INTO eform_data (fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,
        form_data,showLatestFormOnly,patient_independent,roleType)
      VALUES (${fid},${h.sqlString(marker)},${h.sqlString(label)},${patient},${status},${h.sqlString(date)},${h.sqlString(time)},
        ${h.sqlString(provider)},'',0,0,''); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fdid), `The ${label} instance was not saved`);
    const values = { metric: `FAKE-${label}-${marker.slice(-6)}`, remark: `${marker} ${label}` };
    for (const [varName, value] of Object.entries(values)) {
      sql.execute(`INSERT INTO eform_values (fdid,fid,demographic_no,var_name,var_value)
        VALUES (${fdid},${fid},${patient},${h.sqlString(varName)},${h.sqlString(value)})`);
    }
    return { fdid, date: `${date} ${time}`, values };
  };
  const deleted = saveInstance('deleted', '2026-01-05', '08:00:00', 0);
  const first = saveInstance('first', '2026-02-10', '09:30:00', 1);
  const second = saveInstance('second', '2026-03-15', '14:45:00', 1);

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
  // The page's (Remove) answers a confirm, then deletes the report tool and drops its table.
  const removeTool = async () => {
    await h.withExpectedDialogs(admin, async () => {
      const [removed] = await Promise.all([
        admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(`${SERVICE}remove`), { timeout: TIMEOUT }),
        listRow().locator('a', { hasText: '(Remove)' }).click(),
      ]);
      h.assert(removed.ok(), `(Remove) answered HTTP ${removed.status()}`);
    });
    h.assert(ownedTool().length === 0 && ownedTables().length === 0, 'The page\'s (Remove) left the report tool or its table');
  };
  // Columns: actions, name, table, eForm, created, expiry, last populated, latest marked, record count.
  const listed = async () => (await listRow().locator('td').allInnerTexts()).map(text => text.trim());
  // An action posts, and its success handler re-reads the list; both are awaited before anything is read.
  const press = async (action, label) => {
    const relisted = admin.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname.endsWith(`${SERVICE}list`),
      { timeout: TIMEOUT });
    relisted.catch(() => {});
    const [posted] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(`${SERVICE}${action}`), { timeout: TIMEOUT }),
      listRow().locator('a', { hasText: label }).click(),
    ]);
    h.assert(posted.ok(), `${label} answered HTTP ${posted.status()}`);
    const body = await posted.json();
    h.assert(body.status === 'SUCCESS', `${label} answered ${JSON.stringify(body.status)}: ${JSON.stringify(body.error)}`);
    h.assert((await relisted).ok(), `The list after ${label} answered HTTP ${(await relisted).status()}`);
  };

  let table;
  const reportRows = () => sql.rows(`SELECT fdid, demographicNo, DATE_FORMAT(dateFormCreated, '%Y-%m-%d %H:%i:%s'), providerNo, eft_latest,
      COALESCE(metric, ''), COALESCE(remark, '') FROM \`${table}\` ORDER BY fdid`);
  const expectCopied = rows => {
    for (const instance of [first, second]) {
      const row = rows.find(r => r[0] === instance.fdid);
      h.assert(row, `The report table holds no row for the instance saved ${instance.date}`);
      h.assert(row[1] === patient && row[2] === instance.date && row[3] === provider,
        `The row for the instance saved ${instance.date} has the wrong patient, date or provider: ${JSON.stringify(row.slice(1, 4))}`);
      h.assert(row[5] === instance.values.metric && row[6] === instance.values.remark,
        `The row for the instance saved ${instance.date} does not hold its values`);
    }
  };

  let failure;
  try {
    await s.step('Add New ▸ Save makes a report table with a column for each saved field', async () => {
      await frame.locator('#btnAdd').click();
      await frame.locator('#new-report').waitFor({ state: 'visible' });
      await frame.locator(`#eformReportToolEformId option[value="${fid}"]`).waitFor({ state: 'attached' });
      await frame.locator('#eformReportToolName').fill(name);
      await frame.locator('#eformReportToolEformId').selectOption(fid);
      const relisted = admin.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname.endsWith(`${SERVICE}list`),
        { timeout: TIMEOUT });
      relisted.catch(() => {});
      const [added] = await Promise.all([
        admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(`${SERVICE}add`), { timeout: TIMEOUT }),
        frame.getByRole('button', { name: 'Save', exact: true }).click(),
      ]);
      h.assert(added.ok(), `Save answered HTTP ${added.status()}`);
      await relisted;
      await expectValue(sql, `SELECT COUNT(*) FROM EFormReportTool WHERE name=${h.sqlString(name)}`, '1', 'Save did not store one report tool');
      table = ownedTool()[0][1];
      h.assert(JSON.stringify(ownedTables()) === JSON.stringify([table]), 'Save did not create exactly the stored report table');
      const columns = sql.rows(`SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=${h.sqlString(table)}`)
        .map(row => row[0]);
      h.assert(columns.includes('metric') && columns.includes('remark'), `The report table has no column per field: ${columns.join(',')}`);
      await listRow().waitFor({ state: 'visible', timeout: TIMEOUT });
      const cells = await listed();
      h.assert(cells[6] === '' && cells[8] === '0', `A new report tool is listed as populated: ${JSON.stringify(cells.slice(6))}`);
    });

    await s.step('(Populate) copies every saved instance with its patient, date, provider and values', async () => {
      await press('populate', '(Populate)');
      const rows = reportRows();
      expectCopied(rows);
      h.assert(rows.every(row => row[4] === '0'), 'Populate flagged a row as latest');
      const [, , populated, marked] = ownedTool()[0];
      h.assert(populated === '1' && marked === '0', 'The report tool does not record the populate, or is still marked latest');
      const cells = await listed();
      h.assert(cells[6] !== '' && cells[7] === 'false' && cells[8] === String(rows.length),
        `The list does not show the populate, the latest flag and the ${rows.length} records: ${JSON.stringify(cells.slice(6))}`);
    });

    await s.step('(Mark Latest) flags only the patient\'s most recent instance', async () => {
      await press('markLatest', '(Mark Latest)');
      const flagged = reportRows().filter(row => row[4] === '1').map(row => row[0]);
      h.assert(JSON.stringify(flagged) === JSON.stringify([second.fdid]),
        `Mark Latest flagged ${JSON.stringify(flagged)}, not only the instance saved ${second.date}`);
      h.assert(ownedTool()[0][3] === '1', 'The report tool is not recorded as marked latest');
      h.assert((await listed())[7] === 'true', 'The list does not show the report tool as marked latest');
    });

    let third;
    await s.step('(Populate) again picks up an instance saved since and clears the latest flags', async () => {
      third = saveInstance('third', '2026-04-20', '11:15:00', 1);
      await press('populate', '(Populate)');
      const rows = reportRows();
      expectCopied(rows);
      h.assert(rows.some(row => row[0] === third.fdid && row[5] === third.values.metric), 'The second Populate did not copy the newer instance');
      h.assert(rows.every(row => row[4] === '0'), 'The second Populate kept a latest flag');
      h.assert(ownedTool()[0][3] === '0' && (await listed())[7] === 'false', 'The report tool is still marked latest after a new Populate');
      h.assert((await listed())[8] === String(rows.length), 'The list count does not match the report table');
    });

    await s.step('a deleted instance is never copied into the report table', async () => {
      const rows = reportRows();
      h.assert(!rows.some(row => row[0] === deleted.fdid),
        `The report table holds the deleted instance (fdid ${deleted.fdid}, eform_data.status 0) among its ${rows.length} rows`);
    });
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    // Before the harness closes the browser: the page's own (Remove) is the only way this check removes its table.
    // A failing Remove must not hide the step that failed; cleanup's gone-assertion still reports a leftover.
    if (ownedTool().length) {
      try {
        await removeTool();
      } catch (removeError) {
        if (!failure) throw removeError;
        console.error(`    note: (Remove) after the failed step also failed: ${removeError.message}`);
      }
    }
  }
}

if (require.main === module) runWorkflow('eform-report-tool-populate', workflow, { openMaster: false });
module.exports = { workflow };
