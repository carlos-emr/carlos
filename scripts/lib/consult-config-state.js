/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * The clinic-wide state the consultation configuration actions change, and how a check puts it back.
 *
 * WHY. Enable Request/Response (encounter/EnableConRequestResponse) writes two `property` rows
 * (consultRequestEnabled, consultResponseEnabled) and activates or inactivates the "Referring Doctor"
 * consultationServices row; the writes of services and consultants (Add Service, Delete Services, Update
 * Service Specialists, and the consultant, institution and department editors) regenerate the legacy script
 * of services and consultants into specialistsJavascript (EctConConstructSpecialistsScriptsFile.makeString).
 * Nothing reads that script any more (ConsultationLookup2Action replaced it), but it keeps the name of
 * every service a check created until the next write, so a fixture row that was deleted is still named
 * in a table that outlives it.
 * These helpers snapshot the rows the actions rewrite, switch the request/response state off for a probe,
 * and restore it, so that a check which drives (or probes) the actions leaves the install as it found it.
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
const NUMBER = /^[1-9]\d*$/;

/**
 * Snapshot of the clinic-wide request/response switch: the property rows and the Referring Doctor service.
 *
 * @param sql the harness mysql client
 * @return {{properties: string[][], services: string[][]}} rows of [id, name, value, valueIsNull] and
 *   [serviceId, active, activeIsNull]
 */
function snapshotSwitch(sql) {
  return {
    properties: sql.rows(`SELECT id,name,value,value IS NULL FROM property WHERE name IN (${flagList()}) ORDER BY id`),
    services: sql.rows(`SELECT serviceId,active,active IS NULL FROM consultationServices
      WHERE serviceDesc=${sqlString(REFERRING)} ORDER BY serviceId`),
  };
}

/** SQL literal for a snapshotted value: the flag decides NULL; a parsed null beside flag 0 was the text 'NULL'. */
function restoredLiteral(value, isNull) {
  return isNull === '1' ? 'NULL' : sqlString(value === null ? 'NULL' : value);
}

/**
 * The PCRE that matches one service's block in the legacy script: its K line, its D lines and the blank
 * line that ends the block (EctConConstructSpecialistsScriptsFile writes exactly that for each service).
 */
function scriptBlockPattern(serviceId) {
  assert(NUMBER.test(String(serviceId)), 'scriptBlockPattern needs a numeric service id');
  return `(?m)^K\\(${serviceId},[^\\n]*\\n(?:D\\(${serviceId},[^\\n]*\\n)*\\n`;
}

/** Delete the blocks of the given services from the legacy specialistsJavascript script. */
function removeScriptBlocks(sql, serviceIds) {
  for (const id of serviceIds) {
    assert(NUMBER.test(String(id)), 'removeScriptBlocks needs numeric service ids');
    sql.execute(`UPDATE specialistsJavascript SET javascriptString=REGEXP_REPLACE(javascriptString,
      ${sqlString(scriptBlockPattern(id))}, '') WHERE javascriptString LIKE ${sqlString(`%K(${id},%`)}`);
  }
}

/** How many lines of the legacy script still belong to one of the given services (0 when none). */
function scriptLinesFor(sql, serviceIds) {
  if (!serviceIds.length) return 0;
  assert(serviceIds.every((id) => NUMBER.test(String(id))), 'scriptLinesFor needs numeric service ids');
  return Number(sql.value(`SELECT COUNT(*) FROM specialistsJavascript
    WHERE javascriptString REGEXP ${sqlString(`(?m)^[KD]\\((${serviceIds.join('|')}),`)}`));
}

/** Referring Doctor rows that are not in `before`: the ones a run created. */
function createdReferringServices(sql, before) {
  const keep = before.services.map((row) => row[0]);
  return sql.rows(`SELECT serviceId FROM consultationServices
    WHERE serviceDesc=${sqlString(REFERRING)} ${notIn('serviceId', keep)}`).map((row) => row[0]);
}

/**
 * Bring the switch to a known "off": both flags NULL and Referring Doctor inactive, creating nothing and
 * keeping every row of `before`. For a probe that needs the write to be visible (a GET that turns the
 * flags on must find them off) and for a page flow that starts from a known state.
 */
function switchOff(sql, before) {
  sql.execute(`DELETE FROM property WHERE name IN (${flagList()}) ${notIn('id', before.properties.map((row) => row[0]))}`);
  sql.execute(`UPDATE property SET value=NULL WHERE name IN (${flagList()})`);
  removeScriptBlocks(sql, createdReferringServices(sql, before));
  sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${sqlString(REFERRING)}
    ${notIn('serviceId', before.services.map((row) => row[0]))}`);
  sql.execute(`UPDATE consultationServices SET active=${sqlString(INACTIVE)} WHERE serviceDesc=${sqlString(REFERRING)}`);
}

/**
 * Put the switch back to `before` and assert it. A property row or a Referring Doctor row the run created
 * is deleted (and its block dropped from the legacy script); the rows that existed get their value back.
 */
function restoreSwitch(sql, before) {
  const keepProps = before.properties.map((row) => row[0]);
  const keepServices = before.services.map((row) => row[0]);
  sql.execute(`DELETE FROM property WHERE name IN (${flagList()}) ${notIn('id', keepProps)}`);
  for (const [id, , value, isNull] of before.properties) {
    sql.execute(`UPDATE property SET value=${restoredLiteral(value, isNull)} WHERE id=${id}`);
  }
  const created = createdReferringServices(sql, before);
  removeScriptBlocks(sql, created);
  sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${sqlString(REFERRING)} ${notIn('serviceId', keepServices)}`);
  for (const [id, active, isNull] of before.services) {
    sql.execute(`UPDATE consultationServices SET active=${restoredLiteral(active, isNull)} WHERE serviceId=${id}`);
  }
  assert(JSON.stringify(snapshotSwitch(sql)) === JSON.stringify(before),
    'The consultation request/response switch was not restored to its snapshot');
  assert(scriptLinesFor(sql, created) === 0, 'The legacy consultation script still names a Referring Doctor service the run created');
}

module.exports = {
  ACTIVE, FLAGS, INACTIVE, REFERRING,
  createdReferringServices, removeScriptBlocks, restoreSwitch, scriptBlockPattern, scriptLinesFor, snapshotSwitch, switchOff,
};
