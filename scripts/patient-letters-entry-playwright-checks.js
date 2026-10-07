#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */

// Issue #3665 finding 1: reach the retained letter workflow from a real patient,
// keep that patient selected, and show the entry only to report readers.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openPrintMenu, assertIsPdf } = require('./demographic-labels-playwright-checks');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const LETTER_ENTRY = 'a[href*="/report/ViewGenerateLetters?"]';

async function workflow(s) {
  let letters;
  async function assertOwnedSelection() {
    await h.assertNotErrorPage(letters, 'patient letters');
    const selected = letters.locator('input[name="demos"]');
    h.assert(await selected.count() === 1 && await selected.inputValue() === s.patient && await selected.isChecked(),
      'Letter generation did not retain exactly the owned patient as its selected recipient');
    h.assert(await letters.locator('form[action$="/report/GenerateLetters"][method="POST" i]').count() === 1,
      'The letters entry did not reach the existing generation form');
  }
  await s.step('Print / Labels opens Generate Letters with only the current patient selected', async () => {
    const menu = await openPrintMenu(s.master, 20000);
    const link = menu.locator(LETTER_ENTRY);
    h.assert(await link.count() === 1, 'Print / Labels has no Generate Letters entry');
    const links = await s.master.locator(LETTER_ENTRY).evaluateAll(nodes => nodes.map(node => node.href));
    h.assert(links.length === 2 && links.every(href => new URL(href).searchParams.get('demo') === s.patient),
      'The upper toolbar and Print / Labels must both link to the current patient');
    letters = await s.popup(s.master, link, 'patient letters');
    await assertOwnedSelection();
    h.assert(await letters.evaluate(() => window.opener === null), 'The new letters tab should not retain an opener');
  });
  await s.step('Manage Letters remains reachable and browser Back retains the selected patient', async () => {
    await ui.clickAndAwaitReload(letters, letters.locator('a[href$="/report/ViewManageLetters"]'), { label: 'Manage Letters' });
    await h.assertNotErrorPage(letters, 'Manage Letters');
    h.assert(await letters.locator('form[action$="/report/ManageLetters"] input[name="reportFile"]').count() === 1,
      'The existing template-management upload form is missing');
    await letters.goBack({ waitUntil: 'domcontentloaded' });
    await assertOwnedSelection();
    await letters.close();
  });
  await s.step('the existing patient PDF Envelope control still produces a PDF', async () => {
    const menu = await openPrintMenu(s.master, 20000);
    const produced = await ui.clickDownloadsOrOpens(s.master, menu.getByRole('link', { name: 'PDF Envelope', exact: true }),
      { context: s.context, recorder: s.recorder, label: 'patient envelope', timeout: 30000 });
    try {
      const response = await s.context.request.get(produced.url, { maxRedirects: 0 });
      try { assertIsPdf({ label: 'patient envelope' }, response.status(), response.headers()['content-type'] || '', await response.body()); }
      finally { await response.dispose(); }
    } finally { if (produced.page) await produced.page.close(); }
  });

  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: s.config.testUser });
  const role = `${marker}-read`;
  let roleOwned = false;
  s.cleanup(() => {
    fixture.cleanup();
    if (roleOwned) {
      sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)} AND objectName IN ('_appointment','_msg','_demographic')`);
      h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === '0', 'Unexpected role grants retained for investigation');
      sql.execute(`DELETE FROM secRole WHERE role_name=${q(role)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'Owned report-denied role remains');
    }
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'Owned role already exists');
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === '0', 'Owned role grants already exist');
  roleOwned = true;
  sql.execute(`INSERT INTO secRole(role_name,description) VALUES(${q(role)},'Owned patient reader without reports');
    INSERT INTO secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)
    VALUES(${q(role)},'_appointment','r',0,${q(provider)}),(${q(role)},'_msg','r',0,${q(provider)}),(${q(role)},'_demographic','r',0,${q(provider)})`);
  fixture.create({ roleNames: [role], expiresTomorrow: true });
  h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(fixture.providerNo)} AND role_name<>${q(role)}`) === '0',
    'Audit account inherited other roles');
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}
    AND (objectName NOT IN ('_appointment','_msg','_demographic') OR privilege<>'r')`) === '0', 'Audit role has unexpected privileges');
  const restricted = await h.newContext(s.context.browser(), s.config);
  s.cleanup(() => restricted.close());
  await s.step('a patient reader without report permission is not offered either letters entry', async () => {
    const schedule = await h.login(restricted, { ...s.config, testUser: fixture.username }, s.recorder, { label: 'report-denied patient reader' });
    const { masterPage } = await openMasterRecord(restricted, schedule, s.recorder,
      { searchTerm: marker, preferredDemographicNo: s.patient, timeout: 20000 });
    await h.assertNotErrorPage(masterPage, 'report-denied patient master record');
    h.assert(await masterPage.locator(LETTER_ENTRY).count() === 0, 'A patient reader without report permission was offered Generate Letters');
  });
}

if (require.main === module) runWorkflow('patient-letters-entry', workflow);
module.exports = { workflow };
