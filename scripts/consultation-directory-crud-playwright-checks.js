#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §3.3: institution and department CRUD through the consultation
// configuration menu, including cancelled deletes and isolation of unselected rows.
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, marker } = s;
  const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations' });
  const page = await s.popup(list, list.locator('a[href*="ViewShowAllServices"]'), 'consultation-directory');
  const definitions = [
    { table: 'Institution', add: 'ViewAddInstitution', edit: 'ViewEditInstitutions', checkbox: 'institutions', name: '#inst-name' },
    { table: 'Department', add: 'ViewAddDepartment', edit: 'ViewEditDepartments', checkbox: 'specialists', name: '#name' },
  ];
  for (const def of definitions) {
    const name = `${marker}-${def.table}`;
    let id;
    let sentinel;
    const ownedNames = `${sqlString(name)},${sqlString(`${name}-EDIT`)},${sqlString(`${name}-KEEP`)}`;
    s.cleanup(() => {
      sql.execute(`DELETE FROM ${def.table} WHERE name IN (${ownedNames})`);
      assert(sql.value(`SELECT COUNT(*) FROM ${def.table} WHERE name IN (${ownedNames})`) === '0',
        `Owned ${def.table} fixtures were not removed`);
    });
    // Unselected control row proves a bulk delete respects its checkbox selection.
    sentinel = sql.value(`INSERT INTO ${def.table}(name) VALUES(${sqlString(`${name}-KEEP`)}); SELECT LAST_INSERT_ID()`);
    const navigate = suffix => clickAndAwaitReload(page, page.locator(`a[href$="/${suffix}"]`));
    const saved = () => `SELECT name FROM ${def.table} WHERE id=${id}`;
    await s.step(`${def.table}: create and find in the directory`, async () => {
      await navigate(def.add);
      await page.locator(def.name).fill(name);
      if (def.table === 'Institution') {
        await page.locator('#address').fill('123 Synthetic Street');
        await page.locator('#city').fill('Test City');
        await page.locator('#province').fill('ON');
        await page.locator('#phone').fill('555-0142');
        await page.locator('#email').fill('directory@example.invalid');
      }
      await clickAndAwaitReload(page, page.locator('input[type="submit"]'));
      id = sql.value(`SELECT id FROM ${def.table} WHERE name=${sqlString(name)}`);
      assert(/^[1-9]\d*$/.test(id), `${def.table} create did not persist`);
      await navigate(def.edit);
      await page.getByRole('link', { name, exact: true }).waitFor({ state: 'visible' });
    });
    await s.step(`${def.table}: reopen and update persist`, async () => {
      await clickAndAwaitReload(page, page.getByRole('link', { name, exact: true }));
      assert(await page.locator(def.name).inputValue() === name, `${def.table} edit lost its name`);
      if (def.table === 'Institution') {
        assert(await page.locator('#phone').inputValue() === '555-0142', 'Institution phone was lost');
        await page.locator('#phone').fill('555-0143');
      }
      await page.locator(def.name).fill(`${name}-EDIT`);
      await clickAndAwaitReload(page, page.locator('input[type="submit"]'));
      await expectValue(sql, saved(), `${name}-EDIT`, `${def.table} edit did not persist`);
      if (def.table === 'Institution') assert(sql.value(`SELECT phone FROM Institution WHERE id=${id}`) === '555-0143', 'Institution phone edit was lost');
      await navigate(def.edit);
      await page.getByRole('link', { name: `${name}-EDIT`, exact: true }).waitFor({ state: 'visible' });
    });
    await s.step(`${def.table}: cancel preserves; delete affects only the checked row`, async () => {
      await page.locator(`input[name="${def.checkbox}"][value="${id}"]`).check();
      const remove = page.locator('input[name="delete"]');
      const cancel = await withExpectedDialogs(page, () => remove.click(), { accept: false });
      assert(cancel.length === 1 && cancel[0].type === 'confirm', `${def.table} delete omitted confirmation`);
      assert(sql.value(saved()) === `${name}-EDIT`, `Cancelled ${def.table} delete changed the row`);
      const accept = await withExpectedDialogs(page, () => clickAndAwaitReload(page, remove));
      assert(accept.length === 1 && accept[0].type === 'confirm', `${def.table} delete bypassed confirmation`);
      await expectValue(sql, `SELECT COUNT(*) FROM ${def.table} WHERE id=${id}`, '0', `${def.table} delete did not persist`);
      assert(sql.value(`SELECT name FROM ${def.table} WHERE id=${sentinel}`) === `${name}-KEEP`, 'Bulk delete changed an unselected row');
      assert(await page.getByRole('link', { name: `${name}-EDIT`, exact: true }).count() === 0, 'Deleted directory row is still displayed');
    });
  }
}
if (require.main === module) runWorkflow('consultation-directory-crud', workflow, { openPatient: false });
module.exports = { workflow };
