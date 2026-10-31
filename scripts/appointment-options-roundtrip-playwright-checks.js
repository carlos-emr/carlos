#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(s) {
  // The legacy booking helper snapshots these values on import. Each browser
  // check is its own process; bind it to this workflow's newly owned patient.
  process.env.APPOINTMENT_DEMOGRAPHIC_NO = s.patient;
  process.env.APPOINTMENT_PROVIDER_NO = s.provider;
  const f = require('./appointment-lifecycle-playwright-checks');
  f.initMysqlDefaults();
  s.cleanup(() => {
    try {
      const ids = f.stampedAppointments().map(row => row.id);
      if (ids.length) s.sql.execute(`DELETE FROM other_id WHERE other_key='appt_mc_number' AND table_id IN (${ids.map(h.sqlString).join(',')})`);
      f.cleanupRows();
    } finally {f.cleanupMysqlDefaults();}
  });
  const snapshot = id => s.sql.rows(`SELECT reasonCode,urgency,reason,notes FROM appointment WHERE appointment_no=${id}`);
  const mc = id => s.sql.rows(`SELECT other_id FROM other_id WHERE table_id=${h.sqlString(id)} AND other_key='appt_mc_number' AND deleted=0`);
  const firstMC = s.marker.slice(-12), secondMC = firstMC + 'B';
  let primary, control, controlState, reasonA, reasonB, emptyReason;
  await f.openDaySheet(s.schedule);
  await s.step('book critical and ordinary controls through the actual Add form', async () => {
    control = await f.bookFromSlot(s.context, s.schedule, {slotIndex: 2, configureAdd: async page => {
      await page.locator('#urgency').uncheck();
      await page.locator('#appt_mc_number').fill('CONTROL-' + firstMC);
    }});
    controlState = JSON.stringify({row: snapshot(control.id), mc: mc(control.id)});
    primary = await f.bookFromSlot(s.context, s.schedule, {slotIndex: 4, configureAdd: async page => {
      const reasons = await page.locator('#reasonCode option').evaluateAll(nodes => nodes.map(node => ({value: node.value, label: node.textContent.trim()})));
      const coded = reasons.filter(option => option.value !== '-1' && option.label);
      h.assert(coded.length >= 2, 'Appointment reason fixture needs two active named reasons');
      [reasonA, reasonB] = coded;
      emptyReason = reasons.find(option => !option.label || option.value === '-1');
      h.assert(emptyReason, 'Appointment reason fixture needs its ordinary/empty option');
      await page.locator('#reasonCode').selectOption(reasonA.value);
      await page.locator('#urgency').check();
      await page.locator('#appt_mc_number').fill(firstMC);
    }});
    h.assert(snapshot(primary.id)[0][0] === reasonA.value && snapshot(primary.id)[0][1] === 'critical', 'Add dropped selected reason or urgency');
    h.assert(JSON.stringify(mc(primary.id)) === JSON.stringify([[firstMC]]), 'MC number was not attached exactly once to the new appointment');
  });
  async function reopen() {return f.openEditPopup(s.context, s.schedule, primary.id);}
  async function save(page) {
    const [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/appointment/UpdateRecord')),
      page.locator('#updateButton').click(),
    ]);
    h.assert(response.status() === 200, `Appointment update returned HTTP ${response.status()}`);
    await page.close();
  }
  async function shown(critical) {
    await f.openDaySheet(s.schedule);
    const link = s.schedule.locator(`a.apptLink[onclick*="appointment_no=${primary.id}"]`).first();
    await link.waitFor({state: 'visible'});
    const row = link.locator('xpath=ancestor::td[1]');
    h.assert(await row.locator('img[alt="Critical Appointment"]').count() === (critical ? 1 : 0), 'Day sheet critical indicator does not match stored urgency');
  }
  await s.step('reopen, preserve critical through an unrelated edit, and cancel field changes', async () => {
    await shown(true);
    let page = await reopen();
    h.assert(await page.locator('#urgency').isChecked() && await page.locator('#reasonCode').inputValue() === reasonA.value
      && await page.locator('#appt_mc_number').inputValue() === firstMC, 'Reopened Add values differ');
    await page.locator('textarea[name="notes"]').fill(f.bookedNotes + ' unrelated'); await save(page);
    h.assert(snapshot(primary.id)[0][1] === 'critical', 'Unrelated edit reset urgency');
    const before = JSON.stringify({row: snapshot(primary.id), mc: mc(primary.id)});
    page = await reopen();
    await page.locator('#urgency').uncheck(); await page.locator('#reasonCode').selectOption(reasonB.value);
    await page.locator('#appt_mc_number').fill(secondMC); await page.close();
    h.assert(JSON.stringify({row: snapshot(primary.id), mc: mc(primary.id)}) === before, 'Closing an unsaved edit wrote options');
  });
  await s.step('change reason and MC number and clear critical through Edit', async () => {
    const page = await reopen(); await page.locator('#urgency').uncheck();
    await page.locator('#reasonCode').selectOption(reasonB.value); await page.locator('#appt_mc_number').fill(secondMC); await save(page);
    const [row] = snapshot(primary.id);
    h.assert(row[0] === reasonB.value && row[1] !== 'critical', 'Edit dropped reason change or critical clearing');
    h.assert(JSON.stringify(mc(primary.id)) === JSON.stringify([[secondMC]]), 'MC update created duplicate active extensions');
    await shown(false);
  });
  await s.step('clear reason and MC number and reopen the intended empty representation', async () => {
    let page = await reopen();
    h.assert(await page.locator('#reasonCode').inputValue() === reasonB.value && await page.locator('#appt_mc_number').inputValue() === secondMC,
      'Changed options did not reopen');
    await page.locator('#reasonCode').selectOption(emptyReason.value); await page.locator('#appt_mc_number').fill(''); await save(page);
    // OtherIdManager preserves history by soft deleting old versions; clearing
    // an existing MC value leaves exactly one active empty extension.
    h.assert(JSON.stringify(mc(primary.id)) === JSON.stringify([['']]), 'MC clear did not preserve its active empty representation');
    page = await reopen();
    h.assert(await page.locator('#reasonCode').inputValue() === emptyReason.value && await page.locator('#appt_mc_number').inputValue() === ''
      && !await page.locator('#urgency').isChecked(), 'Cleared options did not reopen'); await page.close();
    h.assert(JSON.stringify({row: snapshot(control.id), mc: mc(control.id)}) === controlState, 'Editing the primary appointment changed its control');
    h.assertStrictPage(f.recorder);
  });
}
if (require.main === module) runWorkflow('appointment-options-roundtrip', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
