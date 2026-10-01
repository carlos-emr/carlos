#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Query By Example and its favourites through the packaged front door (carlos-emr/carlos#4133).
 *
 * User path: Administration ▸ Query By Example (oscarReport/RptByExample) ▸ run a query;
 * View Query History ▸ Add to favourite ▸ name it ▸ Add (oscarReport/RptByExamplesFavorite);
 * back on Query By Example ▸ pick the favourite ▸ Load Query ▸ run it.
 *
 * Before #4133 every run was answered 403 by CRS 942100 (libinjection reads the query as SQL
 * injection, which is exactly what it is), and so was every favourite save. Packaged
 * exclusions 1400/1401 now unhook ARGS:sql / ARGS:query / ARGS:newQuery from the SQLi family
 * on those POST routes only, and the application's QueryByExampleSqlValidator is what decides
 * what may run. This check asserts both halves:
 *   - real queries (comparison operators, LIKE, functions) run and show the owned patient,
 *     and are recorded in the query history;
 *   - a write statement reaches the application and is REFUSED there (validation message),
 *     leaving the patient row unchanged;
 *   - a favourite can be saved from the history and run again from the picker, and the
 *     picker no longer posts the stored SQL a second time (selectedRecentSearch);
 *   - with EXPECT_FRONT_DOOR=true, the exclusion stays narrow: an injection payload in any
 *     OTHER argument of the same route, and SQL on a GET, are still refused with 403.
 *
 * Fixtures: the owned FAKE- patient from runWorkflow; cleanup removes the history rows and
 * favourites carrying the run marker.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 30000;

async function csrfToken(page, baseUrl) {
  return page.evaluate(async (tokenUrl) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- tokenUrl is a Playwright argument built by appUrl from the validated base URL
    const response = await fetch(tokenUrl, { credentials: 'same-origin' });
    const match = (await response.text()).match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
    return match ? match[1] : '';
  }, h.appUrl(baseUrl, '/csrfguard'));
}

async function workflow(s) {
  const like = h.sqlString(`%${s.marker}%`);
  const favouriteName = `${s.marker} roster`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM reportByExamples WHERE query LIKE ${like};
      DELETE FROM reportByExamplesFavorite WHERE query LIKE ${like} OR name LIKE ${like}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM reportByExamples WHERE query LIKE ${like})
      + (SELECT COUNT(*) FROM reportByExamplesFavorite WHERE query LIKE ${like} OR name LIKE ${like})`) === '0',
    'Query By Example rows created by the run were not removed');
  });
  const query = `select demographic_no, last_name, first_name from demographic where last_name = '${s.marker}'`
    + " and year_of_birth > '1900' and year_of_birth < '2100' and sex <> 'X' and first_name like 'Work%'"
    + " and date_format(lastUpdateDate, '%Y') >= '2000'";
  const firstName = () => s.sql.value(`SELECT first_name FROM demographic WHERE demographic_no=${s.patient}`);

  const page = await s.context.newPage();
  const runQuery = async sql => {
    if (sql !== null) await page.locator('textarea#sql').fill(sql);
    const [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST' && r.request().isNavigationRequest()
        && new URL(r.url()).pathname.endsWith('/oscarReport/RptByExample'), { timeout: TIMEOUT }),
      page.locator('#queryForm button[type="submit"]').click(),
    ]);
    await page.waitForLoadState('domcontentloaded');
    return response;
  };

  await s.step('a query with comparison operators runs and shows the owned patient', async () => {
    await h.gotoApp(page, s.config.baseUrl, '/oscarReport/RptByExample');
    await page.locator('textarea#sql').waitFor();
    const response = await runQuery(query);
    h.assert(response.status() === 200, `Running the query answered HTTP ${response.status()} (403 is the WAF refusing the SQL)`);
    const refusal = page.locator('#queryForm .alert-danger');
    h.assert(await refusal.count() === 0, `The query was refused by the application: ${await refusal.first().innerText().catch(() => '')}`);
    h.assert((await page.locator('body').innerText()).includes(s.marker), 'The results do not show the owned patient');
    await expectValue(s.sql, `SELECT COUNT(*) FROM reportByExamples WHERE query=${h.sqlString(query)} AND providerNo=${h.sqlString(s.provider)}`,
      '1', 'The run was not recorded in the query history');
  });

  await s.step('a write statement reaches the application and is refused there', async () => {
    const before = firstName();
    const write = `update demographic set first_name = 'FAKE-PW-written' where last_name = '${s.marker}'`;
    const response = await runQuery(write);
    h.assert(response.status() === 200, `The write statement answered HTTP ${response.status()} instead of the validation page`);
    await page.locator('#queryForm .alert-danger').first().waitFor({ timeout: TIMEOUT });
    h.assert(firstName() === before, 'Query By Example executed a write statement');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM reportByExamples WHERE query=${h.sqlString(write)}`) === '0',
      'A refused statement was recorded in the history');
  });

  await s.step('View Query History ▸ Add to favourite saves the query as a named favourite', async () => {
    const history = await s.popup(page, page.locator('a[onclick*="RptViewAllQueryByExamples"]'), 'qbe-history');
    const row = history.locator('tr', { hasText: s.marker }).first();
    await row.waitFor({ timeout: TIMEOUT });
    await Promise.all([
      history.waitForURL(url => url.pathname.endsWith('/oscarReport/RptByExamplesFavorite'), { timeout: TIMEOUT }),
      row.locator('input[type="button"]').click(),
    ]);
    const editor = history.locator('textarea#query');
    await editor.waitFor({ timeout: TIMEOUT });
    h.assert((await editor.inputValue()) === query, 'The favourite editor did not receive the stored query');
    await history.locator('input#favoriteName').fill(favouriteName);
    const [saved] = await Promise.all([
      history.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/oscarReport/RptByExamplesFavorite'), { timeout: TIMEOUT }),
      history.locator('input[type="submit"]').click(),
    ]);
    h.assert(saved.status() === 200, `Saving the favourite answered HTTP ${saved.status()}`);
    await expectValue(s.sql, `SELECT COUNT(*) FROM reportByExamplesFavorite WHERE name=${h.sqlString(favouriteName)}
      AND query=${h.sqlString(query)} AND providerNo=${h.sqlString(s.provider)}`, '1', 'The favourite was not stored');
    await history.close();
  });

  await s.step('the favourites picker loads the query without posting it a second time', async () => {
    await h.gotoApp(page, s.config.baseUrl, '/oscarReport/RptByExample');
    await page.locator('#selectedRecentSearch').selectOption({ value: query });
    await page.locator('input[type="button"][onclick*="write2TextArea"]').click();
    h.assert((await page.locator('textarea#sql').inputValue()) === query, 'Load Query did not fill the textarea');
    let posted = '';
    const onRequest = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/oscarReport/RptByExample')) posted = request.postData() || '';
    };
    s.context.on('request', onRequest);
    try {
      const response = await runQuery(null);
      h.assert(response.status() === 200, `Running the favourite answered HTTP ${response.status()}`);
    } finally { s.context.off('request', onRequest); }
    h.assert(!new URLSearchParams(posted).has('selectedRecentSearch'), 'The picker still posts the stored SQL');
    h.assert((await page.locator('body').innerText()).includes(s.marker), 'The favourite run does not show the owned patient');
  });

  await s.step('the WAF exclusion stays narrow: other arguments and GET are still inspected', async () => {
    if (!s.config.expectFrontDoor) {
      console.log('  NOTE report-query-by-example-front-door: WAF narrowness not asserted (EXPECT_FRONT_DOOR is not true)');
      return;
    }
    const token = await csrfToken(page, s.config.baseUrl);
    const url = h.appUrl(s.config.baseUrl, '/oscarReport/RptByExample');
    const probe = await s.context.request.post(url, { headers: { 'CSRF-TOKEN': token }, maxRedirects: 0, form: {
      'CSRF-TOKEN': token, sql: 'select 1', selectedRecentSearch: "1' or '1'='1' union select password from security--" } });
    h.assert(probe.status() === 403, `An injection payload in another argument answered HTTP ${probe.status()}, not 403`);
    const viaGet = await s.context.request.get(url, { maxRedirects: 0,
      params: { sql: `select demographic_no from demographic where last_name = '${s.marker}'` } });
    h.assert(viaGet.status() === 403, `SQL on a GET answered HTTP ${viaGet.status()}, not 403`);
  });
  await page.close();
}

if (require.main === module) runWorkflow('report-query-by-example-front-door', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
