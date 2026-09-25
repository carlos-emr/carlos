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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the prescription preview's pharmacyId guard
 * (rx/Preview2.jsp), driven the way a user reprints: login, the patient's Rx
 * module, the Reprint panel, the seeded script's row, the rendered preview.
 *
 * ViewScript2.jsp builds the preview iframe URL with
 * pharmacyId=<noNull(request param)>, so a reprint for a patient without a
 * preferred pharmacy hands Preview2.jsp an EMPTY pharmacyId — which used to
 * fall through to Integer.parseInt("") and 500 the whole preview. Clients
 * can also echo the literal string "null". Every seeded prescription patient
 * carries demographicPharmacy links, so this check stages the no-pharmacy
 * state itself (unlinking the patient's pharmacies and restoring them in a
 * finally), walks the real reprint journey — the Reprint panel embeds
 * viewScript as an iframe, whose own #preview iframe is the Preview2 render —
 * and then re-renders the preview with pharmacyId=null literal. Both must
 * render and the whole flow must stay free of 5xx responses.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (to stage and restore the pharmacy links)
 * Optional: PRESCRIPTION_SCRIPT_ID (default 45; must have drugs rows),
 *   PRESCRIPTION_DEMOGRAPHIC_NO (default 1), CHROME_PATH,
 *   RX_PREVIEW_SCREENSHOT_DIR (default /tmp).
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { cleanupOwnedWorkflow } = require('./lib/workflow-session');
const { openRx, stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');
const {
  assert,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.RX_PREVIEW_SCREENSHOT_DIR || '/tmp',
};
const scriptId = process.env.PRESCRIPTION_SCRIPT_ID || '45';
const demographicNo = process.env.PRESCRIPTION_DEMOGRAPHIC_NO || '1';
assert(/^\d+$/.test(scriptId), `PRESCRIPTION_SCRIPT_ID must be numeric, got ${scriptId}`);
assert(/^\d+$/.test(demographicNo), `PRESCRIPTION_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);

const mysqlHost = h.validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

let mysqlDefaults = null;
function initMysqlDefaults() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rx-preview-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  mysqlDefaults = { dir, file };
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}

// The pharmacy unlink is staged through the table's own soft-delete model
// (DemographicPharmacy.ACTIVE='1' / INACTIVE='0') rather than DELETE+re-
// INSERT, and only the ids captured here are flipped back, so a crashed run
// can be repaired by re-running the script or restoring exactly those ids.
function stageNoPharmacy() {
  const linkIds = sql(`SELECT GROUP_CONCAT(id) FROM demographicPharmacy WHERE demographic_no=${demographicNo} AND status='1'`);
  if (linkIds) {
    sql(`UPDATE demographicPharmacy SET status='0' WHERE id IN (${linkIds})`);
  }
  return linkIds;
}
function restorePharmacy(linkIds) {
  if (linkIds) {
    sql(`UPDATE demographicPharmacy SET status='1' WHERE id IN (${linkIds})`);
  }
}

async function assertPreviewRenders(hostFrame, label) {
  const previewHandle = await hostFrame.locator('#preview').elementHandle({ timeout: 30000 });
  const frame = await previewHandle.contentFrame();
  assert(frame, `${label}: preview iframe did not expose a frame`);
  // Preview2.jsp renders #signature at the foot of a successful preview; an
  // error page or the pre-fix parseInt("") 500 never reaches it.
  try {
    await frame.locator('#signature').waitFor({ state: 'attached', timeout: 30000 });
  } catch (error) {
    const bodyText = await frame.locator('body').innerText().catch(() => '');
    throw new Error(`${label}: preview did not render (#signature missing) at ${frame.url()}: ${bodyText.replace(/\s+/g, ' ').slice(0, 400)}`);
  }
  return frame.url();
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  initMysqlDefaults();
  let stagedLinkIds = null;
  let foreignPatient = null;
  const foreignMarker = `FAKE-PW${randomBytes(8).toString('hex')}`;
  try {
    stagedLinkIds = stageNoPharmacy();

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const schedulePage = await login(context, config, recorder);
    await schedulePage.close();

    // Own a complete foreign-patient prescription, so an ownership refusal cannot pass merely
    // because the requested script has no drugs. Create its medication through the prescribing UI.
    const provider = sql(`SELECT provider_no FROM security WHERE user_name=${h.sqlString(config.testUser)}`);
    assert(provider, 'the test login has no provider');
    foreignPatient = sql(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(foreignMarker)},'Preview','1980','01','02','F','AC',
        ${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(foreignPatient), 'the foreign patient fixture was not created');
    const foreignPage = await openRx({ context, config }, foreignPatient);
    wirePage(foreignPage, 'rx-foreign-fixture', recorder);
    await stageCustomDrug(foreignPage, foreignMarker);
    const [foreignSave] = await Promise.all([
      foreignPage.waitForResponse(response => response.request().method() === 'POST'
        && /\/rx\/WriteScript\?[^#]*parameterValue=updateSaveAllDrugs/.test(response.url())),
      foreignPage.locator('#saveOnlyButton').click(),
    ]);
    assert(foreignSave.ok(), 'the foreign prescription fixture was not saved');
    const foreignScript = sql(`SELECT p.script_no FROM prescription p JOIN drugs d ON d.script_no=p.script_no
      WHERE p.demographic_no=${foreignPatient} AND d.demographic_no=${foreignPatient}
        AND d.customName=${h.sqlString(foreignMarker)}`);
    assert(/^[1-9]\d*$/.test(foreignScript), 'the foreign fixture has no saved prescription and drug');
    const ownPreview = await context.request.get(
      `${config.baseUrl}/rx/ViewPreview2?demographicNo=${foreignPatient}&scriptId=${foreignScript}`);
    assert(ownPreview.ok() && (await ownPreview.text()).includes(foreignMarker),
      'the foreign fixture must render for its actual patient before testing its ownership gate');
    await foreignPage.close();

    // User path: the patient's Rx module, the Reprint panel, the script row.
    const rxPage = await context.newPage();
    wirePage(rxPage, 'rx-module', recorder);
    await gotoApp(rxPage, config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
    await rxPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(rxPage, 'rx module');

    await rxPage.locator('a').filter({ hasText: /^Reprint$/ }).first().click();
    const reprintRow = rxPage.locator(`#reprint a[onclick*="reprint2('${scriptId}')"]`).first();
    await reprintRow.waitFor({ state: 'visible', timeout: 15000 });
    await reprintRow.click();

    // reprint2() embeds viewScript as an iframe in the Rx page rather than
    // navigating; find that frame, then its nested #preview (Preview2) frame.
    let viewScriptFrame = null;
    for (let attempt = 0; attempt < 30 && !viewScriptFrame; attempt += 1) {
      viewScriptFrame = rxPage.frames().find((f) => f.url().includes('/rx/viewScript'));
      if (!viewScriptFrame) {
        await rxPage.waitForTimeout(1000);
      }
    }
    assert(viewScriptFrame, 'reprint did not embed a /rx/viewScript frame');
    const viewScriptParams = new URL(viewScriptFrame.url()).searchParams;
    assert(viewScriptParams.get('scriptId') === scriptId,
      `embedded viewScript is for script ${viewScriptParams.get('scriptId')}, expected ${scriptId}`);
    // The staging above is what makes this the regression branch: with no
    // active pharmacy link the UI builds pharmacyId= EMPTY (or omits it).
    const uiPharmacyId = viewScriptParams.get('pharmacyId');
    assert(uiPharmacyId === null || uiPharmacyId === '',
      `staging failed — the reprint UI still carried a pharmacy (pharmacyId=${JSON.stringify(uiPharmacyId)})`);

    const emptyFrameUrl = await assertPreviewRenders(viewScriptFrame, 'reprint preview (no pharmacy)');
    const emptyParam = new URL(emptyFrameUrl).searchParams.get('pharmacyId');
    assert(emptyParam === null || emptyParam === '',
      `Preview2 unexpectedly received a pharmacy (pharmacyId=${JSON.stringify(emptyParam)} at ${emptyFrameUrl})`);
    await screenshot(rxPage, config.screenshotDir, 'rx-preview-pharmacy-empty');

    // Branch 2: the literal string "null" clients can echo back, rendered as
    // a top-level viewScript page like the prescription-signature check does.
    const nullPage = await context.newPage();
    wirePage(nullPage, 'rx-null-literal', recorder);
    await gotoApp(nullPage, config.baseUrl, `/rx/viewScript?scriptId=${scriptId}&pharmacyId=null`);
    await nullPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(nullPage, 'reprint view (pharmacyId=null literal)');
    await assertPreviewRenders(nullPage.mainFrame(), 'reprint preview (pharmacyId=null literal)');
    await screenshot(nullPage, config.screenshotDir, 'rx-preview-pharmacy-null-literal');

    // Explicit saved-script previews must refuse invalid/foreign identities even while a
    // valid same-patient reprint is open; neither may silently render the current workspace.
    for (const [candidate, expectedStatus] of [['bad', 400], ['', 400], [foreignScript, 404]]) {
      const response = await context.request.get(
        `${config.baseUrl}/rx/ViewPreview2?demographicNo=${demographicNo}&scriptId=${encodeURIComponent(candidate)}`);
      assert(response.status() === expectedStatus, 'explicit invalid or foreign prescription preview was not refused');
    }

    // Replacing this patient's reprint workspace must not retarget an already-open preview.
    const originalPreview = nullPage.frames().find((frame) => frame.url().includes('/rx/ViewPreview2'));
    const originalText = await originalPreview.locator('input[name="rx_no_newlines"]').inputValue();
    const originalUrl = originalPreview.url();
    const alternateCandidates = sql(`SELECT p.script_no FROM prescription p JOIN drugs d ON d.script_no=p.script_no WHERE p.demographic_no=${demographicNo} AND d.demographic_no=${demographicNo} AND p.script_no<>${scriptId} GROUP BY p.script_no HAVING SHA2(GROUP_CONCAT(COALESCE(d.special,'') ORDER BY d.drugid),256) <> (SELECT SHA2(GROUP_CONCAT(COALESCE(special,'') ORDER BY drugid),256) FROM drugs WHERE script_no=${scriptId} AND demographic_no=${demographicNo}) ORDER BY p.script_no`).split(/\r?\n/).filter((id) => /^\d+$/.test(id));
    const reprintLinks = await rxPage.locator('#reprint a[onclick*="reprint2("]').evaluateAll((links) =>
      links.map((link) => (link.getAttribute('onclick').match(/reprint2\('(\d+)'\)/) || [])[1]));
    const alternateScript = alternateCandidates.find((id) => reprintLinks.includes(id));
    assert(alternateScript, 'preview identity coverage requires two visible prescriptions with different drug text');
    await rxPage.locator('#carlosModalCloseBtn').click();
    await rxPage.locator('#carlosModal').waitFor({ state: 'hidden' });
    await rxPage.locator(`#reprint a[onclick*="reprint2('${alternateScript}')"]`).first().click();
    let alternateHost = null;
    for (let attempt = 0; attempt < 30 && !alternateHost; attempt += 1) {
      alternateHost = rxPage.frames().find((frame) => frame.url().includes('/rx/viewScript')
        && new URL(frame.url()).searchParams.get('scriptId') === alternateScript);
      if (!alternateHost) await rxPage.waitForTimeout(1000);
    }
    assert(alternateHost, 'alternate reprint did not load');
    await assertPreviewRenders(alternateHost, 'alternate reprint');
    const alternatePreview = alternateHost.childFrames().find((frame) => frame.url().includes('/rx/ViewPreview2'));
    assert(await alternatePreview.locator('input[name="rx_no_newlines"]').inputValue() !== originalText,
      'preview identity coverage requires saved prescriptions with different drug text');
    const pinnedPage = await context.newPage();
    wirePage(pinnedPage, 'rx-pinned-preview', recorder);
    await pinnedPage.goto(originalUrl, { waitUntil: 'networkidle' });
    assert(await pinnedPage.locator('input[name="rx_no_newlines"]').inputValue() === originalText,
      'an older preview changed drugs after a newer same-patient reprint');

    const fatal500s = recorder.badResponses.filter((r) => r.status >= 500);
    assert(fatal500s.length === 0, `preview flow produced 5xx responses: ${JSON.stringify(fatal500s)}`);

    await context.close();
    console.log(`PASS rx preview pharmacy handling, patient ownership, and saved-script identity for script ${scriptId}`);
  } catch (error) {
    console.error('FAIL rx preview pharmacyId Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    try {
      restorePharmacy(stagedLinkIds);
    } catch (restoreError) {
      console.error(`WARN failed to restore demographicPharmacy links (${stagedLinkIds}): ${restoreError.message}`);
    }
    try {
      await cleanupOwnedWorkflow({
        browser,
        sql: { value: sql, execute: sql, dispose() {} }, patient: foreignPatient, marker: foreignMarker,
        cleanups: foreignPatient ? [() => sql(`DELETE FROM drugs WHERE demographic_no=${foreignPatient};
          DELETE FROM prescription WHERE demographic_no=${foreignPatient}`)] : [],
      });
    } catch (cleanupError) {
      console.error(`FAIL owned preview fixture cleanup: ${cleanupError.message}`);
      process.exitCode = 1;
    }
    cleanupMysqlDefaults();
  }
})();
