/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const positiveId = value => {
  assert(/^[1-9]\d*$/.test(String(value)), 'fixture identifiers must be positive integers');
  return String(value);
};
const decode = value => {
  assert(value === 'NULL' || /^(?:[0-9A-Fa-f]{2})*$/.test(value), 'invalid hexadecimal snapshot');
  return value === 'NULL' ? 'NULL' : `UNHEX('${value}')`;
};

/** Owns only its inserted IDs; restores exact review fields for the frozen source chain. */
function createLabRoutingFixture(sql, rows, provider, labs) {
  const providerNo = String(provider);
  assert(/^\d+$/.test(providerNo) && Number(providerNo) > 0, 'fixture provider identifiers must be positive integers');
  const labIds = [...new Set(labs.map(positiveId))];
  assert(labIds.length > 0, 'a lab fixture needs a source chain');
  const scope = `provider_no='${providerNo}' AND lab_type='HL7' AND lab_no IN (${labIds.join(',')})`;
  const marker = `PW_LABFIX_${crypto.randomUUID().replaceAll('-', '')}`;
  // HEX preserves NULL, empty text, literal "NULL", quotes, tabs and line breaks without
  // depending on mysql batch-mode escaping. It also preserves the original review timestamp.
  const original = rows(`SELECT id, lab_no, IFNULL(HEX(status),'NULL'), IFNULL(HEX(comment),'NULL'),
    IFNULL(HEX(timestamp),'NULL') FROM providerLabRouting WHERE ${scope} ORDER BY id`);
  assert(original.every(row => row.length === 5), 'incomplete routing snapshot');
  for (const [id, lab, ...values] of original) {
    positiveId(id);
    assert(labIds.includes(positiveId(lab)), 'snapshot includes a foreign source');
    values.forEach(decode);
  }
  const inserted = new Set();
  const recoverInserted = () => {
    for (const [id] of rows(`SELECT id FROM providerLabRouting WHERE ${scope} AND comment='${marker}'`)) {
      inserted.add(positiveId(id));
    }
  };
  return {
    prepare(resetExisting = true) {
      for (const lab of labIds) {
        if (!original.some(row => String(row[1]) === lab)) {
          // Marker is allocated before INSERT, so cleanup recovers a committed insert whose
          // acknowledgement was lost. Every older version is routed before acknowledgement,
          // preventing the application from creating untracked rows when it files the chain.
          sql(`INSERT INTO providerLabRouting (provider_no,lab_no,status,comment,lab_type)
            VALUES ('${providerNo}',${lab},'N','${marker}','HL7')`);
          recoverInserted();
        }
      }
      if (resetExisting) sql(`UPDATE providerLabRouting SET status='N' WHERE ${scope}`);
    },
    cleanup() {
      const failures = [];
      const attempt = task => { try { task(); } catch (error) { failures.push(error); } };
      attempt(recoverInserted);
      for (const id of inserted) attempt(() => sql(`DELETE FROM providerLabRouting WHERE id=${id} AND ${scope}`));
      for (const [id, lab, status, comment, timestamp] of original) {
        attempt(() => sql(`UPDATE providerLabRouting SET status=${decode(status)}, comment=${decode(comment)},
          timestamp=${decode(timestamp)} WHERE id=${id} AND lab_no=${lab} AND ${scope}`));
      }
      if (failures.length) throw new AggregateError(failures, 'Lab routing fixture cleanup failed');
    },
  };
}
module.exports = {createLabRoutingFixture};
