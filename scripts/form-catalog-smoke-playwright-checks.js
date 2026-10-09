#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan Phase 3 (Ontario): the encounter forms no other check saves. User path: Schedule ▸
// Search ▸ Master Record ▸ E-Chart ▸ Forms menu ▸ <form> ▸ type ▸ Save (form/formname) ▸ the saved
// record redisplayed in the same window ▸ a fresh E-Chart's saved-form entry ▸ Print.
//
// Table-driven over 25 forms (Rourke 2006 and the original Rourke, Letterhead, the 2003 Lab Req, ADFv2,
// ALPHA, CESD, CHF, Caregiver and its SF36, SF36, Cost Questionnaire, Falls History, HOME FAST, Grip
// Strength, ImmunAllergies, Intake Information, both Late Life FDI forms, Position Hazard, Risk
// Assessment, Self Efficacy, Self Management, Treatment Preference, Health Passport) plus the Patient
// Encounter Worksheet, which only prints. Rourke 2020 and the growth charts are form-rourke2020-*.
//
// Each form has up to eight CONCERNS, asserted one labelled step each:
//   bare       (Position Hazard and Health Passport only) the form opens for a patient whose address, phone and
//              health-card columns are NULL, as the harness's bare fixture row leaves them (finding 241); it
//              runs before the fixture is filled in;
//   open       the Forms-menu entry opens the form with no error page, uncaught script error, console
//              error or failed asset;
//   keys       the page shows no unresolved message key (the ???key??? text a missing bundle entry prints);
//   save       Save stores one new row in the form's table with every typed value (and the saving provider);
//   redisplay  the window Save was pressed in redisplays the saved record (the right record, not an error page);
//   reopen     a fresh chart's saved-form entry opens the saved record;
//   restore    the typed values are shown again on the redisplayed form and on the reopened one;
//   print      Print produces what the form promises: a print page, a PDF (%PDF), or the browser's print.
//
// KNOWN FAILURES AND CLAIMS. Every run executes the flow of every selected form and records every
// concern; the entry then asserts, in table order, the pairs it CLAIMS. FORM_CATALOG_ONLY and
// FORM_CATALOG_EXCEPT (lib/form-claims.js: `<form>` or `<form>.<concern>`) choose them, so a broken form
// gets its own manifest entry pinned on its own finding, the default entry leaves that pair out, and
// scripts/form-claims.test.js proves the entries together claim every pair once. A concern that depends
// on another (save needs open; reopen and print need save) is blocked when the other fails, so a defect
// that stops the flow claims the whole form. A pin holds only the defect's own failure: a precondition that
// could not be met (the Forms menu does not list the entry, the Print button is missing), a concern that was
// not reached, and, for a pair that declares the signature of the browser problem it is pinned to (`known`),
// any other browser problem, are reported under a step label of their own, so they read as a failure
// elsewhere and never as the known one (lib/form-claims.js classify()).
//
// Fixtures: the owned synthetic patient (given a complete demographic record, as every registered patient
// has, after the bare concern has run: Position Hazard and Health Passport throw on NULL address and
// health-card columns, finding 241), and one marker-named Forms-menu
// registration per form (the shipped rows are hidden on Ontario installs and are clinic-wide, so run
// EXCLUSIVE=1). Cleanup deletes every form row of the owned patient and every registration and asserts both.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const claims = require('./lib/form-claims');
const { settleProblems } = require('./lib/form-problems');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { pdfText } = require('./form-print-pdf-playwright-checks');

const NAME = 'form-catalog-smoke';
const CONCERNS = ['open', 'keys', 'save', 'redisplay', 'reopen', 'restore', 'print'];
const PROSE = "BP 120/80 & HR 72; pt's c/o SOB";
const SHORT = "A&B 1/2 pt's";

const check = name => ({ name, check: true });
const typed = (name, value) => ({ name, value });

/*
 * code     the registration's suffix (the Forms menu name is `<run marker> <code>`, at most 30 characters)
 * view     the form's route, form/<view>; the shipped registration is ../form/<view>.jsp?demographic_no=
 * table    the form's table; '' for a form that saves nothing
 * prose    the text typed into one input or textarea; limit is that column's length, when shorter than the prose
 * fields   further typed values or ticked boxes, each checked in the table and on every redisplay
 * reveal   what a clinician does before the questions show
 * bare     the form also runs the `bare` concern (open for a patient with NULL contact columns)
 * known    per concern, the signature of the browser problem a pin is for; any other problem is reported apart. A pair
 *          pinned on an ASSERTION (an error page, a missing value, a key that is not resolved) declares
 *          claims.ASSERTION_ONLY instead: no browser problem is its own, so once its defect is fixed a stray one cannot
 *          keep the pin "known". scripts/form-claims.test.js requires every PINNED pair to declare one or the other.
 * print    window: the button calls window.print (stubbed and counted, so the browser's own print dialog is never
 *          opened); pdf: a POST or GET answers a PDF (read in transit), where 'same' replaces the form's window and
 *          'popup' opens another; carries: the typed text must be in what is printed
 */
const FORMS = [
  { key: 'rourke2006', code: 'R06', title: 'Rourke Baby Record 2006', view: 'formrourke2006', table: 'formRourke2006',
    prose: { name: 'p1_signature1w' }, fields: [check('p1_breastFeeding1w'), typed('c_length', '51.5')],
    print: { kind: 'pdf', button: 'Print', where: 'popup' },
    known: { keys: claims.ASSERTION_ONLY, reopen: claims.ASSERTION_ONLY } },
  { key: 'rourke', code: 'ROU', title: 'Rourke Baby Record', view: 'formrourke', table: 'formRourke',
    prose: { name: 'c_birthRemarks' }, fields: [check('p1_breastFeeding1w'), typed('c_length', '51.5')],
    print: { kind: 'window', button: 'Print' } },
  { key: 'letterhead', code: 'LTR', title: 'Letterhead', view: 'formConsultant', table: 'formConsult',
    prose: { name: 'comments' }, fields: [typed('t_name', 'FAKE Referral Doc')],
    print: { kind: 'window', button: 'Print', confirm: false, carries: true },
    known: { restore: claims.ASSERTION_ONLY } },
  { key: 'labreq', code: 'LRQ', title: 'Lab Req', view: 'formlabreq', table: 'formLabReq',
    prose: { name: 'aci' }, fields: [check('b_glucose')],
    print: { kind: 'pdf', button: 'Print Pdf', where: 'same', carries: true } },
  { key: 'adfv2', code: 'ADF', title: 'ADFv2', view: 'formadfv2', table: 'formAdfV2',
    prose: { name: 'actProblem' }, fields: [typed('sendFacility', 'FAKE Facility'), check('capTreatDecY')],
    print: { kind: 'window', button: 'Print' }, known: { redisplay: claims.ASSERTION_ONLY } },
  { key: 'alpha', code: 'ALP', title: 'ALPHA', view: 'formalpha', table: 'formAlpha',
    prose: { name: 'socialSupport' }, fields: [], print: { kind: 'window', button: 'Print' },
    known: { open: claims.ASSERTION_ONLY } },
  { key: 'cesd', code: 'CES', title: 'CESD', view: 'formCESD', table: 'formCESD',
    prose: null, fields: [check('Q1Rare'), check('Q2Some')], print: { kind: 'window', button: 'Print' },
    known: { redisplay: claims.ASSERTION_ONLY, restore: claims.ASSERTION_ONLY } },
  { key: 'chf', code: 'CHF', title: 'CHF', view: 'formchf', table: 'formchf',
    prose: { name: 'PI1', limit: 60 }, fields: [check('SHFDx'), typed('weight1', '70.5')],
    print: { kind: 'window', button: 'Print' } },
  { key: 'caregiver', code: 'CGV', title: 'Caregiver', view: 'formcaregiver', table: 'formCaregiver',
    prose: { name: 'otherRelation' }, fields: [check('sexF'), check('spouseY')], print: { kind: 'window', button: 'Print' } },
  { key: 'sf36caregiver', code: 'CG36', title: 'Caregiver - SF36', view: 'formSF36caregiver', table: 'formSF36Caregiver',
    prose: { name: 'Q1Cmt' }, fields: [check('Q1Ex')], print: { kind: 'window', button: 'Print' } },
  { key: 'sf36', code: 'S36', title: 'SF36', view: 'formSF36', table: 'formSF36',
    prose: { name: 'Q1Cmt' }, fields: [check('Q1Ex')], print: { kind: 'window', button: 'Print' } },
  { key: 'costquestionnaire', code: 'COST', title: 'Cost Questionnaire', view: 'formcostquestionnaire', table: 'formCostQuestionnaire',
    prose: { name: 'paidService1' }, fields: [check('seenDoctorY'), typed('familyPhyVisits', '3')],
    print: { kind: 'window', button: 'Print' } },
  { key: 'falls', code: 'FALL', title: 'Falls History', view: 'formfalls', table: 'formFalls',
    prose: null, fields: [check('fallenLast12MY'), check('injuredN')], print: { kind: 'window', button: 'Print' } },
  { key: 'homefast', code: 'FAST', title: 'HOME FAST', view: 'formhomefalls', table: 'formHomeFalls',
    prose: null, fields: [check('floor1Y'), check('floor2N')], print: { kind: 'window', button: 'Print' } },
  { key: 'gripstrength', code: 'GRIP', title: 'Grip Strength', view: 'formgripstrength', table: 'formGripStrength',
    prose: null, fields: [typed('dom1', '31.5'), typed('nonDom1', '29.5'), typed('dom2', '32'), typed('nonDom2', '30'),
      typed('dom3', '31'), typed('nonDom3', '29')], print: { kind: 'window', button: 'Print' } },
  { key: 'immunallergy', code: 'IMM', title: 'ImmunAllergies', view: 'formimmunallergy', table: 'formImmunAllergy',
    prose: { name: 'tradeName', limit: 50 }, fields: [check('doseAdminIM')], print: { kind: 'window', button: 'Print' } },
  { key: 'intakeinfo', code: 'INT', title: 'Intake Information', view: 'formintakeinfo', table: 'formIntakeInfo',
    prose: { name: 'ethnoCulturalGr' }, fields: [check('maritalStMarried'), typed('nbpplInHousehold', '4')],
    print: { kind: 'window', button: 'Print' } },
  { key: 'fdidisability', code: 'FDID', title: 'FDI Disability', view: 'formlatelifeFDIdisability', table: 'formLateLifeFDIDisability',
    prose: null, fields: [check('D1Often'), check('D2Little')], print: { kind: 'window', button: 'Print' },
    reveal: async page => { await page.getByText('Questionnaire', { exact: true }).first().click(); } },
  { key: 'fdifunction', code: 'FDIF', title: 'FDI Function', view: 'formlatelifeFDIfunction', table: 'formLateLifeFDIFunction',
    prose: null, fields: [check('F1ALittle'), check('F2Some')], print: { kind: 'window', button: 'Print' },
    reveal: async page => {
      await page.getByText('Questionnaire', { exact: true }).first().click();
      await page.locator('a[title="Core questions"]').click();
    } },
  { key: 'positionhazard', code: 'POS', title: 'Position Hazard', view: 'formPositionHazard', table: 'formPositionHazard',
    prose: { name: 'staffName' }, fields: [check('NewHire')], print: { kind: 'pdf', button: 'Print Pdf', where: 'popup' },
    provider: false, bare: true, known: { open: /positionHazardStyle\.css/, bare: claims.ASSERTION_ONLY } },
  { key: 'riskassessment', code: 'RISK', title: 'Risk Assessment', view: 'formselfadministered', table: 'formSelfAdministered',
    prose: null, fields: [check('healthEx'), check('stayInHospNo')], print: { kind: 'window', button: 'Print' } },
  { key: 'selfefficacy', code: 'SEFF', title: 'Self Efficacy', view: 'formselfefficacy', table: 'formSelfEfficacy',
    prose: null, fields: [typed('ex1', '5'), typed('ex2', '7')], print: { kind: 'window', button: 'Print' } },
  { key: 'selfmanagement', code: 'SMGT', title: 'Self Management', view: 'formselfmanagement', table: 'formSelfManagement',
    prose: { name: 'ex6Spec' }, fields: [typed('ex1', '3'), check('tangibleHelpHouseY')], print: { kind: 'window', button: 'Print' },
    // The six pages unlock one after another behind range checks, and the checkboxes (the only TINYINT answers the
    // form can be asked to restore) are on page 4, so page 4 is shown beside page 1 by script: reaching them is not the test.
    reveal: async page => {
      await page.evaluate(() => { document.getElementById('page4').style.display = 'block'; });
    } },
  { key: 'treatmentpref', code: 'TPRF', title: 'Treatment Preference', view: 'formtreatmentpref', table: 'formTreatmentPref',
    prose: null, fields: [check('treatmentGr')], print: { kind: 'window', button: 'Print' } },
  { key: 'healthpassport', code: 'HPP', title: 'Health Passport', view: 'formbchp', table: 'formBCHP',
    prose: { name: 'pg1_allergies' }, fields: [check('pg1_diabetes')], print: { kind: 'pdf', button: 'Print', where: 'popup' },
    bare: true },
  // Prints a worksheet and saves nothing: no table, so no save, redisplay or reopen.
  { key: 'worksheet', code: 'PEW', title: 'Patient Encounter Worksheet', view: 'patientEncounterWorksheet', table: '',
    prose: { name: 'encounter_notes' }, fields: [], ready: 'Print', print: { kind: 'pdf', button: 'Print', where: 'same' } },
].map(form => ({ ...form,
  concerns: [...(form.bare ? ['bare'] : []), ...(form.table ? CONCERNS : ['open', 'keys', 'print'])] }));

const CLAIM_FORMS = FORMS.map(({ key, concerns }) => ({ key, concerns }));
const ONLY = 'FORM_CATALOG_ONLY';
const EXCEPT = 'FORM_CATALOG_EXCEPT';

/**
 * FORM_CATALOG_ONLY and FORM_CATALOG_EXCEPT must be unset or lists of `<form>` / `<form>.<concern>` that name
 * real pairs and leave something to assert. Judged when the check runs, never when the module is required.
 */
function validatePin(env = process.env) {
  return claims.effectiveClaims({ only: env[ONLY], except: env[EXCEPT], onlyVariable: ONLY, exceptVariable: EXCEPT }, CLAIM_FORMS);
}

/*
 * The step labels are literals on purpose where a manifest pins them: expectedFailure.step is checked against
 * the script's own text (run-playwright-suite.js validateExpectedFailure), and a label assembled at run time
 * cannot be found there. scripts/form-claims.test.js proves each literal equals the generated label, so the
 * table cannot drift from the wording below.
 */
const PINNED = Object.freeze({
  'positionhazard.bare': 'Position Hazard: opens for a patient whose address, phone and health-card columns are NULL',
  'alpha.open': 'ALPHA: opens from the Forms menu with no error page, script error or failed asset',
  'cesd.redisplay': 'CESD: Save redisplays the saved record in the window it was pressed in',
  'cesd.restore': 'CESD: the redisplayed and the reopened form show every saved value again',
  'letterhead.restore': 'Letterhead: the redisplayed and the reopened form show every saved value again',
  'rourke2006.keys': 'Rourke Baby Record 2006: shows no unresolved message key',
  'rourke2006.reopen': "Rourke Baby Record 2006: a fresh chart's saved-form entry reopens the saved record",
  'positionhazard.open': 'Position Hazard: opens from the Forms menu with no error page, script error or failed asset',
  'adfv2.redisplay': 'ADFv2: Save redisplays the saved record in the window it was pressed in',
});
const CONCERN_STEP = Object.freeze({
  bare: 'opens for a patient whose address, phone and health-card columns are NULL',
  open: 'opens from the Forms menu with no error page, script error or failed asset',
  keys: 'shows no unresolved message key',
  save: 'Save stores one new row holding every typed value',
  redisplay: 'Save redisplays the saved record in the window it was pressed in',
  reopen: "a fresh chart's saved-form entry reopens the saved record",
  restore: 'the redisplayed and the reopened form show every saved value again',
  print: 'Print produces a print page, a PDF or the browser print',
});
/** What the pair declares about browser problems: the signature of the one it is pinned to, claims.ASSERTION_ONLY, or nothing. */
function knownProblem(key, concern) {
  const form = FORMS.find(entry => entry.key === key);
  return form.known && form.known[concern];
}
function generatedLabel(key, concern) {
  return `${FORMS.find(entry => entry.key === key).title}: ${CONCERN_STEP[concern]}`;
}
function stepLabel(key, concern) {
  return PINNED[claims.claimKey(key, concern)] || generatedLabel(key, concern);
}

const labelsOf = form => [`form-${form.code}`, `reopen-${form.code}`, `print-${form.code}`];

async function fieldValue(page, field) {
  const input = page.locator(`[name="${field.name}"]`).first();
  return field.check ? (await input.isChecked() ? '1' : '0') : input.inputValue();
}

/** What the page fails to show of the saved record (all of it, so one message names every missing value), or null. */
async function shownProblem(page, entry, where) {
  const { form } = entry;
  const missing = [];
  if (form.prose) {
    const shown = await page.locator(`[name="${form.prose.name}"]`).first().inputValue();
    // Lengths, never the text: the value may carry the run's marker, and a stray newline is the usual difference.
    if (shown !== entry.text) missing.push(`${form.prose.name} (typed ${entry.text.length} characters, shown ${shown.length})`);
  }
  for (const field of form.fields) {
    if (await fieldValue(page, field) !== (field.check ? '1' : field.value)) missing.push(field.name);
  }
  return missing.length ? `${where} does not show the saved ${missing.join(', ')}` : null;
}

async function revealSavedForm(page, link) {
  // Initial folding omits entries; collapsing an already-loaded list hides its li.
  if (!await link.isVisible()) await page.locator('#forms img[src$="/expand.gif"]:visible').click();
  try {
    await link.waitFor({ state: 'visible', timeout: 8000 });
  } catch (error) {
    const diag = await page.evaluate(() => ({
      all: document.querySelectorAll('#forms a[onclick*="formname="]').length,
      vis: [...document.querySelectorAll('#forms a[onclick*="formname="]')].filter(a => a.offsetWidth).length,
      expand: document.querySelectorAll('#forms img[src$="/expand.gif"]').length,
      collapse: document.querySelectorAll('#forms img[src$="/collapse.gif"]').length,
    }));
    throw new Error(`${error.message.split('\n')[0]} ${JSON.stringify(diag)}`);
  }
}

const isFormPost = response => response.request().method() === 'POST'
  && new URL(response.url()).pathname.endsWith('/form/formname');

async function workflow(s, { select = validatePin() } = {}) {
  const { sql, patient, provider, marker } = s;
  const wanted = new Set(select);
  const entries = FORMS.filter(form => form.concerns.some(concern => wanted.has(claims.claimKey(form.key, concern))))
    .map(form => ({ form, name: `${marker} ${form.code}`, results: {}, seen: new Set(), pages: [], restore: {},
      text: form.prose && (form.prose.limit && form.prose.limit < `${marker} ${PROSE}`.length ? `${marker} ${SHORT}` : `${marker} ${PROSE}`) }));
  const registrations = entries.map(entry => ({
    name: entry.name, value: `../form/${entry.form.view}.jsp?fixture=${marker}&demographic_no=`, table: entry.form.table,
  }));
  const tables = [...new Set(entries.map(entry => entry.form.table).filter(Boolean))];

  s.cleanup(() => {
    if (!tables.length) return;
    sql.execute(tables.map(table => `DELETE FROM ${table} WHERE demographic_no=${patient}`).join(';'));
    h.assert(sql.value(`SELECT ${tables.map(table => `(SELECT COUNT(*) FROM ${table} WHERE demographic_no=${patient})`).join('+')}`) === '0',
      'Form rows of the owned patient were not removed');
  });
  s.cleanup(() => {
    for (const { name, value } of registrations) {
      sql.execute(`DELETE FROM encounterForm WHERE form_value=${h.sqlString(value)} AND form_name=${h.sqlString(name)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value LIKE ${h.sqlString(`%fixture=${marker}&%`)}`) === '0',
      'The owned Forms-menu registrations were not removed');
  });

  for (const entry of entries) {
    const { form } = entry;
    h.assert(entry.name.length <= 30, `The owned registration name exceeds encounterForm.form_name: ${entry.name}`);
    if (!form.table) continue;
    // The table's own key and whether it records the saving provider, read rather than assumed.
    const columns = sql.rows(`SELECT COLUMN_NAME,COLUMN_KEY FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=${h.sqlString(form.table)}`);
    entry.idColumn = (columns.find(column => column[1] === 'PRI') || [])[0];
    entry.hasProvider = form.provider !== false && columns.some(column => column[0] === 'provider_no');
    h.assert(entry.idColumn, `FORMS is wrong: ${form.table} has no primary key`);
    for (const name of [form.prose && form.prose.name, ...form.fields.map(field => field.name)].filter(Boolean)) {
      h.assert(columns.some(column => column[0] === name), `FORMS is wrong: ${form.table} has no column ${name}`);
    }
  }
  for (const { name, value, table } of registrations) {
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${h.sqlString(value)},${h.sqlString(name)},${h.sqlString(table)},COALESCE(MAX(hidden),0)+1 FROM encounterForm`);
  }
  const chart = await s.chart();

  /**
   * Run one concern's body and record how it ended, with every JS-layer problem the form's pages raised meanwhile.
   * Save is answered by a redirect to the redisplay, so when Save succeeded the problems that follow it (the
   * redisplay's 404, say) are left for the redisplay concern to take.
   */
  async function conclude(entry, concern, body, { carry = false, assertionOnly = false } = {}) {
    let error;
    try {
      await body();
    } catch (caught) {
      error = caught;
    }
    let problems = [];
    // A problem that recurs on every page of the form (a missing stylesheet) belongs to the concern that first met it.
    // An assertion-only concern (`bare`) is judged on its own assertion: its page problems are dropped, not reported.
    if (error || !carry) problems = settleProblems(s.recorder, labelsOf(entry.form), entry.seen, { judge: !assertionOnly });
    record(entry, concern, claims.outcomeOf(error, problems));
  }
  function record(entry, concern, outcome) {
    entry.results[concern] = outcome;
    console.log(`  ${outcome.ok ? 'PASS' : 'FAIL'} ${NAME}: ${stepLabel(entry.form.key, concern)}${outcome.ok ? '' : ` -- ${outcome.message}`}`);
  }
  const blocked = (entry, concern, why) => {
    if (!entry.form.concerns.includes(concern)) return;
    entry.results[concern] = claims.blockedOutcome(why);
    console.log(`  SKIP ${NAME}: ${stepLabel(entry.form.key, concern)} -- ${entry.results[concern].message}`);
  };

  /** Pages the form opens besides its own (a Save that targets another window, a print popup) belong to it. */
  function watchPages(entry, label) {
    const onPage = popup => { h.wireStrictPage(popup, label, s.recorder); entry.pages.push(popup); };
    s.context.on('page', onPage);
    return () => s.context.off('page', onPage);
  }

  async function closeAll(entry) {
    for (const page of [entry.page, ...entry.pages]) if (page && !page.isClosed()) await page.close().catch(() => {});
    entry.page = null;
    entry.pages = [];
  }

  /** Open the form from its Forms-menu entry. The entry being there is a precondition; the form opening is the test. */
  async function openFromMenu(entry) {
    const { form } = entry;
    const link = await claims.asPrecondition(async () => {
      await chart.locator('#menuTitle1 a').hover();
      const found = chart.getByRole('link', { name: entry.name, exact: true });
      const listed = await found.count();
      claims.precondition(listed === 1, `the Forms menu lists the registered form ${listed} times, not once`);
      return found;
    }, 'the Forms-menu entry');
    entry.page = await claims.reaching(() => s.popup(chart, link, `form-${form.code}`));
    h.assert(new URL(entry.page.url()).searchParams.get('demographic_no') === patient, 'the form opened for another patient');
    if (form.reveal) await claims.asPrecondition(() => form.reveal(entry.page), 'revealing the questions');
    await entry.page.getByRole('button', { name: form.ready || 'Save', exact: true }).first().waitFor({ state: 'visible', timeout: 10000 });
  }

  /**
   * Press the form's Print and judge what comes back. A PDF is read in transit (a browser handed a PDF shows its
   * viewer, whose DOM says nothing), so the answering route is wrapped and the bytes are kept.
   */
  async function pressPrint(entry, page) {
    const { form } = entry;
    const spec = form.print;
    const button = await claims.asPrecondition(async () => {
      if (form.reveal) await form.reveal(page);
      const found = page.getByRole('button', { name: spec.button, exact: true }).first();
      await found.waitFor({ state: 'visible', timeout: 10000 });
      return found;
    }, `the ${spec.button} button`);
    if (spec.kind === 'window') {
      await page.evaluate(() => {
        window.__prints = 0;
        window.__printed = '';
        window.print = () => { window.__prints++; window.__printed = document.body.innerText; };
      });
      await h.withExpectedDialogs(page, () => button.click(), { accept: spec.confirm !== false });
      h.assert(await page.evaluate(() => window.__prints) === 1, `${spec.button} did not call the browser print exactly once`);
      if (spec.carries) h.assert((await page.evaluate(() => window.__printed)).includes(marker), 'the print view does not show the saved text');
      return;
    }
    const seen = [];
    const keep = async route => {
      const response = await route.fetch();
      const body = await response.body();
      seen.push({ status: response.status(), type: response.headers()['content-type'] || '', body });
      await route.fulfill({ response, body });
    };
    const answers = url => /\/form\/(createpdf|formname)$/.test(url.pathname);
    await s.context.route(answers, keep);
    const stop = watchPages(entry, `print-${form.code}`);
    try {
      await h.withExpectedDialogs(page, () => button.click({ noWaitAfter: true }));
      const deadline = Date.now() + 20000;
      while (!seen.length && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
    } finally {
      stop();
      await s.context.unroute(answers, keep);
    }
    h.assert(seen.length, `${spec.button} requested no PDF from the application within 20 s`);
    const [pdf] = seen;
    h.assert(pdf.status === 200 && /application\/pdf/.test(pdf.type), `${spec.button} answered ${pdf.status} ${pdf.type || 'with no content type'}, not a PDF`);
    h.assert(pdf.body.subarray(0, 4).toString() === '%PDF', `${spec.button} answered something other than a PDF document`);
    if (spec.carries) h.assert(pdfText(pdf.body).includes(marker), 'the PDF does not carry the typed text');
  }

  // ---- Phase 0: the bare concern, before the fixture is completed ----
  for (const entry of entries.filter(item => item.form.bare)) {
    // Judged on the open alone (the 500 of finding 241 against a rendered form): what else the page raises, the missing
    // Position Hazard stylesheet (235) for one, is the `open` concern's to meet once the fixture is complete.
    await conclude(entry, 'bare', () => openFromMenu(entry), { assertionOnly: true });
    await closeAll(entry);
  }

  // FIXTURE, not an assertion. The shipped templates read the patient's address and health-card columns, and two
  // of them (Position Hazard, Health Passport) throw on NULL (finding 241, pinned by the bare concern above); every
  // registered patient has them, the harness's bare fixture row does not, so they are filled in before the rest.
  sql.execute(`UPDATE demographic SET address='1 Test St',city='Toronto',postal='M5V 2T6',phone='416-555-0100',
    phone2='416-555-0101',hin='9876543217',ver='AB',email='fake@example.invalid',roster_status='RO'
    WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);

  // ---- Phase 1, per form: open, keys, save, redisplay (and print, for a form that saves nothing) ----
  for (const entry of entries) {
    const { form } = entry;
    if (!form.concerns.includes('open')) continue;
    await conclude(entry, 'open', () => openFromMenu(entry));
    if (!entry.page || entry.page.isClosed()) {
      for (const concern of ['keys', 'save', 'redisplay', 'reopen', 'restore', 'print']) blocked(entry, concern, 'the form did not open');
      continue;
    }
    await conclude(entry, 'keys', async () => {
      const { title, text } = await entry.page.evaluate(() => ({ title: document.title, text: document.body.innerText }));
      const keys = [...new Set(`${title}\n${text}`.match(/\?\?\?[\w.-]+\?\?\?/g) || [])];
      h.assert(!keys.length, `the page shows ${keys.length} unresolved message key(s): ${keys.slice(0, 3).join(' ')}`);
    });

    if (!form.table) {
      // Nothing is saved: type, then Print on the form as opened.
      await conclude(entry, 'print', async () => {
        if (form.prose) await claims.asPrecondition(() => entry.page.locator(`[name="${form.prose.name}"]`).first().fill(entry.text), 'typing into the form');
        await pressPrint(entry, entry.page);
      });
      await closeAll(entry);
      continue;
    }
    await conclude(entry, 'save', async () => {
      const page = entry.page;
      await claims.asPrecondition(async () => {
        if (form.prose) await page.locator(`[name="${form.prose.name}"]`).first().fill(entry.text);
        for (const field of form.fields) {
          const input = page.locator(`[name="${field.name}"]`).first();
          if (field.check) await input.check(); else await input.fill(field.value);
        }
      }, 'typing into the form');
      entry.before = Number(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`));
      const stop = watchPages(entry, `form-${form.code}`);
      const posted = s.context.waitForEvent('response', { predicate: isFormPost, timeout: 15000 });
      posted.catch(() => {});
      try {
        const dialogs = await h.withExpectedDialogs(page, () => page.getByRole('button', { name: 'Save', exact: true }).first().click());
        const response = await posted.catch(() => null);
        h.assert(response, `Save posted nothing to form/formname (dialogs: ${dialogs.map(d => `${d.type} "${d.text.slice(0, 60)}"`).join('; ') || 'none'})`);
        h.assert(response.status() === 302, `the save was answered ${response.status()}, not accepted with a redirect`);
        entry.saveFrame = response.frame();
      } finally {
        stop();
      }
      await expectValue(sql, `SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`, String(entry.before + 1),
        'Save did not store exactly one new row');
      const columns = [entry.idColumn, form.prose && form.prose.name, ...form.fields.map(field => field.name), entry.hasProvider && 'provider_no']
        .filter(Boolean).map(column => `\`${column}\``);
      const [row] = sql.rows(`SELECT ${columns.join(',')} FROM ${form.table} WHERE demographic_no=${patient}
        ORDER BY \`${entry.idColumn}\` DESC LIMIT 1`);
      let at = 1;
      entry.id = row[0];
      if (form.prose) h.assert(row[at++] === entry.text, `the stored ${form.prose.name} differs from what was typed`);
      for (const field of form.fields) {
        h.assert(row[at++] === (field.check ? '1' : field.value), `the stored ${field.name} differs from what was set`);
      }
      if (entry.hasProvider) h.assert(row[at] === provider, 'the row is not attributed to the signed-in provider');
    }, { carry: true });
    if (!entry.results.save.ok) {
      for (const concern of ['redisplay', 'reopen', 'restore', 'print']) blocked(entry, concern, 'Save stored no usable row');
      await closeAll(entry);
      continue;
    }
    await conclude(entry, 'redisplay', async () => {
      const landed = entry.saveFrame && entry.saveFrame.page();
      claims.precondition(landed, 'the window that answered Save could not be found');
      h.assert(landed === entry.page,
        'the saved record was redisplayed in another window, and the one Save was pressed in still shows the unsaved form');
      await landed.waitForURL(url => url.pathname.endsWith('/form/forwardname'), { waitUntil: 'domcontentloaded' });
      h.assert(new URL(landed.url()).searchParams.get('formId') === entry.id, 'Save redisplayed another record');
      await h.assertNotErrorPage(landed, 'the redisplayed form');
      entry.restore.redisplay = await shownProblem(landed, entry, 'the redisplayed form');
    });
    await closeAll(entry);
  }

  /**
   * Click the chart's saved-form entry. Closing a form window makes the chart reload its Forms module, which replaces
   * the anchors and folds the list again, so a click that races the reload is retried on a freshly found entry. Only a
   * failure to find or click the entry is retried, never one after the popup has opened.
   */
  async function openSavedEntry(fresh, entry) {
    let last;
    for (let attempt = 1; attempt <= 3; attempt++) {
      try {
        await fresh.waitForLoadState('networkidle').catch(() => {});
        const link = fresh.locator(`#leftNavBar a[onclick*="formname=${entry.name}&"], #rightNavBar a[onclick*="formname=${entry.name}&"]`).first();
        await revealSavedForm(fresh, link);
        return await ui.clickOpensPopup(fresh, link, { context: s.context, recorder: s.recorder,
          label: `reopen-${entry.form.code}`, timeout: attempt < 3 ? 8000 : 20000, position: { x: 8, y: 9 } });
      } catch (error) {
        last = error;
        if (!/locator\.(click|waitFor)|detached|not stable/.test(error.message)) throw error;
      }
    }
    throw last;
  }

  // ---- Phase 2: a fresh chart lists each saved form; reopen it, then Print ----
  const saved = entries.filter(entry => entry.form.table && !entry.results.reopen);
  if (saved.length) {
    const fresh = await s.context.newPage();
    h.wireStrictPage(fresh, 'reopen-chart', s.recorder);
    await fresh.goto(chart.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(fresh, 20000);
    for (const entry of saved) {
      const { form } = entry;
      let page;
      await conclude(entry, 'reopen', async () => {
        page = await claims.reaching(() => openSavedEntry(fresh, entry));
        const params = new URL(page.url()).searchParams;
        h.assert(params.get('demographic_no') === patient, 'the saved-form entry opened another patient');
        h.assert(params.get('formId') === entry.id, 'the saved-form entry did not open the saved record');
        entry.restore.reopen = await shownProblem(page, entry, 'the reopened form');
      });
      if (!page || page.isClosed()) { blocked(entry, 'print', 'the saved record did not reopen'); continue; }
      entry.page = page;
      await conclude(entry, 'print', () => pressPrint(entry, page));
      await closeAll(entry);
    }
    await fresh.close();
  }

  // The restore concern judges the places the saved record was shown; if neither could be read it is blocked.
  for (const entry of entries.filter(item => item.form.table && !item.results.restore)) {
    const shown = Object.entries(entry.restore);
    if (!shown.length) { blocked(entry, 'restore', 'neither the redisplayed nor the reopened form could be read'); continue; }
    const problems = shown.map(([, problem]) => problem).filter(Boolean);
    record(entry, 'restore', problems.length ? claims.outcomeOf(new Error(problems.join(' | '))) : { ok: true });
  }

  // ---- Phase 3: assert the claimed pairs, in table order ----
  for (const entry of entries) {
    for (const concern of entry.form.concerns.filter(name => wanted.has(claims.claimKey(entry.form.key, name)))) {
      const label = stepLabel(entry.form.key, concern);
      // Only the pair's own failure carries the pinned label; a precondition, a concern that was not reached and
      // browser problems beyond the known one are reported under a label of their own.
      const failure = claims.claimFailure(entry.results[concern], label, knownProblem(entry.form.key, concern));
      if (failure) throw h.markFailedStep(new Error(failure.message), failure.label);
      console.log(`  ASSERTED ${NAME}: ${label}`);
    }
  }
}

if (require.main === module) runWorkflow(NAME, workflow, {
  preflight: () => require('./lib/export-content-helpers').requirePoppler('pdftotext'),
});
module.exports = { workflow, FORMS, CONCERNS, validatePin, stepLabel, generatedLabel, knownProblem, PINNED, claimForms: CLAIM_FORMS, revealSavedForm };
