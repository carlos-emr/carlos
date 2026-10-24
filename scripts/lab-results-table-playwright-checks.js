#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(session) {
  const chart = await session.chart();
  const link = chart.getByRole('link', {name: 'Lab Result', exact: true});
  h.assert(await link.count() === 1, 'Expected one actual chart lab-results link');
  const page = await session.popup(chart, link, 'empty-lab-results');
  await session.step('an owned patient without labs has a usable empty results table', async () => {
    await page.waitForFunction(() => window.jQuery && jQuery.fn.dataTable
      && jQuery.fn.dataTable.isDataTable('#labResultsTbl'));
    const count = await page.evaluate(() => jQuery('#labResultsTbl').DataTable().rows().count());
    h.assert(count === 0, 'Empty-state markup must not become a fabricated lab row');
    const empty = page.locator('#labResultsTbl td.dataTables_empty');
    await empty.waitFor({state: 'visible'});
    h.assert((await empty.innerText()).trim(), 'Empty lab table omitted its explanation');
    h.assert(Number(await empty.getAttribute('colspan')) === await page.locator('#labResultsTbl thead th').count(),
      'Empty lab message does not span the actual columns');
    await page.locator('#labResultsTbl_filter input').fill(session.marker);
    await page.waitForFunction(() => jQuery('#labResultsTbl').DataTable().search().startsWith('FAKE-PW'));
    h.assert(await page.evaluate(() => jQuery('#labResultsTbl').DataTable().rows({search: 'applied'}).count()) === 0,
      'Searching the empty table created a clinical result');
    h.assert(await page.locator('input[name="flaggedLabs"]').count() === 0,
      'An empty patient table exposed nonexistent lab selection controls');
  });
}
if (require.main === module) runWorkflow('lab-results-table', workflow);
module.exports = {workflow};
