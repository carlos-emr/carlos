#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Disease Registry quick-list management, end to end.
// User path: Schedule ▸ Administration ▸ Customize Disease Registry Quick List
// (iframe) ▸ Add New Quick List / Edit Quick List / Edit Associations popups ▸
// Code Search ▸ Add >> / << Remove; Master Record ▸ Create Invoice ▸ Next ▸ Dx
// Quick Pick ▸ Add To Disease Registry; E-Chart ▸ Disease Registry sidebar.
// Asserts quickList rows for exactly the codes added (an unknown code is refused
// visibly, writing nothing), removal of only the selected item, the reopened
// list, the billing-review quick pick writing an active dxresearch row and
// refreshing both fragments without saving a bill, retirement of an emptied
// list, the association list, the sidebar switching to a named list whose "add"
// registers the code, and a CSV association appended through the upload form.
// Fixtures: owned FAKE- patient, a UI-created list and a seeded list (FAKE-<hex>
// names, VARCHAR(10)-safe), associations whose code starts with the run marker;
// cleanup deletes only those rows and asserts they are gone.
// Coverage plan: §2.5 dx-registry-quicklist.
const { assert, assertNotErrorPage, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const CODES = ['250', '401', '428'];

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  // quickListUser.quickListName is VARCHAR(10); keep the owned names within it.
  const name = `FAKE-${marker.slice(-5)}`;
  const seeded = `FAKE-${marker.slice(-10, -5)}`;
  const names = `(${sqlString(name)},${sqlString(seeded)})`;
  const ownedCodes = `code LIKE ${sqlString(`${marker}%`)}`;
  const listRows = () => sql.value(`SELECT GROUP_CONCAT(dxResearchCode ORDER BY dxResearchCode)
    FROM quickList WHERE quickListName=${sqlString(name)} AND codingSystem='icd9' AND createdByProvider=${sqlString(provider)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM quickList WHERE quickListName IN ${names};
      DELETE FROM quickListUser WHERE quickListName IN ${names};
      DELETE FROM dxresearch WHERE demographic_no=${patient};
      DELETE FROM dx_associations WHERE ${ownedCodes}`);
    assert(sql.value(`SELECT (SELECT COUNT(*) FROM quickList WHERE quickListName IN ${names})
      + (SELECT COUNT(*) FROM quickListUser WHERE quickListName IN ${names})
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM dx_associations WHERE ${ownedCodes})`) === '0', 'Owned quick-list fixtures were not removed');
  });
  // Seeded, not UI-built: the sidebar step must not depend on the list the
  // lifecycle steps retire. The association row is clinic-wide data, owned by marker.
  sql.execute(`INSERT INTO quickList (quickListName, createdByProvider, dxResearchCode, codingSystem)
    VALUES (${sqlString(seeded)}, ${sqlString(provider)}, '250', 'icd9');
    INSERT INTO dx_associations (dx_codetype, dx_code, codetype, code) VALUES ('icd9', '250', 'icd10', ${sqlString(`${marker}S`)})`);

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'dx-quicklist-administration', timeout: 20000 });
  const link = admin.getByRole('link', { name: 'Customize Disease Registry Quick List', exact: true, includeHidden: true });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const frame = admin.frameLocator('#dynamic-content iframe');
  const addNew = frame.getByRole('button', { name: 'Add New Quick List', exact: true });
  await addNew.waitFor({ state: 'visible' });
  let editor;

  await s.step('Add New Quick List opens the owned list in the editor without writing rows', async () => {
    const created = await s.popup(admin, addNew, 'dx-quicklist-new');
    await created.locator('#quickListName').fill(name);
    await clickAndAwaitReload(created, created.getByRole('button', { name: 'Continue', exact: true }));
    editor = created;
    await editor.locator('select[name="quickListItems"]').waitFor({ state: 'visible' });
    assert((await editor.locator('h4.page-header-title').innerText()).includes(name), 'Editor does not name the new quick list');
    assert(await editor.locator('select[name="quickListItems"] option').count() === 0, 'A new quick list already has items');
    assert(listRows() === null, 'Opening a new quick list wrote quickList rows');
  });

  await s.step('code search prefills the exact codes and Add >> stores exactly those items', async () => {
    await editor.locator('[name="selectedCodingSystem"]').selectOption('icd9');
    for (const [i, code] of CODES.entries()) await editor.locator(`[name="xml_research${i + 1}"]`).fill(code);
    const search = await s.popup(editor, editor.getByRole('button', { name: 'Code Search', exact: true }), 'dx-quicklist-code-search');
    for (const code of CODES) {
      assert(await search.locator(`input[name="searchCodes"][value="${code}"]`).isChecked(), `Code search did not preselect exact code ${code}`);
    }
    // Only the exact matches stay selected (401 also matches 4010/4011/4019).
    const closed = search.waitForEvent('close');
    await search.locator('[name="confirm"]').click();
    await closed;
    for (const [i, code] of CODES.entries()) {
      assert(await editor.locator(`[name="xml_research${i + 1}"]`).inputValue() === code, `Code search did not return ${code} to the editor`);
    }
    await clickAndAwaitReload(editor, editor.getByRole('button', { name: 'Add >>', exact: true }));
    await expectValue(sql, `SELECT GROUP_CONCAT(dxResearchCode ORDER BY dxResearchCode) FROM quickList
      WHERE quickListName=${sqlString(name)} AND codingSystem='icd9' AND createdByProvider=${sqlString(provider)}`,
    CODES.join(','), 'Add >> did not store exactly the three selected codes for the owner');
    for (const code of CODES) {
      await editor.locator(`select[name="quickListItems"] option[value="icd9,${code}"]`).waitFor({ state: 'attached' });
    }
  });

  await s.step('an unknown code is refused with a visible error and writes nothing', async () => {
    await editor.locator('[name="xml_research1"]').fill('Z9Z9');
    for (const i of [2, 3, 4, 5]) await editor.locator(`[name="xml_research${i}"]`).fill('');
    await clickAndAwaitReload(editor, editor.getByRole('button', { name: 'Add >>', exact: true }));
    assert((await editor.locator('.action-errors').innerText()).includes('Z9Z9'), 'Unknown quick-list code produced no visible error');
    assert(listRows() === CODES.join(','), 'An unknown code changed the quick list');
  });

  await s.step('<< Remove deletes only the selected item', async () => {
    await editor.locator('select[name="quickListItems"]').selectOption('icd9,428');
    await clickAndAwaitReload(editor, editor.getByRole('button', { name: '<< Remove', exact: true }));
    await expectValue(sql, `SELECT GROUP_CONCAT(dxResearchCode ORDER BY dxResearchCode) FROM quickList
      WHERE quickListName=${sqlString(name)}`, '250,401', 'Remove did not delete exactly the selected item');
    assert(await editor.locator('select[name="quickListItems"] option[value="icd9,428"]').count() === 0, 'Removed item is still listed');
    await editor.close();
  });

  await s.step('Edit Quick List reopens the owned list with its remaining items', async () => {
    const chooser = await s.popup(admin, frame.getByRole('button', { name: 'Edit Quick List', exact: true }), 'dx-quicklist-edit');
    await chooser.locator('select[name="quickListName"]').selectOption(name);
    await clickAndAwaitReload(chooser, chooser.getByRole('button', { name: 'Continue', exact: true }));
    const values = await chooser.locator('select[name="quickListItems"] option').evaluateAll(o => o.map(x => x.value).sort());
    assert(values.join('|') === 'icd9,250|icd9,401', 'Reopened quick list does not show its stored items');
    await chooser.close();
  });

  await s.step('billing review Dx Quick Pick registers the owned code without saving a bill', async () => {
    const invoice = s.master.getByRole('link', { name: 'Create Invoice', exact: true }).first();
    const bill = await s.popup(s.master, invoice, 'dx-quicklist-bill-form');
    await bill.locator('select[name="xml_billtype"]').waitFor({ state: 'visible' });
    await bill.locator('a[onclick*="showHideLayers(\'Layer1\',\'\',\'show\')"]').first().click();
    await bill.locator('#Layer1').getByRole('link', { name: 'General Practice' }).first().click();
    await bill.locator('input[name="xml_A007A"]:visible').first().check();
    // Physician values are "<provider_no>|<ohip_no>"; prefer the login's own entry.
    const physicians = await bill.locator('select[name="xml_provider"] option').evaluateAll(o => o.map(x => x.value));
    const physician = physicians.find(v => v.split('|')[0] === provider) || physicians.find(v => /^-?\d+\|\d+$/.test(v));
    assert(physician, 'The bill form offers no billable physician');
    await bill.locator('select[name="xml_provider"]').selectOption(physician);
    await bill.locator('input[name="dxCode"]').fill('250');
    await bill.locator('input[name="dxCode"]').dispatchEvent('change');
    const asked = await withExpectedDialogs(bill, () => clickAndAwaitReload(bill,
      bill.locator('#titlesearch input[type="submit"][name="submit"]')), { accept: false });
    assert(asked.every(d => d.type === 'confirm'), 'Bill form Next raised an unexpected alert');
    const pick = bill.locator('#dxListing');
    const ours = pick.locator('td', { has: bill.locator('b', { hasText: name }) });
    const box = ours.locator('input[name="xml_research"][value="icd9,401"]');
    await box.waitFor({ state: 'visible' });
    await box.check();
    const refreshed = bill.waitForResponse(r => new URL(r.url()).pathname.endsWith('/oscarResearch/oscarDxResearch/ViewCurrentCodeList'));
    const confirmed = await withExpectedDialogs(bill, async () => {
      await bill.getByRole('button', { name: 'Add To Disease Registry', exact: true }).click();
      await refreshed;
    });
    assert(confirmed.length === 1 && confirmed[0].type === 'confirm', 'Quick pick did not ask before adding to the registry');
    await expectValue(sql, `SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient} AND dxresearch_code='401'
      AND coding_system='icd9' AND status='A' AND providerNo=${sqlString(provider)}`, '1', 'Quick pick did not register the code');
    await bill.locator('#dxFullListing li', { hasText: 'ESSENTIAL HYPERTENSION' }).waitFor({ state: 'visible' });
    assert(await ours.locator('input[name="xml_research"][value="icd9,401"]').count() === 0, 'Quick pick still offers a registered code');
    assert(await ours.locator('input[name="xml_research"][value="icd9,250"]').count() === 1, 'Quick pick lost the unregistered code');
    assert(sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`) === '0', 'The quick pick saved a bill');
    await bill.close();
  });

  await s.step('removing every item retires the owned list from the chooser', async () => {
    const chooser = await s.popup(admin, frame.getByRole('button', { name: 'Edit Quick List', exact: true }), 'dx-quicklist-retire');
    await chooser.locator('select[name="quickListName"]').selectOption(name);
    await clickAndAwaitReload(chooser, chooser.getByRole('button', { name: 'Continue', exact: true }));
    const items = chooser.locator('select[name="quickListItems"]');
    await items.selectOption(await items.locator('option').evaluateAll(o => o.map(x => x.value)));
    await clickAndAwaitReload(chooser, chooser.getByRole('button', { name: '<< Remove', exact: true }));
    await expectValue(sql, `SELECT COUNT(*) FROM quickList WHERE quickListName=${sqlString(name)}`, '0',
      'Removing every item left quick-list rows behind');
    await chooser.close();
    const again = await s.popup(admin, frame.getByRole('button', { name: 'Edit Quick List', exact: true }), 'dx-quicklist-retired');
    assert(await again.locator(`select[name="quickListName"] option[value="${name}"]`).count() === 0, 'A retired quick list is still offered');
    await again.close();
  });

  await s.step('registry sidebar switches to the owned list and its add link registers the code', async () => {
    const chart = await s.chart();
    const registry = await s.popup(chart, chart.locator('a[onclick*="setupDxResearch"]').first(), 'dx-quicklist-registry');
    const sidebar = registry.locator('#dxCodeQuicklist');
    const switched = registry.waitForResponse(r => r.request().isNavigationRequest()
      && new URL(r.url()).searchParams.get('quickList') === seeded);
    await sidebar.locator('select[name="quickList"]').selectOption(seeded);
    const response = await switched;
    assert(response.status() === 200, `Choosing a named quick list in the registry sidebar answered HTTP ${response.status()}`);
    await registry.waitForLoadState('networkidle').catch(() => {});
    await assertNotErrorPage(registry, 'quick-list sidebar switch');
    assert(await registry.locator('#dxCodeQuicklist select[name="quickList"]').inputValue() === seeded, 'Sidebar did not keep the chosen quick list');
    const add = registry.locator('#dxCodeQuicklist a[title="250"]');
    await add.waitFor({ state: 'visible' });
    await clickAndAwaitReload(registry, add);
    await expectValue(sql, `SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient} AND dxresearch_code='250'
      AND coding_system='icd9' AND status='A'`, '1', 'Sidebar add did not register the quick-list code');
    await registry.locator('#displayDxCodeTable td', { hasText: 'DIABETES MELLITUS' }).first().waitFor({ state: 'visible' });
    await registry.close();
  });
  await s.step('Edit Associations lists the owned mapping with its registry description', async () => {
    const associations = await s.popup(admin, frame.getByRole('button', { name: 'Edit Associations', exact: true }), 'dx-associations');
    const row = associations.locator('#associations tbody tr', { has: associations.locator('td', { hasText: `${marker}S` }) });
    await row.waitFor({ state: 'visible' });
    const cells = await row.locator('td').allInnerTexts();
    assert(cells[0] === 'icd10' && cells[3] === 'icd9' && cells[4] === '250' && cells[5].startsWith('DIABETES MELLITUS'),
      'The association list does not match the stored mapping');
    await associations.close();
  });
  await s.step('Edit Associations appends an uploaded CSV mapping and lists it', async () => {
    const owned = `SELECT COUNT(*) FROM dx_associations WHERE codetype='icd10' AND code=${sqlString(`${marker}U`)} AND dx_codetype='icd9' AND dx_code='250'`;
    const open = () => s.popup(admin, frame.getByRole('button', { name: 'Edit Associations', exact: true }), 'dx-associations');
    let associations = await open();
    await associations.locator('#associations thead').waitFor({ state: 'attached' });
    assert(await associations.locator('#associations td', { hasText: `${marker}U` }).count() === 0, 'Owned association exists before upload');
    await associations.locator('#file').setInputFiles({ name: 'associations.csv', mimeType: 'text/csv',
      buffer: Buffer.from(`Issue List Code Type,Issue List Code,Disease Registry Code Type,Disease Registry Code\r\nicd10,${marker}U,icd9,250\r\n`) });
    // Append, never Replace: the association table is clinic-wide.
    await associations.locator('#appendRadio').check();
    await clickAndAwaitReload(associations, associations.locator('input[type="submit"][name="submit"]'));
    await expectValue(sql, owned, '1', 'Appending the uploaded association did not store exactly the owned row');
    await associations.close();
    associations = await open();
    const row = associations.locator('#associations tbody tr', { has: associations.locator('td', { hasText: `${marker}U` }) });
    await row.waitFor({ state: 'visible' });
    assert((await row.innerText()).includes('DIABETES MELLITUS'), 'Listed association lost its registry description');
    await associations.close();
  });

}

if (require.main === module) runWorkflow('dx-registry-quicklist', workflow);
module.exports = { workflow };
