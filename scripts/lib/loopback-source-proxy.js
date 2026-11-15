/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const http=require('node:http'),net=require('node:net');
const h=require('./playwright-harness');

// An opaque CONNECT relay gives a browser a distinct, real loopback source
// address. It can reach only the configured local application authority and
// never decrypts TLS, rewrites forwarding headers or accepts external targets.
async function loopbackSourceProxy(base,source) {
  const target=new URL(base);
  h.assert(target.protocol==='https:'&&['127.0.0.1','localhost'].includes(target.hostname)&&!target.username&&!target.password,'Address-lock fixture requires a loopback HTTPS application on IPv4');
  h.assert(['127.0.0.2','127.0.0.3'].includes(source),'Unsupported owned loopback source address');
  const authority=target.host,port=Number(target.port||443),sockets=new Set();
  const server=http.createServer((request,response)=>{response.writeHead(405);response.end();});
  server.on('connection',socket=>{sockets.add(socket);socket.once('close',()=>sockets.delete(socket));});
  server.on('connect',(request,client,head)=>{
    if(request.url!==authority&&request.url!==target.hostname+':'+port) {client.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');return;}
    const upstream=net.connect({host:'127.0.0.1',port,localAddress:source},()=>{
      client.write('HTTP/1.1 200 Connection Established\r\n\r\n');if(head.length)upstream.write(head);
      client.pipe(upstream);upstream.pipe(client);
    });
    sockets.add(upstream);upstream.once('close',()=>sockets.delete(upstream));
    upstream.on('error',()=>client.destroy());client.on('error',()=>upstream.destroy());client.once('close',()=>upstream.destroy());
  });
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  return {proxy:{server:'http://127.0.0.1:'+server.address().port,bypass:'<-loopback>'},
    async close(){for(const socket of sockets)socket.destroy();await new Promise(resolve=>server.close(resolve));}};
}
module.exports={loopbackSourceProxy};
