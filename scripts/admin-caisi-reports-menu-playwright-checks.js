#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The CAISI entries of Administration ▸ Reports that no other check opens: Activity Report and
 * Population Report. (CDS Report and MIS Report have their own check, admin-cds-mis-reports.)
 *
 * CAISI_REPORT=activity or =population runs one report (the manifest's admin-caisi-activity-report
 * and admin-caisi-population-report entries, so each known failure has its own entry: a script
 * stops at its first failing step). Unset, it runs both, Activity first.
 *
 * Activity: Schedule ▸ Administration ▸ Reports ▸ "Activity Report" (an .xlink into the shell's
 * frame) shows the report, not an error page.
 *
 * Population: with the owned FAKE patient admitted to an owned FAKE active Service program, so
 * the report's current population is not 0, the legacy Administration page (admin/ViewAdmin,
 * which the CAISI admin pages' back buttons open) ▸ "Population Report" renders every section
 * (shelter population, intensity of shelter use, the three condition tables, the notes). Then
 * Administration ▸ Reports ▸ "Population Report" in the current shell opens the same report.
 * Only rendering is asserted, not counts: the action keeps its figures for an hour in static
 * fields shared by every user, so a run within an hour of another request sees that request's
 * figures.
 *
 * Known failures, held for the findings log: the Activity Report entry points at
 * PMmodule/Reports/ProgramActivityReport, which has no route, so the frame shows a 404; the shell's
 * Population Report entry has a `page` attribute and no href, so clicking it does nothing.
 *
 * Needs caisi=on, with ModuleNames including Caisi as the shipped carlos.properties pairs them
 * ("Load CAISI Application Context"): the menu entries render only with the caisi module, and the
 * report's beans load only with the Caisi context. Without the menu entries it SKIPs before
 * creating its program and admission (the runWorkflow patient is removed as usual).
 *
 * Fixtures: one FAKE Service program named with the run marker (inserted with SQL: the CAISI
 * Program editor needs _pmm_management, which no role holds on a fresh install) and one admission
 * of the runWorkflow FAKE patient to it, both deleted by their captured ids. The program shows in
 * the facility's lists while the run lasts, so the check needs an exclusive run. The report's
 * one-hour cache can keep counting the deleted admission for up to an hour after the run.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const ACTIVITY_LINK = 'a[rel$="/PMmodule/Reports/ProgramActivityReport"]';
const POPULATION_SECTIONS = ['Homeless Shelter Population', 'Intensity of Shelter Use', 'Major Medical Condition',
  'Major Mental Illness', 'Incidence in the past year of Serious Medical Conditions', 'Notes on the Data'];
const REPORT = process.env.CAISI_REPORT || '';
h.assert(['', 'activity', 'population'].includes(REPORT),
  `CAISI_REPORT must be activity, population or unset (was ${JSON.stringify(REPORT)})`);
const runs = report => !REPORT || REPORT === report;

async function openAdmin(s) {
  const { page } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'caisi-reports-admin', timeout: TIMEOUT });
  await page.locator('#adminNav').waitFor();
  return page;
}

/** Asserts a page or frame shows the Population Report with every section. */
async function assertPopulationReport(scope, how) {
  const text = (await scope.locator('body').innerText()).replace(/\s+/g, ' ');
  h.assert(!h.isApplicationErrorPage(text), `${how} showed an error page instead of the Population Report`);
  for (const section of POPULATION_SECTIONS) {
    h.assert(text.includes(section), `${how} did not show the "${section}" section of the Population Report`);
  }
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;

  const admin = await openAdmin(s);
  if (await admin.locator(`#adminNav ${ACTIVITY_LINK}`).count() === 0) {
    throw new h.SkipCheck('Administration ▸ Reports offers no Activity Report link; '
      + 'the CAISI report entries need the CAISI module (caisi=on in carlos.properties)');
  }

  if (runs('activity')) await s.step('Administration ▸ Reports ▸ Activity Report shows the activity report', async () => {
    const link = admin.locator(`#adminNav ${ACTIVITY_LINK}`).first();
    await revealAuditLink(admin, link, TIMEOUT);
    const answered = admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/PMmodule/Reports/ProgramActivityReport'),
      { timeout: TIMEOUT });
    await link.click();
    const response = await answered;
    const frame = admin.frame({ name: 'myFrame' });
    h.assert(frame, 'Activity Report did not open in the administration frame');
    await frame.waitForLoadState('load', { timeout: TIMEOUT });
    const text = (await frame.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(response.status() < 400 && !h.isApplicationErrorPage(text),
      `Activity Report answered HTTP ${response.status()} and showed ${JSON.stringify(text.slice(0, 80))} instead of the report`);
  });

  if (!runs('population')) return;

  const facilities = sql.rows('SELECT id FROM Facility ORDER BY id').map(([id]) => id);
  if (facilities.length !== 1) {
    throw new h.SkipCheck(`This database has ${facilities.length} facilities; the check needs the one facility a login lands in`);
  }
  const program = h.insertId(sql, `INSERT INTO program (facilityId,name,description,type,maxAllowed,
      holdingTank,allowBatchAdmission,allowBatchDischarge,hic,programStatus,transgender,firstNation,bedProgramAffiliated,
      alcohol,physicalHealth,mentalHealth,housing,exclusiveView,defaultServiceRestrictionDays,ageMin,ageMax,userDefined,
      facility_id,capacity_funding,capacity_space,lastUpdateDate,enableEncounterTime,enableEncounterTransportationTime,enableOCAN)
    VALUES (${facilities[0]},${q(marker)},'FAKE population report program','Service',99999,
      0,0,0,0,'active',0,0,0,0,0,0,0,'no',30,1,200,1,0,0,0,NOW(),0,0,0)`, 'Service program');
  let admission = null;
  s.cleanup(() => {
    if (admission) sql.execute(`DELETE FROM admission WHERE am_id=${admission} AND client_id=${Number(patient)}`);
    sql.execute(`DELETE FROM program WHERE id=${program} AND name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program WHERE id=${program})
      + (SELECT COUNT(*) FROM admission WHERE program_id=${program})`) === '0', 'The owned program or its admission was not removed');
  });
  // An undischarged admission of an AC patient to an active Service program: what the report
  // counts as its current population, so its percentages have a non-zero base.
  admission = h.insertId(sql, `INSERT INTO admission (client_id,program_id,provider_no,admission_date,admission_status,
      admission_from_transfer,discharge_from_transfer,temporary_admission_flag,lastUpdateDate)
    VALUES (${Number(patient)},${program},${q(provider)},NOW(),'current',0,0,0,NOW())`, 'admission');

  await s.step('the legacy Administration page ▸ Population Report renders every section', async () => {
    const legacy = await s.context.newPage();
    await h.gotoApp(legacy, s.config.baseUrl, '/admin/ViewAdmin');
    const link = legacy.locator('a[href$="/PopulationReport"]').first();
    h.assert(await link.count() === 1, 'The legacy Administration page offers no Population Report link');
    const [response] = await Promise.all([
      legacy.waitForResponse(r => new URL(r.url()).pathname.endsWith('/PopulationReport'), { timeout: TIMEOUT }),
      link.click(),
    ]);
    h.assert(response.status() === 200, `Population Report answered HTTP ${response.status()}`);
    await legacy.waitForLoadState('load', { timeout: TIMEOUT });
    await assertPopulationReport(legacy, 'Population Report from the legacy Administration page');
    await legacy.close();
  });

  await s.step('Administration ▸ Reports ▸ Population Report opens the report', async () => {
    const link = admin.locator('#adminNav a.contentLink', { hasText: 'Population Report' }).first();
    h.assert(await link.count() === 1, 'Administration ▸ Reports offers no Population Report entry');
    await revealAuditLink(admin, link, TIMEOUT);
    const requested = admin.waitForRequest(request => new URL(request.url()).pathname.endsWith('/PopulationReport'), { timeout: 10000 })
      .then(() => true, () => false);
    await link.click();
    h.assert(await requested, 'Clicking Population Report sent no request: the entry has no href, so the panel stays as it was');
    const frame = admin.frame({ name: 'myFrame' });
    await assertPopulationReport(frame || admin.locator('#dynamic-content'), 'Administration ▸ Reports ▸ Population Report');
  });
}

if (require.main === module) {
  // The admission is inserted for the owned patient; its record is never opened.
  runWorkflow(REPORT ? `admin-caisi-${REPORT}-report` : 'admin-caisi-reports-menu', workflow, { openMaster: false });
}
module.exports = { workflow };
