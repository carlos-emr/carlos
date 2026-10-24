#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Issue #4811: legacy chart tabs, independent inbox filters and private attachments on a packaged install.
 * Owns FAKE patients, an orphaned appointment and two messages. Cleanup deletes only the owned fixtures.
 * The legacy owner action is exercised before entering the modern chart, which selects its layout.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {textPdf} = require('./lib/stored-pdf-documents');
const {waitForNavbars} = require('./echart-navbar-modules-playwright-checks');
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

  // This must precede every inbox/compose visit: a fresh login has no messaging bean.
  await s.step('document transfer works before the inbox has initialized messaging', async () => {
    const page = await context.newPage();
    await page.goto(url(`/messenger/Transfer/SelectItems?val1=spoofed-provider&val2=${patient}`));
    await h.assertNotErrorPage(page, 'fresh-login document transfer');
    const encoded = await page.locator('input[name="xmlDoc"]').inputValue();
    h.assert(Buffer.from(encoded, 'base64').toString('utf8').includes(marker),
      'The real transfer form did not contain the selected patient');
    await Promise.all([page.waitForNavigation(), page.getByRole('button', {name: 'Send These eDocs'}).click()]);
    await page.locator('#subject').waitFor();
    h.assert(new URL(page.url()).pathname.endsWith('/messenger/ViewCreateMessage'),
      'Submitting a first-use transfer lost its selection in an inbox redirect');
    h.assert((await page.locator('body').innerText()).includes('Message has attachments!'),
      'The first-use compose window has no staged patient attachment');
    await page.close();
  });

  await s.step('the report index renders provider choices without privacy filters', async () => {
    const page = await context.newPage();
    const response = await page.goto(url('/report/ViewReportindex'));
    h.assert(response.status() === 200, `Report index returned HTTP ${response.status()}`);
    await h.assertNotErrorPage(page, 'report index');
    h.assert(await page.locator(`select[name="provider_no"] option[value="${provider}"]`).count() > 0,
      'The report index did not render the logged-in provider');
    await page.close();
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

  await s.step('chart notes accept the patient alias and reject malformed identifiers', async () => {
    await s.chart();
    for (const parameter of ['demographicNo', 'demographic_no']) {
      const response = await context.request.get(url(`/casemgmt/ViewChartNotes?${parameter}=${patient}`));
      try {
        h.assert(response.status() === 200, `Chart notes with ${parameter} returned HTTP ${response.status()}`);
        const body = await response.text();
        h.assert(new RegExp(`name="demographicNo"[^>]*value="${patient}"`).test(body),
          `Chart notes with ${parameter} lost its canonical form patient`);
      } finally { await response.dispose(); }
    }
    for (const query of ['', '?demographic_no=invalid']) {
      const response = await context.request.get(url('/casemgmt/ViewChartNotes' + query));
      try { h.assert(response.status() === 400, `Invalid chart patient returned HTTP ${response.status()}`); }
      finally { await response.dispose(); }
    }
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

  await s.step('the message E link opens its linked patient chart through the native href', async () => {
    const page = await context.newPage();
    try {
      await page.goto(url(`/messenger/ViewMessage?messageID=${ids[1]}`));
      await h.assertNotErrorPage(page, 'message with linked patient chart');
      const link = page.locator('a[href*="/encounter/IncomingEncounter"]');
      h.assert(await link.count() === 1, 'The owned message does not render one linked-patient E link');
      const expected = new URL(url('/encounter/IncomingEncounter'));
      const href = new URL(await link.getAttribute('href'), page.url());
      h.assert(href.origin === expected.origin && href.pathname === expected.pathname,
        'The message E href has an incorrect application context or encounter path');
      h.assert(JSON.stringify(href.searchParams.getAll('demographicNo')) === JSON.stringify([patient])
        && JSON.stringify(href.searchParams.getAll('curProviderNo')) === JSON.stringify([provider]),
      'The message E href does not identify the owned patient and provider exactly once');

      // ViewMessage intentionally reuses the named encounter window. Observe its
      // real navigation when the earlier chart step has already opened that window.
      const named = [];
      for (const candidate of context.pages()) {
        if (candidate !== page && await candidate.evaluate(() => window.name).catch(() => '') === 'encounter') {
          named.push(candidate);
        }
      }
      h.assert(named.length <= 1, 'More than one encounter window exists in the workflow context');
      let chart;
      if (named.length) {
        [chart] = named;
        await Promise.all([chart.waitForNavigation({waitUntil: 'domcontentloaded'}), link.click()]);
      } else {
        chart = await s.popup(page, link, 'message-linked-patient-chart');
      }
      await waitForNavbars(chart, 20000);
      const destination = new URL(chart.url());
      const chartPath = new URL(url('/CaseManagementView'));
      h.assert(destination.origin === chartPath.origin && destination.pathname === chartPath.pathname
        && JSON.stringify(destination.searchParams.getAll('demographicNo')) === JSON.stringify([patient]),
      'The message E link opened an incorrect application path or patient chart');
      h.assert(destination.searchParams.getAll('chartContext').length === 1
        && Boolean(destination.searchParams.get('chartContext')),
      'The message E link did not establish exactly one signed chart context');
      h.assert(await chart.evaluate(() => String(globalThis.demographicNo)) === patient,
        'The message E link rendered another patient in the chart editor');
    } finally { await page.close(); }
  });

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

  await s.step('shared chart messages require their linked patient and preserve that patient through quotes and attachments', async () => {
    let otherPatient;
    // demographic.last_name is VARCHAR(30); retain the full ownership marker and
    // a short suffix so both the privacy assertion and cleanup compare stored text.
    const otherName = `${marker} other`;
    h.assert(otherName.length <= 30, 'The other shared-message patient name exceeds its schema limit');
    s.cleanup(() => {
      if (!otherPatient) return;
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${otherPatient}
        AND last_name=${q(otherName)}`) === '1', 'The other shared-message patient ownership changed');
      sql.execute(`DELETE FROM msgDemoMap WHERE messageID=${ids[0]} AND demographic_no=${otherPatient};
        DELETE FROM demographic WHERE demographic_no=${otherPatient} AND last_name=${q(otherName)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${otherPatient}`) === '0',
        'The other shared-message patient remains after cleanup');
    });
    otherPatient = h.insertId(sql, `INSERT INTO demographic
        (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
         provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES(${q(otherName)},'Workflow','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`,
    'other shared-message patient');
    h.assert(sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${otherPatient}`) === otherName,
      'The other shared-message patient name was not stored intact');
    const pdf = textPdf(`${marker} shared chart attachment`);
    const pdfXml = `<PDF><FILE_ID>0</FILE_ID><STATUS>OK</STATUS><TITLE>${marker} shared chart attachment</TITLE>`
      + `<CONTENT>${pdf.toString('base64')}</CONTENT></PDF>`;
    sql.execute(`UPDATE messagetbl SET sentbyNo='0',pdfattachment=${q(pdfXml)} WHERE messageid=${ids[0]};
      DELETE FROM messagelisttbl WHERE message=${ids[0]}`);

    for (const route of [
      `/messenger/ViewMessage?messageID=${ids[0]}&from=encounter&demographic_no=${otherPatient}`,
      ...['reply', 'replyAll', 'forward'].map(operation =>
        `/messenger/HandleMessages?messageNo=${ids[0]}&${operation}=1&chartPatientNo=${otherPatient}`),
      `/messenger/ViewAttach?attachId=${ids[0]}&chartPatientNo=${otherPatient}`,
      `/messenger/ViewPDFAttach?attachId=${ids[0]}&chartPatientNo=${otherPatient}`,
      `/messenger/ViewPDFFile?attId=${ids[0]}&file_id=0&chartPatientNo=${otherPatient}`,
    ]) {
      const response = await context.request.get(url(route), {maxRedirects: 0});
      try {
        h.assert(response.status() === 404,
          `${route.split('?')[0]} accepted a patient not linked to the message (HTTP ${response.status()})`);
      } finally { await response.dispose(); }
    }

    // A message may be linked to several charts. The selected chart grants no access to
    // the other patient's name and must not select that patient for the resulting quote.
    sql.execute(`INSERT INTO msgDemoMap(messageID,demographic_no) VALUES(${ids[0]},${otherPatient})`);
    const page = await context.newPage();
    try {
      const detail = `/messenger/ViewMessage?messageID=${ids[0]}&from=encounter&demographic_no=${patient}`;
      for (const operation of ['reply', 'replyAll', 'forward']) {
        const response = await page.goto(url(detail));
        h.assert(response.status() === 200, `Shared chart detail returned HTTP ${response.status()}`);
        await h.assertNotErrorPage(page, 'shared chart message detail');
        const body = await page.locator('body').innerText();
        h.assert(body.includes(marker + ' alpha') && !body.includes(otherName),
          'Shared chart detail lost the message or disclosed another linked patient');
        h.assert(await page.locator('form[action$="/messenger/HandleMessages"] input[name="chartPatientNo"]').inputValue() === patient,
          'Shared chart detail lost the selected patient in its action form');
        for (const attachment of ['ViewAttach', 'ViewPDFAttach']) {
          const href = await page.locator(`a[href*="${attachment}?attachId="]`).first().getAttribute('href');
          h.assert(href.includes(`chartPatientNo=${patient}`), `${attachment} link lost the shared chart patient`);
        }
        // The legacy encounter detail does not display reply buttons. Open the same
        // read-only route that a reconstructed quote uses, retaining its explicit chart.
        const quote = await page.goto(url(`/messenger/HandleMessages?messageNo=${ids[0]}`
          + `&${operation}=1&chartPatientNo=${patient}`));
        h.assert(quote.status() === 200, `${operation} from a shared chart returned HTTP ${quote.status()}`);
        await page.locator('#subject').waitFor();
        const expectedSubject = `${operation === 'forward' ? 'Fwd:' : 'Re:'}${marker} alpha`;
        h.assert(await page.locator('#subject').inputValue() === expectedSubject,
          `${operation} from a shared chart lost its subject`);
        h.assert((await page.locator('textarea[name="message"]').inputValue()).includes('FAKE-PW content'),
          `${operation} from a shared chart lost its quote`);
        h.assert(await page.locator('input[name="demographic_no"]').inputValue() === patient,
          `${operation} from a shared chart selected another linked patient`);
        h.assert(new URL(page.url()).searchParams.get('chartPatientNo') === patient,
          `${operation} reload URL lost the shared chart patient`);
        await page.reload();
        h.assert(await page.locator('#subject').inputValue() === expectedSubject
          && await page.locator('input[name="demographic_no"]').inputValue() === patient,
        `${operation} reload lost the shared message or its selected patient`);
      }
    } finally { await page.close(); }

    for (const route of [`/messenger/ViewAttach?attachId=${ids[0]}`,
      `/messenger/ViewPDFAttach?attachId=${ids[0]}`,
      `/messenger/ViewPDFFile?attId=${ids[0]}&file_id=0`]) {
      const response = await context.request.get(url(`${route}&chartPatientNo=${patient}`), {maxRedirects: 0});
      try {
        h.assert(response.status() === 200, `${route.split('?')[0]} refused the linked chart (HTTP ${response.status()})`);
        if (route.includes('/ViewPDFFile?')) {
          h.assert((await response.body()).equals(pdf), 'Shared chart download returned another attachment');
        } else if (route.includes('/ViewPDFAttach?')) {
          const body = await response.text();
          h.assert(body.includes(`${marker} shared chart attachment`)
            && body.includes(`name="chartPatientNo" value="${patient}"`),
          'Shared PDF list lost its attachment or selected-patient download field');
        }
      } finally { await response.dispose(); }
    }
  });

  await s.step('unrelated providers cannot read, quote or download a saved message', async () => {
    // Remove this login's sender/recipient relationship while retaining the owned message.
    sql.execute(`UPDATE messagetbl SET sentbyNo='0' WHERE messageid=${ids[0]};
      DELETE FROM messagelisttbl WHERE message=${ids[0]}`);
    for (const route of [`/messenger/ViewMessage?messageID=${ids[0]}`,
      ...['reply', 'replyAll', 'forward'].map(operation =>
        `/messenger/HandleMessages?messageNo=${ids[0]}&${operation}=1`),
      `/messenger/ViewAttach?attachId=${ids[0]}`,
      `/messenger/ViewPDFAttach?attachId=${ids[0]}`,
      `/messenger/ViewPDFFile?attId=${ids[0]}&file_id=0`]) {
      const response = await context.request.get(url(route), {maxRedirects: 0});
      try {
        h.assert(response.status() === 404,
          `${route.split('?')[0]} disclosed an unrelated message (HTTP ${response.status()})`);
        const body = await response.text();
        h.assert(!body.includes(marker + ' alpha') && !body.includes('FAKE-PW content'),
          `${route.split('?')[0]} included the denied message in its error response`);
      } finally { await response.dispose(); }
    }
  });
}
if (require.main === module) runWorkflow('chart-message-followups', workflow);
module.exports = {workflow};
