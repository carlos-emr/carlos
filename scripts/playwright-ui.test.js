/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const { EventEmitter } = require('node:events');
const { clickDownloadsOrOpens, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { createRecorder, wireStrictPage, assertStrictPage } = require('./lib/playwright-harness');

function eventPage(text = 'Working page') {
  const page = Object.assign(new EventEmitter(), {
    url: () => 'https://carlos.test/carlos/page',
    waitForLoadState: async () => {},
    waitForURL: async () => {},
    locator: () => ({ innerText: async () => text }),
  });
  const frame = {};
  page.mainFrame = () => frame;
  return page;
}
function control(click) { return { scrollIntoViewIfNeeded: async () => {}, click }; }
function noOutcomeListeners(page, context) {
  assert.equal(context.listenerCount('page'), 0, 'a later popup must not satisfy the old check');
  assert.equal(page.listenerCount('framenavigated'), 0);
  assert.equal(page.listenerCount('download'), 0);
}

for (const navigates of [true, false]) {
  test(`a popup URL is read after navigation (navigation succeeds: ${navigates})`, async () => {
    const context = new EventEmitter();
    const page = eventPage();
    const popup = eventPage();
    let url = 'about:blank';
    popup.url = () => url;
    popup.waitForURL = async predicate => {
      if (!navigates) throw new Error('timeout');
      url = 'https://carlos.test/carlos/print';
      assert.equal(predicate(url), true);
    };
    const result = await clickDownloadsOrOpens(page, control(async () => context.emit('page', popup)), { context });
    assert.equal(result.url, navigates ? 'https://carlos.test/carlos/print' : 'about:blank');
    assert.equal(result.page, popup);
    noOutcomeListeners(page, context);
  });
}

test('a download wins and a later popup cannot inherit its recorder', async () => {
  const context = new EventEmitter();
  const page = eventPage();
  const recorder = createRecorder();
  const url = 'https://carlos.test/carlos/print';
  const download = { url: () => url };
  const result = await clickDownloadsOrOpens(page, control(async () => page.emit('download', download)), { context, recorder });
  assert.equal(result.kind, 'download');
  assert.equal(result.url, url);
  assert.equal(result.page, null);
  noOutcomeListeners(page, context);
  const later = eventPage();
  context.emit('page', later);
  assert.equal(later.listenerCount('pageerror'), 0);
});

test('same-address main-frame navigation counts, subframes do not, and errors use the new label', async () => {
  const context = new EventEmitter();
  const page = eventPage();
  const recorder = createRecorder();
  wireStrictPage(page, 'login', recorder);
  const result = await clickOpensPopupOrNavigates(page, control(async () => {
    page.emit('framenavigated', {});
    assert.equal(page.listenerCount('framenavigated'), 1, 'subframe must not settle the wait');
    page.emit('framenavigated', page.mainFrame());
  }), { context, recorder, label: 'patient-search' });
  assert.equal(result.isPopup, false);
  assert.equal(result.page, page);
  noOutcomeListeners(page, context);
  page.emit('pageerror', new Error('contextPath is not defined'));
  assert.throws(() => assertStrictPage(recorder, ['patient-search']), /contextPath is not defined/);
  const later = eventPage();
  context.emit('page', later);
  assert.equal(later.listenerCount('pageerror'), 0);
});

for (const helper of [clickOpensPopupOrNavigates, clickDownloadsOrOpens]) {
  test(`${helper.name} removes all listeners when the click fails`, async () => {
    const context = new EventEmitter();
    const page = eventPage();
    const recorder = createRecorder();
    await assert.rejects(helper(page, control(async () => { throw new Error('hidden control'); }),
      { context, recorder, timeout: 10 }), /hidden control/);
    noOutcomeListeners(page, context);
    const later = eventPage();
    context.emit('page', later);
    assert.equal(later.listenerCount('pageerror'), 0);
    // The old deadline must not reject unobserved after the failed click.
    await new Promise(resolve => setTimeout(resolve, 25));
  });
  test(`${helper.name} times out explicitly and removes its listeners`, async () => {
    const context = new EventEmitter();
    const page = eventPage();
    await assert.rejects(helper(page, control(async () => {}), { context, label: 'inert', timeout: 10 }),
      /inert: clicking .* within 10ms/);
    noOutcomeListeners(page, context);
  });
  test(`${helper.name} records first-document errors synchronously before click resolves`, async () => {
    const context = new EventEmitter();
    const page = eventPage();
    const popup = eventPage();
    const recorder = createRecorder();
    const result = await helper(page, control(async () => {
      context.emit('page', popup);
      popup.emit('pageerror', new Error('startup handler failed'));
    }), { context, recorder, label: 'early-popup' });
    assert.equal(result.page, popup);
    assert.throws(() => assertStrictPage(recorder), /startup handler failed/);
    noOutcomeListeners(page, context);
  });
}

test('a failed destination closes its popup but preserves a same-tab host', async () => {
  for (const isPopup of [true, false]) {
    const context = new EventEmitter();
    const destination = eventPage('HTTP Status 500');
    let closed = false;
    destination.close = async () => { closed = true; };
    const page = isPopup ? eventPage() : destination;
    await assert.rejects(clickOpensPopupOrNavigates(page, control(async () => {
      if (isPopup) context.emit('page', destination);
      else page.emit('framenavigated', page.mainFrame());
    }), { context }), /rendered an error page/);
    assert.equal(closed, isPopup);
    noOutcomeListeners(page, context);
  }
});

/*
 * pickDate.
 *
 * The helper used to locate days with `.flatpickr-day[data-date="..."]`.
 * Flatpickr writes no such attribute -- its day factory sets an `aria-label`
 * rendered through the locale's ariaDateFormat and hangs the Date itself off
 * the element as `dateObj` -- so that locator matched nothing, ever, and the
 * shared date picker could not be used by any check.
 */
function flatpickrDouble(shownDays, options = {}) {
  const clicked = [];
  const steps = [];
  const dayElements = () => shownDays().map((entry) => ({
    dateObj: entry.disabled === 'not-a-date' ? null : new Date(entry.year, entry.month - 1, entry.day),
    classList: { contains: (name) => (name === 'flatpickr-disabled' ? Boolean(entry.disabled) : Boolean(entry[name])) },
  }));
  const days = {
    evaluateAll: async (fn, argument) => fn(dayElements(), argument),
    nth: (index) => ({ click: async () => clicked.push(index) }),
  };
  const calendar = {
    waitFor: async () => {},
    locator: (selector) => ({
      isVisible: async () => options.arrowsVisible !== false,
      click: async () => {
        steps.push(selector.includes('next') ? 1 : -1);
        if (options.onStep) { options.onStep(selector.includes('next') ? 1 : -1); }
      },
    }),
    evaluate: async (fn) => fn({
      querySelector: () => {
        const current = shownDays().find((entry) => !entry.prevMonthDay && !entry.nextMonthDay);
        return current ? { dateObj: new Date(current.year, current.month - 1, current.day) } : null;
      },
    }),
  };
  const input = {
    scrollIntoViewIfNeeded: async () => {},
    click: async () => {},
    inputValue: async () => options.value || '2026-09-20',
    // flatpickr hangs its instance off the input; pickDate reads selectedDates
    // from there because the FORMATTED value differs field to field across
    // CARLOS and cannot be compared against an ISO date. `selectedIso` lets a
    // test say the picker ended up on a different day than was asked for.
    evaluate: async (fn) => fn({
      _flatpickr: options.selectedIso === null ? undefined : {
        selectedDates: [new Date(`${options.selectedIso || options.value || '2026-09-20'}T00:00:00`)],
      },
    }),
  };
  const page = {
    locator: (selector) => {
      if (selector === '.flatpickr-calendar.open') { return calendar; }
      if (selector === '.flatpickr-day') { return days; }
      return input;
    },
  };
  // The calendar's own day list is reached through calendar.locator, so route it.
  calendar.locator = ((original) => (selector) => (selector === '.flatpickr-day' ? days : original(selector)))(calendar.locator);
  return {
    page, input, clicked, steps,
  };
}

test('pickDate selects the requested day by its flatpickr dateObj, not a data-date attribute', async () => {
  const { pickDate } = require('./lib/playwright-ui');
  const september = [];
  for (let day = 1; day <= 30; day += 1) {
    september.push({ year: 2026, month: 9, day });
  }
  const double = flatpickrDouble(() => september, { value: '2026-09-20' });
  const value = await pickDate(double.page, '#appointment_date', '2026-09-20', { timeout: 50 });
  assert.equal(value, '2026-09-20');
  // Index 19 is the 20th, and nothing else was clicked.
  assert.deepEqual(double.clicked, [19]);
  assert.deepEqual(double.steps, []);
});

test('pickDate refuses to click a different day when the date is unreachable', async () => {
  const { pickDate } = require('./lib/playwright-ui');
  // Every day disabled: the old fallback selector clicked whatever came first,
  // so a date-sensitive check passed against the wrong date and proved nothing.
  const days = [{
    year: 2026, month: 9, day: 20, disabled: true,
  }];
  const double = flatpickrDouble(() => days, { arrowsVisible: false });
  await assert.rejects(
    () => pickDate(double.page, '#appointment_date', '2026-09-20', { timeout: 50 }),
    /never offered 2026-09-20 as a selectable day/,
  );
  assert.deepEqual(double.clicked, []);
});

test('pickDate walks to the target month and stops rather than stepping forever', async () => {
  const { pickDate } = require('./lib/playwright-ui');
  let month = 9;
  const shown = () => [{ year: 2026, month, day: 1 }, { year: 2026, month, day: 2 }];
  const double = flatpickrDouble(shown, {
    arrowsVisible: true,
    onStep: (delta) => { month += delta; },
    value: '2026-11-02',
  });
  const value = await pickDate(double.page, '#appointment_date', '2026-11-02', { timeout: 50 });
  assert.equal(value, '2026-11-02');
  // Forward twice, September -> November, then the second day of that month.
  assert.deepEqual(double.steps, [1, 1]);
  assert.deepEqual(double.clicked, [1]);
});

test('pickDate gives up after a bounded number of month steps', async () => {
  const { pickDate } = require('./lib/playwright-ui');
  // A maxDate the arrow silently refuses to cross: the month never changes.
  const double = flatpickrDouble(() => [{ year: 2026, month: 9, day: 1 }], { arrowsVisible: true });
  await assert.rejects(
    () => pickDate(double.page, '#appointment_date', '2030-01-01', { timeout: 50, maxMonthSteps: 3 }),
    /after 3 month step\(s\)/,
  );
  assert.equal(double.steps.length, 3);
  assert.deepEqual(double.clicked, []);
});

test('pickDate reads the calendar rather than any attribute flatpickr does not write', () => {
  const source = require('node:fs').readFileSync(require.resolve('./lib/playwright-ui'), 'utf8');
  const pickDateSource = source.slice(source.indexOf('async function pickDate'), source.indexOf('async function dataTableRows'));
  assert.ok(!/data-date/.test(pickDateSource),
    'flatpickr writes no data-date; a selector using one matches nothing and the helper can never click');
  assert.match(pickDateSource, /element\.dateObj/);
});

test('the DataTables wrapper fallback is only built for a bare id selector', () => {
  // `${selector}_wrapper` is only a valid selector when the table was named by
  // an id. For 'table.dt' or '#outer .dt' the concatenation builds something
  // that matches nothing, or throws inside querySelector -- and a throw makes
  // waitForFunction fail rather than fall back, so the caller sees a timeout
  // instead of the table it asked for.
  const source = require('node:fs').readFileSync(require.resolve('./lib/playwright-ui'), 'utf8');
  const helper = source.slice(source.indexOf('async function dataTableRows'));
  assert.match(helper, /\/\^#\[A-Za-z\]\[\\w-\]\*\$\/\.test\(selector\)/);
  // And the id pattern itself does what it claims.
  const idOnly = /^#[A-Za-z][\w-]*$/;
  assert.ok(idOnly.test('#auditLog'));
  assert.ok(!idOnly.test('table.dt'));
  assert.ok(!idOnly.test('#outer .dt'));
  assert.ok(!idOnly.test('#a > #b'));
});

test('a failed navigation names the page by path, never by its query string', () => {
  // CARLOS puts PHI-correlating identifiers in the query: the Master Record is
  // demographiccontrol?demographic_no=NNN, and demographic-edit-update calls
  // clickAndAwaitReload on exactly that page. runCheck() writes a thrown
  // message to stdout AND into RESULT_JSON, which CI archives, so the whole
  // address in this message put a patient key into the artifacts of every
  // failed save.
  const { pathOnly } = require('./lib/playwright-ui');
  assert.equal(
    pathOnly('http://127.0.0.1:8080/carlos/demographic/demographiccontrol?demographic_no=42&displaymode=edit'),
    'http://127.0.0.1:8080/carlos/demographic/demographiccontrol',
  );
  assert.equal(pathOnly('http://host/carlos/x#frag'), 'http://host/carlos/x');
  assert.equal(pathOnly('http://host/carlos/x'), 'http://host/carlos/x');
  // Not absolute, and the degenerate values a page mid-teardown can hand back.
  assert.equal(pathOnly('about:blank'), 'about:blank');
  assert.equal(pathOnly('/carlos/x?demographic_no=42'), '/carlos/x');
  assert.equal(pathOnly(''), '');
  assert.equal(pathOnly(null), '');
  assert.equal(pathOnly(undefined), '');

  // And the message actually uses it, rather than the raw url.
  const source = require('node:fs').readFileSync(require.resolve('./lib/playwright-ui'), 'utf8');
  const helper = source.slice(source.indexOf('async function clickAndAwaitReload'));
  const body = helper.slice(0, helper.indexOf('\n}\n'));
  assert.match(body, /pathOnly\(watchPage\.url\(\)\)/);
  assert.ok(!/\$\{watchPage\.url\(\)\}/.test(body),
    'the raw url must not be interpolated into a message runCheck() archives');
});



test('a popup is wired before the awaiting code resumes, not after', async () => {
  // Playwright does not replay EventEmitter events. A popup whose FIRST
  // document throws, logs an error, raises a dialog or fails a request can do
  // so between the 'page' event and the moment `await popupPromise` resumes --
  // so wiring after the await lost exactly the startup failures the audits
  // exist to report, and they called the popup clean.
  const { clickOpensPopup } = require('./lib/playwright-ui');
  const { createRecorder } = require('./lib/playwright-harness');
  const recorder = createRecorder();

  const handlers = {};
  const popup = {
    on(event, handler) { (handlers[event] = handlers[event] || []).push(handler); },
    url: () => 'https://carlos.test/carlos/admin/panel',
    async waitForLoadState() {},
    locator: () => ({ innerText: async () => 'a working page' }),
    context: () => ({}),
  };
  // The popup emits its startup failure the instant it exists, which is what a
  // real one does while its first document parses.
  let emitted = false;
  const context = {
    async waitForEvent() { return popup; },
  };
  const page = {
    locator: () => ({
      scrollIntoViewIfNeeded: async () => {},
      async click() {
        // The click resolves; the popup's own event fires around the same time.
        emitted = true;
      },
    }),
    context: () => context,
  };

  const opened = await clickOpensPopup(page, page.locator('a'), {
    context, label: 'panel', recorder, timeout: 1000,
  });
  assert.equal(opened, popup);
  assert.ok(emitted, 'the click must have run');
  // The wiring must already have registered its listeners, so a failure raised
  // now is recorded under this popup's label.
  assert.ok((handlers.pageerror || []).length > 0,
    'the popup must be wired for pageerror before the caller gets it back');
  handlers.pageerror[0]({ message: 'boom', stack: 'boom' });
  assert.equal(recorder.pageErrors.length, 1);
  assert.equal(recorder.pageErrors[0].label, 'panel');

  // And the wiring happens inside the event continuation, not after the await.
  const source = require('node:fs').readFileSync(require.resolve('./lib/playwright-ui'), 'utf8');
  const helper = source.slice(source.indexOf('async function clickOpensPopup'));
  const body = helper.slice(0, helper.indexOf('\n}\n'));
  assert.match(body, /waitForEvent\('page', \{ timeout \}\)\.then\(\(popup\) => \{/);
  assert.ok(body.indexOf('wireStrictPage') < body.indexOf('await target.click'),
    'the wiring must be set up before the click, or the popup can outrun it');
});

test('a popup that fails its own checks is closed, not leaked', async () => {
  // assertNotErrorPage() and the load-state wait both throw, and the caller
  // never receives the page when they do -- so auditCatalogue's finally, which
  // closes popups, has nothing to close. A 120-item admin sweep with several
  // broken popups leaked one Playwright page each and kept going, which
  // exhausts the browser for a reason unrelated to anything under test.
  const { clickOpensPopup } = require('./lib/playwright-ui');
  let closed = false;
  const popup = {
    on() {},
    url: () => 'https://carlos.test/carlos/admin/broken',
    async waitForLoadState() { throw new Error('navigation failed'); },
    async close() { closed = true; },
    locator: () => ({ innerText: async () => '' }),
  };
  const context = { async waitForEvent() { return popup; } };
  const page = {
    context: () => context,
    locator: () => ({ scrollIntoViewIfNeeded: async () => {}, click: async () => {} }),
  };
  await assert.rejects(
    () => clickOpensPopup(page, page.locator('a'), { context, label: 'broken', timeout: 500 }),
    /navigation failed/,
  );
  assert.equal(closed, true, 'the popup must be closed before the error is rethrown');
});

/*
 * WHAT pickDate GUARANTEES.
 *
 * The postcondition was `value.trim() !== ''`. A field that already held a date
 * satisfies that, and so does a click that landed on a neighbouring day -- so
 * the one thing the helper exists to promise, that the date asked for is the
 * date now selected, went unchecked. It is asserted against flatpickr's own
 * selectedDates rather than the rendered string, because dateFormat differs
 * field to field across CARLOS.
 */
test('pickDate fails when the picker ended up on a different day than was asked for', async () => {
  const { pickDate } = require('./lib/playwright-ui');
  const september = [];
  for (let day = 1; day <= 30; day += 1) {
    september.push({ year: 2026, month: 9, day });
  }
  // The day is found and clicked, the field is non-empty -- and flatpickr has
  // the WRONG date selected. The old postcondition passed this.
  const double = flatpickrDouble(() => september, { value: '2026-09-21', selectedIso: '2026-09-21' });
  await assert.rejects(
    () => pickDate(double.page, '#appointment_date', '2026-09-20', { timeout: 50 }),
    /selected 2026-09-21, not the 2026-09-20 that was asked for/,
  );
});

test('pickDate accepts an input whose flatpickr instance cannot be read', async () => {
  // Not every date field in CARLOS is a flatpickr, and an unreadable internal
  // must not fail a check that otherwise succeeded. The non-empty check stands
  // on its own there.
  const { pickDate } = require('./lib/playwright-ui');
  const september = [{ year: 2026, month: 9, day: 20 }];
  const double = flatpickrDouble(() => september, { selectedIso: null });
  assert.equal(await pickDate(double.page, '#appointment_date', '2026-09-20', { timeout: 50 }), '2026-09-20');
});

