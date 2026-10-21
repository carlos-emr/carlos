#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Chart > Episodes > Add > ICD9 autocomplete > Submit > reopen > edit.
// The lifecycle check uses uncoded, unpunctuated descriptions; these independent
// cases cover selecting a diagnosis and reopening ordinary apostrophes in prose.
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {grantEpisodeModule} = require('./lib/episode-module-access');
const ui = require('./lib/playwright-ui');
const literal = process.env.EPISODE_ACTION === 'literal';
const name = literal ? 'episode-literal-description' : 'episode-coded-diagnosis';

async function workflow(s) {
  const {sql, patient, marker} = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM Episode WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM Episode WHERE demographicNo=${patient}`) === '0', 'Owned episodes remain');
  });
  grantEpisodeModule(s);
  const chart = await s.chart();
  let editor, id, description;
  async function save() {
    await Promise.all([editor.waitForEvent('close'), editor.locator('input[type="submit"]').click()]);
  }
  await s.step('open Add Episode from the chart and enter an owned episode', async () => {
    editor = await s.popup(chart, chart.locator('#menuTitleepisode a').first(), 'episode-add');
    await editor.locator('#search_coding_system').selectOption('icd9');
    await editor.locator('#startDate').fill('2026-01-02');
    await editor.locator('#startDate').press('Tab');
    if (literal) {
      description = `${marker} O'Neil follow-up`;
      await editor.locator('#description').pressSequentially(description);
    } else {
      await Promise.all([
        editor.waitForResponse(r => new URL(r.url()).pathname.endsWith('/CodeSearch') && r.status() === 200),
        editor.locator('#description').pressSequentially('DIABETES', {delay: 30}),
      ]);
      const choice = editor.locator('.ui-autocomplete .ui-menu-item').filter({hasText: /^DIABETES MELLITUS\*$/}).first();
      await choice.click();
      description = await editor.locator('#description').inputValue();
      h.assert(await editor.locator('#code').inputValue() === '250', 'Autocomplete did not select ICD9 250');
    }
    await save();
    id = sql.value(`SELECT id FROM Episode WHERE demographicNo=${patient}`);
    h.assert(/^[1-9]\d*$/.test(id), 'Episode was not saved');
    await expectValue(sql, `SELECT description FROM Episode WHERE id=${id}`, description, 'Description changed on save');
    if (!literal) h.assert(sql.value(`SELECT CONCAT(codingSystem,'|',code) FROM Episode WHERE id=${id}`) === 'icd9|250', 'Selected code was not saved');
  });
  await s.step(literal ? 'reopen an episode containing an apostrophe with its complete description and dates'
    : 'reopen a coded episode and preserve its diagnosis when changing the end date', async () => {
    const link = chart.locator(`a[onclick*="episode.id=${id}"]`).first();
    editor = await ui.clickOpensPopup(chart, link, {context:s.context, recorder:s.recorder, label:'episode-edit',position:{x:8,y:9}});
    h.assert(await editor.locator('#description').inputValue() === description, 'Reopening lost the episode description');
    h.assert(await editor.locator('#startDate').inputValue() === '2026-01-02', 'Reopening lost the start date');
    if (!literal) {
      h.assert(await editor.locator('#code').inputValue() === '250', 'Reopening lost the code');
      h.assert(await editor.locator('#search_coding_system').inputValue() === 'icd9', 'Reopening changed the coding system');
      await editor.locator('#endDate').fill('2026-02-03');
      await editor.locator('#endDate').press('Tab');
      await save();
      h.assert(sql.value(`SELECT CONCAT(codingSystem,'|',code,'|',DATE(endDate)) FROM Episode WHERE id=${id}`) === 'icd9|250|2026-02-03', 'Date edit changed the diagnosis');
    }
  });
}
if (require.main === module) runWorkflow(name, workflow);
module.exports = {workflow};
