#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Add Appointment's slot lock for bookers who are not administrators.
 *
 * With appointment_locking_timeout>0, a user who has a slot open on Add Appointment holds it: everyone else
 * on an overlapping slot of that provider's column sees "Viewers: N (locked)", and their Add, Repeat and
 * Group buttons are hidden until the holder leaves. appointment-group-paste-restriction exercises the lock
 * only as an administrator. Here three throwaway logins share one slot:
 *   1. the owner, with the test login's roles (the control), opens the slot from its own day sheet and holds it;
 *   2. booker A, without the admin right, opens the same slot: it shows locked and A's booking buttons are hidden;
 *   3. the owner goes Back: within one lock refresh A holds the slot and gets its buttons;
 *   4. booker B, also without the admin right, opens the same slot: it shows locked by A and B's booking buttons
 *      are hidden;
 *   5. A moves to a time that no longer overlaps: A's patient Search works again, and B gets the slot;
 *   6. nothing was booked.
 * Add is never pressed.
 *
 * Requires appointment_locking_timeout>0. The page reads it from the properties file, so the check skips
 * when it is 0 instead of changing it.
 * Fixtures: three throwaway logins (the owner with the test login's roles; A and B with only the doctor
 * role, which books appointments but does not hold `_admin`, the right the lock refresh checks), the owner's
 * schedule group and preferences, and the page-lock rows the three sessions write. All are keyed to the
 * throwaway provider numbers and removed and verified at teardown. A and B are doctors rather than receptionists
 * because a receptionist's day sheet answers HTTP 500 to its tab-alert refresh (finding 142), which would fail the
 * page before the lock is reached; they can go back to receptionists once finding 142 is fixed.
 */
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const NAME = 'appointment-slot-lock-non-admin';
const BOOKING_BUTTONS = ['addButton', 'apptRepeatButton', 'groupButton'];
// The page refreshes its lock every 5 s; allow a few refreshes for a change to show.
const REFRESH_WAIT = 20000;

/** A throwaway login that books appointments without `_admin`. Its cleanup is registered before its first write. */
function nonAdminBooker(s) {
  const fixture = throwawayLoginFixture({
    sql: s.sql, marker: `FAKE-PW${randomBytes(8).toString('hex')}`, provider: s.provider, testUser: s.config.testUser,
  });
  s.cleanup(() => fixture.cleanup());
  return fixture.create({ roleNames: ['doctor'] });
}

/** Wait until the page's lock notice reads "Viewers:<count>", and return whether it also says locked. */
async function lockNotice(page, count, label) {
  try {
    await page.waitForFunction((expected) => {
      const notice = document.getElementById('lock_notification');
      return notice && notice.textContent.replace(/\s+/g, '').startsWith(`Viewers:${expected}`);
    }, count, { timeout: REFRESH_WAIT });
  } catch (error) {
    const shown = await page.locator('#lock_notification').innerText().catch(() => '(no notice)');
    throw new Error(`${label}: the lock notice never showed ${count} viewer(s); it reads "${shown.trim()}"`);
  }
  return (await page.locator('#lock_notification').innerText()).includes('(locked)');
}

/** The booking buttons this layout renders, with whether each is shown. */
async function bookingButtons(page) {
  const shown = {};
  for (const id of BOOKING_BUTTONS) {
    const button = page.locator(`#${id}`);
    if (await button.count() > 0) shown[id] = await button.isVisible();
  }
  return shown;
}

async function assertButtons(page, visible, label) {
  // updateBookingButtonVisibility() runs in the same callback that rewrites the notice.
  const shown = await bookingButtons(page);
  h.assert(Object.keys(shown).length > 0, `${label}: the page renders none of ${BOOKING_BUTTONS.join(', ')}`);
  for (const [id, isVisible] of Object.entries(shown)) {
    h.assert(isVisible === visible, `${label}: ${id} is ${isVisible ? 'shown' : 'hidden'}`);
  }
}

/** A signed-in browser context for one throwaway login, wired like the workflow's own context, and its day sheet. */
async function loginContext(s, fixture, label) {
  const context = await h.newContext(s.context.browser(), s.config);
  s.cleanup(() => context.close());
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, label, s.recorder));
  const page = await h.login(context, { ...s.config, testUser: fixture.username }, s.recorder, { label: `${label}-login` });
  return { context, page };
}

/** Open the slot's Add Appointment page in a context, as a second screen showing the same slot. */
async function openSlot(context, slotUrl) {
  const page = await context.newPage();
  const response = await page.goto(slotUrl, { waitUntil: 'load' });
  h.assert(response && response.status() === 200, `Add Appointment answered HTTP ${response && response.status()}`);
  await h.assertNotErrorPage(page, 'Add Appointment');
  return page;
}

async function workflow(s) {
  const { sql, marker } = s;
  const owner = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, owner);
  const a = nonAdminBooker(s);
  const b = nonAdminBooker(s);
  const numbers = [owner, a, b].map(f => h.sqlString(f.providerNo)).join(',');
  const ownerNo = h.sqlString(owner.providerNo);
  const group = h.sqlString('PW' + marker.slice(-8));
  h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE providerNo IN (${numbers})`) === '0',
    'A throwaway provider number already has page locks');
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group}`) === '0', 'Owned group name already exists');
  // Registered last, so it runs before the logins are removed.
  s.cleanup(() => {
    sql.execute(`DELETE FROM PageMonitor WHERE providerNo IN (${numbers});
      DELETE FROM mygroup WHERE mygroup_no=${group} AND provider_no=${ownerNo}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM PageMonitor WHERE providerNo IN (${numbers}))
      + (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group})`) === '0', 'Owned page locks or group remain');
  });
  // The owner's day sheet: one column (its own group), 15-minute slots from 08:00.
  sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name) VALUES (${group},${ownerNo},${h.sqlString(marker)},'Test');
    INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
        lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
      VALUES (${ownerNo},8,18,15,${group},'deepblue',0,NOW(),20,0)`);

  const { context: ownerContext, page: schedule } = await loginContext(s, owner, 'slot-lock-owner');
  let ownerPage;
  let slotUrl;
  await s.step('the owner opens a slot from its own day sheet and holds it alone', async () => {
    const slot = schedule.locator(`a.adhour[onclick*="provider_no=${owner.providerNo}&"]`).filter({ hasText: '10:00' }).first();
    ownerPage = await ui.clickOpensPopup(schedule, slot, { context: ownerContext, recorder: s.recorder, label: 'slot-lock-owner-add' });
    await ownerPage.waitForLoadState('networkidle');
    if (!await ownerPage.evaluate(() => typeof updatePageLock === 'function')) {
      throw new h.SkipCheck('Requires appointment_locking_timeout>0');
    }
    h.assert(await lockNotice(ownerPage, 1, 'owner alone'), 'The owner\'s own lock is not shown as locked');
    await assertButtons(ownerPage, true, 'owner alone');
    h.assert(await ownerPage.locator('#searchBtn').isEnabled(), 'The owner\'s patient Search is disabled');
    slotUrl = ownerPage.url();
  });

  const { context: contextA } = await loginContext(s, a, 'slot-lock-a');
  let pageA;
  await s.step('booker A without the admin right sees the slot locked and cannot book', async () => {
    pageA = await openSlot(contextA, slotUrl);
    h.assert(await lockNotice(pageA, 2, 'A while the owner holds the slot'), 'A does not see the slot locked');
    await assertButtons(pageA, false, 'A while the owner holds the slot');
    await lockNotice(ownerPage, 2, 'the owner while A watches');
    await assertButtons(ownerPage, true, 'the owner while A watches');
  });

  await s.step('the owner goes Back and A gets the slot', async () => {
    const closed = ownerPage.waitForEvent('close');
    await ownerPage.locator('#backButton').click();
    await closed;
    // Shown as locked with its buttons visible: the lock is A's own.
    h.assert(await lockNotice(pageA, 1, 'A after the owner left'), 'A does not hold the slot after the owner left');
    await assertButtons(pageA, true, 'A after the owner left');
  });

  const { context: contextB } = await loginContext(s, b, 'slot-lock-b');
  let pageB;
  await s.step('booker B without the admin right sees the slot locked by A and cannot book', async () => {
    pageB = await openSlot(contextB, slotUrl);
    h.assert(await lockNotice(pageB, 2, 'B while A holds the slot'), 'B does not see the slot locked');
    await assertButtons(pageB, false, 'B while A holds the slot');
    await lockNotice(pageA, 2, 'A while B watches');
    await assertButtons(pageA, true, 'A while B watches');
  });

  await s.step('A moves to a time that no longer overlaps; A can search again and B gets the slot', async () => {
    const start = pageA.locator('input[name="start_time"]');
    await start.fill('14:00');
    // The field's onChange runs checkPageLock(), which disables Search until the lock refresh answers.
    await start.dispatchEvent('change');
    try {
      await pageA.waitForFunction(() => !document.getElementById('searchBtn').disabled, null, { timeout: REFRESH_WAIT });
    } catch (error) {
      throw new Error('A\'s patient Search stayed disabled after the time change');
    }
    h.assert(await lockNotice(pageA, 1, 'A on the new time'), 'A does not hold its new time');
    await assertButtons(pageA, true, 'A on the new time');
    h.assert(await lockNotice(pageB, 1, 'B after A moved'), 'B does not hold the slot after A moved');
    await assertButtons(pageB, true, 'B after A moved');
  });

  await s.step('nothing was booked', async () => {
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${ownerNo}`) === '0',
      'The slot-lock check created a booking');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false, openMaster: false });
module.exports = { workflow };
