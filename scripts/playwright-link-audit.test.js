/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');

const {
  ERROR_PAGE_RE, catalogueLinks, dedupe, findingsSince, itemLocator, snapshotRecorder,
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

/*
 * Identity, not text. dedupe() deliberately keeps two items that share a label
 * but point at different routes -- and that is worth nothing if the click then
 * resolves by text, because both audits would open the first one and the second
 * route would never be visited while the run reported two successes.
 */

test('a catalogued item is clicked by position, never by its label', () => {
  const clicked = [];
  const fakePage = {
    locator(selector) {
      return {
        nth(index) {
          clicked.push(`${selector}#${index}`);
          return { selector, index };
        },
      };
    },
  };
  const first = { text: 'Manage Billing Form', index: 4, selector: 'a' };
  const second = { text: 'Manage Billing Form', index: 9, selector: 'a' };
  itemLocator(fakePage, first);
  itemLocator(fakePage, second);
  assert.deepEqual(clicked, ['a#4', 'a#9'],
    'two same-text items must resolve to two different anchors');
});

test('an item that did not come from catalogueLinks cannot be clicked', () => {
  // A hand-written item has no index, so locating it would fall back to "the
  // first anchor" -- exactly the bug this replaced. Fail loudly instead.
  const fakePage = { locator: () => ({ nth: () => ({}) }) };
  assert.throws(() => itemLocator(fakePage, { text: 'Search' }), /no index/);
  assert.throws(() => itemLocator(fakePage, { text: 'Search', index: -1 }), /no index/);
});

test('the scoped selector travels with the item, so the index means the same list', () => {
  // The eChart navbar catalogues '#leftNavBar a, #rightNavBar a'. An index taken
  // from that list addresses a different anchor in the page-wide 'a' list.
  const fakePage = {
    locator: (selector) => ({ nth: (index) => ({ selector, index }) }),
  };
  const located = itemLocator(fakePage, { text: 'Allergies', index: 2, selector: '#leftNavBar a, #rightNavBar a' });
  assert.equal(located.selector, '#leftNavBar a, #rightNavBar a');
  assert.equal(located.index, 2);
});

test('an unexpected dialog on an audited page is a finding, not silence', () => {
  // A page that starts asking for confirmation blocks the user-facing flow. The
  // snapshot used to omit unexpectedDialogs entirely, so the audit stayed green
  // through exactly that break.
  const recorder = createRecorder();
  const before = snapshotRecorder(recorder);
  assert.equal(before.unexpectedDialogs, 0, 'the snapshot must count dialogs');
  recorder.unexpectedDialogs.push({ label: 'admin:Manage Billing Form', type: 'confirm', text: 'Are you sure?' });
  const problems = findingsSince(recorder, before, 'Manage Billing Form');
  assert.equal(problems.length, 1);
  assert.match(problems[0], /Manage Billing Form: raised an unexpected confirm dialog/);
});

test('a dialog raised before the snapshot belongs to the previous item', () => {
  const recorder = createRecorder();
  recorder.unexpectedDialogs.push({ label: 'earlier', type: 'alert', text: 'old' });
  const before = snapshotRecorder(recorder);
  assert.deepEqual(findingsSince(recorder, before, 'Issue Editor'), []);
});

/*
 * What the catalogue classifies, and what it therefore opens.
 *
 * Both of these were silent: an opener the catalogue read as same-tab sent the
 * audit down the navigation branch, where it waited for a navigation that never
 * came and then read the UNCHANGED host page -- reporting the opener's own
 * content as the item's destination, which passes for every broken popup. And a
 * route shape the extractor did not recognise on an href="#" anchor dropped the
 * item from the catalogue entirely, so the page was never opened while the run
 * still reported a full sweep.
 */
function anchorDouble(attributes, text) {
  return {
    textContent: text,
    getAttribute: (name) => (Object.prototype.hasOwnProperty.call(attributes, name) ? attributes[name] : null),
  };
}

function catalogue(anchors) {
  const page = { $$eval: async (selector, fn) => fn(anchors) };
  return catalogueLinks(page);
}

test('window.open and target="_blank" are classified as popups, like popupPage()', async () => {
  const items = await catalogue([
    anchorDouble({ href: '#', onclick: "popupPage(600,900,'/carlos/admin/providerAdd')" }, 'Add Provider'),
    anchorDouble({ href: '#', onclick: "window.open('/carlos/report/reportIndex')" }, 'Reports'),
    anchorDouble({ href: '/carlos/oscarMessenger/DisplayMessages', target: '_blank' }, 'Messenger'),
    anchorDouble({ href: '/carlos/admin/systemMessage' }, 'System Messages'),
  ]);
  assert.deepEqual(items.map((item) => [item.text, item.opensPopup]), [
    ['Add Provider', true],
    ['Reports', true],
    ['Messenger', true],
    ['System Messages', false],
  ]);
});

test('relative and bare onclick routes are catalogued, not only absolute ones', async () => {
  const items = await catalogue([
    anchorDouble({ href: '#', onclick: "popupPage(700,1000,'../encounter/IncomingEncounter?providerNo=999998')" }, 'Encounter'),
    anchorDouble({ href: '#', onclick: "popup(500,700,'DemographicEdit?demographic_no=1')" }, 'Edit Demographic'),
    anchorDouble({ href: '#', onclick: "popup(500,700,'viewformwcb?formId=3')" }, 'WCB Form'),
    anchorDouble({ href: '#', onclick: "popupPage(600,900,'/carlos/billing/CA/ON/billingON')" }, 'Billing'),
  ]);
  assert.deepEqual(items.map((item) => item.route), [
    '../encounter/IncomingEncounter?providerNo=999998',
    'DemographicEdit?demographic_no=1',
    'viewformwcb?formId=3',
    '/carlos/billing/CA/ON/billingON',
  ]);
});

test('a window name or a feature string is not mistaken for a route', async () => {
  // popupPage()'s own arguments sit in the same quoted list. Reporting one of
  // them as a route would make every opener look like it resolved to something,
  // and would pull anchors with no destination at all into the sweep.
  const items = await catalogue([
    anchorDouble({ href: '#', onclick: "showHideDetail('_blank')" }, 'Toggle Detail'),
    anchorDouble({ href: '#', onclick: "resizeTo('width=600')" }, 'Resize'),
    anchorDouble({ href: '#', onclick: 'return false;' }, 'Inert'),
  ]);
  assert.deepEqual(items, []);
});

test('the navigation wait is armed before the click, not after it', () => {
  // waitForLoadState() asked for after the click resolves instantly against the
  // document still on screen, so the audit read the opener's body and reported
  // it as this item's destination -- a pass for every broken page.
  const source = require('node:fs').readFileSync(require.resolve('./lib/playwright-link-audit'), 'utf8');
  const openItem = source.slice(source.indexOf('async function openItem'), source.indexOf('Click every item, assert the destination'));
  const armed = openItem.indexOf("waitForURL((url) => String(url) !== before");
  const clicked = openItem.indexOf('await link.click({ timeout });');
  assert.ok(armed > -1, 'the same-tab branch must arm a navigation wait');
  assert.ok(clicked > armed, 'the navigation wait must be created before the click');
  // And the page is relabelled once it navigates, or a later assertStrictPage
  // scoped to this item's label finds nothing recorded under it and passes.
  assert.match(openItem, /relabelStrictPage\(hostPage, label\)/);
});

test('a hostile onclick cannot hang the catalogue inside the page', async () => {
  // CodeQL js/redos on the bare-path pattern: with '/' allowed inside the
  // segment class, `(?:\/[^...]*)*` could split the same string exponentially
  // many ways, so an unterminated quote full of slashes backtracked forever.
  // The catalogue runs inside the browser, so that is a hang rather than a
  // failure -- and onclick text can carry a name somebody typed into an eForm.
  const hostile = `popupPage(600,900,"0${'/'.repeat(64)}`;
  const started = Date.now();
  const items = await catalogue([anchorDouble({ href: '#', onclick: hostile }, 'Hostile')]);
  const elapsed = Date.now() - started;
  assert.ok(elapsed < 1000, `cataloguing took ${elapsed}ms; the route pattern is backtracking`);
  // Unterminated, so there is no route to find -- the point is that it returns.
  assert.deepEqual(items, []);
});
