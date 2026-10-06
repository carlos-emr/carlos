/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  STATUS_FILTERS, TYPE_FILTERS, assertPartitions,
} = require('./inboxhub-filters-playwright-checks');

const SOURCE = fs.readFileSync(path.join(__dirname, 'inboxhub-filters-playwright-checks.js'), 'utf8');
const INBOX_DIR = path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'web', 'inboxhub');
const FORM_JSP = fs.readFileSync(path.join(INBOX_DIR, 'InboxhubForm.jsp'), 'utf8');
const LIST_JSP = fs.readFileSync(path.join(INBOX_DIR, 'InboxhubListMode.jsp'), 'utf8');

/*
 * assertPartitions is the whole check: the browser work only gathers the row
 * identities it compares. If it is wrong in the permissive direction the check
 * reports green on an Inbox whose filters do nothing, which is the exact failure
 * it was written to catch -- so each way it can be wrong gets a test.
 */

test('a correct partition passes', () => {
  const whole = ['DOC:1', 'DOC:2', 'HL7:3', 'HRM:4'];
  assert.doesNotThrow(() => assertPartitions(whole, [
    { title: 'Documents', rows: ['DOC:1', 'DOC:2'] },
    { title: 'Labs', rows: ['HL7:3'] },
    { title: 'HRM reports', rows: ['HRM:4'] },
  ], 'Type filters'));
});

test('a filter that is IGNORED is caught, and named as such', () => {
  // The common failure, and the one a row COUNT cannot see: every value returns
  // the same plausible-looking set.
  const whole = ['DOC:1', 'HL7:2'];
  assert.throws(
    () => assertPartitions(whole, [
      { title: 'Documents', rows: ['DOC:1', 'HL7:2'] },
      { title: 'Labs', rows: ['DOC:1', 'HL7:2'] },
    ], 'Type filters'),
    /returned by more than one filter value.*being ignored rather than applied/s,
  );
});

test('an over-restrictive filter is caught', () => {
  // Every value applied, but a row that belongs to one of them is in none --
  // so a clinician narrowing the list would never see it.
  const whole = ['DOC:1', 'DOC:2', 'HL7:3'];
  assert.throws(
    () => assertPartitions(whole, [
      { title: 'Documents', rows: ['DOC:1'] },
      { title: 'Labs', rows: ['HL7:3'] },
    ], 'Type filters'),
    /in the unfiltered list but in none of the filter values/,
  );
});

test('a filter that widens the list is caught', () => {
  // Narrowing must never produce a row the unfiltered list did not contain.
  assert.throws(
    () => assertPartitions(['DOC:1'], [
      { title: 'Documents', rows: ['DOC:1', 'DOC:9'] },
    ], 'Type filters'),
    /which the unfiltered list does not contain/,
  );
});

test('an empty whole with empty parts does not vacuously pass anything real', () => {
  // It does pass -- there is nothing to partition -- which is why the check
  // refuses to run at all on an empty Inbox rather than reporting success.
  assert.doesNotThrow(() => assertPartitions([], [], 'Type filters'));
  assert.match(SOURCE, /the Inbox is empty on this dataset, so a filter partition cannot be checked/);
  assert.match(SOURCE, /throw new SkipCheck/);
});

test('the family name reaches the failure message, so a run says which filter broke', () => {
  assert.throws(
    () => assertPartitions(['X:1'], [{ title: 'New', rows: [] }], 'Review status'),
    /^Error: Review status:/,
  );
});

test('every type filter this check drives is one the toolbar renders', () => {
  for (const filter of TYPE_FILTERS) {
    const id = filter.id.replace('#', '');
    assert.ok(LIST_JSP.includes(`id="${id}"`), `the inbox toolbar has no ${filter.id} control`);
    assert.ok(LIST_JSP.includes(`filterByType('${filter.type}')`),
      `${filter.id} does not pass '${filter.type}' to filterByType`);
  }
  // filterAll is what the check clicks to clear, so it has to exist too.
  assert.ok(LIST_JSP.includes('id="filterAll"'));
  assert.ok(LIST_JSP.includes("filterByType('ALL')"));
});

test('the type a filter names is the type its rows carry', () => {
  // filterByType sets the query checkboxes; each row records data-lab-type. The
  // check asserts they agree, which is only meaningful if both really exist.
  assert.match(LIST_JSP, /data-lab-type="\$\{carlos:forHtmlAttribute\(labResult\.labType\)\}"/);
  assert.match(LIST_JSP, /data-segment-id="\$\{carlos:forHtmlAttribute\(labResult\.segmentID\)\}"/);
  assert.match(FORM_JSP, /btnDoc\.checked = \(type === 'DOC'\)/);
  assert.match(FORM_JSP, /btnLab\.checked = \(type === 'HL7'\)/);
  assert.match(FORM_JSP, /btnHRM\.checked = \(type === 'HRM'\)/);
});

test('every review status this check drives is one the form renders, with its value', () => {
  for (const filter of STATUS_FILTERS) {
    const id = filter.id.replace('#', '');
    assert.ok(FORM_JSP.includes(`id="${id}"`), `the inbox form has no ${filter.id} radio`);
    assert.ok(FORM_JSP.includes(`changeValueElementByName('query.status', '${filter.value}')`),
      `${filter.id} does not write '${filter.value}' into query.status`);
  }
  // And "All" writes the empty value the partition is measured against.
  assert.ok(FORM_JSP.includes("changeValueElementByName('query.status', '')"));
});

test('the completion signal is the one the page actually uses', () => {
  // Reading rows before the paged load finishes returns a partial set, which
  // would fail the union assertion for a reason that is not a defect.
  assert.match(FORM_JSP, /stopInboxhubListProgress[\s\S]{0,400}?inboxhubFormSearchBtn'\)\.prop\('disabled', false\)/);
  assert.match(SOURCE, /#inboxhubFormSearchBtn/);
  assert.match(SOURCE, /element && !element\.disabled/);
});

test('the check reads row identity, not a row count', () => {
  // A count cannot distinguish an ignored filter from a working one.
  assert.match(SOURCE, /data-lab-type/);
  assert.match(SOURCE, /data-segment-id/);
});

test('the Inbox is opened by clicking the schedule control', () => {
  assert.match(SOURCE, /schedulePage\.locator\('#inboxLink'\)/);
  assert.ok(!/page\.goto\(/.test(SOURCE), 'entering by address would skip the opener');
  assert.ok(!/Inboxhub\?method=/.test(SOURCE), 'the check must not name the route behind the control');
});

test('the check never acknowledges, files or forwards anything', () => {
  for (const control of ['topFBtn', 'topFileBtn', 'ackToggle', 'submitForward', 'submitFile']) {
    assert.ok(!SOURCE.includes(control), `this check is read-only; it must not touch ${control}`);
  }
  assert.ok(!/createSqlRunner/.test(SOURCE), 'it needs no database access to prove a partition');
});


test('only a non-interactive DataTables empty placeholder may lack a result identity', async () => {
  const { shownRows } = require('./inboxhub-filters-playwright-checks');
  for (const [placeholder, interactive, shouldFail] of [[true, false, false], [false, false, true], [true, true, true]]) {
    const row = {
      getAttribute: () => null, children: [{}], textContent: 'No data available in table',
      firstElementChild: { matches: selector => { assert.equal(selector, 'td.dataTables_empty'); return placeholder; } },
      querySelector: () => interactive ? {} : null,
    };
    const page = { $$eval: async (_selector, read) => read([row]) };
    if (shouldFail) await assert.rejects(shownRows(page), /no data-segment-id/);
    else assert.deepEqual(await shownRows(page), []);
  }
});


const {assertReviewStatusCoverage, assertHrmCount} = require('./inboxhub-filters-playwright-checks');
const reviewParts = (fresh, ack, filed) => [
  {title: 'New', rows: fresh}, {title: 'Acknowledged', rows: ack}, {title: 'Filed', rows: filed},
];
test('HRM signed-off aliases coexist with strict document/lab status partitions', () => {
  assertReviewStatusCoverage(['HRM:1', 'HRM:2', 'DOC:3', 'HL7:4'],
    reviewParts(['HRM:1', 'DOC:3'], ['HRM:2', 'HL7:4'], ['HRM:2']));
});
for (const [name, parts] of [
  ['missing HRM', reviewParts([], [], [])],
  ['ignored HRM filter', reviewParts(['HRM:1'], ['HRM:1'], ['HRM:1'])],
  ['inconsistent signed-off aliases', reviewParts([], ['HRM:1'], [])],
  ['duplicate document', reviewParts(['DOC:2'], ['HRM:1', 'DOC:2'], ['HRM:1'])],
]) {
  test(`review coverage still rejects ${name}`, () => {
    const whole = name === 'duplicate document' ? ['HRM:1', 'DOC:2'] : ['HRM:1'];
    assert.throws(() => assertReviewStatusCoverage(whole, parts));
  });
}
for (const [value, badge, success] of [['1', '1', true], ['0', '1', false], ['1', '0', false]]) {
  test(`HRM totals and badge must match returned rows (${value}/${badge})`, async () => {
    const page = {locator: () => ({inputValue: async () => value, count: async () => 1, innerText: async () => badge})};
    const result = assertHrmCount(page, ['DOC:2', 'HRM:1']);
    if (success) await result;
    else await assert.rejects(result, /HRM/);
  });
}
