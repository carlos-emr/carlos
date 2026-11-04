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
const assert = require('node:assert/strict');
const REDIRECT = new Set([301, 302, 303, 307, 308]);

/** A completed UI mutation must belong to the selected owned form, not a stale panel. */
function assertGroupSubmission(hops, {baseUrl, action, group, fid, removed = false}) {
  const groupField = action === '/eforms/removeFromGroup' ? 'groupName'
    : action === '/eforms/delGroup' ? 'group_name' : null;
  assert(groupField, 'Unsupported group mutation action');
  const otherGroupField = groupField === 'groupName' ? 'group_name' : 'groupName';
  const base = new URL(baseUrl);
  const prefix = base.pathname.replace(/\/$/, '');
  assert(hops.length >= 2 && hops.length <= 4, 'Group mutation must complete POST/redirect/GET');
  const post = hops[0];
  assert.equal(post.method, 'POST', 'Group mutation did not start with POST');
  const posted = new URL(post.url);
  assert.equal(posted.origin, base.origin, 'Group mutation posted off-origin');
  assert.equal(posted.pathname, prefix + action, 'Group mutation posted to a different action');
  assert(!posted.searchParams.has('fid') && !posted.searchParams.has('groupName') && !posted.searchParams.has('group_name'), 'Ambiguous group mutation URL parameters');
  const fields = new URLSearchParams(post.body || '');
  assert.deepEqual(fields.getAll(groupField), [group], 'Group mutation targeted a different group');
  assert(!fields.has(otherGroupField), 'Group mutation contains an unexpected group field alias');
  if (fid !== undefined) assert.deepEqual(fields.getAll('fid'), [String(fid)], 'Group mutation targeted a different eForm');
  assert((post.headers || {})['csrf-token'] || fields.getAll('CSRF-TOKEN').some(value => value !== ''),
    'Group mutation POST has no CSRF token');
  for (const [index, hop] of hops.entries()) {
    assert.equal(new URL(hop.url).origin, base.origin, 'Group redirect left the application origin');
    if (index) assert.equal(hop.method, 'GET', 'Group redirect retained POST');
    assert(index === hops.length - 1 ? hop.status === 200 : REDIRECT.has(hop.status),
      'Group mutation response chain did not complete successfully');
  }
  const landed = new URL(hops.at(-1).url);
  assert.equal(landed.pathname, prefix + '/eform/efmmanageformgroups', 'Group redirect ended on a different page');
  assert.deepEqual(landed.searchParams.getAll('orderby'), ['form_name'], 'Group redirect lost its unique sort order');
  assert(landed.searchParams.getAll('group_view').length <= 1, 'Group redirect contains ambiguous selected groups');
  assert.equal(landed.searchParams.get('group_view') || '', removed ? '' : group,
    'Group redirect selected a different group');
}

async function submitGroupConfirmation(page, control, expected) {
  const chainOf = request => {
    const chain = [];
    for (let item = request; item; item = item.redirectedFrom()) chain.unshift(item);
    return chain;
  };
  const base = new URL(expected.baseUrl);
  const actionPath = base.pathname.replace(/\/$/, '') + expected.action;
  // Start before clicking: a fast redirected GET can finish before a second waiter is armed.
  const [landed] = await Promise.all([
    page.waitForResponse(response => {
      const first = chainOf(response.request())[0];
      const url = new URL(first.url());
      return first.method() === 'POST' && url.origin === base.origin && url.pathname === actionPath
        && !REDIRECT.has(response.status());
    }),
    control.click(),
  ]);
  const hops = [];
  for (const request of chainOf(landed.request())) {
    const response = await request.response();
    assert(response, 'Group submission has no response');
    hops.push({url:request.url(), method:request.method(), status:response.status(),
      body:request.postData(), headers:request.headers()});
  }
  assertGroupSubmission(hops, expected);
  await page.waitForLoadState('networkidle').catch(() => {});
  return landed;
}

module.exports = {assertGroupSubmission, submitGroupConfirmation};
