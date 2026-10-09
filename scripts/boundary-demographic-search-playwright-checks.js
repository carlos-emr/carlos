#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient search by names and addresses that carry special characters (wave 6, boundary values).
 * User path: Schedule > Search > type a name or address > Search > sortable result header.
 * Repeat searches in the same popup. The booking screen's name search from an appointment slot
 * is not driven here.
 * Asserts: a control search by the plain run tag finds the seeded patients; then a surname with an
 * apostrophe (O'Brien), a first name with an accent (René), a first name in CJK and an address with an
 * apostrophe, and the same address typed through to its ampersand, are each found by the text a user would type. The patients are stored
 * correctly (checked byte for byte first) so a miss is the search, not the data. Sort postbacks must retain the keyword.
 * SQL-shaped input must not widen results, and markup-shaped keywords must remain literal text in the rendered page.
 * Fixtures: two synthetic patients inserted by SQL whose surname starts with the run tag; cleanup removes
 * their rows and asserts none remain.
 * Implements the wave-6 "boundary values" pattern (special characters round trip: "search finds it").
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const b = require('./lib/boundary-values');
const k = require('./lib/search-sort-helpers');
const { runWorkflow } = require('./lib/workflow-session');
const { removeMarkedPatients } = require('./lib/gap-records-fixtures');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, marker } = s;
  const tag = 'FAKE-PW' + marker.slice(-6);
  s.cleanup(() => b.removeTaggedPatients(sql, tag, marker, removeMarkedPatients));
  const T = b.TOKENS;
  const seed = (last, first, address) => sql.value(`INSERT INTO demographic
    (last_name,first_name,address,city,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(last)},${h.sqlString(first)},${h.sqlString(address)},'Testville','1980','01','02','F','AC',${h.sqlString(s.provider)},'ON','ON','NR',NOW());
    SELECT LAST_INSERT_ID()`);
  const apostropheId = seed((tag + 'A' + T.apostrophe).toUpperCase(), T.latin.toUpperCase(), `12 O'Neil St & "B" René 山田`);
  const cjkId = seed((tag + 'B' + T.cjk).toUpperCase(), T.cjk, '1 Plain Road');
  [apostropheId, cjkId].forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'A patient fixture was not created'));

  await s.step('the fixture patients are stored exactly as seeded', async () => {
    b.assertStored(sql, 'demographic', 'last_name', `demographic_no=${apostropheId}`, (tag + 'A' + T.apostrophe).toUpperCase(), 'Fixture surname');
    b.assertStored(sql, 'demographic', 'first_name', `demographic_no=${apostropheId}`, T.latin.toUpperCase(), 'Fixture accented first name');
    b.assertStored(sql, 'demographic', 'address', `demographic_no=${apostropheId}`, `12 O'Neil St & "B" René 山田`, 'Fixture quoted Unicode address');
    b.assertStored(sql, 'demographic', 'last_name', `demographic_no=${cjkId}`, (tag + 'B' + T.cjk).toUpperCase(), 'Fixture CJK surname');
    b.assertStored(sql, 'demographic', 'first_name', `demographic_no=${cjkId}`, T.cjk, 'Fixture CJK first name');
  });

  const search = await ui.clickOpensPopup(s.schedule, s.schedule.locator('#search a, a:has-text("Search")').first(),
    { context: s.context, recorder: s.recorder, label: 'name-search', timeout: TIMEOUT });
  async function find(mode, term, id) {
    await search.locator('select[name="search_mode"]').selectOption(mode);
    await search.locator('#keyword, input[name="keyword"]').first().fill(term);
    await ui.clickAndAwaitReload(search, search.locator("input[type='submit']").first(), { timeout: TIMEOUT, label: `search ${mode}` });
    const submitted = mode === 'search_name' ? term.toLowerCase() : term;
    h.assert(await search.locator('#search-sort input[name="keyword"]').inputValue() === submitted,
      'The search keyword changed while being rendered into the sort form');
    return await search.locator(`a[title="Master Demographic File"][onclick*="demographic_no=${id}"]`).count() > 0;
  }

  await s.step('a control search by the plain run tag finds both patients', async () => {
    h.assert(await find('search_name', tag, apostropheId) && await find('search_name', tag, cjkId),
      'Searching by the plain run tag did not find the seeded patients (fixture or page problem)');
  });

  await s.step('searches by an apostrophe surname, an accented first name, a CJK first name and an apostrophe address find the patient', async () => {
    const misses = [];
    const cases = [
      ['search_name', tag + 'A' + T.apostrophe, apostropheId, 'surname with an apostrophe'],
      ['search_name', `${tag}A,${T.latin.split(' ')[0]}`, apostropheId, 'first name with an accent'],
      ['search_name', `${tag}B,${T.cjk.slice(0, 2)}`, cjkId, 'first name in CJK'],
      ['search_name', tag + 'B' + T.cjk, cjkId, 'surname in CJK'],
      ['search_address', "12 O'Neil St", apostropheId, 'address with an apostrophe'],
      ['search_address', "12 O'Neil St &", apostropheId, 'address with an apostrophe and an ampersand'],
      ['search_address', `12 O'Neil St & "B" René 山田`, apostropheId, 'address with quotes, accents and CJK'],
    ];
    for (const [mode, term, id, label] of cases) {
      if (!await find(mode, term, id)) {
        misses.push(label);
        continue;
      }
      await k.clickSort(search, 'demographic_no');
      h.assert(await search.locator(`a[title="Master Demographic File"][onclick*="demographic_no=${id}"]`).count() > 0,
        `Sorting lost the match for ${label}`);
      h.assert(await search.locator('#search-sort input[name="keyword"]').inputValue()
        === (mode === 'search_name' ? term.toLowerCase() : term), `Sorting altered the keyword for ${label}`);
    }
    h.assert(misses.length === 0, `Patient search did not find a stored patient by: ${misses.join('; ')} `
      + '(the JSP must pass the original keyword to the bound DAO query)');
  });

  await s.step('SQL-shaped keywords do not return unrelated patients', async () => {
    await find('search_address', "' OR 1=1 --", apostropheId);
    h.assert((await k.readResultRows(search)).length === 0, 'SQL-shaped input widened the patient search');
  });

  await s.step('markup-shaped keywords remain literal text and cannot create executable elements', async () => {
    const term = '<img src="x" onerror="window.__searchKeywordXss=1">';
    await find('search_address', term, apostropheId);
    h.assert((await k.readResultRows(search)).length === 0, 'Markup-shaped input returned unrelated patients');
    h.assert((await search.locator('#searchResults').innerText()).includes(term),
      'The rendered search description did not preserve the literal keyword');
    h.assert(await search.locator('#searchResults img[src="x"]').count() === 0,
      'A search keyword was interpreted as an HTML element');
    h.assert(await search.evaluate(() => window.__searchKeywordXss) === undefined,
      'The search keyword executed JavaScript');
  });
  await search.close();
}

if (require.main === module) runWorkflow('boundary-demographic-search', workflow, { openPatient: false });
module.exports = { workflow };
