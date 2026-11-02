/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const h=require('./playwright-harness');
const pdf=require('./stored-pdf-documents');
const {retireOwnedFile}=require('./owned-file-retirement');
const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
function createOwnedAnnotationFixture(config) {
  h.assert(process.env.CARLOS_DISPOSABLE_HOST==='true','Annotation fixture requires a disposable install');
  const sql=h.createSqlRunner(config.mysql),marker='FAKE-PW'+crypto.randomBytes(8).toString('hex');
  let store;
  try {store=pdf.directory('DOCUMENT_DIR','DOCUMENT_DIR','RX_FAX_DOCUMENT_DIR');}
  catch(error){sql.dispose();throw error;}
  const cache=path.join(path.dirname(store),'document_cache'),records=new Map(),pending=[],known=new WeakSet(),errors=[];
  let receiptDir;
  try {receiptDir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-annotation-fixture-'));}
  catch(error){sql.dispose();throw error;}
  let patient,provider,source,closed=true;
  const persist=()=>fs.writeFileSync(path.join(receiptDir,'recovery.json'),JSON.stringify({marker,patient,provider,source:source?.id,records:[...records]},null,2),{mode:0o600});
  persist();
  const validId=value=>/^[1-9]\d*$/.test(String(value));
  const owner=()=>h.assert(patient&&sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`)==='1','Annotation patient ownership changed; retain fixtures');
  function capture(id,isSource=false,ancestors=new Set()) {
    h.assert(validId(id),'Annotation response returned no positive document ID');id=String(id);owner();
    h.assert(!ancestors.has(id),'Annotation source graph has a cycle');ancestors.add(id);
    const rows=sql.rows(`SELECT * FROM document WHERE document_no=${id}`);
    const details=sql.rows(`SELECT docdesc,docfilename,doccreator,source FROM document WHERE document_no=${id}`);
    h.assert(rows.length===1&&details.length===1,'Annotation response document is missing');
    const [description,filename,creator,origin]=details[0];
    h.assert(description.startsWith(marker)&&creator===provider,'Annotation document ownership changed');
    if(!isSource) {
      const parent=/^annotated-copy-of:([1-9]\d*)$/.exec(origin)?.[1];
      h.assert(parent,'Annotation source identity is missing');
      if(!records.has(parent))capture(parent,parent===source.id,ancestors);
      h.assert(description===records.get(parent).description+' (annotated)','Annotation description no longer matches its owned source');
    }
    h.assert(filename===path.basename(filename)&&(/^[A-Za-z0-9_.-]+$/).test(filename),'Annotation storage filename is not plain');
    const links=sql.rows(`SELECT module,module_id FROM ctl_document WHERE document_no=${id}`);
    h.assert(links.length===1&&links[0][0]==='demographic'&&links[0][1]===patient,'Annotation document has an unexpected patient link');
    const file=path.join(store,filename);h.assert(fs.lstatSync(file).isFile(),'Annotation PDF is not a regular file');
    const entry={id,filename,description,rowHash:hash(JSON.stringify(rows)),fileHash:pdf.sha(file)};
    if(records.has(id))h.assert(JSON.stringify(records.get(id))===JSON.stringify(entry),'An observed annotation document changed');
    else {records.set(id,entry);persist();}
  }
  async function observeResponse(response) {
    const status=response.status();
    if(status===503) {
      const refusal=await response.json();
      if(refusal.success===false&&refusal.retryable===true)return;
    }
    if(status>=500)throw new Error('Annotation server outcome requires reconciliation; retain fixtures');
    if(status!==200)return;
    const body=await response.json();if(!body.success)return;
    h.assert(String(body.demographicNo)===patient,'Annotation response changed patient context');capture(body.documentNo);
  }
  return {
    get documentId(){return source.id;},
    create(){
      provider=sql.value(`SELECT provider_no FROM security WHERE user_name=${h.sqlString(config.testUser)}`);h.assert(validId(provider),'Annotation operator is missing');
      patient=sql.value(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
        VALUES(${h.sqlString(marker)},'Annotation','1980','01','02','F','AC',${h.sqlString(provider)},'ON','ON','NR',NOW());SELECT LAST_INSERT_ID()`);
      h.assert(validId(patient),'Annotation patient was not created');persist();
      [source]=pdf.ownedPdfDocuments(store,marker,[{key:'annotation-source',pages:12}]);
      pdf.seedOwnedPdfDocuments({sql,store,patient,provider,docs:[source]});capture(source.id,true);
    },
    bind(context){
      closed=false;
      context.on('request',request=>{
        const url=new URL(request.url());
        if(url.origin!==config.baseUrl.origin)return;
        const write=request.method()==='POST'&&url.pathname.endsWith('/documentManager/SaveAnnotatedDocument');
        const render=url.pathname.endsWith('/documentManager/ManageDocument')&&url.searchParams.get('method')==='showPage';
        if(!write&&!render)return;
        const operation=(async()=>{
          const response=await request.response();
          if(!response){if(write&&!known.has(request))throw new Error('Annotation request has no confirmed outcome; retain fixtures');return;}
          const failure=await response.finished();
          if(failure){if(write&&!known.has(request))throw new Error('Annotation response did not finish; retain fixtures');return;}
          if(write&&!known.has(request))await observeResponse(response);
        })().catch(error=>errors.push(error));
        pending.push(operation);
      });
    },
    async fetch(route){
      try {const response=await route.fetch();await observeResponse(response);known.add(route.request());return response;}
      catch(error){errors.push(error);try{await route.abort('failed');}catch{}throw error;}
    },
    abort(route,reason){known.add(route.request());return route.abort(reason);},
    async drain(){
      let count,timer;
      try {
        const timeout=new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('Annotation work did not settle; retain fixtures')),180000);});
        await Promise.race([(async()=>{do {count=pending.length;await Promise.all(pending);}while(pending.length!==count);})(),timeout]);
      } catch(error){errors.push(error);throw error;}finally{clearTimeout(timer);}
      h.assert(!errors.length,errors.map(error=>error.message).join('; '));
    },
    confirmClosed(){closed=true;},
    cleanup(){
      try {
        h.assert(closed&&!errors.length,'Annotation work is unconfirmed; retain the recovery receipt and fixtures');
        if(!patient) {
          h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${h.sqlString(marker)}`)==='0','Annotation patient creation is unconfirmed; retain receipt');
          fs.rmSync(receiptDir,{recursive:true,force:true});return;
        }
        owner();
        const ids=[...records.keys()];
        const actual=sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${h.sqlString(marker+'%')} ORDER BY document_no`).map(([id])=>id);
        h.assert(JSON.stringify(actual.slice().sort())===JSON.stringify(ids.slice().sort()),'Annotation has an unobserved document; retain fixtures');
        for(const [id] of records)capture(id,id===source.id);
        const files=[];
        for(const entry of records.values()) {
          files.push({file:path.join(store,entry.filename),sha:entry.fileHash});
          if(fs.existsSync(cache))for(const name of fs.readdirSync(cache))if(name.startsWith(entry.filename+'_')&&/^\d+\.png$/.test(name.slice(entry.filename.length+1))) {
            const file=path.join(cache,name);h.assert(fs.lstatSync(file).isFile(),'Annotation cache entry changed type');files.push({file,sha:pdf.sha(file)});
          }
        }
        for(const item of files)h.assert(fs.lstatSync(item.file).isFile()&&pdf.sha(item.file)===item.sha,'Annotation file changed before cleanup');
        if(ids.length)pdf.removeOwnedPdfDocuments({sql,marker,patient,docs:[...records.values()],files:[]});
        for(const item of files)retireOwnedFile(item.file,bytes=>hash(bytes)===item.sha);
        h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${patient}`)==='0','Annotation links remain');
        sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
        h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`)==='0','Annotation patient remains');
        fs.rmSync(receiptDir,{recursive:true,force:true});
      } finally {sql.dispose();}
    },
  };
}
module.exports={createOwnedAnnotationFixture};
