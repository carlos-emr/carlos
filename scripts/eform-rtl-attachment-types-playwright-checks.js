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
 * Local-only browser regression check for Rich Text Letter attachment type coverage.
 */

const { chromium } = require('playwright');
const {
  assert,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
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

    const addPage = await openAddEform(context, config, recorder, fid, config.demographicNo, 'rtl-types-add');
    await saveCurrentEform(addPage, `RTL types ${Date.now()}`);

    const popup = await openAttachPopup(addPage, context);
    await waitForPopupReady(popup, recorder, 'rtl-types-popup');

    const popupState = await popup.evaluate(() => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code executed without interpolating user-controlled input
      const normalizedText = document.body.innerText.replace(/\s+/g, ' ').trim();
      const sections = Array.from(document.querySelectorAll('.section')).map((section) => {
        const heading = (section.querySelector('h4')?.innerText || '').replace(/\s+/g, ' ').trim();
        const itemCount = section.querySelectorAll('input[type="checkbox"]').length;
        const sectionText = (section.innerText || '').replace(/\s+/g, ' ').trim();
        return { heading, itemCount, sectionText };
      });
      const headingMatches = {
        documents: /Documents/i.test(normalizedText),
        labs: /Labs/i.test(normalizedText),
        hrm: /HRM/i.test(normalizedText),
        eforms: /eForms?|EForms?/i.test(normalizedText),
        forms: /Current Only|Forms/i.test(normalizedText),
      };
      return {
        headingMatches,
        sections,
        checkboxNames: Array.from(document.querySelectorAll('input[type="checkbox"]')).map((input) => input.name),
        bodyText: normalizedText,
      };
    });

    assert(popupState.headingMatches.documents, 'RTL attachment popup did not expose a Documents section');
    assert(popupState.headingMatches.labs, 'RTL attachment popup did not expose a Labs section');
    assert(popupState.headingMatches.hrm, 'RTL attachment popup did not expose an HRM section');
    assert(popupState.headingMatches.eforms, 'RTL attachment popup did not expose an eForms section');
    assert(popupState.headingMatches.forms, 'RTL attachment popup did not expose an encounter Forms section');

    const names = new Set(popupState.checkboxNames.filter(Boolean));
    const sectionInfo = {
      documents: popupState.sections.find((section) => /Documents/i.test(section.heading)),
      labs: popupState.sections.find((section) => /Labs/i.test(section.heading)),
      hrm: popupState.sections.find((section) => /HRM/i.test(section.heading)),
      eforms: popupState.sections.find((section) => /eForms?|EForms?/i.test(section.heading)),
      forms: popupState.sections.find((section) => /Current Only|Forms/i.test(section.heading)),
    };
    const expectFieldWhenPresent = (section, fieldName, label) => {
      assert(section, `RTL attachment popup did not expose a ${label} section`);
      if (section.itemCount === 0) {
        return;
      }
      assert(names.has(fieldName), `RTL attachment popup did not expose ${label} attachment field names: ${popupState.checkboxNames.join(', ')}`);
    };
    expectFieldWhenPresent(sectionInfo.documents, names.has('docNo') ? 'docNo' : 'attachedDocs', 'document');
    expectFieldWhenPresent(sectionInfo.labs, 'labNo', 'lab');
    expectFieldWhenPresent(sectionInfo.hrm, 'hrmNo', 'HRM');
    expectFieldWhenPresent(sectionInfo.eforms, 'eFormNo', 'eForm');
    expectFieldWhenPresent(sectionInfo.forms, 'formNo', 'encounter form');

    await screenshot(popup, config.screenshotDir, 'rtl-attachment-types-popup');
    await popup.close();
    await addPage.close();
    await context.close();

    console.log('PASS rtl attachment popup exposes all expected attachment families');
  } catch (error) {
    console.error('FAIL rtl attachment types Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
