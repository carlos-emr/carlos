#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * A screening's own reminder controls. prevention-add-data checks that Never Remind disables the Next Date box and
 * prevention-immunization-site-next-date that a next date is stored; no check reads what either does to the patient's
 * reminders.
 * User path: E-Chart ▸ Preventions ▸ MAM ▸ Completed 30 months ago ▸ Save; the record ▸ Set Next Date ▸ Save (a month
 * ahead, then a month ago); the record ▸ Never Remind ▸ Save.
 * Asserts: a 60-year-old woman whose last mammogram was 30 months ago is told "Mammogram is overdue for this patient";
 * a next date a month ahead holds that back; a next date a month ago gives "MAM is overdue for this patient"; LAST,
 * Never Remind shows neither overdue warning, and the list still shows the mammogram.
 * Fixtures: the runWorkflow FAKE- patient, made a 60-year-old woman by SQL; its preventions and their ext rows are
 * removed in cleanup.
 */
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TYPE = 'MAM';
const INTERVAL_OVERDUE = 'Mammogram is overdue for this patient';
const NEXT_DATE_OVERDUE = 'MAM is overdue for this patient';

async function workflow(s) {
  const { sql, patient } = s;
  s.cleanup(() => sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
    WHERE p.demographic_no=${patient}; DELETE FROM preventions WHERE demographic_no=${patient}`));
  // The mammogram rules ("MAM 2", "MAM 4") apply to women from 40 to 74.
  sql.execute(`UPDATE demographic SET sex='F', year_of_birth=LPAD(YEAR(CURDATE())-60,4,'0'), month_of_birth='01',
    date_of_birth='02' WHERE demographic_no=${patient}`);
  const chart = await s.chart();
  const openIndex = () => s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  let index = await openIndex();
  let id;
  const active = () => {
    const rows = sql.rows(`SELECT id FROM preventions WHERE demographic_no=${patient}
      AND prevention_type=${sqlString(TYPE)} AND deleted=0`);
    assert(rows.length === 1 && /^[1-9]\d*$/.test(rows[0][0]), `Expected one active ${TYPE} record, found ${rows.length}`);
    return rows[0][0];
  };
  const record = () => index.locator(`.preventionProcedure[onclick*="id=${id}"]`).first();
  /** Saves the open form, reopens the list, and returns its reminders once it shows the saved record. */
  async function saveAndRead(editor) {
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
    id = active();
    await index.close();
    index = await openIndex();
    // The list shows the record first, so a page that did not render can't pass for one without a warning.
    await record().waitFor({ state: 'visible' });
    return (await index.locator('.recommendations').innerText().catch(() => '')).replace(/\s+/g, ' ');
  }
  /** Opens the record and its "Set Next Date" section, which holds both the next date and Never Remind. */
  async function openReminderSettings() {
    const editor = await s.popup(index, record(), 'prevention-editor');
    const disclosure = editor.getByRole('link', { name: /set next date/i }).first();
    if (await disclosure.isVisible()) await disclosure.click();
    await editor.locator('#nextDateDiv').waitFor({ state: 'visible' });
    return editor;
  }
  async function setNextDate(date) {
    const editor = await openReminderSettings();
    await editor.locator('#nextDate').fill(date);
    const shown = await saveAndRead(editor);
    await expectValue(sql, `SELECT DATE(next_date) FROM preventions WHERE id=${id}`, date, `The next date ${date} was not stored`);
    return shown;
  }
  const given = sql.value('SELECT DATE_SUB(CURDATE(), INTERVAL 30 MONTH)');
  const monthAhead = sql.value('SELECT DATE_ADD(CURDATE(), INTERVAL 1 MONTH)');
  const monthAgo = sql.value('SELECT DATE_SUB(CURDATE(), INTERVAL 1 MONTH)');

  await s.step('a woman of 60 whose last mammogram was 30 months ago is told it is overdue', async () => {
    const link = index.locator(`a[onclick*="ViewAddPreventionData"][onclick*="prevention=${TYPE}&"]`).first();
    const editor = await s.popup(index, link, 'prevention-editor');
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill(given);
    const shown = await saveAndRead(editor);
    await expectValue(sql, `SELECT DATE(prevention_date) FROM preventions WHERE id=${id}`, given, 'The mammogram date was not stored');
    assert(shown.includes(INTERVAL_OVERDUE), `No overdue warning for a mammogram 30 months old: ${shown.slice(0, 300)}`);
  });

  await s.step('a next date a month ahead holds the overdue warning back', async () => {
    const shown = await setNextDate(monthAhead);
    assert(!shown.includes(INTERVAL_OVERDUE) && !shown.includes(NEXT_DATE_OVERDUE),
      `An overdue warning shows although the next date is a month ahead: ${shown.slice(0, 300)}`);
  });

  await s.step('a next date that has passed gives the next-date overdue warning', async () => {
    const shown = await setNextDate(monthAgo);
    assert(shown.includes(NEXT_DATE_OVERDUE), `No overdue warning after the next date passed: ${shown.slice(0, 300)}`);
  });

  await s.step('Never Remind shows neither overdue warning, and the list still shows the mammogram', async () => {
    const editor = await openReminderSettings();
    await editor.locator('#neverWarn').check();
    const shown = await saveAndRead(editor);
    await index.close();
    await expectValue(sql, `SELECT never FROM preventions WHERE id=${id}`, '1', 'Never Remind was not stored');
    assert(!shown.includes(INTERVAL_OVERDUE) && !shown.includes(NEXT_DATE_OVERDUE),
      `An overdue warning shows although Never Remind is ticked: ${shown.slice(0, 300)}`);
  });
}

if (require.main === module) runWorkflow('prevention-screening-reminder-controls', workflow);
module.exports = { workflow };
