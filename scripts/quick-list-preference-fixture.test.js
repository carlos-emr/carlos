/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
 'use strict';
const fs=require('node:fs');const path=require('node:path');const os=require('node:os');
const vm=require('node:vm');const assert=require('node:assert/strict');const {test}=require('node:test');
const {spawnSync}=require('node:child_process');
const {createQuickListPreferenceFixture,createNativeQuickListPreferenceFixture}=require('./lib/quick-list-preference-fixture');
const {cleanupOwnedWorkflow}=require('./lib/workflow-session');
const PYTHON_SQLITE=[
  "import sqlite3,json,hashlib,re,sys",
  "request=json.load(sys.stdin)",
  "db=sqlite3.connect(request['database'])",
  "db.execute('PRAGMA foreign_keys=ON')",
  "db.create_function('SHA2',2,lambda value,size:hashlib.sha256(value.encode()).hexdigest() if size==256 else None)",
  "query,args=request['query'],request.get('args',[])",
  "try:",
  " if 'FROM information_schema.COLUMNS' in query:",
  "  rows=[[r[1]] for r in db.execute('PRAGMA table_info(\"quickListUser\")')]",
  " elif 'FROM information_schema.TRIGGERS' in query:",
  "  rows=[[db.execute(\"SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND tbl_name='quickListUser'\").fetchone()[0]]]",
  " elif 'FROM information_schema.REFERENTIAL_CONSTRAINTS' in query:",
  "  rows=[[sum(1 for table, in db.execute(\"SELECT name FROM sqlite_master WHERE type='table'\") for fk in db.execute('PRAGMA foreign_key_list(\"'+table+'\")') if table=='quickListUser' or fk[2]=='quickListUser')]]",
  " elif request.get('script'):",
  "  db.executescript(query);rows=[]",
  " else:",
  "  cursor=db.execute(query,args);rows=cursor.fetchall() if cursor.description else []",
  " db.commit();print(json.dumps([[None if value is None else str(value) for value in row] for row in rows]))",
  "finally:db.close()",
].join('\n');
const marker='FAKE-PW0123456789abcdef';const names=['FAKEOWN001','F1234567"&'];
function database(t,rows=[]) {
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'dx-preferences-test-'));fs.chmodSync(dir,0o700);
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));const file=path.join(dir,'synthetic.sqlite');const calls=[];
 const sql={rows(query,args=[],script=false){calls.push({query,args});const result=spawnSync('/usr/bin/python3',['-c',PYTHON_SQLITE],{input:JSON.stringify({database:file,query,args,script}),encoding:'utf8',timeout:10000,maxBuffer:1048576});assert.equal(result.status,0,'Synthetic SQL adapter refused: '+result.stderr);return JSON.parse(result.stdout);},
 value(query,args){const rows=this.rows(query,args);return rows.length?rows[0][0]:'';},execute(query,args=[]){return this.rows(query,args,query.includes(';'));},dispose(){}};
 sql.rows(`CREATE TABLE quickListUser(id INTEGER PRIMARY KEY AUTOINCREMENT,providerNo TEXT NOT NULL,quickListName TEXT COLLATE NOCASE NOT NULL,lastUsed DATETIME);
 CREATE TABLE quickList(id INTEGER PRIMARY KEY AUTOINCREMENT,quickListName TEXT COLLATE NOCASE,createdByProvider TEXT,dxResearchCode TEXT,codingSystem TEXT);
 CREATE TABLE dxresearch(demographic_no INTEGER);CREATE TABLE dx_associations(dx_codetype TEXT,dx_code TEXT,codetype TEXT,code TEXT);
 INSERT INTO quickList(quickListName,createdByProvider,dxResearchCode,codingSystem)VALUES('List1','2','250','icd9');`,[],true);
 for(const row of rows)sql.execute('INSERT INTO quickListUser(id,providerNo,quickListName,lastUsed)VALUES(?,?,?,?)',row);
 const before=all(sql);const cleanups=[];const events=[];
 const browser={connected:true,isConnected(){return this.connected;},async close(){events.push('close');this.connected=false;}};
 const session={sql,provider:'999998',marker,context:{browser:()=>browser},cleanup(fn){cleanups.push(fn);}};
 const finish=()=>cleanupOwnedWorkflow({browser,sql,cleanups});
 return{sql,dir,calls,before,session,browser,cleanups,events,finish};
}
function all(sql){return sql.rows('SELECT JSON_ARRAY(id,providerNo,quickListName,lastUsed)FROM quickListUser ORDER BY id').map(row=>row[0]);}
function nativePreference(sql,name,provider='999998') {
 const found=sql.value('SELECT COUNT(*) FROM quickListUser WHERE providerNo=? AND quickListName=?',[provider,name]);
 if(found!=='0')sql.execute('UPDATE quickListUser SET lastUsed=CURRENT_TIMESTAMP WHERE providerNo=? AND quickListName=?',[provider,name]);
 else sql.execute('INSERT INTO quickListUser(providerNo,quickListName,lastUsed)VALUES(?,?,CURRENT_TIMESTAMP)',[provider,name]);
}
function reserve(db){return createQuickListPreferenceFixture(db.session,names,{ledgerDirectory:db.dir});}

test('before zero preferences, native owned/default inserts are all removed without resetting sequence',async t=>{
 const db=database(t);const handle=reserve(db);for(const name of [...names,'List1'])nativePreference(db.sql,name);
 const sequence=db.sql.value("SELECT seq FROM sqlite_sequence WHERE name='quickListUser'");await db.finish();
 assert.deepEqual(all(db.sql),db.before);assert.equal(db.sql.value("SELECT seq FROM sqlite_sequence WHERE name='quickListUser'"),sequence);
 assert.equal(JSON.parse(fs.readFileSync(handle.ledgerPath)).status,'RESTORED');assert.equal(fs.statSync(handle.ledgerPath).mode&0o777,0o600);
});
test('preserves existing operator/default identity and foreign provider while exactly restoring known-list timestamp',async t=>{
 const db=database(t,[[1,'999998','default','2024-04-02 22:33:34'],[2,'999998','List1','2020-01-01 00:00:00'],[3,'2','List1','2021-01-01 01:02:03']]);
 reserve(db);nativePreference(db.sql,'List1');nativePreference(db.sql,names[1]);await db.finish();assert.deepEqual(all(db.sql),db.before);
});
test('NULL lastUsed stays NULL after a legitimate native update',async t=>{
 const db=database(t,[[1,'999998','List1',null]]);reserve(db);nativePreference(db.sql,'List1');await db.finish();assert.deepEqual(all(db.sql),db.before);
});
for(const drift of ['foreign timestamp','renamed original','missing original','replaced owner','unknown new name','new foreign provider','new NULL timestamp','old timestamp','future timestamp','duplicate name']) {
 test(`${drift} refuses all preference DML and preserves evidence`,async t=>{
  const db=database(t,[[1,'999998','List1','2020-01-01 00:00:00'],[2,'2','List1','2021-01-01 00:00:00']]);reserve(db);nativePreference(db.sql,names[0]);
  const statements={
   'foreign timestamp':"UPDATE quickListUser SET lastUsed=CURRENT_TIMESTAMP WHERE id=2",
   'renamed original':"UPDATE quickListUser SET quickListName='Foreign' WHERE id=1",
   'missing original':"DELETE FROM quickListUser WHERE id=1",
   'replaced owner':"UPDATE quickListUser SET providerNo='3' WHERE id=1",
   'unknown new name':"INSERT INTO quickListUser(providerNo,quickListName,lastUsed)VALUES('999998','Foreign',CURRENT_TIMESTAMP)",
   'new foreign provider':"INSERT INTO quickListUser(providerNo,quickListName,lastUsed)VALUES('3','List1',CURRENT_TIMESTAMP)",
   'new NULL timestamp':"INSERT INTO quickListUser(providerNo,quickListName,lastUsed)VALUES('999998','FAKEOWN001',NULL)",
   'old timestamp':"INSERT INTO quickListUser(providerNo,quickListName,lastUsed)VALUES('999998','FAKEOWN001','2000-01-01 00:00:00')",
   'future timestamp':"INSERT INTO quickListUser(providerNo,quickListName,lastUsed)VALUES('999998','F1234567\"&','2099-01-01 00:00:00')",
   'duplicate name':"INSERT INTO quickListUser(providerNo,quickListName,lastUsed)VALUES('999998','List1',CURRENT_TIMESTAMP)",
  };db.sql.execute(statements[drift]);const before=all(db.sql);db.calls.length=0;
  await assert.rejects(db.finish(),/preserving/);assert(!db.calls.some(call=>/^(DELETE|UPDATE)/.test(call.query)));assert.deepEqual(all(db.sql),before);
 });
}
for(const table of ['quickList','quickListUser'])test(`generated-name collision in ${table} is never adopted and no destructive cleanup is registered`,t=>{
 const db=database(t);if(table==='quickList')db.sql.execute('INSERT INTO quickList(quickListName,createdByProvider)VALUES(?,?)',[names[0].toLowerCase(),'2']);
 else db.sql.execute('INSERT INTO quickListUser(providerNo,quickListName,lastUsed)VALUES(?,?,NULL)',['2',names[0].toLowerCase()]);
 const before=all(db.sql);db.calls.length=0;assert.throws(()=>reserve(db),/already exists/);assert.equal(db.cleanups.length,0);
 assert(!db.calls.some(call=>/^(DELETE|UPDATE)/.test(call.query)));assert.deepEqual(all(db.sql),before);
});
test('unconfirmed browser close refuses restore before preference mutations',async t=>{
 const db=database(t);reserve(db);nativePreference(db.sql,'List1');db.browser.close=async()=>{throw Error('synthetic close refusal');};db.calls.length=0;
 await assert.rejects(db.finish(),/browser close is unconfirmed/);assert(!db.calls.some(call=>/^(DELETE|UPDATE)/.test(call.query)));
});
for(const schemaChange of ['column','trigger','foreign key'])test(`unreviewed ${schemaChange} refuses before cleanup registration`,t=>{
 const db=database(t);if(schemaChange==='column')db.sql.execute('ALTER TABLE quickListUser ADD COLUMN foreign_value TEXT');
 if(schemaChange==='trigger')db.sql.execute('CREATE TRIGGER preference_trigger AFTER INSERT ON quickListUser BEGIN SELECT 1; END');
 if(schemaChange==='foreign key')db.sql.execute('CREATE TABLE linked(preference_id INTEGER REFERENCES quickListUser(id))');
 assert.throws(()=>reserve(db),/schema|triggers|foreign-key/);assert.equal(db.cleanups.length,0);
});
test('changed schema after native UI leaves every preference intact',async t=>{
 const db=database(t);reserve(db);nativePreference(db.sql,'List1');db.sql.execute('ALTER TABLE quickListUser ADD COLUMN foreign_value TEXT');db.calls.length=0;
 await assert.rejects(db.finish(),/schema/);assert(!db.calls.some(call=>/^(DELETE|UPDATE)/.test(call.query)));
});

function workflowWith(ui,events) {
 const filename=process.env.DX_PREFERENCE_CALLER||path.join(__dirname,'dx-registry-quicklist-playwright-checks.js');const module={exports:{}};
 const h={assert:(ok,message)=>assert(ok,message),sqlString:value=>"'"+String(value).replaceAll("'","''")+"'"};
 const request=name=>{
  if(name==='./lib/playwright-harness')return h;
  if(name==='./lib/playwright-ui')return {clickOpensPopupOrNavigates:ui};
  if(name==='./lib/playwright-link-audit'||name==='./lib/workflow-session'||name==='./lib/billing-site-context')return{};
  if(name==='./lib/quick-list-preference-fixture')return {createQuickListPreferenceFixture(session,ownedNames){events.push('preference');return createQuickListPreferenceFixture(session,ownedNames,{ledgerDirectory:session.ledgerDirectory});}};
  if(name==='./lib/owned-billing-physician')return {createOwnedBillingPhysician(session){events.push('physician');session.cleanup(()=>events.push('physician-cleanup'));return{};}};
  throw Error('Unexpected import '+name);
 };request.main={};vm.runInNewContext(fs.readFileSync(filename,'utf8'),{require:request,module,exports:module.exports,console,process,URL,setTimeout},{filename});return module.exports.workflow;
}
for(const existing of [false,true])test(`actual workflow error/finally restores ${existing?'existing timestamp and identity':'zero-row baseline after default fallback'} in list→preference→physician order`,async t=>{
 const db=database(t,existing?[[1,'999998','List1','2020-01-01 00:00:00']]:[]);const stop=Error('synthetic UI failure');const events=[];
 const execute=db.sql.execute.bind(db.sql);db.sql.execute=(query,args)=>{if(/^DELETE FROM quickList WHERE/.test(query))events.push('list-cleanup');if(/^DELETE FROM quickListUser|^UPDATE quickListUser SET lastUsed=\?/.test(query))events.push('preference-restore');return execute(query,args);};
 const workflow=workflowWith(async()=>{nativePreference(db.sql,'List1');throw stop;},events);
 db.session.ledgerDirectory=db.dir;db.session.patient=77;db.session.schedule={locator:()=>({first(){return this;}})};
 await assert.rejects(workflow(db.session),error=>error===stop);await db.finish();
 assert.deepEqual(all(db.sql),db.before);assert.equal(db.sql.value('SELECT COUNT(*) FROM quickList WHERE quickListName<>?',['List1']),'0');
 assert(events.indexOf('physician')<events.indexOf('preference'));assert(events.indexOf('list-cleanup')<events.indexOf('preference-restore'));assert(events.indexOf('preference-restore')<events.indexOf('physician-cleanup'));
});
test('actual workflow refuses a generated-name collision before registering its list deletion callback',async t=>{
 const db=database(t);const owned='FAKE'+(parseInt(marker.slice(-8),16)%36**6).toString(36).toUpperCase().padStart(6,'0');
 db.sql.execute('INSERT INTO quickList(quickListName,createdByProvider)VALUES(?,?)',[owned,'2']);
 const events=[];const workflow=workflowWith(async()=>{throw Error('UI must not start');},events);db.session.ledgerDirectory=db.dir;
 await assert.rejects(workflow(db.session),/already exists/);assert.equal(db.cleanups.length,1);await db.finish();
 assert.equal(db.sql.value('SELECT COUNT(*) FROM quickList WHERE quickListName=? AND createdByProvider=?',[owned,'2']),'1');assert(!events.includes('list-cleanup'));
});


test('actual workflow browser-close rejection permits zero list or preference cleanup DML',async t=>{
 const db=database(t);const stop=Error('synthetic UI failure');const events=[];
 const workflow=workflowWith(async()=>{nativePreference(db.sql,'List1');throw stop;},events);
 db.session.ledgerDirectory=db.dir;db.session.patient=77;db.session.schedule={locator:()=>({first(){return this;}})};
 await assert.rejects(workflow(db.session),error=>error===stop);
 const before=all(db.sql);db.browser.close=async()=>{throw Error('synthetic browser close refusal');};db.calls.length=0;
 await assert.rejects(db.finish(),/browser close/);
 assert(!db.calls.some(call=>/^(DELETE|UPDATE)/.test(call.query)));assert.deepEqual(all(db.sql),before);
});

test('native-only registry restores a newly created default-list preference without inventing owned lists',async t=>{
 const db=database(t,[[1,'999998','default','2024-04-02 22:33:34']]);
 const handle=createNativeQuickListPreferenceFixture(db.session,{ledgerDirectory:db.dir});
 const beforeLists=db.sql.rows('SELECT * FROM quickList ORDER BY id');nativePreference(db.sql,'List1');await db.finish();
 assert.deepEqual(all(db.sql),db.before);assert.deepEqual(db.sql.rows('SELECT * FROM quickList ORDER BY id'),beforeLists);
 assert.deepEqual(JSON.parse(fs.readFileSync(handle.ledgerPath)).ownedNames,[]);
});
test('native-only registry restores the exact existing timestamp and foreign rows, including NULL',async t=>{
 const db=database(t,[[1,'999998','List1',null],[2,'2','List1','2020-01-01 00:00:00']]);
 createNativeQuickListPreferenceFixture(db.session,{ledgerDirectory:db.dir});nativePreference(db.sql,'List1');await db.finish();assert.deepEqual(all(db.sql),db.before);
});
test('ordinary owned-list API still refuses empty names; native-only mode refuses unknown additions before any DML',async t=>{
 const db=database(t);assert.throws(()=>createQuickListPreferenceFixture(db.session,[],{ledgerDirectory:db.dir}),/Invalid/);
 createNativeQuickListPreferenceFixture(db.session,{ledgerDirectory:db.dir});nativePreference(db.sql,'Foreign');db.calls.length=0;
 await assert.rejects(db.finish(),/Unknown preference/);assert(!db.calls.some(x=>/^(DELETE|UPDATE)/.test(x.query)));
});
test('native-only preference cleanup refuses browser-close failure and preserves the entire snapshot delta',async t=>{
 const db=database(t);createNativeQuickListPreferenceFixture(db.session,{ledgerDirectory:db.dir});nativePreference(db.sql,'List1');const now=all(db.sql);
 db.browser.close=async()=>{throw Error('synthetic close refusal');};db.calls.length=0;
 await assert.rejects(db.finish(),/browser close/);assert(!db.calls.some(x=>/^(DELETE|UPDATE)/.test(x.query)));assert.deepEqual(all(db.sql),now);
});
function sessionCaller(imports={}) {
 const m={exports:{}};const h={assert:(ok,msg)=>assert(ok,msg),sqlString:value=>"'"+String(value).replaceAll("'","''")+"'",insertId:()=> '5'};
 const request=name=>name.startsWith('node:')?require(name):imports[name]||(name==='./lib/playwright-harness'?h:{});
 vm.runInNewContext(fs.readFileSync(process.env.SESSION_STATE_CLEANUP_CALLER_FILE || path.join(__dirname,'session-state-two-window-isolation-playwright-checks.js'),'utf8')+'\nmodule.exports.waitingListPrompt=waitingListPrompt;',
  {require:request,module:m,exports:m.exports,URL,URLSearchParams,Date,Buffer,process:{env:{}}});return m.exports;
}
test('actual session workflow snapshots native preference before its first fixture mutation or UI',async()=>{
 const events=[];const stop=Error('first mutation');
 const caller=sessionCaller({'./lib/quick-list-preference-fixture':{createNativeQuickListPreferenceFixture:()=>events.push('snapshot')},'./lib/export-content-helpers':{requirePoppler:()=>events.push('poppler')}});
 const s={patient:'77',marker,sql:{execute(){events.push('mutation');throw stop;}}};
 await assert.rejects(caller.workflow(s),e=>e===stop);assert.deepEqual(events,['snapshot','poppler','mutation']);
});
test('actual waiting-list caller remembers session/date before Add UI and cleans only new owned locks after browser drain',async t=>{
 const db=database(t);db.sql.execute('CREATE TABLE PageMonitor(id INTEGER PRIMARY KEY,pageName TEXT,pageId TEXT,session TEXT)');
 const actual=require('./lib/appointment-page-monitor-cleanup');let remembered=false;
 const caller=sessionCaller({'./lib/appointment-page-monitor-cleanup':{appointmentMonitorCleanup:scope=>{
  assert.equal(scope.provider,'999998');assert.equal(scope.date,'2026-10-21');const helper=actual.appointmentMonitorCleanup(scope);
  return{remember:async context=>{await helper.remember(context);remembered=true;},cleanup:()=>helper.cleanup()};}}});
 const origValue=db.sql.value.bind(db.sql),origExecute=db.sql.execute.bind(db.sql);
 db.sql.value=(query,args)=>query.includes('myGroupNo')?'':query.startsWith('SELECT DATE_ADD')?'2026-10-21':origValue(query,args);
 db.sql.execute=(query,args)=>query.includes('waitingList')?undefined:origExecute(query,args);
 db.session.context.cookies=async()=>[{name:'JSESSIONID',path:'/carlos',value:'private-test-cookie'}];db.session.config={baseUrl:'https://127.0.0.1/carlos'};
 const stop=Error('native UI boundary');db.session.step=async()=>{assert(remembered);throw stop;};
 await assert.rejects(caller.waitingListPrompt(db.session,'77','78'),e=>e===stop);
 db.sql.execute("INSERT INTO PageMonitor VALUES(1,'addappointment','999998|2026-10-21|10:15|10:29','private-test-cookie'); INSERT INTO PageMonitor VALUES(2,'addappointment','999998|2026-10-21|10:15|10:29','foreign-cookie')");
 const cleanup=db.cleanups.at(-1);db.calls.length=0;assert.throws(cleanup,/browser close/);assert(!db.calls.some(x=>/^DELETE/.test(x.query)));
 db.browser.connected=false;cleanup();assert.deepEqual(db.sql.rows('SELECT id FROM PageMonitor ORDER BY id'),[['2']]);
 assert(db.calls.every(x=>!x.query.includes('private-test-cookie')));
});
