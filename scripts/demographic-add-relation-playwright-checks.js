#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Issue #3352: open/search/select is read-only; saving a relation requires POST.
 * Owns two synthetic patients and their relationships. Run exclusively.
 * Uses the common BASE_URL, TEST_*, CHROME_PATH and MYSQL_* harness contract.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

async function workflow(s) {
  const name = `${s.marker}-rel`; // demographic.last_name is varchar(30).
  const ownedContact = `SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(name)}`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM relationships WHERE demographic_no=${s.patient} OR relation_demographic_no=${s.patient}
      OR demographic_no IN (${ownedContact}) OR relation_demographic_no IN (${ownedContact});
      DELETE FROM demographic WHERE last_name=${h.sqlString(name)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM relationships WHERE demographic_no=${s.patient}
      OR relation_demographic_no=${s.patient}`) === '0', 'Owned relationships remain');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${h.sqlString(name)}`) === '0',
      'Owned contact patient remains');
  });
  s.sql.execute(`INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(name)},'Contact','1980','01','02','M','AC',${h.sqlString(s.provider)},'ON','ON','NR',NOW())`);
  const contact = s.sql.value(ownedContact);
  h.assert(/^[1-9]\d*$/.test(contact), 'The owned contact was not created');
  const count = () => s.sql.value('SELECT COUNT(*) FROM relationships');
  const before = count();
  let popup;

  await s.step('Add Relation opens the search form with GET and creates no relationship', async () => {
    const link = masterRecordLink(s.master, '/demographic/AddRelation?');
    if (await link.count()) popup = await s.popup(s.master, link.first(), 'add-relation');
    else {
      // The modern contacts UI hides this legacy link; the legacy route remains supported.
      popup = await s.context.newPage();
      await h.gotoApp(popup, s.config.baseUrl, `/demographic/AddRelation?demo=${s.patient}`);
    }
    await popup.locator('form#ADDAPPT input[name="keyword"]').waitFor();
    h.assert(await popup.locator('input[name="remarks"]').inputValue() === s.patient, 'The popup lost its originating patient');
    h.assert(count() === before, 'Opening the popup inserted a relationship');
  });

  await s.step('searching and selecting a contact does not save a relationship', async () => {
    await popup.locator('input[name="keyword"]').fill(name);
    await clickAndAwaitReload(popup, popup.locator('#ADDAPPT input[type="submit"]'), { timeout: 30000, label: 'relation search' });
    const result = popup.locator(`tr[onclick^="addName('${contact}',"]`);
    await clickAndAwaitReload(popup, result, { timeout: 30000, label: 'relation contact selection' });
    await popup.locator('select[name="relation"]').waitFor();
    h.assert(await popup.locator('input[name="origDemo"]').inputValue() === s.patient, 'Contact selection lost the original patient');
    h.assert(await popup.locator('input[name="linkingDemo"]').inputValue() === contact, 'Contact selection chose another patient');
    h.assert(count() === before, 'Selecting a contact inserted a relationship before Save');
  });

  await s.step('GET and HEAD with save parameters return 405 without writing', async () => {
    const query = new URLSearchParams({ origDemo: s.patient, linkingDemo: contact, relation: 'Partner' });
    for (const method of ['GET', 'HEAD']) {
      const response = await s.context.request.fetch(`${s.config.baseUrl}/demographic/AddRelation?${query}`, { method });
      h.assert(response.status() === 405 && response.headers().allow === 'POST', `${method} save did not return 405 with Allow: POST`);
    }
    h.assert(count() === before, 'A non-POST save changed relationships');
  });

  await s.step('the form saves the chosen relationship through a real POST', async () => {
    await popup.locator('select[name="relation"]').selectOption('Partner');
    await popup.locator('textarea[name="notes"]').fill(s.marker);
    await Promise.all([
      popup.waitForRequest(request => request.method() === 'POST'
        && new URL(request.url()).pathname.endsWith('/demographic/AddRelation')),
      clickAndAwaitReload(popup, popup.locator('form[action$="/demographic/AddRelation"] input[type="submit"]'),
        { timeout: 30000, label: 'relation save' }),
    ]);
    await h.assertNotErrorPage(popup, 'relation save');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM relationships WHERE demographic_no=${s.patient}
      AND relation_demographic_no=${contact} AND relation='Partner' AND notes=${h.sqlString(s.marker)}`) === '1',
      'The selected relationship was not saved exactly once');
    h.assert(await popup.locator('body').innerText().then(text => text.includes(s.marker)), 'The saved relationship is not visible');
  });
}

if (require.main === module) runWorkflow('demographic-add-relation', workflow);
module.exports = { workflow };
