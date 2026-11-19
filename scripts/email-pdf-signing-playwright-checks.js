#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Signed email PDFs on the installed WAR (#3216, #3851).
 *
 * Runs only in the managed `pdf-signing` property profile, which turns PDF signing on and points it
 * at a keystore path this check owns. It sends an eForm by email from the E-Chart, as a clinician
 * does, through a loopback SMTP sink, and checks what CARLOS handed its transport:
 *
 *   1. Signing on, keystore missing: the send fails closed with "Failed to sign email PDF
 *      attachment". Nothing reaches the transport and nothing is audited as signed.
 *   2. A FAKE ECDSA P-256 keystore provisioned at that path (read on every send, so no restart): the
 *      eForm PDF the transport received carries one PAdES (ETSI.CAdES.detached) signature whose
 *      ByteRange covers the whole file and whose CMS verifies over exactly those bytes (openssl,
 *      chain trust not checked: the certificate is self-signed). The send has one
 *      EmailManager.signAttachment audit row naming the email log, the eForm record and the key ID
 *      computed from the keystore's certificate.
 *
 * Fixtures: the workflow's FAKE patient, an owned eForm template, an owned sender account pointing at
 * the sink, the FAKE keystore and, when none is saved, a FAKE clinic email footer. Cleanup removes the
 * owned email logs, attachments, archives, eForm instances, template, sender and keystore, and asserts
 * each is gone. Audit rows are append-only and are left in place. Disposable installs only, never
 * beside another check (EXCLUSIVE=1): the owned sender is offered clinic-wide while it runs.
 */
'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { ensureClinicEmailFooter } = require('./lib/clinic-email-footer-fixture');
const { createEmailArchiveCleanup, preflightEmailArchives } = require('./lib/email-archive-fixture');
const { runWorkflow } = require('./lib/workflow-session');
const { captureMail } = require('./lib/loopback-mail-capture');
const PROPERTY_PROFILES = require('./lib/property-profiles');

const PROFILE = 'pdf-signing';
const TIMEOUT = 30000;
const SIGNING_FAILURE = 'Failed to sign email PDF attachment';
const AUDIT_ACTION = 'EmailManager.signAttachment';
const TEMPLATE_HTML = '<html><head><title>Signed email fixture</title></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="note" id="note"><input type="submit" value="Submit" id="SubmitButton">'
  + '</form></body></html>';

function profileSettings(env = process.env) {
  return PROPERTY_PROFILES.propertiesFor(PROFILE, env);
}

function preflight(env = process.env, file = '/etc/carlos-emr/carlos.properties') {
  h.assert(env.EXCLUSIVE === '1', 'PDF signing validation requires EXCLUSIVE=1 on a disposable deployment');
  if (env.CARLOS_PROPERTY_PROFILE_ACTIVE !== PROFILE) {
    throw new h.SkipCheck('PDF signing validation requires the managed pdf-signing profile; run through the suite with --profile pdf-signing');
  }
  // The suite verifies the private handoff and restarts the application. Recheck the installed
  // settings before this child creates any fixture.
  PROPERTY_PROFILES.handoffRecord(PROFILE, env, file);
  h.assert(typeof process.getuid === 'function' && process.getuid() === 0,
    'PDF signing validation provisions a keystore the application reads, so it must run as root in the install');
  preflightEmailArchives({ env });
}

function keytool() {
  for (const candidate of ['/usr/lib/jvm/java-25-openjdk-amd64/bin/keytool', '/usr/bin/keytool']) {
    if (fs.existsSync(candidate)) return candidate;
  }
  throw new Error('keytool from the installed Java runtime was not found');
}

/** Creates the FAKE signing keystore the profile names, readable by the application's group only. */
function provisionKeystore(settings, marker, scratch) {
  const keystore = settings['pdf.signing.keystore.path'];
  const password = settings['pdf.signing.keystore.password'];
  const staged = path.join(scratch, 'pdf-signing.p12');
  execFileSync(keytool(), ['-genkeypair', '-alias', settings['pdf.signing.key.alias'], '-keyalg', 'EC',
    '-groupname', 'secp256r1', '-sigalg', 'SHA256withECDSA', '-validity', '2',
    '-dname', `CN=${marker} FAKE validation signing, O=FAKE Validation Clinic`,
    '-ext', 'KeyUsage:critical=digitalSignature',
    '-storetype', 'PKCS12', '-keystore', staged, '-storepass:env', 'CARLOS_FAKE_STOREPASS'],
  { env: { ...process.env, CARLOS_FAKE_STOREPASS: password }, stdio: 'pipe', timeout: 60000 });
  const pem = execFileSync(keytool(), ['-exportcert', '-rfc', '-alias', settings['pdf.signing.key.alias'],
    '-storetype', 'PKCS12', '-keystore', staged, '-storepass:env', 'CARLOS_FAKE_STOREPASS'],
  { env: { ...process.env, CARLOS_FAKE_STOREPASS: password }, encoding: 'utf8', timeout: 60000 });
  fs.copyFileSync(staged, keystore, fs.constants.COPYFILE_EXCL);
  execFileSync('chown', ['root:carlos', keystore], { timeout: 10000 });
  fs.chmodSync(keystore, 0o640);
  // The key ID myCarlos pins and the audit row records: b64url(SHA-256(SubjectPublicKeyInfo DER)).
  const spki = new crypto.X509Certificate(pem).publicKey.export({ type: 'spki', format: 'der' });
  return crypto.createHash('sha256').update(spki).digest('base64url');
}

/** The base64 PDF parts of one captured RFC 5322 message, decoded. */
function pdfAttachments(message) {
  const pdfs = [];
  const parts = message.split(/\r\n--[^\r\n]+\r\n/);
  for (const part of parts) {
    const split = part.indexOf('\r\n\r\n');
    if (split < 0) continue;
    const headers = part.slice(0, split);
    if (!/^Content-Type:\s*application\/pdf/im.test(headers)) continue;
    h.assert(/^Content-Transfer-Encoding:\s*base64/im.test(headers), 'A PDF attachment is not base64 encoded');
    const body = part.slice(split + 4).split(/\r\n--/)[0].replace(/[^A-Za-z0-9+/=]/g, '');
    pdfs.push(Buffer.from(body, 'base64'));
  }
  return pdfs;
}

/** DER length of the CMS object at the start of the zero-padded /Contents value. */
function derLength(der) {
  h.assert(der[0] === 0x30, 'The signature contents are not a DER SEQUENCE');
  if (der[1] < 0x80) return 2 + der[1];
  const octets = der[1] & 0x7f;
  h.assert(octets > 0 && octets <= 4, 'The signature contents have an invalid DER length');
  let length = 0;
  for (let i = 0; i < octets; i += 1) length = (length * 256) + der[2 + i];
  return 2 + octets + length;
}

/** Verifies the one signature in a PDF over exactly the bytes its ByteRange names. */
function verifySignedPdf(pdf, scratch, label) {
  const text = pdf.toString('latin1');
  h.assert(text.startsWith('%PDF-'), `The ${label} attachment is not a PDF`);
  const ranges = [...text.matchAll(/\/ByteRange\s*\[\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*\]/g)];
  h.assert(ranges.length === 1, `The ${label} PDF carries ${ranges.length} signatures; expected exactly one`);
  h.assert(/\/SubFilter\s*\/ETSI\.CAdES\.detached/.test(text), `The ${label} PDF signature is not PAdES (ETSI.CAdES.detached)`);
  const [a, b, c, d] = ranges[0].slice(1).map(Number);
  h.assert(a === 0 && c + d === pdf.length && b < c, `The ${label} signature does not cover the whole file`);
  const gap = text.slice(b, c);
  h.assert(/^<[0-9A-Fa-f]+>$/.test(gap), `The ${label} signature gap does not hold exactly the signature`);
  const padded = Buffer.from(gap.slice(1, -1), 'hex');
  const signature = padded.subarray(0, derLength(padded));
  const signatureFile = path.join(scratch, `${label}.p7s`);
  const contentFile = path.join(scratch, `${label}.bin`);
  fs.writeFileSync(signatureFile, signature, { mode: 0o600 });
  fs.writeFileSync(contentFile, Buffer.concat([pdf.subarray(a, a + b), pdf.subarray(c, c + d)]), { mode: 0o600 });
  // -noverify skips only the chain: the FAKE certificate is self-signed. The signature itself, its
  // signed attributes and the content digest must verify, or openssl exits non-zero.
  execFileSync('openssl', ['cms', '-verify', '-binary', '-inform', 'DER', '-in', signatureFile,
    '-content', contentFile, '-noverify', '-purpose', 'any', '-out', path.join(scratch, `${label}.out`)],
  { stdio: 'pipe', timeout: 30000 });
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  h.assert(process.env.EXCLUSIVE === '1', 'PDF signing validation requires EXCLUSIVE=1 on a disposable deployment');
  // CARLOS opens the SMTP connection to the runner's loopback, so both must be the same host.
  if (!['localhost', '127.0.0.1', '::1', '[::1]'].includes(s.config.baseUrl.hostname.toLowerCase())) {
    throw new h.SkipCheck('The SMTP sink binds the runner\'s loopback, which CARLOS can reach only when BASE_URL is loopback');
  }
  const settings = profileSettings();
  const keystore = settings['pdf.signing.keystore.path'];
  h.assert(!fs.existsSync(keystore), `A keystore this check does not own is already at ${keystore}`);
  // Send stays disabled until a clinic email footer is saved (#3981).
  ensureClinicEmailFooter(s);
  const removeArchives = createEmailArchiveCleanup(sql);
  const hex = marker.slice(-16);
  const recipient = `patient-${hex}@example.com`;
  const sender = `sender-${hex}@example.com`;
  const formName = `${marker} Signed Email`;
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-pdf-signing-'));
  const sink = await captureMail(recipient);
  let configId;
  let fid;
  let keystoreOwned = false;

  s.cleanup(() => fs.rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    if (!keystoreOwned) return;
    fs.rmSync(keystore, { force: true });
    h.assert(!fs.existsSync(keystore), 'The FAKE signing keystore was not removed');
  });
  s.cleanup(async () => {
    await sink.close();
    const logs = sql.rows(`SELECT id FROM emailLog WHERE demographicNo=${patient}`).map(row => row[0]);
    for (const id of logs) h.assert(/^[1-9]\d*$/.test(id), 'Owned email log id is invalid');
    removeArchives({ patient, marker, logIds: logs });
    if (logs.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE table_name=12 AND table_id IN (${logs.join(',')});
        DELETE FROM emailAttachment WHERE logId IN (${logs.join(',')});
        DELETE FROM emailLog WHERE id IN (${logs.join(',')}) AND demographicNo=${patient}`);
    }
    const fdids = sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient}`).map(row => row[0]);
    if (fdids.length) {
      sql.execute(`DELETE FROM eform_values WHERE fdid IN (${fdids.join(',')});
        DELETE FROM eform_data WHERE fdid IN (${fdids.join(',')}) AND demographic_no=${patient}`);
    }
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    if (configId) sql.execute(`DELETE FROM emailConfig WHERE id=${configId} AND senderEmail=${q(sender)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM emailLog WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM emailConfig WHERE senderEmail=${q(sender)})`) === '0',
    'Owned email, eForm or sender rows were not removed');
  });

  sql.execute(`UPDATE demographic SET email=${q(recipient)} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  // Above every configId an emailLog row names: demo rows can name config ids with no config row.
  configId = sql.value(`INSERT INTO emailConfig(id,emailType,emailProvider,active,senderFirstName,senderLastName,senderEmail,configDetails)
    SELECT GREATEST((SELECT COALESCE(MAX(id),0) FROM emailConfig),(SELECT COALESCE(MAX(configId),0) FROM emailLog))+1,
      'SMTP','LOCAL',1,${q(marker)},'Sink',${q(sender)},${q(JSON.stringify({ host: '127.0.0.1', port: String(sink.port) }))};
    SELECT id FROM emailConfig WHERE senderEmail=${q(sender)} AND senderFirstName=${q(marker)}`);
  h.assert(/^[1-9]\d*$/.test(configId), 'Owned sender account was not created');
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
    status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'',${q(formName)},CURDATE(),CURTIME(),${q(provider)},
    1,${q(TEMPLATE_HTML)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'Owned eForm template was not created');

  const auditRows = () => sql.rows(`SELECT contentId, data FROM log WHERE action=${q(AUDIT_ACTION)}
    AND demographic_no=${patient} ORDER BY id`);

  // E-Chart ▸ eForms "+" ▸ owned eForm ▸ Email, answering the opt-in prompt as a clinician does.
  async function compose(label) {
    const chart = await s.chart();
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
    h.assert(await form.locator('.attachmentName').count() === 1, `The ${label} composer does not list exactly the eForm`);
    await form.locator('#senderEmailAddress').selectOption(configId);
    await form.locator('#subjectEmail').fill(`${marker} ${label}`);
    await form.locator('#message').fill(`${marker} body ${label}`);
    await form.locator('#consentOverride').check();
    await form.locator('#consentOverrideReason').fill(`${marker} synthetic consent confirmation`);
    await form.locator('#doNotAddAsNoteOption').check();
    return form;
  }

  async function send(composer) {
    await Promise.all([composer.waitForURL(/\/email\/emailSendAction/, { timeout: TIMEOUT }), composer.locator('#btnSend').click()]);
    return composer.locator('#isEmailSuccessful').inputValue();
  }

  await s.step('with signing on and no keystore, the send fails closed and nothing reaches the transport', async () => {
    const composer = await compose('unsigned');
    h.assert(await send(composer) === 'false', 'A send that could not be signed was reported as sent');
    const alert = (await composer.locator('.alert-danger').innerText()).replace(/\s+/g, ' ');
    h.assert(alert.includes(SIGNING_FAILURE), `The refused send does not say signing failed: ${alert}`);
    h.assert(sink.messages.length === 0, `An unsigned attachment reached the transport (${sink.messages.length} messages)`);
    h.assert(sql.value(`SELECT status FROM emailLog WHERE demographicNo=${patient} ORDER BY id DESC LIMIT 1`) === 'FAILED',
      'The refused send did not record a definite FAILED status');
    h.assert(auditRows().length === 0, 'An attachment that was not signed was audited as signed');
    await composer.close();
  });

  let keyId;
  await s.step('with the keystore provisioned, the transport receives a verifiable PAdES-signed eForm and the send is audited', async () => {
    keystoreOwned = true;
    keyId = provisionKeystore(settings, marker, scratch);
    const composer = await compose('signed');
    const errors = () => composer.locator('.alert-danger:visible').allTextContents();
    h.assert(await send(composer) === 'true', `The signed send was refused: ${(await errors()).join(' ')}`);
    await composer.locator('#successMessage').waitFor({ timeout: TIMEOUT });
    h.assert(sink.messages.length === 1, `The transport received ${sink.messages.length} messages; expected exactly one`);
    const pdfs = pdfAttachments(sink.messages[0]);
    h.assert(pdfs.length === 1, `The delivered email carries ${pdfs.length} PDF attachments; expected the eForm`);
    verifySignedPdf(pdfs[0], scratch, 'eform');

    const log = sql.value(`SELECT id FROM emailLog WHERE demographicNo=${patient} AND status='SUCCESS' ORDER BY id DESC LIMIT 1`);
    h.assert(/^[1-9]\d*$/.test(log), 'The signed send has no SUCCESS email log');
    const record = sql.value(`SELECT a.documentId FROM emailAttachment a JOIN eform_data d ON d.fdid=a.documentId
      WHERE a.logId=${log} AND a.documentType='EFORM' AND d.demographic_no=${patient}`);
    h.assert(/^[1-9]\d*$/.test(record), 'The signed send did not log its own patient\'s eForm');
    // LogAction writes audit rows in the background.
    const deadline = Date.now() + TIMEOUT;
    let rows = auditRows();
    while (rows.length === 0 && Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 500));
      rows = auditRows();
    }
    h.assert(rows.length === 1, `The signed send has ${rows.length} ${AUDIT_ACTION} audit rows; expected one`);
    const [contentId, data] = rows[0];
    const expected = `emailLogId=${log}&source=eForm&recordId=${record}&kid=${keyId}&documentId=&version=&encrypted=false`;
    h.assert(contentId === log && data === expected, `The signing audit row does not match the send: ${data}`);
  });
}

if (require.main === module) {
  runWorkflow('email-pdf-signing', workflow, { openPatient: true, preflightConfig: () => preflight() });
}
module.exports = { workflow, preflight, pdfAttachments, verifySignedPdf, derLength };
