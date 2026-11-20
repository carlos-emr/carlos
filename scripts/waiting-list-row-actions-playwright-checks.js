#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Schedule > Report > Waiting List > row Update / Remove. Independent scenarios
// cover the manager's own actions; the older check edits/removes from Master Record.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { prepareWaitingLists } = require('./lib/waiting-list-ui-workflow');

async function workflow(s) {
  const remove = process.env.WAITING_LIST_ACTION === 'remove';
  h.assert(!process.env.WAITING_LIST_ACTION || remove, 'WAITING_LIST_ACTION must be remove when set');
  const f = await prepareWaitingLists(s, { members: true });
  const current = patient => s.sql.rows(`SELECT note,DATE(onListSince),position FROM waitingList
    WHERE listID=${f.list.id} AND demographic_no=${patient} AND is_history='N'`);
  const all = () => JSON.stringify(s.sql.rows(`SELECT * FROM waitingList WHERE listID=${f.list.id} ORDER BY id`));
  const controlBefore = JSON.stringify(s.sql.rows(`SELECT * FROM waitingList WHERE listID=${f.list.id}
    AND demographic_no=${f.control} ORDER BY id`));
  if (remove) {
    await s.step('cancelling Remove on the manager leaves both patients and all history unchanged', async () => {
      const before = all();
      const dialogs = await h.withExpectedDialogs(f.manager,
        () => f.patientRow(s.patient).getByRole('link', { name: /^remove$/i }).click(), { accept: false });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Remove did not request confirmation');
      h.assert(all() === before && await f.patientRow(s.patient).count() === 1, 'Cancelled removal changed the list');
    });
    await s.step('confirming Remove archives only the selected patient and refreshes the manager', async () => {
      const responsePromise = f.manager.waitForResponse(r => r.request().method() === 'GET'
        && h.pathOnly(r.url()).endsWith('/waitinglist/SetupDisplayWaitingList'));
      const dialogs = await h.withExpectedDialogs(f.manager,
        () => f.patientRow(s.patient).getByRole('link', { name: /^remove$/i }).click());
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Remove did not confirm');
      const response = await responsePromise;
      h.assert(response.status() === 200, 'The manager did not refresh after Remove');
      await f.manager.waitForLoadState('domcontentloaded');
      h.assert(current(s.patient).length === 0, 'The selected patient remains current');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${f.list.id}
        AND demographic_no=${s.patient} AND is_history='Y'`) === '1', 'Remove lost the selected patient history');
      h.assert(await f.patientRow(s.patient).count() === 0 && await f.patientRow(f.control).count() === 1,
        'The refreshed manager shows the wrong patients');
      h.assert(JSON.stringify(s.sql.rows(`SELECT * FROM waitingList WHERE listID=${f.list.id}
        AND demographic_no=${f.control} ORDER BY id`)) === controlBefore, 'Remove changed the other patient');
    });
    return;
  }
  const originalNote = `${s.marker} original note`;
  const newNote = `${s.marker} revised O'Neil "A&B"\nsecond line`;
  async function update(note, date) {
    const row = f.patientRow(s.patient);
    if (note !== undefined) await row.locator('textarea').fill(note);
    if (date !== undefined) await row.locator('input[name$="].onListSince"]').fill(date);
    await ui.clickAndAwaitReload(f.manager, row.locator('input[name^="update_"]'),
      { timeout: 20000, label: 'waiting-list row Update' });
    await h.assertNotErrorPage(f.manager, 'waiting-list row Update');
  }
  await s.step('row Update saves note and date, preserves history and reorders only the selected patient', async () => {
    await update(newNote, '2026-01-01');
    const saved = current(s.patient).map(row => [row[0].replace(/\r\n/g, '\n'), ...row.slice(1)]);
    h.assert(JSON.stringify(saved) === JSON.stringify([[newNote, '2026-01-01', '1']]),
      `Update lost the note/date or did not reprioritize by date: ${JSON.stringify(saved)}`);
    h.assert(JSON.stringify(current(f.control)) === JSON.stringify([[`${s.marker} control note`, '2026-02-02', '2']]),
      'Update overwrote the other patient rather than only changing its position');
    h.assert(s.sql.value(`SELECT note FROM waitingList WHERE listID=${f.list.id}
      AND demographic_no=${s.patient} AND is_history='Y'`) === originalNote, 'Update did not preserve the original note as history');
    h.assert(await f.patientRow(s.patient).locator('textarea').inputValue() === newNote, 'Reload lost the multiline note');
    const rows = await f.manager.locator('textarea[name$="].note"]').evaluateAll(nodes => nodes.map(node => node.value));
    h.assert(rows[0] === newNote, 'The manager did not render the newly prioritized patient first');
  });
  await s.step('clearing a row note persists the empty value without clearing the other patient note', async () => {
    await update('', undefined);
    h.assert(JSON.stringify(current(s.patient)) === JSON.stringify([['', '2026-01-01', '1']]), 'Clearing the note did not persist');
    h.assert(await f.patientRow(s.patient).locator('textarea').inputValue() === '', 'The cleared note reappeared after reload');
    h.assert(current(f.control)[0][0] === `${s.marker} control note`, 'Clearing changed the other patient note');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${f.list.id}
      AND demographic_no=${s.patient} AND is_history='Y'`) === '2', 'Clearing lost earlier history');
  });
  await s.step('Update without an edit only recomputes positions and adds no history version', async () => {
    const before = all();
    await update(undefined, undefined);
    h.assert(all() === before, 'An unedited Update rewrote patient data or created a version');
  });
}
if (require.main === module) runWorkflow(process.env.WAITING_LIST_ACTION === 'remove' ? 'waiting-list-manager-remove' : 'waiting-list-row-edit',
  workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
