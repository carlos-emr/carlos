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
 * The suite's original shared harness, now a thin re-export of
 * scripts/lib/playwright-harness.js plus the eForm-specific helpers that only
 * ever belonged here.
 *
 * WHY THE SPLIT. Despite the eForm name this module became the harness for 50 of
 * the 75 browser checks, which made "shared harness" and "eForm helpers" the same
 * file and left no obvious home for anything that was neither. The general
 * helpers moved to lib/playwright-harness.js; the names below keep working so no
 * existing check has to change in the same commit that moves them.
 *
 * NEW CHECKS SHOULD require('./lib/playwright-harness') AND './lib/playwright-ui'
 * DIRECTLY. This file stays for the unmigrated checks and is expected to shrink
 * to the eForm helpers alone once they have all moved.
 */

const harness = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');

const {
  assert, assertNotErrorPage, gotoApp, wirePage,
} = harness;

async function openManager(context, config, recorder, label = 'manager') {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function findLibraryEform(page, formName) {
  const row = page.locator('#eformTbl tbody tr', { hasText: formName }).first();
  await row.waitFor({ state: 'visible', timeout: 15000 });
  const previewOnclick = await row.locator('a[onclick*="efmshowform_data?fid="]').first().getAttribute('onclick');
  const editHref = await row.locator('a[href*="efmformmanageredit?fid="]').first().getAttribute('href');
  const previewMatch = previewOnclick ? previewOnclick.match(/fid=([^&'"]+)/) : null;
  const editMatch = editHref ? editHref.match(/fid=([^&'"]+)/) : null;
  assert((previewMatch && previewMatch[1]) || (editMatch && editMatch[1]), `Could not extract fid for ${formName}`);
  return {
    row,
    fid: decodeURIComponent((previewMatch && previewMatch[1]) || editMatch[1]),
  };
}

async function openAddEform(context, config, recorder, fid, demographicNo, label = 'add-eform') {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.close = () => {
      window.__playwrightCloseIntercepted = true;
    };
  });
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(demographicNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function saveCurrentEform(page, subjectValue) {
  await assertNotErrorPage(page, 'save candidate');
  await page.locator('#remote_eform_subject').fill(subjectValue);
  await clickAndAwaitReload(page, page.locator('#remoteSubmitButton'), {
    timeout: 30000, label: 'the eForm save',
  });
  await page.locator('#fdid').waitFor({ state: 'attached', timeout: 15000 });
  const fdid = await page.locator('#fdid').inputValue();
  assert(/^\d+$/.test(fdid), `Expected saved eForm fdid after submit, got ${fdid}`);
  return fdid;
}

async function openAttachPopup(page, context) {
  const popupPromise = context.waitForEvent('page', { timeout: 30000 });
  await page.locator('.editControlButton[title="Attach"]').click();
  const popup = await popupPromise;
  return popup;
}

async function waitForPopupReady(popup, recorder, label) {
  wirePage(popup, label, recorder);
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, label);
}

async function invokeFetchAttached(page) {
  const hasFunction = await page.evaluate(() => typeof fetchAttached === 'function'); // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code executed without interpolating user-controlled input
  if (!hasFunction) {
    return { hasFunction: false, text: '', html: '' };
  }

  const target = page.locator('#tdAttachedDocs');
  const previousHtml = await target.evaluate((element) => element.innerHTML).catch(() => '');
  let sidebarResponse = null;
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes('/eform/displayAttachedFiles'),
    { timeout: 30000 },
  ).then((response) => {
    sidebarResponse = response;
    return response;
  }).catch(() => null);
  const domPromise = page.waitForFunction((previousMarkup) => {
    const attachmentTarget = document.getElementById('tdAttachedDocs');
    if (!attachmentTarget) {
      return false;
    }
    const currentMarkup = attachmentTarget.innerHTML.trim();
    return currentMarkup.length > 0 && currentMarkup !== previousMarkup;
  }, previousHtml, { timeout: 30000 }).catch(() => null);

  const invocation = await page.evaluate(() => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code executed without interpolating user-controlled input
    try {
      fetchAttached();
      return { hasFunction: true };
    } catch (error) {
      return { hasFunction: true, error: String(error) };
    }
  });
  if (invocation.error) {
    return {
      hasFunction: true, error: invocation.error, text: '', html: '',
    };
  }

  // Let whichever of the DOM change or the network response settle first, then wait for the DOM to
  // finish updating.
  await Promise.race([responsePromise, domPromise]);
  await page.waitForFunction((previousMarkup) => {
    const attachmentTarget = document.getElementById('tdAttachedDocs');
    return !!attachmentTarget && attachmentTarget.innerHTML.trim().length > 0
      && attachmentTarget.innerHTML !== previousMarkup;
  }, previousHtml, { timeout: 5000 }).catch(() => {});
  // Gate success on the SETTLED network response, not on whichever promise won the race above: if the
  // DOM changed optimistically before the request finished, sidebarResponse would still be null and a
  // 4xx/5xx attachment failure would be wrongly reported as success. Awaiting the
  // response promise (it resolves to null on timeout/error) makes the status check deterministic.
  await responsePromise;
  // A null response means the request timed out or errored (responsePromise resolves to null in that
  // case); treat that as a failure too, otherwise a missing attachment load would pass silently.
  if (!sidebarResponse) {
    return {
      hasFunction: true,
      error: 'fetchAttached() did not receive a displayAttachedFiles response',
      text: '',
      html: '',
    };
  }
  if (sidebarResponse.status() >= 400) {
    return {
      hasFunction: true,
      error: `fetchAttached() request failed with HTTP ${sidebarResponse.status()} for ${sidebarResponse.url()}`,
      text: '',
      html: '',
      status: sidebarResponse.status(),
      url: sidebarResponse.url(),
    };
  }

  return page.evaluate(() => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code executed without interpolating user-controlled input
    const attached = document.getElementById('tdAttachedDocs');
    return {
      hasFunction: true,
      text: attached ? attached.textContent.trim() : '',
      html: attached ? attached.innerHTML : '',
    };
  });
}

module.exports = {
  ...harness,
  findLibraryEform,
  invokeFetchAttached,
  openAddEform,
  openAttachPopup,
  openManager,
  saveCurrentEform,
  waitForPopupReady,
};
