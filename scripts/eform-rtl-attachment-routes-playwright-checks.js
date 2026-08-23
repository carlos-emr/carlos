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
 * Local-only browser regression check for Rich Text Letter attachment routing.
 */

const { chromium } = require('playwright');
const {
  assert,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
  getLatestRequest,
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

    const addPage = await openAddEform(context, config, recorder, fid, config.demographicNo, 'rtl-add');
    const fdid = await saveCurrentEform(addPage, `RTL routes ${Date.now()}`);
    assert(/^\d+$/.test(fdid), `Expected numeric fdid for saved RTL instance, got ${fdid}`);

    const popup = await openAttachPopup(addPage, context);
    await waitForPopupReady(popup, recorder, 'rtl-attach-popup');
    const popupUrl = new URL(popup.url());
    assert(popupUrl.pathname.endsWith('/eform/attachEform'), `Attach popup used unexpected route: ${popup.url()}`);
    assert(!popupUrl.pathname.endsWith('.jsp'), `Attach popup fell back to a legacy JSP route: ${popup.url()}`);
    await screenshot(popup, config.screenshotDir, 'rtl-attachment-routes-popup');
    await popup.close();

    const fetchResult = await invokeFetchAttached(addPage);
    assert(fetchResult.hasFunction, 'Rich Text Letter page did not expose fetchAttached()');
    assert(!fetchResult.error, `fetchAttached() threw on the Rich Text Letter page: ${fetchResult.error}`);
    const sidebarRequest = getLatestRequest(recorder, (entry) => entry.url.includes('/eform/displayAttachedFiles'));
    assert(sidebarRequest, 'No displayAttachedFiles request was captured after invoking fetchAttached()');
    const sidebarUrl = new URL(sidebarRequest.url);
    assert(sidebarUrl.pathname.endsWith('/eform/displayAttachedFiles'), `Attachment sidebar used unexpected route: ${sidebarRequest.url}`);
    assert(!sidebarUrl.pathname.endsWith('.jsp'), `Attachment sidebar fell back to a legacy JSP route: ${sidebarRequest.url}`);
    assert(!/Error loading attachments|HTTP Status 404|HTTP Status 500/i.test(fetchResult.text), `Attachment sidebar rendered an error state: ${fetchResult.text}`);
    await screenshot(addPage, config.screenshotDir, 'rtl-attachment-routes-main');
    await addPage.close();
    await context.close();

    console.log(`PASS rtl attachment routes use gated endpoints and save a real fdid (${fdid})`);
  } catch (error) {
    console.error('FAIL rtl attachment route Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
