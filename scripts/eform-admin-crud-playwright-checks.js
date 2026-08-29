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
 * Local-only browser regression check for eForm ADMINISTRATION create / edit /
 * delete (Administration > eForms). The sibling eform-admin check only renders
 * pages; nothing exercised the mutating routes, which is how three separate
 * defects reached a tester on 2026.08.0-alpha8:
 *
 *   1. delete did nothing. confirmNDelete() builds its POST form in JS at click
 *      time, so CSRFGuard had no <form> to inject a token into and
 *      CarlosCsrfGuardFilter rejected the POST ("Required Token is missing").
 *   2. a SUCCESSFUL delete then returned 405, because DelEForm2Action forwarded
 *      to the library gate, which permits POST only for efmformmanageredit.
 *   3. saving an edited eForm returned nginx's bare 403: ARGS:formHtml IS an
 *      HTML document, so CRS matched the 941xxx/932xxx families and tripped the
 *      949110 anomaly threshold. Only reproducible through the packaged front
 *      door — the devcontainer has no WAF.
 *
 * RUN THIS THROUGH :443 to keep defect 3 covered. Against bare Tomcat the CSRF
 * and persistence assertions still hold, but no WAF sits in the path, so the
 * check silently stops covering the very failure it was written for; it warns
 * on stdout when BASE_URL is not HTTPS.
 *
 * FIXTURE SAFETY: this check creates its own uniquely-named eForm and only ever
 * edits and deletes that one. It must never delete a library form it did not
 * create — removing the shared "Rich Text Letter" breaks every RTL check in the
 * suite (learned the hard way while diagnosing these defects).
 *
 * A PASSING run cleans up after itself: the delete step is the last assertion,
 * so the probe form is gone by the end. A FAILING run deliberately leaves its
 * form behind for diagnosis; the names are timestamped, so repeated failures
 * accumulate rather than collide. Clear them with:
 *   UPDATE eform SET status=0 WHERE form_name LIKE 'Playwright Admin CRUD %';
 */

const { chromium } = require('playwright');
const {
  assert,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  openManager,
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
  screenshotDir: process.env.EFORM_SCREENSHOT_DIR || '/tmp',
};

const stamp = Date.now();
const formName = `Playwright Admin CRUD ${stamp}`;

// Deliberately shaped like a real eForm: a full HTML document with <meta>,
// <style>, <script> and an onload handler. That is exactly the content CRS
// scores as XSS/RCE on ARGS:formHtml, so a WAF regression fails this check
// rather than slipping through on a trivially-safe payload.
const baseHtml = [
  '<html>',
  '<head>',
  '<meta http-equiv="content-type" content="text/html; charset=UTF-8">',
  `<title>${formName}</title>`,
  '<style type="text/css"> body { font-family: Arial; } .lab { color:#CC0099; } </style>',
  '<script language="javascript">',
  'function carlosAdminCrudProbe() { return "ok"; }',
  'window.onload = function () { carlosAdminCrudProbe(); };',
  '</script>',
  '</head>',
  '<body>',
  '<p class="lab">Playwright admin CRUD probe form.</p>',
  '</body>',
  '</html>',
].join('\n');

async function openEditor(context, recorder, label, fid) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  const path = fid ? `/eform/efmformmanageredit?fid=${encodeURIComponent(fid)}` : '/eform/efmformmanageredit';
  await gotoApp(page, config.baseUrl, path);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

// The editor POSTs multipart to /eform/editForm. Capture the response so a WAF
// or CSRF rejection is reported as itself rather than as a downstream "the text
// I typed vanished" mystery.
async function submitEditor(page) {
  const [response] = await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/eform/editForm') && r.request().method() === 'POST',
      { timeout: 60000 },
    ),
    page.locator('#savebtn').click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return response;
}

function assertNotBlocked(response, what) {
  const status = response.status();
  assert(
    status !== 403,
    `${what} was rejected with 403. Either CSRFGuard got no token, or ModSecurity blocked it `
      + '(check /var/log/carlos-emr/modsec/modsec_audit.log for rule 949110 on ARGS:formHtml, '
      + 'and the 1050 exclusion in REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf).',
  );
  assert(
    status !== 405,
    `${what} returned 405 — a mutator result forwarded to a gate that refuses POST. `
      + 'The delEForm success result must be a redirect, not a forward.',
  );
  assert(status < 400, `${what} returned HTTP ${status}`);
}

async function libraryRow(page, name) {
  return page.locator('#eformTbl tbody tr', { hasText: name });
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    // validateBaseUrl returns a URL object, not a string.
    if (config.baseUrl.protocol !== 'https:') {
      console.log(
        '[warn] BASE_URL is not HTTPS, so this run does NOT go through nginx/ModSecurity; '
        + 'the eForm-editor WAF regression (rule 1050) is not covered by this invocation.',
      );
    }

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1400, height: 900 } });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    // ---- create -------------------------------------------------------------
    // A new eForm goes through the same /eform/editForm route as an edit, so
    // this already covers the WAF path for the create direction.
    const createPage = await openEditor(context, recorder, 'admin-crud-create');
    await createPage.locator('input[name="formName"]').fill(formName);
    await createPage.locator('textarea[name="formHtml"]').fill(baseHtml);
    const createResponse = await submitEditor(createPage);
    assertNotBlocked(createResponse, 'Creating an eForm');
    await createPage.close();

    const managerAfterCreate = await openManager(context, config, recorder, 'admin-crud-after-create');
    const createdRow = await libraryRow(managerAfterCreate, formName);
    await createdRow.first().waitFor({ state: 'visible', timeout: 15000 });
    const editHref = await createdRow.first().locator('a[href*="efmformmanageredit?fid="]').first().getAttribute('href');
    const fidMatch = editHref?.match(/fid=([^&'"]+)/);
    assert(fidMatch?.[1], `Could not read the fid of the eForm just created (${formName})`);
    const fid = decodeURIComponent(fidMatch[1]);
    await managerAfterCreate.close();

    // ---- back-to-library control is reachable without scrolling -------------
    // The editor's HTML textarea is 40 rows; before the fix the only back
    // controls sat below it, measured at y=1381 in a 900px viewport.
    const editPage = await openEditor(context, recorder, 'admin-crud-edit', fid);
    const backLinks = editPage.locator('a:has-text("Back to eForm Library")');
    const backCount = await backLinks.count();
    assert(backCount > 0, 'Editor has no "Back to eForm Library" control at all');
    const viewportHeight = editPage.viewportSize().height;
    let aboveFold = 0;
    for (let i = 0; i < backCount; i += 1) {
      const box = await backLinks.nth(i).boundingBox();
      if (box && box.y >= 0 && box.y < viewportHeight) {
        aboveFold += 1;
      }
    }
    assert(
      aboveFold > 0,
      `No "Back to eForm Library" control is visible without scrolling (viewport ${viewportHeight}px, `
        + `${backCount} control(s), all below the fold)`,
    );

    // ---- edit persists ------------------------------------------------------
    const marker = `CARLOS-ADMIN-CRUD-MARKER-${stamp}`;
    const textarea = editPage.locator('textarea[name="formHtml"]');
    const before = await textarea.inputValue();
    assert(before.includes('carlosAdminCrudProbe'), 'Editor did not load the HTML that was just saved');
    await textarea.fill(`${before}\n<!-- ${marker} -->\n`);
    const editResponse = await submitEditor(editPage);
    assertNotBlocked(editResponse, 'Saving an edited eForm');
    await editPage.close();

    // Re-open from scratch: the success banner is not proof, because the editor
    // redirects to the library on a successful save. Persistence is the proof.
    const reopened = await openEditor(context, recorder, 'admin-crud-reopen', fid);
    const persisted = await reopened.locator('textarea[name="formHtml"]').inputValue();
    assert(
      persisted.includes(marker),
      'Edited eForm HTML did not persist: the marker is absent after reloading the editor',
    );
    await reopened.close();

    // ---- delete -------------------------------------------------------------
    const managerPage = await openManager(context, config, recorder, 'admin-crud-delete');
    // wirePage installs a handler that DISMISSES dialogs, which cancels
    // confirmNDelete()'s confirm() and means the delete never runs. Replace it
    // for this page only, still recording the dialog for failure diagnostics.
    managerPage.removeAllListeners('dialog');
    managerPage.on('dialog', async (dialog) => {
      recorder.dialogs.push({ label: 'admin-crud-delete', type: dialog.type(), text: dialog.message() });
      await dialog.accept().catch(() => {});
    });

    const targetRow = (await libraryRow(managerPage, formName)).first();
    await targetRow.waitFor({ state: 'visible', timeout: 15000 });
    const deleteLink = targetRow.locator('a[onclick^="confirmNDelete"]').first();
    assert(await deleteLink.count() > 0, `No delete control on the row for ${formName}`);

    // Guard the fixture-safety invariant in code, not just in the comment: only
    // ever delete the fid this check created.
    const onclick = await deleteLink.getAttribute('onclick');
    const deleteFid = onclick?.match(/confirmNDelete\(\s*["']([^"']+)["']\s*\)/)?.[1];
    assert(
      deleteFid === fid,
      `Refusing to delete fid ${deleteFid}: this check only deletes the eForm it created (fid ${fid})`,
    );

    const [deleteResponse] = await Promise.all([
      managerPage.waitForResponse(
        (r) => r.url().includes('/eform/delEForm') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      deleteLink.click(),
    ]);
    assertNotBlocked(deleteResponse, 'Deleting an eForm');
    await managerPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const landedOn = managerPage.url();
    assert(
      landedOn.includes('/eform/efmformmanager'),
      `After deleting, the operator should land back on the eForm library, got ${landedOn}`,
    );
    const bodyText = await managerPage.locator('body').innerText();
    assert(
      !/CARLOS Error:|unexpected error/i.test(bodyText),
      'Deleting an eForm landed on the application error page',
    );

    await screenshot(managerPage, config.screenshotDir, 'eform-admin-crud-after-delete');

    // Confirm from a fresh load, not the redirected page, that it is really gone.
    const managerFinal = await openManager(context, config, recorder, 'admin-crud-verify');
    const remaining = await (await libraryRow(managerFinal, formName)).count();
    assert(remaining === 0, `Deleted eForm "${formName}" is still listed in the library`);
    await managerFinal.close();

    await managerPage.close();
    await context.close();

    console.log(
      `PASS eForm admin create/edit/delete round trip (fid ${fid}): edit persisted, `
      + 'delete removed the form and returned to the library',
    );
  } catch (error) {
    console.error('FAIL eForm admin CRUD Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
