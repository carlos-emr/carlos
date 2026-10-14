#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Disease Registry popup "Back" -> window.opener reload (risk sweep "lost popup
 * openers"; coverage plan §2.5 dx-registry, opener contracts).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Disease Registry
 * (oscarResearch/oscarDxResearch/setupDxResearch popup) ▸ Back; then ▸ code 401 ▸ Add
 * (oscarResearch/oscarDxResearch/dxResearch, which answers with response.sendRedirect
 * back to setupDxResearch) ▸ Back.
 * dxResearch.jsp reloads window.opener and closes the popup. The former Struts
 * result interceptor omitted COOP from Add/Update/Resolve redirects, severing the
 * opener. The common response filter now preserves it throughout those hops.
 * Asserts each mutation persists, Back closes the popup and reloads the chart,
 * and the chart lists the newly added diagnosis. Cleanup removes the owned
 * dxresearch rows and their partial start dates.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, markOpener } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { documentChain, openerState } = require('./lib/popup-opener-helpers');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, patient } = s;
  const chain = documentChain(s.context);
  s.cleanup(() => {
    sql.execute(`DELETE FROM partial_date WHERE table_name=3 AND field_name=4
      AND table_id IN (SELECT dxresearch_no FROM dxresearch WHERE demographic_no=${patient});
      DELETE FROM dxresearch WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient}`) === '0',
      'Owned dxresearch rows were not removed');
  });
  const chart = await s.chart();
  const openRegistry = async label => {
    const registry = await s.popup(chart, chart.locator('a[onclick*="setupDxResearch"]').first(), label);
    await registry.locator('[name="xml_research1"]').waitFor({ state: 'visible' });
    return registry;
  };
  /** Click Back; report whether the popup closed and whether the chart reloaded. */
  const back = async registry => {
    // Wait for the previous chart refresh to finish before asking Back to reload it
    // again; otherwise the browser legitimately aborts unfinished sidebar XHRs.
    await chart.waitForLoadState('networkidle', { timeout: TIMEOUT });
    const sentinel = await markOpener(chart);
    const state = await openerState(registry);
    const failuresBefore = s.recorder.requestFailures.length;
    const closed = registry.waitForEvent('close', { timeout: TIMEOUT }).then(() => true, () => false);
    const reloaded = chart.waitForEvent('load', { timeout: TIMEOUT }).then(() => true, () => false);
    await registry.locator(`input[type="button"][onclick="handleBackNavigation();"]`).click({ noWaitAfter: true }).catch(error => {
      // Chromium can report the handler's window.close() before acknowledging
      // the click. The close event and chart reload are still required below.
      if (!registry.isClosed() || !/Target page, context or browser has been closed/.test(error.message)) throw error;
    });
    const result = { state, closed: await closed, reloaded: await reloaded };
    if (result.reloaded) {
      result.reloaded = await chart.evaluate(name => window[name], sentinel.marker).catch(() => undefined) !== sentinel.token;
      // Unloading the chart sends its note-lock release beacon (sendBeacon to CaseManagementEntry);
      // Chromium reports the beacon cut off by the reload as a ping ERR_ABORTED. Consume only that.
      const added = s.recorder.requestFailures.splice(failuresBefore);
      s.recorder.requestFailures.push(...added.filter(entry => !(entry.resourceType === 'ping'
        && entry.errorText === 'net::ERR_ABORTED' && new URL(entry.url).pathname.endsWith('/CaseManagementEntry'))));
    }
    return result;
  };

  await s.step('Back on a freshly opened Disease Registry closes it and reloads the chart', async () => {
    const registry = await openRegistry('dx-registry-control');
    const result = await back(registry);
    h.assert(result.closed && result.reloaded,
      `Back did not close the registry and reload the chart (window.opener ${result.state}; closed=${result.closed}, reloaded=${result.reloaded})`);
    await chart.locator('a[onclick*="setupDxResearch"]').first().waitFor({ state: 'attached' });
  });

  let registry;
  await s.step('Add stores code 401 as an active diagnosis and the registry lists it', async () => {
    registry = await openRegistry('dx-registry-add');
    await registry.locator('[name="selectedCodingSystem"]').selectOption('icd9');
    await registry.locator('[name="xml_research1"]').fill('401');
    await clickAndAwaitReload(registry, registry.locator('[name="codeAdd"]'));
    await expectValue(sql, `SELECT CONCAT(COUNT(*),':',MIN(status)) FROM dxresearch WHERE demographic_no=${patient}
      AND dxresearch_code='401' AND coding_system='icd9'`, '1:A', 'Add did not store exactly one active 401 diagnosis');
    const id = sql.value(`SELECT dxresearch_no FROM dxresearch WHERE demographic_no=${patient} AND dxresearch_code='401'`);
    await registry.locator(`#startdate1st${id}`).waitFor({ state: 'visible' });
  });

  // The Add redirect must preserve the chart opener.
  await s.step('Back after the Add closes the registry and reloads the chart, which lists the new diagnosis', async () => {
    const result = await back(registry);
    h.assert(result.closed && result.reloaded,
      `Back after Add did not close the registry and reload the chart (window.opener ${result.state}; `
      + `closed=${result.closed}, reloaded=${result.reloaded}; registry documents: ${chain.describe(registry)})`);
    await chart.locator('#Dx').getByText(/401|hypertension/i).first().waitFor({ state: 'visible', timeout: TIMEOUT });
  });
  const id = sql.value(`SELECT dxresearch_no FROM dxresearch WHERE demographic_no=${patient} AND dxresearch_code='401'`);
  await s.step('Back after a start-date Update closes the registry and reloads the chart', async () => {
    const updated = await openRegistry('dx-registry-update');
    await updated.locator(`#startdate1st${id}`).click();
    await updated.locator(`#startdatenew${id}`).fill('2019-03');
    const row = updated.locator(`#startdate1st${id}`).locator('xpath=ancestor::tr[1]');
    await clickAndAwaitReload(updated, row.getByRole('link', { name: 'Update', exact: true }));
    await expectValue(sql, `SELECT CONCAT(start_date,status) FROM dxresearch WHERE dxresearch_no=${id}`,
      '2019-03-01A', 'Update did not persist the date on the active diagnosis');
    const result = await back(updated);
    h.assert(result.closed && result.reloaded && result.state === 'live', 'Back after Update lost its chart opener');
  });
  await s.step('Back after Resolve closes the registry and reloads the chart', async () => {
    const resolved = await openRegistry('dx-registry-resolve');
    const row = resolved.locator(`#startdate1st${id}`).locator('xpath=ancestor::tr[1]');
    await clickAndAwaitReload(resolved, row.getByRole('link', { name: 'Resolve', exact: true }));
    await expectValue(sql, `SELECT status FROM dxresearch WHERE dxresearch_no=${id}`, 'C', 'Resolve did not persist');
    const result = await back(resolved);
    h.assert(result.closed && result.reloaded && result.state === 'live', 'Back after Resolve lost its chart opener');
  });
}

if (require.main === module) runWorkflow('popup-opener-dx-registry-back', workflow, { openPatient: true });
module.exports = { workflow };
