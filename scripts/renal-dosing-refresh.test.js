/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'),'utf8');
const refresh=source.slice(source.indexOf('function getRenalDosingInformation(divId,atcCode)'),source.indexOf('function getLUC(',source.indexOf('function getRenalDosingInformation(divId,atcCode)')));
function harness(initial='previous calculation') {
  const link={},panel={innerHTML:initial,querySelector:selector=>{assert.equal(selector,'a');return link;}},requests=[];
  let current=panel;
  const context={document:{getElementById:()=>current,createElement:tag=>{
    assert.equal(tag,'template');
    const fragment={innerHTML:''};
    fragment.content={querySelector:selector=>{
      assert.equal(selector,'[data-renal-dosing-panel]');
      return fragment.innerHTML.includes('data-renal-dosing-panel')?{}:null;
    }};
    return fragment;
  }},CarlosAjax:{request:(url,options)=>requests.push({url,...options})},Math,encodeURIComponent};
  vm.runInNewContext(refresh,context);
  return {panel,link,requests,context,replace:value=>{current=value;}};
}
for(const status of [200,400])test(`only the latest renal refresh can replace a panel, including HTTP ${status}`,()=>{
  const {panel,requests,context}=harness();
  context.getRenalDosingInformation('renalDosing_7','J01MA02,S01AX13');
  context.getRenalDosingInformation('renalDosing_7','J01MA02,S01AX13');
  const parameters=new URLSearchParams(requests[1].parameters);
  assert.equal(parameters.get('divId'),'renalDosing_7');
  assert.equal(parameters.get('atcCode'),'J01MA02,S01AX13');
  const body=status===400?'<div data-renal-dosing-panel>newest refusal</div>':'newest calculation';
  requests[1].onComplete({status,responseText:body});
  requests[0].onComplete({status:200,responseText:'stale recommendation'});
  assert.equal(panel.innerHTML,body);
});
test('a removed drug card cannot receive a late renal response',()=>{
  const h=harness('original'),replacement={innerHTML:'replacement'};
  h.context.getRenalDosingInformation('renalDosing_7','J01MA02');
  h.replace(replacement);
  h.requests[0].onComplete({status:200,responseText:'late calculation'});
  assert.equal(h.panel.innerHTML,'original');
  assert.equal(replacement.innerHTML,'replacement');
});
test('an older network failure cannot erase newer renal guidance',()=>{
  const h=harness();
  h.context.getRenalDosingInformation('renalDosing_7','J01MA02');
  h.context.getRenalDosingInformation('renalDosing_7','J01MA02');
  h.requests[1].onComplete({status:200,responseText:'newest calculation'});
  h.requests[0].onComplete({status:0,responseText:'Network error'});
  assert.equal(h.panel.innerHTML,'newest calculation');
});
for(const initial of ['', '<table><tr class="selected">old dose</tr></table>'])for(const status of [0,400,401,403,404,500,503]) {
  test(`renal HTTP ${status} remains retryable from ${initial?'an existing dose':'an initial load'}`,()=>{
    const h=harness(initial),body='<h1>Unexpected backend error body</h1>';
    h.context.getRenalDosingInformation('renalDosing_7','J01MA02,S01AX13');
    h.requests[0].onComplete({status,responseText:body});
    assert.ok(h.panel.innerHTML.includes('data-renal-dosing-unavailable'));
    assert.ok(h.panel.innerHTML.includes('N/A')&&h.panel.innerHTML.includes('Refresh'));
    assert.ok(!h.panel.innerHTML.includes('old dose')&&!h.panel.innerHTML.includes(body));
    assert.equal(h.link.onclick(),false,'Retry must prevent the default link navigation');
    assert.equal(h.requests.length,2);
    const parameters=new URLSearchParams(h.requests[1].parameters);
    assert.equal(parameters.get('divId'),'renalDosing_7');
    assert.equal(parameters.get('atcCode'),'J01MA02,S01AX13');
    h.requests[1].onComplete({status:200,responseText:'recovered calculation'});
    assert.equal(h.panel.innerHTML,'recovered calculation');
  });
}
