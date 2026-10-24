/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
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
'use strict';
const { randomInt } = require('node:crypto');
const h = require('./playwright-harness');

/** Own an MRP with no forwarding; never alter the login provider's genuine rules. */
function prepareIsolatedMrp(session) {
  const { sql, patient, marker, provider } = session;
  h.assert(/^[1-9]\d*$/.test(String(patient)) && /^FAKE-PW[a-f0-9]{16}$/.test(marker), 'Invalid owned MRP fixture');
  const q = h.sqlString;
  const patientScope = `demographic_no=${patient} AND last_name=${q(marker)}`;
  h.assert(sql.value(`SELECT provider_no FROM demographic WHERE ${patientScope}`) === provider,
    'Owned patient no longer has its initial provider');
  let number;
  for (let attempt = 0; attempt < 50; attempt++) {
    const candidate = String(randomInt(700000, 989000));
    const quoted = q(candidate);
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${quoted})
      +(SELECT COUNT(*) FROM security WHERE provider_no=${quoted})
      +(SELECT COUNT(*) FROM demographic WHERE provider_no=${quoted})
      +(SELECT COUNT(*) FROM property WHERE provider_no=${quoted})
      +(SELECT COUNT(*) FROM providerLabRouting WHERE provider_no=${quoted})
      +(SELECT COUNT(*) FROM program_provider WHERE provider_no=${quoted})
      +(SELECT COUNT(*) FROM incomingLabRules WHERE provider_no=${quoted} OR frwdProvider_no=${quoted})`) === '0') {
      number = candidate; break;
    }
  }
  h.assert(number, 'No isolated unused provider identity was available');
  const ownedScope = `provider_no=${q(number)} AND last_name=${q(marker)} AND first_name='Isolated MRP'
    AND provider_type='doctor' AND specialty='GP' AND sex='F' AND status='1' AND lastUpdateUser=${q(provider)}`;
  const columns = sql.rows(`SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='provider' ORDER BY ORDINAL_POSITION`).map(([name]) => {
    h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(name), 'Unexpected provider schema');return '`' + name + '`';
  });
  h.assert(columns.length, 'Cannot snapshot provider schema');
  const digest = `SHA2(JSON_ARRAY(${columns.join(',')}),256)`;
  let inserted = false, fingerprint, finished = false;
  function readFingerprint() { return sql.value(`SELECT ${digest} FROM provider WHERE ${ownedScope}`); }
  function noRules() {
    h.assert(sql.value(`SELECT COUNT(*) FROM incomingLabRules WHERE provider_no=${q(number)} OR frwdProvider_no=${q(number)}`) === '0',
      'Isolated MRP acquired a forwarding rule; preserving it for recovery');
  }
  function verify() {
    h.assert(sql.value(`SELECT provider_no FROM demographic WHERE ${patientScope}`) === number,
      'Owned patient MRP changed');
    h.assert(fingerprint && readFingerprint() === fingerprint, 'Owned MRP provider row changed');
    noRules();
  }
  session.cleanup(() => {
    if (finished || !inserted) return;
    const exists = sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(number)}`);
    if (exists === '0') {
      h.assert(sql.value(`SELECT provider_no FROM demographic WHERE ${patientScope}`) === provider,
        'Missing MRP provider still has an owned patient reference');
      finished = true; return;
    }
    const current = readFingerprint();
    h.assert(/^[0-9a-f]{64}$/.test(current) && (!fingerprint || current === fingerprint),
      'MRP provider ownership changed; refusing cleanup');
    // A lost INSERT acknowledgement is recoverable by exact pre-registered identity/marker/fields.
    fingerprint = current;
    const mrp = sql.value(`SELECT provider_no FROM demographic WHERE ${patientScope}`);
    h.assert(mrp === provider || mrp === number, 'Owned patient provider changed; refusing cleanup');
    noRules();
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE provider_no=${q(number)} AND NOT(${patientScope}))
      +(SELECT COUNT(*) FROM security WHERE provider_no=${q(number)})
      +(SELECT COUNT(*) FROM property WHERE provider_no=${q(number)})
      +(SELECT COUNT(*) FROM providerLabRouting WHERE provider_no=${q(number)})
      +(SELECT COUNT(*) FROM program_provider WHERE provider_no=${q(number)})`) === '0',
    'Isolated MRP acquired external references; refusing cleanup');
    if (mrp === number) {
      sql.execute(`UPDATE demographic SET provider_no=${q(provider)} WHERE ${patientScope} AND provider_no=${q(number)}`);
      h.assert(sql.value(`SELECT provider_no FROM demographic WHERE ${patientScope}`) === provider,
        'Owned patient MRP restore failed');
    }
    sql.execute(`DELETE FROM provider WHERE ${ownedScope} AND ${digest}=${q(fingerprint)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(number)}`) === '0', 'Owned MRP cleanup failed');
    finished = true;
  });
  inserted = true; // registered in the cleanup closure before INSERT, including lost replies
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
    VALUES (${q(number)},${q(marker)},'Isolated MRP','doctor','GP','F','1',${q(provider)},NOW())`);
  fingerprint = readFingerprint();h.assert(/^[0-9a-f]{64}$/.test(fingerprint), 'Owned provider snapshot missing');
  sql.execute(`UPDATE demographic SET provider_no=${q(number)} WHERE ${patientScope} AND provider_no=${q(provider)}`);
  verify();
  return { number, verify };
}
module.exports = { prepareIsolatedMrp };
