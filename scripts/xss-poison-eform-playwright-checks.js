#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the eForm lists, groups and managers.
// User path: Administration > Manage eForms (list, Edit), eForm Groups (group view, Delete group
// confirmation modal), Patient-independent eForm; Master Record > eForms (current list, Add eForm,
// Deleted eForms).
// Fixtures: an owned FAKE patient, an eForm template and a patient-independent template with inert markup
// in the name, subject and file name, a group, and current/deleted/independent instances (INSERTed,
// bypassing the WAF). Cleanup removes exactly those rows by key and asserts they are gone.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error, and the
// seeded values that surface lists are shown; a surface that is not offered is a MISSING finding. Findings
// are collected for the whole walk and the check fails once at the end. The group Delete control is
// only OPENED (confirmation modal inspected, then dismissed); nothing is deleted through the UI.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, openMasterByChartNo, clickAdminItem, fieldIds } = require('./lib/xss-poison-helpers');
const { seedPatient } = require('./lib/xss-poison-patient');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

const HTML = '<html><head><title>xp</title></head><body><form method="post" action="" name="FormName"><input type="text" name="note"></form></body></html>';

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 600: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 600;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-8);
  const chartNo = `XF${hex}`;
  let groupField; let templateId;
  await s.step('seed the poisoned patient, eForm templates, group and instances', async () => {
    const demo = seedPatient(seed, P, '999998', chartNo);
    const d = Number(demo);
    const tpl = (independent) => seed.insert('eform', { form_name: P(independent ? 'independent eform name' : 'eform name'), file_name: P(independent ? 'independent eform file' : 'eform file'),
      subject: P(independent ? 'independent eform subject' : 'eform subject'), form_date: { raw: 'CURDATE()' }, form_time: { raw: 'CURTIME()' }, form_creator: '999998', status: 1,
      form_html: HTML, showLatestFormOnly: 0, patient_independent: independent ? 1 : 0, roleType: '', restrictToProgram: 0, stable: 1 }, { key: 'fid' });
    seed.insert('secRole', { role_name: P('role name', 60), description: P('role description', 60) }, { key: 'role_no' });
    const fid = tpl(false);
    templateId = fid;
    const independentFid = tpl(true);
    groupField = n + 1;
    seed.insert('eform_groups', { fid: Number(fid), group_name: P('eform group name', 20) }, { key: 'id' });
    for (const status of [1, 0]) {
      seed.insert('eform_data', { fid: Number(fid), form_name: P(`eform instance name (${status ? 'current' : 'deleted'})`), subject: P(`eform instance subject (${status ? 'current' : 'deleted'})`),
        demographic_no: d, status, form_date: { raw: 'CURDATE()' }, form_time: { raw: 'CURTIME()' }, form_provider: '999998', form_data: HTML, showLatestFormOnly: 0, patient_independent: 0 }, { key: 'fdid' });
    }
    for (const status of [1, 0]) {
      seed.insert('eform_data', { fid: Number(independentFid), form_name: P(`independent instance name (${status ? 'current' : 'deleted'})`), subject: P(`independent instance subject (${status ? 'current' : 'deleted'})`),
        demographic_no: 0, status, form_date: { raw: 'CURDATE()' }, form_time: { raw: 'CURTIME()' }, form_provider: '999998', form_data: HTML, showLatestFormOnly: 0, patient_independent: 1 }, { key: 'fdid' });
    }
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  const E = (...names) => fieldIds(fields, ...names);
  // The panel loads these pages into itself by AJAX (contentLink), and the page reached networkidle long before,
  // so a load-state wait returns at once: wait for the page's own response, then let it render.
  const openInPanel = async (link, route) => {
    const loaded = admin.waitForResponse(r => r.url().includes(route), { timeout: 20000 });
    await link.click();
    await loaded;
    await admin.waitForTimeout(800);
  };
  let admin; let adminIsPopup = true;
  const dayUrl = s.schedule.url();
  await step('Administration > Manage eForms and the template editor show stored values as text', async () => {
    const opened = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, label: 'administration', recorder: s.recorder, timeout: 20000 });
    admin = opened.page; adminIsPopup = opened.isPopup;
    let since = f.mark();
    await clickAdminItem(admin, 'Manage eForms');
    await admin.waitForTimeout(2000); // the upload/import panes load into frames after the list
    await inspect(f, 'admin manage eForms (list, upload form with role select)', admin, fields, since, { expect: E('eform name', 'eform subject', 'role name') });
    // The seeded template's own Edit link (the list holds every template; the first one is somebody else's).
    const edit = admin.locator(`a[href*="efmformmanageredit?fid=${templateId}"]`).first();
    if (await edit.count()) {
      since = f.mark();
      await openInPanel(edit, 'efmformmanageredit');
      await inspect(f, 'admin eForm editor', admin, fields, since, { expect: E('eform name', 'eform subject') });
    } else f.missing('admin eForm editor', 'Manage eForms offers no Edit link for the seeded template');
  });
  await step('eForm Groups, the group view and the Delete confirmation show stored values as text', async () => {
    let since = f.mark();
    await clickAdminItem(admin, 'eForm Groups');
    await inspect(f, 'admin eForm groups', admin, fields, since, { expect: E('eform group name') });
    const mine = admin.locator('tr', { has: admin.locator(`td[title*="data-xp=${groupField}>"]`) });
    if (await mine.count()) {
      since = f.mark();
      await mine.locator('a[data-confirm]').first().click();
      await admin.locator('#confirmModalBody').waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
      await admin.waitForTimeout(300);
      await inspect(f, 'eForm group delete confirmation', admin, fields, since, { expect: E('eform group name') });
      await admin.keyboard.press('Escape');
      await admin.waitForTimeout(300);
      since = f.mark();
      const view = mine.locator('a.contentLink').first();
      await openInPanel(view, new URL(await view.getAttribute('href'), admin.url()).pathname.split('/').pop());
      await inspect(f, 'admin eForm group view', admin, fields, since, { expect: E('eform name') });
    } else f.add('admin eForm groups', 'MISSING', 'the seeded group is not listed');
  });
  await step('Patient-independent eForm lists show stored values as text', async () => {
    let since = f.mark();
    await clickAdminItem(admin, 'Patient-independent eForm');
    await inspect(f, 'admin patient-independent eForms', admin, fields, since, { expect: E('independent instance name (current)', 'independent instance subject (current)') });
    const deleted = admin.locator('a[href*="efmmanageindependentdeleted"]').first();
    if (await deleted.count()) {
      since = f.mark();
      await openInPanel(deleted, 'efmmanageindependentdeleted');
      await inspect(f, 'admin deleted independent eForms', admin, fields, since, { expect: E('independent instance name (deleted)') });
    } else f.missing('admin deleted independent eForms', 'Patient-independent eForm offers no deleted-forms link');
    if (adminIsPopup) await admin.close(); else await s.schedule.goto(dayUrl);
  });
  await step('Master Record > eForms, Add eForm and Deleted eForms show stored values as text', async () => {
    const { master } = await openMasterByChartNo(s, chartNo);
    let since = f.mark();
    await ui.clickAndAwaitReload(master, master.locator('a[href*="/eform/efmpatientformlist?"]').first(), { timeout: 20000, label: 'Master Record eForms' });
    await inspect(f, 'patient eForm list', master, fields, since, { expect: E('patient last name', 'patient first name', 'eform instance name (current)', 'eform instance subject (current)') });
    for (const [route, surface, expect] of [['efmformslistadd', 'patient Add eForm list', E('eform name', 'eform subject')],
      ['efmpatientformlistdeleted', 'patient Deleted eForms list', E('eform instance name (deleted)', 'eform instance subject (deleted)')]]) {
      since = f.mark();
      await ui.clickAndAwaitReload(master, master.locator(`a[href^="${route}?"], a[href*="/eform/${route}?"]`).first(), { timeout: 20000, label: surface });
      await inspect(f, surface, master, fields, since, { expect });
    }
    await releaseChartLocks(s.context, s.config.baseUrl, [master]);
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('eForm walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-eform', workflow, { openPatient: false });
