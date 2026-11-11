// Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createHash} = require('node:crypto');
const {journalRow,rawPost,interruptPost,assertMetadataVersion,verifyUploadedBody} = require('./demographic-cds-large-ingress-playwright-checks');

function observeRawPostListeners(t) {
  const observations=[];const warnings=[];const original=http.request;
  t.mock.method(http,'request',function(...args){
    const request=Reflect.apply(original,this,args);
    const observed={request,drains:0,maxClose:0,maxError:0};observations.push(observed);
    observed.closed=new Promise(resolve=>request.once('close',resolve));
    request.on('drain',()=>{observed.drains++;
      observed.maxClose=Math.max(observed.maxClose,request.listenerCount('close'));
      observed.maxError=Math.max(observed.maxError,request.listenerCount('error'));
    });
    return request;
  });
  const onWarning=warning=>{
    if(warning.name==='MaxListenersExceededWarning' && observations.some(value=>value.request===warning.emitter))warnings.push(warning);
  };
  process.on('warning',onWarning);t.after(()=>process.off('warning',onWarning));
  return {observations,warnings};
}

async function assertReleasedBackpressureListeners(observed,warnings) {
  await observed.closed;
  // Let the losing promises settle and Node deliver any queued listener warning.
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(observed.request.listenerCount('drain'),1,'Only the test drain observer may remain');
  assert.equal(observed.request.listenerCount('close'),0,'Backpressure close listeners must be released');
  assert.equal(observed.request.listenerCount('error'),1,'Only the request failure handler may remain');
  assert.ok(observed.maxClose<=2,'Close listeners must remain bounded across drain cycles');
  assert.ok(observed.maxError<=3,'Error listeners must remain bounded across drain cycles');
  assert.equal(warnings.length,0,'Real request backpressure must not emit listener warnings');
}

test('independent upload journal includes exact big-endian index, offset, byte count and full hash', () => {
  const hash = createHash('sha256').update('synthetic').digest('hex');
  const row = journalRow(7,2**33,4096,hash);
  assert.equal(row.length,52); assert.equal(row.readBigInt64BE(),7n);
  assert.equal(row.readBigInt64BE(8),2n**33n); assert.equal(row.readInt32BE(16),4096);
  assert.equal(row.subarray(20).toString('hex'),hash);
  assert.throws(() => journalRow(-1,0,4096,hash));
  assert.throws(() => journalRow(0,0,4096,'not-a-digest'));
});

test('typed raw metadata requires independent header and numeric body contract version',()=>{
  const good={apiVersionHeader:'2',contentType:'application/json;charset=UTF-8',json:{apiVersion:2,ok:true}};
  assert.doesNotThrow(()=>assertMetadataVersion(good));
  for(const response of [{...good,apiVersionHeader:undefined},{...good,apiVersionHeader:'1'},
    {...good,json:{apiVersion:'2',ok:true}},{...good,json:{ok:true}},
    {...good,contentType:'text/html'}])assert.throws(()=>assertMetadataVersion(response));
});

test('actual HTTP content-length and chunked modes preserve the whole bounded request', async t => {
  const observed=[];
  const server=http.createServer((request,response) => {
    const hash=createHash('sha256');let bytes=0;
    request.on('data',chunk=>{hash.update(chunk);bytes+=chunk.length;});
    request.on('end',()=>{observed.push({...request.headers,bytes});
      response.setHeader('Content-Type','application/json');response.end(JSON.stringify({ok:true,bytes,hash:hash.digest('hex')}));});
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const url=new URL('http://127.0.0.1:'+server.address().port+'/upload');
  const body=Buffer.alloc(200001,65);
  for (const chunked of [false,true]) {
    const result=await rawPost(url,{'Content-Type':'application/octet-stream'},body,{chunked});
    assert.equal(result.status,200);assert.equal(result.json.bytes,body.length);
    assert.equal(result.json.hash,createHash('sha256').update(body).digest('hex'));
  }
  assert.equal(observed[0]['content-length'],String(body.length));assert.equal(observed[0]['transfer-encoding'],undefined);
  assert.equal(observed[1]['content-length'],undefined);assert.equal(observed[1]['transfer-encoding'],'chunked');
});

test('many actual HTTP drain cycles preserve both framing modes without accumulating listeners',{timeout:5000},async t=>{
  const {observations,warnings}=observeRawPostListeners(t);
  const server=http.createServer((request,response)=>{
    const hash=createHash('sha256');let bytes=0;
    request.on('data',chunk=>{bytes+=chunk.length;hash.update(chunk);});
    request.on('end',()=>response.end(JSON.stringify({bytes,hash:hash.digest('hex')})));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const body=Buffer.alloc(2*1024*1024,65);
  const expectedHash=createHash('sha256').update(body).digest('hex');
  for(const chunked of [false,true]){
    const result=await rawPost(new URL('http://127.0.0.1:'+server.address().port),{},body,{chunked});
    const observed=observations.at(-1);
    assert.equal(result.status,200);assert.equal(result.sentBytes,body.length);
    assert.deepEqual(result.json,{bytes:body.length,hash:expectedHash});
    assert.ok(observed.drains>10,'The real HTTP upload must exercise more than ten drain waits');
    await assertReleasedBackpressureListeners(observed,warnings);
  }
});

test('a peer disconnect during an actual backpressured upload fails and releases pending listeners',{timeout:5000},async t=>{
  const {observations,warnings}=observeRawPostListeners(t);
  let receivedBytes=0;
  const server=http.createServer(request=>{
    request.on('error',()=>{});
    request.on('data',chunk=>{receivedBytes+=chunk.length;
      if(receivedBytes>=1024*1024)request.socket.destroy();
    });
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  await assert.rejects(rawPost(new URL('http://127.0.0.1:'+server.address().port),{},Buffer.alloc(4*1024*1024,65)),
    /Ingress request failed before a complete response/);
  assert.ok(receivedBytes>=1024*1024 && receivedBytes<4*1024*1024,'The peer must close after a real incomplete body prefix');
  assert.ok(observations[0].drains>10,'The peer must close a request that exercised repeated backpressure');
  await assertReleasedBackpressureListeners(observations[0],warnings);
});

test('a stalled actual upload retains its absolute deadline and releases pending listeners',{timeout:5000},async t=>{
  const {observations,warnings}=observeRawPostListeners(t);
  const server=http.createServer(request=>{request.on('error',()=>{});request.pause();});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>{server.closeAllConnections();return new Promise(resolve=>server.close(resolve));});
  const start=performance.now();
  await assert.rejects(rawPost(new URL('http://127.0.0.1:'+server.address().port),{},Buffer.alloc(4*1024*1024,65),{timeout:120}),
    /Ingress absolute request deadline exceeded/);
  assert.ok(performance.now()-start<1000);
  assert.ok(observations[0].drains>10,'The timed-out upload must exercise repeated backpressure');
  await assertReleasedBackpressureListeners(observations[0],warnings);
});

test('truncated, invalid UTF8/JSON and oversized metadata cannot count as successful transport evidence', async t => {
  const server=http.createServer((request,response)=>{
    request.resume(); response.setHeader('Content-Type','application/json');
    if(request.url==='/truncated'){response.setHeader('Content-Length','100');response.end('{"ok":true}');}
    if(request.url==='/invalid'){response.end(Buffer.from([0xff]));}
    if(request.url==='/large'){response.end(Buffer.alloc(65537,65));}
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  for(const route of ['invalid','large','truncated']) {
    await assert.rejects(rawPost(new URL('http://127.0.0.1:'+server.address().port+'/'+route),{},Buffer.from('x'),{timeout:200}));
  }
});

test('a slow drip cannot evade the absolute request deadline',async t=>{
  let timer;
  const server=http.createServer((request,response)=>{
    request.resume();response.setHeader('Content-Type','application/json');response.write('{"ok":');
    timer=setInterval(()=>response.write(' '),20);
    response.on('close',()=>clearInterval(timer));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>{clearInterval(timer);return new Promise(resolve=>server.close(resolve));});
  const start=performance.now();
  await assert.rejects(rawPost(new URL('http://127.0.0.1:'+server.address().port),{},Buffer.from('x'),{timeout:120}),/absolute request deadline/);
  assert.ok(performance.now()-start<1000);
});

test('controlled disconnect sends a real partial body without an EOF that could certify its declaration',async t=>{
  let observed;const incomplete=new Promise(resolve=>{observed=resolve;});
  const server=http.createServer((request,response)=>{
    let bytes=0;request.on('data',chunk=>{bytes+=chunk.length;});
    request.on('aborted',()=>observed({bytes,complete:request.complete}));
    request.on('end',()=>{response.end('{"ok":true}');observed({bytes,complete:request.complete});});
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const prefix=Buffer.alloc(65536,65);
  const result=await interruptPost(new URL('http://127.0.0.1:'+server.address().port),{},prefix,4*1024*1024);
  const actual=await incomplete;
  assert.equal(result.sentPrefixBytes,prefix.length);assert.equal(actual.bytes,prefix.length);assert.equal(actual.complete,false);
});

test('physical upload proof refuses same-length corruption and redirected payload paths',t=>{
  const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'cds-upload-proof-'));
  t.after(()=>fs.rmSync(temporary,{recursive:true}));
  const uploads=path.join(temporary,'uploads');fs.mkdirSync(uploads,{mode:0o700});
  const library=path.join(temporary,'library');const module=path.join(library,'carlos_ctl');fs.mkdirSync(module,{recursive:true});
  fs.writeFileSync(path.join(module,'__init__.py'),'');
  fs.writeFileSync(path.join(module,'o19_preflight.py'),'def parse_properties_text(text):\n return {"cds.import.uploadRoot":'+JSON.stringify(uploads)+'}\n');
  const properties=path.join(temporary,'carlos.properties');fs.writeFileSync(properties,'synthetic',{mode:0o600});
  const id='12345678-1234-1234-1234-123456789abc';const directory=path.join(uploads,id);fs.mkdirSync(directory,{mode:0o700});
  const file=path.join(directory,'source.xml');const body=Buffer.from('complete synthetic source');fs.writeFileSync(file,body,{mode:0o600});
  const expected={bytes:body.length,sha256:createHash('sha256').update(body).digest('hex')};
  assert.deepEqual(verifyUploadedBody(properties,library,id,expected),expected);
  const read=fs.readSync;let appended=false;
  try {
    fs.readSync=(...args)=>{const count=read(...args);if(!appended && count){appended=true;fs.appendFileSync(file,'x');}return count;};
    assert.throws(()=>verifyUploadedBody(properties,library,id,expected),/grew|changed/);
  } finally {fs.readSync=read;}
  fs.writeFileSync(file,Buffer.alloc(body.length,65));
  assert.throws(()=>verifyUploadedBody(properties,library,id,expected),/differ/);
  fs.unlinkSync(file);fs.symlinkSync(properties,file);
  assert.throws(()=>verifyUploadedBody(properties,library,id,expected));
});
