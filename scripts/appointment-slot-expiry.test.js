/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {expiryEvidence}=require('./appointment-slot-expiry-playwright-checks');
const live=['21','provider|2026-11-08|10:00|10:14','1','1000','20','1019.999'];
test('server-clock heartbeat evidence distinguishes the supported expiry boundary',()=>{
  assert.equal(expiryEvidence(live).age,19.999000000000024);
  assert.equal(expiryEvidence([...live.slice(0,5),'1020.001'],{expired:true}).timeout,20);
});
for(const [label,mutate,pattern,options={}] of [
  ['unlocked owner',row=>{row[2]='0';},/real monitor lock/],
  ['wrong configured timeout',row=>{row[4]='60';},/unexpected timeout or timestamp/],
  ['empty timestamp',row=>{row[3]='';},/not numeric/],
  ['future heartbeat',row=>{row[5]='999';},/outside the live boundary/],
  ['unexpired takeover',row=>{row[5]='1019';},/outside the expired boundary/,{expired:true}],
  ['expired takeover exactly at timeout',row=>{row[5]='1020';},/outside the expired boundary/,{expired:true}],
  ['live heartbeat exactly at timeout',row=>{row[5]='1020';},/outside the live boundary/],
  ['ambiguous monitor',row=>{row.push('extra');},/Missing or ambiguous/],
])test(`expiry oracle rejects ${label}`,()=>{
  const row=[...live];mutate(row);
  assert.throws(()=>expiryEvidence(row,options),pattern);
});
