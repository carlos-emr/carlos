#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5: an episode survives create/edit/complete/reopen/delete.
// All writes follow the chart's controls; SQL only seeds, asserts and cleans up.
// The seeded roles deny the Episode module (doctor holds `o` on its two gate objects, _episode and
// _newCasemgmt.episode, in the Flyway seeds and, since #4566, in the devcontainer), so the first
// step grants both to the test login for this run and proves the module follows them.
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { EPISODE_MODULE_GATES, episodeModuleGranted, grantEpisodeModule } = require('./lib/episode-module-access');

const EPISODE_HEADING = '#menuTitleepisode';
const EPISODE_OBJECTS = EPISODE_MODULE_GATES.map(gate => gate.objectName).join(' and ');

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  s.cleanup(() => sql.execute(`DELETE FROM Episode WHERE demographicNo=${patient}`));
  let chart;
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
  // Click the start of the title, as a clinician does: a long title runs under the row's
  // right-floated date link (LeftNavBarDisplay.jsp), which covers the title's centre.
  async function edit() {
    editor = await clickOpensPopup(chart, row(), {
      context: s.context, recorder: s.recorder, label: 'episode-editor', timeout: 20000, position: { x: 8, y: 9 },
    });
  }
  await s.step(`the chart draws the Episode module only once the login holds ${EPISODE_OBJECTS}`, async () => {
    if (episodeModuleGranted(sql, provider)) {
      console.log(`    (the test login's roles already hold ${EPISODE_OBJECTS}; the hidden half is not asserted)`);
    } else {
      const denied = await s.chart();
      // s.chart() waits for every module request to settle; require the Episode one to have
      // answered, so an absent heading means "not drawn" rather than "never asked for".
      const status = await denied.evaluate(() => window.carlosNavbarLoadState?.modules?.episode?.status ?? 'not requested');
      assert(status === 'loaded', `The chart's Episode module request did not complete (status: ${status})`);
      assert(await denied.locator(EPISODE_HEADING).count() === 0,
        `The chart drew the Episode module for a login whose roles do not hold ${EPISODE_OBJECTS}`);
      await denied.close();
    }
    grantEpisodeModule(s);
    chart = await s.chart();
    await chart.locator(EPISODE_HEADING).first().waitFor({ state: 'visible' });
  });
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
  await s.step('completing without an end date is refused without changing the stored episode', async () => {
    await edit();
    await editor.locator('select[name="episode.status"]').selectOption('Complete');
    const dialogs = await withExpectedDialogs(editor, () => editor.locator('input[type="submit"]').click());
    assert(dialogs.length === 1 && dialogs[0].type === 'alert' && /end date/i.test(dialogs[0].text),
      'Completed episode without an end date did not show validation');
    await expectValue(sql, state(), `${marker}|Current|2026-01-02|`, 'Invalid completion changed the episode');
    await editor.close();
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
    // LeftNavBarDisplay.jsp renders the module heading as <h3><a onclick="popupPage(..., '/Episode?method=list...')">.
    const list = await s.popup(chart, chart.locator('#episode h3 a[onclick*="/Episode?method=list"]').first(), 'episode-list');
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
