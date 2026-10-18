#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * appointment-slot-lock-booker -- the Add Appointment slot lock for a booker WITHOUT the admin right (issue #4917).
 *
 * Requires appointment_locking_timeout>0. /PageMonitoringService used to demand _admin r, so every lock refresh
 * from a receptionist, doctor or nurse answered 403: the notice stayed at "Viewers: N/A", a second booker was
 * not stopped, and after any date/time/duration change the patient Search button stayed disabled for the rest
 * of the page's life. The existing appointment-group-paste-restriction check exercises the lock only as an
 * administrator, so it never saw this.
 *
 * User path: an owner (admin + doctor roles) opens a slot and holds the lock; a booker holding only the doctor
 * role opens an overlapping slot of the same provider's column. The booker must see "Viewers:N (locked)",
 * lose the booking buttons, get Search back after changing the duration, and get the buttons back on a slot
 * that does not overlap. Closing the booker's page releases its monitor (the cancel path was refused too).
 * Fixtures: two throwaway logins (lib/throwaway-login-fixture.js); nothing is booked. The PageMonitor rows of
 * both providers are removed and verified at teardown.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

async function workflow(s) {
    const { sql, marker, config, recorder } = s;
    const owner = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
    s.cleanup(() => owner.cleanup());
    owner.create({ roleNames: ['admin', 'doctor'] });
    // The booker is a different login from the owner. Its marker suffix differs, so the fixture's
    // FAKEPW<hex> username and its provider number are distinct.
    const bookerMarker = marker.slice(0, -1) + (marker.endsWith('0') ? '1' : '0');
    const booker = throwawayLoginFixture({ sql, marker: bookerMarker, provider: s.provider, testUser: config.testUser });
    s.cleanup(() => booker.cleanup());
    booker.create({ roleNames: ['doctor'] });
    const ownerNo = h.sqlString(owner.providerNo);
    const bookerNo = h.sqlString(booker.providerNo);
    const providers = `${ownerNo},${bookerNo}`;
    h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE providerNo IN (${providers})`) === '0', 'The throwaways already own page locks');
    s.cleanup(() => {
        sql.execute(`DELETE FROM PageMonitor WHERE providerNo IN (${providers})`);
        h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE providerNo IN (${providers})`) === '0', 'Owned page locks remain');
    });
    // The point of the check: the booker's roles must not carry the admin right the old gate demanded.
    h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole u JOIN secObjPrivilege p ON p.roleUserGroup=u.role_name
        WHERE u.provider_no=${bookerNo} AND u.activeyn=1 AND p.objectName='_admin' AND p.privilege NOT IN ('|0|','')`) === '0',
    'The booker holds _admin, so the check cannot show the non-admin path');
    h.assert(Number(sql.value(`SELECT COUNT(*) FROM secUserRole u JOIN secObjPrivilege p ON p.roleUserGroup=u.role_name
        WHERE u.provider_no=${bookerNo} AND u.activeyn=1 AND p.objectName='_appointment'`)) > 0,
    'The booker holds no _appointment right');

    const ownerContext = await h.newContext(s.context.browser(), config);
    s.cleanup(() => ownerContext.close());
    ownerContext.setDefaultTimeout(20000);
    ownerContext.on('page', page => h.wireStrictPage(page, 'lock-owner', recorder));
    const bookerContext = await h.newContext(s.context.browser(), config);
    s.cleanup(() => bookerContext.close());
    bookerContext.setDefaultTimeout(20000);
    bookerContext.on('page', page => h.wireStrictPage(page, 'lock-booker', recorder));

    const ownerSchedule = await h.login(ownerContext, { ...config, testUser: owner.username }, recorder, { label: 'lock-owner-login' });
    await h.login(bookerContext, { ...config, testUser: booker.username }, recorder, { label: 'lock-booker-login' });

    const notice = page => page.locator('#lock_notification').innerText();
    const lockRows = provider => sql.rows(`SELECT locked FROM PageMonitor WHERE providerNo=${provider} AND pageName='addappointment'`);
    let ownerPopup;
    let slotUrl;

    await s.step('the owner opens a slot and holds the lock', async () => {
        const slot = ownerSchedule.locator(`a.adhour[onclick*="provider_no=${owner.providerNo}&"]`).nth(4);
        ownerPopup = await ui.clickOpensPopup(ownerSchedule, slot, { context: ownerContext, recorder, label: 'lock-owner-add' });
        await ownerPopup.waitForLoadState('networkidle');
        if (!await ownerPopup.evaluate(() => typeof updatePageLock === 'function')) {
            throw new h.SkipCheck('Requires appointment_locking_timeout>0');
        }
        await ownerPopup.waitForFunction(() => /\(locked\)/.test(document.getElementById('lock_notification').innerText),
            null, { timeout: 30000 });
        slotUrl = ownerPopup.url();
        h.assert(lockRows(ownerNo).length === 1 && lockRows(ownerNo)[0][0] === '1', 'The owner holds no locked PageMonitor row');
    });

    let booking;
    await s.step('a booker without the admin right sees the lock and loses the booking buttons', async () => {
        booking = await bookerContext.newPage();
        const refreshed = booking.waitForResponse(response => response.url().includes('/PageMonitoringService')
            && response.request().method() === 'POST');
        await booking.goto(slotUrl);
        const response = await refreshed;
        h.assert(response.ok(), `The booker's lock refresh returned HTTP ${response.status()} (issue #4917: _admin gate)`);
        await booking.waitForFunction(() => /\(locked\)/.test(document.getElementById('lock_notification').innerText),
            null, { timeout: 30000 });
        const text = await notice(booking);
        h.assert(/Viewers:\s*[1-9]/.test(text) && !/N\/A/.test(text), `The booker's viewer notice is "${text}"`);
        h.assert(await booking.locator('#addButton').count() === 1, 'The booker\'s page renders no Add button to hide');
        for (const id of ['addButton', 'apptRepeatButton', 'groupButton']) {
            h.assert(await booking.locator('#' + id).isHidden(), `The locked slot still offers ${id} to the second booker`);
        }
        const rows = lockRows(bookerNo);
        h.assert(rows.length === 1 && rows[0][0] === '0', 'The booker should hold an unlocked monitor row while the owner holds the slot');
    });

    await s.step('changing the duration re-enables Search once the lock refresh succeeds', async () => {
        const refreshed = booking.waitForResponse(response => response.url().includes('/PageMonitoringService')
            && response.request().method() === 'POST');
        await booking.locator('input[name="duration"]').fill('30');
        await booking.locator('input[name="duration"]').dispatchEvent('change');
        const response = await refreshed;
        h.assert(response.ok(), `The re-check after a duration change returned HTTP ${response.status()}`);
        await booking.waitForFunction(() => !document.getElementById('searchBtn').hasAttribute('disabled'), null, { timeout: 20000 });
        h.assert(/\(locked\)/.test(await notice(booking)), 'The overlapping slot lost its locked notice after the change');
    });

    await s.step('a slot that does not overlap the owner\'s offers the booking buttons again', async () => {
        const refreshed = booking.waitForResponse(response => response.url().includes('/PageMonitoringService')
            && response.request().method() === 'POST');
        await booking.locator('input[name="start_time"]').fill('16:30');
        await booking.locator('input[name="start_time"]').dispatchEvent('change');
        await refreshed;
        await booking.waitForFunction(() => !/\(locked\)/.test(document.getElementById('lock_notification').innerText),
            null, { timeout: 20000 });
        h.assert(await booking.locator('#addButton').isVisible(), 'Add stayed hidden on a slot nobody holds');
        h.assert(!await booking.locator('#searchBtn').isDisabled(), 'Search stayed disabled after the time change');
    });

    await s.step('closing the booker\'s page releases its monitor', async () => {
        await booking.close({ runBeforeUnload: true });
        const deadline = Date.now() + 15000;
        while (lockRows(bookerNo).length !== 0 && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 500));
        h.assert(lockRows(bookerNo).length === 0, 'The booker\'s PageMonitor row survived closing the page');
    });

    await s.step('nothing was booked', async () => {
        h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no IN (${providers})`) === '0', 'The lock check created a booking');
        await ownerPopup.close().catch(() => {});
    });
}

if (require.main === module) runWorkflow('appointment-slot-lock-booker', workflow, { openPatient: false, openMaster: false });
module.exports = { workflow };
