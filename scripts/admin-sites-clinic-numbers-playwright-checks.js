#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Clinic-wide administration settings reached from the /administration shell (#myFrame).
 * User path: Schedule ▸ Administration ▸ Billing ▸ Settings (admin/BillingSettings) and
 * Administration ▸ System Management ▸ Help Link Setting (admin/ResourceBaseUrl), then the Help
 * link on the day sheet and on the administration shell's own menu bar.
 * Asserts: Ontario shows a non-mutating empty state with no Save control; an explicit POST,
 * including a forged province field, leaves BC properties/preferences byte-exact. Help Link
 * Website and Details retain their day-sheet and shell behavior; the shell opens the stored
 * website URL and refuses executable or malformed URLs. GET save flags change no settings.
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
  // Row identities and exact values (not a count), so a save that inserts, deletes or rewrites any
  // BC-only key -- even to another non-empty value -- changes the result.
  const storedBcValues = () => JSON.stringify([snapshotRows(sql, 'property', ['name', 'value', 'provider_no'], BC_PROPERTY_NAMES),
    snapshotRows(sql, 'SystemPreferences', ['name', 'value', 'updateDate'], PREFERENCE_NAMES)]);
  const helpRows = name => sql.rows(`SELECT value FROM property WHERE name=${h.sqlString(name)}`).map(row => row[0]);

  // The day sheet's own URL (the post-login landing page) lets a second tab show the day sheet
  // again when Administration opens in place of it rather than in its own window.
  const daySheetUrl = s.schedule.url();
  const {page: admin, isPopup} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder: s.recorder, label: 'settings-administration', timeout: TIMEOUT});
  let daySheet = isPopup ? s.schedule : null;
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
    if (!daySheet) {
      daySheet = await context.newPage();
      await daySheet.goto(daySheetUrl, {waitUntil: 'domcontentloaded'});
    } else await daySheet.reload({waitUntil: 'domcontentloaded'});
    return daySheet.locator('#helpLink a').first().getAttribute('onclick');
  }

  await s.step('Billing ▸ Settings states the Ontario install has no billing options', async () => {
    const settings = await openSection(admin.locator('a.xlink[rel$="/admin/BillingSettings"]'), '/admin/BillingSettings');
    await settings.getByText('Ontario billing has no settings on this page.').waitFor({timeout: TIMEOUT});
    h.assert(await settings.locator('[name="saveBillingSettings"]').count() === 0, 'The empty Ontario page still offers Save');
    h.assert(!(await settings.locator('body').innerText()).includes('OSCAR'), 'Billing Settings still uses the old product name');
    h.assert(await settings.locator('#bc_default_service_location, #default_billing_form, [name="auto_populate_refer"]').count() === 0,
      'BC-only billing settings are offered on the Ontario install');
  });

  await s.step('An explicit Ontario billing POST leaves every BC preference unchanged', async () => {
    const settings = admin.frames().find(f => new URL(f.url(), 'http://x').pathname.endsWith('/admin/BillingSettings'));
    const before = storedBcValues();
    const token = await settings.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const response = await context.request.post(h.appUrl(s.config.baseUrl, '/admin/BillingSettings'), {
      form: {dboperation: 'Save', 'CSRF-TOKEN': token, billregion: 'BC',
        auto_populate_refer: 'true', bc_default_service_location: marker,
        invoice_use_custom_clinic_info: 'on', invoice_custom_clinic_info: marker},
      maxRedirects: 0,
    });
    h.assert(response.status() === 200, `Ontario billing POST answered HTTP ${response.status()}`);
    const html = await response.text();
    h.assert(html.includes('Ontario billing has no settings on this page.') && !html.includes('Settings Saved'),
      'An unsupported billing save did not return the empty state without a success message');
    await response.dispose();
    h.assert(storedBcValues() === before, 'The Ontario POST rewrote BC settings or trusted the submitted province');
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

  await s.step('the shell Help opens the saved Website link', async () => {
    help = await openSection(admin.getByRole('link', {name: 'Help Link Setting', exact: true, includeHidden: true}), '/admin/ResourceBaseUrl');
    await help.locator('input.helpOption[value="website"]').check();
    await websiteInput().fill(helpUrl);
    await submitInFrame(help, help.locator('#websiteSave'));
    await expectValue(sql, "SELECT value FROM property WHERE name='resource_baseurl'", helpUrl, 'The Website link was not stored again');
    h.assert((await scheduleHelpTarget() || '').includes(`'${helpUrl}'`), 'The day sheet Help does not open the saved URL');
    await admin.reload({waitUntil: 'domcontentloaded'});
    const shellHelp = admin.locator('#helpLink a').first();
    h.assert(await shellHelp.getAttribute('data-help-url') === helpUrl, 'The shell Help ignores the saved clinic URL');
    await context.route('https://help.invalid/**', route => route.fulfill({status: 200, contentType: 'text/plain', body: 'Owned clinic help'}));
    const popup = await s.popup(admin, shellHelp, 'settings-clinic-help');
    h.assert(popup.url() === helpUrl, 'The shell Help popup did not open the stored URL');
    await popup.close();
  });
  await s.step('A saved Help URL containing quote and markup characters stays literal', async () => {
    const value = `${helpUrl}?q='"><img src=x onerror=window.__unexpectedClinicHelp=1>`;
    // Exercise already-stored values so input filtering cannot mask an output-encoding bug.
    h.assert(JSON.stringify(helpRows('resource_baseurl')) === JSON.stringify([helpUrl]), 'The owned Help URL is no longer stored');
    sql.execute(`UPDATE property SET value=${h.sqlString(value)} WHERE name='resource_baseurl'`);
    await admin.reload({waitUntil: 'domcontentloaded'});
    const link = admin.locator('#helpLink a').first();
    h.assert(await link.getAttribute('data-help-url') === value, 'Quote or markup characters escaped the Help URL attribute');
    h.assert(await admin.evaluate(() => window.__unexpectedClinicHelp === undefined), 'Stored Help URL markup executed');
    const popup = await s.popup(admin, link, 'settings-literal-help');
    h.assert(popup.url() === new URL(value).href, 'The encoded Help URL opened a different destination');
    await popup.close();
  });

  await s.step('The shell Help refuses executable and malformed stored URLs', async () => {
    for (const value of ['javascript:window.__unexpectedClinicHelp=1', 'http://[']) {
      h.assert(helpRows('resource_baseurl').length === 1, 'The owned Help URL row is missing or duplicated');
      sql.execute(`UPDATE property SET value=${h.sqlString(value)} WHERE name='resource_baseurl'`);
      await admin.reload({waitUntil: 'domcontentloaded'});
      const pages = context.pages().length;
      await admin.locator('#helpLink a').first().click();
      h.assert(context.pages().length === pages, 'An unsafe stored Help URL opened a popup');
      h.assert(await admin.evaluate(() => window.__unexpectedClinicHelp === undefined), 'An unsafe Help URL executed script');
    }
  });
}

if (require.main === module) runWorkflow('admin-sites-clinic-numbers', workflow, {openPatient: false});
module.exports = {workflow};
