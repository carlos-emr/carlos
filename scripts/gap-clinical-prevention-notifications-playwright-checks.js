#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Gap check (clinical): Administration ▸ "Prevention Notification Settings" -- the clinic-wide switch
 * for the "stop sign" prevention reminders shown on the appointment screen.
 *
 * User path: Schedule ▸ Administration ▸ Prevention Notification Settings (loads
 * /prevention/ViewPreventionManager in the admin iframe) ▸ "Display on Appointment Screen" Enabled /
 * Disabled ▸ Save; per-prevention Enabled / Disabled radios ▸ Save Custom.
 * schedule-setting only opens this page; nothing drives its two forms. The setting is global (every
 * provider's appointment screen) and stored as one property row, hide_prevention_stop_signs, in three
 * shapes the page and PreventionManager.isDisabled()/isPrevDisabled() must agree on: "master" (all off),
 * "false" (all on) and "[Type][Type]" (those types off).
 *
 * Asserts: Disabled + Save stores "master" and the reloaded page shows Disabled with the per-prevention
 * rows and Save Custom disabled; Enabled + Save stores "false" and re-enables them; disabling two named
 * prevention types and Save Custom stores exactly "[A][B]" and the reloaded page shows those two (only)
 * as Disabled; Save Custom with every type Enabled stores "false"; one property row exists throughout
 * (no duplicate row per save). With no row at all the page must show the state the appointment screen
 * actually uses (isDisabled() is false when the row is absent, so the page must read Enabled).
 *
 * Fixtures: one owned synthetic child and appointment, deleted after the check.
 * The hide_prevention_stop_signs row (global) is snapshotted first and restored
 * (re-inserted, updated or deleted) in cleanup, so run EXCLUSIVE=1. Coverage plan §3.4 prevention-admin.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const PROP = 'hide_prevention_stop_signs';

async function openSettings(s) {
  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'prevention-notification-admin', timeout: 20000});
  const link = admin.getByRole('link', {name: 'Prevention Notification Settings', exact: true, includeHidden: true}).first();
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The prevention notification settings iframe did not load');
  await frame.locator('form[name="masterForm"]').waitFor();
  // A clinician re-opens the page from the same menu item to see what was stored (the admin shell may
  // swap in a fresh iframe, so the frame is resolved again).
  const reopen = async () => {
    await revealAuditLink(admin, link, 20000);
    await link.click();
    await admin.waitForTimeout(500);
    const fresh = await (await admin.locator('#dynamic-content iframe').first().elementHandle()).contentFrame();
    await fresh.locator('form[name="masterForm"]').waitFor();
    return fresh;
  };
  return {admin, frame, reopen};
}

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const rows = () => sql.rows(`SELECT id, value, provider_no FROM property WHERE name=${h.sqlString(PROP)} ORDER BY id`);
  const snapshot = rows();
  s.cleanup(() => {
    sql.execute(`DELETE FROM property WHERE name=${h.sqlString(PROP)}`);
    for (const [, value, provider] of snapshot) {
      sql.execute(`INSERT INTO property (name, value, provider_no) VALUES (${h.sqlString(PROP)}, ${h.sqlString(value)},
        ${provider === 'NULL' || provider === '' ? 'NULL' : h.sqlString(provider)})`);
    }
    h.assert(JSON.stringify(rows().map(r => r[1])) === JSON.stringify(snapshot.map(r => r[1])), 'The prevention stop-sign setting was not restored');
  });

  // A three-year-old with no vaccinations has a deterministic DTaP-IPV warning.
  sql.execute(`UPDATE demographic SET year_of_birth=YEAR(DATE_SUB(CURDATE(), INTERVAL 3 YEAR)),
    month_of_birth=MONTH(DATE_SUB(CURDATE(), INTERVAL 3 YEAR)),date_of_birth=DAY(DATE_SUB(CURDATE(), INTERVAL 3 YEAR)) WHERE demographic_no=${patient}`);
  let appointment;
  s.cleanup(() => {
    if (appointment) {
      sql.execute(`DELETE FROM appointment WHERE appointment_no=${appointment} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointment}`) === '0',
        'The owned prevention appointment was not removed');
    }
  });
  appointment = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,
    notes,reason,location,resources,type,style,billing,status,createdatetime,updatedatetime,creator,lastupdateuser)
    VALUES (${h.sqlString(provider)},CURDATE(),'10:00:00','10:15:00',${h.sqlString(marker)},${patient},
      '','FAKE prevention warning check','','',NULL,'','','t',NOW(),NOW(),${h.sqlString(s.config.testUser)},${h.sqlString(provider)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointment), 'The prevention appointment was not created');
  const dayUrl = new URL(s.schedule.url());
  const dayPath = dayUrl.pathname.slice(s.config.baseUrl.pathname.length) + dayUrl.search;
  const dayPage = await s.context.newPage();
  async function assertScheduleWarning(enabled) {
    await h.gotoApp(dayPage, s.config.baseUrl, dayPath);
    const link = dayPage.locator(`a.apptLink[onclick*="appointment_no=${appointment}&"]`);
    await link.waitFor();
    const warning = dayPage.locator('td.appt').filter({has: link}).locator('img[src$="/images/stop_sign.png"]');
    h.assert(await warning.count() === (enabled ? 1 : 0),
      `The day-sheet prevention warning should be ${enabled ? 'visible' : 'hidden'}`);
    if (enabled) h.assert((await warning.getAttribute('title')).includes('DTaP-IPV'),
      'The day sheet did not generate the owned patient vaccination warning');
  }

  const opened = await openSettings(s);
  const {admin, reopen} = opened;
  let frame = opened.frame;
  const value = () => rows().map(r => r[1]);
  /** Submits one of the page's forms and waits for the iframe to reload. */
  async function submit(form, button) {
    const navigated = admin.waitForEvent('framenavigated', {predicate: f => f === frame, timeout: 20000});
    navigated.catch(() => {});
    await frame.locator(`form[name="${form}"] input[type="submit"]`).filter({hasText: button}).first().click().catch(async () => {
      await frame.locator(`form[name="${form}"] input[type="submit"]`).first().click();
    });
    await navigated;
    await frame.locator('form[name="masterForm"]').waitFor();
  }
  const masterRadio = which => frame.locator(`form[name="masterForm"] input[name="master_radio"][value="${which}"]`);
  const prevRows = () => frame.locator('form[name="prevForm"] tr:has(input[type="radio"])');
  async function prevNames() {
    return frame.locator('form[name="prevForm"] input[type="radio"][name^="onOff"]:not([value="0"])').evaluateAll(els => els.map(e => e.value));
  }

  // Reported after every other step has run, so this application defect does not hide the round trips.
  const defects = [];
  await s.step('with no setting row the page shows the state the appointment screen uses (Enabled)', async () => {
    sql.execute(`DELETE FROM property WHERE name=${h.sqlString(PROP)}`);
    frame = await reopen();
    h.assert(value().length === 0, 'The setting row was not removed for the starting-state step');
    await assertScheduleWarning(true);
    // PreventionManager.isDisabled() is false without a row: stop signs ARE shown, so Enabled must be checked.
    if (!await masterRadio('false').isChecked()) {
      defects.push('With no setting row the appointment screen shows stop signs (PreventionManager.isDisabled() is false), '
        + 'but the settings page reads "Disabled" (PreventionManager.jsp falls back to SHOW_PREVENTION_STOP_SIGNS=false)');
    }
  });

  await s.step('Disabled + Save stores "master" and disables the per-prevention controls', async () => {
    // The page can already read Disabled (the starting-state defect above); clicking an already checked
    // radio does nothing, so move the selection to Enabled (unsaved) first to make Disabled a real change.
    if (await masterRadio('master').isChecked()) await masterRadio('false').check();
    // Picking Disabled raises a confirm() ("disable all prevention notifications"); a clinician accepts it.
    const seen = await h.withExpectedDialogs(admin, () => masterRadio('master').check(), {accept: true});
    h.assert(seen.length === 1 && seen[0].type === 'confirm' && /disable all prevention notifications/i.test(seen[0].text),
      `Choosing Disabled should raise exactly one confirmation about disabling all prevention notifications, saw ${JSON.stringify(seen.map(d => d.text))}`);
    await submit('masterForm');
    h.assert(JSON.stringify(value()) === JSON.stringify(['master']), `Expected one row "master", found ${JSON.stringify(value())}`);
    h.assert(await masterRadio('master').isChecked(), 'The page does not show Disabled after saving it');
    h.assert(await frame.locator('form[name="prevForm"] input[type="radio"][disabled]').count() > 0, 'The per-prevention radios were not disabled');
    h.assert(await frame.locator('form[name="prevForm"] input[type="submit"]').isDisabled(), 'Save Custom stayed enabled while all notifications are off');
    await assertScheduleWarning(false);
  });

  await s.step('Enabled + Save stores "false" in the same row and re-enables the per-prevention controls', async () => {
    await masterRadio('false').check();
    await submit('masterForm');
    h.assert(JSON.stringify(value()) === JSON.stringify(['false']), `Expected one row "false", found ${JSON.stringify(value())}`);
    h.assert(await frame.locator('form[name="prevForm"] input[type="radio"][disabled]').count() === 0, 'The per-prevention radios stayed disabled');
    await assertScheduleWarning(true);
  });

  let names;
  await s.step('Save Custom stores exactly the two disabled prevention types and the page shows only those two Disabled', async () => {
    names = await prevNames();
    h.assert(names.length >= 3, 'The page lists fewer than three prevention types');
    const pick = [names[0], names[2]];
    for (const [i, name] of names.entries()) {
      if (pick.includes(name)) await frame.locator(`form[name="prevForm"] input[type="radio"][name="onOff${i}"][value="${name}"]`).check();
    }
    await submit('prevForm');
    h.assert(JSON.stringify(value()) === JSON.stringify([`[${pick[0]}][${pick[1]}]`]), `Expected "[${pick[0]}][${pick[1]}]", found ${JSON.stringify(value())}`);
    const disabled = await frame.locator('form[name="prevForm"] input[type="radio"][name^="onOff"]:checked:not([value="0"])').evaluateAll(els => els.map(e => e.value));
    h.assert(JSON.stringify(disabled) === JSON.stringify(pick), `The reloaded page shows ${JSON.stringify(disabled)} Disabled, expected ${JSON.stringify(pick)}`);
    h.assert(await masterRadio('false').isChecked(), 'A custom list should leave the master switch Enabled');
  });

  await s.step('Save Custom with every type Enabled stores "false"', async () => {
    for (const i of names.keys()) await frame.locator(`form[name="prevForm"] input[type="radio"][name="onOff${i}"][value="0"]`).check();
    await submit('prevForm');
    h.assert(JSON.stringify(value()) === JSON.stringify(['false']), `Expected one row "false", found ${JSON.stringify(value())}`);
    h.assert(await prevRows().count() >= names.length, 'The prevention rows vanished after the save');
  });
  h.assert(defects.length === 0, defects.join(' | '));
}

if (require.main === module) runWorkflow('gap-clinical-prevention-notifications', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
