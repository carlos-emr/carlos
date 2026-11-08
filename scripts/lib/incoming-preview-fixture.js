/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {PNG}=require('pngjs');
const {assert}=require('./playwright-harness');
const {readRegular,retireOwnedFile}=require('./owned-file-retirement');
const {trackWrite,retainFixtures}=require('./uncertain-server-writes');
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');

/** Own native PNG cache writes and preserve the exact UI request's binary bytes. */
function createIncomingPreviewFixture(session,{names,cache}) {
  assert(names.length>0&&names.every(name=>path.basename(name)===name&&name.endsWith('.pdf')),'Invalid owned preview names');
  const base=new URL(session.config.baseUrl),prefixes=names.map(name=>name.slice(0,-4)+'_');
  const before=new Map(),owned=new Map(),captured=new WeakMap(),failures=[];
  const listing=()=>fs.existsSync(cache)?fs.readdirSync(cache):[];
  if(fs.existsSync(cache))assert(fs.realpathSync(cache)===cache,'Preview cache must not resolve through links');
  for(const name of listing()) {
    assert(!prefixes.some(prefix=>name.startsWith(prefix)),'Owned preview-cache namespace already exists');
    const file=path.join(cache,name),value=readRegular(file);assert(value,'Cache entry disappeared during inventory');before.set(name,hash(value.bytes));
  }
  function matches(url) {return url.origin===base.origin&&url.pathname===base.pathname.replace(/\/$/,'')+'/documentManager/ManageDocument'
    &&url.searchParams.get('method')==='viewIncomingDocPageAsImage'&&names.includes(url.searchParams.get('pdfName'));}
  function validate(request) {
    const url=new URL(request.url()),name=url.searchParams.get('pdfName'),number=url.searchParams.get('curPage');
    assert(matches(url)&&request.method()==='GET'&&/^[1-9]\d*$/.test(number)
      &&url.searchParams.get('pdfDir')==='File'&&url.searchParams.get('queueId')==='1'
      &&['method','pdfName','curPage','pdfDir','queueId'].every(key=>url.searchParams.getAll(key).length===1),'Preview request left its owned document/page/queue');
    return {name,number};
  }
  function capture(request,result) {
    try {
      const {name,number}=validate(request);
      if(result.status===200) {
        assert(/^image\/png(?:;|$)/i.test(result.headers['content-type']||''),'Native preview is not PNG');
        const image=PNG.sync.read(result.body),file=path.join(cache,name.slice(0,-4)+'_'+number+'.png');
        assert(fs.realpathSync(cache)===cache,'Preview cache was replaced by a link');
        const cached=readRegular(file);assert(cached,'Native PDF preview cache file is missing');const pixels=PNG.sync.read(cached.bytes);
        assert(image.width===pixels.width&&image.height===pixels.height&&image.data.equals(pixels.data),'Cache pixels differ from the actual native preview');
        if(!owned.has(file))owned.set(file,new Set());owned.get(file).add(hash(cached.bytes));
      }
      captured.set(request,result);
    } catch(error){failures.push(error);throw error;}
  }
  async function handler(route) {
    const operation=(async()=>{
      let response,dispatched=false,acknowledged=false;
      try {
        validate(route.request());
        dispatched=true;
        response=await route.fetch({maxRedirects:0,maxRetries:0,timeout:120000});
        const body=await response.body();acknowledged=true;
        capture(route.request(),{body,status:response.status(),headers:response.headers()});
        await route.fulfill({response,body});
      } catch(error) {
        if(dispatched&&!acknowledged)retainFixtures(session.context);
        if(!failures.includes(error))failures.push(error);
        try{await route.abort();}catch(abortError){failures.push(abortError);}
      } finally {if(response)try{await response.dispose();}catch(error){failures.push(error);}}
    })();trackWrite(session.context,operation);await operation;
  }
  function assertSafe(){if(failures.length)throw new AggregateError(failures,'Incoming preview transport/cache ownership failed: '+failures.map(error=>error.message).join('; '));}
  return {
    capture,assertSafe,
    install:()=>session.context.route(matches,handler),
    read(response){assertSafe();const result=captured.get(response.request());assert(result&&result.status===response.status(),'Native preview was not captured losslessly from its actual UI request');return result;},
    cleanup(){
      assertSafe();
      for(const [file,digests] of owned)retireOwnedFile(file,bytes=>digests.has(hash(bytes)));
      assert(!listing().some(name=>prefixes.some(prefix=>name.startsWith(prefix))),'Owned preview cache files remain');
      for(const [name,digest] of before){const value=readRegular(path.join(cache,name));assert(value&&hash(value.bytes)===digest,'Unrelated preview cache changed');}
    },
  };
}
module.exports={createIncomingPreviewFixture};
