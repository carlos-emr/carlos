/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * The clinic-wide state the consultation configuration actions change, and how a check puts it back.
 *
 * WHY. Enable Request/Response (encounter/EnableConRequestResponse) writes two `property` rows
 * (consultRequestEnabled, consultResponseEnabled) and activates or inactivates the "Referring Doctor"
 * consultationServices row; the writes of services and consultants (Add Service, Delete Services, Update
 * Service Specialists, and the consultant, institution and department editors) regenerate the legacy script
 * of services and consultants into specialistsJavascript (EctConConstructSpecialistsScriptsFile.makeString,
 * finding 260). Nothing reads that script any more (ConsultationLookup2Action replaced it), but a write
 * changes it, so a check that drives the actions would leave the clinic's row naming the services it
 * created. These helpers snapshot the rows the actions rewrite, switch the request/response state off for a
 * probe, and restore everything verbatim, so that a check which drives (or probes) the actions leaves the
 * install as it found it.
 *
 * VALUES. `mysql -B` prints SQL NULL and the text 'NULL' alike, so each value is selected with an explicit
 * IS NULL flag: only the flag can tell the restore which one to write back. The script is kept as the hex of
 * its bytes and put back in pieces that never split a UTF-8 character (one statement must fit in an
 * argument of the mysql client), and the restore asserts the MD5 of the row, computed by MariaDB.
 */

const { assert, sqlString } = require('./playwright-harness');

const FLAGS = Object.freeze(['consultRequestEnabled', 'consultResponseEnabled']);
const REFERRING = 'Referring Doctor';
/** consultationServices.active as ConsultationServiceDao writes it. */
const ACTIVE = '1';
const INACTIVE = '02';

const flagList = () => FLAGS.map(sqlString).join(',');
const notIn = (column, ids) => (ids.length ? `AND ${column} NOT IN (${ids.join(',')})` : '');
const NUMBER = /^[1-9]\d*$/;
/** Bytes of a script put back per statement: its hex must fit in one argument of the mysql client (128 KiB). */
const CHUNK_BYTES = 40000;

/**
 * Snapshot of the state the consultation configuration writes change: the request/response property rows, the
 * Referring Doctor service and the legacy specialistsJavascript script.
 *
 * @param sql the harness mysql client
 * @return {{properties: string[][], services: string[][], scripts: string[][]}} rows of
 *   [id, name, value, valueIsNull], [serviceId, active, activeIsNull] and [id, setId, hexOfScript, scriptIsNull, md5]
 */
function snapshotSwitch(sql) {
  return {
    properties: sql.rows(`SELECT id,name,value,value IS NULL FROM property WHERE name IN (${flagList()}) ORDER BY id`),
    services: sql.rows(`SELECT serviceId,active,active IS NULL FROM consultationServices
      WHERE serviceDesc=${sqlString(REFERRING)} ORDER BY serviceId`),
    scripts: sql.rows(`SELECT id,setId,IFNULL(HEX(javascriptString),''),javascriptString IS NULL,IFNULL(MD5(javascriptString),'')
      FROM specialistsJavascript ORDER BY id`),
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
 * Split the hex of a UTF-8 text into pieces of at most `maxBytes` bytes that never end inside a character
 * (a continuation byte, 10xxxxxx, belongs to the character before it), upper-case hex, in order.
 */
function utf8Chunks(hex, maxBytes = CHUNK_BYTES) {
  assert(/^(?:[0-9A-Fa-f]{2})*$/.test(hex), 'utf8Chunks needs the hex of whole bytes');
  assert(Number.isInteger(maxBytes) && maxBytes >= 4, 'utf8Chunks needs room for one four-byte character');
  const bytes = Buffer.from(hex, 'hex');
  const chunks = [];
  let start = 0;
  while (start < bytes.length) {
    let end = Math.min(start + maxBytes, bytes.length);
    while (end < bytes.length && (bytes[end] & 0xC0) === 0x80) end--;
    assert(end > start, 'utf8Chunks found no character boundary; the text is not UTF-8');
    chunks.push(bytes.subarray(start, end).toString('hex').toUpperCase());
    start = end;
  }
  return chunks;
}

/** Put the legacy script back, verbatim, in every snapshotted row it differs in. */
function restoreScripts(sql, before) {
  const current = new Map(scriptDigests(sql).map(([id, , isNull, md5]) => [id, `${isNull}:${md5}`]));
  for (const [id, , hex, isNull, md5] of before.scripts) {
    assert(NUMBER.test(id), 'restoreScripts needs numeric row ids');
    if (current.get(id) === `${isNull}:${md5}`) continue;
    if (isNull === '1') {
      sql.execute(`UPDATE specialistsJavascript SET javascriptString=NULL WHERE id=${id}`);
      continue;
    }
    sql.execute(`UPDATE specialistsJavascript SET javascriptString='' WHERE id=${id}`);
    for (const chunk of utf8Chunks(hex)) {
      sql.execute(`UPDATE specialistsJavascript SET javascriptString=CONCAT(javascriptString,CONVERT(UNHEX('${chunk}') USING utf8mb4))
        WHERE id=${id}`);
    }
  }
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
 * Put the switch and the script back to `before` and assert them. A property row or a Referring Doctor row the
 * run created is deleted; the rows that existed get their value back, and the script returns to its exact text.
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
  restoreScripts(sql, before);
  const after = snapshotSwitch(sql);
  assert(JSON.stringify([after.properties, after.services]) === JSON.stringify([before.properties, before.services]),
    'The consultation request/response switch was not restored to its snapshot');
  assert(JSON.stringify(scriptDigests(sql)) === JSON.stringify(before.scripts.map(([id, setId, , isNull, md5]) => [id, setId, isNull, md5])),
    'The legacy consultation script (specialistsJavascript) was not restored to its snapshot');
}

module.exports = {
  ACTIVE, FLAGS, INACTIVE, REFERRING,
  restoreScripts, restoreSwitch, scriptDigests, snapshotSwitch, switchOff, utf8Chunks,
};
