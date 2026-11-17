/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {validApprovalAction}=require('./gap-encounter-eform-edoc-approval-playwright-checks');
test('eDoc approval validates parsed origin, exact pathname and unique template/patient query independently of CSRF',()=>{
  const base='https://localhost/carlos',current=base+'/eform/addeform',scope={fid:'11',patient:'22',csrfToken:'synthetic'};
  const action=base+'/eform/saveEFormAsEDoc?efmfid=11&efmdemographic_no=22';
  for(const url of [action,action+'&CSRF-TOKEN=synthetic','/carlos/eform/saveEFormAsEDoc?efmdemographic_no=22&efmfid=11'])assert.equal(validApprovalAction(url,current,base,scope),true);
  for(const url of [action.replace('localhost','foreign.invalid'),action.replace('/eform/','/other/eform/'),action.replace('efmfid=11','efmfid=12'),action.replace('efmdemographic_no=22','efmdemographic_no=23'),action+'&efmfid=11',action+'&redirect=foreign',action+'&CSRF-TOKEN=other','file:///eform/saveEFormAsEDoc',action+'#fragment'])assert.equal(validApprovalAction(url,current,base,scope),false,url);
});
