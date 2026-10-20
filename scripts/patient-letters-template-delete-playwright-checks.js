#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// patient-letters-template-delete: deleting a letter template in Manage Letters (report/DeleteLetter). The page lists
// the active templates (report_letters rows with archive '0'), each with a Download link and a Delete button. Delete
// archives the template (archive '1'; the row and its file are kept), and the page then lists the others.
// patient-letters-envelopes uploads and downloads a template, and patient-letters-entry reaches the page; neither
// deletes one (they remove their own rows with SQL).
// User path: Schedule ▸ Search ▸ Master Record ▸ Print / Labels ▸ Generate Letters ▸ Manage Letters ▸ Delete.
//   1. Manage Letters lists both owned templates, each with its Download link and Delete button;
//   2. Delete on the first reloads the page without it and keeps its row, archived; the second stays listed;
//   3. Generate Letters' letter choice offers the second template and not the first;
//   4. LAST: Manage Letters shows its title, labels, headings and buttons, not raw ???message keys???.
// Fixtures: two report_letters rows inserted with the run marker in their names, owned by the test provider, each
// with a tiny FAKE file. Cleanup deletes both rows by id and asserts none remain. Needs the _report read right.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openPrintMenu } = require('./demographic-labels-playwright-checks');

const q = h.sqlString;
const TIMEOUT = 20000;
const LETTER_ENTRY = 'a[href*="/report/ViewGenerateLetters?"]';

async function workflow(s) {
  const { sql, marker, provider } = s;
  const ids = [];
  s.cleanup(() => {
    if (!ids.length) return;
    sql.execute(`DELETE FROM report_letters WHERE ID IN (${ids.join(',')})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM report_letters WHERE ID IN (${ids.join(',')})`) === '0',
      'The owned letter templates were not removed');
  });
  /** Inserts one active letter template named with the run marker; returns its id and name. */
  const insert = label => {
    const name = `${marker} FAKE letter ${label}`;
    const id = sql.value(`INSERT INTO report_letters(provider_no,report_name,file_name,report_file,date_time,archive)
      VALUES(${q(provider)},${q(name)},${q(`FAKE-letter-${label}.jrxml`)},${q('<jasperReport/>')},NOW(),'0');
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `Letter template ${label} was not inserted`);
    ids.push(Number(id));
    return { id, name };
  };
  const first = insert('A');
  const second = insert('B');
  const archive = template => sql.value(`SELECT archive FROM report_letters WHERE ID=${Number(template.id)}`);

  /** Opens Generate Letters for the owned patient from the Master Record's Print / Labels menu. */
  const openLetters = async label => {
    const menu = await openPrintMenu(s.master, TIMEOUT);
    return s.popup(s.master, menu.locator(LETTER_ENTRY).first(), label);
  };
  const letters = await openLetters('patient letters');
  await ui.clickAndAwaitReload(letters, letters.locator('a[href$="/report/ViewManageLetters"]'), { label: 'Manage Letters' });
  await h.assertNotErrorPage(letters, 'Manage Letters');
  const rowOn = (page, template) => page.locator('tbody tr').filter({ hasText: template.name });
  const deleteButtonOn = (page, template) =>
    rowOn(page, template).locator('form[action$="/report/DeleteLetter"] button[type="submit"]');
  const row = template => rowOn(letters, template);
  const deleteButton = template => deleteButtonOn(letters, template);

  await s.step('Manage Letters lists both owned templates, each with its Download link and Delete button', async () => {
    for (const template of [first, second]) {
      h.assert(await row(template).count() === 1, `Manage Letters lists ${await row(template).count()} rows for ${template.name}`);
      h.assert(await row(template).locator(`a[href*="/report/DownloadLetter?reportID=${template.id}"]`).count() === 1,
        `${template.name} has no Download link`);
      h.assert(await deleteButton(template).count() === 1, `${template.name} has no Delete button`);
    }
  });

  await s.step('Delete on the first reloads the page without it and keeps its row, archived; the second stays listed', async () => {
    // The response arrives before the new page commits, so wait for the reload itself, not for a load state the old
    // page already has.
    const responded = letters.waitForResponse(r => r.request().method() === 'POST'
      && h.pathOnly(r.url()).endsWith('/report/DeleteLetter'), { timeout: TIMEOUT });
    await ui.clickAndAwaitReload(letters, deleteButton(first), { label: 'Delete' });
    const response = await responded;
    h.assert(response.status() === 200, `Delete answered HTTP ${response.status()}`);
    await h.assertNotErrorPage(letters, 'Manage Letters after Delete');
    h.assert(await row(first).count() === 0, 'The deleted template is still listed');
    h.assert(await row(second).count() === 1, 'The other template is no longer listed');
    h.assert(archive(first) === '1', `The deleted template's row reads archive "${archive(first)}", not "1"`);
    h.assert(archive(second) === '0', `The other template's row reads archive "${archive(second)}", not "0"`);
  });

  await s.step('Generate Letters\' letter choice offers the second template and not the first', async () => {
    await letters.close();
    const again = await openLetters('patient letters again');
    await h.assertNotErrorPage(again, 'Generate Letters');
    const offered = await again.locator('select[name="reportLetter"] option').evaluateAll(options =>
      options.map(option => ({ value: option.value, text: option.textContent.trim() })));
    h.assert(offered.some(option => option.value === String(second.id) && option.text === second.name),
      `Generate Letters does not offer ${second.name}: ${JSON.stringify(offered.filter(o => o.text.includes(marker)))}`);
    h.assert(!offered.some(option => option.value === String(first.id)),
      `Generate Letters still offers the deleted template ${first.name}`);
    await again.close();
  });

  // Last: it reads the page's text only, so a failure here leaves the steps above standing.
  await s.step('Manage Letters shows its title, labels, headings and buttons, not message keys', async () => {
    const generate = await openLetters('patient letters for the labels');
    await ui.clickAndAwaitReload(generate, generate.locator('a[href$="/report/ViewManageLetters"]'), { label: 'Manage Letters' });
    await h.assertNotErrorPage(generate, 'Manage Letters');
    // Button inputs (Upload) carry their label in value, which innerText leaves out.
    const buttonValues = await generate.locator('input[type="submit"], input[type="button"]').evaluateAll(inputs =>
      inputs.map(input => input.value).join(' '));
    const text = `${await generate.title()} ${await generate.locator('body').innerText()} ${buttonValues}`;
    const keys = [...new Set(text.match(/\?\?\?[^?\s]+\?\?\?/g) || [])];
    h.assert(keys.length === 0, `Manage Letters shows ${keys.length} message key(s) instead of text: ${keys.join(', ')}`);
    h.assert(await generate.title() === 'Manage Letters', `Manage Letters is titled "${await generate.title()}"`);
    h.assert(await deleteButtonOn(generate, second).innerText() === 'Delete',
      `The Delete button reads "${await deleteButtonOn(generate, second).innerText()}"`);
    await generate.close();
  });
}

if (require.main === module) runWorkflow('patient-letters-template-delete', workflow, { openPatient: true });
module.exports = { workflow };
