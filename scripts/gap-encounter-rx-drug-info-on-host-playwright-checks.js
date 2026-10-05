#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rx "Info" on a prescribed drug must not send the drug name to another host.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart ▸ Prescriptions "+" (the Rx page) ▸ drug search (a real
 * DrugRef product) ▸ stage it ▸ Save ▸ the saved drug's row (rx/ViewStaticScript2) ▸ the "Info" link beside
 * the drug (opens rx/drugInfo in a window). Info is offered only for a DrugRef product, which carries a
 * generic name: the Custom Drug path stages a card with no name at all, so it is not used here (and the
 * chooser's own Info links are reached only with a search the Rx page does not offer for custom drugs).
 * Info must render useful configured DrugRef information locally. The first request is made without
 * following redirects, so this regression remains safe against the old off-host redirect. After that
 * check, the real popup must show the saved product's DIN and support local name search/result links.
 * Fixtures: the owned synthetic patient and the one drug saved for it through the Rx page (needs DrugRef
 * data for RX_INFO_DRUG_TERM / RX_INFO_DRUG_NAME, default LIPITOR 20MG); cleanup deletes the patient's
 * drugs and prescription rows and asserts them gone.
 * Implements gap-encounter "drug information from the prescription window" (rx/drugInfo had no check).
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageFromSearch } = require('./rx-edit-discontinue-playwright-checks');

const DRUG_TERM = process.env.RX_INFO_DRUG_TERM || 'LIPITOR 20';
const DRUG_NAME = process.env.RX_INFO_DRUG_NAME || 'LIPITOR 20MG';

async function workflow(s) {
  const { patient, config, sql } = s;
  const appHost = new URL(config.baseUrl).host;
  s.cleanup(() => {
    const drugIds = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(drugIds.every(id => /^[1-9]\d*$/.test(id)), 'Owned drug id is invalid');
    sql.execute(`${drugIds.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugIds.join(',')});` : ''}
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
  });
  const chart = await s.chart();
  let rx;
  let drug;
  let generic;
  let din;
  let infoPage;

  await s.step('Prescriptions ▸ search stages a DrugRef product and Save files it for the patient', async () => {
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0', 'A drug existed before the save');
    rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    await rx.waitForLoadState('networkidle').catch(() => {});
    const card = await stageFromSearch(rx, DRUG_TERM, DRUG_NAME);
    await rx.locator(`#instructions_${card}`).fill('1 tab PO BID x 14 days');
    await rx.locator(`label[for="jsonDxSearch_${card}"]`).click();
    const saved = rx.waitForResponse(r => r.request().method() === 'POST' && /\/rx\/WriteScript\?[^#]*parameterValue=updateSaveAllDrugs/.test(r.url()), { timeout: 30000 });
    saved.catch(() => {});
    await rx.locator('#saveOnlyButton').click();
    h.assert((await saved).status() === 200, 'Save did not answer 200');
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND BN=${h.sqlString(DRUG_NAME)}`, '1',
      'Save did not file the chosen DrugRef product for the patient');
    [[drug, generic, din]] = sql.rows(`SELECT drugid, GN, regional_identifier FROM drugs WHERE demographic_no=${patient}`);
    h.assert(generic && generic !== 'NULL', 'The saved drug has no generic name for Info to look up');
    h.assert(din && din !== 'NULL' && din !== '0', 'The saved DrugRef product has no DIN');
    await rx.locator(`#prescrip_${drug}`).waitFor({ state: 'visible' });
  });

  await s.step('the saved drug\'s Info link opens rx/drugInfo for its own generic name, with no redirect to another host', async () => {
    // The drug row opens the static script page, which offers Info beside a DrugRef drug. The link is clicked
    // for real; window.open is replaced by a recorder so no window (and so no redirect) is ever followed by the
    // browser. The recorded address is then read from CARLOS WITHOUT following redirects, and only the
    // Location's host is looked at (the address carries the drug text).
    await clickAndAwaitReload(rx, rx.locator(`#prescrip_${drug}`), { label: 'drug row' });
    h.assert(h.pathOnly(rx.url()).endsWith('/rx/ViewStaticScript2'), 'The drug row did not open the static script page');
    const info = rx.locator('a[href^="javascript:ShowDrugInfo("]').first();
    await info.waitFor({ state: 'visible', timeout: 20000 });
    await rx.evaluate(() => {
      window.__originalOpen = window.open;
      window.__opened = [];
      window.open = address => { window.__opened.push(String(address)); return null; };
    });
    await info.click();
    // A javascript: link runs after the click returns, so wait for the recorder rather than reading it at once.
    await rx.waitForFunction(() => window.__opened && window.__opened.length > 0, null, { timeout: 10000 }).catch(() => {});
    const opened = await rx.evaluate(() => window.__opened);
    h.assert(opened.length === 1, `Clicking Info opened ${opened.length} window(s), not exactly one`);
    const target = new URL(opened[0], rx.url());
    h.assert(target.host === appHost && target.pathname.endsWith('/rx/drugInfo'), 'The Info link does not open a CARLOS address');
    h.assert(target.searchParams.get('GN') === generic, 'The Info link does not ask for the saved drug\'s generic name');
    h.assert(target.searchParams.get('DIN') === din, 'The Info link does not identify the saved product by DIN');
    const response = await s.context.request.get(target.toString(), { maxRedirects: 0 });
    const status = response.status();
    const headers = response.headers();
    const location = headers.location;
    const host = location ? new URL(location, target).host : null;
    await response.dispose();
    h.assert(host === null || host === appHost,
      `Rx Info redirects to another host (${host}), carrying the drug name in the address`);
    h.assert(status === 200 && !location, `Rx Info did not render its local view (HTTP ${status})`);
    await rx.evaluate(() => { window.open = window.__originalOpen; });
    infoPage = await s.popup(rx, info, 'rx-drug-info');
    await infoPage.locator('#drug-reference-details').waitFor({ state: 'visible' });
    h.assert(new URL(infoPage.url()).origin === new URL(config.baseUrl).origin, 'Drug reference details left CARLOS');
    h.assert((await infoPage.locator('#drug-info-din').innerText()).trim() === din,
      'Drug reference details do not identify the saved product');
    h.assert((await infoPage.locator('#drug-reference-details h2').innerText()).trim() === DRUG_NAME,
      'Drug reference details do not name the saved product');
    h.assert(await infoPage.locator('#drug-info-name').inputValue() === generic,
      'Info did not preserve the saved prescription description');
  });

  await s.step('local drug-name search opens useful reference details through a DrugRef product link', async () => {
    await infoPage.locator('#drug-info-name').fill(DRUG_TERM);
    await clickAndAwaitReload(infoPage, infoPage.getByRole('button', { name: 'Search', exact: true }), { label: 'local reference search' });
    const product = infoPage.locator('#drug-info-matches a').filter({ hasText: DRUG_NAME }).first();
    await product.waitFor({ state: 'visible' });
    const target = new URL(await product.getAttribute('href'), infoPage.url());
    h.assert(target.origin === new URL(config.baseUrl).origin && target.searchParams.has('BN'),
      'The reference search result does not link to a local DrugRef product');
    await clickAndAwaitReload(infoPage, product, { label: 'local reference result' });
    await infoPage.locator('#drug-reference-details').waitFor({ state: 'visible' });
    h.assert((await infoPage.locator('#drug-info-din').innerText()).trim() === din,
      'The reference search result does not show the chosen product DIN');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND drugid=${drug}`) === '1',
      'Viewing drug reference information changed the saved prescription');
  });
}

if (require.main === module) runWorkflow('gap-encounter-rx-drug-info-on-host', workflow, { openPatient: true });
module.exports = { workflow };
