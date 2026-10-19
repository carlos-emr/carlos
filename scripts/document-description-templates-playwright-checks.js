#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Document description templates: the clinic-wide list (Administration ▸ Document Description
 * Template, admin/DisplayDocumentDescriptionTemplate?setDefault=true) and a provider's own list (the
 * same page from Preferences). provider-preferences covers a provider's own list, as the administrator
 * test login; nothing edited the clinic-wide list, and nothing opened the page as a provider without
 * administration rights.
 *
 * User path: Schedule ▸ Administration ▸ Document Description Template (frame) ▸ document type ▸
 * shortcut + description ▸ Add; the entry ▸ Update; the entry ▸ Delete (POST DocumentDescriptionTemplate).
 * Then, signed in as a provider with only the doctor role: Schedule ▸ Preferences ▸ Document
 * description template link.
 * Asserted: Add stores a clinic-wide entry (no provider) and lists it, while a provider's own entry of
 * the same type is not listed there; Update changes it and keeps it clinic-wide; Delete removes it and
 * leaves the provider's entry; a doctor, whose Preferences offer the link, can open their own list.
 * Fixtures: the test login's own entry and the doctor login (lib/throwaway-login-fixture.js, role
 * doctor) are created first; every entry carries the run marker. Cleanup deletes the entries by marker
 * and the throwaway login, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const TIMEOUT = 20000;
const PAGE = '/admin/DisplayDocumentDescriptionTemplate';

async function workflow(s) {
  const { sql, marker, provider, config, recorder } = s;
  const q = h.sqlString;
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  const owned = () => sql.value(`SELECT COUNT(*) FROM documentDescriptionTemplate WHERE description LIKE ${q(`${marker}%`)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM documentDescriptionTemplate WHERE description LIKE ${q(`${marker}%`)}`);
    h.assert(owned() === '0', 'Owned description templates were not removed');
    fixture.cleanup();
  });
  fixture.create({ roleNames: ['doctor'] });
  h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(fixture.providerNo)} AND role_name<>'doctor'`) === '0',
    'The doctor login holds other roles');
  const shortcut = marker.slice(-12);
  const clinic = { description: `${marker} clinic O'Neil "A&B+"`, edited: `${marker} clinic edited` };

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder, label: 'administration', timeout: TIMEOUT });
  const link = admin.locator(`a.xlink[rel*="${PAGE}?setDefault=true"]`).first();
  await link.waitFor({ state: 'attached', timeout: TIMEOUT });
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const deadline = Date.now() + TIMEOUT;
  let frame;
  while (!(frame = admin.frames().find(f => f.parentFrame() && new URL(f.url(), 'http://x').pathname.endsWith(PAGE)))) {
    h.assert(Date.now() < deadline, 'Document Description Template did not load into the administration frame');
    await admin.waitForTimeout(100);
  }
  await frame.locator('#docType').waitFor({ timeout: TIMEOUT });
  await h.assertNotErrorPage(frame, 'clinic description templates');
  const type = await frame.locator('#docType option').nth(1).getAttribute('value');
  h.assert(type, 'The page offers no document type');
  // The test login's own entry of the same type: it must stay off the clinic-wide list.
  sql.execute(`INSERT INTO documentDescriptionTemplate (doctype,description,descriptionShortcut,provider_no,lastUpdated)
    VALUES (${q(type)},${q(`${marker} own`)},${q(`o${shortcut}`.slice(0, 20))},${q(provider)},NOW())`);
  const ownId = sql.value(`SELECT id FROM documentDescriptionTemplate WHERE description=${q(`${marker} own`)}`);
  const clinicRow = () => sql.rows(`SELECT id, IFNULL(provider_no,'clinic'), doctype, descriptionShortcut, description FROM documentDescriptionTemplate
    WHERE description IN (${q(clinic.description)},${q(clinic.edited)})`);
  // Each control posts, and its success handler lists the type again.
  const listed = () => frame.locator('#docDescList option');
  // HTMLOptionElement.text collapses the list's padding spaces, leaving "(shortcut) description".
  const optionText = id => frame.locator(`#docDescList option[value="${id}"]`).evaluate(option => option.text);
  const select = async id => {
    await frame.locator(`#docDescList option[value="${id}"]`).waitFor({ state: 'attached', timeout: TIMEOUT });
    await frame.locator('#docDescList').selectOption(id);
    await frame.waitForFunction(value => document.querySelector('[name="descriptionId"]').value === value, id, { timeout: TIMEOUT });
  };

  let id;
  await s.step('Add stores a clinic-wide entry and lists it, but not a provider\'s own entry', async () => {
    await frame.locator('#docType').selectOption(type);
    await frame.locator('#docDescList').waitFor({ state: 'attached', timeout: TIMEOUT });
    await frame.locator('[name="docDescriptionShortcut"]').fill(shortcut);
    await frame.locator('[name="docDescription"]').fill(clinic.description);
    await frame.locator('#addDescription').click();
    await expectValue(sql, `SELECT COUNT(*) FROM documentDescriptionTemplate WHERE description=${q(clinic.description)}`, '1',
      'Add did not store exactly one entry');
    const [row] = clinicRow();
    id = row[0];
    h.assert(row[1] === 'clinic' && row[2] === type && row[3] === shortcut, `Add did not store a clinic-wide entry: ${JSON.stringify(row)}`);
    await frame.locator(`#docDescList option[value="${id}"]`).waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await optionText(id) === `(${shortcut}) ${clinic.description}`,
      `The list shows the entry as ${JSON.stringify(await optionText(id))}, not its shortcut and description as typed`);
    h.assert(await frame.locator(`#docDescList option[value="${ownId}"]`).count() === 0,
      'The clinic-wide list shows a provider\'s own entry');
  });

  await s.step('Update changes the entry and keeps it clinic-wide', async () => {
    await select(id);
    h.assert(await frame.locator('[name="docDescription"]').inputValue() === clinic.description, 'The entry did not load its description');
    await frame.locator('[name="docDescription"]').fill(clinic.edited);
    await frame.locator('#updateDescription').click();
    await expectValue(sql, `SELECT CONCAT_WS('|', IFNULL(provider_no,'clinic'), description) FROM documentDescriptionTemplate WHERE id=${id}`,
      `clinic|${clinic.edited}`, 'Update did not change the clinic-wide entry');
    await frame.locator(`#docDescList option[value="${id}"]`, { hasText: clinic.edited }).waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await optionText(id) === `(${shortcut}) ${clinic.edited}`, `The list shows the updated entry as ${JSON.stringify(await optionText(id))}`);
  });

  await s.step('Delete removes the entry and leaves the provider\'s own entry', async () => {
    await select(id);
    await frame.locator('#deleteDescription').click();
    await expectValue(sql, `SELECT COUNT(*) FROM documentDescriptionTemplate WHERE id=${id}`, '0', 'Delete did not remove the entry');
    h.assert(sql.value(`SELECT COUNT(*) FROM documentDescriptionTemplate WHERE id=${ownId}`) === '1', 'Delete removed the provider\'s own entry');
    await frame.locator(`#docDescList option[value="${id}"]`).waitFor({ state: 'detached', timeout: TIMEOUT });
    h.assert(await listed().count() >= 1, 'The list is gone after Delete');
  });

  await s.step('a doctor, whose Preferences offer the link, can open their own description templates', async () => {
    const context = await h.newContext(s.context.browser(), config);
    s.cleanup(() => context.close());
    context.setDefaultTimeout(TIMEOUT);
    context.on('page', page => h.wireStrictPage(page, 'doctor', recorder));
    const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'doctor-login' });
    const prefs = await clickOpensPopupOrNavigates(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
      { context, recorder, label: 'doctor-preferences', timeout: TIMEOUT }).then(outcome => outcome.page);
    const entry = prefs.locator(`a[href$="${PAGE}"]`).first();
    h.assert(await entry.count() === 1, 'The doctor\'s Preferences do not offer the description template link');
    await revealAuditLink(prefs, entry, TIMEOUT);
    const since = { responses: recorder.badResponses.length, console: recorder.consoleIssues.length };
    const statuses = [];
    context.on('response', response => { if (new URL(response.url()).pathname.endsWith(PAGE)) statuses.push(response.status()); });
    const popup = await clickOpensPopupOrNavigates(prefs, entry, { context, recorder, label: 'doctor-description-templates', timeout: TIMEOUT })
      .then(outcome => outcome.page);
    await popup.waitForLoadState('load', { timeout: TIMEOUT }).catch(() => {});
    const offered = await popup.locator('#docType').count();
    if (!offered) {
      // The refusal is this step's finding: consume only what the page's own refusal put on the strict
      // recorder (its response and the console line about it), so any other new error still fails the run.
      const refused = url => new URL(url || 'http://x', 'http://x').pathname.endsWith(PAGE);
      for (let i = recorder.badResponses.length - 1; i >= since.responses; i--) {
        if (refused(recorder.badResponses[i].url)) recorder.badResponses.splice(i, 1);
      }
      for (let i = recorder.consoleIssues.length - 1; i >= since.console; i--) {
        if (refused(recorder.consoleIssues[i].location && recorder.consoleIssues[i].location.url)) recorder.consoleIssues.splice(i, 1);
      }
    }
    h.assert(offered === 1, `The doctor's own description templates page was refused (HTTP ${statuses.join(', ') || 'none'})`);
  });
}

if (require.main === module) runWorkflow('document-description-templates', workflow, { openPatient: false });
module.exports = { workflow };
