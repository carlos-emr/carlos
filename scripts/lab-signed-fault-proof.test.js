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
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const {createFaultReceipt, verifyOwnedFaultWindow, ownedTriggerStatements, sha} = require('./lib/lab-signed-fault-proof');
const sources = {testSourceSha256: 'a'.repeat(64), helperSourceSha256: 'b'.repeat(64), handlerSourceSha256: 'c'.repeat(64)};
function valid() {
  const requests = {};
  let second = 31;
  for (const [phase,status] of [['fault',500],['retry',200],['duplicate',409],['wrong-signature',406]]) {
    requests[phase] = {method:'POST',path:'/lab/newLabUpload',status,startedAt:`2026-10-19T18:55:${second}.000Z`,finishedAt:`2026-10-19T18:55:${second+2}.000Z`};
    second += 3;
  }
  return {schemaVersion: 1,triggerName: 'lab_signed_probe_A1B2C3D4',accession: 'SFA1B2C3D4',...sources,
    payloadSha256: 'd'.repeat(64),intentWrittenAt:'2026-10-19T18:55:30.000Z',logCursorBeforeFault:'s=0123456789abcdef;i=1;b=abcdef;m=123;t=456;x=789',logSource:'journal unit carlos-emr.service',databaseMessage: 'Synthetic signed upload failure probe',triggerSqlSha256: sha(ownedTriggerStatements('lab_signed_probe_A1B2C3D4','SFA1B2C3D4').sql),
    expectedTriggerBodySha256: sha(ownedTriggerStatements('lab_signed_probe_A1B2C3D4','SFA1B2C3D4').body),observedTriggerBodySha256: sha(ownedTriggerStatements('lab_signed_probe_A1B2C3D4','SFA1B2C3D4').body),
    observedTriggerScope: {table: 'hl7TextInfo',event: 'INSERT',timing: 'BEFORE'},rollbackRows: 0,decryptedCopiesAfterFailure: 0,
    triggerDropAcknowledged: true,triggerAbsentAfterDrop: true,cleanupAcknowledged: true,logTimeZone: 'UTC',requests};
}
function audit() {
  return [96,97].map(line => `UNKNOWN     1  ERROR handlers.CMLHandler (CMLHandler.java:${line}) @AbstractDaoImpl.persist :: org.hibernate.exception.GenericJDBCException java.sql.SQLException`).join('\n')
    + '\nFAIL server log audit: 2 unexplained signature(s), 0 known, 2 event(s) since 2026-10-19 18:55:06 UTC\n';
}
function errors() {
  return [96,97].map((line,index) => `2026-10-19 18:55:32,${index}00 ERROR handlers.CMLHandler (CMLHandler.java:${line}) - Error\norg.hibernate.exception.GenericJDBCException: could not execute statement [Synthetic signed upload failure probe]\nCaused by: java.sql.SQLException: Synthetic signed upload failure probe\n`).join('');
}

test('private fsynced intent precedes fault work, persists actual request receipts and excludes secret fields', t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(),'lab-signed-proof-'));t.after(() => fs.rmSync(dir,{recursive:true,force:true}));
  const r = valid();const intent = {triggerName:r.triggerName,accession:r.accession,...sources,payloadSha256:r.payloadSha256,logTimeZone:'UTC'};
  const proof = createFaultReceipt(dir,intent);
  assert.equal(fs.statSync(proof.filename).mode & 0o777,0o600);
  assert.equal(JSON.parse(fs.readFileSync(proof.filename,'utf8')).triggerName,r.triggerName);
  proof.request('fault',r.requests.fault);proof.update({rollbackRows:0});
  assert.equal(JSON.parse(fs.readFileSync(proof.filename,'utf8')).requests.fault.status,500);
  assert.throws(() => proof.update({privateKey:'must-not-be-serialized'}),/Unexpected/);
  assert.throws(() => proof.request('fault',{cookies:'must-not-be-serialized'}),/Unexpected/);
  proof.close();assert(!fs.readFileSync(proof.filename,'utf8').includes('must-not-be-serialized'));
  assert.throws(() => createFaultReceipt(dir,intent),/EEXIST/);
  assert.throws(() => createFaultReceipt(dir,{...intent,accession:'Foreign'}));
});

test('only the exact source-bound pair within the actual500 request is recognized without changing raw audit', () => {
  assert.deepEqual(verifyOwnedFaultWindow(errors(),valid(),sources,audit()),{intentionalFaultEvents:2,allOtherErrorsRefused:true,rawStrictAuditUnmodified:true});
});

test('unrelated SQL errors, loggers, messages, duplicate events and out-of-request errors all remain failures', () => {
  const extra='2026-10-19 18:55:32,900 ERROR other.Action (Action.java:1) - SQLException\njava.sql.SQLException: unrelated\n';
  for (const text of [errors()+extra, errors().replace('handlers.CMLHandler','other.Action'),
    errors().replace('Synthetic signed upload failure probe','Other SQL failure'),
    errors().replace('CMLHandler.java:97','CMLHandler.java:96'),errors().replace('18:55:32','18:55:34'),
    errors().replace('GenericJDBCException','NullPointerException'),errors().split('2026-10-19 18:55:32,100')[0]]) {
    assert.throws(() => verifyOwnedFaultWindow(text,valid(),sources,audit()));
  }
});

test('missing trigger, rollback, cleanup or any actual HTTP proof cannot be accounted', () => {
  for (const change of [{observedTriggerBodySha256:'0'.repeat(64)},{triggerAbsentAfterDrop:false},
    {rollbackRows:1},{decryptedCopiesAfterFailure:1},{cleanupAcknowledged:false},{logTimeZone:'UNVERIFIED'},
    {observedTriggerScope:{table:'Other',event:'INSERT',timing:'BEFORE'}}, {requests:{}}]) {
    assert.throws(() => verifyOwnedFaultWindow(errors(),{...valid(),...change},sources,audit()));
  }
  const receipt=valid();receipt.requests.fault.status=200;assert.throws(() => verifyOwnedFaultWindow(errors(),receipt,sources,audit()));
});

test('missing or different source bindings cannot accept a historical or altered receipt', () => {
  assert.throws(() => verifyOwnedFaultWindow(errors(),valid()));
  assert.throws(() => verifyOwnedFaultWindow(errors(),valid(),{...sources,handlerSourceSha256:'0'.repeat(64)},audit()));
});


test('a raw strict audit with any other signature, extra event or absent evidence remains failure', () => {
  for (const text of ['', audit() + 'UNKNOWN 1 ERROR Other.Action', audit().replace('2 event(s)', '3 event(s)'),
    audit().replace('UNKNOWN', 'KNOWN'), audit().replace('java.sql.SQLException', 'java.sql.OtherException')]) {
    assert.throws(() => verifyOwnedFaultWindow(errors(),valid(),sources,text));
  }
});


test('missing durable intent, payload and journal binding or private extra fields cannot be accepted', () => {
  for (const key of ['intentWrittenAt','payloadSha256','logCursorBeforeFault','logSource','triggerSqlSha256']) {
    const receipt=valid();delete receipt[key];assert.throws(() => verifyOwnedFaultWindow(errors(),receipt,sources,audit()));
  }
  for (const change of [{privateKey:'must-not-appear'},{intentWrittenAt:'2026-10-19T18:55:32.000Z'},
    {payloadSha256:'bad'},{logCursorBeforeFault:''},{logSource:'other journal unit'}]) {
    assert.throws(() => verifyOwnedFaultWindow(errors(),{...valid(),...change},sources,audit()));
  }
  const receipt=valid();receipt.requests.fault.cookies='must-not-appear';assert.throws(() => verifyOwnedFaultWindow(errors(),receipt,sources,audit()));
});

test('reversed or overlapping request windows and undocumented phases fail closed', () => {
  for (const phase of ['retry','duplicate','wrong-signature']) {
    const receipt=valid();receipt.requests[phase].startedAt=receipt.requests.fault.startedAt;
    assert.throws(() => verifyOwnedFaultWindow(errors(),receipt,sources,audit()));
  }
  const receipt=valid();receipt.requests.extra=receipt.requests.fault;
  assert.throws(() => verifyOwnedFaultWindow(errors(),receipt,sources,audit()));
});


test('a self-consistent but unowned trigger body or SQL cannot stand in for the source-bound scope', () => {
  const receipt=valid();receipt.expectedTriggerBodySha256='0'.repeat(64);receipt.observedTriggerBodySha256='0'.repeat(64);
  assert.throws(() => verifyOwnedFaultWindow(errors(),receipt,sources,audit()));
  const changed=valid();changed.triggerSqlSha256='0'.repeat(64);
  assert.throws(() => verifyOwnedFaultWindow(errors(),changed,sources,audit()));
});
