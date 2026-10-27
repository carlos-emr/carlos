/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test'),assert=require('node:assert/strict');
const {createCdsMetadataProver,verifyMetadataPage}=require('./lib/cds-metadata-domain-proof');
const id='12345678-1234-4234-8234-123456789abc',owner='web-77';
function fixture({dry=true,mode=dry?'1':'0',summaryChanges={},checkpoints=0,advanced=0,reportChanges={},visibleId=id}={}) {
  const job={id,state:dry?'DRY_RUN_COMPLETE':'COMPLETE',sourceDigest:'a'.repeat(64),optionsDigest:'b'.repeat(64),rulesetDigest:'c'.repeat(64),manifestDigest:'d'.repeat(64)};
  const summary={dryRun:dry,cleanComplete:true,clinicalComplete:!dry,reportsDelivered:true,cleanupComplete:true,resumeRequested:false,expectedPatients:1,...summaryChanges};
  const frame={locator(selector){return{async getAttribute(){assert.equal(selector,'#cdsImport');return owner;},async textContent(){assert.equal(selector,'#runSummary');return JSON.stringify({job,summary});}async inputValue(){assert.equal(selector,'#runId');return visibleId;},async count(){assert.equal(selector,'#jobOverview');return 0;}};}};
  const sql={value(query){
    if(query.includes('SELECT security_no'))return'77';
    if(query.startsWith('SELECT operator FROM'))return owner;
    if(query.startsWith('SELECT dry_run'))return mode;
    if(query.includes('cds_import_checkpoint WHERE first_run_id'))return String(checkpoints);
    if(query.includes('cds_import_unit WHERE'))return String(advanced);
    if(query.includes('LEFT JOIN demographic'))return'0';
    if(query.includes('cds_import_unit u LEFT JOIN'))return'0';
    if(query.includes('COUNT(*) FROM cds_import_patient')&&!query.includes('AND ('))return'1';
    if(query.includes('COUNT(*) FROM cds_import_patient'))return'0';
    throw Error('Unexpected evidence query');
  }};
  const readApi=async(_frame,route)=>route==='cdsImportStatus'?{status:200,body:{ok:true,job,summary}}
    :{status:200,body:{...summary,runId:job.id,sourceDigest:job.sourceDigest,optionsDigest:job.optionsDigest,manifestDigest:job.manifestDigest,rulesetDigest:job.rulesetDigest,...reportChanges}};
  return{prover:createCdsMetadataProver({frame,sql,config:{testUser:'synthetic'},readApi}),receipt:{owner,runId:id,cleanComplete:true,sourceDigest:job.sourceDigest,optionsDigest:job.optionsDigest,rulesetDigest:job.rulesetDigest}};
}
test('clean dry run proves zero native writes and protected reports without claiming clinical completion',async()=>{
 const{prover,receipt}=fixture();const proof=await prover(receipt);assert.equal(proof.nativeVerified,true);assert.equal(proof.reportsVerified,true);
});
test('dry run mode must match durable attempt mode and truthfully report clinicalComplete false',async()=>{
 for(const options of [{mode:'0'},{mode:'unknown'},{summaryChanges:{dryRun:undefined}},{summaryChanges:{clinicalComplete:true}}]){
  const{prover,receipt}=fixture(options);await assert.rejects(prover(receipt),/completion mode differs/);
 }
});
test('dry run refuses advanced clinical units, checkpoints or conflicting protected report mode',async()=>{
 for(const options of [{checkpoints:1},{advanced:1},{reportChanges:{dryRun:false}},{reportChanges:{clinicalComplete:true}}]){
  const{prover,receipt}=fixture(options);await assert.rejects(prover(receipt),/Preview|Protected completion receipt differs/);
 }
});
test('execution completion requires independently committed units, native patient accounting and matching reports',async()=>{
 const{prover,receipt}=fixture({dry:false});const proof=await prover(receipt);assert.equal(proof.nativeVerified,true);assert.equal(proof.reportsVerified,true);
 const bad=fixture({dry:false,reportChanges:{rulesetDigest:'e'.repeat(64)}});await assert.rejects(bad.prover(bad.receipt),/Protected completion receipt differs/);
 const empty=fixture({dry:false,summaryChanges:{expectedPatients:0}});await assert.rejects(empty.prover(empty.receipt),/no proven source patients/);
 for(const runId of [undefined,'22222222-2222-4222-8222-222222222222']){
  const wrong=fixture({dry:false,reportChanges:{runId}});await assert.rejects(wrong.prover(wrong.receipt),/Protected completion receipt differs/);
 }
});

test('metadata pages bind exact cursor and immutable diagnostic identities while permitting resolution',async()=>{
 const page={section:'diagnostics',afterKey:'',afterId:0,limit:50,rows:[{id:1,code:'SOURCE_WARNING',blocking:true,resolved:false}],nextCursor:1};
 const frame={locator(){return{async evaluateAll(){return[['1','SOURCE_WARNING / PREFLIGHT','protected detail','RESOLVED']];}};}};
 const read=async()=>({status:200,body:{ok:true,rows:[{...page.rows[0],resolved:true}],nextCursor:1}});
 await verifyMetadataPage({runId:id,page},frame,read);
 await assert.rejects(verifyMetadataPage({runId:id,page},frame,async()=>({status:200,body:{ok:true,rows:[{...page.rows[0],id:2}],nextCursor:2}})),/page identities differ/);
 await assert.rejects(verifyMetadataPage({runId:id,page},frame,async()=>({status:200,body:{ok:true,rows:[page.rows[0]],nextCursor:2}})),/cursor differs/);
});
test('chunk pages require exact journal hashes and represented verified prefix without payload rereads',async()=>{
 const row={index:0,offset:0,bytes:65536,sha256:'a'.repeat(64)};
 const page={section:'chunks',afterKey:'',afterId:0,limit:50,rows:[row],nextCursor:null};
 const frame={locator(){return{async evaluate(){return 65536;}};}};
 await verifyMetadataPage({uploadId:id,page},frame,async()=>({status:200,body:{ok:true,rows:[row]}}));
 await assert.rejects(verifyMetadataPage({uploadId:id,page},frame,async()=>({status:200,body:{ok:true,rows:[{...row,sha256:'b'.repeat(64)}]}})),/Immutable bounded-page content changed/);
});


test('closed advanced diagnostics cannot qualify another visible job handle',async()=>{
 const{prover,receipt}=fixture({visibleId:'22222222-2222-4222-8222-222222222222'});
 await assert.rejects(prover(receipt),/visible job handle differs/);
});
