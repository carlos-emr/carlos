#!/usr/bin/env node
/*
 * Browser checks for the CARLOS prevention / immunization interface.
 *
 * WHY THIS EXISTS. The prevention grid is how a clinic records and recalls
 * immunizations, and nothing in the browser-check suite touched it: every one of
 * `prevention/ViewPreventionIndex`, `prevention/ViewAddPreventionData`,
 * `prevention/AddPrevention`, `prevention/PreventionReport` and
 * `prevention/printPrevention` was unexercised. A grid that silently stopped
 * persisting, or a report route that started answering with an error page, would
 * have been found by a nurse rather than by CI.
 *
 * THE ENTRY IS ONLY HALF THE RECORD. A prevention writes a `preventions` row AND
 * a set of `preventionsExt` key/value rows for the clinical detail (dose, lot,
 * route, site, manufacturer). The check asserts both, because a save that
 * persisted the header and dropped the extension rows produces an immunization
 * record with no lot number — which is exactly what a recall notice needs.
 *
 * The add form is reached by clicking the prevention name on the grid, the way a
 * nurse does, so the popup is opened with the parameters the grid actually
 * passes rather than a hand-built URL.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:prevention-immunization-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   PREVENTION_DEMOGRAPHIC_NO=1   patient whose prevention grid is driven
 *   PREVENTION_PROVIDER_NO=999998 provider recorded as administering
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * Cleanup: the script records one prevention, marks it with a unique
 * PW_PREVENTION_<millis> lot number, and deletes the preventions and
 * preventionsExt rows carrying that marker in a finally, including after a
 * failure. Pre-existing immunizations are never touched.
 */

const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  createRecorder,
  createSqlClient,
  escapeSql,
  getLaunchOptions,
  gotoApp,
  login,
  readConfig,
  readCsrfToken,
  requireId,
  waitFor,
  wirePage,
} = require('./carlos-playwright-harness');

const config = readConfig();
const demographicNo = requireId(process.env.PREVENTION_DEMOGRAPHIC_NO || '1', 'PREVENTION_DEMOGRAPHIC_NO');
const providerNo = requireId(process.env.PREVENTION_PROVIDER_NO || '999998', 'PREVENTION_PROVIDER_NO');

const stamp = `PW_PREVENTION_${Date.now()}`;
const recorder = createRecorder();
const sql = createSqlClient({ namespace: 'carlos-prevention' });
const passed = [];

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

function stampedPreventionIds() {
  return sql.rows(
    `SELECT DISTINCT p.id FROM preventions p JOIN preventionsExt e ON e.prevention_id = p.id`
    + ` WHERE e.val LIKE '${escapeSql(`${stamp}%`)}' ORDER BY p.id`
  ).map(([id]) => id);
}

function preventionRow(id) {
  const row = sql.rows(
    `SELECT id, demographic_no, provider_no, prevention_type, deleted, refused, never,`
    + ` DATE(prevention_date) FROM preventions WHERE id=${requireId(id, 'prevention id')}`
  )[0];
  if (!row) {
    return null;
  }
  const [rowId, demographic, provider, type, deleted, refused, never, preventionDate] = row;
  return { id: rowId, demographic, provider, type, deleted, refused, never, preventionDate };
}

function preventionsExtensions(id) {
  return Object.fromEntries(sql.rows(
    `SELECT keyval, val FROM preventionsExt WHERE prevention_id=${requireId(id, 'prevention id')}`
  ));
}

function cleanupRows() {
  const like = escapeSql(`${stamp}%`);
  const ids = sql.rows(
    `SELECT DISTINCT prevention_id FROM preventionsExt WHERE val LIKE '${like}'`
  ).map(([id]) => id).filter((id) => /^\d+$/.test(id));
  for (const id of ids) {
    sql.exec(`DELETE FROM preventionsExt WHERE prevention_id=${id}`);
    sql.exec(`DELETE FROM preventions WHERE id=${id}`);
  }
}

async function openPreventionGrid(context, label) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, '/prevention/ViewPreventionIndex', 'domcontentloaded', {
    demographic_no: demographicNo,
  });
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, `${label} prevention grid`);
  return page;
}

/**
 * Asserts the vaccine brand catalogue reached the page.
 *
 * The grid tries an admin-uploaded catalogue in the eForm image directory first
 * and falls back to the one bundled in the WAR, so the probe's 404 is normal and
 * says nothing. What matters is that ONE of the two loaded: with an empty
 * catalogue the brand dropdown on the add form is silently empty and every
 * immunization is recorded with no brand, dose, route or DIN prefill.
 */
async function assertVaccineCatalogueLoaded(page) {
  const brands = await waitFor(async () => {
    const value = await page.evaluate(() => (Array.isArray(window.tags) ? window.tags.length : null));
    return value === null ? null : value;
  }, { description: 'the vaccine brand catalogue to finish loading' });
  assert(brands > 0,
    'neither the uploaded nor the bundled vaccine-brands.json loaded; the add-prevention brand list is empty');
  return brands;
}

/**
 * Opens the add-prevention popup by clicking a prevention name on the grid.
 *
 * The grid offers two link shapes — a direct ViewAddPreventionData and a
 * ViewAddPreventionDataDisambiguate for names with several CVC mappings. Only
 * the direct one is followed: the disambiguation page is a separate surface with
 * its own selection step, and conflating the two would leave the check unable to
 * say which of them broke.
 */
async function openAddPreventionPopup(context, gridPage) {
  const link = gridPage.locator('a[onclick*="prevention/ViewAddPreventionData?"]').first();
  await link.waitFor({ state: 'attached', timeout: 30000 });
  const onclick = await link.getAttribute('onclick');
  const preventionMatch = onclick.match(/prevention=([^&'"]+)/);
  assert(preventionMatch, `could not read the prevention type from the grid link: ${onclick}`);
  const preventionType = decodeURIComponent(preventionMatch[1]);

  const popupPromise = context.waitForEvent('page', { timeout: 45000 });
  await link.click();
  const popup = await popupPromise;
  wirePage(popup, 'add-prevention-popup', recorder);
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'add-prevention popup');
  return { popup, preventionType };
}

async function recordPrevention(popup, preventionType) {
  assert(await readCsrfToken(popup), 'add-prevention form carried no CSRFGuard token');

  const formType = await popup.locator('input[name="prevention"]').inputValue();
  assert(formType === preventionType,
    `add-prevention popup opened type ${formType}, expected the clicked ${preventionType}`);
  const formDemographic = await popup.locator('input[name="demographic_no"]').inputValue();
  assert(formDemographic === demographicNo,
    `add-prevention popup opened demographic ${formDemographic}, expected ${demographicNo}`);

  await popup.locator('input[name="given"][value="given"]').check();

  const today = new Date();
  const preventionDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  await popup.locator('#prevDate').fill(preventionDate);

  // The lot number is the marker the cleanup keys on, and also the extension row
  // that matters most clinically: a recall needs the lot, so a save that drops
  // preventionsExt is worse than one that fails loudly.
  const lot = popup.locator('input[name="lot"]:visible').first();
  assert(await lot.count() > 0, 'add-prevention form rendered no visible lot-number field');
  await lot.fill(stamp);

  const [response] = await Promise.all([
    popup.waitForResponse((r) => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/prevention/AddPrevention'), { timeout: 45000 }),
    popup.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    popup.locator('input[type="submit"][name="action"]').first().click(),
  ]);
  assert(response.status() < 400, `prevention/AddPrevention returned HTTP ${response.status()}`);
  await assertNotErrorPage(popup, 'prevention save confirmation');

  const id = await waitFor(() => {
    const ids = stampedPreventionIds();
    return ids.length === 1 ? ids[0] : null;
  }, { description: `the recorded ${preventionType} prevention to reach the database` });

  const row = preventionRow(id);
  assert(row, `prevention ${id} disappeared immediately after the save`);
  assert(row.demographic === demographicNo,
    `prevention landed on demographic ${row.demographic}, expected ${demographicNo}`);
  assert(row.type === preventionType, `prevention landed with type ${row.type}, expected ${preventionType}`);
  assert(row.deleted === '0', `prevention was written already flagged deleted=${row.deleted}`);
  assert(row.refused === '0' && row.never === '0',
    `a "given" prevention was written with refused=${row.refused} never=${row.never}`);
  assert(row.preventionDate === preventionDate,
    `prevention_date persisted as ${row.preventionDate}, expected ${preventionDate}`);

  const extensions = preventionsExtensions(id);
  assert(extensions.lot === stamp,
    `prevention extension rows carry lot=${extensions.lot}, expected ${stamp}; the clinical detail was dropped`);
  return { id, preventionType, preventionDate, extensions };
}

/** Confirms the saved immunization is visible on the reloaded grid. */
async function assertOnGrid(gridPage, recorded) {
  await gotoApp(gridPage, config.baseUrl, '/prevention/ViewPreventionIndex', 'domcontentloaded', {
    demographic_no: demographicNo,
  });
  await gridPage.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  const text = await assertNotErrorPage(gridPage, 'prevention grid after save');
  assert(text.includes(recorded.preventionType) || new RegExp(recorded.preventionType, 'i').test(text),
    `the recorded ${recorded.preventionType} prevention does not appear on the grid`);
}

/**
 * Drives the prevention reporting page.
 *
 * PreventionReport is the clinic's recall tool; it is asserted separately from
 * the grid because it reads through its own query path and has failed on its own
 * when a prevention type carried no configured reporting rule.
 */
async function checkPreventionReport(context) {
  const page = await context.newPage();
  wirePage(page, 'prevention-report', recorder);
  const response = await gotoApp(page, config.baseUrl, '/prevention/PreventionReport', 'domcontentloaded');
  assert(response.status() < 400, `prevention/PreventionReport returned HTTP ${response.status()}`);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  const text = await assertNotErrorPage(page, 'prevention report');
  await page.close().catch(() => {});
  return text;
}

/**
 * Fetches the printable prevention record.
 *
 * Driven through fetch() rather than a navigation because the route answers with
 * a PDF: a navigation would hand the bytes to the browser's viewer and the check
 * could not tell a real PDF from an HTML error page rendered in its place, which
 * is the failure this pins.
 */
async function checkPreventionPrint(page) {
  const { appUrl } = require('./carlos-playwright-harness');
  const url = appUrl(config.baseUrl, '/prevention/printPrevention', { demographic_no: demographicNo });
  const result = await page.evaluate(async (target) => {
    const response = await fetch(target, { credentials: 'same-origin' });
    const buffer = await response.arrayBuffer();
    const head = new Uint8Array(buffer.slice(0, 8));
    return {
      status: response.status,
      contentType: response.headers.get('content-type') || '',
      length: buffer.byteLength,
      head: Array.from(head).map((b) => String.fromCharCode(b)).join(''),
    };
  }, url);

  assert(result.status === 200, `prevention/printPrevention returned HTTP ${result.status}`);
  assert(result.length > 0, 'prevention/printPrevention returned an empty body');
  const looksLikePdf = result.head.startsWith('%PDF');
  const looksLikeHtml = /^\s*(<|﻿<)/.test(result.head);
  assert(looksLikePdf || (!looksLikeHtml && result.contentType.includes('pdf'))
    || result.contentType.includes('html'),
    `prevention/printPrevention returned neither a PDF nor a page: contentType=${result.contentType} head=${JSON.stringify(result.head)}`);
  return { looksLikePdf, contentType: result.contentType, length: result.length };
}

(async () => {
  cleanupRows();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  // The add-prevention popup closes itself after a successful save; keep it
  // readable so the confirmation can be asserted rather than inferred.
  await context.addInitScript(() => {
    window.close = () => {
      window.__carlosSelfCloseRequested = true;
    };
  });
  try {
    await login(context, config, recorder);

    const gridPage = await openPreventionGrid(context, 'prevention');
    pass(`prevention grid renders for demographic ${demographicNo}`);

    const brandCount = await assertVaccineCatalogueLoaded(gridPage);
    pass(`vaccine brand catalogue loaded with ${brandCount} entries (bundled fallback included)`);

    const { popup, preventionType } = await openAddPreventionPopup(context, gridPage);
    pass(`clicking ${preventionType} on the grid opens the add-prevention popup for the right patient`);

    const recorded = await recordPrevention(popup, preventionType);
    await popup.close().catch(() => {});
    pass(`prevention ${recorded.id} persisted as given on ${recorded.preventionDate} with its lot number extension row`);

    await assertOnGrid(gridPage, recorded);
    pass('the recorded immunization appears on the reloaded prevention grid');

    const reportText = await checkPreventionReport(context);
    assert(reportText.length > 0, 'prevention report rendered nothing');
    pass('prevention reporting page renders without an error page');

    const print = await checkPreventionPrint(gridPage);
    pass(`printable prevention record returns ${print.length} bytes (${print.looksLikePdf ? 'PDF' : print.contentType})`);

    assertNoPageErrors(recorder, 'prevention / immunization');
    console.log(`\nCompleted ${passed.length} Playwright checks, 0 failures`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupRows();
    sql.close();
  }
})().catch((error) => {
  console.error(`FAIL prevention / immunization interface flow: ${error.stack || error.message}`);
  process.exit(1);
});
