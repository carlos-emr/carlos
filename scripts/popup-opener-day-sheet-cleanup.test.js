/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {createHash} = require('node:crypto');
const {createRequire} = require('node:module');
const {DatabaseSync} = require('node:sqlite');
const {installOwnedDaySheetCleanup, nativeSlot} = require('./popup-opener-day-sheet-playwright-checks');
const {cleanupOwnedWorkflow} = require('./lib/workflow-session');
const MARKER = 'FAKE-PW0123456789abcdef';
const BASE = 'https://127.0.0.1/carlos';
const LINK = "confirmPopupPage(600,860, '/carlos/appointment/addappointment?provider_no=999997&bFirstDisp=true&year=2026&month=10&day=21&start_time=08%3A55&end_time=08%3A69&duration=15','','','');return false;";
const PAGE = '999997|2026-10-21|08:55|09:09';

function fixture(body, options = {}) {
  const db = new DatabaseSync(':memory:');
  db.function('SHA2', (text, size) => { assert.equal(size, 256); return createHash('sha256').update(text).digest('hex'); });
  db.function('NOW', () => '2026-10-21 04:00:00');
  db.exec(`CREATE TABLE demographic(demographic_no INTEGER PRIMARY KEY,last_name TEXT,provider_no TEXT);
    CREATE TABLE PageMonitor(id INTEGER PRIMARY KEY,pageName TEXT,pageId TEXT,session TEXT,providerNo TEXT);
    CREATE TABLE appointment(appointment_no INTEGER PRIMARY KEY,demographic_no INTEGER,provider_no TEXT,reason TEXT);
    CREATE TABLE appointmentArchive(id INTEGER PRIMARY KEY,appointment_no INTEGER,demographic_no INTEGER,provider_no TEXT,reason TEXT);
    CREATE TABLE admission(id INTEGER PRIMARY KEY,client_id INTEGER,provider_no TEXT,program_id INTEGER,
      admission_date TEXT,admission_from_transfer INTEGER,discharge_from_transfer INTEGER,admission_status TEXT,lastUpdateDate TEXT);
    CREATE TABLE program_provider(provider_no TEXT,program_id INTEGER);
    INSERT INTO demographic VALUES(41,'${MARKER}','999998'),(42,'FOREIGN','888888');
    INSERT INTO program_provider VALUES('999998',7);`);
  for (const [table, column] of [['casemgmt_note_lock','demographic_no'],['casemgmt_tmpsave','demographic_no'],
    ['measurementsDeleted','demographicNo'],['demographicExt','demographic_no'],['demographicArchive','demographic_no']]) {
    db.exec(`CREATE TABLE ${table}(${column} INTEGER)`);
  }
  const state = {connected: false, callbacks: [], queries: [], events: [], disposed: false};
  const sql = {
    rows(query) {
      state.queries.push(query); state.events.push(query.startsWith('SELECT id FROM PageMonitor') ? 'monitor-snapshot-or-read' : 'read');
      const rows = db.prepare(query).all().map(row => Object.values(row).map(String));
      return state.alterRows ? state.alterRows(query, rows) : rows;
    },
    value(query) {
      if (query.includes('; SELECT LAST_INSERT_ID()')) {
        sql.execute(query.replace('; SELECT LAST_INSERT_ID()', ''));
        return state.lostAdmissionReply ? '' : String(db.prepare('SELECT last_insert_rowid() AS id').get().id);
      }
      return sql.rows(query)[0]?.[0] || '';
    },
    execute(query) { state.queries.push(query); state.events.push('write'); if (!state.beforeWrite || state.beforeWrite(query) !== false) db.exec(query); },
    dispose() { state.disposed = true; },
  };
  const browser = {isConnected: () => state.connected, async close() {
    state.events.push('browser-close'); if (state.closeError) throw new Error('browser close failed'); state.connected = false;
  }};
  const context = {browser: () => browser, on() {}, async cookies() {
    state.events.push('cookies'); return state.noCookie ? [] : [{name:'JSESSIONID',value:'unit-owned-session',path:'/carlos'}];
  }};
  const s = {sql, context, config:{baseUrl: new URL(BASE)}, patient:'41', provider:'999998', marker:MARKER,
    cleanup(fn) { state.callbacks.push(fn); }};
  const monitor = (id, {session='unit-owned-session', provider='999998', pageId=PAGE, pageName='addappointment'} = {}) =>
    db.prepare('INSERT INTO PageMonitor VALUES(?,?,?,?,?)').run(id,pageName,pageId,session,provider);
  const admission = (id=31, patient=41, provider='999998') => db.prepare('INSERT INTO admission(id,client_id,provider_no,program_id) VALUES(?,?,?,7)').run(id,patient,provider);
  const appointments = () => {
    db.prepare('INSERT INTO appointmentArchive VALUES(?,?,?,?,?)').run(51,61,41,'999997',MARKER);
    db.prepare('INSERT INTO appointment VALUES(?,?,?,?)').run(62,41,'999997',MARKER);
  };
  const f = {db,state,sql,browser,s,monitor,admission,appointments,
    begin() { f.owned = installOwnedDaySheetCleanup(s); return f.owned; },
    cleanup() { assert.equal(state.callbacks.length,1); return state.callbacks[0](); },
    ids() { return db.prepare('SELECT id FROM PageMonitor ORDER BY id').all().map(row => row.id); },
    parent() { return db.prepare('SELECT COUNT(*) AS n FROM demographic WHERE demographic_no=41').get().n; },
  };
  Object.assign(s, options);
  try { return Promise.resolve(body(f)).finally(() => db.close()); }
  catch (error) { db.close(); throw error; }
}

test('real SQL removes every new exact slot/session/login-provider monitor and preserves foreign controls', () => fixture(async f => {
  const owned = f.begin(); await owned.rememberSlot(LINK);
  f.monitor(11); f.monitor(12); f.monitor(13,{session:'foreign'}); f.monitor(14,{provider:'888888'});
  f.monitor(15,{pageId:'999997|2026-10-21|09:15|09:29'}); f.monitor(16,{pageId:PAGE.replace('2026-10-21','2026-10-22')});
  f.monitor(17,{pageName:'other-page'}); f.monitor(18,{pageId:PAGE.replace('999997','999996')});
  f.admission(); owned.admissionCreated('31'); f.appointments();
  f.db.exec("INSERT INTO appointment VALUES(70,42,'999997','FOREIGN'); INSERT INTO admission(id,client_id,provider_no) VALUES(71,42,'999998')");
  f.cleanup(); assert.deepEqual(f.ids(),[13,14,15,16,17,18]);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM appointmentArchive').get().n,0);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM appointment').get().n,1);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM admission').get().n,1);
  assert.ok(f.state.queries.every(query => !query.includes('unit-owned-session')));
  const firstDelete = f.state.queries.find(query => query.startsWith('DELETE'));
  assert.match(firstDelete,/^DELETE FROM PageMonitor/);
}));

test('a pre-existing exact owned-session monitor refuses before popup writes and is retained unchanged', () => fixture(async f => {
  f.monitor(11); const owned = f.begin();
  await assert.rejects(owned.rememberSlot(LINK),/already has/);
  f.cleanup(); assert.deepEqual(f.ids(),[11]); assert.equal(f.state.queries.filter(q=>q.startsWith('DELETE FROM PageMonitor')).length,0);
}));

test('missing session refuses setup without consuming any monitor', () => fixture(async f => {
  const owned=f.begin();f.state.noCookie=true;await assert.rejects(owned.rememberSlot(LINK),/fixture session/);
  f.monitor(11);f.cleanup();assert.deepEqual(f.ids(),[11]);
}));

for (const connected of [true, 'missing']) test(`unconfirmed browser closure (${connected}) refuses every cleanup write`, () => fixture(async f => {
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);f.admission();owned.admissionCreated('31');f.appointments();
  if(connected==='missing')f.s.context.browser=()=>null;else f.state.connected=true;
  assert.throws(()=>f.cleanup(),/browser closure/);assert.deepEqual(f.ids(),[11]);assert.equal(f.state.queries.filter(q=>q.startsWith('DELETE')).length,0);
}));

test('changed patient marker retains monitors and all appointment evidence', () => fixture(async f => {
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);f.appointments();f.db.exec("UPDATE demographic SET last_name='FOREIGN' WHERE demographic_no=41");
  assert.throws(()=>f.cleanup(),/ownership changed/);assert.deepEqual(f.ids(),[11]);assert.equal(f.state.queries.filter(q=>q.startsWith('DELETE')).length,0);
}));

test('silent monitor DELETE failure retains appointments and the live marker parent through actual workflow cleanup', () => fixture(async f => {
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);f.appointments();
  f.state.beforeWrite=query=>!query.startsWith('DELETE FROM PageMonitor');
  await assert.rejects(cleanupOwnedWorkflow({browser:f.browser,sql:f.sql,patient:'41',marker:MARKER,cleanups:f.state.callbacks}),/booking locks/);
  assert.equal(f.parent(),1);assert.deepEqual(f.ids(),[11]);assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM appointmentArchive').get().n,1);assert.equal(f.state.disposed,true);
}));

test('a monitor reassigned between selection and deletion is preserved and cannot permit parent cleanup', () => fixture(async f => {
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);f.appointments();
  f.state.beforeWrite=query=>{if(query.startsWith('DELETE FROM PageMonitor'))f.db.exec("UPDATE PageMonitor SET providerNo='888888' WHERE id=11");};
  await assert.rejects(cleanupOwnedWorkflow({browser:f.browser,sql:f.sql,patient:'41',marker:MARKER,cleanups:f.state.callbacks}),/changed ownership/);
  assert.equal(f.parent(),1);assert.deepEqual(f.ids(),[11]);assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM appointmentArchive').get().n,1);
}));

test('atomic marker predicate preserves a monitor if the patient changes immediately before DELETE', () => fixture(async f => {
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);f.appointments();
  f.state.beforeWrite=query=>{if(query.startsWith('DELETE FROM PageMonitor'))f.db.exec("UPDATE demographic SET last_name='FOREIGN' WHERE demographic_no=41");};
  assert.throws(()=>f.cleanup(),/monitor remains|ownership changed/);assert.deepEqual(f.ids(),[11]);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM appointmentArchive').get().n,1);
}));

test('a foreign row attached to the same patient is preserved and retains the patient for review', () => fixture(async f => {
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);f.appointments();
  f.db.exec("INSERT INTO appointment VALUES(99,41,'999997','FOREIGN')");
  await assert.rejects(cleanupOwnedWorkflow({browser:f.browser,sql:f.sql,patient:'41',marker:MARKER,cleanups:f.state.callbacks}),/foreign or uncertain/);
  assert.equal(f.parent(),1);assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM appointment WHERE appointment_no=99').get().n,1);
}));

test('only the captured admission identity is removed; a concurrent admission is retained', () => fixture(async f => {
  const owned=f.begin();f.admission(31);owned.admissionCreated('31');f.admission(32);
  assert.throws(()=>f.cleanup(),/foreign or uncertain/);assert.equal(f.db.prepare('SELECT id FROM admission').get().id,32);
}));

test('successful actual workflow cleanup removes its parent only after scoped monitor and child verification', () => fixture(async f => {
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);f.admission();owned.admissionCreated('31');f.appointments();
  await cleanupOwnedWorkflow({browser:f.browser,sql:f.sql,patient:'41',marker:MARKER,cleanups:f.state.callbacks});
  assert.equal(f.parent(),0);assert.deepEqual(f.ids(),[]);assert.equal(f.state.disposed,true);
}));

test('browser close failure reaches the local guard and leaves children and parent intact', () => fixture(async f => {
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);f.appointments();f.state.connected=true;f.state.closeError=true;
  await assert.rejects(cleanupOwnedWorkflow({browser:f.browser,sql:f.sql,patient:'41',marker:MARKER,cleanups:f.state.callbacks}),/browser close failed.*browser closure/);
  assert.equal(f.parent(),1);assert.deepEqual(f.ids(),[11]);assert.equal(f.state.queries.filter(q=>q.startsWith('DELETE')).length,0);
}));

test('pre-existing child baseline refuses before writes and retains the parent in actual cleanup', () => fixture(async f => {
  f.admission();assert.throws(()=>f.begin(),/already has/);
  await assert.rejects(cleanupOwnedWorkflow({browser:f.browser,sql:f.sql,patient:'41',marker:MARKER,cleanups:f.state.callbacks}),/baseline is unconfirmed/);
  assert.equal(f.parent(),1);assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM admission').get().n,1);
}));

for(const id of [0,-1,2147483648])test(`invalid monitor identity ${id} refuses deletion`,()=>fixture(async f=>{
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(id);assert.throws(()=>f.cleanup(),/identity is invalid/);assert.deepEqual(f.ids(),[id]);
}));
test('maximum signed integer monitor identity is accepted',()=>fixture(async f=>{
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(2147483647);f.cleanup();assert.deepEqual(f.ids(),[]);
}));
test('duplicate or malformed monitor evidence refuses before deleting',()=>fixture(async f=>{
  const owned=f.begin();await owned.rememberSlot(LINK);f.monitor(11);
  f.state.alterRows=(query,rows)=>query.startsWith('SELECT id FROM PageMonitor')?[...rows,...rows]:rows;
  assert.throws(()=>f.cleanup(),/duplicated/);assert.deepEqual(f.ids(),[11]);
  f.state.alterRows=(query,rows)=>query.startsWith('SELECT id FROM PageMonitor')?[['11','extra']]:rows;
  assert.throws(()=>f.cleanup(),/malformed/);assert.deepEqual(f.ids(),[11]);
}));

test('native slot identity uses the UI end-time algorithm and retains the actual selected provider',()=>{
  assert.deepEqual(nativeSlot(LINK,new URL(BASE)),{provider:'999997',date:'2026-10-21',start:'08:55',end:'09:09',pageId:PAGE});
  const escaped=LINK.replace('08%3A55','08\\x2555').replace('08\\x2555','08\\x253A55');
  assert.equal(nativeSlot(escaped,BASE).pageId,PAGE);
});
test('a slot with no template duration takes its length from the same URL end time',()=>{
  // A day sheet with no template code posts duration= and Add Appointment uses the provider's everyMin.
  assert.deepEqual(nativeSlot(LINK.replace('duration=15','duration='),BASE),{provider:'999997',date:'2026-10-21',start:'08:55',end:'09:09',pageId:PAGE});
  for(const end of ['','08%3A54','x%3A10'])assert.throws(()=>nativeSlot(LINK.replace('duration=15','duration=').replace('end_time=08%3A69',`end_time=${end}`),BASE),/native slot (end )?time is invalid/);
});
test('slot cleanup identity agrees with the actual JSP end-time function across hour boundaries',()=>{
  const jsp=fs.readFileSync(path.join(__dirname,'../src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp'),'utf8');
  const start=jsp.indexOf('function calculateEndTime() {'),end=jsp.indexOf('\n            function onNotBook()',start);
  assert.ok(start>=0&&end>start);
  const functionSource=jsp.slice(start,end).replace(/<fmt:message key="[^"]+"\/>/g,'message');
  for(const [time,duration] of [['08:55',15],['08:00',1],['08:00',60],['09:45',30],['23:45',15]]){
    const form=Object.fromEntries(Object.entries({start_time:time,duration:String(duration),end_time:'',keyword:'Fixture',demographic_no:'41',status:''}).map(([name,value])=>[name,{value}]));
    const context={document:{ADDAPPT:form},showJSAlert:()=>assert.fail('Native duration should be valid')};
    vm.runInNewContext(functionSource+'\ncalculateEndTime();',context);
    const link=LINK.replace('08%3A55',encodeURIComponent(time)).replace('duration=15',`duration=${duration}`);
    assert.equal(nativeSlot(link,BASE).end,form.end_time.value);
  }
});
for(const change of [link=>link.replace('provider_no=999997','provider_no=0'),link=>link.replace('month=10&day=21','month=2&day=30'),
  link=>link.replace('duration=15','duration=9999'),link=>link.replace('year=2026','year=2026&year=2027'),
  link=>link.replace('/carlos/appointment','https://foreign.invalid/carlos/appointment')])test('invalid or foreign native slot refuses before monitor SQL',()=>fixture(async f=>{
  const owned=f.begin();await assert.rejects(owned.rememberSlot(change(LINK)));assert.equal(f.state.queries.filter(q=>q.includes('PageMonitor')).length,0);
}));

test('real workflow registers cleanup and snapshots the exact session/slot before opening the first popup',()=>fixture(async f=>{
  const file=path.join(__dirname,'popup-opener-day-sheet-playwright-checks.js'),requireFrom=createRequire(file);
  const module={exports:{}};const source=fs.readFileSync(file,'utf8').replace(/^#![^\n]*\n/,'');
  const injected=name=>name==='./lib/playwright-ui'?{...requireFrom(name),typeAutocomplete:async()=> '41'}:requireFrom(name);
  vm.runInNewContext('(function(require,module,exports){'+source+'\n})', {URL,Date,BigInt,console})(injected,module,module.exports);
  const selected={getAttribute:async()=>LINK};const slots={count:async()=>1,last:()=>selected};
  f.s.schedule={locator:()=>slots};
  f.s.popup=async()=>{f.state.events.push('popup');f.monitor(11);return{locator:selector=>({
    waitFor:async()=>{},fill:async()=>{},evaluate:async fn=>fn({elements:{namedItem:name=>({value:{provider_no:'999997',appointment_date:'2026-10-21',start_time:'08:55',end_time:'09:09'}[name]})}}),
  })};};
  f.s.step=async(label,body)=>{if(label.startsWith('Add books'))throw new Error('stop after first popup');return body();};
  await assert.rejects(module.exports.workflow(f.s),/stop after first popup/);
  assert.equal(f.state.callbacks.length,1);assert.ok(f.state.events.indexOf('cookies')<f.state.events.indexOf('popup'));
  assert.ok(f.state.events.indexOf('monitor-snapshot-or-read')<f.state.events.indexOf('popup'));
  f.cleanup();assert.deepEqual(f.ids(),[]);assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM admission').get().n,0);
}));
