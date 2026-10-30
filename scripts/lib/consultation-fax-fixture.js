/*
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
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
'use strict';
/*
 * Owned fax sender accounts and the fax jobs a consultation Send queues, for consultation-fax-account and
 * consultation-fax-account-transport (issue #5278).
 *
 * The accounts are MIDDLEWARE accounts (the relay provider type the admin UI hides but the transport keeps) with no
 * inbound polling, pointed at a relay the check controls: an unroutable loopback port, or a local stub. Nothing can
 * leave the host through them. Each has its own fax number (drawn until no account or job uses it), fax user and site
 * credentials, so a job or a relay call names exactly one of them. cleanup() removes the accounts by id and name.
 *
 * The jobs are found by the owned patients. cleanup() removes each job's FaxClientLog row and the PDF it names in the
 * document store (a regular file directly inside it, never a link), then the job, and asserts none remains.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./playwright-harness');

const q = h.sqlString;

/** The server's document directory, where a queued consultation fax's PDF is written. */
const DOCUMENT_STORE = process.env.CONSULT_FAX_DOCUMENT_STORE || '/var/lib/carlos-emr/CarlosDocument/carlos/document';

/** An address nothing listens on, which the middleware client also refuses unless an operator allowlists loopback. */
const UNROUTABLE_RELAY = 'http://127.0.0.1:9/';

/**
 * A random value for an owned account's relay credentials. It authenticates nothing real: the accounts point at an
 * unroutable port or the check's own loopback stub, which only compares it with what it was given.
 */
const throwawayCredential = () => crypto.randomBytes(8).toString('hex');

/**
 * Owned sender accounts.
 *
 * @param s the runWorkflow session
 * @param {object} options
 * @param {string} options.url the relay every account points at
 * @param {Array<[string, number]>} options.labels each account's label and active flag
 * @returns {Object<string, {id, number, name, faxUser, siteUser, passwd, faxPasswd}>} by label
 */
function ownedSenders(s, { url, labels }) {
  const { sql, marker } = s;
  const prefix = `${marker.slice(-10)} Sender`;
  const senders = {};
  s.cleanup(() => {
    for (const sender of Object.values(senders)) sql.execute(`DELETE FROM fax_config WHERE id=${sender.id} AND accountName=${q(sender.name)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM fax_config WHERE accountName LIKE ${q(`${prefix}%`)}`) === '0',
      'The owned fax sender accounts were not removed');
  });
  for (const [label, active] of labels) {
    let number;
    for (let attempt = 0; attempt < 20 && !number; attempt++) {
      const candidate = `90555${String(crypto.randomInt(0, 100000)).padStart(5, '0')}`;
      if (sql.value(`SELECT (SELECT COUNT(*) FROM fax_config WHERE faxNumber=${q(candidate)})
        + (SELECT COUNT(*) FROM faxes WHERE faxline=${q(candidate)})`) === '0') number = candidate;
    }
    h.assert(number, 'No unused sender fax number was found');
    const sender = {
      number, name: `${prefix} ${label}`, faxUser: `user${label}${number.slice(-4)}`, siteUser: `site${label}${number.slice(-4)}`,
      passwd: throwawayCredential(), faxPasswd: throwawayCredential(),
    };
    sender.id = h.insertId(sql, `INSERT INTO fax_config
      (providerType,active,faxNumber,faxReply,accountName,senderEmail,faxUser,siteUser,passwd,faxPasswd,gatewayName,queue,url,download)
      VALUES ('MIDDLEWARE',${active},${q(number)},${q(number)},${q(sender.name)},'fax@example.invalid',${q(sender.faxUser)},
        ${q(sender.siteUser)},${q(sender.passwd)},${q(sender.faxPasswd)},'relay','0',${q(url)},0)`, `sender ${label}`);
    senders[label] = sender;
  }
  return senders;
}

/**
 * The fax jobs queued for the owned patients, with their audit rows and PDFs.
 *
 * @param s the runWorkflow session
 * @param {string[]} patients the owned patients' demographic numbers
 */
function ownedJobs(s, patients) {
  const { sql } = s;
  for (const patient of patients) h.assert(/^[1-9]\d*$/.test(String(patient)), 'An owned patient key is invalid');
  const where = `demographicNo IN (${patients.join(',')})`;
  s.cleanup(() => {
    for (const [id, filename] of sql.rows(`SELECT id, IFNULL(filename,'') FROM faxes WHERE ${where}`)) {
      h.assert(/^[1-9]\d*$/.test(id), 'An owned fax job id is invalid');
      if (filename) {
        h.assert(/^[A-Za-z0-9._-]{1,200}\.pdf$/.test(filename), 'An owned fax job names an unexpected file');
        const file = path.join(DOCUMENT_STORE, filename);
        if (fs.existsSync(file) && fs.lstatSync(file).isFile() && !fs.lstatSync(file).isSymbolicLink()
          && path.dirname(fs.realpathSync(file)) === fs.realpathSync(DOCUMENT_STORE)) fs.unlinkSync(file);
      }
      sql.execute(`DELETE FROM FaxClientLog WHERE faxId=${id}; DELETE FROM faxes WHERE id=${id} AND ${where}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM faxes WHERE ${where}`) === '0', 'Owned fax jobs remain');
  });
  return {
    /** Each job with its fax line, user, destination, status, patient, file, provider job id and logged referral. */
    rows: () => sql.rows(`SELECT f.id, IFNULL(f.faxline,''), IFNULL(f.user,''), IFNULL(f.destination,''), IFNULL(f.status,''),
        IFNULL(f.demographicNo,''), IFNULL(f.filename,''), IFNULL(f.jobId,''), IFNULL(f.statusString,''), IFNULL(l.requestId,'')
      FROM faxes f LEFT JOIN FaxClientLog l ON l.faxId=f.id WHERE f.${where} ORDER BY f.id`)
      .map(([id, faxline, user, destination, status, demographicNo, filename, jobId, statusString, requestId]) => (
        { id, faxline, user, destination, status, demographicNo, filename, jobId, statusString, requestId })),
    /** The bytes of the PDF a job names; SKIP when the document store is not readable from here. */
    pdf(job) {
      if (!fs.existsSync(DOCUMENT_STORE)) {
        throw new h.SkipCheck(`The document store ${DOCUMENT_STORE} is not readable here (CONSULT_FAX_DOCUMENT_STORE)`);
      }
      h.assert(/^[A-Za-z0-9._-]{1,200}\.pdf$/.test(job.filename), 'The queued job names no PDF');
      const file = path.join(DOCUMENT_STORE, job.filename);
      h.assert(fs.existsSync(file) && fs.lstatSync(file).isFile(), 'The queued job\'s PDF is not in the document store');
      return fs.readFileSync(file);
    },
  };
}

module.exports = { DOCUMENT_STORE, UNROUTABLE_RELAY, ownedJobs, ownedSenders };
