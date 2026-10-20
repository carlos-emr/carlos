#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// E-Chart > owned PDF > open ticklers > Deleted > Erase Completely. The earlier
// status check asserts this button is offered but deliberately never presses it.
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
    s.sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${owned});
      DELETE FROM tickler_update WHERE tickler_no IN (${owned}); DELETE FROM tickler_comments WHERE tickler_no IN (${owned});
      DELETE FROM tickler_link WHERE tickler_no IN (${owned}); DELETE FROM tickler WHERE demographic_no=${s.patient};
      DELETE FROM eChart WHERE demographicNo=${s.patient}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${s.patient}`) === '0', 'Owned tasks remain');
  });
  for (const [label, status] of [['erase','D'], ['deleted control','D'], ['active control','A']]) {
    ids.push(s.sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES(${s.patient},${h.sqlString(`${s.marker} ${label}`)},${h.sqlString(status)},NOW(),CURDATE(),
        ${h.sqlString(s.provider)},'Normal',${h.sqlString(s.provider)}); SELECT LAST_INSERT_ID()`));
  }
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Task fixtures were not created');
  const snapshot = () => JSON.stringify(s.sql.rows(`SELECT * FROM tickler WHERE demographic_no=${s.patient} ORDER BY tickler_no`));
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
  await s.step('Deleted shows only the two deleted patient tasks and empty Erase changes nothing', async () => {
    await deleted();
    h.assert(await box(ids[0]).count() === 1 && await box(ids[1]).count() === 1 && await box(ids[2]).count() === 0,
      'Deleted listed the wrong patient tasks');
    const before = snapshot();
    await ui.clickAndAwaitReload(list, erase(), { label: 'Erase without selection' });
    h.assert(snapshot() === before && s.sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no IN (${ids.join(',')})`) === '0',
      'Erase without selection changed a task');
  });
  await s.step('Erase Completely removes only the checked task from Deleted and preserves unchecked tasks', async () => {
    await deleted(); await box(ids[0]).check();
    const [response] = await Promise.all([
      list.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/tickler/DbTicklerDemoMain')),
      ui.clickAndAwaitReload(list, erase(), { label: 'Erase selected task' }),
    ]);
    const submitted = new URLSearchParams(response.request().postData());
    h.assert(JSON.stringify(submitted.getAll('checkbox')) === JSON.stringify([ids[0]]) && submitted.get('CSRF-TOKEN'),
      'Erase posted other tasks or omitted CSRF protection');
    h.assert(s.sql.value(`SELECT status FROM tickler WHERE tickler_no=${ids[1]}`) === 'D'
      && s.sql.value(`SELECT status FROM tickler WHERE tickler_no=${ids[2]}`) === 'A', 'Erase changed an unchecked task');
    await deleted();
    h.assert(await box(ids[0]).count() === 0 && await box(ids[1]).count() === 1,
      'Erase Completely left the selected task in Deleted');
    // Retention of audit data is allowed; a task labelled erased must cease to
    // be an ordinary Deleted queue item, whether archived further or removed.
    h.assert(s.sql.value(`SELECT COUNT(*) FROM tickler WHERE tickler_no=${ids[0]} AND status='D'`) === '0',
      'Erased task still has its ordinary Deleted state');
  });
}
if (require.main === module) runWorkflow('tickler-patient-erase', workflow);
module.exports = { workflow };
