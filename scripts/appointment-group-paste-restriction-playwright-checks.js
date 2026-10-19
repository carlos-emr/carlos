#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Requires allowMultipleSameDayGroupAppt=no and appointment_locking_timeout>0. Copy an owned appointment, open a second
 * slot and automatically paste. The same-day warning and hidden controls must survive the real
 * page-lock refresh, including layouts where Add/Repeat are not rendered.
 * An isolated provider/group and one appointment are removed and verified at teardown.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');

async function workflow(s) {
    const { sql, marker, patient, config, recorder } = s;
    const fixture = createUnbookedThrowaway(s);
    registerAppointmentCleanup(s, fixture);
    const owner = h.sqlString(fixture.providerNo);
    const group = h.sqlString('PW' + marker.slice(-8));
    h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group}`) === '0', 'Owned group name already exists');
    h.assert(sql.value(`SELECT COUNT(*) FROM ProviderPreference WHERE providerNo=${owner}`) === '0', 'Owned provider already has preferences');
    h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE providerNo=${owner}`) === '0', 'Owned provider already has page locks');
    s.cleanup(() => {
        sql.execute(`DELETE FROM PageMonitor WHERE providerNo=${owner}; DELETE FROM mygroup WHERE mygroup_no=${group} AND provider_no=${owner}; DELETE FROM ProviderPreference WHERE providerNo=${owner}`);
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group})
            + (SELECT COUNT(*) FROM ProviderPreference WHERE providerNo=${owner})
            + (SELECT COUNT(*) FROM PageMonitor WHERE providerNo=${owner})`) === '0', 'Owned group/preferences/page locks remain');
    });
    sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name) VALUES (${group},${owner},${h.sqlString(marker)},'Test');
        INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
            lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
            VALUES (${owner},8,18,15,${group},'deepblue',0,NOW(),20,0);
        INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,reason,status,createdatetime,updatedatetime,creator)
            VALUES (${owner},CURDATE(),'10:00:00','10:14:00',${h.sqlString(marker)},${patient},${h.sqlString(marker)},'t',NOW(),NOW(),${owner})`);
    const id = sql.value(`SELECT appointment_no FROM appointment WHERE provider_no=${owner}`);
    const context = await h.newContext(s.context.browser(), config);
    s.cleanup(() => context.close());
    context.setDefaultTimeout(20000);
    context.on('page', page => h.wireStrictPage(page, 'group-paste', recorder));
    const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'group-paste-login' });
    let popup;
    await s.step('copy the owned appointment and open another slot in its schedule group', async () => {
        const link = schedule.locator(`a.apptLink[onclick*="appointment_no=${id}&"], a.apptLink[onclick*="appointment_no=${id}'"]`).first();
        const edit = await ui.clickOpensPopup(schedule, link, { context, recorder, label: 'group-paste-edit' });
        await edit.waitForLoadState('networkidle');
        const closed = edit.waitForEvent('close');
        await edit.locator('a[onclick*="appointmentcopyrecord"]').click();
        await closed;
        const slot = schedule.locator(`a.adhour[onclick*="provider_no=${fixture.providerNo}&"]`).filter({ hasText: '11:00' }).first();
        popup = await ui.clickOpensPopup(schedule, slot, { context, recorder, label: 'group-paste-add' });
        await popup.waitForLoadState('networkidle');
        if (!(await popup.locator('#pasteButton').getAttribute('onclick')).includes('true')) {
            throw new h.SkipCheck('Requires allowMultipleSameDayGroupAppt=no');
        }
        if (!await popup.evaluate(() => typeof updatePageLock === 'function')) {
            throw new h.SkipCheck('Requires appointment_locking_timeout>0');
        }
    });
    await s.step('automatic restricted Paste shows feedback without requiring absent booking controls', async () => {
        h.assert(await popup.locator('#tooManySameDayGroupApptWarning').isVisible(), 'The same-day warning is missing');
        h.assert(await popup.locator('#pasteButton').isHidden(), 'Restricted Paste remained available');
        h.assert(await popup.locator('#demographic_no').inputValue() === patient, 'The copied patient was lost');
    });
    await s.step('the scheduled lock refresh preserves the restriction and writes no booking', async () => {
        const refreshed = popup.waitForResponse(response => response.url().includes('/PageMonitoringService')
            && response.request().method() === 'POST');
        await popup.evaluate(() => {
            // The notification is replaced in the refresh callback before visibility is updated.
            // Mutation observers run after that callback completes, so this observes fresh UI state.
            window.__testLockRefreshRendered = false;
            const observer = new MutationObserver(() => {
                observer.disconnect();
                window.__testLockRefreshRendered = true;
            });
            observer.observe(document.getElementById('lock_notification'), { childList: true });
        });
        const response = await refreshed;
        await popup.waitForFunction(() => window.__testLockRefreshRendered, null, { timeout: 20000 });
        h.assert(response.ok(), `Lock refresh returned HTTP ${response.status()}`);
        const locks = await response.json();
        h.assert(locks.some(lock => lock.self && lock.locked), 'The refresh did not acquire our own lock');
        h.assert(await popup.locator('#pasteButton').isHidden(), 'Lock refresh restored restricted Paste');
        for (const id of ['addButton', 'apptRepeatButton']) {
            h.assert(await popup.locator('#' + id).isHidden(), `Lock refresh restored ${id}`);
        }
        h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`) === '1', 'The restriction check created a booking');
    });
}

if (require.main === module) runWorkflow('appointment-group-paste-restriction', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
