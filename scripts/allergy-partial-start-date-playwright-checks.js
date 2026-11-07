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
// Covers stored/imported precision only. Typed partial-date entry is outside the
// supported UI contract (#5576). Fixtures and amendments are marker-owned and
// removed with their partial_date metadata on success, failure and interruption.
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const ALLERGIES = 1;
const START_DATE = 1;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const owned = `SELECT allergyid FROM allergies WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    const partialIds = sql.value(`SELECT GROUP_CONCAT(allergyid) FROM allergies WHERE demographic_no=${patient}`);
    sql.execute(`DELETE FROM partial_date WHERE table_name=${ALLERGIES} AND table_id IN (${owned});
      DELETE FROM allergies WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`) === '0', 'Owned allergies were not removed');
    if (partialIds) assert(sql.value(`SELECT COUNT(*) FROM partial_date WHERE table_name=${ALLERGIES} AND table_id IN (${partialIds})`) === '0',
      'Owned allergy precision metadata was not removed');
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
  /** Submit the reaction form; resolves, once the allergy list has reloaded, with the startDate the dialogue posted. */
  async function submit() {
    const [save] = await Promise.all([
      page.waitForRequest(r => r.method() === 'POST' && new URL(r.url()).pathname.endsWith('/rx/addAllergy2')),
      page.waitForEvent('load'),
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

  for (const [source, value, storage] of [[yearOnly, '2019', '2019-01-01|YYYY'], [yearMonth, '2019-05', '2019-05-01|YYYY-MM']]) {
    await s.step(`Modify offers and preserves the stored precision ${value}`, async () => {
      await page.locator(`#allergy_${source} a.modifyAllergyLink`).click();
      await form.waitFor({ state: 'visible' });
      assert(await form.locator('#allergyToArchive').inputValue() === source, 'Modify lost the allergy it amends');
      assert(await form.locator('#startDate').inputValue() === value, 'Modify lost the stored start-date precision');
      const reaction = `${marker} FAKE amended ${value}`;
      await form.locator('#reactionDescription').fill(reaction);
      const sent = await submit();
      await expectValue(sql, `SELECT archived FROM allergies WHERE allergyid=${source}`, '1', 'The amendment did not archive the original');
      const amended = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient} AND archived=0 AND reaction=${sqlString(reaction)}`);
      assert(/^[1-9]\d*$/.test(amended || ''), 'The amendment did not leave one active replacement');
      assert(stored(amended) === storage,
        `The amendment stored ${stored(amended)} instead of ${storage} (posted ${JSON.stringify(sent)})`);
      assert(await listedStart(amended) === value, 'The amended allergy list lost its partial precision');
    });
  }

}

if (require.main === module) runWorkflow('allergy-partial-start-date', workflow);
module.exports = { workflow };
