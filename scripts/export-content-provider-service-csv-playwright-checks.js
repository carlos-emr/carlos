#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Provider Service Report ▸ Export (CSV): the file's numbers against the notes that were written.
 * User path: Schedule ▸ Administration ▸ Reports ▸ Provider Service Report ▸ Start / End month ▸ Export
 * (oscarReport/ViewProviderServiceReportExport answers provider_service_<agency>_<from>_<to>.csv).
 * Nothing opened this download: it is the clinic's per-program encounter statistic, built from casemgmt_note.
 *
 * Asserts: the first line is the documented eleven-column header; for each owned SERVICE program the CSV has one
 * row per month plus one range row, in the order the form's dates describe; the face-to-face, telephone and
 * no-client counts and the unique-client counts equal the doctor notes the check wrote (two clients, two months);
 * a program name with an accent, a quote and a comma is quoted and survives as UTF-8; the file's name is a valid
 * attachment name. LAST (fails today): the range row is labelled with the INCLUSIVE end month the form was given,
 * the filename carries no "/", and a note that was archived (removed from the chart) or re-saved as a second
 * revision of the same note uuid is not counted as another encounter.
 * Fixtures: two FAKE- Service programs (the default OSCAR program is a Bed program, which the report skips), one
 * more owned patient, doctor-role notes dated in February/March 2003; every assertion is scoped to the owned programs (rows of
 * other programs, or other notes in the window, are ignored); cleanup deletes only those rows and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { clickInjectsPanel } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const x = require('./lib/export-content-helpers');

const q = h.sqlString;
const HEADER = ['Agency Name', 'Program Name', 'Program Type', 'Date', 'total encounters face to face',
  'total encounters by phone', 'total encounters with out client', 'unique client encountered face to face',
  'unique clients encountered by phone', 'unique clients encountered with out client', 'total unique clients encountered'];
const F2F = 'face to face encounter with client';
const TEL = 'telephone encounter with client';
const EMAIL = 'email encounter with client';

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const scratch = x.scratchDir();
  const owned = { programs: [], second: null };
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    const programs = owned.programs.length ? owned.programs.join(',') : '0';
    sql.execute(`DELETE FROM casemgmt_note WHERE program_no IN (${programs.split(',').map(p => q(p)).join(',')})
        AND demographic_no IN (${patient},${owned.second || 0});
      DELETE FROM program WHERE id IN (${programs}) AND name LIKE ${q(`${marker}%`)}`);
    if (owned.second) sql.execute(`DELETE FROM demographic WHERE demographic_no=${owned.second} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program WHERE name LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE program_no IN (${programs.split(',').map(p => q(p)).join(',')}))
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${q(marker)} AND demographic_no<>${patient})`) === '0',
    'Owned programs, notes or patients were not removed');
  });

  const doctorRole = sql.value('SELECT role_no FROM secRole WHERE role_name=\'doctor\'');
  h.assert(/^\d+$/.test(doctorRole), 'The doctor role is missing');
  owned.second = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
    patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(marker)},'SecondClient','1975','03','04','M','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  const second = owned.second;
  const newProgram = suffix => {
    const name = `${marker} Café "Svc", Ünit ${suffix}`;
    const id = sql.value(`INSERT INTO program (facilityId,name,type,maxAllowed,holdingTank,allowBatchAdmission,allowBatchDischarge,hic,
        programStatus,transgender,firstNation,bedProgramAffiliated,alcohol,physicalHealth,mentalHealth,housing,exclusiveView,
        ageMin,ageMax,userDefined,lastUpdateDate)
      SELECT facilityId,${q(name)},'Service',99999,0,0,0,0,'active',0,0,0,0,0,0,0,'no',1,200,1,NOW() FROM program WHERE name='OSCAR' LIMIT 1;
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'The Service program fixture was not created');
    owned.programs.push(id);
    return { id, name };
  };
  const clean = newProgram('A');
  const dirty = newProgram('B');
  const note = (program, demo, type, when, { archived = 0, uuid = null, updated = null } = {}) => sql.execute(`INSERT INTO casemgmt_note
      (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,encounter_type,program_no,
       reporter_caisi_role,history,uuid,locked,archived)
    VALUES (${updated ? q(updated) : 'NOW()'},${q(when)},${demo},${q(provider)},${q(`${marker} stat note`)},1,${q(provider)},${q(type)},
      ${q(program)},${q(doctorRole)},'',${uuid ? q(uuid) : 'UUID()'},'0',${archived})`);
  // Clean program: February 2003 has two face-to-face notes for the owned patient and one telephone note for the second
  // client; March 2003 has one e-mail note for the second client (no CSV column for it, but a unique client).
  note(clean.id, patient, F2F, '2003-02-03 09:00:00');
  note(clean.id, patient, F2F, '2003-02-10 09:00:00');
  note(clean.id, second, TEL, '2003-02-12 09:00:00');
  note(clean.id, second, EMAIL, '2003-03-04 09:00:00');
  // Dirty program: one face-to-face note counted once, one ARCHIVED note, and one note saved as two revisions of one uuid.
  const uuid = sql.value('SELECT UUID()');
  note(dirty.id, patient, F2F, '2003-02-05 09:00:00');
  note(dirty.id, patient, F2F, '2003-02-06 09:00:00', { archived: 1 });
  note(dirty.id, second, F2F, '2003-02-07 09:00:00', { uuid, updated: '2003-02-07 09:00:00' });
  note(dirty.id, second, F2F, '2003-02-07 09:00:00', { uuid, updated: '2003-02-08 10:00:00' });

  const { page: admin } = await require('./lib/playwright-ui').clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator('#admin-panel,#admin2').first(), { context: s.context, recorder: s.recorder, label: 'psr-administration', timeout: 20000 });
  const link = admin.locator('a.contentLink[href*="ViewProviderServiceReportForm"]').first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  let file;
  let rows;

  await s.step('the form exports a CSV for February to March 2003', async () => {
    const panel = await clickInjectsPanel(admin, link, { marker: '#dynamic-content #psrForm' });
    for (const [selector, value] of [['#startDate', '02/2003'], ['#endDate', '03/2003']]) {
      await panel.locator(selector).fill(value);
      // Click the heading, as an operator would, so the open calendar stops covering the Export button.
      await panel.locator('h4').first().click();
      await admin.locator('.flatpickr-calendar.open').first().waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
    }
    file = await x.saveDownload(admin, scratch, () => panel.getByRole('button', { name: /Export/ }).click(),
      { route: /ViewProviderServiceReportExport$/ });
    // Known and filed separately (ISSUES L112): inside the Administration shell the page loads its own jQuery
    // Validate, so the oscarMonth rule is missing and every focus-out throws. Only that error is set aside here so
    // the file the form produces can still be read; any other page error still fails the step.
    await admin.waitForTimeout(300);
    for (let i = s.recorder.pageErrors.length - 1; i >= 0; i--) {
      if (/check the 'oscarMonth' method/.test(s.recorder.pageErrors[i].text)) s.recorder.pageErrors.splice(i, 1);
    }
    h.assert(file.status === 200, `Export answered HTTP ${file.status}`);
    rows = x.parseCsv(file.bytes.toString('utf8')).filter(r => r.some(c => c !== ''));
    h.assert(JSON.stringify(rows[0]) === JSON.stringify(HEADER), 'The CSV first line is not the documented eleven-column header');
    h.assert(rows.every(r => r.length === HEADER.length), 'A CSV line does not have eleven columns');
  });

  const programRows = program => rows.filter(r => r[1] === program.name);

  await s.step('the clean program has a February row, a March row and a range row with the counts of the written notes', async () => {
    const mine = programRows(clean);
    h.assert(mine.length === 3, `The CSV has ${mine.length} rows for the owned Service program, expected 3 (two months and the range)`);
    h.assert(mine.every(r => r[2] === 'Service'), 'The owned program row does not say Service');
    const byDate = Object.fromEntries(mine.map(r => [r[3], r]));
    const feb = byDate['2003-02'];
    const mar = byDate['2003-03'];
    h.assert(feb && mar, 'The program has no February and March month rows');
    // columns 4..10: total F2F, tel, no-client, unique F2F, unique tel, unique no-client, total unique
    h.assert(feb.slice(4).join(',') === '2,1,0,1,1,0,2', `February counts are ${feb.slice(4).join(',')}, written notes give 2,1,0,1,1,0,2`);
    h.assert(mar.slice(4).join(',') === '0,0,0,0,0,0,1', `March counts are ${mar.slice(4).join(',')}, written notes give 0,0,0,0,0,0,1`);
    const range = mine.find(r => /to/.test(r[3]));
    h.assert(range && range[4] === '2' && range[5] === '1' && range[10] === '2', 'The program range row does not total the two months');
    h.assert(rows.some(r => r[1] === 'all programs' && r[3] === '2003-02') && rows.some(r => r[1] === 'all programs' && r[3] === '2003-03'),
      'The CSV has no "all programs" month rows for the window');
  });

  await s.step('a program name with an accent, a quote and a comma is quoted and kept as UTF-8', async () => {
    const raw = file.bytes.toString('utf8');
    h.assert(raw.includes(`"${clean.name.replace(/"/g, '""')}"`), 'The program name is not exported as a quoted CSV field with doubled quotes');
    h.assert(programRows(clean)[0][1] === clean.name, 'The program name did not survive the file round trip');
  });

  await s.step('the dated rows are in month order and the attachment has a usable name', async () => {
    const dates = programRows(clean).map(r => r[3]);
    h.assert(dates[0] === '2003-02' && dates[1] === '2003-03', 'The month rows are not in calendar order');
    h.assert(/\.csv$/.test(file.name), 'The attachment is not named .csv');
  });

  await s.step('the range label, the attachment name and the encounter counts follow what the form and the chart say', async () => {
    const problems = [];
    const range = programRows(clean).find(r => /to/.test(r[3]));
    if (range[3] !== '2003-02 to 2003-03') problems.push(`the range row is labelled "${range[3]}" for an inclusive end month of 2003-03`);
    const disposition = file.headers['content-disposition'] || '';
    if (/filename="?[^";]*\//.test(disposition)) problems.push('the Content-Disposition filename contains "/" (the month format MM/yyyy is used in the name)');
    const dirtyFeb = programRows(dirty).find(r => r[3] === '2003-02');
    // One live note by the owned patient + one by the second client (two revisions of one note) = 2 encounters, 2 unique clients.
    if (dirtyFeb[4] !== '2') problems.push(`an archived note and a second revision of a note are counted as encounters (${dirtyFeb[4]} face-to-face for 2 real notes)`);
    h.assert(!problems.length, `The provider service CSV is wrong: ${problems.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('export-content-provider-service-csv', workflow, { openMaster: false });
module.exports = { workflow };
