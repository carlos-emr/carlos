#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5: an episode survives create/edit/complete/reopen/delete.
// All writes follow the chart's controls; SQL only seeds, asserts and cleans up.
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => sql.execute(`DELETE FROM Episode WHERE demographicNo=${patient}`));
  const chart = await s.chart();
  let editor;
  let id;
  const row = () => chart.locator(`a[onclick*="episode.id=${id}"]`).first();
  const state = () => `SELECT CONCAT(description,'|',status,'|',DATE(startDate),'|',COALESCE(DATE(endDate),''))
    FROM Episode WHERE id=${id} AND demographicNo=${patient}`;
  async function save() {
    const closed = editor.waitForEvent('close', { timeout: 20000 });
    closed.catch(() => {});
    await editor.locator('input[type="submit"]').click();
    await closed;
  }
  async function edit() { editor = await s.popup(chart, row(), 'episode-editor'); }
  await s.step('empty description is refused without writing', async () => {
    editor = await s.popup(chart, chart.locator('#menuTitleepisode a').first(), 'episode-editor');
    const dialogs = await withExpectedDialogs(editor, () => editor.locator('input[type="submit"]').click());
    assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === 'Description Required',
      'Empty episode description did not produce the expected validation');
    assert(sql.value(`SELECT COUNT(*) FROM Episode WHERE demographicNo=${patient}`) === '0',
      'Invalid episode was written');
  });
  await s.step('create persists and refreshes the chart without reload', async () => {
    await editor.locator('#description').fill(marker);
    await editor.locator('#startDate').fill('2026-01-02');
    await editor.locator('#startDate').press('Tab');
    await save();
    id = sql.value(`SELECT id FROM Episode WHERE demographicNo=${patient} AND description=${sqlString(marker)}`);
    assert(/^[1-9]\d*$/.test(id), 'Episode save did not create a row');
    await expectValue(sql, state(), `${marker}|Current|2026-01-02|`, 'Episode fields did not persist');
    await row().waitFor({ state: 'visible' });
  });
  await s.step('reopen, edit and complete round-trip', async () => {
    await edit();
    assert(await editor.locator('#description').inputValue() === marker, 'Reopened episode lost its description');
    await editor.locator('#description').fill(`${marker}-EDIT`);
    await editor.locator('#endDate').fill('2026-02-03');
    await editor.locator('#endDate').press('Tab');
    await editor.locator('select[name="episode.status"]').selectOption('Complete');
    await save();
    await expectValue(sql, state(), `${marker}-EDIT|Complete|2026-01-02|2026-02-03`, 'Completed episode lost fields');
    await row().waitFor({ state: 'detached' });
  });
  await s.step('completed episode can be reopened and deleted with history retained', async () => {
    const list = await s.popup(chart, chart.locator('h3[onclick*="/Episode?method=list"]').first(), 'episode-list');
    const link = list.getByRole('link', { name: `${marker}-EDIT`, exact: true });
    await link.click();
    editor = list;
    assert(await editor.locator('select[name="episode.status"]').inputValue() === 'Complete', 'Completed status did not reopen');
    await editor.locator('select[name="episode.status"]').selectOption('Current');
    await save();
    await row().waitFor({ state: 'visible' });
    await edit();
    await editor.locator('select[name="episode.status"]').selectOption('Deleted');
    await save();
    await expectValue(sql, state(), `${marker}-EDIT|Deleted|2026-01-02|2026-02-03`, 'Deleted episode history was lost');
    await row().waitFor({ state: 'detached' });
  });
}
if (require.main === module) runWorkflow('episode-lifecycle', workflow);
module.exports = { workflow };
