#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Disease Registry status transitions and the registry report.
// User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Disease Registry
// popup ▸ Code Search / Add / start-date Update / Resolve / Delete; then
// Schedule ▸ Administration ▸ Reports ▸ Disease Registry (iframe) ▸ code
// autocomplete ▸ Add ▸ status filter ▸ Search ▸ Clear.
// Asserts: a multi-code search adds every selected code once; a partial start
// date persists as YYYY-MM-01 plus its partial-date format and redisplays as
// entered; an unknown code is refused visibly, and the error page's Resolve
// posts through the oscarDxResearch alias; re-adding a resolved code
// reactivates the same row; Delete from active archives it (status D); the
// report's Active/Resolved/Deleted/All filters list exactly the owned rows
// with DB-identical code, status and start date; Clear empties the code list;
// a quick list chosen in the report adds its codes.
// Fixtures: owned FAKE- patient, its dxresearch/partial_date rows, a seeded
// quick list FAKE-<hex>; cleanup deletes only those and asserts they are gone.
// Coverage plan: §2.5 dx-registry-status-update.
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates, typeAutocomplete } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const CODES = { '401': 'ESSENTIAL HYPERTENSION', '272': 'DIS OF LIPOID METABOLISM', '496': 'CHR AIRWAY OBSTRUCT' };
// PartialDate.DXRESEARCH / PartialDate.DXRESEARCH_STARTDATE
const PARTIAL = 'table_name=3 AND field_name=4';

/** Wait for the POST the registry's hidden action form sends, by route suffix. */
function awaitUpdate(page, route) {
  return page.waitForRequest(r => r.method() === 'POST' && new URL(r.url()).pathname.endsWith(route));
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const quickList = `FAKE-${marker.slice(-5)}`;
  const ids = {};
  const status = code => `SELECT status FROM dxresearch WHERE dxresearch_no=${ids[code]} AND demographic_no=${patient}`;
  s.cleanup(() => {
    const owned = sql.rows(`SELECT dxresearch_no FROM dxresearch WHERE demographic_no=${patient}`).map(r => Number(r[0]));
    const list = owned.length ? owned.join(',') : '0';
    sql.execute(`DELETE FROM partial_date WHERE ${PARTIAL} AND table_id IN (${list});
      DELETE FROM dxresearch WHERE demographic_no=${patient};
      DELETE FROM quickList WHERE quickListName=${sqlString(quickList)};
      DELETE FROM quickListUser WHERE quickListName=${sqlString(quickList)}`);
    assert(sql.value(`SELECT (SELECT COUNT(*) FROM partial_date WHERE ${PARTIAL} AND table_id IN (${list}))
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM quickList WHERE quickListName=${sqlString(quickList)})`) === '0', 'Owned registry fixtures were not removed');
  });
  sql.execute(`INSERT INTO quickList (quickListName, createdByProvider, dxResearchCode, codingSystem)
    VALUES (${sqlString(quickList)}, ${sqlString(provider)}, '401', 'icd9')`);

  const chart = await s.chart();
  const registry = await s.popup(chart, chart.locator('a[onclick*="setupDxResearch"]').first(), 'dx-status-registry');
  const rowOf = code => registry.locator(`#startdate1st${ids[code]}`).locator('xpath=ancestor::tr[1]');

  await s.step('a multi-code search adds every selected code exactly once', async () => {
    await registry.locator('[name="selectedCodingSystem"]').selectOption('icd9');
    for (const [i, code] of Object.keys(CODES).entries()) await registry.locator(`[name="xml_research${i + 1}"]`).fill(code);
    const search = await s.popup(registry, registry.locator('[name="codeSearch"]'), 'dx-status-code-search');
    for (const code of Object.keys(CODES)) {
      assert(await search.locator(`input[name="searchCodes"][value="${code}"]`).isChecked(), `Code search did not preselect ${code}`);
    }
    const closed = search.waitForEvent('close');
    await search.locator('[name="confirm"]').click();
    await closed;
    await clickAndAwaitReload(registry, registry.locator('[name="codeAdd"]'));
    for (const code of Object.keys(CODES)) {
      const rows = sql.rows(`SELECT dxresearch_no, status, providerNo, start_date=CURDATE() FROM dxresearch
        WHERE demographic_no=${patient} AND dxresearch_code='${code}' AND coding_system='icd9'`);
      assert(rows.length === 1 && rows[0][1] === 'A' && rows[0][2] === provider && rows[0][3] === '1',
        `Code ${code} was not added once as an active diagnosis started today by the login`);
      ids[code] = rows[0][0];
      await rowOf(code).waitFor({ state: 'visible' });
    }
  });

  await s.step('a partial start date persists with its format and redisplays as entered', async () => {
    await registry.locator(`#startdate1st${ids['401']}`).click();
    await registry.locator(`#startdatenew${ids['401']}`).fill('2019-03');
    const posted = awaitUpdate(registry, '/oscarResearch/dxresearch/dxResearchUpdate');
    await clickAndAwaitReload(registry, rowOf('401').getByRole('link', { name: 'Update', exact: true }));
    await posted;
    await expectValue(sql, `SELECT CONCAT(start_date, status) FROM dxresearch WHERE dxresearch_no=${ids['401']}`, '2019-03-01A',
      'Start-date update did not persist the first of the month on the still-active row');
    assert(sql.value(`SELECT format FROM partial_date WHERE ${PARTIAL} AND table_id=${ids['401']}`) === 'YYYY-MM',
      'Start-date update did not record the year-month precision');
    assert((await registry.locator(`#startdate1st${ids['401']}`).innerText()).trim() === '2019-03',
      'The registry does not redisplay the partial start date as entered');
  });

  await s.step('an unknown code is refused visibly and the error page resolves through its own route', async () => {
    await registry.locator('[name="xml_research1"]').fill('Z9Z9');
    for (const i of [2, 3, 4, 5]) await registry.locator(`[name="xml_research${i}"]`).fill('');
    await clickAndAwaitReload(registry, registry.locator('[name="codeAdd"]'));
    assert((await registry.locator('.action-errors').innerText()).includes('Z9Z9'), 'Unknown code produced no visible error');
    assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient}`) === '3', 'An unknown code changed the registry');
    const posted = awaitUpdate(registry, '/oscarResearch/oscarDxResearch/dxResearchUpdate');
    await clickAndAwaitReload(registry, rowOf('272').getByRole('link', { name: 'Resolve', exact: true }));
    await posted;
    await expectValue(sql, `SELECT status FROM dxresearch WHERE dxresearch_no=${ids['272']}`, 'C', 'Resolve from the error page did not persist');
    assert(await rowOf('272').getByRole('link', { name: 'Resolve', exact: true }).count() === 0, 'A resolved diagnosis still offers Resolve');
  });

  await s.step('re-adding a resolved code reactivates the same row instead of duplicating it', async () => {
    await registry.locator('[name="xml_research1"]').fill('272');
    await clickAndAwaitReload(registry, registry.locator('[name="codeAdd"]'));
    await expectValue(sql, status('272'), 'A', 'Re-adding a resolved code did not reactivate it');
    assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient} AND dxresearch_code='272'`) === '1',
      'Re-adding a resolved code created a duplicate row');
    await rowOf('272').getByRole('link', { name: 'Resolve', exact: true }).waitFor({ state: 'visible' });
  });

  await s.step('Delete from active archives directly and Resolve changes only its own row', async () => {
    const remove = registry.locator(`a[onclick*="'D','','${ids['272']}'"]`);
    const asked = await withExpectedDialogs(registry, () => clickAndAwaitReload(registry, remove));
    assert(asked.length === 1 && asked[0].type === 'confirm', 'Delete from active did not ask for confirmation');
    await expectValue(sql, status('272'), 'D', 'Delete from active did not archive the diagnosis');
    assert(await registry.locator(`#startdate1st${ids['272']}`).count() === 0, 'Deleted diagnosis is still listed');
    await clickAndAwaitReload(registry, rowOf('496').getByRole('link', { name: 'Resolve', exact: true }));
    await expectValue(sql, status('496'), 'C', 'Resolve did not persist');
    assert(['401', '272', '496'].map(c => sql.value(status(c))).join('') === 'ADC', 'A status change touched another diagnosis');
    await registry.close();
  });

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'dx-report-administration', timeout: 20000 });
  const link = admin.getByRole('link', { name: 'Disease Registry', exact: true, includeHidden: true });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const report = await (await iframe.elementHandle()).contentFrame();
  assert(report, 'Disease Registry report iframe did not load');
  async function submit(control) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === report });
    await control.click();
    await navigated;
    await report.waitForLoadState('networkidle').catch(() => {});
  }
  const codeRows = () => report.locator('table#codeSearch tbody tr').filter({ has: report.locator('td') });

  await s.step('code autocomplete (dxCodeSearchJSON) adds each owned code to the search', async () => {
    await report.locator('#codesearch').waitFor({ state: 'visible' });
    for (const [code, description] of Object.entries(CODES)) {
      await report.locator('#codingSystem').selectOption('icd9');
      await typeAutocomplete(report, '#codesearch', description, { option: new RegExp(`^${code}: `) });
      assert(await report.locator('#codesearch').inputValue() === code, `Autocomplete did not fill code ${code}`);
      await submit(report.getByRole('button', { name: 'Add', exact: true }));
      const row = codeRows().filter({ has: report.locator('td', { hasText: new RegExp(`^${code}$`) }) });
      await row.waitFor({ state: 'visible' });
      assert((await row.innerText()).includes(description), `Search list lost the description of ${code}`);
    }
    assert(await codeRows().count() === 3, 'The search list does not hold exactly the three added codes');
  });

  await s.step('status filters list exactly the owned rows with database-identical fields', async () => {
    await report.locator('#provider_no').selectOption(provider);
    const expected = { Active: ['401'], Resolved: ['496'], Deleted: ['272'], ALL: ['272', '401', '496'] };
    for (const [radio, codes] of Object.entries(expected)) {
      await report.getByLabel(radio, { exact: true }).check();
      await submit(report.getByRole('button', { name: 'Search', exact: true }));
      await report.locator('#listview_filter input').fill(marker);
      const rows = await report.locator('#listview tbody tr').evaluateAll(trs => trs
        .map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent.trim())).filter(cells => cells.length >= 11));
      const seen = rows.map(c => `${c[7]}|${c[6]}|${c[10]}|${c[8]}`).sort();
      const db = sql.rows(`SELECT dxresearch_code, coding_system, status, start_date FROM dxresearch
        WHERE demographic_no=${patient} AND dxresearch_code IN (${codes.map(sqlString).join(',')}) ORDER BY dxresearch_code`)
        .map(r => r.join('|'));
      assert(JSON.stringify(seen) === JSON.stringify(db.sort()), `The ${radio} filter does not list exactly the owned rows as stored`);
    }
  });

  await s.step('Clear empties the code search list', async () => {
    await submit(report.getByRole('button', { name: 'Clear', exact: true }));
    assert(await codeRows().count() === 0, 'Clear left codes in the search list');
  });

  await s.step('a quick list chosen in the report adds its codes to the search', async () => {
    await report.locator('select[name="quicklistname"]').selectOption(quickList);
    await submit(report.getByRole('button', { name: 'Add', exact: true }));
    const row = codeRows().filter({ has: report.locator('td', { hasText: /^401$/ }) });
    assert(await row.count() === 1, 'Choosing a quick list in the report added none of its codes');
    await submit(report.getByRole('button', { name: 'Clear', exact: true }));
  });
}

if (require.main === module) runWorkflow('dx-registry-status-update', workflow);
module.exports = { workflow };
