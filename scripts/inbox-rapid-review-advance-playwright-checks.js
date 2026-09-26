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
 * Browser regression check: Rapid Review in the Inbox's LIST mode must open the
 * result BELOW the one just acknowledged, not the first row of the table.
 *
 * WHAT WENT WRONG. An alpha15 tester enabled Rapid Review, started part-way down
 * the list, and acknowledged a lab. The popup closed, the row disappeared in
 * place (the in-place removal was already correct) -- and the lab that opened
 * next was the one at the TOP of the table. openNextInboxItem() advanced by
 * querying the first row's link, so the clinician was sent back to the top on
 * every acknowledgement and had to scroll back down to where they were working.
 *
 * WHAT IS ASSERTED. The Inbox is put into list mode, fully loaded, and Rapid
 * Review is switched on. A lab that is NOT the first row and NOT the last is
 * acknowledged from its popup, and the check watches which result the Inbox
 * opens next:
 *
 *   - THE NEXT POPUP IS THE ROW THAT FOLLOWED THE ACKNOWLEDGED ONE, read off
 *     the popup's own URL (segmentID) and compared with the identity of the row
 *     that sat below the target in the table's display order before the click.
 *     Opening the first row -- a different result, by construction -- is the
 *     regression itself.
 *   - THE ACKNOWLEDGED ROW IS GONE and the rest of the list is unchanged, in
 *     the same order.
 *   - NO RE-FETCH: the list was fully loaded, so a displayInboxList POST
 *     between the click and the advance would mean the in-place path has
 *     regressed as well.
 *
 * NOT READ-ONLY, AND IT MUST RUN AFTER inboxhub-filters. This check ACKNOWLEDGES
 * one lab -- that is the action under test -- and leaves everything else alone.
 * Acknowledging a lab also files the older versions in its chain, and
 * inboxhub-filters asserts a partition in which nothing has been acknowledged,
 * so this entry sits after that check's (and after inbox-preview-acknowledge,
 * which acknowledges one more) in scripts/playwright-suite.json, whose order
 * is run order. Run against a disposable database.
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   INBOX_TIMEOUT_MS=60000        per-step allowance
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, launchBrowser, login, newContext,
  readConfig, runCheck, withExpectedDialogs,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { settle } = require('./inboxhub-filters-playwright-checks');
const { widenToAnyProvider } = require('./inbox-preview-acknowledge-playwright-checks');

/* The Inboxhub AJAX endpoints. A hit on either between the acknowledgement and
 * the advance means the fully loaded list was re-fetched. */
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

/** Put the Inbox in list mode (the toggle's unchecked state) and let every page load. */
async function enterListMode(page, timeout) {
  const toggle = page.locator('#btnViewMode2');
  await toggle.waitFor({ state: 'attached', timeout });
  if (await toggle.isChecked()) {
    await page.locator('#btnViewModeLabel').click({ timeout });
  }
  await page.locator('#inboxhubListModeTableBody').waitFor({ state: 'attached', timeout });
  return settle(page, timeout);
}

/**
 * Every list row on screen, as "TYPE:segmentID", in DISPLAY order.
 *
 * Display order, not sorted: the row Rapid Review must open is the one the
 * clinician sees below the acknowledged one, which is the DataTable's sort
 * order, whatever that is. inboxhub-filters' shownRows() sorts for set
 * comparison and cannot answer this question.
 */
async function rowsInDisplayOrder(page) {
  return page.$$eval('#inboxhubListModeTableBody tr[data-segment-id]', (rows) => rows.map((row) => (
    `${row.getAttribute('data-lab-type') || '?'}:${row.getAttribute('data-segment-id')}`
  )));
}

/** The link that opens one row's result, addressed by the row's identity. */
function rowLink(page, identity) {
  const [type, segment] = identity.split(':');
  assert(/^[A-Za-z0-9_-]+$/.test(type) && /^[A-Za-z0-9_-]+$/.test(segment),
    `row identity ${identity} is not shaped like a rendered one`);
  return page.locator(`#inboxhubListModeTableBody tr[data-segment-id="${segment}"][data-lab-type="${type}"] a[onclick*="reportWindow"]`).first();
}

/**
 * The Acknowledge control inside a lab popup, or null when it has none.
 *
 * labDisplay.jsp renders it only for a result that is not already acknowledged
 * (the ackFlag guard), so "no control" means "nothing here to acknowledge".
 */
async function acknowledgeControl(popup) {
  for (const selector of [
    'input[type="button"][value="Acknowledge"]',
    'input[type="button"][onclick*="updateStatus"]',
  ]) {
    if (await popup.locator(selector).count() === 0) { continue; }
    const control = popup.locator(selector).first();
    if (await control.isVisible().catch(() => false)) { return control; }
  }
  return null;
}

/** The segmentID a result popup was opened for, off its own URL. */
function segmentOf(url) {
  const match = /[?&]segmentID=([^&#]*)/.exec(url);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Find a lab row that is neither first nor last, has a following row, and can be
 * acknowledged in one click; open its popup and hand it back.
 *
 * LABS ONLY, for the same reason inbox-preview-acknowledge gives: acknowledging
 * a document is a two-step comment flow unless the install sets skipComment,
 * and driving it would make the outcome depend on configuration this check is
 * not about. NEITHER FIRST NOR LAST, by construction: the row that follows the
 * target must be a different result from the first row, or the assertion
 * could not tell the fix from the bug; and the last row has no successor, so
 * falling back to the top is the documented behaviour there, not the defect.
 *
 * Every popup is opened into the same named window ("labreport"), so one that
 * turns out not to be acknowledgeable is CLOSED before the next row is tried:
 * otherwise window.open re-uses it and no page event ever arrives.
 */
async function openAcknowledgeableTarget(context, inbox, rows, recorder, timeout, limit = 8) {
  let examined = 0;
  for (let index = 1; index < rows.length - 1 && examined < limit; index += 1) {
    if (!rows[index].startsWith('HL7:')) { continue; }
    examined += 1;
    const { page: popup, isPopup } = await clickOpensPopupOrNavigates(inbox, rowLink(inbox, rows[index]), {
      context, label: 'lab', recorder, timeout,
    });
    assert(isPopup, `the result link for ${rows[index]} navigated the Inbox away instead of opening a popup`);
    await popup.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
    await popup.waitForLoadState('networkidle', { timeout }).catch(() => {});
    const control = await acknowledgeControl(popup);
    if (control) {
      return { index, identity: rows[index], popup, control };
    }
    await popup.close().catch(() => {});
  }
  return null;
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

    let rowCount = await enterListMode(inbox, timeout);
    if (rowCount < 3) {
      // Empty for THIS provider is the common case on a demo or imported
      // database, not an empty inbox. Ask the question a clinician would.
      await widenToAnyProvider(inbox, timeout);
      rowCount = await enterListMode(inbox, timeout);
    }
    if (rowCount < 3) {
      throw new SkipCheck(
        `list mode shows ${rowCount} row(s) even for Any Provider; this check needs a lab that is neither the `
        + 'first nor the last row, with a different result below it, to tell "opened the next row" from "opened the first"',
      );
    }
    const fullyLoaded = await inbox.evaluate(() => window.hasMoreData === false);
    assert(fullyLoaded,
      'the list reported it had finished loading but hasMoreData is still true, so the in-place path under test would not be taken');

    const before = await rowsInDisplayOrder(inbox);
    const target = await openAcknowledgeableTarget(context, inbox, before, recorder, timeout);
    if (!target) {
      throw new SkipCheck('no lab row between the first and last offers an Acknowledge control; every candidate is already acknowledged');
    }
    const expectedNext = before[target.index + 1];
    assert(expectedNext && expectedNext !== before[0],
      `the row below ${target.identity} is ${expectedNext}, which is also the first row, so this check could not tell the fix from the bug`);

    // Rapid Review is the toolbar toggle; its onchange sets rapidReviewState.
    await inbox.locator('#rapidReviewToggle').check({ timeout });
    assert(await inbox.evaluate(() => window.rapidReviewState === true),
      'ticking the Rapid Review toggle did not switch rapidReviewState on');

    const afterClick = [];
    const onRequest = (request) => afterClick.push(`${request.method()} ${request.url()}`);
    inbox.on('request', onRequest);

    // The advance opens the next result through reportWindow(), a window.open
    // into the "labreport" name. The acknowledged popup closes itself first, so
    // the next result arrives as a NEW page on the context.
    const advanced = context.waitForEvent('page', { timeout });

    // Acknowledging a lab asks for a comment FIRST (labDisplay.jsp's getComment
    // prompt); withExpectedDialogs stands in for that handler and keeps the
    // prompt out of the strict-page findings, where it does not belong.
    const dialogs = await withExpectedDialogs(target.popup, async () => {
      await target.control.click({ timeout });
      await inbox.waitForFunction(
        (identity) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- identity comes off the page's own data attributes and is shape-checked
          const [type, segment] = identity.split(':');
          return document.querySelector(`#inboxhubListModeTableBody tr[data-segment-id="${segment}"][data-lab-type="${type}"]`) === null;
        },
        target.identity,
        { timeout },
      ).catch(() => {});
    }, { promptText: '' });
    assert(dialogs.length > 0,
      'the Acknowledge button raised no comment prompt, so this check never drove an acknowledgement at all');

    const nextPopup = await advanced;
    await nextPopup.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
    const openedSegment = segmentOf(nextPopup.url());
    // Give a re-fetch that WOULD have happened time to show itself.
    await inbox.waitForTimeout(3000);
    inbox.off('request', onRequest);

    const after = await rowsInDisplayOrder(inbox);

    // 1. The acknowledgement took effect.
    assert(!after.includes(target.identity),
      `The acknowledged result ${target.identity} is still on screen; the clinician would see a result they have already dealt with`);
    // 2. THE REGRESSION ITSELF: Rapid Review opened the row below the acknowledged one.
    assert(openedSegment !== null,
      `Rapid Review opened ${nextPopup.url()}, which names no segmentID, so this check cannot tell which result it is`);
    assert(openedSegment === expectedNext.split(':')[1],
      `Rapid Review opened segmentID ${openedSegment} after acknowledging ${target.identity}; the row below it was ${expectedNext}`
      + (openedSegment === before[0].split(':')[1]
        ? ' -- that is the FIRST row of the table, so the clinician who started part-way down was sent back to the top'
        : ''));
    // 3. Nothing was re-fetched: the list was fully loaded.
    const refetches = afterClick.filter((request) => REFETCH_PATTERN.test(request));
    assert(refetches.length === 0,
      `Acknowledging one result re-ran the whole inbox search (${refetches.length} request(s)): ${refetches.slice(0, 2).join(', ')}`);
    // 4. The rest of the list is the same, in the same order.
    const expected = before.filter((identity) => identity !== target.identity);
    assert(JSON.stringify(after) === JSON.stringify(expected),
      `Acknowledging ${target.identity} disturbed the rest of the list (${before.length} row(s) before, ${after.length} after).`
      + `\n    before: ${before.slice(0, 6).join(', ')}...\n    after:  ${after.slice(0, 6).join(', ')}...`);

    await nextPopup.close().catch(() => {});
    assertStrictPage(recorder, ['inbox', 'lab']);

    console.log(`  acknowledged ${target.identity} (row ${target.index + 1} of ${before.length}) with Rapid Review on`);
    console.log(`  Rapid Review opened ${expectedNext}, the row below it; first row was ${before[0]}; no inbox re-fetch`);
    return { acknowledged: target.identity, opened: expectedNext, firstRow: before[0], rows: before.length };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'inbox-rapid-review-advance', run: main });
}

module.exports = { enterListMode, main, openAcknowledgeableTarget, rowsInDisplayOrder, segmentOf };
