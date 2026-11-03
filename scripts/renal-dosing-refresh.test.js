/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'),'utf8');
const refresh=source.slice(source.indexOf('function getRenalDosingInformation(divId,atcCode)'),source.indexOf('function getLUC(',source.indexOf('function getRenalDosingInformation(divId,atcCode)')));
for(const status of [200,400])test(`only the latest renal refresh can replace a panel, including HTTP ${status}`,()=>{
  const panel={innerHTML:'previous calculation'},requests=[];
  const context={document:{getElementById:()=>panel},CarlosAjax:{request:(url,options)=>requests.push({url,...options})},Math,encodeURIComponent};
  vm.runInNewContext(refresh,context);
  context.getRenalDosingInformation('renalDosing_7','J01MA02,S01AX13');
  context.getRenalDosingInformation('renalDosing_7','J01MA02,S01AX13');
  const parameters=new URLSearchParams(requests[1].parameters);
  assert.equal(parameters.get('divId'),'renalDosing_7');
  assert.equal(parameters.get('atcCode'),'J01MA02,S01AX13');
  requests[1].onComplete({status,responseText:'newest calculation or refusal'});
  requests[0].onComplete({status:200,responseText:'stale recommendation'});
  assert.equal(panel.innerHTML,'newest calculation or refusal');
});
test('a removed drug card cannot receive a late renal response',()=>{
  const original={innerHTML:'original'},replacement={innerHTML:'replacement'};let current=original,request;
  const context={document:{getElementById:()=>current},CarlosAjax:{request:(_url,options)=>{request=options;}},Math,encodeURIComponent};
  vm.runInNewContext(refresh,context);
  context.getRenalDosingInformation('renalDosing_7','J01MA02');
  current=replacement;
  request.onComplete({status:200,responseText:'late calculation'});
  assert.equal(original.innerHTML,'original');
  assert.equal(replacement.innerHTML,'replacement');
});
