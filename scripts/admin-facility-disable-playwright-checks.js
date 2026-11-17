#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Administration's CAISI facility list: every row keeps its own native POST form.
// One marker-owned facility copies configuration columns only, without memberships,
// programs or clinical data. The shared authz fixture verifies ownership and removes it.
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {authzReadFixture} = require('./lib/authz-read-fixture');

async function workflow(s) {
  const fixture = authzReadFixture({sql: s.sql, marker: s.marker, provider: s.provider, testUser: s.config.testUser});
  s.cleanup(() => fixture.cleanup());
  const owned = fixture.addFacility('D');
  const q = h.sqlString;
  const state = () => s.sql.value(`SELECT disabled FROM Facility WHERE id=${owned.id}
    AND name=${q(owned.name)} AND description=${q(s.marker)}`);
  const page = await s.context.newPage();
  const target = new URL(h.appUrl(s.config.baseUrl, '/PMmodule/FacilityManager'));
  const row = () => page.locator('#facility tbody tr').filter({hasText: owned.name});
  const disable = () => row().getByRole('link', {name: 'Disable', exact: true});

  await s.step('every listed facility has its own native POST form, including the first row', async () => {
    await h.gotoApp(page, s.config.baseUrl, '/PMmodule/FacilityManager');
    await page.locator('#facility').waitFor();
    h.assert(await row().count() === 1, 'The owned active facility is absent from the native list');
    const missing = await page.locator('#facility a[onclick*="deleteForm_"]').evaluateAll(links => links
      .map(link => link.getAttribute('onclick').match(/deleteForm_(\d+)/)?.[1])
      .filter(id => !id || !document.getElementById('deleteForm_' + id)));
    h.assert(missing.length === 0, 'The browser dropped native facility forms: ' + missing.join(','));
    const form = page.locator('#deleteForm_' + owned.id);
    h.assert(await form.count() === 1, 'The owned facility has no native Disable form');
    await page.waitForFunction(id => Boolean(document.querySelector('#deleteForm_' + id
      + ' input[name="CSRF-TOKEN"]')?.value), owned.id, {timeout: 20000});
  });

  await s.step('cancelling Disable preserves the owned facility', async () => {
    const dialogs = await h.withExpectedDialogs(page, () => disable().click(), {accept: false});
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm'
      && dialogs[0].text.includes(owned.name), 'Cancel did not show this facility confirmation');
    h.assert(state() === '0', 'Cancelling disabled the owned facility');
    h.assert(await row().count() === 1, 'Cancelling removed the owned facility from the list');
  });

  await s.step('a token-less Disable POST is refused without changing the owned facility', async () => {
    const response = await s.context.request.post(target.href, {
      form: {method: 'delete', id: owned.id, name: owned.name}, failOnStatusCode: false,
    });
    h.assert(response.status() === 403, 'Token-less Disable answered HTTP ' + response.status());
    h.assert(state() === '0', 'The token-less POST changed the owned facility');
  });

  await s.step('GET and HEAD cannot dispatch facility delete or save', async () => {
    for (const verb of ['GET', 'HEAD']) {
      for (const method of ['delete', 'save']) {
        const response = await s.context.request.fetch(target.href, {
          method: verb, params: {method, id: owned.id, 'facility.id': owned.id},
          failOnStatusCode: false,
        });
        h.assert(response.status() === 405, verb + ' ' + method + ' answered HTTP ' + response.status());
        h.assert(response.headers().allow === 'POST', 'Rejected mutation omitted Allow: POST');
        h.assert(state() === '0', verb + ' ' + method + ' changed the owned facility');
      }
    }
  });

  await s.step('confirmed Disable posts this facility and persists its disabled state', async () => {
    const response = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === target.pathname);
    const dialogs = await h.withExpectedDialogs(page, () => disable().click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm'
      && dialogs[0].text.includes(owned.name), 'Disable did not show this facility confirmation');
    const saved = await response;
    const fields = new URLSearchParams(saved.request().postData() || '');
    h.assert(saved.status() === 200, 'Native Disable answered HTTP ' + saved.status());
    h.assert(fields.get('method') === 'delete' && fields.get('id') === owned.id
      && fields.get('name') === owned.name, 'Native Disable posted another facility');
    h.assert(Boolean(fields.get('CSRF-TOKEN')), 'Native Disable omitted its CSRF token');
    h.assert(state() === '1', 'Native Disable did not persist the owned disabled state');
    await h.gotoApp(page, s.config.baseUrl, '/PMmodule/FacilityManager');
    await page.locator('#facility').waitFor();
    h.assert(await row().count() === 0, 'The disabled owned facility is still listed as active');
  });
}

if (require.main === module) runWorkflow('admin-facility-disable', workflow, {openPatient: false});
module.exports = {workflow};
