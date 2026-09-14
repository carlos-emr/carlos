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
 * Browser regression check for the Inbox: do its filters actually filter?
 *
 * WHY THE INBOX AND WHY THE FILTERS. Every lab result, every scanned document
 * and every hospital report arrives here, and a clinician works it by narrowing
 * it: Documents / Labs / HRM, New / Acknowledged / Filed. A filter that silently
 * returns the wrong set is not a cosmetic bug -- a result that should be in
 * "New" and is not is a result nobody looks at. The page was also where issue
 * #3313 item 1 lived: a `contextPath is not defined` pageerror that left the
 * provider autocomplete dead while the page looked fine.
 *
 * THE ASSERTION IS A PARTITION, NOT A COUNT. Counting rows per filter proves
 * almost nothing: the classic failure is a filter that is IGNORED, and an
 * ignored filter returns a perfectly plausible number -- the same number every
 * time. So this check reads the identity of every row (`data-lab-type` and
 * `data-segment-id` on each `<tr>`) and asserts that the three type filters
 * PARTITION the unfiltered set:
 *
 *   - every row a filter returns really is of that type,
 *   - the three sets are pairwise disjoint,
 *   - their union is exactly the unfiltered set, and
 *   - clearing the filter restores it.
 *
 * An ignored filter breaks disjointness. An over-restrictive one breaks the
 * union. A filter that returns another type's rows breaks the first. None of
 * those is visible to a check that only counts, and none is visible server-side.
 *
 * The same shape is applied to the review-status filter (New / Acknowledged /
 * Filed against All), which submits the form for real rather than re-fetching
 * over AJAX -- a different code path, and the one a clinician uses most.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, then the schedule's Inbox
 * control. Never by navigating to the Inboxhub URL.
 *
 * READ-ONLY: it narrows and clears filters. It acknowledges nothing, files
 * nothing, forwards nothing, and opens no result.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:inboxhub-filters-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   INBOX_TIMEOUT_MS=45000        per-step allowance; the list loads in pages
 *
 * IMPLEMENTS: coverage plan section 2.6, `inboxhub-filters`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md). App defects this check
 * finds are recorded in docs/ui-tests/app-findings-log.md, not worked around.
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, launchBrowser, login, newContext,
  readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopup } = require('./lib/playwright-ui');

/*
 * The toolbar's type filters, as InboxhubListMode.jsp renders them. The id is
 * the control; the `type` is what filterByType() passes and what every matching
 * row carries in its data-lab-type attribute -- the two have to agree, which is
 * itself part of what this check proves.
 */
const TYPE_FILTERS = [
  { id: '#filterDOC', type: 'DOC', title: 'Documents' },
  { id: '#filterHL7', type: 'HL7', title: 'Labs' },
  {
    id: '#filterHRM',
    type: 'HRM',
    title: 'HRM reports',
    optional: 'the HRM filter is rendered only where the HRM module is enabled',
  },
];

/** The review-status radios and the value each writes into query.status. */
const STATUS_FILTERS = [
  { id: '#statusNew', value: 'N', title: 'New' },
  { id: '#statusAcknowledged', value: 'A', title: 'Acknowledged' },
  { id: '#statusFiled', value: 'F', title: 'Filed' },
];

/**
 * Wait for the list to finish loading.
 *
 * The Inbox loads in pages and reports progress; `#inboxhubFormSearchBtn` is
 * disabled for the duration and re-enabled by stopInboxhubListProgress(), which
 * makes it the earliest honest completion signal. Reading rows before that
 * returns whatever had arrived so far -- a partial set that would fail the union
 * assertion below for a reason that is not a defect.
 */
async function settle(page, timeout) {
  const button = '#inboxhubFormSearchBtn';
  await page.locator(button).waitFor({ state: 'attached', timeout });
  // The load may already have started, or may start a beat from now; missing
  // the disabled edge is fine, waiting for it forever is not.
  await page.waitForFunction(
    (selector) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- a module constant, never request data
      const element = document.querySelector(selector);
      return element && element.disabled;
    },
    button,
    { timeout: 5000 },
  ).catch(() => {});
  await page.waitForFunction(
    (selector) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- a module constant, never request data
      const element = document.querySelector(selector);
      return element && !element.disabled;
    },
    button,
    { timeout },
  );

  // One last append can land after the button is re-enabled, so require the row
  // count to hold still before anything is read off it.
  let previous = -1;
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const count = await page.locator('#inboxhubListModeTableBody tr').count();
    if (count === previous) {
      return count;
    }
    previous = count;
    await page.waitForTimeout(300);
  }
  return previous;
}

/**
 * Every row currently shown, as "TYPE:segmentID".
 *
 * The identity, not the count: a filter that is ignored returns a perfectly
 * plausible number of rows, and only the identities show that it returned the
 * same rows as everything else.
 */
async function shownRows(page) {
  // EVERY BODY ROW IS READ, not only the ones carrying the attribute. The
  // selector used to be `tr[data-segment-id]`, so a markup regression that
  // stopped emitting the attribute made the list look EMPTY -- which this check
  // reads as "no results" and skips, reporting a green run over a table it never
  // examined. Reading every row and failing on a missing identity turns that
  // into the finding it is.
  const rows = await page.$$eval('#inboxhubListModeTableBody tr', (elements) => elements.map((row) => ({
    segment: row.getAttribute('data-segment-id'),
    type: row.getAttribute('data-lab-type') || '?',
    // Only for the diagnostic below; a row with no identity has to be
    // describable without one.
    text: (row.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 60),
  })));
  // A spacer or "no results" row legitimately has no cells to identify; a row
  // with content and no segment id is the regression.
  const unidentified = rows.filter((row) => !row.segment && row.text);
  assert(unidentified.length === 0,
    `${unidentified.length} Inbox row(s) render content but carry no data-segment-id, so this check cannot tell `
    + 'which results are on screen and every partition assertion below would be comparing incomplete sets');
  const identities = rows
    .filter((row) => row.segment)
    .map((row) => `${row.type}:${row.segment}`)
    .sort();
  // A DUPLICATE IS ITS OWN FINDING. [...new Set(rows)] collapsed two rows with
  // the same identity, so the same result listed twice -- a real rendering
  // defect a clinician would see -- disappeared silently while every assertion
  // below still passed. It is asserted HERE rather than left to fall through:
  // assertPartitions would meet the second copy as "returned by more than one
  // filter value" and blame an ignored filter, which is a different defect and
  // the wrong thing to tell someone.
  const duplicates = identities.filter((row, index) => index > 0 && row === identities[index - 1]);
  assert(duplicates.length === 0,
    `The Inbox lists ${duplicates.length} result(s) more than once (${[...new Set(duplicates)].slice(0, 3).join(', ')}). `
    + 'A clinician would see the same result twice and could action it twice.');
  return identities;
}

/** Open the Inbox from the schedule's own control. */
async function openInbox(context, schedulePage, recorder, timeout) {
  // .first() because click() is strict: a locator matching two elements throws
  // rather than clicking. No rendered page carries two #inboxLink today (the
  // day sheet has its own nav copy and does not include mainMenu.jsp), but a
  // duplicated id in a legacy JSP is a plausible future, and this should fail
  // on the Inbox being unreachable, not on the audit's own locator.
  const control = schedulePage.locator('#inboxLink').first();
  assert(await schedulePage.locator('#inboxLink').count() > 0,
    'The schedule offers no Inbox control, so a clinician cannot reach their results from the day sheet at all');
  const inbox = await clickOpensPopup(schedulePage, control, {
    context, label: 'inbox', recorder, timeout,
  });
  await settle(inbox, timeout);
  return inbox;
}

/** Narrow by one type through the toolbar, and read what came back. */
async function applyTypeFilter(page, filter, timeout) {
  const control = page.locator(filter.id).first();
  if (await page.locator(filter.id).count() === 0) {
    throw new SkipCheck(`${filter.title}: ${filter.optional || 'the control is not rendered here'}`);
  }
  await control.click({ timeout });
  await settle(page, timeout);
  return shownRows(page);
}

/** Narrow by review status through the form, which submits for real. */
async function applyStatusFilter(page, filter, timeout) {
  await page.locator(filter.id).check({ timeout });
  await clickAndAwaitReload(page, page.locator('#inboxhubFormSearchBtn'), {
    timeout, label: `the ${filter.title} filter`,
  });
  await settle(page, timeout);
  return shownRows(page);
}

/**
 * The partition assertions, shared by both filter families.
 *
 * @param whole   rows with no narrowing applied
 * @param parts   [{ title, rows }] one entry per filter value that was applied
 */
function assertPartitions(whole, parts, family) {
  const wholeSet = new Set(whole);
  const union = new Set();
  for (const part of parts) {
    for (const row of part.rows) {
      // Disjoint: an IGNORED filter is the common failure, and an ignored filter
      // returns the same rows under every value -- which shows up here first.
      assert(!union.has(row),
        `${family}: row ${row} was returned by more than one filter value. The values are meant to be mutually `
        + 'exclusive, so at least one of them is being ignored rather than applied.');
      union.add(row);

      assert(wholeSet.has(row),
        `${family}: ${part.title} returned row ${row}, which the unfiltered list does not contain. `
        + 'Narrowing a list must never widen it.');
    }
  }

  const missing = whole.filter((row) => !union.has(row));
  assert(missing.length === 0,
    `${family}: ${missing.length} row(s) are in the unfiltered list but in none of the filter values `
    + `(${missing.slice(0, 5).join(', ')}${missing.length > 5 ? ', ...' : ''}). Every row has exactly one `
    + 'value, so a clinician narrowing the list would never see these at all.');
}

async function checkTypeFilters(page, timeout) {
  const whole = await shownRows(page);
  if (whole.length === 0) {
    throw new SkipCheck('the Inbox is empty on this dataset, so a filter partition cannot be checked');
  }

  const parts = [];
  const skipped = [];
  for (const filter of TYPE_FILTERS) {
    let rows;
    try {
      rows = await applyTypeFilter(page, filter, timeout);
    } catch (error) {
      if (error instanceof SkipCheck) {
        skipped.push(error.message);
        continue;
      }
      throw error;
    }
    // Stronger than the partition: every row this filter returned must actually
    // BE of the type the filter names, which the row carries itself.
    const wrong = rows.filter((row) => !row.startsWith(`${filter.type}:`));
    assert(wrong.length === 0,
      `The ${filter.title} filter returned ${wrong.length} row(s) of another type (${wrong.slice(0, 3).join(', ')}). `
      + 'A clinician reviewing only labs would be shown documents.');
    parts.push({ title: filter.title, rows });
    // Back to everything before the next one: filterByType() toggles, so
    // leaving a filter on would make the next narrowing compound.
    await page.locator('#filterAll').click({ timeout });
    await settle(page, timeout);
  }

  assert(parts.length >= 2,
    `Only ${parts.length} type filter(s) are available here; a partition needs at least two to mean anything`);
  assertPartitions(whole, parts, 'Type filters');

  // And clearing is reversible: a filter that cannot be undone strands the
  // clinician in a narrowed list.
  const restored = await shownRows(page);
  // IDENTITIES, not the count. Both are sorted unique arrays already, and a
  // clear that returns a DIFFERENT set of the same size is exactly the failure
  // a count comparison accepts -- the same shape as the ignored-filter case
  // this whole check exists to catch.
  assert(restored.length === whole.length && restored.every((row, index) => row === whole[index]),
    `Clearing the type filter left ${restored.length} row(s) where the unfiltered list had ${whole.length}, `
    + `and ${restored.filter((row) => !whole.includes(row)).length} of them were not in it; `
    + 'the filter cannot be undone');
  return { whole: whole.length, parts: parts.map((part) => `${part.title}=${part.rows.length}`), skipped };
}

async function checkStatusFilters(page, timeout) {
  // "All" first, so the whole set is measured under the same form submission
  // path as the parts -- not against the AJAX-loaded initial list.
  await page.locator('#statusAll').check({ timeout });
  await clickAndAwaitReload(page, page.locator('#inboxhubFormSearchBtn'), {
    timeout, label: 'the unfiltered (All) submission',
  });
  await settle(page, timeout);
  const whole = await shownRows(page);
  if (whole.length === 0) {
    throw new SkipCheck('no results at any review status on this dataset');
  }

  const parts = [];
  for (const filter of STATUS_FILTERS) {
    parts.push({ title: filter.title, rows: await applyStatusFilter(page, filter, timeout) });
  }
  assertPartitions(whole, parts, 'Review status');
  return { whole: whole.length, parts: parts.map((part) => `${part.title}=${part.rows.length}`) };
}

async function main() {
  const config = readConfig();
  const timeout = Number(process.env.INBOX_TIMEOUT_MS || '45000');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const inbox = await openInbox(context, schedulePage, recorder, timeout);

    const skipped = [];
    let types = null;
    try {
      types = await checkTypeFilters(inbox, timeout);
    } catch (error) {
      if (!(error instanceof SkipCheck)) {
        throw error;
      }
      skipped.push(`type filters: ${error.message}`);
    }

    let statuses = null;
    try {
      statuses = await checkStatusFilters(inbox, timeout);
    } catch (error) {
      if (!(error instanceof SkipCheck)) {
        throw error;
      }
      skipped.push(`status filters: ${error.message}`);
    }

    // Issue #3313 item 1 was a pageerror on exactly this page that left the
    // provider autocomplete dead while everything looked fine.
    assertStrictPage(recorder, ['inbox']);

    if (!types && !statuses) {
      throw new SkipCheck(`neither filter family could be checked:\n    - ${skipped.join('\n    - ')}`);
    }
    for (const line of skipped) {
      console.log(`  skipped ${line}`);
    }
    if (types) {
      console.log(`  type filters partition ${types.whole} row(s): ${types.parts.join(', ')}`);
    }
    if (statuses) {
      console.log(`  status filters partition ${statuses.whole} row(s): ${statuses.parts.join(', ')}`);
    }
    return { types, statuses, skipped };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'inboxhub-filters', run: main });
}

module.exports = {
  STATUS_FILTERS, TYPE_FILTERS, assertPartitions, main, settle, shownRows,
};
