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
 *      renders its own message, and no emailLog row is written.
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

  const compose = await context.newPage();
  h.wireStrictPage(compose, 'email-compose', session.recorder);
  await h.gotoApp(compose, config.baseUrl, `/admin/ManageEmails?method=resendEmail&logId=${logId}`);
  await h.assertNotErrorPage(compose, 'email compose page');
  await compose.locator('#emailComposeForm').waitFor({ state: 'attached' });
  h.assert(await compose.locator('#subjectEmail').inputValue() === marker,
    'the compose page did not open with the logged email');

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

  await session.step('the server refuses the same subject when the page check is bypassed', async () => {
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
  await compose.close();
}

if (require.main === module) runWorkflow('email-field-length', workflow);
module.exports = { workflow };
