const {test}=require('node:test');
const assert=require('node:assert/strict');
const {prepareOwnedPostal,assertOwnedPostal,workflow}=require('./popup-opener-master-record-playwright-checks');
const MARK='FAKE-PW0123456789abcdef';
function fixture(){
 const state={owned:true,postal:null,uiPostal:'K1A0B1',cleanups:[],writes:[],reloads:0,store:true,reloadError:null,url:'https://127.0.0.1/carlos/demographic/demographiccontrol.jsp?demographic_no=3333'};
 const s={patient:'3333',marker:MARK,context:{browser:()=>({isConnected:()=>false})},cleanup(fn){state.cleanups.push(fn);},sql:{value(q){assert.ok(q.includes(`demographic_no=3333 AND last_name='${MARK}'`));return state.owned&&(!q.includes("postal='K1A0B1'")||state.postal==='K1A0B1')?'1':'0';},rows(q){if(q.includes('FROM demographicExtArchive')||q.includes('FROM demographiccust'))return [];throw Error('Unexpected support query');},execute(q){state.writes.push(q);assert.equal(q,`UPDATE demographic SET postal='K1A0B1' WHERE demographic_no=3333 AND last_name='${MARK}'`);if(state.store)state.postal='K1A0B1';}},master:{async reload(options){assert.deepEqual(options,{waitUntil:'domcontentloaded'});state.reloads++;if(state.reloadError)throw state.reloadError;},url(){return state.url;},locator(selector){assert.equal(selector,'input[name="postal"]');return {async inputValue(){return state.uiPostal;}};}}};
 return {s,state};
}
test('own fixture alone gets valid postal and the open record reloads',async()=>{const m=fixture();await prepareOwnedPostal(m.s);assert.equal(m.state.postal,'K1A0B1');assert.equal(m.state.writes.length,1);assert.equal(m.state.reloads,1);});
test('changed owner refuses before any write or reload',async()=>{const m=fixture();m.state.owned=false;await assert.rejects(prepareOwnedPostal(m.s),/ownership changed/);assert.equal(m.state.writes.length,0);assert.equal(m.state.reloads,0);});
test('silent postal update failure is detected before reload',async()=>{const m=fixture();m.state.store=false;await assert.rejects(prepareOwnedPostal(m.s),/not stored/);assert.equal(m.state.reloads,0);});
test('failed reload propagates before picker fixtures',async()=>{const m=fixture();m.state.reloadError=Error('reload boundary');await assert.rejects(workflow(m.s),/reload boundary/);assert.equal(m.state.writes.length,1);});
test('reload to another patient refuses',async()=>{const m=fixture();m.state.url='https://127.0.0.1/carlos/demographic/demographiccontrol.jsp?demographic_no=3334';await assert.rejects(prepareOwnedPostal(m.s),/another patient/);});
for(const patient of ['0','-1','2147483648','1 OR 1=1','01'])test(`postal fixture rejects identity ${patient}`,async()=>{const m=fixture();m.s.patient=patient;await assert.rejects(prepareOwnedPostal(m.s),/identity is invalid/);assert.equal(m.state.writes.length,0);assert.equal(m.state.reloads,0);});
test('invalid marker refuses before mutation',async()=>{const m=fixture();m.s.marker="other' OR 1=1";await assert.rejects(prepareOwnedPostal(m.s),/marker is invalid/);assert.equal(m.state.writes.length,0);});

test('read-only assertion refuses picker postal clobber without healing it',async()=>{const m=fixture();await prepareOwnedPostal(m.s);m.state.uiPostal='';await assert.rejects(assertOwnedPostal(m.s),/picker changed/);assert.equal(m.state.writes.length,1);assert.equal(m.state.uiPostal,'');});
test('read-only assertion refuses changed database postal without healing it',async()=>{const m=fixture();await prepareOwnedPostal(m.s);m.state.postal='';await assert.rejects(assertOwnedPostal(m.s),/fixture changed/);assert.equal(m.state.writes.length,1);assert.equal(m.state.postal,'');});
test('native support cleanup is registered before first fixture write',async()=>{const m=fixture();const execute=m.s.sql.execute;m.s.sql.execute=q=>{assert.equal(m.state.cleanups.length,1);execute(q);};m.state.reloadError=Error('reload boundary');await assert.rejects(workflow(m.s),/reload boundary/);assert.equal(m.state.writes.length,1);});
test('shared support cleanup rejects pre-existing support before postal update',async()=>{const m=fixture();m.s.sql.rows=q=>q.includes('demographicExtArchive')?[['99','3333']]:[];await assert.rejects(workflow(m.s),/pre-existing support/);assert.equal(m.state.writes.length,0);});
test('reload with invalid postal refuses before any picker operation',async()=>{const m=fixture();m.state.uiPostal='';await assert.rejects(prepareOwnedPostal(m.s),/picker changed/);assert.equal(m.state.reloads,1);assert.equal(m.state.writes.length,1);});
test('registered native support cleanup removes update archives/custom only for this fixture',async()=>{
 const m=fixture();m.state.reloadError=Error('reload boundary');await assert.rejects(workflow(m.s),/reload boundary/);
 let archives=[['71','3333']],custom=[['3333']];const foreign=[['72','4444']];
 m.s.sql.rows=q=>q.includes('demographicExtArchive')?archives:custom;
 m.s.sql.execute=q=>{assert.ok(q.includes('EXISTS (SELECT 1 FROM demographic WHERE demographic_no=3333'));
  assert.ok(q.includes(`last_name='${MARK}'`));
  if(q.includes('DELETE FROM demographicExtArchive')){assert.ok(q.includes('id IN (71) AND demographic_no=3333'));archives=[];}
  else{assert.ok(q.includes('DELETE FROM demographiccust'));assert.ok(q.includes('demographic_no=3333'));custom=[];}
 };
 m.state.cleanups[0]();assert.deepEqual(archives,[]);assert.deepEqual(custom,[]);assert.deepEqual(foreign,[['72','4444']]);
});
test('registered support cleanup reports a silent native child-delete failure',async()=>{
 const m=fixture();m.state.reloadError=Error('reload boundary');await assert.rejects(workflow(m.s),/reload boundary/);
 m.s.sql.rows=q=>q.includes('demographicExtArchive')?[['71','3333']]:[];m.s.sql.execute=()=>{};
 assert.throws(()=>m.state.cleanups[0](),/were not removed/);
});
