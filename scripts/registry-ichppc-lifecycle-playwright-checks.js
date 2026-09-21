#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Reach the disease registry through a new synthetic patient's chart. ICHPPC
// uses a different DAO from ICD9; the existing diagnosis workflow cannot catch
// its historical ClassCastException (#3741), failed lookup, or missing save.
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient } = s;
  s.cleanup(() => sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient}`));
  const code = sql.value("SELECT ichppccode FROM ichppccode WHERE ichppccode REGEXP '^[A-Za-z0-9.]+$' AND description<>'' ORDER BY ichppccode LIMIT 1");
  assert(/^[A-Za-z0-9.]+$/.test(code), 'The installed ICHPPC reference dataset has no usable code');
  const description = sql.value(`SELECT description FROM ichppccode WHERE ichppccode=${sqlString(code)}`);
  const chart = await s.chart();
  const opener = chart.locator('a[onclick*="setupDxResearch"]').first();
  let registry = await s.popup(chart, opener, 'ichppc-registry');
  let id;
  const saved = () => `SELECT status FROM dxresearch WHERE dxresearch_no=${id} AND demographic_no=${patient} AND coding_system='ichppccode'`;

  await s.step('ICHPPC search returns the selected code and its reference description', async () => {
    await registry.locator('[name="selectedCodingSystem"]').selectOption('ichppccode');
    await registry.locator('[name="xml_research1"]').fill(code);
    const results = await s.popup(registry, registry.locator('[name="codeSearch"]'), 'ichppc-search');
    const choice = results.locator(`input[name="searchCodes"][value="${code}"]`);
    await choice.waitFor({ state: 'visible' });
    assert((await choice.locator('xpath=ancestor::tr[1]').innerText()).includes(description),
      'ICHPPC search did not render the reference description');
    await choice.check();
    await results.locator('[name="confirm"]').click();
    assert(await registry.locator('[name="xml_research1"]').inputValue() === code,
      'ICHPPC selection did not reach the registry opener');
  });

  await s.step('add persists the correct coding system and survives reopening', async () => {
    await clickAndAwaitReload(registry, registry.locator('[name="codeAdd"]'));
    const predicate = `demographic_no=${patient} AND dxresearch_code=${sqlString(code)} AND coding_system='ichppccode'`;
    await expectValue(sql, `SELECT COUNT(*) FROM dxresearch WHERE ${predicate}`, '1',
      'ICHPPC add did not persist exactly one correctly typed diagnosis');
    id = sql.value(`SELECT dxresearch_no FROM dxresearch WHERE ${predicate}`);
    assert(/^[1-9]\d*$/.test(id), 'The saved ICHPPC diagnosis has no identifier');
    await registry.close();
    registry = await s.popup(chart, opener, 'ichppc-reopened');
    const row = registry.locator(`#startdate1st${id}`).locator('xpath=ancestor::tr[1]');
    await row.waitFor({ state: 'visible' });
    assert((await row.innerText()).includes(description), 'Reopened ICHPPC diagnosis lost its description');
    assert(sql.value(saved()) === 'A', 'The new diagnosis is not active');
  });

  await s.step('resolve and confirmed deletion persist; canceled deletion changes nothing', async () => {
    const row = registry.locator(`#startdate1st${id}`).locator('xpath=ancestor::tr[1]');
    await clickAndAwaitReload(registry, row.getByRole('link', { name: 'Resolve', exact: true }));
    await expectValue(sql, saved(), 'C', 'ICHPPC resolve did not persist');
    const remove = registry.locator(`a[onclick*="'D','','${id}'"]`);
    const canceled = await withExpectedDialogs(registry, () => remove.click(), { accept: false });
    assert(canceled.length === 1 && canceled[0].type === 'confirm', 'Delete did not ask for confirmation');
    assert(sql.value(saved()) === 'C', 'Canceled deletion changed the diagnosis');
    const accepted = await withExpectedDialogs(registry, () => clickAndAwaitReload(registry, remove));
    assert(accepted.length === 1 && accepted[0].type === 'confirm', 'Delete bypassed confirmation');
    await expectValue(sql, saved(), 'D', 'Deleted ICHPPC diagnosis was not retained as archived history');
    assert(await remove.count() === 0, 'Deleted ICHPPC diagnosis remains in the visible list');
  });
}

if (require.main === module) runWorkflow('registry-ichppc-lifecycle', workflow);
module.exports = { workflow };
