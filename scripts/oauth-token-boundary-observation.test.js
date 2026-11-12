/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {boundaryObservation}=require('./oauth-token-lifecycle-playwright-checks');
function fixture({status=200,patient='12',mime='application/json',text='invalid_signature',cleanupError}={}) {
  let disposed=0;const response={status:()=>status,headers:()=>({'content-type':mime}),json:async()=>({demographicNo:patient,lastName:'FAKE-PW'}),text:async()=>text,dispose:async()=>{disposed++;if(cleanupError)throw cleanupError;}};
  return {response,disposed:()=>disposed};
}
const timing={lower:100,upper:100,boundary:100,patient:'12',marker:'FAKE-PW'};
for(const [upper,expected] of [[100,true],[101,false]])test('valid patient response is contained only within the observed boundary: '+upper,async()=>{
  const f=fixture();assert.equal(await boundaryObservation(f.response,{...timing,upper}),expected);assert.equal(f.disposed(),1);
});
test('documented expiry refusal after the second is a timing miss rather than a product failure',async()=>{
  const f=fixture({status:401});assert.equal(await boundaryObservation(f.response,{...timing,upper:101}),false);assert.equal(f.disposed(),1);
});
for(const [label,values,upper,pattern] of [
  ['refusal inside exact second',{status:401},100,/refused inside its boundary/],
  ['server error after boundary',{status:500},101,/unexpected HTTP status/],
  ['wrong patient after boundary',{patient:'99'},101,/another patient/],
  ['HTML success after boundary',{mime:'text/html'},101,/typed JSON/],
  ['unrelated refusal after boundary',{status:401,text:'other_failure'},101,/unexpected authentication reason/],
])test('boundary retries never hide '+label,async()=>{
  const f=fixture(values);await assert.rejects(boundaryObservation(f.response,{...timing,upper}),pattern);assert.equal(f.disposed(),1);
});
test('boundary cleanup failure preserves the primary authentication failure',async()=>{
  const cleanupError=new Error('synthetic disposal failure'),f=fixture({status:500,cleanupError});
  await assert.rejects(boundaryObservation(f.response,timing),error=>error instanceof AggregateError&&error.errors[1]===cleanupError&&/unexpected HTTP status/.test(error.errors[0].message));
});
