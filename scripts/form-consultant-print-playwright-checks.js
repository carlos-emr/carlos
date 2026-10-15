#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #4370: on the consultant letter (Letterhead) form, Print never saves the letter; only Save
 * and Save & Exit do.
 *
 * Print was a submit button. After the browser print it asks "Do you wish to make changes?", and
 * OK went back to editing AND posted the form with submit=save, so the letter was stored without
 * the user choosing Save.
 *
 * User path: Schedule > Search > Master Record > E-Chart > Forms menu > the Letterhead entry (a
 * marker-named registration of the shipped ../form/formConsultant.jsp, which Ontario installs hide).
 *
 * Asserted, one labelled step each:
 *   1. Print, then Cancel: the browser print runs once, the print view stays (the letter shown as
 *      text, the editor hidden), the buttons come back, and nothing is posted or stored.
 *   2. Print, then OK: the browser print runs once and nothing is posted or stored. The form is back
 *      in editing: the typed letter is in a visible Comments box, the box and the buttons do not
 *      overlap and each takes a click, the print copy is hidden, and the date picker and referral
 *      search are back.
 *   3. Exit, then Cancel: the form stays open and nothing is posted.
 *   4. Save, then OK: one form/formname POST carrying submit=save stores one formConsult row with
 *      the typed letter, and the saved record is redisplayed in the same window.
 *   5. On the saved record, an edited letter, Print, then OK: nothing is posted, the stored letter
 *      is unchanged, and the edit is still in the box for a later Save.
 *   6. Exit, then OK: the window closes, nothing is posted, and the stored record is unchanged.
 *
 * window.print is stubbed and counted, so the browser's own print dialog never opens.
 *
 * Fixtures: the owned FAKE- patient and one marker-named Forms-menu registration. The registration
 * is clinic-wide, so the check must not run concurrently with other checks (EXCLUSIVE=1). Cleanup
 * deletes the patient's formConsult rows and the registration, and asserts both are gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const NAME = 'form-consultant-print';
const TABLE = 'formConsult';
// Long enough to wait for a submit the click would have started at once.
const SETTLE_MS = 1500;

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const isFormPost = request => request.method() === 'POST'
  && new URL(request.url()).pathname.endsWith('/form/formname');

/** Stub the browser print on the current document and count its calls. */
async function stubPrint(page) {
  await page.evaluate(() => {
    window.__prints = 0;
    window.print = () => { window.__prints++; };
  });
}

/**
 * Press a button whose handler asks one question, answer it, and return what was asked. With
 * `closes`, the answer closes the window, which may land before Playwright acknowledges the click.
 */
async function pressAndAnswer(page, selector, accept, { closes = false } = {}) {
  const press = async () => {
    try {
      await page.locator(selector).click({ noWaitAfter: closes });
    } catch (error) {
      if (!closes || !page.isClosed() || !/closed/i.test(error.message)) throw error;
    }
  };
  const dialogs = await h.withExpectedDialogs(page, press, { accept });
  h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm',
    `${selector} asked ${dialogs.length} question(s) (${dialogs.map(d => d.type).join(', ') || 'none'}), not one confirm`);
  return dialogs[0];
}

async function shown(page, selector) {
  return page.locator(selector).isVisible();
}

async function workflow(s) {
  const { sql, patient, marker, context } = s;
  const name = `${marker} LTR`;
  const value = `../form/formConsultant.jsp?fixture=${marker}&demographic_no=`;
  const rows = `SELECT COUNT(*) FROM ${TABLE} WHERE demographic_no=${patient}`;
  h.assert(name.length <= 30, `The owned registration name exceeds encounterForm.form_name: ${name}`);

  s.cleanup(() => {
    sql.execute(`DELETE FROM ${TABLE} WHERE demographic_no=${patient}`);
    h.assert(sql.value(rows) === '0', 'The owned patient\'s formConsult rows were not removed');
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM encounterForm WHERE form_value=${h.sqlString(value)} AND form_name=${h.sqlString(name)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value=${h.sqlString(value)}`) === '0',
      'The owned Forms-menu registration was not removed');
  });
  // The shipped Letterhead row is hidden on Ontario installs; a positive hidden value is its menu position.
  sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
    SELECT ${h.sqlString(value)},${h.sqlString(name)},'${TABLE}',COALESCE(MAX(hidden),0)+1 FROM encounterForm`);

  const posts = [];
  context.on('request', request => { if (isFormPost(request)) posts.push(request); });
  const nothingPosted = async (what) => {
    await sleep(SETTLE_MS);
    h.assert(posts.length === 0, `${what} posted the form ${posts.length} time(s)`);
    h.assert(sql.value(rows) === '0', `${what} stored a formConsult row`);
  };

  const chart = await s.chart();
  await chart.locator('#menuTitle1 a').hover();
  const entry = chart.getByRole('link', { name, exact: true });
  h.assert(await entry.count() === 1, 'The Forms menu does not list the owned Letterhead registration once');
  const page = await s.popup(chart, entry, 'letterhead');
  h.assert(new URL(page.url()).searchParams.get('demographic_no') === patient, 'The form opened for another patient');
  await page.locator('#printbut').waitFor({ state: 'visible', timeout: 10000 });

  const letter = `${marker} Dear colleague,\nBP 120/80 & HR 72; pt's c/o SOB. Thank you for seeing this patient.`;
  // A textarea submits its line breaks as CRLF (HTML form submission), so that is what is stored.
  const storedLetter = letter.replace(/\n/g, '\r\n');
  await page.locator('#comments').fill(letter);

  await s.step('Print then Cancel keeps the print view and saves nothing', async () => {
    await stubPrint(page);
    await pressAndAnswer(page, '#printbut', false);
    // Posts first: a submit reloads the page, which also discards the print stub.
    await nothingPosted('Print then Cancel');
    h.assert(await page.evaluate(() => window.__prints) === 1, 'Print did not call the browser print exactly once');
    h.assert(await shown(page, '#textDiv') && !(await shown(page, '#comments')), 'Cancel left the print view');
    h.assert((await page.locator('#textDiv').innerText()).includes('Thank you for seeing this patient'),
      'The print view does not show the letter');
    h.assert(await shown(page, '#savebut') && await shown(page, '#printbut'), 'The buttons did not come back after Cancel');
  });

  await s.step('Print then OK returns to editing and saves nothing', async () => {
    await stubPrint(page);
    const asked = await pressAndAnswer(page, '#printbut', true);
    console.log(`    (Print asked: "${asked.text}")`);
    await nothingPosted('Print then OK');
    h.assert(await page.evaluate(() => window.__prints) === 1, 'Print did not call the browser print exactly once');
    h.assert(page.url().includes('/form/formConsultant'), `OK navigated the form to ${h.pathOnly(page.url())}`);
    const comments = page.locator('#comments');
    h.assert(await comments.isVisible() && await comments.isEditable(), 'The Comments box is not back in editing');
    h.assert(await comments.inputValue() === letter, 'The typed letter is no longer in the Comments box');
    h.assert(!(await shown(page, '#textDiv')), 'The print copy is still shown');
    h.assert(await shown(page, '#dating') && await shown(page, '#searching'),
      'The date picker or the referral search is still hidden');
    const box = await comments.boundingBox();
    const buttons = await page.locator('#buttons').boundingBox();
    h.assert(box && buttons && buttons.y >= box.y + box.height - 1,
      'The Comments box and the buttons overlap: the editor was left out of the page flow');
    // Hit-tested without acting: each fails if something else covers it.
    for (const selector of ['#comments', '#savebut', '#printbut']) {
      await page.locator(selector).click({ trial: true, timeout: 5000 });
    }
  });

  await s.step('Exit then Cancel keeps the form open and saves nothing', async () => {
    await pressAndAnswer(page, '#exitbut', false);
    await nothingPosted('Exit then Cancel');
    h.assert(!page.isClosed(), 'Cancel on Exit closed the form');
    h.assert(await page.locator('#comments').inputValue() === letter, 'The typed letter is gone after Exit then Cancel');
  });

  let savedId;
  await s.step('Save then OK stores the letter once and redisplays it', async () => {
    const answered = page.waitForResponse(response => isFormPost(response.request()), { timeout: 15000 });
    await pressAndAnswer(page, '#savebut', true);
    const response = await answered;
    h.assert(response.status() === 302, `Save was answered ${response.status()}, not accepted with a redirect`);
    h.assert(posts.length === 1, `Save posted the form ${posts.length} times`);
    h.assert(new URLSearchParams(posts[0].postData() || '').get('submit') === 'save', 'Save did not post submit=save');
    await expectValue(sql, rows, '1', 'Save did not store exactly one formConsult row');
    const [row] = sql.rows(`SELECT ID, comments FROM ${TABLE} WHERE demographic_no=${patient}`);
    savedId = row[0];
    h.assert(row[1] === storedLetter, 'The stored letter differs from what was typed');
    await page.waitForURL(url => url.pathname.endsWith('/form/forwardname'), { waitUntil: 'domcontentloaded' });
    h.assert(new URL(page.url()).searchParams.get('formId') === savedId, 'Save redisplayed another record');
    await h.assertNotErrorPage(page, 'the redisplayed letter');
    await page.locator('#printbut').waitFor({ state: 'visible', timeout: 10000 });
  });

  await s.step('Print then OK on the saved letter leaves the stored letter unchanged', async () => {
    const edited = `${marker} Edited after saving; printed but not saved.`;
    await page.locator('#comments').fill(edited);
    await stubPrint(page);
    await pressAndAnswer(page, '#printbut', true);
    await sleep(SETTLE_MS);
    h.assert(posts.length === 1, `Print then OK on the saved letter posted the form ${posts.length - 1} more time(s)`);
    h.assert(sql.value(rows) === '1', 'Print then OK on the saved letter stored another row');
    h.assert(sql.value(`SELECT comments FROM ${TABLE} WHERE ID=${savedId}`) === storedLetter,
      'Print then OK on the saved letter changed the stored letter');
    h.assert(await page.locator('#comments').inputValue() === edited, 'The edit is no longer in the Comments box');
  });

  await s.step('Exit then OK closes the form and saves nothing', async () => {
    const closed = page.waitForEvent('close', { timeout: 10000 });
    await pressAndAnswer(page, '#exitbut', true, { closes: true });
    await closed;
    await sleep(SETTLE_MS);
    h.assert(posts.length === 1, `Exit then OK posted the form ${posts.length - 1} more time(s)`);
    h.assert(sql.value(rows) === '1' && sql.value(`SELECT comments FROM ${TABLE} WHERE ID=${savedId}`) === storedLetter,
      'Exit then OK changed the stored letter');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
