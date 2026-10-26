/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const os = require('node:os');
const path = require('node:path');
const { EventEmitter } = require('node:events');
const { assertSelectionRequest, assertSelectionRedirect, assertParallelSelectionDelta, assertClearedSelectionDelta,
  assertSelectionPopupResponse, openSelectionPopup, writeStagedGroupPolicy } = require('./group-note-selection-playwright-checks');
const expected = { baseUrl: new URL('https://example.test/carlos'), patient: '101', program: '7', note: '51', token: 'issued.context', recipient: '202' };
function nativeRequest(change = {}) {
  const body = new URLSearchParams({ demographicNo: expected.patient, programId: expected.program, noteId: expected.note,
    chartContext: expected.token, num_anonymous: '0', group_client_id: expected.recipient, 'CSRF-TOKEN': 'real-token' });
  if (change.edit) change.edit(body);
  return { method: () => change.method || 'POST', url: () => change.url || 'https://example.test/carlos/casemgmt/SaveGroupNoteSelection',
    postData: () => body.toString() };
}
function popupResponse(change = {}) {
  return { url: () => change.url || 'https://example.test/carlos/casemgmt/ViewGroupNoteSelect',
    status: () => change.status || 200, request: () => ({ method: () => change.method || 'GET' }),
    headers: () => change.headers || { 'x-permitted-cross-domain-policies': 'none' } };
}
test('the native popup accepts only its mapped successful application document', () => {
  assert.doesNotThrow(() => assertSelectionPopupResponse(popupResponse(), expected));
  assert.throws(() => assertSelectionPopupResponse(popupResponse({ status: 409 }), expected), /HTTP 409/);
  assert.throws(() => assertSelectionPopupResponse(popupResponse({ headers: {} }), expected), /HTTP 200/);
  assert.throws(() => assertSelectionPopupResponse(popupResponse({ url: 'https://foreign.test/carlos/casemgmt/ViewGroupNoteSelect' }), expected), /unexpected document route/);
  assert.throws(() => assertSelectionPopupResponse(popupResponse({ url: 'https://example.test/carlos/casemgmt/groupNoteSelect.jsp' }), expected), /unexpected document route/);
  assert.throws(() => assertSelectionPopupResponse(popupResponse({ method: 'POST' }), expected), /unexpected document route/);
  assert.throws(() => assertSelectionPopupResponse(null, expected), /observed document response/);
});
for (const status of [200, 409]) test(`popup response arriving before its page event retains HTTP ${status}`, async () => {
  const context = new EventEmitter();
  context.waitForEvent = name => new Promise(resolve => context.once(name, resolve));
  const frame = {};
  const page = { mainFrame: () => frame, waitForLoadState: async () => {} };
  const answer = popupResponse({ status });
  answer.frame = () => frame;
  answer.request = () => ({ method: () => 'GET', isNavigationRequest: () => true });
  const chart = { locator: () => ({ click: async () => { context.emit('response', answer); context.emit('page', page); } }) };
  if (status === 200) assert.equal(await openSelectionPopup(context, chart, { baseUrl: expected.baseUrl }), page);
  else await assert.rejects(openSelectionPopup(context, chart, { baseUrl: expected.baseUrl }), /HTTP 409/);
  assert.equal(context.listenerCount('response'), 0);
});

function stagedPolicy() {
  const scope = {marker:'FAKE-PW0123456789abcdef',provider:'800001',facility:'4',programs:['7','8'],patients:['101','202','303','404']};
  return {scope,columns:{programs:['id','facilityId','name','description'],memberships:['id','program_id','provider_no'],
    admissions:['am_id','client_id','program_id','provider_no']},
  policy:{programs:[['7','4',`${scope.marker}-group-select-A`,'café 中文\nexact'],['8','4',`${scope.marker}-group-select-B`,null]],
    memberships:[['1','7','800001'],['2','8','800001']],
    admissions:[['1','101','7','800001'],['2','101','8','800001'],['3','202','7','800001'],['4','303','8','800001'],['5','404','7','800001']]}};
}
test('owned initial policy evidence is private, exact and cannot be overwritten', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(),'carlos-group-policy-unit-'));
  fs.chmodSync(directory,0o700);
  try {
    const {policy,columns,scope} = stagedPolicy();
    const file = writeStagedGroupPolicy(directory,policy,columns,scope);
    assert.equal(fs.statSync(file).mode&0o777,0o600);
    assert.deepEqual(JSON.parse(fs.readFileSync(file,'utf8')),{scope,columns,rows:policy});
    assert.throws(()=>writeStagedGroupPolicy(directory,policy,columns,scope),/EEXIST/);
    assert.deepEqual(JSON.parse(fs.readFileSync(file,'utf8')),{scope,columns,rows:policy});
  } finally {fs.rmSync(directory,{recursive:true,force:true});}
});
for (const [label,alter] of Object.entries({
  foreignProgram:value=>{value.policy.programs[0][0]='99';},
  foreignFacility:value=>{value.policy.programs[0][1]='99';},
  foreignProgramName:value=>{value.policy.programs[0][2]='unowned';},
  foreignMembership:value=>{value.policy.memberships[0][2]='999998';},
  foreignAdmission:value=>{value.policy.admissions[0][1]='999';},
  foreignAdmissionProvider:value=>{value.policy.admissions[0][3]='999998';},
  duplicateAdmission:value=>{value.policy.admissions[4]=value.policy.admissions[0].slice();},
  extraTable:value=>{value.policy.security=[['do not export']];},
})) test(`policy evidence rejects ${label} before writing`,()=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-group-policy-unit-'));fs.chmodSync(directory,0o700);
  try {const value=stagedPolicy();alter(value);assert.throws(()=>writeStagedGroupPolicy(directory,value.policy,value.columns,value.scope));
    assert.deepEqual(fs.readdirSync(directory),[]);
  } finally {fs.rmSync(directory,{recursive:true,force:true});}
});
test('policy evidence refuses a symlink destination without changing its target',()=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-group-policy-unit-'));fs.chmodSync(directory,0o700);
  try {
    const target=path.join(directory,'target');fs.writeFileSync(target,'unchanged');
    fs.symlinkSync(target,path.join(directory,'staged-policy.json'));
    const {policy,columns,scope}=stagedPolicy();assert.throws(()=>writeStagedGroupPolicy(directory,policy,columns,scope));
    assert.equal(fs.readFileSync(target,'utf8'),'unchanged');
  } finally {fs.rmSync(directory,{recursive:true,force:true});}
});
test('the genuine mapped selection POST and empty native clear retain exact identity', () => {
  assert.doesNotThrow(() => assertSelectionRequest(nativeRequest(), expected));
  assert.doesNotThrow(() => assertSelectionRequest(nativeRequest({ edit: body => body.delete('group_client_id') }), { ...expected, recipient: undefined }));
});
for (const name of ['demographicNo','programId','noteId','chartContext','num_anonymous']) {
  test(`selection request rejects changed ${name}`, () => assert.throws(() => assertSelectionRequest(nativeRequest({ edit: body => body.set(name,'999') }),expected)));
  test(`selection request rejects repeated ${name}`, () => assert.throws(() => assertSelectionRequest(nativeRequest({ edit: body => body.append(name,body.get(name)) }),expected)));
}
for (const [name,change] of Object.entries({
  wrongMethod:{method:'GET'}, externalOrigin:{url:'https://foreign.test/carlos/casemgmt/SaveGroupNoteSelection'},
  obsoleteJsp:{url:'https://example.test/carlos/casemgmt/groupNoteSelectAction.jsp'},
  wrongRecipient:{edit:body=>body.set('group_client_id','303')}, duplicateRecipient:{edit:body=>body.append('group_client_id','202')},
  missingCsrf:{edit:body=>body.delete('CSRF-TOKEN')}, conflictingCsrf:{edit:body=>body.append('CSRF-TOKEN','other')},
})) test(`selection request refuses ${name}`,()=>assert.throws(()=>assertSelectionRequest(nativeRequest(change),expected)));
function redirect(change={}) {
  const target=new URL('https://example.test/carlos/casemgmt/ViewGroupNoteSelectAction');
  for(const name of ['demographicNo','programId','chartContext'])target.searchParams.set(name,expected[{demographicNo:'patient',programId:'program',chartContext:'token'}[name]]);
  if(change.edit)change.edit(target);
  return {status:()=>change.status||303,headers:()=>({location:target.href,'x-permitted-cross-domain-policies':'none',...change.headers})};
}
test('accepted save redirects with303 to its fixed GET close view',()=>assert.doesNotThrow(()=>assertSelectionRedirect(redirect(),expected)));
for(const [name,change]of Object.entries({
  retainedPost307:{status:307}, generic302:{status:302}, notApplication:{headers:{'x-permitted-cross-domain-policies':undefined}},
  foreignPatient:{edit:url=>url.searchParams.set('demographicNo','202')}, duplicateContext:{edit:url=>url.searchParams.append('chartContext',expected.token)},
  external:{edit:url=>url.hostname='foreign.test'}, wrongPath:{edit:url=>url.pathname='/carlos/WEB-INF/jsp/casemgmt/groupNoteSelectAction.jsp'},
})) test(`group redirect refuses ${name}`,()=>{
  const value=redirect(change);if(name==='notApplication'){const headers=value.headers();delete headers['x-permitted-cross-domain-policies'];value.headers=()=>headers;}
  assert.throws(()=>assertSelectionRedirect(value,expected));
});
const source=fs.readFileSync(require.resolve('../src/main/webapp/js/newCaseManagementView.js.jsp'),'utf8');
const start=source.indexOf('    function selectGroup(');const end=source.indexOf('\n    function assign(',start);
assert(start>=0&&end>start);
for(const ctx of ['/carlos','/nested/emr'])test(`actual native group click uses mapped context path ${ctx}`,()=>{
  let popup;
  const scope={ctx,chartContext:'genuine+token&signature',document:{forms:{caseManagementEntryForm:{noteId:{value:'51'}}}},encodeURIComponent,
    popupPage:(height,width,name,url)=>{popup={height,width,name,url};}};
  vm.runInNewContext(source.slice(start,end),scope);scope.selectGroup('7 & other','101');
  const actual=new URL(popup.url,'https://example.test/wrong/relative/page');
  assert.equal(actual.pathname,`${ctx}/casemgmt/ViewGroupNoteSelect`);
  assert.equal(actual.searchParams.get('programId'),'7 & other');assert.equal(actual.searchParams.get('demographicNo'),'101');
  assert.equal(actual.searchParams.get('noteId'),'51');assert.equal(actual.searchParams.get('chartContext'),scope.chartContext);
  assert.equal(popup.name,'group');
});
test('actual native unsaved group click retains zero and genuine tokenless callers omit context',()=>{
  let address;const scope={ctx:'/carlos',chartContext:null,document:{forms:{caseManagementEntryForm:{noteId:{value:'0'}}}},encodeURIComponent,
    popupPage:(_h,_w,_name,url)=>{address=new URL(url,'https://example.test');}};
  vm.runInNewContext(source.slice(start,end),scope);scope.selectGroup('7','101');
  assert.equal(address.searchParams.get('noteId'),'0');assert.equal(address.searchParams.has('chartContext'),false);
});


const linkColumns = ['id','noteId','demographicNo','anonymous','active','created'];
const priorLinks = [['10','51','202','0','0','old'],['11','52','303','0','1','bystander']];
const parallelLinks = () => [...priorLinks.map(row=>row.slice()),
  ['12','51','202','0','0','first-retired'],['13','51','404','0','1','second-active']];
const parallelExpected = {columns:linkColumns,note:'51',recipients:['202','404']};
test('parallel native group replacements retain exactly one complete set and preserve all existing/bystander bytes',()=>{
  assert.deepEqual(assertParallelSelectionDelta(priorLinks,parallelLinks(),parallelExpected),['404']);
  const opposite=parallelLinks();opposite[2][4]='1';opposite[3][4]='0';
  assert.deepEqual(assertParallelSelectionDelta(priorLinks,opposite,parallelExpected),['202']);
});
for(const [label,alter]of Object.entries({
  union:rows=>{rows[2][4]='1';}, lostBoth:rows=>{rows[3][4]='0';},
  wrongNote:rows=>{rows[3][1]='52';}, foreignRecipient:rows=>{rows[3][2]='999';},
  sameRecipientTwice:rows=>{rows[3][2]='202';}, anonymous:rows=>{rows[3][3]='1';}, missingTimestamp:rows=>{rows[3][5]='NULL';},
  changedPriorMetadata:rows=>{rows[0][5]='rewritten';}, changedBystander:rows=>{rows[1][4]='0';},
  missingPriorRow:rows=>{rows.splice(0,1);}, extraNewRow:rows=>{rows.push(['14','51','404','0','0','extra']);},
  reusedIdentity:rows=>{rows[3][0]='10';}, adoptedOlderId:rows=>{rows[3][0]='9';},
}))test(`parallel group snapshot rejects ${label}`,()=>{
  const rows=parallelLinks();alter(rows);assert.throws(()=>assertParallelSelectionDelta(priorLinks,rows,parallelExpected));
});
test('parallel snapshot refuses a nonempty starting group or incomplete schema',()=>{
  const before=priorLinks.map(row=>row.slice());before[0][4]='1';
  assert.throws(()=>assertParallelSelectionDelta(before,parallelLinks(),parallelExpected));
  assert.throws(()=>assertParallelSelectionDelta(priorLinks,parallelLinks(),{...parallelExpected,columns:linkColumns.slice(1)}));
});
function clearedLinks(){const rows=parallelLinks();rows[3][4]='0';rows[3][5]='cleared-now';return rows;}
test('native clear changes only its exact active source link flag and ORM retirement timestamp',()=>{
  assert.doesNotThrow(()=>assertClearedSelectionDelta(parallelLinks(),clearedLinks(),{columns:linkColumns,note:'51'}));
});
for(const [label,alter]of Object.entries({
  activeNotCleared:rows=>{rows[3][4]='1';}, changedOwner:rows=>{rows[3][1]='52';},
  changedRecipient:rows=>{rows[3][2]='202';}, changedAnonymous:rows=>{rows[3][3]='1';},
  changedPriorTimestamp:rows=>{rows[0][5]='rewritten';}, changedBystander:rows=>{rows[1][4]='0';}, missingRetirementTimestamp:rows=>{rows[3][5]='';},
  addedRow:rows=>{rows.push(['14','51','202','0','0','extra']);}, removedRow:rows=>{rows.pop();},
}))test(`native clear snapshot refuses ${label}`,()=>{
  const rows=clearedLinks();alter(rows);assert.throws(()=>assertClearedSelectionDelta(parallelLinks(),rows,{columns:linkColumns,note:'51'}));
});
