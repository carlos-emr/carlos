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
 * while pages REMAIN UNLOADED must not re-run the whole inbox search either.
 *
 * WHAT WENT WRONG. inbox-preview-acknowledge covers the fully loaded list, and
 * the fix it pins deliberately kept the whole-search re-fetch while pages
 * remained, because the server pages by offset and a removal shifts every later
 * result up a place. Preview mode pages only as the clinician scrolls, so a
 * morning's inbox is almost never fully loaded -- which is why an alpha15
 * tester still found "preview is slow on reloads": nearly every acknowledgement
 * threw away every card's iframe and paged from one again.
 *
 * WHAT IS ASSERTED. The Inbox is put into preview mode with its FIRST page
 * loaded and more pages pending (hasMoreData true). A lab card is acknowledged
 * from its iframe, and the check observes the mechanism:
 *
 *   - ONE BOUNDARY REQUEST, NOT A RE-FETCH: exactly one displayInboxView POST
 *     is made between the click and the card disappearing, and it asks for the
 *     LAST LOADED page (the only page the removal can have changed), not for
 *     the list from page 1; no displayInboxList request at all.
 *   - THE OTHER CARDS KEPT THEIR DOCUMENTS: a value set on each surviving
 *     iframe's window before the click is still there afterwards. A re-fetch,
 *     or a merge that MOVED a card, would have reloaded them.
 *   - THE LIST IS THE SAME LIST, in the same order, minus the acknowledged
 *     card, plus at most the ONE card that shifted onto the loaded page from
 *     the next one -- inserted, never re-rendered around.
 *   - THE SCROLL POSITION HOLDS, and pages still remain to be loaded.
 *
 * NOT READ-ONLY, AND IT MUST RUN AFTER inboxhub-filters (see that check and
 * inbox-preview-acknowledge for why). It acknowledges exactly one lab. Run
 * against a disposable database.
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   INBOX_TIMEOUT_MS=60000        per-step allowance; preview cards are iframes
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, launchBrowser, login, newContext,
  readConfig, runCheck, withExpectedDialogs,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const {
  enterPreviewMode, findAcknowledgeable, readStamps, settleList, shownCards, stampSurvivors,
  widenToAnyProvider,
} = require('./inbox-preview-acknowledge-playwright-checks');

const VIEW_PATTERN = /Inboxhub\?method=displayInboxView/;
const LIST_PATTERN = /Inboxhub\?method=displayInboxList/;

async function openInbox(context, schedulePage, recorder, timeout) {
  assert(await schedulePage.locator('#inboxLink').count() > 0,
    'The schedule offers no Inbox control, so a clinician cannot reach their results from the day sheet at all');
  const { page: inbox } = await clickOpensPopupOrNavigates(schedulePage, schedulePage.locator('#inboxLink').first(), {
    context, label: 'inbox', recorder, timeout,
  });
  return inbox;
}

/** The page number a displayInboxView POST body asked for, or null. */
function pageOf(postData) {
  const match = /(?:^|&)page=(\d+)(?:&|$)/.exec(postData || '');
  return match ? Number(match[1]) : null;
}

/**
 * Whether `expected` appears in `actual` in order, allowing at most `slack`
 * extra entries in `actual` (the cards the boundary merge inserted).
 */
function sameOrderAllowingInserts(expected, actual, slack) {
  let at = 0;
  let inserted = 0;
  for (const identity of actual) {
    if (at < expected.length && identity === expected[at]) { at += 1; } else { inserted += 1; }
  }
  return at === expected.length && inserted <= slack;
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
      await widenToAnyProvider(inbox, timeout);
      cardCount = await enterPreviewMode(inbox, timeout);
    }
    if (cardCount < 2) {
      throw new SkipCheck(`preview mode shows ${cardCount} card(s) even for Any Provider; this check needs at least two unacknowledged results`);
    }
    // THE PRECONDITION THIS CHECK EXISTS FOR: pages remain unloaded.
    const state = await inbox.evaluate(() => ({ hasMoreData: window.hasMoreData, page: window.page }));
    if (state.hasMoreData !== true) {
      throw new SkipCheck(
        'the whole result set fitted on the first preview page, so there is no page boundary to re-sync; '
        + 'inbox-preview-acknowledge covers the fully loaded case',
      );
    }
    const loadedPages = state.page - 1;
    assert(loadedPages >= 1, `preview reports page ${state.page} with pages remaining; expected at least one loaded page`);
    const before = await shownCards(inbox);

    const target = await findAcknowledgeable(inbox, before, timeout);
    if (!target) {
      throw new SkipCheck('no preview lab card among the first few offers an Acknowledge control; every lab on screen is already acknowledged');
    }

    const scrollBefore = await inbox.evaluate((index) => {
      const container = document.getElementById('inboxViewItems');
      const card = container.querySelectorAll('.document-card')[index];
      container.scrollTop = card.offsetTop - container.offsetTop;
      return container.scrollTop;
    }, target.index);
    assert(scrollBefore > 0,
      'could not scroll the preview list away from the top, so "the clinician keeps their place" cannot be observed');

    const stamped = await stampSurvivors(inbox, before, target.identity);
    assert(stamped.length > 0, 'no surviving preview card had a loaded document to mark');

    const requests = [];
    const onRequest = (request) => {
      if (request.method() === 'POST' && (VIEW_PATTERN.test(request.url()) || LIST_PATTERN.test(request.url()))) {
        requests.push({ url: request.url(), body: request.postData() || '' });
      }
    };
    inbox.on('request', onRequest);

    const dialogs = await withExpectedDialogs(inbox, async () => {
      await target.control.click({ timeout });
      await inbox.waitForFunction(
        (expected) => document.querySelectorAll('#inboxViewItems .document-card').length <= expected,
        before.length - 1,
        { timeout },
      ).catch(() => {});
    }, { promptText: '' });
    assert(dialogs.length > 0,
      'the Acknowledge button raised no comment prompt, so this check never drove an acknowledgement at all');

    // Give the boundary answer, and any re-fetch that WOULD have happened, time to land.
    await inbox.waitForTimeout(5000);
    inbox.off('request', onRequest);

    const after = await shownCards(inbox);
    const scrollAfter = await inbox.evaluate(() => document.getElementById('inboxViewItems').scrollTop);
    const stillPaging = await inbox.evaluate(() => window.hasMoreData === true);

    // 1. The acknowledgement took effect.
    assert(!after.includes(target.identity),
      `The acknowledged result ${target.identity} is still on screen; the clinician would see a result they have already dealt with`);
    // 2. THE REGRESSION ITSELF: one boundary request for the last loaded page, and nothing else.
    const listFetches = requests.filter((request) => LIST_PATTERN.test(request.url));
    assert(listFetches.length === 0, `acknowledging in preview mode fetched the LIST (${listFetches.length} displayInboxList request(s))`);
    const viewFetches = requests.filter((request) => VIEW_PATTERN.test(request.url));
    assert(viewFetches.length === 1,
      `expected exactly one displayInboxView request (the boundary page), saw ${viewFetches.length}: `
      + viewFetches.map((request) => `page=${pageOf(request.body)}`).join(', ')
      + (viewFetches.length > 1 ? ' -- more than one means the search was re-run from page 1 and every card re-rendered' : ''));
    assert(pageOf(viewFetches[0].body) === loadedPages,
      `the boundary request asked for page ${pageOf(viewFetches[0].body)}; the last loaded page was ${loadedPages}`);
    // 3. The surviving cards kept their documents.
    const lost = await readStamps(inbox, stamped);
    assert(lost.length === 0,
      `${lost.length} of ${stamped.length} surviving preview card(s) lost their loaded document (${lost.slice(0, 3).join(', ')}); `
      + 'each one is a full lab render the clinician waits for again');
    // 4. Same list, same order, minus the acknowledged card, plus at most the one card that shifted in.
    const expected = before.filter((identity) => identity !== target.identity);
    assert(after.length >= expected.length && after.length <= expected.length + 1,
      `expected ${expected.length} or ${expected.length + 1} card(s) after the boundary merge, saw ${after.length}`);
    assert(sameOrderAllowingInserts(expected, after, 1),
      `Acknowledging ${target.identity} disturbed the rest of the list.\n    before: ${before.slice(0, 6).join(', ')}...`
      + `\n    after:  ${after.slice(0, 6).join(', ')}...`);
    // 5. The clinician keeps their place, and the list still has pages to give.
    assert(scrollAfter === scrollBefore,
      `The preview list jumped from scrollTop ${scrollBefore} to ${scrollAfter}; the clinician lost their place in the list`);
    assert(stillPaging, 'hasMoreData turned false: the boundary re-sync must not end paging');

    assertStrictPage(recorder, ['inbox']);

    const merged = after.length - expected.length;
    console.log(`  acknowledged ${target.identity} in preview mode with ${loadedPages} page(s) loaded and more pending`);
    console.log(`  ${before.length} card(s) -> ${after.length}; one boundary request for page ${loadedPages}, no list re-fetch, `
      + `${stamped.length} surviving document(s) kept, ${merged} card(s) merged in, scrollTop held at ${scrollAfter}`);
    return { acknowledged: target.identity, before: before.length, after: after.length, boundaryPage: loadedPages, kept: stamped.length, merged };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'inbox-preview-boundary-resync', run: main });
}

module.exports = { main, pageOf, sameOrderAllowingInserts };
