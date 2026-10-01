#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
//
// Browser check for measurement type, instruction, group and style-sheet admin.
//
// User path: Schedule ▸ Administration ▸ Customize Measurements (loads in the admin
// iframe) ▸ the popups it opens: View All Measurement Types (+Delete), Edit
// Measurement Group (Modify Measurement Types / Modify Measurement Style / Delete),
// View All Style Sheet, Add Measurement Type, Add Measuring Instruction, Add
// Measurement Group, Add Measurement Style Sheet; and Chart ▸ Measurements ▸ group.
//
// Asserts each write in MariaDB: measurementGroup membership add/remove, group and
// type deletion (with measurementTypeDeleted audit rows), types and instructions
// saved by the Add forms, duplicate type refused, and that the chart's measurement
// popup offers the owned type. Steps that need no Add form run first on SQL-seeded
// types and group; the Add forms come last because they are what currently fails.
//
// Fixtures: FAKEPW<hex> type codes, display names, group names and style-sheet name
// (the admin validator rejects '-'); cleanup deletes only those rows and asserts it.
// MEASUREMENT_CSS_UPLOAD_DIR (the server's oscarMeasurement_css_upload_path, readable and
// writable by the check) is required so the uploaded file is asserted and removed.
// Implements coverage plan §3.7 admin-misc (Customize Measurements).
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const ROUTE = '/encounter/oscarMeasurements/';

// Arms a wait for the popup's next document whose path ends with `route`, runs the
// click, then waits for that document. Auto-submitting intermediate pages
// (Process*MeasurementGroupAction) are skipped by the predicate.
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
  const code = suffix => `PW${hex.slice(0, 12)}${suffix}`;
  const name = suffix => `FAKEPW${hex.slice(0, 12)}${suffix}`; // measurementTypeDeleted holds 20 chars
  const [seeded, peer, created] = ['A', 'B', 'C'].map(suffix => ({type: code(suffix), display: name(suffix),
    desc: `FAKEPW ${hex} type ${suffix}`, instruction: `FAKEPW ${hex} instruction ${suffix}`}));
  const instruction2 = `FAKEPW ${hex} second instruction`;
  const seededGroup = `FAKEPW ${hex} seeded group`;
  const editedGroup = `FAKEPW ${hex} edited group`;
  const createdGroup = `FAKEPW ${hex} new group`;
  const cssName = `FAKEPW${hex}.css`;
  const cssUploadDir = process.env.MEASUREMENT_CSS_UPLOAD_DIR;
  const uploadedFile = path.join(cssUploadDir, cssName);
  const types = [seeded, peer, created];
  const codes = types.map(t => q(t.type)).join(',');
  const displays = types.map(t => q(t.display)).join(',');
  const groups = [seededGroup, editedGroup, createdGroup].map(q).join(',');
  const typeRows = t => sql.rows(`SELECT type,typeDisplayName,typeDescription,measuringInstruction,validation
    FROM measurementType WHERE type=${q(t.type)} AND typeDisplayName=${q(t.display)} ORDER BY id`);
  const members = group => sql.rows(`SELECT typeDisplayName FROM measurementGroup WHERE name=${q(group)}
    ORDER BY typeDisplayName`).map(([display]) => display);
  const styles = group => sql.value(`SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=${q(group)}`);
  const cssDir = fs.mkdtempSync(path.join(os.tmpdir(), 'measurement-css-'));
  const ownedCount = () => sql.value(`SELECT
      (SELECT COUNT(*) FROM measurementGroup WHERE name IN (${groups}) OR typeDisplayName IN (${displays}))
    + (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName IN (${groups}))
    + (SELECT COUNT(*) FROM measurementType WHERE type IN (${codes}) OR typeDisplayName IN (${displays}))
    + (SELECT COUNT(*) FROM measurementTypeDeleted WHERE type IN (${codes}))
    + (SELECT COUNT(*) FROM measurementCSSLocation WHERE location=${q(cssName)})`);

  s.cleanup(() => {
    fs.rmSync(cssDir, {recursive: true, force: true});
    sql.execute(`DELETE FROM measurementGroup WHERE name IN (${groups}) OR typeDisplayName IN (${displays});
      DELETE FROM measurementGroupStyle WHERE groupName IN (${groups});
      DELETE FROM measurementType WHERE type IN (${codes}) AND typeDisplayName IN (${displays});
      DELETE FROM measurementTypeDeleted WHERE type IN (${codes});
      DELETE FROM measurementCSSLocation WHERE location=${q(cssName)}`);
    h.assert(ownedCount() === '0', 'Owned measurement admin rows were not removed');
    fs.rmSync(uploadedFile, {force: true});
    h.assert(!fs.existsSync(uploadedFile), 'The uploaded style-sheet file was not removed');
  });
  h.assert(ownedCount() === '0', 'Per-run measurement admin names already exist');
  sql.execute(types.slice(0, 2).map(t => `INSERT INTO measurementType
      (type,typeDisplayName,typeDescription,measuringInstruction,validation,createDate)
      VALUES (${q(t.type)},${q(t.display)},${q(t.desc)},${q(t.instruction)},'5',NOW())`).join(';')
    + `; INSERT INTO measurementGroupStyle(groupName,cssID) VALUES (${q(seededGroup)},0),(${q(editedGroup)},0);
      INSERT INTO measurementGroup(name,typeDisplayName) VALUES (${q(seededGroup)},${q(seeded.display)})`);

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'measurement-administration', timeout: 20000});
  const customize = admin.getByRole('link', {name: 'Customize Measurements', exact: true, includeHidden: true});
  await revealAuditLink(admin, customize, 20000);
  await customize.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const menu = await (await iframe.elementHandle()).contentFrame();
  h.assert(menu, 'Customize Measurements did not load in the administration frame');
  await menu.getByRole('link', {name: 'View All Measurement Types', exact: true}).waitFor();

  // Every Customize Measurements link targets one window name, so each popup is
  // closed after use; otherwise the next link would reuse it instead of opening.
  async function open(linkName, label) {
    const since = s.recorder.badResponses.length;
    try {
      const popup = await s.popup(admin, menu.getByRole('link', {name: linkName, exact: true}), label);
      await popup.waitForLoadState('load');
      return popup;
    } catch (error) {
      const refused = s.recorder.badResponses.slice(since).find(entry => entry.resourceType === 'document');
      h.assert(!refused, `${linkName} opened an HTTP ${refused && refused.status} page for ${refused && refused.method} `
        + `${refused && new URL(refused.url).pathname}; the Customize Measurements link cannot reach its form`);
      throw error;
    }
  }
  async function selectGroup(popup, group, button) {
    await popup.locator('#selectedGroupName').selectOption(group);
    return popup.getByRole('button', {name: button, exact: true});
  }
  const listedType = (popup, t) => popup.locator('tr.data').filter({has: popup.getByRole('link', {name: t.type, exact: true})});

  await s.step('the chart measurement menu offers the group and its popup the owned type', async () => {
    const chart = await s.chart();
    const link = chart.locator('#leftNavBar .menu a, #rightNavBar .menu a').filter({hasText: seededGroup});
    h.assert(await link.count() === 1, 'The chart measurement menu does not list the owned group');
    await revealAuditLink(chart, link, 20000);
    const entry = await s.popup(chart, link, 'measurement-entry');
    await entry.waitForLoadState('load');
    await h.assertNotErrorPage(entry, 'measurement-entry');
    const row = entry.locator(`[id="row-${seeded.type}"]`);
    await row.waitFor();
    h.assert(await entry.locator('tr.data[id^="row-"]').count() === 1, 'The group popup offers types outside the group');
    h.assert((await row.locator('span').first().innerText()).trim() === seeded.display, 'The entry row is not labelled with the display name');
    h.assert((await row.locator('label').allInnerTexts()).map(t => t.trim()).join('|') === seeded.instruction,
      'The entry row does not offer the measuring instruction');
    h.assert(await row.locator('input[name^="inputValue-"]').count() === 1, 'The entry row has no value input');
    await entry.close();
  });

  await s.step('Modify Measurement Style opens the owned group without changing it', async () => {
    const popup = await open('Edit Measurement Group', 'measurement-group-style');
    await landOn(popup, 'SelectMeasurementGroup', async () => (await selectGroup(popup, seededGroup, 'Modify Measurement Style')).click());
    h.assert(await popup.locator('input[name="groupName"]').inputValue() === seededGroup, 'The style editor opened another group');
    h.assert(styles(seededGroup) === '1' && members(seededGroup).length === 1, 'Opening the style editor changed the group');
    await popup.close();
  });

  await s.step('Delete in Edit Measurement Group removes the group after one confirmation', async () => {
    const popup = await open('Edit Measurement Group', 'delete-measurement-group');
    const button = await selectGroup(popup, seededGroup, 'Delete');
    const dialogs = await h.withExpectedDialogs(popup, () => landOn(popup, 'SelectMeasurementGroup', () => button.click()));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm'
      && dialogs[0].text === `Are you sure you want to delete ${seededGroup} group?`, 'Group delete did not ask exactly once');
    h.assert(styles(seededGroup) === '0' && members(seededGroup).length === 0, 'The deleted group still has rows');
    h.assert(await popup.locator('#selectedGroupName option').filter({hasText: seededGroup}).count() === 0,
      'The group list still offers the deleted group');
    await popup.close();
  });

  await s.step('View All Style Sheet renders the style-sheet list without the owned name', async () => {
    const popup = await open('View All Style Sheet', 'view-style-sheets');
    await popup.locator('form[action$="/DeleteMeasurementStyleSheet"]').waitFor();
    h.assert(await popup.getByText(cssName, {exact: true}).count() === 0, 'The owned style sheet exists before upload');
    await popup.close();
  });

  // Every step from here needs an Add form or a Process*MeasurementGroupAction page.
  await s.step('Add Measurement Type saves the new type with its validation rule', async () => {
    const popup = await open('Add Measurement Type', 'add-measurement-type');
    await popup.locator('#type').fill(created.type.toLowerCase());
    await popup.locator('#typeDesc').fill(created.desc);
    await popup.locator('#typeDisplayName').fill(created.display);
    await popup.locator('#measuringInstrc').fill(created.instruction);
    await popup.locator('#validation').selectOption({label: 'Numeric Value: 0 to 300'});
    await landOn(popup, 'AddMeasurementType', () => popup.locator('input[type="submit"][name="submit"]').click());
    await popup.getByText('Measurement type has been added successfully').waitFor();
    h.assert(JSON.stringify(typeRows(created)) === JSON.stringify([[created.type, created.display, created.desc, created.instruction, '5']]),
      'The saved measurementType row does not match the submitted (upper-cased) type');
    await popup.close();
  });

  await s.step('View All Measurement Types lists the new type exactly as stored', async () => {
    const popup = await open('View All Measurement Types', 'view-measurement-types');
    const row = listedType(popup, created);
    h.assert(await row.count() === 1, 'The new type is not listed exactly once');
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[1] === created.display && cells[2] === created.desc && cells[3] === created.instruction,
      'The listed type does not show the stored display name, description and instruction');
    await popup.close();
  });

  await s.step('a second Add with the same type code is refused and writes nothing', async () => {
    const popup = await open('Add Measurement Type', 'add-duplicate-measurement-type');
    await popup.locator('#type').fill(created.type);
    await popup.locator('#typeDesc').fill(created.desc);
    await popup.locator('#typeDisplayName').fill(created.display);
    await popup.locator('#measuringInstrc').fill(instruction2);
    await landOn(popup, 'AddMeasurementType', () => popup.locator('input[type="submit"][name="submit"]').click());
    await popup.locator('.action-errors').getByText('The entered type already exists').waitFor();
    h.assert(typeRows(created).length === 1, 'A duplicate measurement type code was stored');
    await popup.close();
  });

  await s.step('Add Measuring Instruction stores a second instruction for the new type', async () => {
    const popup = await open('Add Measuring Instruction', 'add-measuring-instruction');
    await popup.locator('#typeDisplayName').selectOption(created.display);
    await popup.locator('#measuringInstrc').fill(instruction2);
    await popup.locator('#validation').selectOption({label: 'Numeric Value: 0 to 300'});
    await landOn(popup, 'AddMeasuringInstruction', () => popup.getByRole('button', {name: 'Add', exact: true}).click());
    await popup.getByText('Measuring Instruction has been added successfully').waitFor();
    h.assert(JSON.stringify(typeRows(created).map(row => row[3])) === JSON.stringify([created.instruction, instruction2]),
      'The second instruction was not stored against the new type');
    await popup.close();
  });

  await s.step('Modify Measurement Types adds both owned types to a group and removes one', async () => {
    const popup = await open('Edit Measurement Group', 'edit-measurement-group');
    await landOn(popup, 'SetupEditMeasurementGroup', async () => (await selectGroup(popup, editedGroup, 'Modify Measurement Types')).click());
    await popup.locator('select[name="selectedAddTypes"]').selectOption([seeded.display, peer.display]);
    await landOn(popup, 'SetupEditMeasurementGroup', () => popup.getByRole('button', {name: 'Add', exact: true}).click());
    h.assert(JSON.stringify(members(editedGroup)) === JSON.stringify([seeded.display, peer.display]),
      'Add did not store both owned members');
    const current = (await popup.locator('select[name="selectedDeleteTypes"] option').allInnerTexts()).map(t => t.trim()).sort();
    h.assert(JSON.stringify(current) === JSON.stringify([seeded.display, peer.display]), 'The editor does not list the stored members');
    await popup.locator('select[name="selectedDeleteTypes"]').selectOption(peer.display);
    await landOn(popup, 'SetupEditMeasurementGroup', () => popup.getByRole('button', {name: 'Delete', exact: true}).click());
    h.assert(JSON.stringify(members(editedGroup)) === JSON.stringify([seeded.display]), 'Delete did not remove exactly the second member');
    await popup.close();
  });

  await s.step('Add Measurement Group defines a group and adds the new type to it', async () => {
    const popup = await open('Add Measurement Group', 'add-measurement-group');
    await popup.locator('input[name="groupName"]').fill(createdGroup);
    await landOn(popup, 'SetupAddMeasurementGroup', () => popup.locator('input[type="submit"][name="submit"]').click());
    h.assert(styles(createdGroup) === '1', 'The new group has no measurementGroupStyle row');
    await popup.locator('select[name="selectedAddTypes"]').selectOption(created.display);
    await landOn(popup, 'SetupEditMeasurementGroup', () => popup.getByRole('button', {name: 'Add', exact: true}).click());
    h.assert(JSON.stringify(members(createdGroup)) === JSON.stringify([created.display]), 'The new type was not added to the new group');
    await popup.close();
  });

  await s.step('Delete in View All Measurement Types removes every owned type and records each row', async () => {
    const popup = await open('View All Measurement Types', 'delete-measurement-types');
    for (const t of types) for (const box of await listedType(popup, t).locator('input[name="deleteCheckbox"]').all()) await box.check();
    await landOn(popup, 'DeleteMeasurementTypes', () => popup.getByRole('button', {name: 'Delete', exact: true}).click());
    h.assert(types.every(t => typeRows(t).length === 0), 'The owned measurement types were not deleted');
    const deleted = [...types.map(t => [t.type, t.display, t.instruction]), [created.type, created.display, instruction2]];
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementTypeDeleted WHERE (type,typeDisplayName,measuringInstruction) IN
      (${deleted.map(row => `(${row.map(q).join(',')})`).join(',')})`) === String(deleted.length),
    'Type deletion did not record exactly one deleted-type row per stored instruction');
    h.assert(members(editedGroup).length + members(createdGroup).length === 0, 'Deleting a type left it as a group member');
    for (const t of types) h.assert(await listedType(popup, t).count() === 0, 'A deleted type is still listed');
    await popup.close();
  });

  await s.step('Add Measurement Style Sheet uploads a style sheet that Delete then removes', async () => {
    const file = path.join(cssDir, cssName);
    fs.writeFileSync(file, `/* ${marker} */\n.FAKEPW${hex} { color: #000; }\n`);
    const popup = await open('Add Measurement Style Sheet', 'add-style-sheet');
    await popup.locator('#file').setInputFiles(file);
    await landOn(popup, 'AddMeasurementStyleSheet', () => popup.getByRole('button', {name: 'Continue', exact: true}).click());
    const errors = popup.locator('.action-errors');
    h.assert(await errors.count() === 0, `Uploading a valid style sheet was refused: ${(await errors.allInnerTexts()).join(' ').trim()}`);
    await popup.getByText(/^Style Sheet .* added successfully!$/).waitFor();
    h.assert(fs.existsSync(uploadedFile) && fs.readFileSync(uploadedFile, 'utf8') === fs.readFileSync(file, 'utf8'),
      'The uploaded style sheet was not stored byte-for-byte in the upload directory');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementCSSLocation WHERE location=${q(cssName)}`) === '1',
      'The uploaded style sheet has no measurementCSSLocation row');
    await popup.close();
    const list = await open('View All Style Sheet', 'delete-style-sheet');
    await list.locator('tr.data').filter({hasText: cssName}).locator('input[name="deleteCheckbox"]').check();
    await landOn(list, 'DeleteMeasurementStyleSheet', () => list.getByRole('button', {name: 'Delete', exact: true}).click());
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementCSSLocation WHERE location=${q(cssName)}`) === '0',
      'Delete did not remove the unused style sheet');
    await list.close();
  });
}

if (require.main === module) runWorkflow('measurement-type-group-admin', workflow, {
  openPatient: true,
  preflight() {
    const dir = process.env.MEASUREMENT_CSS_UPLOAD_DIR;
    if (!dir) throw new h.SkipCheck('MEASUREMENT_CSS_UPLOAD_DIR is not set; this check needs it (see scripts/playwright-suite.json)');
    h.assert(path.isAbsolute(dir) && fs.statSync(dir).isDirectory(), 'MEASUREMENT_CSS_UPLOAD_DIR must be an existing absolute directory');
  },
});
module.exports = {workflow};
