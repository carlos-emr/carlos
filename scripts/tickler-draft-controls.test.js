/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const add=fs.readFileSync(path.join(__dirname,'../src/main/webapp/WEB-INF/jsp/tickler/ticklerAdd.jsp'),'utf8');
test('draft POST fields cannot shadow visible date and priority controls',()=>{
 assert.equal([...add.matchAll(/<(?:input|select)\b[^>]*\bname="xml_appointment_date"/g)].length,1);
 assert.equal([...add.matchAll(/<(?:input|select)\b[^>]*\bname="priority"/g)].length,1);
 assert.match(add, /name="ticklerDraftDate"/);
 assert.match(add, /name="ticklerDraftPriority"/);
});

test('Add priority values remain stable across translated draft round trips',()=>{
 for(const value of ['High','Normal','Low'])assert.match(add,new RegExp('<option value="'+value+'"'));
 assert.doesNotMatch(add, /<option value="<carlos:encode value='<%= oscarBundle.getString\("tickler\.ticklerMain\.priority/);
});

const vm=require('node:vm');
const prepare=add.match(/function prepareTicklerPatientSearch\(searchForm\) \{[\s\S]*?\n        \}/)[0];
const fieldNames=['ticklerMessage','xml_appointment_date','priority'];
const hiddenNames=['ticklerMessage','ticklerDraftDate','ticklerDraftPriority'];
function searchForms(missingSource,missingDestination){
 const source=Object.fromEntries(fieldNames.map((name,index)=>[name,{value:['Draft "quoted" & café\nsecond line','2031-01-31','High'][index]}]));
 const destination=Object.fromEntries(hiddenNames.map(name=>[name,{value:'stale '+name}]));
 if(missingSource)delete source[missingSource];
 if(missingDestination)delete destination[missingDestination];
 const messages=[];
 const context={document:{forms:{namedItem:()=>({elements:{namedItem:name=>source[name]}})}},
  CarlosTicklerValidation:{show:message=>messages.push(message)},i18nDraftSearchUnavailable:'Keep the draft before reloading'};
 vm.createContext(context);vm.runInContext(prepare,context);
 return {source,destination,messages,submit:()=>context.prepareTicklerPatientSearch({elements:{namedItem:name=>destination[name]}})};
}
test('native patient search copies the complete visible draft exactly before permitting submission',()=>{
 const f=searchForms();assert.equal(f.submit(),true);
 assert.deepEqual(hiddenNames.map(name=>f.destination[name].value),fieldNames.map(name=>f.source[name].value));
 assert.deepEqual(f.messages,[]);
 assert.match(add,/onsubmit="return prepareTicklerPatientSearch\(this\);"/);
});
for(const [kind,names] of [['source',fieldNames],['destination',hiddenNames]])for(const name of names){
 test(`missing ${kind} ${name} prevents search, preserves every control and gives visible guidance`,()=>{
  const f=searchForms(kind==='source'?name:undefined,kind==='destination'?name:undefined);
  const before=JSON.stringify([f.source,f.destination]);
  assert.equal(f.submit(),false);assert.equal(JSON.stringify([f.source,f.destination]),before);
  assert.deepEqual(f.messages,['Keep the draft before reloading']);
 });
}
