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
const prepareMatch=add.match(/(^[ \t]*)function prepareTicklerPatientSearch\(searchForm\) \{[\s\S]*?^\1\}/m);
assert.ok(prepareMatch,'Missing complete prepareTicklerPatientSearch function in Add Tickler JSP');
const prepare=prepareMatch[0];
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

function recipientForm({value='100',disabled=true}={}) {
 const taskTo={value,disabled},listeners={};
 const assignee={value:'100',set innerHTML(html){this.html=html;this.value='';}};
 const form={task_assigned_to:assignee,addEventListener:(name,listener)=>{listeners[name]=listener;}};
 const context={document:{ADDAPPT:{keyword:{focus(){},select(){}},elements:{taskTo}},serviceform:form},
  _providers:{Alpha:'<option value="100">A</option>',Beta:'<option value="200">B</option>'},
  focus(){},addQuickPick(){}};
 vm.createContext(context);
 for(const name of ['rememberTicklerRecipient','initTicklerAdd','changeSite']) {
  const match=add.match(new RegExp('(^[ \\t]*)function '+name+'\\([^)]*\\) \\{[\\s\\S]*?^\\1\\}', 'm'));
  // Keep the behavioral regression runnable against the preceding JSP as a negative control.
  if(!match&&name==='rememberTicklerRecipient')continue;
  assert.ok(match,`Missing complete ${name} function`);vm.runInContext(match[0],context);
 }
 context.initTicklerAdd();
 const clinic={value:'Beta',form,style:{},options:[{style:{backgroundColor:'white'}}],selectedIndex:0};
 return {taskTo,assignee,change:userInitiated=>context.changeSite(clinic,userInitiated),
  choose:value=>{assignee.value=value;listeners.change({target:{name:'task_assigned_to',value}});}};
}

test('initial clinic rendering leaves automatic MRP transport disabled',()=>{
 const f=recipientForm();f.change();assert.equal(f.taskTo.disabled,true);
});

test('a user clinic change transports the cleared recipient instead of a stale deliberate provider',()=>{
 const f=recipientForm();f.choose('100');f.change(true);
 assert.equal(f.assignee.value,'');assert.deepEqual(f.taskTo,{value:'',disabled:false});
 assert.match(add,/onchange="changeSite\(this, true\)"/);
});

test('a deliberate recipient after changing clinics replaces the transported clear',()=>{
 const f=recipientForm();f.choose('100');f.change(true);f.choose('200');
 assert.deepEqual(f.taskTo,{value:'200',disabled:false});
});

test('rendering a restored deliberate recipient does not clear its transport',()=>{
 const f=recipientForm({value:'200',disabled:false});f.change();
 assert.deepEqual(f.taskTo,{value:'200',disabled:false});
});
