#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */

// Issue #3665 finding 5. Keep real schedule/master/chart navigation working,
// retire the unreachable legacy history view, and reject arbitrary JSP/resource
// inclusion through providercontrol. Negative probes use an appointment-read
// account with message-read access for the login navbar, but no clinical/admin rights.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

async function workflow(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  await s.step('schedule, master record, chart and Month navigation remain available', async () => {
    const chart = await s.chart();
    for (const page of [s.schedule, s.master, chart]) await h.assertNotErrorPage(page, 'retained clinical navigation');
    const month = s.schedule.locator('a[href*="displaymode=month&dboperation=searchappointmentmonth"]').first();
    h.assert(await month.count() === 1, 'Schedule Month navigation is missing');
    await ui.clickAndAwaitReload(s.schedule, month, { label: 'Month view' });
    await h.assertNotErrorPage(s.schedule, 'Month view');
    h.assert(new URL(s.schedule.url()).searchParams.get('displaymode') === 'month', 'Month navigation did not reach the month view');
  });

  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: s.config.testUser });
  const role = `${marker}-ap`;
  let roleOwned = false;
  s.cleanup(() => {
    fixture.cleanup();
    if (roleOwned) {
      sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)} AND objectName IN ('_appointment','_msg')`);
      h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === '0',
        'Unexpected role grants retained for investigation');
      sql.execute(`DELETE FROM secRole WHERE role_name=${q(role)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'Owned audit role remains');
    }
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'Owned role already exists');
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === '0', 'Owned role grants already exist');
  roleOwned = true; // Register ownership before either INSERT can partially succeed.
  sql.execute(`INSERT INTO secRole(role_name,description) VALUES(${q(role)},'Owned schedule-only history audit');
    INSERT INTO secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)
    VALUES(${q(role)},'_appointment','r',0,${q(provider)}),(${q(role)},'_msg','r',0,${q(provider)})`);
  fixture.create({ roleNames: [role], expiresTomorrow: true });
  h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(fixture.providerNo)} AND role_name<>${q(role)}`) === '0',
    'Audit account inherited other roles');
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}
    AND (objectName NOT IN ('_appointment','_msg') OR privilege<>'r')`) === '0', 'Audit role has unexpected privileges');

  const restricted = await h.newContext(s.context.browser(), s.config);
  s.cleanup(() => restricted.close());
  const schedule = await h.login(restricted, { ...s.config, testUser: fixture.username }, s.recorder,
    { label: 'schedule-only history audit' });
  await h.assertNotErrorPage(schedule, 'schedule-only login');
  const token = await ui.csrfTokenPresent(schedule);
  const targets = [
    { label: 'history route', path: '/provider/ViewProviderEncounterHistory', params: { demographic_no: s.patient } },
    { label: 'history dispatcher mode', path: '/provider/providercontrol', params: { displaymode: 'encounterhistory', demographic_no: s.patient } },
    { label: 'raw history JSP', path: '/provider/providercontrol', params: { displaymode: 'vary',
      displaymodevariable: '/WEB-INF/jsp/provider/providerencounterhistory.jsp', demographic_no: s.patient } },
    { label: 'protected translation bundle', path: '/provider/providercontrol', params: { displaymode: 'vary',
      displaymodevariable: '/WEB-INF/classes/oscarResources_en.properties' } },
  ];
  await s.step('retired history and arbitrary-include requests return 404 for GET, HEAD and CSRF-valid POST', async () => {
    const results = [];
    for (const target of targets) {
      for (const method of ['GET', 'HEAD', 'POST']) {
        const response = await restricted.request.fetch(h.appUrl(s.config.baseUrl, target.path), {
          method, params: target.params, maxRedirects: 0,
          ...(method === 'POST' ? { form: { 'CSRF-TOKEN': token }, headers: { 'CSRF-TOKEN': token } } : {}),
        });
        const body = await response.text();
        results.push({ target: target.label, method, status: response.status(),
          exposedResourceContent: body.includes('global.about=') || body.includes('global.license=') });
        await response.dispose();
      }
    }
    console.log('  retired history probes:', JSON.stringify(results));
    h.assert(results.every(result => result.status === 404 && !result.exposedResourceContent),
      'Retired history and arbitrary includes must return 404 without protected-resource content');
  });
  await s.step('an unknown dispatcher mode is rejected and the authenticated schedule still works', async () => {
    const response = await restricted.request.get(h.appUrl(s.config.baseUrl, '/provider/providercontrol'), {
      params: { displaymode: marker }, maxRedirects: 0,
    });
    const status = response.status();
    await response.dispose();
    h.assert(status === 404, `Unknown dispatcher mode answered ${status}, expected 404`);
    const control = await schedule.reload({ waitUntil: 'domcontentloaded' });
    h.assert(control.status() === 200, 'The restricted schedule session stopped working');
    await h.assertNotErrorPage(schedule, 'schedule after rejected includes');
  });
}

if (require.main === module) runWorkflow('retired-provider-history', workflow);
module.exports = { workflow };
