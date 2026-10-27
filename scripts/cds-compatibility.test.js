/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const {createHash}=require('node:crypto');
const {execFileSync}=require('node:child_process');
const gate=require('./demographic-cds-compatibility-playwright-checks');
const hash=value=>createHash('sha256').update(value).digest('hex');

test('cached-source provenance binds the actual archive and member; a supplied WAR label cannot replace proof',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'cds-compat-bundle-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const file=path.join(root,'client.js'),warFile=path.join(root,'unit.war');const source=Buffer.from('/* unit archive integrity only */');
  fs.writeFileSync(file,source);
  execFileSync('python3',['-c','import pathlib,sys,zipfile;z=zipfile.ZipFile(sys.argv[1],"x");z.writestr("js/demographic/cds-import.js",pathlib.Path(sys.argv[2]).read_bytes());z.close()',warFile,file],{stdio:['ignore','pipe','pipe'],timeout:10000});
  const entry={file,warFile,sha256:hash(source),warSha256:hash(fs.readFileSync(warFile))};
  assert.deepEqual(gate.loadBundle(entry),source);
  assert.throws(()=>gate.loadBundle({...entry,warSha256:'0'.repeat(64)}));
  fs.writeFileSync(file,'different bytes');assert.throws(()=>gate.loadBundle(entry));
});

test('state proof detects metadata changes and never follows a redirected child into another tree',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'cds-compat-tree-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const own=path.join(root,'owned'),other=path.join(root,'other');fs.mkdirSync(own);fs.mkdirSync(other);
  fs.writeFileSync(path.join(other,'not-owned'),'unchanged');fs.writeFileSync(path.join(own,'one'),'source');
  fs.symlinkSync(other,path.join(own,'redirect'));
  const before=gate.treeProof([own]);assert.equal(before[0].entries,2);
  assert.deepEqual(gate.treeProof([own]),before);
  fs.utimesSync(path.join(own,'one'),new Date(0),new Date(1000));
  assert.notDeepEqual(gate.treeProof([own]),before);
  assert.throws(()=>gate.treeProof([path.join(own,'redirect')]));
});

test('old-server proof requires its exact unknown-parameter code, not a disabled feature or arbitrary error',()=>{
  const good={status:400,headers:new Headers(),value:{ok:false,code:'IMPORT_PARAMETERS_INVALID'}};
  gate.oldRefusal(good);
  for(const result of [{...good,status:503},{...good,value:{ok:false,code:'IMPORT_DISABLED'}},
    {...good,status:200},{...good,headers:new Headers({'X-CARLOS-CDS-API-Version':'2'})}]) {
    assert.throws(()=>gate.oldRefusal(result));
  }
});

test('a served attachment or a generic error cannot count as anonymous report refusal',()=>{
  gate.noDownload({status:401,headers:new Headers({'x-permitted-cross-domain-policies':'none'})});
  gate.noDownload({status:302,headers:new Headers({location:'/carlos/login','x-permitted-cross-domain-policies':'none'})});
  for(const result of [{status:200,headers:new Headers()}, {status:500,headers:new Headers()},
    {status:403,headers:new Headers({'content-disposition':'attachment'})},
    {status:302,headers:new Headers({location:'/other'})},
    {status:403,headers:new Headers({'x-permitted-cross-domain-policies':'none'}),protectedBodyShape:true},
    {status:401,headers:new Headers()}])assert.throws(()=>gate.noDownload(result));
});

function recorded(request,url,status) {
  const entry={url,status};Object.defineProperty(entry,'requestRef',{value:new WeakRef(request)});return entry;
}
function recorder(badResponses=[]) {return {badResponses,consoleIssues:[],requestFailures:[],pageErrors:[],unexpectedDialogs:[]};}
test('expected negative evidence excludes only its actual request; competing same-URL failures stay fatal',()=>{
  const request={},other={},url='http://127.0.0.1:8080/carlos/form/cdsImportStatus';
  const expected={request,url,status:409};
  const good=recorder([recorded(request,url,409)]);
  assert.equal(gate.qualifyContractRefusal(good,expected,'IMPORT_CLIENT_REFRESH_REQUIRED').length,1);
  const competing=recorder([recorded(request,url,409),recorded(other,url,409)]);
  assert.throws(()=>gate.qualifyContractRefusal(competing,expected,'IMPORT_CLIENT_REFRESH_REQUIRED'));
  assert.equal(competing.badResponses.length,1);
  assert.throws(()=>gate.qualifyContractRefusal(recorder([recorded(request,url,409)]),expected,'OTHER_ERROR'));
});


test('archived database uses its explicit TCP port while preserving the protected option-file position',()=>{
  const seen=[];
  const runner=gate.createArchivedSqlRunner({host:'127.0.0.1',port:13306,user:'synthetic',password:'private-unit',database:'cds_compatibility_old_test'},
    (command,args)=>{seen.push({command,args});return 'cds_compatibility_old_test\n';});
  try {
    assert.equal(runner.value('SELECT DATABASE()'),'cds_compatibility_old_test');
    assert.equal(seen.length,1);assert.equal(seen[0].command,'mysql');
    assert.match(seen[0].args[0],/^--defaults-extra-file=/);
    assert.equal(seen[0].args[1],'--port=13306');
    assert.ok(!seen[0].args.some(value=>value.includes('private-unit')));
  } finally {runner.dispose();}
  for(const port of [undefined,0,65536,'13306',13306.5])assert.throws(()=>gate.createArchivedSqlRunner({port}));
});
