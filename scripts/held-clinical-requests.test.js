/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),http=require('node:http'),fs=require('node:fs');
const {request}=require('playwright');
const {heldClinicalRequests}=require('./lib/held-clinical-requests');
const {hasUncertainWrites,drainWrites}=require('./lib/uncertain-server-writes');
async function setup(t,{transportFailure=false,invalid=false,deliveryFailure=false,abortFailure=false,cancelled=false}={}){
  let calls=0,handler,aborts=0,fulfilled,closed=false;
  const server=http.createServer((req,res)=>{calls++;let data='';req.on('data',bytes=>{data+=bytes;});req.on('end',()=>{
    assert.equal(data,'note=pending-owned-content');res.writeHead(401,{'content-type':'text/plain'});res.end('Authentication required');
  });});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
  const api=await request.newContext();t.after(()=>api.dispose());
  const page={isClosed:()=>closed},context={route:async(_match,fn)=>{handler=fn;},pages:()=>closed?[]:[page]};
  const url='http://127.0.0.1:'+server.address().port+'/clinical/save';
  const fixture=heldClinicalRequests({context},{matches:()=>true,select:()=>{if(invalid)throw new Error('foreign patient');return {kind:'note',write:true};},identity:'FAKE-PW0123456789abcdef'});
  t.after(()=>fs.rmSync(require('node:path').dirname(fixture.journal),{recursive:true,force:true}));
  await fixture.install();
  const browserRequest={method:()=> 'POST',url:()=>url,postData:()=> 'note=pending-owned-content',frame:()=>({page:()=>page}),failure:()=>cancelled?{errorText:'net::ERR_ABORTED'}:null};
  const route={request:()=>browserRequest,fetch:async options=>{assert.equal(options.maxRedirects,0);assert.equal(options.maxRetries,0);
    if(transportFailure)throw new Error('lost acknowledgement');return api.post(url,{...options,data:browserRequest.postData()});},
    fulfill:async result=>{if(deliveryFailure)throw new Error('delivery failed');fulfilled=result;},abort:async()=>{aborts++;if(abortFailure)throw new Error('route closed');}};
  return {fixture,context,forward:()=>handler(route),calls:()=>calls,aborts:()=>aborts,fulfilled:()=>fulfilled,close:()=>{closed=true;}};
}
test('held native clinical POST reaches the real HTTP server once with unchanged bytes after release',async t=>{
  const f=await setup(t);f.fixture.hold('note');const pending=f.forward();
  assert.equal(f.calls(),0);assert.equal(f.fixture.records[0].state,'held');
  const journal=fs.readFileSync(f.fixture.journal,'utf8');assert(!journal.includes('pending-owned-content'));assert(journal.includes('bodySha256'));
  f.fixture.release('note');await pending;await drainWrites(f.context);
  assert.equal(f.calls(),1);assert.equal(f.fixture.records[0].status,401);assert.equal(f.fulfilled().body.toString(),'Authentication required');
  await f.fixture.shutdown();assert.throws(()=>f.fixture.cleanup(),/browser teardown/);f.close();f.fixture.cleanup();assert(!fs.existsSync(f.fixture.journal));
});
test('shutdown aborts a held request before dispatch and drains the outer workflow write guard',async t=>{
  const f=await setup(t);f.fixture.hold('note');const pending=f.forward();await f.fixture.shutdown();await pending;await drainWrites(f.context);
  assert.equal(f.calls(),0);assert.equal(f.aborts(),1);assert.equal(f.fixture.records[0].state,'aborted-before-dispatch');assert(!hasUncertainWrites(f.context));f.close();f.fixture.cleanup();
});
test('releasing one acknowledged older request leaves the next native write held',async t=>{
  const f=await setup(t);f.fixture.hold('note');const first=f.forward(),second=f.forward();
  f.fixture.releaseOne(f.fixture.records[0]);assert.throws(()=>f.fixture.releaseOne(f.fixture.records[0]),/released once/);await first;
  assert.equal(f.calls(),1);assert.equal(f.fixture.records[1].state,'held');await f.fixture.shutdown();await second;assert.equal(f.calls(),1);f.close();f.fixture.cleanup();
});
test('a drained boundary resumes genuine authenticated requests without leaving a write-aborting route installed',async t=>{
  const f=await setup(t);assert.throws(()=>f.fixture.resume(),/drained request boundary/);
  await f.forward();await f.fixture.shutdown();f.fixture.resume();await f.forward();assert.equal(f.calls(),2);
  await f.fixture.shutdown();f.close();f.fixture.cleanup();
});
test('native page closure cancels a still-held request without dispatch or an invented unknown write',async t=>{
  const f=await setup(t,{abortFailure:true});f.fixture.hold('note');const pending=f.forward();f.close();await f.fixture.shutdown();await pending;await drainWrites(f.context);
  assert.equal(f.calls(),0);assert.equal(f.fixture.records[0].state,'aborted-before-dispatch');assert(!hasUncertainWrites(f.context));f.fixture.cleanup();
});
test('a client-cancelled held request is never forwarded after release',async t=>{
  const f=await setup(t,{cancelled:true});await f.forward();assert.equal(f.calls(),0);assert(!hasUncertainWrites(f.context));
  await assert.rejects(f.fixture.shutdown(),AggregateError);assert.throws(()=>f.fixture.assertSafe(),AggregateError);f.close();f.fixture.cleanup();assert(!fs.existsSync(f.fixture.journal));
});
test('shutdown can confirm cancellation of an already-failed browser request before dispatch',async t=>{
  const f=await setup(t,{cancelled:true,abortFailure:true});f.fixture.hold('note');const pending=f.forward();await f.fixture.shutdown();await pending;
  assert.equal(f.calls(),0);assert.equal(f.fixture.records[0].state,'aborted-before-dispatch');f.close();f.fixture.cleanup();
});
test('unknown write response never retries and retains the durable journal and all fixtures',async t=>{
  const f=await setup(t,{transportFailure:true});await f.forward();await drainWrites(f.context);
  assert(hasUncertainWrites(f.context));assert.equal(f.calls(),0);assert.equal(f.aborts(),1);assert.equal(f.fixture.records[0].state,'unknown-write');
  await assert.rejects(f.fixture.shutdown(),AggregateError);assert(fs.existsSync(f.fixture.journal));
});
test('ownership refusal aborts before dispatch without claiming an uncertain server write',async t=>{
  const f=await setup(t,{invalid:true});await f.forward();assert.equal(f.calls(),0);assert.equal(f.aborts(),1);assert(!hasUncertainWrites(f.context));await assert.rejects(f.fixture.shutdown(),AggregateError);
});
test('journal failure refuses before server dispatch and preserves the primary filesystem diagnostic',async t=>{
  const f=await setup(t);fs.unlinkSync(f.fixture.journal);fs.rmdirSync(require('node:path').dirname(f.fixture.journal));
  await f.forward();assert.equal(f.calls(),0);assert(!hasUncertainWrites(f.context));await assert.rejects(f.fixture.shutdown(),/ENOENT/);
});
test('acknowledged response delivery failure remains fatal unless the issuing page really closed',async t=>{
  const f=await setup(t,{deliveryFailure:true});await f.forward();assert.equal(f.calls(),1);assert(!hasUncertainWrites(f.context));
  assert.throws(()=>f.fixture.assertSafe(),AggregateError);assert.throws(()=>f.fixture.acceptClosedDelivery(f.fixture.records[0]),/browser page closed/);
  f.close();f.fixture.acceptClosedDelivery(f.fixture.records[0]);await f.fixture.shutdown();f.fixture.cleanup();
});
