#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser checks for the small fixes ported from MagentaHealth/Open-O in #3891
 * (issues #3882 and #3886). Each part drives the surface a clinician uses:
 *
 *   1. Provider autocomplete with a trailing comma ("Lastname,"). String.split
 *      dropped the empty trailing segment and array[1] threw, so the search
 *      endpoint answered 500 and the autocomplete showed nothing.
 *   2. The patient's document list (ViewDocumentReport) is ordered newest-first.
 *      The table was configured with the invalid DataTables direction 'dsc'.
 *   3. The consultation request header shows the health card as
 *      "number version (type)"; the three parts used to be run together.
 *
 * Read-only: nothing is created or changed.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_* (DB lookups only)
 */

const {
  SkipCheck,
  assert,
  assertNotErrorPage,
  assertStrictPage,
  createRecorder,
  createSqlRunner,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  runCheck,
  wireStrictPage,
} = require('./lib/playwright-harness');

async function checkProviderTrailingComma(page, sql) {
  const [lastName] = sql.rows(
    "SELECT last_name FROM provider WHERE status='1' AND last_name <> '' "
    + "AND last_name NOT LIKE '%,%' ORDER BY provider_no LIMIT 1",
  )[0] || [];
  if (!lastName) throw new SkipCheck('no active provider with a last name to search for');
  // Same request the jQuery UI provider autocomplete (js/carlosAutocomplete.js) sends.
  const result = await page.evaluate(async ({ term }) => {
    const response = await fetch(`${window.location.pathname.split('/').slice(0, 2).join('/')}`
      + `/provider/SearchProvider?query=${encodeURIComponent(term)}`, { credentials: 'same-origin' });
    const text = await response.text();
    return { status: response.status, text };
  }, { term: `${lastName},` });
  assert(result.status === 200, `provider search for "<last name>," answered HTTP ${result.status}`);
  let body;
  try {
    body = JSON.parse(result.text);
  } catch (error) {
    throw new Error('provider search for "<last name>," did not answer JSON');
  }
  assert(Array.isArray(body.results) && body.results.length > 0,
    'provider search for "<last name>," returned no providers');
  return body.results.length;
}

async function checkDocumentReportOrder(context, config, recorder, sql) {
  const [demographicNo] = sql.rows(
    'SELECT c.module_id FROM ctl_document c JOIN document d ON d.document_no = c.document_no '
    + "WHERE c.module = 'demographic' AND d.status <> 'D' GROUP BY c.module_id "
    + 'HAVING COUNT(DISTINCT DATE(d.updatedatetime)) > 1 ORDER BY COUNT(*) DESC LIMIT 1',
  )[0] || [];
  if (!demographicNo) throw new SkipCheck('no patient has documents on two different dates');
  const page = await context.newPage();
  wireStrictPage(page, 'document-report', recorder);
  const response = await gotoApp(page, config.baseUrl,
    `/documentManager/ViewDocumentReport?function=demographic&doctype=&functionid=${encodeURIComponent(demographicNo)}`);
  assert(response && response.status() === 200, `document list answered HTTP ${response ? response.status() : 'none'}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'document list');
  const table = page.locator("table[id^='tblDocs']").first();
  await table.waitFor({ state: 'attached', timeout: 30000 });
  const state = await page.evaluate(() => {
    const element = document.querySelector("table[id^='tblDocs']");
    const api = window.jQuery(element).DataTable();
    const header = element.querySelectorAll('thead th')[6];
    return {
      order: api.order(),
      ariaSort: header ? header.getAttribute('aria-sort') : null,
      rows: api.rows({ search: 'applied' }).count(),
    };
  });
  assert(Array.isArray(state.order) && state.order.length > 0
    && Number(state.order[0][0]) === 6 && state.order[0][1] === 'desc',
    `document list initial order is ${JSON.stringify(state.order)}, expected column 6 descending`);
  assert(state.ariaSort === 'descending',
    `the sorted column header reports aria-sort=${state.ariaSort}, expected descending`);
  await page.close();
  return state.rows;
}

async function checkConsultationHealthCard(context, config, recorder, sql) {
  const row = sql.rows(
    "SELECT demographic_no, hin, ver, hc_type FROM demographic WHERE hin <> '' AND ver <> '' "
    + "AND hc_type <> '' AND patient_status = 'AC' ORDER BY demographic_no LIMIT 1",
  )[0];
  if (!row) throw new SkipCheck('no active patient has a health number, version code and card type');
  const [demographicNo, hin, ver, type] = row;
  const listPage = await context.newPage();
  wireStrictPage(listPage, 'consult-list', recorder);
  await gotoApp(listPage, config.baseUrl,
    `/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${encodeURIComponent(demographicNo)}`);
  await listPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(listPage, 'consultation list');
  // Operator path: the "New Consultation" button opens the request form in a popup.
  const [formPage] = await Promise.all([
    context.waitForEvent('page', { timeout: 30000 }),
    listPage.locator('a.btn', { hasText: /New Consultation/i }).first().click(),
  ]);
  wireStrictPage(formPage, 'consult-form', recorder);
  await formPage.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await formPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(formPage, 'consultation form');
  const text = await formPage.locator('body').innerText();
  const expected = `${hin} ${ver} (${type})`;
  // Assert the shape without echoing the health number into a failure message.
  assert(text.includes(expected),
    'consultation header does not show the health card as "number version (type)"');
  assert(!text.includes(`${hin}${ver}${type}`), 'consultation header still runs the health card parts together');
  await formPage.close();
  await listPage.close();
}

async function main() {
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  const recorder = createRecorder();
  let browser;
  try {
    browser = await launchBrowser(config);
    const context = await newContext(browser, config, { viewport: { width: 1440, height: 1100 } });
    const schedule = await login(context, config, recorder);
    const providers = await checkProviderTrailingComma(schedule, sql);
    const documents = await checkDocumentReportOrder(context, config, recorder, sql);
    await checkConsultationHealthCard(context, config, recorder, sql);
    assertStrictPage(recorder);
    return { providers, documents };
  } finally {
    try {
      if (browser) await browser.close();
    } finally {
      sql.dispose();
    }
  }
}

if (require.main === module) runCheck({ name: 'upstream-small-fixes', run: main });
module.exports = { main };
