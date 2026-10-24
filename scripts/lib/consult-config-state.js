/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * The clinic-wide state the consultation configuration actions change, and how a check puts it back.
 *
 * WHY. Enable Request/Response (encounter/EnableConRequestResponse) writes two `property` rows
 * (consultRequestEnabled, consultResponseEnabled) and activates or inactivates the "Referring Doctor"
 * consultationServices row. These helpers snapshot those rows, switch the request/response state off for a
 * probe, and restore them verbatim, so that a check which drives (or probes) the actions leaves the install
 * as it found it.
 *
 * LEGACY SCRIPT. The writes of services and consultants (Add Service, Delete Services, Update Service
 * Specialists, and the consultant, institution and department editors) used to regenerate a script of
 * services and consultants into the one specialistsJavascript row, and the editors also tried to write
 * encounter/consult.js, for no reader (ConsultationLookup2Action replaced the script; finding 260, #4617).
 * The generator is gone, so nothing in the application writes that row any more. The snapshot keeps the
 * row's MD5 (computed by MariaDB) and the restore asserts it is unchanged instead of putting the text back:
 * every check that drives the actions through these helpers is a regression check that they leave it alone.
 *
 * VALUES. `mysql -B` prints SQL NULL and the text 'NULL' alike, so each value is selected with an explicit
 * IS NULL flag: only the flag can tell the restore which one to write back.
 */

const { assert, sqlString } = require('./playwright-harness');

const FLAGS = Object.freeze(['consultRequestEnabled', 'consultResponseEnabled']);
const REFERRING = 'Referring Doctor';
/** consultationServices.active as ConsultationServiceDao writes it. */
const ACTIVE = '1';
const INACTIVE = '02';

const flagList = () => FLAGS.map(sqlString).join(',');
const notIn = (column, ids) => (ids.length ? `AND ${column} NOT IN (${ids.join(',')})` : '');

/**
 * Snapshot of the state the consultation configuration writes change, the request/response property rows and the
 * Referring Doctor service, plus the digest of the legacy specialistsJavascript row they must leave alone.
 *
 * @param sql the harness mysql client
 * @return {{properties: string[][], services: string[][], scripts: string[][]}} rows of
 *   [id, name, value, valueIsNull], [serviceId, active, activeIsNull] and [id, setId, scriptIsNull, md5]
 */
function snapshotSwitch(sql) {
  return {
    properties: sql.rows(`SELECT id,name,value,value IS NULL FROM property WHERE name IN (${flagList()}) ORDER BY id`),
    services: sql.rows(`SELECT serviceId,active,active IS NULL FROM consultationServices
      WHERE serviceDesc=${sqlString(REFERRING)} ORDER BY serviceId`),
    scripts: scriptDigests(sql),
  };
}

/** What identifies a script row without carrying its text: [id, setId, isNull, md5] (MariaDB computes the MD5). */
function scriptDigests(sql) {
  return sql.rows(`SELECT id,setId,javascriptString IS NULL,IFNULL(MD5(javascriptString),'')
    FROM specialistsJavascript ORDER BY id`);
}

/** SQL literal for a snapshotted value: the flag decides NULL; a parsed null beside flag 0 was the text 'NULL'. */
function restoredLiteral(value, isNull) {
  return isNull === '1' ? 'NULL' : sqlString(value === null ? 'NULL' : value);
}

/**
 * Bring the switch to a known "off": both flags NULL and Referring Doctor inactive, creating nothing and
 * keeping every row of `before`. For a probe that needs the write to be visible (a GET that turns the
 * flags on must find them off) and for a page flow that starts from a known state.
 */
function switchOff(sql, before) {
  sql.execute(`DELETE FROM property WHERE name IN (${flagList()}) ${notIn('id', before.properties.map((row) => row[0]))}`);
  sql.execute(`UPDATE property SET value=NULL WHERE name IN (${flagList()})`);
  sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${sqlString(REFERRING)}
    ${notIn('serviceId', before.services.map((row) => row[0]))}`);
  sql.execute(`UPDATE consultationServices SET active=${sqlString(INACTIVE)} WHERE serviceDesc=${sqlString(REFERRING)}`);
}

/**
 * Put the switch back to `before` and assert it, then assert the legacy script row was left alone. A property row
 * or a Referring Doctor row the run created is deleted and the rows that existed get their value back; the script
 * row is never written here, so a changed digest means the application rewrote it (finding 260, #4617).
 */
function restoreSwitch(sql, before) {
  const keepProps = before.properties.map((row) => row[0]);
  const keepServices = before.services.map((row) => row[0]);
  sql.execute(`DELETE FROM property WHERE name IN (${flagList()}) ${notIn('id', keepProps)}`);
  for (const [id, , value, isNull] of before.properties) {
    sql.execute(`UPDATE property SET value=${restoredLiteral(value, isNull)} WHERE id=${id}`);
  }
  sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${sqlString(REFERRING)} ${notIn('serviceId', keepServices)}`);
  for (const [id, active, isNull] of before.services) {
    sql.execute(`UPDATE consultationServices SET active=${restoredLiteral(active, isNull)} WHERE serviceId=${id}`);
  }
  const after = snapshotSwitch(sql);
  assert(JSON.stringify([after.properties, after.services]) === JSON.stringify([before.properties, before.services]),
    'The consultation request/response switch was not restored to its snapshot');
  assert(JSON.stringify(after.scripts) === JSON.stringify(before.scripts),
    'The consultation configuration actions rewrote the legacy specialistsJavascript script (finding 260, #4617)');
}

module.exports = {
  ACTIVE, FLAGS, INACTIVE, REFERRING,
  restoreSwitch, scriptDigests, snapshotSwitch, switchOff,
};
