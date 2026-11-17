#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Deleting a provider's schedule period from the week-setting page.
 * User path: Schedule ▸ Administration ▸ Schedule Management ▸ Schedule Setting (admin iframe,
 * schedule/TemplateSetting) ▸ provider ▸ week setting (schedule/TemplateApplying) ▸ Next
 * (schedule/CreateDate) creates a period; back on the week setting, the period menu ▸ the first
 * period ▸ Delete ▸ confirm() ▸ POST schedule/TemplateApplying delete=1&deldate=all.
 * Asserts the `rschedule` and `scheduledate` rows after each step: Next stores each period and
 * its dates; a cancelled confirm changes nothing; the confirmed Delete marks that period and every
 * date in its range 'D' and leaves the other period and its dates active; the period menu then
 * offers only the other period; and GET and a tokenless POST of the delete are refused without
 * writing. Creating and filling periods end to end is schedule-setting's and
 * schedule-admin-settings' job; here the week-setting Next is only the way the periods are made.
 * Fixtures: a throwaway login (a doctor plus an owned role holding _admin.schedule, as in
 * schedule-admin-settings: the seeded admin role carries _site_access_privacy, which lets a user
 * delete only their own or a shared site's periods), its ProviderPreference and one owned day
 * template; two far-future periods (about 15 and 18 months out) on that provider only. Cleanup
 * deletes every row keyed by the throwaway provider. The provider list is a five-minute cache,
 * so the first step can wait up to about five and a half minutes for the new provider.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {bundleMessage, throwawayLoginFixture} = require('./lib/throwaway-login-fixture');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 20000;
const DELETE_CONFIRM = bundleMessage('schedule.scheduletemplateapplying.msgDeleteConfirmation',
  'Are you sure you want to delete this template setting?');

const {openAdminSection, frameNavigation: navigateFrame} = require('./schedule-admin-settings-playwright-checks');

/** Do something inside an admin iframe that navigates that iframe, wait for it, and refuse an error page. */
async function frameNavigation(admin, frame, action) {
  await navigateFrame(admin, frame, action);
  await h.assertNotErrorPage(frame, 'the schedule setting frame');
}

/**
 * Send a refusal probe from the page itself rather than the context's request client: the session cookie is
 * Secure, which the browser sends to a loopback origin over plain HTTP but the request client does not
 * (issue #4718, as in admin-lot-number-delete). A POST carries the fields form-encoded and no CSRF token.
 * Returns what h.assertRefused reads: status, headers and body. A redirect is not followed (it is not a refusal).
 */
async function probeFromPage(page, url, method, fields) {
  return page.evaluate(async ({target, verb, form}) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the URL and fields are the check's own fixed probe of a same-origin route
    const query = new URLSearchParams(form).toString();
    const response = await fetch(verb === 'GET' ? `${target}?${query}` : target, {
      method: verb,
      credentials: 'same-origin',
      redirect: 'manual',
      ...(verb === 'GET' ? {} : {headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: query}),
    });
    const headers = {};
    response.headers.forEach((value, name) => { headers[name] = value; });
    return {status: response.type === 'opaqueredirect' ? 302 : response.status, headers, body: await response.text()};
  }, {target: url, verb: method, form: fields});
}

async function workflow(s) {
  const {sql, marker, provider, recorder} = s;
  const hex = marker.slice(-16);
  const q = h.sqlString;
  const templateName = `PW${hex.slice(0, 12)}`;
  const role = `PW${hex}`;
  // Two four-week periods, 15 and 18 months out, each starting on the first of its month.
  const [[aStart, aEnd, bStart, bEnd]] = sql.rows(`SELECT a, DATE_ADD(a, INTERVAL 27 DAY), b, DATE_ADD(b, INTERVAL 27 DAY) FROM (
      SELECT DATE_ADD(LAST_DAY(DATE_ADD(CURDATE(), INTERVAL 14 MONTH)), INTERVAL 1 DAY) a,
             DATE_ADD(LAST_DAY(DATE_ADD(CURDATE(), INTERVAL 17 MONTH)), INTERVAL 1 DAY) b) x`);
  h.assert(/^\d{4}-\d{2}-01$/.test(aStart) && /^\d{4}-\d{2}-01$/.test(bStart), 'The database did not compute the far-future periods');

  const fixture = throwawayLoginFixture({sql, marker, provider, testUser: s.config.testUser});
  let owner = null;
  // Cleanups run in reverse: schedule rows go before the throwaway login.
  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    const statements = [
      `DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`,
      `DELETE FROM secRole WHERE role_name=${q(role)}`,
    ];
    if (owner) {
      for (const table of ['scheduletemplate', 'scheduledate', 'rschedule']) statements.push(`DELETE FROM ${table} WHERE provider_no=${q(owner)}`);
      statements.push(`DELETE FROM secUserRole WHERE provider_no=${q(owner)} AND role_name=${q(role)}`);
      statements.push(`DELETE FROM ProviderPreference WHERE providerNo=${q(owner)}`);
    }
    sql.execute(statements.join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)})
      + (SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)})
      + (SELECT COUNT(*) FROM ProviderPreference WHERE providerNo=${q(owner || '')})
      + (SELECT COUNT(*) FROM scheduletemplate WHERE provider_no=${q(owner || '')})
      + (SELECT COUNT(*) FROM scheduledate WHERE provider_no=${q(owner || '')})
      + (SELECT COUNT(*) FROM rschedule WHERE provider_no=${q(owner || '')})`) === '0', 'Owned schedule rows were not removed');
  });
  fixture.create();
  owner = fixture.providerNo;
  h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(owner)} AND role_name='doctor'`) === '1',
    'The test login is not a doctor, so the throwaway has no appointment rights');
  h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'The owned role already exists');
  sql.execute(`DELETE FROM secUserRole WHERE provider_no=${q(owner)} AND role_name<>'doctor';
    INSERT INTO secRole (role_name,description) VALUES (${q(role)},${q(`${marker} schedule admin`)});
    INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no) VALUES (${q(role)},'_admin.schedule','x',0,${q(provider)});
    INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate)
      SELECT provider_no,${q(role)},orgcd,1,NOW() FROM secUserRole WHERE provider_no=${q(owner)} AND role_name='doctor';
    INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
      lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
      VALUES (${q(owner)},8,18,15,${q(owner)},'deepblue',0,NOW(),20,0);
    INSERT INTO scheduletemplate (provider_no,name,summary,timecode) VALUES (${q(owner)},${q(templateName)},${q(`${marker} day`)},${q('_'.repeat(96))})`);
  h.assert(sql.value(`SELECT COUNT(*) FROM scheduletemplate WHERE provider_no=${q(owner)}`) === '1', 'The owned day template was not seeded');

  const periods = () => sql.rows(`SELECT sdate,edate,status FROM rschedule WHERE provider_no=${q(owner)} ORDER BY sdate,id`)
    .map(row => row.join(' ')).join('; ');
  const dates = (from, to, status) => sql.value(`SELECT COUNT(*) FROM scheduledate WHERE provider_no=${q(owner)}
    AND sdate BETWEEN ${q(from)} AND ${q(to)} AND status=${q(status)}`);

  // ---- administration shell, as the throwaway ---------------------------------------------------
  const context = await h.newContext(s.context.browser(), s.config);
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', page => h.wireStrictPage(page, 'schedule-period-delete', recorder));
  const schedule = await h.login(context, {...s.config, testUser: fixture.username}, recorder, {label: 'throwaway-login'});
  const {page: admin} = await ui.clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder, label: 'administration', timeout: TIMEOUT});
  const scheduleSetting = () => openAdminSection(admin, '/schedule/TemplateSetting', 'select[name="provider_no"]');

  let frame;
  async function openWeekSetting() {
    frame = await scheduleSetting();
    await frameNavigation(admin, frame, () => frame.locator('select[name="provider_no"]').selectOption(owner));
    h.assert(/\/schedule\/TemplateApplying/.test(frame.url()), 'Selecting the provider did not open the week setting step');
  }
  async function createPeriod(start, end) {
    const [sy, sm, sd] = start.split('-');
    const [ey, em, ed] = end.split('-');
    for (const [name, value] of [['syear', sy], ['smonth', sm], ['sday', sd], ['eyear', ey], ['emonth', em], ['eday', ed]]) {
      await frame.locator(`input[name="${name}"]`).fill(String(Number(value)));
    }
    await frame.locator('select[name="mytemplate"]').selectOption(templateName);
    await frame.locator('input[name="checkmon"]').check();
    await frame.locator('input[name="monto1"]').click();
    await frameNavigation(admin, frame, () => frame.locator('input[type="submit"]').first().click());
    h.assert(/\/schedule\/CreateDate/.test(frame.url()), 'Next did not advance to the calendar step');
  }
  const periodOptions = async () => (await frame.locator('select[name="select"] option').evaluateAll(
    options => options.map(option => option.value))).filter(value => value);

  await s.step('the week setting\'s Next stores two periods for the owned provider, each with its Monday dates', async () => {
    // ProviderDao.getActiveProviders() is a five-minute Spring cache evicted only by provider
    // admin writes, so a provider row seeded by SQL appears once the entry expires.
    const deadline = Date.now() + 330000;
    frame = await scheduleSetting();
    while (await frame.locator(`select[name="provider_no"] option[value="${owner}"]`).count() === 0) {
      h.assert(Date.now() < deadline, 'Schedule Setting never offered the owned provider');
      await new Promise(resolve => setTimeout(resolve, 15000));
      frame = await scheduleSetting();
    }
    await frameNavigation(admin, frame, () => frame.locator('select[name="provider_no"]').selectOption(owner));
    h.assert(/\/schedule\/TemplateApplying/.test(frame.url()), 'Selecting the provider did not open the week setting step');
    await createPeriod(aStart, aEnd);
    await openWeekSetting();
    await createPeriod(bStart, bEnd);
    h.assert(periods() === `${aStart} ${aEnd} A; ${bStart} ${bEnd} A`, `The two periods were not stored as active: ${periods()}`);
    h.assert(dates(aStart, aEnd, 'A') === '4' && dates(bStart, bEnd, 'A') === '4',
      `Each four-week period should carry four active Monday dates (first ${dates(aStart, aEnd, 'A')}, second ${dates(bStart, bEnd, 'A')})`);
  });

  async function pressDelete(accept) {
    const button = frame.locator('input[type="button"][name="command"][onclick*="onBtnDelete"]');
    h.assert(await button.count() === 1, 'The week setting offers no Delete next to the period menu');
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      if (accept) await frameNavigation(admin, frame, () => button.click());
      else await button.click();
    }, {accept});
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === DELETE_CONFIRM,
      `Delete did not ask for confirmation (${dialogs.map(dialog => dialog.type).join(', ') || 'no dialog'})`);
  }

  await s.step('the period menu ▸ the first period ▸ Delete ▸ Cancel changes nothing', async () => {
    await openWeekSetting();
    h.assert(JSON.stringify(await periodOptions()) === JSON.stringify([aStart, bStart]),
      `The period menu does not offer the two periods: ${JSON.stringify(await periodOptions())}`);
    // The menu may already show the first period; choosing the shown option fires no change.
    const menu = () => frame.locator('select[name="select"]');
    if (await menu().inputValue() !== aStart) {
      await frameNavigation(admin, frame, () => menu().selectOption(aStart));
      h.assert(new URL(frame.url()).searchParams.get('sdate') === aStart, 'Choosing the first period did not reload the week setting for it');
    }
    h.assert(await menu().inputValue() === aStart, 'The period menu does not show the first period');
    await pressDelete(false);
    h.assert(/\/schedule\/TemplateApplying/.test(frame.url()) && await menu().inputValue() === aStart,
      'A cancelled Delete left the first period\'s page');
    h.assert(periods() === `${aStart} ${aEnd} A; ${bStart} ${bEnd} A`, `A cancelled Delete changed a period: ${periods()}`);
    h.assert(dates(aStart, aEnd, 'A') === '4' && dates(bStart, bEnd, 'A') === '4', 'A cancelled Delete changed a scheduled date');
  });

  await s.step('Delete ▸ OK marks the first period and its dates deleted, leaves the second active, and drops it from the menu', async () => {
    await pressDelete(true);
    h.assert(/\/schedule\/TemplateApplying/.test(frame.url()) && !new URL(frame.url()).searchParams.has('delete'),
      'The delete did not return to the week setting page');
    h.assert(periods() === `${aStart} ${aEnd} D; ${bStart} ${bEnd} A`, `Delete did not mark only the first period deleted: ${periods()}`);
    h.assert(dates(aStart, aEnd, 'D') === '4' && dates(aStart, aEnd, 'A') === '0',
      `Delete did not mark the first period's dates deleted (active ${dates(aStart, aEnd, 'A')}, deleted ${dates(aStart, aEnd, 'D')})`);
    h.assert(dates(bStart, bEnd, 'A') === '4', 'Delete changed a date of the second period');
    h.assert(JSON.stringify(await periodOptions()) === JSON.stringify([bStart]),
      `After the delete the period menu offers ${JSON.stringify(await periodOptions())}, not only the second period`);
  });

  await s.step('GET and a tokenless POST of the delete are refused and change nothing', async () => {
    const where = `provider_no=${q(owner)} AND sdate=${q(bStart)} AND status='A'`;
    const fields = {provider_no: owner, provider_name: marker, sdate: bStart, delete: '1', deldate: 'all'};
    const route = h.appUrl(s.config.baseUrl, '/schedule/TemplateApplying');
    await h.assertRefused(s, {label: 'GET schedule/TemplateApplying delete=1', table: 'rschedule', where, before: '1',
      response: await probeFromPage(admin, route, 'GET', fields)});
    await h.assertRefused(s, {label: 'POST schedule/TemplateApplying delete=1 without a CSRF token', table: 'rschedule', where, before: '1',
      response: await probeFromPage(admin, route, 'POST', fields)});
    h.assert(dates(bStart, bEnd, 'A') === '4', 'A refused delete changed a date of the second period');
  });
}

if (require.main === module) runWorkflow('schedule-period-delete', workflow, {openPatient: false});
module.exports = {workflow};
