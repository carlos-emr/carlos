/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const {assertGroupSubmission} = require('./lib/eform-group-submit-proof');
const expected = {baseUrl:'https://localhost/carlos', action:'/eforms/removeFromGroup', group:'PWOwning', fid:'42'};
function proof() {
  return [
    {url:'https://localhost/carlos/eforms/removeFromGroup',method:'POST',status:302,
      body:'groupName=PWOwning&fid=42&CSRF-TOKEN=synthetic&orderby=form_name',headers:{}},
    {url:'https://localhost/carlos/eform/efmmanageformgroups?orderby=form_name&group_view=PWOwning',method:'GET',status:200},
  ];
}
test('requires an actual owned POST/redirect/GET carrying a CSRF token', () => {
  assert.doesNotThrow(() => assertGroupSubmission(proof(),expected));
  assert.throws(() => assertGroupSubmission([],expected));
});
test('rejects a stale other-group form and a different eForm', () => {
  for (const body of ['groupName=FOREIGN&fid=42&CSRF-TOKEN=x','groupName=PWOwning&fid=99&CSRF-TOKEN=x',
    'groupName=PWOwning&groupName=FOREIGN&fid=42&CSRF-TOKEN=x']) {
    const hops=proof();hops[0].body=body;assert.throws(() => assertGroupSubmission(hops,expected));
  }
});
test('rejects missing CSRF, failed HTTP and POST-preserving redirects', () => {
  for (const mutate of [h=>h[0].body='groupName=PWOwning&fid=42',h=>h[1].status=403,h=>h[1].method='POST']) {
    const hops=proof();mutate(hops);assert.throws(() => assertGroupSubmission(hops,expected));
  }
});
test('rejects off-origin, wrong action, wrong context, wrong group and lost sort', () => {
  for (const mutate of [h=>h[0].url='https://foreign.invalid/carlos/eforms/removeFromGroup',
    h=>h[0].url='https://localhost/carlos/eforms/delGroup',
    h=>h[1].url='https://localhost/foreign/eform/efmmanageformgroups?orderby=form_name&group_view=PWOwning',
    h=>h[1].url='https://localhost/carlos/eform/efmmanageformgroups?orderby=form_name&group_view=FOREIGN',
    h=>h[1].url='https://localhost/carlos/eform/efmmanageformgroups?group_view=PWOwning']) {
    const hops=proof();mutate(hops);assert.throws(() => assertGroupSubmission(hops,expected));
  }
});
test('group deletion must return to groups without selecting the deleted group', () => {
  const hops=proof();hops[0].url='https://localhost/carlos/eforms/delGroup';
  hops[0].body='group_name=PWOwning&CSRF-TOKEN=synthetic&orderby=form_name';
  hops[1].url='https://localhost/carlos/eform/efmmanageformgroups?orderby=form_name';
  assert.doesNotThrow(()=>assertGroupSubmission(hops,{...expected,action:'/eforms/delGroup',fid:undefined,removed:true}));
});

test('each action rejects a wrong, duplicate or additional group-field alias', () => {
  for (const action of ['/eforms/removeFromGroup','/eforms/delGroup']) {
    const groupField=action.endsWith('delGroup')?'group_name':'groupName';
    const other=groupField==='group_name'?'groupName':'group_name';
    for (const body of [other+'=PWOwning',groupField+'=PWOwning&'+groupField+'=PWOwning',groupField+'=PWOwning&'+other+'=PWOwning']) {
      const hops=proof();hops[0].url='https://localhost/carlos'+action;hops[0].body=body+'&fid=42&CSRF-TOKEN=x';
      if(action.endsWith('delGroup'))hops[1].url='https://localhost/carlos/eform/efmmanageformgroups?orderby=form_name';
      assert.throws(()=>assertGroupSubmission(hops,{...expected,action,removed:action.endsWith('delGroup')}));
    }
  }
});
