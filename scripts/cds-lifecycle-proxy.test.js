/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const {createHash,randomUUID} = require('node:crypto');
const {createRelay,publish} = require('./lib/cds-lifecycle-proxy-relay');
const hash = value => createHash('sha256').update(value).digest('hex');
const listen = server => new Promise(resolve => server.listen(0,'127.0.0.1',() => resolve(server.address().port)));
const close = server => new Promise(resolve => {server.closeAllConnections();server.close(resolve);});
function get(port,route,body) {
  return new Promise((resolve,reject) => {
    const request=http.request({host:'127.0.0.1',port,path:route,method:body ? 'POST' : 'GET',
      headers:body ? {'Content-Type':'application/x-www-form-urlencoded','Content-Length':Buffer.byteLength(body),Cookie:'JSESSIONID=private-unit-cookie'} : {}},response => {
      const chunks=[];response.on('data',chunk => chunks.push(chunk));response.on('end',() => resolve({status:response.statusCode,body:Buffer.concat(chunks).toString()}));
    });request.on('error',reject);request.end(body);
  });
}
async function fixture(response) {
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'cds-owned-relay-')),attempt=randomUUID(),uploadId=randomUUID(),runId=randomUUID();
  let calls=0;
  const upstream=http.createServer((request,reply) => {calls++;const parts=[];request.on('data',part => parts.push(part));
    request.on('end',() => response(reply,{runId,request,body:Buffer.concat(parts).toString()}));});
  const target=await listen(upstream);
  const relay=createRelay({target:'http://127.0.0.1:' + target,controlRoot:directory,attempt,fallbackMillis:1000});
  const port=await listen(relay),body=new URLSearchParams({apiVersion:'2',uploadId,providerNo:'owned'}).toString();
  publish(path.join(directory,attempt + '.arm.json'),{attempt,uploadId,bodySha256:hash(body)});
  return {directory,attempt,uploadId,runId,port,body,calls:() => calls,
    async cleanup(){await close(relay);await close(upstream);fs.rmSync(directory,{recursive:true,force:true});}};
}

test('the external relay streams ordinary native responses unchanged',async () => {
  const f=await fixture(reply => {reply.writeHead(200,{'Content-Type':'text/plain'});reply.end('ordinary-native-body');});
  try {assert.deepEqual(await get(f.port,'/carlos/index'),{status:200,body:'ordinary-native-body'});assert.equal(f.calls(),1);}
  finally {await f.cleanup();}
});

test('only an exact armed contract2 submit is forwarded once and its genuine accepted ACK is withheld',async () => {
  const f=await fixture((reply,{runId,request,body}) => {
    assert.equal(request.headers.cookie,'JSESSIONID=private-unit-cookie');assert.equal(body,f.body);
    reply.writeHead(202,{'X-CARLOS-CDS-API-Version':'2','Content-Type':'application/json'});
    reply.end(JSON.stringify({apiVersion:2,ok:true,accepted:true,job:{id:runId}}));});
  try {
    await new Promise((resolve,reject) => {
      const request=http.request({host:'127.0.0.1',port:f.port,path:'/carlos/form/cdsImportSubmit',method:'POST',headers:{Cookie:'JSESSIONID=private-unit-cookie'}},() => reject(Error('The ACK must not reach the client')));
      request.on('error',failure => {if(failure.message==='UNIT_CLIENT_TIMEOUT')resolve();else reject(failure);});
      request.end(f.body);setTimeout(() => request.destroy(Error('UNIT_CLIENT_TIMEOUT')),100);
    });
    const raw=fs.readFileSync(path.join(f.directory,f.attempt + '.accepted.json'),'utf8'),value=JSON.parse(raw);
    assert.equal(value.runId,f.runId);assert.equal(value.uploadId,f.uploadId);assert.equal(value.status,202);assert.equal(value.withheld,true);
    assert.equal(raw.includes('private-unit-cookie'),false);assert.equal(f.calls(),1);
    assert.equal((await get(f.port,'/carlos/form/cdsImportSubmit',f.body)).status,409);
    assert.equal(f.calls(),1);
  } finally {await f.cleanup();}
});

test('a real native refusal is forwarded rather than stalled or called accepted',async () => {
  const f=await fixture(reply => {reply.writeHead(403);reply.end('native-refusal');});
  try {
    assert.deepEqual(await get(f.port,'/carlos/form/cdsImportSubmit',f.body),{status:403,body:'native-refusal'});
    assert.equal(fs.existsSync(path.join(f.directory,f.attempt + '.accepted.json')),false);
  } finally {await f.cleanup();}
});

test('wrong upload/body identity never reaches the native server',async () => {
  const f=await fixture(reply => reply.end('must-not-run'));
  try {assert.equal((await get(f.port,'/carlos/form/cdsImportSubmit',f.body + '&extra=changed')).status,409);assert.equal(f.calls(),0);}
  finally {await f.cleanup();}
});

test('unversioned or oversized accepted bodies fail closed without a fabricated timeout receipt',async () => {
  for (const oversized of [false,true]) {
    const f=await fixture(reply => {reply.writeHead(202,{'X-CARLOS-CDS-API-Version':oversized ? '2' : '1'});
      reply.end(oversized ? 'x'.repeat(2*1024*1024+1) : '{}');});
    try {assert.equal((await get(f.port,'/carlos/form/cdsImportSubmit',f.body)).status,502);
      assert.equal(fs.existsSync(path.join(f.directory,f.attempt + '.accepted.json')),false);}
    finally {await f.cleanup();}
  }
});

test('relay cannot target a remote origin or use an unbounded fallback',() => {
  assert.throws(() => createRelay({target:'https://example.com/',attempt:randomUUID()}),/CONTEXT/);
  assert.throws(() => createRelay({target:'http://127.0.0.1/',attempt:randomUUID(),fallbackMillis:Infinity}),/CONTEXT/);
});
