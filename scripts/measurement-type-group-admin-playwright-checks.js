#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
//
// Browser check for measurement type, instruction, group and style-sheet admin.
//
// User path: Schedule ▸ Administration ▸ System Management ▸ Customize Measurements
// (the page loads in the admin iframe) ▸ each popup it opens: Add Measurement Type,
// View All Measurement Types, Add Measuring Instruction, Add Measurement Group,
// Edit Measurement Group (Modify Measurement Types / Modify Measurement Style /
// Delete), View All Style Sheet and Add Measurement Style Sheet; then Chart ▸
// Measurements ▸ <owned group> for the owned patient.
//
// Asserts every write in MariaDB: measurementType rows (type + extra instruction),
// measurementGroupStyle / measurementGroup rows (create, add and remove a member,
// delete), measurementTypeDeleted audit rows on type deletion, and that the eChart
// measurement popup offers the new type with both instructions. Duplicate type
// names are refused without a second row.
//
// Fixtures: per-run FAKEPW<hex> type codes, display names, group and style-sheet
// names (the admin validator rejects '-', so the marker's hex is reused without
// it); one second owned type is seeded by SQL. Cleanup deletes only rows carrying
// those names and asserts they are gone. Implements coverage plan §3.7 admin-misc
// (Customize Measurements).
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const ROUTE = '/encounter/oscarMeasurements/';

// Arms a wait for the popup's next document whose path ends with `route`, runs
// the click, then waits for that document to load. Intermediate auto-submitting
// pages (ProcessAdd/EditMeasurementGroupAction) are skipped by the predicate.
async function landOn(page, route, act) {
  const landed = page.waitForEvent('framenavigated', {
    timeout: 20000,
    predicate: frame => frame === page.mainFrame() && new URL(frame.url()).pathname.endsWith(ROUTE + route),
  });
  landed.catch(() => {});
  await act();
  await landed;
  await page.waitForLoadState('load');
  await h.assertNotErrorPage(page, route);
}

async function workflow(s) {
  const {sql, marker} = s;
  const q = h.sqlString;
  const hex = marker.slice('FAKE-PW'.length).toUpperCase();
  const type = `PW${hex.slice(0, 12)}`;
  const display = `FAKEPW${hex.slice(0, 12)}`;
  const desc = `FAKEPW ${hex} synthetic type`;
  const instruction = `FAKEPW ${hex} first instruction`;
  const instruction2 = `FAKEPW ${hex} second instruction`;
  const peerType = `PW${hex.slice(4, 16)}`;
  const peerDisplay = `FAKEPW${hex.slice(4, 16)}`;
  const group = `FAKEPW ${hex} group`;
  const cssName = `FAKEPW${hex}.css`;
  const ownedTypes = `type IN (${q(type)},${q(peerType)}) AND typeDisplayName IN (${q(display)},${q(peerDisplay)})`;
  const typeRows = () => sql.rows(`SELECT type,typeDisplayName,typeDescription,measuringInstruction,validation
    FROM measurementType WHERE type=${q(type)} AND typeDisplayName=${q(display)} ORDER BY id`);
  const groupRows = () => sql.rows(`SELECT typeDisplayName FROM measurementGroup WHERE name=${q(group)} ORDER BY typeDisplayName`)
    .map(([name]) => name);
  const groupStyles = () => sql.value(`SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=${q(group)}`);
  const cssDir = fs.mkdtempSync(path.join(os.tmpdir(), 'measurement-css-'));

  s.cleanup(() => {
    fs.rmSync(cssDir, {recursive: true, force: true});
    sql.execute(`DELETE FROM measurementGroup WHERE name=${q(group)} OR typeDisplayName IN (${q(display)},${q(peerDisplay)});
      DELETE FROM measurementGroupStyle WHERE groupName=${q(group)};
      DELETE FROM measurementType WHERE ${ownedTypes};
      DELETE FROM measurementTypeDeleted WHERE type IN (${q(type)},${q(peerType)});
      DELETE FROM measurementCSSLocation WHERE location=${q(cssName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurementGroup WHERE name=${q(group)}
        OR typeDisplayName IN (${q(display)},${q(peerDisplay)}))
      + (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=${q(group)})
      + (SELECT COUNT(*) FROM measurementType WHERE ${ownedTypes})
      + (SELECT COUNT(*) FROM measurementTypeDeleted WHERE type IN (${q(type)},${q(peerType)}))
      + (SELECT COUNT(*) FROM measurementCSSLocation WHERE location=${q(cssName)})`) === '0',
    'Owned measurement admin rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM measurementType WHERE type IN (${q(type)},${q(peerType)})
    OR typeDisplayName IN (${q(display)},${q(peerDisplay)})`) === '0', 'Per-run measurement type names already exist');
  sql.execute(`INSERT INTO measurementType(type,typeDisplayName,typeDescription,measuringInstruction,validation,createDate)
    VALUES (${q(peerType)},${q(peerDisplay)},${q(`FAKEPW ${hex} peer type`)},${q(`FAKEPW ${hex} peer instruction`)},'5',NOW())`);

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'measurement-administration', timeout: 20000});
  const customize = admin.getByRole('link', {name: 'Customize Measurements', exact: true, includeHidden: true});
  await revealAuditLink(admin, customize, 20000);
  await customize.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const menu = await (await iframe.elementHandle()).contentFrame();
  h.assert(menu, 'Customize Measurements did not load in the administration frame');
  await menu.getByRole('link', {name: 'Add Measurement Type', exact: true}).waitFor();

  // Every Customize Measurements link targets the same window name, so a popup
  // left open would be reused by the next link instead of opening a new page.
  async function open(name, label) {
    const popup = await s.popup(admin, menu.getByRole('link', {name, exact: true}), label);
    await popup.waitForLoadState('load');
    await h.assertNotErrorPage(popup, label);
    return popup;
  }

  await s.step('Add Measurement Type saves the owned type with its validation rule', async () => {
    const popup = await open('Add Measurement Type', 'add-measurement-type');
    await popup.locator('#type').fill(type.toLowerCase());
    await popup.locator('#typeDesc').fill(desc);
    await popup.locator('#typeDisplayName').fill(display);
    await popup.locator('#measuringInstrc').fill(instruction);
    await popup.locator('#validation').selectOption({label: 'Numeric Value: 0 to 300'});
    await landOn(popup, 'AddMeasurementType', () => popup.locator('input[type="submit"][name="submit"]').click());
    await popup.getByText('Measurement type has been added successfully!').waitFor();
    h.assert(JSON.stringify(typeRows()) === JSON.stringify([[type, display, desc, instruction, '5']]),
      'The saved measurementType row does not match the submitted (upper-cased) type');
    await popup.close();
  });

  await s.step('a second Add with the same type code is refused and writes nothing', async () => {
    const popup = await open('Add Measurement Type', 'add-duplicate-measurement-type');
    await popup.locator('#type').fill(type);
    await popup.locator('#typeDesc').fill(desc);
    await popup.locator('#typeDisplayName').fill(display);
    await popup.locator('#measuringInstrc').fill(instruction2);
    await landOn(popup, 'AddMeasurementType', () => popup.locator('input[type="submit"][name="submit"]').click());
    await popup.locator('.action-errors').getByText('The entered type already exists').waitFor();
    h.assert(typeRows().length === 1, 'A duplicate measurement type code was stored');
    await popup.close();
  });

  await s.step('View All Measurement Types lists the owned type exactly as stored', async () => {
    const popup = await open('View All Measurement Types', 'view-measurement-types');
    const row = popup.locator('tr.data').filter({has: popup.getByRole('link', {name: type, exact: true})});
    h.assert(await row.count() === 1, 'The owned type is not listed exactly once');
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[1] === display && cells[2] === desc && cells[3] === instruction,
      'The listed type does not show the stored display name, description and instruction');
    await popup.close();
  });

  await s.step('Add Measuring Instruction stores a second instruction for the owned type', async () => {
    const popup = await open('Add Measuring Instruction', 'add-measuring-instruction');
    await popup.locator('#typeDisplayName').selectOption(display);
    await popup.locator('#measuringInstrc').fill(instruction2);
    await popup.locator('#validation').selectOption({label: 'Numeric Value: 0 to 300'});
    await landOn(popup, 'AddMeasuringInstruction', () => popup.getByRole('button', {name: 'Add', exact: true}).click());
    await popup.getByText('Measuring Instruction has been added successfully!').waitFor();
    h.assert(JSON.stringify(typeRows()) === JSON.stringify([
      [type, display, desc, instruction, '5'], [type, display, desc, instruction2, '5']]),
    'The second instruction was not stored against the owned type');
    await popup.close();
  });

  await s.step('Add Measurement Group defines the group and adds the owned type to it', async () => {
    const popup = await open('Add Measurement Group', 'add-measurement-group');
    await popup.locator('input[name="groupName"]').fill(group);
    await landOn(popup, 'SetupAddMeasurementGroup', () => popup.locator('input[type="submit"][name="submit"]').click());
    h.assert(groupStyles() === '1', 'The new group has no measurementGroupStyle row');
    await popup.locator('select[name="selectedAddTypes"]').selectOption(display);
    await landOn(popup, 'SetupEditMeasurementGroup', () => popup.getByRole('button', {name: 'Add', exact: true}).click());
    h.assert(JSON.stringify(groupRows()) === JSON.stringify([display]), 'The owned type was not added to the group');
    h.assert(await popup.locator('select[name="selectedDeleteTypes"] option').allInnerTexts()
      .then(texts => texts.map(text => text.trim()).join('|')) === display,
    'The group editor does not list exactly the owned member');
    await popup.close();
  });

  await s.step('Edit Measurement Group adds and then removes a second member', async () => {
    const popup = await open('Edit Measurement Group', 'edit-measurement-group');
    await popup.locator('#selectedGroupName').selectOption(group);
    await landOn(popup, 'SetupEditMeasurementGroup',
      () => popup.getByRole('button', {name: 'Modify Measurement Types', exact: true}).click());
    await popup.locator('select[name="selectedAddTypes"]').selectOption(peerDisplay);
    await landOn(popup, 'SetupEditMeasurementGroup', () => popup.getByRole('button', {name: 'Add', exact: true}).click());
    h.assert(JSON.stringify(groupRows()) === JSON.stringify([display, peerDisplay].sort()),
      'Modify Measurement Types did not add the second member');
    await popup.locator('select[name="selectedDeleteTypes"]').selectOption(peerDisplay);
    await landOn(popup, 'SetupEditMeasurementGroup', () => popup.getByRole('button', {name: 'Delete', exact: true}).click());
    h.assert(JSON.stringify(groupRows()) === JSON.stringify([display]),
      'Removing the second member did not leave exactly the owned type');
    await popup.close();
  });

  await s.step('the chart measurement menu offers the group and its popup the new type', async () => {
    const chart = await s.chart();
    const link = chart.locator('#leftNavBar .menu a, #rightNavBar .menu a').filter({hasText: group});
    h.assert(await link.count() === 1, 'The chart measurement menu does not list the owned group');
    await revealAuditLink(chart, link, 20000);
    const entry = await s.popup(chart, link, 'measurement-entry');
    await entry.waitForLoadState('load');
    await h.assertNotErrorPage(entry, 'measurement-entry');
    const row = entry.locator(`[id="row-${type}"]`);
    await row.waitFor();
    h.assert((await row.locator('span').first().innerText()).trim() === display, 'The entry row is not labelled with the display name');
    const offered = (await row.locator('label').allInnerTexts()).map(text => text.trim()).sort();
    h.assert(JSON.stringify(offered) === JSON.stringify([instruction, instruction2].sort()),
      'The entry row does not offer both measuring instructions');
    h.assert(await row.locator('input[name^="inputValue-"]').count() === 1, 'The entry row has no value input');
    await entry.close();
  });

  await s.step('Modify Measurement Style opens the owned group with no style sheet', async () => {
    const popup = await open('Edit Measurement Group', 'measurement-group-style');
    await popup.locator('#selectedGroupName').selectOption(group);
    await landOn(popup, 'SelectMeasurementGroup',
      () => popup.getByRole('button', {name: 'Modify Measurement Style', exact: true}).click());
    h.assert(await popup.locator('input[name="groupName"]').inputValue() === group, 'The style editor opened another group');
    h.assert(groupStyles() === '1', 'Opening the style editor changed the group style rows');
    await popup.close();
  });

  await s.step('Delete in Edit Measurement Group removes the group after confirmation', async () => {
    const popup = await open('Edit Measurement Group', 'delete-measurement-group');
    await popup.locator('#selectedGroupName').selectOption(group);
    const dialogs = await h.withExpectedDialogs(popup, () => landOn(popup, 'SelectMeasurementGroup',
      () => popup.getByRole('button', {name: 'Delete', exact: true}).click()));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm'
      && dialogs[0].text === `Are you sure you want to delete ${group} group?`, 'Group delete did not ask exactly once');
    h.assert(groupStyles() === '0' && groupRows().length === 0, 'The deleted group still has rows');
    h.assert(await popup.locator('#selectedGroupName option').filter({hasText: group}).count() === 0,
      'The group list still offers the deleted group');
    await popup.close();
  });

  await s.step('Delete in View All Measurement Types removes both rows and records them as deleted', async () => {
    const popup = await open('View All Measurement Types', 'delete-measurement-types');
    const rows = popup.locator('tr.data').filter({has: popup.getByRole('link', {name: type, exact: true})});
    h.assert(await rows.count() === 2, 'View All Measurement Types does not list both owned instructions');
    for (const box of await rows.locator('input[name="deleteCheckbox"]').all()) await box.check();
    await landOn(popup, 'DeleteMeasurementTypes', () => popup.getByRole('button', {name: 'Delete', exact: true}).click());
    h.assert(typeRows().length === 0, 'The owned measurement type rows were not deleted');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementTypeDeleted WHERE type=${q(type)} AND typeDisplayName=${q(display)}
      AND measuringInstruction IN (${q(instruction)},${q(instruction2)})`) === '2', 'Type deletion left no deleted-type audit rows');
    h.assert(await popup.getByRole('link', {name: type, exact: true}).count() === 0, 'The deleted type is still listed');
    await popup.close();
  });

  await s.step('View All Style Sheet lists, and Add Measurement Style Sheet uploads, an owned style sheet', async () => {
    const list = await open('View All Style Sheet', 'view-style-sheets');
    h.assert(await list.getByText(cssName, {exact: true}).count() === 0, 'The owned style sheet exists before upload');
    await list.close();
    const file = path.join(cssDir, cssName);
    fs.writeFileSync(file, `/* ${marker} */\n.FAKEPW${hex} { color: #000; }\n`);
    const popup = await open('Add Measurement Style Sheet', 'add-style-sheet');
    await popup.locator('#file').setInputFiles(file);
    await landOn(popup, 'AddMeasurementStyleSheet', () => popup.getByRole('button', {name: 'Continue', exact: true}).click());
    h.assert(await popup.locator('.action-errors').count() === 0,
      `Uploading a valid style sheet was refused: ${(await popup.locator('.action-errors').innerText()).trim()}`);
    await popup.getByText(`Style Sheet ${cssName} added successfully!`).waitFor();
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementCSSLocation WHERE location=${q(cssName)}`) === '1',
      'The uploaded style sheet has no measurementCSSLocation row');
    await popup.close();
  });
}

if (require.main === module) runWorkflow('measurement-type-group-admin', workflow, {openPatient: true});
module.exports = {workflow};
