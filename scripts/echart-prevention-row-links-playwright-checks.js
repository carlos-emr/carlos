#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Browser regression check for issue #3975: a row in the eChart Preventions box
 * opens THAT prevention, not the patient's whole prevention list.
 *
 * Entered the way a clinician enters it (login, search, the owned patient's
 * Master Record, its E-Chart link) and driven through the rows the navbar
 * actually rendered:
 *
 *   1. The heading and right-hand link still open the full list. Every row
 *      opens its own prevention in the per-patient `addPreventionData<n>`
 *      window (the name LeftNavBarDisplay.jsp registers for the box refresh),
 *      on the add route preset to the row's prevention, or on the CVC
 *      disambiguation route exactly when that prevention maps to more than
 *      one vaccine in CVCMapping.
 *   2. A row without a record opens a blank add form preset to it. Saving closes
 *      the popup WITHOUT reloading the eChart: the unsaved note text and a
 *      window marker survive, no main-frame navigation happens, and the box
 *      refreshes so the row now links to the saved record's id. Before the
 *      close.jsp change this reloaded the chart and dropped the note.
 *   3. That row then opens the newest record by id, with the date it shows.
 *
 * The read-only fallback (a provider without `_prevention w` keeps the list link
 * on every row) is pinned by EctDisplayPrevention2ActionUnitTest; exercising it
 * here would mean mutating a shared role's privileges.
 *
 * Owns its FAKE-PW patient and every prevention and draft it creates, so it
 * needs a disposable database: TEST_PASSWORD, TEST_PIN and MYSQL_PASSWORD.
 *   npm run test:echart-prevention-row-links-playwright
 */
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { NOTE_TEXTAREA } = require('./echart-note-editor-playwright-checks');

const ROW_SELECTOR = '#leftNavBar a.links[onclick*="addPreventionData"], #rightNavBar a.links[onclick*="addPreventionData"]';
const HANDLER = /popupPage\((\d+),(\d+),'([^']*)','([^']*)'\);return false;/;
const SAVED_DATE = '2001-02-03';

/** Undo the OWASP JavaScript-string encoding the action applies to the handler. */
function decodeJsString(value) {
  return value.replace(/\\(?:x([0-9a-fA-F]{2})|u([0-9a-fA-F]{4})|(.))/g,
    (_m, hex, unicode, literal) => (hex || unicode ? String.fromCharCode(parseInt(hex || unicode, 16)) : literal));
}

/** The prevention rows the navbar rendered, with their decoded popup target. */
async function readRows(chart, baseUrl) {
  const rows = await chart.locator(ROW_SELECTOR).evaluateAll(links => links.map(link => ({
    onclick: link.getAttribute('onclick') || '',
    text: (link.textContent || '').trim(),
  })));
  return rows.map(row => {
    const match = HANDLER.exec(row.onclick);
    assert(match, `A Preventions row's handler is not a popupPage(...) call the box refresh can track: ${row.text}`);
    return { ...row, height: match[1], width: match[2], window: match[3], url: new URL(decodeJsString(match[4]), baseUrl) };
  });
}

/** The handler text of a row that edits record `id` (the action JavaScript-encodes the `&`). */
function recordNeedle(id) {
  return `ViewAddPreventionData?id=${id}\\x26`;
}

/** The rendered row whose handler contains `needle`. */
async function rowWith(chart, needle) {
  const handlers = await chart.locator(ROW_SELECTOR).evaluateAll(links => links.map(link => link.getAttribute('onclick') || ''));
  const index = handlers.findIndex(handler => handler.includes(needle));
  assert(index >= 0, 'The expected Preventions row is not rendered');
  return chart.locator(ROW_SELECTOR).nth(index);
}

/**
 * Wait for the refreshed box to link a row to `needle`. The box renders six rows,
 * warnings and undocumented preventions first, so a newly dated row can sit behind
 * the "more items" expander, which is where a clinician would look for it.
 */
async function waitForRow(chart, needle) {
  const linked = ({ selector, text }) => [...document.querySelectorAll(selector)]
    .some(link => (link.getAttribute('onclick') || '').includes(text));
  const arg = { selector: ROW_SELECTOR, text: needle };
  if (await chart.waitForFunction(linked, arg, { timeout: 5000 }).then(() => true, () => false)) return;
  const expander = chart.locator('#preventions img[src*="expand.gif"]').first();
  assert(await expander.count() > 0, 'The refreshed Preventions box does not link the row to the saved record');
  await expander.click();
  await chart.waitForFunction(linked, arg);
}

async function workflow(s) {
  const { sql, patient, marker, config } = s;
  s.cleanup(() => sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
    WHERE p.demographic_no=${patient}; DELETE FROM preventions WHERE demographic_no=${patient}`));
  const chart = await s.chart();
  let target;

  await s.step('every row opens its own prevention; the heading still opens the list', async () => {
    assert(await chart.locator('#leftNavBar [onclick*="ViewPreventionIndex"], #rightNavBar [onclick*="ViewPreventionIndex"]').count() > 0,
      'The Preventions heading no longer opens the full prevention list');
    const rows = await readRows(chart, config.baseUrl);
    assert(rows.length > 0, 'The Preventions box rendered no rows for the owned patient');
    for (const row of rows) {
      assert(row.height === '600' && row.width === '900', `Row "${row.text}" opens a popup sized unlike the list page's form`);
      assert(row.window === `addPreventionData${patient}`,
        `Row "${row.text}" opens window ${row.window}, not the per-patient form window the refresh tracks`);
      assert(!row.url.pathname.endsWith('/prevention/ViewPreventionIndex'),
        `Row "${row.text}" still opens the whole prevention list`);
      assert(row.url.searchParams.get('demographic_no') === patient, `Row "${row.text}" targets another patient`);
      // The fixture patient starts with no preventions, so every row is an add row.
      const name = row.url.searchParams.get('prevention');
      assert(name && !row.url.searchParams.has('id'), `Row "${row.text}" is not preset to a prevention`);
      const mappings = Number(sql.value(`SELECT COUNT(*) FROM CVCMapping WHERE oscarName=${sqlString(name)}`));
      const expected = mappings > 1 ? '/prevention/ViewAddPreventionDataDisambiguate' : '/prevention/ViewAddPreventionData';
      assert(row.url.pathname.endsWith(expected), `Row "${row.text}" (${mappings} CVC mappings) should open ${expected}`);
    }
    target = rows.find(row => row.url.pathname.endsWith('/ViewAddPreventionData') && row.url.searchParams.has('snomedId'))
      || rows.find(row => row.url.pathname.endsWith('/ViewAddPreventionData'));
    assert(target, 'No Preventions row opens the add form directly, so the save path cannot be exercised');
  });

  const prevention = target.url.searchParams.get('prevention');
  let id;

  await s.step('a row without a record opens a blank form, and saving keeps the unsaved note', async () => {
    const note = chart.locator(NOTE_TEXTAREA).first();
    await note.waitFor({ state: 'visible' });
    await note.click();
    await note.pressSequentially(` ${marker} unsaved`, { delay: 10 });
    await chart.evaluate(value => { window.pwPreventionMarker = value; }, marker);
    let reloaded = false;
    const onNavigate = frame => { if (frame === chart.mainFrame()) reloaded = true; };
    chart.on('framenavigated', onNavigate);

    const editor = await s.popup(chart, await rowWith(chart, target.onclick), 'prevention-editor');
    const opened = new URL(editor.url());
    assert(opened.pathname.endsWith('/prevention/ViewAddPreventionData'), 'The row did not open the add form');
    assert(opened.searchParams.get('prevention') === prevention, 'The add form opened for a different prevention');
    assert(opened.searchParams.get('snomedId') === target.url.searchParams.get('snomedId'),
      'The add form lost the SNOMED id that selects immunization mode');
    assert(await editor.locator('[name="prevention"]').first().inputValue() === prevention,
      'The blank form is not preset to the clicked prevention');
    const given = editor.locator('[name="given"][value="given"]');
    if (await given.count()) await given.first().check();
    await editor.locator('#prevDate').fill(SAVED_DATE);
    await editor.locator('[name="comments"]').first().fill(marker).catch(() => {});
    const refresh = chart.waitForResponse(response => response.url().includes('/encounter/displayPrevention')
      && response.request().method() !== 'OPTIONS');
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
    await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}
      AND prevention_type=${sqlString(prevention)} AND deleted='0' AND DATE(prevention_date)='${SAVED_DATE}'`,
      '1', 'Saving from the eChart row did not record exactly one prevention');
    id = sql.value(`SELECT id FROM preventions WHERE demographic_no=${patient} AND prevention_type=${sqlString(prevention)} AND deleted='0'`);
    assert(/^[1-9]\d*$/.test(id), 'The saved prevention has no id');

    // The eChart's reloadWindows poller reloads only this box once the popup has closed.
    assert((await refresh).ok(), 'The Preventions box refresh failed after the form closed');
    chart.off('framenavigated', onNavigate);
    assert(!reloaded, 'Closing the prevention form reloaded the eChart');
    assert(await chart.evaluate(() => window.pwPreventionMarker) === marker, 'The eChart page was replaced');
    assert((await chart.locator(NOTE_TEXTAREA).first().inputValue()).includes(`${marker} unsaved`),
      'The unsaved encounter note text was lost');
    await waitForRow(chart, recordNeedle(id));
  });

  await s.step('the recorded row opens its newest record', async () => {
    const editor = await s.popup(chart, await rowWith(chart, recordNeedle(id)), 'prevention-record');
    const opened = new URL(editor.url());
    assert(opened.pathname.endsWith('/prevention/ViewAddPreventionData') && opened.searchParams.get('id') === id,
      'The recorded row did not open its record by id');
    assert(await editor.locator('[name="prevention"]').first().inputValue() === prevention,
      'The record opened as a different prevention');
    assert((await editor.locator('#prevDate').inputValue()).startsWith(SAVED_DATE),
      'The record opened with a different date from the one the row shows');
    await editor.close();
  });
}

if (require.main === module) runWorkflow('echart-prevention-row-links', workflow);
module.exports = { HANDLER, decodeJsString, readRows, recordNeedle, workflow };
