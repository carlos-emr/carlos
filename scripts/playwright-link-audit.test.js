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

function catalogue(anchors, baseURI = 'http://carlos.test/carlos/admin/admin.jsp') {
  // The browser-side body reads document.baseURI, which is the resolution base
  // the anonymous-access check needs to place a bare onclick route inside the
  // context path. Supply it the way the page would.
  const page = {
    $$eval: async (selector, fn) => {
      const previous = global.document;
      global.document = { baseURI };
      try {
        return fn(anchors);
      } finally {
        if (previous === undefined) {
          delete global.document;
        } else {
          global.document = previous;
        }
      }
    },
  };
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

test('each surface audit asserts its landing page before cataloguing it', () => {
  // auditCatalogue snapshots findings PER ITEM and measures from the first one
  // onward, so anything the surface's own startup produced -- a pageerror, a
  // failed resource, a script served as text/html, an unanswered confirm() --
  // is recorded and then read by nothing. The audit could report 120 pages
  // opened cleanly while the panel that lists them was itself broken.
  const fs = require('node:fs');
  const path = require('node:path');
  const audits = [
    ['admin-index-links-playwright-checks.js', 'catalogueLinks(adminPage)'],
    ['master-record-tabs-playwright-checks.js', 'catalogueLinks(masterPage)'],
    ['echart-navbar-modules-playwright-checks.js', 'catalogueLinks(chartPage'],
  ];
  for (const [name, catalogueCall] of audits) {
    const source = fs.readFileSync(path.join(__dirname, name), 'utf8');
    const asserted = source.indexOf('assertStrictPage(recorder');
    const catalogued = source.indexOf(catalogueCall);
    assert.ok(asserted > -1, `${name} must assert its landing page`);
    assert.ok(catalogued > -1, `${name}: could not find the catalogue call`);
    assert.ok(asserted < catalogued,
      `${name}: the landing page must be asserted BEFORE the catalogue, or its own startup errors go unread`);
  }
});

test('the tenth-surface audit folds its opening findings into the result', () => {
  // surface-audit opens ten surfaces in one run, so it cannot use one scoped
  // assertStrictPage; it snapshots before each open and attributes what happened
  // to that surface instead. Either shape is fine -- silence is not.
  const fs = require('node:fs');
  const path = require('node:path');
  const source = fs.readFileSync(path.join(__dirname, 'surface-audit-playwright-checks.js'), 'utf8');
  assert.match(source, /const beforeOpen = snapshotRecorder\(recorder\)/);
  assert.match(source, /const openingFindings = findingsSince\(recorder, beforeOpen/);
  assert.match(source, /failures: \[\.\.\.openingFindings, \.\.\.result\.failures\]/);
});

test('fragment hrefs are dropped, but an opener written href="#" keeps its route', async () => {
  const items = await catalogue([
    anchorDouble({ href: '#custom' }, 'Custom'),
    anchorDouble({ href: '#collapseClinical' }, 'Clinical'),
    anchorDouble({ href: '#' }, 'Bare'),
    // The common CARLOS opener shape: no destination in href, all of it in onclick.
    anchorDouble({ href: '#', onclick: "popupPage(600,900,'/carlos/admin/providerAdd')" }, 'Add Provider'),
  ]);
  assert.deepEqual(items.map((item) => item.text), ['Add Provider']);
  assert.equal(items[0].href, '');
  assert.equal(items[0].route, '/carlos/admin/providerAdd');
});

test('each item carries the document it was read from, for resolving its route', async () => {
  const items = await catalogue(
    [anchorDouble({ href: '#', onclick: "popup(500,700,'DemographicEdit?demographic_no=1')" }, 'Edit')],
    'http://carlos.test/carlos/demographic/demographicsearchresults.jsp',
  );
  assert.equal(items[0].baseURI, 'http://carlos.test/carlos/demographic/demographicsearchresults.jsp');
});

/*
 * A fake host page good enough to drive auditCatalogue without a browser.
 *
 * Every item is treated as one that acts IN PLACE: waitForURL settles by
 * rejecting (the page never leaves), which is the common case on the admin
 * surface and the case both tests below are about. onClick lets a test push a
 * browser finding at the moment the item is clicked, so findingsSince attributes
 * it to that item.
 */
function fakeAuditPage(options = {}) {
  const waitForUrlTimeouts = [];
  const screenshots = [];
  let pending = 0;
  // The host page's markup length, so openItem can tell an in-place action from
  // a click that did nothing. `actsInPlace` makes each click change it.
  let markup = 100;
  const page = {
    url: () => 'http://127.0.0.1:8080/carlos/admin',
    locator(selector) {
      return {
        nth: (index) => ({
          textContent: async () => options.textFor(index),
          scrollIntoViewIfNeeded: async () => {},
          click: async () => {
            if (options.actsInPlace) { markup += 1; }
            if (options.onClick) { options.onClick(index); }
          },
        }),
        first: () => ({ inputValue: async () => '' }),
        innerText: async () => 'a page with content',
        evaluate: async () => false,
      };
    },
    // Two different callers evaluate against this page: openItem probes
    // document.body.innerHTML.length, csrfBootstrapFinding asks whether the
    // page reads the token. Answering `markup` to both made the CSRF probe see
    // a truthy value and report a finding for every item.
    evaluate: async (fn) => (String(fn).includes('innerHTML.length') ? markup : false),
    waitForFunction: async (predicate, previousLength) => {
      // openItem's predicate reads document.body.innerHTML.length in the page.
      // Model that here rather than running it against a real DOM.
      if (markup !== previousLength) { return true; }
      throw new Error('timeout');
    },
    async screenshot({ path: outputPath }) { screenshots.push(outputPath); },
    waitForURL(predicate, waitOptions) {
      waitForUrlTimeouts.push(waitOptions.timeout);
      pending += 1;
      // Settle on a COMPRESSED clock so the test does not sit through the real
      // wait. What is asserted is the timeout the audit ASKED FOR, which is the
      // thing the fix changes; the fake's own delay is irrelevant to that.
      return new Promise((_resolve, reject) => {
        setTimeout(() => { pending -= 1; reject(new Error('no navigation')); }, 1);
      });
    },
    waitForLoadState: async () => {},
    goBack: async () => {},
  };
  return {
    page, waitForUrlTimeouts, screenshots, pendingWaiters: () => pending,
  };
}

test('a click that navigates nowhere stops waiting after the navigation-start bound', async () => {
  // waitForURL used to be armed with the FULL per-item timeout while a separate
  // settle timer ended the race at 4s. Every in-place item -- most of the admin
  // surface -- therefore left a live waiter on the page for another 16s whose
  // result nothing reads, and a long sweep ran several of them at once. The
  // bound belongs on waitForURL itself.
  const { auditCatalogue } = require('./lib/playwright-link-audit');
  const items = [{
    text: 'Search', index: 0, selector: 'a', href: '/search', opensPopup: false,
  }];
  // actsInPlace: this test is about the navigation-wait bound, so the item is
  // one that legitimately acts in place rather than an inert one.
  const fake = fakeAuditPage({ textFor: () => 'Search', actsInPlace: true });
  const result = await auditCatalogue({
    context: { pages: () => [] },
    hostPage: fake.page,
    items,
    recorder: createRecorder(),
    labelPrefix: 'admin',
    timeout: 20000,
  });
  assert.deepEqual(result.opened, ['Search']);
  assert.deepEqual(fake.waitForUrlTimeouts, [4000],
    'the navigation wait must carry NAVIGATION_START_TIMEOUT, not the item timeout');
  assert.equal(fake.pendingWaiters(), 0, 'no navigation waiter may outlive the item it belongs to');
});

test('the navigation-start bound never exceeds the item timeout', async () => {
  const { auditCatalogue } = require('./lib/playwright-link-audit');
  const fake = fakeAuditPage({ textFor: () => 'Search', actsInPlace: true });
  await auditCatalogue({
    context: { pages: () => [] },
    hostPage: fake.page,
    items: [{
      text: 'Search', index: 0, selector: 'a', href: '/search', opensPopup: false,
    }],
    recorder: createRecorder(),
    labelPrefix: 'admin',
    timeout: 1500,
  });
  assert.deepEqual(fake.waitForUrlTimeouts, [1500],
    'a caller asking for less than 4s must not be given more');
});

test('two items with the same label get two screenshots, not one overwritten', async () => {
  // catalogueLinks keeps same-text items apart by index on purpose (two admin
  // entries routinely share a label and point at different routes). The
  // screenshot name was built from the label alone, so the second failure
  // overwrote the first and that item's evidence was gone from the artifacts.
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const { auditCatalogue } = require('./lib/playwright-link-audit');
  const screenshotDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-audit-'));
  const recorder = createRecorder();
  const fake = fakeAuditPage({
    textFor: () => 'Manage Billing Form',
    actsInPlace: true,
    onClick: (index) => { recorder.pageErrors.push({ text: `ReferenceError from anchor ${index}` }); },
  });
  const items = [
    {
      text: 'Manage Billing Form', index: 4, selector: 'a', href: '/billing/form', opensPopup: false,
    },
    {
      text: 'Manage Billing Form', index: 9, selector: 'a', href: '/billing/other', opensPopup: false,
    },
  ];
  const result = await auditCatalogue({
    context: { pages: () => [] },
    hostPage: fake.page,
    items,
    recorder,
    labelPrefix: 'admin',
    timeout: 1000,
    screenshotDir,
  });
  assert.equal(result.failures.length, 2, 'both items must report their own finding');
  assert.equal(fake.screenshots.length, 2);
  assert.equal(new Set(fake.screenshots).size, 2,
    `both screenshots landed on the same file: ${fake.screenshots.join(', ')}`);
  for (const item of items) {
    assert.ok(fake.screenshots.some((name) => path.basename(name).startsWith(`admin-${item.index}-`)),
      `no screenshot is attributable to item ${item.index}: ${fake.screenshots.join(', ')}`);
  }
  fs.rmSync(screenshotDir, { recursive: true, force: true });
});

test('a same-tab surface that goes nowhere fails instead of auditing the schedule', async () => {
  // openSurface's non-popup branch armed its load-state waits AFTER the click,
  // against the schedule's own already-loaded document, and asserted no
  // navigation at all. A broken or in-place handler therefore returned the
  // schedule page; catalogueLinks then catalogued the SCHEDULE's links and
  // surface.minimum was satisfied by the schedule's own navigation, so the
  // surface reported a clean audit without ever being opened.
  const { openSurface } = require('./surface-audit-playwright-checks');
  const surface = {
    name: 'same-tab-surface',
    title: 'Same Tab Surface',
    entry: { selector: '#sameTab', popup: false },
  };
  // A control whose click does nothing at all -- the broken handler.
  const inert = {
    count: async () => 1,
    scrollIntoViewIfNeeded: async () => {},
    click: async () => {},
  };
  const schedulePage = {
    url: () => 'http://127.0.0.1:8080/carlos/provider/providercontrol',
    locator: () => ({ first: () => inert }),
    mainFrame: () => ({}),
    waitForEvent: () => new Promise((_r, reject) => {
      setTimeout(() => reject(new Error('no navigation')), 1);
    }),
    waitForLoadState: async () => {},
  };
  await assert.rejects(
    () => openSurface({}, schedulePage, surface, null, 50),
    /without navigating|did not take the schedule anywhere/,
    'a click that navigates nothing must fail the surface, not hand back the schedule',
  );
});

test('a same-tab surface that only reloads the schedule is not treated as opened', async () => {
  // framenavigated alone is satisfied by a same-address reload, which leaves
  // the audit on the schedule just as surely as no navigation at all.
  const { openSurface } = require('./surface-audit-playwright-checks');
  const surface = {
    name: 'same-tab-surface',
    title: 'Same Tab Surface',
    entry: { selector: '#sameTab', popup: false },
  };
  const control = {
    count: async () => 1,
    scrollIntoViewIfNeeded: async () => {},
    click: async () => {},
  };
  const schedulePage = {
    url: () => 'http://127.0.0.1:8080/carlos/provider/providercontrol',
    locator: () => ({ first: () => control }),
    mainFrame: () => ({}),
    waitForEvent: async () => ({}),
    waitForLoadState: async () => {},
  };
  await assert.rejects(
    () => openSurface({}, schedulePage, surface, null, 50),
    /did not take the schedule anywhere/,
    'a same-address reload must not count as opening the surface',
  );
});

test('an opener written as a javascript: href is catalogued, not dropped', async () => {
  // 256 anchors across the webapp write their opener in the href rather than an
  // onclick -- five in admin.jsp alone. They carry no onclick, so reading
  // openers from onclick alone found no route; hasRealHref rejects javascript:
  // (rightly, it is not a destination); and the anchor fell out of the
  // catalogue entirely. The audits then reported a complete sweep of a surface
  // they had never opened part of.
  const items = await catalogue([
    anchorDouble({ href: 'javascript: popupPage( 500, 900, "/carlos/quickBillingBC");' }, 'Quick Billing'),
    anchorDouble({ href: 'javascript: location.href="/carlos/plain"' }, 'Same Tab'),
  ]);
  assert.equal(items.length, 2, 'both javascript: openers must be catalogued');

  const [popup, sameTab] = items;
  assert.equal(popup.text, 'Quick Billing');
  assert.equal(popup.route, '/carlos/quickBillingBC');
  assert.equal(popup.opensPopup, true, 'popupPage in a javascript: href still opens a popup');
  assert.equal(popup.href, '', 'a javascript: href is not a destination to navigate to');

  assert.equal(sameTab.route, '/carlos/plain');
  assert.equal(sameTab.opensPopup, false);
});

test('a javascript: href with no route in it is still not catalogued', async () => {
  // The scheme alone is not a reason to keep an anchor: href="javascript:void(0)"
  // with no onclick goes nowhere, and cataloguing it would put an item in the
  // sweep that can only ever be reported as "opened" without opening anything.
  assert.deepEqual(await catalogue([anchorDouble({ href: 'javascript:void(0)' }, 'Inert')]), []);
});

test('an item whose click does nothing is a finding, not an opened page', async () => {
  // The host page's body is non-empty whatever the click did, so an item that
  // neither navigated nor opened a popup nor changed anything was counted as
  // opened -- and an inert or broken control could make up the per-surface
  // minimum on its own.
  //
  // Navigation is deliberately NOT the test here, unlike the surface audit:
  // plenty of admin items legitimately inject a panel in place. The test is
  // whether anything happened at all.
  const { auditCatalogue } = require('./lib/playwright-link-audit');
  const fake = fakeAuditPage({ textFor: () => 'Inert Control' });
  const result = await auditCatalogue({
    context: { pages: () => [] },
    hostPage: fake.page,
    items: [{
      text: 'Inert Control', index: 0, selector: 'a', href: '/x', opensPopup: false,
    }],
    recorder: createRecorder(),
    labelPrefix: 'admin',
    timeout: 1000,
  });
  assert.deepEqual(result.opened, [], 'a click that did nothing must not count as an opened page');
  assert.equal(result.failures.length, 1);
  assert.match(result.failures[0], /clicking it did nothing/);
});

test('an item that opens an unclassified popup is not reported as inert', async () => {
  // Several CARLOS handlers reach window.open through a helper catalogueLinks'
  // pattern does not name. Those clicks leave the host page untouched, so
  // without counting the context's new page they would trade a false pass for a
  // false failure -- the item works, and the audit would call it broken.
  const { auditCatalogue } = require('./lib/playwright-link-audit');
  const pages = [{}];
  const fake = fakeAuditPage({
    textFor: () => 'Hidden Opener',
    // The click is what opens the popup.
    onClick: () => { pages.push({}); },
  });
  const result = await auditCatalogue({
    context: { pages: () => pages },
    hostPage: fake.page,
    items: [{
      text: 'Hidden Opener', index: 0, selector: 'a', href: '/x', opensPopup: false,
    }],
    recorder: createRecorder(),
    labelPrefix: 'admin',
    timeout: 1000,
  });
  assert.equal(pages.length, 2, 'the double must actually have opened a page, or this test proves nothing');
  assert.deepEqual(result.failures, []);
  assert.deepEqual(result.opened, ['Hidden Opener']);
});

test('the administration shell keeps its route in rel, and that is catalogued', async () => {
  // 124 anchors in the administration left nav are written
  //   <a href='javascript:void(0);' class="xlink" rel="<ctx>/admin/UnLock">
  // and the .xlink handler (leftNav.jspf:906) reads $(this).attr('rel') and
  // loads it into an iframe. href gives nothing -- void(0) has no route -- and
  // there is no onclick, so every one of those was dropped: admin-index-links
  // swept the Administration panel with its primary navigation missing.
  const items = await catalogue([
    anchorDouble({ href: 'javascript:void(0);', rel: '/carlos/admin/UnLock' }, 'Unlock Accounts'),
    anchorDouble({ href: 'javascript:void(0);', rel: '/carlos/admin/ProviderRole' }, 'Provider Roles'),
  ]);
  assert.deepEqual(items.map((item) => [item.text, item.route]), [
    ['Unlock Accounts', '/carlos/admin/UnLock'],
    ['Provider Roles', '/carlos/admin/ProviderRole'],
  ]);
});

test('an ordinary rel keyword is not mistaken for a route', async () => {
  // rel is a standard HTML attribute. Treating every value as a route would put
  // an item in the catalogue for every noopener link on the page, each of them
  // then reported as a route that cannot be reached.
  const items = await catalogue([
    anchorDouble({ href: 'javascript:void(0);', rel: 'noopener' }, 'Noopener'),
    anchorDouble({ href: 'javascript:void(0);', rel: 'noreferrer nofollow' }, 'Nofollow'),
    anchorDouble({ href: 'javascript:void(0);', rel: 'stylesheet' }, 'Stylesheet'),
  ]);
  assert.deepEqual(items, []);
});

test('an onclick route still wins over rel when both are present', async () => {
  // rel is the fallback, not the override: an anchor carrying both is driven by
  // its handler, and the handler reads the onclick.
  const items = await catalogue([
    anchorDouble({
      href: '#',
      onclick: "popupPage(600,900,'/carlos/admin/fromOnclick')",
      rel: '/carlos/admin/fromRel',
    }, 'Both'),
  ]);
  assert.equal(items[0].route, '/carlos/admin/fromOnclick');
});

test('an in-place destination is read from the container, not from the shell around it', async () => {
  // The Administration shell loads a .contentLink route into #dynamic-content
  // by AJAX and an .xlink route into an iframe inside it. The host body still
  // holds the whole shell either way, so reading document.body found plenty of
  // text no matter what came back -- a blank or error destination passed as an
  // opened item.
  const { auditCatalogue } = require('./lib/playwright-link-audit');
  const shellText = 'Administration  Providers  Billing  Reports';
  const panel = { text: '', frames: 0 };
  const page = {
    url: () => 'http://127.0.0.1:8080/carlos/administration',
    locator: (selector) => {
      if (selector === '#dynamic-content') {
        return {
          first: () => ({
            count: async () => 1,
            innerText: async () => panel.text,
            locator: () => ({
              first: () => ({
                count: async () => panel.frames,
                contentFrame: async () => null,
              }),
            }),
          }),
        };
      }
      return {
        nth: () => ({
          textContent: async () => 'Broken Item',
          scrollIntoViewIfNeeded: async () => {},
          click: async () => { panel.text = ''; },
        }),
        first: () => ({ inputValue: async () => '' }),
        innerText: async () => shellText,
        evaluate: async () => false,
      };
    },
    evaluate: async (fn) => (String(fn).includes('innerHTML.length') ? 1 : false),
    waitForFunction: async () => true,
    waitForURL: () => new Promise((_r, reject) => { setTimeout(() => reject(new Error('none')), 1); }),
    waitForLoadState: async () => {},
    goBack: async () => {},
  };
  const result = await auditCatalogue({
    context: { pages: () => [] },
    hostPage: page,
    items: [{
      text: 'Broken Item', index: 0, selector: 'a', route: '/carlos/admin/x', opensPopup: false,
    }],
    recorder: createRecorder(),
    labelPrefix: 'admin',
    inPlaceTarget: '#dynamic-content',
    timeout: 500,
  });
  assert.deepEqual(result.opened, [], 'a blank panel must not count as an opened page');
  assert.equal(result.failures.length, 1);
  assert.match(result.failures[0], /rendered a blank page/);
});

/*
 * resolveControl(): the three strategies the schedule really uses.
 *
 * Review noted this engine drives ten surfaces with no unit coverage of its
 * control resolution, while everything around it is pinned. The strategies are
 * not interchangeable -- a label regex that is end-anchored stops matching the
 * moment <oscar:newLab> appends its "<sup>N</sup>" count, and the title
 * strategy exists only because CSS has no attribute-regex -- so each gets a
 * case here.
 */
test('a selector-strategy surface resolves to the first match of its selector', async () => {
  const { resolveControl } = require('./surface-audit-playwright-checks');
  const calls = [];
  const page = {
    locator: (selector) => { calls.push(selector); return { first: () => ({ selector }) }; },
  };
  const control = await resolveControl(page, { entry: { selector: '#inboxLink', popup: true } });
  assert.deepEqual(calls, ['#inboxLink']);
  assert.equal(control.selector, '#inboxLink');
});

test('a label-strategy surface matches on text that carries a live count beside it', async () => {
  // <oscar:newLab> appends "<sup>3</sup>" inside the anchor, so "Inbox" becomes
  // "Inbox3" the moment a lab is waiting. An end-anchored regex would stop
  // finding the control exactly when the clinic has results to read.
  const { resolveControl } = require('./surface-audit-playwright-checks');
  let filterArg = null;
  const page = {
    locator: () => ({
      filter: (options) => { filterArg = options; return { first: () => ({ matched: true }) }; },
    }),
  };
  const entry = { label: /^\s*Inbox/i, popup: true };
  const control = await resolveControl(page, { entry });
  assert.equal(control.matched, true);
  assert.equal(filterArg.hasText, entry.label);
  assert.ok(entry.label.test('Inbox3'), 'the manifest pattern must survive a live count');
  assert.ok(entry.label.test('  Inbox '));
});

test('a title-strategy surface addresses the anchor by position, never by guessing', async () => {
  // CSS has no attribute-regex, so the titles are read once and the match is
  // addressed by its index in the SAME list -- not by clicking whatever came
  // first, which is how an icon-only control gets confused with another.
  const { resolveControl } = require('./surface-audit-playwright-checks');
  const titles = ['Open the schedule', 'Edit your personal setting', 'Scratch pad'];
  const page = {
    $$eval: async () => titles,
    locator: (selector) => ({ nth: (index) => ({ selector, index }) }),
  };
  const control = await resolveControl(page, { entry: { title: /Edit your personal setting/i, popup: true } });
  assert.equal(control.index, 1, 'the index must be the position in the list the titles came from');
  assert.equal(control.selector, 'a[title]');
});

test('a title-strategy surface that matches nothing resolves to null, not to the first anchor', async () => {
  // The null is what openSurface turns into a SkipCheck (optional) or a
  // failure (required). Returning a locator here would click something
  // arbitrary and report the wrong surface as opened.
  const { resolveControl } = require('./surface-audit-playwright-checks');
  const page = {
    $$eval: async () => ['Open the schedule', 'Scratch pad'],
    locator: () => ({ nth: () => ({ wrong: true }) }),
  };
  assert.equal(await resolveControl(page, { entry: { title: /nothing matches this/i, popup: true } }), null);
});
