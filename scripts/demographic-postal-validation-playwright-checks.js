#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5243: Schedule > Search > Master Record > Edit > Update Record.
 * A blank or legacy postal code must not block unrelated edits. Changed invalid
 * values must still block the POST; a valid correction must persist on reload.
 * Uses only the workflow's owned synthetic patient and removes its update rows.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { master, sql, patient, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographicExtArchive WHERE demographic_no=${patient};
      DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient})`) === '0',
    'The postal validation fixture left update rows');
  });
  const edit = async () => {
    await master.locator('#editBtn').click();
    await master.locator('#editDemographic').waitFor({ state: 'visible' });
    await master.locator('#updateButton').waitFor({ state: 'visible' });
  };
  const submit = () => master.locator('#updateButton input[type="submit"]').first();
  const save = () => ui.clickAndAwaitReload(master, submit(), { timeout: 20000, label: 'Update Record' });
  const storedPostal = () => sql.value(`SELECT IFNULL(postal,'') FROM demographic WHERE demographic_no=${patient}`);
  const archives = () => sql.value(`SELECT COUNT(*) FROM demographicArchive WHERE demographic_no=${patient}`);

  for (const postal of ['', 'UNKNOWN']) {
    await s.step(`an unrelated city edit saves with unchanged ${postal || 'blank'} postal code`, async () => {
      // The first case is exactly the shared fixture reported by all three checks.
      if (postal) {
        sql.execute(`UPDATE demographic SET postal=${h.sqlString(postal)} WHERE demographic_no=${patient}
          AND last_name=${h.sqlString(marker)}`);
        await master.reload({ waitUntil: 'domcontentloaded' });
      }
      h.assert(storedPostal() === postal, 'The fixture does not hold the intended postal value');
      await edit();
      h.assert(await master.locator('[name="postal"]').inputValue() === postal, 'The form changed the legacy postal value');
      const city = postal ? 'Legacy Fixture City' : 'Blank Fixture City';
      await master.locator('[name="city"]').fill(city);
      await save();
      await expectValue(sql, `SELECT city FROM demographic WHERE demographic_no=${patient}`, city, 'The city edit did not persist');
      h.assert(storedPostal() === postal, 'An unrelated edit changed the stored legacy postal value');
    });
  }

  await s.step('changing province revalidates an unchanged legacy postal code', async () => {
    await edit();
    const before = archives();
    const province = master.locator('[name="province"]');
    const originalProvince = await province.inputValue();
    const storedProvince = sql.value(`SELECT province FROM demographic WHERE demographic_no=${patient}`);
    const bc = await province.locator('option[value="CA-BC"]').count() ? 'CA-BC' : 'BC';
    await province.selectOption(bc);
    const dialogs = await h.withExpectedDialogs(master, () => submit().click());
    h.assert(dialogs.length === 1 && dialogs[0].text === 'The entered Postal Code is not valid',
      'Changing province did not revalidate the legacy postal code');
    h.assert(storedPostal() === 'UNKNOWN' && archives() === before
      && sql.value(`SELECT province FROM demographic WHERE demographic_no=${patient}`) === storedProvince,
    'The refused province change altered the patient');
    await province.selectOption(originalProvince);
  });

  await s.step('changing the postal code to an invalid value blocks the POST and preserves the database', async () => {
    const before = archives();
    let posts = 0;
    const countPost = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/demographic/DemographicUpdate')) posts++;
    };
    master.on('request', countPost);
    try {
      const dialogs = await h.withExpectedDialogs(master, async () => {
        await master.locator('[name="postal"]').fill('12345');
        // Leave the field to exercise onchange, then submit to exercise the save guard.
        await master.locator('[name="city"]').click();
        await submit().click();
        await master.waitForTimeout(300);
      });
      h.assert(dialogs.length >= 1 && dialogs.every(d => d.text === 'The entered Postal Code is not valid'),
        'The changed invalid postal code was not announced');
      h.assert(posts === 0, 'An invalid changed postal code sent an Update Record POST');
      h.assert(await master.locator('#editDemographic').isVisible(), 'The rejected update left the Edit form');
      h.assert(storedPostal() === 'UNKNOWN' && archives() === before, 'The rejected update changed or archived the patient');
    } finally {
      master.removeListener('request', countPost);
    }
  });

  await s.step('a valid postal correction saves and survives reopening the edit form', async () => {
    await master.locator('[name="postal"]').fill('K1A 0B1');
    await save();
    h.assert(storedPostal() === 'K1A 0B1', 'The valid postal correction was not stored');
    await edit();
    h.assert(await master.locator('[name="postal"]').inputValue() === 'K1A 0B1', 'Reopening lost the valid postal correction');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-postal-validation', workflow, { openPatient: true });
