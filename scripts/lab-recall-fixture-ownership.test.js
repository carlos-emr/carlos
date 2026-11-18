/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {claimedRecallLabId}=require('./lab-recall-delegate-playwright-checks');
test('an acknowledged claimed lab resolves only its original identity',()=>{
  assert.equal(claimedRecallLabId({claimed:true,labNo:'42'},['42']),'42');
});
test('a lost INSERT acknowledgement recovers the lab from its exact owned payload',()=>{
  assert.equal(claimedRecallLabId({claimed:true,labNo:null},['42']),'42');
});
test('an unclaimed namespace collision never adopts the existing lab',()=>{
  assert.equal(claimedRecallLabId({claimed:false,labNo:null},['42']),null);
});
test('an INSERT that did not land leaves no lab to delete',()=>{
  assert.equal(claimedRecallLabId({claimed:true,labNo:null},[]),null);
});
for(const [label,state,rows] of [
  ['a disappeared original',{claimed:true,labNo:'42'},[]],
  ['a substituted identity',{claimed:true,labNo:'42'},['43']],
  ['ambiguous matches',{claimed:true,labNo:null},['42','43']],
  ['an unsafe numeric identity',{claimed:true,labNo:null},['9007199254740993']],
  ['a malformed identity',{claimed:true,labNo:null},['42;DELETE']],
])test(`recall cleanup refuses ${label}`,()=>assert.throws(()=>claimedRecallLabId(state,rows)));
