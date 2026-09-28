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
 * on every row) is covered by EctDisplayPrevention2ActionUnitTest.
 *
 * Owns its FAKE-PW patient and every prevention and draft it creates, so it
 * needs a disposable database: TEST_PASSWORD, TEST_PIN and MYSQL_PASSWORD.
 *   npm run test:echart-prevention-row-links-playwright
 */
const { assert, assertNotErrorPage, sqlString } = require('./lib/playwright-harness');
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
  s.cleanup(() => sql.execute(`DELETE d FROM partial_date d JOIN preventions p ON p.id=d.table_id
    WHERE d.table_name=4 AND p.demographic_no=${patient}; DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
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
    await editor.locator('[name="comments"]').first().fill(marker);
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

  await s.step('keyboard Enter on the heading link opens the patient list and preserves the unsaved note', async () => {
    // The native anchor owns both keyboard and pointer activation. Exercise
    // the real Enter event path, including its popup refresh registration.
    const headingLink = chart.locator('#preventions .nav-menu-title h3 > a[onclick*="ViewPreventionIndex"]').first();
    await headingLink.focus();
    assert(await headingLink.evaluate(link => document.activeElement === link),
      'The Preventions heading link cannot receive keyboard focus');
    const [index] = await Promise.all([
      chart.waitForEvent('popup'),
      headingLink.press('Enter'),
    ]);
    await index.waitForURL(url => url.pathname.endsWith('/prevention/ViewPreventionIndex')
      && url.searchParams.get('demographic_no') === patient);
    await index.waitForLoadState('domcontentloaded');
    await assertNotErrorPage(index, 'Keyboard-opened prevention list');
    const opened = new URL(index.url());
    assert(opened.pathname.endsWith('/prevention/ViewPreventionIndex')
      && opened.searchParams.get('demographic_no') === patient,
    'Keyboard activation did not open the full prevention list for the chart patient');
    await index.locator(`[onclick*="ViewAddPreventionData?id=${id}&"]`).first().waitFor({ state: 'visible' });
    const refresh = chart.waitForResponse(response => response.url().includes('/encounter/displayPrevention'));
    await index.close();
    assert((await refresh).ok(), 'Closing the keyboard-opened list did not refresh the prevention panel');
    assert(await chart.evaluate(() => window.pwPreventionMarker) === marker,
      'Keyboard heading activation replaced the chart');
    assert((await chart.locator(NOTE_TEXTAREA).first().inputValue()).includes(`${marker} unsaved`),
      'Keyboard heading activation lost the unsaved encounter note');
  });

  for (const [label, selector] of [
    ['heading', '#preventions .nav-menu-title h3 > a[onclick*="ViewPreventionIndex"]'],
    ['plus', '#preventions .nav-menu-add-button a[onclick*="ViewPreventionIndex"]'],
  ]) {
    await s.step(`${label} list edits refresh only the prevention panel and preserve the unsaved note`, async () => {
      const index = await s.popup(chart, chart.locator(selector).first(), `prevention-${label}-list`);
      const editor = await s.popup(index,
        index.locator(`[onclick*="ViewAddPreventionData?id=${id}&"]`).first(), `prevention-${label}-edit`);
      await editor.locator('[name="comments"]').first().fill(`${marker}-${label}`);
      await editor.locator('input[type="submit"][name="action"]').first().click();
      if (!editor.isClosed()) await editor.waitForEvent('close');
      const previous = id;
      await expectValue(sql, `SELECT deleted FROM preventions WHERE id=${previous}`, '1',
        'List edit did not archive the previous prevention');
      id = sql.value(`SELECT id FROM preventions WHERE demographic_no=${patient}
        AND prevention_type=${sqlString(prevention)} AND deleted='0'`);
      assert(id !== previous && /^[1-9]\d*$/.test(id), 'List edit did not create one replacement');
      await index.locator(`[onclick*="ViewAddPreventionData?id=${id}&"]`).first().waitFor({ state: 'visible' });
      const refresh = chart.waitForResponse(response => response.url().includes('/encounter/displayPrevention'));
      await index.close();
      assert((await refresh).ok(), 'Closing the full list did not refresh the panel');
      await waitForRow(chart, recordNeedle(id));
      assert(await chart.evaluate(() => window.pwPreventionMarker) === marker, 'Heading/list close replaced the chart');
      assert((await chart.locator(NOTE_TEXTAREA).first().inputValue()).includes(`${marker} unsaved`),
        'Heading/list close lost the unsaved encounter note');
    });
  }

  const childMarker = `${marker}-merged`;
  const child = sql.value(`INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${sqlString(childMarker)},'Workflow','1980','01','02','F','AC',${sqlString(s.provider)},'ON','ON','NR',NOW());
    SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(child), 'The owned second patient was not created');
  s.cleanup(() => {
    assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${child}
      AND last_name=${sqlString(childMarker)}`) === '1', 'Merged fixture ownership changed');
    sql.execute(`DELETE FROM demographic_merged WHERE demographic_no=${child} AND merged_to=${patient};
      DELETE d FROM partial_date d JOIN preventions p ON p.id=d.table_id WHERE d.table_name=4 AND p.demographic_no=${child};
      DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${child};
      DELETE FROM preventions WHERE demographic_no=${child};
      DELETE FROM demographic WHERE demographic_no=${child} AND last_name=${sqlString(childMarker)}`);
  });

  await s.step('malformed and unrelated-patient save requests fail without changing clinical records', async () => {
    const editor = await s.popup(chart, await rowWith(chart, recordNeedle(id)), 'prevention-rejection');
    const token = await editor.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const form = await editor.locator('form').filter({ has: editor.locator('#prevDate') }).evaluate(element => ({
      action: new URL(element.getAttribute('action'), document.baseURI).href, entries: Array.from(new FormData(element).entries()),
    }));
    const before = JSON.stringify(sql.rows(`SELECT * FROM preventions WHERE demographic_no=${patient} ORDER BY id`));
    for (const [field, value] of [['demographic_no', child], ['id', '-1'], ['prevDate', '2026-02-30']]) {
      const body = new URLSearchParams(form.entries);
      body.set(field, value);
      const response = await s.context.request.post(form.action, {
        headers: { 'CSRF-TOKEN': token, 'Content-Type': 'application/x-www-form-urlencoded' }, data: body.toString(),
      });
      assert(response.status() === 400, `Invalid prevention ${field} returned ${response.status()}`);
      assert(JSON.stringify(sql.rows(`SELECT * FROM preventions WHERE demographic_no=${patient} ORDER BY id`)) === before,
        'Rejected prevention request changed the patient history');
    }
    await editor.close();
  });


  await s.step('merged histories select the newest clinical date and break date ties by record id', async () => {
    sql.execute(`INSERT INTO demographic_merged (demographic_no,merged_to,deleted,lastUpdateUser,lastUpdateDate)
      VALUES (${child},${patient},0,${sqlString(s.provider)},NOW())`);
    const childId = sql.value(`INSERT INTO preventions
      (demographic_no,prevention_type,prevention_date,creator,provider_no,refused,deleted,never,snomedId)
      SELECT ${child},prevention_type,'1999-01-01',creator,provider_no,refused,0,never,snomedId
      FROM preventions WHERE id=${id}; SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(childId) && Number(childId) > Number(id), 'Merged older record was not created');
    async function closeListToRefresh() {
      const index = await s.popup(chart,
        chart.locator('#preventions .nav-menu-title h3 > a[onclick*="ViewPreventionIndex"]').first(), 'prevention-merged-list');
      await index.locator(`[onclick*="ViewAddPreventionData?id=${childId}&"]`).first().waitFor({ state: 'visible' });
      const refresh = chart.waitForResponse(response => response.url().includes('/encounter/displayPrevention'));
      await index.close();
      assert((await refresh).ok(), 'Merged prevention panel failed to refresh');
    }
    await closeListToRefresh();
    await waitForRow(chart, recordNeedle(id));
    assert(!(await readRows(chart, config.baseUrl)).some(row => row.url.searchParams.get('id') === childId),
      'Older appended child history replaced the newest primary record');
    sql.execute(`UPDATE preventions SET prevention_date='${SAVED_DATE}' WHERE id=${childId} AND demographic_no=${child}`);
    await closeListToRefresh();
    await waitForRow(chart, recordNeedle(childId));
    const editor = await s.popup(chart, await rowWith(chart, recordNeedle(childId)), 'prevention-merged-edit');
    const token = await editor.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const form = await editor.locator('form').filter({ has: editor.locator('#prevDate') }).evaluate(element => ({
      action: new URL(element.getAttribute('action'), document.baseURI).href, entries: Array.from(new FormData(element).entries()),
    }));
    const body = new URLSearchParams(form.entries);
    body.set('prevDate', '2026-09');
    body.set('comments', `${marker}-merged-edit`);
    const refresh = chart.waitForResponse(response => response.url().includes('/encounter/displayPrevention'));
    const saved = await s.context.request.post(form.action, {
      headers: { 'CSRF-TOKEN': token, 'Content-Type': 'application/x-www-form-urlencoded' }, data: body.toString(),
    });
    assert(saved.ok() && (await saved.text()).includes('closeWin'), 'Merged partial-date save did not succeed');
    await editor.close();
    assert((await refresh).ok(), 'Merged edit did not refresh the panel');
    await expectValue(sql, `SELECT deleted FROM preventions WHERE id=${childId}`, '1', 'Merged edit did not archive its predecessor');
    const replacement = sql.value(`SELECT p.id FROM preventions p JOIN preventionsExt x ON x.prevention_id=p.id
      WHERE p.demographic_no=${patient} AND p.deleted=0 AND x.keyval='previousId' AND x.val=${sqlString(childId)}`);
    assert(/^[1-9]\d*$/.test(replacement), 'Merged edit did not create an owned replacement');
    assert(sql.value(`SELECT COUNT(*) FROM partial_date WHERE table_name=4 AND table_id=${replacement} AND format='YYYY-MM'`) === '1',
      'Merged edit lost the partial clinical date');
    await waitForRow(chart, recordNeedle(replacement));
    assert((await chart.locator(NOTE_TEXTAREA).first().inputValue()).includes(`${marker} unsaved`),
      'Merged edit lost the unsaved note');
  });

}

if (require.main === module) runWorkflow('echart-prevention-row-links', workflow);
module.exports = { HANDLER, decodeJsString, readRows, recordNeedle, workflow };
