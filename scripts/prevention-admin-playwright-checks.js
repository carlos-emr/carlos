#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §3.4 prevention-admin: the prevention administration surfaces.
// User paths: E-Chart ▸ Preventions ▸ Enable Print ▸ Print (prevention/printPrevention PDF);
// Schedule ▸ Administration ▸ Add Prevention Lot number ▸ Add ▸ View Lots for ▸ lot ▸ Delete,
// then Search lot number by prevention and Add again (restore, duplicate);
// Administration ▸ Prevention List Manager ▸ row ▸ Save ▸ Yes, please save (hide, then unhide).
// Asserts the PDF bytes and text (pdftotext) carry the owned prevention, PreventionsLotNrs rows
// (insert, soft delete, restore without a second row, duplicate refused), and the clinic-wide
// hide_prevention_item property plus its effect on the owned patient's Preventions page.
// Fixtures: the owned FAKE- patient, one seeded Inf prevention (+comments ext) for it, and one lot
// number under JE whose text carries the run marker. Cleanup deletes only those rows and restores
// the hide_prevention_item snapshot. That property is global: run with EXCLUSIVE=1.
// lot-number-search covers the search form against a seeded lot; this check covers add/delete.
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickDownloadsOrOpens, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const LOT_TYPE = 'JE';
const HIDDEN_ITEM = 'AAA';
const HIDE_PROPERTY = 'hide_prevention_item';

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const lot = `PW${marker.slice(-12)}`;
  const comment = `${marker} print comment`;
  const lotWhere = `preventionType=${sqlString(LOT_TYPE)} AND lotNr=${sqlString(lot)} AND providerNo=${sqlString(provider)}`;
  const snapshot = sql.rows(`SELECT id, value FROM property WHERE name=${sqlString(HIDE_PROPERTY)} ORDER BY id`);
  assert(snapshot.length <= 1, 'hide_prevention_item is duplicated; refusing to change it');
  s.cleanup(() => {
    if (snapshot.length) {
      sql.execute(`UPDATE property SET value=${snapshot[0][1] === 'NULL' ? 'NULL' : sqlString(snapshot[0][1])}
        WHERE id=${snapshot[0][0]}; DELETE FROM property WHERE name=${sqlString(HIDE_PROPERTY)} AND id<>${snapshot[0][0]}`);
    } else sql.execute(`DELETE FROM property WHERE name=${sqlString(HIDE_PROPERTY)}`);
    assert(JSON.stringify(sql.rows(`SELECT id, value FROM property WHERE name=${sqlString(HIDE_PROPERTY)} ORDER BY id`))
      === JSON.stringify(snapshot), 'hide_prevention_item was not restored');
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM PreventionsLotNrs WHERE ${lotWhere}`);
    assert(sql.value(`SELECT COUNT(*) FROM PreventionsLotNrs WHERE ${lotWhere}`) === '0', 'Owned lot number was not removed');
  });
  s.cleanup(() => {
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0', 'Owned prevention was not removed');
  });
  const prevention = sql.value(`INSERT INTO preventions(demographic_no,creation_date,prevention_date,provider_no,prevention_type,
      deleted,refused,never,creator,lastUpdateDate)
    VALUES(${patient},NOW(),'2026-02-03',${sqlString(provider)},'Inf','0','0','0',${sqlString(provider)},NOW()); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(prevention), 'Prevention fixture was not created');
  sql.execute(`INSERT INTO preventionsExt(prevention_id,keyval,val) VALUES(${prevention},'comments',${sqlString(comment)})`);

  const chart = await s.chart();
  const openIndex = () => s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  const hiddenLink = page => page.locator(`div.leftBox a[onclick*="prevention=${HIDDEN_ITEM}&"]`);

  await s.step('Enable Print then Print returns a PDF of the patient\'s preventions', async () => {
    const index = await openIndex();
    assert(await hiddenLink(index).count() === 1, `${HIDDEN_ITEM} is not offered before the list manager hides it`);
    const button = index.locator('input[name="printButton"]');
    await button.click();
    assert(await button.inputValue() === 'Print', 'Enable Print did not switch the button to Print');
    assert(await index.locator('input[name="printHP"]:checked').count() === 1, 'The owned prevention has no checked print box');
    const outcome = await clickDownloadsOrOpens(index, button, { context: s.context, recorder: s.recorder, label: 'prevention-print' });
    assert(outcome.kind === 'download', 'Print did not deliver a file');
    const bytes = fs.readFileSync(await outcome.download.path());
    assert(bytes.subarray(0, 4).toString('latin1') === '%PDF', 'Print did not return PDF bytes');
    const text = execFileSync('pdftotext', ['-', '-'], { input: bytes, encoding: 'utf8' });
    // prevention_show_comments=false on this install, so the comment ext is not expected in the PDF.
    assert(text.includes(marker) && text.includes('Inf') && text.includes('2026-02-03'),
      'Printed PDF does not carry the owned patient, prevention type and date');
    await index.close();
  });

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'prevention-administration', timeout: 20000 });
  async function openFrame(name) {
    const link = admin.getByRole('link', { name, exact: true, includeHidden: true });
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor();
    const frame = await (await iframe.elementHandle()).contentFrame();
    assert(frame, `${name} did not load in the administration frame`);
    await frame.waitForLoadState('domcontentloaded');
    return frame;
  }
  async function navigate(frame, locator) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 20000 });
    navigated.catch(() => {});
    await locator.click();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: 20000 });
  }
  async function addLot(expectedMessage) {
    const frame = await openFrame('Add Prevention Lot number');
    await frame.locator('#prevention').selectOption(LOT_TYPE);
    await frame.locator('input[name="lotnr"]').fill(lot);
    await navigate(frame, frame.locator('input[name="submitbtn"]'));
    assert(new URL(frame.url()).pathname.endsWith('/admin/LotNrAddRecord'), 'Add did not post to LotNrAddRecord');
    assert((await frame.locator('body').innerText()).includes(expectedMessage), `Add did not report "${expectedMessage}"`);
    return frame;
  }
  const lotRows = () => sql.rows(`SELECT id, deleted FROM PreventionsLotNrs WHERE ${lotWhere}`);

  let lotId;
  await s.step('Add Prevention Lot number refuses an empty lot, then stores the typed lot', async () => {
    const frame = await openFrame('Add Prevention Lot number');
    const dialogs = await withExpectedDialogs(admin, () => frame.locator('input[name="submitbtn"]').click());
    assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'An empty lot was not refused with one alert');
    assert(new URL(frame.url()).pathname.endsWith('/admin/ViewLotNrAddRecordHtm'), 'An empty lot was submitted');
    await addLot('Lot number record added successfully.');
    const rows = lotRows();
    assert(rows.length === 1 && rows[0][1] === '0', 'Add did not store exactly one active lot');
    lotId = rows[0][0];
  });

  let frame;
  await s.step('View Lots for the type lists the lot and its link opens Delete prefilled', async () => {
    frame = admin.frames().find(f => new URL(f.url()).pathname.endsWith('/admin/LotNrAddRecord'));
    assert(frame, 'The add result page is no longer framed');
    await navigate(frame, frame.getByRole('link', { name: `View Lots for ${LOT_TYPE}` }));
    assert(new URL(frame.url()).pathname.endsWith('/admin/LotNrSearchResults'), 'View Lots did not open the search results');
    await navigate(frame, frame.getByRole('link', { name: lot, exact: true }));
    assert(await frame.locator('input[name="prevention"]').inputValue() === LOT_TYPE
      && await frame.locator('input[name="lotnr"]').inputValue() === lot, 'Delete form was not prefilled with the chosen lot');
  });

  await s.step('Delete soft-deletes the lot and search no longer lists it', async () => {
    await navigate(frame, frame.locator('input[name="submitbtn"]'));
    assert((await frame.locator('body').innerText()).includes('Lot number record deleted successfully.'), 'Delete did not report success');
    await expectValue(sql, `SELECT deleted FROM PreventionsLotNrs WHERE id=${lotId}`, '1', 'Delete did not mark the lot deleted');
    const search = await openFrame('Search lot number by prevention');
    await search.locator('input[name="keyword"]').fill(LOT_TYPE);
    await navigate(search, search.locator('input[type="submit"]'));
    assert(await search.getByRole('link', { name: lot, exact: true }).count() === 0, 'Search still lists the deleted lot');
  });

  await s.step('adding the deleted lot again restores the same row and a third add is refused as a duplicate', async () => {
    await addLot('Lot number record restored successfully.');
    const restored = lotRows();
    assert(restored.length === 1 && restored[0][0] === lotId && restored[0][1] === '0', 'Restore did not reactivate the original row');
    await addLot('Duplicate: this lot number already exists for the selected prevention type.');
    assert(JSON.stringify(lotRows()) === JSON.stringify(restored), 'A duplicate add changed the lot rows');
  });

  const hideValue = () => sql.value(`SELECT IFNULL(value,'') FROM property WHERE name=${sqlString(HIDE_PROPERTY)}`) || '';
  const before = hideValue().split(',').filter(Boolean);
  assert(!before.includes(HIDDEN_ITEM), `${HIDDEN_ITEM} is already hidden on this install`);
  async function toggleListItem() {
    const manager = await openFrame('Prevention List Manager');
    const cell = manager.locator(`tr#${HIDDEN_ITEM} td`).first();
    const wasActive = await cell.evaluate(td => td.classList.contains('item-active'));
    await manager.locator(`tr#${HIDDEN_ITEM}`).click();
    assert(await cell.evaluate(td => td.classList.contains('item-active')) === !wasActive, 'Clicking the row did not toggle it');
    await manager.locator('#btnConfirm').click();
    await manager.locator('#modalConfirm').waitFor({ state: 'visible' });
    await navigate(manager, manager.getByRole('button', { name: 'Yes, please save' }));
    assert(await manager.locator(`tr#${HIDDEN_ITEM} td`).first().evaluate(td => td.classList.contains('item-active')) === !wasActive,
      'The saved list manager does not show the new state');
  }

  await s.step('Prevention List Manager hides an item clinic-wide and the patient page drops it', async () => {
    await toggleListItem();
    await expectValue(sql, `SELECT FIND_IN_SET(${sqlString(HIDDEN_ITEM)}, value) > 0 FROM property WHERE name=${sqlString(HIDE_PROPERTY)}`,
      '1', 'Saving did not store the hidden item');
    assert(before.every(item => hideValue().split(',').includes(item)), 'Saving dropped previously hidden items');
    const index = await openIndex();
    assert(await hiddenLink(index).count() === 0, 'The hidden item is still offered on the Preventions page');
    await index.close();
  });

  await s.step('making the item available again restores the property and the patient page', async () => {
    await toggleListItem();
    await expectValue(sql, `SELECT IFNULL(value,'') FROM property WHERE name=${sqlString(HIDE_PROPERTY)}`,
      before.join(','), 'Unhiding did not restore the previous hidden list');
    const index = await openIndex();
    assert(await hiddenLink(index).count() === 1, 'The unhidden item is not offered on the Preventions page');
    await index.close();
  });
}

if (require.main === module) runWorkflow('prevention-admin', workflow, { openPatient: true });
module.exports = { workflow };
