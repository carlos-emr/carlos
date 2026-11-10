#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
/*
 * The Ontario bill form's service-code and diagnosis type-aheads (billingON.jsp
 * initCodeAutocomplete): ViewBillingCodeSearchAjax and ViewBillingDigSearchAjax answer
 * the typed term, picking a suggestion fills the field with the code, a picked diagnosis
 * shows its description (ViewBillingONDxDesc), and an unpicked diagnosis term longer than
 * five characters is cleared when the field is left. Until now only
 * lib/authz-read-routes.js named these two routes.
 *
 * Fixtures: an owned appointment, one owned Ontario service code and one owned diagnosis
 * code, both described with the run's marker; all removed by their own keys.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { unusedNumber } = require('./lib/bc-billing-fixture');
const { openOntarioBillForm, suggestionsFor, pickSuggestion, requestsWhile } = require('./lib/billing-on-bill-form');

const CODE_SEARCH = '/billing/CA/ON/ViewBillingCodeSearchAjax';
const DX_SEARCH = '/billing/CA/ON/ViewBillingDigSearchAjax';
const DX_DESC = '/billing/CA/ON/ViewBillingONDxDesc';

async function workflow(s) {
  const exists = (table, column) => value => s.sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${column}=?`, [value]) !== '0';
  // Ontario service codes are a letter, three digits and a suffix letter (A001A); the owned one
  // starts with Z9 so no real code shares its prefix.
  const serviceCode = unusedNumber('Z9', 4, candidate => exists('billingservice', 'service_code')(`${candidate}A`)) + 'A';
  const serviceDescription = `${s.marker} service`;
  // Diagnosis codes are three digits; the owned one is free and starts with 9.
  const dxCode = unusedNumber('9', 3, exists('diagnosticcode', 'diagnostic_code'));
  const dxDescription = `${s.marker} diagnosis`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM billingservice WHERE service_code=? AND description=?`, [serviceCode, serviceDescription]);
    s.sql.execute(`DELETE FROM diagnosticcode WHERE diagnostic_code=? AND description=?`, [dxCode, dxDescription]);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=? AND description=?`, [serviceCode, serviceDescription]) === '0', 'Owned service code remains');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM diagnosticcode WHERE diagnostic_code=? AND description=?`, [dxCode, dxDescription]) === '0', 'Owned diagnosis code remains');
  });
  s.sql.execute(`INSERT INTO billingservice (service_code,description,value,percentage,billingservice_date,specialty,region,anaesthesia)
    VALUES (?,?,'12.34','',DATE_SUB(CURDATE(),INTERVAL 1 YEAR),'','ON','00')`, [serviceCode, serviceDescription]);
  s.sql.execute(`INSERT INTO diagnosticcode (diagnostic_code,description,status,region) VALUES (?,?,'A','ON')`, [dxCode, dxDescription]);

  const { page } = await openOntarioBillForm(s, 'on-bill-form-codes');
  const service = index => page.locator(`input[name="serviceCode${index}"]`);
  const dx = page.locator('input[name="dxCode"]');

  await s.step('a one-character term sends no code search', async () => {
    const sent = await requestsWhile(page, CODE_SEARCH, async () => {
      await service(0).click();
      await service(0).fill(serviceCode.slice(0, 1));
    });
    h.assert(sent === 0, `A one-character term sent ${sent} code search(es); the type-ahead starts at two`);
    await service(0).fill('');
  });

  await s.step('the service-code type-ahead answers a code prefix and picking fills the code', async () => {
    const term = serviceCode.slice(0, 4);
    const response = suggestionsFor(page, CODE_SEARCH, term);
    await service(0).fill(term);
    const items = await (await response).json();
    assertCodeMatches(items, term);
    const owned = items.find(item => item.code === serviceCode);
    h.assert(owned && owned.description === serviceDescription,
      `The code search did not return the owned code with its description: ${JSON.stringify(items.map(item => item.code))}`);
    await pickSuggestion(page, serviceCode);
    h.assert(await service(0).inputValue() === serviceCode, 'Picking a service code did not fill the field with the code');
  });

  await s.step('the service-code type-ahead also finds a code by its description', async () => {
    const response = suggestionsFor(page, CODE_SEARCH, s.marker);
    await service(1).fill(s.marker);
    const items = await (await response).json();
    assertCodeMatches(items, s.marker);
    h.assert(items.some(item => item.code === serviceCode), 'A description term did not return the owned code');
    await pickSuggestion(page, serviceCode);
    h.assert(await service(1).inputValue() === serviceCode, 'Picking from a description search did not fill the code');
  });

  await s.step('the diagnosis type-ahead fills the code and shows its description', async () => {
    const response = suggestionsFor(page, DX_SEARCH, dxCode);
    const description = page.waitForResponse(r => new URL(r.url()).pathname.endsWith(DX_DESC)
      && new URL(r.url()).searchParams.get('diagnostic_code') === dxCode, { timeout: 20000 });
    await dx.fill(dxCode);
    const items = await (await response).json();
    assertCodeMatches(items, dxCode);
    const owned = items.find(item => item.code === dxCode);
    h.assert(owned && owned.description === dxDescription,
      `The diagnosis search did not return the owned code with its description: ${JSON.stringify(items.map(item => item.code))}`);
    await pickSuggestion(page, dxCode);
    h.assert(await dx.inputValue() === dxCode, 'Picking a diagnosis did not fill the field with the code');
    await description;
    // The box shows the description, cut to 32 characters plus "..." when longer
    // (BillingDiagCodeViewModelAssembler.assembleDescription); the owned one is longer.
    const shown = dxDescription.length > 32 ? `${dxDescription.slice(0, 32)}...` : dxDescription;
    await page.waitForFunction(expected => (document.getElementById('code_desc').textContent || '').trim() === expected, shown);
    h.assert(await page.locator('#code_desc').getAttribute('title') === shown, 'The diagnosis description box has no matching title');
  });

  await s.step('an unpicked diagnosis term longer than five characters is cleared on leaving the field', async () => {
    // Typed, searched (nothing found, so nothing to pick), then left.
    const searched = suggestionsFor(page, DX_SEARCH, 'ZZZZZZ');
    await dx.fill('ZZZZZZ');
    h.assert((await (await searched).json()).length === 0, 'ZZZZZZ matched a diagnosis code');
    await page.keyboard.press('Tab');
    await page.waitForFunction(() => document.forms[0].dxCode.value === '');
  });

  await s.step('an unrelated service term returns no suggestions', async () => {
    const response = suggestionsFor(page, CODE_SEARCH, `${s.marker}-no-match`);
    await service(2).fill(`${s.marker}-no-match`);
    h.assert((await (await response).json()).length === 0, 'An unrelated service term returned suggestions');
  });
}

/** Every result must answer either the code prefix or the description term. */
function assertCodeMatches(items, term) {
  const needle = term.toLowerCase();
  h.assert(items.every(item => String(item.code || '').toLowerCase().startsWith(needle)
    || String(item.description || '').toLowerCase().includes(needle)), 'The type-ahead returned an unrelated code');
}

if (require.main === module) runWorkflow('billing-on-bill-form-code-typeahead', workflow, { openMaster: false });
module.exports = { workflow, assertCodeMatches };
