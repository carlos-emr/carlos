/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const h = require('./lib/playwright-harness');
const { assertNativeGroupRequest, assertGroupExpansion, ownedGroupLockIds,
  groupPatientLegacyValues, assertOwnedGroupLegacySeed } = require('./lib/group-note-full-view-checks');

const rawOwnerLegacy = [null,null,null,null,null,null,null,null,null,' on ','','',''];
test('the owner fixture proves SQL NULLs, padded province and empty DOB bytes before browser reads', () => {
  assert.deepEqual(groupPatientLegacyValues('owner'), rawOwnerLegacy);
  assert.doesNotThrow(() => assertOwnedGroupLegacySeed([[JSON.stringify(rawOwnerLegacy)]]));
});
for (const [index,field,normalized] of [
  [0,'phone',''],[1,'phone2',''],[2,'chart_no',''],[3,'family_doctor',''],[4,'alias',''],[5,'pronoun',''],[6,'gender',''],
  [7,'patient_status','AC'],[8,'roster_status','NR'],[9,'province','ON'],
  [10,'year_of_birth',null],[11,'month_of_birth',null],[12,'date_of_birth',null],
]) test(`the raw owner seed guard rejects premature ${field} normalization`,()=>{
  const changed=rawOwnerLegacy.slice();changed[index]=normalized;
  assert.throws(()=>assertOwnedGroupLegacySeed([[JSON.stringify(changed)]]),/thirteen raw legacy values/);
});
test('the recipient keeps its active native chart status, roster, province and complete DOB',()=>{
  assert.deepEqual(groupPatientLegacyValues('recipient'),[null,null,null,null,null,null,null,'AC','NR','ON','1980','01','02']);
  assert.throws(()=>groupPatientLegacyValues('unknown'),/Unknown group fixture patient kind/);
});
test('raw seed proof cannot substitute missing/duplicate rows or the text NULL for SQL NULL',()=>{
  assert.throws(()=>assertOwnedGroupLegacySeed([]),/thirteen raw legacy values/);
  assert.throws(()=>assertOwnedGroupLegacySeed([[JSON.stringify(rawOwnerLegacy)],[JSON.stringify(rawOwnerLegacy)]]),/thirteen raw legacy values/);
  const textNull=rawOwnerLegacy.slice();textNull[0]='NULL';
  assert.throws(()=>assertOwnedGroupLegacySeed([[JSON.stringify(textNull)]]),/thirteen raw legacy values/);
});

const identity = { baseUrl: new URL('https://127.0.0.1/carlos'), patient: '101', noteId: '52', token: 'actual-owned-token' };
function request({ path = '/carlos/CaseManagementView', origin = 'https://127.0.0.1',
  method = 'POST', context = identity.token, fields = {} } = {}) {
  return { method: () => method, url: () => `${origin}${path}?chartContext=${context}`,
    postData: () => new URLSearchParams({ method: 'viewNote', raw: 'false', noteId: '52', demographicNo: '101', ...fields }).toString() };
}

test('the exact native fullView POST retains recipient, owner note and captured context', () => {
  assert.doesNotThrow(() => assertNativeGroupRequest(request(), identity));
});

for (const [name, options] of [
  ['wrong patient', { fields: { demographicNo: '202' } }],
  ['conflicting patient alias', { fields: { demographic_no: '202' } }],
  ['wrong note', { fields: { noteId: '53' } }],
  ['editor request', { fields: { raw: 'true' } }],
  ['another action', { fields: { method: 'edit' } }],
  ['another captured chart', { context: 'foreign-token' }],
  ['another origin', { origin: 'https://example.invalid' }],
  ['another route', { path: '/carlos/OtherView' }],
  ['wrong verb', { method: 'GET' }],
]) {
  test(`native request validation rejects ${name}`, () => {
    assert.throws(() => assertNativeGroupRequest(request(options), identity), /Native group|Group expansion/);
  });
}

const shared = 'SHARED <b>H&P</b>\n患者';
const response = { status: 200, headers: { [h.APPLICATION_HEADER]: 'none', 'content-type': 'text/html;charset=UTF-8' },
  html: '\nSHARED &lt;b&gt;H&amp;P&lt;/b&gt;<br>患者\n', text: shared };
test('application HTML contains the exact decoded text and only encoded source markup', () => {
  assert.doesNotThrow(() => assertGroupExpansion(response, shared, ['PRIVATE', 'CUMULATIVE']));
});
for (const status of [204, 302, 403, 500]) test(`HTTP ${status} cannot stand in for successful native expansion`, () => {
  assert.throws(() => assertGroupExpansion({ ...response, status }, shared, []), /application HTML/);
});
test('unmarked or incorrectly typed responses cannot establish application expansion', () => {
  for (const headers of [{ 'content-type': 'text/html' }, { [h.APPLICATION_HEADER]: 'none', 'content-type': 'application/javascript' }]) {
    assert.throws(() => assertGroupExpansion({ ...response, headers }, shared, []), /application HTML/);
  }
});
test('successful status cannot hide executable markup, truncation or unshared clinical text', () => {
  assert.throws(() => assertGroupExpansion({ ...response, html: '<script>changed()</script>' }, shared, []), /markup/);
  assert.throws(() => assertGroupExpansion({ ...response, html: 'SHARED <unfinished' }, shared, []), /markup/);
  assert.throws(() => assertGroupExpansion({ ...response, text: 'SHARED prefix' }, shared, []), /truncated/);
  assert.throws(() => assertGroupExpansion({ ...response, html: `${response.html}PRIVATE` }, shared, ['PRIVATE']), /unshared/);
});

for (const session of ['owned-session', 'owned-session.route']) test(`retirement retains exact ownership for stored ${session}`, () => {
  const rows = [
    ['1', '800001', '101', '0', session],
    ['2', '800002', '101', '0', session],
    ['3', '800001', '202', '0', session],
    ['4', '800001', '101', '52', session],
    ['5', '800001', '101', '0', 'another-session'],
  ];
  assert.deepEqual([...ownedGroupLockIds(rows, { provider: '800001', patient: '101', note: '0',
    sessionCookie: 'owned-session.route' })], ['1']);
});
test('retirement never guesses ownership without the authenticated cookie', () => {
  assert.throws(() => ownedGroupLockIds([], { provider: '800001', patient: '101', note: '0' }), /cookie is missing/);
});
