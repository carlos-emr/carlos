#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Schedule > Dashboard > Common Dashboard > Shared Outcomes popup (#5053).
 * Requires a disposable install configured with these synthetic properties:
 * shared_outcomes_dashboard_url=https://shared-outcomes.invalid/
 * shared_outcomes_dashboard_host=https://shared-outcomes.invalid
 * shared_outcomes_dashboard_key=carlos-5053-fixture
 * shared_outcomes_dashboard_salt=8f3c21ab56789def1234567890abcdef
 * shared_outcomes_dashboard_clinic_id=TEST_CLINIC_5053
 * Set SHARED_OUTCOMES_TEST_FIXTURE=true after configuring them and restarting.
 * All dashboard requests are fulfilled by Playwright; no external service is contacted.
 * The throwaway login, its grants and login side effects are removed by cleanup.
 */
const { pbkdf2Sync, createDecipheriv } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const ORIGIN = 'https://shared-outcomes.invalid';
const SALT = '8f3c21ab56789def1234567890abcdef';

async function workflow(s) {
  const fixture = throwawayLoginFixture({ sql: s.sql, marker: s.marker, provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    if (!fixture.providerNo) return;
    const provider = h.sqlString(fixture.providerNo);
    s.sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${provider}
      AND objectName IN ('_dashboardDisplay','_dashboardCommonLink')`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${provider}`) === '0',
      'Owned dashboard grants were not removed');
  });
  fixture.create();
  const provider = h.sqlString(fixture.providerNo);
  for (const object of ['_dashboardDisplay', '_dashboardCommonLink']) {
    s.sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${provider},${h.sqlString(object)},'r',0,${h.sqlString(s.provider)})`);
  }

  const context = await h.newContext(s.context.browser(), s.config);
  context.on('page', page => h.wireStrictPage(page, 'shared-outcomes', s.recorder));
  let launchUrl;
  await context.route(`${ORIGIN}/**`, async route => {
    launchUrl = new URL(route.request().url());
    await route.fulfill({ contentType: 'text/html', body: `<!doctype html><title>Shared Outcomes test stub</title>
      <p>Shared Outcomes test stub</p><script>
      addEventListener('message', event => {
        if (event.source === opener && event.data.response === 'CONNECT') {
          opener.postMessage({outcomes:{interaction:'CONNECTED',request:{}}}, event.origin);
        }
      });</script>` });
  });
  const schedule = await h.login(context, { ...s.config, testUser: fixture.username }, s.recorder);

  await s.step('Common Dashboard launches an encrypted provider contract without external calls', async () => {
    const link = schedule.locator('a').filter({ hasText: /^Common Dashboard$/ }).first();
    // The link lives in the schedule's Dashboard dropdown.
    await schedule.locator('#dashboardList').hover();
    const dashboardPromise = context.waitForEvent('page', {
      predicate: async page => { const opener = await page.opener(); return opener && opener !== schedule; },
      timeout: 20000,
    });
    const [launcher, dashboard] = await Promise.all([
      s.popup(schedule, link, 'shared-outcomes-launcher'), dashboardPromise,
    ]);
    h.assert(dashboard, 'The dashboard popup did not open');
    await dashboard.getByText('Shared Outcomes test stub', { exact: true }).waitFor();
    await launcher.waitForFunction(() => window.connected === true);
    h.assert(launchUrl && launchUrl.searchParams.get('version') === '1.1', 'The launch protocol version changed');
    h.assert([...launchUrl.searchParams.keys()].sort().join(',') === 'encodedParams,version',
      'The launch URL exposes unexpected plaintext parameters');

    // Spring Encryptors.stronger: PBKDF2-HMAC-SHA1 (1024), AES-256-GCM,
    // 16-byte IV followed by ciphertext and its 16-byte authentication tag.
    const bytes = Buffer.from(launchUrl.searchParams.get('encodedParams').replace(/ /g, '+'), 'base64');
    const key = pbkdf2Sync('carlos-5053-fixture', Buffer.from(SALT, 'hex'), 1024, 32, 'sha1');
    const decipher = createDecipheriv('aes-256-gcm', key, bytes.subarray(0, 16));
    decipher.setAuthTag(bytes.subarray(-16));
    const payload = JSON.parse(Buffer.concat([decipher.update(bytes.subarray(16, -16)), decipher.final()]).toString('utf8'));
    h.assert(payload.user.role === 'provider', 'Dashboard role must be the external protocol value provider');
    h.assert(payload.user.username === fixture.providerNo, 'The launch identifies another user');
    h.assert(payload.clinic.identifier === 'TEST_CLINIC_5053' && payload.clinic.application === 'oscar',
      'The clinic identity contract changed');
  });
}

if (require.main === module) runWorkflow('shared-outcomes-launch', workflow, {
  openPatient: false,
  preflight() {
    if (process.env.SHARED_OUTCOMES_TEST_FIXTURE !== 'true') {
      throw new h.SkipCheck('Configure the documented synthetic Shared Outcomes properties on a disposable install');
    }
  },
});
module.exports = { workflow };
