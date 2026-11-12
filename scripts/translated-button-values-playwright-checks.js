#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * translated-button-values: buttons whose posted value the server compares still work when the browser
 * asks for French (issue #5140). Add A Role compared the posted value with "Search" and "Save", and
 * Manage Private Billing Code with "Save", while both pages posted the translated label, so in French,
 * Spanish, Polish and Portuguese the buttons silently did nothing. admin-role-management and
 * billing-on-admin-config drive the same pages in English only.
 *
 * User path, as a THROWAWAY login (lib/throwaway-login-fixture.js) in a browser whose language is
 * French (fr-CA):
 *   Schedule ▸ Administration ▸ Add A Role ▸ role name ▸ Rechercher ▸ Enregistrer (confirmed);
 *   Schedule ▸ Administration ▸ Manage Private Billing Code ▸ code ▸ Search ▸ description, fee, date
 *     ▸ Enregistrer (confirmed).
 *
 * Asserted, per page:
 * - the page is in French: its button shows the French bundle's label, not the English one;
 * - Add A Role: Rechercher offers the unused role name as new, and Enregistrer stores exactly one role;
 * - Manage Private Billing Code: Enregistrer stores the private code with the typed description and fee.
 *
 * Fixtures: a throwaway login, a role and a private code named by the run marker. Neither page can
 * delete what it made, so cleanup removes the role (with its audit rows) and the private code, then the
 * throwaway, and asserts they are gone.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { openAdmin, adminFrame, navigates } = require('./billing-on-admin-config-playwright-checks');

const ROLE_ROUTE = '/admin/ProviderAddRole';
const PRIVATE_ROUTE = '/billing/CA/ON/ViewBillingONEditPrivateCode';
const BUNDLE_DIR = path.join(__dirname, '..', 'src', 'main', 'resources');

/** One label from a Java resource bundle (key = value, \uXXXX escapes), trimmed like Properties.load. */
function bundleLabel(language, key) {
  const text = fs.readFileSync(path.join(BUNDLE_DIR, `oscarResources_${language}.properties`), 'latin1');
  for (const line of text.split(/\r?\n/)) {
    const match = /^\s*([^=:\s]+)\s*[=:]\s*(.*)$/.exec(line);
    if (match && match[1] === key) {
      return match[2].replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16))).trim();
    }
  }
  throw new Error(`${key} is missing from the ${language} bundle`);
}

const LABELS = {
  search: { fr: bundleLabel('fr', 'admin.provideraddrole.search'), en: bundleLabel('en', 'admin.provideraddrole.search') },
  save: { fr: bundleLabel('fr', 'admin.resourcebaseurl.btnSave'), en: bundleLabel('en', 'admin.resourcebaseurl.btnSave') },
};

/** The one button showing `label`, after proving the page shows the French label rather than the English one. */
async function frenchButton(scope, labels, page) {
  h.assert(labels.fr !== labels.en, `The French and English labels are the same (${labels.fr}), so the language could not be told apart`);
  const button = scope.getByRole('button', { name: labels.fr, exact: true });
  h.assert(await button.count() === 1, `${page} does not show its French "${labels.fr}" button`);
  h.assert(await scope.getByRole('button', { name: labels.en, exact: true }).count() === 0,
    `${page} still shows the English "${labels.en}" button`);
  return button;
}

async function workflow(s) {
  const { sql, config, recorder, marker } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const role = marker;
  const R = h.sqlString(role);
  const privateBare = `PF${marker.slice(7, 12).toUpperCase()}`;
  const privateCode = `_${privateBare}`;
  const P = h.sqlString(privateCode);
  // The private-code date field is a readonly flatpickr: pick a day the calendar offers.
  const issued = `${new Date().toISOString().slice(0, 7)}-01`;
  // Runs before the throwaway's own cleanup (cleanups run in reverse order).
  s.cleanup(() => {
    sql.execute(`DELETE FROM secRole WHERE role_name=${R};
      DELETE FROM log WHERE content='role' AND contentId=${R};
      DELETE FROM billingservice WHERE service_code=${P}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secRole WHERE role_name=${R})
      + (SELECT COUNT(*) FROM log WHERE content='role' AND contentId=${R})
      + (SELECT COUNT(*) FROM billingservice WHERE service_code=${P})`) === '0', 'The owned role or private code was not removed');
  });
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secRole WHERE role_name=${R})
    + (SELECT COUNT(*) FROM billingservice WHERE service_code=${P})`) === '0', 'The run marker already names a role or private code');
  fixture.create();

  const context = await h.newContext(s.context.browser(), config, { locale: 'fr-CA' });
  s.cleanup(() => context.close().catch(() => {}));
  context.on('page', page => h.wireStrictPage(page, 'translated-button-values', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login-fr' });
  const admin = await openAdmin({ schedule, context, recorder });
  let privateAddForm;

  await s.step(`Add A Role in French: ${LABELS.search.fr} offers the new name and ${LABELS.save.fr} stores exactly one role`, async () => {
    const frame = await adminFrame(admin, ROLE_ROUTE, 'input#role_name');
    await frame.locator('#role_name').fill(role);
    await navigates(admin, frame, await frenchButton(frame, LABELS.search, 'Add A Role'));
    h.assert(await frame.locator('input[name="action"]').inputValue() === `add${role}`,
      `${LABELS.search.fr} did not offer the unused role name as new`);
    const save = await frenchButton(frame, LABELS.save, 'Add A Role, after the search,');
    const dialogs = await h.withExpectedDialogs(admin, () => navigates(admin, frame, save));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `${LABELS.save.fr} did not ask for confirmation exactly once`);
    h.assert(sql.value(`SELECT CONCAT(COUNT(*),'|',COALESCE(MAX(description),'')) FROM secRole WHERE role_name=${R}`) === `1|${role}`,
      `${LABELS.save.fr} did not store the role exactly once`);
  });

  await s.step(`Manage Private Billing Code in French: ${LABELS.save.fr} stores the private code`, async () => {
    const frame = await adminFrame(admin, PRIVATE_ROUTE, 'form[name="baseurl"]');
    const form = frame.locator('form[name="baseurl"]');
    await form.locator('input[name="service_code"]').fill(privateBare);
    await navigates(admin, frame, form.locator('button[name="submit"][value="Search"]'));
    h.assert(await form.locator('input[name="action"]').inputValue() === `add${privateCode}`,
      'Searching an unused private code did not offer to add it');
    await form.locator('input[name="description"]').fill(`${marker} private`);
    await form.locator('input[name="value"]').fill('31.50');
    await ui.pickDate(frame, form.locator('#billingservice_date'), issued);
    privateAddForm = await form.evaluate(element => Object.fromEntries(new FormData(element).entries()));
    privateAddForm.submit = 'Save';
    const save = await frenchButton(form, LABELS.save, 'Manage Private Billing Code');
    const dialogs = await h.withExpectedDialogs(admin, () => navigates(admin, frame, save));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `${LABELS.save.fr} did not ask for confirmation exactly once`);
    h.assert(sql.value(`SELECT CONCAT_WS('|', COUNT(*), MAX(description), MAX(value)) FROM billingservice WHERE service_code=${P}`)
      === `1|${marker} private|31.50`, `${LABELS.save.fr} did not store the private code with the typed description and fee`);
  });

  await s.step('replaying the French private-code add leaves one original code and fee', async () => {
    for (let attempt = 0; attempt < 2; attempt++) {
      const response = await context.request.post(h.appUrl(config.baseUrl, PRIVATE_ROUTE), {
        form: { ...privateAddForm, description: `${marker} retry`, value: '99.00' }, maxRedirects: 0 });
      h.assert(response.status() === 200, 'The repeated private-code add did not return its form');
      await response.dispose();
    }
    h.assert(sql.value(`SELECT CONCAT_WS('|', COUNT(*), MAX(description), MAX(value)) FROM billingservice WHERE service_code=${P}`)
      === `1|${marker} private|31.50`, 'Replaying add duplicated or overwrote the original private code');
  });
}

// Both pages are reached from the Administration panel; no patient record is opened.
if (require.main === module) runWorkflow('translated-button-values', workflow, { openMaster: false });
module.exports = { workflow, bundleLabel };
