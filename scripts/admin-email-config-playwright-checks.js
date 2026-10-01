#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Outgoing email, from the administrator's panel to the patient's eForm and back.
 * User path: Schedule ▸ Administration ▸ Emails ▸ Configure Email (#myFrame); E-Chart ▸ eForms "+"
 * ▸ owned eForm ▸ toolbar Email (consent prompt) ▸ email/emailComposeAction ▸ Send
 * (email/emailSendAction); Administration ▸ Emails ▸ Manage Emails ▸ Fetch Emails / Resolve.
 * Nothing leaves the host: the check starts a minimal SMTP sink (node net, 127.0.0.1, ephemeral
 * port) and the only sender account it offers is an owned LOCAL-provider emailConfig pointing at
 * that sink. Asserts the delivered message (envelope, subject, body, PDF attachment), the
 * emailLog/emailAttachment rows, that the patient's .invalid address is never sent to, that a send
 * with the sink closed is reported as NOT sent and logged FAILED, that Manage Emails lists and
 * filters exactly the owned log rows, and that Resolve persists RESOLVED.
 * Fixtures: owned emailConfig, eForm template, patient email; cleanup removes the owned log,
 * attachment, eForm and config rows and asserts them gone. EXCLUSIVE=1: the owned sender is offered
 * clinic-wide while the check runs. Implements coverage plan §3.7 admin-email-config.
 */
const net = require('node:net');
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TIMEOUT = 30000;
const TEMPLATE_HTML = '<html><head><title>Email fixture</title></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="note" id="note"><input type="submit" value="Submit" id="SubmitButton">'
  + '</form></body></html>';

// A loopback-only SMTP endpoint: no STARTTLS, no AUTH, no relay. It records each envelope and
// message so the check can assert exactly what CARLOS handed to its transport.
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
    let current = {from: '', rcpt: [], data: ''};
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
            current = {from: '', rcpt: [], data: ''};
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
        else if (verb === 'RSET') { current = {from: '', rcpt: [], data: ''}; reply('250 OK'); }
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
  const {sql, patient, provider, marker, context} = s;
  const hex = marker.slice(-16);
  const sender = `sender-${hex}@example.com`;
  const recipient = `patient-${hex}@example.com`;
  // Unroutable by definition (RFC 2606); CARLOS must classify it invalid and never send to it.
  const invalidRecipient = `patient-${hex}@example.invalid`;
  const formName = `${marker} Email`;
  const sink = startSmtpSink();
  let configId;
  let fid;
  const ownedLogs = `SELECT id FROM emailLog WHERE demographicNo=${patient}`;
  const configsBefore = JSON.stringify(sql.rows('SELECT * FROM emailConfig ORDER BY id'));

  s.cleanup(async () => {
    await sink.close();
    const logs = sql.rows(ownedLogs).map(row => row[0]);
    for (const id of logs) h.assert(/^[1-9]\d*$/.test(id), 'Owned email log id is invalid');
    if (logs.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE table_name=12 AND table_id IN (${logs.join(',')});
        DELETE FROM emailAttachment WHERE logId IN (${logs.join(',')});
        DELETE FROM emailLog WHERE id IN (${logs.join(',')}) AND demographicNo=${patient}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM emailLog WHERE demographicNo=${patient}`) === '0', 'Owned email log rows were not removed');
    // A send with "add as note" files a chart note on the owned patient; remove any such note.
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned chart notes were not removed');
    const fdids = sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient}`).map(row => row[0]);
    if (fdids.length) {
      sql.execute(`DELETE FROM eform_values WHERE fdid IN (${fdids.join(',')});
        DELETE FROM eform_data WHERE fdid IN (${fdids.join(',')}) AND demographic_no=${patient}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}`) === '0', 'Owned eForm instances were not removed');
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${h.sqlString(formName)}`);
    h.assert(!fid || sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${fid}`) === '0', 'Owned eForm template was not removed');
    if (configId) sql.execute(`DELETE FROM emailConfig WHERE id=${configId} AND senderEmail=${h.sqlString(sender)}`);
    h.assert(JSON.stringify(sql.rows('SELECT * FROM emailConfig ORDER BY id')) === configsBefore,
      'Email sender accounts differ from the snapshot taken before the check');
  });

  const port = await sink.listen();
  configId = sql.value(`INSERT INTO emailConfig(emailType,emailProvider,active,senderFirstName,senderLastName,senderEmail,configDetails)
    VALUES('SMTP','LOCAL',1,${h.sqlString(marker)},'Sink',${h.sqlString(sender)},
      ${h.sqlString(JSON.stringify({host: '127.0.0.1', port: String(port)}))}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(configId), 'Owned sender account was not created');
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
    status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${h.sqlString(formName)},'',${h.sqlString(formName)},CURDATE(),CURTIME(),${h.sqlString(provider)},
    1,${h.sqlString(TEMPLATE_HTML)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'Owned eForm template was not created');
  sql.execute(`UPDATE demographic SET email=${h.sqlString(`${recipient}, ${invalidRecipient}`)}
    WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder: s.recorder, label: 'email-administration', timeout: TIMEOUT});
  // Each left-nav item replaces #myFrame and points it at its route; return that frame once loaded.
  async function openSection(name, path) {
    const link = admin.getByRole('link', {name, exact: true, includeHidden: true});
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const deadline = Date.now() + TIMEOUT;
    let frame;
    while (!(frame = admin.frames().find(f => f.parentFrame() && new URL(f.url(), 'http://x').pathname.endsWith(path)))) {
      h.assert(Date.now() < deadline, `${name} did not load ${path} into the administration frame`);
      await admin.waitForTimeout(100);
    }
    await frame.waitForLoadState('domcontentloaded');
    return frame;
  }

  await s.step('Administration ▸ Emails ▸ Configure Email opens the outgoing-account guide', async () => {
    const guide = await openSection('Configure Email', '/admin/ViewConfigureEmail');
    await guide.getByRole('heading', {name: 'Outgoing Email Accounts'}).waitFor({timeout: TIMEOUT});
    h.assert(await guide.locator('dd').filter({hasText: /^SMTP$/}).count() === 1
      && await guide.locator('dd').filter({hasText: /^API$/}).count() === 1, 'The guide does not document both account types');
  });

  // E-Chart ▸ eForms "+" ▸ owned eForm ▸ Email. The patient has not opted in, so the toolbar asks
  // for the explicit 'Yes' acknowledgement before it saves the eForm and opens the composer.
  // The Add eForm list stays open between the two sends, as it does for a clinician: the chart
  // reopens it by window name, so a second "+" would focus it rather than open a new window.
  let list;
  async function composeFromChart(note) {
    if (!list || list.isClosed()) {
      const chart = await s.chart();
      list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'email-eform-list');
    }
    await list.locator('#efmTable').waitFor();
    const form = await s.popup(list, list.locator('#efmTable').getByRole('link', {name: formName, exact: true}), 'email-eform');
    await form.locator('#note').fill(note);
    const email = form.locator('#remoteEmailButton');
    await email.waitFor({timeout: TIMEOUT});
    h.assert(await email.isEnabled(), 'The eForm Email button is disabled although an active sender exists');
    const dialogs = await h.withExpectedDialogs(form, async () => {
      await Promise.all([form.waitForURL(/\/email\/emailComposeAction/, {timeout: TIMEOUT}), email.click()]);
    }, {promptText: 'Yes'});
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt' && /not explicitly opted-in/.test(dialogs[0].text),
      'Email did not ask for the opt-in acknowledgement exactly once');
    await form.locator('#emailComposeForm').waitFor();
    return form;
  }

  async function fillAndSend(compose, subject, body) {
    const modal = compose.locator('#errorMessageModal');
    await modal.waitFor({state: 'visible', timeout: TIMEOUT});
    h.assert(await modal.locator('li').filter({hasText: invalidRecipient}).count() === 1,
      'The composer did not warn about the invalid patient address');
    await modal.locator('button.btn-close').click();
    await modal.waitFor({state: 'hidden', timeout: TIMEOUT});
    await compose.locator('#senderEmailAddress').selectOption(configId);
    await compose.locator('#subjectEmail').fill(subject);
    await compose.locator('#bodyEmail').fill(body);
    await compose.locator('#emailPDFPassword').fill(`PW${hex}`);
    await compose.locator('#emailPDFPasswordClue').fill(`${marker} clue`);
    await compose.locator('#doNotAddAsNoteOption').check();
    await Promise.all([compose.waitForURL(/\/email\/emailSendAction/, {timeout: TIMEOUT}), compose.locator('#btnSend').click()]);
  }

  const subject = `${marker} sent`;
  let sentLog;
  await s.step('the eForm composer offers the owned sender and only the valid patient address', async () => {
    const compose = await composeFromChart(`${marker} note sent`);
    const recipients = await compose.locator('input[type="hidden"][name="receiverEmailAddress"]').evaluateAll(inputs => inputs.map(input => input.value));
    h.assert(JSON.stringify(recipients) === JSON.stringify([recipient]), 'The composer would send to an address other than the valid patient address');
    h.assert(await compose.locator(`#senderEmailAddress option[value="${configId}"]`).count() === 1, 'The owned sender account is not offered');
    h.assert(await compose.locator('.attachmentName').count() === 1, 'The saved eForm is not attached');
    await fillAndSend(compose, subject, `${marker} body line`);
    await compose.locator('#successMessage').waitFor({timeout: TIMEOUT});
    h.assert((await compose.locator('#successMessage').innerText()).includes(recipient), 'The confirmation does not name the recipient');
    await compose.waitForEvent('close', {timeout: TIMEOUT});
  });

  await s.step('the sink received one message for the valid address with subject, body and PDF', async () => {
    h.assert(sink.messages.length === 1, `The sink received ${sink.messages.length} messages; expected 1`);
    const [message] = sink.messages;
    h.assert(message.from === sender, 'The envelope sender is not the owned account');
    h.assert(JSON.stringify(message.rcpt) === JSON.stringify([recipient]), 'The envelope recipients are not exactly the valid patient address');
    h.assert(message.data.includes(`Subject: ${subject}`), 'The delivered subject differs from the composed subject');
    h.assert(message.data.includes(`${marker} body line`) && message.data.includes(`${marker} clue`),
      'The delivered body lacks the composed text or the password clue');
    h.assert(/Content-Type: application\/pdf/i.test(message.data), 'The delivered message carries no PDF attachment');
    h.assert(!message.data.includes(invalidRecipient), 'The invalid address appears in the delivered message');
  });

  await s.step('the log row records the successful EFORM send with its eForm attachment', async () => {
    const rows = sql.rows(`SELECT id,configId,fromEmail,toEmail,status,transactionType,providerNo,isEncrypted,isAttachmentEncrypted,password
      FROM emailLog WHERE demographicNo=${patient} AND subject=${h.sqlString(subject)}`);
    h.assert(rows.length === 1, 'Expected exactly one log row for the sent email');
    const [id, config, from, to, status, type, logProvider, encrypted, attachmentEncrypted, password] = rows[0];
    sentLog = id;
    h.assert(config === configId && from === sender && to === recipient && status === 'SUCCESS' && type === 'EFORM'
      && logProvider === provider, 'The log row does not record the owned sender, recipient, SUCCESS and EFORM');
    h.assert(encrypted === '1' && attachmentEncrypted === '1' && password === `PW${hex}`, 'The log row lost the encryption settings');
    const fdid = sql.value(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND fid=${fid} ORDER BY fdid LIMIT 1`);
    h.assert(/^[1-9]\d*$/.test(fdid || ''), 'Email did not save the eForm before composing');
    h.assert(sql.value(`SELECT CONCAT(documentType,'|',documentId) FROM emailAttachment WHERE logId=${id}`) === `EFORM|${fdid}`,
      'The attachment row does not reference the saved eForm');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0',
      '"Do not add as note" still wrote a chart note');
  });

  const failedSubject = `${marker} refused`;
  let failedLog;
  await s.step('with the sink closed the send is reported NOT sent and logged FAILED', async () => {
    await sink.close();
    const compose = await composeFromChart(`${marker} note refused`);
    await fillAndSend(compose, failedSubject, `${marker} body refused`);
    const alert = compose.locator('.alert-danger').filter({hasText: 'was NOT sent'});
    await alert.waitFor({timeout: TIMEOUT});
    h.assert((await alert.innerText()).includes(recipient), 'The failure notice does not name the recipient');
    await compose.waitForTimeout(4000);
    h.assert(!compose.isClosed(), 'The composer closed itself after a failed send');
    const row = sql.rows(`SELECT id,status,errorMessage FROM emailLog WHERE demographicNo=${patient} AND subject=${h.sqlString(failedSubject)}`);
    h.assert(row.length === 1 && row[0][1] === 'FAILED' && row[0][2] && row[0][2] !== 'NULL', 'The refused send is not logged FAILED with a reason');
    failedLog = row[0][0];
    h.assert(sink.messages.length === 1, 'A message reached the sink after it was closed');
    await compose.close();
  });

  let manager;
  const cards = () => manager.locator('#results .email-status-card');
  async function fetchEmails(status) {
    const today = sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')");
    for (const selector of ['#dateBegin', '#dateEnd']) {
      await manager.locator(selector).fill(today);
      await manager.locator(selector).press('Tab');
    }
    await manager.locator('#senderEmailAddress').selectOption(sender);
    await manager.locator('#emailStatus').selectOption(status);
    await manager.locator('#results').evaluate(results => { results.innerHTML = ''; });
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/admin/ManageEmails'), {timeout: TIMEOUT}),
      manager.locator('#btnFetch').click(),
    ]);
    h.assert(response.status() === 200, `Fetch Emails answered HTTP ${response.status()}`);
    await manager.locator('#results > *').first().waitFor({timeout: TIMEOUT});
  }

  await s.step('Manage Emails lists exactly the owned sent and failed rows for the owned sender', async () => {
    manager = await openSection('Manage Emails', '/admin/ManageEmails');
    h.assert(await manager.locator(`#senderEmailAddress option[value="${sender}"]`).count() === 1, 'Manage Emails does not offer the owned sender');
    await fetchEmails('-1');
    h.assert(await cards().count() === 2, `Expected the 2 owned log rows, Manage Emails listed ${await cards().count()}`);
    h.assert((await manager.locator(`#emailStatus${sentLog}`).innerText()).trim() === 'SUCCESS'
      && (await manager.locator(`#emailStatus${failedLog}`).innerText()).trim() === 'FAILED', 'Card statuses differ from the log rows');
    h.assert(await cards().filter({hasText: subject}).count() === 1 && await cards().filter({hasText: failedSubject}).count() === 1,
      'Card subjects differ from the log rows');
    h.assert(await manager.locator(`#btnResolve${failedLog}`).count() === 1 && await manager.locator(`#btnResolve${sentLog}`).count() === 0,
      'Resolve is not offered on exactly the failed row');
    await fetchEmails('FAILED');
    h.assert(await cards().count() === 1 && await manager.locator(`#emailStatus${failedLog}`).count() === 1, 'The FAILED filter did not isolate the failed row');
  });

  await s.step('Resolve on the failed row stores RESOLVED', async () => {
    const dialogs = await h.withExpectedDialogs(manager.page(), async () => {
      const [response] = await Promise.all([
        admin.waitForResponse(r => r.request().method() === 'POST' && (r.request().postData() || '').includes('method=setResolved'), {timeout: TIMEOUT}),
        manager.locator(`#btnResolve${failedLog}`).click(),
      ]);
      h.assert(response.status() === 200, `Resolve answered HTTP ${response.status()}`);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Resolve did not ask for confirmation');
    await expectValue(sql, `SELECT status FROM emailLog WHERE id=${failedLog}`, 'RESOLVED',
      'Resolve showed RESOLVED but the log row is still FAILED');
  });
}

if (require.main === module) runWorkflow('admin-email-config', workflow, {openPatient: true});
module.exports = {workflow};
