/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {withConfirmedBrowserDrain, assertOwnedAcknowledgementChain, prepareInboxDataset, requireDisposableEnvironment, assertOwnedRows, assertOwnedReviewStatus} = require('./lib/inbox-owned-dataset');

function model(options = {}) {
  const rows = new Map(), callbacks = [], statements = [], events = [], removed = [];
  let next = 6000, submissions = 0, owner = true;
  const session = {context: {browser: () => ({isConnected: () => false})}, marker: 'FAKE-PW0123456789abcdef', patient: '3001', provider: '999998', schedule: {}, cleanup(fn) {callbacks.push(fn);}};
  session.sql = {
    value(query) {
      statements.push(query);
      if (query.includes('FROM demographic')) return owner ? '1' : '0';
      if (query.startsWith('SELECT status')) return rows.get(query.match(/lab_no=(\d+)/)[1]).status;
      if (query.includes('demographic_no<>')) return options.foreignPatient || (options.hiddenForeign && query.includes('lab_no=6000 ')) ? '1' : '0';
      if (query.includes('FROM patientLabRouting') || query.includes('FROM providerLabRouting')) return '1';
      if (query.includes('FROM hl7TextInfo')) return String(rows.size + (options.collision ? 1 : 0));
      throw new Error(`Unexpected value read ${query}`);
    },
    rows(query) {
      statements.push(query);
      assert.match(query, /SELECT lab_no FROM hl7TextInfo/);
      const accession = query.match(/accessionNum='([^']+)'/);
      return [...rows].filter(([id, row]) => accession ? row.accession === accession[1] : !(options.hiddenForeign && id === '6000')).map(([id]) => [id]);
    },
    dispose() {},
    execute(query) {
      statements.push(query);
      assert.match(query, /UPDATE providerLabRouting SET status='[NAF]' WHERE lab_type='HL7'/);
      assert.match(query, /AND provider_no='999998'/);
      rows.get(query.match(/lab_no=(\d+)/)[1]).status = query.match(/SET status='([NAF])'/)[1];
    },
  };
  const dependencies = {
    async submit(s, input) {
      assert.equal(s.patient, session.patient); assert.equal(s.provider, session.provider);
      assert.ok(callbacks.length, 'cleanup intent must be registered before the first native submission');
      const id = String(next++); rows.set(id, {accession: input.accession, status: 'N'}); submissions++;
      if (options.lostAck) throw new Error('native acknowledgement lost after commit');
      return {form: {async close() {events.push('form closed');}}, inbox: {async close() {events.push('inbox closed');}}};
    },
    remove(sql, ids) { assert.equal(sql, session.sql); removed.push(...ids); for (const id of ids) rows.delete(id); },
    prepareProgram(s) { s.cleanup(() => events.push('admission cleaned')); return {program:'1'}; },
    createDocument(s) {
      let closed = false, cleaned = false;
      s.cleanup(() => {assert.equal(closed, true, 'document must drain before cleanup'); cleaned = true; events.push('document cleaned');});
      return {sourceId:'7000', closeAfterDrain() {closed = true; events.push('document drained');}, isCleaned() {return cleaned;}};
    },
  };
  return {session, dependencies, rows, callbacks, statements, events, removed,
    get submissions() {return submissions;}, changeOwner() {owner = false;},
    async cleanup() {for (const callback of [...callbacks].reverse()) await callback();}};
}
async function disposable(body) {
  const names = ['EXCLUSIVE','CARLOS_DISPOSABLE_VM','LAB_UPLOAD_DOCUMENT_STORE','DOCUMENT_DIR','RX_FAX_DOCUMENT_DIR']; const old = names.map(key => process.env[key]);
  process.env.EXCLUSIVE='1'; process.env.CARLOS_DISPOSABLE_VM='true';
  process.env.LAB_UPLOAD_DOCUMENT_STORE=require('node:os').tmpdir(); process.env.DOCUMENT_DIR=require('node:os').tmpdir(); delete process.env.RX_FAX_DOCUMENT_DIR;
  try {await body();} finally {names.forEach((key,index) => {if (old[index] === undefined) delete process.env[key]; else process.env[key]=old[index];});}
}

test('fixture refuses shared/non-disposable environments before any native writes', async () => {
  assert.throws(() => requireDisposableEnvironment({EXCLUSIVE:'1'}), /exclusive|disposable|EXCLUSIVE/i);
  assert.throws(() => requireDisposableEnvironment({CARLOS_DISPOSABLE_VM:'true'}), /EXCLUSIVE/);
  assert.doesNotThrow(() => requireDisposableEnvironment({EXCLUSIVE:'1',CARLOS_DISPOSABLE_VM:'true'}));
});

test('filters own both document/lab types and actual N/A/F review identities with ordered cleanup', async () => disposable(async () => {
  const m = model(); const result = await prepareInboxDataset(m.session, 'filters', m.dependencies);
  assert.equal(m.submissions,3); assert.deepEqual(result.statuses, {N:['HL7:6000','DOC:7000'],A:['HL7:6001'],F:['HL7:6002']});
  assert.deepEqual(result.new,['HL7:6000','DOC:7000']);
  assert.equal(fs.statSync(result.journal).mode & 0o777,0o600);
  const journal = JSON.parse(fs.readFileSync(result.journal)); assert.equal(journal.phase,'ready'); assert.equal(journal.accessions.length,3);
  await m.cleanup(); assert.equal(m.rows.size,0); assert.deepEqual(m.removed,['6000','6001','6002']);
  assert.deepEqual(m.events.slice(-3),['document drained','document cleaned','admission cleaned']);
  assert.equal(JSON.parse(fs.readFileSync(result.journal)).phase,'cleaned');
  fs.rmSync(path.dirname(result.journal),{recursive:true});
}));

test('acknowledgement fixtures supply three distinct New labs without modifying any foreign route', async () => disposable(async () => {
  const m=model(); const result=await prepareInboxDataset(m.session,'acknowledgement',m.dependencies);
  assert.equal(new Set(result.labs).size,3); assert.deepEqual(result.new,result.labs);
  assert.equal(result.statuses.A.length+result.statuses.F.length,0);
  const updates=m.statements.filter(q=>q.startsWith('UPDATE'));
  assert.equal(updates.length,3); assert.ok(updates.every(q=>/provider_no='999998'/.test(q) && /lab_no=600[012]/.test(q)));
  await m.cleanup(); fs.rmSync(path.dirname(result.journal),{recursive:true});
}));

test('lost native acknowledgement recovers only absent-before accession-owned rows', async () => disposable(async () => {
  const m=model({lostAck:true});
  await assert.rejects(prepareInboxDataset(m.session,'acknowledgement',m.dependencies),/acknowledgement lost/);
  assert.equal(m.rows.size,1); assert.equal(m.callbacks.length,1);
  await m.cleanup(); assert.deepEqual(m.removed,['6000']); assert.equal(m.rows.size,0);
  assert.ok(m.statements.some(q=>q.includes('UNION SELECT lab_id FROM hl7TextMessage')));
}));

test('preexisting accession/message collision refuses all native submissions and cleanup claims', async () => disposable(async () => {
  const m=model({collision:true}); await assert.rejects(prepareInboxDataset(m.session,'acknowledgement',m.dependencies),/already exist/);
  assert.equal(m.submissions,0); assert.equal(m.callbacks.length,0); assert.deepEqual(m.removed,[]);
}));

test('changed patient ownership and a foreign patient route refuse deletion', async () => disposable(async () => {
  for (const foreignPatient of [false,true]) {
    const m=model({foreignPatient}); const result=await prepareInboxDataset(m.session,'acknowledgement',m.dependencies);
    if (!foreignPatient) m.changeOwner();
    await assert.rejects(m.cleanup(), /patient changed|another patient/);
    assert.deepEqual(m.removed,[]); assert.equal(m.rows.size,3);
    fs.rmSync(path.dirname(result.journal),{recursive:true});
  }
}));

test('owned membership prevents a missing fixture or swapped status from passing a partition', () => {
  const data={all:['HL7:1','HL7:2','HL7:3'],statuses:{N:['HL7:1'],A:['HL7:2'],F:['HL7:3']}};
  assert.throws(()=>assertOwnedRows(['HL7:1'],data.all,'baseline'),/missing owned/);
  assert.throws(()=>assertOwnedReviewStatus(['HL7:1'],'bad','N'));
  assert.throws(()=>assertOwnedReviewStatus(['HL7:1'],data,'A'),/missing owned/);
  assert.throws(()=>assertOwnedReviewStatus(['HL7:1','HL7:2'],data,'N'),/wrong N status/);
  assert.doesNotThrow(()=>assertOwnedReviewStatus(['HL7:2','HL7:foreign'],data,'A'));
});


test('acknowledgement refuses every foreign source/version before the existing routing fixture can mutate it', async () => {
  const owned=['HL7:11','HL7:12','HL7:13'];
  await assert.doesNotReject(assertOwnedAcknowledgementChain({evaluate:async()=> '11,12'},'HL7:12',owned));
  await assert.doesNotReject(assertOwnedAcknowledgementChain({evaluate:async()=> ''},'HL7:12',owned));
  for (const chain of ['11,999','11,not-a-number','0','-1']) {
    await assert.rejects(assertOwnedAcknowledgementChain({evaluate:async()=>chain},'HL7:12',owned),/foreign Inbox lab/);
  }
  await assert.rejects(assertOwnedAcknowledgementChain({evaluate:async()=>''},'HL7:999',owned),/not an owned/);
});


test('recorded lab absent from discovery still receives the foreign-patient cleanup guard', async () => disposable(async () => {
  const m=model({hiddenForeign:true}); const result=await prepareInboxDataset(m.session,'acknowledgement',m.dependencies);
  await assert.rejects(m.cleanup(), /another patient/);
  assert.deepEqual(m.removed, []); assert.equal(m.rows.size,3);
  fs.rmSync(path.dirname(result.journal),{recursive:true});
}));

test('unconfirmed browser close retains every Inbox child and parent without cleanup DML', async () => disposable(async () => {
  const {cleanupOwnedWorkflow} = require('./lib/workflow-session');
  const m=model(); const browser={isConnected:()=>true, async close() {throw new Error('browser close failed');}};
  const s=withConfirmedBrowserDrain({...m.session,context:{browser:()=>browser}});
  const result=await prepareInboxDataset(s,'filters',m.dependencies);
  const before=[...m.statements];
  await assert.rejects(cleanupOwnedWorkflow({browser,sql:m.session.sql,patient:s.patient,marker:s.marker,cleanups:m.callbacks}), /browser close failed.*browser drain is unconfirmed/);
  assert.deepEqual(m.removed,[]); assert.equal(m.rows.size,3);
  assert.ok(!m.events.some(event=>/cleaned|drained/.test(event)));
  assert.ok(!m.statements.slice(before.length).some(query=>/^(DELETE|UPDATE|INSERT)/.test(query)));
  fs.rmSync(path.dirname(result.journal),{recursive:true});
}));
