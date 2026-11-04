const {test}=require('node:test');
const assert=require('node:assert/strict');
const {installOwnedFormCleanup,workflow}=require('./popup-opener-echart-forms-playwright-checks');
const MARK='FAKE-PW0123456789abcdef';
function model({patient='3333',oldIds=[],existingParents=[],connected=false}={}) {
 const state={parents:[...existingParents],booleans:[...oldIds.map(id=>({id:String(id),name:'formRourke2017'})),{id:'202',name:'formRourke2017'},{id:'21',name:'formRourke2020'}],owner:true,connected,callbacks:[],writes:[],silentChild:false,silentParent:false,afterChild:null,afterParent:null};
 const ids=q=>q.match(/(?:f\.ID|form_id) IN \(([^)]*)\)/)?.[1].split(',')||[];
 const own=q=>q.includes(`f.demographic_no=${patient}`)&&q.includes(`d.last_name='${MARK}'`)&&q.includes('INNER JOIN demographic d');
 const sql={value(q){
  if(q.includes('FROM demographic WHERE demographic_no='))return state.owner?'1':'0';
  if(q.includes('FROM formRourke2017 WHERE demographic_no='))return String(state.parents.length);
  if(q.includes('FROM form_boolean_value'))return String(state.booleans.filter(b=>b.name==='formRourke2017'&&ids(q).includes(b.id)).length);
  throw Error('Unexpected scalar query');
 },rows(q){
  if(q.includes('SELECT DISTINCT form_id'))return [...new Set(state.booleans.filter(b=>b.name==='formRourke2017').map(b=>b.id))].map(x=>[x]);
  if(q.includes('SELECT ID FROM formRourke2017'))return state.parents.map(x=>[x]);
  throw Error('Unexpected rows query');
 },execute(q){
  state.writes.push(q);if(q.startsWith('DELETE FROM formRourke2017 WHERE demographic_no=')){state.parents=[];return;}assert.ok(own(q),'Every mutation must retain parent-patient-marker ownership');assert.ok(/f\.ID IN \([1-9][\d,]*\)/.test(q),'Every mutation must retain exact positive IDs');
  if(q.startsWith('DELETE b')){
   assert.ok(q.includes("b.form_name='formRourke2017'"));assert.ok(q.includes('INNER JOIN formRourke2017 f ON f.ID=b.form_id'));
   if(!state.silentChild&&state.owner)state.booleans=state.booleans.filter(b=>!(b.name==='formRourke2017'&&ids(q).includes(b.id)&&state.parents.includes(b.id)));
   if(state.afterChild)state.afterChild();
  }else if(q.startsWith('DELETE f')){
   if(!state.silentParent&&state.owner)state.parents=state.parents.filter(x=>!ids(q).includes(x));
   if(state.afterParent)state.afterParent();
  }else throw Error('Unexpected mutation');
 }};
 const s={sql,patient,marker:MARK,context:{on(){},browser:()=>({isConnected:()=>state.connected})},cleanup(fn){state.callbacks.push(fn);}};
 return {s,state,begin(){installOwnedFormCleanup(s);},cleanup(){assert.equal(state.callbacks.length,1);state.callbacks[0]();}};
}
test('actual workflow registers scoped cleanup before first chart or form write',async()=>{
 const m=model();m.s.chart=async()=>{throw Error('chart boundary');};
 await assert.rejects(workflow(m.s),/chart boundary/);assert.equal(m.state.callbacks.length,1);
 m.state.parents=['21'];m.state.booleans.push({id:'21',name:'formRourke2017'});m.cleanup();
 assert.deepEqual(m.state.parents,[]);assert.equal(m.state.booleans.filter(x=>x.name==='formRourke2017'&&x.id==='21').length,0);
});
test('children removed before parents, foreign forms and same ID other form preserved',()=>{
 const m=model();m.begin();m.state.parents=['21'];m.state.booleans.push({id:'21',name:'formRourke2017'});m.cleanup();
 assert.equal(m.state.writes.length,2);assert.match(m.state.writes[0],/^DELETE b/);assert.match(m.state.writes[1],/^DELETE f/);
 assert.deepEqual(m.state.booleans,[{id:'202',name:'formRourke2017'},{id:'21',name:'formRourke2020'}]);
});
test('all owned autosaved forms receive scoped child cleanup',()=>{
 const m=model();m.begin();m.state.parents=['21','22'];for(const id of m.state.parents)m.state.booleans.push({id,name:'formRourke2017'});m.cleanup();assert.deepEqual(m.state.parents,[]);assert.equal(m.state.booleans.length,2);
});
test('failure before save leaves zero forms without delete',()=>{const m=model();m.begin();m.cleanup();assert.deepEqual(m.state.writes,[]);});
test('browser still connected retains children and parents',()=>{const m=model({connected:true});m.begin();m.state.parents=['21'];assert.throws(()=>m.cleanup(),/browser closure/);assert.deepEqual(m.state.writes,[]);});
test('missing browser identity refuses cleanup',()=>{const m=model();m.begin();m.s.context.browser=()=>null;assert.throws(()=>m.cleanup(),/browser closure/);assert.deepEqual(m.state.writes,[]);});
test('changed fixture owner before cleanup retains all forms',()=>{const m=model();m.begin();m.state.parents=['21'];m.state.owner=false;assert.throws(()=>m.cleanup(),/ownership changed/);assert.deepEqual(m.state.writes,[]);});
test('pre-existing owned-parent rows refuse before registration',()=>{const m=model({existingParents:['21']});assert.throws(()=>m.begin(),/already has/);assert.deepEqual(m.state.writes,[]);});
test('pre-existing orphan boolean key refuses without deleting it',()=>{const m=model({oldIds:['21']});m.begin();m.state.parents=['21'];assert.throws(()=>m.cleanup(),/Pre-existing/);assert.deepEqual(m.state.writes,[]);});
test('silent child DELETE failure retains parent and reports failure',()=>{const m=model();m.begin();m.state.parents=['21'];m.state.booleans.push({id:'21',name:'formRourke2017'});m.state.silentChild=true;assert.throws(()=>m.cleanup(),/boolean rows were not removed/);assert.deepEqual(m.state.parents,['21']);assert.equal(m.state.writes.length,1);});
test('silent parent DELETE failure is reported',()=>{const m=model();m.begin();m.state.parents=['21'];m.state.silentParent=true;assert.throws(()=>m.cleanup(),/remain/);assert.deepEqual(m.state.parents,['21']);});
test('new boolean row after parent removal is reported',()=>{const m=model();m.begin();m.state.parents=['21'];m.state.afterParent=()=>m.state.booleans.push({id:'21',name:'formRourke2017'});assert.throws(()=>m.cleanup(),/remain/);});
test('new parent outside captured IDs is retained and reported',()=>{const m=model();m.begin();m.state.parents=['21'];m.state.afterChild=()=>m.state.parents.push('22');assert.throws(()=>m.cleanup(),/remain/);assert.deepEqual(m.state.parents,['22']);});
test('ownership change after child removal refuses parent deletion',()=>{const m=model();m.begin();m.state.parents=['21'];m.state.afterChild=()=>m.state.owner=false;assert.throws(()=>m.cleanup(),/ownership changed/);assert.equal(m.state.writes.length,1);assert.deepEqual(m.state.parents,['21']);});
for(const patient of ['0','-1','2147483648','1 OR 1=1','01'])test(`invalid patient ${patient} refuses before writes`,()=>{const m=model({patient});assert.throws(()=>m.begin(),/identity is invalid/);assert.deepEqual(m.state.writes,[]);});
test('full maximum signed integer parent is accepted',()=>{const m=model();m.begin();m.state.parents=['2147483647'];m.cleanup();assert.deepEqual(m.state.parents,[]);});
for(const id of ['0','-1','2147483648','1 OR 1=1'])test(`invalid form identity ${id} retains rows`,()=>{const m=model();m.begin();m.state.parents=[id];assert.throws(()=>m.cleanup(),/identity is invalid/);assert.deepEqual(m.state.writes,[]);});
test('duplicate parent identity evidence refuses',()=>{const m=model();m.begin();m.state.parents=['21','21'];assert.throws(()=>m.cleanup(),/duplicated/);assert.deepEqual(m.state.writes,[]);});
