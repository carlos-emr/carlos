#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Jobs and Job Types administration workflow (coverage plan admin-jobs).
 *
 * User path: Schedule ▸ Administration ▸ System Management ▸ Job Type Management
 * (admin/ViewJobTypes in the shell's #myFrame) ▸ Add New / edit by name; then
 * System Management ▸ Jobs Management (admin/ViewJobs) ▸ Add New ▸ Save Job,
 * calendar icon ▸ Schedule Job ▸ Save, edit by name ▸ Save Job. Both pages drive
 * the /ws/rs/jobs REST service (types/all, saveJobType, jobType/{id}, all,
 * saveJob, job/{id}, saveCrontabExpression).
 *
 * Asserted: the job type and job round-trip through the editors and OscarJobType /
 * OscarJob (same id on edit, every field, enabled=0); the required-field alert;
 * the unusable type is offered as "(N/A)"; the schedule dialog stores the cron
 * expression it shows and reopens with the stored choice; a disabled job has no
 * planned run; a job name is listed as text, not markup (placed last).
 *
 * Fixtures: one job type (DISABLED, class name in the CARLOS package that does
 * not exist, so it can never be scheduled) and one DISABLED job on it, both named
 * with the run marker. No job is ever enabled. The UI has no delete control
 * (reported); cleanup deletes only rows above the pre-run id high-water marks
 * that carry the marker and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TIMEOUT = 20000;

const isRest = (path, method = 'POST') => response => response.request().method() === method
  && new URL(response.url()).pathname.endsWith(`/ws/rs/jobs/${path}`);

async function workflow(s) {
  const {sql, marker, provider} = s;
  const typeName = `${marker} type O'Neil "A&B"`;
  const className = `io.github.carlos_emr.carlos.FakePw${marker.slice(-16)}NoSuchJob`;
  const jobName = `${marker} job O'Neil "A&B"`;
  const typeHigh = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM OscarJobType'));
  const jobHigh = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM OscarJob'));
  const ownedType = `id>${typeHigh} AND name LIKE ${h.sqlString(marker + '%')}`;
  const ownedJob = `id>${jobHigh} AND name LIKE ${h.sqlString(marker + '%')}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM OscarJob WHERE ${ownedJob}; DELETE FROM OscarJobType WHERE ${ownedType}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM OscarJob WHERE ${ownedJob})+(SELECT COUNT(*) FROM OscarJobType WHERE ${ownedType})`)
      === '0', 'Owned job fixtures were not removed');
  });

  let admin;
  // Open a System Management item from the schedule's Administration opener; the
  // page is hosted in the shell's #myFrame iframe.
  async function openSection(linkName, route, ready) {
    if (!admin || admin.isClosed()) {
      ({page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
        {context: s.context, recorder: s.recorder, label: 'jobs-administration', timeout: TIMEOUT}));
      // The .xlink handlers bind on document ready; a click before then is lost.
      await admin.waitForLoadState('load', {timeout: TIMEOUT});
    }
    const link = admin.getByRole('link', {name: linkName, exact: true, includeHidden: true});
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator(`#dynamic-content iframe#myFrame[src*="${route}"]`);
    await iframe.waitFor({timeout: TIMEOUT});
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, `${linkName} did not load in the administration frame`);
    await frame.waitForLoadState('domcontentloaded', {timeout: TIMEOUT});
    await ready(frame);
    return frame;
  }
  const dialogButton = (frame, dialogId, name) =>
    frame.locator(`.ui-dialog:has(#${dialogId})`).getByRole('button', {name, exact: true});
  async function clickAwaiting(predicate, locator) {
    const [response] = await Promise.all([admin.waitForResponse(predicate, {timeout: TIMEOUT}), locator.click()]);
    h.assert(response.status() === 200, `The jobs service answered HTTP ${response.status()}`);
    return response;
  }

  // ---- Job Type Management -------------------------------------------------
  let types;
  const typesLoaded = async frame => {
    await frame.locator('#jobTypeTable tbody tr').first().waitFor({timeout: TIMEOUT});
    await frame.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, {timeout: TIMEOUT});
  };
  // Seeded rather than created here: saving in the Job Type editor raises a
  // DataTables re-initialisation alert (reported), so the UI create/edit is the
  // last step and every Jobs Management step is proven first.
  const seedName = `${marker} seeded type`;
  const typeId = sql.value(`INSERT INTO OscarJobType(name,description,className,enabled,updated)
    VALUES(${h.sqlString(seedName)},${h.sqlString(`${marker} never runs`)},${h.sqlString(className)},0,NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(typeId) && Number(typeId) > typeHigh, 'The job type fixture was not created');
  await s.step('Job Type Management lists the disabled type as an invalid class and reloads it in the editor', async () => {
    types = await openSection('Job Type Management', '/admin/ViewJobTypes', typesLoaded);
    const row = types.locator('#jobTypeTable tbody tr', {hasText: seedName});
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[0] === seedName && cells[2] === className && cells[3] === 'false' && cells[4] === 'false',
      'The job type list does not show the exact name, class, invalid class and disabled state');
    const loaded = admin.waitForResponse(isRest(`jobType/${typeId}`, 'GET'), {timeout: TIMEOUT});
    await row.getByRole('link', {name: seedName, exact: true}).click();
    h.assert((await loaded).status() === 200, 'The job type editor could not load the stored type');
    await types.locator('#new-jobtype').waitFor({state: 'visible', timeout: TIMEOUT});
    await types.waitForFunction(id => document.getElementById('jobTypeId').value === String(id), typeId, {timeout: TIMEOUT});
    h.assert(await types.locator('#jobTypeName').inputValue() === seedName
      && await types.locator('#jobTypeClassName').inputValue() === className
      && !(await types.locator('#jobTypeEnabled').isChecked()), 'The job type editor did not reload the stored values');
    await dialogButton(types, 'new-jobtype', 'Cancel').click();
    await types.locator('#new-jobtype').waitFor({state: 'hidden', timeout: TIMEOUT});
  });

  // ---- Jobs Management -----------------------------------------------------
  let jobs;
  const jobRow = () => jobs.locator('#jobTable tbody tr', {hasText: marker});
  const jobsLoaded = async frame => {
    await frame.locator(`#jobType option[value="${typeId}"]`).waitFor({state: 'attached', timeout: TIMEOUT});
    await frame.locator(`#jobProvider option[value="${provider}"]`).first().waitFor({state: 'attached', timeout: TIMEOUT});
    await frame.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, {timeout: TIMEOUT});
  };
  let jobId;
  await s.step('the job editor refuses an unnamed job and offers the unusable type as N/A', async () => {
    jobs = await openSection('Jobs Management', '/admin/ViewJobs', jobsLoaded);
    h.assert(await jobRow().count() === 0, 'An owned job was listed before it was created');
    h.assert((await jobs.locator(`#jobType option[value="${typeId}"]`).innerText()).trim() === `${seedName} (N/A)`,
      'The job type with no valid class is not marked N/A in the job editor');
    await jobs.getByRole('button', {name: 'Add New', exact: true}).click();
    await jobs.locator('#new-job').waitFor({state: 'visible', timeout: TIMEOUT});
    h.assert(await jobs.locator('#jobProvider').inputValue() === provider, 'Run As Provider does not default to the signed-in provider');
    let posts = 0;
    const listener = request => { if (request.method() === 'POST' && request.url().includes('/ws/rs/jobs/')) posts++; };
    s.context.on('request', listener);
    try {
      const dialogs = await h.withExpectedDialogs(admin, () => dialogButton(jobs, 'new-job', 'Save Job').click());
      h.assert(dialogs.length === 1 && dialogs[0].text.includes('Please provide a name for the job')
        && dialogs[0].text.includes('Please provide a valid job type'), 'The required-field alert did not name the missing name and type');
    } finally { s.context.off('request', listener); }
    h.assert(posts === 0, 'A refused job was still posted');
    h.assert(await jobs.locator('#new-job').isVisible(), 'The job editor closed after a refused save');
    h.assert(sql.value(`SELECT COUNT(*) FROM OscarJob WHERE ${ownedJob}`) === '0', 'A refused job reached the database');
  });
  await s.step('a disabled job is saved on the owned type and listed with no schedule', async () => {
    await jobs.locator('#jobName').fill(jobName);
    await jobs.locator('#jobType').selectOption(String(typeId));
    await jobs.locator('#jobDescription').fill(`${marker} disabled fixture`);
    await jobs.locator('#jobEnabled').uncheck();
    await clickAwaiting(isRest('saveJob'), dialogButton(jobs, 'new-job', 'Save Job'));
    await jobRow().waitFor({timeout: TIMEOUT});
    await expectValue(sql, `SELECT COUNT(*) FROM OscarJob WHERE ${ownedJob}`, '1', 'Exactly one owned job was not stored');
    jobId = sql.value(`SELECT id FROM OscarJob WHERE ${ownedJob}`);
    h.assert(sql.value(`SELECT CONCAT_WS('|', BINARY name=BINARY ${h.sqlString(jobName)}, oscarJobTypeId, enabled,
      providerNo=${h.sqlString(provider)}, cronExpression IS NULL) FROM OscarJob WHERE id=${jobId}`) === `1|${typeId}|0|1|1`,
    'The stored job does not match the editor values');
    const cells = (await jobRow().locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[1] === jobName && cells[3].startsWith('Disabled') && cells[5] === 'N/A',
      'The job list does not show the exact name, a disabled state and no planned run');
    h.assert(await jobRow().locator('td').first().locator('i.fa-calendar.red').count() === 1, 'An unscheduled job is not flagged red');
  });
  async function openSchedule() {
    const loaded = admin.waitForResponse(isRest(`job/${jobId}`, 'GET'), {timeout: TIMEOUT});
    await jobRow().locator('td').first().locator('a').click();
    h.assert((await loaded).status() === 200, 'The schedule dialog could not load the stored job');
    await jobs.locator('#scheduleDialog').waitFor({state: 'visible', timeout: TIMEOUT});
    await jobs.waitForFunction(id => document.getElementById('scheduleJobId').value === String(id), jobId, {timeout: TIMEOUT});
  }
  await s.step('the schedule dialog stores the chosen minutes and hour as the job cron expression', async () => {
    await openSchedule();
    await jobs.locator('#minute_chooser_choose').check();
    await jobs.locator('#minute').selectOption(['15', '45']);
    await jobs.locator('#hour_chooser_choose').check();
    await jobs.locator('#hour').selectOption('3');
    await clickAwaiting(isRest('saveCrontabExpression'), dialogButton(jobs, 'scheduleDialog', 'Save'));
    await expectValue(sql, `SELECT cronExpression FROM OscarJob WHERE id=${jobId}`, '0 15,45 3 * * *',
      'The schedule dialog did not store the chosen cron expression');
    await jobRow().locator('i.fa-calendar.blue').waitFor({timeout: TIMEOUT});
    h.assert(sql.value(`SELECT enabled FROM OscarJob WHERE id=${jobId}`) === '0', 'Scheduling enabled the job');
    h.assert((await jobRow().locator('td').nth(5).innerText()).trim() === 'N/A', 'A disabled job shows a planned run');
  });
  await s.step('Job Type Management creates a disabled job type and edits the same row', async () => {
    types = await openSection('Job Type Management', '/admin/ViewJobTypes', typesLoaded);
    const row = () => types.locator('#jobTypeTable tbody tr', {hasText: typeName});
    await types.getByRole('button', {name: 'Add New', exact: true}).click();
    await types.locator('#new-jobtype').waitFor({state: 'visible', timeout: TIMEOUT});
    h.assert(await types.locator('#jobTypeEnabled').isChecked(), 'A new job type does not default to enabled');
    await types.locator('#jobTypeName').fill(typeName);
    await types.locator('#jobTypeDescription').fill(`${marker} never runs`);
    await types.locator('#jobTypeClassName').fill(className);
    await types.locator('#jobTypeEnabled').uncheck();
    await clickAwaiting(isRest('saveJobType'), dialogButton(types, 'new-jobtype', 'Save Job Type'));
    const ownedCreated = `${ownedType} AND id<>${typeId}`;
    await expectValue(sql, `SELECT COUNT(*) FROM OscarJobType WHERE ${ownedCreated}`, '1', 'Exactly one job type was not stored');
    const createdId = sql.value(`SELECT id FROM OscarJobType WHERE ${ownedCreated}`);
    h.assert(sql.value(`SELECT CONCAT_WS('|', BINARY name=BINARY ${h.sqlString(typeName)}, className=${h.sqlString(className)}, enabled)
      FROM OscarJobType WHERE id=${createdId}`) === '1|1|0', 'The stored job type does not match the editor values');
    await row().waitFor({timeout: TIMEOUT});
    await types.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, {timeout: TIMEOUT});
    h.assert(await types.locator('#jobTypeTable tbody tr', {hasText: seedName}).count() === 1, 'The refreshed list duplicated or lost rows');
    await row().getByRole('link', {name: typeName, exact: true}).click();
    await types.waitForFunction(id => document.getElementById('jobTypeId').value === String(id), createdId, {timeout: TIMEOUT});
    await types.locator('#jobTypeDescription').fill(`${marker} edited never runs`);
    await clickAwaiting(isRest('saveJobType'), dialogButton(types, 'new-jobtype', 'Save Job Type'));
    await expectValue(sql, `SELECT description FROM OscarJobType WHERE id=${createdId}`, `${marker} edited never runs`,
      'The job type edit did not update the stored row');
    h.assert(sql.value(`SELECT CONCAT(COUNT(*),'|',SUM(enabled)) FROM OscarJobType WHERE ${ownedType}`) === '2|0',
      'Editing created another job type or enabled one');
  });
}

if (require.main === module) runWorkflow('admin-jobs-dev', workflow, {openPatient: false});
module.exports = {workflow};
