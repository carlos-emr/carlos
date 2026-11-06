#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Over-length patient email fields are refused, never silently truncated (#3906, issue #3905;
 * adapted from MagentaHealth/Open-O).
 *
 * CARLOS connects with jdbcCompliantTruncation=false, so an emailLog value longer than its
 * column used to be cut short with no error: the audit record no longer matched what was sent.
 * The compose page now refuses such a field before sending, and EmailSend2Action refuses it
 * again server-side before anything is persisted or sent.
 *
 * Operator path: Administration > Manage Emails > "Copy and Open as New Email to Patient"
 * opens the compose page from a logged email (the button window.opens the resend URL, which
 * the check opens the same way). Then:
 *
 *   1. a 1,100-character subject (limit 1,024) is refused by the page with a visible message
 *      and nothing is submitted;
 *   2. the same form submitted with the page script bypassed is refused by the server, which
 *      renders its own message, and no emailLog row is written;
 *   3. the server re-renders the compose form, still editable, with everything that was entered
 *      (subject, body, encrypted message, password and clue, chart option and internal comment,
 *      sender, recipient and the hidden fields a resend posts), so the provider can shorten the
 *      field and send again from the same window.
 *
 * Fixtures, all removed afterwards: a synthetic patient (runWorkflow) with an example.com email
 * address, one active sender account on an unroutable .invalid domain, and one logged email to resend.
 * No SMTP server is needed: nothing reaches the send step.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const OVERSIZE_SUBJECT = 'S'.repeat(1100);

async function workflow(session) {
  const { sql, patient, provider, marker, context, config } = session;
  // The compose page validates recipients with Commons EmailValidator, which checks the TLD against
  // the IANA list and rejects the reserved .invalid, so the patient uses the reserved documentation
  // domain example.com. A rejected recipient disables the whole form.
  const patientEmail = `${marker.toLowerCase()}@example.com`;
  sql.execute(`UPDATE demographic SET email=${h.sqlString(patientEmail)} WHERE demographic_no=${patient}`);

  const configId = sql.value(`INSERT INTO emailConfig (emailType, emailProvider, active, senderFirstName,
    senderLastName, senderEmail) VALUES ('SMTP', 'GMAIL', 1, 'Playwright', ${h.sqlString(marker)},
    ${h.sqlString(`sender-${marker.toLowerCase()}@example.invalid`)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(configId), 'the sender account fixture was not created');
  session.cleanup(() => sql.execute(`DELETE FROM emailConfig WHERE id=${configId}`));
  session.cleanup(() => sql.execute(`DELETE FROM emailLog WHERE demographicNo=${patient}`));

  const logId = sql.value(`INSERT INTO emailLog (configId, fromEmail, toEmail, subject, body, status,
    chartDisplayOption, transactionType, demographicNo, providerNo)
    VALUES (${configId}, ${h.sqlString(`sender-${marker.toLowerCase()}@example.invalid`)},
      ${h.sqlString(patientEmail)}, ${h.sqlString(marker)}, TO_BASE64('Playwright body'), 'FAILED',
      'WITHOUT_NOTE', 'DIRECT', ${patient}, ${h.sqlString(provider)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(logId), 'the logged email fixture was not created');
  const logCount = () => sql.value(`SELECT COUNT(*) FROM emailLog WHERE demographicNo=${patient}`);
  // The compose page disables itself without a sender or a valid recipient, so confirm the
  // fixtures landed where the application reads them before blaming the page.
  h.assert(sql.value(`SELECT email FROM demographic WHERE demographic_no=${patient}`) === patientEmail,
    'the patient email fixture was not stored on the demographic record');
  h.assert(sql.value(`SELECT COUNT(*) FROM emailConfig WHERE id=${configId} AND active=1`) === '1',
    'the sender account fixture is not active');

  const compose = await context.newPage();
  h.wireStrictPage(compose, 'email-compose', session.recorder);
  await h.gotoApp(compose, config.baseUrl, `/admin/ManageEmails?method=resendEmail&logId=${logId}`);
  await h.assertNotErrorPage(compose, 'email compose page');
  await compose.locator('#emailComposeForm').waitFor({ state: 'attached' });
  h.assert(await compose.locator('#subjectEmail').inputValue() === marker,
    'the compose page did not open with the logged email');
  // The page disables every field when it finds no sender, no valid recipient or an invalid one.
  // Name which of those it saw, so a fixture problem is not reported as a fill timeout.
  const readiness = await compose.evaluate(() => {
    const value = (id) => (document.getElementById(id) || {}).value;
    return {
      senders: value('totalSenderEmails'),
      recipients: value('totalRecipintEmails'),
      invalidRecipients: value('totalInvalidRecipintEmails'),
      emailError: value('isEmailError'),
      subjectDisabled: document.getElementById('subjectEmail').disabled,
    };
  });
  h.assert(!readiness.subjectDisabled,
    `the compose form opened disabled: ${JSON.stringify(readiness)}`);

  await session.step('the page refuses a 1,100-character subject before sending', async () => {
    await compose.locator('#subjectEmail').fill(OVERSIZE_SUBJECT);
    let submitted = false;
    compose.on('request', (request) => {
      if (request.method() === 'POST' && /emailSendAction/.test(request.url())) submitted = true;
    });
    await compose.locator('#btnSend').click();
    const message = compose.locator('#subjectError');
    await message.waitFor({ state: 'visible' });
    const text = await message.innerText();
    h.assert(/1,100/.test(text) && /1,024/.test(text),
      'the subject length message does not state the entered size and the limit');
    await compose.waitForTimeout(500);
    h.assert(!submitted, 'the over-length subject was submitted despite the page check');
    h.assert(logCount() === '1', 'an email was logged for the refused subject');
  });

  // Synthetic values for every other field the retry form has to hand back.
  const entered = {
    body: `Retry body ${marker}`,
    encryptedMessage: `Encrypted ${marker}`,
    password: 'Passw0rd-FAKE',
    clue: `Clue ${marker}`,
    internalComment: `Internal ${marker}`,
  };

  await session.step('the server refuses the same subject when the page check is bypassed', async () => {
    await compose.locator('#bodyEmail').fill(entered.body);
    await compose.locator('#encryptionSwitch').check();
    await compose.locator('#encryptedMessage').fill(entered.encryptedMessage);
    await compose.locator('#emailPDFPassword').fill(entered.password);
    await compose.locator('#emailPDFPasswordClue').fill(entered.clue);
    await compose.locator('#addFullNoteOption').check();
    await compose.locator('#internalComment').fill(entered.internalComment);
    await Promise.all([
      compose.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      // HTMLFormElement.submit() skips the onsubmit handler, as a scripted or replayed POST would.
      compose.evaluate(() => document.getElementById('emailComposeForm').submit()),
    ]);
    await h.assertNotErrorPage(compose, 'email send result');
    const alert = compose.locator('#emailLengthErrorMessage');
    await alert.waitFor({ state: 'visible' });
    h.assert(/1,?100/.test(await alert.innerText()), 'the server message does not state the entered size');
    h.assert(logCount() === '1', 'the server logged (and so truncated) the over-length email');
  });

  await session.step('the refused email reopens editable with everything that was entered', async () => {
    const form = await compose.evaluate(() => {
      const byId = (id) => document.getElementById(id);
      const named = (name) => Array.from(document.querySelectorAll(`#emailComposeForm [name="${name}"]`));
      const hidden = (name) => named(name).filter((el) => el.type === 'hidden').map((el) => el.value);
      return {
        action: byId('emailComposeForm').getAttribute('action') || '',
        subjectDisabled: byId('subjectEmail').disabled,
        subjectLength: byId('subjectEmail').value.length,
        subjectError: byId('subjectError').innerText,
        body: byId('bodyEmail').value,
        encryption: byId('encryptionSwitch').checked,
        isEmailEncrypted: byId('isEmailEncrypted').value,
        encryptedMessage: byId('encryptedMessage').value,
        password: byId('emailPDFPassword').value,
        clue: byId('emailPDFPasswordClue').value,
        fullNote: byId('addFullNoteOption').checked,
        internalComment: byId('internalComment').value,
        sender: byId('senderEmailAddress').value,
        recipients: hidden('receiverEmailAddress'),
        demographicId: hidden('demographicId'),
        transactionType: hidden('transactionType'),
        resultPanelShown: Boolean(document.getElementById('successMessage')),
      };
    });
    h.assert(!form.subjectDisabled, 'the refused email reopened with its fields disabled');
    h.assert(/method=sendDirectEmail/.test(form.action), 'the refused email reopened without a send action');
    h.assert(!form.resultPanelShown, 'the refused email was reported as sent');
    h.assert(form.subjectLength === OVERSIZE_SUBJECT.length, 'the refused subject was not handed back in full');
    h.assert(/1,100/.test(form.subjectError), 'the retry form does not mark the over-length subject');
    h.assert(form.body === entered.body, 'the body was not handed back');
    h.assert(form.encryption && form.isEmailEncrypted === 'true', 'the retry form dropped the encryption choice');
    h.assert(form.encryptedMessage === entered.encryptedMessage, 'the encrypted message was not handed back');
    h.assert(form.password === entered.password, 'the PDF password was not handed back');
    h.assert(form.clue === entered.clue, 'the password clue was not handed back');
    h.assert(form.fullNote && form.internalComment === entered.internalComment,
      'the chart option or internal comment was not handed back');
    h.assert(form.sender === configId, 'the sender account was not handed back');
    h.assert(form.recipients.length === 1 && form.recipients[0] === patientEmail,
      'the recipient was not handed back');
    h.assert(form.demographicId.length === 1 && form.demographicId[0] === patient,
      'the patient was not handed back');
    h.assert(form.transactionType.length === 1 && form.transactionType[0] === 'DIRECT',
      'the transaction type was not handed back');
    h.assert(logCount() === '1', 'reopening the refused email logged it');
  });
  await compose.close();
}

if (require.main === module) runWorkflow('email-field-length', workflow);
module.exports = { workflow };
