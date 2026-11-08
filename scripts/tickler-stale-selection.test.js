/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {responseBarrier,withBarrier}=require('./tickler-stale-selection-playwright-checks');

function serverResponse({status=200,payload={data:[{id:42}]},fulfillError,disposeError}={}) {
  const seen=[];
  const response={status:()=>status,json:async()=>payload,dispose:async()=>{seen.push('dispose');if(disposeError)throw disposeError;}};
  const route={request:()=>({id:42}),fetch:async()=>{seen.push('fetch');return response;},
    fulfill:async value=>{seen.push('fulfill');assert.equal(value.response,response);if(fulfillError)throw fulfillError;},
    abort:async()=>{seen.push('abort');},continue:async()=>{seen.push('continue');}};
  return {seen,route};
}
test('the barrier buffers a real response until released and disposes it after delivery',async()=>{
  const barrier=responseBarrier(()=>true),{route,seen}=serverResponse();
  const handling=barrier.handle(route);await barrier.arrived;
  assert.deepEqual(seen,['fetch']);
  await barrier.finish();await handling;assert.deepEqual(seen,['fetch','fulfill','dispose']);
});
test('an HTTP failure is never delivered as a successful held task list',async()=>{
  const barrier=responseBarrier(()=>true),{route,seen}=serverResponse({status:503});
  await barrier.handle(route);
  assert.ok((await barrier.arrived).error);
  await assert.rejects(barrier.finish(),/real task data/);
  assert.deepEqual(seen,['fetch','abort','dispose']);
});
test('delivery and disposal errors both survive response teardown',async()=>{
  const barrier=responseBarrier(()=>true),{route,seen}=serverResponse({fulfillError:new Error('delivery failed'),disposeError:new Error('disposal failed')});
  const handling=barrier.handle(route);await barrier.arrived;
  await assert.rejects(barrier.finish(),error=>error instanceof AggregateError&&/delivery failed/.test(error.message)&&/disposal failed/.test(error.message));
  await handling;assert.deepEqual(seen,['fetch','fulfill','abort','dispose']);
});
test('an assertion failure releases its held response and removes the route',async()=>{
  const barrier=responseBarrier(()=>true),{route,seen}=serverResponse();let handling;
  const context={route:async(_,handler)=>{handling=handler(route);},unroute:async()=>{seen.push('unroute');}};
  await assert.rejects(withBarrier(context,barrier,async()=>{await barrier.arrived;throw new Error('pending actions exposed');}),/pending actions exposed/);
  await handling;assert.deepEqual(seen,['fetch','fulfill','dispose','unroute']);
});
test('a primary assertion, delivery failure and route-removal failure remain visible together',async()=>{
  const barrier=responseBarrier(()=>true),{route}=serverResponse({fulfillError:new Error('delivery failed')});let handling;
  const context={route:async(_,handler)=>{handling=handler(route);},unroute:async()=>{throw new Error('unroute failed');}};
  await assert.rejects(withBarrier(context,barrier,async()=>{await barrier.arrived;throw new Error('primary assertion');}),error=>error instanceof AggregateError
    &&['primary assertion','delivery failed','unroute failed'].every(text=>error.message.includes(text)));
  await handling;
});
