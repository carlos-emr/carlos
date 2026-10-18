#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Master Record edit: the "Archived paper chart" indicator with its date, and the country of origin. Both are shown on
 * every install (the Aboriginal box next to them needs FIRST_NATIONS_MODULE) and no other check saves or re-reads them;
 * gap-records-demographic-edit-clinical covers the identity, health card, status and contact fields.
 * User path: Schedule ▸ Search ▸ Master Record ▸ Edit ▸ Archived paper chart Yes, a date, a country of origin ▸ Update
 * Record ▸ the refreshed Master Record ▸ Edit again ▸ Archived paper chart No, country Not Set ▸ Update Record.
 * Asserts: Yes stores paper_chart_archived=YES with the typed date and the program the page filled in (demographicExt)
 * and the country on the patient, archiving the patient once; the read view and the reopened Edit form show all three;
 * No clears the date and program in the page, stores NO with an empty date, the country goes back to Not Set, and the
 * read view no longer shows the date or a country.
 * Fixtures: the runWorkflow FAKE- patient, whose demographicExt, archive, extension-archive and custom rows are removed.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const ARCHIVED_ON = '2025-06-07';

async function openEdit(master) {
  await master.locator('#editBtn').click();
  await master.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
  await master.locator('#updateButton').waitFor({ state: 'visible', timeout: TIMEOUT });
}

async function update(master) {
  await ui.clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(), { timeout: TIMEOUT, label: 'Update Record' });
}

/** The read view's "Archived paper chart" section, as label → shown value. */
async function paperChartSection(master) {
  return master.locator('#paperChartIndicator li').evaluateAll(items => Object.fromEntries(items.map(li => [
    (li.querySelector('.label')?.textContent || '').replace(/[:\s]+$/, '').trim(),
    (li.querySelector('.info')?.textContent || '').trim(),
  ])));
}

async function workflow(s) {
  const { sql, patient, master } = s;
  // The parent cleanup removes the patient's demographicExt and demographicArchive rows; an update also writes
  // demographicExtArchive (keyed by archive id) and demographiccust, so those go first.
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographicExtArchive WHERE archiveId IN (SELECT id FROM demographicArchive WHERE demographic_no=${patient})
      OR demographic_no=${patient};
      DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient}
        OR archiveId IN (SELECT id FROM demographicArchive WHERE demographic_no=${patient}))`) === '0',
    'The owned patient\'s custom-field and extension archive rows were not removed');
  });
  const ext = key => sql.value(`SELECT IFNULL((SELECT value FROM demographicExt WHERE demographic_no=${patient}
    AND key_val=${h.sqlString(key)} ORDER BY id DESC LIMIT 1),'(none)')`);
  const [countryId, countryName] = sql.rows(`SELECT country_id, country_name FROM country_codes
    WHERE country_name='Canada' ORDER BY id LIMIT 1`)[0] || [];
  h.assert(countryId, 'The country list has no Canada to choose');
  let program;

  await s.step('Archived paper chart Yes with a date and a country of origin are stored and the patient is archived once', async () => {
    h.assert(ext('paper_chart_archived') === '(none)' && sql.value(`SELECT IFNULL(country_of_origin,'') FROM demographic WHERE demographic_no=${patient}`) === '',
      'The fixture patient already has an archived paper chart or a country of origin');
    await openEdit(master);
    await master.locator('#paper_chart_archived').selectOption('YES');
    program = await master.locator('#paper_chart_archived_program').inputValue();
    await master.locator('#paper_chart_archived_date').fill(ARCHIVED_ON);
    await master.locator('#countryOfOrigin').selectOption(countryId);
    await update(master);
    h.assert(ext('paper_chart_archived') === 'YES', `paper_chart_archived is ${ext('paper_chart_archived')}, not YES`);
    h.assert(ext('paper_chart_archived_date') === ARCHIVED_ON, `The archive date is ${ext('paper_chart_archived_date')}`);
    h.assert(ext('paper_chart_archived_program') === program, 'The stored archive program is not the one the page filled in');
    await expectValue(sql, `SELECT country_of_origin FROM demographic WHERE demographic_no=${patient}`, countryId,
      'The country of origin was not stored');
    await expectValue(sql, `SELECT COUNT(*) FROM demographicArchive WHERE demographic_no=${patient}`, '1',
      'The update did not archive the patient exactly once');
  });

  await s.step('the refreshed Master Record and the reopened Edit form show the archived chart and the country', async () => {
    const shown = await paperChartSection(master);
    const values = Object.values(shown);
    h.assert(values[0] === 'Yes' && values[1] === ARCHIVED_ON && values[2] === program,
      `The read view's paper chart section shows ${JSON.stringify(shown)}`);
    const text = (await master.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(countryName), `The read view does not show the country of origin ${countryName}`);
    await openEdit(master);
    h.assert(await master.locator('#paper_chart_archived').inputValue() === 'YES', 'The Edit form lost Archived paper chart Yes');
    h.assert(await master.locator('#paper_chart_archived_date').inputValue() === ARCHIVED_ON, 'The Edit form lost the archive date');
    h.assert(await master.locator('#countryOfOrigin').inputValue() === countryId, 'The Edit form lost the country of origin');
  });

  await s.step('No clears the date and program, stores NO, and Not Set takes the country away from the read view', async () => {
    await master.locator('#paper_chart_archived').selectOption('NO');
    h.assert(await master.locator('#paper_chart_archived_date').inputValue() === ''
      && await master.locator('#paper_chart_archived_program').inputValue() === '', 'Choosing No left the archive date or program filled in');
    await master.locator('#countryOfOrigin').selectOption('-1');
    await update(master);
    h.assert(ext('paper_chart_archived') === 'NO', `paper_chart_archived is ${ext('paper_chart_archived')}, not NO`);
    h.assert(['', '(none)'].includes(ext('paper_chart_archived_date')), `The cleared archive date was stored as ${ext('paper_chart_archived_date')}`);
    h.assert(['', '-1'].includes(sql.value(`SELECT IFNULL(country_of_origin,'') FROM demographic WHERE demographic_no=${patient}`)),
      'Not Set did not clear the country of origin');
    const values = Object.values(await paperChartSection(master));
    h.assert(values[0] === 'No' && values[1] === '' && values[2] === '', `The read view's paper chart section shows ${JSON.stringify(values)}`);
    const text = (await master.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(!text.includes(countryName), 'The read view still shows the cleared country of origin');
    await expectValue(sql, `SELECT COUNT(*) FROM demographicArchive WHERE demographic_no=${patient}`, '2',
      'The second update did not archive the patient exactly once more');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-paper-chart-country', workflow, { openPatient: true });
