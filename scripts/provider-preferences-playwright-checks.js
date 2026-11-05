#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Disposable VM only: preserves and restores the test provider's affected settings.
const {assert, sqlString} = require('./lib/playwright-harness');
const {clickAndAwaitReload} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
async function workflow(s) {
  const {sql, provider, marker} = s;
  const prefix = ['appointment_receipt', 'pdf_envelope', 'pdf_label', 'pdf_address_label', 'pdf_chart_label', 'client_lab_label'];
  const keys = prefix.flatMap(key => [`default_printer_${key}`, `default_printer_${key}_silent_print`]);
  keys.push('document_description_template');
  const predicate = `provider_no=${sqlString(provider)} AND name IN (${keys.map(sqlString).join(',')})`;
  const snapshotQuery = `SELECT id,name,COALESCE(HEX(value),''),value IS NULL FROM property WHERE ${predicate} ORDER BY id`;
  const originalProperties = sql.rows(snapshotQuery);
  const signatureQuery = `SELECT COALESCE(HEX(signature),''),signature IS NULL FROM providerExt WHERE provider_no=${sqlString(provider)} ORDER BY signature IS NULL, HEX(signature)`;
  const originalSignature = sql.rows(signatureQuery);
  s.cleanup(() => {
    sql.execute(`DELETE FROM documentDescriptionTemplate WHERE provider_no=${sqlString(provider)} AND description LIKE ${sqlString(marker + '%')}`);
    assert(sql.value(`SELECT COUNT(*) FROM documentDescriptionTemplate WHERE provider_no=${sqlString(provider)} AND description LIKE ${sqlString(marker + '%')}`) === '0', 'Owned document templates were not removed');
    const statements = [`DELETE FROM property WHERE ${predicate}`];
    for (const [id, name, hex, isNull] of originalProperties) {
      assert(/^\d+$/.test(id) && /^[0-9a-f]*$/i.test(hex), 'Invalid preference restore snapshot');
      statements.push(`INSERT INTO property(id,provider_no,name,value) VALUES (${id},${sqlString(provider)},${sqlString(name)},${isNull === '1' ? 'NULL' : `UNHEX('${hex}')`})`);
    }
    statements.push(`DELETE FROM providerExt WHERE provider_no=${sqlString(provider)}`);
    for (const [hex, isNull] of originalSignature) {
      assert(/^[0-9a-f]*$/i.test(hex), 'Invalid signature restore snapshot');
      statements.push(`INSERT INTO providerExt(provider_no,signature) VALUES (${sqlString(provider)},${isNull === '1' ? 'NULL' : `UNHEX('${hex}')`})`);
    }
    sql.execute(`START TRANSACTION;${statements.join(';')};COMMIT`);
    assert(JSON.stringify(sql.rows(snapshotQuery)) === JSON.stringify(originalProperties), 'Printer/preferences restore did not match its snapshot');
    assert(JSON.stringify(sql.rows(signatureQuery)) === JSON.stringify(originalSignature), 'Signature restore did not match its snapshot');
  });
  const printerName = `${marker} O'Neil "A&B"`;
  sql.execute(`START TRANSACTION; DELETE FROM providerExt WHERE provider_no=${sqlString(provider)}; INSERT INTO providerExt(provider_no,signature) VALUES (${sqlString(provider)},NULL); COMMIT`);
  const existingLabel = sql.value(`SELECT id FROM property WHERE provider_no=${sqlString(provider)} AND name='default_printer_pdf_label'`);
  if (existingLabel) sql.execute(`UPDATE property SET value=${sqlString(printerName)} WHERE id=${existingLabel}`);
  else sql.execute(`INSERT INTO property(provider_no,name,value) VALUES (${sqlString(provider)},'default_printer_pdf_label',${sqlString(printerName)})`);
  sql.execute(`DELETE FROM property WHERE provider_no=${sqlString(provider)} AND name='default_printer_pdf_label_silent_print';
    INSERT INTO property(provider_no,name,value) VALUES (${sqlString(provider)},'default_printer_pdf_label_silent_print',NULL)`);
  const beforeOpening = JSON.stringify(sql.rows(snapshotQuery));
  const prefs = await s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), 'preferences');
  const openPreference = async (selector, label) => {
    const link = prefs.locator(selector);
    await revealAuditLink(prefs, link, 20000);
    return s.popup(prefs, link, label);
  };
  await s.step('opening printer settings preserves all saved values', async () => {
    const printer = await openPreference('a[href$="/EditPrinter"]', 'printer-preferences');
    assert(await printer.locator('[name="defaultPrinterNamePDFLabel"]').inputValue() === printerName,
      'Printer name did not round-trip or was overwritten on GET');
    assert(JSON.stringify(sql.rows(snapshotQuery)) === beforeOpening, 'Opening printer settings changed stored values');
    assert(!(await printer.locator('[name="silentPrintPDFLabel"]').isChecked()), 'A NULL silent-print setting was treated as enabled');
    const save = printer.locator('input[type="submit"]');
    if (await save.count()) {
      await printer.locator('[name="defaultPrinterNamePDFLabel"]').fill(`${printerName}-EDIT`);
      await printer.locator('[name="silentPrintPDFLabel"]').check();
      await clickAndAwaitReload(printer, save);
      assert(sql.value(`SELECT value FROM property WHERE provider_no=${sqlString(provider)} AND name='default_printer_pdf_label'`) === `${printerName}-EDIT`,
        'Explicit printer save did not persist');
      assert(sql.value(`SELECT value FROM property WHERE provider_no=${sqlString(provider)} AND name='default_printer_pdf_label_silent_print'`) === 'yes',
        'Explicit silent-print choice did not persist');
      console.log('  Printer editing enabled: explicit name and silent-print save passed');
    } else {
      assert(await printer.locator('[name="defaultPrinterNamePDFLabel"]').isDisabled(), 'Disabled printer feature offers unsaveable edits');
      console.log('  Printer feature disabled: read-only settings checked; explicit save requires new_label_print=true');
    }
    await printer.close();
  });
  await s.step('existing null text signature can be edited without duplicate keys or GET writes', async () => {
    let editor = await openPreference('a[href$="/provider/ViewEditSignature"]', 'text-signature');
    assert(await editor.locator('#signature').inputValue() === '', 'Null signature editor did not open');
    await editor.locator('#signature').fill(marker);
    await clickAndAwaitReload(editor, editor.locator('input[type="submit"]'));
    assert(sql.value(`SELECT COUNT(*) FROM providerExt WHERE provider_no=${sqlString(provider)} AND signature=${sqlString(marker)}`) === '1',
      'Signature save failed or created duplicates');
    await editor.close();
    editor = await openPreference('a[href$="/provider/ViewEditSignature"]', 'text-signature-reopen');
    assert(await editor.locator('#signature').inputValue() === marker, 'Opening the signature editor cleared the saved value');
    const action = await editor.locator('form').evaluate(form => form.action);
    const rejected = await s.context.request.get(action);
    assert(rejected.status() === 405, 'GET on signature save was not rejected');
    assert(sql.value(`SELECT signature FROM providerExt WHERE provider_no=${sqlString(provider)}`) === marker, 'Rejected GET changed the signature');
    await editor.close();
  });
  let templateEditor;
  await s.step('opening document description settings does not save preferences', async () => {
    const before = JSON.stringify(sql.rows(snapshotQuery));
    let writes = 0;
    const listener = req => { if (req.method() === 'POST' && /saveDocumentDescriptionTemplatePreference/.test(req.postData() || '')) writes++; };
    s.context.on('request', listener);
    try {
      const editor = await openPreference('a[href$="/admin/DisplayDocumentDescriptionTemplate"]', 'document-description');
      await editor.waitForLoadState('networkidle');
      assert(writes === 0 && JSON.stringify(sql.rows(snapshotQuery)) === before, 'Opening document description settings wrote a preference');
      templateEditor = editor;
    } finally { s.context.off('request', listener); }
  });
  await s.step('document descriptions create, edit, reopen and delete punctuation intact', async () => {
    const editor = templateEditor;
    if (await editor.locator('#useclinicdefault').isChecked()) {
      await Promise.all([
        editor.waitForResponse(r => r.url().includes('/DocumentDescriptionTemplate')
          && (r.request().postData() || '').includes('saveDocumentDescriptionTemplatePreference')),
        editor.locator('#useclinicdefault').uncheck(),
      ]);
    }
    await editor.locator('#docType').selectOption({index: 1});
    await editor.locator('#docDescList').waitFor();
    const description = `${marker} O'Neil "A&B+"`;
    await editor.locator('[name="docDescriptionShortcut"]').fill(marker.slice(-16));
    await editor.locator('[name="docDescription"]').fill(description);
    await editor.locator('#addDescription').click();
    const predicate = `provider_no=${sqlString(provider)} AND description=${sqlString(description)}`;
    await expectValue(sql, `SELECT COUNT(*) FROM documentDescriptionTemplate WHERE ${predicate}`, '1', 'Template create failed or duplicated');
    const id = sql.value(`SELECT id FROM documentDescriptionTemplate WHERE ${predicate}`);
    await editor.locator(`#docDescList option[value="${id}"]`).waitFor({state: 'attached'});
    await editor.locator('#docDescList').selectOption(id);
    await editor.waitForFunction(value => document.querySelector('[name="docDescription"]').value === value, description);
    await editor.locator('[name="docDescription"]').fill(description + '-EDIT');
    await editor.locator('#updateDescription').click();
    await expectValue(sql, `SELECT description FROM documentDescriptionTemplate WHERE id=${id}`, description + '-EDIT', 'Template update failed');
    await editor.reload();
    await editor.locator('#docType').selectOption({index: 1});
    await editor.locator(`#docDescList option[value="${id}"]`).waitFor({state: 'attached'});
    await editor.locator('#docDescList').selectOption(id);
    await editor.waitForFunction(value => document.querySelector('[name="docDescription"]').value === value, description + '-EDIT');
    await editor.locator('#deleteDescription').click();
    await expectValue(sql, `SELECT COUNT(*) FROM documentDescriptionTemplate WHERE id=${id}`, '0', 'Template delete failed');
    await editor.locator(`#docDescList option[value="${id}"]`).waitFor({state: 'detached'});
    assert(await editor.locator('#templateStatus').innerText() === '', 'Template workflow reported an error');
    await editor.close();
  });

}
if (require.main === module) runWorkflow('provider-preferences', workflow, {openPatient: false});
module.exports = {workflow};
