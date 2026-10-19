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
const fs = require('node:fs');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const h = require('./playwright-harness');
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
const MESSAGE = 'Synthetic signed upload failure probe';

/** The independently reconstructed trigger is scoped to one synthetic accession. */
function ownedTriggerStatements(triggerName, accession) {
  assert.match(triggerName, /^lab_signed_probe_[A-F0-9]{8}$/);
  assert.equal(accession, 'SF' + triggerName.slice('lab_signed_probe_'.length));
  const body = `BEGIN
          IF NEW.accessionNum=${h.sqlString(accession)} THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='${MESSAGE}';
          END IF;
        END`;
  const sql = `DELIMITER //
        CREATE TRIGGER ${triggerName} BEFORE INSERT ON hl7TextInfo FOR EACH ROW
        ${body}//
        DELIMITER ;`;
  return {body, sql};
}

/** A private, fsynced intent precedes CREATE; no credentials, keys or lab text are recorded. */
function createFaultReceipt(directory, intent) {
  assert.deepEqual(Object.keys(intent).sort(), ['accession', 'handlerSourceSha256', 'helperSourceSha256', 'logTimeZone', 'payloadSha256', 'testSourceSha256', 'triggerName']);
  assert.match(intent.handlerSourceSha256, /^[a-f0-9]{64}$/);assert.match(intent.helperSourceSha256, /^[a-f0-9]{64}$/);
  assert.match(intent.triggerName, /^lab_signed_probe_[A-F0-9]{8}$/);
  assert.equal(intent.accession, 'SF' + intent.triggerName.slice('lab_signed_probe_'.length));
  assert.match(intent.testSourceSha256, /^[a-f0-9]{64}$/);
  assert.match(intent.payloadSha256, /^[a-f0-9]{64}$/);
  const filename = h.buildArtifactPath(directory, intent.triggerName + '-fault', '.json');
  const fd = fs.openSync(filename, 'wx', 0o600);
  let closed = false;
  const record = {schemaVersion: 1, databaseMessage: MESSAGE, ...intent, intentWrittenAt: new Date().toISOString(), requests: {}};
  function save() {
    assert(!closed, 'Fault receipt is closed');
    const text = JSON.stringify(record, null, 2) + '\n';
    fs.ftruncateSync(fd, 0);fs.writeSync(fd, text, 0, 'utf8');fs.fsyncSync(fd);
  }
  save();
  return {
    filename,
    update(value) {
      const allowed = ['triggerSqlSha256','expectedTriggerBodySha256','observedTriggerBodySha256','observedTriggerScope','logCursorBeforeFault','logSource','rollbackRows','decryptedCopiesAfterFailure','triggerDropAcknowledged','triggerAbsentAfterDrop','cleanupAcknowledged'];
      assert(Object.keys(value).every(key => allowed.includes(key)), 'Unexpected private fault receipt field');
      Object.assign(record, value);save();
    },
    request(phase, value) {
      assert(['fault','retry','duplicate','wrong-signature'].includes(phase), 'Unexpected fault request phase');
      assert(Object.keys(value).every(key => ['method','path','startedAt','finishedAt','status'].includes(key)), 'Unexpected request receipt field');
      record.requests[phase] = {...record.requests[phase], ...value};save();
    },
    close() {if (!closed) {fs.closeSync(fd);closed = true;}},
  };
}

/** Validate only the two actual errors caused by this recorded owned negative request. */
function verifyOwnedFaultWindow(text, receipt, expectedSources, auditText) {
  assert.deepEqual(Object.keys(expectedSources || {}).sort(), ['handlerSourceSha256','helperSourceSha256','testSourceSha256']);
  for (const key of ['testSourceSha256','helperSourceSha256','handlerSourceSha256']) {
    assert.match(expectedSources?.[key] || '', /^[a-f0-9]{64}$/, 'Actual source bindings are required');
    assert.equal(receipt[key], expectedSources[key], 'Fault receipt source differs from the reviewed source');
  }
  const auditLines = String(auditText || '').trim().split(/\r?\n/);
  assert.equal(auditLines.length, 3, 'The raw strict audit must contain only the exact two fault signatures and summary');
  const auditSourceLines = auditLines.slice(0, 2).map(line => {
    const match = /^UNKNOWN\s+1\s+ERROR handlers\.CMLHandler \(CMLHandler\.java:(96|97)\) @AbstractDaoImpl\.persist :: org\.hibernate\.exception\.GenericJDBCException java\.sql\.SQLException$/.exec(line);
    assert(match, 'Unrelated strict audit signatures cannot be accounted');return match[1];
  });
  assert.deepEqual(auditSourceLines.sort(), ['96','97']);
  assert.match(auditLines[2], /^FAIL server log audit: 2 unexplained signature\(s\), 0 known, 2 event\(s\) since [0-9 :\-]+ UTC$/);
  const completedFields = ['schemaVersion','databaseMessage','triggerName','accession','testSourceSha256','helperSourceSha256',
    'handlerSourceSha256','payloadSha256','logTimeZone','intentWrittenAt','requests','triggerSqlSha256',
    'expectedTriggerBodySha256','observedTriggerBodySha256','observedTriggerScope','logCursorBeforeFault','logSource',
    'rollbackRows','decryptedCopiesAfterFailure','triggerDropAcknowledged','triggerAbsentAfterDrop','cleanupAcknowledged'];
  assert.deepEqual(Object.keys(receipt).sort(), completedFields.sort(), 'A complete exact fault receipt schema is required');
  assert.match(receipt.payloadSha256, /^[a-f0-9]{64}$/);
  assert.equal(receipt.logSource, 'journal unit carlos-emr.service');
  assert.match(receipt.logCursorBeforeFault, /^[A-Za-z0-9;=_-]{1,4096}$/);
  assert(Number.isFinite(Date.parse(receipt.intentWrittenAt)), 'Durable intent time is required');
  assert.deepEqual(Object.keys(receipt.requests).sort(), ['duplicate','fault','retry','wrong-signature']);
  assert.equal(receipt.schemaVersion, 1);
  assert.match(receipt.triggerName, /^lab_signed_probe_[A-F0-9]{8}$/);
  assert.equal(receipt.accession, 'SF' + receipt.triggerName.slice('lab_signed_probe_'.length));
  assert.equal(receipt.databaseMessage, MESSAGE);
  assert.match(receipt.handlerSourceSha256, /^[a-f0-9]{64}$/);assert.match(receipt.helperSourceSha256, /^[a-f0-9]{64}$/);
  assert.match(receipt.testSourceSha256, /^[a-f0-9]{64}$/);
  assert.match(receipt.triggerSqlSha256, /^[a-f0-9]{64}$/);
  const intended = ownedTriggerStatements(receipt.triggerName, receipt.accession);
  assert.equal(receipt.triggerSqlSha256, sha(intended.sql));
  assert.equal(receipt.expectedTriggerBodySha256, sha(intended.body));
  assert.equal(receipt.expectedTriggerBodySha256, receipt.observedTriggerBodySha256);
  assert.match(receipt.observedTriggerBodySha256, /^[a-f0-9]{64}$/);
  assert.deepEqual(receipt.observedTriggerScope, {table: 'hl7TextInfo', event: 'INSERT', timing: 'BEFORE'});
  assert.equal(receipt.rollbackRows, 0);assert.equal(receipt.decryptedCopiesAfterFailure, 0);
  assert.equal(receipt.triggerDropAcknowledged, true);assert.equal(receipt.triggerAbsentAfterDrop, true);
  assert.equal(receipt.cleanupAcknowledged, true);assert.equal(receipt.logTimeZone, 'UTC');
  let previousFinishedAt = Date.parse(receipt.intentWrittenAt);
  for (const [phase, status] of [['fault', 500], ['retry', 200], ['duplicate', 409], ['wrong-signature', 406]]) {
    const request = receipt.requests[phase];
    assert.deepEqual(Object.keys(request || {}).sort(), ['finishedAt','method','path','startedAt','status']);
    assert.equal(request?.method, 'POST');assert.equal(request.path, '/lab/newLabUpload');assert.equal(request.status, status);
    assert(Number.isFinite(Date.parse(request.startedAt)) && Date.parse(request.finishedAt) >= Date.parse(request.startedAt));
    assert(Date.parse(request.startedAt) >= previousFinishedAt, 'Actual requests must follow durable intent in sequential phase order');
    previousFinishedAt = Date.parse(request.finishedAt);
  }
  const request = receipt.requests.fault;
  const errors = [...text.matchAll(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) ERROR ([^\n]+)\n([\s\S]*?)(?=^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3} (?:TRACE|DEBUG|INFO|WARN|ERROR) |$(?![\s\S]))/gm)];
  assert.equal(errors.length, 2, 'Only two exact intentional CML error events are permitted');
  const lines = [];
  for (const event of errors) {
    const line = /^handlers\.CMLHandler \(CMLHandler\.java:(96|97)\) - /.exec(event[2]);
    assert(line, 'Unrelated error logger/source must remain a strict failure');lines.push(line[1]);
    assert(event[3].startsWith('org.hibernate.exception.GenericJDBCException:'));
    assert(event[3].includes('[' + MESSAGE + ']') && event[3].includes('java.sql.SQLException: ' + MESSAGE));
    const at = Date.parse(event[1].replace(' ', 'T').replace(',', '.') + 'Z');
    assert(at >= Date.parse(request.startedAt) && at <= Date.parse(request.finishedAt), 'Error falls outside actual failed request');
  }
  assert.deepEqual(lines.sort(), ['96', '97']);
  return {intentionalFaultEvents: 2, allOtherErrorsRefused: true, rawStrictAuditUnmodified: true};
}
module.exports = {createFaultReceipt, verifyOwnedFaultWindow, ownedTriggerStatements, sha};
