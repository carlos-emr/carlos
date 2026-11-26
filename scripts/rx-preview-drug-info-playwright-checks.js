#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { stageFromSearch } = require('./rx-edit-discontinue-playwright-checks');
const { openRx } = require('./rx-stash-patient-isolation-playwright-checks');

const products = [
  {
    term: 'EZETIMIBE',
    selection: 'EZETIMIBE 10MG TABLET',
    brand: 'EZETROL 10MG',
    strength: 10,
    din: '02247521',
  },
  {
    term: 'LORAZEPAM',
    selection: 'LORAZEPAM 0.5MG TABLET',
    brand: 'ATIVAN 0.5MG',
    strength: 0.5,
    din: '02041413',
  },
];

async function workflow(s) {
  const { patient, sql, config } = s;
  s.cleanup(() => {
    const drugIds = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(drugIds.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned drug ID');
    sql.execute(`
      ${drugIds.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugIds.join(',')});` : ''}
      DELETE FROM drugs WHERE demographic_no=${patient};
      DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND moduleType='PRESCRIPTION'
    `);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}) +
        (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient}) +
        (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient} AND moduleType='PRESCRIPTION')`) === '0',
    'Owned prescriptions or signatures were retained');
  });

  for (const product of products) {
    await s.step(`${product.selection} shows the saved product in Save and Print → Drug Information`, async () => {
      const rx = await openRx(s, patient);
      h.wireStrictPage(rx, 'rx-page', s.recorder);
      const [stageRequest, card] = await Promise.all([
        rx.waitForRequest(r => r.method() === 'POST' && h.pathOnly(r.url()).endsWith('/rx/WriteScript')
          && new URLSearchParams(r.postData()).get('parameterValue') === 'createNewRx'),
        stageFromSearch(rx, product.term, product.selection),
      ]);
      const productKey = new URLSearchParams(stageRequest.postData()).get('drugId');
      h.assert(/^[1-9]\d*$/.test(productKey ?? ''), 'The staged generic entry has no DrugRef search key');
      await rx.locator(`#instructions_${card}`).fill('1 tablet daily');
      await rx.locator(`#instructions_${card}`).blur();
      const [previewResponse] = await Promise.all([
        rx.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/rx/viewScript'),
          { timeout: 30000 }),
        rx.locator('#saveButton').click(),
      ]);
      h.assert(previewResponse.ok(), 'Save and Print failed to open its preview');
      const scriptId = new URL(previewResponse.url()).searchParams.get('scriptId');
      h.assert(/^[1-9]\d*$/.test(scriptId ?? ''), 'The preview did not identify a saved script');
      const savedDrug = () => sql.rows(`SELECT GN, BN, regional_identifier FROM drugs
        WHERE demographic_no=${patient} AND script_no=${scriptId}`);
      const rows = savedDrug();
      h.assert(rows.length === 1, 'The preview did not have exactly one saved drug');
      const [[generic, , din]] = rows;
      h.assert(generic?.startsWith(`${product.term} `) && din === product.din,
        'Save and Print filed the wrong catalogue product');

      const preview = rx.frameLocator('iframe[name="carlosPrescriptionPreview"]');
      const link = preview.locator('a[href*="/rx/drugInfo?"]');
      const target = new URL(await link.getAttribute('href'), config.baseUrl);
      h.assert(target.origin === new URL(config.baseUrl).origin, 'Drug Information does not link to CARLOS');
      h.assert(target.searchParams.get('DIN') === din && target.searchParams.get('GN') === generic,
        'The preview link does not identify the saved prescription');

      const info = await s.popup(rx, link, 'preview-drug-info');
      const details = info.locator('#drug-reference-details');
      await details.waitFor({ state: 'visible' });
      h.assert(info.url() && new URL(info.url()).origin === target.origin, 'Drug Information left CARLOS');
      h.assert((await details.locator('h2').innerText()).trim() === product.brand, 'The popup showed the wrong product');
      h.assert((await details.locator('#drug-info-din').innerText()).trim() === product.din, 'The popup showed the wrong DIN');
      h.assert((await details.locator('dd').allTextContents()).some(text => text.trim() === 'TABLET'),
        'The popup did not show the tablet form');
      const ingredients = details.locator('tbody tr');
      h.assert(await ingredients.count() === 1, 'The popup showed the wrong ingredient count');
      const [ingredient, strength, unit] = await ingredients.locator('td').allTextContents();
      h.assert(ingredient.trim() === product.term && Number(strength) === product.strength && unit.trim() === 'MG',
        'The popup showed the wrong ingredient or strength');
      await h.gotoApp(info, config.baseUrl, `/rx/drugInfo?BN=${productKey}`);
      h.assert((await details.locator('h2').innerText()).trim() === product.brand,
        'The generic result showed an unrelated brand label');
      h.assert((await details.locator('#drug-info-din').innerText()).trim() === product.din,
        'The generic result showed the wrong DIN');
      h.assert(JSON.stringify(savedDrug()) === JSON.stringify(rows), 'Viewing Drug Information changed the saved drug');

      await info.close();
      await rx.close();
    });
  }
}

if (require.main === module) runWorkflow('rx-preview-drug-info', workflow, { openMaster: false });
module.exports = { workflow };
