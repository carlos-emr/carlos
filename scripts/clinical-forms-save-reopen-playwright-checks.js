#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 encounter forms. User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸
// Forms menu ▸ <form> ▸ fill ▸ Save (form/formname, or the form's own route) ▸ reopen from the
// E-Chart's saved-forms entry (form/forwardshortcutname?formId=latest) ▸ Print where the form
// prints server-side. Table-driven over the Ontario encounter forms (Annual Health Review, Mental
// Health Form 1, Form 14 and Form 42, Discharge Summary, Palliative Care, Peri-Menopausal, MMSE,
// Annual V2 for a female and for a male patient, Vascular Tracker, and the three-page Mental Health
// referral / assessment / outcome chain): each form's prose cell carries clinical punctuation, a
// structured field is set, the save is asserted in the form's own table (one new row, exact values,
// the signed-in provider), on the redisplayed page, after reopening, and in the print; Palliative Care
// is also saved a second time from the redisplayed window. The remaining Ontario forms are in
// form-catalog-smoke-playwright-checks.js.
//
// KNOWN FAILURES AND CLAIMS. Every form's flow always runs to the end and every concern's outcome is
// recorded (one broken form never hides the rest); the entry then asserts, one labelled step each,
// the (form, concern) pairs it CLAIMS. CLINICAL_FORMS_ONLY and CLINICAL_FORMS_EXCEPT (lib/form-claims.js:
// `<form>` or `<form>.<concern>`, forms lower case) choose them, so a broken form gets its own manifest entry
// pinned on its own finding, the default entry leaves that pair out, and scripts/form-claims.test.js proves
// the entries together claim every pair exactly once. The concerns are open, validate (Discharge Summary and
// Mental Health Forms 1, 14 and 42: invalid dates and cancelled confirmations write nothing), save, redisplay, chain (the
// next Mental Health pages), opener (a Save leaves the E-Chart that opened the form alone), reopen, print, dialogs (Save's
// confirmation), resave (Palliative Care) and
// problems (no JavaScript-layer problem on the form's pages).
// Fixtures: the owned synthetic patient and marker-named Forms-menu registrations of each form
// (the shipped rows are hidden on Ontario installs and are clinic-wide, so run EXCLUSIVE=1);
// cleanup deletes every form row of the owned patient and the registrations, and asserts both.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const claims = require('./lib/form-claims');
const { markProblems, takeProblems } = require('./lib/form-problems');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { pdfText } = require('./form-print-pdf-playwright-checks');

const NAME = 'clinical-forms-save-reopen';
const PROSE = "BP 120/80 & HR 72; pt's c/o SOB";
const DATE = { value: '2026/09/30', stored: '2026-09-30' };
const check = name => ({ name, check: true, stored: '1' });
const date = name => ({ name, value: DATE.value, stored: DATE.stored });

// key: menu suffix; path/query: the shipped form_value; idColumn: the table's key column;
// provider: the table records the saving provider; confirm: Save asks before posting;
// print: 'page' (a server-rendered print popup), 'pdf' (a POST answered with a PDF), 'window' (the button calls
// window.print, stubbed and counted) or 'popup' (a window of its own receives the print page; `pages` lists, for a
// multi-page form, each page's link, route and print route, and each page is printed from itself), pressed
// on the reopened form (on the saved page when printOn is 'saved'); resave: Save again from the window the first
// Save redisplayed; sex: the patient's sex the form needs (the Annual V2 wrapper picks its template by it);
// reopenNow: reopen straight after the save, because the next row writes the same table and the chart's
// saved-form entry always opens the latest record of the table; chain: the later pages of a multi-page form;
// dates: the date boxes Save validates (the `validate` concern types invalid dates into each one).
const FORMS = [
  { key: 'ANN', title: 'Annual Health Review', path: '../form/formannual.jsp', table: 'formAnnual',
    idColumn: 'ID', provider: true, confirm: true, prose: 'currentConcerns',
    fields: [check('headN'), date('formDate')], print: { button: 'Print Page', kind: 'page' } },
  { key: 'MH1', title: 'Mental Health Form 1', path: '../form/formMentalHealthForm1.jsp',
    table: 'formMentalHealthForm1', idColumn: 'id', provider: false, confirm: false, prose: 'observation',
    fields: [check('threatened'), { name: 'onDate', value: '2026/09/30', stored: '2026/09/30' },
      { name: 'todayDate', value: '2026-10-01', stored: '2026-10-01' }], dates: ['onDate', 'todayDate'],
    print: { button: 'Print Pdf', kind: 'pdf' } },
  { key: 'MH14', title: 'Mental Health Form 14', path: '../form/formMentalHealthForm14.jsp',
    table: 'formMentalHealthForm14', idColumn: 'id', provider: false, confirm: false, prose: 'physicianName',
    // Forms 14 and 42 ask for day / month / year, and Save accepts that as well as yyyy/mm/dd (#4597).
    fields: [{ name: 'relationship', value: 'Spouse', stored: 'Spouse' },
      { name: 'signatureDate', value: '30/09/2026', stored: '30/09/2026' }], dates: ['signatureDate'],
    print: { button: 'Print Pdf', kind: 'pdf' } },
  { key: 'MH42', title: 'Mental Health Form 42', path: '../form/formMentalHealthForm42.jsp',
    table: 'formMentalHealthForm42', idColumn: 'id', provider: false, confirm: false, prose: 'name',
    fields: [check('chkThreatenedA'), { name: 'dateOfExamination', value: '2026/09/30', stored: '2026/09/30' },
      { name: 'dateOfOrder', value: '30-09-2026', stored: '30-09-2026' }], dates: ['dateOfExamination', 'dateOfOrder'],
    print: { button: 'Print Pdf', kind: 'pdf' } },
  { key: 'DS', title: 'Discharge Summary', path: '../form/formDischargeSummary.jsp', table: 'formDischargeSummary',
    idColumn: 'id', provider: true, confirm: true, prose: 'briefSummary', fields: [date('dischargeDate')] },
  { key: 'PC', title: 'Palliative Care', path: '../form/formpalliativecare.jsp', table: 'formPalliativeCare',
    idColumn: 'ID', provider: true, confirm: true, prose: 'pain1', fields: [date('date1')], resave: true },
  { key: 'PERI', title: 'Peri-Menopausal', path: '../form/formperimenopausal.jsp', table: 'formPeriMenopausal',
    idColumn: 'ID', provider: true, confirm: true, prose: 'orf_comments', fields: [check('orf_emYes')] },
  { key: 'MMSE', title: 'Mini-Mental State Examination', path: '../form/formmmse.jsp', table: 'formMMSE',
    idColumn: 'ID', provider: true, confirm: true, prose: 'diagnosis',
    fields: [check('lc_alert'), { name: 'o_date', value: '5', stored: '5' }] },
  // The Annual V2 wrapper (formannualV2.jsp) includes the female or the male template by the patient's sex,
  // so each row sets the owned patient's sex first. Both write formAnnualV2, so the female record is
  // reopened straight after its save, before the male one becomes the table's latest.
  { key: 'AV2F', title: 'Annual V2 (female)', path: '../form/formannualV2.jsp', table: 'formAnnualV2',
    idColumn: 'ID', provider: true, confirm: true, prose: 'impressionPlan', sex: 'F', reopenNow: true,
    fields: [check('pmhxPshxUpdated'), { name: 'weight', value: '70', stored: '70' }], print: { button: 'Print', kind: 'window' } },
  { key: 'AV2M', title: 'Annual V2 (male)', path: '../form/formannualV2.jsp', table: 'formAnnualV2',
    idColumn: 'ID', provider: true, confirm: true, prose: 'impressionPlan', sex: 'M',
    fields: [check('pmhxPshxUpdated'), { name: 'weight', value: '80', stored: '80' }], print: { button: 'Print', kind: 'window' } },
  // Referral, then Assessment, then Outcome: three Saves of one multi-page record (each Save files a merged
  // row), each page printed, and reopened from the chart. Referred By is typed on the Referral page and carried,
  // read-only, by the other two: its quotes, apostrophe, ampersand and angle brackets must survive every
  // redisplay, every later Save, the reopen and the three prints (#4683).
  { key: 'MHC', title: 'Mental Health', path: '../form/formmentalhealth.jsp', table: 'formMentalHealth',
    idColumn: 'ID', provider: true, confirm: true, prose: 'r_refComments', printOn: 'saved',
    fields: [{ name: 'c_referredBy', value: `Dr "Sam" O'Neil & <Co>`, stored: `Dr "Sam" O'Neil & <Co>` }],
    chain: [{ link: 'Assessment', prose: 'a_assComments' }, { link: 'Outcome', prose: 'o_outComments' }],
    print: { button: 'Print', kind: 'popup', pages: [
      { link: 'Referral', page: '/form/formmhreferral', pathname: '/form/formmhreferralprint' },
      { link: 'Assessment', page: '/form/formmhassessment', pathname: '/form/formmhassessmentprint' },
      { link: 'Outcome', page: '/form/formmhoutcome', pathname: '/form/formmhoutcomeprint' }] } },
  // The shipped registration points at the Struts 1 route SetupForm.do. The chart opened it as a raw URL and the
  // application answered 404 (finding 103, fixed in #4667: the route now resolves to SetupForm). That row only opens,
  // so nothing downstream of the open can hold it up; the form behind it is VT2.
  { key: 'VT', title: 'Vascular Tracker', path: '../form/SetupForm.do', query: 'formName=VTForm&',
    table: 'formVTForm', openOnly: true, ready: { button: 'Update VT' } },
  // The same form registered the way the route is mapped (SetupForm without the .do suffix, which the action's own
  // registration check now accepts either way). It saves through form/SubmitForm, names its inputs
  // `value(Name)` (the column is Name), and has an Update VT button instead of Save. Its Save answered 400 (finding
  // 254) and every load and Update VT threw a TypeError from the missing eye-exam inputs (finding 255); both are fixed
  // in #4667 and stay covered as regressions.
  { key: 'VT2', title: 'Vascular Tracker (extensionless route)', path: '../form/SetupForm', query: 'formName=VTForm&',
    table: 'formVTForm', idColumn: 'ID', provider: false, confirm: false, prose: 'value(ExerComments)',
    fields: [{ name: 'value(ExerValue)', value: 'Y', stored: 'Y' }], saveButton: 'Update VT', savePath: '/form/SubmitForm',
    idInUrl: false, ready: { button: 'Update VT' } },
].map(form => {
  if (form.openOnly) return { ...form, claim: form.key.toLowerCase(), concerns: ['open', 'problems'] };
  const concerns = ['open'];
  if (form.key === 'DS' || form.dates) concerns.push('validate');
  concerns.push('save', 'redisplay');
  if (form.chain) concerns.push('chain', 'opener');
  concerns.push('reopen');
  if (form.print) concerns.push('print');
  concerns.push('dialogs');
  if (form.resave) concerns.push('resave');
  concerns.push('problems');
  return { ...form, claim: form.key.toLowerCase(), concerns };
});

const CLAIM_FORMS = FORMS.map(({ claim, concerns }) => ({ key: claim, concerns }));
const ONLY = 'CLINICAL_FORMS_ONLY';
const EXCEPT = 'CLINICAL_FORMS_EXCEPT';

/**
 * CLINICAL_FORMS_ONLY and CLINICAL_FORMS_EXCEPT must be unset or lists of `<form>` / `<form>.<concern>` that name
 * real pairs and leave something to assert. Judged when the check runs, never when the module is required.
 */
function validatePin(env = process.env) {
  return claims.effectiveClaims({ only: env[ONLY], except: env[EXCEPT], onlyVariable: ONLY, exceptVariable: EXCEPT }, CLAIM_FORMS);
}

/*
 * The attempt labels are literals where the manifest pins them: expectedFailure.step is checked against this
 * file's text (run-playwright-suite.js validateExpectedFailure), and a label assembled at run time cannot be
 * found there. A step's label is `<form title>: <attempt label>`, which is what the console prints.
 * scripts/form-claims.test.js proves each pinned literal equals the label the run generates.
 */
// No pair is pinned at present: the Mental Health findings 236, 238, 239 and 242 are fixed (#4595, #4596, #4597,
// #4600), and their manifest entries remain as regression coverage.
const PINNED = Object.freeze({});
const stepLabel = (form, concern, attemptLabel) => PINNED[claims.claimKey(form.claim, concern)] || `${form.title}: ${attemptLabel}`;

const labels = form => [`form-${form.key}`, `reopen-${form.key}`, `print-${form.key}`];

/** The column an input fills: the Vascular Tracker names its inputs value(Column). */
const column = name => name.replace(/^value\((.+)\)$/, '$1');
/** The input that carries the form's text once everything is saved: the last page of a multi-page form. */
const lastProse = form => (form.chain ? form.chain[form.chain.length - 1].prose : form.prose);
const VT_EYE_TYPES = ['iDia', 'iHyp', 'iOth', 'iRef'];
const VT_FOOT_TYPES = ['FTEx', 'FTNe', 'FTIs', 'FTUl', 'FTIn', 'FTOt', 'FTRe'];

async function assertVascularTrackerGroups(page, diabetes, pvd) {
  for (const [types, enabled] of [[VT_EYE_TYPES, diabetes], [VT_FOOT_TYPES, diabetes || pvd]]) {
    for (const type of types) {
      const radios = page.locator(`input[name="value(${type}Value)"]`);
      h.assert(await radios.count() === 2, `${type} must have both Yes and No controls`);
      for (const radio of await radios.all()) {
        h.assert(await radio.isEnabled() === enabled, `${type} enablement differs from the selected DM/PVD state`);
      }
    }
  }
}

async function prepareVascularTracker(page, text) {
  await page.waitForLoadState('load'); // VT restores values and enables controls in body.onload.
  h.assert(await page.locator('td').filter({hasText: /^\s*null\s*$/}).count() === 0,
    'A fresh VT renders missing history as the literal null');
  const diabetes = page.locator('input[name="value(DMValue)"]');
  const pvd = page.locator('input[name="value(PVDValue)"]');
  await assertVascularTrackerGroups(page, false, false);
  await pvd.check();
  await assertVascularTrackerGroups(page, false, true);
  await diabetes.check();
  await assertVascularTrackerGroups(page, true, true);
  await pvd.uncheck();
  await assertVascularTrackerGroups(page, true, false);
  await diabetes.uncheck();
  await assertVascularTrackerGroups(page, false, false);
  await diabetes.check();
  await page.locator('input[name="value(iDiaValue)"][value="yes"]').check();
  await page.locator('input[name="iDate"]').fill(DATE.stored);
  await page.locator('textarea[name="iComments"]').fill(`${text} eye`);
}

function assertVascularTrackerStored(sql, patient, id, text) {
  const columns = ['DMValue', 'iDiaValue', ...VT_EYE_TYPES.flatMap(type => [`${type}Date`, `${type}Comments`])];
  const [row] = sql.rows(`SELECT ${columns.join(',')} FROM formVTForm WHERE ID=${id} AND demographic_no=${patient}`);
  const expected = ['yes', 'yes', ...VT_EYE_TYPES.flatMap(() => [DATE.stored, `${text} eye`])];
  h.assert(JSON.stringify(row) === JSON.stringify(expected), 'The saved eye result/date/comments differ from the visible VT fields');
}

async function fieldValue(page, field) {
  const input = page.locator(`[name="${field.name}"]`).first();
  return field.check ? (await input.isChecked() ? '1' : '0') : input.inputValue();
}

async function assertShown(page, form, text, where, proseName = form.prose) {
  if (form.key === 'VT2') await page.waitForLoadState('load');
  if (proseName) {
    h.assert(await page.locator(`[name="${proseName}"]`).first().inputValue() === text,
      `${where} does not show the saved ${proseName}`);
  }
  for (const field of form.fields) {
    const shown = await fieldValue(page, field);
    h.assert(shown === (field.check ? '1' : field.value), `${where} does not show the saved ${field.name}`);
  }
  if (form.key === 'VT2') {
    h.assert(await page.locator('input[name="value(DMValue)"]').isChecked(), `${where} lost the DM selection`);
    h.assert(!await page.locator('input[name="value(PVDValue)"]').isChecked(), `${where} invented a PVD selection`);
    await assertVascularTrackerGroups(page, true, false);
    h.assert(await page.locator('input[name="value(iDiaValue)"][value="yes"]').isChecked(), `${where} lost the eye result`);
    h.assert(await page.locator('input[name="iDate"]').inputValue() === DATE.stored, `${where} lost the eye observation date`);
    h.assert(await page.locator('textarea[name="iComments"]').inputValue() === `${text} eye`, `${where} lost the eye comment`);
  }
}

async function revealSavedForm(page, link) {
  // Initial folding omits entries; collapsing an already-loaded list hides its li.
  // The image owns the click handler in both cases, and its URL is locale-independent.
  if (!await link.isVisible()) await page.locator('#forms img[src$="/expand.gif"]:visible').click();
  await link.waitFor({ state: 'visible' });
}

/**
 * SetupForm lazily creates clinic-wide VT measurement definitions, even on an open-only
 * visit. They belong in explicit environment initialization, before the suite's residue
 * baseline. Refuse a missing initializer before this workflow writes registrations or opens
 * the form; never delete or silently allow clinic-wide dictionary rows after a browser run.
 */
function requireVascularDictionaryReady(sql, xml = require('node:fs').readFileSync(
  require('node:path').join(__dirname, '../src/main/webapp/form/VTForm.xml'), 'utf8')) {
  // A reader for this checked-in definition's two scalar keys, not a general XML parser.
  h.assert(typeof xml === 'string' && !/<!DOCTYPE|<!ENTITY/i.test(xml), 'Invalid VT definition fixture');
  const blocks = [...xml.matchAll(/<measurement>([\s\S]*?)<\/measurement>/g)];
  h.assert(blocks.length > 0 && blocks.length === (xml.match(/<measurement>/g) || []).length
    && blocks.length === (xml.match(/<\/measurement>/g) || []).length, 'Incomplete VT measurement definitions');
  const decode = text => text.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'").replace(/&amp;/g, '&');
  const definitions = blocks.map(([ , block]) => {
    const read = tag => {
      const matches = [...block.matchAll(new RegExp(`<${tag}>([^<]*)<\\/${tag}>`, 'g'))];
      h.assert(matches.length === 1, `VT definition must have one ${tag}`);
      const text = matches[0][1];
      h.assert(!/&(?!lt;|gt;|quot;|apos;|amp;)/.test(text), 'Unsupported VT definition entity');
      return decode(text).trim();
    };
    const type = read('type'), instruction = read('measuringInstrc');
    h.assert(type.length > 0, 'VT definition has no measurement type');
    return { type, instruction };
  });
  const keys = definitions.map(({ type, instruction }) => JSON.stringify([type, instruction]));
  h.assert(new Set(keys).size === keys.length, 'Duplicate VT measurement definition key');
  const required = definitions.map(({ type, instruction }) =>
    `SELECT ${h.sqlString(type)} AS vtType,${h.sqlString(instruction)} AS vtInstruction`).join(' UNION ALL ');
  const missing = sql.rows(`SELECT vt_expected.vtType,vt_expected.vtInstruction FROM (${required}) vt_expected
    WHERE NOT EXISTS (SELECT 1 FROM measurementType mt
      WHERE mt.type=vt_expected.vtType AND mt.measuringInstruction=vt_expected.vtInstruction)`);
  h.assert(Array.isArray(missing) && missing.every(row => Array.isArray(row) && row.length === 2
    && row.every(value => typeof value === 'string')), 'VT dictionary precondition returned invalid rows');
  h.assert(missing.length === 0,
    `VT dictionary initialization is required before the suite residue baseline; missing keys: ${JSON.stringify(missing)}`);
}

async function workflow(s, { forms = FORMS, foldSavedForms = false, select = validatePin() } = {}) {
  const { sql, patient, provider, marker } = s;
  const wanted = new Set(select);
  const results = [];
  const chosen = forms.filter(form => form.concerns.some(concern => wanted.has(claims.claimKey(form.claim, concern))));
  if (chosen.some(form => form.key === 'VT' || form.key === 'VT2')) requireVascularDictionaryReady(sql);
  const registrations = chosen.map(form => ({
    form, name: `${marker} ${form.key}`, value: `${form.path}?fixture=${marker}&row=${form.key}&${form.query || ''}demographic_no=`,
  }));
  // encounterForm.form_value is the table's key, so two rows that open the same JSP (Annual V2) differ by `row=`.
  // Extra owned aliases put the selected saved form beyond the navbar's first six
  // entries without creating additional patient records or changing clinic settings.
  if (foldSavedForms) {
    const form = chosen[0];
    for (let i = 0; i < 8; i++) registrations.unshift({
      form, name: `BC ${marker} ${i}`, fixtureOnly: true,
      value: `${form.path}?fixture=${marker}&fold=${i}&demographic_no=`,
    });
  }
  s.cleanup(() => {
    sql.execute(FORMS.map(form => `DELETE FROM ${form.table} WHERE demographic_no=${patient}`).join(';'));
    h.assert(sql.value(`SELECT ${FORMS.map(form => `(SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient})`)
      .join('+')}`) === '0', 'Form rows of the owned patient were not removed');
  });
  if (chosen.some(form => form.key === 'VT2')) s.cleanup(() => {
    // VT Save & Exit writes measurements. runWorkflow verifies ownership of this synthetic
    // patient before invoking cleanup; remove only its child rows before removing the patient.
    sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN
      (SELECT id FROM measurements WHERE demographicNo=${patient});
      DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`) === '0',
      'VT measurements of the owned patient were not removed');
  });
  s.cleanup(() => {
    for (const { name, value } of registrations) {
      sql.execute(`DELETE FROM encounterForm WHERE form_value=${h.sqlString(value)} AND form_name=${h.sqlString(name)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value LIKE ${h.sqlString(`%fixture=${marker}&%`)}`) === '0',
      'The owned Forms-menu registrations were not removed');
  });
  // The shipped registrations are hidden on Ontario installs and enabling one would edit a demo
  // row, so the run registers its own marker-named entry pointing at the shipped form_value.
  for (const { form, name, value } of registrations) {
    h.assert(name.length <= 30, 'The owned registration name exceeds encounterForm.form_name');
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${h.sqlString(value)},${h.sqlString(name)},${h.sqlString(form.table)},COALESCE(MAX(hidden),0)+1 FROM encounterForm`);
  }
  // FIXTURE, not an assertion: the Mental Health referral warns about a patient who is not rostered. The
  // warning is the form's behaviour; a rostered fixture lets the check reach the form's own controls.
  sql.execute(`UPDATE demographic SET roster_status='RO' WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  const chart = await s.chart();

  /**
   * Run one concern's body and record how it ended (lib/form-claims.js outcomeOf). `needs` are the concerns it cannot
   * run without; unmet, it is recorded as not reached. `own` are the page labels whose browser problems belong to
   * this concern (the reopened and the print windows), so a script error on a reopened page is reported with the
   * reopen, not with the form's own pages in the `problems` concern. A body marks what it merely needs in order to
   * act (a menu entry, a button, the typing) with claims.precondition / asPrecondition / reaching, so that a pin is
   * never held by a step that could not be attempted.
   */
  async function attempt(entry, concern, label, body, needs = [], own = [], since) {
    const stamp = stepLabel(entry.form, concern, label);
    const unmet = needs.filter(need => entry.form.concerns.includes(need) && !(entry.results[need] && entry.results[need].ok));
    if (unmet.length) {
      entry.results[concern] = claims.blockedOutcome(`${unmet.join(' and ')} did not pass`);
      console.log(`  SKIP ${NAME}: ${stamp} -- ${entry.results[concern].message}`);
      return;
    }
    let error;
    let detail = [];
    const mark = since || markProblems(s.recorder);
    try {
      await body();
    } catch (caught) {
      error = caught;
      // The rest of a Playwright call log (what the click waited for) is what tells a race from a defect.
      detail = caught.message.split('\n').slice(1, 12);
    }
    // A problem the form's `problems` pair is pinned to (a script error on every page load) stays in the recorder for
    // that pair to take: it is not a reason for the reopen or the print to fail.
    const owned = entry.form.known && entry.form.known.problems;
    // A concern that ended in a failure also owns what the form's own pages raised while it ran: the 400 a rejected
    // Save is answered with, and the handler error that came with the click, are that Save's failure, not a second
    // defect for the `problems` pair (which would then never read as the one defect it is pinned to). A concern that
    // passed leaves them where they are, so a script error beside a working Save is still judged by `problems`.
    const pages = own.length ? own : (error ? [`form-${entry.form.key}`] : []);
    const problems = pages.length ? [...new Set(takeProblems(s.recorder, pages, mark, owned ? (text => owned.test(text)) : undefined))] : [];
    const outcome = claims.outcomeOf(error, problems);
    entry.results[concern] = outcome;
    console.log(`  ${outcome.ok ? 'PASS' : 'FAIL'} ${NAME}: ${stamp}${outcome.ok ? '' : ` -- ${outcome.message}`}`);
    for (const line of detail) console.log(`        ${line.slice(0, 220)}`);
  }

  /** The Annual V2 wrapper picks its template by the patient's sex when it opens and when it reopens a record. */
  let sexNow = 'F';
  function setSex(sex) {
    if (sex === sexNow) return;
    sql.execute(`UPDATE demographic SET sex=${h.sqlString(sex)} WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
    sexNow = sex;
  }

  for (const registration of registrations.filter(item => !item.fixtureOnly)) {
    const { form, name } = registration;
    const entry = { form, name, results: {}, text: `${marker} ${PROSE}${form.key === 'ANN' ? ' <follow-up>' : ''}` };
    results.push(entry);
    // Each form's menu entry is asserted in its own attempt, so one missing registration is
    // recorded without stopping the remaining forms.
    await attempt(entry, 'open', 'is listed once in the Forms menu and opens for the owned patient', async () => {
      // The entry being in the menu is what the step needs; the form opening for the owned patient is the test.
      const link = await claims.asPrecondition(async () => {
        setSex(form.sex || 'F');
        // Save refreshes the panel immediately; a started-form item can now have
        // the same name as its menu entry. Count registrations inside the menu only.
        const found = chart.locator('#forms .menu').getByRole('link', { name, exact: true, includeHidden: true });
        await found.first().waitFor({state: 'attached', timeout: 20000});
        const listed = await found.count();
        claims.precondition(listed === 1, `the Forms menu lists the registered form ${listed} times, not once`);
        await ui.openFormsMenu(chart);
        return found;
      }, 'the Forms-menu entry');
      entry.page = await claims.reaching(() => s.popup(chart, link, `form-${form.key}`));
      // SetupForm forwards internally; VT's hidden patient is the explicit setup/save contract.
      if (form.idInUrl !== false) h.assert(new URL(entry.page.url()).searchParams.get('demographic_no') === patient, 'The form opened for another patient');
      const ready = form.ready ? entry.page.getByRole('button', { name: form.ready.button, exact: true }).first()
        : entry.page.locator(`[name="${form.prose}"]`).first();
      await ready.waitFor({ state: 'visible' });
      if (form.key === 'VT2') h.assert(await entry.page.locator('input[name="demographic_no"]').inputValue() === patient,
        'VT setup rendered another patient');
    });

    if (form.openOnly) continue;

    if (form.key === 'DS') {
      await attempt(entry, 'validate', 'invalid dates and cancelled confirmations prevent writes without JavaScript errors', async () => {
        const { page } = entry;
        const dateInput = page.locator('[name="dischargeDate"]');
        const save = page.getByRole('button', { name: 'Save', exact: true }).first();
        const originalUrl = page.url();
        let posts = 0;
        const countPost = request => {
          if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/form/formname')) posts++;
        };
        page.on('request', countPost);
        try {
          await page.locator(`[name="${form.prose}"]`).fill(entry.text);
          await dateInput.fill('2026/02/30');
          const invalid = await h.withExpectedDialogs(page, () => save.click());
          h.assert(invalid.length === 1 && invalid[0].type === 'alert',
            'An invalid date must show one validation alert before any save confirmation');
          h.assert(await dateInput.inputValue() === '2026/02/30', 'Validation discarded the typed date');
          h.assert(page.url() === originalUrl && posts === 0, 'Invalid-date validation submitted the form');
          h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '0',
            'Invalid-date validation wrote a form record');
          await dateInput.fill(DATE.value);
          const cancelled = await h.withExpectedDialogs(page, () => save.click(), { accept: false });
          h.assert(cancelled.length === 1 && cancelled[0].type === 'confirm',
            'A valid date must reach exactly one cancellable save confirmation');
          h.assert(page.url() === originalUrl && posts === 0, 'Cancelling Save submitted the form');
          h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '0',
            'Cancelling Save wrote a form record');
          h.assert(await dateInput.inputValue() === DATE.value
            && await page.locator(`[name="${form.prose}"]`).inputValue() === entry.text,
          'Cancelling Save discarded the typed date or prose');
          h.assertStrictPage(s.recorder, labels(form));
        } finally {
          page.off('request', countPost);
        }
      }, ['open']);
    }

    if (form.dates) {
      await attempt(entry, 'validate', 'each real date is validated and cancelling Save and Exit preserves the unsaved form', async () => {
        const { page } = entry;
        const originalUrl = page.url();
        let posts = 0;
        const countPost = request => {
          if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/form/formname')) posts++;
        };
        page.on('request', countPost);
        try {
          await page.locator(`[name="${form.prose}"]`).fill(entry.text);
          const dates = form.fields.filter(field => form.dates.includes(field.name));
          for (const field of dates) await page.locator(`[name="${field.name}"]`).fill(field.value);
          for (const field of dates) {
            const input = page.locator(`[name="${field.name}"]`);
            for (const invalidValue of ['2026/02/30', '2026//01', '2026/01/', '2026//', '2026--01']) {
              await input.fill(invalidValue);
              const invalid = await h.withExpectedDialogs(page,
                () => page.getByRole('button', { name: 'Save', exact: true }).first().click());
              h.assert(invalid.length === 1 && invalid[0].type === 'alert'
                && /valid date/i.test(invalid[0].text), 'An invalid date must show one useful validation alert');
              h.assert(await input.inputValue() === invalidValue, 'Validation discarded the typed date');
              h.assert(await input.evaluate(element => element === element.ownerDocument.activeElement),
                'Validation did not focus the invalid date');
              h.assert(page.url() === originalUrl && posts === 0, 'Invalid-date validation submitted the form');
              h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '0',
                'Invalid-date validation wrote a form record');
            }
            await input.fill(field.value);
          }
          const cancelled = await h.withExpectedDialogs(page,
            () => page.getByRole('button', { name: 'Save and Exit', exact: true }).first().click(), { accept: false });
          h.assert(cancelled.length === 1 && cancelled[0].type === 'confirm',
            'Save and Exit must ask for one cancellable confirmation');
          h.assert(page.url() === originalUrl && posts === 0 && !page.isClosed(),
            'Cancelling Save and Exit submitted or closed the form');
          h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '0',
            'Cancelling Save and Exit wrote a form record');
          for (const field of dates) h.assert(await fieldValue(page, field) === field.value,
            'Cancelling Save and Exit discarded a typed date');
          h.assert(await page.locator(`[name="${form.prose}"]`).inputValue() === entry.text,
            'Cancelling Save and Exit discarded the typed prose');
          h.assertStrictPage(s.recorder, labels(form));
        } finally {
          page.off('request', countPost);
        }
      }, ['open']);
    }

    await attempt(entry, 'save', 'Save stores exactly the typed prose and structured fields for the signed-in provider', async () => {
      const { page } = entry;
      if (form.key === 'VT2') await prepareVascularTracker(page, entry.text);
      await claims.asPrecondition(async () => {
        await page.locator(`[name="${form.prose}"]`).first().fill(entry.text);
        for (const field of form.fields) {
          const input = page.locator(`[name="${field.name}"]`).first();
          if (field.check) await input.check(); else await input.fill(field.value);
        }
      }, 'typing into the form');
      // Forms that share a table (Annual V2) are counted from where this row started.
      entry.before = Number(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`));
      const savePath = form.savePath || '/form/formname';
      const posted = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(savePath));
      // A form whose id is not in the URL has no URL to wait for: wait for the navigation the Save starts. The load state of
      // the page it is already on would resolve at once, and the redisplay concern would then judge the old document.
      const landed = form.idInUrl === false
        ? page.waitForEvent('framenavigated', { predicate: frame => frame === page.mainFrame() })
          .then(() => page.waitForLoadState('domcontentloaded'))
        : page.waitForURL(url => url.pathname.endsWith('/form/forwardname'), { waitUntil: 'domcontentloaded' });
      // Observed below; a failure raised first must not surface later as an unhandled rejection.
      posted.catch(() => {});
      landed.catch(() => {});
      // The confirmation is asserted after the save has been proven, so a missing prompt does
      // not hide whether the form still saves.
      entry.dialogs = await h.withExpectedDialogs(page, () => page.getByRole('button', { name: form.saveButton || 'Save', exact: true }).first().click());
      const status = (await posted).status();
      h.assert(form.idInUrl === false ? status < 400 : status === 302, `The save was answered ${status}, not accepted`);
      await landed;
      const columns = [form.idColumn, column(form.prose), ...form.fields.map(field => column(field.name)), form.provider ? 'provider_no' : "''"];
      await expectValue(sql, `SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`, String(entry.before + 1),
        'Save did not store exactly one new row');
      const [row] = sql.rows(`SELECT ${columns.join(',')} FROM ${form.table} WHERE demographic_no=${patient}
        ORDER BY ${form.idColumn} DESC LIMIT 1`);
      h.assert(row[1] === entry.text, 'The stored prose differs from what was typed');
      form.fields.forEach((field, i) => h.assert(row[2 + i] === field.stored, `The stored ${field.name} differs from what was set`));
      if (form.provider) h.assert(row[row.length - 1] === provider, 'The row is not attributed to the signed-in provider');
      entry.id = row[0];
      if (form.key === 'VT2') assertVascularTrackerStored(sql, patient, entry.id, entry.text);
      if (form.idInUrl !== false) h.assert(new URL(page.url()).searchParams.get('formId') === entry.id, 'Save redisplayed another record');
    }, ['open']);

    await attempt(entry, 'redisplay', 'the redisplayed form shows the saved values', async () => {
      if (form.idInUrl === false) await h.assertNotErrorPage(entry.page, 'The redisplayed form');
      await assertShown(entry.page, form, entry.text, 'The redisplayed form');
    }, ['save']);

    if (form.chain) await mentalHealthChain(entry);

    if (form.printOn === 'saved' && form.print) {
      await attempt(entry, 'print', 'Print renders the saved page for the owned patient', () => pressPrint(entry, entry.page), ['chain'], [`print-${form.key}`, `form-${form.key}`]);
    }

    // A form that shares its table with the next row is reopened now, while its record is the table's latest.
    if (form.reopenNow) {
      const now = await s.context.newPage();
      h.wireStrictPage(now, 'reopen-chart', s.recorder);
      await now.goto(chart.url(), { waitUntil: 'domcontentloaded' });
      await waitForNavbars(now, 20000);
      await reopen(entry, now);
      await now.close();
    }
  }

  /** Referral is saved; open Assessment and then Outcome from the page's own links and Save each. */
  async function mentalHealthChain(entry) {
    const { form } = entry;
    // The Assessment page reloaded its opener on Save (window.opener.location.reload(), #4600), and the opener is the
    // E-Chart. Whether the chart stayed put is the `opener` concern, judged from what the chart did while the pages were saved.
    entry.chartReloads = 0;
    // Each page prints its own comments, so the print concern needs the text typed on each page.
    entry.pageTexts = { Referral: entry.text };
    entry.chartMark = markProblems(s.recorder);
    const reloaded = frame => { if (frame === chart.mainFrame()) entry.chartReloads++; };
    chart.on('framenavigated', reloaded);
    await attempt(entry, 'chain', 'Assessment and Outcome save into the same record and each Save keeps the earlier pages', async () => {
      const { page } = entry;
      let expected = entry.before + 1;
      const carried = [[form.prose, entry.text]];
      for (const next of form.chain) {
        await claims.asPrecondition(() => page.getByRole('link', { name: next.link, exact: true }).first().click(), `the ${next.link} link`);
        await page.waitForURL(url => url.pathname.endsWith(`/form/formmh${next.link.toLowerCase()}`), { waitUntil: 'domcontentloaded' });
        const text = `${marker} ${next.link} ${PROSE}`;
        await claims.asPrecondition(async () => {
          await page.locator(`[name="${next.prose}"]`).first().waitFor({ state: 'visible' });
          await page.locator(`[name="${next.prose}"]`).first().fill(text);
        }, `typing into the ${next.link} page`);
        const posted = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/formname'));
        const landed = page.waitForURL(url => url.pathname.endsWith('/form/forwardname'), { waitUntil: 'domcontentloaded' });
        posted.catch(() => {});
        landed.catch(() => {});
        await h.withExpectedDialogs(page, () => page.getByRole('button', { name: 'Save', exact: true }).first().click());
        h.assert((await posted).status() === 302, `Saving the ${next.link} page was not accepted`);
        await landed;
        expected += 1;
        await expectValue(sql, `SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`, String(expected),
          `Saving the ${next.link} page did not file exactly one new row`);
        carried.push([next.prose, text]);
        entry.pageTexts[next.link] = text;
        const kept = form.fields.map(field => column(field.name));
        const [row] = sql.rows(`SELECT ${form.idColumn},${[...carried.map(([name]) => name), ...kept].join(',')} FROM ${form.table}
          WHERE demographic_no=${patient} ORDER BY ${form.idColumn} DESC LIMIT 1`);
        carried.forEach(([name, value], i) => h.assert(row[1 + i] === value,
          `After the ${next.link} Save the stored ${name} differs from what was typed`));
        // A field the page only shows, read-only, is posted back by its Save: a page that cut it short stores the cut value.
        form.fields.forEach((field, i) => h.assert(row[1 + carried.length + i] === field.stored,
          `After the ${next.link} Save the stored ${field.name} differs from what was set`));
        for (const field of form.fields) {
          h.assert(await fieldValue(page, field) === field.value, `The redisplayed ${next.link} page does not show the saved ${field.name}`);
        }
        entry.id = row[0];
        h.assert(new URL(page.url()).searchParams.get('formId') === entry.id, `Saving the ${next.link} page redisplayed another record`);
        h.assert(await page.locator(`[name="${next.prose}"]`).first().inputValue() === text,
          `The redisplayed ${next.link} page does not show the saved ${next.prose}`);
      }
      entry.text = carried[carried.length - 1][1];
    }, ['redisplay']);
    chart.off('framenavigated', reloaded);
    await attempt(entry, 'opener', 'Saving a page leaves the E-Chart that opened the form alone', async () => {
      // The reload's note-lock beacon is reported a moment after the navigation it belongs to.
      // Waiting for the chart to settle is not the test; only the reload count below is.
      await claims.asPrecondition(async () => {
        await chart.waitForLoadState('load');
        await new Promise(resolve => setTimeout(resolve, 1000));
        if (entry.chartReloads) await waitForNavbars(chart, 20000);
      }, 'the E-Chart settling after the Saves');
      h.assert(!entry.chartReloads, `Saving a page reloaded the E-Chart that opened the form (${entry.chartReloads} reload${entry.chartReloads === 1 ? '' : 's'} during the three Saves)`);
    }, ['redisplay'], ['echart'], entry.chartMark);
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
          label: `reopen-${entry.form.key}`, timeout: attempt < 3 ? 8000 : 20000, position: { x: 8, y: 9 } });
      } catch (error) {
        last = error;
        if (!/locator\.(click|waitFor)|detached|not stable/.test(error.message)) throw error;
      }
    }
    throw last;
  }

  // Reopen from a fresh E-Chart, whose navbar lists each saved form as a forwardshortcutname entry.
  // A new page rather than chart.reload(): leaving the encounter fires an unload beacon that the
  // strict recorder reports as an aborted request. In foldSavedForms mode the saved entry is beyond the
  // navbar's first page, so the reopen also proves the folded list expands and reopens the same record.
  async function reopen(entry, fresh) {
    const { form } = entry;
    let page;
    setSex(form.sex || 'F');
    // A multi-page form's reopen shows its last page's text, so it is not judged (not reached) when the chain did not pass.
    await attempt(entry, 'reopen', 'reopening from the E-Chart restores the saved values', async () => {
      const link = fresh.locator(`#leftNavBar a[onclick*="formname=${entry.name}&"], #rightNavBar a[onclick*="formname=${entry.name}&"]`).first();
      if (foldSavedForms) h.assert(!await link.isVisible(),
        'The folded-navigation fixture did not put the saved form beyond the first page');
      page = await claims.reaching(() => openSavedEntry(fresh, entry));
      const params = new URL(page.url()).searchParams;
      // VT uses the setup route and explicit hidden patient/record fields after its internal forward.
      if (form.idInUrl !== false) h.assert(params.get('demographic_no') === patient, 'The saved-form entry opened another patient');
      if (form.idInUrl !== false) h.assert(params.get('formId') === entry.id, 'The saved-form entry did not open the saved record');
      // After a multi-page form's last Save the chart opens its last page, so that page's text is what must be there.
      await assertShown(page, form, entry.text, 'The reopened form', lastProse(form));
      if (form.key === 'VT2') {
        h.assert(await page.locator('input[name="demographic_no"]').inputValue() === patient,
          'VT reopened for another patient');
        h.assert(await page.locator('input[name="value(formId)"]').inputValue() === entry.id,
          'VT did not reopen the saved record');
        const before = sql.rows(`SELECT * FROM formVTForm WHERE ID=${entry.id} AND demographic_no=${patient}`);
        const count = Number(sql.value(`SELECT COUNT(*) FROM formVTForm WHERE demographic_no=${patient}`));
        const posted = page.waitForResponse(response => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/form/SubmitForm'));
        const closed = page.waitForEvent('close');
        const refreshed = fresh.waitForResponse(response => new URL(response.url()).pathname.endsWith('/encounter/displayForms'));
        for (const pending of [posted, closed, refreshed]) pending.catch(() => {});
        const dialogs = await h.withExpectedDialogs(page, () => page.locator('input[type="submit"][onclick*="onSaveExit();"]').first().click()
          .catch(error => { if (!page.isClosed()) throw error; }));
        h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'VT Save & Exit must ask for confirmation once');
        const response = await posted;
        h.assert(response.status() === 200, 'VT Save & Exit was not accepted');
        const submitted = new URLSearchParams(response.request().postData() || '');
        h.assert(submitted.get('submit') === 'exit' && submitted.get('demographic_no') === patient
          && submitted.get('value(formId)') === entry.id, 'VT Save & Exit did not submit its own patient and record');
        await closed;
        h.assert((await refreshed).status() === 200, 'VT completion did not refresh its own Forms module');
        await expectValue(sql, `SELECT COUNT(*) FROM formVTForm WHERE demographic_no=${patient}`, String(count + 1),
          'VT Save & Exit did not create exactly one form revision');
        h.assert(JSON.stringify(sql.rows(`SELECT * FROM formVTForm WHERE ID=${entry.id} AND demographic_no=${patient}`)) === JSON.stringify(before),
          'VT Save & Exit overwrote the previous form revision');
        entry.id = sql.value(`SELECT MAX(ID) FROM formVTForm WHERE demographic_no=${patient}`);
        assertVascularTrackerStored(sql, patient, entry.id, entry.text);
        const readings = sql.rows(`SELECT dataField,DATE(dateObserved),comments FROM measurements
          WHERE demographicNo=${patient} AND type='iDia'`);
        h.assert(JSON.stringify(readings) === JSON.stringify([['yes', DATE.stored, `${entry.text} eye`]]),
          'VT Save & Exit did not file the owned eye measurement exactly once');
        page = await openSavedEntry(fresh, entry);
        h.assert(await page.locator('input[name="value(formId)"]').inputValue() === entry.id,
          'VT reopened an older record after Save & Exit');
        await assertShown(page, form, entry.text, 'VT reopened after Save & Exit');
      }
      if (!foldSavedForms) return;
      // A previously loaded and collapsed saved-form list must expand and reopen the same record.
      await page.close();
      await fresh.locator('#forms img[src$="/collapse.gif"]:visible').first().click();
      const cached = fresh.locator(`#forms a[onclick*="formname=${entry.name}&"]`).first();
      h.assert(await cached.count() > 0 && !await cached.isVisible(),
        'The cached-list fixture did not retain a hidden saved-form anchor');
      await revealSavedForm(fresh, cached);
      page = await ui.clickOpensPopup(fresh, cached, { context: s.context, recorder: s.recorder,
        label: `reopen-${form.key}`, timeout: 20000, position: { x: 8, y: 9 } });
      const again = new URL(page.url()).searchParams;
      h.assert(again.get('demographic_no') === patient && again.get('formId') === entry.id,
        'Expanding the cached list opened a different patient or saved record');
      await assertShown(page, form, entry.text, 'The reopened form from the cached list');
    }, ['save', 'chain'], [`reopen-${form.key}`]);
    entry.reopened = page;
  }

  /**
   * Press Print on a page whose button posts the page into a window of its own, and judge that window: it shows the
   * print view on `pathname`, carrying `text` and every structured value of the form exactly as it was typed.
   */
  async function printPopup(entry, page, buttonName, pathname, text) {
    const { form } = entry;
    const button = await claims.asPrecondition(async () => {
      const found = page.getByRole('button', { name: buttonName, exact: true }).first();
      await found.waitFor({ state: 'visible', timeout: 10000 });
      return found;
    }, `the ${buttonName} button`);
    const opened = [];
    const onPage = popup => { h.wireStrictPage(popup, `print-${form.key}`, s.recorder); opened.push(popup); };
    s.context.on('page', onPage);
    try {
      await h.withExpectedDialogs(page, () => button.click({ noWaitAfter: true }));
      const deadline = Date.now() + 20000;
      const printed = () => opened.find(popup => !popup.isClosed() && popup.url().startsWith('http') && new URL(popup.url()).pathname.endsWith(pathname));
      while (!printed() && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 150));
      const print = printed();
      h.assert(print, `${buttonName} opened no window on ${pathname}`);
      await print.waitForLoadState('domcontentloaded');
      await h.assertNotErrorPage(print, `The print window on ${pathname}`);
      const shown = await print.locator('body').innerText();
      h.assert(shown.includes(text), `The print window on ${pathname} does not show the saved text`);
      for (const field of form.fields.filter(item => !item.check)) {
        h.assert(shown.includes(field.value), `The print window on ${pathname} does not show the saved ${field.name} as typed`);
      }
    } finally {
      s.context.off('page', onPage);
      for (const popup of opened) if (!popup.isClosed()) await popup.close().catch(() => {});
    }
  }

  /** Press Print on the form and judge what comes back; see FORMS for the four kinds. */
  async function pressPrint(entry, page) {
    const { form } = entry;
    const spec = form.print;
    const button = await claims.asPrecondition(async () => {
      const found = page.getByRole('button', { name: spec.button, exact: true }).first();
      await found.waitFor({ state: 'visible', timeout: 10000 });
      return found;
    }, `the ${spec.button} button`);
    if (spec.kind === 'window') {
      await page.evaluate(() => {
        window.__prints = 0;
        window.print = () => { window.__prints++; };
      });
      await h.withExpectedDialogs(page, () => button.click());
      h.assert(await page.evaluate(() => window.__prints) === 1, `${spec.button} did not call the browser print exactly once`);
      return;
    }
    if (spec.kind === 'popup') {
      // A multi-page form prints each page from that page, reached by its own link; the page Print was found on comes last.
      const pages = spec.pages || [{ pathname: spec.pathname }];
      for (const target of pages) {
        if (target.page && !new URL(page.url()).pathname.endsWith(target.page)) {
          await claims.asPrecondition(() => page.getByRole('link', { name: target.link, exact: true }).first().click(), `the ${target.link} link`);
          await page.waitForURL(url => url.pathname.endsWith(target.page), { waitUntil: 'domcontentloaded' });
        }
        await printPopup(entry, page, spec.button, target.pathname, target.link ? entry.pageTexts[target.link] : entry.text);
      }
      return;
    }
    if (spec.kind === 'page') {
      const print = await s.popup(page, button, `print-${form.key}`);
      const printUrl = new URL(print.url());
      h.assert(printUrl.pathname.endsWith('/form/formannualfemaleprint'), 'Print did not use the gated form route');
      h.assert(printUrl.searchParams.get('demographic_no') === patient, 'Print opened another patient');
      h.assert(printUrl.searchParams.get('formId') === entry.id, 'Print opened another saved record');
      h.assert(await print.locator('[name="ID"]').inputValue() === entry.id, 'Print rendered another saved record');
      h.assert((await print.locator('body').innerText()).includes(DATE.value), 'Print omitted the saved review date');
      h.assert(await print.locator('follow-up').count() === 0, 'Print treated clinical prose as HTML');
      h.assert((await print.locator('body').innerText()).includes(entry.text), 'Print does not show the saved prose');
      await print.close();
      return;
    }
    // A PDF navigation hands Playwright the viewer's HTML, so the bytes are read in transit.
    let body;
    await page.route('**/form/formname?*', async route => {
      const answer = await route.fetch();
      body = await answer.body();
      await route.fulfill({ response: answer, body });
    });
    const answered = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/formname'));
    await button.click({ noWaitAfter: true });
    const response = await answered;
    await page.unroute('**/form/formname?*');
    h.assert(response.status() === 200 && /application\/pdf/.test(response.headers()['content-type'] || ''),
      'Print did not answer a PDF');
    h.assert(body.subarray(0, 4).toString() === '%PDF', 'Print answered something other than a PDF document');
    // The whole prose, not just its marker prefix, so a dropped or truncated tail fails. pdfText yields one line per
    // PDF string literal and a wrapped cell splits across literals, so whitespace is compared away on both sides.
    const squash = value => value.replace(/\s+/g, '');
    h.assert(squash(pdfText(body)).includes(squash(entry.text)), 'The PDF does not carry the whole saved prose');
  }

  const fresh = await s.context.newPage();
  h.wireStrictPage(fresh, 'reopen-chart', s.recorder);
  await fresh.goto(chart.url(), { waitUntil: 'domcontentloaded' });
  await waitForNavbars(fresh, 20000);
  for (const entry of results) {
    const { form } = entry;
    if (form.openOnly) {
      if (entry.page && !entry.page.isClosed()) await entry.page.close();
      continue;
    }
    if (!entry.reopened && !form.reopenNow) await reopen(entry, fresh);
    const page = entry.reopened;

    if (form.print && form.printOn !== 'saved') {
      await attempt(entry, 'print', form.print.kind === 'pdf' ? 'Print Pdf answers a PDF carrying the saved prose'
        : form.print.kind === 'window' ? 'Print calls the browser print once'
          : 'Print Page renders the saved form for the owned patient', () => pressPrint(entry, page), ['reopen'], [`print-${form.key}`]);
    }
    if (page && !page.isClosed()) await page.close();

    await attempt(entry, 'dialogs', form.confirm ? 'Save asked for confirmation exactly once' : 'Save raised no dialog', async () => {
      h.assert(entry.dialogs.length === (form.confirm ? 1 : 0),
        form.confirm ? 'Save did not ask for confirmation exactly once' : 'Save raised a dialog');
    }, ['save']);

    if (form.resave) {
      // A clinician keeps working in the window Save redisplayed; its next Save must be accepted.
      // Encounter forms are versioned: every Save inserts a new row (FrmRecordHelp.saveFormRecord)
      // and the chart reopens formId=latest, so the revision is the second row, not an overwrite.
      await attempt(entry, 'resave', 'a second Save from the redisplayed form is accepted and files the revision as the next version', async () => {
        h.assert(await entry.page.locator('script[src$="/csrfguard"]').count() === 1,
          'The redisplayed form must load CSRFGuard exactly once');
        await entry.page.waitForFunction(() => {
          const token = document.querySelector('form input[name="CSRF-TOKEN"]');
          return token && token.value.length > 0;
        });
        const revised = `${entry.text} (revised)`;
        await entry.page.locator(`[name="${form.prose}"]`).first().fill(revised);
        const posted = entry.page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/formname'));
        posted.catch(() => {});
        await h.withExpectedDialogs(entry.page, () => entry.page.getByRole('button', { name: 'Save', exact: true }).first().click());
        const response = await posted;
        h.assert(response.status() === 302, 'The second save was refused');
        await expectValue(sql, `SELECT ${form.prose} FROM ${form.table} WHERE demographic_no=${patient}
          ORDER BY ${form.idColumn} DESC LIMIT 1`, revised, 'The second save did not store the revision');
        h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '2',
          'The second save did not file exactly one new version');
        h.assert(sql.value(`SELECT ${form.prose} FROM ${form.table} WHERE demographic_no=${patient}
          AND ${form.idColumn}=${entry.id}`) === entry.text, 'The second save altered the first saved version');
        const columns = [form.idColumn, ...form.fields.map(field => field.name), 'provider_no'];
        const [revision] = sql.rows(`SELECT ${columns.join(',')} FROM ${form.table}
          WHERE demographic_no=${patient} ORDER BY ${form.idColumn} DESC LIMIT 1`);
        const latest = revision[0];
        form.fields.forEach((field, i) => h.assert(revision[i + 1] === field.stored,
          `The revision did not preserve the stored ${field.name}`));
        h.assert(revision[revision.length - 1] === provider,
          'The revision is not attributed to the signed-in provider');
        h.assert(latest !== entry.id, 'The revision did not get its own record ID');
        await entry.page.waitForURL(url => url.pathname.endsWith('/form/forwardname')
          && url.searchParams.get('formId') === latest, { waitUntil: 'domcontentloaded' });
        await assertShown(entry.page, form, revised, 'The redisplayed revision');
        const revisedChart = await s.context.newPage();
        h.wireStrictPage(revisedChart, 'revision-chart', s.recorder);
        await revisedChart.goto(chart.url(), { waitUntil: 'domcontentloaded' });
        await waitForNavbars(revisedChart, 20000);
        const saved = revisedChart.locator(`#forms a[onclick*="formname=${entry.name}&"]`).first();
        await revealSavedForm(revisedChart, saved);
        const reopened = await ui.clickOpensPopup(revisedChart, saved, {
          context: s.context, recorder: s.recorder, label: `reopen-${form.key}`,
          timeout: 20000, position: { x: 8, y: 9 },
        });
        const params = new URL(reopened.url()).searchParams;
        h.assert(params.get('demographic_no') === patient && params.get('formId') === latest,
          'The saved-form entry did not reopen the latest version for the owned patient');
        await assertShown(reopened, form, revised, 'The reopened revision');
        await reopened.close();
        await revisedChart.close();
      }, ['redisplay', 'reopen']);
    }
    if (entry.page && !entry.page.isClosed()) await entry.page.close();
  }
  for (const entry of results) {
    // The problems the form's pages raised belong to the form, so they are taken out of the recorder here and
    // reported with it; what remains (the chart's own pages) is judged by runWorkflow at the end.
    const problems = [...new Set(takeProblems(s.recorder, labels(entry.form)))];
    const outcome = problems.length ? { ...claims.outcomeOf(null, problems), message: `The browser reported ${problems.length} JavaScript-layer problem(s): ${problems.join(' | ')}` } : { ok: true };
    entry.results.problems = outcome;
    console.log(`  ${outcome.ok ? 'PASS' : 'FAIL'} ${NAME}: ${stepLabel(entry.form, 'problems', 'raised no JavaScript-layer problems')}${outcome.ok ? '' : ` -- ${outcome.message}`}`);
  }
  await fresh.close();

  // Assert the claimed pairs, in table order, one labelled step each.
  for (const entry of results) {
    for (const concern of entry.form.concerns.filter(name => wanted.has(claims.claimKey(entry.form.claim, name)))) {
      const label = labelFor(entry.form, concern);
      // Only the pair's own failure carries the pinned label; a precondition, a concern that was not reached and
      // browser problems beyond the known one are reported under a label of their own (lib/form-claims.js).
      const failure = claims.claimFailure(entry.results[concern], label, entry.form.known && entry.form.known[concern]);
      if (failure) throw h.markFailedStep(new Error(failure.message), failure.label);
      console.log(`  ASSERTED ${NAME}: ${label}`);
    }
  }
}

/** The step label of a (form, concern) pair: what the console prints and a manifest pins. */
const labelFor = (form, concern) => stepLabel(form, concern, concernLabel(form, concern));

/** The attempt label a concern was recorded under (the console and the manifest read the same text). */
function concernLabel(form, concern) {
  return {
    open: 'is listed once in the Forms menu and opens for the owned patient',
    validate: form.key === 'DS' ? 'invalid dates and cancelled confirmations prevent writes without JavaScript errors'
      : 'each real date is validated and cancelling Save and Exit preserves the unsaved form',
    save: 'Save stores exactly the typed prose and structured fields for the signed-in provider',
    redisplay: 'the redisplayed form shows the saved values',
    chain: 'Assessment and Outcome save into the same record and each Save keeps the earlier pages',
    opener: 'Saving a page leaves the E-Chart that opened the form alone',
    reopen: 'reopening from the E-Chart restores the saved values',
    print: form.printOn === 'saved' ? 'Print renders the saved page for the owned patient'
      : form.print && form.print.kind === 'pdf' ? 'Print Pdf answers a PDF carrying the saved prose'
        : form.print && form.print.kind === 'window' ? 'Print calls the browser print once'
          : 'Print Page renders the saved form for the owned patient',
    dialogs: form.confirm ? 'Save asked for confirmation exactly once' : 'Save raised no dialog',
    resave: 'a second Save from the redisplayed form is accepted and files the revision as the next version',
    problems: 'raised no JavaScript-layer problems',
  }[concern];
}

// What scripts/form-claims.test.js and scripts/playwright-pin-validation.test.js read; the tail of this file is
// evaluated alone by scripts/pdf-preflight.test.js, so it names as few globals as it can.
const testing = { validatePin, claimForms: CLAIM_FORMS, labelFor, PINNED, requireVascularDictionaryReady,
  generatedLabel: (form, concern) => `${form.title}: ${concernLabel(form, concern)}` };
if (require.main === module) runWorkflow(NAME, workflow, {
  preflight: ({ sql } = {}) => {
    require('./lib/export-content-helpers').requirePoppler('pdftotext');
    const selected = new Set(validatePin());
    if (FORMS.some(form => (form.key === 'VT' || form.key === 'VT2')
      && form.concerns.some(concern => selected.has(claims.claimKey(form.claim, concern))))) {
      requireVascularDictionaryReady(sql);
    }
  },
});
module.exports = { workflow, FORMS, ...testing };
