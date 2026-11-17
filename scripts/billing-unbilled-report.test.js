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

const { FIXTURES, SCENARIOS } = require('./billing-unbilled-report-playwright-checks');

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
