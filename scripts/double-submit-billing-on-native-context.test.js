/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {DatabaseSync} = require('node:sqlite');
const REPO = process.env.DOUBLE_SUBMIT_BILLING_REPO_ROOT || path.resolve(__dirname, '..');
const CALLERS = process.env.DOUBLE_SUBMIT_BILLING_CALLER_ROOT || __dirname;
const SCRIPT = path.join(CALLERS, 'double-submit-billing-on-playwright-checks.js');
const MARKER = 'FAKE-PW0123456789abcdef';
const STOP = 'STOP_AFTER_OWNED_NATIVE_BILL_LINK';
const q = value => `'${String(value).replaceAll("'", "''")}'`;
function load(file, imports, env = {}) {
  const module = {exports:{}};
  const require = name => {
    if (Object.hasOwn(imports,name)) return imports[name];
    throw Error('Unexpected unit import '+name);
  };
  require.main = null;
  vm.runInNewContext(fs.readFileSync(file,'utf8'), {module,exports:module.exports,require,
    process:{env},Date,URL,URLSearchParams,console,Buffer}, {filename:file});
  return module.exports;
}
function fixture(body, options = {}) {
  const db = new DatabaseSync(':memory:');
  db.function('NOW',()=> '2026-10-21 04:00:00');
  db.function('ADDTIME',(time,delta)=>{assert.equal(delta,'00:14:00');return time.replace(':00:00',':14:00');});
  db.exec(`CREATE TABLE demographic(demographic_no INTEGER PRIMARY KEY,last_name TEXT,provider_no TEXT,
    hin TEXT,ver TEXT,hc_type TEXT,hc_renew_date TEXT,date_joined TEXT);
    INSERT INTO demographic VALUES(41,'${MARKER}','999998','','','','','');
    CREATE TABLE site(site_id INTEGER PRIMARY KEY,name TEXT,status INTEGER);
    INSERT INTO site VALUES(1,'West Clinic',1),(2,'East Clinic',1);
    CREATE TABLE providersite(site_id INTEGER,provider_no TEXT);
    INSERT INTO providersite VALUES(1,'999998'),(2,'999998');
    CREATE TABLE appointment(appointment_no INTEGER PRIMARY KEY,provider_no TEXT,appointment_date TEXT,
    start_time TEXT,end_time TEXT,name TEXT,demographic_no INTEGER,program_id INTEGER,notes TEXT,reason TEXT,
    location TEXT,resources TEXT,type TEXT,style TEXT,billing TEXT,status TEXT,createdatetime TEXT,
    updatedatetime TEXT,creator TEXT,remarks TEXT,urgency TEXT);
    CREATE TABLE billing_on_cheader1(id INTEGER PRIMARY KEY,appointment_no INTEGER,demographic_no INTEGER);`);
  if (options.unauthorized) db.exec('DELETE FROM providersite');
  const state = {queries:[],writes:[],callbacks:[],goes:[],popup:false,billLocators:[],connected:false,
    current:options.current || 'West Clinic', options:options.sites || ['West Clinic','East Clinic'],
    multisites:options.multisites!==false,date:'2026-10-21',actor:options.actor || '999998',
    visible:options.visible || '999998'};
  const sql = {
    rows(query) {state.queries.push(query);return db.prepare(query).all().map(r=>Object.values(r).map(String));},
    value(query) {
      if(query.includes('; SELECT LAST_INSERT_ID()')) {
        sql.execute(query.replace('; SELECT LAST_INSERT_ID()',''));
        return String(db.prepare('SELECT last_insert_rowid() AS id').get().id);
      }
      return sql.rows(query)[0]?.[0] || '';
    },
    execute(query) {state.writes.push(query);db.exec(query);},
  };
  const h={assert:(v,m)=>assert.ok(v,m),sqlString:q,SkipCheck:class extends Error{},
    async gotoApp(page,base,route) {
      assert.equal(page,schedule);const url=new URL(route,String(base));state.url=url.href;state.goes.push(url.href);
      const p=url.searchParams;state.date=options.displayedDate || `${p.get('year')}-${p.get('month').padStart(2,'0')}-${p.get('day').padStart(2,'0')}`;
      if(state.goes.length>1 && options.reloadSite)state.current=options.reloadSite;
    }};
  const helper=load(path.join(REPO,'scripts/lib/native-schedule-context.js'),{'./playwright-harness':h});
  const jsp=fs.readFileSync(path.join(REPO,'src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp'),'utf8');
  assert.ok(jsp.includes('String sitename = appointment.getLocation() != null ? appointment.getLocation().trim() : "";'));
  assert.ok(jsp.includes('if (!bMultisites || (selectedSite == null && CurrentSiteMap.get(sitename) != null) || sitename.equals(selectedSite))'));
  function visible(row) {
    const site=String(row.location || '').trim();const selected=state.current==='none'?null:state.current;
    return !state.multisites || (selected===null && ['West Clinic','East Clinic'].includes(site)) || site===selected;
  }
  const schedule={url:()=>state.url || 'https://127.0.0.1/carlos/provider/providercontrol?displaymode=day',
    waitForLoadState:async()=>{},locator(selector) {
      const field={'#scheduleLoggedInProviderNo':'actor','#scheduleCurrentDate':'date','#scheduleVisibleProviderNos':'visible'}[selector];
      if(field)return{inputValue:async()=>state[field]};
      if(selector==='select#site[name="site"]')return{count:async()=>Number(state.multisites),
        inputValue:async()=>state.current,locator:()=>({evaluateAll:async()=>state.options.map(value=>({value,disabled:false}))}),
        selectOption:()=>assert.fail('Native site selection must not be changed')};
      assert.match(selector,/^a\[onclick\*="\/billing\?billRegion"\]\[onclick\*="appointment_no=\d+&"\]$/);
      state.billLocators.push(selector);const id=Number(selector.match(/appointment_no=(\d+)&/)[1]);
      const locator={first:()=>locator,waitFor:async()=>{
        const row=db.prepare('SELECT * FROM appointment WHERE appointment_no=?').get(id);
        assert.ok(row && row.demographic_no===41 && row.provider_no==='999998','The locator lost owned appointment context');
        assert.ok(visible(row),'NATIVE_APPOINTMENT_HIDDEN_BY_SITE_PREDICATE');
      }};return locator;
    }};
  const browser={isConnected:()=>state.connected};
  const s={sql,patient:'41',marker:MARKER,provider:'999998',schedule,config:{baseUrl:new URL('https://127.0.0.1/carlos')},
    context:{browser:()=>browser},cleanup:fn=>state.callbacks.push(fn),step:async(label,fn)=>fn(),
    popup:async(page,locator,label)=>{assert.equal(page,schedule);assert.equal(label,'bill-form');state.popup=true;throw Error(STOP);}};
  const env=options.billDate?{BILLING_SUBMIT_DATE:options.billDate}:{};
  const subject=load(SCRIPT,{'./lib/playwright-harness':h,'./lib/playwright-ui':{},'./lib/workflow-session':{runWorkflow(){}},
    './lib/native-schedule-context':helper,'./lib/double-submit-helpers':{MODES_REPLAY:[{key:'dblclick',label:'dblclick()',tag:'dblclick'}],
      verdicts:()=>({record(){},finish(){}})}},env);
  const f={db,state,s,subject,visible,appointments:()=>db.prepare('SELECT * FROM appointment ORDER BY appointment_no').all()};
  try{return Promise.resolve(body(f)).finally(()=>db.close());}catch(e){db.close();throw e;}
}
async function openOwnedLink(f) {await assert.rejects(f.subject.workflow(f.s),new RegExp(STOP));assert.equal(f.state.popup,true);}

test('actual caller INSERT uses the displayed authorized named site so the native JSP exposes its exact owned billing link',()=>fixture(async f=>{
  await openOwnedLink(f);const [a]=f.appointments();assert.equal(a.location,'West Clinic');assert.equal(a.appointment_date,'2024-05-06');
  assert.equal(a.provider_no,'999998');assert.equal(a.demographic_no,41);assert.equal(a.name,`${MARKER},Workflow`);
  assert.equal(a.reason,`${MARKER}-dblclick bill`);assert.equal(a.notes,a.reason);assert.ok(f.visible(a));
  assert.equal(f.state.billLocators[0],`a[onclick*="/billing?billRegion"][onclick*="appointment_no=${a.appointment_no}&"]`);
}));
test('an already displayed All Clinics view uses a real authorized site without changing the view',()=>fixture(async f=>{
  await openOwnedLink(f);assert.equal(f.state.current,'none');assert.equal(f.appointments()[0].location,'West Clinic');
},{current:'none'}));
test('a displayed East Clinic remains East and never switches to another offered clinic',()=>fixture(async f=>{
  await openOwnedLink(f);assert.equal(f.state.current,'East Clinic');assert.equal(f.appointments()[0].location,'East Clinic');
},{current:'East Clinic'}));
test('legacy nonmultisite view keeps its blank location and exact appointment link',()=>fixture(async f=>{
  await openOwnedLink(f);assert.equal(f.appointments()[0].location,'');assert.ok(f.visible(f.appointments()[0]));
},{multisites:false}));
test('explicit historical billing date override is retained rather than using today',()=>fixture(async f=>{
  await openOwnedLink(f);assert.equal(f.appointments()[0].appointment_date,'2023-08-09');
},{billDate:'2023-08-09'}));
for(const [name,options]of[
  ['foreign visible provider',{actor:'999997',visible:'999997'}],
  ['unauthorized site membership',{unauthorized:true}],
  ['padded clinic name',{current:' West Clinic '}],
  ['unoffered clinic',{current:'Unknown Clinic'}],
  ['missing named site',{current:'none',sites:['none']}],
  ['wrong displayed billing day',{displayedDate:'2024-05-07'}],
])test(`${name} refuses before any patient or appointment write`,()=>fixture(async f=>{
  await assert.rejects(f.subject.workflow(f.s));assert.equal(f.state.writes.length,0);assert.equal(f.appointments().length,0);assert.equal(f.state.popup,false);
},options));
test('changed site after appointment creation fails instead of loosening the locator or changing preferences',()=>fixture(async f=>{
  await assert.rejects(f.subject.workflow(f.s),/native schedule context changed/);assert.equal(f.appointments().length,1);
  assert.equal(f.appointments()[0].location,'West Clinic');assert.equal(f.state.current,'East Clinic');assert.equal(f.state.popup,false);assert.deepEqual(f.state.billLocators,[]);
},{reloadSite:'East Clinic'}));
test('native context reads precede fixture writes and do not mutate memberships or preferences',()=>fixture(async f=>{
  await openOwnedLink(f);assert.equal(f.state.queries.filter(q=>q.includes('COUNT(DISTINCT site.site_id)')).length,3);
  assert.ok(f.state.writes.every(q=>!/\b(?:site|providersite|userProperty|user_properties)\b/.test(q)));
}));
test('failed browser closure retains owned billing appointments and prevents all DELETEs',()=>fixture(async f=>{
  await openOwnedLink(f);f.state.connected=true;assert.throws(()=>f.state.callbacks[0](),/browser closure/);
  assert.equal(f.appointments().length,1);assert.equal(f.state.writes.filter(q=>q.startsWith('DELETE')).length,0);
},{multisites:false}));
test('changed live patient marker retains billing appointment evidence',()=>fixture(async f=>{
  await openOwnedLink(f);f.db.exec("UPDATE demographic SET last_name='FOREIGN' WHERE demographic_no=41");
  assert.throws(()=>f.state.callbacks[0](),/patient identity changed/);assert.equal(f.appointments().length,1);
},{multisites:false}));
test('confirmed closed browser cleanup still removes the exact appointment and preserves a foreign row',()=>fixture(async f=>{
  await openOwnedLink(f);f.db.exec("INSERT INTO appointment(appointment_no,provider_no,demographic_no,location) VALUES(99,'999997',42,'East Clinic')");
  f.state.callbacks[0]();assert.equal(f.appointments().length,1);assert.equal(f.appointments()[0].demographic_no,42);
},{multisites:false}));
