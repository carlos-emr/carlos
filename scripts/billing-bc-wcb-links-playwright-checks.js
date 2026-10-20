#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The two links into the BC WCB pages that billing-bc-wcb-form does not take (#4439 D10 and D11, #4965): Chart ▸
 * Forms ▸ BC-WCB, and the billing simulation's correction link for a WCB claim.
 * User paths:
 *   - Administration ▸ Select Forms adds BC-WCB to the encounter form list (the BC seed ships it off the list:
 *     encounterForm.hidden is the display order, and 0 means not shown) ▸ the patient's chart ▸ Forms ▸ BC-WCB (a
 *     popup: the seeded form value, billing/CA/BC/viewformwcb, with the patient, formId=0 and the provider);
 *   - Administration ▸ Billing ▸ Simulate Submission File (billing/CA/BC/ViewBillingSim) ▸ the owned billing
 *     physician ▸ Create Report (billing/CA/BC/ViewGenSimulation, a dry run) ▸ the WCB claim's invoice link (a popup:
 *     billing/CA/BC/billingTeleplanCorrectionWCB, the claim's correction page) ▸ Settle Bill.
 * Asserted:
 *   - Select Forms adds BC-WCB, and the chart's Forms menu offers it;
 *   - Chart ▸ Forms ▸ BC-WCB opens a new WCB form for the patient;
 *   - the simulation lists the owned WCB claim, and its invoice link opens that claim's correction page;
 *   - Settle Bill on the correction page comes back to it, and the claim is settled (status S, one history row);
 *   - LAST: Select Forms removes BC-WCB again, preserving the original visible menu and its relative order.
 * Fixtures: the owned FAKE- patient made a BC patient; one billing physician saved through Administration before
 * the simulation, so its active-provider cache sees the owned physician even on a warm install; one open WCB claim
 * for them (billing, billingmaster and a wcb row for fee item 19937). BC-WCB is
 * shown on the clinic's form list while the check runs, and the physician shows in clinic-wide lists, so the check
 * must not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Select Forms normalizes tied seed display
 * positions; the check asserts the exact resulting order, and cleanup puts the recorded raw display order
 * back, deletes the patient's wcb rows, the claim's history rows, billingmaster and billing rows, and the physician,
 * and asserts each is gone.
 */
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { runWorkflow } = require('./lib/workflow-session');
const { withProviderCleanup } = require('./billing-bc-simulation-encoding-playwright-checks');

const TIMEOUT = 30000;
const WCB_FORM = 'BC-WCB';
/** A WCB service code with a fee row: the simulation lists a WCB claim only joined to its fee item's billingservice. */
const WCB_FEE_ITEM = '19937';
const MIN_PANEL_CHARS = 500;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  shown: 'Select Forms adds BC-WCB, and the chart\'s Forms menu offers it',
  form: 'Chart ▸ Forms ▸ BC-WCB opens a new WCB form for the patient',
  correction: 'the simulation lists the owned WCB claim, and its invoice link opens the claim\'s correction page',
  settle: 'Settle Bill on the correction page comes back to it, and the claim is settled',
  restored: 'Select Forms removes BC-WCB again, preserving the original visible form order',
};

/** Every encounterForm row, so the end state can be compared with the start, display order included. */
function encounterForms(sql) {
  return sql.rows('SELECT form_name,form_value,form_table,hidden FROM encounterForm ORDER BY form_name,form_value');
}

/** Select Forms assigns distinct 1-based positions in the visible list; hidden forms have position 0. */
function formsAfterSelection(before, selected) {
  h.assert(new Set(selected).size === selected.length, 'Select Forms repeated a selected form');
  for (const name of selected) {
    h.assert(before.filter(row => row[0] === name).length === 1, 'Select Forms listed an unknown or ambiguous form');
  }
  return before.map(row => [...row.slice(0, 3), String(selected.indexOf(row[0]) + 1)]);
}

async function openSelectForms(page, s) {
  await h.gotoApp(page, s.config.baseUrl, '/administration');
  await page.locator('#dynamic-content').waitFor({ state: 'attached', timeout: TIMEOUT });
  // The navigation accordion can keep the link collapsed; its click handler is what loads the panel.
  await page.evaluate(() => {
    const link = [...document.querySelectorAll('a.contentLink')]
      .find(anchor => /\/form\/setupSelect$/.test(anchor.getAttribute('href') || ''));
    if (!link) throw new Error('Administration has no Select Forms link');
    link.click();
  });
  await page.locator('#dynamic-content #selectForm').waitFor({ state: 'attached', timeout: TIMEOUT });
}

async function selectFormsLists(page) {
  return page.evaluate(() => {
    const panel = document.getElementById('dynamic-content');
    const options = name => [...panel.querySelectorAll(`select[name="${name}"] option`)].map(o => o.value);
    return { available: options('selectedAddTypes'), selected: options('selectedDeleteTypes') };
  });
}

/** Moves one form between the Select Forms lists with the panel's own button, then waits for the new panel. */
async function moveForm(page, { button, list, name }) {
  const stale = await page.locator('#dynamic-content #selectForm').elementHandle();
  const posted = page.waitForResponse(r => r.request().method() === 'POST'
    && new URL(r.url()).pathname.endsWith('/form/select'), { timeout: TIMEOUT });
  await page.locator(`#dynamic-content select[name="${list}"]`).selectOption(name);
  await page.locator(`#dynamic-content ${button}`).click();
  const response = await posted;
  h.assert(response.status() === 200 && (await response.text()).length >= MIN_PANEL_CHARS,
    `Select Forms ${button} for ${name} did not return the panel (HTTP ${response.status()})`);
  await page.waitForFunction(old => {
    const form = document.querySelector('#dynamic-content #selectForm');
    return !!form && form !== old;
  }, stale, { timeout: TIMEOUT });
  return selectFormsLists(page);
}

/**
 * The Forms menu's BC-WCB item, which starts a new form. The claim's wcb row also lists in the Forms module as a
 * started BC-WCB form, so the link is looked up in the "+" menu (#menu1) only.
 */
function newFormLink(chart) {
  return chart.locator('#menu1').getByRole('link', { name: WCB_FORM, exact: true });
}

// The billing physician, made before login (see the header); runWorkflow passes the test login's provider.
let physician = null;
async function preflight({ sql, provider }) {
  bc.requireBcBillingSchema(sql);
  if (sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(WCB_FEE_ITEM)}`) === '0') {
    throw new h.SkipCheck(`This BC check needs fee item ${WCB_FEE_ITEM} in billingservice`);
  }
  if (!encounterForms(sql).some(row => row[0] === WCB_FORM)) {
    throw new h.SkipCheck('This BC check needs the BC encounter form seed (BC-WCB)');
  }
  const q = h.sqlString;
  const marker = `FAKE-PW${randomBytes(8).toString('hex')}`;
  const providerNo = bc.unusedNumber('97', 6, c => sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(c)}`) !== '0');
  const ohipNo = bc.unusedNumber('9', 5, c => sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE ohip_no=${q(c)})
    + (SELECT COUNT(*) FROM billingmaster WHERE practitioner_no=${q(c)})`) !== '0');
  const payeeNo = bc.unusedNumber('8', 5, c => sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE billing_no=${q(c)})
    + (SELECT COUNT(*) FROM billingmaster WHERE payee_no=${q(c)})`) !== '0');
  const columns = sql.value(`SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='provider'`).split(',');
  const overrides = { provider_no: q(providerNo), ohip_no: q(ohipNo), billing_no: q(payeeNo),
    last_name: q(marker), first_name: "'Billing'", status: "'1'" };
  // A backstop for a run that dies before its cleanup registers; the cleanup removes the row and asserts it gone.
  process.once('exit', () => {
    try {
      sql.execute(`DELETE FROM provider WHERE provider_no=${q(providerNo)} AND last_name=${q(marker)}`);
    } catch {
      // The live wrapper's residue counters report a physician left behind.
    }
  });
  sql.execute(`INSERT INTO provider (${columns.map(c => `\`${c}\``).join(',')})
    SELECT ${columns.map(c => overrides[c] || `\`${c}\``).join(',')} FROM provider WHERE provider_no=${q(provider)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(providerNo)} AND ohip_no=${q(ohipNo)}`) === '1',
    'The owned billing physician was not created');
  physician = { providerNo, ohipNo, payeeNo, marker };
}

/** One open WCB claim for the owned patient and physician: billing, billingmaster and its wcb row. */
function seedWcbClaim(s, phn) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  const billingNo = sql.value(`INSERT INTO billing (clinic_no,demographic_no,provider_no,appointment_no,demographic_name,
      billing_date,billing_time,update_date,update_time,status,provider_ohip_no,billingtype,creator,total)
    VALUES (0,${patient},${q(physician.providerNo)},0,${q(`${marker},Workflow`)},CURDATE(),CURTIME(),CURDATE(),CURTIME(),
      'O',${q(physician.ohipNo)},'WCB',${q(physician.providerNo)},'23.00'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingNo), 'The WCB claim was not created');
  const billingmasterNo = sql.value(`INSERT INTO billingmaster (billing_no,createdate,billingstatus,demographic_no,appointment_no,
      claimcode,datacenter,payee_no,practitioner_no,phn,name_verify,dependent_num,billing_unit,clarification_code,
      anatomical_area,after_hour,new_program,billing_code,bill_amount,payment_mode,service_date,service_to_day,
      submission_code,extended_submission_code,dx_code1,service_location,referral_flag1,referral_flag2,birth_date,
      correspondence_code,claim_comment,mva_claim_code,paymentMethod)
    VALUES (${billingNo},NOW(),'O',${patient},0,'C02','00000',${q(physician.payeeNo)},${q(physician.ohipNo)},${q(phn)},'WOR',
      '00','1','00','00','0','00',${q(WCB_FEE_ITEM)},'23.00','0',DATE_FORMAT(CURDATE(),'%Y%m%d'),'00','0',' ','780','A',
      '0','0','19800102','0','','N',6); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingmasterNo), 'The WCB claim\'s billingmaster row was not created');
  sql.execute(`INSERT INTO wcb (billing_no,demographic_no,provider_no,formCreated,formEdited,w_reporttype,w_fname,w_lname,
      w_dob,w_city,w_phn,w_servicedate,w_doi,status,w_feeitem,w_extrafeeitem,w_duration,formNeeded,w_icd9,w_pracno,w_payeeno)
    VALUES (${billingNo},${patient},${q(physician.providerNo)},NOW(),NOW(),'F','Workflow',${q(marker.slice(0, 18))},'1980-01-02',
      'Vancouver',${q(phn)},CURDATE(),CURDATE(),'O',${q(WCB_FEE_ITEM)},'',1,0,'780',${q(physician.ohipNo)},${q(physician.payeeNo)})`);
  return { billingNo, billingmasterNo };
}

async function workflow(s) {
  const { sql } = s;
  const q = h.sqlString;
  s.cleanup(() => {
    const owned = `provider_no=${q(physician.providerNo)}`;
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE ${owned} AND last_name=${q(physician.marker)}`) === '1',
      'Billing physician ownership changed');
    for (const table of ['program_provider', 'provider_facility', 'secUserRole', 'providersite',
      'property', 'providerArchive', 'providerbillcenter']) {
      sql.execute(`DELETE FROM ${table} WHERE ${owned}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${owned}`) === '0',
        `The owned billing physician's ${table} rows were not removed`);
    }
    sql.execute(`DELETE FROM provider WHERE ${owned} AND last_name=${q(physician.marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE ${owned}`) === '0', 'The owned billing physician was not removed');
  });
  const admin = await s.context.newPage();
  async function saveStatus(active) {
    await h.gotoApp(admin, s.config.baseUrl, `/admin/ViewProviderUpdateProvider?keyword=${encodeURIComponent(physician.providerNo)}`);
    const form = admin.locator('form[name="updatearecord"]');
    await form.locator(active ? '#statusActive' : '#statusInactive').check();
    await Promise.all([
      admin.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
      form.locator('input[type="submit"][name="subbutton"]').click(),
    ]);
    h.assert(sql.value(`SELECT status FROM provider WHERE provider_no=${q(physician.providerNo)}`) === (active ? '1' : '0'),
      'The owned billing physician was not saved through Administration');
  }
  // Only an application-side provider save evicts the cached provider list. A direct SQL
  // fixture was invisible after earlier checks warmed it, and its deletion left a ghost.
  return withProviderCleanup(async () => {
    await saveStatus(true);
    await runScenario(s);
  }, () => saveStatus(false), () => admin.close());
}

async function runScenario(s) {
  const { sql, patient } = s;
  const q = h.sqlString;
  h.assert(physician, 'The billing physician was not made before login');
  const before = encounterForms(sql);
  const shownBefore = Number(before.find(row => row[0] === WCB_FORM)[3]) > 0;
  s.cleanup(() => {
    // A run that failed before the last step still owes the clinic its form list: put the recorded display order back.
    for (const [name, value, , hidden] of before) {
      sql.execute(`UPDATE encounterForm SET hidden=${Number(hidden)}
        WHERE form_name=${q(name)} AND form_value=${q(value)} AND hidden<>${Number(hidden)}`);
    }
    h.assert(JSON.stringify(encounterForms(sql)) === JSON.stringify(before), 'The encounter form list is not as the check found it');
  });
  let claim = null;
  s.cleanup(() => {
    sql.execute(`DELETE FROM wcb WHERE demographic_no=${patient}`);
    if (claim) {
      sql.execute(`DELETE FROM billing_history WHERE billingmaster_no=${claim.billingmasterNo};
        DELETE FROM billingmaster WHERE billing_no=${claim.billingNo} AND demographic_no=${patient};
        DELETE FROM billing WHERE billing_no=${claim.billingNo} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${claim.billingmasterNo}`) === '0',
        'The claim\'s history rows were not removed');
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM wcb WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billingmaster WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billing WHERE demographic_no=${patient})`) === '0', 'The owned WCB claim was not removed');
  });
  const phn = bc.makeBcPatient(s);
  claim = seedWcbClaim(s, phn);

  let chart;
  let shownNamesBefore;
  await s.step(STEP.shown, async () => {
    const admin = await s.context.newPage();
    await openSelectForms(admin, s);
    shownNamesBefore = (await selectFormsLists(admin)).selected;
    h.assert(shownNamesBefore.includes(WCB_FORM) === shownBefore,
      'Select Forms and the stored BC-WCB visibility disagree');
    if (!shownBefore) {
      const lists = await moveForm(admin, { button: '#add', list: 'selectedAddTypes', name: WCB_FORM });
      h.assert(JSON.stringify(lists.selected) === JSON.stringify([...shownNamesBefore, WCB_FORM])
        && !lists.available.includes(WCB_FORM), 'Select Forms did not append BC-WCB without changing the existing menu');
      h.assert(JSON.stringify(encounterForms(sql)) === JSON.stringify(formsAfterSelection(before, lists.selected)),
        'Select Forms did not persist exactly the displayed order after adding BC-WCB');
    }
    await admin.close();
    h.assert(Number(sql.value(`SELECT hidden FROM encounterForm WHERE form_name=${q(WCB_FORM)}`)) > 0,
      'BC-WCB is still off the encounter form list');
    chart = await s.chart();
    await ui.openFormsMenu(chart);
    await newFormLink(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
  });

  await s.step(STEP.form, async () => {
    await ui.openFormsMenu(chart);
    const form = await s.popup(chart, newFormLink(chart), 'bc-wcb-new');
    await form.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(form, 'BC-WCB from the chart');
    const opened = new URL(form.url());
    h.assert(opened.pathname.endsWith('/billing/CA/BC/viewformwcb'), `BC-WCB opened ${opened.pathname}, not the WCB form route`);
    h.assert(opened.searchParams.get('demographic_no') === patient && opened.searchParams.get('formId') === '0',
      'BC-WCB did not open a new form for the patient');
    h.assert(await form.locator('form[name="WCBForm"]').count() === 1, 'BC-WCB did not show the WCB form');
    h.assert(await form.locator('[name="w_phn"]').inputValue() === phn, 'The new WCB form is not the patient\'s');
    await form.close();
  });

  let correction;
  await s.step(STEP.correction, async () => {
    const sim = await s.context.newPage();
    await h.gotoApp(sim, s.config.baseUrl, '/billing/CA/BC/ViewBillingSim');
    await h.assertNotErrorPage(sim, 'billing simulation form');
    await sim.locator('select[name="providers"]').selectOption(physician.ohipNo);
    await Promise.all([
      sim.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
      sim.locator('input[type="submit"][value="Create Report"]').click(),
    ]);
    await h.assertNotErrorPage(sim, 'billing simulation report');
    const link = sim.locator('a[onclick*="billingTeleplanCorrectionWCB"]').filter({ hasText: claim.billingNo }).first();
    h.assert(await link.count() === 1, 'The simulation report does not list the owned WCB claim');
    correction = await s.popup(sim, link, 'wcb-correction');
    await correction.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(correction, 'WCB correction page');
    const opened = new URL(correction.url());
    h.assert(opened.pathname.endsWith('/billing/CA/BC/billingTeleplanCorrectionWCB')
      && Number(opened.searchParams.get('billing_no')) === Number(claim.billingmasterNo),
    `The invoice link opened ${opened.pathname}, not the claim's correction page`);
    h.assert(await correction.locator('form[action$="/billing/CA/BC/billingTeleplanCorrectionWCB"]').count() === 1,
      'The correction page shows no correction form');
    await sim.close();
  });

  await s.step(STEP.settle, async () => {
    await Promise.all([
      correction.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
      correction.locator('input[type="submit"][value="Settle Bill"]').click(),
    ]);
    await h.assertNotErrorPage(correction, 'WCB correction page after Settle Bill');
    const back = new URL(correction.url());
    h.assert(back.pathname.endsWith('/billing/CA/BC/billingTeleplanCorrectionWCB')
      && Number(back.searchParams.get('billing_no')) === Number(claim.billingmasterNo),
    `Settle Bill ended at ${back.pathname}, not the claim's correction page`);
    h.assert(sql.value(`SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${claim.billingmasterNo}`) === 'S',
      'Settle Bill did not settle the claim');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${claim.billingmasterNo}`) === '1',
      'Settle Bill did not record the claim\'s history');
    await correction.close();
  });

  await s.step(STEP.restored, async () => {
    if (!shownBefore) {
      const admin = await s.context.newPage();
      await openSelectForms(admin, s);
      const lists = await moveForm(admin, { button: '#delete', list: 'selectedDeleteTypes', name: WCB_FORM });
      h.assert(JSON.stringify(lists.selected) === JSON.stringify(shownNamesBefore) && lists.available.includes(WCB_FORM),
        'Removing BC-WCB changed the original visible form menu');
      await admin.close();
    }
    // The BC seed shares positions across forms. The first Select Forms mutation
    // intentionally normalizes them (#4945); cleanup restores the exact raw seed.
    const expected = shownBefore ? before : formsAfterSelection(before, shownNamesBefore);
    h.assert(JSON.stringify(encounterForms(sql)) === JSON.stringify(expected),
      'Removing BC-WCB did not persist exactly the original visible menu with distinct positions');
  });
}

if (require.main === module) runWorkflow('billing-bc-wcb-links', workflow, { preflight });
module.exports = { workflow, STEP, formsAfterSelection };
