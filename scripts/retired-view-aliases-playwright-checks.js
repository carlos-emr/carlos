#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */

// Issue #3665 findings 2 and 4: two unused view aliases must be absent, while
// the shared JSP fragment and the working immunization configuration stay.
// Read-only: opens the existing template list and Add New editor, never saves.
// Direct requests below are intentional negative route checks, not UI coverage.
const {
  assert, assertNotErrorPage, assertStrictPage, createRecorder, launchBrowser,
  login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');

const RETIRED = [
  '/admin/ViewDbConnection',
  '/encounter/immunization/config/ViewImmunizationSetDisplay',
];

async function main() {
  const config = readConfig();
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const page = await login(context, config, recorder);
    const statuses = [];
    // Check both read verbs so HEAD cannot retain the removed view mapping.
    // A 403/redirect does not prove removal; only the application's 404 does.
    for (const route of RETIRED) {
      for (const method of ['GET', 'HEAD']) {
        const response = await context.request.fetch(config.baseUrl + route,
          { method, maxRedirects: 0 });
        statuses.push({ route, method, status: response.status() });
        await response.dispose();
      }
    }

    const response = await page.goto(config.baseUrl + '/encounter/immunization/config/initConfig',
      { waitUntil: 'domcontentloaded' });
    assert(response.status() === 200, 'Immunization template management did not render');
    await assertNotErrorPage(page, 'immunization template management');
    const addNew = page.locator('input[onclick*="ViewCreateImmunizationSetInit"]');
    assert(await addNew.count() === 1, 'Immunization template management lost Add New');
    await clickAndAwaitReload(page, addNew, { label: 'Add New immunization template', timeout: 20000 });
    assert(new URL(page.url()).pathname.endsWith('/ViewCreateImmunizationSetInit'),
      'Add New did not reach the template editor');
    assert(await page.locator('#setName').count() === 1, 'Template editor lost the name field');
    await assertNotErrorPage(page, 'immunization template editor');
    assertStrictPage(recorder);
    console.log('  retained immunization management and Add New editor render');
    for (const result of statuses) console.log(`  ${result.method} ${result.route}: ${result.status}`);
    assert(statuses.every(result => result.status === 404),
      'Retired view aliases must return 404 for GET and HEAD');
    return { statuses, templateEditor: true };
  } finally {
    await browser.close();
  }
}

if (require.main === module) runCheck({ name: 'retired-view-aliases', run: main });
module.exports = { main };
