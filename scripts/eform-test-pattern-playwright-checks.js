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
 * App-backed browser regression check for the all-in-one eForm test pattern.
 *
 * The script imports scripts/fixtures/eform/test-pattern.html into a running
 * local CARLOS app, uploads its image asset, opens the form through the add
 * route, verifies renderer-injected path and hidden-state markers, saves a
 * real eForm instance, reopens it directly and from the patient eForm list,
 * and downloads the rendered PDF.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:eform-test-pattern-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   EFORM_TEST_PATTERN_DEMOGRAPHIC_NO=1
 *   EFORM_TEST_PATTERN_SCREENSHOT_DIR=/tmp
 *   EFORM_TEST_PATTERN_HTML=/path/to/test-pattern.html
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const {
  assert,
  assertNotErrorPage,
  buildArtifactPath,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

function validateDestructiveTestBaseUrl(rawBaseUrl) {
  const baseUrl = validateBaseUrl(rawBaseUrl);
  const normalizedHost = baseUrl.hostname.toLowerCase().replace(/^\[(.*)]$/, '$1');
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const isLocalHost = localHosts.has(normalizedHost);
  if (!isLocalHost && baseUrl.protocol !== 'https:') {
    throw new Error(`Refusing non-local HTTP BASE_URL ${baseUrl.origin}; use HTTPS for non-local test targets`);
  }
  if (!isLocalHost && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing destructive eForm test against non-local BASE_URL host ${baseUrl.hostname}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  return baseUrl;
}

const config = {
  baseUrl: validateDestructiveTestBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  demographicNo: process.env.EFORM_TEST_PATTERN_DEMOGRAPHIC_NO || '1',
  screenshotDir: process.env.EFORM_TEST_PATTERN_SCREENSHOT_DIR || '/tmp',
  fixtureHtmlPath: process.env.EFORM_TEST_PATTERN_HTML || path.join(__dirname, 'fixtures/eform/test-pattern.html'),
};

const defaultBgImageName = 'playwright_test_pattern_bg.png';
const sourceMarkerValue = 'eform-test-pattern';
const testPatternPngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl5n2QAAAAASUVORK5CYII=';
const sensitiveQueryParamPattern = /([?&](?:efmdemographic_no|demographic_no|demographicNo|demoNo|demo_no|patient_id|patientId|fdid)=)[^&#\s"']*/gi;
const sensitiveDiagnosticFieldPattern = /(?:fdid|demographic|patient)/i;
let bgImageName = defaultBgImageName;

function validateConfig() {
  assert(/^\d+$/.test(config.demographicNo), `EFORM_TEST_PATTERN_DEMOGRAPHIC_NO must be numeric, got ${config.demographicNo}`);
  const resolvedFixture = path.resolve(config.fixtureHtmlPath); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- local developer-only fixture path; existence is checked before upload
  assert(fs.existsSync(resolvedFixture), `Test pattern fixture does not exist: ${resolvedFixture}`);
  config.fixtureHtmlPath = resolvedFixture;
}

function createRuntimeFixture(imageName) {
  assert(/^[A-Za-z0-9_.-]+$/.test(imageName), `Invalid generated image fixture name: ${imageName}`);
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-eform-test-pattern-'));
  const imagePath = path.join(tempDir, imageName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- imageName is generated from timestamp/process id and validated before joining under a private temp directory
  const htmlPath = path.join(tempDir, 'test-pattern.html');
  fs.writeFileSync(imagePath, Buffer.from(testPatternPngBase64, 'base64'));
  const fixtureHtml = fs.readFileSync(config.fixtureHtmlPath, 'utf8');
  fs.writeFileSync(htmlPath, fixtureHtml.split(defaultBgImageName).join(imageName));
  return { tempDir, imagePath, htmlPath };
}

async function ensureImageUploaded(context, recorder, imagePath, imageName) {
  const page = await context.newPage();
  wirePage(page, `test-pattern-image:${imageName}`, recorder);
  try {
    await gotoApp(page, config.baseUrl, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const imageLink = page.locator('#tblImage a.viewImage', { hasText: imageName }).first();
    if (await imageLink.count()) {
      return;
    }

    const frame = page.frameLocator('#uploadFrame');
    await frame.locator('#image').setInputFiles(imagePath);
    await frame.locator('input.upload[type="submit"]').click();
    await page.waitForURL(/administration\?show=ImageUpload/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, config.baseUrl, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await page.locator('#tblImage a.viewImage', { hasText: imageName }).first().waitFor({ state: 'visible', timeout: 15000 });
  } finally {
    await page.close();
  }
}

async function uploadEform(context, recorder, formName, formSubject) {
  const page = await context.newPage();
  wirePage(page, 'test-pattern-upload', recorder);
  try {
    await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const frame = page.frameLocator('#uploadFrame');
    await frame.locator('input[name="formName"]').fill(formName);
    await frame.locator('input[name="formSubject"]').fill(formSubject);
    await frame.locator('#formHtml').setInputFiles(config.fixtureHtmlPath);
    await frame.locator('input.upload[type="submit"]').click();

    await page.waitForURL(/administration\?show=Forms/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const { row, fid } = await findLibraryEform(page, formName);
    return { page, row, fid };
  } catch (error) {
    await page.close().catch(() => {});
    throw error;
  }
}

async function findImportedEformForCleanup(context, recorder, formName) {
  const page = await context.newPage();
  wirePage(page, 'test-pattern-template-cleanup-discovery', recorder);
  try {
    await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    const { fid } = await findLibraryEform(page, formName);
    return { page, fid };
  } catch (error) {
    await page.close().catch(() => {});
    return null;
  }
}

async function openManagerPreview(context, recorder, row) {
  const [popup] = await Promise.all([
    context.waitForEvent('page', { timeout: 30000 }),
    row.locator('td a').first().click(),
  ]);
  wirePage(popup, 'test-pattern-manager-preview', recorder);
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return popup;
}

async function openAddEform(context, recorder, fid) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.close = () => {
      window.__playwrightCloseIntercepted = true;
    };
  });
  wirePage(page, 'test-pattern-add', recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(config.demographicNo)}&source=${encodeURIComponent(sourceMarkerValue)}`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function openSavedEformDirect(context, recorder, fdid) {
  const page = await context.newPage();
  wirePage(page, 'test-pattern-saved-direct', recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmshowform_data?fdid=${encodeURIComponent(fdid)}`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function openSavedEformFromPatientList(context, recorder, fdid) {
  const page = await context.newPage();
  wirePage(page, 'test-pattern-patient-list', recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(config.demographicNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const link = page.locator(`#efmTable a[onclick*="fdid=${fdid}"]`).first();
  await link.waitFor({ state: 'visible', timeout: 15000 });
  const [popup] = await Promise.all([
    context.waitForEvent('page', { timeout: 30000 }),
    link.click(),
  ]);
  wirePage(popup, 'test-pattern-patient-list-popup', recorder);
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.close();
  return popup;
}

async function listSavedFdidsFromPatientList(context, recorder, searchText) {
  const page = await context.newPage();
  wirePage(page, 'test-pattern-patient-list-resolve', recorder);
  try {
    await gotoApp(page, config.baseUrl, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(config.demographicNo)}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const rows = page.locator('#efmTable tr', { hasText: searchText });
    const fdids = [];
    const rowCount = await rows.count();
    for (let index = 0; index < rowCount; index += 1) {
      const row = rows.nth(index);
      const link = row.locator('a[onclick*="fdid="]').first();
      const onclick = await link.getAttribute('onclick').catch(() => '');
      const href = await link.getAttribute('href').catch(() => '');
      const match = (onclick && onclick.match(/fdid=([^&'"]+)/)) || (href && href.match(/fdid=([^&'"]+)/));
      if (match && match[1]) {
        const fdid = decodeURIComponent(match[1]);
        if (/^\d+$/.test(fdid)) {
          fdids.push(fdid);
        }
      }
    }
    return Array.from(new Set(fdids));
  } finally {
    await page.close().catch(() => {});
  }
}

async function resolveSavedFdidFromPatientList(context, recorder, subject) {
  const fdids = await listSavedFdidsFromPatientList(context, recorder, subject);
  assert(fdids.length > 0, `Could not resolve saved fdid from patient eForm list row for ${subject}`);
  return fdids[0];
}

async function assertImageLoaded(page, selector, label) {
  const loaded = await page.locator(selector).evaluate((img) => ({
    complete: img.complete,
    width: img.naturalWidth,
    height: img.naturalHeight,
    src: img.currentSrc || img.src,
  }));
  assert(loaded.complete, `${label} did not finish loading`);
  assert(loaded.width > 0 && loaded.height > 0, `${label} failed to decode: ${loaded.src}`);
}

async function assertCanvasPainted(page) {
  const state = await page.locator('#test_pattern_canvas').evaluate((canvas) => {
    const context = canvas.getContext('2d');
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    let visiblePixels = 0;
    let paintedPixels = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      const alpha = pixels[i + 3];
      if (alpha === 0) {
        continue;
      }
      visiblePixels += 1;
      if (!(pixels[i] === 255 && pixels[i + 1] === 255 && pixels[i + 2] === 255)) {
        paintedPixels += 1;
      }
    }
    return { width: canvas.width, height: canvas.height, visiblePixels, paintedPixels };
  });
  assert(state.width === 240 && state.height === 100, `Unexpected canvas dimensions: ${formatDiagnostic(state)}`);
  assert(state.visiblePixels > 1000, `Canvas test pattern appears transparent: ${formatDiagnostic(state)}`);
  assert(state.paintedPixels > 1000, `Canvas test pattern appears blank: ${formatDiagnostic(state)}`);
}

async function captureState(page) {
  return page.evaluate(() => {
    const selectedRadio = document.querySelector('input[name="test_pattern_radio"]:checked');
    const textOf = (id) => {
      const element = document.getElementById(id);
      return element ? element.value : '';
    };
    return {
      fdid: textOf('fdid'),
      fid: textOf('fid'),
      context: textOf('context'),
      demographicNo: textOf('demographicNo'),
      newForm: textOf('newForm'),
      jsPath: textOf('test_pattern_javascript_path'),
      faxJsPath: textOf('test_pattern_javascript_fax_path'),
      jSignatureJsPath: textOf('test_pattern_javascript_jsignature_path'),
      imageScriptPath: textOf('test_pattern_image_script_path'),
      imageStylesheetPath: textOf('test_pattern_image_stylesheet_path'),
      legacyCalendarScript: !!document.querySelector('script[src*="share/calendar/calendar.js"]'),
      legacyCalendarStylesheet: !!document.querySelector('link[href*="share/calendar/calendar.css"]'),
      calendarLoaded: typeof window.Calendar !== 'undefined',
      hasBeforePrint: typeof document.body.onbeforeprint === 'function' || document.body.hasAttribute('onbeforeprint'),
      sourceMarker: textOf('test_pattern_source_marker'),
      fdidMarker: textOf('test_pattern_fdid_marker'),
      hiddenPersisted: textOf('test_pattern_hidden_persisted'),
      storeSignature: textOf('StoreSignature1'),
      signatureChoice: textOf('SignatureChoice'),
      text: textOf('test_pattern_text'),
      textPlaceholder: document.getElementById('test_pattern_text') ? document.getElementById('test_pattern_text').getAttribute('placeholder') : '',
      date: textOf('test_pattern_date'),
      textarea: textOf('test_pattern_textarea'),
      select: textOf('test_pattern_select'),
      datalist: textOf('test_pattern_datalist'),
      datalistOptionCount: document.querySelectorAll('#test_pattern_datalist_options option').length,
      radio: selectedRadio ? selectedRadio.value : '',
      checkboxYes: document.getElementById('test_pattern_checkbox_yes') ? document.getElementById('test_pattern_checkbox_yes').checked : false,
      checkboxNo: document.getElementById('test_pattern_checkbox_no') ? document.getElementById('test_pattern_checkbox_no').checked : false,
      legacyXbox: textOf('test_pattern_legacy_xbox'),
      onlyOneAlpha: document.getElementById('test_pattern_only_one_alpha') ? document.getElementById('test_pattern_only_one_alpha').checked : false,
      onlyOneBravo: document.getElementById('test_pattern_only_one_bravo') ? document.getElementById('test_pattern_only_one_bravo').checked : false,
      oscarOpenTarget: document.getElementById('test_pattern_oscaropen_button') ? document.getElementById('test_pattern_oscaropen_button').getAttribute('oscarOPEN') : '',
      buttonElementText: document.getElementById('test_pattern_button_element') ? document.getElementById('test_pattern_button_element').textContent.trim() : '',
      buttonResult: textOf('test_pattern_button_result'),
      passwordInputType: document.getElementById('test_pattern_password') ? document.getElementById('test_pattern_password').type : '',
      fileInputType: document.getElementById('test_pattern_file') ? document.getElementById('test_pattern_file').type : '',
      imageInputType: document.getElementById('test_pattern_image_input') ? document.getElementById('test_pattern_image_input').type : '',
      contentEditable: document.getElementById('test_pattern_contenteditable') ? document.getElementById('test_pattern_contenteditable').isContentEditable : false,
      iframePresent: !!document.getElementById('test_pattern_iframe'),
      objectData: document.getElementById('test_pattern_object') ? document.getElementById('test_pattern_object').getAttribute('data') : '',
      linkTarget: document.getElementById('test_pattern_link') ? document.getElementById('test_pattern_link').getAttribute('href') : '',
      listItemCount: document.querySelectorAll('#test_pattern_list li').length,
      subject: textOf('subject'),
      calcTotal: textOf('test_pattern_calc_total'),
      conditionalDisplay: document.getElementById('test_pattern_conditional_block') ? document.getElementById('test_pattern_conditional_block').style.display : '',
      conditionalClass: document.getElementById('test_pattern_conditional_block') ? document.getElementById('test_pattern_conditional_block').className : '',
      submitInputType: document.getElementById('SubmitButton') ? document.getElementById('SubmitButton').type : '',
      remoteSubjectVisible: !!document.getElementById('remote_eform_subject'),
      remoteSubmitVisible: !!document.getElementById('remoteSubmitButton'),
      remoteDownloadVisible: !!document.getElementById('remoteDownloadButton'),
      printOnlyDisplay: window.getComputedStyle(document.getElementById('test_pattern_print_only')).display,
      autoclose: textOf('isSuccess_Autoclose'),
      closeIntercepted: Boolean(window.__playwrightCloseIntercepted),
    };
  });
}

async function assertPrintMediaStyles(page) {
  await page.emulateMedia({ media: 'print' });
  try {
    const state = await page.evaluate(() => ({
      printOnlyDisplay: window.getComputedStyle(document.getElementById('test_pattern_print_only')).display,
      screenOnlyDisplay: window.getComputedStyle(document.getElementById('test_pattern_screen_only_details')).display,
    }));
    assert(state.printOnlyDisplay !== 'none', `Print-only content should be visible in print media: ${formatDiagnostic(state)}`);
    assert(state.screenOnlyDisplay === 'none', `DoNotPrint content should be hidden in print media: ${formatDiagnostic(state)}`);
  } finally {
    await page.emulateMedia({ media: 'screen' });
  }
}

async function assertRuntimeSurface(page, fid, options = {}) {
  await assertNotErrorPage(page, 'test pattern runtime');
  await page.locator('#FormName').waitFor({ state: 'attached', timeout: 15000 });
  await assertImageLoaded(page, '#BGImage1', 'test pattern uploaded background image');
  await assertImageLoaded(page, '#test_pattern_data_uri_image', 'test pattern data URI image');
  await assertImageLoaded(page, '#test_pattern_signature_image', 'test pattern signature image surface');
  await assertCanvasPainted(page);
  const state = await captureState(page);
  assert(state.fid === fid, `Runtime did not receive expected fid ${fid}: ${formatDiagnostic(state)}`);
  assert(state.context, `Runtime did not include context hidden input: ${formatDiagnostic(state)}`);
  assert(state.jsPath.includes('/library/eforms/printControl.js'), `oscar_javascript_path marker was not replaced: ${formatDiagnostic(state)}`);
  assert(state.faxJsPath.includes('eforms/faxControl.js'), `faxControl oscar_javascript_path marker was not replaced: ${formatDiagnostic(state)}`);
  assert(state.jSignatureJsPath.includes('jquery/jSignature.min.js'), `jSignature oscar_javascript_path marker was not replaced: ${formatDiagnostic(state)}`);
  assert(state.imageScriptPath.includes('jSignature.min.js'), `oscar_image_path script asset marker was not replaced: ${formatDiagnostic(state)}`);
  assert(state.imageStylesheetPath.includes('JSMPC.css'), `oscar_image_path stylesheet asset marker was not replaced: ${formatDiagnostic(state)}`);
  assert(!state.jsPath.includes('${oscar_javascript_path}'), `Raw oscar_javascript_path marker leaked: ${formatDiagnostic(state)}`);
  assert(!state.faxJsPath.includes('${oscar_javascript_path}'), `Raw oscar_javascript_path marker leaked in fax path: ${formatDiagnostic(state)}`);
  assert(!state.jSignatureJsPath.includes('${oscar_javascript_path}'), `Raw oscar_javascript_path marker leaked in jSignature path: ${formatDiagnostic(state)}`);
  assert(!state.imageScriptPath.includes('${oscar_image_path}'), `Raw oscar_image_path marker leaked in script asset path: ${formatDiagnostic(state)}`);
  assert(!state.imageStylesheetPath.includes('${oscar_image_path}'), `Raw oscar_image_path marker leaked in stylesheet asset path: ${formatDiagnostic(state)}`);
  assert(state.legacyCalendarScript, `Legacy calendar script tag was not present: ${formatDiagnostic(state)}`);
  assert(state.legacyCalendarStylesheet, `Legacy calendar stylesheet link was not present: ${formatDiagnostic(state)}`);
  assert(state.calendarLoaded, `Legacy calendar script did not expose Calendar global: ${formatDiagnostic(state)}`);
  assert(state.hasBeforePrint, `onbeforeprint handler was not present: ${formatDiagnostic(state)}`);
  assert(state.datalistOptionCount === 3, `Datalist options were not present in runtime DOM: ${formatDiagnostic(state)}`);
  assert(state.textPlaceholder === 'Free text', `Placeholder attribute was not rendered: ${formatDiagnostic(state)}`);
  assert(state.buttonElementText === 'HTML button', `HTML button element was not rendered: ${formatDiagnostic(state)}`);
  assert(state.passwordInputType === 'password', `Password input was not rendered: ${formatDiagnostic(state)}`);
  assert(state.fileInputType === 'file', `File input was not rendered: ${formatDiagnostic(state)}`);
  assert(state.imageInputType === 'image', `Image input was not rendered: ${formatDiagnostic(state)}`);
  assert(state.contentEditable, `contenteditable surface was not rendered editable: ${formatDiagnostic(state)}`);
  assert(state.iframePresent, `iframe surface was not rendered: ${formatDiagnostic(state)}`);
  assert(state.objectData.startsWith('data:text/plain'), `object surface was not rendered with data URI: ${formatDiagnostic(state)}`);
  assert(state.linkTarget === '#test_pattern_canvas', `Anchor link was not rendered: ${formatDiagnostic(state)}`);
  assert(state.listItemCount === 2, `List markup was not rendered: ${formatDiagnostic(state)}`);
  assert(state.conditionalClass.includes('Show_Hide'), `Show_Hide legacy class was not rendered: ${formatDiagnostic(state)}`);
  assert(state.submitInputType === 'submit', `Submit input was not rendered: ${formatDiagnostic(state)}`);
  assert(state.oscarOpenTarget === 'Pt Summary', `oscarOPEN attribute was not preserved: ${formatDiagnostic(state)}`);
  if (Object.prototype.hasOwnProperty.call(options, 'expectedSource')) {
    assert(state.sourceMarker === options.expectedSource, `source marker was not replaced with ${options.expectedSource}: ${formatDiagnostic(state)}`);
    assert(!state.sourceMarker.includes('${source}'), `Raw source marker leaked: ${formatDiagnostic(state)}`);
  }
  if (options.expectedFdid) {
    assert(state.fdidMarker === options.expectedFdid, `fdid marker was not replaced with ${options.expectedFdid}: ${formatDiagnostic(state)}`);
    assert(!state.fdidMarker.includes('${fdid}'), `Raw fdid marker leaked on saved eForm: ${formatDiagnostic(state)}`);
  }
  if (options.expectToolbar === false) {
    assert(!state.remoteSubjectVisible, 'Floating toolbar subject field should not be injected in this view');
    assert(!state.remoteSubmitVisible, 'Floating toolbar submit button should not be injected in this view');
    assert(!state.remoteDownloadVisible, 'Floating toolbar PDF download button should not be injected in this view');
  } else {
    assert(state.remoteSubjectVisible, 'Floating toolbar subject field was not injected');
    assert(state.remoteSubmitVisible, 'Floating toolbar submit button was not injected');
    assert(state.remoteDownloadVisible, 'Floating toolbar PDF download button was not injected');
  }
  assert(state.printOnlyDisplay === 'none', `Print-only content should be hidden on screen: ${formatDiagnostic(state)}`);
  await assertPrintMediaStyles(page);
}

async function fillPattern(page, expected) {
  await page.locator('#test_pattern_text').fill(expected.text);
  await page.locator('#test_pattern_date').fill(expected.date);
  await page.locator('#test_pattern_textarea').fill(expected.textarea);
  await page.locator('#test_pattern_select').selectOption(expected.select);
  await page.locator('#test_pattern_datalist').fill(expected.datalist);
  await page.locator(`input[name="test_pattern_radio"][value="${expected.radio}"]`).check({ force: true });
  await page.locator('#test_pattern_checkbox_yes').evaluate((element) => {
    element.checked = true;
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await page.locator('#test_pattern_checkbox_no').evaluate((element) => {
    element.checked = false;
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await page.locator('#test_pattern_legacy_xbox').click();
  await page.locator('#test_pattern_only_one_alpha').click();
  await page.locator('#test_pattern_only_one_bravo').click();
  await page.locator('#test_pattern_button_element').click();
  await page.locator('#subject').fill(expected.subject);
  await page.locator('#test_pattern_hidden_persisted').evaluate((element, value) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- value is generated inside this local regression test and passed as a Playwright argument, not interpolated into executable code
    element.value = value;
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  }, expected.hiddenPersisted);
  await page.locator('#StoreSignature1').evaluate((element, value) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- value is generated inside this local regression test and passed as a Playwright argument, not interpolated into executable code
    element.value = value;
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  }, expected.storeSignature);
  await page.locator('#SignatureChoice').evaluate((element, value) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- value is generated inside this local regression test and passed as a Playwright argument, not interpolated into executable code
    element.value = value;
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  }, expected.signatureChoice);
  await page.locator('#test_pattern_calc_one').fill('14.25');
  await page.locator('#test_pattern_calc_two').fill('5.75');
  await page.locator('#test_pattern_toggle_details').check();
  await page.locator('#test_pattern_toggle_details').uncheck();
}

function assertPersistedState(state, expected, expectedFdid, label) {
  if (expectedFdid !== null) {
    assert(state.fdid === expectedFdid, `${label} did not render fdid ${expectedFdid}: ${formatDiagnostic(state)}`);
  }
  assert(state.text === expected.text, `${label} did not preserve text: ${formatDiagnostic(state)}`);
  assert(state.date === expected.date, `${label} did not preserve date: ${formatDiagnostic(state)}`);
  assert(state.textarea === expected.textarea, `${label} did not preserve textarea: ${formatDiagnostic(state)}`);
  assert(state.select === expected.select, `${label} did not preserve select: ${formatDiagnostic(state)}`);
  assert(state.datalist === expected.datalist, `${label} did not preserve datalist-backed text input: ${formatDiagnostic(state)}`);
  assert(state.radio === expected.radio, `${label} did not preserve radio: ${formatDiagnostic(state)}`);
  assert(state.checkboxYes === true, `${label} did not preserve checked checkbox: ${formatDiagnostic(state)}`);
  assert(state.checkboxNo === false, `${label} did not preserve unchecked checkbox: ${formatDiagnostic(state)}`);
  assert(state.legacyXbox === 'X', `${label} did not preserve legacy Xbox text checkbox: ${formatDiagnostic(state)}`);
  assert(state.onlyOneAlpha === false, `${label} did not preserve unchecked only-one checkbox: ${formatDiagnostic(state)}`);
  assert(state.onlyOneBravo === true, `${label} did not preserve checked only-one checkbox: ${formatDiagnostic(state)}`);
  assert(state.buttonResult === expected.buttonResult, `${label} did not preserve HTML button interaction result: ${formatDiagnostic(state)}`);
  assert(state.subject === expected.subject, `${label} did not preserve subject field: ${formatDiagnostic(state)}`);
  assert(state.hiddenPersisted === expected.hiddenPersisted, `${label} did not preserve hidden input: ${formatDiagnostic(state)}`);
  assert(state.storeSignature === expected.storeSignature, `${label} did not preserve jSignature storage field: ${formatDiagnostic(state)}`);
  assert(state.signatureChoice === expected.signatureChoice, `${label} did not preserve signature choice field: ${formatDiagnostic(state)}`);
}

async function waitForSavedFormState(page, preSaveState) {
  await page.waitForFunction((previousState) => {
    const valueOf = (id) => {
      const element = document.getElementById(id);
      return element ? element.value : '';
    };
    const fdid = valueOf('fdid');
    const autoclose = valueOf('isSuccess_Autoclose');
    return (/^\d+$/.test(fdid) && fdid !== previousState.fdid)
      || (autoclose === 'true' && autoclose !== previousState.autoclose)
      || /[?&]fdid=\d+/.test(window.location.href);
  }, {
    fdid: preSaveState.fdid || '',
    autoclose: preSaveState.autoclose || '',
  }, { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

async function savePattern(page, expected) {
  await fillPattern(page, expected);
  const preSaveState = await captureState(page);
  assert(preSaveState.calcTotal === '20.0', `Calculated total did not update before save: ${formatDiagnostic(preSaveState)}`);
  assert(preSaveState.conditionalDisplay === 'none', `Conditional block did not hide before save: ${formatDiagnostic(preSaveState)}`);
  await page.locator('#remote_eform_subject').fill(expected.subject);

  const saveResponsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST'
      && response.url().includes('/eform/addEForm')
      && response.status() < 400,
    { timeout: 30000 },
  );
  await Promise.all([
    saveResponsePromise,
    page.locator('#remoteSubmitButton').click(),
  ]);
  await waitForSavedFormState(page, preSaveState);

  const state = await captureState(page);
  if (state.autoclose) {
    assert(state.autoclose === 'true', `Expected auto-close success flag after save, got ${formatDiagnostic(state)}`);
  }
  if (state.fdid) {
    assert(/^\d+$/.test(state.fdid), `Expected numeric fdid after save, got ${formatDiagnostic(state)}`);
  }
  assertPersistedState(state, expected, null, 'post-save test pattern');
  return state.fdid;
}

async function assertSavedPattern(page, expected, fdid, screenshotName) {
  await assertRuntimeSurface(page, expected.fid, { expectedFdid: fdid });
  const state = await captureState(page);
  assertPersistedState(state, expected, fdid, screenshotName);
  await screenshot(page, config.screenshotDir, screenshotName);
}

async function downloadPdf(page, artifactBaseName) {
  const pdfPath = buildArtifactPath(config.screenshotDir, artifactBaseName, '.pdf');
  const downloadPromise = page.waitForEvent('download', { timeout: 60000 });
  await page.locator('#remoteDownloadButton').click();
  const download = await downloadPromise;
  await download.saveAs(pdfPath);
  const pdfBytes = fs.readFileSync(pdfPath);
  assert(pdfBytes.subarray(0, 5).toString('utf8') === '%PDF-', 'Downloaded test pattern payload was not a PDF');
  assert(pdfBytes.length > 500, `Downloaded test pattern PDF was unexpectedly small (${pdfBytes.length} bytes)`);
  return { pdfBytes: pdfBytes.length };
}

function assertDisplayImageFetched(recorder) {
  const matches = recorder.requestLog.filter((response) => response.url.includes(`imagefile=${encodeURIComponent(bgImageName)}`) || response.url.includes(`imagefile=${bgImageName}`));
  assert(matches.length > 0, `No displayImage request captured for ${bgImageName}`);
  assert(matches.some((response) => response.status === 200), `displayImage never returned 200 for ${bgImageName}: ${JSON.stringify(matches, null, 2)}`);
}

function filteredConsoleIssues(recorder) {
  return recorder.consoleIssues.filter((issue) => !(
    issue.label === 'test-pattern-upload'
    && /checkFormAndDisable is not defined/.test(issue.text)
  ) && !(
    /\/favicon\.ico$/.test(issue.location?.url || '')
    && /404/.test(issue.text)
  ));
}

function filteredPageErrors(recorder) {
  // Legacy patient-list unload code can run after its opener has gone away.
  // Keep this exception exact so unrelated patient-list script failures still fail.
  return recorder.pageErrors.filter((issue) => !(
    issue.label === 'test-pattern-patient-list'
    && issue.text === 'Invalid or unexpected token'
  ) && !(
    issue.label === 'test-pattern-patient-list'
    && /Cannot read properties of null \(reading 'document'\)/.test(issue.text)
    && /at updateAjax .*efmpatientformlist\?demographic_no=/.test(issue.text)
    && /at onunload .*efmpatientformlist\?demographic_no=/.test(issue.text)
  ));
}

function redactSensitiveFailureText(value) {
  return String(value)
    .replace(sensitiveQueryParamPattern, '$1<redacted>')
    .replace(/(["']?)(fdid|efmdemographic_no|demographic_no|demographicNo|demo_no|demoNo|patient_id|patientId)(\1)(\s*[:=]\s*)(["']?)\d+\5/gi, '$1$2$3$4$5<redacted>$5')
    .replace(/\b(fdid|efmdemographic_no|demographic_no|demographicNo|demo_no|demoNo|patient_id|patientId)\b\s+\d+/gi, '$1 <redacted>');
}

function redactSensitiveFailureDetails(value) {
  if (typeof value === 'string') {
    return redactSensitiveFailureText(value);
  }
  if (Array.isArray(value)) {
    return value.map(redactSensitiveFailureDetails);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, nestedValue]) => [
        key,
        sensitiveDiagnosticFieldPattern.test(key) ? '<redacted>' : redactSensitiveFailureDetails(nestedValue),
      ]),
    );
  }
  return value;
}

function formatDiagnostic(value) {
  return JSON.stringify(redactSensitiveFailureDetails(value));
}

async function readCsrfToken(page, label) {
  let csrfToken = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue({ timeout: 5000 }).catch(() => '');
  if (!csrfToken) {
    const configuredContextPath = config.baseUrl.pathname === '/' ? '' : config.baseUrl.pathname.replace(/\/$/, '');
    csrfToken = await page.evaluate(async (baseContextPath) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection,javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- baseContextPath is derived from validateBaseUrl and used only for a same-origin CSRFGuard fetch fallback
      const contextInput = document.getElementById('context');
      const contextPath = contextInput && contextInput.value
        ? contextInput.value
        : baseContextPath;
      const response = await fetch(`${contextPath}/csrfguard`, { credentials: 'same-origin' });
      if (!response.ok) {
        return '';
      }
      const js = await response.text();
      const match = js.match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
      return match ? match[1] : '';
    }, configuredContextPath).catch(() => '');
  }
  assert(csrfToken, `${label} page did not expose a CSRF token for cleanup`);
  return csrfToken;
}

async function cleanupImportedEform(managerPage, fid) {
  if (!managerPage || managerPage.isClosed() || !fid) {
    return;
  }
  const deleteFormId = `test_pattern_delete_form_${fid}`;
  const csrfToken = await readCsrfToken(managerPage, 'form manager');
  await managerPage.evaluate(({ submittedFid, formId, token }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- submittedFid, formId, and token are passed as Playwright arguments, not interpolated into code
    const form = document.createElement('form');
    form.id = formId;
    form.method = 'post';
    form.action = `${window.location.origin}${window.location.pathname.replace(/\/efmformmanager.*$/, '')}/delEForm`;
    const csrfInput = document.createElement('input');
    csrfInput.type = 'hidden';
    csrfInput.name = 'CSRF-TOKEN';
    csrfInput.value = token;
    form.appendChild(csrfInput);
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'fid';
    input.value = submittedFid;
    form.appendChild(input);
    document.body.appendChild(form);
  }, { submittedFid: fid, formId: deleteFormId, token: csrfToken });
  const deleteResponsePromise = managerPage.waitForResponse(
    (response) => response.request().method() === 'POST'
      && response.url().includes('/eform/delEForm'),
    { timeout: 15000 },
  );
  const deleteNavigationPromise = managerPage.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => null);
  await Promise.all([
    deleteResponsePromise,
    deleteNavigationPromise,
    managerPage.locator(`#${deleteFormId}`).evaluate((form) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed cleanup helper submits an already-selected local form without interpolated code
      form.submit();
    }),
  ]);
  await managerPage.waitForLoadState('domcontentloaded', { timeout: 15000 }).catch(() => {});
  await managerPage.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await gotoApp(managerPage, config.baseUrl, '/eform/efmformmanager');
  await managerPage.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  const remaining = await managerPage.locator(`#eformTbl a[href*="fid=${fid}"], #eformTbl a[onclick*="fid=${fid}"]`).count();
  assert(remaining === 0, `Imported eForm fid ${fid} still appears in the form manager after cleanup`);
}

async function cleanupSavedEform(context, recorder, fdid) {
  if (!context || !/^\d+$/.test(String(fdid))) {
    return false;
  }
  const page = await context.newPage();
  wirePage(page, 'test-pattern-saved-cleanup', recorder);
  try {
    await gotoApp(page, config.baseUrl, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(config.demographicNo)}`);
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    const form = page.locator('form', {
      has: page.locator(`input[name="fdid"][value="${fdid}"]`),
    }).first();
    if (!(await form.count())) {
      return false;
    }

    const removeResponsePromise = page.waitForResponse(
      (response) => response.request().method() === 'POST'
        && response.url().includes('/eform/removeEForm'),
      { timeout: 15000 },
    );
    const removeNavigationPromise = page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => null);
    await Promise.all([
      removeResponsePromise,
      removeNavigationPromise,
      form.evaluate((formElement) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed cleanup helper submits an already-selected local form without interpolated code
        formElement.submit();
      }),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    await gotoApp(page, config.baseUrl, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(config.demographicNo)}`);
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    const remaining = await page.locator('form', {
      has: page.locator(`input[name="fdid"][value="${fdid}"]`),
    }).count();
    assert(remaining === 0, `Saved eForm fdid ${fdid} still appears in the active patient eForm list after cleanup`);
    return true;
  } finally {
    await page.close().catch(() => {});
  }
}

async function cleanupSavedEforms(context, recorder, fdids) {
  const uniqueFdids = Array.from(new Set(Array.from(fdids).filter((fdid) => /^\d+$/.test(String(fdid)))));
  for (const fdid of uniqueFdids) {
    await cleanupSavedEform(context, recorder, fdid);
  }
}

async function cleanupUploadedImage(context, imageName) {
  if (!context || !imageName) {
    return false;
  }
  const page = await context.newPage();
  try {
    await gotoApp(page, config.baseUrl, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    const imageLink = page.locator('#tblImage a.viewImage', { hasText: imageName }).first();
    if (!(await imageLink.count())) {
      return false;
    }

    const deleteFormId = `test_pattern_delete_image_${Date.now()}`;
    const csrfToken = await readCsrfToken(page, 'image manager');
    await page.evaluate(({ fileName, formId, token }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- fileName, formId, and token are passed as Playwright arguments, not interpolated into code
      const form = document.createElement('form');
      form.id = formId;
      form.method = 'post';
      form.action = `${window.location.origin}${window.location.pathname.replace(/\/efmimagemanager.*$/, '')}/deleteImage`;
      const csrfInput = document.createElement('input');
      csrfInput.type = 'hidden';
      csrfInput.name = 'CSRF-TOKEN';
      csrfInput.value = token;
      form.appendChild(csrfInput);
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = 'filename';
      input.value = fileName;
      form.appendChild(input);
      document.body.appendChild(form);
    }, { fileName: imageName, formId: deleteFormId, token: csrfToken });
    const deleteResponsePromise = page.waitForResponse(
      (response) => response.request().method() === 'POST'
        && response.url().includes('/eform/deleteImage'),
      { timeout: 15000 },
    );
    const deleteNavigationPromise = page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => null);
    await Promise.all([
      deleteResponsePromise,
      deleteNavigationPromise,
      page.locator(`#${deleteFormId}`).evaluate((form) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed cleanup helper submits an already-selected local form without interpolated code
        form.submit();
      }),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    await gotoApp(page, config.baseUrl, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    const remaining = await page.locator('#tblImage a.viewImage', { hasText: imageName }).count();
    assert(remaining === 0, `Uploaded image ${imageName} still appears in the image manager after cleanup`);
    return true;
  } finally {
    await page.close().catch(() => {});
  }
}

(async () => {
  validateConfig();
  const recorder = createRecorder();
  const timestamp = Date.now();
  const runId = `${timestamp}_${process.pid}`;
  const formName = `Playwright Test Pattern ${runId}`;
  const formSubject = `Test Pattern ${runId}`;
  bgImageName = `playwright_test_pattern_bg_${runId}.png`;
  const expected = {
    fid: '',
    text: `Pattern text ${timestamp}`,
    date: '2026-07-14',
    textarea: `Pattern textarea ${timestamp}\nLine two\nLine three`,
    select: 'charlie',
    datalist: 'Legacy typed option',
    radio: 'charlie',
    subject: `Pattern subject ${runId}`,
    hiddenPersisted: `hidden-${timestamp}`,
    storeSignature: `image/jsignature;base30,legacy-${timestamp}`,
    signatureChoice: 'Wet',
    buttonResult: 'clicked',
  };

  let managerPage = null;
  let importedFid = null;
  let context = null;
  let runtimeFixture = null;
  let browser = null;
  const savedFdidsToCleanup = new Set();

  try {
    runtimeFixture = createRuntimeFixture(bgImageName);
    config.fixtureHtmlPath = runtimeFixture.htmlPath;
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    context = await browser.newContext({
      acceptDownloads: true,
      ignoreHTTPSErrors: true,
      viewport: { width: 1280, height: 1600 },
    });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    await ensureImageUploaded(context, recorder, runtimeFixture.imagePath, bgImageName);
    const uploadResult = await uploadEform(context, recorder, formName, formSubject);
    managerPage = uploadResult.page;
    importedFid = uploadResult.fid;
    expected.fid = importedFid;

    const managerPreview = await openManagerPreview(context, recorder, uploadResult.row);
    await assertRuntimeSurface(managerPreview, importedFid, { expectToolbar: false });
    await screenshot(managerPreview, config.screenshotDir, 'eform-test-pattern-manager-preview');
    await managerPreview.close();

    const addPage = await openAddEform(context, recorder, importedFid);
    await assertRuntimeSurface(addPage, importedFid, { expectedSource: sourceMarkerValue });
    let fdid = await savePattern(addPage, expected);
    if (fdid) {
      savedFdidsToCleanup.add(fdid);
    }
    await screenshot(addPage, config.screenshotDir, 'eform-test-pattern-after-save');
    if (!fdid) {
      fdid = await resolveSavedFdidFromPatientList(context, recorder, expected.subject);
      savedFdidsToCleanup.add(fdid);
    }
    await addPage.close();

    const directPage = await openSavedEformDirect(context, recorder, fdid);
    await assertSavedPattern(directPage, expected, fdid, 'eform-test-pattern-direct-reopen');
    const pdfResult = await downloadPdf(directPage, `eform-test-pattern-${runId}`);
    for (const currentFdid of await listSavedFdidsFromPatientList(context, recorder, expected.subject)) {
      savedFdidsToCleanup.add(currentFdid);
    }
    await directPage.close();

    const patientListPopup = await openSavedEformFromPatientList(context, recorder, fdid);
    assert(patientListPopup.url().includes(`fdid=${fdid}`), `Patient list popup did not open fdid ${fdid}: ${patientListPopup.url()}`);
    await assertSavedPattern(patientListPopup, expected, fdid, 'eform-test-pattern-patient-list-reopen');
    await patientListPopup.close();

    assertDisplayImageFetched(recorder);
    assert(recorder.badResponses.length === 0, `Unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    const consoleIssues = filteredConsoleIssues(recorder);
    assert(consoleIssues.length === 0, `Unexpected browser console failures: ${JSON.stringify(consoleIssues, null, 2)}`);
    const pageErrors = filteredPageErrors(recorder);
    assert(pageErrors.length === 0, `Unexpected page errors: ${JSON.stringify(pageErrors, null, 2)}`);

    console.log(JSON.stringify({
      importedFormName: formName,
      importedTemplateCreated: Boolean(importedFid),
      savedEformCreated: /^\d+$/.test(String(fdid)),
      savedEformsQueuedForCleanup: savedFdidsToCleanup.size,
      pdfDownloaded: true,
      pdfBytes: pdfResult.pdfBytes,
    }, null, 2));
    console.log('PASS all-in-one eForm test pattern render/save/PDF check');
  } catch (error) {
    console.error('FAIL all-in-one eForm test pattern Playwright check');
    console.error(redactSensitiveFailureText(error.stack || error.message));
    console.error(JSON.stringify(redactSensitiveFailureDetails(buildFailureDetails(recorder)), null, 2));
    process.exitCode = 1;
  } finally {
    const cleanupErrors = [];
    if (context) {
      await listSavedFdidsFromPatientList(context, recorder, formName)
        .then((fdids) => fdids.forEach((fdid) => savedFdidsToCleanup.add(fdid)))
        .catch((error) => cleanupErrors.push(error));
      await cleanupSavedEforms(context, recorder, savedFdidsToCleanup).catch((error) => cleanupErrors.push(error));
    }
    if (context && (!importedFid || !managerPage || managerPage.isClosed())) {
      const rediscovered = await findImportedEformForCleanup(context, recorder, formName);
      if (rediscovered) {
        if (managerPage && !managerPage.isClosed()) {
          await managerPage.close().catch(() => {});
        }
        managerPage = rediscovered.page;
        importedFid = rediscovered.fid;
      }
    }
    await cleanupImportedEform(managerPage, importedFid).catch((error) => cleanupErrors.push(error));
    if (context) {
      await cleanupUploadedImage(context, bgImageName).catch((error) => cleanupErrors.push(error));
    }
    if (managerPage && !managerPage.isClosed()) {
      await managerPage.close().catch(() => {});
    }
    if (runtimeFixture) {
      fs.rmSync(runtimeFixture.tempDir, { recursive: true, force: true });
    }
    if (browser) {
      await browser.close();
    }
    if (cleanupErrors.length) {
      for (const cleanupError of cleanupErrors) {
        console.error(redactSensitiveFailureText(cleanupError.stack || cleanupError.message));
      }
      process.exitCode = 1;
    }
  }
})();
