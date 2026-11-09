#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * #5530: BC-only pages must return the application's 404 on an Ontario install,
 * before their JSPs query billingmaster / wcb or render the BC statement.
 * These are intentional negative route probes after a real browser login.
 * No data is created or changed. BC view and privilege contracts have unit coverage.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { authenticatedProbe } = require('./lib/authenticated-probe');
const { propertyValue } = require('./lib/download-contract');

// Derive all BC mappings from the shipped configuration so future routes cannot escape coverage.
const path = require('node:path');
const billingConfig = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/classes/struts-billing.xml'), 'utf8');
const ROUTES = [...new Set([...billingConfig.matchAll(/<action\s+name="(billing\/CA\/BC\/[^"*]+)"/g)]
  .map(match => '/' + match[1]).concat(['/quickBillingBC', '/saveQuickBillingBC']))];

/** Require CARLOS's response marker even for HEAD, where no error-page body is available. */
function assertApplicationNotFound(response, method, route) {
  const status = response.status();
  h.assert(status === 404, `${method} ${route} must return 404, got ${status}`);
  h.assert(response.headers()[h.APPLICATION_HEADER] === 'none',
    `${method} ${route} must carry the CARLOS response header ${h.APPLICATION_HEADER}`);
}

async function main() {
  const propertiesFile = process.env.CARLOS_PROPERTIES_FILE || '/etc/carlos-emr/carlos.properties';
  const region = propertyValue(fs.readFileSync(propertiesFile, 'utf8'), 'billregion');
  if (!region || region.trim().toUpperCase() === 'BC') {
    throw new h.SkipCheck('This refusal check requires a configured non-BC install');
  }
  const config = h.readConfig();
  const recorder = h.createRecorder();
  const browser = await h.launchBrowser(config);
  try {
    const context = await h.newContext(browser, config);
    await h.login(context, config, recorder);
    const statuses = [];
    for (const route of ROUTES) {
      for (const method of ['GET', 'HEAD']) {
        // Match the route crawl and attempt to override the deployment's province.
        const target = config.baseUrl + route
          + '?demographic_no=3459&demographicNo=3459&demoNo=3459&billRegion=BC&billregion=BC';
        const response = await authenticatedProbe({ config, context }, target, { method });
        try {
          assertApplicationNotFound(response, method, route);
          const status = response.status();
          statuses.push({ route, method, status });
          console.log(`  ${method} ${route}: ${status}`);
        } finally {
          await response.dispose();
        }
      }
    }
    h.assertStrictPage(recorder);
    return { statuses };
  } finally {
    await browser.close();
  }
}

if (require.main === module) h.runCheck({ name: 'billing-bc-province-guard', run: main });
module.exports = { main, assertApplicationNotFound };
