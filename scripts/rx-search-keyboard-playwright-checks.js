#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-search-keyboard: the Rx page's drug search box (#searchString, SearchDrug3.jsp) from the keyboard. Typing
// three or more letters opens a jQuery UI autocomplete list of DrugRef products. A keypress guard stops Enter from
// submitting #drugForm, and the widget's own Enter picks the highlighted row: searchResultsHandler posts
// rx/WriteScript parameterValue=createNewRx with the row's drug id and name, and the card it returns is staged in
// the session (nothing is saved). Other checks pick a row with the mouse (rx-edit-discontinue,
// boundary-rx-drug-name, allergy-rx-alert) or use the Search popup (popup-opener-rx-drug-search); none used the
// keyboard.
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ type in the drug search box ▸ arrow keys, Enter,
// Escape.
//   1. typing a product name lists its products; Enter with nothing highlighted picks nothing and submits nothing;
//   2. ArrowDown highlights the first row without filling the box, and Enter stages it: one createNewRx naming
//      that row's drug id and name, and one new card;
//   3. ArrowDown twice then Enter stages the second row;
//   4. Escape closes the list, and Enter then picks nothing;
//   5. staging wrote no drugs row;
//   6. LAST: a search with no match offers no row that can be picked: ArrowDown, Enter and a click post nothing.
// Each staged card brings its own autocomplete lists (the instructions box's), so every list locator here is scoped
// to the search box's own widget, never to the first ul.ui-autocomplete. A staged card also focuses its
// instructions box when it renders; the check waits for that before typing the next search, or the keystrokes
// would land in the card.
// RX_SEARCH_TERM (default LIPITOR) must list at least two DrugRef products; needs carlos-emr-drugref.
// Fixtures: the owned FAKE- patient only. Staging is session-only; cleanup removes the patient's drugs and
// prescriptions by key in case a regression saved one, and asserts none remain. No clinic-wide state is changed.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const SEARCH_TERM = process.env.RX_SEARCH_TERM || 'LIPITOR';
// Letters DrugRef has no product for.
const NO_MATCH_TERM = 'ZZQXNOMATCH';
const QUIET_MS = 2000;

const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
/** Waits for the response the predicate names; a timeout says which request never answered. */
const answered = (page, predicate, what) => page.waitForResponse(predicate, { timeout: 60000 })
  .catch(error => { throw new Error(`${what} never answered: ${error.message.split('\n')[0]}`); });
const formOf = request => new URLSearchParams(request.postData() || '');

async function workflow(s) {
  const { sql, patient } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
  });

  // The Master Record's Prescriptions link, by the URL it opens (an onclick on older pages, a .patient-nav href since #4468).
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(),
    'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  const box = rx.locator('#searchString');
  await box.waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');
  const widget = `#${await rx.evaluate(() => jQuery('#searchString').autocomplete('widget').attr('id'))}`;
  h.assert(/^#ui-id-\d+$/.test(widget), `The drug search box has no autocomplete list (${widget})`);
  const list = rx.locator(widget);
  const rows = rx.locator(`${widget} li.ui-menu-item`);
  const highlighted = () => rx.locator(`${widget} .ui-state-active`).allInnerTexts();
  const cardIds = () => rx.locator("[id^='drugName_']").evaluateAll(els => els.map(el => el.id.slice('drugName_'.length)));

  const picks = [];
  rx.on('request', request => {
    if (request.method() === 'POST' && h.pathOnly(request.url()).endsWith('/rx/WriteScript')
        && formOf(request).get('parameterValue') === 'createNewRx') {
      picks.push({ drugId: formOf(request).get('drugId'), text: formOf(request).get('text') });
    }
  });
  let navigations = 0;
  rx.on('framenavigated', frame => { if (frame === rx.mainFrame()) navigations++; });

  /** Types a term into the search box and waits for its search to answer. */
  async function search(term) {
    await box.fill('');
    const [response] = await Promise.all([
      answered(rx, response => isPost('/rx/searchDrug')(response) && formOf(response.request()).get('query') === term.toUpperCase(),
        `the drug search for ${term}`),
      box.pressSequentially(term, { delay: 30 }),
    ]);
    h.assert(response.status() === 200, `The drug search for ${term} answered HTTP ${response.status()}`);
  }
  /** Searches the product term and returns the listed rows, as the page rendered them. */
  async function listProducts() {
    // The rows, not the request: a repeated term may come from the widget's cache.
    await box.fill('');
    await box.pressSequentially(SEARCH_TERM, { delay: 30 });
    await rows.first().waitFor({ state: 'visible', timeout: 60000 });
    return rows.evaluateAll(lis => lis.map(li => ({ id: li.querySelector('a') && li.querySelector('a').id, text: li.innerText.trim() })));
  }
  /** Presses Enter and waits for the pick's createNewRx and its new card; returns the request and the card id. */
  async function enterStages(row) {
    const before = await cardIds();
    const [response] = await Promise.all([
      answered(rx, response => isPost('/rx/WriteScript')(response) && formOf(response.request()).get('parameterValue') === 'createNewRx',
        `staging ${row.text}`),
      box.press('Enter'),
    ]);
    h.assert(response.status() === 200, `Staging ${row.text} answered HTTP ${response.status()}`);
    const form = formOf(response.request());
    h.assert(form.get('drugId') === row.id && form.get('text') === row.text,
      `Enter staged ${form.get('drugId')} "${form.get('text')}", not the highlighted row ${row.id} "${row.text}"`);
    await rx.waitForFunction(count => document.querySelectorAll("[id^='drugName_']").length === count, before.length + 1, { timeout: 30000 });
    const added = (await cardIds()).filter(id => !before.includes(id));
    h.assert(added.length === 1, `Enter staged ${added.length} new cards for ${row.text}`);
    h.assert((await rx.locator(`#drugName_${added[0]}`).inputValue()).includes(row.text), `The new card does not name ${row.text}`);
    // The card takes focus for its instructions once it renders; typing before then would land there.
    await rx.waitForFunction(id => document.activeElement && document.activeElement.id === `instructions_${id}`, added[0], { timeout: 20000 });
    h.assert(await box.inputValue() === '', `The search box still reads "${await box.inputValue()}" after the pick`);
    h.assert(!(await list.isVisible()), 'The list stayed open after the pick');
    return added[0];
  }

  let products;
  await s.step('typing a product name lists its products; Enter with nothing highlighted picks nothing and submits nothing', async () => {
    products = await listProducts();
    h.assert(products.length >= 2 && products.every(row => /^[1-9]\d*$/.test(row.id || '')),
      `The search for ${SEARCH_TERM} listed ${JSON.stringify(products)}; set RX_SEARCH_TERM to a name with two or more DrugRef products`);
    const url = rx.url();
    h.assert((await highlighted()).length === 0, 'A row was highlighted before any arrow key');
    await box.press('Enter');
    await rx.waitForTimeout(QUIET_MS);
    h.assert(picks.length === 0, `Enter with nothing highlighted staged ${JSON.stringify(picks)}`);
    h.assert(navigations === 0 && rx.url() === url, `Enter with nothing highlighted navigated to ${rx.url()}`);
    h.assert(await box.inputValue() === SEARCH_TERM, `Enter changed the search box to "${await box.inputValue()}"`);
    h.assert(await list.isVisible(), 'Enter with nothing highlighted closed the list');
    h.assert((await cardIds()).length === 0, 'Enter with nothing highlighted staged a card');
  });

  await s.step('ArrowDown highlights the first row without filling the box, and Enter stages that row', async () => {
    await box.press('ArrowDown');
    h.assert(JSON.stringify(await highlighted()) === JSON.stringify([products[0].text]),
      `ArrowDown highlighted ${JSON.stringify(await highlighted())}, not ${products[0].text}`);
    h.assert(await box.inputValue() === SEARCH_TERM, `ArrowDown put "${await box.inputValue()}" in the search box`);
    await enterStages(products[0]);
    h.assert(picks.length === 1, `One Enter staged ${picks.length} times`);
  });

  await s.step('ArrowDown twice then Enter stages the second row', async () => {
    const listed = await listProducts();
    h.assert(JSON.stringify(listed) === JSON.stringify(products), `The second search listed ${JSON.stringify(listed)}`);
    await box.press('ArrowDown');
    await box.press('ArrowDown');
    h.assert(JSON.stringify(await highlighted()) === JSON.stringify([products[1].text]),
      `ArrowDown twice highlighted ${JSON.stringify(await highlighted())}, not ${products[1].text}`);
    await enterStages(products[1]);
    h.assert((await cardIds()).length === 2 && picks.length === 2, `Two picks left ${(await cardIds()).length} cards and ${picks.length} requests`);
  });

  await s.step('Escape closes the list, and Enter then picks nothing', async () => {
    await listProducts();
    await box.press('Escape');
    await list.waitFor({ state: 'hidden', timeout: 10000 });
    await box.press('Enter');
    await rx.waitForTimeout(QUIET_MS);
    h.assert(picks.length === 2, `Enter after Escape staged ${JSON.stringify(picks.slice(2))}`);
    h.assert(await box.inputValue() === SEARCH_TERM, `Enter after Escape changed the search box to "${await box.inputValue()}"`);
    h.assert((await cardIds()).length === 2, 'Enter after Escape staged a card');
  });

  await s.step('staging wrote no drugs row', async () => {
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0', 'Staging wrote a drugs row before Save');
  });

  // Last: the no-match row, which the drug search lists but no longer lets anyone pick.
  await s.step('a search with no match offers no row that can be picked: ArrowDown, Enter and a click post nothing', async () => {
    const before = await cardIds();
    const messages = await rx.locator('.rx-stage-message').count();
    await search(NO_MATCH_TERM);
    await rx.waitForTimeout(QUIET_MS);
    const offered = await rows.allInnerTexts();
    await box.press('ArrowDown');
    await box.press('Enter');
    await rx.waitForTimeout(QUIET_MS);
    if (await rows.first().isVisible()) {
      await rows.first().click();
      await rx.waitForTimeout(QUIET_MS);
    }
    h.assert(picks.length === 2, `With no match (rows ${JSON.stringify(offered)}), the search staged ${JSON.stringify(picks.slice(2))}`);
    h.assert(await rx.locator('.rx-stage-message').count() === messages,
      `With no match, the staging pane showed ${JSON.stringify(await rx.locator('.rx-stage-message').allInnerTexts())}`);
    h.assert(JSON.stringify(await cardIds()) === JSON.stringify(before), 'With no match, a card was staged');
  });
}

if (require.main === module) runWorkflow('rx-search-keyboard', workflow, { openPatient: true });
module.exports = { workflow };
