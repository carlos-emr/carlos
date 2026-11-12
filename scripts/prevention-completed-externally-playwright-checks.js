#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * A vaccine given elsewhere. The prevention form's "Completed externally" status (given_ext) with an outside provider's
 * name is recorded by no other check: prevention-print fills an outside name for a vaccine given here.
 * User path: E-Chart ▸ Preventions ▸ Inf ▸ Completed externally, a date, the outside provider's name ▸ Save; the list;
 * the record again.
 * Asserts: the vaccine is stored as completed externally (refused=3) with the "Other" provider and the typed name; the
 * list shows it without the refused or ineligible colour; the reopened form shows the status and the name; and it
 * counts as done, so the patient's "Needs Flu Shot" reminder goes away.
 * Fixtures: the runWorkflow FAKE- patient, made 70 by SQL so the flu reminder applies; its preventions and their ext
 * rows are removed in cleanup.
 */
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const REFUSED_COLOUR = 'rgb(255, 221, 221)'; // index.jsp r(): #FFDDDD refused
const INELIGIBLE_COLOUR = 'rgb(255, 204, 36)'; // #FFCC24 ineligible

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
    WHERE p.demographic_no=${patient}; DELETE FROM preventions WHERE demographic_no=${patient}`));
  sql.execute(`UPDATE demographic SET year_of_birth=LPAD(YEAR(CURDATE())-70,4,'0'), month_of_birth='01', date_of_birth='02'
    WHERE demographic_no=${patient}`);
  const chart = await s.chart();
  let index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  const reminders = async () => (await index.locator('.recommendations').innerText().catch(() => '')).replace(/\s+/g, ' ');
  const outside = `${marker} Pharmacy`;
  const given = sql.value('SELECT DATE_SUB(CURDATE(), INTERVAL 20 DAY)');
  let id;

  await s.step('Completed externally with an outside provider\'s name is stored as given elsewhere by that provider', async () => {
    assert((await reminders()).includes('Needs Flu Shot'), 'The 70-year-old is not asked for a flu shot before the vaccine is recorded');
    const editor = await s.popup(index, index.locator('a[onclick*="ViewAddPreventionData"][onclick*="prevention=Inf&"]').first(), 'prevention-editor');
    await editor.locator('[name="given"][value="given_ext"]').check();
    await editor.locator('[name="providerName"]').waitFor({ state: 'visible' });
    await editor.locator('[name="providerName"]').fill(outside);
    await editor.locator('#prevDate').fill(given);
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
    // The outside provider's name is kept with the vaccine's other details (preventionsExt providerName).
    const rows = sql.rows(`SELECT p.id, p.refused, p.provider_no, IFNULL((SELECT val FROM preventionsExt x WHERE x.prevention_id=p.id
      AND x.keyval='providerName' ORDER BY x.id DESC LIMIT 1),''), DATE(p.prevention_date) FROM preventions p
      WHERE p.demographic_no=${patient} AND p.prevention_type='Inf' AND p.deleted=0`);
    assert(rows.length === 1, `Expected one active flu record, found ${rows.length}`);
    [id] = rows[0];
    assert(JSON.stringify(rows[0].slice(1)) === JSON.stringify(['3', '-1', outside, given]), `The stored record is ${JSON.stringify(rows[0].slice(1))}`);
  });

  await s.step('the list shows it as done, and the reopened form shows the status and the outside provider', async () => {
    await index.close();
    index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
    const record = index.locator(`.preventionProcedure[onclick*="id=${id}"]`).first();
    await record.waitFor({ state: 'visible' });
    const colour = await record.locator('p').first().evaluate(p => getComputedStyle(p).backgroundColor);
    assert(![REFUSED_COLOUR, INELIGIBLE_COLOUR].includes(colour), `The externally completed vaccine is on the ${colour} refused/ineligible colour`);
    const editor = await s.popup(index, record, 'prevention-editor');
    assert(await editor.locator('[name="given"][value="given_ext"]').isChecked(), 'The reopened record lost Completed externally');
    assert(await editor.locator('[name="providerName"]').inputValue() === outside, 'The reopened record lost the outside provider\'s name');
    await editor.close();
  });

  await s.step('the vaccine given elsewhere counts as done, so the flu reminder goes away', async () => {
    // The list shows the record first, so a page that did not render can't pass for one without the reminder.
    await index.locator(`.preventionProcedure[onclick*="id=${id}"]`).first().waitFor({ state: 'visible' });
    const shown = await reminders();
    await index.close();
    assert(!shown.includes('Needs Flu Shot'), `The flu reminder is still shown after a flu shot given elsewhere: ${shown.slice(0, 300)}`);
  });
}

if (require.main === module) runWorkflow('prevention-completed-externally', workflow);
module.exports = { workflow };
