#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Search -> Master Record -> E-Chart -> Urinalysis -> Print.
 * Requires pdftotext (poppler-utils) and a linked demo lab report.
 * LAB_PDF_DEMOGRAPHIC_NO defaults to 1. LAB_PDF_EXPECTED_NOTICE overrides the
 * shipped confidentiality_statement.v1 when the test deployment customizes it.
 * Read-only: examines the actual downloaded PDF without sending it to a printer.
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const {
  SkipCheck, assert, assertStrictPage, createRecorder, createSqlRunner,
  launchBrowser, login, newContext, readConfig, runCheck, wireStrictPage,
} = require('./lib/playwright-harness');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

async function main() {
  const config = readConfig();
  const demo = process.env.LAB_PDF_DEMOGRAPHIC_NO || '1';
  assert(/^\d+$/.test(demo), 'LAB_PDF_DEMOGRAPHIC_NO must be numeric');
  const properties = fs.readFileSync(path.join(__dirname, '../src/main/resources/carlos.properties'), 'utf8');
  const notice = (process.env.LAB_PDF_EXPECTED_NOTICE
    || properties.match(/^confidentiality_statement\.v1\s*=\s*(.*)$/m)?.[1] || '').trim().replace(/\s+/g, ' ');
  assert(notice.length > 0, 'configure the expected confidentiality notice');
  const sql = createSqlRunner(config.mysql);
  let browser;
  try {
    const patient = sql.rows(`SELECT last_name,first_name FROM demographic WHERE demographic_no=${demo}`)[0];
    if (!patient) throw new SkipCheck('configured demo patient is absent');
    const recorder = createRecorder();
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    await context.addInitScript(() => { window.close = () => {}; });
    const schedule = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedule, recorder, {
      searchTerm: patient.join(','), preferredDemographicNo: demo, timeout: 45000,
    });
    const chart = await openChart(context, masterPage, recorder, 45000);
    await waitForNavbars(chart, 45000);
    const link = chart.locator('#leftNavBar a,#rightNavBar a').filter({ hasText: /URINALYSIS/i }).first();
    if (!await link.count()) throw new SkipCheck('need a linked demo Urinalysis report');
    const [lab] = await Promise.all([context.waitForEvent('page', { timeout: 45000 }), link.click()]);
    wireStrictPage(lab, 'lab-print', recorder);
    await lab.waitForLoadState('domcontentloaded');
    const [download] = await Promise.all([
      lab.waitForEvent('download', { timeout: 45000 }),
      lab.locator('input[onclick^="printPDF"]').first().click(),
    ]);
    assert(await download.failure() === null, 'lab PDF download failed');
    const stream = await download.createReadStream();
    assert(stream, 'lab PDF download has no content');
    const chunks = [];
    for await (const chunk of stream) chunks.push(chunk);
    const bytes = Buffer.concat(chunks);
    assert(bytes.subarray(0, 5).toString() === '%PDF-', 'lab Print returned an error instead of PDF');
    const xml = execFileSync('pdftotext', ['-bbox', '-', '-'], {
      input: bytes, encoding: 'utf8', timeout: 30000, maxBuffer: 16 * 1024 * 1024,
    });
    const pages = await lab.evaluate((input) => {
      const doc = new DOMParser().parseFromString(input, 'application/xml');
      if (doc.querySelector('parsererror')) throw new Error('invalid PDF text-position XML');
      return Array.from(doc.getElementsByTagName('page'), (page) => ({
        width: Number(page.getAttribute('width')), height: Number(page.getAttribute('height')),
        words: Array.from(page.getElementsByTagName('word'), (word) => ({
          text: word.textContent,
          x1: Number(word.getAttribute('xMin')), x2: Number(word.getAttribute('xMax')),
          y1: Number(word.getAttribute('yMin')), y2: Number(word.getAttribute('yMax')),
        })),
      }));
    }, xml);
    assert(pages.length > 0, 'printed lab has no readable PDF pages');
    const noticeWords = notice.split(' ');
    for (let index = 0; index < pages.length; index++) {
      const page = pages[index];
      const start = page.words.findIndex((word, n) => word.text === noticeWords[0]
        && page.words.slice(n, n + noticeWords.length).map((w) => w.text).join(' ') === notice);
      assert(start >= 0, `page ${index + 1} omits or clips part of the confidentiality notice`);
      const footer = page.words.slice(start, start + noticeWords.length);
      const other = page.words.filter((_, n) => n < start || n >= start + noticeWords.length);
      for (const word of footer) {
        assert(word.x1 >= 17.9 && word.x2 <= page.width - 17.9 && word.y1 >= 17.9 && word.y2 <= page.height - 17.9,
          `page ${index + 1} puts legal text outside printable margins`);
        assert(!other.some((w) => Math.min(w.x2, word.x2) - Math.max(w.x1, word.x1) > 0.5
          && Math.min(w.y2, word.y2) - Math.max(w.y1, word.y1) > 0.5),
        `page ${index + 1} overlaps the legal notice with other printed content`);
      }
      assert(page.words.map((w) => w.text).join(' ').includes(`Page ${index + 1} of ${pages.length}`),
        `page ${index + 1} has incorrect page numbering`);
    }
    assertStrictPage(recorder);
    return { pages: pages.length, fullNoticeWithinMargins: true };
  } finally {
    try { if (browser) await browser.close(); }
    finally { sql.dispose(); }
  }
}
if (require.main === module) runCheck({ name: 'lab-pdf-footer', run: main });
module.exports = { main };
