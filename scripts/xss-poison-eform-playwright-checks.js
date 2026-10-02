#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the eForm lists, groups and managers.
// User path: Administration > Manage eForms (list, Edit), eForm Groups (group view, Delete group
// confirmation modal), Patient-independent eForm; Master Record > eForms (current list, Add eForm,
// Deleted eForms).
// Fixtures: an owned FAKE patient, an eForm template and a patient-independent template with inert markup
// in the name, subject and file name, a group, and current/deleted/independent instances (INSERTed,
// bypassing the WAF). Cleanup removes exactly those rows by key and asserts they are gone.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error;
// findings are collected for the whole walk and the check fails once at the end. The group Delete control is
// only OPENED (confirmation modal inspected, then dismissed); nothing is deleted through the UI.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, openMasterByChartNo, clickAdminItem } = require('./lib/xss-poison-helpers');
const { seedPatient, purgeSupport } = require('./lib/xss-poison-patient');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

const HTML = '<html><head><title>xp</title></head><body><form method="post" action="" name="FormName"><input type="text" name="note"></form></body></html>';

async function workflow(s) {
  const fields = {};
  let n = 0;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup);
  const hex = s.marker.slice(-8);
  const chartNo = `XF${hex.slice(0, 6)}`;
  let groupField;
  await s.step('seed the poisoned patient, eForm templates, group and instances', async () => {
    const demo = seedPatient(seed, P, '999998', chartNo);
    s.cleanup(() => purgeSupport(s.sql, demo));
    const d = Number(demo);
    const tpl = (independent) => seed.insert('eform', { form_name: P(independent ? 'independent eform name' : 'eform name'), file_name: P(independent ? 'independent eform file' : 'eform file'),
      subject: P(independent ? 'independent eform subject' : 'eform subject'), form_date: { raw: 'CURDATE()' }, form_time: { raw: 'CURTIME()' }, form_creator: '999998', status: 1,
      form_html: HTML, showLatestFormOnly: 0, patient_independent: independent ? 1 : 0, roleType: '', restrictToProgram: 0, stable: 1 }, { key: 'fid' });
    seed.insert('secRole', { role_name: P('role name', 60), description: P('role description', 60) }, { key: 'role_no' });
    const fid = tpl(false);
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
  let admin; let adminIsPopup = true;
  const dayUrl = s.schedule.url();
  await step('Administration > Manage eForms and the template editor show stored values as text', async () => {
    const opened = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, label: 'administration', recorder: s.recorder, timeout: 20000 });
    admin = opened.page; adminIsPopup = opened.isPopup;
    const open = async (text, surface) => {
      const since = f.mark();
      await clickAdminItem(admin, text);
      await admin.waitForTimeout(2000); // the upload/import panes load into frames after the list
      await inspect(f, surface, admin, fields, since);
    };
    await open('Manage eForms', 'admin manage eForms (list, upload form with role select)');
    const edit = admin.locator('a[href*="efmformmanageredit?fid="]').first();
    if (await edit.count()) {
      const since = f.mark();
      await edit.click();
      await admin.waitForLoadState('networkidle').catch(() => {});
      await inspect(f, 'admin eForm editor', admin, fields, since);
    }
  });
  await step('eForm Groups, the group view and the Delete confirmation show stored values as text', async () => {
    let since = f.mark();
    await clickAdminItem(admin, 'eForm Groups');
    await inspect(f, 'admin eForm groups', admin, fields, since);
    const mine = admin.locator('tr', { has: admin.locator(`td[title*="data-xp=${groupField}>"]`) });
    if (await mine.count()) {
      since = f.mark();
      await mine.locator('a[data-confirm]').first().click();
      await admin.locator('#confirmModalBody').waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
      await admin.waitForTimeout(300);
      await inspect(f, 'eForm group delete confirmation', admin, fields, since);
      await admin.keyboard.press('Escape');
      await admin.waitForTimeout(300);
      since = f.mark();
      await mine.locator('a.contentLink').first().click();
      await admin.waitForLoadState('networkidle').catch(() => {});
      await inspect(f, 'admin eForm group view', admin, fields, since);
    } else f.add('admin eForm groups', 'MISSING', 'the seeded group is not listed');
  });
  await step('Patient-independent eForm lists show stored values as text', async () => {
    let since = f.mark();
    await clickAdminItem(admin, 'Patient-independent eForm');
    await inspect(f, 'admin patient-independent eForms', admin, fields, since);
    const deleted = admin.locator('a[href*="efmmanageindependentdeleted"]').first();
    if (await deleted.count()) {
      since = f.mark();
      await deleted.click();
      await admin.waitForLoadState('networkidle').catch(() => {});
      await inspect(f, 'admin deleted independent eForms', admin, fields, since);
    }
    if (adminIsPopup) await admin.close(); else await s.schedule.goto(dayUrl);
  });
  await step('Master Record > eForms, Add eForm and Deleted eForms show stored values as text', async () => {
    const { master } = await openMasterByChartNo(s, chartNo);
    let since = f.mark();
    await ui.clickAndAwaitReload(master, master.locator('a[href*="/eform/efmpatientformlist?"]').first(), { timeout: 20000, label: 'Master Record eForms' });
    await inspect(f, 'patient eForm list', master, fields, since);
    for (const [route, surface] of [['efmformslistadd', 'patient Add eForm list'], ['efmpatientformlistdeleted', 'patient Deleted eForms list']]) {
      since = f.mark();
      await ui.clickAndAwaitReload(master, master.locator(`a[href^="${route}?"], a[href*="/eform/${route}?"]`).first(), { timeout: 20000, label: surface });
      await inspect(f, surface, master, fields, since);
    }
    await releaseChartLocks(s.context, s.config.baseUrl, [master]).catch(() => {});
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('eForm walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-eform', workflow, { openPatient: false });
