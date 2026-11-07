#!/usr/bin/env node
// Before/after visual + data comparison for PR 5705 (Growth 0-36 JPA migration).
// Usage: node growth-visual.js <phase> where phase is "before" or "after".
//   before: create a fixed synthetic infant, save one Growth 0-36 form with the legacy JDBC code,
//           screenshot the new form, the redisplay, the saved form, the print PDFs and the eChart.
//   after:  (same install upgraded in place to the PR build) screenshot the SAME pages for the SAME
//           rows the legacy code wrote, then save the same values again with the new code and
//           compare the stored rows column by column.
// Synthetic data only. Runs on the host against https://127.0.0.1/carlos (host networking).
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const SCRIPTS = process.env.CARLOS_SCRIPTS || '/home/user/build/after/scripts';
const h = require(path.join(SCRIPTS, 'lib/playwright-harness'));
const ui = require(path.join(SCRIPTS, 'lib/playwright-ui'));
const { openMasterRecord } = require(path.join(SCRIPTS, 'master-record-tabs-playwright-checks'));
const { openChart, waitForNavbars } = require(path.join(SCRIPTS, 'echart-navbar-modules-playwright-checks'));

const phase = process.argv[2];
const NOTES_ONLY = process.argv[3] === 'notes';
if (!['before', 'after'].includes(phase)) throw new Error('phase must be before or after');
const ROOT = '/home/user/build/visual';
const OUT = path.join(ROOT, phase);
fs.mkdirSync(OUT, { recursive: true });
const STATE = path.join(ROOT, 'state.json');
const CONTAINER = process.env.CONTAINER || 'carlos-deb-validation';
const MARKER = 'FAKE-GROWTHVIS';

function sql(query) {
  return execFileSync('docker', ['exec', CONTAINER, 'mariadb', '-u', 'root', 'carlos', '-N', '-B', '-e', query],
    { encoding: 'utf8' }).trim();
}
function sqlRows(query) {
  const out = execFileSync('docker', ['exec', CONTAINER, 'mariadb', '-u', 'root', 'carlos', '-B', '-e', query],
    { encoding: 'utf8' }).replace(/\n+$/, '');
  if (!out) return [];
  const [head, ...lines] = out.split('\n');
  const cols = head.split('\t');
  return lines.map(line => Object.fromEntries(line.split('\t').map((v, i) => [cols[i], v])));
}

// Deterministic visit values (synthetic). DOB 2025-05-01, so the infant is ~18 months old on the test date.
const FIELDS = {
  recordNo: 'R-0518', motherStature: '165 cm', gestationalAge: '39', fatherStature: '180 cm', edc: '2025/05/03',
};
const VISITS = [
  ['2025/05/08', '0.25', '3.40', '51.0', '35.0', 'first visit'],
  ['2025/06/01', '1', '4.20', '54.5', '37.2', 'feeding well'],
  ['2025/07/01', '2', '5.10', '58.0', '39.0', ''],
  ['2025/09/01', '4', '6.40', '62.5', '41.0', 'vaccines'],
  ['2025/11/01', '6', '7.30', '66.0', '42.8', 'solids started'],
  ['2026/05/01', '12', '9.10', '74.0', '45.5', ''],
];

function fieldValues() {
  const values = { ...FIELDS };
  VISITS.forEach(([date, age, weight, length, head, comment], i) => {
    const n = i + 1;
    Object.assign(values, { [`date_${n}`]: date, [`age_${n}`]: age, [`weight_${n}`]: weight,
      [`length_${n}`]: length, [`headCirc_${n}`]: head, [`comment_${n}`]: comment });
  });
  return values;
}

function ensurePatient(provider) {
  let id = sql(`SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(MARKER)} ORDER BY demographic_no LIMIT 1`);
  if (!id) {
    id = sql(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
      provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(MARKER)},'Visual','2025','05','01','F','AC',${h.sqlString(provider)},'ON','ON','NR',NOW());
      SELECT LAST_INSERT_ID()`);
  }
  return id;
}

const problems = [];
function watch(page, label) {
  page.on('pageerror', e => problems.push(`${label} pageerror: ${e.message}`));
  page.on('console', m => { if (m.type() === 'error') problems.push(`${label} console: ${m.text()}`); });
  page.on('response', r => { if (r.status() >= 500) problems.push(`${label} HTTP ${r.status()} ${r.url()}`); });
}

async function shot(page, name) {
  const file = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

function pdfToPng(pdfFile, name) {
  const prefix = path.join(OUT, name);
  execFileSync('pdftoppm', ['-png', '-r', '60', pdfFile, prefix]);
  return fs.readdirSync(OUT).filter(f => f.startsWith(name) && f.endsWith('.png'));
}

async function openForm(context, config, patient, formId, label) {
  const page = await context.newPage();
  watch(page, label);
  page.on('dialog', d => { problems.push(`${label} dialog(${d.type()}): ${d.message()}`); console.log('DIALOG', d.type(), d.message()); d.accept(); });
  const response = await page.goto(`${config.baseUrl}/form/formGrowth0_36?demographic_no=${patient}&formId=${formId}`,
    { waitUntil: 'load' });
  h.assert(response && response.status() === 200, `${label}: HTTP ${response && response.status()}`);
  await page.locator('input[name="recordNo"]').waitFor({ state: 'visible', timeout: 20000 });
  return page;
}

async function fillAndSave(page) {
  for (const [name, value] of Object.entries(fieldValues())) {
    const input = page.locator(`[name="${name}"]`).first();
    const picker = await input.evaluate(e => e.classList.contains('flatpickr-input') || e.readOnly);
    if (picker && /^\d{4}\/\d{2}\/\d{2}$/.test(value)) {
      // Read-only flatpickr inputs: pick through the calendar as a user does.
      await ui.pickDate(page, input, value.replace(/\//g, '-'));
    } else {
      await input.fill(value);
    }
  }
  const [nav] = await Promise.all([
    page.waitForNavigation({ waitUntil: 'load', timeout: 30000 }),
    page.locator('input[type="submit"][value="Save"]').first().click(),
  ]);
  await page.locator('input[name="recordNo"]').waitFor({ state: 'visible', timeout: 20000 });
  return nav;
}

async function formValues(page) {
  return page.evaluate(() => Object.fromEntries([...document.querySelectorAll('form input[name], form textarea[name], form select[name]')]
    .filter(e => e.type !== 'submit' && e.name !== 'CSRF-TOKEN').map(e => [e.name, e.value])));
}

async function printPdf(context, page, buttonValue, name) {
  // The print buttons retarget the form to a new window (formGrowth0_36Print), which auto-posts to
  // form/createpdf. Chromium's PDF viewer discards the body, so take the bytes in the route handler.
  let captured;
  const done = new Promise(resolve => { captured = resolve; });
  const handler = async route => {
    const response = await route.fetch();
    const body = await response.body();
    captured({ status: response.status(), body });
    await route.fulfill({ response, body });
  };
  await context.route('**/form/createpdf**', handler);
  await page.locator(`input[type="submit"][value="${buttonValue}"]`).first().click();
  const result = await Promise.race([done, new Promise((_, reject) => setTimeout(() => reject(new Error(`${name}: no createpdf answer`)), 60000))]);
  await context.unroute('**/form/createpdf**', handler);
  h.assert(result.status === 200 && result.body.subarray(0, 4).toString() === '%PDF', `${name}: not a PDF (HTTP ${result.status})`);
  const file = path.join(OUT, `${name}.pdf`);
  fs.writeFileSync(file, result.body);
  for (const extra of context.pages().slice(1)) if (/formGrowth0_36Print|createpdf/.test(extra.url())) await extra.close().catch(() => {});
  return pdfToPng(file, name);
}

function growthRows(patient) {
  return sqlRows(`SELECT * FROM formGrowth0_36 WHERE demographic_no=${patient} ORDER BY ID`);
}

(async () => {
  const env = Object.fromEntries(execFileSync('docker', ['exec', CONTAINER, 'bash', '-c', '. /root/suite-env.sh; env'],
    { encoding: 'utf8' }).split('\n').filter(l => /^(TEST_USER|TEST_PASSWORD|TEST_PIN|BASE_URL)=/.test(l))
    .map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
  Object.assign(process.env, env, { BASE_URL: 'https://127.0.0.1/carlos' });
  const config = h.readConfig();
  config.chromePath = '';
  const provider = sql(`SELECT provider_no FROM security WHERE user_name=${h.sqlString(config.testUser)}`);
  const state = phase === 'before' ? {} : JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const patient = phase === 'before' ? ensurePatient(provider) : state.patient;
  // Re-runs of the before phase start from no Growth rows for this synthetic patient.
  if (phase === 'before') sql(`DELETE FROM formGrowth0_36 WHERE demographic_no=${patient}`);
  // A chart left open by an earlier run holds this synthetic patient's encounter note lock.
  sql(`DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient}`);
  const result = { phase, patient, provider, steps: {} };

  const browser = await h.launchBrowser({ ...config, chromePath: '' });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 900 },
    timezoneId: 'America/Toronto', locale: 'en-CA' });
  context.setDefaultTimeout(30000);
  const recorder = h.createRecorder();
  const schedule = await h.login(context, config, recorder);
  watch(schedule, 'schedule');

  // 1. A new (unsaved) form: demographic prefill.
  let page = await openForm(context, config, patient, 0, 'new-form');
  result.steps.newFormValues = await formValues(page);
  await shot(page, '01-new-form');

  if (phase === 'before') {
    // 2. Save through the legacy JDBC path, then the redisplay.
    await fillAndSave(page);
    await shot(page, '02-after-save');
    result.steps.redisplayUrl = page.url();
    const rows = growthRows(patient);
    h.assert(rows.length === 1, `expected one saved row, found ${rows.length}`);
    state.patient = patient;
    state.legacyFormId = rows[0].ID;
    state.legacyRow = rows[0];
  } else {
    // 2. Save the same values through the new JPA path from a NEW blank form.
    const before = growthRows(patient).length;
    await fillAndSave(page);
    await shot(page, '02-after-save');
    result.steps.redisplayUrl = page.url();
    const rows = growthRows(patient);
    h.assert(rows.length === before + 1, `expected one new row, found ${rows.length - before}`);
    const fresh = rows[rows.length - 1];
    state.jpaFormId = fresh.ID;
    state.jpaRow = fresh;
    const legacy = state.legacyRow;
    const differ = Object.keys(legacy).filter(k => !['ID', 'formEdited'].includes(k) && legacy[k] !== fresh[k])
      .map(k => ({ column: k, legacy: legacy[k], jpa: fresh[k] }));
    result.steps.rowColumnDifferences = differ;
    result.steps.rowColumnCount = Object.keys(legacy).length;
    result.steps.legacyFormEditedDate = legacy.formEdited.slice(0, 10);
    result.steps.jpaFormEditedDate = fresh.formEdited.slice(0, 10);
  }
  await page.close();

  // 3. The saved form the LEGACY code wrote (in "after" this is read by the new JPA code).
  page = await openForm(context, config, patient, state.legacyFormId, 'saved-form');
  result.steps.savedFormValues = await formValues(page);
  await shot(page, '03-saved-legacy-row');
  // 4. Print growth (length/weight) and head circumference PDFs from that saved form.
  result.steps.printGrowth = await printPdf(context, page, 'Print Growth', '04-print-growth');
  await page.close();
  page = await openForm(context, config, patient, state.legacyFormId, 'saved-form-hc');
  result.steps.printHead = await printPdf(context, page, 'Head Circ(1)', '05-print-headcirc');
  await page.close();
  if (phase === 'after') {
    page = await openForm(context, config, patient, state.jpaFormId, 'saved-jpa-form');
    result.steps.jpaSavedFormValues = await formValues(page);
    await shot(page, '03b-saved-jpa-row');
    await page.close();
  }

  // 4b. A legacy row whose twenty measurement slots are all SQL NULL (older or imported data).
  if (phase === 'before') {
    state.nullSlotFormId = sql(`INSERT INTO formGrowth0_36 (demographic_no, provider_no, formCreated, formEdited, patientName, recordNo)
      VALUES (${patient}, ${provider}, '2025-05-02', '2025-05-02 10:00:00', 'Visual FAKE-GROWTHVIS', 'NULLS'); SELECT LAST_INSERT_ID()`);
  }
  page = await openForm(context, config, patient, state.nullSlotFormId, 'null-slot-form');
  result.steps.nullSlotFormValues = await formValues(page);
  await shot(page, '08-null-slot-legacy-row');
  await page.close();

  // 5. The eChart: Forms navbar module (typed history) and the note list (grouped forms).
  const { masterPage } = await openMasterRecord(context, schedule, recorder,
    { searchTerm: MARKER, preferredDemographicNo: String(patient), timeout: 30000 });
  let chart;
  for (let attempt = 1; ; attempt++) {
    try { chart = await openChart(context, masterPage, recorder, 30000); break; } catch (error) {
      // A follow-up encounter POST can answer an empty body that the harness reads as a blank page.
      if (attempt >= 3) throw error;
      result.steps[`echartRetry${attempt}`] = String(error.message).slice(0, 200);
      for (const extra of context.pages()) if (extra !== masterPage && extra !== schedule) await extra.close().catch(() => {});
    }
  }
  watch(chart, 'echart');
  await waitForNavbars(chart, 30000);
  await chart.waitForTimeout(1500);
  const formsModule = chart.locator('#formlist, #menuTitleforms, [id^="forms"]').first();
  result.steps.echartFormsText = await chart.locator('#leftNavBar').innerText().catch(() => '');
  result.steps.echartNotesText = await chart.locator('#encMainDiv, #notesContainer, #caseNote').first().innerText().catch(() => '');
  await shot(chart, '06-echart');
  await chart.locator('#leftNavBar').screenshot({ path: path.join(OUT, '07-echart-leftnav.png') }).catch(() => {});

  await browser.close();
  result.problems = problems;
  fs.writeFileSync(STATE, JSON.stringify(state, null, 2));
  fs.writeFileSync(path.join(OUT, 'result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify({ phase, patient, legacyFormId: state.legacyFormId, jpaFormId: state.jpaFormId,
    rowColumnDifferences: result.steps.rowColumnDifferences, problems }, null, 2));
})().catch(error => { console.error(error); process.exit(1); });
