#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rourke 2009 export (gap-records / coverage plan §2.4 data export).
 * User path: Schedule ▸ Administration ▸ Data Management ▸ Demographic Export (admin #myFrame
 * iframe) ▸ "Rourke 2009 Export" link (demographic/eRourkeExport) ▸ vendor/contact form ▸ patient
 * set ▸ Run Report ▸ the "previous reports" table ▸ the zip's file link.
 * Asserts: the form is offered the owned patient set by name; Run Report writes exactly one
 * dataExport row carrying the typed contact details and a zip in DOCUMENT_DIR whose XML names the
 * owned patient (and only the owned patient's Rourke form); the previous-reports table lists the run
 * and renders the typed contact user name as TEXT (markup must not be interpreted); last, the
 * file link downloads the zip (it points at eRourkeExport?method=getFile, a request parameter the
 * action must dispatch to the recorded export download rather than re-rendering the form).
 * Fixtures: the runWorkflow FAKE- patient, one formRourke2009 row (marker in c_pName), one
 * marker-named patient set containing quote/markup characters (set_name is varchar(20)); the run's dataExport row and
 * its zip are found by the marker contact user. Cleanup deletes exactly those and asserts them gone.
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 30000;

/** Contents of the single XML member of a zip, via python3 (no unzip on the packaged image). */
function zipText(file) {
  return execFileSync('python3', ['-c',
    'import sys,zipfile;z=zipfile.ZipFile(sys.argv[1]);print("\\n".join(z.namelist()));'
    + 'print("\\x00");print(z.read(z.namelist()[0]).decode("utf-8","replace"))', file], { encoding: 'utf8' });
}

async function workflow(s) {
  const { sql, patient, marker, context, recorder } = s;
  const setName = `P${marker.slice(-8)}"<b>s</b>`;
  const contactUser = `${marker}<b>u</b>`;
  const docDir = process.env.DOCUMENT_DIR;
  if (!docDir) throw new h.SkipCheck('DOCUMENT_DIR is not set: the export zip location is unknown');
  const exportRows = `dataExport WHERE contactLName=${h.sqlString(marker)}`;
  s.cleanup(() => {
    for (const [name] of sql.rows(`SELECT file FROM ${exportRows}`)) {
      const file = path.join(docDir, path.basename(name));
      h.assert(/^rourke2009_export-[\d.-]+\.zip$/.test(path.basename(name)), 'Unexpected export file name in an owned row');
      fs.rmSync(file, { force: true });
      h.assert(!fs.existsSync(file), 'The export zip was not removed');
    }
    sql.execute(`DELETE FROM ${exportRows}`);
    sql.execute(`DELETE FROM demographicSets WHERE set_name=${h.sqlString(setName)}`);
    sql.execute(`DELETE FROM formRourke2009 WHERE demographic_no=${patient} AND c_pName=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ${exportRows})
      +(SELECT COUNT(*) FROM demographicSets WHERE set_name=${h.sqlString(setName)})
      +(SELECT COUNT(*) FROM formRourke2009 WHERE demographic_no=${patient} AND c_pName=${h.sqlString(marker)})`) === '0',
    'Rourke export fixtures were not removed');
  });

  await s.step('fixtures: one Rourke 2009 form and a patient set holding only the owned patient', async () => {
    const id = sql.value(`INSERT INTO formRourke2009 (demographic_no, provider_no, formCreated, c_pName, c_birthDate)
      VALUES (${patient}, ${h.sqlString(s.provider)}, CURDATE(), ${h.sqlString(marker)}, '2025-01-15'); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Rourke form fixture not created');
    // The entity maps every checkbox column to a primitive byte: a row saved by the form has 0/1 in all
    // of them, a bare INSERT leaves NULL and the export's DAO cannot even hydrate it.
    const flags = sql.rows(`SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='formRourke2009' AND column_type LIKE 'tinyint%'`).map(row => `\`${row[0]}\`=0`);
    sql.execute(`UPDATE formRourke2009 SET ${flags.join(',')} WHERE ID=${Number(id)} AND c_pName=${h.sqlString(marker)}`);
    sql.execute(`INSERT INTO demographicSets (demographic_no, set_name, archive) VALUES (${patient}, ${h.sqlString(setName)}, '0')`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicSets WHERE set_name=${h.sqlString(setName)}`) === '1', 'Set fixture not created');
  });

  let exporter;
  await s.step('Administration ▸ Demographic Export ▸ Rourke 2009 Export opens the vendor form offering the set', async () => {
    const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context, recorder, label: 'rourke-administration', timeout: TIMEOUT });
    const link = admin.getByRole('link', { name: 'Demographic Export', exact: true, includeHidden: true });
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor();
    exporter = await (await iframe.elementHandle()).contentFrame();
    h.assert(exporter, 'The demographic export iframe did not load');
    const rourke = exporter.getByRole('link', { name: /Rourke/i });
    await rourke.first().waitFor({ timeout: TIMEOUT });
    await rourke.first().click();
    // The export landing page also has #patientSet; wait for the Rourke form itself.
    await exporter.locator('input[name="contactUserName"]').waitFor({ timeout: TIMEOUT });
    await h.assertNotErrorPage(exporter, 'rourke export form');
    const named = exporter.locator('select#patientSet option').filter({ hasText: setName });
    h.assert(await named.count() === 1, 'The Rourke export form does not offer the owned patient set by name');
  });

  let zipName;
  await s.step('Run Report stores the export row, the zip and its XML for exactly the owned patient', async () => {
    await exporter.locator('input[name="contactLName"]').fill(marker);
    await exporter.locator('input[name="contactFName"]').fill('Export');
    await exporter.locator('input[name="contactPhone"]').fill('555-0143');
    await exporter.locator('input[name="contactEmail"]').fill('pw-export@example.invalid');
    await exporter.locator('input[name="contactUserName"]').fill(contactUser);
    await exporter.locator('select#patientSet').selectOption(setName);
    await exporter.locator('input[type="submit"]').first().click();
    await expectValue(sql, `SELECT COUNT(*) FROM ${exportRows}`, '1', 'Run Report did not record exactly one export run');
    const [row] = sql.rows(`SELECT file, user, type, contactFName, contactPhone, contactEmail FROM ${exportRows}`);
    zipName = row[0];
    h.assert(row[1] === contactUser && row[3] === 'Export' && row[4] === '555-0143' && row[5] === 'pw-export@example.invalid',
      'The export row does not carry the contact details typed into the form');
    h.assert(/^rourke2009_export-[\d.-]+\.zip$/.test(zipName), 'The export row names an unexpected file');
    const file = path.join(docDir, zipName);
    h.assert(fs.existsSync(file), 'The export zip is missing from DOCUMENT_DIR');
    const [names, xml] = zipText(file).split('\n\x00\n');
    h.assert(/Rourke2009Export\.xml/.test(names), 'The zip does not hold the Rourke XML');
    h.assert((xml.match(/<cds:patient\b/g) || []).length === 1, 'The XML must hold exactly the one owned Rourke form');
    h.assert(xml.includes(`<cds:NAME>${marker}, Workflow</cds:NAME>`), 'The XML does not name the owned patient');
  });

  await s.step('the previous-reports table downloads the zip and shows the contact user name as text', async () => {
    const problems = [];
    await exporter.locator('a', { hasText: zipName }).first().waitFor({ timeout: TIMEOUT });
    const cell = exporter.locator('td', { hasText: marker }).first();
    h.assert(await cell.count() === 1, 'The previous-reports table does not list the run by its contact user');
    // Interpreted markup would leave a <b> element inside the cell and drop the literal tags from its text.
    const text = (await cell.innerText()).trim();
    if (text !== contactUser || await cell.locator('b').count() !== 0) {
      problems.push('the contact user name is interpreted as HTML instead of shown as text (stored markup in the table)');
    }
    const link = exporter.locator('a', { hasText: zipName }).first();
    const outcome = await ui.clickDownloadsOrOpens(exporter.page(), link,
      { context, recorder, label: 'rourke-zip', timeout: 15000 }).catch(error => ({ error }));
    if (!outcome || outcome.kind !== 'download') {
      problems.push('the file link sends no zip (it points at eRourkeExport?method=getFile, a request parameter the action never dispatches on, so the form is shown again)');
    } else {
      const saved = await outcome.download.path();
      if (!fs.readFileSync(saved).equals(fs.readFileSync(path.join(docDir, zipName)))) problems.push('the downloaded zip differs from the stored export');
    }
    h.assert(problems.length === 0, `Previous reports: ${problems.join('; ')}`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-rourke-export', workflow, { openPatient: true, openMaster: false });
