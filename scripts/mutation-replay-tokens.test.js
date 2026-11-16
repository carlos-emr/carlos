/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const {allergyAdd, chartNoteSave, preventionAdd} = require('./lib/mutation-families');
const {workflow: labUpload} = require('./lab-upload-playwright-checks');
const {SkipCheck} = require('./lib/playwright-harness');
const {buildReplay} = require('./lib/mutation-replay');

test('rollback without a private artifact directory skips before any SQL or browser operation', async () => {
  const previous = process.env.LAB_ROLLBACK_FAULT_ARTIFACT_DIR;
  delete process.env.LAB_ROLLBACK_FAULT_ARTIFACT_DIR;
  try {
    await assert.rejects(labUpload({}, {injectFailure: true}),
      error => error instanceof SkipCheck && error.message.includes('LAB_ROLLBACK_FAULT_ARTIFACT_DIR'));
  } finally {
    if (previous !== undefined) process.env.LAB_ROLLBACK_FAULT_ARTIFACT_DIR = previous;
  }
});

for (const [family, guard, field, prefix, path] of [
  [allergyAdd, 'saveToken', 'reactionDescription', 'AL', '/carlos/rx/addAllergy2'],
  [preventionAdd, 'carlosPreventionSubmission', 'comments', 'PV', '/carlos/prevention/AddPrevention'],
]) for (const token of [null, 'replaying-session-token']) {
  test(`${guard}: independent replay with CSRF ${token ? 'present' : 'absent'} keeps its ownership marker`, () => {
    const marker = 'FAKE-PW1234567890abcdef';
    const captured = {path, query: new URLSearchParams(), headers: {},
      contentType: 'application/x-www-form-urlencoded',
      body: new URLSearchParams({[guard]: 'claimed', [field]: 'original',
        demographicNo: '123', 'CSRF-TOKEN': 'captured-session-token'})};
    const {overrides} = family({marker, patient: '123'}).aim(captured, 'REPLAY');
    const request = buildReplay(captured, {origin: 'https://example.test', token, overrides});
    const replay = new URLSearchParams(request.data);
    assert.equal(replay.has(guard), false);
    assert.equal(replay.get(field), `${marker}-${prefix}-REPLAY`);
    assert.equal(replay.get('CSRF-TOKEN'), token);
    assert.equal(replay.get('demographicNo'), '123');
    assert.equal(captured.body.get(guard), 'claimed');
  });
}

/** The chart note save the page sent while its editor was a new note, as captureRequest returns it. */
function capturedNoteSave() {
  return {path: '/carlos/CaseManagementEntry', query: new URLSearchParams(), headers: {},
    contentType: 'application/x-www-form-urlencoded',
    body: new URLSearchParams({method: 'save', noteId: '0', note_edit: 'new', newNoteIdx: '01',
      caseNote_note: 'original', demographicNo: '123'})};
}

/**
 * A chart page and browser context that answer the New Note button the way the application does:
 * the click sends the editor's `edit` request, which `answer` describes ({form, status}).
 */
function noteEditorSession(answer) {
  const marker = 'FAKE-PW1234567890abcdef';
  const clicks = [];
  let deliver = () => {};
  const request = () => ({
    method: () => 'POST', url: () => 'https://example.test/carlos/CaseManagementEntry',
    frame: () => ({url: () => 'https://example.test/carlos/CaseManagementEntry'}),
    allHeaders: async () => ({'content-type': 'application/x-www-form-urlencoded'}),
    postData: () => new URLSearchParams(answer.form).toString(),
    response: async () => ({status: () => answer.status}),
  });
  const chart = {
    waitForLoadState: async () => {},
    locator: selector => ({
      first() { return this; },
      waitFor: async () => {},
      click: async () => { clicks.push(selector); if (selector === '#newNoteImg') deliver(); },
    }),
  };
  const context = {
    waitForEvent: (event, {predicate}) => new Promise((resolve, reject) => {
      deliver = () => (predicate(request()) ? resolve(request()) : reject(new Error('The click sent no matching request')));
    }),
  };
  return {marker, clicks, session: {marker, patient: '123', context, chart: async () => chart}};
}

test('chart note save: a replay meant to be refused goes as captured and opens no editor', async () => {
  const marker = 'FAKE-PW1234567890abcdef';
  const session = {marker, patient: '123', chart: async () => { throw new Error('The refused replay opened the chart'); }};
  for (const options of [undefined, {}, {write: false}]) {
    const aim = await chartNoteSave(session).aim(capturedNoteSave(), 'RR', options);
    assert.deepEqual(aim, {overrides: {caseNote_note: `${marker}-NS-RR note text`}});
  }
});

test('chart note save: the write control opens a new note editor and names it in the replay', async () => {
  const {marker, clicks, session} = noteEditorSession({status: 200,
    form: {method: 'edit', noteId: '0', note_edit: 'new', newNoteIdx: '02'}});
  const captured = capturedNoteSave();
  const {overrides} = await chartNoteSave(session).aim(captured, 'RF', {write: true});
  assert.deepEqual(clicks, ['#newNoteImg']);
  assert.deepEqual(overrides, {caseNote_note: `${marker}-NS-RF note text`, noteId: '0', note_edit: 'new', newNoteIdx: '02'});
  const replay = new URLSearchParams(buildReplay(captured, {origin: 'https://example.test', token: null, overrides}).data);
  assert.equal(replay.get('method'), 'save');
  assert.equal(replay.get('newNoteIdx'), '02');
  assert.equal(replay.get('caseNote_note'), `${marker}-NS-RF note text`);
  assert.equal(captured.body.get('newNoteIdx'), '01');
});

test('chart note save: the write control fails when the new editor is refused or names a saved note', async () => {
  const refused = noteEditorSession({status: 409, form: {method: 'edit', noteId: '0', note_edit: 'new', newNoteIdx: '02'}});
  await assert.rejects(chartNoteSave(refused.session).aim(capturedNoteSave(), 'RF', {write: true}), /answered HTTP 409/);
  const saved = noteEditorSession({status: 200, form: {method: 'edit', noteId: '57', note_edit: 'new', newNoteIdx: '02'}});
  await assert.rejects(chartNoteSave(saved.session).aim(capturedNoteSave(), 'RF', {write: true}), /does not name an unsaved note/);
});
