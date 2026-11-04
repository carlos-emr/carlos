/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./playwright-harness');

function identifier(value) {
  h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(value), 'Unexpected fixture schema identifier');
  return '`' + value + '`';
}
function numeric(value) {
  h.assert(/^[1-9]\d*$/.test(value), 'Invalid owned program fixture identity');
  return value;
}

/** Admit only the workflow-owned patient; never alter existing provider membership. */
function prepareIncomingFilingProgram(session) {
  const {sql, patient, provider, marker} = session;
  numeric(patient);
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker) && /^\d{1,6}$/.test(provider), 'Invalid workflow fixture owner');
  const name = `${marker}-incoming`;
  const owned = [], attempted = [];
  let finished = false, program;
  const columns = new Map();
  const patientPredicate = `demographic_no=${patient} AND last_name=${h.sqlString(marker)}
      AND first_name='Workflow' AND provider_no=${h.sqlString(provider)}`;
  function verifyPatient() {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE ${patientPredicate}`) === '1', 'Program fixture patient ownership changed');
  }
  function digestExpression(table) {
    if (!columns.has(table)) {
      const names = sql.rows(`SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME=${h.sqlString(table)} ORDER BY ORDINAL_POSITION`).map(row => identifier(row[0]));
      h.assert(names.length, 'Cannot snapshot owned program fixture schema');
      columns.set(table, `SHA2(JSON_ARRAY(${names.join(',')}),256)`);
    }
    return columns.get(table);
  }
  function capture(table, key, id) {
    numeric(id);
    const expression = digestExpression(table);
    const digest = sql.value(`SELECT ${expression} FROM ${identifier(table)} WHERE ${identifier(key)}=${id}`);
    h.assert(/^[0-9a-f]{64}$/.test(digest), 'Cannot snapshot owned program fixture row');
    const record = {table, key, id, expression, digest};
    owned.push(record);
    return record;
  }
  function insert(table, key, statement) {
    // Register intent first. An uncertain insert/snapshot is retained for recovery,
    // never guessed from a broad patient/program DELETE after a partial failure.
    attempted.push(table);
    const id = sql.value(`${statement}; SELECT LAST_INSERT_ID()`);
    return capture(table, key, id);
  }
  function externalReferences(record) {
    // Legacy relationships often lack foreign keys. Discover both declared FKs
    // and conventional identifier columns, including mixed-case legacy names.
    const catalog = sql.rows(`SELECT TABLE_NAME,COLUMN_NAME,'legacy' FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA=DATABASE() AND (LOWER(REPLACE(COLUMN_NAME,'_','')) REGEXP '(program(id|no|name)|admissionid)$'
      OR LOWER(COLUMN_NAME) IN ('intakeprogram','bedprogramlinkid'))
      UNION SELECT TABLE_NAME,COLUMN_NAME,'declared' FROM information_schema.KEY_COLUMN_USAGE
      WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME=${h.sqlString(record.table)}
      AND REFERENCED_COLUMN_NAME=${h.sqlString(record.key)}`);
    const guards = [];
    for (const [table, column, kind] of catalog) {
      identifier(table); identifier(column);
      const normalized = column.replaceAll('_', '').toLowerCase();
      if (kind === 'legacy') {
        if (record.table === 'admission' && !normalized.endsWith('admissionid')) continue;
        if (record.table === 'program' && normalized.endsWith('admissionid')) continue;
        if (record.table === 'program_provider') continue;
      }
      const target = kind === 'legacy' && normalized.endsWith('programname') ? h.sqlString(name) : record.id;
      const exclusions = owned.filter(row => row.table === table).map(row => `${identifier(row.key)}<>${row.id}`);
      const predicate = `${identifier(column)}=${target}${exclusions.length ? ' AND ' + exclusions.join(' AND ') : ''}`;
      h.assert(sql.value(`SELECT COUNT(*) FROM ${identifier(table)} WHERE ${predicate}`) === '0',
        'Owned program fixture acquired an external reference');
      // The bounded derived table prevents MySQL target-table restrictions when
      // a program's intake/bed link references another row in the same table.
      guards.push(`NOT EXISTS(SELECT 1 FROM (SELECT ${identifier(column)} FROM ${identifier(table)} WHERE ${predicate}
        LIMIT 1) AS fixture_reference)`);
    }
    return guards;
  }
  function cleanup() {
    if (finished) return;
    verifyPatient();
    h.assert(attempted.length === owned.length, 'Incomplete program fixture snapshot; retain rows for explicit recovery');
    const cleanupRows = [...owned].reverse().map(record => {
      h.assert(sql.value(`SELECT ${record.expression} FROM ${identifier(record.table)} WHERE ${identifier(record.key)}=${record.id}`) === record.digest,
        'Owned program fixture row changed; refusing cleanup');
      return {record, guards: externalReferences(record)};
    });
    for (const {record, guards} of cleanupRows) {
      const conditions = [`${identifier(record.key)}=${record.id}`, `${record.expression}=${h.sqlString(record.digest)}`,
        `EXISTS(SELECT 1 FROM demographic WHERE ${patientPredicate})`, ...guards];
      sql.execute(`DELETE FROM ${identifier(record.table)} WHERE ${conditions.join(' AND ')}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM ${identifier(record.table)} WHERE ${identifier(record.key)}=${record.id}`) === '0',
        'Owned program fixture cleanup was refused or incomplete');
    }
    finished = true;
  }
  // workflow-session unwinds callbacks in reverse order. This registration must
  // precede both our first INSERT and the caller's filing cleanup registration.
  session.cleanup(cleanup);
  verifyPatient();
  h.assert(sql.value(`SELECT COUNT(*) FROM admission WHERE client_id=${patient}`) === '0',
    'Workflow patient already has admissions; refusing to change existing enrolment');
  program = sql.value(`SELECT p.id FROM program p JOIN program_provider pp ON pp.program_id=p.id
    WHERE pp.provider_no=${h.sqlString(provider)} AND p.programStatus='active' ORDER BY p.id LIMIT 1`);
  if (!program) {
    h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE name=${h.sqlString(name)}`) === '0', 'Private fixture program name already exists');
    const facility = numeric(sql.value('SELECT id FROM Facility ORDER BY id LIMIT 1'));
    program = insert('program', 'id', `INSERT INTO program
      (facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,alcohol,
       physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax)
      VALUES (${facility},${h.sqlString(name)},'service',1,'active',0,0,0,0,0,0,'none',0,150)`).id;
    insert('program_provider', 'id', `INSERT INTO program_provider (program_id,provider_no)
      VALUES (${program},${h.sqlString(provider)})`);
  }
  numeric(program);
  insert('admission', 'am_id', `INSERT INTO admission (client_id,program_id,provider_no,admission_date,
    admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
    VALUES (${patient},${program},${h.sqlString(provider)},NOW(),0,0,'current',NOW())`);
  const membership = sql.value(`SELECT COUNT(*) FROM admission a JOIN program_provider pp ON pp.program_id=a.program_id
    WHERE a.client_id=${patient} AND a.program_id=${program} AND a.admission_status='current'
    AND pp.provider_no=${h.sqlString(provider)}`);
  h.assert(/^[1-9]\d*$/.test(membership), 'Owned patient admission did not establish the provider domain');
  return {program, cleanup};
}
module.exports = {prepareIncomingFilingProgram};
