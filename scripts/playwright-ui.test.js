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
