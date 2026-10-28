#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * A patient's prevention reminders. The prevention page shows the decision-support warnings that prevention.drl raises
 * for the patient; gap-clinical-prevention-notifications covers only the on/off setting and prevention-recall-report the
 * roster report, so no check reads one patient's reminders.
 * User path: E-Chart ▸ Preventions (the Prevention Recommendations box); ▸ Inf ▸ Completed today ▸ Save; ▸ Td ▸
 * Completed twelve years ago ▸ Save.
 * Asserts: a 70-year-old with no flu shot is told "Needs Flu Shot" and decision support reports no error; recording a
 * flu shot today takes that reminder away; LAST, a Td from twelve years ago, the patient's only tetanus vaccine, raises
 * "Needs repeat Td".
 * Fixtures: the runWorkflow FAKE- patient, made 70 by SQL; its preventions and their ext rows are removed in cleanup.
 */
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient } = s;
  s.cleanup(() => sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
    WHERE p.demographic_no=${patient}; DELETE FROM preventions WHERE demographic_no=${patient}`));
  // The flu rules ("Inf 2", "Inf 3") apply from 65.
  sql.execute(`UPDATE demographic SET year_of_birth=LPAD(YEAR(CURDATE())-70,4,'0'), month_of_birth='01', date_of_birth='02'
    WHERE demographic_no=${patient}`);
  const chart = await s.chart();
  let index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  const reminders = async () => (await index.locator('.recommendations').innerText().catch(() => '')).replace(/\s+/g, ' ');
  /** The reminders once the reopened list shows the given record, so a page that did not render can't pass. */
  async function remindersListing(id) {
    await index.locator(`.preventionProcedure[onclick*="id=${id}"]`).first().waitFor({ state: 'visible' });
    return reminders();
  }
  /** Records a given vaccine of the type on the date through its form and returns the new record's id. */
  async function record(type, date) {
    const link = index.locator(`a[onclick*="ViewAddPreventionData"][onclick*="prevention=${type}&"]`).first();
    const editor = await s.popup(index, link, 'prevention-editor');
    assert(await editor.locator('[name="prevention"]').inputValue() === type, `The list opened a form for another type than ${type}`);
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill(date);
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
    await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient} AND prevention_type=${sqlString(type)}
      AND deleted=0 AND refused=0 AND DATE(prevention_date)=${sqlString(date)}`, '1', `The ${type} was not recorded on ${date}`);
    const id = sql.value(`SELECT MAX(id) FROM preventions WHERE demographic_no=${patient} AND prevention_type=${sqlString(type)}`);
    await index.close();
    index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
    return id;
  }
  const today = sql.value('SELECT CURDATE()');
  const twelveYearsAgo = sql.value('SELECT DATE_SUB(CURDATE(), INTERVAL 12 YEAR)');

  await s.step('a 70-year-old with no flu shot is told "Needs Flu Shot", and decision support reports no error', async () => {
    const shown = await reminders();
    assert(shown.includes('Needs Flu Shot'), `The reminders do not ask for a flu shot: ${shown.slice(0, 300)}`);
    assert(!shown.includes('Decision Support Had Errors Running'), 'Decision support reported an error');
  });

  await s.step('recording a flu shot today takes the flu reminder away', async () => {
    const shown = await remindersListing(await record('Inf', today));
    assert(!shown.includes('Needs Flu Shot'), `The flu reminder is still shown after today's flu shot: ${shown.slice(0, 300)}`);
  });

  await s.step('a Td from twelve years ago, the only tetanus vaccine, raises "Needs repeat Td"', async () => {
    const shown = await remindersListing(await record('Td', twelveYearsAgo));
    await index.close();
    assert(shown.includes('Needs repeat Td'), `No repeat-Td reminder for a Td twelve years old: ${shown.slice(0, 300)}`);
  });
}

if (require.main === module) runWorkflow('prevention-reminders', workflow);
module.exports = { workflow };
