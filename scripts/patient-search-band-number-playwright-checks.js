#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient search by First Nations band (status) number: the "Band Number" mode of the search page,
 * offered only with FIRST_NATIONS_MODULE=true. patient-search-modes covers every other mode and leaves
 * this one out because it sits behind the property; nothing else searched by it.
 *
 * User path: Schedule ▸ Search ▸ search mode "Band Number" ▸ the number ▸ Search
 * (demographic/DemographicSearch; demographicsearchresults.jsp turns the number into the patient's
 * demographic number through the statusNum demographicExt row).
 * Asserted: the patient whose record carries the number is listed, and no one else; a number no
 * record carries lists no one; when two records carry the same number (a patient registered twice),
 * both are listed, as every other search mode lists every match.
 * Env: none. SKIP when the search page offers no Band Number mode (FIRST_NATIONS_MODULE is not true).
 * Fixtures: the owned FAKE- patient and a second FAKE- record with the run marker as last name, each
 * with a statusNum demographicExt row holding a number derived from the marker (checked unused first).
 * Cleanup deletes the second record and both ext rows, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  // Status numbers are ten digits; this one comes from the marker and is checked unused before it is stored.
  const number = `9${BigInt(`0x${marker.slice(-12)}`).toString().padStart(9, '0').slice(-9)}`;
  const unknown = `8${number.slice(1)}`;
  for (const value of [number, unknown]) {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicExt WHERE key_val='statusNum' AND value=${h.sqlString(value)}`) === '0',
      `Band number ${value} is already on a record`);
  }
  let twin;
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographicExt WHERE key_val='statusNum' AND value=${h.sqlString(number)} AND demographic_no IN (${patient}${twin ? `,${twin}` : ''})`);
    if (twin) sql.execute(`DELETE FROM demographic WHERE demographic_no=${twin} AND last_name=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicExt WHERE key_val='statusNum' AND value=${h.sqlString(number)}`) === '0'
      && (!twin || sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${twin}`) === '0'), 'The band number fixtures were not removed');
  });
  const carry = (demographicNo, when) => sql.execute(`INSERT INTO demographicExt (demographic_no,provider_no,key_val,value,date_time,hidden)
    VALUES (${demographicNo},${h.sqlString(provider)},'statusNum',${h.sqlString(number)},${when},'0')`);

  const { page } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#search a').first(),
    { context: s.context, recorder: s.recorder, label: 'patient-search', timeout: TIMEOUT });
  const form = () => page.locator('form[name="titlesearch"]').first();
  await form().waitFor({ state: 'attached', timeout: TIMEOUT });
  if (await form().locator('select[name="search_mode"] option[value="search_band_number"]').count() === 0) {
    throw new h.SkipCheck('The search page offers no Band Number mode (FIRST_NATIONS_MODULE is not true)');
  }
  // Stored only once the mode is offered, so a SKIP writes nothing.
  carry(patient, 'NOW() - INTERVAL 1 DAY');
  const search = async value => {
    const keyword = form().locator('input[name="keyword"]').first();
    if (!await keyword.isVisible().catch(() => false)) {
      await page.locator('#searchPopUpButton').first().click({ timeout: TIMEOUT }).catch(() => {});
      await keyword.waitFor({ state: 'visible', timeout: TIMEOUT });
    }
    await form().locator('select[name="search_mode"]').first().selectOption('search_band_number');
    await keyword.fill('');
    await keyword.type(value, { delay: 15 });
    await clickAndAwaitReload(page, form().locator('input[type="submit"]').first(), { timeout: TIMEOUT, label: 'the band number search' });
    await h.assertNotErrorPage(page, 'band number search results');
    // The row's link text is the record's own number (see patient-search-modes).
    const texts = await page.locator('a[title="Master Demographic File"]').allTextContents();
    return texts.map(text => text.trim()).filter(text => /^\d+$/.test(text)).sort();
  };

  await s.step('the band number lists the patient whose record carries it, and no one else', async () => {
    const shown = await search(number);
    h.assert(JSON.stringify(shown) === JSON.stringify([patient]), `The band number search listed ${JSON.stringify(shown)}, not only ${patient}`);
  });

  await s.step('a band number no record carries lists no one', async () => {
    const shown = await search(unknown);
    h.assert(shown.length === 0, `A band number no record carries listed ${JSON.stringify(shown)}`);
  });

  await s.step('two records that carry the same band number are both listed', async () => {
    twin = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
        provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(marker)},'Twin','1980','01','02','F','AC',${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(twin), 'The second record was not created');
    carry(twin, 'NOW()');
    const shown = await search(number);
    const expected = [patient, twin].sort();
    h.assert(JSON.stringify(shown) === JSON.stringify(expected),
      `With two records carrying the band number the search listed ${JSON.stringify(shown)}, not both ${JSON.stringify(expected)}`);
  });
}

if (require.main === module) runWorkflow('patient-search-band-number', workflow, { openMaster: false });
module.exports = { workflow };
