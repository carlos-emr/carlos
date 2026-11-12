#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Schedule > Report > Waiting List > row Update / Remove. Independent scenarios
// cover the manager's own actions; the older check edits/removes from Master Record.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { prepareWaitingLists } = require('./lib/waiting-list-ui-workflow');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

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

  // #6141: the note box holds what waitingList.note holds (255), counted as the server counts it:
  // code points, each line break as the CR LF the browser submits. A longer note used to be cut at the
  // column while the page reported success.
  const LIMIT = 255;
  const storedLength = () => s.sql.value(`SELECT CHAR_LENGTH(note) FROM waitingList WHERE listID=${f.list.id}
    AND demographic_no=${s.patient} AND is_history='N'`);
  await s.step(`a note of ${LIMIT} characters as submitted (a line break counts as CR LF) is saved whole`, async () => {
    await update('N'.repeat(100) + '\n' + 'N'.repeat(LIMIT - 102), undefined);
    h.assert(storedLength() === String(LIMIT), `The note at the limit was stored with ${storedLength()} characters, not ${LIMIT}`);
  });
  await s.step('a note one character longer is stopped in the browser with the limit message, and nothing is sent', async () => {
    const before = all();
    const box = f.patientRow(s.patient).locator('textarea');
    await box.fill('N'.repeat(100) + '\n' + 'N'.repeat(LIMIT - 101));
    await box.blur();
    let sent = 0;
    const watch = request => {
      if (request.method() === 'POST' && h.pathOnly(request.url()).endsWith('/waitinglist/SetupDisplayWaitingList')) sent++;
    };
    f.manager.on('request', watch);
    try {
      await f.patientRow(s.patient).locator('input[name^="update_"]').click();
      await f.manager.waitForTimeout(1500);
    } finally {
      f.manager.off('request', watch);
    }
    h.assert(sent === 0, 'An over-long note was sent to the server');
    h.assert(await box.evaluate(el => el.validationMessage) === `Maximum ${LIMIT} characters.`, 'The note box does not say its limit');
    h.assert(all() === before, 'A stopped update changed the list');
  });
  await s.step('a crafted over-long note past the browser is refused with 400, shown above the list, and changes no row', async () => {
    const before = all();
    const mark = failureMark(s.recorder);
    // As a hand-made request would: the page's own row selection, then a submit that skips its checks.
    const [response] = await Promise.all([
      f.manager.waitForResponse(r => r.request().method() === 'POST'
        && h.pathOnly(r.url()).endsWith('/waitinglist/SetupDisplayWaitingList'), { timeout: 20000 }),
      f.patientRow(s.patient).locator('textarea').evaluate((box, { list, value }) => {
        box.value = value;
        setParameters(box);
        box.form.waitingListId.value = list;
        box.form.update.value = 'Y';
        HTMLFormElement.prototype.submit.call(box.form);
      }, { list: String(f.list.id), value: 'C'.repeat(LIMIT + 1) }),
    ]);
    await f.manager.waitForLoadState('domcontentloaded');
    h.assert(response.status() === 400, `A crafted over-long note was answered ${response.status()}, not 400`);
    h.assert((await f.manager.locator('[data-carlos-waiting-list-note-error]').innerText())
      .includes(`Waiting list note exceeds maximum length of ${LIMIT} characters.`), 'The page does not say why the note was refused');
    h.assert(all() === before, 'A refused note changed the list');
    // The refusal is this step's expected result, not a page defect. The browser logs the refused load after the
    // page has loaded, so give it a moment before claiming the 400 and its console line.
    await f.manager.waitForTimeout(1000);
    consumeExpectedFailure(s.recorder, mark, { status: 400, path: /\/waitinglist\/SetupDisplayWaitingList$/ });
  });
}
if (require.main === module) runWorkflow(process.env.WAITING_LIST_ACTION === 'remove' ? 'waiting-list-manager-remove' : 'waiting-list-row-edit',
  workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
