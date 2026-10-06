#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * tickler-preferences — coverage plan §3.4 (`tickler-forward-filters`, the "Preferences ▸ tickler
 * settings" part; the forward/filter part lives in tickler-forward-filters-playwright-checks.js).
 *
 * User path: Schedule ▸ preferences icon ▸ "Set Tickler Preferences" (the form posts to
 * setTicklerPreferences) ▸ Set a provider / Default ▸ Submit; Schedule ▸ Search ▸ Master Record ▸
 * Tickler ▸ New Tickler (the preference's only consumer, ticklerAdd.jsp: the defaulted assignee).
 *
 * Asserted: saving "default assignee = <second active provider>" writes exactly one `property`
 * row `tickler_task_assignee` with that provider; a new tickler form preselects that provider and
 * its save carries it; reopening the form shows the stored provider; choosing "Default" deletes
 * the row; and a GET against setTicklerPreferences is refused (405) and changes nothing — kept
 * last. Every preference page is held to the strict JavaScript signals.
 *
 * Fixtures: the tickler the add step saves for the owned synthetic patient (message carries the
 * marker); the test provider's preference row is snapshotted before any change. Cleanup deletes
 * the owned tickler with its comments, updates and attachments, restores the preference snapshot,
 * and asserts both.
 *
 * Env: the common contract (lib/playwright-harness.js readConfig()). The regression always
 * follows the actual Preferences link so a direct-route fallback cannot hide a navigation bug.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForSaveSentinel } = require('./tickler-forward-filters-playwright-checks');

const PREFERENCE = 'tickler_task_assignee';

/**
 * Verifies tickler preference persistence and reopening through the actual UI.
 * @param {object} s Isolated workflow session with owned fixtures and strict browser checks.
 * @returns {Promise<void>} Resolves after provider, MRP, Default and GET protections pass.
 */
async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const defaultedMessage = `${marker} saved with the preferred default assignee`;
  const other = sql.rows(`SELECT provider_no FROM provider WHERE status='1'
    AND provider_no NOT LIKE '-%' AND provider_no<>${h.sqlString(provider)} ORDER BY last_name, first_name, provider_no LIMIT 1`)[0];
  if (!other) throw new h.SkipCheck('No second active provider exists to make the default tickler assignee');
  const [otherNo] = other;
  const ownedTicklers = `demographic_no=${patient} AND message LIKE ${h.sqlString(`${marker}%`)}`;
  const preferencePredicate = `provider_no=${h.sqlString(provider)} AND name=${h.sqlString(PREFERENCE)}`;
  const snapshotQuery = `SELECT id, COALESCE(value,''), value IS NULL FROM property WHERE ${preferencePredicate} ORDER BY id`;
  const preferenceSnapshot = sql.rows(snapshotQuery);
  const storedPreference = () => sql.value(`SELECT COALESCE(value,'') FROM property WHERE ${preferencePredicate} ORDER BY id LIMIT 1`);
  const preferenceRows = () => sql.value(`SELECT COUNT(*) FROM property WHERE ${preferencePredicate}`);
  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE ${ownedTicklers}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Owned tickler ID is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler WHERE ${ownedTicklers} AND tickler_no IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${ownedTicklers}`) === '0', 'Owned ticklers were not removed');
    const statements = [`DELETE FROM property WHERE ${preferencePredicate}`];
    for (const [id, value, isNull] of preferenceSnapshot) {
      h.assert(/^\d+$/.test(id), 'Invalid preference restore snapshot');
      statements.push(`INSERT INTO property(id,provider_no,name,value) VALUES (${id},${h.sqlString(provider)},${h.sqlString(PREFERENCE)},${isNull === '1' ? 'NULL' : h.sqlString(value)})`);
    }
    sql.execute(`START TRANSACTION;${statements.join(';')};COMMIT`);
    h.assert(JSON.stringify(sql.rows(snapshotQuery)) === JSON.stringify(preferenceSnapshot), 'Tickler preference restore did not match its snapshot');
  });

  const prefs = await s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), 'preferences');
  async function openPreferenceForm(label) {
    const link = prefs.locator('a[href*="method=viewTicklerTaskAssignee"]').first();
    await revealAuditLink(prefs, link, 20000);
    const settings = await s.popup(prefs, link, label);
    h.assert(h.pathOnly(settings.url()).endsWith('/setTicklerPreferences'),
      'The "Set Tickler Preferences" link did not open its own action route');
    h.assert(await settings.locator('#taskAssigneeProvider').count() === 1,
      'The "Set Tickler Preferences" link did not open the tickler-assignee form');
    return settings;
  }

  await s.step('Preferences ▸ Set Tickler Preferences saves the other provider as the default assignee', async () => {
    const settings = await openPreferenceForm('tickler-preferences');
    await settings.locator('#taskAssigneeProvider').check();
    const select = settings.locator('#assigneeSelect');
    h.assert(await settings.locator('label[for="assigneeSelect"]').isVisible(),
      'The provider selector has no visible associated label');
    await select.waitFor({ state: 'visible' });
    await select.selectOption(otherNo);
    h.assert(await settings.locator('#taskAssignee').inputValue() === otherNo, 'Choosing a provider did not stage it for submission');
    await ui.clickAndAwaitReload(settings, settings.locator('form input[type="submit"]'), { label: 'tickler preference Submit' });
    h.assert(h.pathOnly(settings.url()).endsWith('/setTicklerPreferences'), 'The preference form did not post to setTicklerPreferences');
    await settings.locator('#AlertBanner').waitFor({ state: 'visible', timeout: 20000 });
    h.assert(preferenceRows() === '1' && storedPreference() === otherNo, 'The default assignee preference was not stored');
    await settings.close();
  });

  await s.step('a new tickler form preselects the preferred assignee and the save carries it', async () => {
    const patientList = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(), 'patient-tickler-list');
    const add = await s.popup(patientList, patientList.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(), 'tickler-add');
    await add.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await add.locator('form[name="serviceform"] input[name="demographic_no"]').last().inputValue() === patient, 'The add form did not open for the owned patient');
    h.assert(await add.locator('select[name="task_assigned_to"]').first().inputValue() === otherNo, 'The add form did not default its assignee to the preferred provider');
    await add.locator('textarea[name="ticklerMessage"]').fill(defaultedMessage);
    await add.locator('input[name="xml_appointment_date"]').fill(sql.value('SELECT CURDATE()'));
    await add.locator('input.btn-primary[name="Button"]').first().click();
    await waitForSaveSentinel(add, 'ticklerSubmitFrame', 'tickler-save-ok');
    await expectValue(sql, `SELECT task_assigned_to FROM tickler WHERE demographic_no=${patient} AND message=${h.sqlString(defaultedMessage)}`,
      otherNo, 'The defaulted tickler was not saved to the preferred assignee');
    h.assert(sql.value(`SELECT creator FROM tickler WHERE demographic_no=${patient} AND message=${h.sqlString(defaultedMessage)}`) === provider,
      'The defaulted tickler was not created by the test provider');
    if (!add.isClosed()) await add.close();
    if (!patientList.isClosed()) await patientList.close();
  });

  await s.step('reopening selects the stored provider and saving unchanged preserves it', async () => {
    const settings = await openPreferenceForm('tickler-preferences-reopen');
    h.assert(await settings.locator('#taskAssigneeProvider').isChecked()
      && await settings.locator('#assigneeSelect').inputValue() === otherNo,
    'Reopening did not restore the saved provider selection');
    h.assert(await settings.locator('#taskAssignee').inputValue() === otherNo,
      'Reopening did not stage the saved provider for an unchanged submission');
    await ui.clickAndAwaitReload(settings, settings.locator('form input[type="submit"]'), { label: 'unchanged preference Submit' });
    await settings.locator('#AlertBanner').waitFor({state: 'visible'});
    h.assert(preferenceRows() === '1' && storedPreference() === otherNo,
      'Saving the reopened form changed or duplicated the preference');
    await settings.close();
  });

  await s.step('MRP saves and reopens as the selected preference', async () => {
    const settings = await openPreferenceForm('tickler-preferences-mrp');
    await settings.locator('#taskAssigneeMRP').check();
    await ui.clickAndAwaitReload(settings, settings.locator('form input[type="submit"]'), { label: 'MRP preference Submit' });
    await settings.locator('#AlertBanner').waitFor({state: 'visible'});
    h.assert(preferenceRows() === '1' && storedPreference() === 'mrp', 'MRP was not saved');
    await settings.close();
    const reopened = await openPreferenceForm('tickler-preferences-mrp-reopen');
    h.assert(await reopened.locator('#taskAssigneeMRP').isChecked(), 'Reopening did not select MRP');
    await reopened.close();
  });

  await s.step('choosing Default removes the preference row and reopens with Default selected', async () => {
    const settings = await openPreferenceForm('tickler-preferences-reset');
    await settings.locator('#taskAssigneeDefault').check();
    await ui.clickAndAwaitReload(settings, settings.locator('form input[type="submit"]'), { label: 'tickler preference Submit' });
    await settings.locator('#AlertBanner').waitFor({ state: 'visible', timeout: 20000 });
    h.assert(preferenceRows() === '0', 'Choosing Default did not delete the preference row');
    await settings.close();
    const reopened = await openPreferenceForm('tickler-preferences-default-reopen');
    h.assert(await reopened.locator('#taskAssigneeDefault').isChecked(), 'Reopening did not select Default');
    await reopened.close();
  });

  await s.step('a GET against setTicklerPreferences is refused and does not change the stored preference', async () => {
    const before = `${preferenceRows()}:${storedPreference()}`;
    const rejected = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/setTicklerPreferences?method=saveTicklerTaskAssignee&taskAssigneeMRP.value=provider&taskAssigneeSelection.value=${encodeURIComponent(otherNo)}`),
    { maxRedirects: 0 });
    h.assert(rejected.status() === 405, `A GET preference save answered HTTP ${rejected.status()} instead of 405`);
    h.assert(`${preferenceRows()}:${storedPreference()}` === before, 'A GET request changed the stored tickler preference');
  });
}

if (require.main === module) runWorkflow('tickler-preferences', workflow, { openPatient: true });
module.exports = { workflow };
