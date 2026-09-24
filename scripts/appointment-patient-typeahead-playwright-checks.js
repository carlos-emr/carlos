#!/usr/bin/env node
/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for the Add/Edit Appointment patient typeahead (issue #3883).
 *
 * Arrowing to a patient and then leaving the field used to fill the name but
 * post demographic_no empty, so the appointment saved with no patient link.
 * src/main/webapp/js/appointmentPatientLink.js now keeps #keyword and
 * #demographic_no/#mrp in step; scripts/appointment-patient-link.test.js pins
 * its rules with fakes. This check proves them on the two real JSPs, against
 * the real SearchDemographic endpoint and the real jQuery UI widget, by reading
 * what each page actually POSTs. On both pages it drives:
 *
 *   1. arrow to a patient, blur, submit: the posted demographic_no (and, on
 *      Edit, the posted MRP) is the highlighted patient's;
 *   2. hand-edit a linked name to other text, submit: demographic_no is
 *      posted empty and the MRP display is cleared;
 *   3. blank a linked name, submit: the link is kept and the linked name is
 *      put back and posted;
 *   4. hand-edit a linked name and submit WITHOUT leaving the field
 *      (requestSubmit while #keyword keeps focus): the page's inline
 *      onsubmit handler (onAdd/onSub) must already see demographic_no
 *      cleared. On Add that is observable in the post too: onAdd's "."
 *      no-show rule turns status into N only for an unlinked name.
 *
 * NOTHING IS SAVED. Every POST to appointment/AddRecord and
 * appointment/UpdateRecord is intercepted with page.route(), its form body
 * recorded, and answered with a stub page, so no appointment row is created or
 * changed and there is nothing to clean up. The pages are opened by the same
 * URLs the day sheet's slot and appointment links open in their popups; the
 * day-sheet navigation itself is appointment-lifecycle's job.
 *
 * Demo data only. The default search term matches the devcontainer's FAKE-
 * prefixed demo patients (.devcontainer/db/scripts/demo-name-sanitization.sql);
 * never point this at a database holding real patients.
 *
 * Needs a running Tomcat and the dev database (a manual reference check, like
 * the other *-playwright-checks.js). Defaults are for the local devcontainer:
 *   npm run test:appointment-patient-typeahead-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   APPOINTMENT_NO=1                 appointment opened in the Edit page
 *   APPOINTMENT_PROVIDER_NO=999998   provider the Add page books for
 *   APPOINTMENT_DAYS_AHEAD=400       date the Add page is opened on
 *   TYPEAHEAD_SEARCH_TERM=FAKE-      typed into #keyword; must return at least
 *                                    one active demo patient
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  createRecorder,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  runCheck,
  wirePage,
} = require('./lib/playwright-harness');

const appointmentNo = process.env.APPOINTMENT_NO || '1';
const providerNo = process.env.APPOINTMENT_PROVIDER_NO || '999998';
const daysAhead = Number(process.env.APPOINTMENT_DAYS_AHEAD || '400');
const searchTerm = process.env.TYPEAHEAD_SEARCH_TERM || 'FAKE-';
const HAND_EDITED = 'PW typeahead hand edited';

const SAVE_ROUTE = /\/appointment\/(AddRecord|UpdateRecord)$/;

const PAGES = {
  add: {
    label: 'add-appointment',
    saveButton: '#addButton',
    inlineHandler: 'onAdd',
    // Posts only #demographic_no; #mrp has no name on the Add page.
    postsMrpAs: null,
    path() {
      const target = new Date(Date.now() + daysAhead * 24 * 60 * 60 * 1000);
      const query = new URLSearchParams({
        provider_no: providerNo,
        bFirstDisp: 'true',
        year: String(target.getUTCFullYear()),
        month: String(target.getUTCMonth() + 1),
        day: String(target.getUTCDate()),
        start_time: '10:00',
        end_time: '10:14',
        duration: '15',
      });
      return `/appointment/addappointment?${query.toString()}`;
    },
  },
  edit: {
    label: 'edit-appointment',
    saveButton: '#updateButton',
    inlineHandler: 'onSub',
    postsMrpAs: 'doctorNo',
    path() {
      return `/appointment/editappointment?${new URLSearchParams({ appointment_no: appointmentNo }).toString()}`;
    },
  },
};

async function openPage(context, recorder, spec, baseUrl) {
  const page = await context.newPage();
  wirePage(page, spec.label, recorder, async (dialog, entry) => {
    recorder.dialogs.push({ ...entry, accepted: true });
    await dialog.accept().catch(() => {});
  });
  const posts = [];
  await page.route((url) => SAVE_ROUTE.test(url.pathname), async (route) => {
    posts.push(new URLSearchParams(route.request().postData() || ''));
    await route.fulfill({
      status: 200,
      contentType: 'text/html; charset=utf-8',
      body: '<!DOCTYPE html><html><body>intercepted by appointment-patient-typeahead check</body></html>',
    });
  });
  await gotoApp(page, baseUrl, spec.path());
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, spec.label);
  assert(await page.locator('#keyword').count() === 1, `${spec.label}: no #keyword field`);
  assert(await page.locator(spec.saveButton).count() === 1, `${spec.label}: no ${spec.saveButton}`);
  return { page, posts };
}

async function fieldState(page) {
  return page.evaluate(() => ({
    keyword: document.getElementById('keyword').value,
    demographicNo: document.getElementById('demographic_no').value,
    mrp: document.getElementById('mrp').value,
  }));
}

/** Types the search term and arrows onto the first result; returns that result. */
async function highlightFirstResult(page, label) {
  const keyword = page.locator('#keyword');
  await keyword.click();
  const response = page.waitForResponse((candidate) => candidate.request().method() === 'POST'
    && new URL(candidate.url()).pathname.endsWith('/demographic/SearchDemographic'), { timeout: 30000 });
  await keyword.fill(searchTerm);
  const items = await (await response).json();
  assert(Array.isArray(items) && items.length > 0,
    `${label}: searching "${searchTerm}" returned no patients; set TYPEAHEAD_SEARCH_TERM to match an active demo patient`);
  await page.locator('ul.ui-autocomplete li.ui-menu-item').first().waitFor({ state: 'visible', timeout: 15000 });
  await page.keyboard.press('ArrowDown');
  const first = items[0];
  const shown = await keyword.inputValue();
  assert(shown === first.formattedName,
    `${label}: arrowing onto the first result showed ${JSON.stringify(shown)}, expected ${JSON.stringify(first.formattedName)}`);
  return { value: String(first.value), provider: String(first.provider || ''), name: first.formattedName };
}

/** Leaves #keyword the way a user does, by clicking another field. */
async function blurKeyword(page) {
  await page.locator('textarea[name="notes"]').first().click();
}

/** Links a patient through the widget's own select (arrow + Enter). */
async function linkBySelect(page, label) {
  const item = await highlightFirstResult(page, label);
  await page.keyboard.press('Enter');
  const state = await fieldState(page);
  assert(state.demographicNo === item.value, `${label}: Enter on a highlighted row did not link it`);
  return item;
}

async function save(page, posts, spec) {
  const before = posts.length;
  await page.locator(spec.saveButton).click();
  await page.waitForFunction(() => document.body && /intercepted by appointment-patient-typeahead/.test(document.body.textContent), null, { timeout: 30000 });
  assert(posts.length === before + 1, `${spec.label}: expected exactly one intercepted save POST`);
  return posts[posts.length - 1];
}

async function arrowBlurSubmit(context, recorder, spec, baseUrl) {
  const { page, posts } = await openPage(context, recorder, spec, baseUrl);
  const item = await highlightFirstResult(page, spec.label);
  await blurKeyword(page);
  const state = await fieldState(page);
  assert(state.demographicNo === item.value,
    `${spec.label}: after arrow + blur #demographic_no=${JSON.stringify(state.demographicNo)}, expected the highlighted patient ${item.value}`);
  assert(state.mrp === item.provider,
    `${spec.label}: after arrow + blur #mrp=${JSON.stringify(state.mrp)}, expected ${JSON.stringify(item.provider)}`);
  const posted = await save(page, posts, spec);
  assert(posted.get('demographic_no') === item.value,
    `${spec.label}: posted demographic_no=${posted.get('demographic_no')}, expected ${item.value}`);
  assert(posted.get('keyword') === item.name, `${spec.label}: posted keyword does not match the highlighted patient`);
  if (spec.postsMrpAs) {
    assert(posted.get(spec.postsMrpAs) === item.provider,
      `${spec.label}: posted ${spec.postsMrpAs}=${posted.get(spec.postsMrpAs)}, expected ${item.provider}`);
  }
  await page.close();
  console.log(`PASS ${spec.label}: arrow + blur commits the highlighted patient and posts its demographic_no`);
}

async function handEditSubmit(context, recorder, spec, baseUrl) {
  const { page, posts } = await openPage(context, recorder, spec, baseUrl);
  await linkBySelect(page, spec.label);
  await page.locator('#keyword').fill(HAND_EDITED);
  const posted = await save(page, posts, spec);
  assert(posted.get('demographic_no') === '',
    `${spec.label}: a hand-edited name posted the stale demographic_no=${posted.get('demographic_no')}`);
  assert(posted.get('keyword') === HAND_EDITED, `${spec.label}: the hand-edited name was not posted as typed`);
  if (spec.postsMrpAs) {
    assert(posted.get(spec.postsMrpAs) === '', `${spec.label}: the stale MRP was posted with a hand-edited name`);
  }
  await page.close();
  console.log(`PASS ${spec.label}: a hand-edited name posts demographic_no empty`);
}

async function blankSubmit(context, recorder, spec, baseUrl) {
  const { page, posts } = await openPage(context, recorder, spec, baseUrl);
  const item = await linkBySelect(page, spec.label);
  await page.locator('#keyword').fill('');
  const posted = await save(page, posts, spec);
  assert(posted.get('demographic_no') === item.value,
    `${spec.label}: blanking the name dropped the link (posted demographic_no=${posted.get('demographic_no')})`);
  assert(posted.get('keyword') === item.name,
    `${spec.label}: blanking the name did not restore the linked name (posted ${JSON.stringify(posted.get('keyword'))})`);
  await page.close();
  console.log(`PASS ${spec.label}: a blanked name keeps the link and restores the name`);
}

async function submitWithoutBlur(context, recorder, spec, baseUrl) {
  const { page, posts } = await openPage(context, recorder, spec, baseUrl);
  await linkBySelect(page, spec.label);
  await page.locator('#keyword').fill('.pw no show');
  // Record what the page's own inline onsubmit handler reads. The attribute
  // calls the global by name at submit time, so wrapping it is enough.
  await page.evaluate((name) => {
    const original = window[name];
    window[name] = function recordingHandler(...args) {
      window.pwInlineSawDemographicNo = document.getElementById('demographic_no').value;
      return original.apply(this, args);
    };
  }, spec.inlineHandler);
  const focused = await page.evaluate(() => document.activeElement && document.activeElement.id);
  assert(focused === 'keyword', `${spec.label}: #keyword must still have focus, so no blur can settle the link first`);
  const before = posts.length;
  await page.evaluate((selector) => {
    const form = document.getElementById('keyword').form;
    form.requestSubmit(document.querySelector(selector));
  }, spec.saveButton);
  await page.waitForFunction(() => document.body && /intercepted by appointment-patient-typeahead/.test(document.body.textContent), null, { timeout: 30000 });
  assert(posts.length === before + 1, `${spec.label}: expected exactly one intercepted save POST`);
  const posted = posts[posts.length - 1];
  assert(posted.get('demographic_no') === '',
    `${spec.label}: a submit without blur posted the stale demographic_no=${posted.get('demographic_no')}`);
  if (spec === PAGES.add) {
    // onAdd sets status N for a "." name only when demographic_no is already
    // empty, so this is the inline handler's view of the link, as posted.
    assert(posted.get('status') === 'N',
      `${spec.label}: onAdd ran before the link was reconciled (status=${posted.get('status')}, expected N)`);
  }
  await page.close();
  console.log(`PASS ${spec.label}: the inline ${spec.inlineHandler}() sees the reconciled link on a submit without blur`);
}

async function main() {
  assert(/^\d+$/.test(appointmentNo), 'APPOINTMENT_NO must be numeric');
  assert(/^\d+$/.test(providerNo), 'APPOINTMENT_PROVIDER_NO must be numeric');
  assert(Number.isInteger(daysAhead) && daysAhead > 0 && daysAhead < 3650,
    'APPOINTMENT_DAYS_AHEAD must be a day count between 1 and 3649');
  assert(searchTerm.length >= 2, 'TYPEAHEAD_SEARCH_TERM must be at least 2 characters (the widget minLength)');

  const config = readConfig();
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const landing = await login(context, config, recorder);
    await landing.close();
    for (const spec of [PAGES.add, PAGES.edit]) {
      await arrowBlurSubmit(context, recorder, spec, config.baseUrl);
      await handEditSubmit(context, recorder, spec, config.baseUrl);
      await blankSubmit(context, recorder, spec, config.baseUrl);
      await submitWithoutBlur(context, recorder, spec, config.baseUrl);
    }
    assertNoPageErrors(recorder, [PAGES.add.label, PAGES.edit.label]);
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'appointment-patient-typeahead', run: main });
}

module.exports = { PAGES, SAVE_ROUTE, main };
