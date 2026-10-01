#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Gap check (clinical): Customize Measurements ▸ Edit Measurement Group ▸ "Modify Measurement Style".
 *
 * User path: Schedule ▸ Administration ▸ Customize Measurements (admin iframe) ▸ Edit Measurement
 * Group popup ▸ pick a group ▸ Modify Measurement Style ▸ choose a style sheet ▸ OK
 * (POST encounter/oscarMeasurements/EditMeasurementStyle -> EctEditMeasurementStyle2Action.changeCSS).
 * measurement-type-group-admin covers the types, instructions, group membership and style-sheet
 * upload; get-reject-measurement-groups probes the membership writes. Nothing drives this action, and
 * it is the one that rewrites the clinic's per-group display style.
 *
 * Asserts, over rows the run owns: the style page names the group's current style sheet and offers the
 * owned style sheet; OK ends back on the group list without an error; the chosen group's
 * measurementGroupStyle row now carries the chosen style sheet id (cssID) and keeps its own primary
 * key; and no OTHER group's style row is touched. The correct behaviour is asserted in the LAST step
 * so everything provable passes first. (Code reading, finding L88: changeCSS calls m.setId(styleSheet),
 * i.e. writes the style sheet id into the row's PRIMARY KEY, so the merge overwrites whichever group's
 * row has that key and the chosen group's style never changes.)
 *
 * Fixtures: FAKEPW<hex> groups A and B (each with a type member and a style row), one FAKEPW
 * measurementCSSLocation row whose id is a large explicit number, and B's style row sits AT THAT KEY so
 * a mishandled merge can only ever damage the owned row, never a demo group. Cleanup deletes only those
 * rows (by name and by key), asserts it, and puts both tables' AUTO_INCREMENT counters (advanced by the
 * explicit keys) back to their pre-run values. Coverage plan §3.7 admin-misc.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(s) {
  const {sql, marker} = s;
  const q = h.sqlString;
  const hex = marker.slice('FAKE-PW'.length).toUpperCase().slice(0, 12);
  const gA = `FAKEPW ${hex} style A`;
  const gB = `FAKEPW ${hex} style B`;
  const type = {type: `PW${hex}S`, display: `FAKEPW${hex}S`};
  const css = 8000000 + Math.floor(Math.random() * 900000);
  const cssName = `FAKEPW-${hex}.css`;
  const owned = () => sql.value(`SELECT (SELECT COUNT(*) FROM measurementGroup WHERE name IN (${q(gA)},${q(gB)}))
    + (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName IN (${q(gA)},${q(gB)}) OR groupID=${css})
    + (SELECT COUNT(*) FROM measurementCSSLocation WHERE cssID=${css})
    + (SELECT COUNT(*) FROM measurementType WHERE typeDisplayName=${q(type.display)})`);
  // The explicit 8,xxx,xxx keys below push both tables' AUTO_INCREMENT counters up and deleting the
  // rows does not rewind them, so every run would leave the clinic's counters further ahead. Snapshot
  // them first and put them back after the delete. ALTER ... AUTO_INCREMENT never goes below
  // MAX(id)+1 on InnoDB, so a row another session inserts meanwhile can never be handed a duplicate key.
  const counterOf = table => sql.value(`SELECT AUTO_INCREMENT FROM information_schema.TABLES
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=${q(table)}`);
  const counters = {measurementCSSLocation: counterOf('measurementCSSLocation'), measurementGroupStyle: counterOf('measurementGroupStyle')};
  s.cleanup(() => {
    sql.execute(`DELETE FROM measurementGroup WHERE name IN (${q(gA)},${q(gB)});
      DELETE FROM measurementGroupStyle WHERE groupName IN (${q(gA)},${q(gB)}) OR groupID=${css};
      DELETE FROM measurementCSSLocation WHERE cssID=${css} AND location=${q(cssName)};
      DELETE FROM measurementType WHERE typeDisplayName=${q(type.display)} AND type=${q(type.type)}`);
    h.assert(owned() === '0', 'Owned measurement style rows were not removed');
    for (const [table, next] of Object.entries(counters)) {
      if (!/^\d+$/.test(next || '')) continue;
      try {
        sql.execute(`ALTER TABLE ${table} AUTO_INCREMENT=${Number(next)}`);
      } catch (error) {
        // A database account without ALTER rights cannot restore the counter; the rows are gone, which is what matters.
      }
    }
  });
  h.assert(owned() === '0' && sql.value(`SELECT COUNT(*) FROM measurementGroupStyle WHERE groupID=${css}`) === '0',
    'The per-run measurement style rows already exist');
  sql.execute(`INSERT INTO measurementType(type,typeDisplayName,typeDescription,measuringInstruction,validation,createDate)
      VALUES (${q(type.type)},${q(type.display)},${q(`FAKEPW ${hex} type`)},${q(`FAKEPW ${hex} instr`)},'5',NOW());
    INSERT INTO measurementCSSLocation(cssID,location) VALUES (${css},${q(cssName)});
    INSERT INTO measurementGroupStyle(groupName,cssID) VALUES (${q(gA)},0);
    INSERT INTO measurementGroupStyle(groupID,groupName,cssID) VALUES (${css},${q(gB)},0);
    INSERT INTO measurementGroup(name,typeDisplayName) VALUES (${q(gA)},${q(type.display)}),(${q(gB)},${q(type.display)})`);
  const idA = sql.value(`SELECT groupID FROM measurementGroupStyle WHERE groupName=${q(gA)}`);

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'measurement-administration', timeout: 20000});
  const customize = admin.getByRole('link', {name: 'Customize Measurements', exact: true, includeHidden: true});
  await revealAuditLink(admin, customize, 20000);
  await customize.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const menu = await (await iframe.elementHandle()).contentFrame();
  h.assert(menu, 'Customize Measurements did not load in the administration frame');

  let editor;
  await s.step('Edit Measurement Group lists the owned group and Modify Measurement Style names its current style sheet and offers the owned one', async () => {
    editor = await s.popup(admin, menu.getByRole('link', {name: 'Edit Measurement Group', exact: true}), 'measurement-group-style');
    await editor.waitForLoadState('load');
    await editor.locator('#selectedGroupName').selectOption(gA);
    const landed = editor.waitForEvent('framenavigated', {timeout: 20000,
      predicate: f => f === editor.mainFrame() && /EditMeasurementGroupStyle|SelectMeasurementGroup/.test(new URL(f.url()).pathname)});
    landed.catch(() => {});
    await editor.getByRole('button', {name: 'Modify Measurement Style', exact: true}).click();
    await landed;
    await editor.waitForLoadState('load');
    await h.assertNotErrorPage(editor, 'style page');
    h.assert((await editor.locator('body').innerText()).includes(gA), 'The style page does not name the selected group');
    const option = editor.locator('select[name="styleSheet"] option', {hasText: cssName});
    h.assert(await option.count() === 1, 'The style page does not offer the owned style sheet');
    h.assert(await option.getAttribute('value') === String(css), 'The owned style sheet option does not carry its style sheet id');
  });

  await s.step('OK posts the chosen style sheet and returns to the group list without an error', async () => {
    await editor.locator('select[name="styleSheet"]').selectOption(String(css));
    const post = editor.waitForResponse(r => r.request().method() === 'POST' && /\/oscarMeasurements\/EditMeasurementStyle$/.test(new URL(r.url()).pathname), {timeout: 20000});
    await editor.locator('input[type="button"][value="OK"]').click();
    const response = await post;
    h.assert(response.status() < 400, `Modify Measurement Style answered HTTP ${response.status()}`);
    const params = new URLSearchParams(response.request().postData() || '');
    h.assert(params.get('styleSheet') === String(css) && params.get('groupName') === gA, 'OK did not post the chosen group and style sheet');
    await editor.waitForLoadState('load');
    await h.assertNotErrorPage(editor, 'after style change');
  });

  await s.step('the chosen group carries the chosen style sheet and no other group\'s style row was touched', async () => {
    await new Promise(resolve => setTimeout(resolve, 500));
    const a = sql.rows(`SELECT groupID, cssID FROM measurementGroupStyle WHERE groupName=${q(gA)}`);
    const b = sql.rows(`SELECT groupID, groupName, cssID FROM measurementGroupStyle WHERE groupID=${css}`);
    const problems = [];
    if (!(a.length === 1 && a[0][0] === idA && a[0][1] === String(css))) {
      problems.push(`group A's style row should keep key ${idA} and read cssID ${css}, found ${JSON.stringify(a)}`);
    }
    if (!(b.length === 1 && b[0][1] === gB && b[0][2] === '0')) {
      problems.push(`group B's style row (key ${css}) was changed: ${JSON.stringify(b)}`);
    }
    h.assert(problems.length === 0, `${problems.join('; ')} -- Modify Measurement Style wrote the style sheet id into the row's primary key (EctEditMeasurementStyle2Action.changeCSS)`);
  });
  await editor.close().catch(() => {});
}

if (require.main === module) runWorkflow('gap-clinical-measurement-group-style', workflow, {openPatient: false});
module.exports = {workflow};
