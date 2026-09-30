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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for the signed lab feed, lab/newLabUpload (issue #4086): a failed upload must
 * never make the sender's retry look already delivered.
 *
 * Senders post an AES-encrypted lab, its RSA-wrapped AES key and an MD5withRSA signature, and
 * set use_http_response_code, so the HTTP status is the whole answer: 200 delivered, 409
 * "uploaded previously" (the sender stops retrying), 500 retry. Before #4086 a failure after the
 * content's checksum was recorded could leave that checksum behind, and the sender's retry of
 * the same file was answered 409 for a lab that never reached an inbox.
 *
 * Steps (all posts carry the session's CSRF token, through the front door when BASE_URL is :443):
 *   1. A database failure after the checksum is recorded (a trigger scoped to this run's
 *      accession rejects the lab's hl7TextInfo row) is answered 500, and leaves no checksum,
 *      no lab rows and no decrypted copy in DOCUMENT_DIR.
 *   2. The sender's retry of the same bytes is answered 200, files the lab and routes it to the
 *      run's FAKE- patient.
 *   3. Sending it once more is answered 409 and stores nothing new.
 *   4. The same content with a wrong signature is answered 406 and leaves no decrypted copy.
 *
 * The lab is the same SYNTHETIC CML HL7 message as lab-upload-playwright-checks.js, with a
 * per-run accession, so every row it creates can be found and removed. It is never a real result.
 *
 * FIXTURE SAFETY: registers a sender under a per-run service name with a throwaway RSA key and
 * removes it in cleanup. If the server has no lab-upload key pair ('oscar' in oscarKeys) the
 * check installs a throwaway one and removes it again, only if it is still the one it installed.
 * An existing server key is read (to derive its public key) but never changed or logged. Lab,
 * routing, measurement and fileUploadCheck rows are removed by this run's accession and content
 * checksum, pass or fail. Decrypted copies in DOCUMENT_DIR are named after the servlet
 * container's temp file, not the lab, so they are found by content: with
 * LAB_UPLOAD_DOCUMENT_STORE set, cleanup deletes the LabUpload.* files whose bytes are this run's
 * lab and asserts none remain; without it the per-step "no decrypted copy" assertions are skipped
 * and say so.
 *
 * Requires CREATE/DROP TRIGGER privileges in the isolated test database (step 1), like
 * lab-upload-rollback-playwright-checks.js.
 *
 * Environment (beyond the common contract in lib/playwright-harness.js):
 *   LAB_UPLOAD_DOCUMENT_STORE  the server's DOCUMENT_DIR, mounted or local.
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { syntheticCmlLab, openUploader, contextPathOf } = require('./lab-upload-playwright-checks');

/** Encrypts and signs a lab the way the legacy sender protocol does. */
function sealForUpload(plaintext, serverPublicKey, senderPrivateKey) {
  const aesKey = crypto.randomBytes(16);
  // Java's Cipher.getInstance("AES") is AES/ECB/PKCS5Padding; the legacy wire format fixes it.
  const cipher = crypto.createCipheriv('aes-128-ecb', aesKey, null);
  const encrypted = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const wrappedKey = crypto.publicEncrypt({ key: serverPublicKey, padding: crypto.constants.RSA_PKCS1_PADDING }, aesKey);
  const signature = crypto.sign('md5', plaintext, senderPrivateKey);
  return { encrypted, key: wrappedKey.toString('base64'), signature: signature.toString('base64') };
}

/** POSTs a sealed lab to lab/newLabUpload from a logged-in page and returns the HTTP status. */
async function postSigned(page, contextPath, sealed, service) {
  const result = await page.evaluate(async ({ context, content, key, signature, serviceName }) => {
    const tokenInput = document.querySelector('input[name="CSRF-TOKEN"]');
    const bytes = Uint8Array.from(atob(content), (c) => c.charCodeAt(0));
    const form = new FormData();
    form.append('importFile', new Blob([bytes], { type: 'application/octet-stream' }), 'signed-lab.enc');
    form.append('key', key);
    form.append('signature', signature);
    form.append('service', serviceName);
    form.append('use_http_response_code', 'true');
    const headers = { 'X-Requested-With': 'XMLHttpRequest' };
    if (tokenInput && tokenInput.value) headers['CSRF-TOKEN'] = tokenInput.value;
    const response = await fetch(`${context}/lab/newLabUpload`, {
      method: 'POST', body: form, headers, credentials: 'same-origin',
    });
    await response.text();
    return { status: response.status, hadToken: !!(tokenInput && tokenInput.value) };
  }, {
    context: contextPath, content: sealed.encrypted.toString('base64'), key: sealed.key,
    signature: sealed.signature, serviceName: service,
  });
  h.assert(result.hadToken, 'The uploader page carried no CSRF token to send with the signed post');
  return result.status;
}

async function workflow(session) {
  const { sql, patient, marker, cleanup, config } = session;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const accession = `SF${stamp}`;
  const service = `lab-upload-probe-${stamp}`;
  const failureTrigger = `lab_signed_probe_${stamp}`;
  const plaintext = Buffer.from(syntheticCmlLab(accession, marker), 'latin1');
  // FileUploadCheck keys content by MD5 (a duplicate index, not a security control).
  const md5 = crypto.createHash('md5').update(plaintext).digest('hex');
  const ownChecksum = `md5sum=${h.sqlString(md5)}`;
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE ? fs.realpathSync(process.env.LAB_UPLOAD_DOCUMENT_STORE) : null;
  let triggerMayExist = false;
  let installedServerKey = null;

  /** This run's decrypted copies in DOCUMENT_DIR, found by content. */
  const ownCopies = () => fs.readdirSync(store)
    .filter((name) => /^LabUpload\.[A-Za-z0-9_.-]+$/.test(name))
    // name matched the fixed pattern above and is joined to the resolved store root.
    .map((name) => path.join(store, name)) // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    .filter((file) => {
      try {
        const stat = fs.statSync(file);
        return stat.isFile() && stat.size === plaintext.length && fs.readFileSync(file).equals(plaintext);
      } catch { return false; }
    });
  const expectCopies = (count, why) => {
    if (!store) {
      console.log(`    SKIP decrypted-copy count (${why}): LAB_UPLOAD_DOCUMENT_STORE is not set`);
      return;
    }
    const found = ownCopies().length;
    h.assert(found === count, `${why}: expected ${count} decrypted copies in DOCUMENT_DIR, found ${found}`);
  };

  h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownChecksum}`) === '0',
    'A fileUploadCheck row already carries this run\'s content');

  const sender = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  let serverPrivate = sql.value(`SELECT privKey FROM oscarKeys WHERE name='oscar'`);
  if (!serverPrivate) {
    const server = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
    installedServerKey = {
      pub: server.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
      priv: server.privateKey.export({ type: 'pkcs8', format: 'der' }).toString('base64'),
    };
    sql.execute(`INSERT INTO oscarKeys (name, pubKey, privKey)
      VALUES ('oscar', ${h.sqlString(installedServerKey.pub)}, ${h.sqlString(installedServerKey.priv)})`);
    serverPrivate = installedServerKey.priv;
  }
  const serverPublicKey = crypto.createPublicKey(crypto.createPrivateKey({
    key: Buffer.from(serverPrivate, 'base64'), format: 'der', type: 'pkcs8',
  }));
  serverPrivate = null;
  sql.execute(`INSERT INTO publicKeys (service, type, pubKey, privateKey)
    VALUES (${h.sqlString(service)}, 'CML',
      ${h.sqlString(sender.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'))}, '')`);

  cleanup(async () => {
    if (triggerMayExist) sql.execute(`DROP TRIGGER IF EXISTS ${failureTrigger}`);
    sql.execute(`DELETE FROM publicKeys WHERE service=${h.sqlString(service)}`);
    if (installedServerKey) {
      sql.execute(`DELETE FROM oscarKeys WHERE name='oscar' AND privKey=${h.sqlString(installedServerKey.priv)}`);
    }
    const labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}
      UNION SELECT lab_id FROM hl7TextMessage WHERE FROM_BASE64(message) LIKE ${h.sqlString(`%${accession}%`)}`)
      .map(([labNo]) => labNo).filter((labNo) => /^\d+$/.test(labNo));
    if (labs.length) {
      const list = labs.join(',');
      const measurements = sql.rows(`SELECT measurement_id FROM measurementsExt
        WHERE keyval='lab_no' AND val IN (${labs.map((labNo) => h.sqlString(labNo)).join(',')})`)
        .map(([id]) => id).filter((id) => /^\d+$/.test(id));
      if (measurements.length) {
        const ids = measurements.join(',');
        sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (${ids});
          DELETE FROM measurements WHERE id IN (${ids})`);
      }
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
        DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
        DELETE FROM hl7TextInfo WHERE lab_no IN (${list});
        DELETE FROM hl7TextMessage WHERE lab_id IN (${list})`);
    }
    sql.execute(`DELETE FROM fileUploadCheck WHERE ${ownChecksum}`);
    if (store) {
      for (const file of ownCopies()) fs.unlinkSync(file);
      h.assert(ownCopies().length === 0, 'This run\'s decrypted lab copies were not all removed');
    } else {
      console.warn('    decrypted copies retained: set LAB_UPLOAD_DOCUMENT_STORE to remove them');
    }
    h.assert(sql.value(`SELECT
        (SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)})
      + (SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownChecksum})
      + (SELECT COUNT(*) FROM publicKeys WHERE service=${h.sqlString(service)})
      + (SELECT COUNT(*) FROM hl7TextMessage WHERE FROM_BASE64(message) LIKE ${h.sqlString(`%${accession}%`)})`) === '0',
    'The synthetic signed-feed rows were not all removed');
  });

  const { inbox, popup } = await openUploader(session);
  cleanup(async () => {
    await popup.close().catch(() => {});
    await inbox.close().catch(() => {});
  });
  const contextPath = contextPathOf(config.baseUrl);
  const sealed = sealForUpload(plaintext, serverPublicKey, sender.privateKey);

  await session.step('a failure after the checksum is recorded is answered 500 and leaves nothing', async () => {
    // Scoped to this run's accession: rejects the lab's metadata row after the checksum and the
    // raw message were inserted, the half-failed upload #4086 describes.
    triggerMayExist = true;
    try {
      sql.execute(`DELIMITER //
        CREATE TRIGGER ${failureTrigger} BEFORE INSERT ON hl7TextInfo FOR EACH ROW
        BEGIN
          IF NEW.accessionNum=${h.sqlString(accession)} THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic signed upload failure probe';
          END IF;
        END//
        DELIMITER ;`);
      const status = await postSigned(popup, contextPath, sealed, service);
      h.assert(status === 500, `A failed signed upload answered HTTP ${status}; a sender must be told to retry`);
      h.assert(sql.value(`SELECT
          (SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownChecksum})
        + (SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)})
        + (SELECT COUNT(*) FROM hl7TextMessage WHERE FROM_BASE64(message) LIKE ${h.sqlString(`%${accession}%`)})`) === '0',
      'The failed upload left its checksum or partial lab rows, which would refuse the retry');
      expectCopies(0, 'after the failed upload');
    } finally {
      sql.execute(`DROP TRIGGER IF EXISTS ${failureTrigger}`);
      triggerMayExist = false;
    }
  });

  await session.step('the sender\'s retry of the same file is delivered, not answered 409', async () => {
    const status = await postSigned(popup, contextPath, sealed, service);
    h.assert(status === 200, `The retry answered HTTP ${status} instead of 200`);
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`, '1',
      'The retried lab did not reach hl7TextInfo under its accession');
    await expectValue(sql, `SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownChecksum}`, '1',
      'The delivered lab recorded no checksum');
    const matched = sql.value(`SELECT COUNT(*) FROM patientLabRouting pl JOIN hl7TextInfo t
      ON t.lab_no=pl.lab_no AND pl.lab_type='HL7'
      WHERE t.accessionNum=${h.sqlString(accession)} AND pl.demographic_no=${patient}`);
    h.assert(matched === '1', `The delivered lab was not routed to the run's patient (matched ${matched})`);
    expectCopies(1, 'after the delivered upload');
  });

  await session.step('a real duplicate is answered 409 and stores nothing new', async () => {
    const status = await postSigned(popup, contextPath, sealed, service);
    h.assert(status === 409, `A delivered lab sent again answered HTTP ${status} instead of 409`);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`) === '1',
      'The duplicate filed a second copy of the lab');
    expectCopies(1, 'after the duplicate');
  });

  await session.step('a wrong signature is answered 406 and leaves no decrypted copy', async () => {
    const forged = { ...sealed, signature: crypto.sign('md5', Buffer.from(`not ${accession}`), sender.privateKey).toString('base64') };
    const status = await postSigned(popup, contextPath, forged, service);
    h.assert(status === 406, `A wrongly signed lab answered HTTP ${status} instead of 406`);
    expectCopies(1, 'after the wrongly signed upload');
  });
}

if (require.main === module) runWorkflow('lab-upload-signed-feed', workflow);
module.exports = { workflow, sealForUpload };
