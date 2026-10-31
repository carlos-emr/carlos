#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {randomBytes} = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');
const {throwawayLoginFixture} = require('./lib/throwaway-login-fixture');
const {removeMarkedPatients} = require('./lib/gap-records-fixtures');
async function workflow(s) {
  const {sql, patient, master, marker} = s;
  const controlName = marker + '-CONTROL';
  s.cleanup(() => removeMarkedPatients(sql, controlName));
  sql.execute(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(controlName)},'Control','1980','01','02','F','AC',${h.sqlString(s.provider)},'ON','ON','NR',NOW())`);
  const control = sql.value(`SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(controlName)}`);
  s.cleanup(() => sql.execute(`DELETE FROM demographicExtArchive WHERE demographic_no=${patient}; DELETE FROM demographiccust WHERE demographic_no=${patient}`));
  const state = id => sql.rows(`SELECT patient_status,COALESCE(DATE(patient_status_date),''),first_name,last_name,hin,sex FROM demographic WHERE demographic_no=${id}`);
  const controlState = JSON.stringify(state(control));
  async function edit() {
    if (await master.locator('#editBtn').isVisible()) await master.locator('#editBtn').click();
    await master.locator('#editDemographic').waitFor({state: 'visible'});
  }
  async function save() {
    await ui.clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(), {label: 'Update Record', timeout: 30000});
  }
  async function date(value) {
    const parts = value ? value.split('-') : ['', '', ''];
    for (const [index, field] of ['year', 'month', 'day'].entries()) await master.locator(`[name="patientstatus_date_${field}"]`).fill(parts[index]);
  }
  async function searchMembership(expectedInactive) {
    for (const mode of ['active', 'inactive', 'all']) {
      const {page} = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#search a').first(),
        {context: s.context, recorder: s.recorder, label: 'status-search', timeout: 20000});
      await page.locator('#keyword, input[name="keyword"]').first().fill(marker);
      const button = mode === 'active' ? page.locator('input[type="submit"]').first()
        : page.locator(`input[onclick*="${mode === 'inactive' ? 'searchInactive' : 'searchAll'}"]`).first();
      await ui.clickAndAwaitReload(page, button, {label: 'status patient search', timeout: 20000});
      const ids = await page.locator('a[title="Master Demographic File"]').evaluateAll(links => links.map(link => /demographic_no=(\d+)/.exec(link.getAttribute('onclick') || '')?.[1]));
      h.assert(ids.includes(patient) === (mode === 'all' || (mode === 'inactive') === expectedInactive), 'Status search returned incorrect primary patient membership');
      h.assert(ids.includes(control) === (mode !== 'inactive'), 'Status search changed active control membership');
      if (page !== s.schedule) await page.close();
    }
  }
  for (const status of ['DE', 'MO', 'FI']) await s.step(`${status} status and date archive, reopen and reactivate through the editor`, async () => {
    await edit(); await h.withExpectedDialogs(master, () => master.locator('[name="patient_status"]').selectOption(status));
    await date('2025-02-03'); await save();
    h.assert(state(patient)[0][0] === status && state(patient)[0][1] === '2025-02-03', 'Status/date were not saved');
    await searchMembership(true);
    await edit();
    h.assert(await master.locator('[name="patient_status"]').inputValue() === status, 'Reopened status differs');
    await date('2025-03-04'); await save();
    h.assert(state(patient)[0][1] === '2025-03-04', 'Status date edit was lost');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicArchive WHERE demographic_no=${patient} AND patient_status=${h.sqlString(status)} AND DATE(patient_status_date)='2025-02-03'`) !== '0',
      'The prior dated status was not archived');
    const before = JSON.stringify(state(patient));
    await edit(); await master.locator('[name="patient_status"]').selectOption('AC');
    await master.reload(); h.assert(JSON.stringify(state(patient)) === before, 'Abandoning an edit changed status');
    await edit(); await master.locator('[name="patient_status"]').selectOption('AC'); await date(''); await save();
    h.assert(state(patient)[0][0] === 'AC' && state(patient)[0][1] === '', 'Reactivation retained a misleading inactive date');
    await searchMembership(false);
    h.assert(JSON.stringify(state(control)) === controlState, 'Status changes affected the control patient');
  });
  await s.step('a partial status date follows the editor null-date contract', async () => {
    await edit(); await master.locator('[name="patient_status"]').selectOption('MO'); await date('2025--'); await save();
    h.assert(state(patient)[0][0] === 'MO' && state(patient)[0][1] === '', 'Partial status date was not represented as null');
    await edit(); await master.locator('[name="patient_status"]').selectOption('AC'); await date(''); await save();
  });
  const providers = [];
  for (let index = 0; index < 3; index++) {
    const fixture = throwawayLoginFixture({sql, marker: 'FAKE-PW' + randomBytes(8).toString('hex'), provider: s.provider, testUser: s.config.testUser});
    s.cleanup(() => fixture.cleanup()); fixture.create(); providers.push(fixture.providerNo);
    if (index < 2) sql.execute(`INSERT INTO secUserRole(provider_no,role_name,orgcd) VALUES (${h.sqlString(fixture.providerNo)},'midwife','')`);
  }
  const midwife = () => sql.value(`SELECT COALESCE(cust4,'') FROM demographiccust WHERE demographic_no=${patient}`);
  await s.step('eligible midwives assign, replace, preserve, cancel and clear on one patient', async () => {
    for (const selected of providers.slice(0, 2)) {
      await edit(); const picker = master.locator('select[name="midwife"]');
      h.assert(await picker.locator(`option[value="${providers[2]}"]`).count() === 0, 'A provider without a midwife role was offered');
      await picker.selectOption(selected); await save(); h.assert(midwife() === selected, 'Midwife assignment was not stored');
      await edit(); h.assert(await master.locator('select[name="midwife"]').inputValue() === selected, 'Midwife assignment did not reopen');
      await master.locator('[name="city"]').fill('Synthetic City'); await save(); h.assert(midwife() === selected, 'Unrelated edit reset the midwife');
    }
    await edit(); await master.locator('select[name="midwife"]').selectOption(providers[0]); await master.reload();
    h.assert(midwife() === providers[1], 'Cancelled midwife edit changed the assignment');
    await edit(); await master.locator('select[name="midwife"]').selectOption(''); await save(); h.assert(midwife() === '', 'Clearing midwife was not stored');
    await edit(); h.assert(await master.locator('select[name="midwife"]').inputValue() === '', 'Cleared midwife did not reopen'); await master.reload();
    h.assert(JSON.stringify(state(control)) === controlState, 'Midwife changes affected the control patient');
  });
}
if (require.main === module) runWorkflow('demographic-status-midwife', workflow, {openPatient: true});
module.exports = {workflow};
