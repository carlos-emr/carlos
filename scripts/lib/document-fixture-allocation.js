/*
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
'use strict';
const assert = require('node:assert/strict');

const MAX_DOCUMENT_ID = 2147483647;
function identifier(value) {
  assert(typeof value === 'string' && /^[A-Za-z_][A-Za-z0-9_]*$/.test(value), 'Unfamiliar document reference identifier');
  return '`' + value + '`';
}
function integer(value, label, allowZero = false) {
  assert(typeof value === 'string' && /^(?:0|[1-9]\d*)$/.test(value), `Invalid ${label}`);
  const parsed = Number(value);
  assert(Number.isSafeInteger(parsed) && parsed <= MAX_DOCUMENT_ID && (allowZero || parsed > 0), `Out-of-range ${label}`);
  return parsed;
}

/** Same legacy-name and declared-FK inventory used by the cleanup refusal guard. */
function documentReferenceColumns(sql) {
  const references = sql.rows(`SELECT c.TABLE_NAME,c.COLUMN_NAME,0 AS declared_fk FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
    WHERE c.TABLE_SCHEMA=DATABASE() AND t.TABLE_TYPE='BASE TABLE'
    AND LOWER(REPLACE(c.COLUMN_NAME,'_','')) IN ('documentno','documentid','docno','docid')
    UNION ALL SELECT TABLE_NAME,COLUMN_NAME,1 AS declared_fk FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='document' AND REFERENCED_COLUMN_NAME='document_no'`);
  assert(Array.isArray(references) && references.length > 0, 'Document reference inventory is unavailable');
  for (const row of references) {
    assert(Array.isArray(row) && row.length === 3 && ['0', '1'].includes(row[2]), 'Document reference provenance is unavailable');
    identifier(row[0]); identifier(row[1]);
  }
  return references;
}

/** Read-only allocation plan. Even preexisting dangling references keep their identities. */
function planDocumentFixtureId(sql) {
  const scopes = new Map();
  function add(table, column, where = '1') {
    identifier(table); identifier(column);
    scopes.set(`${table}\0${column}\0${where}`, {table, column, where});
  }
  for (const [table, column] of documentReferenceColumns(sql)) add(table, column);
  add('document', 'document_no');
  add('providerLabRouting', 'lab_no', "lab_type='DOC'");
  add('patientLabRouting', 'lab_no', "lab_type='DOC'");
  // This table has no lab_type discriminator, so all existing lock identities count.
  add('providerLabRoutingLock', 'lab_no');
  add('casemgmt_note_link', 'table_id', 'table_name=5');
  let maximumReference = 0;
  const references = [];
  for (const {table, column, where} of scopes.values()) {
    const target = identifier(column), from = identifier(table);
    // Reject unfamiliar textual/negative/fractional identities rather than rely on
    // MySQL's lossy numeric coercion of values such as '22-other'.
    const invalid = sql.value(`SELECT COUNT(*) FROM ${from} WHERE (${where}) AND ${target} IS NOT NULL
      AND CAST(${target} AS CHAR) NOT REGEXP '^[0-9]+$'`);
    assert.equal(invalid, '0', 'Unfamiliar document reference value');
    const maximum = integer(sql.value(`SELECT COALESCE(MAX(CAST(${target} AS UNSIGNED)),0) FROM ${from} WHERE ${where}`),
      'document reference maximum', true);
    maximumReference = Math.max(maximumReference, maximum);
    references.push({table, column, where, maximum});
  }
  const nextAutoIncrement = integer(sql.value(`SELECT AUTO_INCREMENT FROM information_schema.TABLES
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document'`), 'document auto increment');
  const sourceId = Math.max(nextAutoIncrement, maximumReference + 1);
  assert(sourceId < MAX_DOCUMENT_ID, 'No safe document identity range remains for source and split');
  return {sourceId: String(sourceId), nextAutoIncrement, maximumReference, references};
}

function assertSplitFixtureId(sourceId, splitId) {
  assert(integer(splitId, 'split document identity') > integer(sourceId, 'source document identity'),
    'Split reused an identity at or below the reserved source range');
}

module.exports = {MAX_DOCUMENT_ID, documentReferenceColumns, planDocumentFixtureId, assertSplitFixtureId};
