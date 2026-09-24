/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const { assertPatientText, resolvePdfUrl } = require('./demographic-label-content-playwright-checks');
test('label content assertion tolerates PDF line wrapping but rejects wrong or missing patient data', () => {
  assert.doesNotThrow(() => assertPatientText('FAKE-\nFixture\n42 O\'Neil & Test Lane', 'FAKE-Fixture', "42 O'Neil & Test Lane"));
  assert.throws(() => assertPatientText('OTHER Person 42 Test Lane', 'FAKE-Fixture', null), /patient name/);
  assert.throws(() => assertPatientText('FAKE-Fixture wrong address', 'FAKE-Fixture', '42 Test Lane'), /address/);
  assert.throws(() => assertPatientText('', 'FAKE-Fixture', null), /patient name/);
});
test('label PDF url follows a new_label_print wrapper iframe but stays in the application', () => {
  const base = 'http://127.0.0.1:8080/carlos';
  const wrapper = `${base}/demographic/ViewPrintDemoLabel?demographic_no=7`;
  assert.equal(resolvePdfUrl(wrapper, null, base), wrapper);
  assert.equal(resolvePdfUrl(wrapper, 'printDemoLabelAction?demographic_no=7&appointment_no=', base),
    'http://127.0.0.1:8080/carlos/demographic/printDemoLabelAction?demographic_no=7&appointment_no=');
  assert.equal(resolvePdfUrl(wrapper, '/carlos/report/GenerateEnvelopes?demos=7', new URL(base)),
    'http://127.0.0.1:8080/carlos/report/GenerateEnvelopes?demos=7');
  assert.throws(() => resolvePdfUrl(wrapper, 'https://example.com/label.pdf', base), /outside the CARLOS application/);
  // Same origin, different application on the host.
  assert.throws(() => resolvePdfUrl(wrapper, '/otherapp/label.pdf', base), /outside the CARLOS application/);
  assert.throws(() => resolvePdfUrl(wrapper, '/carlosother/label.pdf', base), /outside the CARLOS application/);
});
