#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// allergy-partial-start-date: an allergy whose start is known only to the year, or the year and month.
// The Start Date box says "(yyyy-mm-dd OR yyyy-mm OR yyyy)". The date lives in allergies.start_date
// (the first day of the period) and its precision in partial_date (table_name 1 = allergies,
// field_name 1 = start date, format YYYY or YYYY-MM).
// User path: E-Chart ▸ Allergies (rx/showAllergy) ▸
//   1. allergies already stored with a year-only and a year-and-month start (as an import or an older
//      save stores them) are listed as 2019 and 2019-05, not as the first day of the period;
//   2. Modify on the year-only allergy offers 2019 in Start Date;
//   3. that amendment, changing only the reaction, keeps the start date year-only;
//   4. a new Custom Allergy typed with Start Date 2019-05 is saved year-and-month.
// Steps 3 and 4 fail on develop: the Start Date box is a flatpickr field (share/calendar/
// calendar-setup.js, since #1406) whose typed input is parsed as a full date, so the dialogue posts
// 2019-01-01 / 2019-05-01 and the precision is lost. They run last so steps 1-2 are proven first.
// Other allergy checks type full dates only (allergy-custom-lifecycle, allergy-add-penicillin,
// allergy-save-retry).
// Fixtures: the owned FAKE- patient, two allergies seeded by SQL with their partial_date rows, and the
// allergies the dialogue saves. Cleanup deletes the patient's allergies and their partial_date rows and
// asserts none remain.
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const ALLERGIES = 1;
const START_DATE = 1;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const owned = `SELECT allergyid FROM allergies WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM partial_date WHERE table_name=${ALLERGIES} AND table_id IN (${owned});
      DELETE FROM allergies WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`) === '0', 'Owned allergies were not removed');
  });
  const seed = (description, start, format) => {
    const id = sql.value(`INSERT INTO allergies (demographic_no,entry_date,start_date,DESCRIPTION,TYPECODE,reaction,nonDrug,archived,position,
        lastUpdateDate,providerNo) VALUES (${patient},CURDATE(),${sqlString(start)},${sqlString(description)},0,${sqlString(`${marker} FAKE rash`)},
        1,0,0,NOW(),${sqlString(provider)}); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id || ''), `The ${format} allergy fixture was not created`);
    sql.execute(`INSERT INTO partial_date (table_name,table_id,field_name,format) VALUES (${ALLERGIES},${id},${START_DATE},${sqlString(format)})`);
    return id;
  };
  const yearOnly = seed(`${marker.slice(-8).toUpperCase()} YEAR`, '2019-01-01', 'YYYY');
  const yearMonth = seed(`${marker.slice(-8).toUpperCase()} MONTH`, '2019-05-01', 'YYYY-MM');

  const chart = await s.chart();
  const page = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'allergy-list');
  const form = page.locator('#RxAddAllergyForm');
  /** The saved start date and its precision, as `yyyy-mm-dd|format`. */
  const stored = id => sql.value(`SELECT CONCAT(DATE(a.start_date),'|',IFNULL(p.format,'full')) FROM allergies a
    LEFT JOIN partial_date p ON p.table_name=${ALLERGIES} AND p.field_name=${START_DATE} AND p.table_id=a.allergyid
    WHERE a.allergyid=${id}`);
  /** The Start Date cell of a listed allergy, found by its column header. */
  async function listedStart(id) {
    const row = page.locator(`#allergy_${id}`);
    await row.waitFor({ state: 'visible' });
    const column = await page.evaluate(rowId => {
      const table = document.getElementById(rowId).closest('table');
      const headers = [...table.querySelectorAll('tr:first-child th, tr:first-child td')].map(cell => cell.textContent.trim());
      return headers.findIndex(text => /^Start Date$/i.test(text));
    }, `allergy_${id}`);
    assert(column >= 0, 'The allergy list has no Start Date column');
    return (await row.locator('td').nth(column).innerText()).trim();
  }
  /** Submit the reaction form; resolves with the startDate the dialogue posted. */
  async function submit() {
    const [save] = await Promise.all([
      page.waitForRequest(r => r.method() === 'POST' && new URL(r.url()).pathname.endsWith('/rx/addAllergy2')),
      form.locator('input[type="submit"]').click(),
    ]);
    return new URLSearchParams(save.postData() || '').get('startDate');
  }

  await s.step('stored year-only and year-and-month start dates are listed as typed', async () => {
    for (const [id, expected] of [[yearOnly, '2019'], [yearMonth, '2019-05']]) {
      const shown = await listedStart(id);
      assert(shown === expected, `The list shows a ${expected} start date as ${JSON.stringify(shown)}`);
    }
  });

  await s.step('Modify on the year-only allergy offers 2019', async () => {
    await page.locator(`#allergy_${yearOnly} a.modifyAllergyLink`).click();
    await form.waitFor({ state: 'visible' });
    assert(await form.locator('#allergyToArchive').inputValue() === yearOnly, 'Modify lost the allergy it amends');
    const offered = await form.locator('#startDate').inputValue();
    assert(offered === '2019', `Modify offers the start date as ${JSON.stringify(offered)}`);
  });

  await s.step('amending only the reaction keeps the start date year-only', async () => {
    await form.locator('#reactionDescription').fill(`${marker} FAKE rash, amended`);
    const sent = await submit();
    await expectValue(sql, `SELECT archived FROM allergies WHERE allergyid=${yearOnly}`, '1', 'The amendment did not archive the original');
    const amended = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient} AND archived=0 AND allergyid NOT IN (${yearOnly},${yearMonth})`);
    assert(/^[1-9]\d*$/.test(amended || ''), 'The amendment did not leave one active replacement');
    assert(stored(amended) === '2019-01-01|YYYY',
      `The amendment stored the start date as ${stored(amended)} (the dialogue posted startDate=${JSON.stringify(sent)})`);
  });

  await s.step('a new allergy typed with Start Date 2019-05 is saved year-and-month', async () => {
    const reaction = `${marker} FAKE hives`;
    await page.locator('#searchString').fill(`${marker.slice(-8).toUpperCase()} NEW`);
    const dialogs = await withExpectedDialogs(page, () => page.locator('input[value="Custom Allergy"]').click());
    assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Custom Allergy did not ask for confirmation');
    await form.waitFor({ state: 'visible' });
    await form.locator('#reactionDescription').fill(reaction);
    await form.locator('[name="nonDrug"]').selectOption('on');
    await form.locator('#startDate').fill('2019-05');
    const sent = await submit();
    await expectValue(sql, `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND reaction=${sqlString(reaction)}`,
      '1', 'The new allergy was not saved');
    const id = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient} AND reaction=${sqlString(reaction)}`);
    assert(stored(id) === '2019-05-01|YYYY-MM',
      `The new allergy stored its start date as ${stored(id)} (the dialogue posted startDate=${JSON.stringify(sent)})`);
  });
}

if (require.main === module) runWorkflow('allergy-partial-start-date', workflow);
module.exports = { workflow };
