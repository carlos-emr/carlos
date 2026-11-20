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
 * Open every surface the schedule leads to, and everything each one offers.
 *
 * WHY ONE SCRIPT FOR TEN SURFACES. Report, Inbox, Consultations, Msg, Tickler,
 * eDoc, Ref, Preferences, WorkFlow and Scratch all need the identical check
 * -- open it by clicking, open what it offers, fail on anything broken -- and
 * differ only in the control clicked to reach them. Ten near-identical scripts
 * would drift apart and a fix would have to be made ten times. The surfaces are
 * data in lib/playwright-surfaces.js; this is the engine.
 *
 * EACH SURFACE IS ITS OWN CHECK. Run one at a time with SURFACE=report-index, or
 * all of them in one process. scripts/playwright-suite.json registers each name
 * separately so the runner can select, time and report them individually -- a
 * failure says "inbox-surface", not "surface-audit".
 *
 * WHAT IT CATCHES that a route-level check does not: these surfaces are popup-
 * and AJAX-driven, so a break is an HTTP 200 whose inline script throws. That is
 * exactly the shape of issue #3313 item 1 (a pageerror on the Inbox that left the
 * provider autocomplete dead) and issue #3377 (a panel that rendered white).
 *
 * READ-ONLY: opens destinations and closes them; submits no form; seeds nothing.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:surface-audit-playwright              # every surface
 *   SURFACE=inbox-surface npm run test:surface-audit-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   SURFACE=name                  run one surface (see lib/playwright-surfaces.js)
 *   SURFACE_PROVINCE=ON           skip surfaces that do not exist in this region
 *   SURFACE_LIMIT=0               stop after N items per surface
 *   SURFACE_TIMEOUT_MS=20000      per-item allowance
 *   SURFACE_SCREENSHOT_DIR=       capture a screenshot of each failing item
 *
 * IMPLEMENTS, one table row each (docs/ui-tests/playwright-coverage-plan-2026.08.md):
 *   report-index          -> section 3.6 `report-index-links`
 *   inbox-surface         -> section 2.6 `inboxhub-filters` (the surface half)
 *   consultations-surface -> section 3.3 `consultation-edit-status` (the surface half)
 *   messenger-surface     -> section 3.4 `messenger-attachments` (the surface half)
 *   tickler-surface       -> section 3.4 `tickler-forward-filters` (the surface half)
 *   edoc-surface          -> section 2.6 `document-manage` (the surface half)
 *   referrals-surface     -> section 3.6, referral management
 *   preferences-surface   -> section 3.7 `provider-preferences` (the surface half)
 *   workflow-surface      -> section 4.4, integration surfaces
 *   scratch-surface       -> section 4.4, integration surfaces
 * App defects these find are recorded in docs/ui-tests/app-findings-log.md.
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig,
  runCheck,
} = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const {
  auditCatalogue, catalogueLinks, dedupe, findingsSince, snapshotRecorder,
} = require('./lib/playwright-link-audit');
const {
  NEVER_OPEN, describeEntry, entryStrategy, surfaceByName, surfacesForProvince,
} = require('./lib/playwright-surfaces');

/**
 * The one control on the schedule that reaches this surface.
 *
 * Three strategies because the schedule genuinely uses three conventions; see
 * the header of lib/playwright-surfaces.js for which control needs which and
 * why. A label regex must not be end-anchored: <oscar:newLab>/<oscar:newTickler>
 * append a live "<sup>N</sup>" count inside the anchor, so "Inbox" becomes
 * "Inbox3" the moment a lab is waiting.
 */
async function resolveControl(schedulePage, surface) {
  const strategy = entryStrategy(surface);
  if (strategy === 'selector') {
    return schedulePage.locator(surface.entry.selector).first();
  }
  if (strategy === 'label') {
    return schedulePage.locator('a').filter({ hasText: surface.entry.label }).first();
  }
  // CSS has no attribute-regex, and the two icon-only controls are identified by
  // their title. Read the titles once, match here, then address that anchor by
  // its position in the same list -- never by clicking whatever came first.
  const titles = await schedulePage.$$eval('a[title]', (anchors) => anchors.map((anchor) => anchor.getAttribute('title') || ''));
  const index = titles.findIndex((title) => surface.entry.title.test(title));
  if (index < 0) {
    return null;
  }
  return schedulePage.locator('a[title]').nth(index);
}

/** Reach one surface from the schedule, by clicking the control a user clicks. */
async function openSurface(context, schedulePage, surface, recorder, timeout) {
  const control = await resolveControl(schedulePage, surface);
  const found = control ? await control.count() : 0;
  if (found === 0 && surface.optional) {
    // A property-gated control that is off is not a defect, but it must be
    // reported as a skip rather than silently passing an empty audit.
    throw new SkipCheck(`${surface.title} is not offered on this deployment: ${surface.optional}`);
  }
  assert(found > 0,
    `The schedule offers no control matching ${describeEntry(surface)} for ${surface.title}, so a user cannot reach it from the schedule at all`);

  if (surface.entry.popup) {
    // Focused schedule mode navigates in place; tab/popup modes open a page.
    const { page } = await clickOpensPopupOrNavigates(schedulePage, control, {
      context, label: surface.name, recorder, timeout,
    });
    return page;
  }
  // A SAME-TAB SURFACE MUST ACTUALLY GO SOMEWHERE, and this branch used to
  // prove neither half of that. The load-state waits were armed AFTER the
  // click, so they resolved against the schedule's own already-loaded
  // document; and nothing asserted that a navigation happened at all. A
  // broken handler -- or one that became an in-place/AJAX interaction --
  // therefore returned the schedule page, catalogueLinks catalogued the
  // SCHEDULE's links, and surface.minimum was satisfied by the schedule's own
  // navigation. The surface reported a clean audit without ever being opened.
  //
  // No surface in the manifest takes this branch today (all nine entries are
  // popup: true), so this is a latent hazard rather than a live false green --
  // but auditSurface's finally block is written for a same-tab surface, so it
  // is one manifest entry away from running.
  const before = schedulePage.url();
  await clickAndAwaitReload(schedulePage, control, {
    timeout, label: `the ${surface.title} control`,
  });
  // framenavigated alone would also be satisfied by a same-address reload,
  // which leaves the audit on the schedule just as surely.
  assert(schedulePage.url() !== before,
    `Clicking ${describeEntry(surface)} did not take the schedule anywhere, so ${surface.title} was never `
    + "opened and the audit would have catalogued the schedule's own links instead");
  return schedulePage;
}

async function assertSurfaceControls(page, surface, timeout) {
  assert(surface.controls.length > 0, 'A form surface must declare its required controls');
  for (const selector of surface.controls) {
    await page.locator(selector).waitFor({ state: 'visible', timeout });
  }
}

async function auditSurface(context, schedulePage, surface, recorder, options) {
  const {
    timeout, limit, screenshotDir, scheduleUrl,
  } = options;
  // Snapshot BEFORE the surface is opened. auditCatalogue snapshots per item, so
  // without this the opener phase -- the landing page of the popup itself, where
  // issue #3313 item 1 actually lived -- is the one page in the run whose errors
  // nothing attributes to anything.
  const beforeOpen = snapshotRecorder(recorder);
  const page = await openSurface(context, schedulePage, surface, recorder, timeout);
  const openingFindings = findingsSince(recorder, beforeOpen, `${surface.title} (opening)`);
  try {
    const body = await page.locator('body').innerText({ timeout }).catch(() => '');
    assert(body.trim().length > 0, `${surface.title} rendered a blank page`);

    if (surface.controls) {
      await assertSurfaceControls(page, surface, timeout);
      return { surface: surface.name, opened: [], skipped: 0, failures: openingFindings,
        controls: surface.controls.length };
    }

    const items = dedupe(await catalogueLinks(page, surface.scope ? { selector: `${surface.scope} a` } : {}));
    assert(items.length >= surface.minimum,
      `${surface.title} offered ${items.length} navigable item(s); it should offer at least ${surface.minimum}, `
      + 'so either the surface failed to render its content or the catalogue step is broken');

    const result = await auditCatalogue({
      context,
      hostPage: page,
      items,
      recorder,
      labelPrefix: surface.name,
      skipRules: [...NEVER_OPEN, ...(surface.skip || [])],
      limit,
      timeout,
      screenshotDir,
    });
    return {
      surface: surface.name,
      ...result,
      failures: [...openingFindings, ...result.failures],
    };
  } finally {
    if (page !== schedulePage) {
      // Only close a popup; closing the schedule would strand the next surface.
      await page.close().catch(() => {});
    } else if (scheduleUrl && schedulePage.url() !== scheduleUrl) {
      // A SAME-TAB surface navigated the schedule itself. Left there, the next
      // iteration hunts for the schedule's own controls on whatever this
      // surface rendered and reports every one of them as missing -- one
      // conditional surface turning into a cascade of false findings about the
      // surfaces after it. Going back is what a clinician does, so use history
      // first and fall back to the address only if that does not land.
      await schedulePage.goBack({ timeout }).catch(() => {});
      if (schedulePage.url() !== scheduleUrl) {
        await schedulePage.goto(scheduleUrl, { timeout, waitUntil: 'domcontentloaded' }).catch(() => {});
      }
      await schedulePage.waitForLoadState('networkidle', { timeout }).catch(() => {});
    }
  }
}

/**
 * Read SURFACE_LIMIT into a per-surface budget, rejecting anything that is not a
 * count.
 *
 * VALIDATED, because -1 is truthy. auditCatalogue breaks out before clicking the
 * first item when `limit` is set and already reached, so SURFACE_LIMIT=-1 made
 * the whole audit a no-op -- while the items.length >= minimum check still
 * passed and the run reported success. A malformed budget must not be able to
 * manufacture a green run, so it fails loudly instead of silently auditing
 * nothing.
 *
 * @param raw the raw environment value; absent or empty means unlimited
 * @returns the budget, 0 meaning unlimited
 */
function surfaceLimitFrom(raw) {
  const limit = Number(raw || '0');
  assert(Number.isInteger(limit) && limit >= 0,
    `SURFACE_LIMIT must be a non-negative whole number (0 means unlimited), got ${JSON.stringify(raw)}`);
  return limit;
}

async function main() {
  const config = readConfig();
  const selected = process.env.SURFACE || '';
  const province = process.env.SURFACE_PROVINCE || '';
  const limit = surfaceLimitFrom(process.env.SURFACE_LIMIT);
  const timeout = Number(process.env.SURFACE_TIMEOUT_MS || '20000');
  const screenshotDir = process.env.SURFACE_SCREENSHOT_DIR || '';

  let surfaces;
  if (selected) {
    const one = surfaceByName(selected);
    // Deliberately a failure, not a skip. This name comes from the suite
    // manifest, so a typo or a renamed surface would otherwise report SKIP
    // forever and nobody would notice the surface stopped being checked.
    assert(one, `SURFACE=${selected} is not a known surface; see lib/playwright-surfaces.js`);
    if (province && one.province !== 'all' && one.province !== province) {
      throw new SkipCheck(`${selected} does not exist in billregion ${province}`);
    }
    surfaces = [one];
  } else {
    surfaces = surfacesForProvince(province);
  }
  assert(surfaces.length > 0, 'No surfaces selected');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  const failures = [];
  const summary = [];
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    // The address to come back to: a same-tab surface navigates this very page,
    // and the next iteration then looks for schedule controls on whatever it
    // landed on. See the restore in auditSurface's finally.
    const scheduleUrl = schedulePage.url();
    // Read back BEFORE the loop. auditSurface snapshots per surface and
    // measures from there, so anything login() recorded -- and login is a page
    // like any other -- would otherwise sit in the recorder unread while every
    // surface reported clean.
    assertStrictPage(recorder, ['login']);

    const audited = [];
    const notOffered = [];
    for (const surface of surfaces) {
      try {
        const result = await auditSurface(context, schedulePage, surface, recorder, {
          timeout, limit, screenshotDir, scheduleUrl,
        });
        audited.push(surface.name);
        summary.push(`${surface.name}: opened ${result.opened.length}, skipped ${result.skipped}${result.controls ? `, inspected ${result.controls} form controls (read-only)` : ''}`);
        // Prefix each finding with the surface so a multi-surface run stays readable.
        failures.push(...result.failures.map((line) => `[${surface.name}] ${line}`));
      } catch (error) {
        if (error instanceof SkipCheck) {
          // A property-gated surface that this deployment does not offer. Only
          // rows carrying `optional` can reach here; a missing control on any
          // other row is an assertion failure.
          notOffered.push(surface.name);
          summary.push(`${surface.name}: not offered here (${error.message})`);
          continue;
        }
        failures.push(`[${surface.name}] ${String(error.message).split('\n')[0]}`);
      }
    }

    for (const line of summary) {
      console.log(`  ${line}`);
    }
    assert(failures.length === 0,
      `${failures.length} surface item(s) are broken:\n    - ${failures.join('\n    - ')}`);
    if (audited.length === 0 && notOffered.length > 0) {
      // Nothing was actually exercised. Reporting PASS here would be the exact
      // dishonesty this suite is meant to avoid.
      throw new SkipCheck(`none of the selected surfaces are offered on this deployment: ${notOffered.join(', ')}`);
    }
    return { surfaces: audited, notOffered, summary };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: process.env.SURFACE ? `surface-audit:${process.env.SURFACE}` : 'surface-audit', run: main });
}

module.exports = {
  assertSurfaceControls, auditSurface, main, openSurface, resolveControl, surfaceLimitFrom,
};
