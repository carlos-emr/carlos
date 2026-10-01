#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Clinic-wide administration settings reached from the /administration shell (#myFrame).
 * User path: Schedule ▸ Administration ▸ Billing ▸ Settings (admin/BillingSettings) and
 * Administration ▸ System Management ▸ Help Link Setting (admin/ResourceBaseUrl), then the Help
 * link on the day sheet and on the administration shell's own menu bar.
 * Asserts: the Ontario Billing Settings page states it has no options and its Save confirms
 * without storing any value for the BC-only keys; Help Link "Website" stores resource_baseurl and
 * the day sheet's Help opens it; "Details" replaces it with resource_helpHtml and the shell's Help
 * panel shows the text; GET with a save flag is refused 405 and stores nothing; finally the shell's
 * Help follows a saved Website link like the day sheet does.
 * Fixtures: none created; the property / SystemPreferences rows these pages own are snapshotted
 * (byte-exact, with ids) before the first click and restored in cleanup. EXCLUSIVE=1: both are
 * clinic-wide settings. Satellite-sites Admin (multisites=off) and Manage Clinic NBR Codes
 * (rma_enabled=false) have no menu entry on the Ontario install, so they get no step here.
 * Implements coverage plan §3.7 admin-misc (Help Link Setting) and the billing Settings page.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TIMEOUT = 20000;
const PROPERTY_NAMES = ['auto_populate_refer', 'bc_default_service_location', 'default_billing_form', 'resource_baseurl', 'resource_helpHtml'];
const BC_PROPERTY_NAMES = PROPERTY_NAMES.slice(0, 3);
const PREFERENCE_NAMES = ['invoice_custom_clinic_info', 'invoice_use_custom_clinic_info'];
const inList = names => `(${names.map(h.sqlString).join(',')})`;

// Hex round-trip keeps NULL, empty and arbitrary bytes distinct, so restore is exact.
function snapshotRows(sql, table, columns, names) {
  const select = columns.map(column => `IF(${column} IS NULL,'N',CONCAT('X',HEX(${column})))`).join(',');
  return sql.rows(`SELECT id,${select} FROM ${table} WHERE name IN ${inList(names)} ORDER BY id`);
}
function restoreRows(sql, table, columns, names, rows) {
  const literal = cell => (cell === 'N' ? 'NULL' : `UNHEX('${cell.slice(1)}')`);
  const inserts = rows.map(([id, ...cells]) => `INSERT INTO ${table}(id,${columns.join(',')})
    VALUES(${Number(id)},${cells.map(literal).join(',')})`);
  sql.execute([`DELETE FROM ${table} WHERE name IN ${inList(names)}`, ...inserts].join(';'));
}

async function workflow(s) {
  const {sql, marker, context} = s;
  const helpUrl = `https://help.invalid/${marker}/`;
  const helpText = `${marker} help details`;
  const propertyColumns = ['name', 'value', 'provider_no'];
  const preferenceColumns = ['name', 'value', 'updateDate'];
  const properties = snapshotRows(sql, 'property', propertyColumns, PROPERTY_NAMES);
  const preferences = snapshotRows(sql, 'SystemPreferences', preferenceColumns, PREFERENCE_NAMES);
  s.cleanup(() => {
    restoreRows(sql, 'property', propertyColumns, PROPERTY_NAMES, properties);
    restoreRows(sql, 'SystemPreferences', preferenceColumns, PREFERENCE_NAMES, preferences);
    h.assert(JSON.stringify(snapshotRows(sql, 'property', propertyColumns, PROPERTY_NAMES)) === JSON.stringify(properties)
      && JSON.stringify(snapshotRows(sql, 'SystemPreferences', preferenceColumns, PREFERENCE_NAMES)) === JSON.stringify(preferences),
    'Billing/help settings were not restored to their snapshot');
  });
  const storedBcValues = () => sql.value(`SELECT (SELECT COUNT(*) FROM property WHERE name IN ${inList(BC_PROPERTY_NAMES)}
    AND COALESCE(value,'')<>'') + (SELECT COUNT(*) FROM SystemPreferences WHERE name IN ${inList(PREFERENCE_NAMES)} AND COALESCE(value,'')<>'')`);
  const helpRows = name => sql.rows(`SELECT value FROM property WHERE name=${h.sqlString(name)}`).map(row => row[0]);

  const {page: admin, isPopup} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder: s.recorder, label: 'settings-administration', timeout: TIMEOUT});
  h.assert(isPopup, 'Administration replaced the day sheet instead of opening its own window');
  async function openSection(link, path) {
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const deadline = Date.now() + TIMEOUT;
    let frame;
    while (!(frame = admin.frames().find(f => f.parentFrame() && new URL(f.url(), 'http://x').pathname.endsWith(path)))) {
      h.assert(Date.now() < deadline, `The menu item did not load ${path} into the administration frame`);
      await admin.waitForTimeout(100);
    }
    await frame.waitForLoadState('domcontentloaded');
    return frame;
  }
  async function submitInFrame(frame, control) {
    const navigated = admin.waitForEvent('framenavigated', {predicate: f => f === frame, timeout: TIMEOUT});
    await control.click();
    await navigated;
    await frame.waitForLoadState('domcontentloaded');
  }
  async function scheduleHelpTarget() {
    await s.schedule.reload({waitUntil: 'domcontentloaded'});
    return s.schedule.locator('#helpLink a').first().getAttribute('onclick');
  }

  await s.step('Billing ▸ Settings states the Ontario install has no billing options', async () => {
    const settings = await openSection(admin.locator('a.xlink[rel$="/admin/BillingSettings"]'), '/admin/BillingSettings');
    await settings.getByText('No billing options to display.').waitFor({timeout: TIMEOUT});
    h.assert(await settings.locator('#bc_default_service_location, #default_billing_form, [name="auto_populate_refer"]').count() === 0,
      'BC-only billing settings are offered on the Ontario install');
  });

  await s.step('Billing Settings Save confirms and stores no value for the BC-only keys', async () => {
    const settings = admin.frames().find(f => new URL(f.url(), 'http://x').pathname.endsWith('/admin/BillingSettings'));
    const before = storedBcValues();
    await submitInFrame(settings, settings.locator('input[name="saveBillingSettings"]'));
    await settings.getByText('Settings Saved').waitFor({timeout: TIMEOUT});
    h.assert(storedBcValues() === before, 'Saving the Ontario settings page stored values for BC-only keys');
  });

  let help;
  const websiteInput = () => help.locator('#websiteForm input[name="resource_baseurl"]');
  await s.step('Help Link Setting ▸ Website stores the URL and the day sheet Help opens it', async () => {
    help = await openSection(admin.getByRole('link', {name: 'Help Link Setting', exact: true, includeHidden: true}), '/admin/ResourceBaseUrl');
    await websiteInput().waitFor({timeout: TIMEOUT});
    await help.locator('input.helpOption[value="website"]').check();
    await websiteInput().fill(helpUrl);
    await submitInFrame(help, help.locator('#websiteSave'));
    await help.getByText('Help link has been saved.').waitFor({timeout: TIMEOUT});
    h.assert(await websiteInput().inputValue() === helpUrl, 'The saved page does not show the stored URL');
    h.assert(JSON.stringify(helpRows('resource_baseurl')) === JSON.stringify([helpUrl]) && helpRows('resource_helpHtml').length === 0,
      'resource_baseurl is not stored exactly once (or help details survived)');
    h.assert((await scheduleHelpTarget() || '').includes(`'${helpUrl}'`), 'The day sheet Help does not open the saved URL');
  });

  await s.step('Help Link Setting ▸ Details replaces the URL and the shell Help panel shows the text', async () => {
    await help.locator('input.helpOption[value="details"]').check();
    h.assert(await websiteInput().isDisabled(), 'Choosing Details left the Website form enabled');
    const editor = help.locator('#resource_helpHtml_editor .toastui-editor-ww-container [contenteditable="true"]');
    await editor.click();
    await editor.pressSequentially(helpText);
    await submitInFrame(help, help.locator('#detailsSave'));
    await help.getByText('Your new help details has been saved.').waitFor({timeout: TIMEOUT});
    const stored = helpRows('resource_helpHtml');
    h.assert(stored.length === 1 && stored[0].includes(helpText) && helpRows('resource_baseurl').length === 0,
      'Details did not replace the URL with exactly one help-details row');
    h.assert(await help.locator('input.helpOption[value="details"]').isChecked()
      && (await help.locator('#resource_helpHtml_editor').innerText()).includes(helpText), 'The reloaded page does not show the stored details');
    h.assert(!(await scheduleHelpTarget() || '').includes(helpUrl), 'The day sheet Help still opens the replaced URL');
    await admin.reload({waitUntil: 'domcontentloaded'});
    await admin.locator('#help-link > a').click();
    await admin.locator('#helpHtml').filter({hasText: helpText}).waitFor({state: 'visible', timeout: TIMEOUT});
  });

  await s.step('GET with a save flag is refused and stores nothing', async () => {
    const before = JSON.stringify([helpRows('resource_baseurl'), helpRows('resource_helpHtml'), storedBcValues()]);
    const website = await context.request.get(h.appUrl(s.config.baseUrl, `/admin/ResourceBaseUrl?websiteSave=Save&resource_baseurl=${encodeURIComponent(helpUrl + 'get')}`),
      {maxRedirects: 0});
    h.assert(website.status() === 405, `GET websiteSave answered HTTP ${website.status()}, expected 405`);
    const billing = await context.request.get(h.appUrl(s.config.baseUrl, '/admin/BillingSettings?dboperation=Save'), {maxRedirects: 0});
    h.assert(billing.status() === 200, `GET BillingSettings answered HTTP ${billing.status()}`);
    h.assert(JSON.stringify([helpRows('resource_baseurl'), helpRows('resource_helpHtml'), storedBcValues()]) === before,
      'A GET with a save flag changed the stored settings');
  });

  await s.step('the administration shell Help follows a saved Website link like the day sheet', async () => {
    help = await openSection(admin.getByRole('link', {name: 'Help Link Setting', exact: true, includeHidden: true}), '/admin/ResourceBaseUrl');
    await help.locator('input.helpOption[value="website"]').check();
    await websiteInput().fill(helpUrl);
    await submitInFrame(help, help.locator('#websiteSave'));
    await expectValue(sql, "SELECT value FROM property WHERE name='resource_baseurl'", helpUrl, 'The Website link was not stored again');
    h.assert((await scheduleHelpTarget() || '').includes(`'${helpUrl}'`), 'The day sheet Help does not open the saved URL');
    await admin.reload({waitUntil: 'domcontentloaded'});
    const shellHelp = await admin.locator('#helpLink a').first().getAttribute('onclick');
    h.assert((shellHelp || '').includes(`'${helpUrl}'`),
      'The administration shell Help ignores the saved Help Link and opens the carlos.properties resource_base_url');
  });
}

if (require.main === module) runWorkflow('admin-sites-clinic-numbers', workflow, {openPatient: false});
module.exports = {workflow};
