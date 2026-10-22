#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * BC Administration ▸ Billing ▸ Manage Referral Doctors: the list of referral doctors (by billing number), its
 * last-name search, Add Doctor and each doctor's Edit. Nothing else drives these pages; the BC bill form only reads
 * the list through its own referral-doctor lookup.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Manage Referral Doctors (billing/CA/BC/ViewBillingManageReferralDoc,
 * loaded into the panel's #dynamic-content) ▸ Add Doctor (ViewBillingAddReferralDoc) ▸ the form ▸ Save (POST
 * billing/CA/BC/AddReferralDoc, sent by the panel's own AJAX submit, the list comes back into the panel) ▸ Last Name
 * search ▸ the doctor's billing number (ViewBillingAddReferralDoc?id=N) ▸ Save.
 * Asserted: the menu item opens the list in the panel; Add refuses an empty billing number before anything is
 * posted; Add stores the doctor with every field, addresses included, and the panel stays; the search finds the doctor
 * by last name and a name nobody has lists nothing; the doctor's Edit opens filled, and its Save changes that same
 * doctor (specialty and address) without adding another; the Edit refuses a billing number another doctor has, with
 * nothing changed; the list opened on its own (the legacy Administration page's popup) runs without a script error.
 *
 * Fixtures: doctor A is created through Add, with a free FAKE 9xxxx billing number and the run marker as last name.
 * Doctor B, whose number the duplicate step tries, is SQL-seeded the same way (marker + "-B"). The pages have no
 * Delete, so the cleanup deletes exactly those two doctors by billing number AND last name, and fails the run (leaving
 * the rows for a look) if anything else carries the marker. The clinic's own doctors are only read.
 *
 * Expected (see the manifest notes): on develop the first step fails with HTTP 405 until #4778 (release) is
 * forward-merged, and the empty-number step then fails until #4853 (release) is.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const SAVE_PATH = /\/billing\/CA\/BC\/AddReferralDoc$/;
const LIST_PATH = /\/billing\/CA\/BC\/(ViewBillingManageReferralDoc|AddReferralDoc)$/;

/** The step labels the manifest's notes name; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Administration ▸ Billing ▸ Manage Referral Doctors opens the referral doctor list in the panel',
  refuse: 'Add Doctor refuses an empty billing number before anything is posted',
  add: 'Add Doctor stores the doctor with every field, addresses included, and the panel stays',
  search: 'the list finds the doctor by last name and lists nothing for a name nobody has',
  edit: "the doctor's Edit opens filled and its Save changes that doctor's specialty and address",
  duplicate: "the doctor's Edit refuses a billing number another doctor has",
  standalone: 'the list opened on its own runs without a script error',
};

const DOCTOR = {
  first_name: 'Fake', specialty: 'FAKE specialty', address1: '1 FAKE Street', address2: 'Unit FAKE',
  city: 'Faketown', province: 'BC', postal: 'V0V0V0', phone: '555-0100', fax: '555-0101',
};

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billing_trayfees'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required');
  }
  const markerB = `${marker}-B`;
  const freeNumber = (taken) => {
    for (let i = 0; i < 50; i++) {
      const candidate = String(90000 + Math.floor(Math.random() * 10000));
      if (!taken.includes(candidate)
        && sql.value(`SELECT COUNT(*) FROM billingreferral WHERE referral_no=${q(candidate)}`) === '0') return candidate;
    }
    throw new Error('No free FAKE billing number in 90000-99999');
  };
  const numberA = freeNumber([]);
  const numberB = freeNumber([numberA]);
  const doctorA = () => sql.rows(`SELECT billingreferral_no, referral_no, first_name, specialty, address1, address2, city,
      province, postal, phone, fax FROM billingreferral WHERE last_name=${q(marker)}`);
  const doctorB = () => sql.rows(`SELECT billingreferral_no, referral_no, specialty FROM billingreferral WHERE last_name=${q(markerB)}`);

  // Registered before any row exists. The pages offer no Delete, so this removes the run's two doctors by number
  // AND marker; anything else that carries the marker is left for a look and fails the run.
  s.cleanup(() => {
    sql.execute(`DELETE FROM billingreferral WHERE (referral_no=${q(numberA)} AND last_name=${q(marker)})
      OR (referral_no=${q(numberB)} AND last_name=${q(markerB)})`);
    const left = sql.value(`SELECT COUNT(*) FROM billingreferral WHERE last_name IN (${q(marker)}, ${q(markerB)})
      OR referral_no IN (${q(numberA)}, ${q(numberB)})`);
    h.assert(left === '0', `${left} referral doctor row(s) of this run were left behind`);
  });
  sql.execute(`INSERT INTO billingreferral (referral_no, last_name, first_name, specialty, country)
    VALUES (${q(numberB)}, ${q(markerB)}, 'Fake', 'FAKE other', 'Canada')`);
  h.assert(doctorB().length === 1, 'FAKE doctor B was not seeded');

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-referral-doctors-admin' });
  const content = admin.locator('#dynamic-content');
  const form = () => content.locator('form[action$="/billing/CA/BC/AddReferralDoc"]');
  const posts = [];
  admin.on('request', request => {
    if (request.method() === 'POST' && SAVE_PATH.test(new URL(request.url()).pathname)) posts.push(request);
  });
  let shell;
  const stayedInPanel = () => h.assert(admin.url() === shell, 'Save left the Administration panel');

  const search = async (lastName) => {
    await content.locator('form#referralDocform input[name="lastname"]').fill(lastName);
    const answered = admin.waitForResponse(r => /\/billing\/CA\/BC\/ViewBillingManageReferralDoc$/.test(new URL(r.url()).pathname)
      && new URL(r.url()).searchParams.get('lastname') === lastName, { timeout: TIMEOUT });
    await content.locator('form#referralDocform input[type="submit"][value="Search"]').click();
    const response = await answered;
    h.assert(response.status() === 200, `The last-name search answered HTTP ${response.status()}`);
    await content.locator('form#referralDocform').waitFor({ timeout: TIMEOUT });
    return content.locator('table tbody tr');
  };
  const save = async () => {
    const saved = admin.waitForResponse(r => r.request().method() === 'POST' && SAVE_PATH.test(new URL(r.url()).pathname), { timeout: TIMEOUT });
    await form().locator('input[type="submit"][value="Save"]').click();
    const response = await saved;
    h.assert(response.status() === 200, `Save answered HTTP ${response.status()}`);
  };
  const openEdit = async () => {
    const [[id]] = doctorA();
    const rows = await search(marker);
    await rows.locator(`a[href*="ViewBillingAddReferralDoc?id=${id}"]`).click();
    await form().locator('input[name="referral_no"]').waitFor({ timeout: TIMEOUT });
    return id;
  };

  await s.step(STEP.open, async () => {
    const link = admin.locator('#collapseTwo a.defaultManageReferralDoc');
    h.assert(await link.count() === 1, 'Administration ▸ Billing has no Manage Referral Doctors item');
    const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
    if (!await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
    const loaded = admin.waitForResponse(r => LIST_PATH.test(new URL(r.url()).pathname), { timeout: TIMEOUT });
    await link.click();
    const response = await loaded;
    h.assert(response.status() === 200, `Manage Referral Doctors answered HTTP ${response.status()}`);
    await content.locator('form#referralDocform').waitFor({ timeout: TIMEOUT });
    h.assert(await content.locator('a', { hasText: 'Add Doctor' }).count() === 1, 'The referral doctor list offers no Add Doctor');
    shell = admin.url();
  });

  await s.step(STEP.refuse, async () => {
    await content.locator('a', { hasText: 'Add Doctor' }).click();
    await form().locator('input[name="referral_no"]').waitFor({ timeout: TIMEOUT });
    const before = posts.length;
    // If the page posts the empty form anyway, hold the POST back: it still counts as posted, but a failing run must
    // not store a blank doctor (no billing number, no marker) that the cleanup could not tell from the clinic's own.
    const holdBack = route => (route.request().method() === 'POST'
      ? route.fulfill({ status: 200, contentType: 'text/html', body: '<html><body>held back by the check</body></html>' })
      : route.continue());
    await admin.route('**/billing/CA/BC/AddReferralDoc', holdBack);
    let dialogs;
    try {
      dialogs = await h.withExpectedDialogs(admin, async () => {
        // A refused number stays on the page; give a wrongful post time to start before calling it refused.
        const posted = admin.waitForRequest(r => r.method() === 'POST' && SAVE_PATH.test(new URL(r.url()).pathname), { timeout: 3000 })
          .catch(() => null);
        await form().locator('input[type="submit"][value="Save"]').click();
        await posted;
      }, { accept: true });
    } finally {
      await admin.unroute('**/billing/CA/BC/AddReferralDoc', holdBack);
    }
    h.assert(dialogs.some(d => /must enter a Billing Number/i.test(d.text || '')), 'Add Doctor did not ask for a billing number');
    h.assert(posts.length === before, 'Add Doctor posted the form with no billing number');
    h.assert(doctorA().length === 0, 'Add Doctor stored a doctor with no billing number');
    stayedInPanel();
  });

  await s.step(STEP.add, async () => {
    await form().locator('input[name="referral_no"]').fill(numberA);
    await form().locator('input[name="last_name"]').fill(marker);
    for (const [name, value] of Object.entries(DOCTOR)) await form().locator(`input[name="${name}"]`).fill(value);
    await save();
    await content.locator('form#referralDocform').waitFor({ timeout: TIMEOUT });
    stayedInPanel();
    const rows = doctorA();
    h.assert(rows.length === 1, `Add Doctor stored ${rows.length} doctor(s) for the run`);
    const [, number, ...fields] = rows[0];
    h.assert(number === numberA, `Add Doctor stored billing number ${number}`);
    h.assert(JSON.stringify(fields) === JSON.stringify(Object.values(DOCTOR)),
      `Add Doctor stored ${JSON.stringify(fields)}, not what was typed`);
  });

  await s.step(STEP.search, async () => {
    const [[id]] = doctorA();
    const found = await search(marker);
    h.assert(await found.count() === 2, `A search for the run's last name listed ${await found.count()} doctors, not A and B`);
    h.assert((await found.locator(`a[href*="ViewBillingAddReferralDoc?id=${id}"]`).innerText()).trim() === numberA,
      "The doctor's row does not link its billing number to its Edit");
    const none = await search(`${marker}-nobody`);
    h.assert(await none.count() === 0, `A search for a name nobody has listed ${await none.count()} doctors`);
  });

  await s.step(STEP.edit, async () => {
    const id = await openEdit();
    h.assert((await form().locator('legend').innerText()).trim() === 'Update Referral Doctor', 'The Edit form is not an update');
    const shown = {};
    for (const name of ['referral_no', 'last_name', 'specialty', 'address1', 'city']) {
      shown[name] = await form().locator(`input[name="${name}"]`).inputValue();
    }
    h.assert(JSON.stringify(shown) === JSON.stringify({ referral_no: numberA, last_name: marker, specialty: DOCTOR.specialty,
      address1: DOCTOR.address1, city: DOCTOR.city }), `The Edit form opened with ${JSON.stringify(shown)}`);
    await form().locator('input[name="specialty"]').fill('FAKE specialty 2');
    await form().locator('input[name="address1"]').fill('2 FAKE Avenue');
    await save();
    await content.locator('form#referralDocform').waitFor({ timeout: TIMEOUT });
    stayedInPanel();
    const rows = doctorA();
    h.assert(rows.length === 1, `Saving the Edit left ${rows.length} doctors for the run`);
    h.assert(rows[0][0] === id && rows[0][1] === numberA && rows[0][3] === 'FAKE specialty 2' && rows[0][4] === '2 FAKE Avenue'
      && rows[0][6] === DOCTOR.city, `Saving the Edit stored ${JSON.stringify(rows[0])}`);
  });

  await s.step(STEP.duplicate, async () => {
    const beforeA = JSON.stringify(doctorA());
    const beforeB = JSON.stringify(doctorB());
    await openEdit();
    await form().locator('input[name="referral_no"]').fill(numberB);
    await form().locator('input[name="specialty"]').fill('FAKE specialty 3');
    await save();
    await content.locator('.alert-danger').waitFor({ timeout: TIMEOUT });
    h.assert(/Billing # already in use/.test(await content.locator('.alert-danger').innerText()),
      "The Edit did not say the billing number is in use");
    stayedInPanel();
    h.assert(JSON.stringify(doctorA()) === beforeA && JSON.stringify(doctorB()) === beforeB,
      'A refused billing number changed a doctor');
  });

  await s.step(STEP.standalone, async () => {
    const page = await s.context.newPage();
    try {
      await page.goto(h.appUrl(s.config.baseUrl, `/billing/CA/BC/ViewBillingManageReferralDoc?lastname=${encodeURIComponent(marker)}`),
        { waitUntil: 'load', timeout: TIMEOUT });
      await page.locator('form#referralDocform').waitFor({ timeout: TIMEOUT });
      h.assert(await page.locator('table tbody tr').count() === 2, 'The list opened on its own did not list the run\'s doctors');
    } finally {
      await page.close();
    }
  });
  await admin.close();
}

if (require.main === module) runWorkflow('billing-bc-referral-doctors', workflow, { openPatient: false });
module.exports = { workflow, STEP };
