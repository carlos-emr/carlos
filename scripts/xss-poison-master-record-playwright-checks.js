#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the patient search results and the Master Record.
// User path: Schedule > Search > (Chart No search) > result row > Master Demographic File > Edit, then every
// link the Master Record offers (the same catalogue as master-record-tabs).
// Fixtures: one owned FAKE patient whose text columns carry inert markup (INSERTed, bypassing the WAF),
// a doctor/nurse provider whose names carry markup (they fill the Edit form's provider selects), and
// contacts/relationship rows. Cleanup removes exactly these rows by key and asserts they are gone.
// Asserted per page: literal text visible, no `[data-xp]` element in any frame, no script error. Findings
// are collected across the walk and the check fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { SKIP_ITEMS } = require('./master-record-tabs-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { payload, inspect, Findings, Seeder, walkLinks, openMasterByChartNo } = require('./lib/xss-poison-helpers');
const { seedPatient, purgeSupport } = require('./lib/xss-poison-patient');

async function workflow(s) {
  const fields = {};
  let n = 0;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup);
  const hex = s.marker.slice(-8);
  const chartNo = `XP${hex.slice(0, 6)}`;
  let demo;
  await s.step('seed the poisoned patient, provider and contacts', async () => {
    // One provider per role the Edit form offers (doctor, nurse, midwife); the role lists come from secUserRole.
    const providerNos = [];
    for (const [i, role] of ['doctor', 'nurse', 'midwife'].entries()) {
      const no = String(700000 + ((parseInt(hex, 16) + i * 7) % 99990));
      providerNos.push(no);
      seed.insert('provider', {
        provider_no: no, last_name: P(`${role} last name`, 30), first_name: P(`${role} first name`, 30), provider_type: role,
        sex: 'F', specialty: '', status: '1', lastUpdateDate: { raw: 'NOW()' },
      }, { where: `provider_no='${no}'` });
      seed.insert('secUserRole', { provider_no: no, role_name: role, activeyn: 1, lastUpdateDate: { raw: 'NOW()' } }, { key: 'id' });
    }
    const provNo = providerNos[0];
    demo = seedPatient(seed, P, provNo, chartNo);
    s.cleanup(() => purgeSupport(s.sql, demo));
    seed.insert('demographicExt', { demographic_no: Number(demo), provider_no: '999998', key_val: 'phoneComment', value: P('patient phone comment'), date_time: { raw: 'NOW()' } }, { key: 'id' });
    for (const key of ['hPhoneExt', 'wPhoneExt', 'cytolNum', 'demo_cell']) {
      seed.insert('demographicExt', { demographic_no: Number(demo), provider_no: '999998', key_val: key, value: P(`patient ${key}`, 20), date_time: { raw: 'NOW()' } }, { key: 'id' });
    }
    seed.insert('demographicExt', { demographic_no: Number(demo), provider_no: '999998', key_val: 'cell', value: P('patient cell', 20), date_time: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('demographiccust', { demographic_no: Number(demo), cust1: P('patient cust1'), cust2: P('patient cust2'), cust3: P('patient alert'), cust4: P('patient cust4'), content: P('patient notes') }, { where: `demographic_no=${demo}` });
    const contact = seed.insert('Contact', { type: 'personal', lastName: P('contact last name', 100), firstName: P('contact first name', 100), address: P('contact address'), city: P('contact city', 100), note: P('contact note'), deleted: 0 }, { key: 'id' });
    seed.insert('DemographicContact', { facilityId: 1, creator: '999998', demographicNo: Number(demo), contactId: contact, role: P('contact role', 100), type: 1, category: 'personal', note: P('contact relationship note', 200), active: 1, deleted: 0, consentToContact: 0 }, { key: 'id' });
    const tickler = seed.insert('tickler', { demographic_no: Number(demo), message: P('tickler message'), status: 'A', update_date: { raw: 'NOW()' }, service_date: { raw: 'DATE_SUB(NOW(), INTERVAL 1 DAY)' }, creator: '999998', priority: 'Normal', task_assigned_to: '999998' }, { key: 'tickler_no' });
    seed.insert('tickler_comments', { tickler_no: Number(tickler), message: P('tickler comment'), provider_no: provNo, update_date: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('relationships', { demographic_no: Number(demo), relation_demographic_no: Number(demo), relation: P('relationship relation', 20), creator: '999998', notes: P('relationship notes'), deleted: '0' }, { key: 'id' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  let search;
  await step('search the owned patient by chart number from the schedule', async () => {
    ({ page: search } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#search a').first(),
      { context: s.context, label: 'patient-search', recorder: s.recorder, timeout: 20000 }));
    await search.locator('#search_mode').selectOption('search_chart_no');
    await search.locator('#keyword, input[name="keyword"]').first().fill(chartNo);
    await ui.clickAndAwaitReload(search, search.locator("input[type='submit']").first(), { timeout: 20000, label: 'the patient search' });
    const row = search.locator('a[title="Master Demographic File"]');
    h.assert(await row.count() === 1, 'The chart-number search did not return exactly the owned patient');
    const since = f.mark();
    await inspect(f, 'patient search results', search, fields, since);
  });
  let master;
  await step('open the Master Record and inspect the view', async () => {
    master = await ui.clickOpensPopup(search, search.locator('a[title="Master Demographic File"]').first(),
      { context: s.context, label: 'master-record', recorder: s.recorder, timeout: 20000 });
    await master.locator('#editBtn').waitFor({ state: 'visible', timeout: 20000 });
    const since = f.mark();
    await inspect(f, 'master record view', master, fields, since);
  });
  await step('open the Edit form and inspect the provider selects and fields', async () => {
    await master.locator('#editBtn').click();
    await master.locator('#editDemographic').waitFor({ state: 'visible', timeout: 20000 });
    const since = f.mark();
    await inspect(f, 'master record edit form', master, fields, since);
  });
  await step('walk every Master Record link', async () => {
    const items = dedupe(await catalogueLinks(master));
    h.assert(items.length > 0, 'The Master Record offered no links');
    await walkLinks({ context: s.context, recorder: s.recorder, host: master, items, findings: f, fields, timeout: 40000, label: 'master', skip: SKIP_ITEMS,
      beforeClose: page => releaseChartLocks(s.context, s.config.baseUrl, [page]).catch(() => {}) });
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Master Record walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-master-record', workflow, { openPatient: false });
