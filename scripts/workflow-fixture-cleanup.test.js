/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
const test=require('node:test');
const assert=require('node:assert/strict');
const {cleanupClients}=require('./oauth-token-lifecycle-playwright-checks');
const {cleanupSharedFixtures}=require('./contact-shared-directory-playwright-checks');

test('OAuth cleanup recovers an INSERT whose ID read was lost',()=>{
  const client={key:'owned-key',name:'FAKE-PW-client'};let deleted=false;
  const sql={rows(query){
    if(query.startsWith('SELECT id'))return [['42']];
    return [['owned-key','FAKE-PW-client']];
  },execute(query){assert.match(query,/clientId=42/);deleted=true;},value(){return deleted?'0':'1';}};
  cleanupClients(sql,[client]);assert.equal(client.id,'42');assert.equal(deleted,true);
});

test('OAuth cleanup retains a changed owner, continues independent clients and reports every failure',()=>{
  const attempted=[];
  const clients=[1,2,3].map(id=>({id:String(id),key:'key'+id,name:'FAKE-PW-'+id}));
  const sql={rows(query){
    const id=/WHERE id=(\d+)/.exec(query)[1];
    if(id==='1')return [['other-owner','other-name']];
    if(id==='3')throw new Error('injected SQL read failure');
    return []; // The application deleted client 2; its issued tokens still need cleanup.
  },execute(query){attempted.push(query);},value(){return '0';}};
  assert.throws(()=>cleanupClients(sql,clients),error=>error instanceof AggregateError&&error.errors.length===2
    &&/ownership changed/.test(error.message)&&/injected SQL read failure/.test(error.message));
  assert.equal(attempted.length,1);assert.match(attempted[0],/clientId=2/);assert.doesNotMatch(attempted[0],/clientId=1/);
});

test('shared-contact cleanup tolerates an uncreated secondary patient and still cleans the primary links and control',()=>{
  const writes=[];const removed=new Set();
  const sql={rows(query){assert.doesNotMatch(query,/undefined/);return [];},value(query){
    assert.doesNotMatch(query,/undefined/);
    if(query.includes('DemographicContact'))return '0';
    if(query.includes('Contact WHERE'))return query.includes('AND lastName')?'1':'0';
    const id=/demographic_no=(\d+)/.exec(query)[1];
    return removed.has(id)?'0':'1';
  },execute(query){writes.push(query);const match=/DELETE FROM demographic WHERE demographic_no=(\d+)/.exec(query);if(match)removed.add(match[1]);}};
  cleanupSharedFixtures(sql,{patient:'1',marker:'FAKE-PW',ownedPatients:[{id:'2',name:'FAKE-PW-B'},{name:'FAKE-PW-C'}],control:'42'});
  assert.deepEqual([...removed],['2']);
  assert(writes.some(query=>query==='DELETE FROM DemographicContact WHERE demographicNo=1'));
  assert(writes.some(query=>query.startsWith('DELETE FROM Contact WHERE id=42')));
  assert(writes.every(query=>!query.includes('undefined')));
});

test('a changed shared-contact patient cannot prevent cleanup of independently owned patients and contacts',()=>{
  const removed=new Set();const writes=[];
  const sql={rows(){return [];},value(query){
    if(query.includes('DemographicContact'))return '0';
    if(query.includes('Contact WHERE'))return query.includes('AND lastName')?'1':'0';
    const id=/demographic_no=(\d+)/.exec(query)[1];
    if(id==='2')return '0'; // Its ownership no longer matches: never delete it or its links.
    return removed.has(id)?'0':'1';
  },execute(query){writes.push(query);const match=/DELETE FROM demographic WHERE demographic_no=(\d+)/.exec(query);if(match)removed.add(match[1]);}};
  assert.throws(()=>cleanupSharedFixtures(sql,{patient:'1',marker:'FAKE-PW',ownedPatients:[{id:'2',name:'FAKE-PW-B'},{id:'3',name:'FAKE-PW-C'}],control:'42'}),/ownership changed/);
  assert.deepEqual([...removed],['3']);assert(writes.every(query=>!query.includes('demographicNo=2')&&!query.includes('demographic_no=2')));
  assert(writes.some(query=>query.startsWith('DELETE FROM Contact WHERE id=42')));
});
