#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Master Record > Print / Labels > Generate Letters > Manage Letters. Separate
// scenarios test Delete, real Generate submission, and a stale template form.
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { preparePatientLetters } = require('./lib/patient-letter-ui-workflow');

async function workflow(s) {
  const mode = process.env.PATIENT_LETTER_ACTION || 'delete';
  h.assert(['delete', 'generate', 'stale', 'labels'].includes(mode), 'PATIENT_LETTER_ACTION must be generate, stale or labels');
  const f = await preparePatientLetters(s);
  const [target, control] = f.templates;
  if (mode === 'labels') {
    await s.step('Manage Letters displays readable field labels and action names without unresolved message keys', async () => {
      const buttons = await f.manager.locator('input[type="submit"]').evaluateAll(nodes => nodes.map(node => node.value));
      const text = `${await f.manager.title()}\n${await f.manager.locator('body').innerText()}\n${buttons.join('\n')}`;
      const unresolved = [...new Set(text.match(/\?\?\?report\.ManageLetters\.[^?]+\?\?\?/g) || [])];
      h.assert(unresolved.length === 0, `Manage Letters displays unresolved labels: ${unresolved.join(', ')}`);
    });
    return;
  }
  const states = () => s.sql.rows(`SELECT ID,archive FROM report_letters
    WHERE ID IN (${target.id},${control.id}) ORDER BY ID`);
  const page = await f.letters();
  const select = page.locator('select[name="reportLetter"]');
  await select.selectOption(target.id);
  const button = page.locator('#listDemographic input[type="submit"]');
  const isGeneration = response => response.request().method() === 'POST'
    && h.pathOnly(response.url()).endsWith('/report/GenerateLetters');
  if (mode === 'delete') {
    await s.step('the uploaded template download preserves its original XML bytes and filename', async () => {
      const produced = await ui.clickDownloadsOrOpens(f.manager,
        f.row(target).locator(`a[href*="reportID=${target.id}"]`), { context: s.context, recorder: s.recorder, label: 'letter-template-download' });
      h.assert(produced.kind === 'download', 'The template link did not deliver a download');
      h.assert(produced.download.suggestedFilename() === target.filename, 'The download lost the uploaded filename');
      h.assert(fs.readFileSync(await produced.download.path(), 'utf8') === target.xml, 'The template download changed its bytes');
    });
    await s.step('Delete archives only its template, removes the manager row and hides it from generation choices', async () => {
      await f.archive(target);
      h.assert(JSON.stringify(states()) === JSON.stringify([[target.id, '1'], [control.id, '0']]), 'Delete changed the wrong template or did not archive it');
      h.assert(await f.row(target).count() === 0 && await f.row(control).count() === 1, 'The manager lost the wrong template');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM report_letters WHERE ID=${target.id}
        AND HEX(report_file)=HEX(${h.sqlString(target.xml)})`) === '1', 'Archiving destroyed the retained template bytes');
      await page.reload({ waitUntil: 'domcontentloaded' });
      h.assert(await select.locator(`option[value="${target.id}"]`).count() === 0
        && await select.locator(`option[value="${control.id}"]`).count() === 1, 'The generation picker offers an archived template or lost its control');
    });
    return;
  }
  if (mode === 'stale') {
    await s.step('a template deleted after the generation form opens is visibly refused without clinical writes', async () => {
      await f.archive(target);
      h.assert(await select.inputValue() === target.id, 'The stale form no longer carries its originally selected template');
      const [response] = await Promise.all([page.waitForResponse(isGeneration),
        ui.clickAndAwaitReload(page, button, { label: 'Generate with stale template' })]);
      h.assert(response.status() === 400, `A stale template answered HTTP ${response.status()} instead of 400`);
      await page.locator('#letterGenerationFailed').waitFor({ state: 'visible' });
      h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM log_letters WHERE report_id=${target.id})
        +(SELECT COUNT(*) FROM document WHERE docdesc=${h.sqlString(`${target.id}-${target.name}`)})`) === '0',
        'Refused stale-template generation wrote clinical data');
      h.assert(await page.locator('input[name="demos"]').inputValue() === s.patient
        && await page.locator('input[name="demos"]').isChecked(), 'The refusal lost the selected patient');
      // This one application validation response is required by the test. Keep
      // all other HTTP and JavaScript failures visible to the strict recorder.
      s.recorder.badResponses = s.recorder.badResponses.filter(entry => !(entry.url === response.url()
        && entry.method === 'POST' && entry.status === 400));
      s.recorder.consoleIssues = s.recorder.consoleIssues.filter(entry => !(entry.location?.url === response.url()
        && /Failed to load resource: the server responded with a status of 400/.test(entry.text)));
      h.assert(JSON.stringify(states()) === JSON.stringify([[target.id, '1'], [control.id, '0']]), 'Stale generation changed template states');
    });
    return;
  }
  await s.step('Generate with no patient selected shows a validation alert and sends no request', async () => {
    await page.locator('input[name="demos"]').uncheck();
    let writes = 0;
    page.on('request', request => { if (request.method() === 'POST'
      && h.pathOnly(request.url()).endsWith('/report/GenerateLetters')) writes++; });
    const dialogs = await h.withExpectedDialogs(page, () => button.click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && /No patients selected/i.test(dialogs[0].text),
      'Empty selection did not show the specific validation alert');
    h.assert(writes === 0, 'Empty selection posted a generation request');
    await page.locator('input[name="demos"]').check();
  });
  await s.step('Generate Letters delivers the selected template PDF and files one patient document and generation log', async () => {
    // Chromium's inline PDF viewer substitutes its HTML shell for response.body().
    // Observe the raw response by forwarding this UI request exactly once, with
    // no retry or redirect, then pass its status, headers and bytes through unchanged.
    let raw;
    const routePattern = '**/report/GenerateLetters';
    await page.route(routePattern, async route => {
      if (route.request().method() !== 'POST') return route.continue();
      const forwarded = await route.fetch({ maxRedirects: 0, maxRetries: 0 });
      try {
        raw = await forwarded.body();
        await route.fulfill({ response: forwarded, body: raw });
      } finally { await forwarded.dispose(); }
    });
    let response;
    try {
      [response] = await Promise.all([page.waitForResponse(isGeneration), button.click()]);
    } finally { await page.unroute(routePattern); }
    h.assert(response.status() === 200 && /application\/pdf/.test(response.headers()['content-type']), 'Generate did not answer with a PDF');
    const form = new URLSearchParams(response.request().postData());
    h.assert(form.get('reportLetter') === target.id && JSON.stringify(form.getAll('demos')) === JSON.stringify([s.patient])
      && form.get('CSRF-TOKEN'), 'Generate submitted another patient/template or omitted CSRF protection');
    h.assert(Buffer.isBuffer(raw), 'The actual Generate POST response was not captured');
    const bytes = raw;
    const text = spawnSync('pdftotext', ['-', '-'], { input: bytes, timeout: 15000 });
    h.assert(bytes.subarray(0, 5).toString() === '%PDF-' && text.status === 0
      && text.stdout.toString().replace(/\s/g, '').includes(`${target.name} patient letter`.replace(/\s/g, '')),
      `The generated PDF does not contain the selected template text: ${JSON.stringify({ head: bytes.subarray(0, 5).toString(), status: text.status, text: text.stdout.toString() })}`);
    await expectValue(s.sql, `SELECT COUNT(*) FROM log_letters WHERE report_id=${target.id}
      AND log=${h.sqlString(s.patient)} AND provider_no=${h.sqlString(s.provider)}`, '1', 'Generate did not record exactly the selected recipient');
    const docs = s.sql.rows(`SELECT d.document_no,d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${s.patient} AND d.docdesc=${h.sqlString(`${target.id}-${target.name}`)}`);
    h.assert(docs.length === 1 && path.basename(docs[0][1]) === docs[0][1], 'Generate did not file exactly one owned patient letter');
    const stored = fs.readFileSync(path.join(f.store, docs[0][1]));
    const storedText = spawnSync('pdftotext', ['-', '-'], { input: stored, timeout: 15000 });
    h.assert(storedText.status === 0 && storedText.stdout.toString().replace(/\s/g, '').includes(target.name), 'The filed patient PDF differs from the generated template');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM log_letters WHERE report_id=${control.id}`) === '0', 'Generate used the unselected control template');
  });
}
const name = process.env.PATIENT_LETTER_ACTION === 'generate' ? 'patient-letter-generation-ui'
  : process.env.PATIENT_LETTER_ACTION === 'stale' ? 'patient-letter-stale-template'
    : process.env.PATIENT_LETTER_ACTION === 'labels' ? 'patient-letter-manager-labels' : 'patient-letter-template-delete';
if (require.main === module) runWorkflow(name, workflow);
module.exports = { workflow };
