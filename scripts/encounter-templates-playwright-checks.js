#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Encounter templates, end to end (coverage plan §2.5 encounter-templates).
 *
 * User path: Schedule ▸ Administration ▸ eChart ▸ "Insert a Template" (loads
 * /admin/ProviderTemplate into the #dynamic-content iframe) ▸ Save / Edit / Delete,
 * then Master Record ▸ E-Chart ▸ "Template Search" (#enTemplate, the chart's own
 * autocompleter, which POSTs /encounter/InsertTemplate and appends the text to the
 * active note) ▸ Save.
 *
 * Asserted: the encountertemplate row after create and after edit (exact text with
 * punctuation, a quote pair, an ampersand, a literal backslash, <b> markup and a line
 * break; creator = the test provider), the Edit form showing the stored text, the chart
 * offering exactly the owned template and inserting its exact text, the casemgmt_note
 * row containing that text, and the Delete leaving no row.
 *
 * Fixtures and cleanup: one marker-named template (PW<hex>) and the notes written to
 * the run's own synthetic patient; both removed in cleanup and asserted gone. Nothing
 * shared is modified.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const NOTE_EDITOR = '#encMainDiv textarea[name="caseNote_note"]';

/** Admin ▸ eChart ▸ Insert a Template, inside the administration iframe. */
async function openTemplateAdmin(s) {
  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'template-administration', timeout: 20000});
  const link = admin.getByRole('link', {name: 'Insert a Template', exact: true, includeHidden: true}).first();
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The template administration iframe did not load');
  await frame.locator('form[name="template"] input[name="name"]').waitFor();
  // The page's buttons submit their form with form.submit(); wait for the frame
  // itself to navigate so the next assertion reads the re-rendered document.
  async function submit(action) {
    const navigated = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: 20000});
    navigated.catch(() => {});
    await action();
    await navigated;
    await frame.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
    await frame.locator('form[name="template"] input[name="name"]').waitFor();
  }
  return {admin, frame, submit};
}

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  // Alphanumeric on purpose: the chart keys its template menu by the
  // JavaScript-encoded name, so a name with "-" would not round-trip (see report).
  const name = 'PW' + marker.slice(-16);
  const text = `${marker} tmpl: "quoted" & <b>bold</b>; it's 50% back\\slash #1?\nSecond line of ${marker}`;
  const edited = `${text}\nThird line, edited (v2)`;
  const stored = value => `SELECT COUNT(*) FROM encountertemplate WHERE encountertemplate_name=${h.sqlString(name)}
    AND creator=${h.sqlString(provider)} AND BINARY REPLACE(encountertemplate_value, CHAR(13), '')=BINARY ${h.sqlString(value)}`;
  const count = () => sql.value(`SELECT COUNT(*) FROM encountertemplate WHERE encountertemplate_name=${h.sqlString(name)}`);
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})`) === '0', 'Owned chart note rows were not removed');
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM encountertemplate WHERE encountertemplate_name=${h.sqlString(name)}`);
    h.assert(count() === '0', 'Owned encounter template was not removed');
  });
  h.assert(count() === '0', 'A template with the owned name already exists');

  const {frame, submit} = await openTemplateAdmin(s);
  const nameField = frame.locator('form[name="template"] input[name="name"]');
  const valueField = frame.locator('form[name="template"] textarea[name="value"]');
  const option = () => frame.locator('form[name="edittemplate"] select[name="name"] option').filter({hasText: name});

  await s.step('Administration saves a template with punctuation, a backslash, markup and a line break exactly', async () => {
    await nameField.fill(name);
    await valueField.fill(text);
    await submit(() => frame.getByRole('button', {name: 'Save', exact: true}).click());
    h.assert(await frame.locator('#template-result').isVisible()
      && (await frame.locator('#template-result').innerText()).trim() === 'Template saved.',
    'Saving the template did not show its result');
    h.assert(await option().count() === 1, 'The saved template is not offered in the Edit selector');
    h.assert(sql.value(stored(text)) === '1', 'The template text was not stored exactly as typed');
  });

  await s.step('Edit loads the stored text and Save updates it without duplicating the row', async () => {
    await frame.locator('form[name="edittemplate"] select[name="name"]').selectOption(name);
    await submit(() => frame.getByRole('button', {name: 'Edit', exact: true}).click());
    h.assert(await nameField.inputValue() === name, 'Edit did not load the template name');
    h.assert((await valueField.inputValue()).replace(/\r\n?/g, '\n') === text, 'Edit did not load the exact stored template text');
    await valueField.fill(edited);
    await submit(() => frame.getByRole('button', {name: 'Save', exact: true}).click());
    h.assert(sql.value(stored(edited)) === '1', 'Editing the template did not update its text');
    h.assert(count() === '1', 'Editing the template created a second row');
  });

  const chart = await s.chart();
  const editor = chart.locator(NOTE_EDITOR).first();
  await s.step('the chart template search offers the template and inserts its exact text into the note', async () => {
    await editor.waitFor({state: 'visible'});
    await editor.click();
    const before = await editor.inputValue();
    const search = chart.locator('#enTemplate');
    await search.click();
    await search.pressSequentially(name.slice(0, 8), {delay: 40});
    const suggestions = chart.locator('#enTemplate_list li');
    await suggestions.first().waitFor({state: 'visible'});
    const match = suggestions.filter({hasText: name});
    h.assert(await match.count() === 1 && (await match.first().innerText()).trim() === name,
      'The template menu did not offer exactly the owned template');
    const [response] = await Promise.all([
      chart.waitForResponse(r => r.request().method() === 'POST' && /\/encounter\/InsertTemplate(\?|$)/.test(r.url()), {timeout: 20000}),
      match.first().click(),
    ]);
    h.assert(response.status() === 200, `Template insertion returned HTTP ${response.status()}`);
    await chart.waitForFunction(({selector, needle}) => {
      const element = document.querySelector(selector);
      return Boolean(element && element.value.includes(needle));
    }, {selector: NOTE_EDITOR, needle: edited}, {timeout: 20000});
    const after = await editor.inputValue();
    h.assert(after.startsWith(before) && after.slice(before.length).replace(/\r/g, '').includes(edited),
      'Insertion did not append the exact template text after the existing note text');
  });

  await s.step('Save stores the note with the inserted template text exactly', async () => {
    const [response] = await Promise.all([
      chart.waitForResponse(r => r.request().method() === 'POST' && /\/CaseManagementEntry/.test(r.url())
        && new URLSearchParams(r.request().postData() || '').get('method') === 'save', {timeout: 30000}),
      chart.locator('#saveImg').first().click(),
    ]);
    h.assert(response.status() < 400, `Save returned HTTP ${response.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND provider_no=${h.sqlString(provider)} AND LOCATE(BINARY ${h.sqlString(edited)}, REPLACE(note, CHAR(13), ''))>0`,
    '1', 'The saved note does not contain the inserted template text exactly');
  });

  await s.step('GET and HEAD cannot delete the owned template', async () => {
    for (const method of ['GET', 'HEAD']) {
      const response = await s.context.request.fetch(h.appUrl(s.config.baseUrl, '/admin/ProviderTemplate'), {
        method, params: {dboperation: 'Delete', name}, maxRedirects: 0,
      });
      h.assert(response.status() < 500, `${method} deletion probe returned HTTP ${response.status()}`);
      h.assert(count() === '1', `${method} deleted the template`);
    }
  });

  await s.step('Delete shows confirmation and removes the template from Administration and the database', async () => {
    await frame.locator('form[name="edittemplate"] select[name="name"]').selectOption(name);
    await submit(() => frame.getByRole('button', {name: 'Edit', exact: true}).click());
    h.assert(await nameField.inputValue() === name, 'Edit did not load the template before deletion');
    await submit(() => frame.getByRole('button', {name: 'Delete', exact: true}).click());
    h.assert(await frame.locator('#template-result').isVisible()
      && (await frame.locator('#template-result').innerText()).trim() === 'Template deleted.',
    'Deleting the template did not show confirmation');
    h.assert(await option().count() === 0, 'The deleted template is still offered in the Edit selector');
    h.assert(count() === '0', 'Delete left the template row in place');
    // Reopen with GET so this also verifies a fresh selector without replaying Delete.
    await frame.goto(h.appUrl(s.config.baseUrl, '/admin/ProviderTemplate'), {waitUntil: 'domcontentloaded'});
    await frame.locator('form[name="edittemplate"] select[name="name"]').waitFor();
    h.assert(await option().count() === 0, 'Reopening Administration offered the deleted template again');
  });
}
if (require.main === module) runWorkflow('encounter-templates', workflow, {openPatient: true});
module.exports = {workflow, openTemplateAdmin};
