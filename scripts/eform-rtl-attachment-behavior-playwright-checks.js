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
 * Local-only browser regression check for Rich Text Letter attachment behavior.
 */

const { chromium } = require('playwright');
const {
  assert,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
  invokeFetchAttached,
  login,
  openAddEform,
  openAttachPopup,
  openManager,
  saveCurrentEform,
  screenshot,
  validateBaseUrl,
  waitForPopupReady,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  demographicNo: process.env.RTL_DEMOGRAPHIC_NO || '1',
  screenshotDir: process.env.RTL_SCREENSHOT_DIR || '/tmp',
  formName: process.env.RTL_FORM_NAME || 'Rich Text Letter',
};

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    const managerPage = await openManager(context, config, recorder, 'rtl-manager');
    const { fid } = await findLibraryEform(managerPage, config.formName);
    await managerPage.close();

    const addPage = await openAddEform(context, config, recorder, fid, config.demographicNo, 'rtl-behavior-add');
    const fdid = await saveCurrentEform(addPage, `RTL behavior ${Date.now()}`);

    const popup = await openAttachPopup(addPage, context);
    await waitForPopupReady(popup, recorder, 'rtl-behavior-popup');
    const popupUrl = new URL(popup.url());
    const requestId = popupUrl.searchParams.get('requestId');
    assert(requestId === fdid, `Attach popup requestId should match saved fdid ${fdid}, got ${requestId || '(empty)'}`);

    await screenshot(popup, config.screenshotDir, 'rtl-attachment-behavior-popup-before-submit');

    const firstDoc = popup.locator('input[name="docNo"]').first();
    await firstDoc.waitFor({ state: 'attached', timeout: 15000 });
    const selectedDocValue = await firstDoc.getAttribute('value');
    assert(selectedDocValue, 'RTL attachment popup did not expose a document checkbox value');
    await firstDoc.check();

    await Promise.all([
      popup.waitForLoadState('domcontentloaded').catch(() => {}),
      popup.locator('input[type="submit"][value="Attach Selected"]').click(),
    ]);
    await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const popupBodyText = await popup.locator('body').innerText({ timeout: 10000 }).catch(() => '');
    assert(!/HTTP Status 500|Exception Report|CARLOS has encountered an unexpected error/i.test(popupBodyText), `Attachment submit crashed the popup: ${popupBodyText}`);
    assert(/ok/i.test(popupBodyText), `Attachment submit did not complete cleanly: ${popupBodyText}`);
    await screenshot(popup, config.screenshotDir, 'rtl-attachment-behavior-popup-after-submit');
    await popup.close();

    const reopenedPopup = await openAttachPopup(addPage, context);
    await waitForPopupReady(reopenedPopup, recorder, 'rtl-behavior-popup-reopen');
    const reopenedDoc = reopenedPopup.locator(`input[name="docNo"][value="${selectedDocValue}"]`);
    await reopenedDoc.waitFor({ state: 'attached', timeout: 15000 });
    assert(await reopenedDoc.isChecked(), `Reopened attach popup should keep document ${selectedDocValue} checked`);
    await screenshot(reopenedPopup, config.screenshotDir, 'rtl-attachment-behavior-popup-reopen');
    await reopenedPopup.close();

    const fetchResult = await invokeFetchAttached(addPage);
    assert(fetchResult.hasFunction, 'Rich Text Letter page did not expose fetchAttached() after popup submit');
    assert(!fetchResult.error, `fetchAttached() threw after popup submit: ${fetchResult.error}`);
    assert(!/Error loading attachments|HTTP Status 500/i.test(fetchResult.text), `Attachment sidebar rendered an error after empty submit: ${fetchResult.text}`);
    await screenshot(addPage, config.screenshotDir, 'rtl-attachment-behavior-main');
    await addPage.close();
    await context.close();

    console.log(`PASS rtl attachment submit stays stable and reopens with selected documents pre-checked (fdid ${fdid}, doc ${selectedDocValue})`);
  } catch (error) {
    console.error('FAIL rtl attachment behavior Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
