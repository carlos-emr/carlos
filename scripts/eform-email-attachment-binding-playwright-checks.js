#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * An email's attachments are bound to the window that prepared them and to that window's patient
 * (#4425).
 *
 * Every eForm compose and every Manage Emails resend stages its prepared attachments under a
 * one-time key in its own page, bound to the patient it was opened for. The send takes exactly that
 * entry, refuses it for any other patient, and checks each eForm, document, lab and HRM record's
 * owner. eform-email-two-windows proves two composers each send their own patient's eForm; this
 * check covers the paths around it:
 *
 *   1. A composer whose patient number is changed before Send (as a crafted POST would) is
 *      refused: nothing reaches the transport and nothing is logged for either patient.
 *   2. A sent composer's exact POST, replayed, is refused: the key is used up.
 *   3. A Manage Emails resend window keeps its own patient's eForm while another patient's eForm
 *      email is composed in a second window, and each window then sends its own patient's eForm.
 *
 * Asserted from what CARLOS handed its transport (a loopback SMTP sink, as in admin-email-config)
 * and from emailLog/emailAttachment/eform_data.
 *
 * Fixtures: the workflow's FAKE patient (X), a second FAKE patient (Y) named after the run marker,
 * an owned eForm template and an owned sender account pointing at the sink. Cleanup removes the
 * owned email logs, attachments, chart notes, eForm instances, template, sender and patient Y, and
 * asserts each is gone. Run on a disposable install only, never beside another check: while it runs
 * the owned sender is offered clinic-wide (as for admin-email-config).
 */
const net = require('node:net');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const TIMEOUT = 30000;
const REFUSED = 'This email was not sent';
const TEMPLATE_HTML = '<html><head><title>Email fixture</title></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="note" id="note"><input type="submit" value="Submit" id="SubmitButton">'
  + '</form></body></html>';

// Loopback-only SMTP endpoint: records each envelope and message, relays nothing.
function startSmtpSink() {
  const messages = [];
  const sockets = new Set();
  const server = net.createServer(socket => {
    sockets.add(socket);
    socket.on('close', () => sockets.delete(socket));
    socket.on('error', () => {});
    socket.setEncoding('latin1');
    let buffer = '';
    let inData = false;
    let current = { from: '', rcpt: [], data: '' };
    const reply = line => socket.write(`${line}\r\n`);
    reply('220 carlos-check-sink ESMTP');
    socket.on('data', chunk => {
      buffer += chunk;
      let end;
      while ((end = buffer.indexOf('\r\n')) >= 0) {
        const line = buffer.slice(0, end);
        buffer = buffer.slice(end + 2);
        if (inData) {
          if (line === '.') {
            inData = false;
            messages.push(current);
            current = { from: '', rcpt: [], data: '' };
            reply('250 2.0.0 accepted by check sink');
          } else current.data += `${line.startsWith('..') ? line.slice(1) : line}\r\n`;
          continue;
        }
        const verb = line.split(/[\s:]/)[0].toUpperCase();
        if (verb === 'EHLO') { reply('250-carlos-check-sink'); reply('250 8BITMIME'); }
        else if (verb === 'HELO' || verb === 'NOOP') reply('250 OK');
        else if (verb === 'MAIL') { current.from = (line.match(/<([^>]*)>/) || [])[1] || ''; reply('250 OK'); }
        else if (verb === 'RCPT') { current.rcpt.push((line.match(/<([^>]*)>/) || [])[1] || ''); reply('250 OK'); }
        else if (verb === 'DATA') { inData = true; reply('354 end with <CRLF>.<CRLF>'); }
        else if (verb === 'RSET') { current = { from: '', rcpt: [], data: '' }; reply('250 OK'); }
        else if (verb === 'QUIT') { reply('221 bye'); socket.end(); }
        else reply('502 not implemented');
      }
    });
  });
  let closed = false;
  return {
    messages,
    listen: () => new Promise((resolve, reject) => {
      server.once('error', reject);
      server.listen(0, '127.0.0.1', () => resolve(server.address().port));
    }),
    close: () => new Promise(resolve => {
      if (closed) return resolve();
      closed = true;
      for (const socket of sockets) socket.destroy();
      server.close(() => resolve());
    }),
  };
}

async function workflow(s) {
  const { sql, patient, provider, marker, context, recorder } = s;
  const q = h.sqlString;
  // CARLOS opens the SMTP connection to the runner's loopback, so both must be the same host.
  if (!['localhost', '127.0.0.1', '::1', '[::1]'].includes(s.config.baseUrl.hostname.toLowerCase())) {
    throw new h.SkipCheck('The SMTP sink binds the runner\'s loopback, which CARLOS can reach only when BASE_URL is loopback');
  }
  const hex = marker.slice(-16);
  const sender = `sender-${hex}@example.com`;
  const lastNameY = `${marker}Y`;
  const formName = `${marker} Email`;
  const patients = {
    x: { demographicNo: patient, address: `patient-x-${hex}@example.com` },
    y: { demographicNo: null, address: `patient-y-${hex}@example.com` },
  };
  const sink = startSmtpSink();
  let configId;
  let fid;

  // Registered first so it runs last, after every child row below has been removed.
  s.cleanup(() => {
    const y = patients.y.demographicNo;
    if (!y) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${y} AND last_name=${q(lastNameY)}`) === '1',
      'Patient Y fixture ownership changed');
    const support = [['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'],
      ['demographicExt', 'demographic_no'], ['demographicArchive', 'demographic_no'], ['admission', 'client_id']];
    sql.execute(support.map(([table, column]) => `DELETE FROM ${table} WHERE ${column}=${y}`).join(';'));
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${y} AND last_name=${q(lastNameY)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${y}`) === '0', 'Patient Y was not removed');
  });
  s.cleanup(async () => {
    await sink.close();
    for (const { demographicNo: demo } of Object.values(patients)) {
      if (!demo) continue;
      const logs = sql.rows(`SELECT id FROM emailLog WHERE demographicNo=${demo}`).map(row => row[0]);
      for (const id of logs) h.assert(/^[1-9]\d*$/.test(id), 'Owned email log id is invalid');
      if (logs.length) {
        sql.execute(`DELETE FROM casemgmt_note_link WHERE table_name=12 AND table_id IN (${logs.join(',')});
          DELETE FROM emailAttachment WHERE logId IN (${logs.join(',')});
          DELETE FROM emailLog WHERE id IN (${logs.join(',')}) AND demographicNo=${demo}`);
      }
      const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${demo}`;
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
        DELETE FROM casemgmt_note WHERE demographic_no=${demo}`);
      const fdids = sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${demo}`).map(row => row[0]);
      if (fdids.length) {
        sql.execute(`DELETE FROM eform_values WHERE fdid IN (${fdids.join(',')});
          DELETE FROM eform_data WHERE fdid IN (${fdids.join(',')}) AND demographic_no=${demo}`);
      }
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM emailLog WHERE demographicNo=${demo})
        + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${demo})
        + (SELECT COUNT(*) FROM eform_data WHERE demographic_no=${demo})`) === '0',
      'Owned email, note or eForm rows were not removed');
    }
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    if (configId) sql.execute(`DELETE FROM emailConfig WHERE id=${configId} AND senderEmail=${q(sender)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM emailConfig WHERE senderEmail=${q(sender)})`) === '0',
    'The owned eForm template or sender account was not removed');
  });

  patients.y.demographicNo = sql.value(`INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(lastNameY)},'Workflow','1981','02','03','M','AC',${q(provider)},'ON','ON','NR',NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(patients.y.demographicNo), 'Patient Y fixture was not created');
  for (const [key, entry] of Object.entries(patients)) {
    sql.execute(`UPDATE demographic SET email=${q(entry.address)} WHERE demographic_no=${entry.demographicNo}
      AND last_name=${q(key === 'x' ? marker : lastNameY)}`);
  }
  const port = await sink.listen();
  // Above every configId an emailLog row names: demo rows can name config ids with no config row.
  configId = sql.value(`INSERT INTO emailConfig(id,emailType,emailProvider,active,senderFirstName,senderLastName,senderEmail,configDetails)
    SELECT GREATEST((SELECT COALESCE(MAX(id),0) FROM emailConfig),(SELECT COALESCE(MAX(configId),0) FROM emailLog))+1,
      'SMTP','LOCAL',1,${q(marker)},'Sink',${q(sender)},${q(JSON.stringify({ host: '127.0.0.1', port: String(port) }))};
    SELECT id FROM emailConfig WHERE senderEmail=${q(sender)} AND senderFirstName=${q(marker)}`);
  h.assert(/^[1-9]\d*$/.test(configId), 'Owned sender account was not created');
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
    status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'',${q(formName)},CURDATE(),CURTIME(),${q(provider)},
    1,${q(TEMPLATE_HTML)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'Owned eForm template was not created');

  const logCount = entry => Number(sql.value(`SELECT COUNT(*) FROM emailLog WHERE demographicNo=${entry.demographicNo}`));
  // Every eForm attachment logged for a patient's email must be an eForm of that patient.
  const loggedEForms = entry => sql.rows(`SELECT a.documentId, IFNULL(d.demographic_no,0) FROM emailLog l
      JOIN emailAttachment a ON a.logId=l.id LEFT JOIN eform_data d ON d.fdid=a.documentId
      WHERE l.demographicNo=${entry.demographicNo} AND a.documentType='EFORM' ORDER BY l.id`);

  // E-Chart ▸ eForms "+" ▸ owned eForm ▸ Email, answering the opt-in prompt as a clinician does.
  async function compose(chart, label) {
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), `${label}-eform-list`);
    await list.locator('#efmTable').waitFor();
    const form = await s.popup(list, list.locator('#efmTable').getByRole('link', { name: formName, exact: true }), `${label}-eform`);
    await form.locator('#note').fill(`${marker} note ${label}`);
    const email = form.locator('#remoteEmailButton');
    await email.waitFor({ timeout: TIMEOUT });
    await h.withExpectedDialogs(form, async () => {
      await Promise.all([form.waitForURL(/\/email\/emailComposeAction/, { timeout: TIMEOUT }), email.click()]);
    }, { promptText: 'Yes' });
    await form.locator('#emailComposeForm').waitFor();
    await list.close();
    return form;
  }

  async function fill(composer, label) {
    await composer.locator('#senderEmailAddress').selectOption(configId);
    await composer.locator('#subjectEmail').fill(`${marker} ${label}`);
    await composer.locator('#bodyEmail').fill(`${marker} body ${label}`);
    await composer.locator('#emailPDFPassword').fill(`PW${hex}${label}`);
    await composer.locator('#emailPDFPasswordClue').fill(`${marker} clue`);
    await composer.locator('#doNotAddAsNoteOption').check();
    const key = await composer.locator('#emailAttachmentKey').inputValue();
    h.assert(/^[A-Za-z0-9_-]{22}$/.test(key), `The ${label} composer carries no attachment key of its own`);
    return key;
  }

  async function send(composer, entry, label) {
    await Promise.all([composer.waitForURL(/\/email\/emailSendAction/, { timeout: TIMEOUT }), composer.locator('#btnSend').click()]);
    await composer.locator('#successMessage').waitFor({ timeout: TIMEOUT });
    h.assert((await composer.locator('#successMessage').innerText()).includes(entry.address),
      `The ${label} confirmation does not name its patient's address`);
  }

  // Send, expecting the refusal alert; the composer then closes itself.
  async function sendRefused(composer, label) {
    const composerLabel = `${label}-eform`;  // the page label compose() gave this window
    const closed = composer.waitForEvent('close', { timeout: TIMEOUT });
    const dialogs = await h.withExpectedDialogs(composer, async () => {
      await composer.locator('#btnSend').click();
      await closed;
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text.includes(REFUSED),
      `The ${label} send was not refused with the attachment refusal message`);
    // The refusal page closes its window as soon as the alert is acknowledged, so the browser
    // abandons whatever subresources were still loading. Drop only those failures the harness
    // proves were abandoned by that close (the rule xss-poison-helpers applies); anything else
    // still fails the step.
    const failures = recorder.requestFailures;
    for (let i = failures.length - 1; i >= 0; i -= 1) {
      const entry = failures[i];
      if (entry.label === composerLabel && /ERR_ABORTED/.test(entry.errorText || '')
        && typeof entry.navigatedAway === 'function' && entry.navigatedAway()) failures.splice(i, 1);
    }
  }

  await s.step('a composer whose patient number is changed before Send is refused and sends nothing', async () => {
    const composer = await compose(await s.chart(), 'tampered');
    h.assert(await composer.locator('.attachmentName').count() === 1, 'The composer does not list exactly one attachment');
    await fill(composer, 'tampered');
    // What a crafted POST does: X's window and key, Y's patient number.
    await composer.locator('#emailComposeForm input[name="demographicId"]')
      .evaluate((input, y) => { input.value = y; }, patients.y.demographicNo);
    await sendRefused(composer, 'tampered');
    h.assert(sink.messages.length === 0, `The sink received ${sink.messages.length} message(s) from a refused send`);
    h.assert(logCount(patients.x) === 0 && logCount(patients.y) === 0, 'A refused send was logged');
  });

  let sentLogX;
  await s.step('a sent composer\'s POST replayed with its used key is refused', async () => {
    const composer = await compose(await s.chart(), 'x');
    await fill(composer, 'x');
    const { action, body } = await composer.locator('#emailComposeForm').evaluate(form => ({
      action: form.action,
      body: new URLSearchParams(new FormData(form)).toString(),
    }));
    h.assert(body.includes('CSRF-TOKEN='), 'The compose form carries no CSRF token to replay');
    await send(composer, patients.x, 'x');
    h.assert(sink.messages.length === 1 && JSON.stringify(sink.messages[0].rcpt) === JSON.stringify([patients.x.address]),
      'X\'s send was not delivered to exactly X');
    const rows = loggedEForms(patients.x);
    h.assert(rows.length === 1 && rows[0][1] === String(patients.x.demographicNo), 'X\'s email did not log exactly X\'s eForm');
    sentLogX = sql.value(`SELECT id FROM emailLog WHERE demographicNo=${patients.x.demographicNo} ORDER BY id DESC LIMIT 1`);

    const origin = new URL(action).origin;
    const replay = await context.request.post(action, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Origin: origin, Referer: action },
      data: body,
      maxRedirects: 0,
      timeout: TIMEOUT,
    });
    h.assert(replay.status() === 200, `The replayed send answered HTTP ${replay.status()}`);
    const html = await replay.text();
    h.assert(/id="isEmailError"\s+value="true"/.test(html) && html.includes(REFUSED),
      'The replayed send was not refused with the attachment refusal message');
    h.assert(sink.messages.length === 1, `The replay reached the transport (${sink.messages.length} messages)`);
    h.assert(logCount(patients.x) === 1, 'The replayed send was logged');
  });

  let resend;
  let composerY;
  await s.step('a Manage Emails resend of X\'s email keeps X\'s eForm while Y\'s email is composed', async () => {
    h.assert(/^[1-9]\d*$/.test(sentLogX || ''), 'X\'s sent email log is missing');
    resend = await context.newPage();
    await h.gotoApp(resend, s.config.baseUrl, `/admin/ManageEmails?method=resendEmail&logId=${sentLogX}`);
    await resend.locator('#emailComposeForm').waitFor({ timeout: TIMEOUT });
    h.assert(await resend.locator('.attachmentName').count() === 1, 'The resend window does not list X\'s eForm');

    // The search, Master Record, E-Chart and eForm list popups reuse fixed window names, so close
    // them before opening Y's; the resend window stays open with its staged attachment.
    const keep = new Set([s.schedule, resend]);
    for (const page of context.pages()) if (!keep.has(page) && !page.isClosed()) await page.close();
    const { masterPage } = await openMasterRecord(context, s.schedule, recorder, {
      searchTerm: lastNameY, preferredDemographicNo: patients.y.demographicNo, timeout: TIMEOUT,
    });
    h.assert(new URL(masterPage.url()).searchParams.get('demographic_no') === patients.y.demographicNo,
      'The search opened a patient other than the owned patient Y');
    const chartY = await openChart(context, masterPage, recorder, TIMEOUT);
    await waitForNavbars(chartY, TIMEOUT);
    composerY = await compose(chartY, 'y');
    h.assert(!resend.isClosed(), 'Opening Y\'s composer closed the resend window');
  });

  await s.step('the resend window sends X\'s own eForm to X, and Y\'s window Y\'s own', async () => {
    const keyResend = await fill(resend, 'resend');
    const keyY = await fill(composerY, 'y');
    h.assert(keyResend !== keyY, 'Two windows share one attachment key');
    await send(resend, patients.x, 'resend');
    h.assert(sink.messages.length === 2 && JSON.stringify(sink.messages[1].rcpt) === JSON.stringify([patients.x.address]),
      'The resend was not delivered to exactly X');
    h.assert(/Content-Type: application\/pdf/i.test(sink.messages[1].data), 'The resend carries no PDF attachment');
    const rowsX = loggedEForms(patients.x);
    h.assert(rowsX.length === 2 && rowsX.every(([, owner]) => owner === String(patients.x.demographicNo)),
      'X\'s resend carried an eForm that is not X\'s');

    await send(composerY, patients.y, 'y');
    h.assert(sink.messages.length === 3 && JSON.stringify(sink.messages[2].rcpt) === JSON.stringify([patients.y.address]),
      'Y\'s send was not delivered to exactly Y');
    const rowsY = loggedEForms(patients.y);
    h.assert(rowsY.length === 1 && rowsY[0][1] === String(patients.y.demographicNo),
      `Y's email logged ${rowsY.length} eForm attachment(s) of Y; expected exactly 1`);
  });
}

if (require.main === module) runWorkflow('eform-email-attachment-binding', workflow, { openPatient: true });
module.exports = { workflow };
