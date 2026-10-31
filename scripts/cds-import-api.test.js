/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {importApi}=require('./demographic-cds-import-playwright-checks');
function fixture({version='2',body={ok:true,apiVersion:2},status=200,asFrame=false,text}={}) {
 const calls=[];let disposed=0;
 const page={async evaluate(_fn,{route}){return{url:'http://localhost/carlos/form/'+route,token:'synthetic-native-token'};},context(){return{request:{async fetch(url,options){calls.push({url,options});return{
  async text(){return text ?? JSON.stringify(body);},headers(){return{'content-type':'application/json','x-carlos-cds-api-version':version};},status(){return status;},async dispose(){disposed++;}
 };}}};}};
 const frame=asFrame?{evaluate:page.evaluate,page:()=>page}:page;
 return{frame,calls,disposed:()=>disposed};
}
test('shared API helper sends exact current version and native CSRF from either a page or frame',async()=>{
 for(const asFrame of [false,true]){
  const f=fixture({asFrame});const result=await importApi(f.frame,'cdsImportStatus',{runId:'owned-run',apiVersion:1});
  const u=new URL(f.calls[0].url);assert.deepEqual(u.searchParams.getAll('apiVersion'),['2']);
  assert.equal(f.calls[0].options.headers['CSRF-TOKEN'],'synthetic-native-token');assert.equal(f.calls[0].options.maxRedirects,0);
  assert.equal(result.apiVersion,'2');assert.equal(f.disposed(),1);
 }
});
test('shared mutator helper sends version in form and disposes independent response bytes',async()=>{
 const f=fixture();await importApi(f.frame,'cdsUpload',{operation:'begin',fileName:'synthetic.xml',expectedBytes:1},'POST');
 assert.equal(new URL(f.calls[0].url).search,'');assert.equal(f.calls[0].options.form.apiVersion,'2');
 assert.equal(f.calls[0].options.form.expectedBytes,'1');assert.equal(f.calls[0].options.method,'POST');assert.equal(f.disposed(),1);
});
test('successful live receipts with a missing or mismatched header or body version require refresh',async()=>{
 for(const options of [{version:undefined},{version:'1'},{body:{ok:true}},{body:{ok:true,apiVersion:1}},{body:{ok:true,apiVersion:'2'}}]){
  const f=fixture({...options,version:Object.hasOwn(options,'version')?options.version:'2'});
  // Explicit undefined in the fixture represents a headerless response.
  if(Object.hasOwn(options,'version')&&options.version===undefined){
   const original=f.frame.context;f.frame.context=()=>{const context=original();const fetch=context.request.fetch;context.request.fetch=async(...args)=>{const r=await fetch(...args);r.headers=()=>({'content-type':'application/json'});return r;};return context;};
  }
  await assert.rejects(importApi(f.frame,'cdsImportStatus'),/requires a client refresh/);assert.equal(f.disposed(),1);
 }
});
test('immutable JSON receipt and all other report types remain versionless downloads',async()=>{
 for(const type of ['receipt','event','error','units']){
  const f=fixture({version:'1',body:{cleanComplete:true,sourceDigest:'a'.repeat(64)}});
  const result=await importApi(f.frame,'cdsImportReport',{runId:'owned-run',type});
  assert.equal(new URL(f.calls[0].url).searchParams.has('apiVersion'),false);assert.equal(result.body.cleanComplete,true);assert.equal(f.disposed(),1);
 }
});
test('intentional refusal remains inspectable and oversize metadata still fails with disposal',async()=>{
 const refused=fixture({version:'1',body:{ok:false,code:'IMPORT_CLIENT_REFRESH_REQUIRED'},status:409});
 assert.equal((await importApi(refused.frame,'cdsImportControl',{},'POST')).status,409);assert.equal(refused.disposed(),1);
 const large=fixture({text:'x'.repeat(2*1024*1024+1)});await assert.rejects(importApi(large.frame,'cdsImportStatus'),/bounded receipt budget/);assert.equal(large.disposed(),1);
});
