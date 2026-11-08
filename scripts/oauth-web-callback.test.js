/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),net=require('node:net');
const {callbackReceiver,redactOAuthError,workflow}=require('./oauth-web-callback-playwright-checks');

test('the actual receiver records the callback while its body discloses no token, verifier or secret',async()=>{
  const receiver=callbackReceiver();
  try {
    const origin=await receiver.listen();
    const response=await fetch(origin+'/callback?context=owned%20%26%20value&oauth_token=request-fixture&oauth_verifier=verifier-fixture',
      {headers:{Connection:'close'}});
    assert.equal(response.status,200);const body=await response.text();
    assert(!body.includes('request-fixture')&&!body.includes('verifier-fixture'));
    assert.equal(response.headers.get('cache-control'),'no-store');
    assert.equal(receiver.requests.length,1);
    assert.equal(receiver.requests[0].url.searchParams.get('context'),'owned & value');
    assert.equal(receiver.requests[0].url.searchParams.get('oauth_token'),'request-fixture');
    assert.equal(receiver.requests[0].url.searchParams.get('oauth_verifier'),'verifier-fixture');
  }finally{await receiver.close();}
  await receiver.close();
  await assert.rejects(receiver.listen(),/cannot be reopened/);
});
test('cleanup during a pending bind waits for and retires the acquired receiver',async()=>{
  const receiver=callbackReceiver(),opening=receiver.listen(),closing=receiver.close();
  const origin=await opening;await closing;
  await assert.rejects(fetch(origin+'/callback',{signal:AbortSignal.timeout(1000)}));
});
test('a malformed incoming connection remains a failure after its socket and receiver are closed',async()=>{
  const receiver=callbackReceiver(),origin=await receiver.listen(),address=new URL(origin);
  await new Promise((resolve,reject)=>{
    const socket=net.createConnection({host:address.hostname,port:Number(address.port)},()=>socket.end('INVALID HTTP REQUEST\r\n\r\n'));
    socket.on('error',reject);socket.on('close',resolve);
  });
  assert(receiver.errors.length>0);await assert.rejects(receiver.close(),/receiver reported an error/);
  await assert.rejects(fetch(origin+'/callback',{signal:AbortSignal.timeout(1000)}));
});
test('OAuth diagnostics keep failure attribution while removing URL queries and known credentials',()=>{
  const error=new Error('page.goto failed: https://clinic.test/oauth?oauth_token=request-fixture; Authorization token=access-fixture secret=secret-fixture');
  error.failedStep='native callback';
  const result=redactOAuthError(error,new Set(['request-fixture','access-fixture','secret-fixture']));
  assert.equal(result,error);assert.equal(result.failedStep,'native callback');
  for(const secret of ['request-fixture','access-fixture','secret-fixture'])assert(!result.message.includes(secret)&&!result.stack.includes(secret));
  assert(result.message.includes('page.goto failed')&&result.message.includes('https://clinic.test/oauth'));
});
test('a namespace collision never adopts or deletes an existing OAuth client',async()=>{
  const cleanups=[];let ownershipReads=0,writes=0;
  await assert.rejects(workflow({marker:'FAKE-PW0123456789abcdef',patient:'1',provider:'999998',config:{},
    sql:{value:()=> '1',rows:()=>{ownershipReads++;return [];},execute:()=>{writes++;}},cleanup:fn=>cleanups.push(fn)}),/namespace collision/);
  for(const cleanup of cleanups.reverse())await cleanup();
  assert.equal(ownershipReads,0);assert.equal(writes,0);
});
