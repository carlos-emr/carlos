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
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {assertGroupSubmission} = require('./lib/eform-group-submit-proof');

// Exercise the actual caller and real redirect proof together. The in-memory
// panel model follows the application's contract: plain Administration defaults
// to date sorting; clicking a group's link requests form_name; POST redirects
// carry only the sort and selected group the form supplied. No browser or SQL.
async function runGroupStep({loseSecondSelection = false, foreignDeleteForm = false} = {}) {
  const marker = 'FAKE-PW-composition-abcdef123456';
  const group = 'PW' + marker.slice(-12);
  const baseUrl = new URL('https://localhost/carlos');
  const state = {sort: null, selected: null, member: true, groupExists: true, selections: 0, posts: [], completed: false};
  const stop = new Error('stop after owned group step');
  const page = {
    currentUrl: baseUrl + '/administration',
    url() { return this.currentUrl; },
    waitForLoadState: async () => {},
    close: async () => {},
    locator(selector, options = {}) { return locator(selector, options); },
  };
  function locator(selector, options) {
    return {selector, options, filter() { return this; },
      locator(inner) { return locator(inner, options); }, first() { return this; },
      count: async () => selector.includes('removeFromGroup') ? Number(state.selected === group && state.member) : 1,
      waitFor: async () => {}, isHidden: async () => false,
      click: async () => {
        if (selector.includes('remove from group')) state.confirmAction = '/eforms/removeFromGroup';
        if (selector.includes('delete this group')) state.confirmAction = '/eforms/delGroup';
      }};
  }
  const sql = {
    execute(statement) { assert.match(statement, /eform_groups/); },
    value(statement) {
      if (statement.startsWith('SELECT fid FROM eform')) return '42';
      if (statement.includes('AND fid=42')) return state.member ? '1' : '0';
      if (statement.includes('COUNT(*) FROM eform_groups')) return state.groupExists ? '1' : '0';
      throw new Error('Unexpected composition SQL: ' + statement);
    },
  };
  const h = {
    assert, sqlString: value => "'" + value.replace(/'/g, "''") + "'",
    pathOnly: url => new URL(url).pathname, assertNotErrorPage: async () => {},
    async gotoApp(target, _base, route) {
      target.currentUrl = baseUrl + route;
      state.sort = null; state.selected = null;
    },
  };
  async function selectGroup(target, control, options) {
    assert.equal(target, page);
    assert.equal(control.options.hasText, group);
    assert.equal(options.marker, `#dynamic-content #groupListTbl tr.table-success td[title="${group}"]`);
    state.selections++;
    state.sort = loseSecondSelection && state.selections === 2 ? null : 'form_name';
    state.selected = group;
  }
  async function submit(target, _control, expected) {
    assert.equal(target, page);assert.equal(state.confirmAction, expected.action);
    const removed = expected.action === '/eforms/delGroup';
    const fields = new URLSearchParams({[removed ? 'group_name' : 'groupName']:
      removed && foreignDeleteForm ? 'FOREIGN' : group, 'CSRF-TOKEN': 'synthetic'});
    if (!removed) fields.set('fid', '42');
    if (state.sort) fields.set('orderby', state.sort);
    const query = new URLSearchParams();
    if (state.sort) query.set('orderby', state.sort);
    if (!removed) query.set('group_view', group);
    const landed = baseUrl + '/eform/efmmanageformgroups' + (query.size ? '?' + query : '');
    const hops = [
      {url: baseUrl + expected.action, method: 'POST', status: 302, body: fields.toString(), headers: {}},
      {url: landed, method: 'GET', status: 200},
    ];
    // Membership removal belongs to the selected panel. A group's delete icon
    // is present in the list even when that group is not currently selected.
    if (!removed) assert.equal(state.selected, group, 'Member form must belong to selected owned group');
    // This is the unchanged production test proof, not an imitation of its assertions.
    assertGroupSubmission(hops, expected);
    state.posts.push({action: expected.action, fields: Object.fromEntries(fields), landed});
    if (removed) state.groupExists = false; else state.member = false;
    page.currentUrl = landed;
  }
  const session = {sql, marker, patient: '123', provider: '999998', config: {baseUrl},
    context: {on() {}, newPage: async () => page}, cleanup() {},
    async step(label, body) {
      if (label.startsWith('eForm Groups')) { await body(); state.completed = true; }
      else if (state.completed) throw stop;
    }};
  const requireStub = name => {
    if (name === './lib/playwright-harness') return h;
    if (name === './lib/workflow-session') return {runWorkflow() {}, expectValue: async (_sql, query, expected, message) => assert.equal(sql.value(query), expected, message)};
    if (name === './lib/playwright-ui') return {clickInjectsPanel: selectGroup};
    if (name === './lib/eform-group-submit-proof') return {submitGroupConfirmation: submit};
    throw new Error('Unexpected caller dependency: ' + name);
  };
  const filename = process.env.CSRF_GROUP_CALLER || path.join(__dirname, 'csrf-runtime-forms-playwright-checks.js');
  const module = {exports: {}};
  vm.runInNewContext(fs.readFileSync(filename, 'utf8'), {require: requireStub, module, URL, URLSearchParams, console}, {filename});
  try { await module.exports.workflow(session); } catch (error) { if (error !== stop) throw error; }
  assert.equal(state.completed, true);
  return state;
}

test('actual caller selects its owned name-sorted panel again before both group mutations', async () => {
  const state = await runGroupStep();
  assert.equal(state.selections, 2);
  assert.deepEqual(state.posts.map(post => post.action), ['/eforms/removeFromGroup', '/eforms/delGroup']);
  assert(state.posts.every(post => post.fields.orderby === 'form_name'));
  assert.equal(state.member, false);assert.equal(state.groupExists, false);
});
test('unchanged redirect proof still rejects a second selection that did not actually establish sorting', async () => {
  await assert.rejects(runGroupStep({loseSecondSelection: true}), /unique sort order/);
});
test('unchanged proof still rejects a foreign delete form after the owned panel reload', async () => {
  await assert.rejects(runGroupStep({foreignDeleteForm: true}), /different group/);
});
