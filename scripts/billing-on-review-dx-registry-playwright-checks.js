#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario bill review ▸ Dx quick pick ▸ "Add to Disease Registry": the review page adds the ticked quick-list codes to the
 * patient's disease registry without saving the bill.
 *
 * WHY THIS CHECK EXISTS. The review page carries the clinic's Dx quick lists (oscarResearch/oscarDxResearch/quickCodeList)
 * under the bill, and its button posts the ticked codes to oscarResearch/oscarDxResearch/dxResearch in the background. The
 * review-page checks (gap-billing-private-bill-save-actions, billing-review-failed-calculation, double-submit-billing-on)
 * press its Save, Settle and Back to Edit buttons; dashboard-display drives a different page's bulk "add to registry", and
 * dx-registry-quicklist the registry page's own quick list. Nothing pressed the review page's button.
 *
 * User path: Schedule ▸ owned appointment "B" (bill form) ▸ the owned billing physician, A007A and a diagnostic code ▸ Next
 * (ViewBillingONReview) ▸ the Dx quick pick box ▸ tick a code ▸ Add to Disease Registry.
 * Asserted:
 *   1. With nothing ticked the button alerts and posts nothing.
 *   2. With a code ticked, dismissing the confirmation posts nothing and writes no registry row.
 *   3. Confirming posts exactly the ticked code for the owned patient and adds one active dxresearch row (that code, the
 *      quick list's coding system); the refreshed quick pick no longer offers it, the review page stays with its Save
 *      button, and no claim is saved.
 * Fixtures: createBillingFixture (owned billing provider and HIN on the owned patient), one owned appointment today, and
 * an owned Dx quick list (two quickList rows named with the run marker: ICD-9 401 and 493). quickList is clinic-wide: every
 * review page and the registry page list it while the run lasts, so the check must not run concurrently with other checks
 * (live wrapper: EXCLUSIVE=1). Cleanup removes the quick list, the patient's dxresearch rows and any claim the browser
 * wrote, and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const g = require('./lib/gap-billing-support');

const q = h.sqlString;
const DX_POST = r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/oscarResearch/oscarDxResearch/dxResearch');

async function workflow(s) {
  const { sql, patient } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const registry = () => sql.rows(`SELECT dxresearch_code, coding_system, status FROM dxresearch WHERE demographic_no=${patient}
    ORDER BY dxresearch_no`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient}`);
    h.assert(registry().length === 0, 'The owned patient\'s disease registry rows were not removed');
  });
  h.assert(registry().length === 0, 'The owned patient already has disease registry rows');
  // An owned quick list of two ICD-9 codes: one to add, one that must stay offered. The demo's own quick lists name
  // ICHPPC codes, which a schema without the ichppc table cannot describe, so the review page offers none of them.
  const system = 'icd9';
  const [code, kept] = ['401', '493'];
  h.assert(sql.value(`SELECT COUNT(*) FROM icd9 WHERE icd9 IN (${q(code)},${q(kept)})`) === '2', 'The ICD-9 codes 401 and 493 are not installed');
  s.cleanup(() => {
    sql.execute(`DELETE FROM quickList WHERE quickListName=${q(s.marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM quickList WHERE quickListName=${q(s.marker)}`) === '0', 'The owned Dx quick list was not removed');
  });
  sql.execute(`INSERT INTO quickList (quickListName,createdByProvider,dxResearchCode,codingSystem)
    VALUES (${q(s.marker)},${q(s.provider)},${q(code)},${q(system)}),(${q(s.marker)},${q(s.provider)},${q(kept)},${q(system)})`);
  const appointment = g.seedAppointment(s, { time: '16:15:00' });
  await g.showSeededAppointments(s);

  let review;
  const offered = () => review.locator('#dxListing input[name="xml_research"]').evaluateAll(boxes => boxes.map(box => box.value));
  const button = () => review.locator('#dxForm input[type="button"][onclick^="addToDiseaseRegistry"]');
  /** POSTs to dxResearch while `body` runs and briefly after. */
  async function postsDuring(body) {
    const posted = [];
    const watch = request => { if (request.method() === 'POST' && /\/oscarDxResearch\/dxResearch$/.test(new URL(request.url()).pathname)) posted.push(request); };
    review.on('request', watch);
    try {
      await body();
      await review.waitForTimeout(1500);
    } finally {
      review.off('request', watch);
    }
    return posted;
  }

  await s.step('with nothing ticked, Add to Disease Registry alerts and posts nothing', async () => {
    review = await g.openBillForm(s, appointment);
    await g.chooseBillingPhysician(review, owned.providerNo);
    await review.locator('input[name="serviceCode0"]').fill('A007A');
    await review.locator('input[name="dxCode"]').fill('250');
    await g.nextToReview(review);
    await review.locator('#dxForm').waitFor({ state: 'attached', timeout: 30000 });
    const shown = await offered();
    h.assert(shown.includes(`${system},${code}`) && shown.includes(`${system},${kept}`),
      `The review page's Dx quick pick does not offer both codes of the owned quick list (it offers ${JSON.stringify(shown)})`);
    let dialogs;
    const posted = await postsDuring(async () => {
      dialogs = await h.withExpectedDialogs(review, () => button().click());
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', `Nothing ticked raised ${JSON.stringify(dialogs)}, not one alert`);
    h.assert(posted.length === 0, 'Add to Disease Registry with nothing ticked still posted to dxResearch');
    h.assert(registry().length === 0, 'Add to Disease Registry with nothing ticked wrote a registry row');
  });

  await s.step('with a code ticked, dismissing the confirmation posts nothing', async () => {
    await review.locator(`#dxListing input[name="xml_research"][value="${system},${code}"]`).check();
    let dialogs;
    const posted = await postsDuring(async () => {
      dialogs = await h.withExpectedDialogs(review, () => button().click(), { accept: false });
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `Add to Disease Registry raised ${JSON.stringify(dialogs)}, not one confirmation`);
    h.assert(posted.length === 0, 'A dismissed confirmation still posted to dxResearch');
    h.assert(registry().length === 0, 'A dismissed confirmation wrote a registry row');
  });

  await s.step('confirming adds exactly the ticked code to the patient\'s registry and leaves the bill unsaved', async () => {
    const before = (await offered()).filter(value => value !== `${system},${code}`);
    let response;
    const dialogs = await h.withExpectedDialogs(review, async () => {
      [response] = await Promise.all([review.waitForResponse(DX_POST, { timeout: 30000 }), button().click()]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Add to Disease Registry did not ask for confirmation');
    const sent = new URLSearchParams(response.request().postData() || '');
    h.assert(sent.get('demographicNo') === patient && JSON.stringify(sent.getAll('xml_research')) === JSON.stringify([`${system},${code}`]),
      `Add to Disease Registry posted ${JSON.stringify(Object.fromEntries(sent))}, not the ticked code for the owned patient`);
    // dxResearch answers with a redirect to the patient's refreshed quick code list, which the page's XHR follows.
    const next = response.status() === 302 ? new URL(response.headers().location || '', response.url()) : null;
    h.assert(next && next.pathname.endsWith('/oscarResearch/oscarDxResearch/ViewQuickCodeList')
      && next.searchParams.get('demographicNo') === patient,
    `Add to Disease Registry answered HTTP ${response.status()}, not the redirect to the patient's refreshed quick code list`);
    h.assert(JSON.stringify(registry()) === JSON.stringify([[code, system, 'A']]),
      `The patient's registry holds ${JSON.stringify(registry())}, not one active ${system} ${code} row`);
    await review.waitForFunction(value => !document.querySelector(`#dxListing input[name="xml_research"][value="${value}"]`),
      `${system},${code}`, { timeout: 15000 });
    h.assert(JSON.stringify(await offered()) === JSON.stringify(before) && before.includes(`${system},${kept}`),
      'The refreshed quick pick does not offer exactly the other codes it offered before');
    h.assert(await review.locator('input[type="submit"][value="Save"]').count() === 1, 'The review page lost its Save button');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`) === '0',
      'Add to Disease Registry saved a claim');
    await review.close();
  });
}

if (require.main === module) runWorkflow('billing-on-review-dx-registry', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
