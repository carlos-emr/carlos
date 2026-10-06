#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §3.4 immunization-schedule-config: the legacy immunization schedule.
// User path: E-Chart ▸ Preventions ▸ "Old immunizations" (IMMUNIZATION_IN_PREVENTION=yes)
// ▸ Manage Immu. Template ▸ Add New ▸ Next Step ▸ Render ▸ set link (display popup); then
// "Old immunizations" ▸ Add. Immu. Template ▸ cell edit popup ▸ Save ▸ Configure/Cancel;
// Configure ▸ Manage Immu. Template ▸ Delete ▸ Deleted List; reopen ▸ del / Show All / restore.
// Asserts each round trip against config_Immunization (setName, setXmlDoc, archived) and the
// patient's current immunizations row (archived=0 replaces the previous one; set status and
// cell lot/givenDate/comments live in the stored XML).
// Fixtures: the owned FAKE- patient, one template created through the UI and one seeded template
// (both carry the run marker in their row names). The picker must offer the newly created name;
// the created template is copied into the patient schedule. Cleanup deletes only
// those templates and the owned patient's immunizations rows, then asserts they are gone.
// The application defects (del link dead, template name lost, missing stylesheets) are asserted
// in the LAST step so every other step is proven first.
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

// Stylesheets the legacy JSPs link but the webapp does not ship. Their failures are moved out
// of the recorder after each step and asserted in the final step, not ignored.
const MISSING_STYLESHEETS = ['/styles.css', '/encounterStyles.css'];

function deferMissingStylesheets(recorder, contextPath, deferred) {
  const urls = MISSING_STYLESHEETS.map(path => contextPath + path);
  const isMissing = url => { try { return urls.includes(new URL(url).pathname); } catch { return false; } };
  const take = (list, predicate) => {
    for (let i = list.length - 1; i >= 0; i--) if (predicate(list[i])) deferred.push(...list.splice(i, 1));
  };
  take(recorder.requestFailures, entry => entry.resourceType === 'stylesheet' && isMissing(entry.url));
  take(recorder.badResponses, entry => entry.resourceType === 'stylesheet' && isMissing(entry.url));
  take(recorder.consoleIssues, entry => {
    const match = /^Refused to apply style from '([^']+)'/.exec(entry.text);
    return Boolean(match && isMissing(match[1]));
  });
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const contextPath = new URL(s.config.baseUrl).pathname.replace(/\/$/, '');
  const deferred = [];
  const step = (label, body) => s.step(label, async () => {
    await body();
    deferMissingStylesheets(s.recorder, contextPath, deferred);
  });
  const uiName = `${marker} "Created" <b>literal</b>`;
  const seededName = `${marker} Seeded`;
  const lot = `L${marker.slice(-10)}`;
  const rowComment = `${marker} row comment`;
  const owned = `providerNo=${sqlString(provider)} AND setXmlDoc LIKE ${sqlString(`%${marker}-%`)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM immunizations WHERE demographic_no=${patient};
      DELETE FROM config_Immunization WHERE ${owned}`);
    assert(sql.value(`SELECT (SELECT COUNT(*) FROM immunizations WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM config_Immunization WHERE ${owned})`) === '0', 'Owned immunization rows were not removed');
  });
  const seededXml = '<?xml version="1.0" encoding="UTF-8"?>'
    + `<immunizationSet headers="true" name="${seededName}"><columnList><column name="Dose 1"/><column name="Dose 2"/></columnList>`
    + `<rowList><row name="${marker}-S1"><cell index="1"/><cell index="2"/></row>`
    + `<row name="${marker}-S2"><cell index="2"/></row></rowList></immunizationSet>`;
  const seededId = sql.value(`INSERT INTO config_Immunization(setName,setXmlDoc,createDate,providerNo,archived)
    VALUES(${sqlString(seededName)},${sqlString(seededXml)},CURDATE(),${sqlString(provider)},0); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(seededId), 'Seeded template was not created');
  let uiId;
  const current = `FROM immunizations WHERE demographic_no=${patient} AND archived=0`;
  const currentXml = () => sql.value(`SELECT immunizations ${current}`) || '';

  const chart = await s.chart();
  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  let imm;
  const scheduledNames = () => imm.evaluate(xml => Array.from(
    new DOMParser().parseFromString(xml, 'application/xml').getElementsByTagName('immunizationSet'),
    set => set.getAttribute('name')), currentXml());
  const path = () => new URL(imm.url()).pathname;
  const go = (locator, label) => clickAndAwaitReload(imm, locator, { label });
  async function openSchedule() {
    if (imm && !imm.isClosed()) await imm.close();
    imm = await s.popup(index, index.locator('a[onclick*="immunization/initSchedule"]'), 'immunization-schedule');
  }

  await step('Old immunizations sends a patient without a schedule to the template picker', async () => {
    await openSchedule();
    assert(path().endsWith('/encounter/immunization/loadConfig'), 'No-schedule patient did not reach loadConfig');
    assert(await imm.locator(`input[name="chkSet"][value="${seededId}"]`).count() === 1, 'Template picker did not list the owned template');
    assert(sql.value(`SELECT COUNT(*) FROM immunizations WHERE demographic_no=${patient}`) === '0', 'Opening the picker wrote a schedule');
  });

  await step('Manage template ▸ Add New ▸ Next Step ▸ Render stores the drawn template grid', async () => {
    await go(imm.locator('input[value="Manage Immu. Template"]'), 'manage template');
    assert(path().endsWith('/encounter/immunization/config/initConfig'), 'Manage template did not open the set administration');
    await go(imm.locator('input[value="Add New"]'), 'add new set');
    await imm.locator('#setName').fill(uiName);
    await imm.locator('#numRows').fill('2');
    await imm.locator('#numCols').fill('2');
    await go(imm.locator('input[type="submit"][value="Next Step"]'), 'next step');
    assert(await imm.locator('#setName').inputValue() === uiName, 'Set name was not carried to the grid step');
    await imm.locator('textarea[name="heading0D1"]').fill('2 mo');
    await imm.locator('textarea[name="heading0D2"]').fill('4 mo');
    await imm.locator('textarea[name="immunization1D0"]').fill(`${marker}-R1`);
    await imm.locator('textarea[name="immunization2D0"]').fill(`${marker}-R2`);
    for (const box of ['yearAge1D1', 'yearAge1D2', 'yearAge2D2']) await imm.locator(`input[name="${box}"]`).check();
    await go(imm.locator('input[type="submit"][value="Render Immunization Set"]'), 'render set');
    assert(path().endsWith('/encounter/immunization/config/CreateImmunizationSetConfig'), 'Render did not return to the set list');
    uiId = sql.value(`SELECT setId FROM config_Immunization WHERE ${owned} AND setId<>${seededId}`);
    assert(/^[1-9]\d*$/.test(uiId || ''), 'Render did not store exactly one owned template');
    const [[archived, xml]] = sql.rows(`SELECT archived, setXmlDoc FROM config_Immunization WHERE setId=${uiId}`);
    assert(archived === '0', 'New template was stored archived');
    assert(sql.value(`SELECT setName FROM config_Immunization WHERE setId=${uiId}`) === uiName,
      'The created template name was not preserved');
    assert(await imm.getByText(uiName, {exact: true}).count() === 1, 'The template list did not render the literal name');
    assert(await imm.locator('a b').count() === 0, 'The template name rendered as HTML');
    assert(xml.includes(`name="${marker}-R2"`) && xml.includes('name="4 mo"') && (xml.match(/<cell /g) || []).length === 3,
      'Template XML lost its rows, columns or checked cells');
    assert(await imm.locator(`input[name="chkSetId"][value="${uiId}"]`).count() === 1, 'Set list does not show the new template');
  });

  await step('the set link opens the read-only template display', async () => {
    const created = await s.popup(imm, imm.locator(`a[href*="ImmunizationSetDisplay?setId=${uiId}"]`), 'created-set-display');
    assert((await created.locator('h1').innerText()).includes(uiName), 'The created set display lost the literal name');
    assert(await created.locator('h1 b').count() === 0, 'The displayed template name rendered as HTML');
    await created.close();
    const display = await s.popup(imm, imm.locator(`a[href*="ImmunizationSetDisplay?setId=${seededId}"]`), 'immunization-set-display');
    assert((await display.locator('h1').innerText()).includes(seededName), 'Set display shows a different template');
    assert(await display.getByText(`${marker}-S2`, { exact: true }).count() === 1, 'Set display lost a template row');
    await display.close();
  });

  await step('Add. Immu. Template copies the chosen template into the patient schedule', async () => {
    await openSchedule();
    const createdChoice = imm.locator(`input[name="chkSet"][value="${uiId}"]`);
    assert(await createdChoice.count() === 1, 'The named created template is absent from the picker');
    assert((await createdChoice.locator('..').innerText()).includes(uiName), 'The picker lost the literal created name');
    assert(await createdChoice.locator('..').locator('b').count() === 0, 'The picker rendered the template name as HTML');
    await createdChoice.check();
    await go(imm.locator('input[type="submit"][name="submit"]'), 'add template');
    await expectValue(sql, `SELECT COUNT(*) ${current}`, '1', 'Adding a template did not create the patient schedule');
    assert((await scheduledNames()).includes(uiName) && currentXml().includes(`name="${marker}-R2"`),
      'Patient schedule does not hold a copy of the chosen template');
    assert(await imm.locator('#chkSet0').count() === 1 && (await imm.getByText(uiName).count()) === 1,
      'Schedule page does not list the added template');
  });

  await step('a cell edited in the Record Immunization popup is saved with its lot and comments', async () => {
    await imm.locator('#chkSet0').check();
    await imm.locator('#tblSet0').waitFor({ state: 'visible' });
    const edit = await s.popup(imm, imm.locator('#tdSet0_Row0_Col1 a'), 'immunization-cell-edit');
    await edit.locator('input[name="chkStatus"][value="1"]').check();
    await edit.locator('#lot').fill(lot);
    await edit.locator('#comments').fill(`${marker} cell`);
    const closed = edit.waitForEvent('close');
    await edit.locator('input[value="Save and Close"]').click();
    await closed;
    const label = (await imm.locator('#tdSet0_Row0_Col1_label').innerText()).trim();
    assert(/^\d{4}\/\d{1,2}\/\d{1,2}$/.test(label), 'Opener cell label was not updated with the given date');
    await imm.locator('input[name="tdSet0_Row0_comments_text"]').fill(rowComment);
    const first = sql.value(`SELECT ID ${current}`);
    await go(imm.locator('input[type="button"][value="Save"]').first(), 'save schedule');
    await expectValue(sql, `SELECT archived FROM immunizations WHERE ID=${first}`, '1', 'Save did not archive the previous schedule');
    const xml = currentXml();
    assert(xml.includes(`lot="${lot}"`) && xml.includes(`givenDate="${label}"`) && xml.includes(`comments="${marker} cell"`),
      'Saved schedule lost the cell lot, given date or comments');
    assert(xml.includes(`<comments>${rowComment}</comments>`), 'Saved schedule lost the row comment');
    assert((await imm.locator('#tdSet0_Row0_Col1_label').innerText()).trim() === label, 'Reloaded schedule does not show the given date');
  });

  await step('Configure then Cancel returns to the schedule without writing', async () => {
    const rowsBefore = sql.value(`SELECT COUNT(*) FROM immunizations WHERE demographic_no=${patient}`);
    await go(imm.locator('input[type="button"][value="Configure"]').first(), 'configure');
    assert(await imm.locator(`input[name="chkSet"][value="${seededId}"]`).count() === 1, 'Configure did not open the template picker');
    await go(imm.locator('input[type="button"][value="Cancel"]'), 'cancel configure');
    assert(path().endsWith('/encounter/immunization/loadSchedule'), 'Cancel did not reload the schedule');
    assert(await imm.getByText(uiName).count() === 1, 'Reloaded schedule lost the template');
    assert(sql.value(`SELECT COUNT(*) FROM immunizations WHERE demographic_no=${patient}`) === rowsBefore, 'Configure/Cancel wrote a schedule row');
  });

  await step('the set administration deletes both owned templates and lists them as deleted', async () => {
    await go(imm.locator('input[type="button"][value="Configure"]').first(), 'configure');
    await go(imm.locator('input[value="Manage Immu. Template"]'), 'manage template');
    for (const id of [seededId, uiId]) await imm.locator(`input[name="chkSetId"][value="${id}"]`).check();
    await go(imm.locator('input[type="submit"][name="action"][value="Delete"]'), 'delete templates');
    await expectValue(sql, `SELECT GROUP_CONCAT(archived ORDER BY setId) FROM config_Immunization WHERE setId IN (${seededId},${uiId})`,
      '2,2', 'Delete did not archive the owned templates');
    assert(await imm.locator(`input[name="chkSetId"][value="${seededId}"]`).count() === 0, 'Deleted template is still in the active list');
    await go(imm.locator('input[type="button"][value="Deleted List"]'), 'deleted list');
    assert(await imm.locator(`input[name="chkSetId"][value="${seededId}"]`).count() === 1, 'Deleted list does not show the template');
    assert((await scheduledNames()).includes(uiName), 'Deleting the template changed the patient schedule copy');
  });

  await step('reopening Old immunizations goes straight to the saved schedule', async () => {
    await openSchedule();
    assert(path().endsWith('/encounter/immunization/initSchedule'), 'A patient with a schedule was sent elsewhere');
    assert(await imm.getByText(uiName).count() === 1 && await imm.locator('#tdSet0_Row0_Col1_label').innerText() !== '',
      'Saved schedule did not reopen with its recorded cell');
  });

  await step('cancelling schedule deletion leaves the saved schedule untouched', async () => {
    const before = sql.rows(`SELECT ID, archived, immunizations FROM immunizations WHERE demographic_no=${patient} ORDER BY ID`);
    const dialogs = await withExpectedDialogs(imm,
      () => imm.getByRole('link', {name: 'del', exact: true}).click(), {accept: false});
    assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask for confirmation');
    assert(JSON.stringify(sql.rows(`SELECT ID, archived, immunizations FROM immunizations WHERE demographic_no=${patient} ORDER BY ID`))
      === JSON.stringify(before), 'Cancelling deletion changed the schedule');
  });

  // Last, so every provable step above is proven first: each problem here is an application defect.
  await step('del/restore round-trips, the created template kept its name, and every stylesheet loaded', async () => {
    const problems = [];
    const dialogs = await withExpectedDialogs(imm, async () => {
      const navigated = await clickAndAwaitReload(imm, imm.getByRole('link', { name: 'del', exact: true }),
        { label: 'delete set', required: false });
      if (!navigated) problems.push('the schedule "del" link does nothing: its form is nested inside the save form, so the parser drops it and getElementById returns null');
    });
    assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask for confirmation exactly once');
    if (!problems.length) {
      await expectValue(sql, `SELECT immunizations LIKE '%status="deleted"%' ${current}`, '1', 'Delete did not mark the set deleted');
      assert(await imm.getByText(uiName).count() === 0, 'Deleted set is still listed');
      await go(imm.locator('input[type="button"][value="Show All"]').first(), 'show all');
      assert(await imm.getByText(uiName).count() === 1, 'Show All does not list the deleted set');
      const again = await withExpectedDialogs(imm, () => go(imm.getByRole('link', { name: 'restore', exact: true }), 'restore set'));
      assert(again.length === 1 && again[0].type === 'confirm', 'Restore did not ask for confirmation exactly once');
      await expectValue(sql, `SELECT immunizations LIKE '%status="deleted"%' ${current}`, '0', 'Restore did not clear the deleted status');
      assert(currentXml().includes(`lot="${lot}"`), 'Delete/restore lost the recorded cell');
    }
    if (sql.value(`SELECT setName FROM config_Immunization WHERE setId=${uiId}`) !== uiName) {
      problems.push('CreateImmunizationSetConfig dropped the set name typed on Add New (hidden setName vs action property name), so the template never appears in the picker');
    }
    if (deferred.length) {
      const pages = [...new Set(deferred.map(entry => entry.label))].join(', ');
      problems.push(`legacy immunization JSPs link stylesheets the webapp does not ship (${MISSING_STYLESHEETS.join(', ')}) on: ${pages}`);
    }
    assert(problems.length === 0, problems.join('; '));
  });
}

if (require.main === module) runWorkflow('immunization-schedule-config', workflow, { openPatient: true });
module.exports = { workflow };
