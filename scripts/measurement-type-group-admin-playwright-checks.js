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

__BODY__
}

if (require.main === module) runWorkflow('measurement-type-group-admin', workflow, {openPatient: true});
module.exports = {workflow};
