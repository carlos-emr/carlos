#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rx ▸ Manage pharmacies: order a patient's preferred pharmacies and read one in full.
 *
 * User path: Schedule ▸ Master Record ▸ Rx (the patient's prescription window) ▸ Manage Pharmacies
 * (rx/managePharmacy) ▸ the preferred list's Move Down / Move Up / Remove from List and "View More"
 * (the read-only details modal, rx/ViewViewPharmacy) ▸ click two pharmacies in the clinic list to
 * make them the patient's preferred pharmacies. pharmacy-editor-workflow covers creating, editing,
 * linking one pharmacy and deleting it; this check covers what it leaves out: the order of several
 * preferred pharmacies (the first is the one a prescription is faxed to) and the details view.
 * Asserts demographicPharmacy.preferredOrder after each click (the two-request swap leaves 1 and 2,
 * never two equal orders), the preferred list order on the reloaded page, that Move Up on the top
 * pharmacy only alerts and changes nothing, that View More shows the stored name, address, city,
 * postal code, phone, fax, email and notes read-only and changes nothing, and that Remove from List
 * unlinks only the chosen pharmacy. The last step fails today: choosing two pharmacies for an empty
 * list stores orders 2 and 3 in the wrong order (the empty-list placeholder div is counted).
 * Fixtures: two clinic pharmacies named with the run marker and the owned patient's links to them
 * (cleanup deletes both and asserts them gone). Implements gap-encounter "choose and order the
 * patient's pharmacies".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient } = s;
  const ids = [];
  const names = { A: `${marker} Pharmacy A`, B: `${marker} Pharmacy B` };
  const notes = `Notes ${marker} keep & "quotes"`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographicPharmacy WHERE demographic_no=${patient}`);
    for (const id of ids) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE pharmacyID=${id}`) === '0',
        'An owned pharmacy is linked to another patient; retained for inspection');
      sql.execute(`DELETE FROM pharmacyInfo WHERE recordID=${id} AND name LIKE ${q(`${marker}%`)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID=${id}`) === '0', 'An owned pharmacy was not removed');
    }
  });
  const make = (key, address, city) => {
    const id = sql.value(`INSERT INTO pharmacyInfo(name,address,city,province,postalCode,phone1,fax,email,notes,status)
      VALUES(${q(names[key])},${q(address)},${q(city)},'ON','M1A 1A1','416-555-0301','416-555-0302','pharmacy@example.invalid',${q(notes)},'1');
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A pharmacy fixture was not created');
    ids.push(id);
    return id;
  };
  const a = make('A', '1 Order Street', 'Fixture City');
  const b = make('B', '2 Order Street', 'Fixture Town');
  const order = id => sql.value(`SELECT COALESCE((SELECT preferredOrder FROM demographicPharmacy WHERE demographic_no=${patient}
    AND pharmacyID=${id} AND status='1'),'none')`);

  const rx = await s.popup(s.master, s.master.locator('a[onclick*="rx/choosePatient"]').first(), 'pharmacy-order-rx');
  await rx.locator('a[href$="/rx/managePharmacy"]').click();
  await rx.waitForURL(/\/rx\/managePharmacy(?:\?|$)/);
  await rx.waitForLoadState('networkidle');
  const row = id => rx.locator(`tr.pharmacyItem[pharmId="${id}"]`);
  const entry = id => rx.locator(`#preferredList div[pharmId="${id}"]`);
  const preferred = async () => rx.locator('#preferredList div[pharmId]').evaluateAll(els => els.map(el => el.getAttribute('pharmId')));
  const reloaded = async action => {
    await Promise.all([rx.waitForNavigation({ waitUntil: 'load', timeout: 30000 }), action()]);
    await rx.waitForLoadState('networkidle');
    await rx.locator('#preferredList').waitFor();
  };

  // The patient's two links are seeded first (orders 1 and 2) so the steps below prove ordering on its own;
  // choosing pharmacies from the clinic list is the last step because it is where the order goes wrong.
  sql.execute(`INSERT INTO demographicPharmacy(pharmacyID,demographic_no,status,addDate,preferredOrder,consentToContact)
    VALUES(${a},${patient},'1',NOW(),1,0),(${b},${patient},'1',NOW(),2,0)`);
  await rx.reload({ waitUntil: 'networkidle' });
  await s.step('the preferred list shows the two linked pharmacies in their stored order', async () => {
    await entry(a).waitFor();
    h.assert(JSON.stringify(await preferred()) === JSON.stringify([a, b]), 'The preferred list is not in the stored order');
  });

  await s.step('Move Down on the top pharmacy swaps the two orders and the reloaded list shows the new order', async () => {
    await reloaded(() => entry(a).locator('.prefDown').click());
    h.assert(order(a) === '2' && order(b) === '1', `Move Down left the orders as ${order(a)} / ${order(b)}, not 2 / 1`);
    h.assert(JSON.stringify(await preferred()) === JSON.stringify([b, a]), 'The reloaded preferred list did not swap');
  });

  await s.step('Move Up swaps back; Move Up on the top pharmacy only alerts', async () => {
    await reloaded(() => entry(a).locator('.prefUp').click());
    h.assert(order(a) === '1' && order(b) === '2', 'Move Up did not restore the original order');
    const dialogs = await h.withExpectedDialogs(rx, () => entry(a).locator('.prefUp').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'Move Up on the most preferred pharmacy did not alert');
    h.assert(order(a) === '1' && order(b) === '2', 'Move Up on the top pharmacy changed the order');
  });

  await s.step('View More shows the pharmacy read-only and changes nothing', async () => {
    const before = sql.value(`SELECT CONCAT_WS('|',name,address,city,province,postalCode,phone1,fax,email,notes,status) FROM pharmacyInfo WHERE recordID=${a}`);
    await entry(a).getByText('View More').click();
    const frame = rx.frameLocator('#pharmacyModalIframe');
    await frame.locator('#pharmacyAddress').filter({ hasText: '1 Order Street' }).waitFor({ timeout: 20000 });
    const read = async field => (await frame.locator(`#${field}`).innerText()).trim();
    h.assert(await read('pharmacyName') === names.A, 'The details view shows another pharmacy name');
    h.assert(await read('pharmacyCity') === 'Fixture City' && await read('pharmacyPostalCode') === 'M1A 1A1', 'The details view lost the city or postal code');
    h.assert(await read('pharmacyPhone1') === '416-555-0301' && await read('pharmacyFax') === '416-555-0302', 'The details view lost the phone or fax');
    h.assert(await read('pharmacyEmail') === 'pharmacy@example.invalid', 'The details view lost the email');
    h.assert(await read('pharmacyNotes') === notes, 'The details view lost the notes');
    h.assert(await frame.locator('input[type="text"], textarea').count() === 0, 'The details view offers editable fields');
    h.assert(sql.value(`SELECT CONCAT_WS('|',name,address,city,province,postalCode,phone1,fax,email,notes,status) FROM pharmacyInfo WHERE recordID=${a}`) === before,
      'Viewing the pharmacy changed it');
    await rx.locator('#pharmacyModal button[data-bs-dismiss="modal"]').first().click();
    await rx.locator('#pharmacyModal').waitFor({ state: 'hidden' });
  });

  await s.step('Remove from List unlinks only the chosen pharmacy', async () => {
    await reloaded(() => entry(b).locator('.prefUnlink').click());
    h.assert(order(b) === 'none', 'Remove from List left the pharmacy linked');
    h.assert(order(a) === '1', 'Removing the second pharmacy changed the first one\'s order');
    h.assert(JSON.stringify(await preferred()) === JSON.stringify([a]), 'The preferred list still shows the removed pharmacy');
  });

  // The page numbers a new link as "(divs in the preferred list) + 1", and the empty list holds a
  // placeholder div, so the first pharmacy is stored as order 2 and the next one is inserted ahead of it.
  await s.step('choosing two pharmacies from the clinic list for an empty preferred list stores orders 1 and 2 in the order chosen', async () => {
    await reloaded(() => entry(a).locator('.prefUnlink').click());
    h.assert(await rx.locator('#preferredList div[pharmId]').count() === 0, 'The preferred list is not empty');
    await row(a).locator('.pharmacyName').click();
    await expectValue(sql, `SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${patient} AND pharmacyID=${a} AND status='1'`, '1',
      'The first pharmacy was not linked');
    await entry(a).waitFor();
    await rx.waitForLoadState('networkidle');
    h.assert(order(a) === '1', 'The first chosen pharmacy must have preferred order 1');
    await row(b).locator('.pharmacyName').click();
    await expectValue(sql, `SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${patient} AND pharmacyID=${b} AND status='1'`, '1',
      'The second pharmacy was not linked');
    await entry(b).waitFor();
    h.assert(order(a) === '1' && order(b) === '2',
      `The preferred orders are ${order(a)} then ${order(b)}, not 1 then 2, in the order the pharmacies were chosen`);
    h.assert(JSON.stringify(await preferred()) === JSON.stringify([a, b]), 'The preferred list is not in the order the pharmacies were chosen');
  });

  await s.step('choosing an already preferred pharmacy changes neither stored order', async () => {
    const dialogs = await h.withExpectedDialogs(rx, () => row(a).locator('.pharmacyName').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'A repeated selection did not alert');
    h.assert(order(a) === '1' && order(b) === '2', 'A repeated selection changed the preferred order');
  });
}

if (require.main === module) runWorkflow('gap-encounter-rx-pharmacy-order', workflow, { openPatient: true });
module.exports = { workflow };
