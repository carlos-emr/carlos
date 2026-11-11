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
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');
const { seedSecondPatient, assertDistinctCharts } = require('./lib/echart-module-list');
const { assertCodeMatches } = require('./billing-on-bill-form-code-typeahead-playwright-checks');
const { assertReferralMatches } = require('./billing-on-bill-form-referral-typeahead-playwright-checks');
const { workflow: consultation } = require('./consultation-specialist-details-playwright-checks');

function patientFixture({ committed = true, lostKey = false } = {}) {
  let present = false;
  let owner = '42';
  let cleanup;
  const writes = [];
  const s = {
    marker: 'FAKE-PW-owned', provider: '999998',
    cleanup(fn) { cleanup = fn; },
    sql: {
      insertId(query, values) {
        assert.equal(typeof cleanup, 'function', 'cleanup intent must precede INSERT');
        assert.match(query, /VALUES \(\?, \?/);
        assert.deepEqual(values, [s.marker, 'PatientBravo', s.provider]);
        present = committed;
        if (lostKey) throw new Error('client timed out after INSERT');
        return owner;
      },
      rows(query, values) {
        assert.match(query, /last_name=\? AND first_name=\? AND provider_no=\?/);
        assert.deepEqual(values, [s.marker, 'PatientBravo', s.provider]);
        return present ? [[owner]] : [];
      },
      execute(query, values) {
        writes.push({ query, values });
        if (query.startsWith('DELETE FROM demographic WHERE')) present = false;
      },
      value() { return present ? '1' : '0'; },
    },
  };
  return { s, writes, cleanup: () => cleanup(), replaceOwner: () => { owner = '43'; } };
}

test('a committed second-patient INSERT is recovered and removed when its key is lost', () => {
  const f = patientFixture({ lostKey: true });
  assert.throws(() => seedSecondPatient(f.s), /timed out/);
  f.cleanup();
  assert.equal(f.writes.length, 5);
  assert.ok(f.writes.every(write => write.values[0] === '42'));
  assert.deepEqual(f.writes.at(-1).values, ['42', f.s.marker, 'PatientBravo', f.s.provider]);
  f.cleanup();
  assert.equal(f.writes.length, 5, 'cleanup must be idempotent');
});

test('a failed second-patient INSERT that never committed has nothing to delete', () => {
  const f = patientFixture({ committed: false, lostKey: true });
  assert.throws(() => seedSecondPatient(f.s), /timed out/);
  f.cleanup();
  assert.deepEqual(f.writes, []);
});

test('second-patient cleanup refuses a replaced owner before deleting children', () => {
  const f = patientFixture();
  assert.equal(seedSecondPatient(f.s), '42');
  f.replaceOwner();
  assert.throws(f.cleanup, /changed owner/);
  assert.deepEqual(f.writes, []);
});

for (const appRoot of ['/carlos', '/proxy/carlos', '']) {
  test(`second chart keeps its login, its first window and application path ${appRoot || '/'}`, async () => {
    let windowName = 'encounter';
    const navigations = [];
    const context = { newPage: async () => ({ goto: async url => navigations.push(String(url)) }) };
    const page = id => ({ isClosed: () => false, url: () => `http://127.0.0.1${appRoot}/CaseManagementEntry?demographicNo=${id}`, context: () => context });
    const first = { ...page('41'), evaluate: async (_fn, name) => { windowName = name; } };
    const other = page('42');
    const module = { exports: {} };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'lib/echart-module-list.js'), 'utf8'), {
      module, URL,
      require(name) {
        if (name === './playwright-harness') return h;
        if (name === './chart-lock-cleanup') return {};
        if (name === '../master-record-tabs-playwright-checks') return { openMasterRecord: async () => ({ masterPage: {} }) };
        if (name === '../echart-navbar-modules-playwright-checks') return {
          openChart: async passedContext => {
            assert.equal(passedContext, context);
            return windowName === 'encounter' ? first : other;
          },
          waitForNavbars: async () => {},
        };
        throw new Error(`Unexpected dependency: ${name}`);
      },
    });
    const s = { patient: '41', marker: 'FAKE-PW-owned', context,
      config: { baseUrl: new URL(`http://127.0.0.1${appRoot}`) },
      schedule: { url: () => `http://127.0.0.1${appRoot}/provider/providercontrol?year=2026` } };
    assert.equal(await module.exports.openSecondChart(s, '42', first), other);
    assert.deepEqual(navigations, [`http://127.0.0.1${appRoot}/provider/providercontrol?year=2026`]);
    assertDistinctCharts(s, first, other);
    assert.throws(() => assertDistinctCharts(s, first, first), /reused/);
    assert.throws(() => assertDistinctCharts(s, other, first), /changed patient/);
    assert.throws(() => assertDistinctCharts(s, first, { ...other, context: () => ({}) }), /same login/);
  });
}

test('typeahead assertions reject unfiltered results while accepting supported search fields', () => {
  const code = { code: 'Z999A', description: 'FAKE-PW-owned service' };
  assertCodeMatches([code], 'z999');
  assertCodeMatches([code], 'owned');
  assert.throws(() => assertCodeMatches([code, { code: 'A001A', description: 'unrelated' }], 'z999'), /unrelated/);
  assert.throws(() => assertCodeMatches([code], 'no-match'), /unrelated/);
  const doctor = { lastName: 'FAKE-PW-owned', referralNo: '912345' };
  assertReferralMatches([doctor], 'FAKE-PW');
  assertReferralMatches([doctor], '9123');
  assertReferralMatches([{ phoneNumber: '(912) 345-6789' }], '9123');
  assertReferralMatches([{ streetAddress: '12 Owned Street' }], 'owned');
  assert.throws(() => assertReferralMatches([doctor, { lastName: 'unrelated' }], 'FAKE-PW'), /unrelated/);
  assert.throws(() => assertReferralMatches([doctor], 'no-match'), /unrelated/);
});

test('health-care-team consultation mode skips before any shared fixture INSERT', async () => {
  const form = {
    waitForLoadState: async () => {},
    locator: selector => { assert.equal(selector, 'select#specialist'); return { count: async () => 1 }; },
  };
  const s = {
    config: { baseUrl: 'http://127.0.0.1/carlos' }, patient: '41', recorder: h.createRecorder(),
    context: { newPage: async () => ({ on() {}, goto: async () => {}, getByRole: () => ({}) }) },
    popup: async () => form,
    cleanup() { assert.fail('no fixtures should be registered'); },
    sql: { insertId() { assert.fail('no fixtures should be inserted'); } },
  };
  await assert.rejects(consultation(s), error => error instanceof h.SkipCheck && /health-care-team/.test(error.message));
});
