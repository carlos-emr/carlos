#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Schedule > Report > Waiting List > Create List > Rename / Delete.
// Existing waiting-list checks create names, but never rename or archive them.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { prepareWaitingLists } = require('./lib/waiting-list-ui-workflow');

async function workflow(s) {
  const f = await prepareWaitingLists(s, { members: true });
  const other = f.seedList('untouched');
  const editor = await f.editor();
  const renamed = `${s.marker} renamed O'Neil "A&B"`;
  const beforeMembers = JSON.stringify(s.sql.rows(`SELECT * FROM waitingList WHERE listID=${f.list.id} ORDER BY id`));
  const listState = id => s.sql.rows(`SELECT name,is_history FROM waitingListName WHERE ID=${id}`);
  async function submit(action) {
    await ui.clickAndAwaitReload(editor, editor.locator(`input[type="submit"][onclick*="actionChosen.value='${action}'"]`),
      { timeout: 20000, label: `waiting-list ${action}` });
    await h.assertNotErrorPage(editor, `waiting-list ${action}`);
  }
  await s.step('renaming an occupied list preserves its ID and members and prints the new name literally', async () => {
    await editor.locator('#selectedWL').selectOption(f.list.id);
    await editor.locator('[name="wlChangedName"]').fill(renamed);
    await submit('change');
    h.assert(JSON.stringify(listState(f.list.id)) === JSON.stringify([[renamed, 'N']]), 'Rename lost the name, ID or active state');
    h.assert(JSON.stringify(s.sql.rows(`SELECT * FROM waitingList WHERE listID=${f.list.id} ORDER BY id`)) === beforeMembers,
      'Renaming changed list membership');
    h.assert((await editor.locator(`#selectedWL option[value="${f.list.id}"]`).textContent()).trim() === renamed,
      'Rename did not round-trip quotes and ampersand as literal text');
    await f.manager.reload({ waitUntil: 'domcontentloaded' });
    h.assert((await f.manager.locator(`#selectedWL option[value="${f.list.id}"]`).textContent()).trim() === renamed,
      'The manager does not offer the renamed list');
  });
  await s.step('a duplicate rename is refused and deleting an occupied list retains its members', async () => {
    await editor.locator('#selectedWL').selectOption(f.list.id);
    await editor.locator('[name="wlChangedName"]').fill(other.name);
    await submit('change');
    h.assert(/already exists/i.test(await editor.locator('.alert').innerText()), 'Duplicate rename has no actionable feedback');
    h.assert(listState(f.list.id)[0][0] === renamed, 'Duplicate rename changed the list');
    await editor.locator('#selectedWL2').selectOption(f.list.id);
    await submit('remove');
    h.assert(/being used|in use/i.test(await editor.locator('.alert').innerText()), 'Deleting an occupied list has no feedback');
    h.assert(listState(f.list.id)[0][1] === 'N', 'An occupied list was archived');
    h.assert(JSON.stringify(s.sql.rows(`SELECT * FROM waitingList WHERE listID=${f.list.id} ORDER BY id`)) === beforeMembers,
      'A refused rename/delete changed members');
  });
  await s.step('deleting an empty list archives only that list and removes it from both pickers', async () => {
    await editor.locator('#selectedWL2').selectOption(other.id);
    await submit('remove');
    h.assert(JSON.stringify(listState(other.id)) === JSON.stringify([[other.name, 'Y']]), 'Delete did not archive the empty list');
    h.assert(await editor.locator(`#selectedWL option[value="${other.id}"], #selectedWL2 option[value="${other.id}"]`).count() === 0,
      'An archived list remains selectable in the editor');
    await f.manager.reload({ waitUntil: 'domcontentloaded' });
    h.assert(await f.manager.locator(`#selectedWL option[value="${other.id}"]`).count() === 0, 'The manager offers an archived list');
    h.assert(listState(f.list.id)[0][1] === 'N' && await f.patientRow(s.patient).count() === 1,
      'Archiving another list affected the occupied list');
  });
}
if (require.main === module) runWorkflow('waiting-list-management', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
