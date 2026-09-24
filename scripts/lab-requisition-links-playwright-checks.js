#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Search -> Master Record -> E-Chart -> Urinalysis -> Msg and Req#.
 * LAB_LINK_DEMOGRAPHIC_NO defaults to demo patient 1. Requires both 2007/2010
 * requisitions and an initially unlinked report. It never replaces an existing
 * link; cleanup removes only this run's links to the selected demo requisitions.
 */
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner,
  launchBrowser, login, newContext, readConfig, runCheck, wireStrictPage,
} = require('./lib/playwright-harness');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { NAVBAR_ROW_CLICK } = require('./lib/playwright-ui');

async function main() {
  const config = readConfig();
  const demographicNo = process.env.LAB_LINK_DEMOGRAPHIC_NO || '1';
  assert(/^\d+$/.test(demographicNo), 'LAB_LINK_DEMOGRAPHIC_NO must be numeric');
  const sql = createSqlRunner(config.mysql);
  let browser, ownedLinkWhere;
  try {
    const tables = Number(sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('formLabReq07','formLabReq10')"));
    if (tables !== 2) throw new SkipCheck('requires Ontario 2007/2010 requisition tables');
    const patient = sql.rows(`SELECT last_name, first_name FROM demographic WHERE demographic_no=${demographicNo}`)[0];
    if (!patient) throw new SkipCheck('configured demo patient is absent');
    const requisitions = ['formLabReq07', 'formLabReq10'].map((table) => {
      const row = sql.rows(`SELECT ID, formCreated FROM ${table} WHERE demographic_no=${demographicNo} AND formCreated IS NOT NULL ORDER BY ID LIMIT 1`)[0];
      return row && { table, id: row[0], date: row[1] };
    });
    if (requisitions.some((row) => !row)) throw new SkipCheck('need dated 2007 and 2010 demo requisitions');
    requisitions.forEach((row) => assert(/^\d+$/.test(row.id), 'requisition fixture ID must be numeric'));
    const recorder = createRecorder();
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    // Keep link confirmation inspectable after it requests self.close().
    await context.addInitScript(() => { window.close = () => { window.__closeRequested = true; }; });
    const schedule = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedule, recorder, {
      searchTerm: patient.join(','), preferredDemographicNo: demographicNo, timeout: 45000,
    });
    const chart = await openChart(context, masterPage, recorder, 45000);
    await waitForNavbars(chart, 45000);
    async function popupFrom(page, control, label, clickOptions = {}) {
      const pending = context.waitForEvent('page', { timeout: 45000 });
      pending.catch(() => {});
      await control.click(clickOptions);
      const popup = await pending;
      wireStrictPage(popup, label, recorder);
      await popup.waitForLoadState('domcontentloaded');
      await assertNotErrorPage(popup, label);
      return popup;
    }
    const labLink = chart.locator('#leftNavBar a, #rightNavBar a').filter({ hasText: /URINALYSIS/i }).first();
    if (!await labLink.count()) throw new SkipCheck('need a linked demo Urinalysis report');
    const lab = await popupFrom(chart, labLink, 'lab', { position: NAVBAR_ROW_CLICK });
    const failures = [];
    try {
      const compose = await popupFrom(lab, lab.locator('input[value="Msg"]').first(), 'lab-message');
      await compose.locator('#subject').waitFor({ state: 'visible', timeout: 30000 });
      assert(await compose.locator('input[name="demographic_no"]').inputValue() === demographicNo,
        'lab Msg composer lost its patient context');
      await compose.close();
      console.log('PASS lab Msg opens a composer for the report patient');
    } catch (error) { failures.push(`Msg: ${error.message}`); }
    try {
      let request = await popupFrom(lab, lab.locator('input[title="Link to Requisition"]').first(), 'lab-requisition');
      const reportId = await request.locator('input[name="rptid"]').inputValue();
      const reportTable = await request.locator('input[name="table"]').inputValue();
      assert(/^\d+$/.test(reportId) && reportTable === 'hl7TextMessage', 'unexpected lab report identity');
      const reportWhere = `report_table='hl7TextMessage' AND report_id=${reportId}`;
      if (Number(sql.value(`SELECT COUNT(*) FROM labRequestReportLink WHERE ${reportWhere}`))) {
        throw new SkipCheck('the selected report already has a requisition link; refusing to replace it');
      }
      ownedLinkWhere = `${reportWhere} AND (${requisitions.map((r) => `(request_table='${r.table}' AND request_id=${r.id})`).join(' OR ')})`;
      for (const row of requisitions) {
        const value = `form:${row.table}:${row.id}`;
        const option = request.locator(`select[name="linkReqId"] option[value="${value}"]`);
        assert((await option.innerText()).includes(row.date), 'requisition date is missing or incorrect');
        await request.locator('select[name="linkReqId"]').selectOption(value);
        const [response] = await Promise.all([
          request.waitForResponse((r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/lab/ViewLinkReq')),
          lab.waitForEvent('load', { timeout: 30000 }),
          request.locator('input[type="submit"]').click(),
        ]);
        assert(response.status() === 200, `Link returned HTTP ${response.status()}`);
        await request.waitForFunction(() => window.__closeRequested === true, undefined, { timeout: 30000 });
        const saved = sql.rows(`SELECT request_table,request_id,DATE(request_date) FROM labRequestReportLink WHERE ${reportWhere}`);
        assert(saved.length === 1 && saved[0][0] === row.table && saved[0][1] === row.id && saved[0][2] === row.date,
          'link did not persist exactly the chosen requisition and date');
        await request.close();
        request = await popupFrom(lab, lab.locator('input[title="Link to Requisition"]').first(), 'lab-requisition');
        assert(await request.locator('select[name="linkReqId"]').inputValue() === value, 'reopened selector lost the saved link');
      }
      await request.locator('select[name="linkReqId"]').selectOption('-1');
      await Promise.all([
        request.waitForResponse((r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/lab/ViewLinkReq')),
        lab.waitForEvent('load', { timeout: 30000 }),
        request.locator('input[type="submit"]').click(),
      ]);
      await request.waitForFunction(() => window.__closeRequested === true, undefined, { timeout: 30000 });
      assert(sql.value(`SELECT COUNT(*) FROM labRequestReportLink WHERE ${reportWhere}`) === '0', 'unlink left a saved requisition link');
      await request.close();
      console.log('PASS lab Req# lists, links, changes, reloads and unlinks both requisition versions');
    } catch (error) {
      if (error instanceof SkipCheck && failures.length === 0) throw error;
      failures.push(`Req#: ${error.message}`);
    }
    assert(failures.length === 0, failures.join(' | '));
    // Opening a demo chart may offer to resume this user's existing draft.
    // The strict handler dismissed it, preserving the draft and its lock. This
    // read-only chart workflow must not take over or save someone else's work.
    recorder.unexpectedDialogs = recorder.unexpectedDialogs.filter((entry) =>
      !(entry.label === 'echart' && entry.type === 'confirm'
        && /^You have started to edit this note in another window at [^\n]+\.\nDo you wish to continue\?$/.test(entry.text)));
    assertStrictPage(recorder);
  } finally {
    try { if (browser) await browser.close(); }
    finally {
      try { if (ownedLinkWhere) sql.execute(`DELETE FROM labRequestReportLink WHERE ${ownedLinkWhere}`); }
      finally { sql.dispose(); }
    }
  }
}
if (require.main === module) runCheck({ name: 'lab-requisition-links', run: main });
module.exports = { main };
