/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'),'utf8');
const refresh=source.slice(source.indexOf('function getRenalDosingInformation(divId,atcCode)'),source.indexOf('function getLUC(',source.indexOf('function getRenalDosingInformation(divId,atcCode)')));
function harness(initial='previous calculation') {
  const button={},panel={innerHTML:initial,querySelector:selector=>{
    assert.equal(selector,'button');
    return /<button\b[^>]*\btype="button"[^>]*>[^<]*Refresh<\/button>/.test(panel.innerHTML)?button:null;
  }},requests=[];
  let current=panel;
  const context={document:{getElementById:id=>id==='renalDosing_7'?current:null,createElement:tag=>{
    assert.equal(tag,'template');
    const fragment={innerHTML:''};
    fragment.content={querySelector:selector=>{
      assert.equal(selector,'[data-renal-dosing-panel]');
      return /<div\b[^>]*\sdata-renal-dosing-panel(?:\s|=|>)/.test(fragment.innerHTML)?{}:null;
    }};
    return fragment;
  }},CarlosAjax:{request:(url,options)=>requests.push({url,...options})},Math,encodeURIComponent};
  vm.runInNewContext(refresh,context);
  return {panel,button,requests,context,replace:value=>{current=value;}};
}
for(const status of [200,400])test(`only the latest renal refresh can replace a panel, including HTTP ${status}`,()=>{
  const {panel,requests,context}=harness();
  context.getRenalDosingInformation('renalDosing_7','J01MA02,S01AX13');
  context.getRenalDosingInformation('renalDosing_7','J01MA02,S01AX13');
  const parameters=new URLSearchParams(requests[1].parameters);
  assert.equal(parameters.get('divId'),'renalDosing_7');
  assert.equal(parameters.get('atcCode'),'J01MA02,S01AX13');
  const body=`<div data-renal-dosing-panel>newest ${status===400?'refusal':'calculation'}</div>`;
  requests[1].onComplete({status,responseText:body});
  requests[0].onComplete({status:200,responseText:'<div data-renal-dosing-panel>stale recommendation</div>'});
  assert.equal(panel.innerHTML,body);
});
for(const replacement of [null,{innerHTML:'replacement'}])test(`a ${replacement?'replaced':'removed'} drug card cannot receive a late renal response`,()=>{
  const h=harness('original');
  h.context.getRenalDosingInformation('renalDosing_7','J01MA02');
  h.replace(replacement);
  h.requests[0].onComplete({status:200,responseText:'<div data-renal-dosing-panel>late calculation</div>'});
  assert.equal(h.panel.innerHTML,'original');
  if(replacement)assert.equal(replacement.innerHTML,'replacement');
});
test('an unknown panel ID cannot start a renal request',()=>{
  const h=harness();
  h.context.getRenalDosingInformation('missing-panel','J01MA02');
  assert.equal(h.requests.length,0);
  assert.equal(h.panel.innerHTML,'previous calculation');
});
test('an older network failure cannot erase newer renal guidance',()=>{
  const h=harness();
  h.context.getRenalDosingInformation('renalDosing_7','J01MA02');
  h.context.getRenalDosingInformation('renalDosing_7','J01MA02');
  const body='<div data-renal-dosing-panel>newest calculation</div>';
  h.requests[1].onComplete({status:200,responseText:body});
  h.requests[0].onComplete({status:0,responseText:'Network error'});
  assert.equal(h.panel.innerHTML,body);
});
for(const initial of ['', '<table><tr class="selected">old dose</tr></table>'])for(const {status,body} of [
  ...[0,400,401,403,404,500,503].map(status=>({status,body:'<h1>Unexpected backend error body</h1>'})),
  {status:200,body:''},
  {status:200,body:'<h1>Sign in</h1>'},
  {status:200,body:'data-renal-dosing-panel'},
]) {
  test(`renal HTTP ${status} with ${JSON.stringify(body)} remains retryable from ${initial?'an existing dose':'an initial load'}`,()=>{
    const h=harness(initial);
    h.context.getRenalDosingInformation('renalDosing_7','J01MA02,S01AX13');
    h.requests[0].onComplete({status,responseText:body});
    assert.ok(h.panel.innerHTML.includes('data-renal-dosing-unavailable'));
    assert.ok(h.panel.innerHTML.includes('N/A')&&h.panel.innerHTML.includes('Refresh'));
    assert.ok(!h.panel.innerHTML.includes('old dose'));
    if(body)assert.ok(!h.panel.innerHTML.includes(body));
    h.button.onclick();
    assert.equal(h.requests.length,2);
    const parameters=new URLSearchParams(h.requests[1].parameters);
    assert.equal(parameters.get('divId'),'renalDosing_7');
    assert.equal(parameters.get('atcCode'),'J01MA02,S01AX13');
    const recovered='<div data-renal-dosing-panel>recovered calculation</div>';
    h.requests[1].onComplete({status:200,responseText:recovered});
    assert.equal(h.panel.innerHTML,recovered);
  });
}

for (const status of [200, 403, 500]) test(`only an explicit successful no-guidance response clears the renal panel: ${status}`, () => {
  const h = harness();
  h.context.getRenalDosingInformation('renalDosing_7', 'J01MA02');
  h.requests[0].onComplete({status, responseText: '{"renalState":"no-guidance"}', responseJSON: {renalState: 'no-guidance'}});
  if (status === 200) assert.equal(h.panel.innerHTML, '');
  else assert.ok(h.panel.innerHTML.includes('data-renal-dosing-unavailable'));
});
