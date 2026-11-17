#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Month view ▸ provider/group selector: switching "My Group" posts a form built at runtime.
 *
 * User path (as a throwaway login whose My Group is an owned group): Schedule ▸ Month
 * (displaymode=month) ▸ provider selector ▸ "GRP: <second owned group>". selectprovider()
 * in provider/appointmentprovideradminmonth.jsp calls postViaFormPopup(), which creates a
 * <form> with document.createElement, opens the `oscar_appt` popup and submits the form into
 * it at once (provider/providercontrol, dboperation=updatepreference). Unlike the day sheet's
 * postViaForm(), it copies no CSRF-TOKEN into the form, and CSRFGuard's client script cannot
 * tokenise a form submitted synchronously after it is appended. Pattern sweep
 * `csrf-runtime-form` (issue #4130).
 *
 * Asserts: the month view lists both owned groups with the current one selected; choosing the
 * other group sends a POST carrying a CSRF-TOKEN that is accepted (not 403) and the
 * throwaway's ProviderPreference.myGroupNo becomes the chosen group. Expected to fail at the
 * second step while the month view posts without a token.
 * Fixtures: a throwaway login (lib/throwaway-login-fixture.js), two owned groups PW<hex8>/PX<hex8>
 * holding only the throwaway provider, and the throwaway's ProviderPreference (myGroupNo = the
 * first group). Cleanup deletes both groups and the throwaway (which removes its preference
 * rows) and asserts they are gone. The shared test login's preferences are never touched.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, marker, config, recorder } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  const first = 'PW' + marker.slice(-8);
  const second = 'PX' + marker.slice(-8);
  const groups = [first, second].map(h.sqlString).join(',');
  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    sql.execute(`DELETE FROM mygroup WHERE mygroup_no IN (${groups}) AND last_name=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no IN (${groups})`) === '0', 'The owned groups were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no IN (${groups})`) === '0', 'An owned group name is already taken');
  fixture.create();
  const providerNo = h.sqlString(fixture.providerNo);
  sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name) VALUES
      (${h.sqlString(first)},${providerNo},${h.sqlString(marker)},'Throwaway'),
      (${h.sqlString(second)},${providerNo},${h.sqlString(marker)},'Throwaway');
    INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
      lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
      VALUES (${providerNo},8,18,15,${h.sqlString(first)},'deepblue',0,NOW(),20,0)`);
  const myGroup = () => sql.value(`SELECT myGroupNo FROM ProviderPreference WHERE providerNo=${providerNo}`);
  h.assert(myGroup() === first, 'The throwaway preference fixture was not created');

  const context = await h.newContext(s.context.browser(), config);
  s.cleanup(() => context.close().catch(() => {}));
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'month-view-group', recorder));
  const daySheet = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const selector = daySheet.locator('select[name="provider_no"]');

  await s.step('Month lists both owned groups with the current My Group selected', async () => {
    await ui.clickAndAwaitReload(daySheet,
      daySheet.locator('a[href*="displaymode=month&dboperation=searchappointmentmonth"]').first(), { label: 'Month' });
    h.assert(new URL(daySheet.url()).searchParams.get('displaymode') === 'month', 'The Month link did not open the month view');
    await selector.waitFor({ state: 'visible' });
    for (const group of [first, second]) {
      h.assert(await selector.locator(`option[value="_grp_${group}"]`).count() === 1, `The month view does not offer group ${group}`);
    }
    h.assert(await selector.inputValue() === `_grp_${first}`, 'The month view does not preselect the current My Group');
  });

  await s.step('choosing the other group posts with a CSRF token, is accepted and saves My Group', async () => {
    const isPost = r => r.method() === 'POST' && new URL(r.url()).pathname.endsWith('/provider/providercontrol');
    const request = context.waitForEvent('request', { predicate: isPost, timeout: 20000 });
    const response = context.waitForEvent('response', { predicate: r => isPost(r.request()), timeout: 20000 });
    request.catch(() => {});
    response.catch(() => {});
    await selector.selectOption(`_grp_${second}`);
    const sent = new URLSearchParams((await request).postData() || '');
    h.assert(sent.get('mygroup_no') === second && sent.get('dboperation') === 'updatepreference',
      'The group switch did not post the chosen group to updatepreference');
    const status = (await response).status();
    h.assert((sent.get('CSRF-TOKEN') || '').length > 0 && status !== 403,
      `The month-view group switch was refused (HTTP ${status}): postViaFormPopup() builds its form at runtime without a CSRF-TOKEN`);
    h.assert(status === 200, `The group switch answered HTTP ${status}`);
    const deadline = Date.now() + 15000;
    while (myGroup() !== second && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 250));
    h.assert(myGroup() === second, 'The group switch did not save the throwaway\'s My Group');
  });
}

if (require.main === module) runWorkflow('csrf-runtime-form-month-view-group', workflow, { openPatient: false });
module.exports = { workflow };
