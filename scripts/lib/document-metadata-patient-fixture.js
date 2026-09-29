/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createHash} = require('node:crypto');
const h = require('./playwright-harness');
const mixed = require('./mixed-engine-owned-cleanup');
const NORMALIZED = ['phone', 'phone2', 'chart_no', 'family_doctor', 'alias', 'pronoun', 'gender'];
const PATIENT_COLUMNS = new Set(['demographicno', 'demographicid', 'demographicnumber', 'demono', 'patientid', 'patientno', 'clientid', 'clientno']);
function positive(value) {assert(/^[1-9][0-9]*$/.test(String(value)), 'Invalid owned patient identity'); return String(value);}
function schemaColumns(schema, table) {
  const columns = schema.filter(row => row[0] === table && row[9] === 'BASE TABLE').map(row => row[1]);
  assert(columns.length, 'Owned patient schema missing'); columns.forEach(mixed.identifier); return columns;
}
function parentReferences(schema, foreign, patient) {
  positive(patient);
  const scopes = new Map();
  function add(table, where) {
    schemaColumns(schema, table);
    if (!scopes.has(table)) scopes.set(table, new Set());
    scopes.get(table).add(where);
  }
  for (const [table, column, , , , , , , , type] of schema) {
    if (type !== 'BASE TABLE' || table === 'log') continue; // Immutable audit is retained.
    const normalized = column.replaceAll('_', '').toLowerCase();
    if (table === 'demographic' && column === 'demographic_no') continue;
    if (PATIENT_COLUMNS.has(normalized) || (/demographic/i.test(table) && normalized === 'mergedto')) {
      add(table, `${mixed.identifier(column)}=${patient}`);
    }
  }
  for (const [table, column, target, key] of foreign) {
    // A declared audit FK is a real parent reference: retain it and refuse the
    // parent deletion. Only conventional, undeclared read-audit IDs are exempt.
    if (target !== 'demographic') continue;
    assert.equal(key, 'demographic_no', 'Unreviewed patient reference key');
    if (table === 'demographic' && column === 'demographic_no') continue;
    add(table, `${mixed.identifier(column)}=${patient}`);
  }
  add('ctl_document', `module='demographic' AND module_id=${patient}`);
  add('casemgmt_note_link', `table_name=7 AND table_id=${patient}`);
  return [...scopes].map(([table, conditions]) => ({table, where: [...conditions].map(value => '(' + value + ')').join(' OR '), count: 0}));
}
function preflightMetadataPatient(sql) {
  const schema = mixed.readSchema(sql), foreign = mixed.readForeign(sql);
  const columns = schemaColumns(schema, 'demographic');
  for (const column of ['demographic_no', 'last_name', 'first_name', 'provider_no', ...NORMALIZED]) {
    assert(columns.includes(column), 'Metadata patient initialization schema is incomplete');
  }
  const references = parentReferences(schema, foreign, '1');
  const tables = [...new Set(['demographic', ...references.map(reference => reference.table)])];
  mixed.capability(sql, schema, ['demographic'], tables, foreign);
  return {schema, foreign, tables};
}

/** Lifecycle registered by workflow-session before its first possible INSERT. */
function createMetadataPatientFixture({sql, marker, provider}) {
  assert(/^FAKE-PW[a-f0-9]{16}$/.test(marker) && /^[0-9]{1,6}$/.test(provider), 'Invalid private patient fixture owner');
  const plan = preflightMetadataPatient(sql);
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'stored-metadata-patient-owned-'));
  fs.chmodSync(directory, 0o700);
  const journal = path.join(directory, 'journal.json');
  let patient, hash, attempted = false, ready = false, cleaning = false, cleaned = false;
  const schemaDigest = mixed.signature(plan.schema), foreignDigest = mixed.signature(plan.foreign);
  const digest = `SHA2(JSON_ARRAY(${schemaColumns(plan.schema, 'demographic').map(mixed.identifier).join(',')}),256)`;
  function checkpoint(phase, extra = {}) {
    const temporary = journal + '.new';
    const descriptor = fs.openSync(temporary, 'wx', 0o600);
    try {
      fs.writeFileSync(descriptor, JSON.stringify({phase, marker, provider, patient, hash, attempted, ready, cleaning, cleaned,
        schemaDigest, foreignDigest, ...extra}, null, 2));
      fs.fsyncSync(descriptor);
    } finally {fs.closeSync(descriptor);}
    fs.renameSync(temporary, journal);
    const parent = fs.openSync(directory, 'r');
    try {fs.fsyncSync(parent);} finally {fs.closeSync(parent);}
  }
  function predicate() {
    positive(patient);
    assert(/^[a-f0-9]{64}$/.test(hash || ''), 'Original patient snapshot missing');
    return `demographic_no=${patient} AND last_name=${h.sqlString(marker)} AND first_name='Workflow'
      AND provider_no=${h.sqlString(provider)} AND ${digest}=${h.sqlString(hash)}`;
  }
  function verifyOwner() {
    assert(ready && !cleaning && !cleaned, `Patient creation/cleanup is unconfirmed; preserve ${journal}`);
    assert.equal(sql.value(`SELECT COUNT(*) FROM demographic WHERE ${predicate()}`), '1', 'Owned metadata patient changed; retain it');
  }
  checkpoint('planned');
  return {
    journal,
    create() {
      assert(!attempted && !ready && !cleaned, 'Patient fixture insertion cannot be replayed');
      attempted = true; checkpoint('inserting');
      const columns = ['last_name', 'first_name', 'year_of_birth', 'month_of_birth', 'date_of_birth', 'sex', 'patient_status',
        'provider_no', 'hc_type', 'province', 'roster_status', 'lastUpdateDate', ...NORMALIZED];
      // These are initialization values, never a repair of an existing row.
      const values = [h.sqlString(marker), "'Workflow'", "'1980'", "'01'", "'02'", "'F'", "'AC'", h.sqlString(provider),
        "'ON'", "'ON'", "'NR'", 'NOW()', ...NORMALIZED.map(() => "''")];
      patient = positive(sql.value(`INSERT INTO demographic (${columns.map(mixed.identifier).join(',')}) VALUES (${values.join(',')}); SELECT LAST_INSERT_ID()`));
      checkpoint('inserted');
      hash = sql.value(`SELECT ${digest} FROM demographic WHERE demographic_no=${patient}`);
      assert(/^[a-f0-9]{64}$/.test(hash), 'Original metadata patient snapshot unavailable');
      // A private immutable original is never advanced after normal application reads.
      const original = path.join(directory, 'original.json');
      const descriptor = fs.openSync(original, 'wx', 0o600);
      try {fs.writeFileSync(descriptor, JSON.stringify({patient, marker, provider, hash, schemaDigest, foreignDigest})); fs.fsyncSync(descriptor);}
      finally {fs.closeSync(descriptor);}
      ready = true;
      try {checkpoint('ready');} catch (error) {ready = false; throw error;}
      return patient;
    },
    verifyOwner,
    cleanup({browserClosed}) {
      assert(browserClosed === true, 'Metadata browser close is unconfirmed; retain owned parent');
      verifyOwner();
      const fresh = preflightMetadataPatient(sql);
      assert.equal(mixed.signature(fresh.schema), schemaDigest, 'Patient cleanup schema changed');
      assert.equal(mixed.signature(fresh.foreign), foreignDigest, 'Patient cleanup reference catalog changed');
      const checks = [...parentReferences(plan.schema, plan.foreign, patient), {table: 'demographic', where: predicate(), count: 1}];
      const query = mixed.build({...plan, owned: ['demographic'], checks,
        deletes: [{table: 'demographic', where: predicate(), expected: 1}]});
      cleaning = true;
      const querySha256 = createHash('sha256').update(query).digest('hex');
      checkpoint('cleanup-dispatch', {querySha256});
      // One fresh CLI connection; no generic fallback and no replay after a lost
      // acknowledgement. The SQL handler rolls back failures before releasing locks.
      assert.equal(sql.value(query), '1', 'Exact patient cleanup was not confirmed');
      cleaned = true; cleaning = false;
      checkpoint('cleaned', {querySha256});
    },
  };
}
module.exports = {NORMALIZED, parentReferences, preflightMetadataPatient, createMetadataPatientFixture};
