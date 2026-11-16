#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// #5730: native uploader contracts, independent bytes/metadata/link/routing.
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process');
const {PNG}=require('pngjs');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const {openSecondSession,failureMark,consumeExpectedFailure}=require('./lib/concurrency-support');
const {masterRecordLink}=require('./lib/master-record-link');
const {openInbox}=require('./lab-forwarding-rules-playwright-checks');
const {openSidebar,setMode,search}=require('./inbox-file-forward-playwright-checks');
const {authenticatedProbe}=require('./lib/authenticated-probe');
const {clickLabelOutput}=require('./lib/label-file-output');
const pdf=require('./lib/stored-pdf-documents');
const {captureUploadFiles,removeUploadFiles}=require('./lib/document-upload-files');
const {markDocumentResidue,removeDocumentResidue}=require('./lib/document-residue');
const q=h.sqlString,hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');

/** Insert legal whitespace before xref, preserving every object offset and EOF. */
function paddedPdf(base,target) {
  const match=/startxref\s+(\d+)\s+%%EOF/.exec(base.toString('latin1'));
  h.assert(match&&target>base.length,'PDF boundary fixture needs an intact classic xref and larger target');
  const offset=Number(match[1]);let padding=target-base.length;
  for(let attempt=0;attempt<4;attempt++) {
    const tail=Buffer.from(base.subarray(offset).toString('latin1').replace(/startxref\s+\d+/,'startxref\n'+(offset+padding)),'latin1');
    const total=offset+padding+tail.length;
    if(total===target)return Buffer.concat([base.subarray(0,offset),Buffer.alloc(padding,32),tail]);
    padding+=target-total;
  }
  throw new Error('Could not produce exact-size valid PDF');
}

/** Preserve prior findings when a later profile prerequisite is unavailable. */
function unavailableWithFindings(defects,message) {
  h.assert(defects.length===0,'Document matrix failures before missing prerequisite ('+message+'): '+defects.join('; '));
  throw new h.SkipCheck(message);
}

/** Test acceptance as opaque storage separately from claims of valid PDF/image content. */
async function workflow(s) {
  const {sql,patient,marker}=s;
  const root=process.env.CARLOS_INSTALLED_WEBAPP_DIR||'/usr/share/carlos-emr/webapp/carlos';
  const xml=fs.readFileSync(path.join(root,'WEB-INF/classes/struts.xml'),'utf8');
  const limits=[...xml.matchAll(/<constant\s+name="struts\.multipart\.maxSize"\s+value="(\d+)"\s*\/>/g)];
  h.assert(limits.length===1,'Installed multipart size limit is ambiguous');
  const limit=Number(limits[0][1]);h.assert(limit>1024*1024&&limit<=64*1024*1024,'Configured upload boundary is outside this bounded fixture budget');
  const store=pdf.directory('DOCUMENT_DIR','DOCUMENT_DIR','RX_FAX_DOCUMENT_DIR');
  const beforeFiles=captureUploadFiles(store),residue=markDocumentResidue(sql);
  const scratch=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-document-matrix-'));
  s.cleanup(()=>fs.rmSync(scratch,{recursive:true,force:true}));
  const actor=throwawayLoginFixture({sql,marker,provider:s.provider,testUser:s.config.testUser});
  s.cleanup(()=>actor.cleanup());actor.create({roleNames:['doctor']});
  let documentsClean=true;
  const actorCleanup=actor.cleanup.bind(actor);actor.cleanup=()=>{h.assert(documentsClean,'Upload rows/files remain; retain their provider');actorCleanup();};
  s.cleanup(()=>{
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(actor.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(actor.providerNo)}`)==='0','Upload provider default program remains');
  });
  sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id)
    SELECT program_id,${q(actor.providerNo)},role_id,team_id FROM program_provider WHERE provider_no=${q(s.provider)};
    INSERT INTO provider_default_program(provider_no,program_id,signnote)
    SELECT ${q(actor.providerNo)},program_id,signnote FROM provider_default_program WHERE provider_no=${q(s.provider)}`);
  const routingRecipients=new Set([actor.providerNo]);
  const recipientSql=()=>[...routingRecipients].map(q).join(',');
  const documents=()=>sql.rows(`SELECT document_no,docfilename FROM document WHERE docfilename LIKE ${q('%'+marker+'%')} ORDER BY document_no`);
  const captured=new Set();
  s.cleanup(()=>{
    const rows=documents(),ids=[...new Set([...captured,...rows.map(row=>row[0])])];
    h.assert(ids.every(id=>/^[1-9]\d*$/.test(id)),'Invalid owned upload identity');
    const files=new Set(rows.map(row=>row[1]));
    for(const name of fs.readdirSync(store))if(name.includes(marker)&&!beforeFiles.files.has(name))files.add(name);
    if(ids.length) {
      const list=ids.join(',');
      h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${list}) AND NOT(module='demographic' AND module_id IN (${patient},'-1'))`)==='0','Upload acquired a foreign module/patient link');
      h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_no IN (${list}) AND NOT(lab_type='DOC' AND provider_no IN (${recipientSql()}))`)==='0','Upload acquired an unowned provider/lab route');
      h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}) AND demographic_no NOT IN (${patient},-1)`)==='0','Upload acquired another patient route');
      removeDocumentResidue(sql,residue,ids);
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}) AND provider_no IN (${recipientSql()});
        DELETE FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}) AND demographic_no IN (${patient},-1);
        DELETE FROM ctl_document WHERE document_no IN (${list}) AND module='demographic' AND module_id IN (${patient},'-1');
        DELETE FROM document WHERE document_no IN (${list}) AND docfilename LIKE ${q('%'+marker+'%')}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM document WHERE document_no IN (${list}))+(SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${list}))+(SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}))+(SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}))`)==='0','Owned upload rows or routing remain');
    }
    removeUploadFiles(beforeFiles,[...files]);
    h.assert(!fs.readdirSync(store).some(name=>name.includes(marker)),'Owned or partial stored upload remains');
    fs.rmSync(scratch,{recursive:true,force:true});
    documentsClean=true;
  });
  const [control]=pdf.ownedPdfDocuments(store,marker,[{key:'CONTROL',pages:1}]);
  documentsClean=false;
  pdf.seedOwnedPdfDocuments({sql,store,patient,provider:actor.providerNo,docs:[control]});captured.add(control.id);
  const controlState=()=>JSON.stringify({document:sql.rows(`SELECT * FROM document WHERE document_no=${control.id}`),links:sql.rows(`SELECT * FROM ctl_document WHERE document_no=${control.id}`),
    routes:sql.rows(`SELECT * FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${control.id}`),hash:hash(fs.readFileSync(control.file))});
  const controlBefore=controlState();
  const base=pdf.textPdf(marker+' upload matrix',1);
  fs.writeFileSync(path.join(scratch,'source.pdf'),base,{flag:'wx',mode:0o600});
  execFileSync('pdftoppm',['-f','1','-singlefile','-jpeg','-scale-to','256',path.join(scratch,'source.pdf'),path.join(scratch,'image')],{timeout:30000,stdio:'pipe'});
  const png=new PNG({width:64,height:48});
  for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++){
    const i=(y*png.width+x)*4;png.data[i]=(x*7+y*3)%256;png.data[i+1]=(x*3+y*11)%256;png.data[i+2]=(x+y)%2?20:230;png.data[i+3]=255;
  }
  const cases=[
    {key:'pdf',extension:'pdf',mime:'application/pdf',bytes:base,pages:1},
    {key:'png',extension:'png',mime:'image/png',bytes:PNG.sync.write(png),pages:0},
    {key:'jpg',extension:'jpg',mime:'image/jpeg',bytes:fs.readFileSync(path.join(scratch,'image.jpg')),pages:0},
    {key:'text',extension:'txt',mime:'text/plain',bytes:Buffer.from(marker+' O\'Neil é & text\n'),pages:0},
    {key:'rtf',extension:'rtf',mime:'application/rtf',bytes:Buffer.from('{\\rtf1\\ansi '+marker+' synthetic document}'),pages:0},
    {key:'corrupt-pdf',extension:'pdf',mime:'application/pdf',bytes:Buffer.from('%PDF-1.4\n'+marker+' truncated without objects/xref\n'),pages:0,opaque:true},
    {key:'invalid-image',extension:'png',mime:'image/png',bytes:Buffer.from(marker+' is opaque text, not PNG bytes'),pages:0,opaque:true},
    {key:'pdf-as-image',extension:'png',mime:'image/png',bytes:base,pages:0,opaque:true},
    {key:'pdf-mime',extension:'PDF',mime:'application/octet-stream',bytes:base,pages:1,storedMime:'application/pdf'},
  ];
  const session=await openSecondSession(s,{label:'document-matrix-provider',config:{...s.config,testUser:actor.username}});
  let report=await ui.clickOpensPopup(session.master,masterRecordLink(session.master,'/documentManager/ViewDocumentReport').first(),{context:session.context,recorder:s.recorder,label:'matrix-edoc'});
  const navigationPages=new Set(session.context.pages());
  async function closeOutputs(){
    // Attachment downloads can leave the browser's named output window blank.
    // Retire consumed output windows before the next native display click.
    for(const page of session.context.pages())if(page!==report&&!navigationPages.has(page)&&!page.isClosed())await page.close();
  }
  const defects=[];let sequence=0;
  const record=(okay,message)=>{if(!okay){defects.push(message);console.log('  FAIL document matrix: '+message);}};
  const unavailable=message=>unavailableWithFindings(defects,message);
  function input(item){return {name:marker+'-'+String(++sequence).padStart(3,'0')+'-'+item.key+'.'+item.extension,mimeType:item.mime,buffer:item.bytes};}
  async function selectFile(form,payload) {
    if(payload.buffer.length<1024*1024)return form.locator('input[type="file"]').setInputFiles(payload);
    // The browser and check share this filesystem. Let the native file control
    // read the exact large fixture path instead of base64-copying 50 MiB through
    // the browser protocol; the request still comes from the normal form.
    const file=path.join(scratch,payload.name);
    fs.writeFileSync(file,payload.buffer,{flag:'wx',mode:0o600});
    await form.locator('input[type="file"]').setInputFiles(file,{timeout:120000});
  }
  async function verifyStored(payload,item,owner,expectedPatient) {
    const rows=sql.rows(`SELECT document_no,docfilename,doccreator,responsible,contenttype,number_of_pages,status FROM document WHERE docfilename LIKE ${q('%'+payload.name)}`);
    h.assert(rows.length===1,'Upload did not create exactly one owned document');
    const [id,name,creator,responsible,mime,pages,status]=rows[0];captured.add(id);
    h.assert(/^[1-9]\d*$/.test(id)&&name===path.basename(name)&&name.includes(marker),'Unsafe stored upload identity');
    record(hash(fs.readFileSync(path.join(store,name)))===hash(item.bytes),item.key+': stored bytes differ');
    const expectedResponsible=expectedPatient==='-1'?owner:'';
    record(creator===owner&&responsible===expectedResponsible&&status==='A',item.key+': creator/responsible/status differ '+JSON.stringify({creator,responsible,status,expectedCreator:owner,expectedResponsible}));
    record(mime===(item.storedMime||item.mime)&&Number(pages)===item.pages,item.key+': metadata differs '+JSON.stringify({mime,pages,expectedMime:item.storedMime||item.mime,expectedPages:item.pages}));
    const links=sql.rows(`SELECT module,module_id,status FROM ctl_document WHERE document_no=${id}`);
    record(JSON.stringify(links)===JSON.stringify([['demographic',String(expectedPatient),'A']]),item.key+': wrong patient/module link');
    record(controlState()===controlBefore,item.key+': unrelated control document changed');
    return {id,name};
  }
  async function openAdd(){
    const panel=report.locator('#addDocDiv');if(!await panel.isVisible())await report.locator('button[data-bs-target="#addDocDiv"]').click();
    await panel.waitFor({state:'visible'});return panel.locator('form');
  }
  let chosenType;
  async function edoc(item,{description=marker+' matrix upload'}={}) {
    const payload=input(item),form=await openAdd();
    h.assert(await form.locator('[name="functionid"]').inputValue()===patient,'eDoc upload form names another patient');
    const types=await form.locator('select[name="docType"] option').evaluateAll(options=>options.map(option=>option.value).filter(Boolean));
    h.assert(types.length,'eDoc upload offers no document types');chosenType=chosenType||types[0];
    await form.locator('select[name="docType"]').selectOption(chosenType);await form.locator('#docDesc').fill(description);
    await selectFile(form,payload);
    const [response]=await Promise.all([report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/documentManager/addEditDocument'),{timeout:120000}),
      ui.clickAndAwaitReload(report,form.locator('input[name="Submit"]'),{timeout:120000})]);
    h.assert(response.status()<400,'Accepted eDoc file was refused with HTTP '+response.status());
    const stored=await verifyStored(payload,item,actor.providerNo,patient);
    record(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${stored.id}`)==='0',item.key+': eDoc upload unexpectedly routed to a provider');
    // Only PDFs have docNo checkboxes (they select files for PDF combining).
    // Every supported format has its own native display link instead.
    const opener=report.locator('a[onclick*="ManageDocument"][onclick*="doc_no='+stored.id+'"]');
    await opener.waitFor({state:'visible'});
    h.assert(await opener.count()===1,'Stored upload has an ambiguous native display/download link');
    await closeOutputs();
    // A format the browser cannot render (RTF) is handed to its download path by the popup itself, whose
    // own address then stays empty; the completed-output capture reads the address from that download.
    const delivered=await clickLabelOutput(report,opener,{context:session.context,recorder:s.recorder,label:'matrix-document-output',timeout:30000});
    try{
      h.assert(delivered.url&&new URL(delivered.url).origin===new URL(s.config.baseUrl).origin&&new URL(delivered.url).searchParams.get('doc_no')===stored.id,'Native document output changed application or document identity');
      const read=await authenticatedProbe({...s,context:session.context},delivered.url);
      try{record(read.status()===200&&hash(await read.body())===hash(item.bytes),item.key+': native document link returned different bytes');}finally{await read.dispose();}
    }finally{await delivered.dispose();}
    await closeOutputs();
    h.assertStrictPage(s.recorder);
    console.log('  Observed eDoc '+item.key+': '+(item.opaque?'opaque storage; no content-validity claim':'bytes/metadata/native output checked'));
    const headers=await response.request().allHeaders();return {stored,length:Number(headers['content-length']),fileBytes:item.bytes.length,payload};
  }
  for(const item of cases)await edoc(item);
  // Multipart rejection must be an application message, never a passing raw 500.
  const beforeEmpty=documents().length,empty={key:'empty',extension:'pdf',mime:'application/pdf',bytes:Buffer.alloc(0)};
  let form=await openAdd();await form.locator('select[name="docType"]').selectOption(chosenType);await form.locator('#docDesc').fill(marker+' retained empty draft');
  await form.locator('input[type="file"]').setInputFiles(input(empty));
  await ui.clickAndAwaitReload(report,form.locator('input[name="Submit"]'));
  const feedback=await report.locator('[role="alert"]').allInnerTexts();
  record(feedback.some(text=>/empty files not accepted|zero(?:-|\s*)size|0 bytes/i.test(text)),'Empty upload lacks application validation feedback in its alert');
  record(documents().length===beforeEmpty,'Empty upload created a document');
  const back=report.getByRole('button',{name:'Back',exact:true});if(await back.count())await ui.clickAndAwaitReload(report,back);
  form=await openAdd();
  record(await form.locator('#docDesc').inputValue()===marker+' retained empty draft'&&await form.locator('select[name="docType"]').inputValue()===chosenType,'Native correction after empty refusal lost draft metadata');
  await edoc({...cases[0],key:'retry-after-empty'});
  await report.close();
  const inbox=await openInbox(session.schedule,s.recorder,'matrix-inbox');
  const uploader=await ui.clickOpensPopup(inbox,inbox.locator('a[href*="ViewHtml5AddDocuments" i],a[href*="ViewDocumentUploader" i]').first(),{context:session.context,recorder:s.recorder,label:'matrix-inbox-upload'});
  if(await uploader.locator('#destinationDrop').count()!==1)unavailable('Format matrix needs the actual modern Inbox PDF uploader; legacy entry is inventoried separately');
  await uploader.locator('#destinationDrop').selectOption('pendingDocs');
  await uploader.waitForFunction(()=>!window.jQuery||window.jQuery.active===0);
  // Use a native offered recipient with no active forwarding rules, so an
  // existing clinic forwarding chain cannot become an unowned cleanup target.
  const offered=await uploader.locator('#providerDrop option').evaluateAll(options=>options.map(option=>option.value));
  const routingProvider=offered.find(value=>/^[1-9]\d*$/.test(value)&&sql.value(`SELECT COUNT(*) FROM incomingLabRules WHERE provider_no=${q(value)} AND archive='0'`)==='0');
  if(!routingProvider)unavailable('Document matrix needs an offered provider without active forwarding rules');
  routingRecipients.add(routingProvider);
  await uploader.locator('#providerDrop').selectOption(routingProvider);
  // A representative image is refused by the actual native file control before
  // any request. This is client policy evidence, not server content validation.
  let posted=0;const countPost=request=>{if(request.method()==='POST'&&new URL(request.url()).pathname.endsWith('/documentManager/documentUpload'))posted++;};
  uploader.on('request',countPost);
  await uploader.locator('input[type="file"]').setInputFiles(input(cases[1]));
  record(await uploader.locator('#fileList tr').count()===0&&await uploader.locator('#msg').isVisible()&&posted===0,'Inbox did not truthfully refuse a non-PDF without dispatch');
  uploader.off('request',countPost);
  const inboxDocuments=[];
  for(const [key,extension,mime,bytes,pages] of [
    ['inbox-pdf','pdf','application/pdf',base,1],['inbox-upper','PDF','application/octet-stream',base,1],
    ['inbox-mixed','pDf','application/pdf',base,1],['inbox-corrupt','pdf','application/pdf',cases[5].bytes,0],
    ['inbox-mismatch','pdf','image/png',cases[1].bytes,0],
  ]) {
    h.assert(sql.value(`SELECT COUNT(*) FROM incomingLabRules WHERE provider_no=${q(routingProvider)} AND archive='0'`)==='0','Upload recipient forwarding configuration changed');
    const item={key,extension,mime,bytes,pages,storedMime:'application/pdf',opaque:['inbox-corrupt','inbox-mismatch'].includes(key)},payload=input(item);
    await uploader.locator('#btnReset').click();await uploader.locator('input[type="file"]').setInputFiles(payload);
    const [response]=await Promise.all([uploader.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/documentManager/documentUpload')),
      uploader.locator('#btnUpload').click()]);
    const body=await response.json();h.assert(response.status()===200&&Array.isArray(body)&&body.length===1&&!body[0].error,'Inbox upload failed its JSON success contract');
    await uploader.waitForFunction(()=>!window.jQuery||window.jQuery.active===0);
    const stored=await verifyStored(payload,item,actor.providerNo,'-1');
    const routes=sql.rows(`SELECT provider_no,lab_type,status FROM providerLabRouting WHERE lab_no=${stored.id}`);
    inboxDocuments.push({stored,item});
    record(routes.length===1&&routes[0][0]===routingProvider&&routes[0][1]==='DOC'&&routes[0][2]==='N',key+': upload success omitted or misdirected provider routing');
  }
  await uploader.close();
  report=await ui.clickOpensPopup(session.master,masterRecordLink(session.master,'/documentManager/ViewDocumentReport').first(),{context:session.context,recorder:s.recorder,label:'matrix-edoc-boundary'});
  const description=marker+' boundary';
  const calibration=await edoc({...cases[0],key:'limit'},{description});
  const overhead=calibration.length-calibration.fileBytes;
  h.assert(Number.isSafeInteger(overhead)&&overhead>0&&overhead<65536,'Native multipart Content-Length cannot establish the configured boundary');
  for(const difference of [-1,0]) {
    const bytes=paddedPdf(base,limit-overhead+difference);
    const file=path.join(scratch,'boundary'+difference+'.pdf');fs.writeFileSync(file,bytes,{mode:0o600,flag:'wx'});
    h.assert(/Pages:\s+1\b/.test(execFileSync('pdfinfo',[file],{encoding:'utf8',timeout:30000})),'Boundary PDF is not independently readable as one page');
    const accepted=await edoc({...cases[0],key:'limit',bytes},{description});
    record(accepted.length===limit+difference,'Positive size boundary was not reached exactly: '+accepted.length+' versus '+(limit+difference));
    fs.unlinkSync(file);
  }
  const oversized={...cases[0],key:'limit',bytes:paddedPdf(base,limit-overhead+1)},payload=input(oversized);
  form=await openAdd();await form.locator('select[name="docType"]').selectOption(chosenType);await form.locator('#docDesc').fill(description);
  await selectFile(form,payload);
  const countBefore=documents().length,mark=failureMark(s.recorder);
  const [refused]=await Promise.all([report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/documentManager/addEditDocument'),{timeout:120000}),
    ui.clickAndAwaitReload(report,form.locator('input[name="Submit"]'),{timeout:120000})]);
  const actualLength=Number((await refused.request().allHeaders())['content-length']);
  h.assert(actualLength===limit+1,'Oversize multipart boundary was not reached exactly');
  h.assert(refused.status()===413&&/too large|entity|limit/i.test(await report.locator('body').innerText()),'Oversize upload did not truthfully report its transport-envelope refusal');
  consumeExpectedFailure(s.recorder,mark,{status:413,method:'POST',path:/\/documentManager\/addEditDocument$/});
  record(documents().length===countBefore&&!fs.readdirSync(store).some(name=>name.endsWith(payload.name)),'Refused oversize upload left a document or partial stored file');
  record(controlState()===controlBefore,'Oversize refusal changed the unrelated control');
  // The shared packaged front door also caps the entire request at this size.
  // A 413 here proves that envelope, not application-level content validation.
  console.log('  Exact native multipart boundaries: '+(limit-1)+' and '+limit+' accepted; '+(limit+1)+' refused by the transport envelope.');
  await report.goBack({waitUntil:'load'});
  form=await openAdd();
  record(await form.locator('#docDesc').inputValue()===description&&await form.locator('select[name="docType"]').inputValue()===chosenType,'Native Back lost the oversize draft metadata');
  await edoc({...cases[0],key:'boundary-retry'},{description});
  await report.close();
  // Read the actual newly routed Inbox entries after the size checks, so a
  // malformed document's preview cannot hide the boundary outcomes.
  const readInbox=await openInbox(s.schedule,s.recorder,'matrix-output-inbox');
  await setMode(readInbox,'list');await openSidebar(readInbox);
  await readInbox.locator('#anyProvider').check();await readInbox.locator('#unmatchedPatients').check();await readInbox.locator('#statusAll').check();
  await search(readInbox);
  for(const {stored,item} of inboxDocuments) {
    const row=readInbox.locator(`tr[data-lab-type="DOC"][data-segment-id="${stored.id}"]`);
    await row.waitFor({state:'visible',timeout:30000});
    const mark=failureMark(s.recorder),previews=[];
    const observe=response=>{
      const url=new URL(response.url());
      if(url.origin===new URL(s.config.baseUrl).origin&&url.pathname.endsWith('/documentManager/ManageDocument')
          &&url.searchParams.get('method')==='viewDocPage'&&url.searchParams.get('doc_no')===stored.id&&url.searchParams.get('curPage')==='1')previews.push(response);
    };
    s.context.on('response',observe);
    try {
      const viewer=await ui.clickOpensPopup(readInbox,row.locator('a[onclick*="reportWindow"]').first(),{context:s.context,recorder:s.recorder,label:'matrix-inbox-output',timeout:30000});
      h.assert(new URL(viewer.url()).searchParams.get('segmentID')===stored.id,'Inbox opened a different uploaded document');
      const image=viewer.locator(`#docImg_${stored.id}`);
      h.assert(await image.count()===1,'Document matrix needs the native image-preview profile');
      await viewer.waitForFunction(({id,state})=>document.getElementById('docImg_'+id)?.getAttribute('data-document-image-state')===state,
        {id:stored.id,state:item.opaque?'failed':'loaded'},{timeout:30000});
      h.assert(previews.length===1&&previews[0].request().method()==='GET','Preview request identity/count changed');
      if(item.opaque) {
        h.assert(previews[0].status()===500&&await viewer.locator('.document-image-status[role="status"]').isVisible()
          &&/could not be loaded/i.test(await viewer.locator('.document-image-status[role="status"]').innerText()),'Malformed PDF did not show its truthful render failure');
        // This is an asserted failure to render deliberately malformed opaque
        // bytes, with a visible native fallback and the original still available.
        // It is neither an upload rejection nor content-validation evidence.
        consumeExpectedFailure(s.recorder,mark,{status:500,method:'GET',path:/\/documentManager\/ManageDocument$/});
      } else {
        h.assert(previews[0].status()===200&&await image.evaluate(node=>node.complete&&node.naturalWidth>0&&node.naturalHeight>0),'Valid Inbox PDF preview did not decode');
      }
      const delivered=await ui.clickDownloadsOrOpens(viewer,viewer.locator(`#printBtn_${stored.id}`),{context:s.context,recorder:s.recorder,label:'matrix-inbox-download',timeout:30000});
      h.assert(delivered.url&&new URL(delivered.url).origin===new URL(s.config.baseUrl).origin&&new URL(delivered.url).searchParams.get('doc_no')===stored.id,'Inbox download changed application/document identity');
      const read=await authenticatedProbe(s,delivered.url);
      try{record(read.status()===200&&hash(await read.body())===hash(item.bytes),item.key+': actual Inbox output bytes differ');}finally{await read.dispose();}
      if(delivered.page&&!delivered.page.isClosed())await delivered.page.close();await viewer.close();
    } finally {s.context.off('response',observe);}
    record(controlState()===controlBefore,item.key+': Inbox read changed the unrelated control');
    console.log('  Observed Inbox '+item.key+': native reopen/download and exact stored bytes checked');
  }
  await readInbox.close();
  const previewErrors=s.recorder.badResponses.filter(entry=>entry.label==='matrix-inbox-output'&&new URL(entry.url).pathname.endsWith('/documentManager/ManageDocument'));
  record(previewErrors.length===0,'Inbox document preview returned HTTP errors: '+previewErrors.map(entry=>entry.status).join(','));
  h.assert(defects.length===0,'Document matrix failures: '+defects.join('; '));
}
if(require.main===module)runWorkflow('document-format-matrix',workflow,{preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Document routing/actor fixtures require EXCLUSIVE=1');
},preflight:()=>pdf.requirePoppler('pdftoppm','pdfinfo')});
module.exports={workflow,paddedPdf,unavailableWithFindings};
