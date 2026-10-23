#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The BC WCB form's code lookups: the Search links beside ICD9, Body Part and Nature of Injury open
 * billing/CA/BC/support/Icd9, support/BodyPart and support/NatureInjury, which list the codes matching the box's
 * text, and a pick writes the code back into that box. billing-bc-wcb-form fills these boxes by typing;
 * billing-bc-adjust-bill-code-search's diagnosis search is Adjust Bill's ViewBillingDigNewSearch, another page.
 *
 * User path: Master record ▸ Create Invoice ▸ billing physician ▸ Billing Type WCB ▸ New WCB Form (viewformwcb) ▸
 * Search beside ICD9 / Body Part / Nature of Injury ▸ a code in the popup. Nothing is saved.
 * Asserted:
 *   - New WCB Form opens the form with its ICD9, Body Part and Nature of Injury boxes and their Search links;
 *   - ICD9: the lookup lists the codes for the box's text, and a pick fills exactly the ICD9 box and closes the popup;
 *   - Body Part and Nature of Injury: the lookup lists the owned code for the box's text, and a pick fills exactly
 *     that box and closes the popup; the other boxes keep what they held;
 *   - LAST: the install lists WorkSafeBC codes of its own: the Side list has options and both lookups list codes other
 *     than the owned ones. On a Flyway-built BC install wcb_side, wcb_bp_code and wcb_noi_code were empty until
 *     bc/V1.0.59.3 restored their reference rows (#5062).
 * Fixtures: one billing physician (bc.createBillingPhysician) and the owned FAKE patient made a BC patient; one
 * wcb_bp_code and one wcb_noi_code row carrying the run marker, with a non-numeric code ("Q" and 4 hex) no WorkSafeBC
 * code uses. The two code tables are clinic-wide (every login's lookups list the rows while the run lasts), so the check
 * must not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup deletes the owned code rows by
 * marker, then the patient's BC billing rows and the physician, and asserts each is gone. BC installs only.
 */
const h = require('./lib/playwright-harness');
const bc = require('./lib/bc-billing-fixture');
const { runWorkflow } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const TIMEOUT = 30000;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Create Invoice ▸ WCB ▸ New WCB Form opens the form with its ICD9, Body Part and Nature of Injury lookups',
  icd9: 'the ICD9 lookup lists the codes for the typed text and a pick fills exactly the ICD9 box',
  bodyPart: 'the Body Part lookup lists the owned code and a pick fills exactly the Body Part box',
  injury: 'the Nature of Injury lookup lists the owned code and a pick fills exactly the Nature of Injury box',
  codes: 'the install lists WorkSafeBC side, body part and nature-of-injury codes of its own',
};

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
}

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const hex = marker.slice(-4).toUpperCase();
  const owned = { bp: `Q${hex}`, noi: `Q${hex}` };
  const ownedRows = () => sql.value(`SELECT (SELECT COUNT(*) FROM wcb_bp_code WHERE level1=${q(marker)})
    + (SELECT COUNT(*) FROM wcb_noi_code WHERE level1=${q(marker)})`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM wcb_bp_code WHERE level1=${q(marker)}; DELETE FROM wcb_noi_code WHERE level1=${q(marker)}`);
    h.assert(ownedRows() === '0', 'The owned WCB code rows were not removed');
  });
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM wcb_bp_code WHERE code=${q(owned.bp)})
    + (SELECT COUNT(*) FROM wcb_noi_code WHERE code=${q(owned.noi)})`) === '0', 'A WCB code already uses the owned code');
  sql.execute(`INSERT INTO wcb_bp_code (level1, level2, level3, code) VALUES (${q(marker)}, 'FAKE body part', 'FAKE', ${q(owned.bp)});
    INSERT INTO wcb_noi_code (level1, level2, level3, code) VALUES (${q(marker)}, 'FAKE injury', 'FAKE', ${q(owned.noi)})`);
  h.assert(ownedRows() === '2', 'The owned WCB code rows were not created');
  const physician = bc.createBillingPhysician(s);
  bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  // An ICD9 code the BC reference data ships, searched by its own number.
  const icd9 = sql.value("SELECT diagnostic_code FROM diagnosticcode WHERE diagnostic_code='250' LIMIT 1");
  h.assert(icd9 === '250', 'The diagnostic code list has no 250 to search for');

  let wcb;
  const box = name => wcb.locator(`[name="${name}"]`).first();
  const boxes = async () => Object.fromEntries(await Promise.all(['w_icd9', 'w_bp', 'w_noi'].map(async n => [n, await box(n).inputValue()])));
  /** Type into the box, press its Search link, and return the lookup popup once it has loaded. */
  async function lookup(field, fn, text) {
    await box(field).fill(text);
    const popup = await s.popup(wcb, wcb.locator(`a[onclick*="${fn}('WCBForm','${field}')"]`), `wcb-lookup-${field}`);
    await popup.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(popup, `the ${field} lookup`);
    return popup;
  }
  const codeLinks = popup => popup.locator('a[onclick*="posttoText("]');
  /** Pick `code` in the popup: the popup closes and the box holds the code. */
  async function pick(popup, field, code) {
    const link = popup.locator(`a[onclick*="posttoText('${code}')"]`);
    h.assert(await link.count() >= 1, `The ${field} lookup does not list ${code}`);
    await Promise.all([popup.waitForEvent('close', { timeout: TIMEOUT }), link.first().click({ noWaitAfter: true })]);
    await wcb.waitForFunction(([name, value]) => document.WCBForm[name].value === value, [field, code], { timeout: TIMEOUT });
  }

  await s.step(STEP.open, async () => {
    // The form page's first open compiles a large JSP; a plain request first, so the popup's wait measures the page.
    await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/billing/CA/BC/viewformwcb?demographic_no=${s.patient}&formId=0&provNo=${physician.providerNo}&hideToBill=true`),
      { timeout: 120000 });
    const link = masterRecordLink(s.master, '/billing?billRegion=', 'appointment_no=0&').first();
    h.assert(await link.count() === 1, 'The master record has no Create Invoice link');
    const form = await s.popup(s.master, link, 'bc-bill-form');
    await form.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(form, 'BC bill form');
    await bc.changeBillFormSelect(form, '#xml_provider', physician.providerNo, 2, 'choosing the billing physician');
    await Promise.all([
      form.waitForResponse(r => new URL(r.url()).pathname.endsWith('/billing/CA/BC/ViewWcbForms'), { timeout: TIMEOUT }),
      form.locator('#xml_billtype').selectOption('WCB'),
    ]);
    await form.waitForFunction(() => !window.jQuery || window.jQuery.active === 0, null, { timeout: TIMEOUT });
    wcb = await s.popup(form, form.locator('#wcbForms a', { hasText: 'New WCB Form' }), 'wcb-form');
    await wcb.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(wcb, 'WCB form');
    for (const [field, fn] of [['w_icd9', 'popICD9List'], ['w_bp', 'popBodyPartList'], ['w_noi', 'popNOIList']]) {
      h.assert(await box(field).count() === 1, `The WCB form has no ${field} box`);
      h.assert(await wcb.locator(`a[onclick*="${fn}('WCBForm','${field}')"]`).count() === 1, `The WCB form has no Search link for ${field}`);
    }
  });

  await s.step(STEP.icd9, async () => {
    const popup = await lookup('w_icd9', 'popICD9List', icd9);
    const listed = await codeLinks(popup).count();
    h.assert(listed > 0, `The ICD9 lookup lists no code for "${icd9}"`);
    const before = await boxes();
    await pick(popup, 'w_icd9', icd9);
    const after = await boxes();
    h.assert(after.w_icd9 === icd9 && after.w_bp === before.w_bp && after.w_noi === before.w_noi,
      `The ICD9 pick left the boxes as ${JSON.stringify(after)}`);
  });

  await s.step(STEP.bodyPart, async () => {
    const popup = await lookup('w_bp', 'popBodyPartList', marker);
    const listed = await codeLinks(popup).evaluateAll(links => links.map(link => link.textContent.trim()));
    h.assert(JSON.stringify(listed) === JSON.stringify([owned.bp]), `The Body Part lookup for the marker lists ${JSON.stringify(listed)}`);
    const before = await boxes();
    await pick(popup, 'w_bp', owned.bp);
    const after = await boxes();
    h.assert(after.w_bp === owned.bp && after.w_icd9 === before.w_icd9 && after.w_noi === before.w_noi,
      `The Body Part pick left the boxes as ${JSON.stringify(after)}`);
  });

  await s.step(STEP.injury, async () => {
    const popup = await lookup('w_noi', 'popNOIList', marker);
    const listed = await codeLinks(popup).evaluateAll(links => links.map(link => link.textContent.trim()));
    h.assert(JSON.stringify(listed) === JSON.stringify([owned.noi]), `The Nature of Injury lookup for the marker lists ${JSON.stringify(listed)}`);
    const before = await boxes();
    await pick(popup, 'w_noi', owned.noi);
    const after = await boxes();
    h.assert(after.w_noi === owned.noi && after.w_icd9 === before.w_icd9 && after.w_bp === before.w_bp,
      `The Nature of Injury pick left the boxes as ${JSON.stringify(after)}`);
  });

  await s.step(STEP.codes, async () => {
    const sides = await wcb.locator('#w_side option').count();
    const others = {};
    for (const [field, fn, code] of [['w_bp', 'popBodyPartList', owned.bp], ['w_noi', 'popNOIList', owned.noi]]) {
      const popup = await lookup(field, fn, '');
      others[field] = await codeLinks(popup).evaluateAll((links, mine) => links.filter(link => link.textContent.trim() !== mine).length, code);
      await popup.close();
    }
    h.assert(sides > 0 && others.w_bp > 0 && others.w_noi > 0,
      `The install lists ${sides} side(s), ${others.w_bp} body part code(s) and ${others.w_noi} nature-of-injury code(s) of its own (#5062)`);
  });
}

if (require.main === module) runWorkflow('billing-bc-wcb-code-pickers', workflow, { preflight });
module.exports = { workflow, STEP };
