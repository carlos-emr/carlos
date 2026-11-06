#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// E-Chart > owned PDF > open ticklers > Deleted. No erase control is offered;
// legacy Erase Completely submissions retain tasks and history, including when none are selected.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments,
  assertOwnedPdfDocumentsRemoved } = require('./lib/stored-pdf-documents');

async function workflow(s) {
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const docs = ownedPdfDocuments(store, s.marker, [{ key: 'erase-entry' }]);
  const ownedDocument = { sql: s.sql, marker: s.marker, patient: s.patient, docs, files: docs.map(doc => doc.file) };
  s.cleanup(() => { removeOwnedPdfDocuments(ownedDocument); assertOwnedPdfDocumentsRemoved(ownedDocument); });
  seedOwnedPdfDocuments({ sql: s.sql, store, patient: s.patient, provider: s.provider, docs });
  const ids = [];
  s.cleanup(() => {
    const owned = `SELECT tickler_no FROM tickler WHERE demographic_no=${s.patient}`;
    const idList = ids.join(',') || '0';
    s.sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${owned}) OR tickler_id IN (${idList});
      DELETE FROM tickler_update WHERE tickler_no IN (${owned}) OR tickler_no IN (${idList});
      DELETE FROM tickler_comments WHERE tickler_no IN (${owned}) OR tickler_no IN (${idList});
      DELETE FROM tickler_link WHERE tickler_no IN (${owned}) OR tickler_no IN (${idList});
      DELETE FROM tickler WHERE demographic_no=${s.patient};
      DELETE FROM eChart WHERE demographicNo=${s.patient}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE demographic_no=${s.patient})
      + (SELECT COUNT(*) FROM tickler_update WHERE tickler_no IN (${idList}))
      + (SELECT COUNT(*) FROM tickler_comments WHERE tickler_no IN (${idList}))
      + (SELECT COUNT(*) FROM tickler_link WHERE tickler_no IN (${idList}))
      + (SELECT COUNT(*) FROM ticklerdocs WHERE tickler_id IN (${idList}))`) === '0', 'Owned tasks or related rows remain');
  });
  for (const [label, status] of [['erase','D'], ['deleted control','D'], ['active control','A']]) {
    ids.push(s.sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES(${s.patient},${h.sqlString(`${s.marker} ${label}`)},${h.sqlString(status)},NOW(),CURDATE(),
        ${h.sqlString(s.provider)},'Normal',${h.sqlString(s.provider)}); SELECT LAST_INSERT_ID()`));
  }
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Task fixtures were not created');
  s.sql.execute(`INSERT INTO tickler_update (tickler_no,status,provider_no,update_date)
    VALUES (${ids[0]},'D',${h.sqlString(s.provider)},NOW())`);
  const snapshot = () => JSON.stringify([
    s.sql.rows(`SELECT * FROM tickler WHERE demographic_no=${s.patient} ORDER BY tickler_no`),
    s.sql.rows(`SELECT * FROM tickler_update WHERE tickler_no IN (${ids.join(',')}) ORDER BY id`),
  ]);
  const chart = await s.chart();
  const link = chart.locator(`#leftNavBar a[onclick*="segmentID=${docs[0].id}'"], #rightNavBar a[onclick*="segmentID=${docs[0].id}'"]`).first();
  const viewer = await ui.clickOpensPopup(chart, link, { context: s.context, recorder: s.recorder,
    label: 'erase-entry-document', position: { x: 4, y: 4 } });
  let list;
  async function deleted() {
    // Mutations return to the modern main list. Re-enter this retained patient
    // queue through its real document alert, as the existing status check does.
    if (list && !list.isClosed()) await list.close();
    list = await s.popup(viewer, viewer.locator('.alert-info a.alert-link[onclick*="/tickler/ViewTicklerDemoMain"]'), 'patient-erased-tasks');
    h.assert(await list.locator('form[name="serviceform"] input[name="demoview"]').inputValue() === s.patient,
      'The document alert opened another patient task queue');
    await list.locator('form[name="serviceform"] select[name="ticklerview"]').selectOption('D');
    await ui.clickAndAwaitReload(list, list.locator('form[name="serviceform"] input[type="submit"]'), { label: 'Deleted task report' });
  }
  const erase = () => list.locator('form[name="ticklerform"] input[onclick*="submit_form.value=\'Erase Completely\'"]');
  const box = id => list.locator(`form[name="ticklerform"] input[name="checkbox"][value="${id}"]`);
  async function legacyErase(selected) {
    const [response] = await Promise.all([
      list.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/tickler/DbTicklerDemoMain')),
      list.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      list.evaluate(() => {
        const form = document.forms['ticklerform'];
        form.submit_form.value = 'Erase Completely';
        form.submit();
      }),
    ]);
    h.assert(response.status() < 400, `Legacy erase answered HTTP ${response.status()}`);
    const submitted = new URLSearchParams(response.request().postData());
    h.assert(JSON.stringify(submitted.getAll('checkbox')) === JSON.stringify(selected) && submitted.get('CSRF-TOKEN'),
      'Legacy erase posted other tasks or omitted CSRF protection');
    await h.assertNotErrorPage(list, 'the task list after a legacy erase submission');
    const returned = new URL(list.url()).searchParams;
    h.assert(returned.get('demoview') === s.patient && !returned.has('failCount'),
      'Legacy erase returned to another patient or reported a failed task update');
  }
  await s.step('Deleted has no erase action and a legacy erase with no selection changes nothing', async () => {
    await deleted();
    h.assert(await box(ids[0]).count() === 1 && await box(ids[1]).count() === 1 && await box(ids[2]).count() === 0,
      'Deleted listed the wrong patient tasks');
    h.assert(await erase().count() === 0, 'Deleted still renders an erase action');
    const before = snapshot();
    await legacyErase([]);
    h.assert(snapshot() === before, 'Legacy erase without selection changed a task or its history');
  });
  await s.step('a legacy erase retains the checked Deleted task and history and preserves unchecked tasks', async () => {
    await deleted(); await box(ids[0]).check();
    const before = snapshot();
    await legacyErase([ids[0]]);
    h.assert(snapshot() === before, 'Legacy erase changed a task or its status history');
    await deleted();
    h.assert(await box(ids[0]).count() === 1 && await box(ids[1]).count() === 1 && await box(ids[2]).count() === 0,
      'Legacy erase changed which tasks remain in Deleted');
    h.assert(await erase().count() === 0, 'Deleted renders an erase action after a legacy submission');
  });
}
if (require.main === module) runWorkflow('tickler-patient-erase', workflow);
module.exports = { workflow };
