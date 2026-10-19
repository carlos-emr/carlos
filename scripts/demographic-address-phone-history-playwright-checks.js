#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Master Record address and phone history. The read view's "History" links next to the address and the home, work
 * and cell phones open a popup listing the earlier values with the date each was entered; no other check reads them.
 * User path: Master Record ▸ Edit ▸ a new address ▸ Update Record (twice); ▸ Edit ▸ a new home phone ▸ Update Record
 * (twice); the same for the work phone (the second with an extension) and the cell phone; point at Address History,
 * Home phone History, Work phone History and cell phone History.
 * Asserts: the address history lists each address the patient had, dated; the home phone history lists each home
 * phone; the work phone history lists each work phone, the one with an extension followed by "x" and the extension;
 * the cell phone history lists each cell phone once; LAST, an address or phone that did not change is listed once,
 * not again for every save that changed something else, and a phone without an extension shows no "x".
 * Fixtures: the runWorkflow FAKE- patient (FAKE addresses and 555 phones); the update's ext-archive and custom rows are
 * removed before the harness removes the patient's ext and archive rows.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, patient, master } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographicExtArchive WHERE archiveId IN (SELECT id FROM demographicArchive WHERE demographic_no=${patient})
      OR demographic_no=${patient};
      DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient}
        OR archiveId IN (SELECT id FROM demographicArchive WHERE demographic_no=${patient}))`) === '0',
    'The owned patient\'s custom-field and extension archive rows were not removed');
  });
  sql.execute(`UPDATE demographic SET address='10 FAKE Start Rd', city='Faketown', province='ON', postal='K1A0A1',
    phone='613-555-0100' WHERE demographic_no=${patient}`);
  await master.reload({ waitUntil: 'load' });
  const today = sql.value('SELECT CURDATE()');

  async function update(fields) {
    await master.locator('#editBtn').click();
    await master.locator('#updateButton').waitFor({ state: 'visible', timeout: TIMEOUT });
    for (const [name, value] of Object.entries(fields)) await master.locator(`[name="${name}"]`).first().fill(value);
    await ui.clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(), { timeout: TIMEOUT, label: 'Update Record' });
  }
  /** The rows of a history popup, as [date, value] pairs. */
  async function history(title) {
    await master.locator(`span[title="${title}"]`).first().hover();
    const popup = master.locator('div.pup');
    await popup.waitFor({ state: 'visible', timeout: TIMEOUT });
    return popup.locator('tr').evaluateAll(rows => rows.map(row => [...row.querySelectorAll('td')].map(cell => cell.textContent.trim()))
      .filter(cells => cells.length === 2));
  }

  await s.step('the address history lists each address the patient had, with the date it was entered', async () => {
    await update({ address: '20 FAKE Second Ave' });
    await update({ address: '30 FAKE Third St' });
    const rows = await history('Address History');
    for (const address of ['20 FAKE Second Ave', '30 FAKE Third St']) {
      h.assert(rows.some(([date, value]) => date === today && value.startsWith(`${address}, Faketown`)),
        `The address history does not list ${address}: ${JSON.stringify(rows)}`);
    }
  });

  await s.step('the home phone history lists each home phone', async () => {
    await update({ phone: '613-555-0177' });
    await update({ phone: '613-555-0188' });
    const rows = await history('Home phone History');
    for (const phone of ['613-555-0177', '613-555-0188']) {
      h.assert(rows.some(([date, value]) => date === today && value.startsWith(phone)), `The home phone history does not list ${phone}: ${JSON.stringify(rows)}`);
    }
  });

  await s.step('the work phone history lists each work phone, with an extension after an "x"', async () => {
    await update({ phone2: '613-555-0300' });
    await update({ phone2: '613-555-0301', wPhoneExt: '123' });
    const rows = await history('Work phone History');
    for (const phone of ['613-555-0300', '613-555-0301x123']) {
      h.assert(rows.some(([date, value]) => date === today && value === phone), `The work phone history does not list ${phone}: ${JSON.stringify(rows)}`);
    }
  });

  await s.step('the cell phone history lists each cell phone once', async () => {
    await update({ demo_cell: '613-555-0400' });
    await update({ demo_cell: '613-555-0401' });
    const cells = (await history('cell phone History')).map(([, value]) => value);
    for (const phone of ['613-555-0400', '613-555-0401']) {
      h.assert(cells.filter(value => value === phone).length === 1, `The cell phone history does not list ${phone} once: ${JSON.stringify(cells)}`);
    }
  });

  await s.step('an address or phone that did not change is listed once, and a phone without an extension shows no "x"', async () => {
    const rows = await history('Address History');
    const latest = rows.filter(([, value]) => value.startsWith('30 FAKE Third St'));
    h.assert(latest.length === 1, `The unchanged address is listed ${latest.length} times: ${JSON.stringify(rows)}`);
    const phones = (await history('Home phone History')).map(([, value]) => value);
    // The popup is shared with the address history: a home phone in it shows this is the phone list, not a stale read.
    h.assert(phones.some(phone => phone.startsWith('613-555-0188')), `The home phone history was not read: ${JSON.stringify(phones)}`);
    h.assert(new Set(phones).size === phones.length, `A home phone is listed more than once: ${JSON.stringify(phones)}`);
    h.assert(!phones.some(phone => phone.endsWith('x')), `A home phone without an extension ends in "x": ${JSON.stringify(phones)}`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-address-phone-history', workflow, { openPatient: true });
