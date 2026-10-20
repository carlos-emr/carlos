#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Drug names with an apostrophe or an accent on the prescription card (wave 6, boundary values).
 * User path: Schedule > Search > Master Record > Rx (rx/choosePatient popup) > drug search box > pick a drug from the
 * autocomplete list > the staged prescription card.
 * Asserts: a DrugRef product whose name contains an apostrophe (CHILDREN'S BENADRYL ALLERGY LIQUID) and one with
 * French accents (ACETAMINOPHENE written with accents, from the bilingual DrugRef names) are staged with the
 * name the autocomplete list showed, not with the name run through Java string escaping (a backslash before the
 * apostrophe, É for an accent). Nothing is saved: no prescription is written for the owned patient.
 * Fixtures: the owned FAKE- patient; cleanup asserts no drugs row was created for it.
 * Implements the wave-6 "boundary values" pattern, Part 1 (special characters round trip: apostrophes, accents).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const CASES = [
  { label: 'apostrophe', term: 'BENADRYL ALLERGY', pick: "CHILDREN'S BENADRYL ALLERGY" },
  { label: 'accents', term: 'ACÉTAMINOPHÈNE', pick: 'ACÉTAMINOPHÈNE' },
];
const isPost = (route) => (response) => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);

async function workflow(s) {
  const { sql, patient } = s;
  s.cleanup(() => h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0', 'A drug was written for the owned patient'));
  const rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');

  const problems = [];
  await s.step('a DrugRef product with an apostrophe or accents is staged under the name the autocomplete showed', async () => {
    for (const item of CASES) {
      const before = await rx.locator('[id^="drugName_"]').evaluateAll(nodes => nodes.map(node => node.id));
      await rx.locator('#searchString').fill('');
      // The search fires per typed prefix; wait for the response to the COMPLETE term, and require it succeeded.
      const [searched] = await Promise.all([
        rx.waitForResponse(r => isPost('/rx/searchDrug')(r)
          && decodeURIComponent((r.request().postData() || '').replace(/\+/g, ' ')).includes(item.term), { timeout: 60000 }),
        rx.locator('#searchString').pressSequentially(item.term, { delay: 40 }),
      ]);
      h.assert(searched.ok(), `The DrugRef search for "${item.term}" answered HTTP ${searched.status()}`);
      // The menu is filled asynchronously after the response: wait for the product itself (a product that never shows is
      // genuinely absent and is handled below), not just for the empty list element.
      await rx.locator('ul.ui-autocomplete li.ui-menu-item').filter({ hasText: item.pick }).first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => {});
      const option = rx.locator('ul.ui-autocomplete li.ui-menu-item').filter({ hasText: item.pick }).first();
      if (await option.count() === 0) {
        const offered = (await rx.locator('ul.ui-autocomplete li.ui-menu-item').allInnerTexts()).slice(0, 6).map(text => text.replace(/\s+/g, ' ').trim());
        // Report a mismatch already recorded for an earlier product before skipping an unavailable one.
        h.assert(problems.length === 0, `${problems.join('; ')} (#3952: createNewRx and prescribe.jsp must keep the picked name raw and encode it only at output)`);
        // SKIP only for a product that is genuinely absent: any browser problem while searching (script error, console error,
        // failed or bad response) is a failure, and so is a completed search whose JSON lists the product that the menu lacks.
        for (const list of ['pageErrors', 'consoleIssues', 'requestFailures', 'badResponses']) {
          h.assert((s.recorder[list] || []).length === 0, `The product search recorded ${list}: ${JSON.stringify(s.recorder[list]).slice(0, 300)}`);
        }
        const searchedBody = await searched.text();
        h.assert(!searchedBody.toUpperCase().includes(item.pick.toUpperCase()),
          `The DrugRef search response lists "${item.pick}" but the autocomplete menu does not show it`);
        throw new h.SkipCheck(`DrugRef offers no "${item.pick}" for "${item.term}" on this install (offered: ${offered.join(' | ')})`);
      }
      await option.waitFor({ state: 'visible' });
      const shown = (await option.innerText()).replace(/\s+/g, ' ').trim();
      await Promise.all([
        rx.waitForResponse(r => isPost('/rx/WriteScript')(r) && new URLSearchParams(r.request().postData() || '').get('parameterValue') === 'createNewRx'),
        option.click(),
      ]);
      // The new card is the drugName_ box whose id was not on the page before the pick (new cards may be prepended).
      await rx.waitForFunction(known => document.querySelectorAll('[id^="drugName_"]').length > known.length, before, { timeout: 30000 });
      const fresh = await rx.locator('[id^="drugName_"]').evaluateAll((nodes, known) => nodes.map(node => node.id).filter(id => !known.includes(id)), before);
      h.assert(fresh.length === 1, `Expected exactly one new prescription card, found ${fresh.length}`);
      const staged = (await rx.locator(`[id="${fresh[0]}"]`).inputValue()).replace(/\s+/g, ' ').trim();
      if (staged !== shown) problems.push(`${item.label}: the autocomplete showed "${shown}" but the staged card is named "${staged}"`);
    }
    h.assert(problems.length === 0, `${problems.join('; ')} (#3952: createNewRx and prescribe.jsp must keep the picked name raw and encode it only at output)`);
  });
}

if (require.main === module) runWorkflow('boundary-rx-drug-name', workflow, { openPatient: true });
module.exports = { workflow };
