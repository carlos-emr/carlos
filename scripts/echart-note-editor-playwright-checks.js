#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the eChart note editor: can a clinician click
 * into a chart note and type, with a clean console, and does the draft autosave
 * that typing triggers reach the server with its CSRF token?
 *
 * WHY THIS CHECK EXISTS (issue #3665, findings 7 and 10). The note editor's
 * click handler, getActiveText() in js/newCaseManagementView.js.jsp, used to
 * write to a `keyword` element the encounter layout no longer renders, so
 * every click into a note threw a TypeError (alpha-11 observation 15). The
 * whole suite had to baseline that error to run at all -- and while the
 * baseline stood, no check could type into a note and assert a clean console,
 * which is the single most-used screen in the product. The handler now returns
 * when the element is absent, and this check proves it with NO baseline.
 *
 * The autosave is the second half. newCaseManagementView.js.jsp and the
 * encounter layout that hosts it carry no populated CSRF-TOKEN input, and a
 * static audit concluded their POSTs must therefore be rejected (finding 10).
 * They are not: the page POSTs through CarlosAjax, which uses XMLHttpRequest
 * precisely so CSRFGuard's script injects the CSRF-TOKEN header into every
 * send. The draft autosave is the natural POST to observe, so this check waits
 * for it, reads the request headers the browser actually sent, and requires
 * the token header, a 200, and the draft row in casemgmt_tmpsave.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, Search, the patient's Master
 * Record, E-Chart, then click into the note and type.
 *
 * WHAT IT WRITES, AND REMOVES. Typing makes the page autosave a draft row
 * (casemgmt_tmpsave) five seconds later, and opening the chart takes a note
 * lock (casemgmt_note_lock) that closing the popup from a script does not
 * always release. Both are deleted afterwards through MYSQL_*; nothing is
 * signed or saved, so no casemgmt_note row is ever written.
 *
 * Defaults are for the local devcontainer:
 *   MYSQL_PASSWORD=... npm run test:echart-note-editor-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   NOTE_EDITOR_SEARCH=FAKE-           surname prefix used to reach a patient
 *   NOTE_EDITOR_DEMOGRAPHIC_NO=2       which patient's chart to open
 *   NOTE_EDITOR_TIMEOUT_MS=20000       per-step allowance
 *
 * IMPLEMENTS: docs/ui-tests/app-findings-log.md findings 7 and 10 (the live half).
 */

const {
  assert, assertStrictPage, createRecorder, createSqlRunner, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

const NOTE_TEXTAREA = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const AUTOSAVE_URL = /\/CaseManagementEntry(\?|$)/;
/** backup() fires 5s after the last timer tick when the note changed; allow for a slow page. */
const AUTOSAVE_WAIT_MS = 30000;

/**
 * Open the chart from the Master Record with NO console baseline. The chart is
 * the page under test here, so the suite-wide baseline that used to tolerate
 * getActiveText must not apply to it.
 */
async function openChartWithoutBaseline(context, masterPage, recorder, timeout) {
  const chartLink = masterPage.locator('a').filter({ hasText: /^\s*E-?Chart\s*$/i }).first();
  assert(await chartLink.count() > 0,
    'The Master Record offers no E-Chart link, so a clinician cannot open the chart from the patient record');
  const { page } = await clickOpensPopupOrNavigates(masterPage, chartLink, {
    context, label: 'echart', recorder, timeout, baseline: [],
  });
  return page;
}

/** Is this the draft autosave the note editor POSTs after typing? */
function isAutosaveRequest(request) {
  return request.method() === 'POST' && AUTOSAVE_URL.test(request.url())
    && /(^|&)method=autosave(&|$)/.test(request.postData() || '');
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const searchTerm = process.env.NOTE_EDITOR_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.NOTE_EDITOR_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.NOTE_EDITOR_TIMEOUT_MS || '20000');
  const stamp = `PW_NOTE_EDITOR_${Date.now()}`;

  const sql = createSqlRunner(config.mysql);
  const recorder = createRecorder();
  let demographicNo = '';
  let browser;
  try {
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const chartPage = await openChartWithoutBaseline(context, masterPage, recorder, timeout);
    demographicNo = new URL(chartPage.url()).searchParams.get('demographicNo') || '';
    assert(/^\d+$/.test(demographicNo), `the chart URL names no demographicNo (${chartPage.url()}), so the draft it writes could not be removed`);

    const note = chartPage.locator(NOTE_TEXTAREA).first();
    await note.waitFor({ state: 'visible', timeout });
    // The click is the point: getActiveText() is a click handler on the note.
    await note.click({ timeout });
    const autosave = chartPage.waitForRequest(isAutosaveRequest, { timeout: AUTOSAVE_WAIT_MS });
    await note.pressSequentially(`${stamp} typed by the note-editor check`, { delay: 15 });

    const request = await autosave;
    const headers = await request.allHeaders();
    const response = await request.response();
    assert(headers['csrf-token'] && headers['csrf-token'].trim(),
      'the draft autosave POST carried no CSRF-TOKEN header; CSRFGuard\'s XHR interceptor is not injecting it on the chart');
    assert(/^XMLHttpRequest$/i.test(headers['x-requested-with'] || ''),
      'the draft autosave was not sent as an XMLHttpRequest, so CSRFGuard would not validate it by header');
    assert(response && response.status() === 200,
      `the draft autosave answered HTTP ${response ? response.status() : '(no response)'} instead of 200`);
    // The draft holds the whole textarea, and a fresh note opens with a header
    // line ("[date .: reason]") already in it, so the stamp is inside the text,
    // not at its start.
    const drafts = Number(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE note LIKE '%${stamp}%'`) || '0');
    assert(drafts >= 1, 'the autosave answered 200 but wrote no casemgmt_tmpsave row, so the POST did not reach the action');

    assertStrictPage(recorder, ['echart']);
    console.log(`  typed into the note for patient ${demographicNo}: console clean, autosave POST carried the CSRF-TOKEN header and was stored`);
    await chartPage.close().catch(() => {});
    return { demographicNo, drafts };
  } finally {
    if (browser) await browser.close().catch(() => {});
    // Every step is attempted; a fixture left behind fails the check even when
    // every assertion passed.
    for (const statement of [
      `DELETE FROM casemgmt_tmpsave WHERE note LIKE '%${stamp}%'`,
      demographicNo ? `DELETE FROM casemgmt_note_lock WHERE demographic_no = ${demographicNo}` : null,
    ].filter(Boolean)) {
      try {
        sql.execute(statement);
      } catch (cleanupError) {
        console.error(`FAIL cleanup failed: ${statement}: ${cleanupError.message}`);
        process.exitCode = 1;
      }
    }
    sql.dispose();
  }
}

if (require.main === module) {
  runCheck({ name: 'echart-note-editor', run: main });
}

module.exports = { AUTOSAVE_URL, NOTE_TEXTAREA, isAutosaveRequest, main, openChartWithoutBaseline };
