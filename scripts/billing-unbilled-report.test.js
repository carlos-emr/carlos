/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Static contract for scripts/billing-unbilled-report-playwright-checks.js
 * (issue #3960): the browser check's scenario matrix must cover every
 * checkbox combination, never expect a billed appointment, and match the
 * JSP checkbox names the report forms submit.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const { FIXTURES, SCENARIOS, SCREENS, bcScreensServed, installedProvince } = require('./billing-unbilled-report-playwright-checks');

const JSP_DIR = path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'billing', 'CA');
const FORMS = [
  'ON/billingReportCenter.jsp',
  'ON/billingReportControl.jsp',
  'BC/billingReportCenter.jsp',
  'BC/billingReportControl.jsp',
];

test('scenario matrix covers all four checkbox combinations exactly once', () => {
  const combos = SCENARIOS.map(s => `${s.noShow}/${s.cancelled}`).sort();
  assert.deepEqual(combos, ['false/false', 'false/true', 'true/false', 'true/true']);
});

test('billed appointments are never expected and N/C follow their checkbox', () => {
  for (const s of SCENARIOS) {
    assert.ok(!s.expected.includes('billed'), 'a billed appointment is never unbilled work');
    assert.equal(s.expected.includes('noshow'), s.noShow);
    assert.equal(s.expected.includes('cancelled'), s.cancelled);
    assert.ok(s.expected.includes('todo') && s.expected.includes('custom3'),
      'pending and lowercase custom statuses are always listed');
  }
});

test('fixtures seed each status whose prefix the DAO filters on', () => {
  const statuses = FIXTURES.map(f => f.status);
  for (const code of ['N', 'C', 'B', 'c', 't']) {
    assert.ok(statuses.includes(code), `missing fixture for status ${code}`);
  }
});

test('every unbilled report form submits the includeNoShow / includeCancelled checkboxes', () => {
  for (const form of FORMS) {
    const jsp = fs.readFileSync(path.join(JSP_DIR, form), 'utf8');
    assert.match(jsp, /type="checkbox"[^>]*name="includeNoShow"[^>]*value="true"/, `${form}: includeNoShow`);
    assert.match(jsp, /type="checkbox"[^>]*name="includeCancelled"[^>]*value="true"/, `${form}: includeCancelled`);
  }
});

test('the BC screen is driven as a working page only on BC or when the run names no province', () => {
  assert.deepEqual(SCREENS.filter(screen => screen.bcOnly).map(screen => screen.path), ['/billing/CA/BC/ViewBillingReportControl']);
  assert.equal(SCREENS.some(screen => !screen.bcOnly && /\/CA\/BC\//.test(screen.path)), false);
  assert.equal(bcScreensServed(installedProvince({ CARLOS_SUITE_PROVINCE: 'BC' })), true);
  assert.equal(bcScreensServed(installedProvince({ CARLOS_SUITE_PROVINCE: ' bc ' })), true);
  assert.equal(bcScreensServed(installedProvince({})), true);
  assert.equal(bcScreensServed(installedProvince({ CARLOS_SUITE_PROVINCE: '' })), true);
  assert.equal(bcScreensServed(installedProvince({ CARLOS_SUITE_PROVINCE: 'ON' })), false);
  assert.equal(bcScreensServed(installedProvince({ CARLOS_SUITE_PROVINCE: 'on' })), false);
});

test('an unknown province value is an error of the run, not a default', () => {
  assert.throws(() => installedProvince({ CARLOS_SUITE_PROVINCE: 'AB' }), /CARLOS_SUITE_PROVINCE must be ON or BC/);
});

test('the BC routes this check treats as province-gated are the ones the interceptor refuses', () => {
  const interceptor = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'java', 'io', 'github', 'carlos_emr', 'carlos',
    'billings', 'ca', 'bc', 'pageUtil', 'BcBillingProvinceInterceptor.java'), 'utf8');
  assert.match(interceptor, /actionName\.startsWith\("billing\/CA\/BC\/"\)/);
  for (const screen of SCREENS) {
    assert.equal(Boolean(screen.bcOnly), screen.path.startsWith('/billing/CA/BC/'), screen.path);
  }
});

test('neither unbilled report puts the patient name in its Bill link', () => {
  const root = path.join(__dirname, '..', 'src', 'main');
  const sources = [
    path.join(root, 'java', 'io', 'github', 'carlos_emr', 'carlos', 'billings', 'ca', 'on', 'assembler', 'BillingReportFragmentViewModelAssembler.java'),
    path.join(root, 'webapp', 'WEB-INF', 'jsp', 'billing', 'CA', 'BC', 'billingReport_unbilled.jspf'),
  ];
  for (const file of sources) {
    const source = fs.readFileSync(file, 'utf8');
    assert.match(source, /\/billing\?billRegion=(?:ON|BC)&billForm=/, `${path.basename(file)}: builds the Bill link`);
    assert.doesNotMatch(source, /demographic_name=/, `${path.basename(file)}: the Bill link carries no patient name`);
  }
});
