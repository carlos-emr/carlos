/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { selectTicklerAssignee } = require('./lib/tickler-clinic');

function form(sites, {preferredHidden=false,changePresent=true}={}) {
  const actions = [];
  let currentSite = 'none';
  let visible=!preferredHidden;
  const page = {
    locator(selector) {
      if(selector==='#nameWrapper a[onclick*="toggleWrappers"]')return {count:async()=>changePresent?1:0,click:async()=>{visible=true;actions.push(['change']);}};
      if (selector === 'select#site') return {
        count: async () => sites === null ? 0 : 1,
        isVisible:async()=>visible,
        locator: () => ({ evaluateAll: async fn => fn([{ value: 'none' },
          ...Object.keys(sites).map(value => ({ value }))]) }),
        selectOption: async value => { assert(visible,'clinic cannot be selected while its saved preference is displayed');currentSite = value; actions.push(['clinic', value]); },
      };
      assert.equal(selector, 'select[name="task_assigned_to"]');
      return {
        locator: () => ({ evaluateAll: async (fn, provider) =>
          fn((sites[currentSite] || []).map(value => ({ value })), provider) }),
        selectOption: async value => actions.push(['assignee', value]),
      };
    },
  };
  return { page, actions };
}

test('single-site Add Tickler selects the requested assignee directly', async () => {
  const { page, actions } = form(null);
  await selectTicklerAssignee(page, '999998');
  assert.deepEqual(actions, [['assignee', '999998']]);
});

test('multisite Add Tickler finds the clinic containing the requested provider', async () => {
  const { page, actions } = form({ Empty: [], Other: ['101'], Matching: ['102', '999998'], Later: ['999998'] });
  await selectTicklerAssignee(page, '999998');
  assert.deepEqual(actions, [['clinic', 'Empty'], ['clinic', 'Other'], ['clinic', 'Matching'], ['assignee', '999998']]);
});

test('a missing provider fails explicitly without selecting an unrelated assignee', async () => {
  const { page, actions } = form({ Other: ['101'] });
  await assert.rejects(selectTicklerAssignee(page, '999998'), /No available Tickler clinic contains provider 999998/);
  assert.deepEqual(actions, [['clinic', 'Other']]);
});

test('a multisite install with no clinics fails explicitly', async () => {
  const { page, actions } = form({});
  await assert.rejects(selectTicklerAssignee(page, '999998'), /No available Tickler clinic/);
  assert.deepEqual(actions, []);
});

test('a saved multisite preference is changed through the visible native control before choosing a clinic',async()=>{
 const {page,actions}=form({Matching:['999998']},{preferredHidden:true});
 await selectTicklerAssignee(page,'999998');
 assert.deepEqual(actions,[['change'],['clinic','Matching'],['assignee','999998']]);
});

test('a hidden saved clinic without Change fails before selecting any clinic or assignee',async()=>{
 const {page,actions}=form({Matching:['999998']},{preferredHidden:true,changePresent:false});
 await assert.rejects(selectTicklerAssignee(page,'999998'),/Saved Tickler clinic has no Change control/);
 assert.deepEqual(actions,[]);
});
