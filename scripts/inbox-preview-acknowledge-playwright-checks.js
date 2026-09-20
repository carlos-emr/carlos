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
 * Browser regression check: acknowledging one item in the Inbox's PREVIEW mode
 * must not re-run the whole inbox search.
 *
 * WHAT WENT WRONG. Preview mode renders each unacknowledged result as a card
 * holding an <iframe> of the full lab or document. The Inboxhub answered every
 * acknowledgement by calling fetchInboxhubData(), which empties #inboxhubMode
 * and re-fetches from page 1. Three things followed, all of them visible to the
 * clinician working a morning's results:
 *
 *   - every OTHER card's iframe was thrown away and re-rendered, so one
 *     acknowledgement cost a full lab render per remaining result;
 *   - the scroll position went back to the top of the list; and
 *   - every page past the first was discarded.
 *
 * WHY NOT ASSERT ON APPEARANCE. After the fix the card is removed in place and
 * nothing is re-fetched, and a screenshot of that is indistinguishable from a
 * re-fetch that happened to land on the same content. So the three assertions
 * that matter each observe the mechanism instead:
 *
 *   - NO RE-FETCH: the requests the page makes between the click and the card
 *     disappearing must include no displayInboxView/displayInboxList POST.
 *     That POST is the regression itself.
 *   - THE OTHER CARDS KEPT THEIR DOCUMENTS: a value is set on each surviving
 *     iframe's own window before the click and read back after. It survives if
 *     and only if that document was left alone. Counting requests cannot answer
 *     this -- preview mode lazy-loads further cards the whole time, so a repeat
 *     GET is as likely to be normal paging as a reload.
 *   - THE SCROLL POSITION HOLDS: the symptom a clinician actually reports.
 *
 * NOT READ-ONLY, AND IT MUST RUN AFTER inboxhub-filters. This check
 * ACKNOWLEDGES one result -- that is the action under test, and there is no
 * read-only way to observe it. It acknowledges exactly one and leaves the rest
 * alone, but acknowledging a lab also FILES the older versions in its chain,
 * and inboxhub-filters asserts that the review-status filters partition a set
 * in which nothing has been acknowledged. Its manifest entry therefore sits
 * immediately after that check's, because scripts/playwright-suite.json order
 * is run order. Moving it earlier turns inboxhub-filters red for a reason that
 * is not a defect. Run both against a disposable database.
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   INBOX_TIMEOUT_MS=60000        per-step allowance; preview cards are iframes
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, launchBrowser, login, newContext,
  readConfig, runCheck, withExpectedDialogs,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');

/* The Inboxhub AJAX endpoints. A hit on either between the click and the card
 * disappearing IS the re-fetch this check exists to catch. */
const REFETCH_PATTERN = /Inboxhub\?method=displayInbox(View|List)/;

/** Open the Inbox the way a clinician does: the schedule's own control. */
async function openInbox(context, schedulePage, recorder, timeout) {
  assert(await schedulePage.locator('#inboxLink').count() > 0,
    'The schedule offers no Inbox control, so a clinician cannot reach their results from the day sheet at all');
  const { page: inbox } = await clickOpensPopupOrNavigates(schedulePage, schedulePage.locator('#inboxLink').first(), {
    context, label: 'inbox', recorder, timeout,
  });
  return inbox;
}

/**
 * Widen the provider filter when the default view is empty.
 *
 * Results arrive routed to a provider, and a demonstration or freshly imported
 * database routes most of them to no provider at all. The clinician's own
 * answer to an empty inbox is the same one: "Any Provider" in the search panel.
 * Without this the check would skip on every install whose results are not
 * addressed to the account it logs in as, which is most of them.
 */
async function widenToAnyProvider(page, timeout) {
  if (!await page.locator('#inbox-sidebar').isVisible()) {
    await page.locator('#inbox-sidebar-toggle').click({ timeout });
  }
  await page.locator('#anyProvider').check({ timeout, force: true });
  await page.locator('#inboxhubFormSearchBtn').click({ timeout });
  await page.waitForLoadState('domcontentloaded');
  await settleList(page, timeout);
}

/**
 * Wait for the list to stop growing.
 *
 * #inboxhubFormSearchBtn is disabled for the duration of a load and re-enabled
 * by stopInboxhubListProgress(), which makes it the earliest honest completion
 * signal -- the same one inboxhub-filters uses.
 */
async function settleList(page, timeout) {
  const button = '#inboxhubFormSearchBtn';
  await page.locator(button).waitFor({ state: 'attached', timeout });
  await page.waitForFunction(
    (selector) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- a module constant, never request data
      const element = document.querySelector(selector);
      return element && !element.disabled;
    },
    button,
    { timeout },
  ).catch(() => {});
}

/**
 * Switch the Inbox into preview mode and wait for cards to render.
 *
 * The control is #btnViewMode2, a Bootstrap btn-check whose input is visually
 * replaced by #btnViewModeLabel. The LABEL is what a clinician clicks and what
 * this clicks: forcing the hidden input does not reliably flip it.
 *
 * Waits for two cards rather than for the list to stop growing. Preview mode
 * appends further pages as the clinician scrolls, so "stopped growing" is not a
 * state it reaches on its own, and two is all the assertions need.
 */
async function enterPreviewMode(page, timeout) {
  const toggle = page.locator('#btnViewMode2');
  await toggle.waitFor({ state: 'attached', timeout });
  if (!await toggle.isChecked()) {
    await page.locator('#btnViewModeLabel').click({ timeout });
  }
  await page.locator('#inboxViewItems').waitFor({ state: 'attached', timeout });
  await page.waitForFunction(
    () => document.querySelectorAll('#inboxViewItems .document-card').length >= 2,
    undefined,
    { timeout },
  ).catch(() => {});
  // One more page can land moments later; let the count hold still briefly so
  // "the rest of the list is unchanged" is compared against a stable set.
  let previous = -1;
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const count = await page.locator('#inboxViewItems .document-card').count();
    if (count === previous) { return count; }
    previous = count;
    await page.waitForTimeout(500);
  }
  return previous;
}

/** Every preview card on screen, as "TYPE:segmentID", in rendered order. */
async function shownCards(page) {
  return page.$$eval('#inboxViewItems .document-card', (cards) => cards.map((card) => {
    const type = card.getAttribute('data-lab-type') || '?';
    const segment = card.getAttribute('data-segment-id') || '?';
    return `${type}:${segment}`;
  }));
}

/**
 * The Acknowledge control inside one lab card's iframe, or null when it has none.
 *
 * labDisplay.jsp renders it only for a result that is not already acknowledged
 * (the ackFlag guard), so "no control" means "nothing here to acknowledge".
 */
async function acknowledgeControl(frame) {
  for (const selector of [
    'input[type="button"][value="Acknowledge"]',
    'input[type="button"][onclick*="updateStatus"]',
  ]) {
    if (await frame.locator(selector).count() === 0) { continue; }
    const control = frame.locator(selector).first();
    if (await control.isVisible().catch(() => false)) { return control; }
  }
  return null;
}

/**
 * Find a lab card that can be acknowledged in one click, and its frame.
 *
 * LABS ONLY, and that is a deliberate narrowing rather than an oversight.
 * Acknowledging a DOCUMENT is a two-step flow unless the install sets
 * skipComment: showDocument.jsp wires its button to getDocComment(), which
 * opens a comment dialog first. Driving that would make this check's pass or
 * failure depend on a configuration flag that has nothing to do with what it
 * is testing. Labs are also the volume case the preview mode exists for.
 *
 * BOUNDED ON PURPOSE. Preview mode can hold a hundred-odd cards, each an
 * iframe; walking all of them costs more than the check does. The first few
 * are examined, which is also where a clinician starts.
 *
 * Returns null rather than throwing: an inbox whose visible labs are all
 * already acknowledged is a reason to skip, not a failure of the code
 * under test.
 */
async function findAcknowledgeable(page, identities, timeout, limit = 12) {
  let examined = 0;
  for (let index = 0; index < identities.length && examined < limit; index += 1) {
    if (!identities[index].startsWith('HL7:')) { continue; }
    examined += 1;
    const iframe = page.locator('#inboxViewItems .document-card').nth(index).locator('iframe').first();
    const handle = await iframe.elementHandle({ timeout: 10000 }).catch(() => null);
    if (!handle) { continue; }
    // Lazy-loaded: a card below the fold has no document yet, so bring it into
    // view before asking its frame anything.
    await iframe.scrollIntoViewIfNeeded({ timeout: 10000 }).catch(() => {});
    const frame = await handle.contentFrame().catch(() => null);
    if (!frame) { continue; }
    await frame.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
    const control = await acknowledgeControl(frame);
    if (control) {
      return { index, identity: identities[index], control };
    }
  }
  return null;
}

/**
 * The frame showing one card's document, addressed by the card's identity.
 *
 * Returns null when the card is gone or its iframe has not loaded yet (preview
 * cards are lazy), both of which the callers treat as "nothing to say here".
 */
async function cardFrame(page, identity) {
  const [type, segment] = identity.split(':');
  // The identities come off the page's own data attributes, but they reach a
  // CSS selector, so they are shape-checked exactly as the JSP's own
  // isInboxhubItemToken does rather than interpolated on trust.
  if (!/^[A-Za-z0-9_-]+$/.test(type) || !/^[A-Za-z0-9_-]+$/.test(segment)) { return null; }
  const selector = `#inboxViewItems .document-card[data-lab-type="${type}"][data-segment-id="${segment}"] iframe`;
  if (await page.locator(selector).count() === 0) { return null; }
  const handle = await page.locator(selector).first().elementHandle({ timeout: 10000 }).catch(() => null);
  if (!handle) { return null; }
  return handle.contentFrame().catch(() => null);
}

/**
 * Mark the documents of the cards that must SURVIVE the acknowledgement.
 *
 * This is the direct form of "the other cards were not reloaded". Counting
 * requests cannot answer it: preview mode lazily loads further cards the whole
 * time the clinician is scrolling, so a second GET of some URL is as likely to
 * be normal paging as a reload. A value set on the iframe's own window is not
 * ambiguous -- it survives if and only if that document was left alone, and a
 * re-fetch, which replaces #inboxhubMode wholesale, destroys every one of them.
 *
 * @returns the identities actually stamped
 */
async function stampSurvivors(page, identities, exclude, limit = 5) {
  const stamped = [];
  for (const identity of identities) {
    if (stamped.length >= limit) { break; }
    if (identity === exclude) { continue; }
    const frame = await cardFrame(page, identity);
    if (!frame) { continue; }
    const ok = await frame.evaluate(() => {
      window.__carlosPreviewStamp = 'kept';
      return true;
    }).catch(() => false);
    if (ok) { stamped.push(identity); }
  }
  return stamped;
}

/** Which of the stamped documents still carry their mark. */
async function readStamps(page, identities) {
  const lost = [];
  for (const identity of identities) {
    const frame = await cardFrame(page, identity);
    const value = frame
      ? await frame.evaluate(() => window.__carlosPreviewStamp).catch(() => null)
      : null;
    if (value !== 'kept') { lost.push(identity); }
  }
  return lost;
}

async function main() {
  const config = readConfig();
  const timeout = Number(process.env.INBOX_TIMEOUT_MS || '60000');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const inbox = await openInbox(context, schedulePage, recorder, timeout);

    await settleList(inbox, timeout);
    let cardCount = await enterPreviewMode(inbox, timeout);
    if (cardCount < 2) {
      // Empty for THIS provider is the common case on a demo or imported
      // database, not an empty inbox. Ask the question a clinician would.
      await widenToAnyProvider(inbox, timeout);
      cardCount = await enterPreviewMode(inbox, timeout);
    }
    if (cardCount < 2) {
      throw new SkipCheck(
        `preview mode shows ${cardCount} card(s) even for Any Provider; this check needs at least two `
        + 'unacknowledged results so that acknowledging one leaves another whose iframe must NOT be reloaded',
      );
    }
    const before = await shownCards(inbox);

    const target = await findAcknowledgeable(inbox, before, timeout);
    if (!target) {
      throw new SkipCheck('no preview lab card offers an Acknowledge control; every lab on screen is already acknowledged');
    }

    // Scroll so the card being acknowledged sits at the TOP of the viewport.
    // Two things depend on this. "Back at the top of the list" is the symptom
    // clinicians report and cannot be observed from a list that was never
    // scrolled; and the control has to stay in view, because a click that has
    // to scroll the container to reach it moves the very number being asserted.
    const scrollBefore = await inbox.evaluate((index) => {
      const container = document.getElementById('inboxViewItems');
      const card = container.querySelectorAll('.document-card')[index];
      container.scrollTop = card.offsetTop - container.offsetTop;
      return container.scrollTop;
    }, target.index);
    assert(scrollBefore > 0,
      'could not scroll the preview list away from the top, so "the clinician keeps their place" cannot be observed');

    const stamped = await stampSurvivors(inbox, before, target.identity);
    assert(stamped.length > 0,
      'no surviving preview card had a loaded document to mark, so "the other cards were not reloaded" '
      + 'could not be observed');

    const afterClick = [];
    const onRequest = (request) => afterClick.push(`${request.method()} ${request.url()}`);

    inbox.on('request', onRequest);

    // Acknowledging a lab asks for a comment FIRST: labDisplay.jsp wires the
    // button to getComment(), which prompts and then does nothing at all if the
    // prompt is dismissed -- which is exactly what the harness's strict wiring
    // does to a dialog no one expected. withExpectedDialogs is the supported way
    // to stand in for that handler, and it keeps the prompt out of
    // assertStrictPage's unexpected-dialog findings, where it does not belong:
    // asking for a comment is the documented behaviour, not a defect.
    const dialogs = await withExpectedDialogs(inbox, async () => {
      await target.control.click({ timeout });
      // The prompt is raised synchronously by the click handler, but the
      // acknowledge POST it leads to is not; hold the stand-in handler in place
      // until the card has actually gone.
      await inbox.waitForFunction(
        (expected) => document.querySelectorAll('#inboxViewItems .document-card').length <= expected,
        before.length - 1,
        { timeout },
      ).catch(() => {});
    }, { promptText: '' });
    assert(dialogs.length > 0,
      'the Acknowledge button raised no comment prompt, so this check never drove an acknowledgement at all');

    // Give a re-fetch that WOULD have happened time to show itself, so a pass
    // is not just this check reading too early.
    await inbox.waitForTimeout(4000);
    inbox.off('request', onRequest);

    const after = await shownCards(inbox);
    const scrollAfter = await inbox.evaluate(() => document.getElementById('inboxViewItems').scrollTop);

    // 1. The acknowledgement actually took effect.
    assert(!after.includes(target.identity),
      `The acknowledged result ${target.identity} is still on screen; the clinician would see a result they have already dealt with`);
    // 2. THE REGRESSION ITSELF, asserted BEFORE the list is compared. A
    //    re-fetch re-runs the search from page 1, so the list comes back SHORT
    //    as well as reloaded -- and "the rest of the list changed" is the
    //    symptom, not the cause. Reporting the symptom first sends the reader
    //    looking for a list-rendering bug that is not there.
    const refetches = afterClick.filter((request) => REFETCH_PATTERN.test(request));
    assert(refetches.length === 0,
      `Acknowledging one result re-ran the whole inbox search (${refetches.length} request(s)): ${refetches.slice(0, 2).join(', ')}. `
      + 'That reloads every surviving card\'s iframe, discards every page after the first, and returns the '
      + 'clinician to the top of the list.');

    // 3. And the surviving cards kept the documents they had. The re-fetch is
    //    the usual way to lose them, but a card re-rendered by any other route
    //    costs the same full lab render, so this is asserted in its own right
    //    rather than inferred from assertion 2.
    const lost = await readStamps(inbox, stamped);
    assert(lost.length === 0,
      `${lost.length} of ${stamped.length} surviving preview card(s) lost their loaded document to an `
      + `unrelated acknowledgement (${lost.slice(0, 3).join(', ')}); each one is a full lab render the `
      + 'clinician now waits for again');

    // 4. The surviving results are the same ones, in the same order. `after`
    //    may have grown at the END -- preview mode lazy-loads the next page as
    //    the list shortens, which is correct behaviour and not this check's
    //    business.
    const expected = before.filter((identity) => identity !== target.identity);
    assert(JSON.stringify(after.slice(0, expected.length)) === JSON.stringify(expected),
      `Acknowledging ${target.identity} disturbed the rest of the list `
      + `(${before.length} card(s) before, ${after.length} after).\n    before: ${before.slice(0, 6).join(', ')}...`
      + `\n    after:  ${after.slice(0, 6).join(', ')}...`);

    // 5. The clinician keeps their place.
    assert(scrollAfter === scrollBefore,
      `The preview list jumped from scrollTop ${scrollBefore} to ${scrollAfter}; the clinician lost their place in the list`);

    assertStrictPage(recorder, ['inbox']);

    console.log(`  acknowledged ${target.identity} in preview mode`);
    console.log(`  ${before.length} card(s) -> ${after.length}; no inbox re-fetch, ${stamped.length} surviving document(s) kept, scrollTop held at ${scrollAfter}`);
    return { acknowledged: target.identity, before: before.length, after: after.length, kept: stamped.length, scrollTop: scrollAfter };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'inbox-preview-acknowledge', run: main });
}

module.exports = { cardFrame, enterPreviewMode, main, readStamps, settleList, shownCards, stampSurvivors, widenToAnyProvider };
