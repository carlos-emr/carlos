#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The JavaScript-path helpers: how a check drives the mechanisms CARLOS actually
 * uses, instead of the Struts action underneath them.
 *
 * WHY. Measured on release/2026.08, the webapp's WEB-INF JSPs contain 827
 * popupPage/popup/newWindow openers, 155 files with a window.opener refresh
 * callback, 1,383 confirm()/alert() call sites, 1,158 inline <script> blocks and
 * 171 fetch/XHR/$.ajax call sites, plus 35 DataTables lists, 19 jQuery UI
 * autocompletes and 35 flatpickr date pickers. What breaks for a user is
 * therefore rarely the action: it is the onclick that opens the popup, the
 * opener callback that repaints the day sheet, the DataTables init, the
 * autocomplete that fills the hidden id, or the confirm() whose result gates the
 * delete. A check that posts the form directly, or calls the page's handler from
 * page.evaluate, passes straight through all of it.
 *
 * THE LINE THIS MODULE DRAWS. page.evaluate is for READING page state (is the
 * hidden input filled, did the opener reload). It is never the way an action is
 * triggered: that is always a real locator.click()/fill()/press() on the element
 * the user uses. A check that needs to invoke a handler directly has found a
 * defect -- an unreachable control -- and should report it, not route around it.
 *
 * SELECTOR PROVENANCE. The ids in NAVIGATION below were read out of
 * src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp on
 * release/2026.08. Entries carry `validated: false` until a run against a live
 * deployment confirms them; the manifest test asserts the flag exists so an
 * unvalidated entry cannot quietly look authoritative.
 */

const {
  assert, assertNotErrorPage, pathOnly, relabelStrictPage, wireStrictPage,
} = require('./playwright-harness');

const DEFAULT_TIMEOUT = 30000;

// Own the listeners, so the losing outcome cannot observe a later action.
// Transform in the event callback: popup startup errors can precede click resolution.
function watchOutcomes(sources, timeout, message) {
  const listeners = [];
  let expire;
  let settled = false;
  const cancel = () => {
    settled = true;
    clearTimeout(expire);
    for (const [emitter, event, listener] of listeners) emitter.off(event, listener);
  };
  const promise = new Promise((resolve, reject) => {
    for (const { emitter, event, accepts = () => true, result } of sources) {
      const listener = value => {
        if (settled || !accepts(value)) return;
        cancel();
        try { resolve(result(value)); } catch (error) { reject(error); }
      };
      listeners.push([emitter, event, listener]);
      emitter.on(event, listener);
    }
    expire = setTimeout(() => { cancel(); reject(new Error(message)); }, timeout);
  });
  // A failed click may prevent awaiting the event promise.
  promise.catch(() => {});
  return { promise, cancel };
}

/**
 * Click an opener and take the popup window it opens.
 *
 * Three checks in 75 waited for a popup event; the other openers were bypassed
 * by navigating to the popup's URL. That misses the whole opener: the
 * messenger's compose page, for instance, redirects to the login page when it is
 * opened without the session bean its opener establishes, so a check that
 * navigated to it directly was asserting against a login form and passing (see
 * docs/ui-tests/clinical-workflow-browser-checks.md).
 */
async function clickOpensPopup(page, locator, options = {}) {
  const context = options.context || page.context();
  const label = options.label || 'popup';
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const pending = watchOutcomes([
    { emitter: context, event: 'page', result(popup) {
      if (options.recorder) wireStrictPage(popup, label, options.recorder, options);
      return popup;
    } },
  ], timeout, `${label}: clicking opened no popup within ${timeout}ms`);
  const target = typeof locator === 'string' ? page.locator(locator) : locator;
  let popup;
  try {
    await target.scrollIntoViewIfNeeded({ timeout }).catch(() => {});
    await target.click({ timeout });
    popup = await pending.promise;
  } finally {
    pending.cancel();
  }
  // THE POPUP IS CLOSED IF THIS THROWS. assertNotErrorPage() and the
  // domcontentloaded wait both can, and the caller never receives the page when
  // they do -- so auditCatalogue's `finally`, which closes popups, has nothing
  // to close. A 120-item admin sweep with several broken popups leaked one
  // Playwright page each and kept going, which is how a long run exhausts the
  // browser for a reason unrelated to anything it is testing.
  try {
    await popup.waitForLoadState('domcontentloaded', { timeout });
    await popup.waitForLoadState('networkidle', { timeout }).catch(() => {});
    await assertNotErrorPage(popup, label);
  } catch (error) {
    await popup.close().catch(() => {});
    throw error;
  }
  return popup;
}

/**
 * Click a control that MAY open a popup or MAY navigate in place, and take
 * whichever actually happened.
 *
 * WHY THIS IS NEEDED AND NOT PARANOIA. Several CARLOS controls are conditional
 * in the JSP, not in the check: the schedule's Search control is a same-tab href
 * to PMmodule/ClientSearch2 when the caisi module is loaded and a popupPage2
 * otherwise; an eChart navbar module may render as either depending on
 * properties. A check that assumes "popup" waits out the whole popup timeout and
 * then fails on a page that worked perfectly; a check that assumes "same tab"
 * asserts against the opener and passes while testing nothing. Racing the two is
 * the only honest reading of a control whose behaviour is a deployment setting.
 *
 * Returns { page, isPopup }. The caller closes the page only when isPopup.
 */
async function clickOpensPopupOrNavigates(page, locator, options = {}) {
  const context = options.context || page.context();
  const label = options.label || 'target';
  const timeout = options.timeout || DEFAULT_TIMEOUT;

  const pending = watchOutcomes([
    { emitter: context, event: 'page', result(popup) {
      if (options.recorder) wireStrictPage(popup, label, options.recorder, options);
      return { page: popup, isPopup: true };
    } },
    { emitter: page, event: 'framenavigated', accepts: frame => frame === page.mainFrame(),
      result: () => ({ page, isPopup: false }) },
  ], timeout, `${label}: clicking opened neither a popup nor a navigation within ${timeout}ms`);

  const target = typeof locator === 'string' ? page.locator(locator) : locator;
  let outcome;
  try {
    await target.scrollIntoViewIfNeeded({ timeout }).catch(() => {});
    await target.click({ timeout });
    outcome = await pending.promise;
  } finally {
    pending.cancel();
  }

  if (options.recorder) {
    if (outcome.isPopup) {
      wireStrictPage(outcome.page, label, options.recorder, options);
    } else {
      // SAME page, new document. Without this it keeps the label it was wired
      // with -- usually 'login' -- and every caller that later scopes
      // assertStrictPage(recorder, ['patient-search', ...]) scopes to a label
      // nothing was recorded under: the assertion runs, finds nothing, passes.
      relabelStrictPage(outcome.page, label);
    }
  }
  try {
    await outcome.page.waitForLoadState('domcontentloaded', { timeout });
    await outcome.page.waitForLoadState('networkidle', { timeout }).catch(() => {});
    await assertNotErrorPage(outcome.page, label, options);
    return outcome;
  } catch (error) {
    // The caller never receives a rejected popup. Close it here so the next
    // named-window opener cannot reuse an untracked failed page.
    if (outcome.isPopup) await outcome.page.close().catch(() => {});
    throw error;
  }
}

/**
 * Click a control whose destination is a generated FILE, and take whichever way
 * the browser chose to deliver it.
 *
 * WHY THIS IS A RACE AND NOT A WAIT. CARLOS opens its PDFs with
 * popupPage(...) -> window.open(url). A headless Chromium with no PDF viewer
 * turns that into a download on the opener and the popup never settles; a
 * headed one renders it in a popup and no download fires. Both are correct
 * behaviour, and a check that waits for one of them hangs on the other.
 *
 * Returns { kind: 'download' | 'popup', url, download, page }. `url` is the
 * address the APPLICATION built for this control -- worth having on its own,
 * because the caller reads the bytes back through the session rather than out
 * of the renderer: a download gives a check no response body to inspect, and a
 * PDF rendered in a viewer gives it no bytes either.
 */
async function clickDownloadsOrOpens(page, locator, options = {}) {
  const context = options.context || page.context();
  const label = options.label || 'download';
  const timeout = options.timeout || DEFAULT_TIMEOUT;

  const pending = watchOutcomes([
    { emitter: page, event: 'download', result: download =>
      ({ kind: 'download', url: download.url(), download, page: null }) },
    { emitter: context, event: 'page', result(popup) {
      if (options.recorder) wireStrictPage(popup, label, options.recorder, options);
      return { kind: 'popup', url: '', download: null, page: popup };
    } },
  ], timeout, `${label}: clicking produced neither a download nor a popup within ${timeout}ms`);

  const target = typeof locator === 'string' ? page.locator(locator) : locator;
  let outcome;
  try {
    await target.scrollIntoViewIfNeeded({ timeout }).catch(() => {});
    await target.click({ timeout });
    outcome = await pending.promise;
  } finally {
    pending.cancel();
  }
  if (outcome.kind === 'popup') {
    if (options.recorder) {
      wireStrictPage(outcome.page, label, options.recorder, options);
    }
    // THE URL IS NOT READ AT THE EVENT. 'page' fires when the window is created,
    // and at that instant its url is 'about:blank' -- the address the opener
    // asked for arrives with the navigation a moment later. Reading it too early
    // hands the caller 'about:blank' for a popup that went on to load perfectly.
    await outcome.page.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
    await outcome.page.waitForURL((url) => String(url) !== 'about:blank', { timeout: 5000 }).catch(() => {});
    // Whatever it settled on, including about:blank: a popup that really did
    // open to nothing is a finding the caller should see, not one to paper over.
    outcome.url = outcome.page.url();
  }
  return outcome;
}

/**
 * Click something that submits or navigates, and wait for the page to be
 * REPLACED -- not merely to be loaded.
 *
 * WHY THIS EXISTS, AND WHY IT IS SHARED. The idiom it replaces,
 *
 *     await Promise.all([
 *       page.waitForLoadState('domcontentloaded').catch(() => {}),
 *       control.click({ timeout }),
 *     ]);
 *
 * looks like the documented Playwright pattern but is not it. waitForLoadState
 * resolves against the document ALREADY ON SCREEN, and that document is already
 * loaded -- so it returns immediately, the click's navigation has not started,
 * and whatever the check reads next is the page's PREVIOUS state. A search
 * validates the previous result set, a date jump is compared against the day it
 * started on, a saved form is read back before it was saved. Every one of those
 * passes. (The documented pattern uses waitForNavigation, which waits for a NEW
 * navigation; that form is correct and is left alone where it appears.)
 *
 * Six sites in this suite had it, in five files, found one or two at a time
 * across two review rounds -- which is why it is one exported helper now rather
 * than a correction repeated per call site.
 *
 * @param watchPage the page that NAVIGATES, which is not always the page
 *   clicked: a calendar popup's cells navigate the opener and then close
 *   themselves, so the wait belongs on the opener.
 * @param options.required set false for a control that legitimately may not
 *   navigate; the return value then says whether it did.
 * @returns true when a navigation was observed.
 */
async function clickAndAwaitReload(watchPage, locator, options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const what = options.label || 'the control';
  // ARMED BEFORE THE CLICK. Armed after, it could be satisfied by the document
  // that is already there, which is the whole defect.
  const navigated = watchPage.waitForEvent('framenavigated', {
    predicate: (frame) => frame === watchPage.mainFrame(),
    timeout,
  }).then(() => true, () => false);
  const target = typeof locator === 'string' ? watchPage.locator(locator) : locator;
  await target.scrollIntoViewIfNeeded().catch(() => {});
  await target.click({ timeout });
  const reloaded = await navigated;
  if (options.required !== false) {
    assert(reloaded,
      `${what}: clicking left the page on ${pathOnly(watchPage.url())} without navigating, so anything read next `
      + 'would be the state before the click');
  }
  await watchPage.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
  await watchPage.waitForLoadState('networkidle', { timeout }).catch(() => {});
  return reloaded;
}

/**
 * Click a control that replaces an AJAX-injected panel, and wait for the panel.
 *
 * The Administration shell injects its pages into #dynamic-content. Issue #3377
 * is what this guards: the Select Forms buttons posted to an action whose
 * success result was a servlet forward, so the response came back HTTP 200 with
 * an empty body and the panel rendered white. Asserting the POST status would
 * have passed; only asserting that the panel still has content catches it.
 */
async function clickInjectsPanel(page, locator, options = {}) {
  const panelSelector = options.panel || '#dynamic-content';
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const target = typeof locator === 'string' ? page.locator(locator) : locator;
  const panel = page.locator(panelSelector);
  const before = await panel.innerHTML().catch(() => '');
  await target.scrollIntoViewIfNeeded().catch(() => {});
  await target.click({ timeout });
  await page.waitForFunction(
    ({ selector, previous }) => {
      const element = document.querySelector(selector);
      if (!element) {
        return false;
      }
      const current = element.innerHTML.trim();
      return current.length > 0 && current !== previous;
    },
    { selector: panelSelector, previous: before.trim() },
    { timeout },
  );
  if (options.marker) {
    await page.locator(options.marker).first().waitFor({ state: 'visible', timeout });
  }
  const after = await panel.innerHTML();
  assert(after.trim().length > 0, `${panelSelector} rendered empty after the click (issue #3377 class)`);
  return panel;
}

/** The Administration shell hosts some pages in an iframe rather than a panel. */
function inFrame(page, selector = '#myFrame') {
  return page.frameLocator(selector);
}

/**
 * Mark the opener so a later reload can be detected.
 *
 * Deliberately page.evaluate and NOT addInitScript: an init script would re-run
 * on reload and the sentinel would survive, which is the opposite of what this
 * has to detect.
 *
 * ON THE Math.random(): scanners flag the Date.now() + toString(36) shape as an
 * insecure token. This is not a token. It is a value written into one page under
 * test so a later read can tell whether that page was replaced; it authenticates
 * nothing, never leaves the browser, and predicting it grants an attacker
 * nothing because there is nobody to present it to. A CSPRNG here would cost a
 * crypto import to make a uniqueness marker slightly more unique.
 */
async function markOpener(page, marker = '__carlosOpenerGeneration') {
  const token = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  await page.evaluate(({ name, value }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the sentinel name is a module constant and the value is locally generated
    window[name] = value;
  }, { name: marker, value: token });
  return { marker, token };
}

/**
 * Assert the opener re-rendered what the popup saved, without a manual reload.
 *
 * This is the contract 155 JSP files rely on and that only 10 of 75 checks
 * touched at all: the popup saves, calls back into window.opener, and the row or
 * badge appears where the user is already looking. A check that closes the popup
 * and reloads the opener itself proves the row is in the database and proves
 * nothing about the callback -- so a broken callback, which to a user means "I
 * saved it and nothing happened", stays green.
 */
async function expectOpenerRefresh(opener, popup, rowLocator, options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const sentinel = options.sentinel;
  assert(sentinel && sentinel.marker && sentinel.token,
    'expectOpenerRefresh needs the sentinel returned by markOpener(opener) before the popup was opened');
  if (!popup.isClosed()) {
    // Swallowing this timeout would be a false pass: if the popup never closed,
    // the opener was never called back, and the marker assertion below would
    // still hold -- it holds precisely because nothing happened.
    await popup.waitForEvent('close', { timeout }).catch(() => {});
    assert(popup.isClosed(),
      `The popup did not close within ${timeout}ms, so its save never completed and no opener callback was made`);
  }
  const target = typeof rowLocator === 'string' ? opener.locator(rowLocator) : rowLocator;
  await target.first().waitFor({ state: 'visible', timeout });
  const stillMarked = await opener.evaluate((name) => window[name], sentinel.marker); // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the sentinel name is a module constant
  assert(stillMarked === sentinel.token,
    'The opener reloaded instead of refreshing in place, so this did not test the window.opener callback');
  return target;
}

/**
 * Drive a jQuery UI autocomplete the way a user does and prove it filled the id.
 *
 * eform-consultation-acceptance is the cautionary tale: the consultation form
 * replaced its <select id="specialist"> with a hidden #specialist plus a
 * #specialistInput autocomplete, so the check silently fell through to its
 * programmatic fallback and stopped testing the widget at all (alpha-11
 * observation 5). Typing and picking is the test; the hidden id is the proof.
 */
async function typeAutocomplete(page, inputSelector, text, options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const menu = options.menu || '.ui-autocomplete:visible, ul.ui-menu:visible';
  const input = typeof inputSelector === 'string' ? page.locator(inputSelector) : inputSelector;
  await input.click({ timeout });
  await input.fill('');
  await input.type(text, { delay: options.delay || 60 });
  const suggestions = page.locator(menu).locator('li');
  await suggestions.first().waitFor({ state: 'visible', timeout });
  const option = options.option
    ? page.locator(menu).locator('li', { hasText: options.option }).first()
    : suggestions.first();
  await option.click({ timeout });
  if (options.hidden) {
    const hidden = typeof options.hidden === 'string' ? page.locator(options.hidden) : options.hidden;
    const value = await hidden.inputValue();
    assert(value && value.trim() !== '',
      `The autocomplete did not populate ${typeof options.hidden === 'string' ? options.hidden : 'its hidden field'}; the widget looks selected but the form would submit no id`);
    return value;
  }
  return null;
}

/**
 * Pick a date through the flatpickr calendar rather than typing into the input.
 *
 * 35 JSPs use flatpickr. Its inputs are frequently readonly and always carry a
 * change handler the rest of the form depends on, so fill() either throws or
 * sets a value nothing reacts to.
 *
 * Days are matched on the `dateObj` Date that flatpickr hangs off each day
 * element. There is deliberately no attribute selector here: flatpickr writes
 * no `data-date`, and its only textual identifier is an `aria-label` rendered
 * through `config.ariaDateFormat` in the active locale -- so matching on either
 * would be a locator that silently matches nothing, or one that breaks the
 * first time a page overrides the format.
 */
async function pickDate(page, inputSelector, isoDate, options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  assert(/^\d{4}-\d{2}-\d{2}$/.test(isoDate), `pickDate needs a YYYY-MM-DD date, got ${isoDate}`);
  const input = typeof inputSelector === 'string' ? page.locator(inputSelector) : inputSelector;
  await input.scrollIntoViewIfNeeded().catch(() => {});
  await input.click({ timeout });
  const calendar = page.locator('.flatpickr-calendar.open');
  await calendar.waitFor({ state: 'visible', timeout });

  const days = calendar.locator('.flatpickr-day');
  // Bounded, because a disabled range or a maxDate can leave the arrow inert:
  // stepping forever would hang the check instead of reporting the date is
  // unreachable. 24 covers two years either way, which is more than any CARLOS
  // date field asks for.
  const maxMonthSteps = options.maxMonthSteps || 24;
  let index = -1;
  let steps = 0;
  for (;;) {
    // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- `wanted` is isoDate, already pinned to YYYY-MM-DD above, and is only compared as a string
    index = await days.evaluateAll((elements, wanted) => elements.findIndex((element) => {
      const date = element.dateObj;
      if (!date || typeof date.getFullYear !== 'function') {
        return false;
      }
      // Local components, not toISOString(): the day is rendered in the
      // browser's zone, so a UTC conversion would be off by one for every
      // negative offset -- which is every Canadian deployment.
      const iso = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
      return iso === wanted && !element.classList.contains('flatpickr-disabled');
    }), isoDate);
    if (index >= 0 || steps >= maxMonthSteps) {
      break;
    }
    // Which way to step is decided from the month flatpickr is actually
    // showing, so this walks towards the target instead of guessing.
    const shown = await calendar.evaluate((element) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code, no interpolation and no argument
      const day = element.querySelector('.flatpickr-day:not(.prevMonthDay):not(.nextMonthDay)');
      const date = day && day.dateObj;
      return date ? { year: date.getFullYear(), month: date.getMonth() + 1 } : null;
    });
    if (!shown) {
      break;
    }
    const [wantedYear, wantedMonth] = isoDate.split('-').map(Number);
    const delta = (wantedYear - shown.year) * 12 + (wantedMonth - shown.month);
    if (delta === 0) {
      // Right month, no matching enabled day: stepping again would only walk
      // away from the date. Fall through to the assertion below.
      break;
    }
    const arrow = calendar.locator(delta > 0 ? '.flatpickr-next-month' : '.flatpickr-prev-month');
    if (!(await arrow.isVisible().catch(() => false))) {
      break;
    }
    await arrow.click({ timeout });
    steps += 1;
  }

  assert(index >= 0,
    `The calendar never offered ${isoDate} as a selectable day after ${steps} month step(s) `
    + '(it may be outside minDate/maxDate, or disabled); refusing to click a different date');
  await days.nth(index).click({ timeout });
  await calendar.waitFor({ state: 'hidden', timeout }).catch(() => {});
  const value = await input.inputValue();
  assert(value && value.trim() !== '', `The flatpickr picker left ${isoDate} unset`);

  // NON-EMPTY IS NOT THE POSTCONDITION. A field that already held a date, or a
  // click that landed on a neighbouring day, satisfies "not empty" -- so the one
  // thing this helper exists to guarantee, that the date asked for is the date
  // now selected, went unchecked.
  //
  // Asserted against flatpickr's OWN selectedDates rather than the formatted
  // string, because dateFormat differs field to field across CARLOS and
  // comparing the rendered text would either be wrong or would hard-code one
  // form. Read in local components for the same reason as the day search above:
  // toISOString() is off by one for every negative offset, which is every
  // Canadian deployment. A field whose instance is not reachable falls back to
  // the non-empty check rather than failing a check over an unreadable internal.
  let selected = null;
  try {
    selected = await input.evaluate((element) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code, no interpolation and no argument
      const picker = element._flatpickr;
      const date = picker && picker.selectedDates && picker.selectedDates[0];
      if (!date || typeof date.getFullYear !== 'function') {
        return null;
      }
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    });
  } catch {
    // An input whose instance cannot be read at all. Fall back to the non-empty
    // check rather than failing a check over an unreadable internal.
    selected = null;
  }
  if (selected !== null) {
    assert(selected === isoDate,
      `The flatpickr picker selected ${selected}, not the ${isoDate} that was asked for -- the field now reads `
      + `"${value}", which a non-empty check would have accepted`);
  }
  return value;
}

/**
 * Wait for a DataTables list to finish its first draw, then return its rows.
 *
 * 35 lists in the webapp are DataTables-rendered. The rows exist in the response
 * before the draw, so a check that reads the table immediately sees either the
 * pre-init markup or the "No data available" placeholder, and a draw callback
 * that throws (the deleted-eForms ReferenceError this suite once shipped green)
 * leaves the list empty with the response still HTTP 200.
 */
async function dataTableRows(page, tableSelector, options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  // The draw wait runs page-side through document.querySelector, so it needs a
  // CSS string. Passing a Locator threw inside the page and the throw was
  // swallowed, which meant every Locator caller skipped the draw wait entirely
  // and could read the table before DataTables had initialised it.
  assert(typeof tableSelector === 'string',
    'dataTableRows needs a CSS selector string: the DataTables draw wait runs inside the page and cannot take a Locator');
  const table = page.locator(tableSelector);
  await table.waitFor({ state: 'visible', timeout });
  // Deliberately NOT swallowed. A draw callback that throws is the exact defect
  // this helper exists to catch (the deleted-eForms ReferenceError this suite
  // once shipped green); ignoring the timeout would report the table as drawn
  // and hand back pre-initialisation or zero rows.
  await page.waitForFunction(
    (selector) => {
      const element = document.querySelector(selector);
      if (!element) {
        return false;
      }
      // DataTables adds the wrapper and the processing container on init.
      // The "<id>_wrapper" fallback is only a valid selector when the table was
      // named by a bare id: for "table.dt" or "#outer .dt" the concatenation
      // builds something that matches nothing, or throws inside querySelector
      // -- and a throw makes waitForFunction fail instead of falling back.
      const byConvention = /^#[A-Za-z][\w-]*$/.test(selector)
        ? document.querySelector(`${selector}_wrapper`)
        : null;
      const wrapper = element.closest('.dataTables_wrapper') || byConvention;
      if (!wrapper) {
        return false;
      }
      const processing = wrapper.querySelector('.dataTables_processing');
      return !processing || processing.style.display === 'none' || processing.offsetParent === null;
    },
    tableSelector,
    { timeout },
  );
  const rows = table.locator('tbody tr');
  // An empty table is a legitimate result, so this wait may time out; the
  // dataTables_empty placeholder below is what distinguishes empty from broken.
  await rows.first().waitFor({ state: 'visible', timeout }).catch(() => {});
  const empty = await table.locator('tbody tr td.dataTables_empty').count();
  return { rows, count: empty ? 0 : await rows.count() };
}

/** The day sheet binds 17 single-key shortcuts; nothing but three checks pressed one. */
async function pressShortcut(page, key, options = {}) {
  await page.locator('body').click({ position: { x: 2, y: 2 } }).catch(() => {});
  await page.keyboard.press(key, { delay: options.delay || 50 });
}

/**
 * Assert the hidden CSRF-TOKEN input exists AND is populated.
 *
 * CLAUDE.md's rule: CSRFGuard's client script only injects the token into a form
 * whose action is a real URL and whose method is non-GET, so a page that does
 * AJAX POSTs reading input[name="CSRF-TOKEN"] silently sends an empty token, the
 * request is rejected with an HTML error page, and response.json() throws into a
 * catch the user never sees. An empty token is the defect, so presence alone is
 * not the assertion.
 */
async function csrfTokenPresent(page, options = {}) {
  const selector = options.selector || 'input[name="CSRF-TOKEN"]';
  const token = await page.locator(selector).first().inputValue().catch(() => '');
  assert(token && token.trim() !== '',
    `${selector} is absent or empty, so this page's AJAX POSTs would be rejected with an HTML error page (see the CSRF bootstrapping rule in CLAUDE.md)`);
  return token;
}

/**
 * Answer the next dialog the page raises, and prove it was raised.
 *
 * Both halves matter. A delete whose confirm() disappeared would otherwise pass
 * (nothing to answer, the delete proceeds); a delete that started asking twice
 * would hang. Pair with wireStrictPage's unexpected-dialog recording, which
 * fails a check that raised a dialog nobody expected.
 */
function expectDialog(page, options = {}) {
  const expectedType = options.type || 'confirm';
  const accept = options.accept !== false;
  const seen = [];
  const handler = async (dialog, entry) => {
    seen.push(entry);
    if (accept) {
      await dialog.accept(options.promptText).catch(() => {});
    } else {
      await dialog.dismiss().catch(() => {});
    }
  };
  return {
    handler,
    assertRaised() {
      assert(seen.length > 0,
        `Expected a ${expectedType}() dialog and none was raised; the control no longer asks for confirmation`);
      assert(seen.length === 1,
        `Expected exactly one ${expectedType}() dialog, saw ${seen.length}`);
      assert(seen[0].type === expectedType,
        `Expected a ${expectedType}() dialog, saw ${seen[0].type}`);
      return seen[0];
    },
    get dialogs() {
      return seen.slice();
    },
  };
}

/**
 * How a user reaches each section, as data.
 *
 * Every entry is a CLICK from somewhere the user already is. A check that needs
 * a section not listed here should add it rather than navigate to the URL: if
 * the only way to reach a surface is an address, that is a finding about the
 * surface, not a licence to type it.
 *
 * `from`      where the user is when they click.
 * `click`     the element, preferring the id the JSP renders.
 * `opens`     'page' (same window), 'popup' (a new window), 'panel' (AJAX
 *             fragment) or 'frame'.
 * `validated` whether a run against a live deployment has confirmed the
 *             selector. New entries start false; flip it in the PR that runs it.
 */
const NAVIGATION = {
  schedule: {
    from: 'login', click: null, opens: 'page', validated: true, note: 'login() lands here',
  },
  search: {
    // Conditional by design. appointmentprovideradminday.jsp renders three
    // anchors inside <li id="search">: with the caisi module loaded the first is
    // a plain href to PMmodule/ClientSearch2 (same tab); otherwise it is
    // popupPage2(demographic/ViewSearch) (popup). A check must handle both, so
    // this entry must not claim one.
    from: 'schedule', click: '#search a', opens: 'popup-or-page', validated: false, note: 'caisi module loaded => same-tab PMmodule/ClientSearch2; otherwise a popup',
  },
  inbox: {
    from: 'schedule', click: '#inboxLink', opens: 'popup', validated: false,
  },
  inboxUnmatched: {
    from: 'schedule', click: '#unclaimedLabLink', opens: 'popup', validated: false,
  },
  tickler: {
    from: 'schedule', click: '#oscar_new_tickler', opens: 'popup', validated: false,
  },
  msg: {
    from: 'schedule', click: '#oscar_new_msg', opens: 'popup', validated: false,
  },
  consultations: {
    from: 'schedule', click: '#con', opens: 'popup', validated: false,
  },
  econsult: {
    from: 'schedule', click: '#econ', opens: 'popup', validated: false,
  },
  administration: {
    from: 'schedule', click: '#admin-panel', opens: 'popup', validated: false,
  },
  dashboard: {
    from: 'schedule', click: '#dashboardList', opens: 'popup', validated: false,
  },
  preferences: {
    // #userSettingsMenu is a plain always-visible flex <ul>, not a dropdown, and
    // the control inside it is an icon with no text -- its title is its
    // accessible name.
    from: 'schedule', click: '#userSettingsMenu a[title="Edit your personal setting"]', opens: 'popup', validated: false,
  },
  help: {
    from: 'schedule', click: '#helpLink', opens: 'popup', validated: false,
  },
  logout: {
    from: 'schedule', click: '#logoutButton', opens: 'page', validated: false,
  },
  scratch: {
    from: 'schedule', click: '[title="Scratch Pad"]', opens: 'popup', validated: false,
  },
};

/** Sections the coverage plan's Priority 1 checks need to reach by clicking. */
const REQUIRED_SECTIONS = [
  'schedule', 'search', 'inbox', 'tickler', 'msg', 'consultations',
  'administration', 'preferences', 'logout',
];

module.exports = {
  NAVIGATION,
  REQUIRED_SECTIONS,
  clickAndAwaitReload,
  clickDownloadsOrOpens,
  clickInjectsPanel,
  clickOpensPopup,
  clickOpensPopupOrNavigates,
  csrfTokenPresent,
  dataTableRows,
  expectDialog,
  expectOpenerRefresh,
  inFrame,
  markOpener,
  pathOnly,
  pickDate,
  pressShortcut,
  typeAutocomplete,
};
