/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const BASELINE = require('./lib/navigation-race-baseline.json');

/*
 * One defect, found one file at a time.
 *
 * Two review rounds each surfaced a check that raced a click against
 * page.waitForLoadState(). Both times the fix was local, and both times the
 * same shape was still sitting in files nobody had looked at -- so the third
 * round would have found it again. This counts it instead.
 *
 * Why it is worth counting rather than trusting review: the failure is silent
 * and it always passes. waitForLoadState resolves against the document already
 * on screen, so the wait returns before the click's navigation begins and the
 * assertion reads the page's previous state. Nothing errors; the check just
 * stops testing what it says it tests.
 */

function scriptFiles() {
  const dir = __dirname;
  const lib = path.join(dir, 'lib');
  return [
    ...fs.readdirSync(dir).filter((name) => name.endsWith('.js') && !name.endsWith('.test.js'))
      .map((name) => ({ name, full: path.join(dir, name) })),
    ...fs.readdirSync(lib).filter((name) => name.endsWith('.js') && !name.endsWith('.test.js'))
      .map((name) => ({ name, full: path.join(lib, name) })),
  ];
}

/**
 * Count the raced spelling only.
 *
 * `Promise.all([page.waitForNavigation(...), click])` is the CORRECT documented
 * idiom -- it waits for a new navigation -- so it is deliberately not counted.
 * Only waitForLoadState, which cannot distinguish the new document from the old
 * one, is the defect.
 */
function racedWaits(source) {
  let count = 0;
  for (const match of source.matchAll(/Promise\.all\(\[(.*?)\]\)/gs)) {
    const body = match[1];
    if (body.includes('waitForLoadState') && body.includes('.click(')) {
      count += 1;
    }
  }
  return count;
}

test('no check gains a click raced against waitForLoadState', () => {
  const baseline = BASELINE.unmigrated;
  const regressions = [];
  const stale = [];
  for (const { name, full } of scriptFiles()) {
    const found = racedWaits(fs.readFileSync(full, 'utf8'));
    const allowed = baseline[name] || 0;
    if (found > allowed) {
      regressions.push(`${name}: ${found} raced wait(s), baseline allows ${allowed}`);
    }
    if (found < allowed) {
      stale.push(`${name}: ${found} raced wait(s) left, baseline still claims ${allowed}`);
    }
  }
  assert.deepEqual(regressions, [],
    'use clickAndAwaitReload() from lib/playwright-ui.js: it arms the navigation wait BEFORE the click, '
    + 'so the assertion cannot read the page as it was before it');
  // A burn-down that is not burnt down is a burn-down nobody is reading.
  assert.deepEqual(stale, [],
    'lower or delete these entries in scripts/lib/navigation-race-baseline.json');
});

test('every baseline entry names a file that exists', () => {
  const present = new Set(scriptFiles().map((entry) => entry.name));
  const missing = Object.keys(BASELINE.unmigrated).filter((name) => !present.has(name));
  assert.deepEqual(missing, [], 'delete baseline entries whose script is gone');
});

test('the checks this PR migrated carry none of it', () => {
  // The five files fixed when the shared helper was introduced. Listing them
  // explicitly means a later edit cannot quietly reintroduce the race in one of
  // them by also adding a baseline entry.
  const migrated = [
    'master-record-tabs-playwright-checks.js',
    'patient-search-modes-playwright-checks.js',
    'inboxhub-filters-playwright-checks.js',
    'eform-local-playwright-utils.js',
    'schedule-date-navigation-playwright-checks.js',
    'demographic-edit-update-playwright-checks.js',
  ];
  for (const name of migrated) {
    assert.ok(!(name in BASELINE.unmigrated), `${name} must not be in the burn-down baseline`);
  }
});

test('the shared helper arms its wait before the click', () => {
  const source = fs.readFileSync(path.join(__dirname, 'lib', 'playwright-ui.js'), 'utf8');
  const helper = source.slice(source.indexOf('async function clickAndAwaitReload'));
  const body = helper.slice(0, helper.indexOf('\n}\n'));
  const armed = body.indexOf("waitForEvent('framenavigated'");
  const clicked = body.indexOf('await target.click(');
  assert.ok(armed > -1 && clicked > armed,
    'the navigation wait must be created before the click, or the helper has the bug it exists to fix');
  // And a control that navigates nothing is a finding by default, not a shrug.
  assert.match(body, /without navigating, so anything read next/);
  assert.match(body, /options\.required !== false/);
});
