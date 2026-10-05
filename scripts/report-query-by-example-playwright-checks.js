#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Query by Example report. User path: Schedule ▸ Administration ▸ Reports ▸ Query By Example
 * (the /administration shell loads oscarReport/RptByExample into #myFrame); View Query History
 * and Edit My Favorite open as popups from that frame.
 * Asserts: a SELECT typed into the textarea renders exactly the rows MariaDB returns for the
 * owned FAKE- patient and is recorded in reportByExamples; quotes/markup round-trip encoded;
 * History ▸ Add to Favorite ▸ name ▸ Add persists a reportByExamplesFavorite row, Close reloads
 * the frame, Load Query copies the favourite into the textarea and re-runs it; Edit renames it;
 * Delete is refused on a dismissed confirm and removes the row on an accepted one. Negative
 * probes through the same textarea (UPDATE, DELETE, DROP, stacked statements, security table,
 * INTO OUTFILE, SLEEP, UNION, FOR UPDATE) each render the validation alert, write no history and
 * leave the owned row unchanged; a GET of the run action executes nothing; a GET of the favourite
 * mutator answers 405; another provider's favourite cannot be deleted with this session's token.
 * Fixtures: the session's patient; history/favourite rows carrying the marker (one favourite is
 * seeded for another provider). Cleanup deletes only marker rows and asserts they are gone.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §3.6 report-query-by-example.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {clickOpensPopup, clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 20000;
const VALIDATION_KEY = 'oscarReport.RptByExample.MsgValidationError';
const VALIDATION_FALLBACK = 'Only one read-only SELECT from the application database is allowed. '
  + 'Comments, UNION, locking, output operations, and prohibited functions are not permitted.';

// The English bundle text, read from the source tree so a reworded message moves the
// assertion with it; the fallback serves a packaged copy that carries no src/.
function bundleMessage(key, fallback) {
  try {
    const bundle = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'oscarResources_en.properties'), 'utf8');
    const line = bundle.split('\n').find(candidate => candidate.startsWith(`${key}=`));
    return line ? line.slice(key.length + 1).trim() : fallback;
  } catch (error) { return fallback; }
}

// Wait for the frame (or page) the click navigates, then for its requests to settle.
async function clickAndNavigate(page, frame, locator) {
  const navigated = page.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: TIMEOUT});
  navigated.catch(() => {});
  await locator.click();
  await navigated;
  await frame.waitForLoadState('networkidle', {timeout: TIMEOUT});
}

// The result table RptResultStruct renders: bare <th> cells followed by <tr><td> rows.
async function renderedTable(frame) {
  const table = frame.locator('table#results');
  await table.waitFor({state: 'visible', timeout: TIMEOUT});
  const headers = (await table.locator('th').allInnerTexts()).map(text => text.trim());
  const rows = await table.locator('tr:has(td)').evaluateAll(trs => trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent)));
  return {headers, rows};
}

async function workflow(s) {
  const {sql, provider, patient, marker, context, config} = s;
  const providerLiteral = h.sqlString(provider);
  const markerLiteral = h.sqlString(marker);
  const favouriteName = `${marker} favourite`;
  const query = `SELECT demographic_no, last_name, first_name FROM demographic WHERE last_name=${markerLiteral}`;
  // SQL-standard quote doubling: both the QBE parser and the mysql client read it the same way.
  const noteLiteral = '\'O\'\'Neil "A&B" <b>bold</b> 100% done\'';
  const punctuationQuery = `SELECT demographic_no, CONCAT(last_name, ' ', ${noteLiteral}) AS note FROM demographic WHERE last_name=${markerLiteral}`;
  const validationText = bundleMessage(VALIDATION_KEY, VALIDATION_FALLBACK);
  const historyCount = () => sql.value(`SELECT COUNT(*) FROM reportByExamples WHERE providerNo=${providerLiteral}
    AND query LIKE ${h.sqlString('%' + marker + '%')}`);
  const patientSnapshot = () => JSON.stringify(sql.rows(`SELECT demographic_no,last_name,first_name,patient_status FROM demographic WHERE demographic_no=${patient}`));
  s.cleanup(() => {
    sql.execute(`DELETE FROM reportByExamplesFavorite WHERE name LIKE ${h.sqlString(marker + '%')}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamplesFavorite WHERE name LIKE ${h.sqlString(marker + '%')}`) === '0',
      'Owned favourite rows were not removed');
    sql.execute(`DELETE FROM reportByExamples WHERE query LIKE ${h.sqlString('%' + marker + '%')}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamples WHERE query LIKE ${h.sqlString('%' + marker + '%')}`) === '0',
      'Owned query history rows were not removed');
  });
  h.assert(historyCount() === '0', 'Marker history rows exist before the first run');
  const before = patientSnapshot();

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder: s.recorder, label: 'qbe-administration', timeout: TIMEOUT});
  const link = admin.getByRole('link', {name: 'Query By Example', exact: true, includeHidden: true});
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The Query By Example frame did not load');
  await frame.locator('#queryForm').waitFor({timeout: TIMEOUT});
  const runQuery = async text => {
    await frame.locator('#sql').fill(text);
    await clickAndNavigate(admin, frame, frame.locator('#queryForm button[type="submit"]'));
  };
  const visibleAlerts = () => frame.locator('.alert[role="alert"]:visible');

  await s.step('a typed SELECT renders exactly the rows MariaDB returns and is recorded in history', async () => {
    await runQuery(query);
    const {headers, rows} = await renderedTable(frame);
    h.assert(JSON.stringify(headers) === JSON.stringify(['demographic_no', 'last_name', 'first_name']), 'Result headers differ from the selected columns');
    h.assert(JSON.stringify(rows) === JSON.stringify(sql.rows(query)), 'Rendered rows differ from the SQL result');
    h.assert(rows.length === 1 && rows[0][0] === patient, 'The query did not return exactly the owned patient');
    // MessageFormat renders the {1} limit with locale grouping ("1,000"). The line carries
    // only counts, so it is safe to quote in a failure.
    const summary = (await frame.locator('p.text-muted.small').first().innerText()).replace(/\s+/g, ' ').trim();
    h.assert(/^Returned 1 rows? \(limit: 1[,.  ]?000\)\.?$/.test(summary), `The row-count summary reads "${summary}"`);
    h.assert(await visibleAlerts().count() === 0, 'A successful run rendered an alert');
    h.assert(await frame.locator('#sql').inputValue() === query, 'The submitted SQL was not echoed back into the textarea');
    h.assert(historyCount() === '1', 'The successful run was not recorded in the query history');
    h.assert(sql.value(`SELECT query FROM reportByExamples WHERE providerNo=${providerLiteral} AND query LIKE ${h.sqlString('%' + marker + '%')}
      ORDER BY id DESC LIMIT 1`) === query, 'History stored a different query text');
  });

  await s.step('quotes and markup in a result cell render encoded, byte for byte', async () => {
    await runQuery(punctuationQuery);
    const {rows} = await renderedTable(frame);
    h.assert(JSON.stringify(rows) === JSON.stringify(sql.rows(punctuationQuery)), 'Punctuation result differs from the SQL result');
    h.assert(rows[0][1].endsWith('O\'Neil "A&B" <b>bold</b> 100% done'), 'The punctuation literal did not round-trip');
    h.assert(await frame.locator('table#results b').count() === 0, 'Result markup was interpreted instead of encoded');
    h.assert(historyCount() === '2', 'The second run was not recorded in history');
  });

  await s.step('write, stacked, secret-table, output, function, UNION and locking statements are refused', async () => {
    const refused = [
      ['UPDATE', `UPDATE demographic SET first_name='Changed' WHERE last_name=${markerLiteral}`],
      ['DELETE', `DELETE FROM demographic WHERE last_name=${markerLiteral}`],
      ['DROP', `DROP TABLE IF EXISTS \`${marker}_probe\``],
      ['stacked statements', `${query}; DELETE FROM demographic WHERE last_name=${markerLiteral}`],
      ['authentication table', `SELECT user_name FROM security WHERE user_name=${h.sqlString(config.testUser)}`],
      ['INTO OUTFILE', `${query} INTO OUTFILE '/tmp/${marker}.txt'`],
      ['prohibited function', `SELECT SLEEP(1) FROM demographic WHERE last_name=${markerLiteral}`],
      ['UNION', `${query} UNION SELECT 1, 2, 3`],
      ['locking SELECT', `${query} FOR UPDATE`],
    ];
    for (const [label, statement] of refused) {
      await runQuery(statement);
      const alerts = visibleAlerts();
      h.assert(await alerts.count() === 1 && (await alerts.first().innerText()).trim() === validationText,
        `${label}: the validation message was not the only alert rendered; the statement may have been accepted`);
      h.assert(await frame.locator('table#results').count() === 0, `${label}: results were rendered for a refused statement`);
      h.assert(patientSnapshot() === before, `${label}: the owned patient row changed`);
      h.assert(historyCount() === '2', `${label}: a refused statement was recorded in history`);
    }
  });

  await s.step('a GET of the run action renders the form without executing or recording the query', async () => {
    const response = await context.request.get(h.appUrl(config.baseUrl, '/oscarReport/RptByExample'), {params: {sql: query}, maxRedirects: 0});
    const body = await response.text();
    // Through the packaged front door the WAF may refuse SQL in a query string outright (403);
    // reaching the action, a GET must only render the empty form.
    if (config.expectFrontDoor && response.status() === 403) console.log('  note: the front door refused the SQL-bearing GET before it reached the action');
    else h.assert(response.status() === 200 && body.includes('id="queryForm"') && !body.includes("<table id='results'>"),
      `A GET of the run action answered HTTP ${response.status()} or executed the query`);
    h.assert(historyCount() === '2', 'A GET recorded a history row');
  });

  let favouriteId;
  await s.step('History ▸ Add to Favorite persists the favourite and Close reloads the frame', async () => {
    const history = await clickOpensPopup(admin, frame.getByRole('link', {name: 'View Query History'}),
      {context, recorder: s.recorder, label: 'qbe-history', timeout: TIMEOUT});
    const row = history.locator('tr', {has: history.getByText(query, {exact: true})}).first();
    await row.waitFor({timeout: TIMEOUT});
    await clickAndNavigate(history, history.mainFrame(), row.getByRole('button', {name: 'Add to Favorite'}));
    h.assert(await history.locator('#query').inputValue() === query, 'The favourite editor did not carry the history query');
    await history.locator('#favoriteName').fill(favouriteName);
    await clickAndNavigate(history, history.mainFrame(), history.locator('input[type="submit"]'));
    const listed = history.locator('#favoritesForm tbody tr', {hasText: favouriteName});
    h.assert(await listed.count() === 1 && (await listed.innerText()).includes(query), 'The favourites list does not show the new favourite with its query');
    favouriteId = sql.value(`SELECT id FROM reportByExamplesFavorite WHERE providerNo=${providerLiteral} AND name=${h.sqlString(favouriteName)}`);
    h.assert(/^[1-9]\d*$/.test(favouriteId), 'The favourite did not reach the database');
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamplesFavorite WHERE name=${h.sqlString(favouriteName)}`) === '1', 'The favourite was stored more than once');
    h.assert(sql.value(`SELECT query FROM reportByExamplesFavorite WHERE id=${favouriteId}`) === query, 'The favourite stored a different query text');
    const rejected = await context.request.get(h.appUrl(config.baseUrl, '/oscarReport/RptByExamplesFavorite'),
      {params: {toDelete: 'true', id: favouriteId}, maxRedirects: 0});
    h.assert(rejected.status() === 405 && rejected.headers().allow === 'POST', 'GET of the favourite mutator was not rejected with Allow: POST');
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamplesFavorite WHERE id=${favouriteId}`) === '1', 'A rejected GET deleted the favourite');
    const reloaded = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: TIMEOUT});
    reloaded.catch(() => {});
    await Promise.all([history.waitForEvent('close', {timeout: TIMEOUT}), history.getByRole('button', {name: 'Close'}).click()]);
    await reloaded;
    await frame.waitForLoadState('networkidle', {timeout: TIMEOUT});
  });

  await s.step('Load Query copies the favourite into the textarea and re-runs it', async () => {
    await frame.locator('#selectedRecentSearch').selectOption({value: query});
    await frame.getByRole('button', {name: 'Load Query'}).click();
    h.assert(await frame.locator('#sql').inputValue() === query, 'Load Query did not copy the favourite into the textarea');
    await clickAndNavigate(admin, frame, frame.locator('#queryForm button[type="submit"]'));
    const {rows} = await renderedTable(frame);
    h.assert(JSON.stringify(rows) === JSON.stringify(sql.rows(query)), 'The favourite did not return the same rows');
    h.assert(historyCount() === '3', 'Running the favourite was not recorded in history');
  });

  await s.step('Edit My Favorite renames it, refuses a foreign delete, and deletes it only on confirmation', async () => {
    const favourites = await clickOpensPopup(admin, frame.getByRole('link', {name: 'Edit My Favorite'}),
      {context, recorder: s.recorder, label: 'qbe-favourites', timeout: TIMEOUT});
    const row = favourites.locator('#favoritesForm tbody tr', {hasText: favouriteName});
    h.assert(await row.count() === 1, 'The favourites editor does not list the owned favourite');
    await clickAndNavigate(favourites, favourites.mainFrame(), row.getByRole('button', {name: 'Edit'}));
    h.assert(await favourites.locator('#favoriteName').inputValue() === favouriteName
      && await favourites.locator('#query').inputValue() === query
      && await favourites.locator('input[name="id"]').inputValue() === favouriteId, 'The favourite editor opened a different favourite');
    await favourites.locator('#favoriteName').fill(`${favouriteName}-EDIT`);
    await clickAndNavigate(favourites, favourites.mainFrame(), favourites.locator('input[type="submit"]'));
    const edited = favourites.locator('#favoritesForm tbody tr', {hasText: `${favouriteName}-EDIT`});
    h.assert(await edited.count() === 1 && await favourites.locator('#favoritesForm tbody tr', {hasText: favouriteName}).count() === 1,
      'The rename did not replace the listed favourite');
    h.assert(sql.value(`SELECT CONCAT(name,'|',query) FROM reportByExamplesFavorite WHERE id=${favouriteId}`) === `${favouriteName}-EDIT|${query}`,
      'The rename did not persist on the same favourite');
    const otherProvider = sql.value(`SELECT provider_no FROM provider WHERE provider_no<>${providerLiteral} ORDER BY provider_no LIMIT 1`);
    h.assert(otherProvider, 'A second provider is required for the foreign-favourite probe');
    const foreignId = sql.value(`INSERT INTO reportByExamplesFavorite (providerNo,query,name)
      VALUES (${h.sqlString(otherProvider)},'SELECT 1',${h.sqlString(marker + '-OTHER')}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(foreignId), 'The foreign favourite fixture was not created');
    await favourites.waitForFunction(() => document.querySelector('input[name="CSRF-TOKEN"]')?.value);
    const token = await favourites.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const action = await favourites.locator('#favoritesForm').evaluate(form => form.action);
    const forged = await context.request.post(action, {headers: {'CSRF-TOKEN': token},
      form: {'CSRF-TOKEN': token, toDelete: 'true', id: foreignId, newName: '', newQuery: ''}, maxRedirects: 0});
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamplesFavorite WHERE id=${foreignId}`) === '1', 'Another provider\'s favourite was deleted');
    h.assert(!(await forged.text()).includes(`${favouriteName}-EDIT`), 'A foreign delete answered with this provider\'s favourites list');
    // A dismissed confirm must post nothing: count requests to the mutator while it is refused.
    let posts = 0;
    const record = request => { if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/oscarReport/RptByExamplesFavorite')) posts++; };
    context.on('request', record);
    let dismissed;
    try {
      dismissed = await h.withExpectedDialogs(favourites, async () => {
        await edited.getByRole('button', {name: 'Delete'}).click();
        await favourites.waitForLoadState('networkidle', {timeout: TIMEOUT});
      }, {accept: false});
    } finally { context.off('request', record); }
    h.assert(dismissed.length === 1 && dismissed[0].type === 'confirm', 'Delete did not ask for confirmation exactly once');
    h.assert(posts === 0 && await edited.count() === 1
      && sql.value(`SELECT COUNT(*) FROM reportByExamplesFavorite WHERE id=${favouriteId}`) === '1',
    'A dismissed confirmation still submitted or deleted the favourite');
    const accepted = await h.withExpectedDialogs(favourites, async () => {
      await clickAndNavigate(favourites, favourites.mainFrame(), edited.getByRole('button', {name: 'Delete'}));
    });
    h.assert(accepted.length === 1 && accepted[0].type === 'confirm', 'The accepted delete did not raise exactly one confirm');
    h.assert(await favourites.locator('#favoritesForm tbody tr', {hasText: marker}).count() === 0, 'The deleted favourite is still listed');
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamplesFavorite WHERE id=${favouriteId}`) === '0', 'The delete did not reach the database');
    const reloaded = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: TIMEOUT});
    reloaded.catch(() => {});
    await Promise.all([favourites.waitForEvent('close', {timeout: TIMEOUT}), favourites.getByRole('button', {name: 'Close'}).click()]);
    await reloaded;
    await frame.waitForLoadState('networkidle', {timeout: TIMEOUT});
    h.assert(await frame.locator('#selectedRecentSearch option', {hasText: marker}).count() === 0, 'The reloaded frame still offers the deleted favourite');
  });
}
if (require.main === module) runWorkflow('report-query-by-example', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
