/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { selectTicklerAssignee } = require('./lib/tickler-clinic');

function form(sites) {
  const actions = [];
  let currentSite = 'none';
  const page = {
    locator(selector) {
      if (selector === 'select#site') return {
        count: async () => sites === null ? 0 : 1,
        locator: () => ({ evaluateAll: async fn => fn([{ value: 'none' },
          ...Object.keys(sites).map(value => ({ value }))]) }),
        selectOption: async value => { currentSite = value; actions.push(['clinic', value]); },
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
