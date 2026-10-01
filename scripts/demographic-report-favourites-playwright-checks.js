#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Demographic Report Tool saved queries ("favourites").
 * User path: Schedule ▸ Report ▸ Demographic Report Tool ▸ Save Query; Load Query from the
 * saved-query dropdown ▸ Run Query; "manage" (oscarReport/ViewManageDemographicQueryFavourites)
 * ▸ tick the query ▸ Delete Selected (report/DeleteDemographicReport).
 * Asserts: Save Query writes one demographicQueryFavourites row carrying the chosen columns,
 * last-name and sex criteria (archived='1') and offers it by its exact (encoded) name; Load
 * Query restores exactly those criteria into a deliberately different form; the re-run lists
 * exactly the rows MariaDB returns for the owned FAKE- patients (the sex filter excludes the
 * second one); the manage page lists it and Delete Selected archives only that row and drops it
 * from the dropdown. Negative probes on a second marker favourite: a tokenless POST and a GET of
 * the delete mutator must leave it unarchived (GET must answer 405), and a GET of the tool with
 * Save Query intent must store nothing (405).
 * Fixtures: the runWorkflow patient plus a second marker patient; favourites named with the
 * marker. Cleanup deletes only marker rows and asserts they are gone.
 * Implements coverage plan §3.6 (report: Demographic Report Tool saved queries).
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, clickAndAwaitReload} = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 30000;
const COLUMNS = ['demographic_no', 'last_name', 'first_name', 'sex'];

async function workflow(s) {
  const {sql, patient, marker, context, recorder} = s;
  const queryName = `${marker} Fav "A&B" O'Neil`;
  const favourites = `demographicQueryFavourites WHERE queryName LIKE ${h.sqlString(marker + '%')}`;
  let secondPatient;
  let favouriteId;
  s.cleanup(() => {
    sql.execute(`DELETE FROM ${favourites}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM ${favourites}`) === '0', 'Owned saved queries were not removed');
  });
  s.cleanup(() => {
    if (!secondPatient) return;
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${secondPatient} AND last_name=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${secondPatient}`) === '0',
      'The second owned patient was not removed');
  });
  secondPatient = sql.value(`INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(marker)},'Second','1975','03','04','M','AC',
      ${h.sqlString(s.provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(secondPatient), 'The second owned patient was not created');

  const {page: reportIndex} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a[title="Generate a report"]').first(),
    {context, recorder, label: 'report-index', timeout: TIMEOUT});
  const {page: tool} = await clickOpensPopupOrNavigates(reportIndex, reportIndex.locator('a[href*="ViewReportDemographicReport"]').first(),
    {context, recorder, label: 'demographic-report-tool', timeout: TIMEOUT});
  await tool.locator('#select_demographic_no').waitFor();
  const savedOption = () => tool.locator('#savedQuery option').filter({hasText: queryName});
  async function submit(page, name) {
    await clickAndAwaitReload(page, page.getByRole('button', {name, exact: true}).or(page.locator(`input[type="submit"][value="${name}"]`)).first(),
      {timeout: TIMEOUT, label: name});
    await h.assertNotErrorPage(page, name);
  }

  await s.step('Save Query stores the chosen columns and criteria and offers the query by name', async () => {
    for (const column of COLUMNS) await tool.locator(`#select_${column}`).check();
    await tool.locator('#lastName').fill(marker);
    await tool.locator('select[name="sex"]').selectOption('1');
    await tool.locator('#queryName').fill(queryName);
    await submit(tool, 'Save Query');
    favouriteId = sql.value(`SELECT favId FROM demographicQueryFavourites WHERE queryName=${h.sqlString(queryName)}`);
    h.assert(/^[1-9]\d*$/.test(favouriteId), 'Save Query did not reach demographicQueryFavourites');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${favourites}`) === '1', 'Save Query stored the query more than once');
    const [row] = sql.rows(`SELECT selects,lastName,sex,archived FROM demographicQueryFavourites WHERE favId=${favouriteId}`);
    const stored = [...String(row[0]).matchAll(/value="([^"]*)"/g)].map(match => match[1]);
    h.assert(JSON.stringify(stored) === JSON.stringify(COLUMNS), 'The saved query stored different columns');
    h.assert(row[1] === marker && row[2] === '1' && row[3] === '1', 'The saved query stored different criteria or is not active');
    h.assert(await savedOption().count() === 1 && await savedOption().getAttribute('value') === favouriteId
      && (await savedOption().innerText()).trim() === queryName, 'The saved-query list does not offer the query by its exact name');
  });

  await s.step('Load Query restores exactly the saved criteria into a different form', async () => {
    // Leave one unrelated column checked: the form refuses to submit with none.
    await tool.locator('#select_city').check();
    for (const column of COLUMNS) await tool.locator(`#select_${column}`).uncheck();
    await tool.locator('#lastName').fill('');
    await tool.locator('select[name="sex"]').selectOption('0');
    await tool.locator('#savedQuery').selectOption(favouriteId);
    await submit(tool, 'Load Query');
    for (const column of COLUMNS) {
      h.assert(await tool.locator(`#select_${column}`).isChecked(), 'Load Query did not restore a saved column');
    }
    h.assert(!(await tool.locator('#select_city').isChecked()), 'Load Query kept a column the saved query does not select');
    h.assert(await tool.locator('#lastName').inputValue() === marker && await tool.locator('select[name="sex"]').inputValue() === '1',
      'Load Query did not restore the last-name and sex criteria');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${favourites}`) === '1', 'Load Query wrote a saved query');
  });

  await s.step('the reloaded query runs and lists exactly the rows MariaDB returns for it', async () => {
    await submit(tool, 'Run Query');
    const header = tool.locator('.section-header').filter({hasText: 'Search Returned:'});
    await header.waitFor();
    const expected = sql.rows(`SELECT ${COLUMNS.join(',')} FROM demographic
      WHERE last_name LIKE ${h.sqlString(marker + '%')} AND sex='F' ORDER BY demographic_no`);
    h.assert(expected.length === 1 && expected[0][0] === patient, 'The fixture does not isolate the owned female patient');
    h.assert((await header.innerText()).replace(/\s+/g, ' ').includes(`Search Returned: ${expected.length} Results`),
      'The re-run did not report the expected result count');
    const rendered = await header.locator('xpath=following-sibling::table[1]').locator('tr').evaluateAll(rows =>
      rows.slice(1).map(row => [...row.querySelectorAll('td')].map(cell => cell.textContent.trim())));
    h.assert(JSON.stringify(rendered) === JSON.stringify(expected), 'The re-run rows differ from the database rows');
    h.assert(!rendered.some(cells => cells[0] === secondPatient), 'The sex criterion did not exclude the second owned patient');
  });

  await s.step('manage ▸ Delete Selected archives only the saved query and drops it from the list', async () => {
    const {page: manage} = await clickOpensPopupOrNavigates(tool, tool.getByRole('link', {name: 'manage', exact: true}),
      {context, recorder, label: 'manage-demographic-queries', timeout: TIMEOUT});
    const listed = manage.locator('tr').filter({has: manage.locator(`input[name="queryFavourite"][value="${favouriteId}"]`)});
    h.assert(await listed.count() === 1 && (await listed.innerText()).trim() === queryName,
      'The manage page does not list the saved query by its exact name');
    await listed.locator('input[name="queryFavourite"]').check();
    await submit(manage, 'Delete Selected');
    h.assert(sql.value(`SELECT archived FROM demographicQueryFavourites WHERE favId=${favouriteId}`) === '0',
      'Delete Selected did not archive the saved query');
    await manage.locator('#savedQuery').waitFor({state: 'attached'});
    h.assert(await manage.locator(`#savedQuery option[value="${favouriteId}"]`).count() === 0,
      'The deleted query is still offered in the saved-query list');
  });

  await s.step('the delete mutator refuses a tokenless POST and a GET (405) and archives nothing', async () => {
    const probeId = sql.value(`INSERT INTO demographicQueryFavourites (queryName,archived,selects,lastName)
      VALUES (${h.sqlString(marker + '-PROBE')},'1','',${h.sqlString(marker)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(probeId), 'The probe query fixture was not created');
    const active = () => sql.value(`SELECT archived FROM demographicQueryFavourites WHERE favId=${probeId}`) === '1';
    const url = h.appUrl(s.config.baseUrl, '/report/DeleteDemographicReport');
    await context.request.post(url, {form: {queryFavourite: probeId}, maxRedirects: 0});
    h.assert(active(), 'A POST without a CSRF token archived a saved query');
    const unsafeGet = await context.request.get(url, {params: {queryFavourite: probeId}, maxRedirects: 0});
    const archivedByGet = !active();
    h.assert(unsafeGet.status() === 405 && !archivedByGet,
      `GET of report/DeleteDemographicReport was not rejected (status ${unsafeGet.status()}, archived=${archivedByGet})`);
  });

  await s.step('a GET of the tool with Save Query intent stores nothing (405)', async () => {
    const getName = `${marker}-GET`;
    const unsafeSave = await context.request.get(h.appUrl(s.config.baseUrl, '/report/DemographicReport'),
      {params: {query: 'Save Query', queryName: getName, select: 'demographic_no', lastName: marker}, maxRedirects: 0});
    const stored = sql.value(`SELECT COUNT(*) FROM demographicQueryFavourites WHERE queryName=${h.sqlString(getName)}`);
    h.assert(unsafeSave.status() === 405 && stored === '0',
      `GET of report/DemographicReport?query=Save Query was not rejected (status ${unsafeSave.status()}, rows stored=${stored})`);
  });
}

if (require.main === module) runWorkflow('demographic-report-favourites', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
