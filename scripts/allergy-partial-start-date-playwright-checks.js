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
//   4. a new Custom Allergy typed with the full date 2019-05-14 is saved as that day;
//   5. a new Custom Allergy whose Start Date is typed, left, then cleared is saved with no start date;
//   6. a day picked on the calendar, here 1 May 2019 after 2019-05 was typed, is saved as that day (picked with
//      the calendar button, with the calendar left open after the box lost focus, and with a click alone);
//   7. a year or a year and month keeps its precision: new allergies typed 2019 and 2019-05 are saved
//      year-only and year-and-month and listed as typed, and Modify on the stored 2019-05 allergy, after
//      clicking into Start Date and out again, still posts 2019-05 and saves it year-and-month.
// Step 7 fails on develop: the Start Date box is a flatpickr field (share/calendar/calendar-setup.js,
// since #1406) that parses what is in it as a full date when the box loses focus, so the dialogue
// posts 2019-01-01 and 2019-05-01 and the precision is lost (finding 300). Step 3 passes because the
// prefilled 2019 is never focused. Step 7 records all three cases before it reports.
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

  /** Adds a Custom Allergy with this Start Date; returns its id and the startDate the dialogue posted. */
  async function addCustom(label, fillStart) {
    const reaction = `${marker} FAKE ${label}`;
    await page.locator('#searchString').fill(`${marker.slice(-8).toUpperCase()} ${label.toUpperCase()}`);
    const dialogs = await withExpectedDialogs(page, () => page.locator('input[value="Custom Allergy"]').click());
    assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Custom Allergy did not ask for confirmation');
    await form.waitFor({ state: 'visible' });
    await form.locator('#reactionDescription').fill(reaction);
    await form.locator('[name="nonDrug"]').selectOption('on');
    await fillStart(form.locator('#startDate'));
    const sent = await submit();
    await expectValue(sql, `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND reaction=${sqlString(reaction)}`,
      '1', `The ${label} allergy was not saved`);
    const id = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient} AND reaction=${sqlString(reaction)}`);
    await page.locator(`#allergy_${id}`).waitFor({ state: 'visible' });
    return { id, sent };
  }
  /** Leaves the Start Date box the way a clinician does, by clicking the next box (Age Of Onset). The calendar
   *  opens above Start Date, over the reaction box, so the box below is the one a click can reach. */
  const leave = start => form.locator('[name="ageOfOnset"]').click().then(() => start);

  await s.step('a new allergy typed with the full date 2019-05-14 is saved as that day', async () => {
    const { id, sent } = await addCustom('day', async start => { await start.fill('2019-05-14'); await leave(start); });
    assert(stored(id) === '2019-05-14|full', `The allergy stored its start date as ${stored(id)} (posted ${JSON.stringify(sent)})`);
  });

  await s.step('a new allergy whose Start Date is cleared is saved with no start date', async () => {
    const { id, sent } = await addCustom('cleared', async start => {
      await start.fill('2019'); await leave(start);
      await start.fill(''); await leave(start);
    });
    const saved = sql.value(`SELECT CONCAT(IFNULL(DATE(start_date),'none'),'|',(SELECT COUNT(*) FROM partial_date
      WHERE table_name=${ALLERGIES} AND field_name=${START_DATE} AND table_id=${id})) FROM allergies WHERE allergyid=${id}`);
    assert(sent === '' && saved === 'none|0', `A cleared Start Date posted ${JSON.stringify(sent)} and stored ${saved}`);
  });

  await s.step('a day picked on the calendar is saved as that day, even the first of a typed month', async () => {
    const { id, sent } = await addCustom('picked', async start => {
      await start.fill('2019-05'); await leave(start);
      await form.locator('#startDateCal').click();
      await page.locator('.flatpickr-calendar.open .flatpickr-day[aria-label="May 1, 2019"]').click();
    });
    assert(sent === '2019-05-01' && stored(id) === '2019-05-01|full',
      `A picked 1 May 2019 posted ${JSON.stringify(sent)} and stored ${stored(id)}`);
    // The same pick while the calendar stays open after the box lost focus (as when the clinician switches
    // window): the typed 2019-05 has been read again by then, and the picked day must still win.
    const open = await addCustom('picked open', async start => {
      await start.click();
      await start.fill('2019-05');
      await start.evaluate(box => box.blur());
      await page.locator('.flatpickr-calendar.open .flatpickr-day[aria-label="May 1, 2019"]').click();
    });
    assert(open.sent === '2019-05-01' && stored(open.id) === '2019-05-01|full',
      `1 May 2019 picked after the box lost focus posted ${JSON.stringify(open.sent)} and stored ${stored(open.id)}`);
    // The same pick made by a screen reader's or voice control's activate, which sends the click alone.
    const activated = await addCustom('picked click', async start => {
      await start.click();
      await start.fill('2019-05');
      await start.evaluate(box => box.blur());
      await page.locator('.flatpickr-calendar.open .flatpickr-day[aria-label="May 1, 2019"]').dispatchEvent('click');
    });
    assert(activated.sent === '2019-05-01' && stored(activated.id) === '2019-05-01|full',
      `1 May 2019 activated with a click alone posted ${JSON.stringify(activated.sent)} and stored ${stored(activated.id)}`);
  });

  await s.step('a year or a year and month, typed or kept on Modify, is saved with that precision', async () => {
    const found = {};
    const year = await addCustom('year', async start => { await start.fill('2019'); await leave(start); });
    found.year = { sent: year.sent, stored: stored(year.id), listed: await listedStart(year.id) };
    const month = await addCustom('month', async start => { await start.fill('2019-05'); await leave(start); });
    found.month = { sent: month.sent, stored: stored(month.id), listed: await listedStart(month.id) };
    // Modify on the stored 2019-05 allergy, then click into Start Date, press Escape and click out again.
    await page.locator(`#allergy_${yearMonth} a.modifyAllergyLink`).click();
    await form.waitFor({ state: 'visible' });
    const start = form.locator('#startDate');
    const offered = await start.inputValue();
    await start.click();
    await start.press('Escape');
    await leave(start);
    found.modify = { offered, afterFocus: await start.inputValue() };
    found.modify.sent = await submit();
    await expectValue(sql, `SELECT archived FROM allergies WHERE allergyid=${yearMonth}`, '1', 'The amendment did not archive the original');
    const replacement = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient} AND archived=0
      AND allergyid NOT IN (${[yearOnly, yearMonth, year.id, month.id].join(',')}) AND reaction=${sqlString(`${marker} FAKE rash`)}`);
    assert(/^[1-9]\d*$/.test(replacement || ''), 'The amendment did not leave one active replacement');
    found.modify.stored = stored(replacement);
    console.log(`    found: ${JSON.stringify(found)}`);
    const expected = {
      year: { sent: '2019', stored: '2019-01-01|YYYY', listed: '2019' },
      month: { sent: '2019-05', stored: '2019-05-01|YYYY-MM', listed: '2019-05' },
      modify: { offered: '2019-05', afterFocus: '2019-05', sent: '2019-05', stored: '2019-05-01|YYYY-MM' },
    };
    assert(JSON.stringify(found) === JSON.stringify(expected), `Partial start dates were saved as ${JSON.stringify(found)}`);
  });
}

if (require.main === module) runWorkflow('allergy-partial-start-date', workflow);
module.exports = { workflow };
