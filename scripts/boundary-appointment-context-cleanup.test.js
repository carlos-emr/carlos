/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {createHash}=require('node:crypto');
const {DatabaseSync}=require('node:sqlite');
const {installOwnedBoundaryCleanup,nativeSlot}=require(process.env.BOUNDARY_APPOINTMENT_HELPER_FILE||'./boundary-appointment-text-playwright-checks');
const {cleanupOwnedWorkflow}=require('./lib/workflow-session');
const {captureScheduleContext}=require('./lib/native-schedule-context');
const MARKER='FAKE-PW0123456789abcdef',BASE='https://127.0.0.1/carlos',SITE="North's & East";
const LINK="popupPage(600,860,'/carlos/appointment/addappointment?provider_no=888888&bFirstDisp=true&year=2026&month=10&day=21&start_time=08%3A55&end_time=08%3A69&duration=15');return false;";
const PAGE='888888|2026-10-21|08:55|09:09';
function fixture(body){
 const db=new DatabaseSync(':memory:');
 db.function('SHA2',(value,size)=>{assert.equal(size,256);return createHash('sha256').update(value).digest('hex');});
 db.exec(`CREATE TABLE demographic(demographic_no INTEGER PRIMARY KEY,last_name TEXT,first_name TEXT,provider_no TEXT,
   year_of_birth TEXT,month_of_birth TEXT,date_of_birth TEXT,sex TEXT,patient_status TEXT,hc_type TEXT,province TEXT,roster_status TEXT,lastUpdateDate TEXT);
  CREATE TABLE provider(provider_no TEXT PRIMARY KEY,last_name TEXT);
  CREATE TABLE security(security_no INTEGER PRIMARY KEY,provider_no TEXT,user_name TEXT);
  CREATE TABLE providersite(provider_no TEXT,site_id INTEGER,PRIMARY KEY(provider_no,site_id));
  CREATE TABLE site(site_id INTEGER PRIMARY KEY,name TEXT,status INTEGER);
  CREATE TABLE PageMonitor(id INTEGER PRIMARY KEY,pageName TEXT,pageId TEXT,session TEXT,providerNo TEXT);
  CREATE TABLE appointment(appointment_no INTEGER PRIMARY KEY,provider_no TEXT,demographic_no INTEGER,appointment_date TEXT,
   start_time TEXT,end_time TEXT,name TEXT,location TEXT,reason TEXT,notes TEXT,resources TEXT);
  CREATE TABLE appointmentArchive(id INTEGER PRIMARY KEY,appointment_no INTEGER,provider_no TEXT,demographic_no INTEGER,
   appointment_date TEXT,start_time TEXT,end_time TEXT,name TEXT,location TEXT,reason TEXT,notes TEXT,resources TEXT);
  INSERT INTO demographic(demographic_no,last_name,first_name,provider_no) VALUES(41,'${MARKER}','Workflow','999998'),(42,'FOREIGN','Foreign','999999');
  INSERT INTO provider VALUES('888888','${MARKER}'),('999998','SHARED');
  INSERT INTO security VALUES(9,'888888','FAKEPW0123456789abcdef');
  INSERT INTO providersite VALUES('999998',7);`);
 db.prepare('INSERT INTO site VALUES(?,?,1)').run(7,SITE);
 for(const [table,column] of [['casemgmt_note_lock','demographic_no'],['casemgmt_tmpsave','demographic_no'],['measurementsDeleted','demographicNo'],['demographicExt','demographic_no'],['demographicArchive','demographic_no'],['demographicExtArchive','demographic_no'],['demographiccust','demographic_no']])db.exec(`CREATE TABLE ${table}(${column} INTEGER)`);
 const state={connected:false,callbacks:[],queries:[],events:[],logs:[]};
 const sql={rows(query){state.queries.push(query);state.events.push(query.startsWith('SELECT id FROM PageMonitor')?'monitor-read':'read');
   const statement=db.prepare(query);statement.setReturnArrays(true);
   const rows=statement.all().map(row=>row.map(value=>value===null?'NULL':String(value)));
   return state.alterRows?state.alterRows(query,rows):rows;},
  value(query){return sql.rows(query)[0]?.[0]||'';},
  execute(query){state.queries.push(query);state.events.push('write');if(!state.beforeWrite||state.beforeWrite(query)!==false)db.exec(query);},dispose(){state.disposed=true;}};
 const browser={isConnected:()=>state.connected,async close(){state.events.push('browser-close');if(state.closeError)throw Error('close failed');state.connected=false;}};
 const context={browser:()=>browser,on(){},setDefaultTimeout(){},async cookies(){state.events.push('cookies');return state.noCookie?[]:[{name:'JSESSIONID',value:'unit-boundary-session',path:'/carlos'}];}};
 const f={db,state,sql,browser,context,fixture:{providerNo:'888888',securityNo:'9',username:'FAKEPW0123456789abcdef'},
  s:{sql,context,patient:'41',provider:'999998',marker:MARKER,config:{baseUrl:BASE},cleanup(fn){state.callbacks.push(fn);}},
  begin(){f.owned=installOwnedBoundaryCleanup(f.s,f.fixture);return f.owned;},
  async bind(){if(!f.owned)f.begin();f.owned.provisionSite({multisites:true,location:SITE});
   f.owned.bind(context,{providerNo:'888888',date:'2026-10-21',multisites:true,location:SITE});await f.owned.rememberSlot(LINK);return f.owned;},
  monitor(id,{provider='888888',session='unit-boundary-session',pageId=PAGE,pageName='addappointment'}={}){db.prepare('INSERT INTO PageMonitor VALUES(?,?,?,?,?)').run(id,pageName,pageId,session,provider);},
  appointment(id=61,{provider='888888',patient=41,date='2026-10-21',start='08:55:00',end='09:09:00',name=`${MARKER},Workflow`,location=SITE,reason='',notes='',resources=''}={}){
   db.prepare('INSERT INTO appointment VALUES(?,?,?,?,?,?,?,?,?,?,?)').run(id,provider,patient,date,start,end,name,location,reason,notes,resources);},
  archive(id=71,appt=61){db.prepare('INSERT INTO appointmentArchive SELECT ?,appointment_no,provider_no,demographic_no,appointment_date,start_time,end_time,name,location,reason,notes,resources FROM appointment WHERE appointment_no=?').run(id,appt);},
  cleanup(){return state.callbacks.at(-1)();},
  ids(){return db.prepare('SELECT id FROM PageMonitor ORDER BY id').all().map(row=>row.id);},
  count(table){return db.prepare(`SELECT COUNT(*) n FROM ${table}`).get().n;},
  async workflowCleanup(){return cleanupOwnedWorkflow({browser,sql,patient:'41',marker:MARKER,cleanups:state.callbacks});},
  guardProvider(){state.callbacks.unshift(()=>{assert(f.owned.canRelease(),'Throwaway retained');db.exec("DELETE FROM security WHERE security_no=9;DELETE FROM provider WHERE provider_no='888888'");});},
 };
 try{return Promise.resolve(body(f)).finally(()=>db.close());}catch(error){db.close();throw error;}
}
test('real SQL cleans exact new native slots and owned appointments while preserving foreign monitor and clinical rows',()=>fixture(async f=>{
 await f.bind();f.monitor(11);f.monitor(12,{provider:'999999'});f.monitor(13,{provider:'999999',pageName:'other'});
 f.appointment();f.archive();f.owned.rememberAppointment('61');f.appointment(99,{provider:'999999',patient:42,name:'FOREIGN'});
 f.guardProvider();await f.workflowCleanup();assert.deepEqual(f.ids(),[12,13]);assert.equal(f.count('appointment'),1);assert.equal(f.count('appointmentArchive'),0);
 assert.equal(f.db.prepare('SELECT COUNT(*) n FROM demographic WHERE demographic_no=41').get().n,0);
 assert.equal(f.db.prepare("SELECT COUNT(*) n FROM provider WHERE provider_no='888888'").get().n,0);
 assert(f.state.queries.every(query=>!query.includes('unit-boundary-session')));
 assert.match(f.state.queries.find(query=>query.startsWith('DELETE')),/^DELETE FROM PageMonitor/);
}));
test('multiple native slots are captured before writes and cleaned independently after closure',()=>fixture(async f=>{
 await f.bind();const second=LINK.replace('08%3A55','09%3A15');await f.owned.rememberSlot(second);
 f.monitor(11);f.monitor(12,{pageId:'888888|2026-10-21|09:15|09:29'});f.appointment();f.appointment(62,{start:'09:15:00',end:'09:29:00'});
 f.cleanup();assert.deepEqual(f.ids(),[]);assert.equal(f.count('appointment'),0);assert(f.owned.canRelease());
}));
for(const kind of ['appointment','appointmentArchive','PageMonitor','providersite'])test(`pre-existing ${kind} baseline refuses and retains both parents`,()=>fixture(async f=>{
 if(kind==='appointmentArchive'){f.appointment();f.archive();f.db.exec('DELETE FROM appointment');}
 else if(kind==='appointment')f.appointment();else if(kind==='PageMonitor')f.monitor(11);else f.db.exec("INSERT INTO providersite VALUES('888888',8)");
 assert.throws(()=>f.begin(),/already owns/);f.guardProvider();await assert.rejects(f.workflowCleanup(),/baseline is unconfirmed/);
 assert.equal(f.db.prepare('SELECT COUNT(*) n FROM demographic WHERE demographic_no=41').get().n,1);assert.equal(f.count(kind),kind==='providersite'?2:1);
}));
for(const connected of [true,'missing','different-context'])test(`browser ownership/closure ${connected} prevents all cleanup writes`,()=>fixture(async f=>{
 await f.bind();f.monitor(11);f.appointment();const count=f.state.queries.length;
 if(connected==='missing')f.s.context={browser:()=>null};else if(connected==='different-context')f.s.context={browser:()=>({isConnected:()=>false})};else f.state.connected=true;
 assert.throws(()=>f.cleanup(),/browser closure/);assert(!f.state.queries.slice(count).some(q=>q.startsWith('DELETE')));assert.deepEqual(f.ids(),[11]);
}));
for(const table of ['demographic','provider','security'])test(`changed ${table} ownership refuses before deleting monitors or evidence`,()=>fixture(async f=>{
 await f.bind();f.monitor(11);f.appointment();f.db.exec(table==='security'?"UPDATE security SET user_name='FOREIGN'":`UPDATE ${table} SET last_name='FOREIGN' ${table==='provider'?"WHERE provider_no='888888'":'WHERE demographic_no=41'}`);
 assert.throws(()=>f.cleanup(),/ownership changed/);assert.deepEqual(f.ids(),[11]);assert.equal(f.count('appointment'),1);
}));
test('silent monitor deletion fails actual teardown and retains both marker parents and appointment evidence',()=>fixture(async f=>{
 await f.bind();f.monitor(11);f.appointment();f.guardProvider();f.state.beforeWrite=q=>!q.startsWith('DELETE FROM PageMonitor');
 await assert.rejects(f.workflowCleanup(),/booking locks/);assert.deepEqual(f.ids(),[11]);assert.equal(f.count('appointment'),1);assert.equal(f.count('provider'),2);assert.equal(f.count('demographic'),2);assert(f.state.disposed);
}));
test('monitor reassignment during deletion cannot pass scoped count and remove marker parents',()=>fixture(async f=>{
 await f.bind();f.monitor(11);f.appointment();f.owned.rememberAppointment('61');f.guardProvider();
 f.state.beforeWrite=q=>{if(q.startsWith('DELETE FROM PageMonitor'))f.db.exec("UPDATE PageMonitor SET providerNo='999999' WHERE id=11");};
 await assert.rejects(f.workflowCleanup(),/monitor remains or changed ownership/);assert.deepEqual(f.ids(),[11]);assert.equal(f.count('appointment'),1);assert.equal(f.count('provider'),2);
}));
test('atomic patient marker guard prevents monitor deletion after owner changes between selection and DELETE',()=>fixture(async f=>{
 await f.bind();f.monitor(11);f.state.beforeWrite=q=>{if(q.startsWith('DELETE FROM PageMonitor'))f.db.exec("UPDATE demographic SET last_name='FOREIGN' WHERE demographic_no=41");};
 assert.throws(()=>f.cleanup(),/monitor remains|ownership changed/);assert.deepEqual(f.ids(),[11]);
}));
for(const foreign of [{session:'foreign-session'},{pageId:'888888|2026-10-21|09:15|09:29'},{pageName:'other'}])test(`foreign concurrent monitor ${Object.keys(foreign)[0]} is preserved and retains the throwaway`,()=>fixture(async f=>{
 await f.bind();f.monitor(11,foreign);f.guardProvider();await assert.rejects(f.workflowCleanup(),/Foreign or uncertain/);assert.deepEqual(f.ids(),[11]);assert.equal(f.count('provider'),2);
}));
test('unowned appointment or archived version is refused before the first clinical DELETE',()=>fixture(async f=>{
 await f.bind();f.appointment();f.archive();f.db.exec("UPDATE appointmentArchive SET name='FOREIGN' WHERE id=71");f.owned.rememberAppointment('61');
 assert.throws(()=>f.cleanup(),/unowned boundary/);assert.equal(f.count('appointment'),1);assert.equal(f.count('appointmentArchive'),1);
 assert(!f.state.queries.some(q=>/^DELETE FROM appointment/.test(q)));
}));
test('atomic clinical identity guard catches reassignment and retains both parents',()=>fixture(async f=>{
 await f.bind();f.appointment();f.owned.rememberAppointment('61');f.guardProvider();f.state.beforeWrite=q=>{if(q.startsWith('DELETE FROM appointment '))f.db.exec("UPDATE appointment SET provider_no='999999' WHERE appointment_no=61");};
 await assert.rejects(f.workflowCleanup(),/Captured boundary appointment rows remain/);assert.equal(f.count('appointment'),1);assert.equal(f.count('provider'),2);assert.equal(f.count('demographic'),2);
}));
test('a captured appointment moved to another provider before cleanup is not silently missed',()=>fixture(async f=>{
 await f.bind();f.appointment();f.owned.rememberAppointment('61');f.db.exec("UPDATE appointment SET provider_no='999999' WHERE appointment_no=61");assert.throws(()=>f.cleanup(),/unowned boundary/);assert.equal(f.count('appointment'),1);
}));
test('only registered native free-text names and their exact column truncation belong to this boundary probe',()=>fixture(async f=>{
 await f.bind();const name='Nabcdef'.repeat(12);f.owned.allowFreeName(name,80);f.appointment(61,{patient:0,name:name.slice(0,80)});f.archive();
 f.cleanup();assert.equal(f.count('appointment'),0);assert.equal(f.count('appointmentArchive'),0);
}));
test('unregistered free-text names sharing the tail are preserved',()=>fixture(async f=>{
 await f.bind();f.owned.allowFreeName('Nabcdef',80);f.appointment(61,{patient:0,name:'FOREIGNabcdef'});assert.throws(()=>f.cleanup(),/unowned boundary/);assert.equal(f.count('appointment'),1);
}));
test('concurrent site memberships are preserved and cannot be swept up by throwaway teardown',()=>fixture(async f=>{
 await f.bind();f.db.exec("INSERT INTO providersite VALUES('888888',8)");f.guardProvider();await assert.rejects(f.workflowCleanup(),/Foreign or uncertain/);
 assert.equal(f.db.prepare("SELECT COUNT(*) n FROM providersite WHERE provider_no='888888' AND site_id=8").get().n,1);assert.equal(f.count('provider'),2);
}));
for(const id of [0,-1,2147483648])test(`invalid monitor ID ${id} is refused before DELETE`,()=>fixture(async f=>{
 await f.bind();f.monitor(id);assert.throws(()=>f.cleanup(),/identity is invalid/);assert.deepEqual(f.ids(),[id]);
}));
test('signed integer maximum monitor and appointment IDs are supported without precision loss',()=>fixture(async f=>{
 await f.bind();f.monitor(2147483647);f.appointment(2147483647);f.owned.rememberAppointment('2147483647');f.cleanup();assert.deepEqual(f.ids(),[]);assert.equal(f.count('appointment'),0);
}));
test('duplicate and malformed monitor evidence fail before deletion',()=>fixture(async f=>{
 await f.bind();f.monitor(11);f.state.alterRows=(q,rows)=>q.startsWith('SELECT id FROM PageMonitor')?[...rows,...rows]:rows;assert.throws(()=>f.cleanup(),/duplicated/);
 f.state.alterRows=(q,rows)=>q.startsWith('SELECT id FROM PageMonitor')?[['11','extra']]:rows;assert.throws(()=>f.cleanup(),/malformed/);assert.deepEqual(f.ids(),[11]);
}));
test('native slot parser handles hour rollover and refuses ambiguous/foreign inputs',()=>{
 assert.deepEqual(nativeSlot(LINK,BASE),{provider:'888888',date:'2026-10-21',start:'08:55',end:'09:09',pageId:PAGE});
 for(const edit of [x=>x.replace('provider_no=888888','provider_no=0'),x=>x.replace('year=2026','year=2026&year=2027'),x=>x.replace('/carlos/appointment','https://foreign.invalid/carlos/appointment')])assert.throws(()=>nativeSlot(edit(LINK),BASE));
});
function schedule(f,actor){
 return {url:()=>BASE+'/provider/providercontrol?displaymode=day',setDefaultTimeout(){},waitForLoadState:async()=>{},reload:async()=>{},
  locator(selector){
   const values={'#scheduleLoggedInProviderNo':actor,'#scheduleVisibleProviderNos':actor,'#scheduleCurrentDate':'2026-10-21'};
   if(selector in values)return{inputValue:async()=>values[selector]};
   if(selector==='select#site[name="site"]')return{count:async()=>1,inputValue:async()=>'none',locator:()=>({evaluateAll:async fn=>fn(f.db.prepare('SELECT site.name value FROM site JOIN providersite p ON p.site_id=site.site_id WHERE p.provider_no=? AND site.status<>0').all(actor).map(row=>({...row,disabled:false,closest:()=>null})))})};
   if(selector.startsWith('a.adhour'))return{nth:()=>({getAttribute:async()=>LINK,kind:'slot'})};
   if(selector.includes('/appointment/editappointment'))return{first:()=>({kind:'edit',waitFor:async()=>{
    const row=f.db.prepare('SELECT location FROM appointment WHERE appointment_no=61').get();
    assert(row&&nativeVisible(true,null,new Map([[SITE,'Y']]),row.location),'The native day sheet hides the saved blank-location booking');
    f.state.events.push('edit-link-visible');}})};
   throw Error('Unexpected native schedule locator '+selector);
  }};
}
const jsp=fs.readFileSync(path.join(__dirname,'../src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp'),'utf8');
const expression=jsp.match(/if \((!bMultisites \|\| \(selectedSite == null && CurrentSiteMap\.get\(sitename\) != null\) \|\| sitename\.equals\(selectedSite\))\)/)?.[1];
assert(expression,'The real multisite visibility predicate must be found');
const nativeVisible=new Function('bMultisites','selectedSite','CurrentSiteMap','sitename',`return ${expression.replace('sitename.equals(selectedSite)','(sitename === selectedSite)')};`);
async function replayNativeWorkflow(f,{monitorDeleteFails=false}={}){
 const caller=process.env.BOUNDARY_APPOINTMENT_CALLER_FILE||path.join(__dirname,'boundary-appointment-text-playwright-checks.js');
 const module={exports:{}},stop=Error('native edit link reached');const calls=[];f.s.schedule=schedule(f,'999998');
 const popupFields={provider_no:'888888',appointment_date:'2026-10-21',start_time:'08:55',end_time:'09:09',demographic_no:'',keyword:'',reason:'',notes:'',resources:'',location:''};
 const optionRows=()=>f.db.prepare("SELECT site.name value FROM site JOIN providersite p ON p.site_id=site.site_id WHERE p.provider_no='888888'").all().map(row=>({...row,disabled:false}));
 const popup={waitForLoadState:async()=>{},close:async()=>{},locator(selector){
  if(selector==='form#addappt')return{evaluate:async fn=>fn({elements:{namedItem:name=>({value:popupFields[name]})}})};
  if(selector==='form#addappt select[name="location"]')return{count:async()=>1,locator:()=>({evaluateAll:async(fn,arg)=>fn(optionRows(),arg)}),selectOption:async({value})=>{popupFields.location=value;},inputValue:async()=>popupFields.location};
  if(selector.startsWith('table tr'))return{first:()=>({kind:'pick',waitFor:async()=>{}})};
  const field=selector.match(/\[name="(\w+)"\]/)?.[1]||selector.replace(/^#/, '');
  return{kind:field,fill:async value=>{popupFields[field]=value;},inputValue:async()=>popupFields[field]};
 }};
 const h={assert:(ok,msg)=>assert(ok,msg),sqlString:value=>`'${String(value).replaceAll("'","''")}'`,newContext:async()=>f.context,wireStrictPage(){},login:async()=>schedule(f,'888888')};
 const boundary={...require('./lib/boundary-values'),columnLength:(_,table,column)=>({reason:80,notes:255}[column]),assertStored(sql,table,column,where,value){assert.equal(sql.value(`SELECT ${column} FROM ${table} WHERE ${where}`),value);calls.push(column);}};
 const imports={'./lib/playwright-harness':h,'./lib/boundary-values':boundary,'./lib/workflow-session':{},
  './lib/gap-provider-fixture':{createUnbookedThrowaway(s){s.cleanup(()=>{f.db.exec("DELETE FROM providersite WHERE provider_no='888888';DELETE FROM security WHERE security_no=9;DELETE FROM provider WHERE provider_no='888888'");});return f.fixture;},registerAppointmentCleanup(){}},
  './lib/appointment-page-monitor-cleanup':require('./lib/appointment-page-monitor-cleanup'),
  './lib/native-schedule-context':require('./lib/native-schedule-context'),
  './lib/playwright-ui':{async clickOpensPopup(page,target){if(target.kind==='edit')throw stop;f.state.events.push('popup');f.monitor(11);popupFields.location=optionRows()[0]?.value||'';return popup;},
   async clickAndAwaitReload(page,target){if(target.kind==='pick')popupFields.demographic_no='41';if(target.kind==='addButton')f.appointment(61,{reason:popupFields.reason,notes:popupFields.notes,resources:popupFields.resources,location:popupFields.location});}}};
 const request=name=>imports[name]||require(name);request.main=null;
 vm.runInNewContext(fs.readFileSync(caller,'utf8'),{module,exports:module.exports,require:request,URL,Date,Buffer,BigInt,console:{log:text=>f.state.logs.push(text)},setTimeout:fn=>{fn();return 0;}});
 f.s.step=async(label,body)=>body();await assert.rejects(module.exports.workflow(f.s),error=>error===stop);
 assert.deepEqual(calls,['reason','notes','resources']);assert(f.state.events.includes('edit-link-visible'));
 assert(f.state.events.indexOf('monitor-read')<f.state.events.indexOf('popup'));
 assert.equal(f.db.prepare('SELECT location FROM appointment WHERE appointment_no=61').get().location,SITE);
 assert(f.state.logs.every(line=>!line.includes('unit-boundary-session')));
 if(monitorDeleteFails){f.state.beforeWrite=query=>!query.startsWith('DELETE FROM PageMonitor');
  await assert.rejects(f.workflowCleanup(),/booking locks/);assert.equal(f.count('provider'),2);assert.equal(f.count('appointment'),1);assert.deepEqual(f.ids(),[11]);
 }else{await f.workflowCleanup();assert.deepEqual(f.ids(),[]);assert.equal(f.count('appointment'),0);}
}
test('real native scheduling predicate shows the saved booking and reaches its edit link with the exact boundary payload',()=>fixture(f=>replayNativeWorkflow(f)));
test('real workflow retains its throwaway callback and both parents when monitor deletion fails',()=>fixture(f=>replayNativeWorkflow(f,{monitorDeleteFails:true})));

test('native nonmultisite booking preserves the legacy blank location without assigning a site',()=>fixture(async f=>{
 f.begin();f.owned.provisionSite({multisites:false,location:''});f.owned.bind(f.context,{providerNo:'888888',date:'2026-10-21',multisites:false,location:''});
 await f.owned.rememberSlot(LINK);f.appointment(61,{location:''});f.cleanup();assert.equal(f.count('appointment'),0);
 assert.equal(f.db.prepare("SELECT COUNT(*) n FROM providersite WHERE provider_no='888888'").get().n,0);
}));
for(const [kind,change] of [['inactive',"UPDATE site SET status=0 WHERE site_id=7"],['foreign membership',"DELETE FROM providersite WHERE provider_no='999998'"],['ambiguous name',"INSERT INTO site VALUES(8,'North''s & East',1);INSERT INTO providersite VALUES('999998',8)"]])
 test(`site provisioning refuses ${kind} source before writing the throwaway membership`,()=>fixture(f=>{
  f.begin();f.db.exec(change);const before=f.state.queries.length;assert.throws(()=>f.owned.provisionSite({multisites:true,location:SITE}),/absent or ambiguous/);
  assert(!f.state.queries.slice(before).some(q=>q.startsWith('INSERT')));f.cleanup();
 }));
test('a lost site INSERT reply is cleaned only through its captured exact provider/site pair',()=>fixture(f=>{
 f.begin();f.state.beforeWrite=query=>{if(query.startsWith('INSERT INTO providersite')){f.db.exec(query);throw Error('lost INSERT reply');}};
 assert.throws(()=>f.owned.provisionSite({multisites:true,location:SITE}),/lost INSERT reply/);f.state.beforeWrite=null;f.cleanup();
 assert.equal(f.db.prepare("SELECT COUNT(*) n FROM providersite WHERE provider_no='888888'").get().n,0);assert(f.owned.canRelease());
}));


test('exact30/30 additional patient leaves the generic patient untouched and releases only after all native children and monitors',()=>fixture(async f=>{
 await f.bind();const linked=f.owned.createLongNamePatient();
 assert.equal(linked.lastName.length,30);assert.equal(linked.firstName.length,30);assert.equal(linked.name.length,61);
 assert.equal(f.db.prepare('SELECT last_name,first_name FROM demographic WHERE demographic_no=41').get().last_name,MARKER);
 f.appointment(61,{patient:Number(linked.patient),name:linked.name});f.archive();f.monitor(11);f.owned.rememberAppointment('61');
 f.guardProvider();await f.workflowCleanup();assert.equal(f.count('demographic'),1);assert.equal(f.count('provider'),1);
 assert.equal(f.count('appointment'),0);assert.equal(f.count('appointmentArchive'),0);assert.deepEqual(f.ids(),[]);
 const parentDelete=f.state.queries.findIndex(q=>q.startsWith('DELETE FROM demographic WHERE demographic_no='+linked.patient));
 assert(parentDelete>f.state.queries.findIndex(q=>q.startsWith('DELETE FROM appointment ')));
}));
test('pre-existing exact long-name pair is refused without adopting or deleting its patient',()=>fixture(async f=>{
 await f.bind();f.db.prepare('INSERT INTO demographic(demographic_no,last_name,first_name,provider_no) VALUES(?,?,?,?)')
  .run(43,MARKER+'-Longxx','Linked-'+MARKER.slice(-16)+'xxxxxxx','999999');
 assert.throws(()=>f.owned.createLongNamePatient(),/already exists/);f.cleanup();assert.equal(f.count('demographic'),3);
}));
test('a lost linked-patient INSERT reply retains intent and resolves only the exact new owned identity for cleanup',()=>fixture(async f=>{
 await f.bind();f.state.beforeWrite=q=>{if(q.startsWith('INSERT INTO demographic')){f.db.exec(q);throw Error('lost linked INSERT reply');}};
 assert.throws(()=>f.owned.createLongNamePatient(),/lost linked INSERT/);assert.equal(f.count('demographic'),3);
 f.state.beforeWrite=null;f.cleanup();assert.equal(f.count('demographic'),2);assert(f.owned.canRelease());
}));
for(const change of ['last_name','first_name','provider_no'])test(`changed additional patient ${change} retains original and throwaway parents`,()=>fixture(async f=>{
 await f.bind();const linked=f.owned.createLongNamePatient();f.appointment(61,{patient:Number(linked.patient),name:linked.name});f.owned.rememberAppointment('61');f.guardProvider();
 f.db.prepare(`UPDATE demographic SET ${change}=? WHERE demographic_no=?`).run('FOREIGN',Number(linked.patient));
 await assert.rejects(f.workflowCleanup(),/ownership changed|disappeared/);assert.equal(f.count('appointment'),1);assert.equal(f.count('demographic'),3);assert.equal(f.count('provider'),2);
}));
test('a silently retained linked appointment prevents removal of either patient and the provider',()=>fixture(async f=>{
 await f.bind();const linked=f.owned.createLongNamePatient();f.appointment(61,{patient:Number(linked.patient),name:linked.name});f.archive();f.owned.rememberAppointment('61');f.guardProvider();
 f.state.beforeWrite=q=>!q.startsWith('DELETE FROM appointment ');await assert.rejects(f.workflowCleanup(),/Captured boundary/);
 assert.equal(f.count('demographic'),3);assert.equal(f.count('provider'),2);assert.equal(f.count('appointment'),1);
}));
test('an unregistered linked child under another provider is retained and blocks its extra parent release',()=>fixture(async f=>{
 await f.bind();const linked=f.owned.createLongNamePatient();f.appointment(61,{provider:'999999',patient:Number(linked.patient),name:linked.name});f.guardProvider();
 await assert.rejects(f.workflowCleanup(),/still has clinical or appointment children/);assert.equal(f.count('demographic'),3);assert.equal(f.count('provider'),2);assert.equal(f.count('appointment'),1);
}));
test('unexpected support children prevent extra parent deletion rather than broad support cleanup',()=>fixture(async f=>{
 await f.bind();const linked=f.owned.createLongNamePatient();f.db.prepare('INSERT INTO demographiccust VALUES(?)').run(Number(linked.patient));f.guardProvider();
 await assert.rejects(f.workflowCleanup(),/clinical or appointment children/);assert.equal(f.count('demographiccust'),1);assert.equal(f.count('demographic'),3);assert.equal(f.count('provider'),2);
}));
test('a child arriving between reference verification and parent DELETE cannot become an orphan',()=>fixture(async f=>{
 await f.bind();const linked=f.owned.createLongNamePatient();f.guardProvider();
 f.state.beforeWrite=q=>{if(q.startsWith('DELETE FROM demographic WHERE demographic_no='+linked.patient))f.db.prepare('INSERT INTO demographiccust VALUES(?)').run(Number(linked.patient));};
 await assert.rejects(f.workflowCleanup(),/not removed or changed ownership/);assert.equal(f.count('demographiccust'),1);assert.equal(f.count('demographic'),3);assert.equal(f.count('provider'),2);
}));
test('a same-prefix linked appointment with a different full name is foreign and never swept',()=>fixture(async f=>{
 await f.bind();const linked=f.owned.createLongNamePatient();f.appointment(61,{patient:Number(linked.patient),name:linked.name+'X'});f.owned.rememberAppointment('61');
 assert.throws(()=>f.cleanup(),/unowned boundary/);assert.equal(f.count('demographic'),3);assert.equal(f.count('appointment'),1);
}));
