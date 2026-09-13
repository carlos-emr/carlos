/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');

const {
  ERROR_PAGE_RE, catalogueLinks, dedupe, findingsSince, snapshotRecorder,
} = require('./lib/playwright-link-audit');
const { createRecorder } = require('./lib/playwright-harness');

const adminSkips = require('./admin-index-links-playwright-checks').SKIP_ITEMS;
const masterSkips = require('./master-record-tabs-playwright-checks').SKIP_ITEMS;
const chartSkips = require('./echart-navbar-modules-playwright-checks').SKIP_ITEMS;

test('the same item rendered in two permission branches is opened once', () => {
  // admin.jsp renders several groups twice, once per oscarSec branch, so an
  // un-deduped catalogue would open the same page twice and double the runtime.
  const items = [
    { text: 'System Messages', href: '/SystemMessage', route: '' },
    { text: 'System Messages', href: '/SystemMessage', route: '' },
    { text: 'Issue Editor', href: '/issueAdmin?method=list', route: '' },
  ];
  assert.equal(dedupe(items).length, 2);
});

test('two different items that share a label are both kept', () => {
  // "Manage Billing Form" appears twice in admin.jsp pointing at different
  // routes; dropping one would silently stop checking a real page.
  const items = [
    { text: 'Manage Billing Form', href: '', route: '/billing/CA/BC/ManageBillingform' },
    { text: 'Manage Billing Form', href: '', route: '/billing/CA/ON/ManageBillingform' },
  ];
  assert.equal(dedupe(items).length, 2);
});

test('browser findings are attributed to the item that was open at the time', () => {
  // A failure that says only "something broke somewhere in 120 pages" does not
  // get acted on; naming the page is the whole point of the slice.
  const recorder = createRecorder();
  recorder.pageErrors.push({ label: 'earlier', text: 'ReferenceError: old' });
  const before = snapshotRecorder(recorder);
  recorder.pageErrors.push({ label: 'x', text: 'ReferenceError: contextPath is not defined\n  at foo' });
  recorder.consoleIssues.push({ label: 'x', type: 'error', text: 'TypeError: bad' });
  recorder.badResponses.push({ label: 'x', status: 500, resourceType: 'document' });
  recorder.requestFailures.push({ label: 'x', resourceType: 'script', errorText: 'net::ERR_ABORTED' });

  const problems = findingsSince(recorder, before, 'Manage Billing Form');
  assert.equal(problems.length, 4);
  assert.ok(problems.every((line) => line.startsWith('Manage Billing Form: ')));
  assert.match(problems[0], /uncaught ReferenceError: contextPath is not defined$/, 'only the first stack line is kept');
  assert.match(problems[2], /HTTP 500/);
  assert.match(problems[3], /script request failed \(net::ERR_ABORTED\)/);
  // The pre-existing error belongs to an earlier item, not this one.
  assert.ok(!problems.some((line) => line.includes('old')));
});

test('an item that raised nothing produces no findings', () => {
  const recorder = createRecorder();
  const before = snapshotRecorder(recorder);
  assert.deepEqual(findingsSince(recorder, before, 'Usage Report'), []);
});

test('the error-page pattern matches what CARLOS actually renders, and not prose', () => {
  for (const body of [
    'CARLOS has encountered an unexpected error',
    'HTTP Status 500 - Internal Server Error',
    'Exception Report',
    'There is no Action mapped for namespace [/] and action name [patientlistbyappt]',
    'Whitelabel Error Page',
  ]) {
    assert.ok(ERROR_PAGE_RE.test(body), `should be treated as an error page: ${body}`);
  }
  for (const body of [
    'Billing Report Centre',
    'No results found for this date range',
    'Error messages will appear here',
    'HTTP Status 200',
  ]) {
    assert.ok(!ERROR_PAGE_RE.test(body), `should not be treated as an error page: ${body}`);
  }
});

test('every skip rule names a reason, so nothing is quietly excluded', () => {
  for (const [surface, rules] of [['admin', adminSkips], ['master record', masterSkips], ['chart', chartSkips]]) {
    assert.ok(rules.length > 0, `${surface} should declare its skips explicitly`);
    for (const rule of rules) {
      assert.ok(rule.match instanceof RegExp, `${surface} skip needs a pattern`);
      assert.ok(rule.reason && rule.reason.length > 25,
        `${surface} skip ${rule.match} must explain why that page is not opened`);
    }
  }
});

test('the admin skips catch the three items that are not read-only', () => {
  const matches = (rules, text) => rules.some((rule) => rule.match.test(text));
  assert.ok(matches(adminSkips, 'Update Drugref'), 'a 15-60 minute DrugRef rebuild must not run inside this check');
  assert.ok(matches(adminSkips, 'Database/Document Download'), 'a full backup archive must not be downloaded');
  assert.ok(matches(adminSkips, 'Log Out'), 'logging out would end the session the remaining items need');
  // Everything else must still be opened.
  for (const text of ['Manage Billing Form', 'Usage Report', 'System Messages', 'Add a Provider Record', 'Merge Patient Records']) {
    assert.ok(!matches(adminSkips, text), `${text} should be opened, not skipped`);
  }
});

test('the chart skips exclude the writing "+" controls and nothing else', () => {
  const matches = (text) => chartSkips.some((rule) => rule.match.test(text));
  assert.ok(matches('+'), 'the add control opens a writing form');
  assert.ok(matches('Add Measurement'), 'an add control opens a writing form');
  for (const text of ['Allergies', 'Prescriptions', 'Preventions', 'Labs', 'Dx Registry', 'Consultations']) {
    assert.ok(!matches(text), `${text} is a read-only module link and must be opened`);
  }
});

test('the master-record skips keep the destructive items out of a read-only check', () => {
  const matches = (text) => masterSkips.some((rule) => rule.match.test(text));
  assert.ok(matches('Delete'), 'a delete must not run in a read-only check');
  assert.ok(matches('Merge'), 'merge has its own check and mutates two records');
  for (const text of ['Appt. History', 'Billing History', 'E-Chart', 'Prevention', 'Tickler', 'Documents']) {
    assert.ok(!matches(text), `${text} should be opened, not skipped`);
  }
});

test('catalogueLinks is evaluated in the browser, so it takes no server-side state', () => {
  // It runs through page.$$eval: the function body must be self-contained, or it
  // throws a ReferenceError inside the page instead of cataloguing anything.
  const source = catalogueLinks.toString();
  assert.match(source, /\$\$eval/);
  assert.doesNotMatch(source, /require\(/, 'the browser-side body cannot require anything');
});
