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
 * Browser check that forms CSRFGuard's client script cannot reach still post
 * WITH the session token, and are accepted (issue #4130).
 *
 * CSRFGuard injects CSRF-TOKEN into a page's forms when the page loads, and its
 * MutationObserver re-injects only when an inserted node IS a <form>, after the
 * current task. Three CARLOS patterns fall through, and each one became a 403
 * from CarlosCsrfGuardFilter that the user saw as a button doing nothing:
 *
 *   1. PANEL FORMS. The Administration shell loads its sections with
 *      $("#dynamic-content").load(), which inserts containers whose
 *      DESCENDANTS are the forms. Driven here: Administration > eForm Groups >
 *      delete a group, through the shell's own confirm modal.
 *   2. RUNTIME-BUILT FORMS. document.createElement('form') + submit() in one
 *      click handler leaves before the observer runs. Driven here: Report by
 *      Template > Delete Template, which now goes through carlosPostForm().
 *   3. NUMERICALLY-NAMED CONTROLS. The dx code search named each description
 *      input with its bare dx code; CSRFGuard's form.elements[key] lookup then
 *      resolved the name as an index and threw, stopping the page-load pass.
 *      Driven here: Ontario dx code search > Update, resubmitting the row's
 *      existing description so reference data is unchanged.
 *
 * WHAT IS ASSERTED for each: the request that reached the server carried a
 * non-empty CSRF-TOKEN, the server did not answer 403, the database shows the
 * effect (the group and template rows are gone; the dx description is
 * unchanged apart from trailing padding), and the pages raised no JavaScript error.
 *
 * FIXTURES: one eForm group and one report template, both uniquely named and
 * created by SQL, removed by the workflow under test and, if the run fails
 * first, by cleanup. The dx row's description is snapshotted and restored if
 * it ever differs.
 *
 * Defaults are for the local devcontainer:
 *   MYSQL_PASSWORD=... npm run test:csrf-runtime-forms-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   CSRF_FORMS_DX_SEARCH=diabetes    description text to search the dx codes for
 *   CSRF_FORMS_TIMEOUT_MS=20000      per-step allowance
 */

const {
  assert, assertStrictPage, createRecorder, createSqlRunner, gotoApp, launchBrowser, login, newContext,
  readConfig, runCheck, sqlString, withExpectedDialogs, wireStrictPage,
} = require('./lib/playwright-harness');

const TOKEN_FIELD = /(?:^|&)CSRF-TOKEN=([^&]+)/;

/** The CSRF-TOKEN a request carried, in its body or its header; '' if none. */
async function tokenCarried(request) {
  const body = request.postData() || '';
  const match = TOKEN_FIELD.exec(body);
  if (match && decodeURIComponent(match[1]).trim()) {
    return decodeURIComponent(match[1]);
  }
  const headers = await request.allHeaders();
  return (headers['csrf-token'] || '').trim();
}

/** Asserts the POST carried a token and was not refused. */
async function assertAccepted(label, request) {
  const token = await tokenCarried(request);
  assert(token, `${label}: the POST reached the server with no CSRF-TOKEN, so CarlosCsrfGuardFilter refuses it (#4130)`);
  const response = await request.response();
  const status = response ? response.status() : 0;
  assert(status !== 403, `${label}: the POST answered 403; CSRFGuard (or the WAF) refused it`);
  assert(status > 0 && status < 400, `${label}: the POST answered HTTP ${status}`);
  console.log(`  ${label}: POST carried CSRF-TOKEN and answered ${status}`);
}

/** Every POST form inside `scope` carries a populated CSRF-TOKEN input. */
async function assertFormsTokenised(label, page, scope) {
  const report = await page.evaluate((selector) => Array.from( // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed selector constant
    // The scope may itself be the form (#diagcode is), or contain forms.
    document.querySelectorAll(`${selector}, ${selector} form`),
  ).filter((element) => element.tagName === 'FORM').filter((form) => (form.getAttribute('method') || '').toLowerCase() === 'post')
    .map((form) => ({
      action: form.getAttribute('action') || '',
      token: Array.from(form.querySelectorAll('input[name="CSRF-TOKEN"]')).map((input) => input.value).find(Boolean) || '',
    })), scope);
  assert(report.length > 0, `${label}: no POST form was found in ${scope}; the step did not reach the page it tests`);
  const missing = report.filter((form) => !form.token).map((form) => form.action.replace(/\?.*$/, ''));
  assert(missing.length === 0,
    `${label}: ${missing.length} of ${report.length} POST form(s) have no CSRF token: ${missing.join(', ')}`);
}

async function deleteEFormGroupInAdminPanel(context, config, recorder, sql, timeout) {
  const groupName = `PW4130 ${Date.now().toString(36)}`.slice(0, 20);
  const fid = sql.value('SELECT fid FROM eform WHERE status = 1 ORDER BY fid LIMIT 1');
  assert(fid, 'eform-groups: the eForm library is empty, so no group can be built to delete');
  let page = null;
  // The insert sits inside the try so a failure part-way through it, or in
  // opening the page, still runs the cleanup below.
  try {
    sql.execute(`INSERT INTO eform_groups (fid, group_name) VALUES (${Number(fid)}, ${sqlString(groupName)})`);
    page = await context.newPage();
    wireStrictPage(page, 'admin-eform-groups', recorder);
    await gotoApp(page, config.baseUrl, '/administration');
    await page.waitForLoadState('networkidle', { timeout }).catch(() => {});
    const formsSection = page.locator('button[data-bs-target="#collapseForms"]').first();
    await formsSection.waitFor({ state: 'visible', timeout });
    await formsSection.click();
    const groupsLink = page.locator('a.defaultFormsGroups').first();
    await groupsLink.waitFor({ state: 'visible', timeout });
    await groupsLink.click();

    // The group list arrived by .load(); open our group the way an operator
    // does, which is a second .load() through efmFooter's delegated handler.
    const groupLink = page.locator('#dynamic-content a', { hasText: groupName }).first();
    await groupLink.waitFor({ state: 'visible', timeout });
    await groupLink.click();
    const deleteGroup = page.locator(
      '#dynamic-content form[action$="/eforms/delGroup"]',
      { has: page.locator(`input[name="group_name"][value="${groupName}"]`) },
    ).first();
    await deleteGroup.waitFor({ state: 'attached', timeout });
    await page.locator('#dynamic-content form[action$="/eforms/removeFromGroup"]').first()
      .waitFor({ state: 'attached', timeout });

    // The injected panel's forms, before anything is clicked. This is the
    // assertion the defect fails: they arrived nested in a container.
    await page.waitForFunction(() => Array.from(document.querySelectorAll('#dynamic-content form[method="post"]'))
      .every((form) => Array.from(form.querySelectorAll('input[name="CSRF-TOKEN"]')).some((input) => input.value)),
    null, { timeout }).catch(() => {});
    await assertFormsTokenised('eform-groups', page, '#dynamic-content');

    // efmFooter.jspf binds the confirm modal with a delegated handler once the
    // panel's scripts have run; clicking before that opens nothing (seen on the
    // first request after a restart, while the JSPs compile).
    await page.waitForFunction(() => window.confirmModalInitialized === true, null, { timeout });
    await deleteGroup.locator('a[data-confirm]').first().click();
    const confirm = page.locator('#confirmModal #dataConfirmed');
    await confirm.waitFor({ state: 'visible', timeout });
    const [request] = await Promise.all([
      page.waitForRequest((candidate) => candidate.method() === 'POST' && /\/eforms\/delGroup(\?|$)/.test(candidate.url()),
        { timeout }),
      confirm.click(),
    ]);
    await request.response();
    await assertAccepted('eform-groups delete', request);
    await page.waitForLoadState('load', { timeout }).catch(() => {});

    const remaining = Number(sql.value(`SELECT COUNT(*) FROM eform_groups WHERE group_name = ${sqlString(groupName)}`));
    assert(remaining === 0, `eform-groups: the group still has ${remaining} row(s) after a delete the server accepted`);
  } finally {
    sql.execute(`DELETE FROM eform_groups WHERE group_name = ${sqlString(groupName)}`);
    if (page) {
      await page.close().catch(() => {});
    }
  }
}

async function deleteReportTemplate(context, config, recorder, sql, timeout) {
  const title = `PW4130 template ${Date.now().toString(36)}`;
  const xml = `<report title="${title}" description="CSRF runtime-form probe" active="1">`
    + '<query>SELECT 1 AS probe</query></report>';
  let page = null;
  // As above: the fixture is created inside the try so it is always removed.
  try {
    sql.execute('INSERT INTO reportTemplates (templatetitle, templatedescription, templatesql, templatexml, active) '
      + `VALUES (${sqlString(title)}, 'CSRF runtime-form probe', 'SELECT 1 AS probe', ${sqlString(xml)}, 1)`);
    const templateId = sql.value(`SELECT templateid FROM reportTemplates WHERE templatetitle = ${sqlString(title)}`);
    assert(templateId, 'report-by-template: the fixture template was not created');

    page = await context.newPage();
    wireStrictPage(page, 'report-template', recorder);
    await gotoApp(page, config.baseUrl,
      `/oscarReport/reportByTemplate/ViewReportConfiguration?templateid=${encodeURIComponent(templateId)}`);
    const deleteLink = page.locator('#optionsDiv a', { hasText: /Delete Template/i }).first();
    await deleteLink.waitFor({ state: 'visible', timeout });

    let request;
    const dialogs = await withExpectedDialogs(page, async () => {
      [request] = await Promise.all([
        page.waitForRequest((candidate) => candidate.method() === 'POST'
          && /\/addEditTemplatesAction(\?|$)/.test(candidate.url()), { timeout }),
        deleteLink.click(),
      ]);
      await request.response();
    });
    assert(dialogs.length === 1 && dialogs[0].type === 'confirm',
      `report-by-template: expected one delete confirmation, saw ${dialogs.length} dialog(s)`);
    assert(/(?:^|&)action=delete(?:&|$)/.test(request.postData() || ''),
      'report-by-template: the POST did not carry action=delete');
    await assertAccepted('report-by-template delete', request);

    const remaining = Number(sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templateid = ${Number(templateId)}`));
    assert(remaining === 0, 'report-by-template: the template is still there after a delete the server accepted');
  } finally {
    sql.execute(`DELETE FROM reportTemplates WHERE templatetitle = ${sqlString(title)}`);
    if (page) {
      await page.close().catch(() => {});
    }
  }
}

async function resubmitDxDescription(context, config, recorder, sql, timeout) {
  const search = process.env.CSRF_FORMS_DX_SEARCH || 'diabetes';
  const page = await context.newPage();
  wireStrictPage(page, 'dx-search', recorder);
  let code = '';
  let before = null;
  try {
    await gotoApp(page, config.baseUrl,
      `/billing/CA/ON/ViewBillingDigSearch?coderange=&codedesc=${encodeURIComponent(search)}`);
    await page.locator('#diagcode input[name^="desc_"]').first().waitFor({ state: 'visible', timeout });
    // BillingDiagUpdate2Action takes the code from the last three characters of
    // the Update button's value, so only a three-character code round-trips.
    // Pick the first row whose desc_<code> input carries one (ICD-9 codes such
    // as 2740 also appear in results).
    const names = await page.locator('#diagcode input[name^="desc_"]').evaluateAll(
      (inputs) => inputs.map((input) => input.getAttribute('name')),
    );
    const name = names.find((candidate) => /^desc_[0-9A-Z]{3}$/i.test(candidate || ''));
    assert(name, `dx-search: no result row has a three-character dx code (searched for "${search}")`);
    code = name.slice('desc_'.length);
    const row = page.locator('#diagcode tbody tr', { has: page.locator(`input[name="${name}"]`) }).first();
    const updateButton = row.locator('input[type="submit"][name="update"]');
    before = sql.value(`SELECT description FROM diagnosticcode WHERE diagnostic_code = ${sqlString(code)} LIMIT 1`);

    // The page-load pass used to throw on this form; now it is tokenised.
    await assertFormsTokenised('dx-search', page, '#diagcode');
    const [request] = await Promise.all([
      page.waitForRequest((candidate) => candidate.method() === 'POST'
        && /\/billing\/CA\/ON\/BillingDigUpdate(\?|$)/.test(candidate.url()), { timeout }),
      updateButton.click(),
    ]);
    await request.response();
    await assertAccepted('dx-search update', request);
    assert((request.postData() || '').includes(`desc_${code}=`),
      `dx-search: the update did not post desc_${code}; the input is still named with the bare code`);

    const after = sql.value(`SELECT description FROM diagnosticcode WHERE diagnostic_code = ${sqlString(code)} LIMIT 1`);
    // Trailing whitespace is not a change: legacy rows are space-padded, and the
    // round trip through the form drops the padding (it did before #4130 too).
    // The cleanup below still restores the row byte-for-byte.
    assert(String(after).trimEnd() === String(before).trimEnd(),
      `dx-search: resubmitting the unchanged description changed the stored text for ${code}`);
  } finally {
    if (code && before !== null) {
      const current = sql.value(`SELECT description FROM diagnosticcode WHERE diagnostic_code = ${sqlString(code)} LIMIT 1`);
      if (current !== before) {
        sql.execute(`UPDATE diagnosticcode SET description = ${sqlString(before)} WHERE diagnostic_code = ${sqlString(code)}`);
      }
    }
    await page.close().catch(() => {});
  }
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const timeout = Number(process.env.CSRF_FORMS_TIMEOUT_MS || '20000');
  const sql = createSqlRunner(config.mysql);
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    await login(context, config, recorder);

    await deleteEFormGroupInAdminPanel(context, config, recorder, sql, timeout);
    await deleteReportTemplate(context, config, recorder, sql, timeout);
    await resubmitDxDescription(context, config, recorder, sql, timeout);

    assertStrictPage(recorder, ['admin-eform-groups', 'report-template', 'dx-search']);
    return { steps: 3 };
  } finally {
    await browser.close().catch(() => {});
    sql.dispose();
  }
}

if (require.main === module) {
  runCheck({ name: 'csrf-runtime-forms', run: main });
}

module.exports = { assertAccepted, assertFormsTokenised, main, tokenCarried };
