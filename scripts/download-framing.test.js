/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const zlib=require('node:zlib');
const {request}=require('playwright');
const h=require('./lib/playwright-harness');
const contract=require('./lib/download-contract');
const bytes=Buffer.concat([Buffer.from('FAKE framing caf\u00e9 \u03a9\n'),Buffer.from([0,255,254]),Buffer.alloc(8192,73)]);
const headers={'Content-Type':'application/octet-stream','Content-Disposition':'attachment;filename="FAKE.dat"',
  'X-Content-Type-Options':'nosniff',[h.APPLICATION_HEADER]:'1'};

async function fixture(body, use) {
  const server=http.createServer(body);
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  const context=await request.newContext();
  try {await use({request:context},`http://127.0.0.1:${server.address().port}/FAKE.dat`);}
  finally {
    await context.dispose();
    await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));
  }
}

test('identity download preserves complete chunked Unicode and binary bytes without requiring Content-Length',{timeout:10000},async()=>{
  let encoding;
  await fixture((req,res)=>{encoding=req.headers['accept-encoding'];res.writeHead(200,headers);
    res.write(bytes.subarray(0,37));res.end(bytes.subarray(37));},async(context,url)=>{
    const response=await contract.fetchRaw(context,url);
    assert.equal(encoding,'identity');
    assert.equal(response.headers['content-length'],undefined);
    assert.equal(response.headers['transfer-encoding'],'chunked');
    contract.assertServedExactly(response,{bytes,name:'FAKE.dat',label:'FAKE chunked response'});
  });
});

test('premature Content-Length response cannot become a successful download',{timeout:10000},async()=>{
  await fixture((_req,res)=>{res.writeHead(200,{...headers,'Content-Length':String(bytes.length+17)});
    res.write(bytes);setImmediate(()=>res.socket?.destroy());},async(context,url)=>{
    await assert.rejects(contract.fetchRaw(context,url),/socket|closed|reset|response|aborted|terminated|hang/i);
  });
});

test('missing final HTTP chunk cannot become a successful download',{timeout:10000},async()=>{
  await fixture((_req,res)=>{res.writeHead(200,headers);res.write(bytes);
    setImmediate(()=>res.socket?.end());},async(context,url)=>{
    await assert.rejects(contract.fetchRaw(context,url),/socket|closed|reset|response|aborted|terminated|hang/i);
  });
});

test('unexpected compression is rejected before comparing decoded bytes with a wire Content-Length',{timeout:10000},async()=>{
  const compressed=zlib.gzipSync(bytes);
  await fixture((_req,res)=>{res.writeHead(200,{...headers,'Content-Encoding':'gzip','Content-Length':String(compressed.length)});
    res.end(compressed);},async(context,url)=>{
    await assert.rejects(contract.fetchRaw(context,url),/content.encoding|compression/i);
  });
});
