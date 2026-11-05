/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {triggerIntent,consumeRxRefusal,uncertainWarning}=require('./rx-save-failure-retry-playwright-checks');
test('late Rx fault rejects an unowned scope before producing executable SQL',()=>{
  const valid=['pw_rx_late_0123456789abcdef','123',['44'],'FAKE-PW0123456789abcdef'];
  for(const args of [[...valid.slice(0,1),'0',...valid.slice(2)],['foreign',...valid.slice(1)],[valid[0],valid[1],[],valid[3]],[valid[0],valid[1],['44); DROP TABLE drugs;'],valid[3]],[...valid.slice(0,3),'someone-else']])assert.throws(()=>triggerIntent(...args),/Invalid owned Rx fault scope/);
  const {body,message}=triggerIntent(...valid);
  assert.match(body,/NEW.demographic_no=123/);assert.match(body,/drugid NOT IN \(44\)/);
  assert.match(body,/EXISTS\(SELECT 1 FROM drugs/);assert.match(body,/SIGNAL SQLSTATE '45000'/);
  assert.equal(message,'PW_RX_LATE_0123456789abcdef');
});

test('Rx fault accounting removes a wrapper-attributed error only with the one owned response and preserves unrelated diagnostics',()=>{
  const base='https://127.0.0.1/carlos',owned={status:500,method:'POST',url:base+'/rx/WriteScript'},wrapped={type:'error',text:'Failed to load resource: the server responded with a status of 500 ()',location:{url:base+'/csrfguard'}};
  const other={type:'error',text:'unrelated application error',location:{url:base+'/other'}};
  const session={config:{baseUrl:new URL(base)},recorder:{badResponses:[owned],consoleIssues:[wrapped,other],requestFailures:[]}},mark={responses:0,console:0,failures:0};
  consumeRxRefusal(session,mark,500);assert.deepEqual(session.recorder.badResponses,[]);assert.deepEqual(session.recorder.consoleIssues,[other]);
  session.recorder.badResponses=[owned,{status:500,method:'GET',url:base+'/csrfguard'}];session.recorder.consoleIssues=[wrapped];
  assert.throws(()=>consumeRxRefusal(session,mark,500),/exactly one/);assert.equal(session.recorder.badResponses.length,2);assert.equal(session.recorder.consoleIssues.length,1);
});
test('lost-response warning must express uncertainty rather than a false unsaved state or generic transport failure',()=>{
  const refused='The prescription was NOT saved.';
  assert(uncertainWarning('Some changes may already be saved. Review the medication record before trying again.',refused));
  for(const message of [refused+' Check the session.','HTTP 0',null,'Unexpected server error'])assert.equal(uncertainWarning(message,refused),false);
});
test('the shipped uncertain-save message satisfies the lost-response warning check (#5686)',()=>{
  const fs=require('node:fs'),path=require('node:path');
  const text=fs.readFileSync(path.join(__dirname,'../src/main/resources/oscarResources_en.properties'),'utf8');
  const message=key=>{const match=new RegExp(`^SearchDrug\\.js\\.${key}=(.*)$`,'m').exec(text);assert(match,`missing ${key}`);return match[1];};
  const refused=message('saveRefused'),uncertain=message('saveUncertain');
  // The page appends the status only when a response arrived (none for a lost response).
  for(const shown of [uncertain,uncertain+' (HTTP 502)'])assert(uncertainWarning(shown,refused),shown);
});
