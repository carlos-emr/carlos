#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Customize Measurements navigation and write-method check (issue #3335).
 *
 * User path: Schedule ▸ Administration ▸ Customize Measurements (admin iframe) ▸ every link
 * the page offers: Add Measurement Group, Edit Measurement Group, View All Measurement Types,
 * Add Measurement Type, View Mapping, Add Measurement Mapping, Remove/Remap Measurement
 * Mapping, Add Measuring Instruction, View All Style Sheet, Add Measurement Style Sheet.
 *
 * Before #3335 four of those links (and the group-definition continuation) answered 405:
 * HttpMethodGuardFilter refused GET on the read-only Setup* form openers and on the
 * AddMeasurementMap/RemoveMeasurementMap routes, which render their page on a GET without write
 * parameters; and View Mapping threw `ReferenceError: stripe`. This check opens every link and
 * asserts the page it lands on is the intended form, answered 200, with no console error; then
 * that the continuation SetupAddMeasurementGroup renders by GET without storing a group; then
 * that every write of the page, sent with its write parameters, refuses GET and HEAD and leaves
 * the rows it owns unchanged (the mutation routes require POST). Probes are asserted together
 * in the LAST step.
 *
 * Fixtures: one FAKEPW measurement type, one FAKEPW group (style row and member), one owned
 * X<hex> LOINC mapping row and one FAKEPW style-sheet row, all seeded by SQL; cleanup deletes
 * only those (and anything a write could have created under the owned names) and asserts it.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');
const {createLedger} = require('./lib/get-reject-probe');

const NAME = 'measurement-customization-routes';
const ROUTE = '/encounter/oscarMeasurements/';

// Link text, the route it must land on, and an element only that form renders.
const LINKS = [
  ['Add Measurement Group', 'SetupStyleSheetList', 'input[name="groupName"]'],
  ['Edit Measurement Group', 'SetupGroupList', '#selectedGroupName'],
  ['View All Measurement Types', 'SetupDisplayMeasurementTypes', 'form[action$="/DeleteMeasurementTypes"]'],
  ['Add Measurement Type', 'SetupAddMeasurementType', '#typeDisplayName'],
  ['View Mapping', 'ViewViewMeasurementMap', 'form[name="LOINC"]'],
  ['Add Measurement Mapping', 'AddMeasurementMap', 'select[name="loinc_code"]'],
  ['Remove/Remap Measurement Mapping', 'RemoveMeasurementMap', 'input[name="searchstring"]'],
  ['Add Measuring Instruction', 'SetupAddMeasuringInstruction', '#measuringInstrc'],
  ['View All Style Sheet', 'SetupDisplayMeasurementStyleSheet', 'form[action$="/DeleteMeasurementStyleSheet"]'],
  ['Add Measurement Style Sheet', 'SetupAddMeasurementStyleSheet', '#file'],
];

async function workflow(s) {
  const {sql, marker} = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const hex = marker.slice('FAKE-PW'.length).toUpperCase().slice(0, 12);
  const type = {type: `PW${hex}R`, display: `FAKEPW${hex}R`, instruction: `FAKEPW ${hex} routes instr`};
  const newType = {type: `PW${hex}N`, display: `FAKEPW${hex}N`};
  const group = `FAKEPW ${hex} routes group`;
  const newGroup = `FAKEPW ${hex} routes new group`;
  const loinc = `X${hex.slice(0, 10)}R`;
  const loinc2 = `X${hex.slice(0, 10)}S`;
  const cssName = `FAKEPW${hex}routes.css`;
  const groups = [group, newGroup].map(q).join(',');
  const types = [type, newType];
  const owned = () => sql.value(`SELECT
      (SELECT COUNT(*) FROM measurementGroup WHERE name IN (${groups}) OR typeDisplayName IN (${types.map(t => q(t.display)).join(',')}))
    + (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName IN (${groups}))
    + (SELECT COUNT(*) FROM measurementType WHERE type IN (${types.map(t => q(t.type)).join(',')}))
    + (SELECT COUNT(*) FROM measurementTypeDeleted WHERE type IN (${types.map(t => q(t.type)).join(',')}))
    + (SELECT COUNT(*) FROM measurementMap WHERE loinc_code IN (${q(loinc)},${q(loinc2)}))
    + (SELECT COUNT(*) FROM measurementCSSLocation WHERE location=${q(cssName)})`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM measurementGroup WHERE name IN (${groups}) OR typeDisplayName IN (${types.map(t => q(t.display)).join(',')});
      DELETE FROM measurementGroupStyle WHERE groupName IN (${groups});
      DELETE FROM measurementType WHERE type IN (${types.map(t => q(t.type)).join(',')});
      DELETE FROM measurementTypeDeleted WHERE type IN (${types.map(t => q(t.type)).join(',')});
      DELETE FROM measurementMap WHERE loinc_code IN (${q(loinc)},${q(loinc2)});
      DELETE FROM recyclebin WHERE table_name='measurementMap' AND table_content LIKE ${q(`%<loinc_code>${loinc}</loinc_code>%`)};
      DELETE FROM measurementCSSLocation WHERE location=${q(cssName)}`);
    h.assert(owned() === '0', 'Owned measurement customization rows were not removed');
  });
  h.assert(owned() === '0', 'Per-run measurement customization names already exist');
  sql.execute(`INSERT INTO measurementType(type,typeDisplayName,typeDescription,measuringInstruction,validation,createDate)
      VALUES (${q(type.type)},${q(type.display)},${q(`FAKEPW ${hex} routes type`)},${q(type.instruction)},'5',NOW());
    INSERT INTO measurementGroupStyle(groupName,cssID) VALUES (${q(group)},0);
    INSERT INTO measurementGroup(name,typeDisplayName) VALUES (${q(group)},${q(type.display)});
    INSERT INTO measurementMap(loinc_code,ident_code,name,lab_type) VALUES (${q(loinc)},${q(type.type)},${q(type.display)},'FLOWSHEET');
    INSERT INTO measurementCSSLocation(location) VALUES (${q(cssName)})`);
  const typeId = sql.value(`SELECT id FROM measurementType WHERE type=${q(type.type)}`);
  const mapId = sql.value(`SELECT id FROM measurementMap WHERE loinc_code=${q(loinc)}`);
  const cssId = sql.value(`SELECT cssID FROM measurementCSSLocation WHERE location=${q(cssName)}`);

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'measurement-customization', timeout: 20000});
  const customize = admin.getByRole('link', {name: 'Customize Measurements', exact: true, includeHidden: true});
  await revealAuditLink(admin, customize, 20000);
  await customize.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const menu = await (await iframe.elementHandle()).contentFrame();
  h.assert(menu, 'Customize Measurements did not load in the administration frame');
  await menu.getByRole('link', {name: 'Add Measurement Group', exact: true}).waitFor();

  for (const [link, route, form] of LINKS) {
    await s.step(`${link} opens its form by GET (${route})`, async () => {
      const since = s.recorder.badResponses.length;
      // Every link targets one window name, so each popup is closed after use.
      const popup = await s.popup(admin, menu.getByRole('link', {name: link, exact: true}), `customize-${route}`);
      await popup.waitForLoadState('load');
      const refused = s.recorder.badResponses.slice(since).find(entry => entry.resourceType === 'document');
      h.assert(!refused, `${link} opened an HTTP ${refused && refused.status} page for ${refused && refused.method} `
        + `${refused && new URL(refused.url).pathname}`);
      h.assert(new URL(popup.url()).pathname.endsWith(ROUTE + route), `${link} landed on ${new URL(popup.url()).pathname}`);
      await h.assertNotErrorPage(popup, link);
      await popup.locator(form).first().waitFor({state: 'attached', timeout: 10000});
      await popup.close();
    });
  }

  await s.step('the group-definition continuation renders by GET and stores no group', async () => {
    const path = `${s.config.baseUrl.pathname.replace(/\/$/, '')}${ROUTE}SetupAddMeasurementGroup`;
    const url = new URL(`${path}?${new URLSearchParams({groupName: newGroup, styleSheet: String(cssId)})}`, s.config.baseUrl.origin);
    const response = await s.context.request.get(url.toString(), {maxRedirects: 0, failOnStatusCode: false});
    h.assert(response.status() === 200, `GET SetupAddMeasurementGroup answered ${response.status()}`);
    const body = await response.text();
    h.assert(body.includes('name="selectedAddTypes"') && body.includes(`value="${cssId}"`),
      'The continuation did not render the type list with the chosen style sheet');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=${q(newGroup)}`) === '0',
      'Rendering the type list stored the group');
  });

  const groupRows = name => () => sql.value(`SELECT CONCAT((SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=${q(name)}),'|',
    (SELECT COALESCE(GROUP_CONCAT(cssID ORDER BY cssID),'') FROM measurementGroupStyle WHERE groupName=${q(name)}),'|',
    (SELECT COALESCE(GROUP_CONCAT(typeDisplayName ORDER BY typeDisplayName),'') FROM measurementGroup WHERE name=${q(name)}))`);
  const typeRows = () => sql.value(`SELECT CONCAT((SELECT COUNT(*) FROM measurementType WHERE type IN (${q(type.type)},${q(newType.type)})),'|',
    (SELECT COUNT(*) FROM measurementTypeDeleted WHERE type IN (${q(type.type)},${q(newType.type)})))`);
  const mapRows = () => sql.value(`SELECT COALESCE(GROUP_CONCAT(CONCAT(id,':',loinc_code,':',ident_code) ORDER BY id),'')
    FROM measurementMap WHERE loinc_code IN (${q(loinc)},${q(loinc2)})`);
  const cssRows = () => sql.value(`SELECT COUNT(*) FROM measurementCSSLocation WHERE location=${q(cssName)}`);
  const base = `${s.config.baseUrl.pathname.replace(/\/$/, '')}${ROUTE}`;
  const probes = [
    ['DefineNewMeasurementGroup', {groupName: newGroup, styleSheet: '0'}, groupRows(newGroup)],
    ['AddMeasurementGroup', {groupName: newGroup, styleSheet: '0', forward: 'add', selectedAddTypes: type.display}, groupRows(newGroup)],
    ['EditMeasurementGroup', {groupName: group, forward: 'delete', selectedDeleteTypes: type.display}, groupRows(group)],
    ['EditMeasurementStyle', {groupName: group, styleSheet: String(cssId)}, groupRows(group)],
    ['SelectMeasurementGroup', {selectedGroupName: group, forward: 'delete'}, groupRows(group)],
    ['AddMeasurementType', {type: newType.type, typeDesc: 'FAKEPW routes', typeDisplayName: newType.display,
      measuringInstrc: 'FAKEPW routes', validation: '5'}, typeRows],
    ['AddMeasuringInstruction', {typeDisplayName: type.display, measuringInstrc: 'FAKEPW routes second', validation: '5'}, typeRows],
    ['DeleteMeasurementTypes', {deleteCheckbox: typeId}, typeRows],
    ['AddMeasurementMap', {identifier: `${type.type},FLOWSHEET,${type.display}`, loinc_code: loinc2}, mapRows],
    ['NewMeasurementMap', {identifier: `${loinc2},PATHL7,${marker}`, loinc_code: loinc2, name: marker}, mapRows],
    ['RemoveMeasurementMap', {id: mapId}, mapRows],
    ['RemapMeasurementMap', {id: mapId, identifier: type.type, name: type.display, type: 'FLOWSHEET', loinc_code: loinc2}, mapRows],
    ['DeleteMeasurementStyleSheet', {deleteCheckbox: cssId}, cssRows],
  ];
  for (const [route, params, snapshot] of probes) {
    await s.step(`GET and HEAD to ${route} are recorded`, async () => {
      await ledger.probe(s, {label: route, path: base + route, params: new URLSearchParams(params), snapshot});
    });
  }

  await s.step('every Customize Measurements write refused GET and HEAD and changed no owned row', async () => {
    ledger.assertAllRefused();
  });
}

if (require.main === module) runWorkflow(NAME, workflow, {openPatient: false});
module.exports = {workflow};
