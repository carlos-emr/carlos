/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {createHash} = require('node:crypto');
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const digest = bytes => createHash('sha256').update(bytes).digest('hex');

function privateJson(file) {
  const fd = fs.openSync(file,fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    const stat = fs.fstatSync(fd);
    if (!stat.isFile() || (stat.mode & 0o077) || stat.uid !== process.getuid() || stat.size > 16384) throw Error('RELAY_PRIVATE_CONTROL_INVALID');
    const bytes = Buffer.alloc(16385);let total=0,count;
    while (total < bytes.length && (count=fs.readSync(fd,bytes,total,bytes.length-total,null)) > 0) total+=count;
    if (total !== stat.size || total > 16384) throw Error('RELAY_PRIVATE_CONTROL_INVALID');
    return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes.subarray(0,total)));
  } finally {fs.closeSync(fd);}
}

function publish(file,value) {
  const bytes = Buffer.from(JSON.stringify(value) + '\n');
  if (bytes.length > 16384) throw Error('RELAY_EVENT_CAPACITY');
  const fd=fs.openSync(file,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_NOFOLLOW,0o600);
  try {fs.writeFileSync(fd,bytes);fs.fsyncSync(fd);} finally {fs.closeSync(fd);}
}

/** External disposable proxy fixture: all ordinary bodies stream; one exact accepted ACK is withheld. */
function createRelay({target,controlRoot,attempt,contextPath='/carlos',fallbackMillis=25000}) {
  const upstream = new URL(target);
  if (upstream.protocol !== 'http:' || !['127.0.0.1','localhost','[::1]'].includes(upstream.hostname)
    || upstream.username || upstream.password || upstream.pathname !== '/' || upstream.search || upstream.hash
    || !UUID.test(attempt) || !/^\/[A-Za-z0-9_-]{1,64}$/.test(contextPath)
    || !Number.isSafeInteger(fallbackMillis) || fallbackMillis < 100 || fallbackMillis > 120000) throw Error('RELAY_CONTEXT_INVALID');
  const root = fs.realpathSync(controlRoot), info=fs.statSync(root);
  if (!info.isDirectory() || info.uid !== process.getuid() || (info.mode & 0o077)) throw Error('RELAY_PRIVATE_ROOT_INVALID');
  const selected = name => path.join(root,attempt + '.' + name + '.json');
  const server = http.createServer({maxHeaderSize:32768},(request,response) => {
    let timer;
    const fail = code => {
      if (!response.headersSent) response.writeHead(code,{'Content-Type':'text/plain','Cache-Control':'no-store'});
      response.end('Disposable relay refused the request');
    };
    const forward = (body,armed) => {
      let sent;
      try {sent=http.request({hostname:upstream.hostname,port:upstream.port || 80,path:request.url,
        method:request.method,headers:request.headers,timeout:120000},native => {
        native.once('error',() => {if(response.headersSent)response.destroy();else fail(502);});
        if (!armed || native.statusCode !== 202) {response.writeHead(native.statusCode,native.headers);native.pipe(response);return;}
        let size=0;const parts=[];
        native.on('data',part => {size+=part.length;if(size>2*1024*1024)native.destroy(Error('RELAY_ACK_CAPACITY'));else parts.push(part);});
        native.once('end',() => {
          try {
            const bytes=Buffer.concat(parts,size), value=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));
            if (native.headers['x-carlos-cds-api-version'] !== '2' || value.apiVersion !== 2 || value.ok !== true
              || value.accepted !== true || !UUID.test(value.job?.id || '')) throw Error('RELAY_ACK_CONTRACT_INVALID');
            publish(selected('accepted'),{version:1,attempt,uploadId:armed.uploadId,runId:value.job.id,status:202,
              bodySha256:armed.bodySha256,ackSha256:digest(bytes),acceptedAt:Date.now(),withheld:true});
            // No response headers/body are sent. The real outer Nginx read timer must produce504.
            // The fixture's later fallback only destroys the connection; it never manufactures504.
            timer=setTimeout(() => {response.destroy();},fallbackMillis);
          } catch (_) {fail(502);}
        });
      });} catch (_) {fail(502);return;}
      sent.once('timeout',() => sent.destroy());sent.once('error',() => fail(502));
      response.once('close',() => {clearTimeout(timer);sent.destroy();});
      request.once('aborted',() => sent.destroy());
      if (body) sent.end(body);else request.pipe(sent);
    };
    if (request.method !== 'POST' || request.url !== contextPath + '/form/cdsImportSubmit') {forward(null,null);return;}
    let size=0;const parts=[];
    request.on('data',part => {size+=part.length;if(size>16384)request.destroy();else parts.push(part);});
    request.once('end',() => {
      try {
        const arm=privateJson(selected('arm')), body=Buffer.concat(parts,size), form=new URLSearchParams(new TextDecoder('utf-8',{fatal:true}).decode(body));
        if (arm.attempt !== attempt || arm.bodySha256 !== digest(body) || !UUID.test(arm.uploadId || '')
          || arm.uploadId !== form.get('uploadId') || form.get('apiVersion') !== '2') throw Error('RELAY_ARM_MISMATCH');
        publish(selected('forwarded'),{version:1,attempt,uploadId:arm.uploadId,bodySha256:arm.bodySha256,forwardedAt:Date.now()});
        forward(body,arm);
      } catch (_) {fail(409);}
    });
  });
  server.on('clientError',(_failure,socket) => socket.destroy());
  server.requestTimeout=150000;server.headersTimeout=30000;server.keepAliveTimeout=5000;
  return server;
}

if (require.main === module) {
  try {
    const [target,controlRoot,attempt,contextPath,rawPort]=process.argv.slice(2);
    if (!/^[1-9][0-9]{3,4}$/.test(rawPort || '') || Number(rawPort)>65535) throw Error('RELAY_PORT_INVALID');
    const server=createRelay({target,controlRoot,attempt,contextPath});
    server.listen(Number(rawPort),'127.0.0.1',() => {
      publish(path.join(controlRoot,attempt + '.ready.json'),{version:1,attempt,port:Number(rawPort),pid:process.pid,ready:true});
    });
    server.on('error',() => {console.error('CDS_LIFECYCLE_RELAY_FAILED');process.exitCode=1;});
    const stop=() => {server.closeAllConnections();server.close(() => process.exit(0));};
    for (const signal of ['SIGTERM','SIGINT']) process.on(signal,stop);
    setInterval(() => {if(fs.existsSync(path.join(controlRoot,attempt + '.stop.json'))){privateJson(path.join(controlRoot,attempt + '.stop.json'));stop();}},500).unref();
  } catch (_) {console.error('CDS_LIFECYCLE_RELAY_FAILED');process.exitCode=1;}
}
module.exports={createRelay,privateJson,publish};
