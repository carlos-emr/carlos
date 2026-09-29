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
 * Browser check for appointment status editing (ENABLE_EDIT_APPT_STATUS).
 *
 * A tester found every packaged install booking appointments as "t" with no
 * status pull-down, and the Admin > Schedule Management "Appointment Status
 * Setting" link gone: the turnkey carlos.properties never set the property, and
 * every surface read an absent value as "off". Status editing is now on unless the
 * property is set to a non-active value, so this check asserts the ENABLED shape
 * on an install that does not switch it off:
 *
 *   - the add-appointment popup offers a status <select> (not the free-text "t"
 *     field) listing the configured statuses, including t and C;
 *   - the edit popup offers the labelled status <select> with the booked status
 *     pre-selected;
 *   - an appointment whose status is NOT among the active statuses (deactivated in
 *     the status editor, or imported) keeps that status as the selected option, and
 *     saving the edit form without touching status leaves it unchanged;
 *   - both admin navigation surfaces (admin.jsp and the administration panel's
 *     left navigation) link to the status editor, and the editor lists statuses.
 *
 * appointment-lifecycle accepts either status shape because the property is a
 * deployment choice; this check is the one that fails when the default regresses.
 * Set APPT_STATUS_EDITING_EXPECT=disabled against an install that sets
 * ENABLE_EDIT_APPT_STATUS=no to assert the free-text shape and hidden links instead.
 *
 * Reuses the appointment-lifecycle fixture (same environment: BASE_URL, TEST_*,
 * MYSQL_*, APPOINTMENT_*). One appointment is booked through the UI, marked with
 * a unique PW_APPT_<millis> stamp, and removed in finally with its archive rows.
 */

const { chromium } = require('playwright');
const {
  assert, assertNoPageErrors, assertNotErrorPage, buildFailureDetails, getLaunchOptions, gotoApp, login,
  wirePage,
} = require('./eform-local-playwright-utils');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const fixture = require('./appointment-lifecycle-playwright-checks');

const expectation = (process.env.APPT_STATUS_EDITING_EXPECT || 'enabled').toLowerCase();
assert(['enabled', 'disabled'].includes(expectation),
  'APPT_STATUS_EDITING_EXPECT must be "enabled" or "disabled"');
const expectEnabled = expectation === 'enabled';
// Stands in for a status deactivated after use when the install has no inactive row.
const FALLBACK_UNLISTED_STATUS = 'Z';
const STATUS_SETTING_PATH = '/appointment/apptStatusSetting';

const passed = [];
function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

function activeStatuses() {
  return fixture.sql('SELECT status FROM appointment_status WHERE active=1 ORDER BY id')
    .split('\n').filter(Boolean);
}

/** A status deactivated in the editor (the demo set ships 'c' inactive), else an unknown code. */
function unlistedStatus() {
  return fixture.sql('SELECT status FROM appointment_status WHERE active=0 ORDER BY id LIMIT 1')
    || FALLBACK_UNLISTED_STATUS;
}

async function optionValues(select) {
  return select.locator('option').evaluateAll((nodes) => nodes.map((n) => n.value));
}

/** Opens the add popup from an empty slot, asserts the status field, closes without booking. */
async function checkAddPopup(context, daySheet, statuses) {
  await fixture.openDaySheet(daySheet);
  const slot = daySheet.locator('a.adhour[onclick*="provider_no="]').first();
  assert(await slot.count() > 0, `day sheet for ${fixture.targetDate} rendered no bookable slot`);
  const popupPromise = context.waitForEvent('page', { timeout: 45000 });
  await slot.click();
  const popup = await popupPromise;
  wirePage(popup, 'add-appointment-status', fixture.recorder, async (dialog, entry) => {
    fixture.recorder.dialogs.push({ ...entry, accepted: true });
    await dialog.accept().catch(() => {});
  });
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, 'add-appointment popup');

  const form = popup.locator('form#addappt');
  const select = form.locator('select[name="status"]');
  const text = form.locator('input[type="text"][name="status"]');
  if (expectEnabled) {
    assert(await select.count() === 1,
      'add-appointment popup offers no status pull-down; ENABLE_EDIT_APPT_STATUS is being read as off');
    assert(await text.count() === 0, 'add-appointment popup rendered a free-text status field next to the pull-down');
    const values = await optionValues(select);
    for (const code of ['t', 'C']) {
      assert(values.includes(code), `add-appointment status pull-down has no ${code}: ${JSON.stringify(values)}`);
    }
    assert(values.length === statuses.length,
      `add-appointment status pull-down lists ${values.length} statuses, the database has ${statuses.length} active`);
    pass(`add-appointment popup offers a status pull-down of ${values.length} active statuses including t and C`);
  } else {
    assert(await select.count() === 0, 'add-appointment popup offers a status pull-down although editing is disabled');
    assert(await text.count() === 1, 'add-appointment popup rendered no status field at all');
    pass('add-appointment popup presents the free-text status field (editing disabled)');
  }
  await popup.close().catch(() => {});
}

async function openEditStatus(context, daySheet, appointmentNo) {
  const popup = await fixture.openEditPopup(context, daySheet, appointmentNo);
  return { popup, select: popup.locator('select[name="status"]'), text: popup.locator('input[type="text"][name="status"]') };
}

async function submitEdit(popup) {
  const responsePromise = popup.waitForResponse((r) => r.request().method() === 'POST'
    && /\/appointment\/UpdateRecord$/.test(new URL(r.url()).pathname), { timeout: 45000 });
  await clickAndAwaitReload(popup, popup.locator('#updateButton'), { timeout: 45000, label: 'edit popup Update' });
  const response = await responsePromise;
  assert(response.status() < 400, `appointment UpdateRecord returned HTTP ${response.status()}`);
  await assertNotErrorPage(popup, 'appointment update confirmation');
}

async function checkEditPopup(context, daySheet, booked, statuses) {
  let { popup, select, text } = await openEditStatus(context, daySheet, booked.id);
  if (!expectEnabled) {
    assert(await select.count() === 0, 'edit popup offers a status pull-down although editing is disabled');
    assert(await text.count() === 1, 'edit popup rendered no status field at all');
    pass('edit popup presents the free-text status field (editing disabled)');
    await popup.close().catch(() => {});
    return;
  }
  assert(await select.count() === 1, 'edit popup offers no status pull-down');
  assert(await popup.locator('#apptStatusSelect').count() === 1, 'edit popup status pull-down lost its id');
  const labelFor = await popup.locator('#apptStatusLabel').getAttribute('for');
  assert(labelFor === 'apptStatusSelect', `edit popup status label points at ${labelFor}, not the pull-down`);
  const values = await optionValues(select);
  assert(values.length === statuses.length,
    `edit popup status pull-down lists ${values.length} statuses, the database has ${statuses.length} active`);
  const current = await select.inputValue();
  assert(current === booked.status,
    `edit popup pre-selected ${current}, the appointment is ${booked.status}`);
  pass(`edit popup offers the labelled status pull-down with the booked status ${booked.status} selected`);
  await popup.close().catch(() => {});

  // A status outside the active list (deactivated in the editor after use, or imported)
  // used to fall through to the first option, so any save silently rewrote it.
  const UNLISTED_STATUS = unlistedStatus();
  assert(!values.includes(UNLISTED_STATUS), `status ${UNLISTED_STATUS} is inactive yet offered in the pull-down`);
  fixture.sql(`UPDATE appointment SET status='${UNLISTED_STATUS}' WHERE appointment_no=${Number(booked.id)}`);
  ({ popup, select } = await openEditStatus(context, daySheet, booked.id));
  const kept = await select.inputValue();
  assert(kept === UNLISTED_STATUS,
    `edit popup selected ${kept} for an appointment whose status is the unlisted ${UNLISTED_STATUS}`);
  await submitEdit(popup);
  await popup.close().catch(() => {});
  const after = fixture.stampedAppointments();
  assert(after.length === 1 && after[0].status === UNLISTED_STATUS,
    `saving the edit form rewrote the unlisted status ${UNLISTED_STATUS} to ${after[0] && after[0].status}`);
  pass(`an unlisted status (${UNLISTED_STATUS}) stays selected and survives an edit-form save`);
}

async function checkAdminLinks(context, statuses) {
  const page = await context.newPage();
  wirePage(page, 'admin-status-links', fixture.recorder);
  try {
    await gotoApp(page, fixture.config.baseUrl, '/admin/ViewAdmin');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'admin.jsp');
    const legacyLinks = await page.locator(`a[onclick*="${STATUS_SETTING_PATH}"]`).count();

    await gotoApp(page, fixture.config.baseUrl, '/administration');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'administration panel');
    const panelLink = page.locator(`a.xlink[rel*="${STATUS_SETTING_PATH}"]`);
    const panelLinks = await panelLink.count();

    if (!expectEnabled) {
      assert(legacyLinks === 0 && panelLinks === 0,
        `status editor links present although editing is disabled (admin.jsp=${legacyLinks}, panel=${panelLinks})`);
      pass('both admin surfaces hide the Appointment Status Setting link (editing disabled)');
      return;
    }
    assert(legacyLinks > 0, 'admin.jsp has no Appointment Status Setting link');
    assert(panelLinks > 0, 'the administration panel left navigation has no Appointment Status Setting link');
    const label = (await panelLink.first().textContent()).trim();
    assert(/appointment status/i.test(label), `administration panel status link reads ${JSON.stringify(label)}`);
    pass('admin.jsp and the administration panel both link to Appointment Status Setting');

    await gotoApp(page, fixture.config.baseUrl, `${STATUS_SETTING_PATH}?dispatch=view`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'appointment status setting');
    const listed = await page.locator('td.nowrap:first-child').evaluateAll(
      (cells) => cells.map((cell) => cell.textContent.trim()));
    for (const code of ['t', 'C']) {
      assert(listed.includes(code), `status editor does not list ${code}: ${JSON.stringify(listed)}`);
    }
    assert(listed.length >= statuses.length,
      `status editor lists ${listed.length} statuses, fewer than the ${statuses.length} active ones`);
    const editLinks = await page.locator('a[href*="dispatch=modify"]').count();
    assert(editLinks > 0, 'status editor offers no Edit link for any status');
    pass(`status editor lists ${listed.length} statuses with Edit links`);
  } finally {
    await page.close().catch(() => {});
  }
}

async function main() {
  fixture.initMysqlDefaults();
  let browser = null;
  try {
    fixture.cleanupRows();
    const statuses = activeStatuses();
    assert(statuses.length > 1, `appointment_status has ${statuses.length} active rows; the pull-down needs a list`);

    browser = await chromium.launch(getLaunchOptions(fixture.config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    // appointmentaddarecord.jsp self-closes on success; keep the page inspectable.
    await context.addInitScript(() => {
      window.close = () => { window.__carlosSelfCloseRequested = true; };
    });
    const daySheet = await login(context, fixture.config, fixture.recorder);

    await checkAdminLinks(context, statuses);
    await checkAddPopup(context, daySheet, statuses);
    await fixture.openDaySheet(daySheet);
    const booked = await fixture.bookFromSlot(context, daySheet);
    if (expectEnabled) {
      // The pull-down's first entry; a default "t" booking is what the tester saw
      // when the pull-down was missing, so the row must match what was selected.
      assert(booked.status === statuses[0],
        `booked appointment has status ${booked.status}, the pull-down's default is ${statuses[0]}`);
    }
    await checkEditPopup(context, daySheet, booked, statuses);

    assertNoPageErrors(fixture.recorder);
    assert(fixture.recorder.badResponses.length === 0,
      `unexpected HTTP errors: ${JSON.stringify(fixture.recorder.badResponses, null, 2)}`);
    assert(fixture.recorder.consoleIssues.length === 0,
      `unexpected console issues: ${JSON.stringify(fixture.recorder.consoleIssues, null, 2)}`);
    console.log(`\nPASS appointment status editing (${expectation}): ${passed.length} checks, 0 failures`);
  } catch (error) {
    console.error(`FAIL appointment status editing: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(fixture.recorder), null, 2));
    process.exitCode = 1;
  } finally {
    if (browser) await browser.close().catch(() => {});
    try {
      fixture.cleanupRows();
    } finally {
      fixture.cleanupMysqlDefaults();
    }
  }
}

if (require.main === module) main().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
module.exports = { main };
