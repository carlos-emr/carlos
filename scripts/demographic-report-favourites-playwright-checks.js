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
 * second one); the manage page lists it and Delete Selected archives only that row (a second,
 * unticked marker favourite stays active) and drops it from the dropdown. Negative probes on a second marker favourite: a tokenless POST and a GET of
 * the delete mutator must leave it unarchived (GET must answer 405), and both tool write intents refuse GET/HEAD (405 + Allow: POST) and tokenless POST (403),
 * storing neither favourites nor patient sets. Native POST controls carry actual CSRF tokens;
 * read-only GET display and query execution remain available.
 * Fixtures: the runWorkflow patient plus a second marker patient; favourites named with the
 * marker, plus uniquely named patient sets (20 characters) restricted to those owned patients.
 * Zero-name baselines and foreign-patient refusal gates precede owned-set cleanup.
 * Implements coverage plan §3.6 (report: Demographic Report Tool saved queries).
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, clickAndAwaitReload} = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 30000;
const COLUMNS = ['demographic_no', 'last_name', 'first_name', 'sex'];

const WRITE_INTENTS = ['Save Query', 'Run Query And Save to Patient Set'];

function reportMutationNames(marker, suffix) {
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker) && /^[A-Z][0-9]$/.test(suffix), 'Invalid owned report probe marker');
  return {queryName: `${marker}-${suffix}`, setName: `PW${marker.slice(-16)}${suffix}`};
}

function registerOwnedPatientSets(s, secondPatient) {
  const names = ['G0', 'G1', 'H0', 'H1', 'P0', 'P1', 'S0'].map(suffix => reportMutationNames(s.marker, suffix).setName);
  const where = `set_name IN (${names.map(h.sqlString).join(',')})`;
  h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicSets WHERE ${where}`) === '0',
    "A patient set already uses this run's exact owned names; refusing to adopt it");
  let cleaned = false;
  const reservation = {where, assertClean() {h.assert(cleaned, 'Owned patient-set cleanup is unconfirmed; retaining the second owned patient');}};
  const patients = [s.patient, secondPatient];
  h.assert(patients.every(value => /^[1-9]\d*$/.test(String(value))), 'Owned patient set keys are invalid');
  s.cleanup(() => {
    h.assert(s.context.browser() && !s.context.browser().isConnected(), 'Report browser drain is unconfirmed; retaining patient sets');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicSets WHERE ${where}
      AND (demographic_no IS NULL OR demographic_no NOT IN (${patients.join(',')}))`) === '0',
      'An owned-name patient set references a foreign patient; refusing cleanup');
    s.sql.execute(`DELETE FROM demographicSets WHERE ${where} AND demographic_no IN (${patients.join(',')})`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicSets WHERE ${where}`) === '0', 'Owned patient sets were not removed');
    cleaned = true;
  });
  return reservation;
}

async function assertReportMutationRefused(s, {method, intent, queryName, setName}) {
  h.assert(['GET', 'HEAD', 'POST'].includes(method) && WRITE_INTENTS.includes(intent), 'Invalid report refusal probe');
  const favouriteWhere = `queryName=${h.sqlString(queryName)}`;
  const setWhere = `set_name=${h.sqlString(setName)}`;
  h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicQueryFavourites WHERE ${favouriteWhere}`) === '0'
    && s.sql.value(`SELECT COUNT(*) FROM demographicSets WHERE ${setWhere}`) === '0', 'Report probe names are not empty');
  const fields = {query: intent, queryName, setName, select: 'demographic_no', lastName: s.marker, sex: '1'};
  const response = await s.context.request.fetch(h.appUrl(s.config.baseUrl, '/report/DemographicReport'),
    {method, ...(method === 'POST' ? {form: fields} : {params: fields}), maxRedirects: 0});
  const expectedStatus = method === 'POST' ? 403 : 405;
  h.assert(response.status() === expectedStatus, `${method} report write intent answered HTTP ${response.status()}, expected ${expectedStatus}`);
  if (method !== 'POST') h.assert(response.headers().allow === 'POST', `${method} report write refusal did not advertise Allow: POST`);
  await h.assertRefused(s, {response, table: 'demographicQueryFavourites', where: favouriteWhere, before: '0', label: `${method} report write intent`});
  h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicSets WHERE ${setWhere}`) === '0', `${method} report refusal wrote a patient-set row`);
}

async function submitReportWrite(page, intent, submit, reportUrl) {
  const native = page.waitForRequest(request => request.method() === 'POST'
    && new URL(request.url()).origin === new URL(reportUrl).origin
    && new URL(request.url()).pathname === new URL(reportUrl).pathname
    && new URLSearchParams(request.postData() || '').get('query') === intent, {timeout: TIMEOUT});
  const [request] = await Promise.all([native, submit(page, intent)]);
  const fields = new URLSearchParams(request.postData() || '');
  h.assert(Boolean(request.headers()['csrf-token'] || fields.get('CSRF-TOKEN')), 'Native report POST did not carry a CSRF token');
  const response = await request.response();
  h.assert(response && response.status() === 200, 'Native report POST did not complete successfully');
}

async function workflow(s) {
  const {sql, patient, marker, context, recorder} = s;
  const queryName = `${marker} Fav "A&B" O'Neil`;
  const favourites = `demographicQueryFavourites WHERE queryName LIKE ${h.sqlString(marker + '%')}`;
  let secondPatient;
  let ownedSets;
  let favouriteId;
  s.cleanup(() => {
    sql.execute(`DELETE FROM ${favourites}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM ${favourites}`) === '0', 'Owned saved queries were not removed');
  });
  s.cleanup(() => {
    if (!secondPatient) return;
    h.assert(context.browser() && !context.browser().isConnected(), 'Report browser drain is unconfirmed; retaining the second owned patient');
    if (ownedSets) ownedSets.assertClean();
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
  ownedSets = registerOwnedPatientSets(s, secondPatient);
  const ownedSetsWhere = ownedSets.where;
  const reportUrl = h.appUrl(s.config.baseUrl, '/report/DemographicReport');

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
    await submitReportWrite(tool, 'Save Query', submit, reportUrl);
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

  await s.step('native POST with CSRF saves the exact owned patient set', async () => {
    const setName = reportMutationNames(marker, 'S0').setName;
    await tool.locator('input[name="setName"]').fill(setName);
    await submitReportWrite(tool, WRITE_INTENTS[1], submit, reportUrl);
    const rows = sql.rows(`SELECT demographic_no,archive FROM demographicSets WHERE set_name=${h.sqlString(setName)} ORDER BY demographic_no`);
    h.assert(JSON.stringify(rows) === JSON.stringify([[patient, '0']]), 'Native patient-set POST stored different patients or archive state');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${favourites}`) === '1', 'Saving a patient set also wrote a favourite');
  });

  await s.step('read-only GET still displays and runs the report without creating rows', async () => {
    const counts = () => [sql.value(`SELECT COUNT(*) FROM ${favourites}`), sql.value(`SELECT COUNT(*) FROM demographicSets WHERE ${ownedSetsWhere}`)];
    const before = counts();
    const display = await context.request.get(reportUrl, {maxRedirects: 0});
    h.assert(display.status() === 200 && (await display.text()).includes('id="select_demographic_no"'), 'Read-only report GET did not display its form');
    // The native report form submits age=0 for its default no-age criterion.
    const run = await context.request.get(reportUrl, {params: {query: 'Run Query', select: 'demographic_no', lastName: marker, sex: '1', age: '0'}, maxRedirects: 0});
    const body = await run.text();
    const results = body.match(/Search Returned:\s*1 Results<\/div>\s*<table[^>]*>([\s\S]*?)<\/table>/);
    const patients = results ? [...results[1].matchAll(/<td>\s*(\d+)\s*<\/td>/g)].map(match => match[1]) : [];
    h.assert(run.status() === 200 && JSON.stringify(patients) === JSON.stringify([patient]), 'Read-only Run Query GET did not return exactly the owned patient');
    h.assert(JSON.stringify(counts()) === JSON.stringify(before), 'A read-only report GET created an owned favourite or patient set');
  });

  await s.step('manage ▸ Delete Selected archives only the saved query and drops it from the list', async () => {
    // A second owned active favourite, left unticked, proves the delete is limited to the selection.
    const keptId = sql.value(`INSERT INTO demographicQueryFavourites (queryName,archived,selects,lastName)
      VALUES (${h.sqlString(marker + '-KEEP')},'1','',${h.sqlString(marker)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(keptId), 'The kept query fixture was not created');
    const {page: manage} = await clickOpensPopupOrNavigates(tool, tool.getByRole('link', {name: 'manage', exact: true}),
      {context, recorder, label: 'manage-demographic-queries', timeout: TIMEOUT});
    const listed = manage.locator('tr').filter({has: manage.locator(`input[name="queryFavourite"][value="${favouriteId}"]`)});
    h.assert(await listed.count() === 1 && (await listed.innerText()).trim() === queryName,
      'The manage page does not list the saved query by its exact name');
    await listed.locator('input[name="queryFavourite"]').check();
    await submit(manage, 'Delete Selected');
    h.assert(sql.value(`SELECT archived FROM demographicQueryFavourites WHERE favId=${favouriteId}`) === '0',
      'Delete Selected did not archive the saved query');
    h.assert(sql.value(`SELECT archived FROM demographicQueryFavourites WHERE favId=${keptId}`) === '1',
      'Delete Selected archived a saved query that was not selected');
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

  await s.step('both report write intents refuse GET and HEAD with 405, Allow POST and no rows', async () => {
    for (const method of ['GET', 'HEAD']) for (const [index, intent] of WRITE_INTENTS.entries()) {
      await assertReportMutationRefused(s, {method, intent, ...reportMutationNames(marker, `${method[0]}${index}`)});
    }
  });

  await s.step('both report write intents refuse tokenless POST without storing rows', async () => {
    for (const [index, intent] of WRITE_INTENTS.entries()) {
      await assertReportMutationRefused(s, {method: 'POST', intent, ...reportMutationNames(marker, `P${index}`)});
    }
  });
}

if (require.main === module) runWorkflow('demographic-report-favourites', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow, reportMutationNames, registerOwnedPatientSets, assertReportMutationRefused, submitReportWrite};
