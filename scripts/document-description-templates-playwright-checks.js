#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Document description templates: the clinic-wide list (Administration ▸ Document Description
 * Template, admin/DisplayDocumentDescriptionTemplate?setDefault=true), a provider's own list (the
 * same page from Preferences) and the shortcut buttons Incoming Docs builds from them. A provider
 * without administration rights keeps a list of their own and uses it in Incoming Docs, and cannot
 * reach another provider's entries (issue #5057). provider-preferences covers the administrator test
 * login's own list.
 *
 * User path: Schedule ▸ Administration ▸ Document Description Template (frame) ▸ document type ▸
 * shortcut + description ▸ Add; the entry ▸ Update; the entry ▸ Delete (POST DocumentDescriptionTemplate).
 * Then, signed in as a provider with only the doctor role: Schedule ▸ Preferences ▸ Document
 * description template link ▸ clear "use clinic default" ▸ Add, Update, Delete; Schedule ▸ Inbox ▸
 * Incoming Docs ▸ Data Entry Mode Fast ▸ document type ▸ shortcut button; Schedule ▸ Administration ▸
 * Document Description Template.
 * Asserted: Add stores a clinic-wide entry (no provider) and lists it, while a provider's own entry of
 * the same type is not listed there; Update changes it and keeps it clinic-wide; Delete removes it and
 * leaves the provider's entry. The doctor opens their own list from Preferences; Add stores an entry
 * under the doctor's provider number and the list shows neither the clinic's nor another provider's
 * entries; Update keeps it the doctor's; Delete removes it. Incoming Docs offers the doctor's own
 * shortcuts while they use their own list, and the clinic's once they use the clinic default, and a
 * shortcut fills the description. Crafted requests from the doctor's session to read, add, change or
 * delete another provider's entries are refused by the application and change no row. The doctor
 * role's seeded _admin.document grant opens the clinic-wide list from Administration.
 * Fixtures: the test login's own entry, a clinic-wide entry for the doctor's steps and the doctor login
 * (lib/throwaway-login-fixture.js, role doctor; its preferences, entry mode included, go with it);
 * every entry carries the run marker. Cleanup deletes the entries by marker and the throwaway login,
 * and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const TIMEOUT = 20000;
const PAGE = '/admin/DisplayDocumentDescriptionTemplate';
const ENDPOINT = '/DocumentDescriptionTemplate';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  clinicAdd: 'Add stores a clinic-wide entry and lists it, but not a provider\'s own entry',
  clinicUpdate: 'Update changes the entry and keeps it clinic-wide',
  clinicDelete: 'Delete removes the entry and leaves the provider\'s own entry',
  doctorOpens: 'a doctor, whose Preferences offer the link, can open their own description templates',
  doctorOwnList: 'the doctor\'s Add, Update and Delete change only an entry of their own',
  incomingDocs: 'Incoming Docs offers the doctor\'s own shortcuts, then the clinic\'s, and a shortcut fills the description',
  crossProvider: 'the doctor\'s session cannot read, add, change or delete another provider\'s entries',
  doctorClinicList: 'the doctor\'s _admin.document grant opens the clinic-wide list from Administration',
};

/** Waits for the template page to load into an Administration panel frame. */
async function templateFrame(page) {
  const deadline = Date.now() + TIMEOUT;
  let frame;
  while (!(frame = page.frames().find(f => f.parentFrame() && new URL(f.url(), 'http://x').pathname.endsWith(PAGE)))) {
    h.assert(Date.now() < deadline, 'Document Description Template did not load into the administration frame');
    await page.waitForTimeout(100);
  }
  await frame.locator('#docType').waitFor({ timeout: TIMEOUT });
  return frame;
}

async function openClinicList(schedule, context, recorder, label) {
  const { page: admin } = await clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel, #admin2').first(),
    { context, recorder, label, timeout: TIMEOUT });
  const link = admin.locator(`a.xlink[rel*="${PAGE}?setDefault=true"]`).first();
  await link.waitFor({ state: 'attached', timeout: TIMEOUT });
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  return templateFrame(admin);
}

async function workflow(s) {
  const { sql, marker, provider, config, recorder } = s;
  const q = h.sqlString;
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  const ownedWhere = `description LIKE ${q(`${marker}%`)}`;
  const owned = () => sql.value(`SELECT COUNT(*) FROM documentDescriptionTemplate WHERE ${ownedWhere}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM documentDescriptionTemplate WHERE ${ownedWhere}`);
    h.assert(owned() === '0', 'Owned description templates were not removed');
    fixture.cleanup();
  });
  fixture.create({ roleNames: ['doctor'] });
  h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(fixture.providerNo)} AND role_name<>'doctor'`) === '0',
    'The doctor login holds other roles');
  const shortcut = marker.slice(-12);
  const clinic = { description: `${marker} clinic O'Neil "A&B+"`, edited: `${marker} clinic edited` };

  const frame = await openClinicList(s.schedule, s.context, recorder, 'administration');
  await h.assertNotErrorPage(frame, 'clinic description templates');
  const type = await frame.locator('#docType option').nth(1).getAttribute('value');
  h.assert(type, 'The page offers no document type');
  // The test login's own entry of the same type: it must stay off the clinic-wide list.
  sql.execute(`INSERT INTO documentDescriptionTemplate (doctype,description,descriptionShortcut,provider_no,lastUpdated)
    VALUES (${q(type)},${q(`${marker} own`)},${q(`o${shortcut}`.slice(0, 20))},${q(provider)},NOW())`);
  const ownId = sql.value(`SELECT id FROM documentDescriptionTemplate WHERE description=${q(`${marker} own`)}`);
  const clinicRow = () => sql.rows(`SELECT id, IFNULL(provider_no,'clinic'), doctype, descriptionShortcut, description FROM documentDescriptionTemplate
    WHERE description IN (${q(clinic.description)},${q(clinic.edited)})`);
  // HTMLOptionElement.text collapses the list's padding spaces, leaving "(shortcut) description".
  const optionText = (target, id) => target.locator(`#docDescList option[value="${id}"]`).evaluate(option => option.text);
  const select = async (target, id) => {
    await target.locator(`#docDescList option[value="${id}"]`).waitFor({ state: 'attached', timeout: TIMEOUT });
    await target.locator('#docDescList').selectOption(id);
    await target.waitForFunction(value => document.querySelector('[name="descriptionId"]').value === value, id, { timeout: TIMEOUT });
  };

  let id;
  await s.step(STEP.clinicAdd, async () => {
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
    h.assert(await optionText(frame, id) === `(${shortcut}) ${clinic.description}`,
      `The list shows the entry as ${JSON.stringify(await optionText(frame, id))}, not its shortcut and description as typed`);
    h.assert(await frame.locator(`#docDescList option[value="${ownId}"]`).count() === 0,
      'The clinic-wide list shows a provider\'s own entry');
  });

  await s.step(STEP.clinicUpdate, async () => {
    await select(frame, id);
    h.assert(await frame.locator('[name="docDescription"]').inputValue() === clinic.description, 'The entry did not load its description');
    await frame.locator('[name="docDescription"]').fill(clinic.edited);
    await frame.locator('#updateDescription').click();
    await expectValue(sql, `SELECT CONCAT_WS('|', IFNULL(provider_no,'clinic'), description) FROM documentDescriptionTemplate WHERE id=${id}`,
      `clinic|${clinic.edited}`, 'Update did not change the clinic-wide entry');
    await frame.locator(`#docDescList option[value="${id}"]`, { hasText: clinic.edited }).waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await optionText(frame, id) === `(${shortcut}) ${clinic.edited}`, `The list shows the updated entry as ${JSON.stringify(await optionText(frame, id))}`);
  });

  await s.step(STEP.clinicDelete, async () => {
    await select(frame, id);
    await frame.locator('#deleteDescription').click();
    await expectValue(sql, `SELECT COUNT(*) FROM documentDescriptionTemplate WHERE id=${id}`, '0', 'Delete did not remove the entry');
    h.assert(sql.value(`SELECT COUNT(*) FROM documentDescriptionTemplate WHERE id=${ownId}`) === '1', 'Delete removed the provider\'s own entry');
    await frame.locator(`#docDescList option[value="${id}"]`).waitFor({ state: 'detached', timeout: TIMEOUT });
    h.assert(await frame.locator('#docDescList option').count() >= 1, 'The list is gone after Delete');
  });

  // The doctor's steps: a clinic-wide entry they may read but not change, and the test login's own
  // entry (ownId) they may neither read nor change.
  const kept = { description: `${marker} clinic kept`, shortcut: `k${shortcut}`.slice(0, 20) };
  sql.execute(`INSERT INTO documentDescriptionTemplate (doctype,description,descriptionShortcut,provider_no,lastUpdated)
    VALUES (${q(type)},${q(kept.description)},${q(kept.shortcut)},NULL,NOW())`);
  const keptId = sql.value(`SELECT id FROM documentDescriptionTemplate WHERE description=${q(kept.description)}`);
  const doctorEntry = { shortcut: `d${shortcut}`.slice(0, 20), description: `${marker} doctor own`, edited: `${marker} doctor own edited` };
  const doctorRow = () => sql.rows(`SELECT id, IFNULL(provider_no,'clinic'), description FROM documentDescriptionTemplate
    WHERE description IN (${q(doctorEntry.description)},${q(doctorEntry.edited)})`);

  const context = await h.newContext(s.context.browser(), config);
  s.cleanup(() => context.close());
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', page => h.wireStrictPage(page, 'doctor', recorder));
  let schedule;
  let scheduleUrl;
  let own;

  await s.step(STEP.doctorOpens, async () => {
    schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'doctor-login' });
    scheduleUrl = schedule.url();
    const prefs = await clickOpensPopupOrNavigates(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
      { context, recorder, label: 'doctor-preferences', timeout: TIMEOUT }).then(outcome => outcome.page);
    const entry = prefs.locator(`a[href$="${PAGE}"]`).first();
    h.assert(await entry.count() === 1, 'The doctor\'s Preferences do not offer the description template link');
    await revealAuditLink(prefs, entry, TIMEOUT);
    const statuses = [];
    context.on('response', response => { if (new URL(response.url()).pathname.endsWith(PAGE)) statuses.push(response.status()); });
    own = await clickOpensPopupOrNavigates(prefs, entry, { context, recorder, label: 'doctor-description-templates', timeout: TIMEOUT })
      .then(outcome => outcome.page);
    await own.waitForLoadState('load', { timeout: TIMEOUT }).catch(() => {});
    h.assert(await own.locator('#docType').count() === 1,
      `The doctor's own description templates page was refused (HTTP ${statuses.join(', ') || 'none'})`);
    await h.assertNotErrorPage(own, 'doctor description templates');
    h.assert(await own.locator('#useclinicdefault').count() === 1, 'The doctor\'s own list offers no "use clinic default" choice');
  });

  const preference = `SELECT IFNULL(MAX(value),'none') FROM property
    WHERE provider_no=${q(fixture.providerNo)} AND name='document_description_template'`;
  const savePreference = async useClinic => {
    const checkbox = own.locator('#useclinicdefault');
    if (await checkbox.isChecked() === useClinic) return;
    await Promise.all([
      own.waitForResponse(r => new URL(r.url()).pathname.endsWith(ENDPOINT)
        && (r.request().postData() || '').includes('saveDocumentDescriptionTemplatePreference'), { timeout: TIMEOUT }),
      useClinic ? checkbox.check() : checkbox.uncheck(),
    ]);
    await expectValue(sql, preference, useClinic ? 'Clinic' : 'User', 'The template preference was not saved');
  };

  await s.step(STEP.doctorOwnList, async () => {
    await savePreference(false);
    await own.locator('#docType').selectOption(type);
    await own.locator('#docDescList').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await own.locator(`#docDescList option[value="${keptId}"]`).count() === 0, 'The doctor\'s own list shows a clinic-wide entry');
    h.assert(await own.locator(`#docDescList option[value="${ownId}"]`).count() === 0, 'The doctor\'s own list shows another provider\'s entry');
    await own.locator('[name="docDescriptionShortcut"]').fill(doctorEntry.shortcut);
    await own.locator('[name="docDescription"]').fill(doctorEntry.description);
    await own.locator('#addDescription').click();
    await expectValue(sql, `SELECT COUNT(*) FROM documentDescriptionTemplate WHERE description=${q(doctorEntry.description)}`, '1',
      'The doctor\'s Add did not store exactly one entry');
    const [row] = doctorRow();
    h.assert(row[1] === fixture.providerNo, `The doctor's Add did not store an entry of their own: ${JSON.stringify(row)}`);
    await select(own, row[0]);
    await own.locator('[name="docDescription"]').fill(doctorEntry.edited);
    await own.locator('#updateDescription').click();
    await expectValue(sql, `SELECT CONCAT_WS('|', IFNULL(provider_no,'clinic'), description) FROM documentDescriptionTemplate WHERE id=${row[0]}`,
      `${fixture.providerNo}|${doctorEntry.edited}`, 'The doctor\'s Update did not keep the entry their own');
    await select(own, row[0]);
    await own.locator('#deleteDescription').click();
    await expectValue(sql, `SELECT COUNT(*) FROM documentDescriptionTemplate WHERE id=${row[0]}`, '0', 'The doctor\'s Delete did not remove the entry');
    h.assert(await own.locator('#templateStatus').innerText() === '', 'The doctor\'s own list reported a failed request');
  });

  await s.step(STEP.incomingDocs, async () => {
    // A fresh own entry for the shortcut buttons; the one the doctor typed is gone.
    const mine = { description: `${marker} doctor shortcut`, shortcut: `m${shortcut}`.slice(0, 20) };
    sql.execute(`INSERT INTO documentDescriptionTemplate (doctype,description,descriptionShortcut,provider_no,lastUpdated)
      VALUES (${q(type)},${q(mine.description)},${q(mine.shortcut)},${q(fixture.providerNo)},NOW())`);
    const opened = await clickOpensPopupOrNavigates(schedule, schedule.locator('#inboxLink').first(),
      { context, recorder, label: 'doctor-inbox', timeout: TIMEOUT });
    const inbox = opened.page;
    const reached = await clickOpensPopupOrNavigates(inbox, inbox.locator('a[href*="ViewIncomingDocs"]').first(),
      { context, recorder, label: 'doctor-incoming-docs', timeout: TIMEOUT });
    const incoming = reached.page;
    await incoming.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await h.assertNotErrorPage(incoming, 'doctor incoming docs');
    if (await incoming.locator('#entryModeList').inputValue() !== 'Fast') {
      await Promise.all([incoming.waitForEvent('domcontentloaded', { timeout: TIMEOUT }), incoming.locator('#entryModeList').selectOption('Fast')]);
      await incoming.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    }
    h.assert(await incoming.locator('#entryModeList').inputValue() === 'Fast', 'Data Entry Mode did not switch to Fast');
    const shortcuts = async () => {
      const read = incoming.waitForResponse(r => new URL(r.url()).pathname.endsWith(ENDPOINT)
        && (r.request().postData() || '').includes('getDocumentDescriptionFromDocType'), { timeout: TIMEOUT });
      await incoming.locator('#docType').selectOption({ index: 0 });
      await incoming.locator('#docType').selectOption(type);
      const response = await read;
      h.assert(response.status() === 200, `Incoming Docs' shortcut read answered HTTP ${response.status()}`);
      // The buttons (or the failure note) are added after the response is parsed.
      await incoming.waitForFunction(() => document.querySelector('#docDescriptionList').childElementCount > 0,
        null, { timeout: TIMEOUT });
      return incoming.locator('#docDescriptionList input[type="button"]').evaluateAll(buttons => buttons.map(b => b.value));
    };
    const ownShortcuts = await shortcuts();
    h.assert(ownShortcuts.includes(mine.shortcut), `Incoming Docs did not offer the doctor's own shortcut: ${JSON.stringify(ownShortcuts)}`);
    h.assert(!ownShortcuts.includes(kept.shortcut), 'Incoming Docs offered a clinic-wide shortcut while the doctor uses their own list');
    h.assert(await incoming.locator('#docDescriptionList .text-danger').count() === 0, 'Incoming Docs reported the shortcuts unavailable');
    await incoming.locator(`#docDescriptionList input[type="button"][value="${mine.shortcut}"]`).click();
    h.assert(await incoming.locator('#documentDescription').inputValue() === mine.description,
      'The doctor\'s shortcut did not fill the description');

    await savePreference(true);
    await incoming.reload();
    await incoming.locator('#docType').waitFor({ timeout: TIMEOUT });
    const clinicShortcuts = await shortcuts();
    h.assert(clinicShortcuts.includes(kept.shortcut), `Incoming Docs did not offer the clinic's shortcut: ${JSON.stringify(clinicShortcuts)}`);
    h.assert(!clinicShortcuts.includes(mine.shortcut), 'Incoming Docs offered the doctor\'s own shortcut while they use the clinic default');
    // Close only what this step opened as a popup: the Inbox link can navigate the schedule page
    // itself, and the last step reopens the schedule in a fresh tab either way.
    if (reached.isPopup) await incoming.close();
    if (opened.isPopup) await inbox.close();
  });

  await s.step(STEP.crossProvider, async () => {
    const token = await own.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    h.assert(token, 'The doctor\'s template page has no CSRF token');
    const post = form => context.request.post(h.appUrl(config.baseUrl, ENDPOINT), {
      form, maxRedirects: 0, headers: { 'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest' },
    });
    const unchanged = `id=${ownId} AND provider_no=${q(provider)} AND description=${q(`${marker} own`)}`;
    const probes = [
      ['reading another provider\'s entry', { method: 'getDocumentDescriptionFromId', id: ownId }, unchanged, '1'],
      ['listing another provider\'s entries', { method: 'getDocumentDescriptionFromDocType', doctype: type,
        useDocumentDescriptionTemplateType: 'User', providerNo: provider }, unchanged, '1'],
      ['adding to another provider\'s list', { method: 'addDocumentDescription', doctype: type, providerNo: provider,
        description: `${marker} planted`, shortcut: 'planted' }, `description=${q(`${marker} planted`)}`, '0'],
      ['changing another provider\'s entry', { method: 'updateDocumentDescription', id: ownId, doctype: type, providerNo: provider,
        description: `${marker} taken`, shortcut: 'taken' }, unchanged, '1'],
      ['moving another provider\'s entry into the doctor\'s list', { method: 'updateDocumentDescription', id: ownId, doctype: type,
        providerNo: fixture.providerNo, description: `${marker} own`, shortcut: 'taken' }, unchanged, '1'],
      ['deleting another provider\'s entry', { method: 'deleteDocumentDescription', id: ownId }, unchanged, '1'],
    ];
    for (const [label, form, where, before] of probes) {
      h.assert(sql.value(`SELECT COUNT(*) FROM documentDescriptionTemplate WHERE ${where}`) === before, `Fixture drifted before ${label}`);
      const response = await post(form);
      try {
        await h.assertRefused(s, { response, table: 'documentDescriptionTemplate', where, before, label });
        h.assert(!(await response.text()).includes(`${marker} own`), `${label} returned the other provider's entry`);
      } finally { await response.dispose(); }
    }
  });

  await s.step(STEP.doctorClinicList, async () => {
    h.assert(sql.value(`SELECT IFNULL(MAX(privilege),'none') FROM secObjPrivilege WHERE roleUserGroup='doctor' AND objectName='_admin.document'`) === 'x',
      'Precondition: the doctor role no longer holds the seeded _admin.document grant');
    const home = await context.newPage();
    await home.goto(scheduleUrl, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    const doctorFrame = await openClinicList(home, context, recorder, 'doctor-administration');
    await h.assertNotErrorPage(doctorFrame, 'doctor clinic description templates');
    await doctorFrame.locator('#docType').selectOption(type);
    await doctorFrame.locator(`#docDescList option[value="${keptId}"]`).waitFor({ state: 'attached', timeout: TIMEOUT });
    await doctorFrame.locator('#addDescription').waitFor({ state: 'visible', timeout: TIMEOUT });
  });
}

if (require.main === module) runWorkflow('document-description-templates', workflow, { openPatient: false });
module.exports = { workflow, STEP };
