#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Immunization site and next date. The injection layout's Site box (location), its "Other" site text (location2,
 * shown by changeSite()) and the next date's value are saved by no other check: prevention-brand-picker covers the
 * name, DIN, lot, dose, route and manufacturer, and prevention-screening-result covers Never Remind.
 * User path: E-Chart ▸ Preventions ▸ Td ▸ Completed, date, Site ▸ Save; the record ▸ Site Other with a typed site ▸
 * Save; the record ▸ a named site ▸ Save; the record ▸ Set Next Date with a date ▸ Save; the record again.
 * Asserts: the chosen site reaches preventionsExt location and reopens selected with the Other box hidden; Other with
 * a typed site stores both and reopens with the box shown and filled; choosing a named site again clears the typed
 * site in the page and stores it empty; a next date is stored (never=0) and reopens in its box.
 * Fixtures: the runWorkflow FAKE- patient; its preventions and their ext rows are removed in cleanup.
 */
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TYPE = 'Td';
const SITE = 'Anterolateral Thigh Lt';
const NAMED_AGAIN = 'Superior Deltoid Lt';
const NEXT_DATE = '2036-05-06';

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
    WHERE p.demographic_no=${patient}; DELETE FROM preventions WHERE demographic_no=${patient}`));
  const chart = await s.chart();
  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  let editor;
  let id;
  const active = () => {
    const rows = sql.rows(`SELECT id FROM preventions WHERE demographic_no=${patient}
      AND prevention_type=${sqlString(TYPE)} AND deleted=0`);
    assert(rows.length === 1 && /^[1-9]\d*$/.test(rows[0][0]), `Expected one active ${TYPE} record, found ${rows.length}`);
    return rows[0][0];
  };
  const ext = key => sql.value(`SELECT IFNULL((SELECT GROUP_CONCAT(val ORDER BY id SEPARATOR '|')
    FROM preventionsExt WHERE prevention_id=${id} AND keyval=${sqlString(key)}),'(none)')`);
  const record = () => index.locator(`.preventionProcedure[onclick*="id=${id}"]`).first();
  async function open() { editor = await s.popup(index, record(), 'prevention-editor'); }
  async function save() {
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
  }
  async function saveEdit() {
    const previous = id;
    await save();
    id = active();
    assert(id !== previous, 'Saving the edit did not store a replacement record');
    await expectValue(sql, `SELECT deleted FROM preventions WHERE id=${previous}`, '1', 'The edited record was not archived');
    await record().waitFor({ state: 'visible' });
  }
  const otherSite = `${marker} left calf`;

  await s.step('a Td given in the thigh stores the site, and the record reopens with it selected', async () => {
    const link = index.locator(`a[onclick*="ViewAddPreventionData"][onclick*="prevention=${TYPE}&"]`).first();
    editor = await s.popup(index, link, 'prevention-editor');
    assert(await editor.locator('[name="prevention"]').inputValue() === TYPE, `The list opened a form for another type than ${TYPE}`);
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill('2026-04-05');
    await editor.locator('#location').selectOption(SITE);
    assert(!(await editor.locator('#locationDiv').isVisible()), 'A named site showed the Other site box');
    await save();
    id = active();
    await record().waitFor({ state: 'visible' });
    assert(ext('location') === SITE, `The stored site is ${ext('location')}`);
    await open();
    assert(await editor.locator('#location').inputValue() === SITE, 'The reopened record lost its site');
    assert(!(await editor.locator('#locationDiv').isVisible()), 'The reopened record shows the Other site box for a named site');
  });

  await s.step('Other with a typed site stores both, and the record reopens with the Other box shown and filled', async () => {
    await editor.locator('#location').selectOption('Other');
    await editor.locator('#locationDiv').waitFor({ state: 'visible' });
    await editor.locator('#location2').fill(otherSite);
    await saveEdit();
    assert(ext('location') === 'Other' && ext('location2') === otherSite, `The stored site is ${ext('location')} / ${ext('location2')}`);
    await open();
    assert(await editor.locator('#location').inputValue() === 'Other', 'The reopened record lost the Other site');
    assert(await editor.locator('#locationDiv').isVisible(), 'The reopened record hides the Other site box');
    assert(await editor.locator('#location2').inputValue() === otherSite, 'The reopened record lost the typed site');
  });

  await s.step('choosing a named site again clears the typed site in the page and stores it empty', async () => {
    await editor.locator('#location').selectOption(NAMED_AGAIN);
    assert(!(await editor.locator('#locationDiv').isVisible()) && await editor.locator('#location2').inputValue() === '',
      'Choosing a named site left the Other site box or its text');
    await saveEdit();
    assert(ext('location') === NAMED_AGAIN, `The stored site is ${ext('location')}`);
    assert(['', '(none)'].includes(ext('location2')), `The cleared typed site was stored as ${ext('location2')}`);
  });

  await s.step('Set Next Date stores the date without Never Remind, and the record reopens with it', async () => {
    await open();
    await editor.getByRole('link', { name: /set next date/i }).first().click();
    await editor.locator('#nextDateDiv').waitFor({ state: 'visible' });
    assert(!(await editor.locator('#neverWarn').isChecked()), 'Never Remind is ticked on a record that never set it');
    await editor.locator('#nextDate').fill(NEXT_DATE);
    await saveEdit();
    await expectValue(sql, `SELECT CONCAT(IFNULL(DATE(next_date),'none'),'|',never) FROM preventions WHERE id=${id}`,
      `${NEXT_DATE}|0`, 'The next date was not stored, or Never Remind was');
    assert(ext('location') === NAMED_AGAIN, 'Setting the next date changed the stored site');
    await open();
    assert(await editor.locator('#nextDate').inputValue() === NEXT_DATE, 'The reopened record lost its next date');
    await editor.close();
  });
}

if (require.main === module) runWorkflow('prevention-immunization-site-next-date', workflow);
module.exports = { workflow };
