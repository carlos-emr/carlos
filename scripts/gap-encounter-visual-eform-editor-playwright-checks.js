#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Forms/eForms ▸ Visual eForm Editor: build an eForm, save it to the library, use it
 * in a chart, then update it.
 *
 * User path: Schedule ▸ Administration ▸ Forms/eForms ▸ "Visual eForm Editor" (a new window) ▸ Form
 * Building ▸ Text Box tab ▸ Prefilled Text ▸ drag the Text Box onto the page; tick the database checkbox
 * (opens the tag reference, eform/Eform_dbtags) ▸ choose patient_nameF ▸ drag a second Text Box ▸
 * Finalize ▸ eForm Name ▸ View Eform Source ▸ "Save As New Eform" (REST POST ws/rs/eform/json) ▸ E-Chart ▸
 * eForms "+" ▸ the new eForm ▸ type into the field ▸ toolbar Save ▸ back in the editor "Update Eform"
 * (REST PUT) after editing the box's text in place, and Download As File. The menu entry had a presence check
 * only (eform-admin); nothing ever built a form with it.
 * Asserts against MariaDB: the first save inserts one eform row with the chosen name, the logged-in
 * provider as creator, status 1 and markup carrying the generated text input and its default text; the
 * generated source shown before saving contains the same input; the tag reference lists patient_nameF and the
 * box bound to it opens in the chart with the owned patient's first name; the chart lists the new eForm, opens it
 * with the default text in the field and saving it stores the typed value under the field name in
 * eform_values; the second save UPDATES the same row (still one row for the name, new default text) and
 * the downloaded file is named after the eForm and holds the updated markup.
 * Fixtures: the owned patient and the eForm the editor creates (named with the run marker); cleanup
 * deletes its instances and the template by name and asserts them gone. Implements gap-encounter
 * "build an eForm in the visual editor".
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const formName = `${marker} Visual`;
  const firstDefault = `First${marker.slice(-8)}`;
  const secondDefault = `Second${marker.slice(-8)}`;
  const typed = `Typed${marker.slice(-8)}`;
  s.cleanup(() => {
    const fids = sql.rows(`SELECT fid FROM eform WHERE form_name=${q(formName)}`).map(r => r[0]);
    for (const fid of fids) {
      sql.execute(`DELETE FROM eform_values WHERE fid=${fid} AND demographic_no=${patient};
        DELETE FROM eform_data WHERE fid=${fid} AND demographic_no=${patient};
        DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)})`) === '0', 'The owned visual-editor eForm was not removed');
  });
  const rows = () => sql.rows(`SELECT fid, form_creator, status FROM eform WHERE form_name=${q(formName)}`);
  const html = fid => sql.value(`SELECT form_html FROM eform WHERE fid=${fid}`);

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'visual-editor-admin', timeout: 20000 });
  let editor;
  let fid;
  let fieldName;
  let boundField;
  const tagReferenceProblems = [];
  const alertText = async () => (await editor.locator('.ui-dialog:visible').first().innerText()).replace(/\s+/g, ' ');
  const closeAlert = async () => {
    await editor.locator('.ui-dialog:visible button, .ui-dialog:visible .ui-dialog-titlebar-close').first().click();
    await editor.locator('.ui-dialog:visible').waitFor({ state: 'hidden' }).catch(() => {});
  };

  await s.step('Administration ▸ Forms/eForms ▸ Visual eForm Editor opens the editor window', async () => {
    await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
    const link = admin.locator('a[onclick*="visualEformEditor"]').first();
    await revealAuditLink(admin, link, 20000);
    [editor] = await Promise.all([s.context.waitForEvent('page', { timeout: 20000 }), link.click()]);
    await editor.waitForLoadState('networkidle').catch(() => {});
    await h.assertNotErrorPage(editor, 'visual eForm editor');
    await editor.locator('#page_1').waitFor();
    h.assert(await editor.locator('#eformNameInput').count() === 1, 'The editor has no eForm name box');
  });

  await s.step('a Text Box with prefilled text, dragged onto the page, becomes a generated field', async () => {
    await editor.locator('h3:has-text("Form Building")').click();
    await editor.locator('a:has-text("Text Box")').first().click();
    // "Prefilled Text" belongs to the Text Box template: every box dragged out afterwards carries it.
    await editor.locator('#gen-textBoxDefaultTextId').fill(firstDefault);
    await editor.locator('#gen-textBoxDefaultTextId').blur();
    await editor.locator('#textBoxTemplate').dragTo(editor.locator('#page_1'), { targetPosition: { x: 120, y: 100 } });
    const widget = editor.locator('#page_1 .gen-widget').first();
    await widget.waitFor();
    fieldName = await widget.locator('input[type="text"]').getAttribute('name');
    h.assert(/^gen_inputId\d+$/.test(fieldName), 'The dropped text box has no generated field name');
    h.assert(await widget.locator('input[type="text"]').inputValue() === firstDefault, 'The dropped text box did not take the prefilled text');
  });

  await s.step('a Text Box bound to a patient database tag opens the tag reference and is dragged onto the page', async () => {
    // Ticking the database checkbox opens the tag reference window (eform/Eform_dbtags). That page loads
    // jQuery 3.6.4 from a path the webapp does not ship and, failing that, from code.jquery.com. The
    // external request is aborted here (nothing leaves this host); what the page reports about itself is
    // moved out of the strict recorder and asserted in the LAST step, so every other step is proven first.
    const externalHosts = new Set();
    const block = route => { externalHosts.add(new URL(route.request().url()).host); return route.abort('blockedbyclient'); };
    const isExternal = url => new URL(url).host === 'code.jquery.com';
    await s.context.route(isExternal, block);
    // Label the one window the checkbox opens, synchronously on the page event (after the harness's own wiring,
    // before its first script runs), so its errors can be told apart from every other page's below.
    let tagReferenceLabelled = false;
    const labelTagReference = page => {
      if (tagReferenceLabelled) return;
      tagReferenceLabelled = true;
      h.relabelStrictPage(page, 'tag-reference');
    };
    s.context.on('page', labelTagReference);
    try {
      const [tags] = await Promise.all([s.context.waitForEvent('page', { timeout: 20000 }), editor.locator('#toggleGOscarDbCheckbox').check()]);
      await tags.waitForLoadState('domcontentloaded');
      await tags.waitForURL(url => /Eform_dbtags/.test(String(url)), { timeout: 20000 });
      await tags.waitForLoadState('networkidle').catch(() => {});
      h.assert(/patient_nameF/.test(await tags.locator('body').innerText()), 'The database tag reference does not list the patient_nameF tag');
      await tags.close();
    } finally {
      s.context.off('page', labelTagReference);
      await s.context.unroute(isExternal, block).catch(() => {});
    }
    // Only what the tag-reference window itself raised about jQuery is moved out of the strict recorder: the
    // "$ is not defined" error counts only when it came from that window, so an unrelated one elsewhere still fails.
    const mine = entry => (entry.label === 'tag-reference' && /\$ is not defined/.test(entry.text || '')) || /jquery-3\.6\.4|code\.jquery\.com/.test(entry.url || '') || /jquery-3\.6\.4|code\.jquery\.com/.test(entry.text || '')
      || /jquery-3\.6\.4|code\.jquery\.com/.test((entry.location && entry.location.url) || '');
    for (const list of ['badResponses', 'consoleIssues', 'requestFailures', 'pageErrors']) {
      for (let i = s.recorder[list].length - 1; i >= 0; i--) {
        if (mine(s.recorder[list][i])) tagReferenceProblems.push(`${list}: ${(s.recorder[list][i].url || s.recorder[list][i].text || '').split('?')[0].slice(0, 120)}`);
        if (mine(s.recorder[list][i])) s.recorder[list].splice(i, 1);
      }
    }
    if (externalHosts.size) tagReferenceProblems.push(`requested ${[...externalHosts].join(', ')}`);
    // The tag list is a jQuery UI select menu: open it and pick the item.
    await editor.locator('#oscarDbTagSelect-button').click();
    await editor.locator('#oscarDbTagSelect-menu .ui-menu-item-wrapper').getByText('patient_nameF', { exact: true }).first().click();
    await editor.locator('#textBoxTemplate').dragTo(editor.locator('#page_1'), { targetPosition: { x: 120, y: 200 } });
    const bound = editor.locator('#page_1 .gen-widget').nth(1);
    await bound.waitFor();
    boundField = await bound.locator('input[type="text"]').getAttribute('name');
    h.assert(boundField && boundField !== fieldName, 'The second text box has no field name of its own');
    h.assert(await bound.locator('input[type="text"]').getAttribute('oscarDB') === 'patient_nameF', 'The dropped text box is not bound to the patient_nameF tag');
  });

  await s.step('Finalize: the eForm name and View Eform Source show the generated markup', async () => {
    await editor.locator('h3:has-text("Finalize")').click();
    await editor.locator('#eformNameInput').fill(formName);
    await editor.locator('#eformNameInput').blur();
    const [source] = await Promise.all([s.context.waitForEvent('page', { timeout: 20000 }).catch(() => null), editor.locator('#showSource').click()]);
    // The source opens as a Blob URL in a new window: wait for it to load before reading the body.
    if (source) await source.waitForLoadState('domcontentloaded');
    const text = source ? await source.locator('body').innerText().catch(() => '') : await editor.locator('.ui-dialog:visible, textarea:visible').first().innerText().catch(() => '');
    h.assert(text.includes(fieldName) && text.includes(firstDefault), 'The source view does not contain the generated field and its default text');
    h.assert(text.includes(formName), 'The source view does not carry the eForm name as its title');
    if (source) await source.close(); else await closeAlert().catch(() => {});
  });

  await s.step('Save As New Eform stores one eForm for the provider with the generated field', async () => {
    h.assert(rows().length === 0, 'An eForm with this name already exists');
    await editor.locator('#saveToOscarButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)}`, '1', 'Save As New Eform stored no eForm');
    const [row] = rows();
    fid = row[0];
    h.assert(row[2] === '1', 'The saved eForm is not active');
    h.assert(row[1] === provider, 'The saved eForm is not attributed to the signed-in provider');
    const markup = html(fid);
    h.assert(markup.includes(`name="${fieldName}"`) && markup.includes(firstDefault), 'The stored markup lost the generated field or its default text');
    h.assert(/Save Successful/i.test(await alertText()), 'The editor did not confirm the save');
    h.assert((await editor.locator('#saveToOscarButton').innerText()).trim() === 'Update Eform', 'The save button did not become Update Eform');
    await closeAlert();
  });

  await s.step('the new eForm is offered in the chart, opens with its default text and saves the typed value', async () => {
    const chart = await s.chart();
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'visual-eform-fill');
    const field = form.locator(`input[name="${fieldName}"]`);
    await field.waitFor();
    h.assert(await field.inputValue() === firstDefault, 'The generated field does not open with its default text');
    h.assert(await form.locator(`input[name="${boundField}"]`).inputValue() === 'Workflow',
      'The field bound to the patient_nameF tag does not open with the patient\'s first name');
    await field.fill(typed);
    await form.locator('#remote_eform_subject').fill(`${marker} visual subject`);
    await form.locator('#remoteSubmitButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid WHERE d.demographic_no=${patient}
      AND d.fid=${fid} AND v.var_name=${q(fieldName)} AND v.var_value=${q(typed)}`, '1', 'The value typed into the generated field was not stored');
  });

  await s.step('Update Eform changes the same row, and Download As File names the file after the eForm', async () => {
    await editor.bringToFront();
    await editor.locator('h3:has-text("Form Building")').click();
    // Double-click activates the dropped box so its own text can be edited in place.
    await editor.locator('#page_1 .gen-widget .inputOverride').first().dblclick();
    await editor.locator('#page_1 .gen-widget input[type="text"]').first().fill(secondDefault);
    await editor.locator('h3:has-text("Finalize")').click();
    await editor.locator('#saveToOscarButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)} AND form_html LIKE ${q(`%${secondDefault}%`)}`, '1',
      'Update Eform did not store the new default text');
    h.assert(rows().length === 1 && rows()[0][0] === fid, 'Update Eform created a second eForm instead of updating the first');
    h.assert(html(fid).includes(`value="${secondDefault}"`), 'The updated field does not carry the edited text as its value');
    await closeAlert();
    const [download] = await Promise.all([editor.waitForEvent('download', { timeout: 20000 }), editor.locator('#downloadSource').click()]);
    h.assert(/\.html$/.test(download.suggestedFilename()) && download.suggestedFilename().includes('Visual'),
      'The downloaded file is not named after the eForm');
    const body = fs.readFileSync(await download.path(), 'utf8');
    h.assert(body.includes(fieldName) && body.includes(secondDefault), 'The downloaded file does not hold the updated markup');
  });

  await s.step('the database tag reference loads cleanly and from this host only', async () => {
    h.assert(tagReferenceProblems.length === 0,
      `The tag reference window (eform/Eform_dbtags) loaded jQuery from a missing local file and then from another host: ${tagReferenceProblems.join(' | ')}`);
  });
}

if (require.main === module) runWorkflow('gap-encounter-visual-eform-editor', workflow, { openPatient: true });
module.exports = { workflow };
