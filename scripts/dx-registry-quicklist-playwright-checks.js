#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Disease Registry quick-list management, end to end.
// User path: Schedule ▸ Administration ▸ Customize Disease Registry Quick List
// (iframe) ▸ Add New Quick List / Edit Quick List popups ▸ Code Search ▸ Add >>
// / << Remove; then Master Record ▸ Create Invoice ▸ Next ▸ Dx Quick Pick ▸ Add
// To Disease Registry; then E-Chart ▸ Disease Registry ▸ quick-list sidebar.
// Asserts: quickList rows for exactly the codes added (and an unknown code is
// refused with a visible error and no row), removal deletes only the selected
// item, the reopened list round-trips, the billing-review quick pick writes an
// active dxresearch row for the owned patient and refreshes both fragments
// without saving a bill, the registry sidebar offers the owned list and its
// "add" link registers the code, and removing every item retires the list.
// Fixtures: an owned FAKE- patient and an owned quick list named FAKE-<hex>;
// cleanup deletes only that list's quickList/quickListUser rows and the owned
// patient's dxresearch rows, and asserts they are gone.
// Coverage plan: §2.5 dx-registry-quicklist.
const { assert, assertNotErrorPage, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const CODES = ['250', '401', '428'];

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  // quickListUser.quickListName is VARCHAR(10); keep the owned name within it.
  const name = `FAKE-${marker.slice(-5)}`;
  const listRows = () => sql.value(`SELECT GROUP_CONCAT(dxResearchCode ORDER BY dxResearchCode)
    FROM quickList WHERE quickListName=${sqlString(name)} AND codingSystem='icd9' AND createdByProvider=${sqlString(provider)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM quickList WHERE quickListName=${sqlString(name)};
      DELETE FROM quickListUser WHERE quickListName=${sqlString(name)};
      DELETE FROM dxresearch WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT (SELECT COUNT(*) FROM quickList WHERE quickListName=${sqlString(name)})
      + (SELECT COUNT(*) FROM quickListUser WHERE quickListName=${sqlString(name)})
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})`) === '0', 'Owned quick-list fixtures were not removed');
  });

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
    editor = chooser;
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
    const box = pick.locator('td', { has: bill.locator('b', { hasText: name }) }).locator('input[name="xml_research"][value="icd9,401"]');
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
    assert(await pick.locator('input[name="xml_research"][value="icd9,401"]').count() === 0, 'Quick pick still offers a registered code');
    assert(await pick.locator('input[name="xml_research"][value="icd9,250"]').count() === 1, 'Quick pick lost the unregistered code');
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
    const reloaded = registry.waitForEvent('framenavigated', { predicate: f => f === registry.mainFrame() });
    await sidebar.locator('select[name="quickList"]').selectOption(name);
    await reloaded;
    await registry.waitForLoadState('networkidle').catch(() => {});
    await assertNotErrorPage(registry, 'quick-list sidebar switch');
    assert(await registry.locator('#dxCodeQuicklist select[name="quickList"]').inputValue() === name, 'Sidebar did not keep the chosen quick list');
    const add = registry.locator('#dxCodeQuicklist a[title="250"]');
    await add.waitFor({ state: 'visible' });
    await clickAndAwaitReload(registry, add);
    await expectValue(sql, `SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient} AND dxresearch_code='250'
      AND coding_system='icd9' AND status='A'`, '1', 'Sidebar add did not register the quick-list code');
    await registry.locator('#displayDxCodeTable td', { hasText: 'DIABETES MELLITUS' }).first().waitFor({ state: 'visible' });
    await registry.close();
  });
}

if (require.main === module) runWorkflow('dx-registry-quicklist', workflow);
module.exports = { workflow };
