#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Flowsheet: delete a saved reading from its Add Measurement Data page.
 *
 * A flowsheet lists each saved reading; clicking one opens Add Measurement Data for it
 * (AddMeasurementData.jsp with the reading's id), which is read-only with Delete as its only
 * action and posts to encounter/oscarMeasurements/DeleteData2 instead of the usual save. The
 * history page's delete is covered by measurement-group-entry and measurement-history, and
 * omd-flowsheet-conformance checks that this page offers Delete; nothing pressed it.
 *
 * User path: Master record ▸ E-Chart ▸ Diabetes flowsheet (popup) ▸ the owned reading (popup) ▸
 * Delete. Asserted: the flowsheet lists the owned reading; it opens read-only with Delete and no
 * Save; a GET to the delete is refused with 405 and removes nothing; Delete removes the reading,
 * keeps one measurementsDeleted copy of it (originalId, type, value and comment), closes its popup,
 * and the flowsheet no longer lists it.
 *
 * Fixtures: the workflow's own FAKE patient with an active diabetes diagnosis (dxresearch 250,
 * so the chart offers the flowsheet) and one tuning-fork reading (NRTF, "Yes", observed a month
 * ago, commented with the run marker). Cleanup deletes the patient's dxresearch, measurements and
 * measurementsDeleted rows and asserts none is left.
 */
const h = require('./lib/playwright-harness');
const { expectValue, runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  s.cleanup(() => {
    sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient};
      DELETE FROM measurements WHERE demographicNo=${patient};
      DELETE FROM measurementsDeleted WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient})`) === '0',
    'The owned diagnosis or measurement rows were not removed');
  });
  // The chart offers the diabetes flowsheet only for a patient with the diagnosis; seeding it is
  // set-up (diagnosis-flowsheet covers the registry itself).
  sql.execute(`INSERT INTO dxresearch (demographic_no, start_date, update_date, status, dxresearch_code, coding_system, providerNo)
    VALUES (${patient}, CURDATE(), NOW(), 'A', '250', 'icd9', ${q(provider)})`);
  const comment = `${marker} reading to delete`;
  const id = sql.value(`INSERT INTO measurements (type, demographicNo, providerNo, dataField, measuringInstruction,
      comments, dateObserved, dateEntered)
    VALUES ('NRTF', ${patient}, ${q(provider)}, 'Yes', 'Yes/No/NA', ${q(comment)},
      NOW() - INTERVAL 1 MONTH, NOW() - INTERVAL 1 MONTH); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'The owned reading was not created');
  const readings = () => sql.value(`SELECT COUNT(*) FROM measurements WHERE id=${id}`);
  const readingLink = page => page.locator(`div.preventionProcedure[onclick*="ViewAddMeasurementData?measurement="][onclick*="&id=${id}&"]`);

  const chart = await s.chart();
  let flowsheet;
  await s.step('the diabetes flowsheet lists the owned reading', async () => {
    const link = chart.locator(`a[onclick*="ViewTemplateFlowSheet"][onclick*="template=diab2'"]`).first();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    flowsheet = await s.popup(chart, link, 'diabetes-flowsheet');
    await flowsheet.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(flowsheet, 'Diabetes flowsheet');
    h.assert(await readingLink(flowsheet).count() === 1, 'The diabetes flowsheet does not list the owned reading');
  });

  let entry;
  await s.step('the reading opens read-only with Delete and no Save', async () => {
    entry = await s.popup(flowsheet, readingLink(flowsheet), 'reading-entry');
    await entry.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(entry, 'Add Measurement Data for the owned reading');
    const form = entry.locator('#measurementForm');
    h.assert(new URL(await form.evaluate(f => f.action)).pathname.endsWith('/encounter/oscarMeasurements/DeleteData2'),
      'The saved reading\'s page does not post to the delete');
    h.assert(await form.locator('input[name="deleteCheckbox"]').inputValue() === String(id), 'The page is not for the owned reading');
    h.assert(await entry.locator('#deleteButton').count() === 1, 'The saved reading\'s page has no Delete');
    h.assert(await entry.locator('input[type="button"][value="Save"]').count() === 0, 'The saved reading\'s page offers Save');
  });

  await s.step('a GET to the delete is refused and removes nothing', async () => {
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/encounter/oscarMeasurements/DeleteData2?deleteCheckbox=${id}&demographicNo=${patient}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET delete answered ${response.status()}, not 405`);
    h.assert(readings() === '1', 'A refused GET removed the reading');
  });

  await s.step('Delete removes the reading, keeps a deleted copy, and the flowsheet no longer lists it', async () => {
    const posted = entry.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/encounter/oscarMeasurements/DeleteData2'), { timeout: TIMEOUT });
    await Promise.all([entry.waitForEvent('close', { timeout: TIMEOUT }),
      entry.locator('#deleteButton').click({ noWaitAfter: true })]);
    await posted;
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE id=${id}`, '0', 'Delete did not remove the reading');
    const copy = sql.value(`SELECT CONCAT_WS('|',type,dataField,comments) FROM measurementsDeleted WHERE originalId=${id} AND demographicNo=${patient}`);
    h.assert(copy === `NRTF|Yes|${comment}`, `The deleted copy is ${JSON.stringify(copy)}, not NRTF|Yes|<the comment>`);
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId=${id}`) === '1', 'Delete did not keep exactly one deleted copy');
    await flowsheet.reload();
    await h.assertNotErrorPage(flowsheet, 'Diabetes flowsheet after the delete');
    h.assert(await readingLink(flowsheet).count() === 0, 'The flowsheet still lists the deleted reading');
  });
}

if (require.main === module) runWorkflow('flowsheet-measurement-delete', workflow, { openPatient: true });
module.exports = { workflow };
