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
 * eDoc, Billing, Preferences, WorkFlow and Scratch all need the identical check
 * -- open it by clicking, open what it offers, fail on anything broken -- and
 * differ only in the label clicked to reach them. Ten near-identical scripts
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
 *   billing-surface       -> section 2.7, the Ontario billing surfaces
 *   preferences-surface   -> section 3.7 `provider-preferences` (the surface half)
 *   workflow-surface      -> section 4.4, integration surfaces
 *   scratch-surface       -> section 4.4, integration surfaces
 * App defects these find are recorded in docs/ui-tests/app-findings-log.md.
 */

const {
  SkipCheck, assert, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { auditCatalogue, catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { NEVER_OPEN, surfaceByName, surfacesForProvince } = require('./lib/playwright-surfaces');

/** Reach one surface from the schedule, by clicking the control a user clicks. */
async function openSurface(context, schedulePage, surface, recorder, timeout) {
  const control = schedulePage.locator('a').filter({ hasText: surface.entry.label }).first();
  const found = await control.count();
  assert(found > 0,
    `The schedule offers no control matching ${surface.entry.label} for ${surface.title}, so a user cannot reach it from the schedule at all`);

  if (surface.entry.popup) {
    return clickOpensPopup(schedulePage, control, {
      context, label: surface.name, recorder, timeout,
    });
  }
  await control.click({ timeout });
  await schedulePage.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
  await schedulePage.waitForLoadState('networkidle', { timeout }).catch(() => {});
  return schedulePage;
}

async function auditSurface(context, schedulePage, surface, recorder, options) {
  const { timeout, limit, screenshotDir } = options;
  const page = await openSurface(context, schedulePage, surface, recorder, timeout);
  try {
    const body = await page.locator('body').innerText({ timeout }).catch(() => '');
    assert(body.trim().length > 0, `${surface.title} rendered a blank page`);

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
    return { surface: surface.name, ...result };
  } finally {
    // Only close a popup; closing the schedule would strand the next surface.
    if (page !== schedulePage) {
      await page.close().catch(() => {});
    }
  }
}

async function main() {
  const config = readConfig();
  const selected = process.env.SURFACE || '';
  const province = process.env.SURFACE_PROVINCE || '';
  const limit = Number(process.env.SURFACE_LIMIT || '0');
  const timeout = Number(process.env.SURFACE_TIMEOUT_MS || '20000');
  const screenshotDir = process.env.SURFACE_SCREENSHOT_DIR || '';

  let surfaces;
  if (selected) {
    const one = surfaceByName(selected);
    if (!one) {
      throw new SkipCheck(`SURFACE=${selected} is not a known surface; see lib/playwright-surfaces.js`);
    }
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

    for (const surface of surfaces) {
      try {
        const result = await auditSurface(context, schedulePage, surface, recorder, { timeout, limit, screenshotDir });
        summary.push(`${surface.name}: opened ${result.opened.length}, skipped ${result.skipped}`);
        // Prefix each finding with the surface so a multi-surface run stays readable.
        failures.push(...result.failures.map((line) => `[${surface.name}] ${line}`));
      } catch (error) {
        failures.push(`[${surface.name}] ${String(error.message).split('\n')[0]}`);
      }
    }

    for (const line of summary) {
      console.log(`  ${line}`);
    }
    assert(failures.length === 0,
      `${failures.length} surface item(s) are broken:\n    - ${failures.join('\n    - ')}`);
    return { surfaces: surfaces.map((surface) => surface.name), summary };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: process.env.SURFACE ? `surface-audit:${process.env.SURFACE}` : 'surface-audit', run: main });
}

module.exports = { auditSurface, main, openSurface };
