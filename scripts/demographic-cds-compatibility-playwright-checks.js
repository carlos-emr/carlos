#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Installed compatibility evidence. Cached assets come from actual immutable WARs;
// every HTTP endpoint remains the real authenticated application.
const fs = require('node:fs');
const path = require('node:path');
const {createHash,randomUUID} = require('node:crypto');
const {execFileSync} = require('node:child_process');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {workflow: ordinaryDocumentUpload} = require('./popup-opener-document-add-playwright-checks');
const {authzReadFixture} = require('./lib/authz-read-fixture');
const {signIn} = require('./lib/authz-read-probe');
const {openImportFrame} = require('./demographic-cds-import-playwright-checks');
const {textPdf} = require('./lib/stored-pdf-documents');
const {API_VERSION,API_VERSION_HEADER} = require('../src/main/webapp/js/demographic/cds-import');
const UUID = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const SHA = /^[0-9a-f]{64}$/;

function privateJson(file,value) {
  const pending=file+'.'+randomUUID();
  const fd=fs.openSync(pending,'wx',0o600);
  try {fs.writeFileSync(fd,JSON.stringify(value,null,2));fs.fsyncSync(fd);} finally {fs.closeSync(fd);}
  fs.renameSync(pending,file);
  const directory=fs.openSync(path.dirname(file),fs.constants.O_RDONLY|fs.constants.O_DIRECTORY);
  try {fs.fsyncSync(directory);} finally {fs.closeSync(directory);}
}
function hashFile(file) {
  const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  const hash=createHash('sha256');const buffer=Buffer.alloc(65536);let n;
  try {while((n=fs.readSync(fd,buffer,0,buffer.length,null))>0)hash.update(buffer.subarray(0,n));}
  finally {fs.closeSync(fd);}
  return hash.digest('hex');
}
function loadBundle(entry) {
  h.assert(entry && SHA.test(entry.warSha256) && SHA.test(entry.sha256) && path.isAbsolute(entry.file)
    && path.isAbsolute(entry.warFile),
    'An immutable actual-WAR cached-bundle proof is required');
  const stat=fs.lstatSync(entry.file);
  h.assert(stat.isFile() && !stat.isSymbolicLink() && stat.size>0 && stat.size<=1024*1024,
    'Cached bundle must be a bounded regular file');
  h.assert(hashFile(entry.file)===entry.sha256,'Cached bundle bytes changed after immutable-WAR extraction');
  const verified=execFileSync('python3',['-c',
    'import hashlib,pathlib,sys,zipfile;p=pathlib.Path(sys.argv[1]);'
      +'assert p.is_absolute() and p.is_file() and not p.is_symlink();'
      +'s=p.open("rb");h=hashlib.file_digest(s,"sha256").hexdigest();s.close();assert h==sys.argv[2];'
      +'z=zipfile.ZipFile(p);assert len(z.infolist())<=50000;'
      +'a=[e for e in z.infolist() if e.filename=="js/demographic/cds-import.js"];'
      +'assert len(a)==1 and 0<a[0].file_size<=1048576;'
      +'b=z.read(a[0]);assert hashlib.sha256(b).hexdigest()==sys.argv[3];sys.stdout.buffer.write(b)',
    entry.warFile,entry.warSha256,entry.sha256,
  ],{maxBuffer:1024*1024+1024,timeout:30000,stdio:['ignore','pipe','pipe']});
  h.assert(verified.equals(fs.readFileSync(entry.file)),'Cached source is not the exact member of its actual immutable WAR');
  return verified;
}
function loadPrivate(file) {
  h.assert(file && path.isAbsolute(file),'Protected compatibility selectors are required');
  const stat=fs.lstatSync(file);
  h.assert(stat.isFile() && !stat.isSymbolicLink() && !(stat.mode&0o077) && stat.size<=65536,
    'Compatibility selectors must remain private and bounded');
  return JSON.parse(fs.readFileSync(file,'utf8'));
}
function createArchivedSqlRunner(config,exec=execFileSync) {
  h.assert(Number.isInteger(config?.port) && config.port>=1 && config.port<=65535,
    'The isolated archived database requires its explicit TCP port');
  return h.createSqlRunner(config,{exec:(command,args,options)=>
    exec(command,[args[0],`--port=${config.port}`,...args.slice(1)],options)});
}
function requireDocumentStore(directory) {
  h.assert(typeof directory==='string' && path.isAbsolute(directory) && fs.existsSync(directory)
    && fs.statSync(directory).isDirectory(),
  'The required installed compatibility gate needs its readable ordinary DOCUMENT_DIR; it cannot skip that proof');
  fs.accessSync(directory,fs.constants.R_OK|fs.constants.X_OK);
}
function inventory(sql) {
  return sql.value(`SELECT CONCAT_WS('|',
    (SELECT COUNT(*) FROM cds_import_run),(SELECT COALESCE(SUM(review_version),0) FROM cds_import_run),
    (SELECT COALESCE(SUM(owner_epoch),0) FROM cds_import_run),
    (SELECT COUNT(*) FROM cds_import_patient),(SELECT COUNT(*) FROM cds_import_patient WHERE held=1),
    (SELECT COUNT(*) FROM cds_import_unit),(SELECT COUNT(*) FROM cds_import_checkpoint),
    (SELECT COUNT(*) FROM cds_import_event),(SELECT COUNT(*) FROM cds_import_upload),
    (SELECT COALESCE(SUM(version),0) FROM cds_import_upload),
    (SELECT COALESCE(SUM(received_bytes),0) FROM cds_import_upload),
    (SELECT COALESCE(SUM(chunk_count),0) FROM cds_import_upload),
    (SELECT COALESCE(SUM(reserved_bytes),0) FROM cds_import_admission),
    (SELECT COUNT(*) FROM demographic),(SELECT COUNT(*) FROM drugs),(SELECT COUNT(*) FROM allergies),
    (SELECT COUNT(*) FROM preventions),(SELECT COUNT(*) FROM document),(SELECT COUNT(*) FROM casemgmt_note))`);
}
function treeProof(roots) {
  h.assert(Array.isArray(roots) && roots.length>0 && roots.length<=4,'Explicit owned state roots are required');
  return roots.map((root,index)=>{
    h.assert(path.isAbsolute(root) && fs.realpathSync(root)===path.normalize(root),'State root must be physical and explicit');
    const hash=createHash('sha256');let entries=0,bytes=0;
    const stack=[{directory:root,reader:fs.opendirSync(root),depth:0}];
    try {
      while(stack.length) {
        const current=stack[stack.length-1];const child=current.reader.readSync();
        if(!child){current.reader.closeSync();stack.pop();continue;}
        h.assert(++entries<=100000 && current.depth<128,'State inventory exceeded its bounded proof budget');
        const file=path.join(current.directory,child.name);const stat=fs.lstatSync(file);
        hash.update(JSON.stringify([path.relative(root,file),stat.mode,stat.dev,stat.ino,stat.size,stat.mtimeMs,stat.ctimeMs])+'\n');
        if(stat.isFile())bytes+=stat.size;
        if(stat.isDirectory() && !stat.isSymbolicLink())stack.push({directory:file,reader:fs.opendirSync(file),depth:current.depth+1});
      }
    } finally {for(const current of stack)current.reader.closeSync();}
    return {root:index,entries,bytes,metadataSha256:hash.digest('hex')};
  });
}
async function responseBytes(response,maximum=2*1024*1024) {
  h.assert(response.body,'The actual HTTP response has no stream');
  const chunks=[];let bytes=0;
  await response.body.pipeTo(new WritableStream({write(chunk){bytes+=chunk.byteLength;
    h.assert(bytes<=maximum,'Actual HTTP metadata exceeded its bounded evidence contract');chunks.push(chunk);}}));
  return Buffer.concat(chunks,bytes);
}
async function request(context,url,{method='GET',params={},token,maximum=65536}={}) {
  const target=new URL(url);let body;
  if(method==='GET'||method==='HEAD')for(const [key,value]of Object.entries(params))target.searchParams.set(key,String(value));
  else body=new URLSearchParams(Object.entries(params).map(([key,value])=>[key,String(value)]));
  const cookies=await context.cookies(target.href);
  const headers={'Cookie':cookies.map(cookie=>cookie.name+'='+cookie.value).join('; '),'X-Requested-With':'XMLHttpRequest'};
  if(token)headers['CSRF-TOKEN']=token;
  const response=await fetch(target,{method,headers,body,redirect:'manual',signal:AbortSignal.timeout(30000)});
  const bytes=method==='HEAD'?Buffer.alloc(0):await responseBytes(response,maximum);
  let value;const text=new TextDecoder('utf-8',{fatal:true}).decode(bytes);
  try{value=JSON.parse(text);}catch(_){value=null;}
  return {status:response.status,headers:response.headers,value,bytes:bytes.length,
    protectedBodyShape:/"(?:patientId|sourcePatientId|patientKey|sourceXPath|manifestDigest|optionsDigest|rulesetDigest|targetDemographicNo)"\s*:/.test(text),
    privatePathOrSecurityShape:/\/workspace\/|\/var\/lib\/|\/tmp\/|"(?:jdbcPassword|db_password|mfaSecret|security_no)"\s*:/.test(text),
    sha256:createHash('sha256').update(bytes).digest('hex')};
}
function live(result,status,code) {
  h.assert(result.status===status && result.headers.get(API_VERSION_HEADER)===String(API_VERSION)
    && result.value?.apiVersion===API_VERSION && (code?result.value.code===code:result.value.ok===true),
  'The actual live API response does not prove its exact finite contract');
}
function oldRefusal(result) {
  h.assert(result.status===400 && result.value?.ok===false && result.value?.code==='IMPORT_PARAMETERS_INVALID'
    && result.headers.get(API_VERSION_HEADER)===null,'The real old WAR did not reach its strict unknown-parameter refusal');
}
function noDownload(result) {
  h.assert(!result.headers.has('content-disposition'),'A refused session received a protected attachment');
  h.assert(!result.protectedBodyShape&&!result.privatePathOrSecurityShape,'A refused request disclosed protected report/path/security fields');
  h.assert(result.headers.has('x-permitted-cross-domain-policies'),'The refusal was not independently identified as an application response');
  if([301,302,303,307,308].includes(result.status)) {
    h.assert(/(?:login|logout|session|index\.jsp)/i.test(result.headers.get('location')||''),'Unrecognized anonymous redirect');
  } else h.assert([401,403].includes(result.status),'Anonymous/stale session did not refuse protected content');
}
function safeResult(result) {return {status:result.status,code:result.value?.code||null,bytes:result.bytes,sha256:result.sha256};}

// Expected negative fetch diagnostics stay in the evidence. Only the exact verified
// request is removed from this page's local strict recorder; unrelated problems fail.
function qualifyContractRefusal(recorder,expected,code) {
  h.assert(expected.request && ((expected.status===409&&code==='IMPORT_CLIENT_REFRESH_REQUIRED')
    ||(expected.status===400&&code==='IMPORT_PARAMETERS_INVALID')),'Only exact finite contract refusals can be qualified');
  const consoleRefusal = expected.status===409
    ? /^Failed to load resource: the server responded with a status of 409\b/
    : /^Failed to load resource: the server responded with a status of 400\b/;
  const removed=[];
  let consoles=0,bad=0;
  for(const key of ['badResponses','consoleIssues','requestFailures']) {
    const rows=recorder[key];
    for(let index=rows.length-1;index>=0;index--) {
      const row=rows[index];const url=row.url||row.location?.url;
      const same=url===expected.url;
      const identity=row.requestRef?.deref()===expected.request;
      const qualified=key==='badResponses'?same&&identity&&row.status===expected.status
        :key==='consoleIssues'?same&&row.type==='error'
          &&consoleRefusal.test(row.text)
          :same&&identity&&row.errorText==='net::ERR_ABORTED'&&expected.status===400&&code==='IMPORT_PARAMETERS_INVALID';
      if(qualified){if(key==='consoleIssues')h.assert(++consoles<=1,'Ambiguous same-route console failures cannot be qualified');
        if(key==='badResponses')h.assert(++bad===1,'Ambiguous same-request responses cannot be qualified');
        removed.push({kind:key,...rows.splice(index,1)[0]});}
    }
  }
  h.assert(bad===1,'The verified negative response was not recorded with exact request identity');
  h.assertStrictPage(recorder);
  return removed;
}
async function cachedPage(browser,config,bundle,expectedVersion,status,code) {
  const context=await h.newContext(browser,config);const loginRecorder=h.createRecorder();
  await h.login(context,config,loginRecorder);
  h.assertStrictPage(loginRecorder);
  const page=await context.newPage();const recorder=h.createRecorder();h.wireStrictPage(page,'cached-client-contract',recorder);
  // EventEmitter calls the strict listener above synchronously before this listener.
  // Add identity only to the exact entry that listener just created for this event.
  page.on('response',response=>{
    if(![400,409].includes(response.status()))return;
    const row=recorder.badResponses.at(-1);
    if(row&&row.url===response.url()&&row.status===response.status()&&!Object.hasOwn(row,'requestRef')) {
      Object.defineProperty(row,'requestRef',{enumerable:false,value:new WeakRef(response.request())});
    }
  });
  let installed=0;
  await page.route('**/js/demographic/cds-import.js',async route=>{installed++;await route.fulfill({status:200,contentType:'application/javascript',body:bundle});});
  const pending=page.waitForResponse(response=>new URL(response.url()).pathname.endsWith('/form/cdsImportStatus'),{timeout:30000});
  const form=await page.goto(h.appUrl(config.baseUrl,'/form/importUpload'));
  h.assert(form.status()===200,'The real opposite-version server did not render its import page');
  await page.locator('#cdsImport').waitFor();
  const response=await pending;
  await page.locator('#cdsNotice.alert-danger').waitFor();
  h.assert((await page.locator('#cdsNotice').innerText()).includes('IMPORT_CLIENT_REFRESH_REQUIRED'),'Cached client did not visibly require refresh');
  h.assert(installed===1 && await page.evaluate(()=>globalThis.CarlosCdsImport.API_VERSION??null)===expectedVersion,
    'The page did not execute the exact intended cached WAR bundle');
  const url=new URL(response.url());
  h.assert(url.searchParams.get('apiVersion')===(expectedVersion===null?null:String(expectedVersion)),'Cached client sent an unexpected wire contract');
  const token=await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
  const observed=await request(context,url.href,{token});
  if(status===409)live(observed,409,code);else oldRefusal(observed);
  await page.waitForLoadState('networkidle',{timeout:10000});
  const diagnostics=qualifyContractRefusal(recorder,{url:response.url(),status,request:response.request()},code);
  return {context,page,recorder,token,observed,diagnostics};
}

async function workflow(s) {
  h.assert(process.env.CDS_COMPATIBILITY_DISPOSABLE==='true' && process.env.CDS_COMPATIBILITY_EXCLUSIVE==='true',
    'Explicit disposable exclusive installed compatibility authorization is required');
  requireDocumentStore(process.env.DOCUMENT_DIR);
  const selectors=loadPrivate(process.env.CDS_COMPATIBILITY_SELECTORS);
  const run=process.env.CDS_COMPATIBILITY_REPORT_RUN;
  h.assert(UUID.test(run||'') && selectors.old?.mysql?.database==='cds_compatibility_old_test',
    'An owned completed run and the explicitly isolated archived-WAR schema are required');
  const evidence=process.env.CDS_COMPATIBILITY_EVIDENCE;
  h.assert(evidence&&path.isAbsolute(evidence)&&!fs.existsSync(evidence),'Compatibility evidence must be absent/private');
  fs.mkdirSync(evidence,{mode:0o700});
  const proof={protocol:1,status:'RUNNING',candidateWarSha256:selectors.current.bundle.warSha256,
    archivedWarSha256:selectors.old.bundle.warSha256,steps:[]};
  const save=()=>privateJson(path.join(evidence,'proof.json'),proof);
  save();
  const oldSql=createArchivedSqlRunner(selectors.old.mysql);
  try {
    h.assert(s.sql.value("SELECT COUNT(*) FROM cds_import_patient WHERE held=1")==='0'
      && s.sql.value("SELECT maintenance_active FROM cds_import_admission WHERE id='clinical-maintenance'")==='0',
    'Ordinary multipart writes require a released maintenance window and no retained holds');
    const multipart=[];
    const captureMultipart=response=>{
      const incoming=response.request();
      if(incoming.method()==='POST'&&/\/addEditDocument(?:\.do)?$/.test(new URL(response.url()).pathname)) {
        multipart.push({status:response.status(),method:incoming.method(),contentType:incoming.headers()['content-type']||''});
      }
    };
    s.context.on('response',captureMultipart);
    try {await ordinaryDocumentUpload(s);} finally {s.context.off('response',captureMultipart);}
    h.assert(multipart.length===1&&/^multipart\/form-data;\s*boundary=/i.test(multipart[0].contentType)
      &&multipart[0].status>=200&&multipart[0].status<400,'The real unrelated multipart upload request was not observed');
    const files=s.sql.rows(`SELECT d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${s.patient} AND d.docdesc=${h.sqlString(s.marker+' opener upload')}`);
    h.assert(files.length===1&&path.basename(files[0][0])===files[0][0],'The ordinary upload did not identify one owned native file');
    const stored=path.join(process.env.DOCUMENT_DIR,files[0][0]);
    const expectedPdf=textPdf(s.marker);
    h.assert(fs.statSync(stored).size===expectedPdf.length&&hashFile(stored)===createHash('sha256').update(expectedPdf).digest('hex'),
      'The ordinary multipart native PDF bytes differ from the actual selected source');
    proof.steps.push({gate:'W04',result:'PASSED',multipart,pdfBytes:expectedPdf.length,pdfSha256:hashFile(stored),
      evidence:'actual ordinary-document multipart UI/native/file/opener workflow'});save();
    const baseline=inventory(s.sql);const trees=treeProof(selectors.current.roots);
    const owner='web-'+s.sql.value(`SELECT security_no FROM security WHERE user_name=${h.sqlString(s.config.testUser)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM cds_import_run WHERE id=${h.sqlString(run)} AND operator=${h.sqlString(owner)} AND report_state='DELIVERED'`)==='1',
      'Report authorization requires an existing delivered run owned by the actual login');
    const {frame:ownerFrame}=await openImportFrame(s);
    const ownerToken=await ownerFrame.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const fixtures=authzReadFixture({sql:s.sql,marker:s.marker,provider:s.provider,testUser:s.config.testUser});
    s.cleanup(()=>fixtures.cleanup());
    const role=fixtures.addRole({_cds_import:'w',_demographic:'w'});const login=fixtures.addLogin(role);
    fixtures.joinTestLoginPrograms(login);
    s.sql.execute(`INSERT INTO provider_facility(provider_no,facility_id) SELECT ${h.sqlString(login.providerNo)},facility_id FROM provider_facility WHERE provider_no=${h.sqlString(s.provider)}`);
    const other=await signIn(s,login);
    const otherPage=await other.context.newPage();await otherPage.goto(h.appUrl(s.config.baseUrl,'/form/importUpload'));
    const otherToken=await otherPage.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    live(await request(other.context,h.appUrl(s.config.baseUrl,'/form/cdsImportStatus'),{params:{apiVersion:API_VERSION,section:'uploads'},token:otherToken}),200);
    const browser=s.context.browser();
    const anonymous=await h.newContext(browser,s.config);
    let expired;
    const authorization=[];
    try {
      for(const type of ['event','error','receipt']) {
        const url=h.appUrl(s.config.baseUrl,'/form/cdsImportReport')+'?'+new URLSearchParams({runId:run,type});
        const served=await request(s.context,url,{token:ownerToken,maximum:2*1024*1024});
        h.assert(served.status===200&&served.headers.has('content-disposition'),'The owner could not download the real protected report');
        const foreign=await request(other.context,url,{token:otherToken});live(foreign,403,'IMPORT_ACCESS_DENIED');noDownload(foreign);
        const guest=await request(anonymous,url);noDownload(guest);
        authorization.push({type,owner:safeResult(served),nonowner:safeResult(foreign),anonymous:safeResult(guest)});
      }
      const legacy=await request(s.context,h.appUrl(s.config.baseUrl,'/form/importLogDownload')+'?filename=synthetic-expired.log',{token:ownerToken});
      live(legacy,410,'IMPORT_LEGACY_LOG_LINK_EXPIRED');
      const staleState=await s.context.storageState();
      const logout=[];
      const captureLogout=response=>{if(response.request().method()==='POST'
        &&new URL(response.url()).pathname.endsWith('/logout'))logout.push(response.status());};
      s.context.on('response',captureLogout);
      try {await s.context.close();} finally {s.context.off('response',captureLogout);}
      h.assert(logout.length===1&&logout[0]<400,'Native logout-on-close did not invalidate the original owner session');
      expired=await h.newContext(browser,s.config,{storageState:staleState});
      for(const entry of authorization) {
        const url=h.appUrl(s.config.baseUrl,'/form/cdsImportReport')+'?'+new URLSearchParams({runId:run,type:entry.type});
        const loggedOut=await request(expired,url,{token:ownerToken});noDownload(loggedOut);
        entry.loggedOut=safeResult(loggedOut);
      }
      proof.steps.push({gate:'W06',result:'PASSED',authorization,legacy:safeResult(legacy)});save();
    } finally {await other.context.close();await anonymous.close();if(expired){await expired.clearCookies();await expired.close();}}
    const oldBaseline=inventory(oldSql);const oldTrees=treeProof(selectors.old.roots);
    h.assert(oldSql.value('SELECT DATABASE()')==='cds_compatibility_old_test'
      && oldSql.value('SELECT (SELECT COUNT(*) FROM cds_import_run)+(SELECT COUNT(*) FROM cds_import_upload)+(SELECT COUNT(*) FROM cds_import_checkpoint)')==='0',
    'Archived compatibility server must have no accepted jobs, uploads or checkpoints');
    const oldBundle=loadBundle(selectors.old.bundle),newBundle=loadBundle(selectors.current.bundle);
    const oldOnNew=await cachedPage(browser,s.config,oldBundle,null,409,'IMPORT_CLIENT_REFRESH_REQUIRED');
    try {
      const post=oldOnNew.page.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname.endsWith('/form/cdsUpload'));
      const selected=await oldOnNew.page.locator('#cdsSource option').evaluateAll(options=>options.find(option=>option.value)?.value);
      h.assert(selected,'The actual server supplied no approved source for its stale-client refusal probe');
      await oldOnNew.page.locator('#cdsSource').selectOption(selected);
      await oldOnNew.page.locator('#importFile').setInputFiles({name:'synthetic-contract.xml',mimeType:'application/xml',buffer:Buffer.from('<refused-before-source-reading/>')});
      await oldOnNew.page.locator('#cdsNotice').evaluate(node=>{node.hidden=true;node.textContent='';});
      await oldOnNew.page.locator('#cdsStart').click();const response=await post;
      h.assert(response.status()===409,'Actual cached old client POST was not refused');
      await oldOnNew.page.locator('#cdsNotice.alert-danger').waitFor();
      await oldOnNew.page.waitForLoadState('networkidle',{timeout:10000});
      h.assert((await oldOnNew.page.locator('#cdsNotice').innerText()).includes('IMPORT_CLIENT_REFRESH_REQUIRED'),
        'Cached old client did not visibly report its POST contract refusal');
      proof.oldOnNew={initial:safeResult(oldOnNew.observed),postStatus:response.status(),
        diagnostics:[...oldOnNew.diagnostics,...qualifyContractRefusal(oldOnNew.recorder,{url:response.url(),status:409,request:response.request()},'IMPORT_CLIENT_REFRESH_REQUIRED')]};save();
    } finally {await oldOnNew.context.close();}
    const oldConfig={...s.config,...selectors.old.login,baseUrl:h.validateBaseUrl(selectors.old.baseUrl),mysql:selectors.old.mysql};
    // Each browser's native logout wrapper is bound to its actual server URL.
    const oldBrowser=await h.launchBrowser(oldConfig);
    let newOnOld;
    try {
      newOnOld=await cachedPage(oldBrowser,oldConfig,newBundle,API_VERSION,400,'IMPORT_PARAMETERS_INVALID');
      const refused=await request(newOnOld.context,h.appUrl(oldConfig.baseUrl,'/form/cdsUpload'),{method:'POST',token:newOnOld.token,
        params:{apiVersion:API_VERSION,operation:'begin',fileName:'synthetic-contract.xml',expectedBytes:1}});
      oldRefusal(refused);
      proof.newOnOld={initial:safeResult(newOnOld.observed),post:safeResult(refused),diagnostics:newOnOld.diagnostics};save();
    } finally {if(newOnOld)await newOnOld.context.close();await oldBrowser.close();}
    const currentAfter=inventory(s.sql),currentTreesAfter=treeProof(selectors.current.roots);
    const oldAfter=inventory(oldSql),oldTreesAfter=treeProof(selectors.old.roots);
    proof.inventories={counterOrder:['runs','reviewVersionSum','ownerEpochSum','patients','held','units','checkpoints','events',
      'uploads','uploadVersionSum','receivedBytesSum','chunksSum','reservedBytesSum','nativePatients','nativeDrugs',
      'nativeAllergies','nativePreventions','nativeDocuments','nativeNotes'],
    current:{before:baseline,after:currentAfter,treesBefore:trees,treesAfter:currentTreesAfter},
    old:{before:oldBaseline,after:oldAfter,treesBefore:oldTrees,treesAfter:oldTreesAfter}};save();
    h.assert(currentAfter===baseline&&JSON.stringify(currentTreesAfter)===JSON.stringify(trees),
      'Current-server compatibility probes changed quota/import/native counters or protected state');
    h.assert(oldAfter===oldBaseline&&JSON.stringify(oldTreesAfter)===JSON.stringify(oldTrees),
      'Archived-server compatibility probes changed quota/import/native counters or protected state');
    proof.steps.push({gate:'W05',result:'PASSED',scope:'genuine opposite-WAR cached bundles on real markup/backends; explicit POST refusals'});
    proof.status='PASSED';save();
  } catch(error) {proof.status='FAILED';proof.failureClass=error.name;save();throw error;}
  finally {oldSql.dispose();}
}
if(require.main===module)runWorkflow('cds-import-compatibility',workflow,{openPatient:true});
module.exports={workflow,loadBundle,treeProof,live,oldRefusal,noDownload,qualifyContractRefusal,createArchivedSqlRunner,requireDocumentStore};
