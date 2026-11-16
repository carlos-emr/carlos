/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),net=require('node:net');
const {loopbackSourceProxy}=require('./lib/loopback-source-proxy');
test('opaque fixture relay uses its real source address and refuses a different authority',{timeout:10000},async t=>{
  const seen=[],upstreams=new Set();
  const target=net.createServer(socket=>{
    seen.push(socket.remoteAddress);upstreams.add(socket);socket.once('close',()=>upstreams.delete(socket));
    socket.on('data',data=>socket.write(data));
  });
  await new Promise(resolve=>target.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>{for(const socket of upstreams)socket.destroy();target.close(resolve);}));
  const authority='127.0.0.1:'+target.address().port;
  const relay=await loopbackSourceProxy('https://'+authority+'/carlos','127.0.0.2');t.after(()=>relay.close());
  const proxy=new URL(relay.proxy.server);
  async function connect(destination,payload) {
    const socket=net.connect({host:'127.0.0.1',port:Number(proxy.port)});let received='';
    socket.on('error',()=>{});
    socket.write(`CONNECT ${destination} HTTP/1.1\r\nHost: ${destination}\r\n\r\n${payload}`);
    for await(const chunk of socket){received+=chunk;if(received.includes(payload)||received.includes('403 Forbidden'))break;}
    socket.destroy();return received;
  }
  assert.match(await connect(authority,'OWNED-TUNNEL-PROBE'),/200 Connection Established[\s\S]*OWNED-TUNNEL-PROBE/);
  assert.deepEqual(seen,['127.0.0.2']);
  assert.match(await connect('other.invalid:443','FORBIDDEN-TARGET'),/403 Forbidden/);
  assert.deepEqual(seen,['127.0.0.2']);
});
test('fixture relay rejects remote, credentialed, cleartext and unowned source targets before opening a listener',async()=>{
  for(const url of ['https://other.invalid/carlos','https://user@localhost/carlos','http://127.0.0.1/carlos','https://[::1]/carlos'])
    await assert.rejects(loopbackSourceProxy(url,'127.0.0.2'),/loopback HTTPS/);
  await assert.rejects(loopbackSourceProxy('https://127.0.0.1/carlos','0.0.0.0'),/owned loopback/);
});
