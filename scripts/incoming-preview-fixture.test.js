/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),http=require('node:http');
const {PNG}=require('pngjs'),{request}=require('playwright');
const {createIncomingPreviewFixture}=require('./lib/incoming-preview-fixture');
const {hasUncertainWrites,drainWrites}=require('./lib/uncertain-server-writes');

async function setup(t,{cacheFile=true,transportFailure=false,method='GET'}={}) {
  const cache=fs.mkdtempSync(path.join(os.tmpdir(),'preview-fixture-'));t.after(()=>fs.rmSync(cache,{recursive:true,force:true}));
  const unrelated=path.join(cache,'unrelated.bin');fs.writeFileSync(unrelated,'unrelated original');
  const name='FAKE-PW0123456789abcdef.pdf',file=path.join(cache,'FAKE-PW0123456789abcdef_1.png');
  const image=new PNG({width:20,height:30});image.data.fill(255);const bytes=PNG.sync.write(image);let calls=0;
  const server=http.createServer((_req,res)=>{calls++;if(cacheFile)fs.writeFileSync(file,bytes);res.writeHead(200,{'content-type':'image/png;charset=UTF-8'});res.end(bytes);});
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});t.after(()=>new Promise(resolve=>server.close(resolve)));
  const api=await request.newContext();t.after(()=>api.dispose());let handler,matcher,fulfilled,aborted=0,attempts=0;
  const baseUrl=new URL('http://127.0.0.1:'+server.address().port+'/carlos');
  const context={route:async(match,handle)=>{matcher=match;handler=handle;}};
  const session={config:{baseUrl},context},options={names:[name],cache},fixture=createIncomingPreviewFixture(session,options);await fixture.install();
  const target=new URL('/carlos/documentManager/ManageDocument',baseUrl);target.search=new URLSearchParams({method:'viewIncomingDocPageAsImage',pdfName:name,curPage:'1',queueId:'1',pdfDir:'File'}).toString();
  const browserRequest={url:()=>target.href,method:()=>method};
  const route={request:()=>browserRequest,fetch:async settings=>{attempts++;assert.equal(settings.maxRedirects,0);assert.equal(settings.maxRetries,0);if(transportFailure)throw new Error('synthetic lost response');return api.get(target.href,settings);},
    fulfill:async result=>{fulfilled=result;},abort:async()=>{aborted++;}};
  return {fixture,context,options,session,route,browserRequest,bytes,file,unrelated,target,forward:()=>handler(route),matches:url=>matcher(url),calls:()=>calls,attempts:()=>attempts,aborted:()=>aborted,fulfilled:()=>fulfilled};
}
test('actual binary HTTP response is captured once, forwarded as bytes and cleaned without changing unrelated cache files',async t=>{
  const f=await setup(t);assert.equal(f.matches(f.target),true);assert.equal(f.matches(new URL('http://other.invalid'+f.target.pathname+f.target.search)),false);
  await f.forward();await drainWrites(f.context);f.fixture.assertSafe();
  assert.equal(f.calls(),1);assert.deepEqual(f.fulfilled().body,f.bytes);
  const browser={request:()=>f.browserRequest,status:()=>200,body:()=>{throw new Error('Do not read the CDP text-decoded body');}};
  assert.deepEqual(f.fixture.read(browser).body,f.bytes);assert.equal(hasUncertainWrites(f.context),false);
  assert.throws(()=>createIncomingPreviewFixture(f.session,f.options),/namespace already exists/);
  f.fixture.cleanup();assert.equal(fs.existsSync(f.file),false);assert.equal(fs.readFileSync(f.unrelated,'utf8'),'unrelated original');
});
test('unacknowledged image GET retains fixtures because the server may still be writing its cache',async t=>{
  const f=await setup(t,{transportFailure:true});await f.forward();await drainWrites(f.context);
  assert.equal(f.attempts(),1);assert.equal(f.aborted(),1);assert.equal(hasUncertainWrites(f.context),true);
  assert.throws(()=>f.fixture.assertSafe(),/lost response/);assert.throws(()=>f.fixture.cleanup(),/transport\/cache ownership/);
});
test('known response with a missing cache is fatal without inventing an uncertain dispatch',async t=>{
  const f=await setup(t,{cacheFile:false});await f.forward();
  assert.equal(hasUncertainWrites(f.context),false);assert.equal(f.aborted(),1);assert.throws(()=>f.fixture.cleanup(),/cache file is missing/);
});
test('changed cache bytes refuse deletion after successful capture',async t=>{
  const f=await setup(t);await f.forward();fs.writeFileSync(f.file,'operator replacement');
  assert.throws(()=>f.fixture.cleanup(),/changed; retain/);assert.equal(fs.readFileSync(f.file,'utf8'),'operator replacement');
});
test('invalid preview method is refused before upstream dispatch',async t=>{
  const f=await setup(t,{method:'POST'});await f.forward();assert.equal(f.attempts(),0);assert.equal(f.calls(),0);
  assert.equal(hasUncertainWrites(f.context),false);assert.throws(()=>f.fixture.cleanup(),/left its owned/);
});
