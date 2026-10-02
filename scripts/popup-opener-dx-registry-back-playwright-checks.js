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
 * dxResearch.jsp handleBackNavigation() reloads window.opener (the chart) and closes
 * the popup; with no opener it falls back to history.back(). The Add redirect is a
 * 302 sent before Struts' COOP PreResultListener runs, so it carries no
 * Cross-Origin-Opener-Policy; Chromium enforces COOP on redirects and the popup
 * leaves the chart's browsing-context group, losing window.opener for good.
 * Asserts: Back on a fresh registry closes it and reloads the chart (control: the
 * contract works while the opener lives); Add writes one active dxresearch row and
 * lists it; Back after the Add closes the popup and reloads the chart, whose Disease
 * Registry module then lists the code.
 * Fixtures: the owned FAKE- patient; cleanup deletes its dxresearch rows and asserts
 * they are gone.
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
    sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient}`);
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
    const sentinel = await markOpener(chart);
    const state = await openerState(registry);
    const failuresBefore = s.recorder.requestFailures.length;
    const closed = registry.waitForEvent('close', { timeout: TIMEOUT }).then(() => true, () => false);
    const reloaded = chart.waitForEvent('load', { timeout: TIMEOUT }).then(() => true, () => false);
    await registry.locator(`input[type="button"][onclick="handleBackNavigation();"]`).click({ noWaitAfter: true });
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

  // Last: the opener reload after the Add's COOP-less redirect.
  await s.step('Back after the Add closes the registry and reloads the chart, which lists the new diagnosis', async () => {
    const result = await back(registry);
    h.assert(result.closed && result.reloaded,
      `Back after Add did not close the registry and reload the chart (window.opener ${result.state}; `
      + `closed=${result.closed}, reloaded=${result.reloaded}; registry documents: ${chain.describe(registry)})`);
    await chart.locator('#Dx').getByText(/401|hypertension/i).first().waitFor({ state: 'visible', timeout: TIMEOUT });
  });
}

if (require.main === module) runWorkflow('popup-opener-dx-registry-back', workflow, { openPatient: true });
module.exports = { workflow };
