/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');

const { clickDownloadsOrOpens, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');

/*
 * These two helpers exist because several CARLOS controls are conditional in the
 * JSP rather than in the check: a popup on one deployment, a same-tab link or a
 * download on another. Both race two outcomes, and a race is exactly the kind of
 * code that looks right and is wrong at one particular moment -- so the moments
 * are pinned here rather than discovered on a live run.
 */

/** A locator that records the click and does nothing else. */
function locatorDouble(clicks) {
  return {
    scrollIntoViewIfNeeded: async () => {},
    click: async () => { clicks.push('clicked'); },
  };
}

/**
 * A popup whose url is 'about:blank' until its navigation lands.
 *
 * This is what Playwright actually hands you: the 'page' event fires when the
 * WINDOW is created, and the address the opener asked for arrives with the
 * navigation a moment later.
 */
function popupDouble(target, { navigates = true } = {}) {
  let current = 'about:blank';
  return {
    url: () => current,
    async waitForLoadState() {},
    async waitForURL(predicate) {
      if (!navigates) {
        // Never settles, the way a popup that opened to nothing behaves; the
        // caller's bounded wait is what gives up.
        throw new Error('timeout');
      }
      current = target;
      if (typeof predicate === 'function') {
        assert.equal(predicate(current), true, 'the predicate must accept the landed url');
      }
    },
  };
}

function contextDouble(popup) {
  return {
    async waitForEvent(event) {
      assert.equal(event, 'page');
      return popup;
    },
  };
}

function pageDouble(clicks, { url = 'https://carlos.test/carlos/demographic/edit' } = {}) {
  return {
    url: () => url,
    context: () => ({ async waitForEvent() { return new Promise(() => {}); } }),
    locator: () => locatorDouble(clicks),
    async waitForEvent() { return new Promise(() => {}); },
    async waitForURL() { return new Promise(() => {}); },
    async waitForLoadState() {},
  };
}

test('a popup url is read after its navigation lands, not at the page event', async () => {
  // Reading it at the event returns 'about:blank' for a popup that went on to
  // load perfectly, and demographic-labels asserts the produced url is not
  // about:blank -- so this timing decides whether that check is usable at all.
  const clicks = [];
  const target = 'https://carlos.test/carlos/demographic/ViewPrintEnvelope?demos=2';
  const popup = popupDouble(target);
  const page = pageDouble(clicks);
  const outcome = await clickDownloadsOrOpens(page, locatorDouble(clicks), {
    context: contextDouble(popup),
    label: 'label:PDF Envelope',
    timeout: 1000,
  });
  assert.equal(outcome.kind, 'popup');
  assert.equal(outcome.url, target, 'the url must be the address the popup landed on');
  assert.notEqual(outcome.url, 'about:blank');
  assert.equal(outcome.page, popup);
  assert.ok(clicks.includes('clicked'), 'the control must actually be clicked');
});

test('a popup that really opens to nothing still reports about:blank', async () => {
  // Papering over it would hide a control wired to an empty window, which is a
  // finding the caller should see.
  const clicks = [];
  const popup = popupDouble('unused', { navigates: false });
  const outcome = await clickDownloadsOrOpens(pageDouble(clicks), locatorDouble(clicks), {
    context: contextDouble(popup),
    label: 'label:broken',
    timeout: 1000,
  });
  assert.equal(outcome.url, 'about:blank');
});

test('a download wins the race and carries its own url, with no page to close', async () => {
  // A headless Chromium has no PDF viewer, so window.open() on a PDF becomes a
  // download and the popup never settles.
  const clicks = [];
  const target = 'https://carlos.test/carlos/demographic/printDemoChartLabelAction?demographic_no=2';
  const page = {
    url: () => 'https://carlos.test/carlos/demographic/edit',
    context: () => ({ async waitForEvent() { return new Promise(() => {}); } }),
    locator: () => locatorDouble(clicks),
    async waitForEvent(event) {
      assert.equal(event, 'download');
      return { url: () => target };
    },
    async waitForURL() { return new Promise(() => {}); },
    async waitForLoadState() {},
  };
  const outcome = await clickDownloadsOrOpens(page, locatorDouble(clicks), {
    context: { async waitForEvent() { return new Promise(() => {}); } },
    label: 'label:PDF Chart Label',
    timeout: 1000,
  });
  assert.equal(outcome.kind, 'download');
  assert.equal(outcome.url, target);
  assert.equal(outcome.page, null, 'there is no popup to close on the download path');
});

test('neither outcome within the deadline is an error naming the control', async () => {
  // A loser that settles would win the race and hide what happened; a deadline
  // that never fires would hang the run.
  const clicks = [];
  await assert.rejects(
    () => clickDownloadsOrOpens(pageDouble(clicks), locatorDouble(clicks), {
      context: { async waitForEvent() { return new Promise(() => {}); } },
      label: 'label:inert',
      timeout: 200,
    }),
    /label:inert: clicking produced neither a download nor a popup within 200ms/,
  );
});

test('a same-tab navigation is relabelled, so a later scoped assertion can see it', async () => {
  // The in-place branch returns the SAME page, still carrying the label it was
  // wired with. Without the relabel, assertStrictPage(recorder, ['patient-search'])
  // scopes to a label nothing was recorded under and passes having checked
  // nothing.
  const { createRecorder, wireStrictPage, assertStrictPage } = require('./lib/playwright-harness');
  const recorder = createRecorder();
  const handlers = {};
  let current = 'https://carlos.test/carlos/provider/providercontrol';
  const page = {
    on(event, handler) { (handlers[event] = handlers[event] || []).push(handler); },
    emit(event, payload) { return Promise.all((handlers[event] || []).map((h) => h(payload))); },
    url: () => current,
    context: () => ({ async waitForEvent() { return new Promise(() => {}); } }),
    locator: () => ({
      scrollIntoViewIfNeeded: async () => {},
      click: async () => {},
      innerText: async () => 'Search Patient',
    }),
    async waitForEvent() { return new Promise(() => {}); },
    async waitForURL(predicate) {
      current = 'https://carlos.test/carlos/PMmodule/ClientSearch2';
      assert.equal(predicate(current), true);
    },
    async waitForLoadState() {},
  };
  wireStrictPage(page, 'login', recorder);

  const outcome = await clickOpensPopupOrNavigates(page, page.locator('#search a'), {
    context: { async waitForEvent() { return new Promise(() => {}); } },
    label: 'patient-search',
    recorder,
    timeout: 1000,
  });
  assert.equal(outcome.isPopup, false);
  assert.equal(outcome.page, page);

  await page.emit('pageerror', new Error('contextPath is not defined'));
  assert.throws(() => assertStrictPage(recorder, ['patient-search']), /contextPath is not defined/);
});

/*
 * A click that throws must fail as a click failure.
 *
 * Both race helpers arm a rejecting deadline timer before clicking. If the
 * click throws first, nothing awaits that deadline -- and an unobserved
 * rejection ends the Node process, so a plain "locator not visible" was
 * reported as a crash with a stack pointing nowhere near the control.
 */
async function clickThrowsIsReportedAsSuch(helper) {
  const boom = new Error('locator resolved to hidden element');
  const locator = {
    scrollIntoViewIfNeeded: async () => {},
    click: async () => { throw boom; },
  };
  const page = {
    url: () => 'https://carlos.test/carlos/provider/providercontrol',
    async waitForURL() { return new Promise(() => {}); },
    async waitForEvent() { return new Promise(() => {}); },
    async waitForLoadState() {},
  };
  const unhandled = [];
  const onUnhandled = (reason) => unhandled.push(reason);
  process.on('unhandledRejection', onUnhandled);
  try {
    await assert.rejects(
      () => helper(page, locator, {
        context: { async waitForEvent() { return new Promise(() => {}); } },
        label: 'probe',
        // Short, so an unhandled rejection would surface within the test.
        timeout: 40,
      }),
      /locator resolved to hidden element/,
    );
    // Past the deadline the helper armed: if it were still live and unobserved,
    // it would reject here.
    await new Promise((resolve) => { setTimeout(resolve, 120); });
  } finally {
    process.off('unhandledRejection', onUnhandled);
  }
  assert.deepEqual(unhandled, [], 'the armed deadline must not reject unobserved after a failed click');
}

test('clickOpensPopupOrNavigates reports a failed click, not an unhandled rejection', async () => {
  await clickThrowsIsReportedAsSuch(clickOpensPopupOrNavigates);
});

test('clickDownloadsOrOpens reports a failed click, not an unhandled rejection', async () => {
  await clickThrowsIsReportedAsSuch(clickDownloadsOrOpens);
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
