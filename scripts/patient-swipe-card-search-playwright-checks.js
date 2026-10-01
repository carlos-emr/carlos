#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.4 patient identification, health card swipe. User paths:
// Schedule ▸ Search ▸ type an Ontario health card track into the keyword box (any mode) ▸ Search,
// and the same track swiped with no field focused (the search page's global card listener);
// Master Record ▸ Edit ▸ Swipe Card ▸ type the track ▸ Validate (demographic/ValidateSwipeCard)
// ▸ Confirm ▸ Update Record. Asserts the swipe is rewritten into a health-number search that
// finds exactly the owned patient (checked against SQL), that a malformed track is not treated
// as a health number and finds nobody, that ValidateSwipeCard renders the parsed card and its
// Confirm writes the card's version and dates into the owned demographic row, that the swipe
// popup refuses a malformed track client-side without a request, and that a malformed track
// posted straight to ValidateSwipeCard is refused (4xx) rather than rendered. The other search
// modes are patient-search-modes'. Fixtures: the owned synthetic patient, given a synthetic
// Luhn-valid HIN that no other row carries; the patient row is deleted by runWorkflow.
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

function luhnHin() {
  const body = Array.from({ length: 9 }, (_, i) => (i === 0 ? 9 : randomInt(10))).join('');
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    let digit = Number(body[i]);
    // check_hin.js mod10Check doubles the even (0-based) positions of an Ontario HIN.
    if (i % 2 === 0) { digit *= 2; if (digit > 9) digit -= 9; }
    sum += digit;
  }
  return body + String((10 - (sum % 10)) % 10);
}

// HCMagneticStripe offsets: HIN 8-18, LAST/FIRST 19-45, expiry YYMM 46-50, sex 53,
// birth date 54-62, version 62-64, issue YYMMDD 69-75; 78 or 79 characters in all.
function track({ hin, last, first, sex, dob, version, expiry, issued }) {
  const name = `${last}/${first}`.padEnd(26, ' ');
  h.assert(name.length === 26, 'The synthetic card name does not fit the stripe');
  const stripe = `%b610054${hin}^${name}^${expiry}000${sex}${dob}${version}00000${issued}000?`;
  h.assert(stripe.length === 79, 'The synthetic stripe is not 79 characters');
  return stripe;
}

async function shownPatients(page) {
  return (await page.locator('a[title="Master Demographic File"]').allTextContents())
    .map(text => text.trim()).filter(text => /^\d+$/.test(text)).sort();
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const hin = luhnHin();
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE hin=${h.sqlString(hin)}`) === '0',
    'The synthetic HIN collides with an existing record; rerun');
  // The owned patient is deleted by runWorkflow; this gives it a card to match and the
  // (fictional, X0X) postal code the record's Update validation requires.
  sql.execute(`UPDATE demographic SET hin=${h.sqlString(hin)}, ver='AA', hc_type='ON', postal='X0X0X0'
    WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  h.assert(sql.value(`SELECT GROUP_CONCAT(demographic_no) FROM demographic WHERE hin=${h.sqlString(hin)}`) === patient,
    'The synthetic HIN was not given to the owned patient alone');
  const card = { hin, last: marker, first: 'WO', sex: '2', dob: '19800102', version: 'AB', expiry: '3012', issued: '250115' };
  const stripe = track(card);
  const malformed = track({ ...card, hin: `${hin.slice(0, 5)}X${hin.slice(6)}` });

  const search = await s.popup(s.schedule, s.schedule.locator('#search a').first(), 'swipe-search');
  const keyword = search.locator('form[name="titlesearch"] input[name="keyword"]');

  await s.step('a card swiped into the keyword box searches by health number and finds the owned patient', async () => {
    await search.locator('select[name="search_mode"]').selectOption('search_name');
    await keyword.fill('');
    await keyword.type(stripe, { delay: 2 });
    await ui.clickAndAwaitReload(search, search.locator('form[name="titlesearch"] input[type="submit"]').first(),
      { label: 'the swiped search' });
    h.assert(JSON.stringify(await shownPatients(search)) === JSON.stringify([patient]),
      'The swiped card did not find exactly the patient holding that health number');
    h.assert(await search.locator('select[name="search_mode"]').inputValue() === 'search_hin'
      && await keyword.inputValue() === hin, 'The swipe was not rewritten into a health-number search');
  });

  await s.step('a malformed track is not read as a health number and finds nobody', async () => {
    await search.locator('select[name="search_mode"]').selectOption('search_name');
    await keyword.fill('');
    await keyword.type(malformed, { delay: 2 });
    await ui.clickAndAwaitReload(search, search.locator('form[name="titlesearch"] input[type="submit"]').first(),
      { label: 'the malformed swipe' });
    h.assert(await search.locator('select[name="search_mode"]').inputValue() === 'search_name',
      'A malformed track was rewritten into a health-number search');
    h.assert((await shownPatients(search)).length === 0, 'A malformed track matched a patient');
  });

  await s.step('a card swiped with no field focused submits the health-number search on its own', async () => {
    await search.close();
    const fresh = await s.popup(s.schedule, s.schedule.locator('#search a').first(), 'swipe-search-global');
    await fresh.locator('form[name="titlesearch"] input[name="keyword"]').waitFor();
    await fresh.locator('.search-header-title').first().click();
    h.assert(await fresh.evaluate(() => document.activeElement && document.activeElement.id) !== 'keyword',
      'The keyword box kept focus, so the global card listener would not be exercised');
    const navigated = fresh.waitForEvent('framenavigated', { predicate: frame => frame === fresh.mainFrame() });
    // A reader types far faster than a person; the listener drops anything slower than 100ms a key.
    await fresh.keyboard.type(stripe.slice(0, 30), { delay: 5 });
    await navigated;
    await fresh.waitForLoadState('networkidle').catch(() => {});
    h.assert(JSON.stringify(await shownPatients(fresh)) === JSON.stringify([patient]),
      'The unfocused swipe did not find exactly the patient holding that health number');
    await fresh.close();
  });

  // Opened only now: openMasterRecord leaves its search window open, and the schedule's
  // Search control would then reuse that window instead of opening the fresh one used above.
  const { masterPage: master, searchPage } = await openMasterRecord(s.context, s.schedule, s.recorder,
    { searchTerm: marker, preferredDemographicNo: patient, timeout: 20000 });
  h.assert(new URL(master.url()).searchParams.get('demographic_no') === patient, 'Search opened a patient other than the owned one');
  await searchPage.close();
  // Swipe Card sits in the record's view-mode toolbar (Edit hides it); Confirm writes the card
  // into the record's edit form, which the clinician then opens and saves.
  let swipe;

  await s.step('the Swipe Card popup refuses a malformed track without asking the server', async () => {
    swipe = await s.popup(master, master.locator('#swipeButton input[type="button"]').first(), 'swipe-card');
    const before = s.recorder.requestLog.length;
    const seen = await h.withExpectedDialogs(swipe, async () => {
      await swipe.locator('input[name="magneticStripe"]').type(stripe.slice(0, 40), { delay: 1 });
      await swipe.getByRole('button', { name: 'Validate' }).click();
      await swipe.waitForTimeout(500);
    });
    h.assert(seen.length === 1 && /Try scanning again/.test(seen[0].text), 'The short track was not refused with an alert');
    h.assert(!s.recorder.requestLog.slice(before).some(entry => entry.url.includes('/demographic/ValidateSwipeCard')),
      'A refused track still reached ValidateSwipeCard');
    h.assert(await swipe.locator('input[name="magneticStripe"]').inputValue() === '', 'The refused track was left in the box');
  });

  await s.step('Validate renders the parsed card for the owned patient as a valid health number', async () => {
    await swipe.locator('input[name="magneticStripe"]').type(stripe, { delay: 1 });
    await ui.clickAndAwaitReload(swipe, swipe.getByRole('button', { name: 'Validate' }), { label: 'Validate' });
    h.assert(new URL(swipe.url()).pathname.endsWith('/demographic/ValidateSwipeCard'), 'Validate did not reach ValidateSwipeCard');
    const field = name => swipe.locator(`input[name="${name}"]`).inputValue();
    h.assert(await field('hin') === hin && await field('ver') === 'AB', 'The card health number or version was not parsed');
    h.assert(await field('last_name') === marker.toUpperCase() && await field('first_name') === 'WO',
      'The card name was not parsed');
    h.assert([await field('year_of_birth'), await field('month_of_birth'), await field('date_of_birth')].join('') === card.dob,
      'The card birth date was not parsed');
    h.assert(/Valid/i.test(await swipe.locator('table').nth(1).innerText()), 'The card was not reported as a valid health number');
  });

  await s.step('Confirm copies the card into the record and Update Record stores its version and dates', async () => {
    const closed = swipe.waitForEvent('close');
    const seen = await h.withExpectedDialogs(swipe, () => swipe.getByRole('button', { name: 'Confirm' }).click());
    await closed;
    h.assert(seen.length === 1 && /replace the existing patient/.test(seen[0].text), 'Confirm did not ask before replacing');
    await master.locator('#editBtn').click();
    await master.locator('#editDemographic').waitFor({ state: 'visible' });
    h.assert(await master.locator('#hinBox').inputValue() === hin && await master.locator('#verBox').inputValue() === 'AB',
      'The card was not copied into the edit form');
    await ui.clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(), { label: 'Update Record' });
    const [row] = sql.rows(`SELECT hin,ver,eff_date,hc_renew_date,UPPER(last_name),first_name,
      CONCAT(year_of_birth,month_of_birth,date_of_birth),sex FROM demographic WHERE demographic_no=${patient}`);
    h.assert(row[0] === hin && row[1] === 'AB', 'The card health number and version were not stored');
    h.assert(row[2] === '2025-01-15' && row[3] === '2030-12-02', 'The card issue and renewal dates were not stored');
    h.assert(row[4] === marker.toUpperCase() && row[5] === 'WO' && row[6] === card.dob && row[7] === 'F',
      'The card identity was not stored as swiped');
  });

  await s.step('a malformed track sent straight to ValidateSwipeCard is refused, not rendered', async () => {
    const endpoint = new URL('demographic/ValidateSwipeCard', s.config.baseUrl.href + '/').href;
    const response = await s.context.request.get(endpoint, { params: { magneticStripe: malformed.slice(0, 40) } });
    h.assert(response.status() >= 400 && response.status() < 500,
      `A malformed track was not refused as a bad request (HTTP ${response.status()})`);
    h.assert(!(await response.text()).includes(hin.slice(0, 5)), 'A refused track was echoed back');
  });
}

if (require.main === module) runWorkflow('patient-swipe-card-search', workflow, { openMaster: false });
module.exports = { workflow };
