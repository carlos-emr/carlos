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
 *      The token now comes from csrf-token.jspf, which populates it from an
 *      ASYNC fetch — so a click that beats the fetch reproduces defect 1
 *      exactly. The delete step therefore runs with /csrfguard deliberately
 *      stalled, and asserts the token really was still empty at click time.
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
  assertNoPageErrors,
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
let restoredForm = false;

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
  // Parent-relative asset path, deliberately. CRS 930110 (attack-lfi) is
  // CRITICAL at paranoia level 1 and matches a bare "../" in ARGS, so an eForm
  // referencing an image the way saved eForms actually do was blocked on that
  // signature alone until exclusions 1050/1060/1070 carried attack-lfi. Without
  // this line the probe is trivially clean of traversal and the check passes
  // through that regression.
  '<img src="../eform/displayImage.do?imagefile=carlos-crud-probe.png" alt="probe">',
  '<link rel="stylesheet" href="../../share/css/carlos-crud-probe.css">',
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

/**
 * Opens the editor the way an administrator actually reaches it: land on
 * Administration, CLICK "Manage eForms" in the left nav, then CLICK the pencil
 * on the form's row. Everything stays inside the panel's #dynamic-content.
 *
 * Driving it by clicks rather than by navigating to /eform/efmformmanageredit
 * is the whole point: the operator edits inside the admin shell, and that path
 * once diverged from the standalone one (registerFormSubmit re-serialised the
 * save urlencoded via $(form).serialize(), which the standalone save did not),
 * so a WAF 403 hid on the panel path while the standalone save this file
 * already exercised passed. registerFormSubmit now posts multipart FormData, so
 * both paths post the same shape — this check drives the operator path to keep
 * that true and to prove the FormData save still works.
 */
async function openEditorInAdminPanel(context, recorder, label, formLabel) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, '/administration');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  assert(
    await page.evaluate(() => typeof window.registerFormSubmit === 'function'),
    'The Administration shell did not define registerFormSubmit, so this run would silently '
      + 'fall back to the standalone save and stop covering the operator (panel) path.',
  );

  // Left nav: expand "Forms/eForms" first. It is a Bootstrap accordion section
  // that ships collapsed, so the eForm links exist in the DOM but are not
  // clickable until an operator opens it -- and a check that skips this step
  // times out on an invisible element rather than testing anything.
  const formsSection = page.locator('button[data-bs-target="#collapseForms"]').first();
  await formsSection.waitFor({ state: 'visible', timeout: 20000 });
  await formsSection.click();

  // Then Manage eForms. index.jsp binds a.contentLink at ready() time, and this
  // link is present then, so the click loads the library into #dynamic-content
  // rather than navigating away.
  const manageEForms = page.locator('a.defaultForms').first();
  await manageEForms.waitFor({ state: 'visible', timeout: 20000 });
  await manageEForms.click();
  await page.locator('#dynamic-content #eformTbl').waitFor({ state: 'visible', timeout: 30000 });

  // Library row -> the pencil. These rows arrived by AJAX, so this click is
  // served by efmFooter.jspf's DELEGATED a.contentLink handler -- the same
  // handler the delete-banner defect lives in.
  const row = page.locator('#dynamic-content #eformTbl tbody tr', { hasText: formLabel }).first();
  await row.waitFor({ state: 'visible', timeout: 20000 });
  await row.locator('a.contentLink[href*="efmformmanageredit?fid="]').first().click();
  await page.locator('#dynamic-content textarea[name="formHtml"]').waitFor({ state: 'visible', timeout: 30000 });
  return page;
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

    // ---- save from inside the Administration panel -------------------------
    // The operator's real path: the editor loaded into the admin shell's
    // #dynamic-content and saved through the shared registerFormSubmit() helper.
    // That helper once re-serialised the save urlencoded (which put the document
    // in REQUEST_BODY and is how a 403 survived a "fixed" build); it now posts
    // multipart FormData, so this asserts the operator save is not WAF-blocked
    // AND that the FormData path still saves. If FormData is ever reverted this
    // save 403s and this assertion fails — the intended regression guard.
    const panelMarker = `CARLOS-ADMIN-PANEL-MARKER-${stamp}`;
    const panelPage = await openEditorInAdminPanel(context, recorder, 'admin-crud-panel-edit', formName);
    const panelTextarea = panelPage.locator('#dynamic-content textarea[name="formHtml"]');
    const panelBefore = await panelTextarea.inputValue();
    await panelTextarea.fill(`${panelBefore}\n<!-- ${panelMarker} -->\n`);

    const [panelResponse] = await Promise.all([
      panelPage.waitForResponse(
        (r) => r.url().includes('/eform/editForm') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      panelPage.locator('#dynamic-content #savebtn').click(),
    ]);

    // The panel save must go out multipart (the FormData fix); a urlencoded
    // content type here means registerFormSubmit reverted to serialize(), which
    // silently drops file inputs on multipart forms.
    const panelContentType = (panelResponse.request().headers()['content-type'] || '').toLowerCase();
    assert(
      panelContentType.includes('multipart/form-data'),
      `The Administration-panel save posted "${panelContentType}", not multipart — `
        + 'registerFormSubmit is no longer sending FormData, which drops file inputs.',
    );
    assertNotBlocked(panelResponse, 'Saving an eForm from the Administration panel');
    await panelPage.close();

    const panelReopened = await openEditor(context, recorder, 'admin-crud-panel-reopen', fid);
    const panelPersisted = await panelReopened.locator('textarea[name="formHtml"]').inputValue();
    assert(
      panelPersisted.includes(panelMarker),
      'The Administration-panel save did not persist: its marker is absent after reloading the editor',
    );
    await panelReopened.close();

    // ---- delete -------------------------------------------------------------
    // Delete while the CSRF token fetch is still in flight. The delete form is
    // built in JS at click time and csrf-token.jspf populates the hidden
    // CSRF-TOKEN input from an async fetch, so a click landing before that
    // fetch resolves reads an empty value; submitting anyway is rejected by
    // CSRFGuard with a 403 the operator only ever sees as "delete did nothing"
    // — the same silent no-op this check exists to catch. Stalling the fetch
    // widens that window enough to click inside reliably, so the delete
    // assertions below cover the race and not just the happy path where the
    // token happened to win.
    //
    // Stall only the bootstrap's fetch(), never the parser-blocking
    // <script src=".../csrfguard"> that CsrfGuardScriptInjectionFilter puts
    // before </head>: delaying that tag delays DOMContentLoaded, so the
    // bootstrap fetch would not have started by the time the page settled.
    // That closes the window instead of opening it, and the check would go on
    // passing while testing nothing.
    const csrfStallMs = 5000;
    await context.route('**/csrfguard*', async (route) => {
      if (route.request().resourceType() !== 'script') {
        await new Promise((resolve) => setTimeout(resolve, csrfStallMs));
      }
      await route.continue();
    });
    const managerPage = await context.newPage();
    wirePage(managerPage, 'admin-crud-delete', recorder);
    await gotoApp(managerPage, config.baseUrl, '/eform/efmformmanager');
    // Wait for 'load', not 'networkidle': 'load' lets DataTables finish
    // initialising the library table, but a pending fetch() does not hold it
    // open, so the stalled token request is still in flight underneath us.
    // 'networkidle' would wait the stall out and destroy the race window.
    await managerPage.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
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

    // Prove the stall actually held before clicking. Without this the check
    // would silently degrade into the happy path the moment the token started
    // arriving first, and stop covering the race at all.
    const tokenAtClickTime = await managerPage
      .locator('input[name="CSRF-TOKEN"]').first().inputValue();
    assert(
      tokenAtClickTime === '',
      'The /csrfguard stall did not hold: the CSRF token was already populated before the '
        + 'delete click, so this run did not exercise the empty-token race it is meant to cover.',
    );

    // Deterministically pin the efmFooter guard: wrap jQuery.fn.load so we can see
    // whether the delegated a.contentLink handler tries to AJAX-load the anchor's
    // javascript:void(0); href. This is independent of whether #dynamic-content
    // exists (on the standalone library page jQuery.load on an empty set is a
    // no-op, so the painted banner is invisible there and the body-text check
    // below cannot see it) — but the handler still CALLS load() with the bogus
    // URL pre-fix, and must NOT post-fix. This records every such call.
    // Stash the flag in sessionStorage, not a window var: confirmNDelete's
    // form.submit() navigates this page, destroying the JS context before we
    // could read a window global. sessionStorage survives the same-origin
    // redirect (delEForm -> 302 -> library), so the flag is readable after the
    // page settles. The bogus load() call is synchronous in the click handler,
    // before navigation starts, so the write always lands.
    const instrumented = await managerPage.evaluate(() => {
      try {
        const jq = window.jQuery;
        if (!jq || !jq.fn || typeof jq.fn.load !== 'function') return false;
        sessionStorage.removeItem('__carlosBogusLoad');
        const orig = jq.fn.load;
        jq.fn.load = function (url) {
          if (typeof url === 'string' && (/^\s*javascript:/i.test(url) || url === '#' || url === '')) {
            try { sessionStorage.setItem('__carlosBogusLoad', url); } catch (e) { /* ignore */ }
          }
          return orig.apply(this, arguments);
        };
        return true;
      } catch (e) {
        return false;
      }
    });
    // The probe below is only evidence if the wrapper was actually installed.
    // Returning early on a missing jQuery.fn.load and then reading the flag
    // would make the assertion unfailable -- it would report "no bogus load"
    // for a page it never instrumented, which is indistinguishable from the
    // guard working.
    assert(
      instrumented,
      'Could not instrument jQuery.fn.load, so the delete-banner probe below would pass '
        + 'without testing anything. jQuery is missing, or no longer exposes fn.load.',
    );

    const [deleteResponse] = await Promise.all([
      managerPage.waitForResponse(
        (r) => r.url().includes('/eform/delEForm') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      deleteLink.click(),
    ]);
    // Reaching here at all is part of the assertion: before the fix, the click
    // submitted immediately with no token and this POST came back 403.
    assertNotBlocked(deleteResponse, 'Deleting an eForm');
    await context.unroute('**/csrfguard*');
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
    // Deterministic pin for the efmFooter guard: the delete used to succeed AND
    // report failure at once — the delete anchor is href="javascript:void(0);"
    // with .contentLink, and the footer's delegated handler AJAX-loaded that
    // literal href (status 0 / "error" -> the "Sorry but there was an error: 0
    // error" banner). The sessionStorage flag set by the load() wrapper above
    // survives the delete's redirect; if it is set, the guard is gone. (A
    // body-text check here is vacuous: the redirect lands on a fresh library
    // page where any transient banner is already gone.)
    const bogusLoad = await managerPage.evaluate(() => {
      try { return sessionStorage.getItem('__carlosBogusLoad'); } catch (e) { return null; }
    });
    assert(
      !bogusLoad,
      `On delete the footer's contentLink handler AJAX-loaded a non-URL href ${JSON.stringify(bogusLoad)} `
        + '— the efmFooter.jspf guard is gone, so a successful delete will paint the '
        + '"Sorry but there was an error: 0 error" banner again.',
    );

    await screenshot(managerPage, config.screenshotDir, 'eform-admin-crud-after-delete');

    // Confirm from a fresh load, not the redirected page, that it is really gone.
    const managerFinal = await openManager(context, config, recorder, 'admin-crud-verify');
    const remaining = await (await libraryRow(managerFinal, formName)).count();
    assert(remaining === 0, `Deleted eForm "${formName}" is still listed in the library`);
    await managerFinal.close();

    // ---- deleted list initialises cleanly -----------------------------------
    // #tblDeletedEforms shipped its header and body rows with no thead/tbody,
    // so DataTables registered zero columns against six-cell rows and aborted
    // init with "Incorrect column count" (tn/18). The warning goes to the
    // console (and, with the default errMode, an alert), so watch both.
    // Reached the way an operator reaches it: the "View Deleted" link on the
    // eForm library, clicked. That link is an a.contentLink inside the AJAX-
    // loaded library, so the list arrives through the same delegated handler
    // the rest of this panel uses -- navigating straight to
    // /eform/efmformmanagerdeleted would test a page the operator never loads
    // that way.
    const deletedPage = await context.newPage();
    wirePage(deletedPage, 'admin-crud-deleted-list', recorder);
    const dataTablesWarnings = [];
    deletedPage.on('console', (message) => {
      if (/DataTables warning/i.test(message.text())) {
        dataTablesWarnings.push(message.text());
      }
    });
    // DataTables' default errMode is 'alert', so the tn/18 warning can arrive
    // as a dialog rather than a console line. wirePage's dismiss handler is
    // registered first and therefore runs first, but dialog.message() stays
    // readable after the dismissal, so recording it here still captures the
    // text.
    deletedPage.on('dialog', (dialog) => {
      if (/DataTables warning/i.test(dialog.message())) {
        dataTablesWarnings.push(dialog.message());
      }
    });
    await gotoApp(deletedPage, config.baseUrl, '/administration');
    await deletedPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await deletedPage.locator('button[data-bs-target="#collapseForms"]').first().click();
    await deletedPage.locator('a.defaultForms').first().waitFor({ state: 'visible', timeout: 20000 });
    await deletedPage.locator('a.defaultForms').first().click();
    await deletedPage.locator('#dynamic-content #eformTbl').waitFor({ state: 'visible', timeout: 30000 });
    await deletedPage.locator('#dynamic-content a.contentLink[href*="efmformmanagerdeleted"]').first().click();
    await deletedPage.locator('#dynamic-content #tblDeletedEforms').waitFor({ state: 'visible', timeout: 30000 });
    await deletedPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    assert(
      dataTablesWarnings.length === 0,
      `The deleted-eForms list raised a DataTables warning: ${dataTablesWarnings.join(' | ')}`,
    );
    // A successful init is the positive half: DataTables adds its wrapper and
    // sorting controls, so their absence means it aborted even if the warning
    // channel changed.
    assert(
      await deletedPage.locator('#dynamic-content #tblDeletedEforms_wrapper').count() > 0,
      'DataTables did not initialise the deleted-eForms table (no #tblDeletedEforms_wrapper), '
        + 'which is what an aborted init looks like.',
    );
    assert(
      await deletedPage.locator('#dynamic-content #tblDeletedEforms thead th').count() === 6,
      'The deleted-eForms table does not expose six header cells inside a thead.',
    );

    // Restore the eForm this run deleted, through the actual Restore control.
    //
    // Restore was dead: it builds its POST form after page load, so CSRFGuard's
    // injector -- which runs off a MutationObserver microtask -- never reached
    // it, and CarlosCsrfGuardFilter answered 403 on every attempt. Nothing
    // exercised the control, so the failure was invisible. Drive it and assert
    // the POST is not rejected AND the form leaves the deleted list, which is
    // the only proof the restore actually took effect.
    // Show every row first. DataTables paginates at 10, detaching off-page rows
    // from the DOM entirely, and each FAILED run of this check leaves another
    // "Playwright Admin CRUD <epoch>" on this list. Sorted ascending by name the
    // newest stamp drifts onto page 2, at which point a row-presence lookup
    // silently finds nothing forever after.
    await deletedPage.evaluate(() => {
      const table = window.jQuery && window.jQuery.fn && window.jQuery.fn.DataTable
        && window.jQuery('#tblDeletedEforms');
      if (table && table.length && window.jQuery.fn.DataTable.isDataTable(table)) {
        table.DataTable().page.len(-1).draw();
      }
    }).catch(() => {});

    const restoreRow = deletedPage.locator(
      `#dynamic-content #tblDeletedEforms tbody tr:has-text("${formName}")`,
    );
    // Unconditional. Guarding this with "if the row is there" is what turns a
    // regression into a silent skip: a delete that hard-deleted instead of
    // setting status='D', or a deleted-list query that returned nothing, would
    // remove the row and the whole Restore step with it, and the run would still
    // print PASS. The row MUST be here -- this check deleted that form itself,
    // moments ago.
    assert(
      await restoreRow.count() === 1,
      `The form this run just deleted ("${formName}") is not on the deleted-eForms list. `
        + 'Delete should soft-delete (status=\'D\') so the form can be restored; if the list is '
        + 'empty or the row is missing, the soft-delete/restore contract is broken.',
    );

    const [restoreResponse] = await Promise.all([
      deletedPage.waitForResponse(
        (r) => r.url().includes('/eform/restoreEForm') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      restoreRow.locator('a.contentLink').first().click(),
    ]);
    assertNotBlocked(restoreResponse, 'Restoring a deleted eForm');
    assert(
      restoreResponse.status() < 400,
      `Restore POST returned HTTP ${restoreResponse.status()}. A 403 here is the CSRF `
        + 'rejection that made this control silently do nothing.',
    );

    // Restore submits a real form, so the assertion below runs against a NEW
    // document. Wait for that document's table to exist before counting:
    // "the row is gone" is a zero-count assertion, so every timing failure --
    // a blank page, a mid-navigation read, a 404 on the forward -- would
    // otherwise satisfy it and report success.
    await deletedPage.waitForURL(/efmformmanagerdeleted/, { timeout: 30000 }).catch(() => {});
    await deletedPage.locator('#tblDeletedEforms').waitFor({ state: 'visible', timeout: 30000 });
    const stillDeleted = await deletedPage
      .locator(`#tblDeletedEforms tbody tr:has-text("${formName}")`).count();
    assert(
      stillDeleted === 0,
      `"${formName}" is still on the deleted-eForms list after Restore, so the restore did `
        + 'not take effect even though the POST was accepted.',
    );
    restoredForm = true;

    await deletedPage.close();

    await managerPage.close();

    // Fail on any uncaught JS error these pages raised. Without this the
    // assertions above can all pass over a visibly broken page -- the
    // deleted-eForms list threw a ReferenceError out of the DataTables draw
    // callback AFTER the rows existed, so every assertion here was already
    // satisfied by the time the page broke.
    assertNoPageErrors(recorder);

    await context.close();

    console.log(
      `PASS eForm admin create/edit/delete round trip (fid ${fid}): edit persisted, `
      + 'delete removed the form and returned to the library'
      + (restoredForm ? ', and Restore put it back' : ''),
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
