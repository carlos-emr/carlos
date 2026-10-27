/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {DatabaseSync}=require('node:sqlite');
const root=process.env.APPOINTMENT_TEXT_SOURCE_ROOT||path.resolve(__dirname,'..');
const repo=process.env.APPOINTMENT_TEXT_REPO_ROOT||root;
const bounds=require(path.join(root,'src/main/webapp/js/appointmentTextBounds.js'));
const fields=[['keyword',255],['reason',80],['notes',32768],['resources',255]];
const payload=n=>{const prefix="O'Bé中文😀&amp;\"\\%41+;";return prefix+'x'.repeat(n-Array.from(prefix).length-1)+'Z';};
function form(mode='ADDAPPT',overrides={},originals={}){
 const messages=[],focused=[];const controls=Object.fromEntries(fields.map(([name])=>[name,{value:overrides[name]??'short',dataset:Object.hasOwn(originals,name)?{appointmentOriginal:originals[name]}:{},focus(){focused.push(name);}}]));
 Object.assign(controls,{demographic_no:{value:''},displaymode:{value:''},printReceipt:{value:''}});
 const state={name:mode,dataset:{appointmentTooLong:'{0} exceeds maximum length {1}; entries kept',appointmentInvalid:'{0} has invalid Unicode; entries kept',appointmentName:'Name',appointmentReason:'Reason',appointmentNotes:'Notes',appointmentResources:'Resources'},elements:{namedItem:name=>controls[name]},...controls};
 return {state,controls,messages,focused,validate:()=>bounds.validate(state,text=>messages.push(text))};
}
for(const mode of ['ADDAPPT','EDITAPPT'])for(const [field,max] of fields){
 test(`${mode} accepts exactly ${max} UTF8 codepoints in ${field} without changing bytes`,()=>{
  const value=payload(max),f=form(mode,{[field]:value});assert.equal(f.validate(),true);assert.equal(f.controls[field].value,value);assert.equal(f.messages.length,0);
 });
 test(`${mode} refuses the next UTF8 codepoint in ${field} visibly and retains every input`,()=>{
  const value=payload(max+1),f=form(mode,{[field]:value});const before=JSON.stringify(Object.fromEntries(fields.map(([key])=>[key,f.controls[key].value])));
  assert.equal(f.validate(),false);assert.equal(f.messages.length,1);assert.match(f.messages[0],new RegExp(`maximum length ${max}`));assert.deepEqual(f.focused,[field]);
  assert.equal(JSON.stringify(Object.fromEntries(fields.map(([key])=>[key,f.controls[key].value]))),before);
 });
}
test('supplementary characters are counted as codepoints rather than UTF16 code units',()=>{
 for(const [field,max] of fields){const f=form('ADDAPPT',{[field]:'😀'.repeat(max)});assert.equal(f.validate(),true);f.controls[field].value+='😀';assert.equal(f.validate(),false);}
});
test('logical CRLF and lone CR textarea breaks count once without rewriting the visible input',()=>{
 for(const [field,max] of fields.filter(([name])=>['reason','notes'].includes(name))){for(const line of ['\r\n','\r','\n']){
  const value='X'.repeat(max-2)+line+'Z',f=form('ADDAPPT',{[field]:value});assert.equal(f.validate(),true);assert.equal(f.controls[field].value,value);
 }}
});
test('unchanged legacy edit values remain intact even above current widths and with CRLF',()=>{
 for(const [field,max] of fields){const original='L'.repeat(max+5)+(field==='reason'||field==='notes'?'\r\nZ':'');
  const f=form('EDITAPPT',{[field]:original.replace(/\r\n/g,'\n')},{[field]:original});assert.equal(f.validate(),true);}
});
test('a retry cannot treat its rejected default value as a saved legacy exception',()=>{
 const value='R'.repeat(256),f=form('EDITAPPT',{resources:value},{resources:'previous'});f.controls.resources.defaultValue=value;
 assert.equal(f.validate(),false);assert.equal(f.controls.resources.value,value);
});
test('an add never exempts an oversized value because of a legacy data attribute',()=>{
 const value='R'.repeat(256),f=form('ADDAPPT',{resources:value},{resources:value});assert.equal(f.validate(),false);
});
test('the Add patient search display is resolved by the server while edit keyword remains validated literally',()=>{
 const add=form('ADDAPPT',{keyword:'N'.repeat(300)});add.controls.demographic_no.value='41';assert.equal(add.validate(),true);
 const edit=form('EDITAPPT',{keyword:'N'.repeat(300)});edit.controls.demographic_no.value='41';assert.equal(edit.validate(),false);
});
for(const [field] of fields)test(`invalid Unicode in ${field} is explicit and remains available for correction`,()=>{
 for(const bad of ['\uD800','\uDC00']){const f=form('ADDAPPT',{[field]:'A'+bad+'Z'});assert.equal(f.validate(),false);assert.match(f.messages[0],/invalid Unicode/);assert.equal(f.controls[field].value,'A'+bad+'Z');}
});
const readJsp=name=>fs.readFileSync(path.join(process.env.APPOINTMENT_TEXT_JSP_ROOT||root,'src/main/webapp/WEB-INF/jsp/appointment',name),'utf8');
function handler(name,start,end,extra={}){
 const source=readJsp(name);const from=source.indexOf(`function ${start}(`),to=source.indexOf(`function ${end}(`,from);
 assert(from>=0&&to>from);const context={...extra,CarlosAppointmentTextBounds:bounds};
 vm.runInNewContext(source.slice(from,to).replace(/<fmt:message key="[^"]+"\s*\/>/g,'Confirm'),context);
 return context[start];
}
test('native Add button refuses oversize visibly then accepts the corrected same form without clearing its state',()=>{
 const f=form('ADDAPPT',{resources:'R'.repeat(256)});let times=0;
 const onAdd=handler('addappointment.jsp','onAdd','setfocus',{document:{ADDAPPT:f.state},showJSAlert:text=>f.messages.push(text),calculateEndTime:()=>{times++;return true;}});
 f.controls.displaymode.value='Add Appointment';assert.equal(onAdd(),false);assert.equal(times,0);assert.equal(f.controls.resources.value,'R'.repeat(256));
 f.controls.resources.value='corrected';assert.equal(onAdd(),true);assert.equal(times,1);
});
test('native Add search and group paths preserve their existing long search-input behavior',()=>{
 for(const mode of ['Search ','Group Appt']){const f=form('ADDAPPT',{keyword:'N'.repeat(500)});f.controls.displaymode.value=mode;
  const onAdd=handler('addappointment.jsp','onAdd','setfocus',{document:{ADDAPPT:f.state},showJSAlert:()=>assert.fail('Search must not validate a persisted name'),calculateEndTime:()=>true});assert.equal(onAdd(),true);}
});
test('native edit receipt does not open or mutate inputs on refusal and corrected retry reserves exactly one receipt',()=>{
 const f=form('EDITAPPT',{notes:'N'.repeat(32769)});let receipt=0,times=0;f.controls.printReceipt.value='1';
 const onSub=handler('editappointment.jsp','onSub','calculateEndTime',{document:{EDITAPPT:f.state},showJSAlert:text=>f.messages.push(text),calculateEndTime:()=>{times++;return true;},reserveAppointmentReceiptWindow:()=>{receipt++;},saveTemp:2});
 assert.equal(onSub(),false);assert.equal(receipt,0);assert.equal(times,0);assert.equal(f.controls.printReceipt.value,'');assert.equal(f.controls.notes.value,'N'.repeat(32769));
 f.controls.notes.value='fixed';f.controls.printReceipt.value='1';assert.equal(onSub(),true);assert.equal(receipt,1);assert.equal(times,1);
});
test('native edit delete confirmation bypasses text validation and retains accept/cancel behavior',()=>{
 for(const accept of [true,false]){const f=form('EDITAPPT',{notes:'N'.repeat(300)});f.state.status={value:'B'};let prompts=0;
  const onSub=handler('editappointment.jsp','onSub','calculateEndTime',{document:{EDITAPPT:f.state},showJSAlert:()=>assert.fail('Delete must not change text'),calculateEndTime:()=>assert.fail('Delete must not change time'),saveTemp:1,confirm:()=>{prompts++;return accept;}});
  assert.equal(onSub(),accept);assert.equal(prompts,1);}
});
test('both native error forms display an encoded localized refusal and bind their four submitted fields',()=>{
 for(const file of ['addappointment.jsp','editappointment.jsp']){const jsp=readJsp(file);assert(jsp.includes('if (appointmentTextRetry) bFirstDisp = false;'));
  assert(jsp.includes('appointmentTextRetry ? "" : "display:none"'));assert(jsp.includes('<carlos:encode value="${appointmentTextMessage}"/>'));
  assert(jsp.includes('/js/appointmentTextBounds.js'));for(const [name] of fields)assert(new RegExp(`name="${name}"`).test(jsp));
  for(const key of ['too-long','invalid','name','reason','notes','resources'])assert(jsp.includes('data-appointment-'+key+'='));
 }
 const edit=readJsp('editappointment.jsp');for(const getter of ['Name','Reason','Notes','Resources'])assert(edit.includes(`StringUtils.defaultString(apptFromRequest.get${getter}())`));
});
test('the actual edit retry keyword expression preserves all submitted characters ahead of legacy first-display values',()=>{
 const jsp=readJsp('editappointment.jsp'),start=jsp.indexOf('name="keyword"'),tag=jsp.slice(start,jsp.indexOf('placeholder=',start));
 const expr=tag.match(/\bvalue="<carlos:encode value='<%= (.*?) %>' context="htmlAttribute"\/>"/s)?.[1];assert(expr);
 const typed="O'Bé中文😀<script>&\\%41+;";
 const result=vm.runInNewContext(expr,{appointmentTextRetry:true,bFirstDisp:true,StringUtils:{defaultString:value=>value??''},request:{getParameter:key=>key==='keyword'?typed:'wrong-legacy-name'},nameSb:{toString:()=> 'saved-name'}});
 assert.equal(result,typed);
});
test('the actual Add retry branch preserves a free-text keyword and the same submission token',()=>{
 const jsp=readJsp('addappointment.jsp');const branch=jsp.match(/if \(appointmentTextRetry\) \{\s*(name = StringUtils\.defaultString\(request\.getParameter\("keyword"\)\);)\s*\} else if/);assert(branch);
 const context={name:'',StringUtils:{defaultString:x=>x??''},request:{getParameter:()=>"N'Bé<>&😀"}};vm.runInNewContext(branch[1],context);assert.equal(context.name,"N'Bé<>&😀");
 assert(jsp.includes('(appointmentTextRetry && request.getParameter(FormSubmissionGuard.PARAMETER) != null) ? request.getParameter(FormSubmissionGuard.PARAMETER) : FormSubmissionGuard.issue'));
});
test('Struts INPUT maps only the native add/update retry views and keeps the success destinations',()=>{
 const xml=fs.readFileSync(path.join(root,'src/main/webapp/WEB-INF/classes/struts-scheduling.xml'),'utf8');
 for(const [action,view,success] of [['AddRecord','addappointment','appointmentaddarecord'],['UpdateRecord','editappointment','appointmentupdatearecord']]){
  const block=xml.match(new RegExp(`<action name="appointment/${action}"[^>]*>([\\s\\S]*?)</action>`))?.[1];assert(block);assert(block.includes(`<result name="input">/WEB-INF/jsp/appointment/${view}.jsp</result>`));assert(block.includes(`<result name="success">/WEB-INF/jsp/appointment/${success}.jsp</result>`));
 }
});
function properties(text){return Object.fromEntries(text.split(/\r?\n/).filter(line=>line&&!line.startsWith('#')).map(line=>{const i=line.indexOf('=');return[line.slice(0,i),line.slice(i+1).replace(/\\u([0-9a-fA-F]{4})/g,(_,x)=>String.fromCharCode(parseInt(x,16)))];}));}
test('every shipped locale contains complete plain validation messages with the same parameter contract',()=>{
 for(const locale of ['en','fr','es','pl','pt_BR']){const text=fs.readFileSync(path.join(root,`src/main/resources/oscarResources_${locale}.properties`),'utf8');const props=properties(text);
  assert(props['Appointment.textTooLong'].includes('{0}')&&props['Appointment.textTooLong'].includes('{1}'));assert(props['Appointment.textInvalidUnicode'].includes('{0}'));assert(!/\\x[0-9a-fA-F]{2}/.test(props['Appointment.textTooLong']));assert(!/<|>/.test(props['Appointment.textTooLong']));
 }
});
const schema=fs.readFileSync(path.join(repo,'database/mysql/migration/common/V1__baseline_schema.sql'),'utf8');
const baselineLimits=Object.fromEntries(['appointment','appointmentArchive'].map(table=>{const block=schema.match(new RegExp('CREATE TABLE `'+table+'` \\(([\\s\\S]*?)\\) ENGINE='))?.[1];assert(block);return[table,Object.fromEntries(['name','reason','notes','resources'].map(field=>{const n=block.match(new RegExp('`'+field+'` varchar\\((\\d+)\\)'))?.[1];assert(n);return[field,Number(n)];}))];}));
const migrationName='V1.0.59.5__preserve_full_appointment_names.sql';
const migration=fs.readFileSync(process.env.APPOINTMENT_NAME_MIGRATION_FILE||path.join(root,'database/mysql/migration/common',migrationName),'utf8');
function selectFor(table){
 const matches=[...migration.matchAll(/SELECT CASE[\s\S]*?FROM information_schema\.columns\s+WHERE table_schema = DATABASE\(\) AND table_name = '([^']+)' AND column_name = 'name'/g)];
 const selected=matches.filter(match=>match[1]===table);assert.equal(selected.length,1);return selected[0][0];
}
function metadataPlan(table,metadata={},extraRows=[]){
 const db=new DatabaseSync(':memory:');try{
  db.exec("ATTACH DATABASE ':memory:' AS information_schema; CREATE TABLE information_schema.columns(table_schema TEXT,table_name TEXT,column_name TEXT,character_maximum_length INTEGER,data_type TEXT,character_set_name TEXT,collation_name TEXT,is_nullable TEXT,column_default TEXT,column_comment TEXT,extra TEXT,generation_expression TEXT)");
  db.function('DATABASE',()=> 'owned_fixture');db.function('IF',(condition,yes,no)=>condition?yes:no);
  db.function('CONCAT',{varargs:true},(...args)=>args.some(value=>value===null)?null:args.join(''));
  db.function('QUOTE',text=>text===null?'NULL':"'"+text.replaceAll('\\','\\\\').replaceAll("'","\\'")+"'");
  const row={table_schema:'owned_fixture',table_name:table,column_name:'name',character_maximum_length:50,data_type:'varchar',character_set_name:'utf8mb4',collation_name:'utf8mb4_general_ci',is_nullable:'YES',column_default:'NULL',column_comment:'',extra:'',generation_expression:'',...metadata};
  const insert=entry=>db.prepare('INSERT INTO information_schema.columns VALUES(?,?,?,?,?,?,?,?,?,?,?,?)').run(...Object.values(entry));
  const missing=row.missing;delete row.missing;if(!missing)insert(row);for(const extra of extraRows)insert({...row,...extra});
  const result=db.prepare(selectFor(table)).all();assert(result.length<=1,'Metadata query may not join foreign schemas/tables/columns');
  const assignments=[...migration.matchAll(/^SET @\w+ = ([\s\S]*?);$/gm)].filter(match=>match[1].includes("table_name = '"+table+"'"));assert.equal(assignments.length,1);
  return db.prepare('SELECT '+assignments[0][1]+' AS ddl').get().ddl;
 }finally{db.close();}
}
const limits=Object.fromEntries(Object.entries(baselineLimits).map(([table,columns])=>{const ddl=metadataPlan(table);const width=ddl.match(new RegExp('ALTER TABLE '+table+' MODIFY COLUMN name VARCHAR\\((\\d+)\\)'))?.[1];assert(width);return[table,{...columns,name:Number(width)}];}));
test('historical schemas remain unchanged and the actual migration metadata SELECT derives effective255 name capacity',()=>{
 for(const table of ['appointment','appointmentArchive']){assert.deepEqual(baselineLimits[table],{name:50,reason:80,notes:255,resources:255});assert.deepEqual(limits[table],{name:255,reason:80,notes:255,resources:255});}
});
test('schema-derived SQL proves raw browser CRLF overflows while complete logical LF fits with the last character intact',()=>{
 const db=new DatabaseSync(':memory:');try{
  db.exec('CREATE TABLE booking(reason TEXT CHECK(length(reason)<=80),notes TEXT CHECK(length(notes)<=255))');
  for(const [field,max] of [['reason',80],['notes',255]]){const posted='X'.repeat(max-2)+'\r\nZ';const insert=db.prepare(`INSERT INTO booking(${field}) VALUES(?)`);
   assert.throws(()=>insert.run(posted),/CHECK constraint failed/);const logical=posted.replace(/\r\n/g,'\n');insert.run(logical);
   const row=db.prepare(`SELECT ${field} value,hex(${field}) bytes,length(${field}) chars FROM booking WHERE ${field} IS NOT NULL`).get();assert.equal(row.value,logical);assert.equal(row.chars,max);assert.equal(row.bytes,Buffer.from(logical).toString('hex').toUpperCase());assert(row.value.endsWith('Z'));
  }
 }finally{db.close();}
});
test('schema-derived SQL stores full supplementary-character boundaries and refuses rather than truncating each next codepoint',()=>{
 const db=new DatabaseSync(':memory:');try{
  for(const [field,max] of Object.entries(limits.appointment)){db.exec(`CREATE TABLE ${field}(value TEXT CHECK(length(value)<=${max}))`);const insert=db.prepare(`INSERT INTO ${field} VALUES(?)`);const text='😀'.repeat(max);insert.run(text);assert.equal(db.prepare(`SELECT hex(value) value FROM ${field}`).get().value,Buffer.from(text).toString('hex').toUpperCase());assert.throws(()=>insert.run(text+'Z'),/CHECK constraint failed/);}
 }finally{db.close();}
});

test('free-text patient zero remains subject to Add name validation',()=>{
 for(const zero of ['0','000']){const f=form('ADDAPPT',{keyword:'N'.repeat(256)});f.controls.demographic_no.value=zero;assert.equal(f.validate(),false);assert.equal(f.controls.keyword.value,'N'.repeat(256));}
});

test('native edit retry tolerates its absent doctor_no and retains a supplied legacy doctor without a null dereference',()=>{
 const jsp=readJsp('editappointment.jsp');assert(!/<input[^>]*name="doctor_no"/.test(jsp));
 const condition=jsp.match(/else if \(([^\n]*request\.getParameter\("doctor_no"\)[^\n]*)\) \{\s*doctorNo = request\.getParameter\("doctor_no"\);/)?.[1];assert(condition);
 const expression=condition.replace(/request\.getParameter\("doctor_no"\)\.equals\(""\)/g,'javaEquals(request.getParameter("doctor_no"), "")');
 for(const value of [null,'','777']){const context={request:{getParameter:()=>value},doctorNo:'',javaEquals(left,right){if(left===null)throw Error('Native Java String.equals dereferenced null');return left===right;}};
  vm.runInNewContext('if ('+expression+') doctorNo=request.getParameter("doctor_no");',context);assert.equal(context.doctorNo,value||'');}
});
test('native retry duplicate policy excludes only this appointment and retains every real other appointment',()=>{
 const jsp=readJsp('editappointment.jsp');const expr=jsp.match(/long numSameDayGroupAppts = ([\s\S]*?);/)?.[1];assert(expr);
 const javascript=expr.replace(/\.stream\(\)/g,'').replace(/candidate ->/g,'candidate =>').replace(/\.count\(\)/g,'.length').replace(/aa\.size\(\)/g,'aa.length').replace(/new Long\(/g,'Number(');
 for(const [retry,current,ids,want] of [[true,61,[61],0],[true,61,[61,62],1],[false,61,[61],1],[true,null,[61],1],[true,61,[],0],[true,2147483647,[2147483647],0]]){
  const got=vm.runInNewContext(javascript,{retryingThisAppointment:retry,retriedAppointment:current===null?null:{getId:()=>current},aa:ids.map(id=>({getId:()=>id})),Objects:{equals:(a,b)=>a===b},Number});assert.equal(got,want);
 }
 assert(jsp.includes('bMultipleSameDayGroupAppt = (numSameDayGroupAppts > 0);'));
 // The lambda may capture only effectively final copies (jspc rejects the reassigned originals).
 assert(jsp.includes('final boolean retryingThisAppointment = appointmentTextRetry;'));
 assert(jsp.includes('final Appointment retriedAppointment = apptFromRequest;'));
});

test('migration metadata query preserves every requested MariaDB attribute and raw quoted default expression',()=>{
 for(const table of ['appointment','appointmentArchive'])for(const [defaultValue,nullable,comment,extra] of [["'NULL'",'NO',"O'Brien \u4e2d\u6587",''],["'quote\\\" value'",'YES','legacy comment','INVISIBLE'],[null,'NO','no default',''],['NULL','YES','nullable default','']]){
  const ddl=metadataPlan(table,{character_set_name:'utf8mb4',collation_name:'utf8mb4_bin',column_default:defaultValue,is_nullable:nullable,column_comment:comment,extra});
  assert(ddl.startsWith(`ALTER TABLE ${table} MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin`));
  assert(ddl.includes(nullable==='YES'?' NULL':' NOT NULL'));if(defaultValue===null)assert(!ddl.includes(' DEFAULT '));else assert(ddl.includes(' DEFAULT '+defaultValue));
  assert(ddl.includes(' COMMENT '));if(extra)assert(ddl.endsWith(' '+extra));assert(ddl.includes(comment.replaceAll("'","\\'")));
 }
});
test('wider adopted columns and a partially committed widening are unchanged on the next execution',()=>{
 for(const table of ['appointment','appointmentArchive'])for(const [width,type] of [[255,'varchar'],[400,'varchar'],[65535,'text']])assert.equal(metadataPlan(table,{character_maximum_length:width,data_type:type}),'SELECT 1');
 const first=metadataPlan('appointment');assert(first.includes('VARCHAR(255)'));assert.equal(metadataPlan('appointment',{character_maximum_length:255}),'SELECT 1');assert(metadataPlan('appointmentArchive').includes('VARCHAR(255)'));
});
test('unsupported narrow generated/type columns fail explicitly and exact metadata predicates preserve foreign controls',()=>{
 for(const table of ['appointment','appointmentArchive']){for(const metadata of [{data_type:'char'},{generation_expression:'concat(first,last)'}])assert.match(metadataPlan(table,metadata),/^SIGNAL SQLSTATE '45000'/);
  const ddl=metadataPlan(table,{},[{table_schema:'foreign_schema',character_maximum_length:900},{table_name:'foreign_table',character_maximum_length:900},{column_name:'foreign_column',character_maximum_length:900}]);assert(ddl.startsWith('ALTER TABLE '+table+' '));
 }
 assert(migration.includes('Missing appointment.name column'));assert(migration.includes('Missing appointmentArchive.name column'));
});
test('migration has one collision-free common version and independent prepare/execute/deallocate sequences for both tables',()=>{
 const version=migrationName.slice(0,migrationName.indexOf('__')+2);
 const files=fs.readdirSync(path.join(repo,'database/mysql/migration'),{recursive:true}).filter(name=>path.basename(name).startsWith(version));
 assert.deepEqual(files,['common/'+migrationName]);assert.equal((migration.match(/PREPARE \w+ FROM/g)||[]).length,2);assert.equal((migration.match(/^EXECUTE /gm)||[]).length,2);assert.equal((migration.match(/^DEALLOCATE PREPARE /gm)||[]).length,2);
 assert.equal((migration.match(/SET @\w+ = COALESCE\(\(/g)||[]).length,2);
});
test('full baseline61-character linked legal names fit the widened native UI without shortening the displayed name',()=>{
 for(const mode of ['ADDAPPT','EDITAPPT']){const name='L'.repeat(30)+','+'F'.repeat(30),f=form(mode,{keyword:name});assert.equal(f.validate(),true);assert.equal(f.controls.keyword.value.length,61);}
});

test('the actual SQL COALESCE expression explicitly refuses a missing live or archive name column',()=>{
 for(const table of ['appointment','appointmentArchive'])assert.equal(metadataPlan(table,{missing:true}),"SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing "+table+".name column'");
});

test('native note editors retain every supplementary codepoint at the complete CDS32K boundary',()=>{
 for(const file of ['addappointment.jsp','editappointment.jsp']){const jsp=readJsp(file);const start=jsp.indexOf('name="notes"');assert(start>=0);const field=jsp.slice(start,start+1000);assert(field.includes('maxlength="65536"'));assert(field.includes('data-max-codepoints="32768"'));}
 for(const mode of ['ADDAPPT','EDITAPPT']){const value='😀'.repeat(32768),f=form(mode,{notes:value});assert.equal(f.validate(),true);assert.equal(f.controls.notes.value,value);f.controls.notes.value+='Z';assert.equal(f.validate(),false);assert.equal(f.controls.notes.value,value+'Z');}
});
