#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// The test process must run on the application host: LOCAL SMTP connects only to loopback.
// Owns its patient/eForm/sender/consent/log rows; never uses an existing sender.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureMail } = require('./lib/loopback-mail-capture');

async function workflow(s) {
  const recipient = `${s.marker.toLowerCase()}@example.com`;
  const sender = `${s.marker.toLowerCase()}-sender@example.com`;
  const consentType = s.sql.value(`SELECT ct.id FROM consentType ct JOIN property p
    ON ct.type=SUBSTRING_INDEX(SUBSTRING_INDEX(p.value,',',1),' ',1)
    WHERE p.name='email_communication' AND ct.active=1 LIMIT 1`);
  if (!/^[1-9]\d*$/.test(consentType)) throw new h.SkipCheck('Email consent must be configured with an active type');
  let fid; let configId;
  const sink = await captureMail(recipient);
  s.cleanup(() => sink.close());
  s.cleanup(() => {
    if (configId) {
      const logs = `SELECT id FROM emailLog WHERE configId=${configId} AND DemographicNo=${s.patient}`;
      h.assert(s.sql.value(`SELECT COUNT(*) FROM emailAttachment WHERE logId IN (${logs})`) === '0',
        'Unexpected email attachment requires fixture recovery');
      s.sql.execute(`DELETE FROM emailLog WHERE configId=${configId} AND DemographicNo=${s.patient}`);
      s.sql.execute(`DELETE FROM emailConfig WHERE id=${configId} AND senderEmail=${h.sqlString(sender)}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM emailConfig WHERE id=${configId}`) === '0', 'Sender fixture cleanup failed');
    }
    if (fid) {
      s.sql.execute(`DELETE FROM eform_values WHERE fid=${fid} AND demographic_no=${s.patient};
        DELETE FROM eform_data WHERE fid=${fid} AND demographic_no=${s.patient};
        DELETE FROM eform WHERE fid=${fid} AND form_name=${h.sqlString(s.marker)}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${fid}`) === '0', 'eForm fixture cleanup failed');
    }
    s.sql.execute(`DELETE FROM Consent WHERE demographic_no=${s.patient} AND consent_type_id=${consentType}`);
  });
  s.sql.execute(`UPDATE demographic SET email=${h.sqlString(recipient)} WHERE demographic_no=${s.patient};
    INSERT INTO Consent (demographic_no,consent_type_id,explicit,optout,last_entered_by,consent_date,edit_date,deleted)
    VALUES (${s.patient},${consentType},1,0,${h.sqlString(s.provider)},NOW(),NOW(),0)`);
  configId = s.sql.value(`INSERT INTO emailConfig (emailType,emailProvider,active,senderFirstName,senderLastName,senderEmail,configDetails)
    VALUES ('SMTP','LOCAL',1,'Coverage','Fixture',${h.sqlString(sender)},
      ${h.sqlString(JSON.stringify({ host: '127.0.0.1', port: String(sink.port) }))}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(configId), 'Owned loopback sender was not created');
  const html = `<!DOCTYPE html><html><head><title>Coverage mail</title></head><body>
    <form method="post" action="" name="FormName" id="FormName">
      <p>Synthetic mail workflow fixture</p><input name="subject" value="Coverage mail">
      <input type="hidden" name="attachEFormToEmail" value="false">
      <input type="hidden" name="enableEmailEncryption" value="false">
      <input type="hidden" name="encryptEmailAttachments" value="false">
      <input type="hidden" name="emailPatientChartOption" value="doNotAddAsNote">
      <input type="submit" name="SubmitButton" value="Submit">
    </form></body></html>`;
  fid = s.sql.value(`INSERT INTO eform (form_name,file_name,subject,form_html,showLatestFormOnly,patient_independent,roleType,status,form_date,form_time)
    VALUES (${h.sqlString(s.marker)},'coverage-mail.html','Coverage mail',${h.sqlString(html)},0,0,'',1,CURDATE(),CURTIME()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'Owned mail eForm was not created');
  const chart = await s.chart();
  const list = await s.popup(chart, chart.locator('a[onclick*="/eform/efmformslistadd"]').first(), 'email-eform-list');
  const form = await s.popup(list, list.getByRole('link', { name: s.marker, exact: true }), 'email-eform');
  let compose;
  await s.step('open compose through the eForm Email control with the owned patient', async () => {
    await form.locator('#remoteEmailButton').waitFor();
    await form.locator('#remoteEmailButton').click();
    await form.waitForURL(/\/email\/emailComposeAction/);
    compose = form;
    await compose.locator('#senderEmailAddress').selectOption(configId);
    h.assert(await compose.locator('[name="receiverEmailAddress"][type="hidden"]').inputValue() === recipient,
      'Compose selected a recipient other than the owned patient');
    await compose.locator('#doNotAddAsNoteOption').check();
  });
  await s.step('missing subject blocks sending and keeps the draft', async () => {
    await compose.locator('#subjectEmail').fill('');
    await compose.locator('#bodyEmail').fill('Synthetic coverage body');
    await compose.locator('#btnSend').click();
    await compose.locator('#subjectError').waitFor({ state: 'visible' });
    h.assert(sink.messages.length === 0, 'Invalid compose form sent an email');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM emailLog WHERE configId=${configId}`) === '0', 'Invalid compose form wrote a delivery log');
    h.assert(await compose.locator('#bodyEmail').inputValue() === 'Synthetic coverage body', 'Validation discarded the draft');
  });
  await s.step('send through loopback SMTP and verify delivery plus the persisted success log', async () => {
    const subject = `${s.marker} delivery`;
    await compose.locator('#subjectEmail').fill(subject);
    await compose.locator('#btnSend').click();
    await expectValue(s.sql, `SELECT COUNT(*) FROM emailLog WHERE configId=${configId} AND DemographicNo=${s.patient} AND status='SUCCESS'`,
      '1', 'Email send did not persist one success log');
    h.assert(sink.messages.length === 1, 'SMTP capture did not receive exactly one message');
    const payload = sink.messages[0].replace(/=\r\n/g, '');
    h.assert(payload.includes(subject) && payload.includes('Synthetic coverage body'), 'Captured email lost its subject or body');
    h.assert(s.sql.value(`SELECT toEmail FROM emailLog WHERE configId=${configId}`) === recipient, 'Delivery log recorded the wrong recipient');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${s.patient}`) === '0', 'Do not add as note unexpectedly wrote a clinical note');
  });
  await s.step('find the delivery in Administration email status using sender and status filters', async () => {
    const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'mail-admin' });
    const link = admin.locator('a[rel*="/admin/ManageEmails?"]');
    const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
    if (!await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
    await link.click();
    const page = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
    h.assert(page, 'Email status iframe did not load');
    const today = s.sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')");
    await page.locator('#dateBegin').fill(today);
    await page.locator('#dateEnd').fill(today);
    await page.locator('#senderEmailAddress').selectOption(sender);
    await page.locator('#emailStatus').selectOption('SUCCESS');
    await page.locator('#btnFetch').click();
    await page.locator('.email-status-card').filter({ hasText: s.marker }).first().waitFor();
    h.assert(await page.locator('.email-status-card').count() === 1, 'Sender/status filter returned unexpected delivery rows');
  });

}
if (require.main === module) runWorkflow('email-compose-send', workflow);
module.exports = { workflow };
