/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {assertLookupWritesSettled}=require('./lookup-list-isolation-playwright-checks');
const {retainFixtures,trackWrite}=require('./lib/uncertain-server-writes');
test('unknown lookup outcome preserves both the original product failure and recovery requirement',async()=>{
  const context={},primary=new Error('#5816 inactive provider remains offered');retainFixtures(context);
  await assert.rejects(assertLookupWritesSettled(context,primary),error=>{
    assert(error instanceof AggregateError);assert.equal(error.errors[0],primary);assert.match(error.errors[1].message,/outcome is unknown/);assert.match(error.message,/#5816.*outcome is unknown/);return true;
  });
});
test('unknown lookup outcome remains fatal when there is no earlier failure',async()=>{
  const context={};retainFixtures(context);await assert.rejects(assertLookupWritesSettled(context),error=>error instanceof AggregateError&&error.errors.length===1);
});
test('lookup safety guard drains a tracked operation before returning',async()=>{
  const context={};let release,finished=false;const write=new Promise(resolve=>{release=resolve;});trackWrite(context,write);
  const checked=assertLookupWritesSettled(context,new Error('retained by caller')).then(()=>{finished=true;});
  await Promise.resolve();assert.equal(finished,false);release();await checked;assert.equal(finished,true);
});
