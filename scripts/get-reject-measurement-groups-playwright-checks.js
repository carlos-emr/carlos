#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, measurement groups. User path: Schedule ▸ Administration ▸ Customize
 * Measurements (admin iframe) ▸ Edit Measurement Group popup ▸ pick a group ▸ Modify
 * Measurement Types ▸ Add / Delete a type, and ▸ Delete (confirm) the group.
 *
 * encounter/oscarMeasurements/EditMeasurementGroup (EctEditMeasurementGroup2Action:
 * forward=add|delete persists/removes measurementGroup rows) and .../SelectMeasurementGroup
 * (EctSelectMeasurementGroup2Action: forward=delete removes a whole group and its style row)
 * have no POST check, are outside the GET-rejection contract, and their names have no
 * mutator prefix; the dispatch key is `forward=`, which HttpMethodGuardFilter never reads.
 * Each write is driven through the page on owned group 1 (request captured), then replayed
 * as GET/HEAD against owned groups 2/3; all probes are asserted in the LAST step.
 *
 * Fixtures: FAKEPW<hex> measurement types (codes/display names) and three marker-named
 * groups with style rows, seeded by SQL; cleanup deletes only those and asserts it.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger, takeKnownNoise } = require('./lib/get-reject-probe');

const NAME = 'get-reject-measurement-groups';

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const hex = marker.slice('FAKE-PW'.length).toUpperCase().slice(0, 12);
  const [typeA, typeB] = ['A', 'B'].map(x => ({ type: `PW${hex}${x}`, display: `FAKEPW${hex}${x}` }));
  const [g1, g2, g3] = [1, 2, 3].map(n => `FAKEPW ${hex} group ${n}`);
  const groups = [g1, g2, g3].map(q).join(',');
  const displays = [typeA, typeB].map(t => q(t.display)).join(',');
  const member = (g, t) => `SELECT COUNT(*) FROM measurementGroup WHERE name=${q(g)} AND typeDisplayName=${q(t.display)}`;
  const groupRows = g => `SELECT CONCAT((SELECT COUNT(*) FROM measurementGroup WHERE name=${q(g)}),'|',
    (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=${q(g)}))`;
  const owned = () => sql.value(`SELECT (SELECT COUNT(*) FROM measurementGroup WHERE name IN (${groups}) OR typeDisplayName IN (${displays}))
    + (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName IN (${groups}))
    + (SELECT COUNT(*) FROM measurementType WHERE typeDisplayName IN (${displays}))`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM measurementGroup WHERE name IN (${groups}) OR typeDisplayName IN (${displays});
      DELETE FROM measurementGroupStyle WHERE groupName IN (${groups});
      DELETE FROM measurementType WHERE typeDisplayName IN (${displays}) AND type IN (${q(typeA.type)},${q(typeB.type)})`);
    h.assert(owned() === '0', 'Owned measurement group rows were not removed');
  });
  h.assert(owned() === '0', 'Per-run measurement names already exist');
  sql.execute([typeA, typeB].map(t => `INSERT INTO measurementType(type,typeDisplayName,typeDescription,measuringInstruction,validation,createDate)
      VALUES (${q(t.type)},${q(t.display)},${q(`FAKEPW ${hex} type`)},${q(`FAKEPW ${hex} instr`)},'5',NOW())`).join(';')
    + `; INSERT INTO measurementGroupStyle(groupName,cssID) VALUES (${q(g1)},0),(${q(g2)},0),(${q(g3)},0);
      INSERT INTO measurementGroup(name,typeDisplayName) VALUES (${q(g1)},${q(typeA.display)}),(${q(g2)},${q(typeA.display)}),
        (${q(g3)},${q(typeA.display)})`);

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'measurement-administration', timeout: 20000 });
  const customize = admin.getByRole('link', { name: 'Customize Measurements', exact: true, includeHidden: true });
  await revealAuditLink(admin, customize, 20000);
  await customize.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const menu = await (await iframe.elementHandle()).contentFrame();
  h.assert(menu, 'Customize Measurements did not load in the administration frame');
  const openGroupEditor = async label => {
    const popup = await s.popup(admin, menu.getByRole('link', { name: 'Edit Measurement Group', exact: true }), label);
    await popup.waitForLoadState('load');
    return popup;
  };
  const land = async (page, route, act) => {
    const landed = page.waitForEvent('framenavigated', { timeout: 20000,
      predicate: frame => frame === page.mainFrame() && new URL(frame.url()).pathname.endsWith(route) });
    landed.catch(() => {});
    await act();
    await landed;
    await page.waitForLoadState('load');
    await h.assertNotErrorPage(page, route);
  };
  // The group editor pages link /styles.css, which does not exist (finding L87). Those
  // console/request errors are moved into the final assertion rather than failing the step
  // that merely loaded a page; nothing is dropped.
  let knownNoise = 0;
  const step = (label, body) => s.step(label, async () => {
    try { await body(); } finally { knownNoise += takeKnownNoise(s.recorder, '/styles.css').length; }
  });
  let added;
  let removed;
  let dropped;
  let types;

  await step('Modify Measurement Types ▸ Add puts the owned type into owned group 1 (POST EditMeasurementGroup forward=add)', async () => {
    types = await openGroupEditor('measurement-group-types');
    await types.locator('#selectedGroupName').selectOption(g1);
    await land(types, 'SetupEditMeasurementGroup', () => types.getByRole('button', { name: 'Modify Measurement Types', exact: true }).click());
    await types.locator('select[name="selectedAddTypes"]').selectOption(typeB.display);
    added = await captureRequest(types, url => url.pathname.endsWith('/oscarMeasurements/EditMeasurementGroup'),
      () => land(types, 'SetupEditMeasurementGroup', () => types.getByRole('button', { name: 'Add', exact: true }).click()));
    h.assert(added.params.get('forward') === 'add' && added.params.get('groupName') === g1, 'Add did not post forward=add for group 1');
    await expectValue(sql, member(g1, typeB), '1', 'Add did not put the type into group 1');
  });

  await step('the Add replayed as GET/HEAD against owned group 2 is recorded', async () => {
    await ledger.probe(s, { label: 'oscarMeasurements/EditMeasurementGroup forward=add', path: added.path,
      params: replayParams(added.params, { groupName: g2 }), snapshot: () => sql.value(member(g2, typeB)) });
  });

  await step('Modify Measurement Types ▸ Delete takes the type out of group 1 (POST EditMeasurementGroup forward=delete)', async () => {
    await types.locator('select[name="selectedDeleteTypes"]').selectOption(typeB.display);
    removed = await captureRequest(types, url => url.pathname.endsWith('/oscarMeasurements/EditMeasurementGroup'),
      () => land(types, 'SetupEditMeasurementGroup', () => types.getByRole('button', { name: 'Delete', exact: true }).click()));
    h.assert(removed.params.get('forward') === 'delete', 'Delete did not post forward=delete');
    await expectValue(sql, member(g1, typeB), '0', 'Delete did not remove the type from group 1');
    h.assert(sql.value(member(g1, typeA)) === '1', 'Delete removed an unselected type');
    await types.close();
  });

  await step('the type Delete replayed as GET/HEAD against owned group 2 is recorded', async () => {
    await ledger.probe(s, { label: 'oscarMeasurements/EditMeasurementGroup forward=delete', path: removed.path,
      params: replayParams(removed.params, { groupName: g2, selectedDeleteTypes: typeA.display }),
      snapshot: () => sql.value(member(g2, typeA)) });
  });

  await step('Edit Measurement Group ▸ Delete removes owned group 1 after one confirmation (POST SelectMeasurementGroup forward=delete)', async () => {
    const popup = await openGroupEditor('measurement-group-delete');
    await popup.locator('#selectedGroupName').selectOption(g1);
    const dialogs = await h.withExpectedDialogs(popup, async () => {
      dropped = await captureRequest(popup, url => url.pathname.endsWith('/oscarMeasurements/SelectMeasurementGroup'),
        () => land(popup, 'SelectMeasurementGroup', () => popup.getByRole('button', { name: 'Delete', exact: true }).click()));
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Group delete did not confirm exactly once');
    h.assert(dropped.params.get('forward') === 'delete', 'Group delete did not post forward=delete');
    await expectValue(sql, groupRows(g1), '0|0', 'Group delete left rows of group 1');
    await popup.close();
  });

  await step('the group Delete replayed as GET/HEAD against owned group 3 is recorded', async () => {
    await ledger.probe(s, { label: 'oscarMeasurements/SelectMeasurementGroup forward=delete', path: dropped.path,
      params: replayParams(dropped.params, { selectedGroupName: g3 }), snapshot: () => sql.value(groupRows(g3)) });
  });

  await s.step('every measurement-group write refused GET/HEAD, groups 2 and 3 are unchanged, and the pages loaded cleanly', async () => {
    const problems = [];
    try { ledger.assertAllRefused(); } catch (error) { problems.push(error.message); }
    if (knownNoise) problems.push(`the group editor pages raised ${knownNoise} console/request error(s) for the missing /styles.css (finding L87)`);
    h.assert(!problems.length, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow };
