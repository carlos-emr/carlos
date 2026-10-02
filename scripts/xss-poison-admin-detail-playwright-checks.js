#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the Administration pages that list a stored value only after a further click.
// User path: Schedule > Administration > Search/Edit/Delete Groups (own group);
// Search/Edit Provider Records > search by provider number > the record; Report by Template > a template;
// Document Description Template > a document type; Insert a Template; Customize Disease Registry Quick List.
// Fixtures (INSERTed, bypassing the WAF): a doctor with a group and a custom lookup list, a report template, a
// document type and description template, an encounter template and a dx quick list, every text column
// carrying inert markup. Cleanup removes exactly those rows by key and asserts they are gone.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error;
// findings are collected for the whole walk and the check fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, clickAdminItem, freeProviderNo } = require('./lib/xss-poison-helpers');

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 200: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 200;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-6);
  let provNo;
  const grp = `XD${hex}`.slice(0, 8);
  const lookupName = `xd${hex}`;
  let templateId;
  await s.step('seed inert-markup rows for the second-level administration pages', async () => {
    provNo = freeProviderNo(s.sql, 500000 + (parseInt(hex, 16) % 99000));
    seed.insert('provider', {
      provider_no: provNo, last_name: P('provider last', 30), first_name: P('provider first', 30), provider_type: 'doctor',
      sex: 'F', specialty: P('provider specialty', 40), team: P('provider team', 20), address: P('provider address', 40),
      phone: '555', work_phone: '555', ohip_no: '', billing_no: '', status: '1', comments: P('provider comments'),
      practitionerNo: '', init: '', job_title: P('provider job title', 100), email: P('provider email', 60),
      title: '', lastUpdateDate: { raw: 'NOW()' }, practitionerNoType: P('provider practitionerNoType'),
    }, { where: `provider_no='${provNo}'` });
    seed.insert('mygroup', { mygroup_no: `${grp}b`, provider_no: '999998', last_name: P('own group member last', 30), first_name: P('own group member first', 30), vieworder: '1' },
      { where: `mygroup_no='${grp}b' AND provider_no='999998'` });
    seed.insert('mygroup', { mygroup_no: grp, provider_no: provNo, last_name: P('group member last', 30), first_name: P('group member first', 30), vieworder: '1' },
      { where: `mygroup_no='${grp}' AND provider_no='${provNo}'` });
    const list = seed.insert('LookupList', { name: lookupName, listTitle: P('lookup list title', 50), description: P('lookup list description'), categoryId: 1, active: 1, createdBy: '999998' }, { key: 'id' });
    seed.insert('LookupListItem', { lookupListId: list, value: P('lookup item value', 50), label: P('lookup item label'), displayOrder: 1, active: 1, createdBy: '999998' }, { key: 'id' });
    seed.insert('quickList', { quickListName: P('dx quick list name'), createdByProvider: '999998', dxResearchCode: '250', codingSystem: 'icd9' }, { key: 'id' });
    seed.insert('ctl_doctype', { module: 'demographic', doctype: P('document type', 60), status: 'A' }, { key: 'id' });
    seed.insert('documentDescriptionTemplate', { doctype: 'consult', description: P('document description template'), descriptionShortcut: P('doc template shortcut', 20), provider_no: '999998' }, { key: 'id' });
    const templateName = P('encounter template name', 50);
    seed.insert('encountertemplate', { encountertemplate_name: templateName, encountertemplate_value: P('encounter template value'), creator: '999998', createdatetime: { raw: 'NOW()' } }, { where: `encountertemplate_name=${h.sqlString(templateName)}` });
    templateId = seed.insert('reportTemplates', { templatetitle: P('report template title', 80), templatedescription: P('report template description'), templatesql: 'SELECT 1', templatexml: '<report id="9991" title="xp" description="xp" active="1"><query>SELECT 1</query><parameters/></report>', active: 1, type: 'sql', uuid: `xd${hex}` }, { key: 'templateid' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  let admin; let adminIsPopup = true;
  const dayUrl = s.schedule.url();
  await step('open Administration from the schedule', async () => {
    const opened = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, label: 'administration', recorder: s.recorder, timeout: 20000 });
    admin = opened.page; adminIsPopup = opened.isPopup;
  });
  const scope = async () => ((await admin.locator('#dynamic-content iframe').count()) ? admin.frameLocator('#dynamic-content iframe').first() : admin);
  const reach = async (surface, action) => {
    const since = f.mark();
    try { await action(); } catch (error) {
      const links = await (await scope()).locator('a').evaluateAll(a => a.map(x => (x.getAttribute('href') || x.getAttribute('onclick') || '').slice(0, 70)).filter(Boolean).slice(0, 25)).catch(() => []);
      f.note(surface, `not reached: ${String(error.message).split('\n')[0].slice(0, 100)} | links: ${links.join(' ; ')}`);
      return;
    }
    await admin.waitForLoadState('networkidle').catch(() => {});
    await admin.waitForTimeout(500);
    await inspect(f, surface, admin, fields, since);
  };
  await step('lists reached by one more click show stored values as text', async () => {
    await reach('group members', async () => {
      await clickAdminItem(admin, 'Search/Edit/Delete Groups');
      await (await scope()).locator('tr', { hasText: `${grp}b` }).first().waitFor({ timeout: 10000 });
    });
    await reach('provider search results', async () => {
      await clickAdminItem(admin, 'Search/Edit Provider Records');
      await (await scope()).locator('input[name="search_mode"][value="search_providerno"]').check();
      await (await scope()).locator('input[name="keyword"]').fill(provNo);
      await (await scope()).locator('input[type="submit"]').first().click();
    });
    await reach('provider edit form', async () => {
      await (await scope()).locator('a[href*="ViewProviderUpdateProvider"]').first().click();
    });
    await reach('report template run page', async () => {
      await clickAdminItem(admin, 'Report by Template');
      await (await scope()).locator(`a[href*="templateid=${templateId}"], a[href*="reportId=${templateId}"]`).first().click();
    });
    await reach('document description templates', async () => {
      await clickAdminItem(admin, 'Document Description Template');
      await (await scope()).locator('select').first().selectOption({ index: 1 });
    });
    await reach('insert a template', async () => { await clickAdminItem(admin, 'Insert a Template'); });
    await reach('dx quick list customization', async () => { await clickAdminItem(admin, 'Customize Disease Registry Quick List'); });
    if (adminIsPopup) await admin.close(); else await s.schedule.goto(dayUrl);
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Administration detail walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-admin-detail', workflow, { openPatient: false });
