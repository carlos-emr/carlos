#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * CDS and MIS reports: the two funder reports a CAISI clinic runs from Administration ▸ Reports.
 *
 * User path: Schedule ▸ Administration ▸ Reports ▸ "MIS Report" (oscarReport/ViewMisReportForm, a
 * panel of the administration page) ▸ Report By Functional Centre or Programs ▸ a start and end
 * month ▸ View Report; and Reports ▸ "CDS Report" (oscarReport/ViewCds4ReportForm) ▸ a functional
 * centre ▸ Date Start and Date End ▸ View Report (oscarReport/ViewCds4ReportResults, shown under
 * the form).
 *
 * Asserted: MIS offers exactly the functional centres programs of this facility use (or says none
 * is in use) and only its Service programs. With a FAKE Service program on a FAKE functional
 * centre, CDS offers that centre and program, and its report names the centre and the dates chosen and
 * lists every CDS 4 category with a count. MIS for one month counts that month's visits in the
 * program, by functional centre and by program: one face-to-face note in that month, none by
 * telephone, one individual served; the two face-to-face notes of the following month must not
 * count.
 *
 * CDS_MIS_REPORT=cds or =mis runs one report's steps (the manifest's admin-cds-report and
 * admin-mis-report entries, so each known failure has its own entry: a script stops at its first
 * failing step). Unset, it runs both, CDS first.
 *
 * Known failures, held for the findings log: MIS builds its dates with new GregorianCalendar(year,
 * month, 1) from month options numbered 1 to 12, while Java counts months from 0, so every MIS
 * report covers the month after the one chosen (December runs into January of the next year).
 * The CDS form loads its own jQuery over the administration page's, so its jQuery Validation
 * call throws ($(...).validate is not a function) when the panel opens, as finding 40 records
 * for other Reports panels; the required functional centre is then never enforced, and with
 * none in use (a fresh install) View Report gets HTTP 500 and the panel stays empty.
 * Not covered: CDS counts above zero, which need CDS intake forms on admissions; and a login
 * refused by the shared _report gate (ViewReport2Action).
 *
 * Needs caisi=on: both menu entries are rendered only with the caisi module. Without it the check
 * SKIPs before creating its program and notes (the runWorkflow patient is removed as usual), as
 * report-age-sex-visit notes.
 *
 * Fixtures: the program is inserted with SQL, because the CAISI Program editor
 * (PMmodule/ProgramManager) requires _pmm_management, which no role holds on a fresh install
 * (SecurityObjectSeedContractUnitTest, KNOWN_UNREACHABLE). The run owns a functional centre and
 * the program on it (both named with the run marker), the runWorkflow FAKE patient and three notes
 * on that patient in the program; all are deleted by their captured ids. The centre is the run's
 * own because MIS by functional centre counts every program of the facility on it. The centre and
 * program show in the facility's lists while the run lasts, so the check needs an exclusive run;
 * a MySQL named lock also keeps two runs from counting each other's notes.
 */
const { randomUUID } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { acquireMysqlWorkflowLock } = require('./lib/mysql-workflow-lock');

const TIMEOUT = 20000;
const FACE_TO_FACE = 'face to face encounter with client';
const MIS_ROWS = { faceToFace: '4502440', phone: '4512440', individualsServed: '4552440' };
const MIS_LINK = 'a[href$="/oscarReport/ViewMisReportForm"]';
const CDS_LINK = 'a[href$="/oscarReport/ViewCds4ReportForm"]';
const REPORT = process.env.CDS_MIS_REPORT || '';
h.assert(['', 'cds', 'mis'].includes(REPORT), `CDS_MIS_REPORT must be cds, mis or unset (was ${JSON.stringify(REPORT)})`);
const runs = report => !REPORT || REPORT === report;

const pad = value => String(value).padStart(2, '0');

/** The month two months back and the one after it, as {year, month} with month 1-12. */
function reportMonths(now = new Date()) {
  const chosen = new Date(now.getFullYear(), now.getMonth() - 2, 1);
  const following = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const shape = date => ({ year: date.getFullYear(), month: date.getMonth() + 1 });
  return { chosen: shape(chosen), following: shape(following) };
}

async function openAdmin(s) {
  const { page } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'cds-mis-admin', timeout: TIMEOUT });
  await page.locator('#adminNav').waitFor();
  return page;
}

/** Opens a Reports panel; the caisi module renders both links or neither. */
async function openPanel(admin, selector, marker) {
  const link = admin.locator(`#adminNav ${selector}`).first();
  await revealAuditLink(admin, link, TIMEOUT);
  await link.waitFor({ state: 'visible' });
  await ui.clickInjectsPanel(admin, link, { marker, timeout: TIMEOUT });
  return admin.locator('#dynamic-content');
}

/** Option values and texts of a select, trimmed. */
async function options(select) {
  return select.locator('option').evaluateAll(list => list.map(option =>
    ({ value: option.value, text: option.textContent.replace(/\s+/g, ' ').trim() })));
}

/** Runs MIS for one month and returns {id: result} of the results table. */
async function runMis(admin, panel, { reportBy, value, month }) {
  if (reportBy === 'programs') {
    await panel.locator('#reportByPr').check();
    await panel.locator('#programIds').selectOption([value]);
  } else {
    await panel.locator('#reportByFn').check();
    await panel.locator('#functionalCentreId').selectOption(value);
  }
  await panel.locator('select[name="startYear"]').selectOption(String(month.year));
  await panel.locator('select[name="startMonth"]').selectOption(String(month.month));
  await panel.locator('select[name="endYear"]').selectOption(String(month.year));
  await panel.locator('select[name="endMonth"]').selectOption(String(month.month));
  // registerFormSubmit sends the form by XHR and puts the answer, the form plus its results
  // table, in place of the panel.
  const answered = admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/oscarReport/ViewMisReportForm')
    && new URL(response.url()).searchParams.get('reportBy') === reportBy, { timeout: TIMEOUT });
  await panel.locator('input[type="submit"][value="View Report"]').click();
  const response = await answered;
  h.assert(response.status() === 200, `MIS Report answered HTTP ${response.status()}`);
  const table = panel.locator('table').first();
  await table.waitFor({ timeout: TIMEOUT });
  const rows = await table.locator('tbody tr').evaluateAll(list => list.map(row =>
    [...row.querySelectorAll('td')].map(cell => cell.textContent.replace(/\s+/g, ' ').trim())));
  return Object.fromEntries(rows.map(cells => [cells[0], cells[2]]));
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const releaseLock = await acquireMysqlWorkflowLock(s.config.mysql, 'admin-cds-mis-reports');
  s.cleanup(releaseLock); // Registered first, therefore released after the fixture cleanup.

  const admin = await openAdmin(s);
  if (await admin.locator(`#adminNav ${MIS_LINK}`).count() === 0) {
    throw new h.SkipCheck('Administration ▸ Reports offers no MIS Report link; '
      + 'the CDS and MIS reports need the CAISI module (caisi=on in carlos.properties)');
  }
  const facilities = sql.rows('SELECT id FROM Facility ORDER BY id').map(([id]) => id);
  if (facilities.length !== 1) {
    throw new h.SkipCheck(`This database has ${facilities.length} facilities; the check needs the one facility a login lands in`);
  }
  const [facility] = facilities;
  const centresInUse = () => sql.rows(`SELECT DISTINCT f.accountId, f.description FROM program p
    JOIN FunctionalCentre f ON f.accountId=p.functionalCentreId WHERE p.facilityId=${facility} ORDER BY f.accountId`)
    .map(([accountId, description]) => ({ accountId, description }));
  const servicePrograms = () => sql.rows(`SELECT id FROM program WHERE facilityId=${facility} AND type='Service' ORDER BY id`)
    .map(([id]) => id);

  if (runs('mis')) await s.step('MIS Report offers exactly the functional centres in use and only the Service programs', async () => {
    const panel = await openPanel(admin, MIS_LINK, '#dynamic-content #misForm');
    const inUse = centresInUse();
    if (inUse.length === 0) {
      h.assert(await panel.locator('#functionalCentreId').count() === 0
        && /No function centre in use by facility/.test(await panel.innerText()),
      'With no functional centre in use, MIS Report did not say so');
    } else {
      const offered = (await options(panel.locator('#functionalCentreId'))).map(option => option.value).sort();
      h.assert(JSON.stringify(offered) === JSON.stringify(inUse.map(centre => centre.accountId).sort()),
        `MIS Report offered the functional centres ${JSON.stringify(offered)}, not those in use ${JSON.stringify(inUse.map(c => c.accountId))}`);
    }
    const programs = (await options(panel.locator('#programIds'))).map(option => option.value).sort();
    h.assert(JSON.stringify(programs) === JSON.stringify(servicePrograms().sort()),
      `MIS Report offered the programs ${JSON.stringify(programs)}, not this facility's Service programs`);
  });

  // ------------------------------------------------------------------ owned program and notes
  // A functional centre of the run's own: MIS by functional centre counts every program of the
  // facility on that centre, so a shipped centre a real program uses would add its visits too.
  const centre = { accountId: marker, description: 'FAKE CDS/MIS report centre' };
  sql.execute(`INSERT INTO FunctionalCentre (accountId, description) VALUES (${q(centre.accountId)}, ${q(centre.description)})`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM FunctionalCentre WHERE accountId=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM FunctionalCentre WHERE accountId=${q(marker)}`) === '0',
      'The owned functional centre was not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM FunctionalCentre WHERE accountId=${q(marker)}`) === '1',
    'The owned functional centre fixture was not created');
  const program = h.insertId(sql, `INSERT INTO program (facilityId,name,description,functionalCentreId,type,maxAllowed,
      holdingTank,allowBatchAdmission,allowBatchDischarge,hic,programStatus,transgender,firstNation,bedProgramAffiliated,
      alcohol,physicalHealth,mentalHealth,housing,exclusiveView,defaultServiceRestrictionDays,ageMin,ageMax,userDefined,
      facility_id,capacity_funding,capacity_space,lastUpdateDate,enableEncounterTime,enableEncounterTransportationTime,enableOCAN)
    VALUES (${facility},${q(marker)},'FAKE CDS/MIS report program',${q(centre.accountId)},'Service',99999,
      0,0,0,0,'active',0,0,0,0,0,0,0,'no',30,1,200,1,0,0,0,NOW(),0,0,0)`, 'Service program');
  const notes = [];
  s.cleanup(() => {
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note WHERE note_id IN (${notes.join(',')}) AND demographic_no=${Number(patient)}`);
    }
    sql.execute(`DELETE FROM program WHERE id=${program} AND name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program WHERE id=${program})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE program_no=${q(program)})`) === '0',
    'The owned program or its notes were not removed');
  });
  const { chosen, following } = reportMonths();
  const note = (month, day) => {
    notes.push(h.insertId(sql, `INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,history,
        uuid,locked,archived,signed,program_no,encounter_type)
      VALUES (NOW(),${q(`${month.year}-${pad(month.month)}-${pad(day)} 10:00:00`)},${Number(patient)},${q(provider)},
        'FAKE report visit','',${q(randomUUID())},'0',0,1,${q(program)},${q(FACE_TO_FACE)})`, 'visit note'));
  };
  note(chosen, 15);
  note(following, 10);
  note(following, 20);
  const monthName = month => `${month.year}-${pad(month.month)}`;

  if (runs('cds')) await s.step('CDS Report offers the functional centre in use and the new program', async () => {
    const panel = await openPanel(admin, CDS_LINK, '#dynamic-content #cdsForm');
    const centres = await options(panel.locator('#functionalCentreId'));
    h.assert(centres.some(option => option.value === centre.accountId && option.text === `${centre.accountId}, ${centre.description}`),
      `CDS Report does not offer the functional centre ${centre.accountId} that the new program uses`);
    const programs = await options(panel.locator('select[name="programIds"]'));
    h.assert(programs.some(option => option.value === program && option.text === `${marker} (Service)`),
      'CDS Report does not offer the new Service program');
  });

  if (runs('cds')) await s.step('CDS Report names the functional centre and dates chosen and lists every CDS 4 category with a count', async () => {
    const panel = admin.locator('#dynamic-content');
    const start = `${monthName(chosen)}-01`;
    const end = `${monthName(following)}-${pad(new Date(following.year, following.month, 0).getDate())}`;
    await panel.locator('#functionalCentreId').selectOption(centre.accountId);
    for (const [selector, value] of [['#startDate', start], ['#endDate', end]]) {
      // flatpickr owns both inputs; setDate also updates the input the form sends.
      await panel.locator(selector).evaluate((input, date) => {
        if (input._flatpickr) input._flatpickr.setDate(date, true); else input.value = date;
      }, value);
    }
    const answered = admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/oscarReport/ViewCds4ReportResults'),
      { timeout: TIMEOUT });
    await panel.getByRole('button', { name: 'View Report' }).click();
    const response = await answered;
    h.assert(response.status() === 200, `CDS Report answered HTTP ${response.status()}`);
    const results = panel.locator('#cds-results');
    await results.locator('table').waitFor({ timeout: TIMEOUT });
    const text = (await results.innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(`Functional Centre : ${centre.accountId}, ${centre.description}`),
      'The CDS report does not name the functional centre chosen');
    h.assert(text.includes(`Dates : ${start} to ${end} (inclusive)`), `The CDS report does not show the dates ${start} to ${end}`);
    const rows = await results.locator('table tbody tr').evaluateAll(list => list.map(row =>
      [...row.querySelectorAll('td')].map(cell => cell.textContent.trim())));
    const categories = sql.rows(`SELECT cdsDataCategory FROM CdsFormOption WHERE cdsFormVersion='4'`).map(([category]) => category);
    h.assert(categories.length > 0, 'This database has no CDS 4 categories');
    h.assert(JSON.stringify(rows.map(cells => cells[0]).sort()) === JSON.stringify([...categories].sort()),
      `The CDS report listed ${rows.length} categories, not the ${categories.length} CDS 4 categories`);
    h.assert(rows.every(cells => cells.slice(2).every(cell => /^(\d+|N\/A)$/.test(cell))),
      'A CDS report row has a count that is neither a number nor N/A');
  });

  const expected = { [MIS_ROWS.faceToFace]: '1', [MIS_ROWS.phone]: '0', [MIS_ROWS.individualsServed]: '1' };
  const misMatches = (results, how) => {
    for (const [id, value] of Object.entries(expected)) {
      h.assert(results[id] === value, `MIS Report ${how} for ${monthName(chosen)} gave ${results[id]} for row ${id}, expected ${value}`
        + (id === MIS_ROWS.faceToFace && results[id] === '2'
          ? ` (2 is the face-to-face count of ${monthName(following)}, the month after the one chosen)` : ''));
    }
  };

  if (runs('mis')) await s.step('MIS Report by functional centre counts only the visits of the month chosen', async () => {
    const panel = await openPanel(admin, MIS_LINK, '#dynamic-content #misForm');
    misMatches(await runMis(admin, panel, { reportBy: 'functionalCentre', value: centre.accountId, month: chosen }),
      'by functional centre');
  });

  if (runs('mis')) await s.step('MIS Report by program counts only the visits of the month chosen', async () => {
    const panel = await openPanel(admin, MIS_LINK, '#dynamic-content #misForm');
    misMatches(await runMis(admin, panel, { reportBy: 'programs', value: program, month: chosen }), 'by program');
  });
}

// The notes are inserted for the owned patient; its record is never opened.
if (require.main === module) {
  runWorkflow(REPORT ? `admin-${REPORT}-report` : 'admin-cds-mis-reports', workflow, { openMaster: false });
}
module.exports = { workflow, reportMonths };
