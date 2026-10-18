#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * BC Administration ▸ Billing ▸ Manage Private Bill: the list of the clinic's private (A-prefixed) billing codes,
 * with Add Code, each code's Edit (its fee rows) and Delete. Nothing else drives these pages; the BC billing checks
 * cover associations, reprocessing and the simulation.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Manage Private Bill (billing/CA/BC/ViewBillingPrivateCodeAdjust,
 * in the #dynamic-content iframe) ▸ Add Code (ViewBillingAddPrivateCode) ▸ Code, Description, Price ▸ Add
 * (POST billing/CA/BC/billingAddCode, whereTo=private) ▸ back on the list ▸ the code's Edit (ViewBillingEditCode) ▸
 * the fee row's edit, Price, save (POST billingEditCode?method=ajaxCodeUpdate) ▸ Back ▸ the code's Delete
 * (POST DeletePrivateCode).
 * Asserted: the menu item opens the list; Add refuses a price that is not a number before anything is posted; Add
 * stores the code with its A prefix, description and price and lists it; Edit's save changes the stored price and
 * shows it on the row; Delete removes the code from the list and from the fee schedule.
 *
 * Fixtures: none seeded. The private code is created through Add, named FK<run id>, with the run marker as its
 * description, and removed with the list's own Delete while the browser is still open (the harness closes it before
 * cleanup runs). There is no SQL delete: a code the page could not remove fails the run and is left for a look.
 *
 * Expected (see the manifest notes): on develop the first step fails with HTTP 405 until #4778 (release) is
 * forward-merged. After that the price-check step fails while Add's checkUnits() throws (the form lost the name it
 * reads), and once that is fixed the Edit step fails while a code added through Add is stored without an end date
 * (its fee row posts "null" back and the save never returns the row to view).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 30000;

/** The step labels the manifest's expectedFailure names; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Administration ▸ Billing ▸ Manage Private Bill opens the private code list',
  refuse: 'Add Code refuses a price that is not a number before anything is posted',
  add: 'Add Code stores the private code with its A prefix, description and price and lists it',
  edit: "the code's Edit saves a new price on its fee row",
  remove: "the code's Delete removes it from the list and from the fee schedule",
};

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billing_trayfees'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required');
  }
  const typed = `FK${marker.slice(-7).toUpperCase()}`;
  h.assert(/^[A-Z0-9]{1,9}$/.test(typed), 'The private code must fit the 9-character Code box');
  const code = `A${typed}`;
  const owned = () => sql.rows(`SELECT billingservice_no, service_code, value FROM billingservice WHERE description=${q(marker)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${q(code)}`) === '0', 'The FAKE private code is taken');

  let frame = null;
  const list = async () => {
    await frame.goto(new URL('ViewBillingPrivateCodeAdjust', frame.url()).href, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
  };
  const ownedRow = () => frame.locator('table tbody tr').filter({ hasText: code });

  let stepError = null;
  try {
    const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'bc-private-codes-admin' });

    await s.step(STEP.open, async () => {
      const link = admin.locator('#collapseTwo a.xlink', { hasText: 'Manage Private Bill' });
      h.assert(await link.count() === 1, 'Administration ▸ Billing has no Manage Private Bill item');
      const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
      if (!await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
      const route = new URL(await link.getAttribute('rel'), 'https://host.invalid').pathname.split('/').pop();
      const loaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith(`/${route}`), { timeout: TIMEOUT });
      await link.click();
      const response = await loaded;
      h.assert(response.status() === 200, `Manage Private Bill answered HTTP ${response.status()}`);
      frame = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
      h.assert(frame, 'The private code list did not load in the Administration panel');
      await frame.locator('h3', { hasText: 'Manage Private Bill' }).waitFor({ timeout: TIMEOUT });
      h.assert(await frame.locator('a', { hasText: 'Add Code' }).count() === 1, 'The private code list offers no Add Code');
    });

    const openAdd = async () => {
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }), frame.locator('a', { hasText: 'Add Code' }).click()]);
      await frame.locator('form input[name="code"]').waitFor({ timeout: TIMEOUT });
    };
    const fill = async (price) => {
      await frame.locator('form input[name="code"]').fill(typed);
      await frame.locator('form input[name="desc"]').fill(marker);
      await frame.locator('form input[name="value"]').fill(price);
    };

    await s.step(STEP.refuse, async () => {
      await openAdd();
      await fill('twelve');
      let posted = false;
      const dialogs = await h.withExpectedDialogs(admin, async () => {
        // A refused price stays on the page; give a wrongful post time to start before calling it refused.
        const navigated = frame.waitForNavigation({ timeout: 5000 }).then(() => true, () => false);
        await frame.locator('form input[type="submit"][value="Add"]').click();
        posted = await navigated;
      }, { accept: true });
      h.assert(dialogs.some(d => /numeric/i.test(d.text || '')), 'Add did not say the price has to be a number');
      h.assert(!posted, 'Add posted the form although its price is not a number');
      h.assert(owned().length === 0, 'Add stored a code whose price is not a number');
      await frame.locator('form input[name="code"]').waitFor({ timeout: TIMEOUT });
    });

    await s.step(STEP.add, async () => {
      await fill('12.34');
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        frame.locator('form input[type="submit"][value="Add"]').click()]);
      await expectValue(sql, `SELECT COUNT(*) FROM billingservice WHERE description=${q(marker)}`, '1', 'Add did not store the private code');
      const [row] = owned();
      h.assert(row[1] === code && Number(row[2]) === 12.34, `Add stored ${JSON.stringify(row)}, not ${code} at 12.34`);
      console.log(`    added ${code}: service date / termination date stored as ${JSON.stringify(sql.rows(
        `SELECT billingservice_date, termination_date FROM billingservice WHERE billingservice_no=${Number(row[0])}`)[0])}`);
      await frame.locator('h3', { hasText: 'Manage Private Bill' }).waitFor({ timeout: TIMEOUT });
      h.assert(await ownedRow().count() === 1, 'The new private code is not listed');
      h.assert((await ownedRow().innerText()).includes('12.34'), 'The listed private code does not show its price');
    });

    await s.step(STEP.edit, async () => {
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        ownedRow().getByRole('link', { name: 'Edit', exact: true }).click()]);
      const [id] = owned()[0];
      // The page nests its fee table in a layout table, so take the row that holds the code's own hidden id.
      const fee = frame.locator(`xpath=//input[@type="hidden" and @value="${id}"]/ancestor::tr[1]`);
      h.assert(await fee.count() === 1, 'Edit does not list the code\'s fee row');
      await fee.getByRole('link', { name: 'edit', exact: true }).click();
      await fee.locator('input[name="value"]').fill('23.45');
      const saved = admin.waitForResponse(r => r.url().includes('method=ajaxCodeUpdate') && r.request().method() === 'POST', { timeout: TIMEOUT });
      await fee.getByRole('link', { name: 'save', exact: true }).click();
      h.assert((await saved).status() === 200, 'The fee row save did not answer HTTP 200');
      await expectValue(sql, `SELECT value FROM billingservice WHERE billingservice_no=${Number(id)}`, '23.45', 'The fee row save did not store the new price');
      const back = await fee.getByRole('link', { name: 'edit', exact: true }).waitFor({ timeout: TIMEOUT }).then(() => true, () => false);
      h.assert(back, 'The fee row stays in edit mode after its save');
      h.assert((await fee.innerText()).includes('23.45'), 'The fee row does not show the saved price');
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }), frame.locator('input[type="submit"][value="Back"]').click()]);
      await frame.locator('h3', { hasText: 'Manage Private Bill' }).waitFor({ timeout: TIMEOUT });
    });

    await s.step(STEP.remove, async () => {
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        ownedRow().getByRole('link', { name: 'Delete', exact: true }).click()]);
      await expectValue(sql, `SELECT COUNT(*) FROM billingservice WHERE description=${q(marker)}`, '0', 'Delete did not remove the private code');
      await frame.locator('h3', { hasText: 'Manage Private Bill' }).waitFor({ timeout: TIMEOUT });
      h.assert(await ownedRow().count() === 0, 'The deleted private code is still listed');
    });
  } catch (error) {
    stepError = error;
    throw error;
  } finally {
    // Remove anything this run added (a failed step can leave the code, or a code posted with a bad price) with the
    // list's own Delete while the browser is open. No SQL delete: a code the page cannot remove is left for a look.
    const left = owned();
    if (left.length && frame) {
      for (const [id] of left) {
        try {
          await list();
          await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
            frame.evaluate(no => window.deletePrivateCode(no), String(id))]);
        } catch (error) {
          console.log(`    cleanup: the list's Delete failed for billingservice ${id}: ${error.message.split('\n')[0]}`);
        }
      }
    }
    const remaining = owned();
    if (left.length) console.log(`    cleanup: ${left.length} code(s) left by the run, ${remaining.length} remain after the list's Delete`);
    if (remaining.length) {
      const message = `This run's private code is still stored (${JSON.stringify(remaining)}); left for a look, no SQL delete`;
      // A throw here would replace the step's own (possibly expected, tagged) failure, so report it instead.
      if (stepError) console.error(`    cleanup: ${message}`);
      else h.assert(false, message);
    }
  }
}

if (require.main === module) runWorkflow('billing-bc-private-codes', workflow, { openPatient: false });
module.exports = { workflow, STEP };
