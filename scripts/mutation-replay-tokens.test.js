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
const {allergyAdd, preventionAdd} = require('./lib/mutation-families');
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
