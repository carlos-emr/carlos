#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Issue #4865: Schedule > Tickler > Assigned to clinic > provider > Create Report.
 * Requires multisites=on; otherwise skips. Two owned clinics contain respectively three
 * active providers plus an inactive provider, and just the logged-in provider. Provider
 * names include quotes and HTML metacharacters. Two owned ticklers have distinct assignees.
 * Cleanup removes the ticklers, clinics, links and throwaway providers and verifies removal.
 * Run exclusively: the owned clinics are visible in clinic-wide menus while this runs.
 */
'use strict';
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { waitForTicklerTable } = require('./tickler-forward-filters-playwright-checks');
const { resetProviderFilters } = require('./search-sort-tickler-list-playwright-checks');
const q = h.sqlString;

const STEP = {
  providers: 'a clinic with multiple providers renders every active provider and excludes inactive providers',
  switch: 'switching clinics replaces the providers and Select Clinic clears them',
  filter: 'each clinic assignee filters the returned and displayed ticklers',
};

async function workflow(s) {
  const { sql, provider, patient, marker } = s;
  // Check the install before creating clinic-wide fixtures.
  const probe = await s.context.newPage();
  await h.gotoApp(probe, s.config.baseUrl, '/tickler/ViewTicklerMain');
  if (!await probe.locator('select#site').count()) throw new h.SkipCheck('Requires multisites=on');
  await probe.close();

  const providers = [];
  for (const [first, status] of [[`Active "A" & <B>`, '1'], ['Active second', '1'], ['Inactive', '0']]) {
    const fixture = throwawayLoginFixture({ sql, provider, testUser: s.config.testUser,
      marker: `FAKE-PW${randomBytes(8).toString('hex')}` });
    s.cleanup(() => fixture.cleanup());
    fixture.create();
    sql.execute(`UPDATE provider SET first_name=${q(first)},status=${q(status)} WHERE provider_no=${q(fixture.providerNo)}`);
    providers.push({ no: fixture.providerNo, label: `${sql.value(`SELECT last_name FROM provider WHERE provider_no=${q(fixture.providerNo)}`)}, ${first}` });
  }
  const siteIds = [];
  s.cleanup(() => {
    if (!siteIds.length) return;
    const ids = siteIds.join(',');
    h.assert(sql.value(`SELECT COUNT(*) FROM site WHERE site_id IN (${ids}) AND name LIKE ${q(`${marker}%`)}`) === String(siteIds.length),
      'Owned clinic identity changed');
    sql.execute(`DELETE FROM providersite WHERE site_id IN (${ids}); DELETE FROM site WHERE site_id IN (${ids})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM site WHERE site_id IN (${ids}))
      + (SELECT COUNT(*) FROM providersite WHERE site_id IN (${ids}))`) === '0', 'Owned clinics or links remain');
  });
  for (const [label, numbers] of [['Multiple', [provider, ...providers.map(p => p.no)]], ['Single', [provider]]]) {
    const id = h.insertId(sql, `INSERT INTO site (name,short_name,status) VALUES (${q(`${marker} ${label}`)},${q(`${marker.slice(-8)}${label[0]}`)},1)`, 'clinic');
    h.assert(/^[1-9]\d*$/.test(id), 'Owned clinic key is invalid');
    siteIds.push(id);
    sql.execute(numbers.map(no => `INSERT INTO providersite (provider_no,site_id) VALUES (${q(no)},${id})`).join(';'));
  }
  const ticklers = [];
  s.cleanup(() => {
    if (!ticklers.length) return;
    const ids = ticklers.map(t => t.id).join(',');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE tickler_no IN (${ids}) AND demographic_no=${patient}
      AND message LIKE ${q(`${marker}%`)}`) === String(ticklers.length), 'Owned tickler identity changed');
    sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids}); DELETE FROM tickler_comments WHERE tickler_no IN (${ids});
      DELETE FROM tickler_update WHERE tickler_no IN (${ids}); DELETE FROM tickler WHERE tickler_no IN (${ids})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE tickler_no IN (${ids})`) === '0', 'Owned ticklers remain');
  });
  for (const assignee of providers.slice(0, 2)) {
    const message = `${marker} assigned to ${assignee.no}`;
    const id = h.insertId(sql, `INSERT INTO tickler (demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES (${patient},${q(message)},'A',NOW(),NOW(),${q(provider)},'Normal',${q(assignee.no)})`, 'tickler');
    h.assert(/^[1-9]\d*$/.test(id), 'Owned tickler key is invalid');
    ticklers.push({ id, message, assignee: assignee.no });
  }

  const list = await s.context.newPage();
  await h.gotoApp(list, s.config.baseUrl, `/tickler/ViewTicklerMain?demoview=${patient}&ticklerview=A`);
  await waitForTicklerTable(list);
  await resetProviderFilters(list);
  const clinic = list.locator('select#site');
  const assigned = list.locator('select#assignedTo');
  const values = () => assigned.locator('option').evaluateAll(options => options.map(o => o.value).sort());
  await s.step(STEP.providers, async () => {
    h.assert(await list.evaluate(() => typeof changeSite === 'function'), 'The clinic script failed to define changeSite');
    await clinic.selectOption(siteIds[0]);
    h.assert(JSON.stringify(await values()) === JSON.stringify([provider, ...providers.slice(0, 2).map(p => p.no)].sort()),
      'The multi-provider clinic has missing, inactive or unrelated providers');
    const names = await assigned.locator('option').evaluateAll(options => options.map(o => o.textContent));
    for (const p of providers.slice(0, 2)) h.assert(names.includes(p.label), 'Provider name did not round-trip through JavaScript and HTML encoding');
  });
  await s.step(STEP.switch, async () => {
    await clinic.selectOption(siteIds[1]);
    h.assert(JSON.stringify(await values()) === JSON.stringify([provider]), 'Switching clinics retained another clinic\'s providers');
    await clinic.selectOption('none');
    h.assert((await values()).length === 0, 'Select Clinic did not clear the assignees');
    await clinic.selectOption(siteIds[0]);
    h.assert((await values()).length === 3, 'Returning to the multi-provider clinic lost providers');
  });
  await s.step(STEP.filter, async () => {
    for (const tickler of ticklers) {
      await assigned.selectOption(tickler.assignee);
      const [response] = await Promise.all([
        list.waitForResponse(r => r.request().method() === 'GET' && h.pathOnly(r.url()).endsWith('/tickler/ListTicklers')
          && new URL(r.url()).searchParams.get('assignee') === tickler.assignee),
        list.locator('#formSubmitBtn').click(),
      ]);
      h.assert(response.status() === 200, 'The assignee filter request failed');
      const json = await response.json();
      h.assert(Array.isArray(json.data) && json.data.length === 1 && String(json.data[0].id) === tickler.id,
        'The assignee filter returned the wrong ticklers');
      await waitForTicklerTable(list);
      await list.locator('#ticklerResults tbody tr').filter({ hasText: tickler.message }).waitFor({ state: 'visible' });
      const other = ticklers.find(t => t.id !== tickler.id);
      h.assert(await list.locator('#ticklerResults tbody tr').filter({ hasText: other.message }).count() === 0,
        'The list displayed another assignee\'s tickler');
    }
  });
}

if (require.main === module) runWorkflow('multisite-tickler-filter', workflow, { openMaster: false });
module.exports = { workflow, STEP };
