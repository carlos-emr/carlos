#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario hospital (shortcut) billing, driven through its real opener (#5055).
 * User path: Master Record ▸ Billing ▸ Hospital Billing (popup, billingShortcutPg1View) ▸ service
 * date from the calendar, service code, dx, admission date ▸ Next (BillingShortcutPg2Save, the
 * Confirmation page) ▸ Back to Edit / Save / Save and Back.
 * Asserted: the Master Record offers the link for this patient on an Ontario install; it opens page 1
 * for that patient with a CSRF token in its form; the calendar writes the picked service date into
 * the read-only date box; Next shows the Confirmation with the schedule fee and writes nothing; Back
 * to Edit returns to page 1 with the entries kept; a GET to the save route is 405 and writes nothing;
 * Save asks once, writes exactly one claim for the patient (header: billing date, provider,
 * appointment 0, hospital visit type, admission date, total; item: service code, fee, unit, dx,
 * service date) and closes the window; page 1 reopened lists that claim in the patient's history;
 * Save and Back writes a second claim and returns the window to page 1 for the same patient; a
 * French (fr-CA) browser's page 1 shows a translated Next that still posts "Next", so its bill is
 * priced and saved.
 * Before #5055 the Master Record had no Hospital Billing link (#654 removed it), so this check failed
 * at its first step; the service-date calendar then threw on open ("Maximum call stack size
 * exceeded"), Save reopened page 1 instead of closing, and a translated Next posted its label, which
 * the service never priced or saved.
 * Fixtures: runWorkflow FAKE- patient (Ontario, the login's provider as MRP). Every claim row written
 * for that patient is removed and re-checked in cleanup. Implements coverage-plan billing-shortcut.
 */

const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

const CODE = 'A007A';
const DX = '250';
const PAGE1 = /\/billing\/CA\/ON\/billingShortcutPg1View/;
const SAVE_ROUTE = /\/billing\/CA\/ON\/BillingShortcutPg2Save/;

function isoDay(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** Page 1's service-date calendar, as a clinician uses it: open, pick today, click away. */
async function pickServiceDate(page) {
  await page.locator('#trigger').click();
  const calendar = page.locator('.flatpickr-calendar.open');
  await calendar.waitFor({ state: 'visible' });
  await calendar.locator('.flatpickr-day.today').click();
  // Multiple-date mode stays open after a pick; the page copies the dates when it closes.
  await page.locator('form[name="titlesearch"] b').first().click();
  await calendar.waitFor({ state: 'hidden' });
}

/** Fill page 1's bill and return the billing physician it will be sent with. */
async function fillBill(page, { admission, provider }) {
  await page.locator('input[name="serviceDate0"]').fill(CODE);
  await page.locator('input[name="serviceUnit0"]').fill('1');
  await page.locator('input[name="dxCode"]').fill(DX);
  await page.locator('input[name="xml_vdate"]').fill(admission);
  await page.keyboard.press('Escape');
  const select = page.locator('select[name="xml_provider"]');
  const options = await select.locator('option').evaluateAll(entries => entries.map(entry => entry.value));
  let physician = await select.inputValue();
  if (physician === '000000') {
    physician = options.includes(provider) ? provider : options.find(value => value !== '000000');
    h.assert(physician, 'No billing physician is offered on page 1');
    await select.selectOption(physician);
  }
  return { physician, offeredProvider: options.includes(provider) };
}

/**
 * Run `action` and wait for the page's next document. Not waitForURL: the Confirmation page is
 * already at the save route, and Back to Edit answers on that same URL, so the URL alone cannot
 * tell the new page from the old one.
 */
async function navigation(page, action, waitFor) {
  const loaded = page.waitForEvent('framenavigated', { predicate: frame => frame === page.mainFrame() });
  loaded.catch(() => {});
  await action();
  await loaded;
  await page.waitForLoadState('domcontentloaded');
  if (waitFor) await page.waitForURL(waitFor, { waitUntil: 'domcontentloaded' });
}

/** Press a Confirmation-page button, answer its confirm(), and wait for the page it loads. */
async function confirmButton(page, value, waitFor) {
  const dialogs = await h.withExpectedDialogs(page, async () => {
    await navigation(page, () => page.locator(`input[type="submit"][name="button"][value="${value}"]`).click(), waitFor);
  });
  h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === `Are you sure you want to ${value}?`,
    `${value} should ask "Are you sure you want to ${value}?" once; saw ${JSON.stringify(dialogs.map(d => d.text))}`);
}

const nextButton = page => page.locator('form[name="titlesearch"] [type="submit"][name="submit"]');

async function next(page) {
  await navigation(page, () => nextButton(page).click(), SAVE_ROUTE);
  await h.assertNotErrorPage(page, 'shortcut confirmation');
}

async function workflow(s) {
  // Ontario-only (the manifest limits it to ON installs): elsewhere the link is correctly absent.
  const { sql, patient, provider, master, marker } = s;
  const q = h.sqlString;
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${q(CODE)} AND region='ON'
    AND billingservice_date<=CURDATE() ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+\.\d{2}$/.test(fee) || Number(fee) <= 0) throw new h.SkipCheck(`${CODE} has no positive Ontario fee`);

  const claims = () => sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no=${patient} ORDER BY id`).map(r => r[0]);
  s.cleanup(() => {
    const ids = claims();
    const hIds = ids.length ? ids.map(Number).join(',') : '0';
    sql.execute(`DELETE FROM billing_on_transaction WHERE ch1_id IN (${hIds});
      DELETE FROM billing_on_ext WHERE billing_no IN (${hIds});
      DELETE FROM billing_on_item WHERE ch1_id IN (${hIds});
      DELETE FROM billing_on_cheader1 WHERE id IN (${hIds}) AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id IN (${hIds}))
      + (SELECT COUNT(*) FROM billing_on_ext WHERE billing_no IN (${hIds}))
      + (SELECT COUNT(*) FROM billing_on_transaction WHERE ch1_id IN (${hIds}))`) === '0',
    'The owned hospital-billing claim rows were not removed');
  });

  const today = isoDay(new Date());
  const admission = isoDay(new Date(Date.now() - 2 * 24 * 3600 * 1000));
  let page1;
  let physician;
  let firstClaim;

  await s.step('the Master Record offers Hospital Billing for this patient', async () => {
    const link = master.locator('#hospitalBillingLink');
    h.assert(await link.count() === 1, 'The Master Record has no Hospital Billing link (#5055)');
    await link.scrollIntoViewIfNeeded();
    h.assert(await link.isVisible(), 'The Hospital Billing link is not visible in the Billing section');
    const href = new URL(await link.getAttribute('href'), master.url());
    h.assert(href.pathname.endsWith('/billing/CA/ON/billingShortcutPg1View'),
      'Hospital Billing does not open the shortcut page-1 route');
    h.assert(href.searchParams.get('demographic_no') === patient,
      'Hospital Billing is not addressed to the open patient');
    h.assert(href.searchParams.get('appointment_no') === '0' && href.searchParams.get('apptProvider_no') === 'none',
      'Hospital Billing must open a bill with no appointment');
  });

  await s.step('Hospital Billing opens the shortcut page for this patient', async () => {
    page1 = await s.popup(master, master.locator('#hospitalBillingLink'), 'hospital-billing');
    h.assert(PAGE1.test(new URL(page1.url()).pathname), 'The popup is not the shortcut page-1 route');
    const form = page1.locator('form[name="titlesearch"]');
    await form.waitFor();
    h.assert((await form.getAttribute('action')).endsWith('/billing/CA/ON/BillingShortcutPg2Save'),
      'Page 1 does not post to the shortcut save route');
    h.assert((await page1.locator('body').innerText()).includes(marker), 'Page 1 does not name the open patient');
    await ui.csrfTokenPresent(page1, { selector: 'form[name="titlesearch"] input[name="CSRF-TOKEN"]' });
  });

  await s.step('the service-date calendar records the picked date', async () => {
    await pickServiceDate(page1);
    const billDate = await page1.locator('textarea[name="billDate"]').inputValue();
    h.assert(billDate.trim() === today, `The service date box shows "${billDate.trim()}", not the picked day`);
  });

  await s.step('Next shows the Confirmation with the schedule fee and writes nothing', async () => {
    let offeredProvider;
    ({ physician, offeredProvider } = await fillBill(page1, { admission, provider }));
    h.assert(physician === provider || !offeredProvider,
      'Page 1 did not default the billing physician to the patient\'s provider');
    h.assert((await page1.locator('select[name="xml_visittype"]').inputValue()).startsWith('02'),
      'Hospital billing did not default to a hospital visit');
    await next(page1);
    const body = await page1.locator('body').innerText();
    h.assert(/Confirmation/.test(body), 'Next did not show the Confirmation page');
    h.assert(body.includes(CODE) && body.includes(fee), `The Confirmation does not show ${CODE} at its fee`);
    h.assert(claims().length === 0, 'Next wrote a claim before Save');
  });

  await s.step('Back to Edit returns to page 1 with the entries kept', async () => {
    await confirmButton(page1, 'Back to Edit', SAVE_ROUTE);
    await h.assertNotErrorPage(page1, 'shortcut page 1 after Back to Edit');
    h.assert(await page1.locator('form[name="titlesearch"] input[name="serviceDate0"]').inputValue() === CODE,
      'Back to Edit lost the service code');
    h.assert(await page1.locator('input[name="dxCode"]').inputValue() === DX, 'Back to Edit lost the dx code');
    h.assert((await page1.locator('textarea[name="billDate"]').inputValue()).trim() === today,
      'Back to Edit lost the service date');
    h.assert(claims().length === 0, 'Back to Edit wrote a claim');
    await page1.locator('input[name="xml_vdate"]').fill(admission);
    await page1.keyboard.press('Escape');
    if (await page1.locator('select[name="xml_provider"]').inputValue() !== physician) {
      await page1.locator('select[name="xml_provider"]').selectOption(physician);
    }
    await next(page1);
    h.assert(/Confirmation/.test(await page1.locator('body').innerText()), 'Next did not show the Confirmation again');
  });

  await s.step('a GET to the save route writes nothing', async () => {
    const response = await page1.context().request.get(
      `${s.config.baseUrl}/billing/CA/ON/BillingShortcutPg2Save?addition=Confirm&submit=Save&button=Save&demographic_no=${patient}`
      + `&billDate=${today}&serviceDate0=${CODE}&serviceUnit0=1&dxCode=${DX}&xml_provider=${encodeURIComponent(physician)}`,
      { maxRedirects: 0, failOnStatusCode: false });
    h.assert(response.status() === 405, `A GET to the save route answered ${response.status()}, not 405`);
    h.assert(claims().length === 0, 'A GET to the save route wrote a claim');
  });

  await s.step('Save writes one claim for this patient and closes the window', async () => {
    const closed = page1.waitForEvent('close', { timeout: 20000 });
    closed.catch(() => {});
    const dialogs = await h.withExpectedDialogs(page1, async () => {
      await page1.locator('input[type="submit"][name="button"][value="Save"]').click({ noWaitAfter: true });
      await expectValue(sql, `SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`, '1',
        'Save did not write exactly one claim header for the patient');
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === 'Are you sure you want to Save?',
      `Save should ask "Are you sure you want to Save?" once; saw ${JSON.stringify(dialogs.map(d => d.text))}`);
    const [id, billingDate, providerNo, appointmentNo, visitType, admissionDate, total, status] = sql.rows(
      `SELECT id, billing_date, provider_no, appointment_no, visittype, admission_date, total, status
       FROM billing_on_cheader1 WHERE demographic_no=${patient}`)[0];
    firstClaim = id;
    h.assert(billingDate === today, `The claim is dated ${billingDate}, not the service date`);
    h.assert(providerNo === physician, 'The claim is not billed to the chosen physician');
    h.assert(appointmentNo === '0', 'A hospital bill must not name an appointment');
    h.assert(visitType === '02', `The claim's visit type is ${visitType}, not a hospital visit`);
    h.assert(admissionDate === admission, `The claim's admission date is ${admissionDate}, not ${admission}`);
    h.assert(Number(total) === Number(fee), `The claim totals ${total}, not the ${CODE} fee ${fee}`);
    h.assert(status !== 'D', 'The new claim is marked deleted');
    const items = sql.rows(`SELECT service_code, fee, ser_num, dx, service_date FROM billing_on_item WHERE ch1_id=${Number(id)}`);
    h.assert(items.length === 1, `The claim has ${items.length} items, not one`);
    const [code, itemFee, units, dx, serviceDate] = items[0];
    h.assert(code === CODE && Number(itemFee) === Number(fee) && Number(units) === 1 && dx === DX,
      `The claim item is ${code} ${itemFee} x ${units} dx ${dx}, not ${CODE} ${fee} x 1 dx ${DX}`);
    h.assert(serviceDate === today, `The claim item is dated ${serviceDate}, not the service date`);
    await closed;
    h.assert(page1.isClosed(), 'Save left the hospital-billing window open');
  });

  await s.step('page 1 reopened lists the saved claim in the patient\'s history', async () => {
    page1 = await s.popup(master, master.locator('#hospitalBillingLink'), 'hospital-billing-again');
    // The history is a six-column table nested in the page's layout tables, so match the row by
    // its own first cell rather than by any row that contains one.
    const rows = await page1.locator('tr').evaluateAll((all, id) => all
      .filter(row => row.cells.length === 6 && row.cells[0].textContent.trim() === id)
      .map(row => Array.from(row.cells, cell => cell.textContent.trim())), firstClaim);
    h.assert(rows.length === 1, 'The saved claim is not in page 1\'s billing history');
    const [, historyDate, historyAdmission, historyCode, historyDx] = rows[0];
    h.assert(historyDate === today && historyAdmission === admission && historyCode.startsWith(`${CODE} x 1`)
      && historyDx === DX, 'The history row does not show the saved claim\'s dates, code and dx');
  });

  await s.step('Save and Back writes a second claim and returns to page 1', async () => {
    await pickServiceDate(page1);
    const filled = await fillBill(page1, { admission, provider });
    if (filled.physician !== physician) await page1.locator('select[name="xml_provider"]').selectOption(physician);
    await next(page1);
    await confirmButton(page1, 'Save and Back', PAGE1);
    await page1.locator('form[name="titlesearch"]').waitFor();
    await h.assertNotErrorPage(page1, 'shortcut page 1 after Save and Back');
    await expectValue(sql, `SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`, '2',
      'Save and Back did not write a second claim');
    h.assert(new URL(page1.url()).searchParams.get('demographic_no') === patient
      && (await page1.locator('body').innerText()).includes(marker),
    'Save and Back did not return to page 1 for the same patient');
    h.assert(!page1.isClosed(), 'Save and Back closed the window');
  });

  await s.step('a French browser\'s Next and Save price and write the bill too', async () => {
    // Page 1's Next is translated; what it posts must not be, or the service never prices or saves.
    const fr = await h.newContext(s.context.browser(), s.config, { locale: 'fr-CA' });
    fr.setDefaultTimeout(20000);
    fr.on('page', page => h.wireStrictPage(page, 'hospital-billing-fr', s.recorder));
    try {
      const schedule = await h.login(fr, s.config, s.recorder, { label: 'login-fr' });
      const { masterPage } = await openMasterRecord(fr, schedule, s.recorder, {
        searchTerm: marker, preferredDemographicNo: patient, timeout: 20000,
      });
      const page = await ui.clickOpensPopup(masterPage, masterPage.locator('#hospitalBillingLink'), {
        context: fr, recorder: s.recorder, label: 'hospital-billing-fr', timeout: 20000,
      });
      const label = await nextButton(page).evaluate(el => (el.tagName === 'INPUT' ? el.value : el.textContent).trim());
      h.assert(label === 'Suivant', 'Page 1 is not rendered in French');
      await pickServiceDate(page);
      const filled = await fillBill(page, { admission, provider });
      if (filled.physician !== physician) await page.locator('select[name="xml_provider"]').selectOption(physician);
      await next(page);
      const body = await page.locator('body').innerText();
      h.assert(body.includes(CODE) && body.includes(fee), 'The French Next did not price the Confirmation');
      const closed = page.waitForEvent('close', { timeout: 20000 });
      closed.catch(() => {});
      await h.withExpectedDialogs(page, async () => {
        await page.locator('input[type="submit"][name="button"][value="Save"]').click({ noWaitAfter: true });
        await expectValue(sql, `SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`, '3',
          'Save from the French page did not write a claim');
      });
      await closed;
    } finally {
      await fr.close();
    }
  });
}

if (require.main === module) runWorkflow('billing-on-hospital-shortcut', workflow, { openPatient: true });

module.exports = { workflow };
