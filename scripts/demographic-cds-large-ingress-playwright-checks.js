#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Opt-in disposable deployment gate. Transfers are never import acceptances.
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const https = require('node:https');
const {once} = require('node:events');
const {createHash, randomBytes} = require('node:crypto');
const {execFileSync} = require('node:child_process');
const h = require('./lib/playwright-harness');
const {importApi} = require('./demographic-cds-import-playwright-checks');
const {assertRetainedLogin} = require('./demographic-cds-retained-recovery-playwright-checks');
const {installCdsMetadataTransportObserver} = require('./lib/cds-metadata-transport');
const {createCdsMetadataProver} = require('./lib/cds-metadata-domain-proof');
const MiB = 1024 * 1024;

function writeFixture(file, payloadBytes) {
  h.assert(Number.isSafeInteger(payloadBytes) && payloadBytes > 50 * MiB, 'Payload must exceed the old50MiB limit');
  const fd = fs.openSync(file, 'wx', 0o600);
  const hash = createHash('sha256'); let bytes = 0;
  const write = value => { const buffer = Buffer.isBuffer(value) ? value : Buffer.from(value);
    fs.writeSync(fd, buffer); hash.update(buffer); bytes += buffer.length; };
  try {
    write('<?xml version="1.0"?><OmdCds xmlns="cds" xmlns:cdsd="cds_dt"><PatientRecord><Demographics>'
      + '<Names><cdsd:LegalName namePurpose="L"><cdsd:FirstName><cdsd:Part>Synthetic</cdsd:Part>'
      + '<cdsd:PartType>GIV</cdsd:PartType></cdsd:FirstName><cdsd:LastName><cdsd:Part>LargeIngress</cdsd:Part>'
      + '<cdsd:PartType>FAMC</cdsd:PartType></cdsd:LastName></cdsd:LegalName></Names>'
      + '<DateOfBirth>1971-03-04</DateOfBirth><Gender>F</Gender><UniqueVendorIdSequence>LARGE-INGRESS-ONLY</UniqueVendorIdSequence>'
      + '<PersonStatusCode><PersonStatusAsEnum>A</PersonStatusAsEnum></PersonStatusCode></Demographics>'
      + '<Reports><Format>Text</Format><Content><cdsd:TextContent>');
    for (let remaining = payloadBytes; remaining > 0;) {
      const chunk = Buffer.from(randomBytes(48 * 1024).toString('base64'));
      const size = Math.min(chunk.length, remaining); write(chunk.subarray(0, size)); remaining -= size;
    }
    write('</cdsd:TextContent></Content><Class>Medical Records Report</Class></Reports></PatientRecord></OmdCds>');
    fs.fsyncSync(fd);
  } finally { fs.closeSync(fd); }
  return {bytes, sha256:hash.digest('hex')};
}

function journalRow(index, offset, bytes, sha256) {
  h.assert([index,offset,bytes].every(Number.isSafeInteger) && index >= 0 && offset >= 0 && bytes > 0
    && /^[0-9a-f]{64}$/.test(sha256), 'Invalid local chunk receipt');
  const row = Buffer.alloc(52); row.writeBigInt64BE(BigInt(index)); row.writeBigInt64BE(BigInt(offset), 8);
  row.writeInt32BE(bytes, 16); Buffer.from(sha256, 'hex').copy(row, 20); return row;
}

// Actual HTTP/1 framing: Node fetch/request helpers must not silently add Content-Length.
async function rawPost(url, headers, body, {chunked = false, length = body.length, timeout = 30000} = {}) {
  const transport = url.protocol === 'https:' ? https : http;
  const requestHeaders = {...headers};
  if (chunked) requestHeaders['Transfer-Encoding'] = 'chunked';
  else requestHeaders['Content-Length'] = String(length);
  return new Promise((resolve, reject) => {
    let resolved = false; let absoluteTimer;
    const failure = message => { if (resolved) return; resolved=true;clearTimeout(absoluteTimer);
      request.destroy();reject(new Error(message)); };
    const request = transport.request(url, {method:'POST', headers:requestHeaders}, response => {
      const chunks = []; let size = 0;
      response.on('data', chunk => {
        size += chunk.length;
        if (size > 65536) { response.destroy();failure('Ingress metadata exceeded64KiB'); }
        else chunks.push(chunk);
      });
      response.on('error', () => failure('Ingress response did not reach complete EOF'));
      response.on('end', () => {
        if (resolved) return;
        if (!response.complete) { failure('Ingress response was truncated'); return; }
        let json; try { json = JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(chunks))); }
        catch (_) { failure('Ingress returned invalid metadata JSON'); return; }
        resolved = true; clearTimeout(absoluteTimer);resolve({status:response.statusCode, json, sentBytes:sent,
          contentType:response.headers['content-type'],apiVersionHeader:response.headers['x-carlos-cds-api-version'],chunked});
      });
    });
    let sent = 0;
    absoluteTimer=setTimeout(()=>failure('Ingress absolute request deadline exceeded'),timeout);
    request.setTimeout(timeout, () => failure('Ingress socket idle deadline exceeded'));
    request.on('error', () => failure('Ingress request failed before a complete response'));
    (async () => {
      for (let offset = 0; offset < body.length && !resolved; offset += 65536) {
        const chunk = body.subarray(offset, Math.min(body.length, offset + 65536)); sent += chunk.length;
        if (!request.write(chunk)) await Promise.race([once(request, 'drain'), once(request, 'close')]);
      }
      request.end();
    })().catch(() => failure('Ingress body transmission failed'));
  });
}

function assertMetadataVersion(response) {
  h.assert(response.apiVersionHeader==='2' && response.json?.apiVersion===2
    && /^application\/json(?:\s*;|$)/i.test(response.contentType || ''),
  'Raw metadata response does not prove the supported CDS API contract');
}

async function interruptPost(url,headers,prefix,declaredBytes) {
  h.assert(prefix.length>0 && prefix.length<declaredBytes,'An interruption requires a genuinely partial request body');
  const transport=url.protocol==='https:'?https:http;
  return new Promise((resolve,reject)=>{
    let settled=false;let interrupted=false;
    const finish=(error)=>{if(settled)return;settled=true;clearTimeout(timer);
      if(error)reject(new Error('Controlled upload interruption could not be established'));
      else resolve({sentPrefixBytes:prefix.length,declaredBytes,connectionClosed:true});};
    const request=transport.request(url,{method:'POST',headers:{...headers,'Content-Length':String(declaredBytes)}},response=>{
      response.destroy();request.destroy();finish(true);
    });
    const timer=setTimeout(()=>{request.destroy();finish(true);},5000);
    request.on('error',()=>{if(!interrupted)finish(true);});
    request.on('close',()=>finish(!interrupted));
    request.write(prefix,error=>{
      if(error){request.destroy();finish(true);return;}
      setTimeout(()=>{interrupted=true;request.destroy();},25);
    });
  });
}

function verifyUploadedBody(properties, library, id, expected) {
  h.assert(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(id),'Invalid owned upload identity');
  const root = execFileSync('python3',['-c',
    'import pathlib,sys;sys.path.insert(0,sys.argv[2]);from carlos_ctl.o19props import parse_properties_text;'
      + 'p=pathlib.Path(sys.argv[1]);assert p.is_file() and not p.is_symlink() and p.stat().st_size<=65536;'
      + 'print(dict(parse_properties_text(p.read_text(encoding="ISO-8859-1")))["cds.import.uploadRoot"])',
    properties,library],{encoding:'utf8',stdio:['ignore','pipe','pipe'],maxBuffer:16384,timeout:30000}).trim();
  h.assert(path.isAbsolute(root) && fs.realpathSync(root)===path.normalize(root)
    && (fs.statSync(root).mode & 0o777)===0o700,'Configured upload root is not the exact private physical directory');
  const directory=path.join(root,id);
  h.assert(fs.realpathSync(directory)===directory && (fs.statSync(directory).mode & 0o777)===0o700,
    'Owned upload directory contains a redirected path');
  const file=path.join(directory,'source.xml');
  const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  const hash=createHash('sha256');let bytes=0;
  try {
    const stat=fs.fstatSync(fd);h.assert(stat.isFile() && stat.size===expected.bytes,'Uploaded body has an unexpected physical size');
    const buffer=Buffer.alloc(65536);let count;
    while((count=fs.readSync(fd,buffer,0,Math.min(buffer.length,expected.bytes-bytes+1),null))>0){
      bytes+=count;h.assert(bytes<=expected.bytes,'Uploaded body grew during bounded physical verification');
      hash.update(buffer.subarray(0,count));
    }
    const after=fs.fstatSync(fd);const selected=fs.lstatSync(file);
    h.assert(after.size===stat.size && after.ino===stat.ino && after.dev===stat.dev
      && after.mtimeMs===stat.mtimeMs && after.ctimeMs===stat.ctimeMs
      && !selected.isSymbolicLink() && selected.ino===stat.ino && selected.dev===stat.dev,
    'Uploaded body identity changed during physical verification');
  } finally {fs.closeSync(fd);}
  const sha256=hash.digest('hex');h.assert(bytes===expected.bytes && sha256===expected.sha256,'Actual retained upload bytes differ from their original source');
  return {bytes,sha256};
}

function serverSample(pid, expectedBase, priorIdentity) {
  h.assert(/^[1-9][0-9]*$/.test(String(pid)) && path.isAbsolute(expectedBase || ''),'An exact disposable Tomcat PID/base is required');
  const root = '/proc/'+pid; const args = fs.readFileSync(root+'/cmdline').toString().split('\0');
  h.assert(args.includes('org.apache.catalina.startup.Bootstrap') && args.includes('-Dcatalina.base='+expectedBase),
    'Metrics PID is not the selected disposable Tomcat');
  const stat = fs.readFileSync(root+'/stat','utf8').split(') ').at(-1).split(' ');
  const identity = pid+':'+stat[19];
  h.assert(!priorIdentity || priorIdentity === identity,'Tomcat restarted during the ingress measurement');
  const status = fs.readFileSync(root+'/status','utf8'); const value = key => {
    const match = status.match(new RegExp('^'+key+':\\s+(\\d+) kB$', 'm'));
    h.assert(match,'Required resident-memory metric is unavailable'); return Number(match[1])*1024;
  };
  return {identity,rssBytes:value('VmRSS'),highWaterBytes:value('VmHWM')};
}

async function run(env = process.env) {
  h.assert(env.CDS_LARGE_INGRESS_DISPOSABLE === 'true', 'Explicit disposable large-ingress authorization is required');
  const evidence = env.CDS_LARGE_INGRESS_EVIDENCE;
  h.assert(evidence && path.isAbsolute(evidence) && !fs.existsSync(evidence), 'Evidence directory must be absent and absolute');
  fs.mkdirSync(evidence, {mode:0o700});
  const report = {protocol:1, candidate:env.CDS_CANDIDATE_SHA256 || '', status:'RUNNING', transfers:[],ownedUploads:[]};
  h.assert(/^[0-9a-f]{64}$/.test(report.candidate), 'Freeze the exact deployed WAR SHA256');
  const rssBudget = Number(env.CDS_LARGE_INGRESS_MAX_RSS_MIB || 2560)*MiB;
  const growthBudget = Number(env.CDS_LARGE_INGRESS_MAX_RSS_GROWTH_MIB || 256)*MiB;
  h.assert(Number.isSafeInteger(rssBudget) && rssBudget > 0 && Number.isSafeInteger(growthBudget) && growthBudget > 0,
    'Numeric resident-memory budgets are required');
  const first = serverSample(env.CDS_LARGE_INGRESS_SERVER_PID,env.CDS_LARGE_INGRESS_TOMCAT_BASE);
  h.assert(first.highWaterBytes<=rssBudget && first.highWaterBytes-first.rssBytes<=growthBudget,
    'Existing Tomcat high-water state cannot prove the requested growth budget; a fresh controlled node is required');
  report.memory = {initialRssBytes:first.rssBytes,initialHighWaterBytes:first.highWaterBytes,
    peakRssBytes:first.rssBytes,peakHighWaterBytes:first.highWaterBytes,samples:1,rssBudget,growthBudget};
  let metricsFailure;
  const sample = () => { try {
    const current = serverSample(env.CDS_LARGE_INGRESS_SERVER_PID,env.CDS_LARGE_INGRESS_TOMCAT_BASE,first.identity);
    report.memory.peakRssBytes = Math.max(report.memory.peakRssBytes,current.rssBytes); report.memory.samples++;
    report.memory.peakHighWaterBytes=Math.max(report.memory.peakHighWaterBytes,current.highWaterBytes);
    h.assert(current.highWaterBytes<=rssBudget && current.rssBytes-first.rssBytes<=growthBudget
      && current.highWaterBytes-first.rssBytes<=growthBudget,'Ingress resident-memory budget exceeded');
  } catch (_) { metricsFailure = true; } };
  const metricTimer = setInterval(sample,100);
  const save = () => fs.writeFileSync(path.join(evidence,'proof.json'), JSON.stringify(report, null, 2), {mode:0o600});
  metricTimer.unref();save(); let browser; let sql;
  try {
    const config = h.readConfig();
    h.assert(env.CDS_LARGE_INGRESS_BASE_URL && /^[0-9a-f]{32}$/.test(env.CDS_LARGE_INGRESS_PROXY_TOKEN || ''),
      'An explicit owned real reverse-proxy URL and independently recorded route nonce are required');
    config.baseUrl=h.validateBaseUrl(env.CDS_LARGE_INGRESS_BASE_URL);
    config.ignoreHTTPSErrors=false;
    h.assert(/(?:^|_)test$/.test(config.mysql.database), 'A disposable test database is required');
    sql = h.createSqlRunner(config.mysql);
    const clinicalCounts = () => sql.value(`SELECT CONCAT_WS('|', (SELECT COUNT(*) FROM cds_import_run),
      (SELECT COUNT(*) FROM cds_import_checkpoint), (SELECT COUNT(*) FROM demographic),
      (SELECT COUNT(*) FROM drugs), (SELECT COUNT(*) FROM allergies), (SELECT COUNT(*) FROM preventions),
      (SELECT COUNT(*) FROM casemgmt_note), (SELECT COUNT(*) FROM document),
      (SELECT COUNT(*) FROM cds_import_patient WHERE held=1))`);
    const before = clinicalCounts(); report.before = before;
    browser = await h.launchBrowser(config); const context = await h.newContext(browser, config);
    const loginRecorder = h.createRecorder(); await h.login(context, config, loginRecorder);
    const retained = sql.value("SELECT maintenance_active FROM cds_import_admission WHERE id='clinical-maintenance'") === '1';
    assertRetainedLogin(loginRecorder, config.baseUrl, retained);
    const page = await context.newPage(); const recorder = h.createRecorder(); h.wireStrictPage(page,'large-ingress',recorder);
    const transport = await installCdsMetadataTransportObserver(page,recorder,{baseUrl:config.baseUrl});
    const prover = createCdsMetadataProver({frame:page,config,sql,readApi:importApi});
    const qualify = async () => {
      await page.waitForLoadState('networkidle',{timeout:10000});
      for (const audit of await transport.qualifyPending(prover)) {
        (report.metadataEofEvidence ||= []).push({rawError:'net::ERR_ABORTED',...audit});
      }
      h.assertStrictPage(recorder);
    };
    const form = await page.goto(h.appUrl(config.baseUrl,'/form/importUpload'));
    h.assert(form.status() === 200, 'Authorized large-ingress form is unavailable');
    h.assert((await form.allHeaders())['x-carlos-cds-test-proxy']===env.CDS_LARGE_INGRESS_PROXY_TOKEN,
      'Large-ingress requests did not traverse the independently owned real HTTP reverse proxy');
    await page.locator('#cdsImport').waitFor();
    await qualify();
    const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const url = new URL(h.appUrl(config.baseUrl,'/form/cdsUpload'));
    const cookies = await context.cookies(url.href);
    const headers = {'Cookie':cookies.map(cookie => cookie.name+'='+cookie.value).join('; '),
      'CSRF-TOKEN':token, 'X-Requested-With':'XMLHttpRequest','Content-Type':'application/octet-stream'};
    const reservationCounts=()=>sql.value(`SELECT CONCAT_WS('|',(SELECT COUNT(*) FROM cds_import_upload),
      (SELECT COALESCE(SUM(reserved_bytes),0) FROM cds_import_admission))`);
    const beforeCsrf=reservationCounts();report.csrfRefusals=[];
    for(const kind of ['missing','invalid']) {
      const csrfHeaders={'X-Requested-With':'XMLHttpRequest'};
      if(kind==='invalid')csrfHeaders['CSRF-TOKEN']='synthetic-invalid-csrf-token';
      const denied=await context.request.post(url.href,{form:{apiVersion:'2',operation:'begin',fileName:'synthetic-csrf.xml',expectedBytes:'1'},
        headers:csrfHeaders,maxRedirects:0,timeout:30000});
      const status=denied.status();await denied.dispose();
      h.assert(status===403 && reservationCounts()===beforeCsrf,'Invalid CSRF was not refused before durable upload reservation');
      report.csrfRefusals.push({kind,status,reservationCountsUnchanged:true});save();
    }
    const source = path.join(evidence,'synthetic-large.xml'); report.source = writeFixture(source, 64 * MiB);
    const contextPath=new URL(config.baseUrl).pathname.replace(/^\/+|\/+$/g,'');
    h.assert(/^[A-Za-z0-9_-]+$/.test(contextPath),'Physical ingress proof requires one explicit deployed application context');
    const properties=path.join(env.CDS_LARGE_INGRESS_TOMCAT_BASE,'webapps',contextPath,'WEB-INF/classes/carlos.properties');
    const original = fs.openSync(source,'r');
    try { execFileSync('xmllint',['--nonet','--stream','--noout','--schema',
      path.join(__dirname,'../src/main/resources/cds-import/schema/EMR_Data_Migration_Schema.xsd'),'-'],
    {stdio:[original,'ignore','pipe'],timeout:120000,maxBuffer:65536}); }
    finally { fs.closeSync(original); }
    for (const chunked of [false,true]) {
      const start = performance.now();
      const begin = await importApi(page,'cdsUpload',{operation:'begin',fileName:'synthetic-large.xml',expectedBytes:report.source.bytes},'POST');
      h.assert(begin.status === 200 && begin.body?.ok === true && begin.body.upload?.state === 'OPEN','Large upload reservation failed');
      const upload = begin.body.upload;const ownership={uploadId:upload.id,phase:'RESERVED',framing:chunked?'chunked':'content-length'};
      report.ownedUploads.push(ownership);save();
      h.assert(Number.isSafeInteger(upload.chunkBytes) && upload.chunkBytes >= 65536 && upload.chunkBytes <= 16 * MiB,'Unsafe server chunk bound');
      const fd = fs.openSync(source,'r'); const digest = createHash('sha256'); let offset = 0; let index = 0;
      try {
        const buffer = Buffer.alloc(upload.chunkBytes);
        while (offset < report.source.bytes) {
          const expected = Math.min(buffer.length, report.source.bytes-offset);
          const count = fs.readSync(fd,buffer,0,expected,offset); h.assert(count === expected,'Synthetic source shortened');
          const body = buffer.subarray(0,count); const sha256 = createHash('sha256').update(body).digest('hex');
          const endpoint = new URL(url); for (const [key,value] of Object.entries({apiVersion:2,operation:'chunk',uploadId:upload.id,offset,bytes:count,sha256})) endpoint.searchParams.set(key,String(value));
          if(!chunked && index===0) {
            ownership.phase='INTERRUPTION_STARTED';save();
            report.interruption={uploadId:upload.id,...await interruptPost(endpoint,headers,body.subarray(0,65536),count)};save();
            const current=await importApi(page,'cdsImportStatus',{section:'upload',uploadId:upload.id});
            h.assert(current.body?.upload?.receivedBytes===0 && current.body?.upload?.committedChunks===0
              && current.body?.upload?.runId===null,'An incomplete transport body advanced its durable checkpoint');
            ownership.phase='RESUMING_SAME_UUID';save();
          }
          let response;
          for(let attempt=0;attempt<10;attempt++) {
            response=await rawPost(endpoint,headers,body,{chunked});
            assertMetadataVersion(response);
            if(response.status!==409 || response.json?.code!=='UPLOAD_BUSY')break;
            await new Promise(resolve=>setTimeout(resolve,100));
          }
          h.assert(response.status === 200 && response.json?.ok === true && response.json.upload?.receivedBytes === offset+count,
            'Actual framed request did not commit its exact bounded receipt');
          digest.update(journalRow(index,offset,count,sha256)); index++; offset += count;
        }
      } finally { fs.closeSync(fd); }
      const done = await importApi(page,'cdsUpload',{operation:'finish',uploadId:upload.id},'POST');
      const manifest = digest.digest('hex');
      h.assert(done.status === 200 && done.body?.ok === true && done.body.upload?.state === 'COMPLETE'
        && done.body.upload?.manifestSha256 === manifest && done.body.upload?.committedChunks === index
        && done.body.upload?.receivedBytes === report.source.bytes && done.body.upload?.runId === null
        && done.body.upload?.dryRunId === null,'Completed transfer lacks its exact independent chunk manifest or unexpectedly accepted a job');
      ownership.phase='TRANSFER_COMPLETE';save();
      const actualBody=verifyUploadedBody(properties,env.CDS_LARGE_INGRESS_CTL_LIBRARY || '/usr/lib/carlos-ctl',upload.id,report.source);
      report.transfers.push({uploadId:upload.id,framing:chunked?'chunked':'content-length',bytes:offset,chunks:index,
        manifestSha256:manifest,actualBody,elapsedMs:Math.round(performance.now()-start)}); save();
      // This is an explicitly unaccepted transfer; supported abandon owns its cleanup.
      const abandon = await importApi(page,'cdsUpload',{operation:'abandon',uploadId:upload.id},'POST');
      h.assert(abandon.status === 200 && abandon.body?.upload?.state === 'ABANDONED','Supported unaccepted transfer retirement failed');
      ownership.phase='ABANDONED';save();
    }
    const begin = await importApi(page,'cdsUpload',{operation:'begin',fileName:'synthetic-limit.xml',expectedBytes:8*MiB},'POST');
    h.assert(begin.status === 200 && begin.body?.upload?.id,'Oversize probe reservation failed');
    const probe = begin.body.upload;const probeOwnership={uploadId:probe.id,phase:'RESERVED',purpose:'OVERSIZE_REFUSAL'};
    report.ownedUploads.push(probeOwnership);report.refusals=[];save();
    h.assert(Number.isSafeInteger(probe.chunkBytes) && probe.chunkBytes>=65536 && probe.chunkBytes<=16*MiB,
      'Oversize probe has an unsafe advertised chunk bound');
    for (const chunked of [false,true]) {
      const body = Buffer.alloc(probe.chunkBytes+1,65); const endpoint = new URL(url);
      for (const [key,value] of Object.entries({apiVersion:2,operation:'chunk',uploadId:probe.id,offset:0,bytes:probe.chunkBytes,
        sha256:createHash('sha256').update(body.subarray(0,probe.chunkBytes)).digest('hex')})) endpoint.searchParams.set(key,String(value));
      const response = await rawPost(endpoint,headers,body,{chunked});
      assertMetadataVersion(response);
      h.assert([400,413].includes(response.status) && response.json?.ok === false,'Oversized actual request was not refused');
      const current = await importApi(page,'cdsImportStatus',{section:'upload',uploadId:probe.id});
      h.assert(current.body?.upload?.receivedBytes === 0 && current.body?.upload?.committedChunks === 0
        && current.body?.upload?.runId === null,'Oversize refusal committed bytes or accepted clinical work');
      report.refusals.push({framing:chunked?'chunked':'content-length',status:response.status,sentBytes:response.sentBytes});
    }
    h.assert((await importApi(page,'cdsUpload',{operation:'abandon',uploadId:probe.id},'POST')).status === 200,'Probe retirement failed');
    probeOwnership.phase='ABANDONED';save();
    report.after = clinicalCounts(); h.assert(report.after === before,'Ingress gate changed clinical jobs, checkpoints, native rows or holds');
    sample(); h.assert(!metricsFailure && report.memory.samples >= 2,'Required stable-process resident-memory measurements failed');
    await qualify(); report.status='PASSED'; save();
    console.log('PASS large ingress: two actual64MiB transfers, exact framed chunk receipts, oversize refusals, zero clinical acceptance');
  } catch (_) { report.status='FAILED'; save(); throw new Error('Large-ingress gate failed; inspect protected evidence and retained upload IDs'); }
  finally { clearInterval(metricTimer); if (browser) await browser.close(); if (sql) sql.dispose(); }
}
if (require.main === module) run().catch(error => { console.error(error.message); process.exitCode=1; });
module.exports = {writeFixture,journalRow,rawPost,interruptPost,assertMetadataVersion,serverSample,verifyUploadedBody,run};
