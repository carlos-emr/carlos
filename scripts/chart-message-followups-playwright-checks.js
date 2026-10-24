#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Issue #4811: legacy chart tabs, independent inbox filters and private attachments on a packaged install.
 * Owns one FAKE patient, an orphaned appointment and two messages. Cleanup deletes only those messages, then the owned patient.
 * The legacy owner action is exercised before entering the modern chart, which selects its layout.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const q = h.sqlString;

async function workflow(s) {
  const {context, sql, patient, marker, provider, config} = s;
  const url = path => h.appUrl(config.baseUrl, path);
  const ids = [];
  s.cleanup(() => {
    sql.execute(`DELETE FROM casemgmt_cpp WHERE demographic_no=${patient}`);
    sql.execute(`DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient}`) === '0',
      'Owned CPP remains after cleanup');
    h.assert(sql.value(`SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient}`) === '0',
      'Owned legacy chart remains after cleanup');
  });
  s.cleanup(() => {
    if (!ids.length) return;
    sql.execute(`DELETE FROM messagelisttbl WHERE message IN (${ids.join(',')});
      DELETE FROM msgDemoMap WHERE messageID IN (${ids.join(',')});
      DELETE FROM messagetbl WHERE messageid IN (${ids.join(',')}) AND thesubject LIKE ${q(marker + '%')}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${ids.join(',')})`) === '0',
      'Owned follow-up messages remain after cleanup');
  });

  await s.step('an orphaned appointment does not prevent the day sheet from rendering', async () => {
    const missingPatient = Number(sql.value('SELECT MAX(demographic_no) FROM demographic')) + 100;
    const date = sql.value('SELECT CURDATE()');
    const [year, month, day] = date.split('-');
    const appointment = h.insertId(sql, `INSERT INTO appointment(provider_no,appointment_date,start_time,end_time,
        name,demographic_no,notes,reason,location,resources,type,style,billing,status,createdatetime,creator)
      VALUES(${q(provider)},${q(date)},'09:00:00','09:15:00',${q(marker + ' orphan')},${missingPatient},
        '','','','',NULL,'','','t',NOW(),${q(provider)})`, 'orphan appointment');
    s.cleanup(() => {
      sql.execute(`DELETE FROM appointment WHERE appointment_no=${appointment} AND name=${q(marker + ' orphan')}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointment}`) === '0',
        'Owned orphan appointment remains after cleanup');
    });
    const response = await s.schedule.goto(url(`/provider/providercontrol?year=${year}&month=${month}&day=${day}`
      + `&view=1&curProvider=${encodeURIComponent(provider)}&displaymode=day&dboperation=searchappointmentday`));
    const appointmentLink = s.schedule.locator(`a[onclick*="/appointment/editappointment?appointment_no=${appointment}&"]`).first();
    h.assert(response.status() === 200, `The orphan appointment day sheet returned HTTP ${response.status()}`);
    h.assert(await appointmentLink.count() === 1,
      'The orphan appointment is absent from its provider and date on the day sheet');
    h.assert((await appointmentLink.getAttribute('data-title-full') || '').includes(marker + ' orphan'),
      'The orphan appointment lost its saved name in the full appointment tooltip');
  });

  await s.step('the legacy chart owner renders its current issues and populated client history tabs', async () => {
    const originalReminder = `${marker} original reminder`;
    sql.execute(`INSERT INTO casemgmt_cpp(demographic_no,provider_no,reminders,ongoingConcerns)
      VALUES(${patient},${q(provider)},${q(originalReminder)},'FAKE-PW concern')`);
    const page = await context.newPage();
    for (const [tab, text] of [['Current Issues', 'Issue Filter'], ['Client History', 'Social History']]) {
      const response = tab === 'Current Issues'
        ? await page.goto(url(`/CaseManagementView?method=view&demographicNo=${patient}&tab=${encodeURIComponent(tab)}`))
        : (await Promise.all([page.waitForNavigation(), page.getByText(tab, {exact: true}).click()]))[0];
      h.assert(response.status() === 200, `${tab} returned HTTP ${response.status()}`);
      await h.assertNotErrorPage(page, tab);
      h.assert((await page.locator('body').innerText()).includes(text), `${tab} failed to render its fragment`);
      h.assert(await page.locator('input[name="demographicNo"]').first().inputValue() === patient,
        `${tab} lost its patient context`);
    }
    const history = `${marker} patient history`;
    await page.getByLabel('Social History', {exact: true}).fill(history);
    await page.getByLabel('Other Support Systems', {exact: true}).fill(`${marker} support`);
    await Promise.all([page.waitForNavigation(), page.locator('input[type="submit"][value="Save"]').click()]);
    h.assert(sql.value(`SELECT socialHistory FROM casemgmt_cpp WHERE demographic_no=${patient}`) === history,
      'The legacy CPP form did not save the named patient history');
    h.assert(await page.getByLabel('Social History', {exact: true}).inputValue() === history,
      'The saved history was lost on redisplay');
    h.assert(sql.value(`SELECT reminders FROM casemgmt_cpp WHERE demographic_no=${patient}`) === originalReminder,
      'Saving history erased the reminder from another tab');
    await Promise.all([page.waitForNavigation(), page.getByText('Reminders', {exact: true}).first().click()]);
    h.assert(await page.getByLabel('Reminders', {exact: true}).inputValue() === originalReminder,
      'The reminder tab lost its saved text');
    await page.getByLabel('Reminders', {exact: true}).fill(`${marker} updated reminder`);
    await Promise.all([page.waitForNavigation(), page.locator('input[type="submit"][value="Save"]').click()]);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient}
      AND socialHistory=${q(history)} AND otherSupportSystems=${q(marker + ' support')}
      AND reminders=${q(marker + ' updated reminder')}
      AND ongoingConcerns='FAKE-PW concern'`) === '1',
    'Saving the reminder lost history or other patient fields');
    await page.close();
  });

  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'The installed clinic has no messenger location');
  for (const label of ['alpha', 'bravo']) {
    const id = h.insertId(sql, `INSERT INTO messagetbl(thedate, theime, themessage, thesubject,
        sentby, sentto, sentbyNo, sentByLocation, type, attachment)
      VALUES(CURDATE(),CURTIME(),'FAKE-PW content',${q(marker + ' ' + label)},'FAKE-PW sender','FAKE-PW recipient',
        ${q(provider)},${location},0,'<root/>')`, 'follow-up message');
    ids.push(id);
    sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(${id},${q(provider)},'new',${location},0,0);
      INSERT INTO msgDemoMap(messageID,demographic_no) VALUES(${id},${patient})`);
  }

  await s.step('two inbox windows retain their own searches after the other window changes its filter', async () => {
    const first = await context.newPage();
    const second = await context.newPage();
    for (const [page, label, order] of [[first, 'alpha', 'subject'], [second, 'bravo', 'from']]) {
      await page.goto(url('/messenger/DisplayMessages'));
      await page.locator('.page-header-search-form input[name="searchString"]').fill(marker + ' ' + label);
      await Promise.all([page.waitForNavigation(), page.locator('button[name="btnSearch"]').click()]);
      await Promise.all([page.waitForNavigation(), page.locator(`a[href*="?orderby=${order}&"]`).first().click()]);
    }
    await first.reload();
    h.assert(await first.locator('input[name="orderby"]').inputValue() === 'subject',
      "Reloading the first inbox adopted or toggled another window's sort");
    h.assert(await first.locator('.page-header-search-form input[name="searchString"]').inputValue() === marker + ' alpha',
      'Reloading the first inbox adopted the second inbox filter');
    h.assert((await first.locator('body').innerText()).includes(marker + ' alpha')
      && !(await first.locator('body').innerText()).includes(marker + ' bravo'),
    'The first inbox did not show only its own search results');
    await first.close();
    await second.close();
  });

  await s.step('Inbox and Sent return links retain their mailbox search and sort', async () => {
    for (const [box, button] of [['0', 'Back To Inbox'], ['1', 'Back to Sent']]) {
      const page = await context.newPage();
      await page.goto(url(`/messenger/DisplayMessages?boxType=${box}&orderby=subject&searchString=`
        + encodeURIComponent(marker + ' bravo')));
      await Promise.all([page.waitForNavigation(),
        page.locator(`a[href*="/messenger/ViewMessage?messageID=${ids[1]}&"]`).first().click()]);
      await h.assertNotErrorPage(page, `${button} message`);
      await Promise.all([page.waitForNavigation(), page.getByRole('link', {name: button, exact: true}).click()]);
      h.assert(await page.locator('.page-header-search-form input[name="searchString"]').inputValue() === marker + ' bravo',
        `${button} return lost the mailbox search`);
      h.assert(await page.locator('input[name="orderby"]').inputValue() === 'subject',
        `${button} return lost the mailbox sort`);
      h.assert(new URL(page.url()).searchParams.get('boxType') === box,
        `${button} return selected a different mailbox`);
      const body = await page.locator('body').innerText();
      h.assert(body.includes(marker + ' bravo') && !body.includes(marker + ' alpha'),
        `${button} return did not preserve the filtered results`);
      await page.close();
    }
  });

  await s.step('position navigation redirects to its resolved message and refuses a missing position', async () => {
    const route = `/messenger/ViewMessageByPosition?demographic_no=${patient}&orderBy=subject`;
    const found = await context.request.get(url(`${route}&messagePosition=0`), {maxRedirects: 0});
    try {
      h.assert(found.status() === 302, `Position navigation returned HTTP ${found.status()}`);
      const destination = new URL(found.headers().location, config.baseUrl);
      h.assert(destination.pathname.endsWith('/messenger/ViewMessage')
        && destination.searchParams.get('messageID') === ids[0]
        && destination.searchParams.get('demographic_no') === patient,
      'Position navigation lost its resolved message or patient on redirect');
    } finally { await found.dispose(); }
    const missing = await context.request.get(url(`${route}&messagePosition=99&messageID=${ids[1]}`),
      {maxRedirects: 0});
    try {
      h.assert(missing.status() === 404, 'A missing position accepted a caller-supplied message ID');
    } finally { await missing.dispose(); }
  });

  await s.step('unrelated providers cannot view or download a saved message attachment', async () => {
    // Remove this login's sender/recipient relationship while retaining the owned message.
    sql.execute(`UPDATE messagetbl SET sentbyNo='0' WHERE messageid=${ids[0]};
      DELETE FROM messagelisttbl WHERE message=${ids[0]}`);
    for (const route of [`/messenger/ViewAttach?attachId=${ids[0]}`,
      `/messenger/ViewPDFAttach?attachId=${ids[0]}`,
      `/messenger/ViewPDFFile?attId=${ids[0]}&file_id=0`]) {
      const response = await context.request.get(url(route), {maxRedirects: 0});
      h.assert(response.status() === 404, `${route.split('?')[0]} disclosed an unrelated message (HTTP ${response.status()})`);
    }
  });
}
if (require.main === module) runWorkflow('chart-message-followups', workflow);
module.exports = {workflow};
