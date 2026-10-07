#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Manual installed-package check. The caller restarts Tomcat after this fixture.
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createJasperProbe } = require('./lib/jasper-probe');

async function workflow(s) {
  if (process.env.PLAYWRIGHT_ALLOW_JSP_FIXTURE_WRITE !== 'true') {
    throw new h.SkipCheck('Requires an isolated deployment and PLAYWRIGHT_ALLOW_JSP_FIXTURE_WRITE=true');
  }
  h.assert(process.env.JASPER_WEBAPP_DIR && process.env.JASPER_CLASS_DIR,
    'Set JASPER_WEBAPP_DIR and JASPER_CLASS_DIR to the installed webapp and its org/apache/jsp work directory');
  const classes = fs.realpathSync(process.env.JASPER_CLASS_DIR);
  h.assert(classes !== '/tmp' && !classes.startsWith(`/tmp${path.sep}`), 'JSP compilation must stay outside /tmp');
  const probe = createJasperProbe(process.env.JASPER_WEBAPP_DIR, classes);
  console.log(`  Created owned JSP probe ${probe.filename}`);
  s.cleanup(() => {
    probe.cleanup();
    console.log(`  Removed owned JSP probe ${probe.filename} and its generated files`);
  });
  const page = await s.context.newPage();
  async function visit() {
    const response = await h.gotoApp(page, s.config.baseUrl, `/${probe.filename}`);
    h.assert(response && response.status() === 200, 'Cold JSP did not compile and render successfully');
    h.assert(await page.locator('#probe-token').innerText() === probe.stem, 'Unexpected JSP probe response');
  }
  await s.step('compile a cold JSP with production Jasper settings and retain the application override', async () => {
    await visit();
    h.assert(await page.locator('#probe-version').innerText() === 'one', 'Cold JSP rendered unexpected content');
    const development = await page.locator('#jsp-development').innerText();
    console.log(`  Installed Jasper development=${development}`);
    h.assert(development === 'false', 'Packaged Jasper is still in development mode');
    h.assert(await page.locator('#jsp-mappedfile').innerText() === 'false', 'Application mappedfile override was lost');
  });
  await s.step('serve the compiled JSP after an in-place change until the package restart', async () => {
    probe.replace();
    // Jasper's default modificationTestInterval is four seconds.
    await page.waitForTimeout(6000);
    await visit();
    h.assert(await page.locator('#probe-version').innerText() === 'one', 'Production Jasper recompiled a changed JSP on access');
  });
}
if (require.main === module) runWorkflow('jasper-production', workflow, { openPatient: false });
module.exports = { workflow };
