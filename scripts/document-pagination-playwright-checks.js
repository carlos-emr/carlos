#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Read-only: Search -> Master Record -> E-Chart -> an existing document.
 * Requires linked single-page and >=3-page demo PDFs in image display mode.
 * DOCUMENT_PAGINATION_DEMOGRAPHIC_NO defaults to demo patient 1.
 * Missing fixtures/configuration report SKIP, never PASS. No clinical rows are changed.
 */
const {
  SkipCheck, assert, assertStrictPage, createRecorder, createSqlRunner,
  launchBrowser, login, newContext, readConfig, runCheck, wireStrictPage,
} = require('./lib/playwright-harness');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

async function main() {
  const config = readConfig();
  const demographicNo = process.env.DOCUMENT_PAGINATION_DEMOGRAPHIC_NO || '1';
  assert(/^\d+$/.test(demographicNo), 'DOCUMENT_PAGINATION_DEMOGRAPHIC_NO must be numeric');
  const sql = createSqlRunner(config.mysql);
  let browser;
  try {
    const patient = sql.rows(`SELECT last_name, first_name FROM demographic WHERE demographic_no=${demographicNo}`)[0];
    if (!patient) throw new SkipCheck('configured demo patient is absent');
    const docs = sql.rows(`SELECT d.document_no, d.number_of_pages FROM document d
      JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${demographicNo}
        AND d.status='A' AND d.contenttype='application/pdf' ORDER BY d.document_no`);
    const recorder = createRecorder();
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    const schedule = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedule, recorder, {
      searchTerm: patient.join(','), preferredDemographicNo: demographicNo, timeout: 45000,
    });
    const chart = await openChart(context, masterPage, recorder, 45000);
    await waitForNavbars(chart, 45000);
    // The chart applies privacy, program/facility and ECONSULT visibility rules.
    // Choose fixtures from its rendered links, not merely the database rows.
    const visibleIds = new Set(await chart.locator('#docs a[onclick]').evaluateAll((links) =>
      links.map((link) => /segmentID=(\d+)'/.exec(link.getAttribute('onclick') || '')?.[1]).filter(Boolean)));
    const visibleDocs = docs.filter(([id]) => visibleIds.has(id));
    const multi = visibleDocs.find((row) => Number(row[1]) >= 3);
    const single = visibleDocs.find((row) => Number(row[1]) === 1);
    if (!multi || !single) throw new SkipCheck('need chart-visible single-page and >=3-page demo PDFs');
    for (const [id, count] of [multi, single]) {
      assert(/^\d+$/.test(id), 'document fixture ID must be numeric');
      // Use the actual patient document link; a duplicate date link opens the
      // same viewer, so deliberately choose just the first matching link.
      const target = chart.locator(`#docs a[onclick*="segmentID=${id}'"]`).first();
      assert(await target.count() === 1, `document ${id} is missing from its patient's chart`);
      const pending = context.waitForEvent('page', { timeout: 45000 });
      pending.catch(() => {});
      await target.click();
      const viewer = await pending;
      wireStrictPage(viewer, `document-${id}`, recorder);
      await viewer.waitForLoadState('domcontentloaded');
      const mode = await viewer.locator(`#displayDocumentAs_${id}`).inputValue();
      if (mode !== 'Image') throw new SkipCheck('document pagination requires image display mode');
      const total = Number(await viewer.locator(`#totalPage_${id}`).inputValue());
      assert(total === Number(count), 'viewer page count differs from the selected document');
      const image = viewer.locator(`#docImg_${id}`);
      async function checkPage(pageNo) {
        await viewer.waitForFunction(({ id: docId, pageNo: expected }) => {
          const img = document.getElementById(`docImg_${docId}`);
          return img && img.complete && img.naturalWidth > 100 && img.naturalHeight > 100
            && new URL(img.src).searchParams.get('curPage') === String(expected);
        }, { id, pageNo }, { timeout: 45000 });
        assert(await image.isVisible(), 'document page image is hidden');
        assert(await viewer.locator(`#curPage_${id}`).inputValue() === String(pageNo), 'hidden page state is stale');
        assert((await viewer.locator(`#viewedPage_${id}`).innerText()).trim() === String(pageNo), 'visible page number is stale');
      }
      await checkPage(1);
      if (total === 1) {
        assert(await viewer.locator('a[onclick*="Page("]').count() === 0, 'single-page document has pagination controls');
      } else {
        for (const action of ['first', 'prev', 'next', 'last']) {
          assert(await viewer.locator(`a[onclick^="${action}Page("]`).count() === 1,
            `${action} pagination control is duplicated or missing`);
        }
        for (const [action, pageNo] of [['next', 2], ['last', total], ['prev', total - 1], ['first', 1]]) {
          const responsePromise = viewer.waitForResponse((response) => {
            const url = new URL(response.url());
            return url.searchParams.get('method') === 'viewDocPage'
              && url.searchParams.get('doc_no') === id
              && url.searchParams.get('curPage') === String(pageNo);
          }, { timeout: 45000 });
          const [response] = await Promise.all([responsePromise, viewer.locator(`#${action}P_${id}`).click()]);
          assert(response.status() === 200 && /^image\//.test(response.headers()['content-type'] || ''),
            `${action} did not load a document image successfully`);
          await checkPage(pageNo);
          assert(await viewer.locator(`#prevP_${id}`).isVisible() === (pageNo > 1), 'previous boundary control is wrong');
          assert(await viewer.locator(`#nextP_${id}`).isVisible() === (pageNo < total), 'next boundary control is wrong');
        }
      }
      await viewer.close();
    }
    assertStrictPage(recorder);
    return { documents: 2, transitions: 4 };
  } finally {
    if (browser) await browser.close();
    sql.dispose();
  }
}
if (require.main === module) runCheck({ name: 'document-pagination', run: main });
module.exports = { main };
