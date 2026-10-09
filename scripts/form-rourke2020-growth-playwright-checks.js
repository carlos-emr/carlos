#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan Phase 3 (Ontario): the Rourke 2020 well-baby record and the two growth charts. User path:
// Schedule > Search > Master Record > E-Chart > Forms menu > <form> > type (and, on Rourke, import weights and
// lengths through the measurement dialog) > Save (form/formname) > the saved record redisplayed in the same
// window > a fresh E-Chart's saved-form entry > the graph links and Print.
//
// Three forms, one owned FAKE infant (an 18-month-old girl, so every Rourke visit date, up to the 18-month one,
// is in the past):
//   Rourke 2020   formRourke2020 (+ form_boolean_value for its radio buttons): four pages behind jQuery tabs,
//                 a "Graph Length and Weight" and a "Graph Head Circumference" link, Print = a four-page Jasper PDF;
//   Growth 0-36m  formGrowth0_36 ("CDC US Growth Charts" in its title): ten rows of date, age, weight, length,
//                 head circumference; Print Growth, Head Circ(1) and Head Circ(2) answer WHO chart PDFs;
//   Growth Chart  formGrowthChart ("WHO Growth Charts", 2 to 20 years): rows of date, age, stature, weight, BMI;
//                 Print Growth and Print BMI answer WHO chart PDFs. (The form takes any typed ages, so the infant
//                 is used for it too: what is under test is the form's mechanics, not the clinical sense.)
//
// "THE GRAPH". The brief asked for a non-empty PNG; the application answers every graph as a PDF (FrmPDFServlet lays
// the typed values over a WHO template, one stroked circle per point). So a graph is judged three ways, each from
// the bytes in transit: it is a PDF (%PDF), its page overlay holds at least as many circles as there are typed
// points the chart can place (lib/pdf-graph.js plottedPoints, which reads the content streams, so a chart with
// nothing plotted fails although it is a perfectly valid PDF), and the page draws as a non-trivial PNG (pdftoppm).
//
// Each form has up to twenty CONCERNS, asserted one labelled step each:
//   open        the Forms-menu entry opens with no error page, uncaught script error, console error or failed asset;
//   keys        the page shows no unresolved message key (the ???key??? text a missing bundle entry prints);
//   title       (Growth 0-36) the window is not titled for the CDC charts the form does not draw (finding 253);
//   measurements   (Rourke) the weights and lengths saved in the measurement dialog are stored in `measurements` and
//                  imported into the form;
//   headcirc       (Rourke) head circumference has a measurement dialog too;
//   measuredate    (Rourke) the dialog files the local date of the observation by default (finding 152);
//   save        Save stores one new row holding every typed value and the saving provider;
//   redisplay   the window Save was pressed in redisplays the saved record (the right record, not an error page);
//   reopen      a fresh chart's saved-form entry opens the saved record;
//   restore     the typed values are shown again on the redisplayed form and on the reopened one (Rourke: on every page);
//   graph       the graph PDFs have their pages, plot every typed point and render every page as a PNG (Rourke: both graph
//               links; Growth: the chart prints);
//   graphmeasure   (Rourke) weights and lengths that exist only in `measurements` are plotted too;
//   graphgrowth    (Rourke) the Growth 0-36 rows are plotted on the Rourke graphs ("Rourke will graph input here");
//   graphtitle     (Rourke) the two Graph Length and Weight links open PDFs with the same, length-and-weight title (252);
//   print       Print produces a PDF that carries the typed text (Rourke: four pages; every typed number and text);
//   printnull, printsex, printgestation, printnotes   (Rourke) the printed record leaves an empty visit date blank,
//                  marks the patient's sex (in the box the template places it in) and only that, prints the gestational age
//                  the dates give (read from the box the template places it in), and carries the notes typed in the seven
//                  page II boxes the template declares a parameter for and never places; printbmi (Growth Chart) Print BMI
//                  is a PDF;
//   printdob    (Growth forms) the printed chart carries the patient's date of birth;
//   sweep, storage (Rourke) a second record with a distinct value in every other text box and every radio button and
//                  checkbox ticked: each stored value is in its column, the ticks are stored and shown again (sweep),
//                  and no box of the page is one the record cannot hold (storage).
//
// KNOWN FAILURES AND CLAIMS. Every run executes the flow of every selected form and records every concern (lib/form-claims.js
// outcomeOf); the entry then asserts, in table order, the pairs it CLAIMS. ROURKE_GROWTH_ONLY and ROURKE_GROWTH_EXCEPT
// (`<form>` or `<form>.<concern>`) choose them, so a broken pair gets its own manifest entry pinned on its own finding,
// the default entry leaves that pair out, and scripts/form-claims.test.js proves the entries together claim every
// pair once. Only the pair's OWN failure keeps the pinned step label (lib/form-claims.js claimFailure): a concern that
// depends on another is blocked ("not reached") when the other fails, a control that cannot be found or clicked is a
// precondition, and a browser problem a concern did not cause is not absorbed by a concern that did no browser work,
// so none of them reads as the known defect. Finding 251 (Save and Print take 7 and 11 seconds) is deliberately
// not pinned: the timings are unstable and no budget is agreed, so there is no step that could assert it.
//
// Fixtures: the owned synthetic patient (given a complete demographic record and an infant's date of birth), one
// marker-named Forms-menu registration per form (the shipped rows are hidden on Ontario installs and are clinic-wide,
// so run EXCLUSIVE=1), and, for the graph concerns, measurement rows the check inserts for the owned patient. Cleanup
// deletes every form row (and Rourke's form_boolean_value rows), measurement and registration and asserts all of it.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const claims = require('./lib/form-claims');
const pdfGraph = require('./lib/pdf-graph');
const { takeProblems } = require('./lib/form-problems');
const { captureRequest } = require('./lib/get-reject-probe');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { requirePoppler, pdfTextBuffer, squash } = require('./lib/export-content-helpers');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { revealSavedForm } = require('./form-catalog-smoke-playwright-checks');

const NAME = 'form-rourke2020-growth';
const ONLY = 'ROURKE_GROWTH_ONLY';
const EXCEPT = 'ROURKE_GROWTH_EXCEPT';

/** The infant's age in days. 560 puts the 18-month visit (548 days) twelve days ago. */
const INFANT_DAYS = 560;
/** A rendered chart page at 40 dpi is about 60 KB; an empty page is a few KB. */
const MIN_PNG_BYTES = 15000;

/*
 * THE BROWSER'S TIME ZONE IS A TEST INPUT. Finding 152: the measurement dialog files `new Date().toISOString()`'s date,
 * which is the UTC date, so it differs from the clinician's date for part of every day wherever the clinic is not on UTC
 * (Ontario: after 20:00). The packaged install runs on UTC, so a default browser could never show it. This picks a
 * zone whose calendar date differs from the UTC date NOW (Kiritimati is UTC+14: ahead of UTC from 10:00 UTC; Pago Pago
 * is UTC-11: behind it until 11:00 UTC), so the step is deterministic at every hour and fails only while the defect stands.
 */
function zoneWhereTodayDiffersFromUtc(now = new Date()) {
  return now.getUTCHours() >= 10 ? 'Pacific/Kiritimati' : 'Pacific/Pago_Pago';
}
/** The calendar date (YYYY-MM-DD) in a zone. */
function localDate(zone, now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/** The calendar date (YYYY-MM-DD) in UTC, which is what the measurement dialog files. */
function utcDate(now = new Date()) {
  return now.toISOString().slice(0, 10);
}

/**
 * One zone for the whole run: the context is created with it and the measuredate step judges against it. It is chosen when
 * the module loads, so the measurement-date pair checks that the zone's date still differs from UTC's when it runs (an early
 * precondition step, and again when the default-date measurement is imported): a run that crosses UTC midnight cannot judge.
 */
const RUN_ZONE = zoneWhereTodayDiffersFromUtc();

/** The sex of the owned infant (the fixture UPDATE below), and so the box the printed record must mark. */
const PATIENT_SEX = 'F';

const pad = n => String(n).padStart(2, '0');
/** A calendar day as {y, m, d}, moved by whole days with UTC arithmetic, so no zone or daylight saving can shift it. */
function day(from, days = 0) {
  const t = new Date(Date.UTC(from.y, from.m - 1, from.d + days));
  return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate() };
}
const dmy = x => `${pad(x.d)}/${pad(x.m)}/${x.y}`;
const ymdSlash = x => `${x.y}/${pad(x.m)}/${pad(x.d)}`;
const iso = x => `${x.y}-${pad(x.m)}-${pad(x.d)}`;

// ---- what is typed -------------------------------------------------------------------------------------------------
// kind: text/area (filled), date (the field is read-only: its value is set, as the calendar does), radio (checked),
// measure (typed through the measurement dialog on Rourke; type is the dialog's measurement type, observed its date).
const text = (name, page, value) => ({ kind: 'text', name, page, value });
const area = (name, page, value) => ({ kind: 'text', name, page, value });
const date = (name, page, value, column) => ({ kind: 'date', name, page, value, column });
/** The column a field is stored in: its own name unless the form names it otherwise (start of pregnancy). */
const colOf = field => field.column || field.name;
const radio = (name, page) => ({ kind: 'radio', name, page });
const measure = (name, page, type, value, observed) => ({ kind: 'measure', name, page, type, value, observed });

/** Rourke 2020. Dates are visit dates after the infant's birth; every weight and length that has a dialog is imported. */
function rourkeFields(marker, dob) {
  const at = days => day(dob, days);
  return [
    area('c_birthRemarks', 0, `${marker} birth remarks`),
    // The start of pregnancy is exactly 40 weeks before the birth: the printed gestational age is judged against it.
    date('c_startOfGestation', 0, dmy(at(-280)), 'start_of_gestation'),
    text('c_length', 0, '50.5'), text('c_headCirc', 0, '34.5'),
    measure('c_birthWeight', 0, 'WT', '3.2', at(0)),
    date('p1_date1w', 0, dmy(at(7))),
    measure('p1_wt1w', 0, 'WT', '3.6', at(7)), measure('p1_ht1w', 0, 'HT', '52.1', at(7)), text('p1_hc1w', 0, '35.5'),
    date('p1_date2w', 0, dmy(at(14))),
    measure('p1_wt2w', 0, 'WT', '3.9', at(14)), measure('p1_ht2w', 0, 'HT', '53.6', at(14)), text('p1_hc2w', 0, '36.1'),
    area('p1_pConcern1w', 0, `${marker} concern 1w`), radio('p1_breastFeeding1wOk', 0),
    date('p2_date2m', 1, dmy(at(61))),
    measure('p2_wt2m', 1, 'WT', '5.1', at(61)), measure('p2_ht2m', 1, 'HT', '57.2', at(61)), text('p2_hc2m', 1, '38.5'),
    area('p2_pConcern2m', 1, `${marker} concern 2m`), radio('p2_breastFeeding2mOk', 1),
    // The seven note boxes of page II that page2.jrxml declares a parameter for and never places (the printnotes concern).
    area('p2_development2m', 1, `${marker} development 2m`), area('p2_development4m', 1, `${marker} development 4m`),
    area('p2_development6m', 1, `${marker} development 6m`), area('p2_physical2m', 1, `${marker} physical 2m`),
    area('p2_physical4m', 1, `${marker} physical 4m`), area('p2_physical6m', 1, `${marker} physical 6m`),
    area('p2_immunization6m', 1, `${marker} immunization 6m`),
    date('p3_date9m', 2, dmy(at(274))),
    text('p3_wt9m', 2, '8.2'), text('p3_ht9m', 2, '70.3'), text('p3_hc9m', 2, '43.1'),
    area('p3_pConcern9m', 2, `${marker} concern 9m`), radio('p3_breastFeeding9mOk', 2),
    date('p4_date18m', 3, dmy(at(548))),
    // No observed date: this one is imported with the dialog's default date (the measuredate concern judges it).
    measure('p4_wt18m', 3, 'WT', '10.2', null), text('p4_ht18m', 3, '80.1'), text('p4_hc18m', 3, '46.8'),
    area('p4_pConcern18m', 3, `${marker} concern 18m`), radio('p4_breastFeeding18mOk', 3),
  ];
}
/**
 * The notes the printnotes concern judges: the seven page II boxes whose parameters page2.jrxml declares and the page never
 * places (finding 248). The other 47 note boxes have no parameter at all, which may be the paper form's design, so they are
 * not asserted (the finding records them as an open question).
 */
const ROURKE_NOTES = ['p2_development2m', 'p2_development4m', 'p2_development6m', 'p2_physical2m', 'p2_physical4m', 'p2_physical6m',
  'p2_immunization6m'];
/**
 * Where the printed record's page I places the cells the printsex and printgestation concerns read, in the page's own
 * units (a 1700 x 2200 point page, so pdftotext -bbox reports the same numbers). Copied from page1.jrxml and proved against
 * it by scripts/form-rourke2020-growth.test.js, so a moved box fails a unit test and not a live run.
 */
const PRINT_PAGE = Object.freeze({ width: 1700, height: 2200 });
const PRINT_BOXES = Object.freeze({
  male: Object.freeze({ x: 970, y: 157, width: 20, height: 28 }),
  female: Object.freeze({ x: 1035, y: 157, width: 20, height: 28 }),
  gestationalAge: Object.freeze({ x: 245, y: 190, width: 90, height: 22 }),
});
/** Slack around a box for text whose glyphs ride a little outside the cell the template gives it. */
const BOX_SLACK = 6;
/** The cells the sweep leaves to the main record: the measurement cells and dates Save validates, and the patient's own identity fields. */
const MEASURE_OR_DATE = /^p\d_(ht|wt|hc|bmi)\d+[wm]$|^c_(length|headCirc|birthWeight|dischargeWeight|birthDate|pName|fsa|startOfGestation)$|^p\d_date|^CSRF/;
/** Typed points the Rourke graphs can place: (date, weight) and (date, length) pairs, and (length, weight) pairs. */
const ROURKE_LENGTH_WEIGHT_POINTS = 12;
const ROURKE_HEAD_POINTS = 12;
/** Pages of each PDF the application answers: the Length and Weight graph adds the 2 to 19 years chart, which an infant leaves empty. */
const ROURKE_LENGTH_WEIGHT_PAGES = 2;
const ROURKE_HEAD_PAGES = 1;
const ROURKE_PRINT_PAGES = 4;
const GROWTH_PRINT_PAGES = 1;

/** The rows of a Growth form: `cells` (text names, per row), at most `rows` of them, each with a comment. */
function rowFields(rows, cells) {
  return rows.flatMap(([n, values, comment]) => [
    date(`date_${n}`, 0, values.date),
    ...Object.entries(cells(values)).map(([name, value]) => text(`${name}_${n}`, 0, value)),
    text(`comment_${n}`, 0, comment),
  ]);
}
/** Growth Chart (2 to 20 years): rows 1 to 4 on the first block, row 8 on the second; ages are typed, as the form allows. */
function chartFields(marker, today) {
  const at = years => ymdSlash(day(today, -Math.round((7 - years) * 365.25)));
  const rows = [
    [1, { date: at(2.5), age: '2.5', stature: '92', weight: '13.5', bmi: '16' }, marker],
    [2, { date: at(4), age: '4', stature: '103.5', weight: '16.5', bmi: '15.5' }, 'c2'],
    [3, { date: at(5.5), age: '5.5', stature: '112', weight: '19', bmi: '15.1' }, 'c3'],
    [4, { date: at(6.5), age: '6.5', stature: '118', weight: '22', bmi: '15.8' }, 'c4'],
    [8, { date: at(6.9), age: '6.9', stature: '120', weight: '23', bmi: '16' }, `${marker}-8`],
  ];
  return [
    text('recordNo', 0, 'R-2026'), text('motherStature', 0, '165'), text('fatherStature', 0, '180'),
    ...rowFields(rows, v => ({ age: v.age, stature: v.stature, weight: v.weight, bmi: v.bmi })),
  ];
}
const CHART_POINTS = 10; // five rows in range, two points each
/** Growth 0-36 (birth to 24 months): rows 1 to 3 on the first block, row 6 on the second. */
function growthFields(marker, dob) {
  const at = days => ymdSlash(day(dob, days));
  const rows = [
    [1, { date: at(2), age: '0', weight: '3.3', length: '50', headCirc: '34.5' }, marker],
    [2, { date: at(30), age: '1', weight: '4.4', length: '54.8', headCirc: '37.2' }, 'c2'],
    [3, { date: at(120), age: '3.9', weight: '6.5', length: '61.5', headCirc: '40.8' }, 'c3'],
    [6, { date: at(365), age: '12', weight: '9.2', length: '74.5', headCirc: '45.5' }, `${marker}-6`],
  ];
  return [
    text('recordNo', 0, 'R-2026'), text('motherStature', 0, '165'), text('fatherStature', 0, '180'),
    text('gestationalAge', 0, '39'), text('edc', 0, ymdSlash(day(dob, 5))),
    ...rowFields(rows, v => ({ age: v.age, weight: v.weight, length: v.length, headCirc: v.headCirc })),
  ];
}
const GROWTH_ROWS = 4; // every row in range, two points each on every chart

/*
 * code     the registration's suffix (the Forms menu name is `<run marker> <code>`, at most 30 characters)
 * view     the form's route, form/<view>; the shipped registration is ../form/<view>.jsp?demographic_no=
 * table    the form's table
 * needs    forms that must run for a concern to be judged (the Rourke graph plots the Growth 0-36 rows)
 */
const FORMS = [
  { key: 'rourke2020', code: 'R20', title: 'Rourke 2020', view: 'formrourke2020complete', table: 'formRourke2020',
    concerns: ['open', 'keys', 'measurements', 'headcirc', 'measuredate', 'save', 'redisplay', 'reopen', 'restore', 'graph',
      'graphmeasure', 'graphgrowth', 'graphtitle', 'print', 'printnull', 'printsex', 'printgestation', 'printnotes', 'sweep', 'storage'],
    needs: { graphgrowth: ['growth036'] } },
  { key: 'growth036', code: 'G36', title: 'Growth 0-36m', view: 'formGrowth0_36', table: 'formGrowth0_36',
    concerns: ['open', 'keys', 'title', 'save', 'redisplay', 'reopen', 'restore', 'graph', 'print', 'printdob'] },
  { key: 'growthchart', code: 'GRC', title: 'Growth Chart', view: 'formGrowthChart', table: 'formGrowthChart',
    concerns: ['open', 'keys', 'save', 'redisplay', 'reopen', 'restore', 'graph', 'print', 'printbmi', 'printdob'] },
];

const CLAIM_FORMS = FORMS.map(({ key, concerns }) => ({ key, concerns }));

/**
 * ROURKE_GROWTH_ONLY and ROURKE_GROWTH_EXCEPT must be unset or lists of `<form>` / `<form>.<concern>` that name
 * real pairs and leave something to assert. Judged when the check runs, never when the module is required.
 */
function validatePin(env = process.env) {
  return claims.effectiveClaims({ only: env[ONLY], except: env[EXCEPT], onlyVariable: ONLY, exceptVariable: EXCEPT }, CLAIM_FORMS);
}

/*
 * The step labels are literals on purpose where a manifest pins them: expectedFailure.step is checked against the
 * script's own text (run-playwright-suite.js validateExpectedFailure), and a label assembled at run time cannot be found
 * there. scripts/form-claims.test.js proves each literal equals the generated label, so the table cannot drift from
 * the wording below.
 */
const PINNED = Object.freeze({
  'rourke2020.headcirc': 'Rourke 2020: head circumference can be saved to measurements from the form like weight and length',
  'rourke2020.measuredate': 'Rourke 2020: the measurement dialog files the local date of the observation by default',
  'rourke2020.printnull': 'Rourke 2020: the printed form leaves an empty visit date blank',
  'rourke2020.printsex': 'Rourke 2020: the printed form marks the sex of the patient, and only that',
  'rourke2020.printgestation': 'Rourke 2020: the printed gestational age is the weeks from the start of pregnancy to the birth',
  'rourke2020.printnotes': 'Rourke 2020: the printed form carries the notes typed in the page II boxes the template declares for them',
  'rourke2020.graphtitle': 'Rourke 2020: both Graph Length and Weight links open a PDF with the same, length-and-weight title',
  'rourke2020.storage': 'Rourke 2020: every box the form offers can hold what is typed in it',
  'growth036.title': 'Growth 0-36m: the window title does not name the CDC US charts, which the form does not draw',
  'growth036.printdob': 'Growth 0-36m: the printed chart carries the date of birth',
  'growthchart.printbmi': 'Growth Chart: Print BMI produces a PDF',
});
const CONCERN_STEP = Object.freeze({
  open: 'opens from the Forms menu with no error page, script error or failed asset',
  keys: 'shows no unresolved message key',
  title: 'the window title does not name the CDC US charts, which the form does not draw',
  measurements: 'weights and lengths saved in the measurement dialog are stored in measurements and imported into the form',
  headcirc: 'head circumference can be saved to measurements from the form like weight and length',
  measuredate: 'the measurement dialog files the local date of the observation by default',
  save: 'Save stores one new row holding every typed value',
  sweep: 'a value typed in every other box and every ticked box are stored and shown again',
  storage: 'every box the form offers can hold what is typed in it',
  redisplay: 'Save redisplays the saved record in the window it was pressed in',
  reopen: "a fresh chart's saved-form entry reopens the saved record",
  restore: 'the redisplayed and the reopened form show every saved value again',
  graph: 'the graph PDFs have their pages, plot every typed point and draw every page as a PNG',
  graphmeasure: 'the graphs also plot weights and lengths held only in measurements',
  graphgrowth: 'the graphs also plot the Growth 0-36 rows',
  graphtitle: 'both Graph Length and Weight links open a PDF with the same, length-and-weight title',
  print: 'Print produces a PDF that carries the typed text',
  printnull: 'the printed form leaves an empty visit date blank',
  printsex: 'the printed form marks the sex of the patient, and only that',
  printgestation: 'the printed gestational age is the weeks from the start of pregnancy to the birth',
  printnotes: 'the printed form carries the notes typed in the page II boxes the template declares for them',
  printbmi: 'Print BMI produces a PDF',
  printdob: 'the printed chart carries the date of birth',
});
function generatedLabel(key, concern) {
  return `${FORMS.find(entry => entry.key === key).title}: ${CONCERN_STEP[concern]}`;
}
function stepLabel(key, concern) {
  return PINNED[claims.claimKey(key, concern)] || generatedLabel(key, concern);
}

const labelsOf = form => [`form-${form.code}`, `reopen-${form.code}`, `print-${form.code}`];
const isFormPost = response => response.request().method() === 'POST'
  && new URL(response.url()).pathname.endsWith('/form/formname');
/** How many times the word null stands on its own in a printed page's text (what Jasper prints for an expression that is null). */
const nullWords = textOfPdf => (textOfPdf.match(/(^|[\s/])null(?=$|[\s/])/gm) || []).length;

/** The marks (a lone x or X) printed page I puts in the template's M and F boxes. `words` is pdfGraph.pageWords().words. */
function sexMarks(words) {
  const count = box => pdfGraph.wordsIn(words, PRINT_BOXES[box], BOX_SLACK).filter(word => /^[xX]$/.test(word.text)).length;
  return { male: count('male'), female: count('female') };
}
/** What the gestational-age cell of printed page I reads (empty when blank). */
function printedGestation(words) {
  return pdfGraph.wordsIn(words, PRINT_BOXES.gestationalAge, BOX_SLACK).map(word => word.text).join(' ');
}
/**
 * The measurement-date pair on what the run read: throws a Precondition when the dates cannot tell the clinician's day from UTC's
 * (the zone and UTC date today alike, or a date line was crossed during the import), otherwise asserts that the dialog filed the
 * clinician's date.
 */
function judgeMeasureDate({ expected, utc, offered, stored, clockMoved }, zone) {
  claims.precondition(expected !== utc, `${zone} and UTC both date today ${utc}, so the run cannot tell the clinician's date from UTC's`);
  claims.precondition(!clockMoved, 'the date changed (in the browser zone or in UTC) while the measurement was being imported');
  h.assert(stored === expected, `the dialog offered ${offered} and filed ${stored} on a day that is ${expected} where the clinician is (${zone}); it uses the UTC date`);
}

async function workflow(s, { select = validatePin() } = {}) {
  const { sql, patient, provider, marker } = s;
  const wanted = new Set(select);
  const wants = form => form.concerns.some(concern => wanted.has(claims.claimKey(form.key, concern)));
  const needed = new Set(FORMS.filter(wants).map(form => form.key));
  for (const form of FORMS.filter(wants)) {
    for (const concern of form.concerns.filter(name => wanted.has(claims.claimKey(form.key, name)))) {
      for (const key of (form.needs || {})[concern] || []) needed.add(key);
    }
  }
  const today = (() => { const n = new Date(); return { y: n.getUTCFullYear(), m: n.getUTCMonth() + 1, d: n.getUTCDate() }; })();
  const dob = day(today, -INFANT_DAYS);
  const fieldsOf = { rourke2020: rourkeFields(marker, dob), growth036: growthFields(marker, dob), growthchart: chartFields(marker, today) };
  const entries = FORMS.filter(form => needed.has(form.key)).map(form => ({
    form, name: `${marker} ${form.code}`, results: {}, seen: new Set(), restore: {}, lost: {}, pages: [], fields: fieldsOf[form.key], extras: [], radios: [],
  }));
  const byKey = key => entries.find(entry => entry.form.key === key);
  const registrations = entries.map(entry => ({
    name: entry.name, value: `../form/${entry.form.view}.jsp?fixture=${marker}&demographic_no=`, table: entry.form.table,
  }));
  const zone = RUN_ZONE;

  // PRECONDITION of the measurement-date pair, not an assertion of it: the pair compares the date the dialog files with the
  // date where the clinician is, which is only a test while that date differs from UTC's. The zone was chosen when the module
  // loaded; a run that starts across UTC midnight finds them equal, and must not read as the defect being fixed.
  if (wanted.has(claims.claimKey('rourke2020', 'measuredate'))) {
    await preconditionStep('the browser time zone dates today differently from UTC (the measurement-date check needs that)', () => {
      const now = new Date();
      claims.precondition(localDate(zone, now) !== utcDate(now), `${zone} and UTC both date today ${utcDate(now)}`);
    });
  }

  const tables = entries.map(entry => entry.form.table);
  const rourkeTable = tables.includes('formRourke2020');
  // A Rourke 2020 record keeps its radio buttons in form_boolean_value, keyed by form name and the record's id only. This
  // is the highest id there when the check starts: any row of the form above it that no formRourke2020 row owns is
  // an orphan this run left behind, whatever its patient (the residue audit does not look at the table).
  const radioFloor = rourkeTable ? Number(sql.value(`SELECT COALESCE(MAX(form_id),0) FROM form_boolean_value WHERE form_name='formRourke2020'`)) : 0;
  h.assert(Number.isFinite(radioFloor), 'The highest Rourke 2020 radio-button record id could not be read');
  const orphans = () => Number(sql.value(`SELECT COUNT(*) FROM form_boolean_value WHERE form_name='formRourke2020' AND form_id>${radioFloor}
    AND form_id NOT IN (SELECT ID FROM formRourke2020)`));

  s.cleanup(async () => {
    if (rourkeTable) {
      // The browser is closed by now, but a Save or autosave it had already sent can still be written by the server (a Rourke
      // Save takes 7 to 17 s): wait until the patient's rows and the radio rows above the floor stop changing, then delete.
      const snapshot = () => sql.value(`SELECT CONCAT((SELECT COUNT(*) FROM formRourke2020 WHERE demographic_no=${patient}),'/',
        (SELECT COUNT(*) FROM form_boolean_value WHERE form_name='formRourke2020' AND form_id>${radioFloor}))`);
      let last = snapshot();
      for (let steady = 0, waited = 0; steady < 2 && waited < 60; waited++) {
        await new Promise(resolve => setTimeout(resolve, 1000));
        const now = snapshot();
        steady = now === last ? steady + 1 : 0;
        last = now;
      }
    }
    // The radio buttons of a Rourke 2020 record live in form_boolean_value, keyed by the record's id: read the ids first.
    const ids = rourkeTable ? sql.rows(`SELECT ID FROM formRourke2020 WHERE demographic_no=${patient}`).map(row => Number(row[0])).filter(Number.isFinite) : [];
    const list = ids.length ? ids.join(',') : '0';
    sql.execute([
      rourkeTable && `DELETE FROM form_boolean_value WHERE form_name='formRourke2020' AND form_id IN (${list})`,
      ...tables.map(table => `DELETE FROM ${table} WHERE demographic_no=${patient}`),
      `DELETE FROM measurements WHERE demographicNo=${patient}`,
    ].filter(Boolean).join(';'));
    h.assert(sql.value(`SELECT ${[...tables.map(table => `(SELECT COUNT(*) FROM ${table} WHERE demographic_no=${patient})`),
      `(SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})`,
      rourkeTable && `(SELECT COUNT(*) FROM form_boolean_value WHERE form_name='formRourke2020' AND form_id IN (${list}))`].filter(Boolean).join('+')}`) === '0',
    'Form, radio-button and measurement rows of the owned patient were not removed');
    if (rourkeTable) h.assert(orphans() === 0, `${orphans()} Rourke 2020 radio-button rows were left with no record to belong to`);
  });
  s.cleanup(() => {
    for (const { name, value } of registrations) {
      sql.execute(`DELETE FROM encounterForm WHERE form_value=${h.sqlString(value)} AND form_name=${h.sqlString(name)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value LIKE ${h.sqlString(`%fixture=${marker}&%`)}`) === '0',
      'The owned Forms-menu registrations were not removed');
  });

  // FIXTURE, not an assertion. An infant (so every Rourke visit up to 18 months is in the past) with the contact
  // and health-card columns every registered patient has.
  sql.execute(`UPDATE demographic SET year_of_birth='${dob.y}',month_of_birth='${pad(dob.m)}',date_of_birth='${pad(dob.d)}',sex='${PATIENT_SEX}',
    address='1 Test St',city='Toronto',postal='M5V 2T6',phone='416-555-0100',phone2='416-555-0101',hin='9876543217',ver='AB',
    email='fake@example.invalid',roster_status='RO'
    WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  for (const entry of entries) {
    const { form } = entry;
    h.assert(entry.name.length <= 30, `The owned registration name exceeds encounterForm.form_name: ${entry.name}`);
    const columns = sql.rows(`SELECT COLUMN_NAME,COLUMN_KEY FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=${h.sqlString(form.table)}`);
    entry.idColumn = (columns.find(column => column[1] === 'PRI') || [])[0];
    entry.columns = new Set(columns.map(column => column[0]));
    h.assert(entry.idColumn, `FORMS is wrong: ${form.table} has no primary key`);
    for (const field of entry.fields.filter(field => field.kind !== 'radio')) {
      h.assert(entry.columns.has(colOf(field)), `FORMS is wrong: ${form.table} has no column ${colOf(field)}`);
    }
  }
  for (const { name, value, table } of registrations) {
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${h.sqlString(value)},${h.sqlString(name)},${h.sqlString(table)},COALESCE(MAX(hidden),0)+1 FROM encounterForm`);
  }
  const chart = await s.chart();

  // ---- recording -------------------------------------------------------------------------------------------------
  /**
   * Run one concern's body; record how it ended (lib/form-claims.js outcomeOf), and every JS-layer problem the pages
   * raised meanwhile. Save is answered by a redirect to the redisplay, so when Save succeeded the problems that follow it
   * are left for the redisplay concern to take (`carry`). A concern that drives no page (it only compares what an earlier
   * concern fetched or stored) passes `take: false`: it must not absorb a stray problem of another page and so be judged
   * for it; the problem stays in the recorder for the next concern that does drive a page, or for the run's own end check.
   * `body` marks what it merely needs in order to act (a menu entry, a link, a button) with claims.reaching /
   * asPrecondition, so that a pin is never held by a control that could not be reached.
   */
  async function conclude(entry, concern, body, { carry = false, take = true } = {}) {
    let error;
    try {
      await body();
    } catch (caught) {
      error = caught;
    }
    excuseViewerAborts();
    let problems = [];
    if (take && (error || !carry)) {
      // A problem that recurs on every page of the form belongs to the concern that first met it.
      problems = takeProblems(s.recorder, labelsOf(entry.form)).filter(problem => !entry.seen.has(problem));
      problems.forEach(problem => entry.seen.add(problem));
    }
    record(entry, concern, claims.outcomeOf(error, problems));
  }
  function record(entry, concern, outcome) {
    entry.results[concern] = outcome;
    console.log(`  ${outcome.ok ? 'PASS' : 'FAIL'} ${NAME}: ${stepLabel(entry.form.key, concern)}${outcome.ok ? '' : ` -- ${outcome.message}`}`);
  }
  /** An outcome for a concern judged from a message (empty: it passed) rather than from a body that threw. */
  const outcomeOfMessage = message => (message ? claims.outcomeOf(new Error(message), []) : { ok: true });
  const blocked = (entry, concern, why) => {
    if (!entry.form.concerns.includes(concern) || entry.results[concern]) return;
    entry.results[concern] = claims.blockedOutcome(why);
    console.log(`  SKIP ${NAME}: ${stepLabel(entry.form.key, concern)} -- ${entry.results[concern].message}`);
  };
  const passed = (entry, concern) => Boolean(entry.results[concern] && entry.results[concern].ok);
  // The sweep and storage concerns run on a record of their own after the main flow, so a failure of the main flow does not block them.
  const SWEEP = ['sweep', 'storage'];
  const blockAll = (entry, why, except = []) => {
    for (const concern of entry.form.concerns) if (!except.includes(concern) && !SWEEP.includes(concern)) blocked(entry, concern, why);
  };

  /**
   * A graph popup is Chromium's built-in PDF viewer, and closing it can abort the viewer's own extension UI request
   * (net::ERR_ABORTED on a chrome-extension: URL). That is the browser, not the application, whose PDF this check reads
   * in transit; the same allowance is made in export-content-patient-labels. Only the print/graph popups of this
   * check, only that failure.
   */
  function excuseViewerAborts() {
    const list = s.recorder.requestFailures;
    for (let i = list.length - 1; i >= 0; i--) {
      const failure = list[i];
      if (/^print-/.test(failure.label) && failure.resourceType === 'other' && failure.errorText === 'net::ERR_ABORTED'
        && String(failure.url).startsWith('chrome-extension://')) list.splice(i, 1);
    }
  }

  /** A measured fact of the run (counts and sizes only, never a value of the patient), printed beside the concern's outcome. */
  const detail = (entry, text) => console.log(`    detail ${entry.form.title}: ${text}`);

  /** Pages the form opens besides its own (a Save that targets another window, a print or graph popup) belong to it. */
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

  /**
   * Run `act` (a real click) and return what the application answered to the requests `match` accepts, read in
   * transit (a browser handed a PDF shows its viewer or downloads it, and neither leaves anything to read). The
   * wait ends at the first answer: every print and graph here makes one such request.
   */
  async function answerTo(entry, match, act, timeout = 120000) {
    const seen = [];
    // A handler that throws would be an unhandled rejection, which ends the process before cleanup runs: every failure
    // is kept as an answer of its own and judged by the caller.
    const keep = async route => {
      try {
        const response = await route.fetch({ maxRedirects: 0, timeout });
        const body = await response.body();
        seen.push({ status: response.status(), type: response.headers()['content-type'] || '', body });
        await route.fulfill({ response, body });
      } catch (error) {
        seen.push({ status: 0, type: '', body: Buffer.alloc(0), error: error.message.split('\n')[0] });
        await route.abort().catch(() => {});
      }
    };
    await s.context.route(match, keep);
    const stop = watchPages(entry, `print-${entry.form.code}`);
    try {
      // A control that cannot be found or clicked is a precondition (claims.reaching); so is a request that was never
      // made, never answered or failed in transit: only an answer that is not a PDF is for assertPdf to judge.
      await h.withExpectedDialogs(entry.page, () => claims.reaching(() => act()));
      const deadline = Date.now() + timeout;
      while (!seen.length && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
    } finally {
      stop();
      await s.context.unroute(match, keep).catch(() => {});
    }
    claims.precondition(seen.length, `the application answered nothing within ${timeout / 1000} s`);
    claims.precondition(!seen[0].error, `the request failed: ${seen[0].error}`);
    return seen[0];
  }
  /** A PDF answer: 200, application/pdf, %PDF. Anything else (an HTML error page above all) is a failure naming the status. */
  function assertPdf(answer, what) {
    h.assert(answer.status === 200 && /application\/pdf/.test(answer.type), `${what} answered ${answer.status} ${answer.type || 'with no content type'}, not a PDF`);
    h.assert(answer.body.subarray(0, 4).toString() === '%PDF', `${what} answered something other than a PDF document`);
    return answer.body;
  }
  const isFormAnswer = url => url.pathname.endsWith('/form/formname');
  const isCreatePdf = url => url.pathname.endsWith('/form/createpdf');

  // ---- helpers shared by the forms -------------------------------------------------------------------------------
  async function openForm(entry) {
    const { form } = entry;
    // Getting to the menu entry and through it is what the check needs in order to look at the form: a menu that does not list
    // the registration or a window that does not open is a precondition, not a defect of the form under test.
    const link = await claims.asPrecondition(async () => {
      await chart.locator('#menuTitle1 a').hover();
      // The menu entry (#menu1) opens a new record; the saved records of the same form are listed again in #formslist below it.
      const found = chart.locator('#menu1').getByRole('link', { name: entry.name, exact: true });
      const listed = await found.count();
      claims.precondition(listed === 1, `the Forms menu lists the registered form ${listed} times, not once`);
      return found;
    }, 'finding the Forms-menu entry');
    entry.page = await claims.reaching(() => s.popup(chart, link, `form-${form.code}`));
    h.assert(new URL(entry.page.url()).searchParams.get('demographic_no') === patient, 'the form opened for another patient');
    await claims.reaching(async () => {
      await entry.page.getByRole('button', { name: 'Save', exact: true }).first().waitFor({ state: 'attached', timeout: 10000 });
      if (form.key === 'rourke2020') await entry.page.locator('#rourke2020-tabs').waitFor({ state: 'visible', timeout: 10000 });
    });
  }
  /** A tab of the Rourke form; a click that cannot be made is a precondition of whatever needed the tab. */
  const tab = (page, index) => page.locator('#tab-list a').nth(index);
  const openTab = (page, index) => claims.reaching(() => tab(page, index).click());

  /**
   * Rourke only, for the sweep: every text box and every radio button or checkbox the page offers (the measurement cells and visit
   * dates, which Save validates, are left to the main record), so a box nobody listed here must also be saved and shown again.
   */
  async function findBoxes(entry) {
    const found = await entry.page.evaluate(() => [...document.querySelectorAll('#frmP1 input[type="text"]:not([readonly]), #frmP1 textarea')]
      .filter(element => element.name).map(element => ({ name: element.name, max: element.maxLength })));
    const seen = new Set();
    entry.extras = [];
    for (const { name, max } of found) {
      if (seen.has(name) || MEASURE_OR_DATE.test(name)) continue;
      seen.add(name);
      const base = `x${entry.extras.length + 1}`;
      entry.extras.push({ kind: 'text', name, value: max > 0 && max < base.length ? base.slice(0, max) : base, stored: entry.columns.has(name) });
    }
    entry.radios = await entry.page.evaluate(() => [...new Set([...document.querySelectorAll('#frmP1 input[type="radio"], #frmP1 input[type="checkbox"]')]
      .map(element => element.name).filter(Boolean))]);
    detail(entry, `the sweep types ${entry.extras.length} text boxes and ticks ${entry.radios.length} radio buttons and checkboxes`);
  }

  /** What the page fails to show of the saved record (all of it, so one message names every missing value), or null. */
  async function shownProblem(entry, page, where) {
    const missing = [];
    let current = -1;
    for (const field of entry.fields) {
      if (entry.form.key === 'rourke2020' && field.page !== current) { current = field.page; await openTab(page, current); }
      const input = page.locator(`[name="${field.name}"]`).first();
      if (field.kind === 'radio') {
        if (!await input.isChecked()) missing.push(field.name);
        continue;
      }
      if (entry.form.key === 'rourke2020' && !await input.isVisible()) { missing.push(`${field.name} (not shown on page ${field.page + 1})`); continue; }
      const shown = await input.inputValue();
      if (shown !== field.value) missing.push(field.kind === 'text' && field.value.length > 20 ? `${field.name} (typed ${field.value.length} characters, shown ${shown.length})` : field.name);
    }
    if (entry.form.key === 'rourke2020') {
      // The All tab shows the four pages together.
      await openTab(page, 4);
      for (const id of ['tab-cp1', 'tab-cp2', 'tab-cp3', 'tab-cp4']) {
        if (!await page.locator(`#${id}`).isVisible()) missing.push(`the All tab does not show ${id}`);
      }
      await openTab(page, 0);
    }
    return missing.length ? `${where} does not show the saved ${missing.join(', ')}` : null;
  }

  /** The values the row must hold: the Rourke dates are stored as dates and read back dd/MM/yyyy. */
  const storedFields = entry => entry.fields.filter(field => field.kind !== 'radio');
  function rowQuery(entry, id) {
    const columns = storedFields(entry)
      .map(field => (field.kind === 'date' && entry.form.key === 'rourke2020' ? `DATE_FORMAT(\`${colOf(field)}\`,'%d/%m/%Y')`
        : field.kind === 'date' ? `DATE_FORMAT(\`${colOf(field)}\`,'%Y/%m/%d')` : `\`${colOf(field)}\``));
    return `SELECT provider_no,${columns.join(',')} FROM ${entry.form.table} WHERE \`${entry.idColumn}\`=${Number(id)} AND demographic_no=${patient}`;
  }
  function assertRowHolds(entry, id) {
    const rows = sql.rows(rowQuery(entry, id));
    h.assert(rows.length === 1, 'the saved row is not in the form table');
    const [row] = rows;
    h.assert(row[0] === provider, 'the row is not attributed to the signed-in provider');
    const wrong = [];
    storedFields(entry).forEach((field, index) => { if (row[index + 1] !== field.value) wrong.push(colOf(field)); });
    h.assert(!wrong.length, `the stored ${wrong.join(', ')} differ${wrong.length === 1 ? 's' : ''} from what was typed`);
    const radios = entry.fields.filter(field => field.kind === 'radio').map(field => field.name);
    if (radios.length) {
      const stored = new Set(sql.rows(`SELECT field_name FROM form_boolean_value WHERE form_name=${h.sqlString(entry.form.table)} AND form_id=${Number(id)} AND value=1`).map(r => r[0]));
      const unticked = radios.filter(name => !stored.has(name));
      h.assert(!unticked.length, `${unticked.length} ticked boxes were not stored (${unticked.join(', ')})`);
    }
  }

  /** Type every field except the measurement imports that worked; the value of an import that failed is typed (see rourkeMeasurements). */
  async function typeFields(entry, { typeMeasures }) {
    const { page, form } = entry;
    let current = -1;
    for (const field of entry.fields) {
      if (field.kind === 'measure' && !typeMeasures.has(field.name)) continue;
      if (form.key === 'rourke2020' && field.page !== current) { current = field.page; await openTab(page, current); }
      const input = page.locator(`[name="${field.name}"]`).first();
      if (field.kind === 'radio') await input.check();
      else if (field.kind === 'date') await input.evaluate((element, value) => { element.value = value; }, field.value);
      else await input.fill(field.value ?? '');
    }
    if (form.key === 'rourke2020') await openTab(page, 0);
  }

  /** Press Save and judge the answer. Returns when the row is stored; records the redirect's frame for the redisplay. */
  async function pressSave(entry, { exactRows }) {
    const { page, form } = entry;
    const save = form.key === 'rourke2020'
      ? page.locator('#tab-cp1 input[type="submit"][value="Save"]').first()
      : page.getByRole('button', { name: 'Save', exact: true }).first();
    entry.before = Number(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`));
    entry.writes = 0;
    const onRequest = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/form/formname')) entry.writes++;
    };
    page.on('request', onRequest);
    const stop = watchPages(entry, `form-${form.code}`);
    // A Rourke 2020 record stores one form_boolean_value row per ticked box (about 450 on an ordinary record, 1,400 here), so its Save is slow.
    const posted = s.context.waitForEvent('response', { predicate: isFormPost, timeout: 90000 });
    posted.catch(() => {});
    const started = Date.now();
    try {
      const dialogs = await h.withExpectedDialogs(page, () => save.click());
      const response = await posted.catch(() => null);
      detail(entry, `Save was answered after ${Date.now() - started} ms`);
      h.assert(response, `Save posted nothing to form/formname (dialogs: ${dialogs.map(d => `${d.type} "${d.text.slice(0, 60)}"`).join('; ') || 'none'})`);
      h.assert(response.status() === 302, `the save was answered ${response.status()}, not accepted with a redirect`);
      entry.saveFrame = response.frame();
    } finally {
      stop();
      page.off('request', onRequest);
    }
    const after = Number(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`));
    // The Rourke form autosaves every ten seconds while it is changed, and each autosave is a row of its own, so its
    // rows are counted against the writes the page made (the Save and any autosave in the same window), not assumed to be one.
    h.assert(entry.writes >= 1, 'no write reached form/formname');
    h.assert(after - entry.before === entry.writes, `Save wrote ${after - entry.before} rows for ${entry.writes} requests, not one row each`);
    if (exactRows) h.assert(entry.writes === 1, `Save made ${entry.writes} requests, not one`);
  }

  /** The id of the record the redisplay shows (the formId of its URL). */
  async function redisplayedId(entry) {
    const landed = entry.saveFrame && entry.saveFrame.page();
    claims.precondition(landed, 'the window that answered Save could not be found');
    h.assert(landed === entry.page,
      'the saved record was redisplayed in another window, and the one Save was pressed in still shows the unsaved form');
    await landed.waitForURL(url => url.pathname.endsWith('/form/forwardname'), { waitUntil: 'domcontentloaded' });
    const id = new URL(landed.url()).searchParams.get('formId');
    h.assert(/^[1-9]\d*$/.test(id || ''), 'Save redisplayed no saved record');
    await h.assertNotErrorPage(landed, 'the redisplayed form');
    return { landed, id };
  }

  /**
   * Click the chart's saved-form entry. Closing a form window makes the chart reload its Forms module, which
   * replaces the anchors and folds the list again, so a click that races the reload is retried on a freshly found
   * entry. Only a failure to find or click the entry is retried, never one after the popup has opened.
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

  /** Open the form's saved record from a fresh chart (the user path "open the saved form"), judge it, and leave it open. */
  async function reopen(entry) {
    const fresh = await s.context.newPage();
    h.wireStrictPage(fresh, 'reopen-chart', s.recorder);
    try {
      await fresh.goto(chart.url(), { waitUntil: 'domcontentloaded' });
      await waitForNavbars(fresh, 20000);
      let page;
      await conclude(entry, 'reopen', async () => {
        page = await claims.reaching(() => openSavedEntry(fresh, entry));
        const params = new URL(page.url()).searchParams;
        h.assert(params.get('demographic_no') === patient, 'the saved-form entry opened another patient');
        h.assert(params.get('formId') === entry.id, 'the saved-form entry did not open the saved record');
        entry.restore.reopen = await shownProblem(entry, page, 'the reopened form');
      });
      if (!passed(entry, 'reopen') && page && !page.isClosed()) await page.close().catch(() => {});
      entry.page = passed(entry, 'reopen') && page && !page.isClosed() ? page : null;
    } finally {
      await fresh.close().catch(() => {});
    }
  }

  /** The common part of every form's flow up to the saved record: open, keys, (type, save), redisplay. */
  async function openAndKeys(entry) {
    await conclude(entry, 'open', () => openForm(entry));
    if (!entry.page || entry.page.isClosed()) { blockAll(entry, 'the form did not open'); return false; }
    await conclude(entry, 'keys', async () => {
      if (entry.form.key === 'rourke2020') await openTab(entry.page, 4);
      const { title, body } = await entry.page.evaluate(() => ({ title: document.title, body: document.body.innerText }));
      const keys = [...new Set(`${title}\n${body}`.match(/\?\?\?[\w.-]+\?\?\?/g) || [])];
      h.assert(!keys.length, `the page shows ${keys.length} unresolved message key(s): ${keys.slice(0, 3).join(' ')}`);
      if (entry.form.key === 'rourke2020') await openTab(entry.page, 0);
    });
    if (entry.form.key === 'growth036') {
      // Finding 253. Reading the title drives no page, so the concern takes no browser problem.
      await conclude(entry, 'title', async () => {
        const title = await claims.reaching(() => entry.page.title());
        claims.precondition(title.trim(), 'the Growth 0-36 window has no title to read');
        h.assert(!/\bCDC\b/i.test(title), `the Growth 0-36 window is titled ${JSON.stringify(title)}, though its charts are the WHO Growth Charts`);
      }, { take: false });
    }
    return true;
  }
  async function saveAndRedisplay(entry, { typeMeasures = new Set() } = {}) {
    await conclude(entry, 'save', async () => {
      await typeFields(entry, { typeMeasures });
      await pressSave(entry, { exactRows: entry.form.key !== 'rourke2020' });
      const { id } = await redisplayedId(entry).catch(error => {
        // The row was stored; the redisplay concern reports where the record landed.
        entry.redisplayError = error;
        return { id: null };
      });
      entry.id = id || sql.value(`SELECT MAX(\`${entry.idColumn}\`) FROM ${entry.form.table} WHERE demographic_no=${patient}`);
      assertRowHolds(entry, entry.id);
    }, { carry: true });
    if (!passed(entry, 'save')) { blockAll(entry, 'Save stored no usable row'); await closeAll(entry); return false; }
    await conclude(entry, 'redisplay', async () => {
      if (entry.redisplayError) throw entry.redisplayError;
      const { landed } = await redisplayedId(entry);
      entry.restore.redisplay = await shownProblem(entry, landed, 'the redisplayed form');
    });
    await closeAll(entry);
    return true;
  }
  /** `restore` judges the places the saved record was shown; if neither could be read it is blocked. */
  function concludeRestore(entry) {
    if (entry.results.restore) return;
    const shown = Object.values(entry.restore);
    if (!shown.length) blocked(entry, 'restore', 'neither the redisplayed nor the reopened form could be read');
    else record(entry, 'restore', outcomeOfMessage(shown.filter(Boolean).join(' | ')));
  }

  // ---- Rourke 2020 -----------------------------------------------------------------------------------------------
  /** Import one value through the measurement dialog the label opens. Returns the date the dialog offered and the request it made. */
  async function importMeasurement(page, field) {
    await openTab(page, field.page);
    await page.locator(`a[onclick*="displayDemographicMeasurements('${field.name}'"]`).first().click();
    await page.locator('#currentMeasurementValue').fill(field.value);
    const offered = await page.locator('#currentMeasurementObservationDate').inputValue();
    if (field.observed) await page.locator('#currentMeasurementObservationDate').fill(iso(field.observed));
    const request = await captureRequest(page, url => url.pathname.endsWith('/encounter/MeasurementData')
      && url.searchParams.get('action') === 'saveMeasurement', () => page.locator('.meas-btn-save').click(), { timeout: 15000 });
    h.assert(request.status === 200, `the measurement Save for ${field.name} was answered ${request.status}`);
    h.assert(request.params.get('type') === field.type && request.params.get('value') === field.value
      && request.params.get('demographicNo') === patient, `the measurement Save for ${field.name} sent another patient, type or value`);
    h.assert(await page.locator(`[name="${field.name}"]`).first().inputValue() === field.value, `the dialog did not import ${field.name} into the form`);
    return { offered, request };
  }
  const measurementRows = `SELECT type,dataField,measuringInstruction,DATE(dateObserved),providerNo FROM measurements WHERE demographicNo=${patient}`;

  async function rourkeMeasurements(entry) {
    const imported = entry.fields.filter(field => field.kind === 'measure' && field.observed);
    const byDefault = entry.fields.find(field => field.kind === 'measure' && !field.observed);
    entry.importFailed = new Set();
    // The imports, the default-date one included, are the controls of the pair below: a dialog that cannot import fails
    // `measurements`, and measuredate then only compares a date.
    await conclude(entry, 'measurements', async () => {
      const failures = [];
      for (const field of imported) {
        try {
          await importMeasurement(entry.page, field);
        } catch (error) {
          failures.push(`${field.name}: ${error.message.split('\n')[0]}`);
          entry.importFailed.add(field.name);
        }
      }
      try {
        // The two dates the dialog is judged against are read around the import, so a clock that crosses a date line while
        // it runs is noticed (measuredate treats it as a precondition).
        const before = new Date();
        const offered = await importMeasurement(entry.page, byDefault);
        const after = new Date();
        entry.defaultImport = {
          expected: localDate(zone, before), utc: utcDate(before),
          clockMoved: localDate(zone, after) !== localDate(zone, before) || utcDate(after) !== utcDate(before), ...offered,
        };
      } catch (error) {
        failures.push(`${byDefault.name}: ${error.message.split('\n')[0]}`);
        entry.importFailed.add(byDefault.name);
      }
      const rows = sql.rows(measurementRows);
      const defaulted = rows.filter(row => row[0] === byDefault.type && row[1] === byDefault.value && row[2] === 'in kg');
      // The date filed for the default-date import is read FIRST and kept whatever else this concern finds, so measuredate
      // judges the date and nothing else: it is blocked, not failed, when this read is not possible.
      if (entry.defaultImport && defaulted.length === 1) entry.defaultImport.stored = defaulted[0][3];
      h.assert(!failures.length, failures.join(' | '));
      for (const field of imported) {
        const instruction = field.type === 'WT' ? 'in kg' : 'in cm';
        const found = rows.filter(row => row[0] === field.type && row[1] === field.value && row[2] === instruction && row[3] === iso(field.observed));
        h.assert(found.length === 1, `measurements holds ${found.length} rows for ${field.name} (${field.type} ${field.value} ${instruction}), not one`);
        h.assert(found[0][4] === provider, `the ${field.name} measurement is not attributed to the signed-in provider`);
      }
      h.assert(defaulted.length === 1, `measurements holds ${defaulted.length} rows for ${byDefault.name} (${byDefault.type} ${byDefault.value} in kg), not one`);
      h.assert(rows.length === imported.length + 1, `measurements holds ${rows.length} rows after ${imported.length + 1} imports`);
    });
    // Head circumference. The page gives every weight and length a measurement dialog; the brief asks for the same for head circumference.
    // The pair is the dialog's existence (the pinned assertion). Only when one exists does the body drive the page, and only then
    // does the concern take the page's problems.
    const hcLink = entry.page.locator(`a[onclick*="displayDemographicMeasurements('p1_hc1w'"]`);
    const hasDialog = await hcLink.count() > 0;
    await conclude(entry, 'headcirc', async () => {
      h.assert(hasDialog, 'the Rourke 2020 page has no measurement dialog for head circumference: the head circumference cells (c_headCirc, p1_hc1w and the rest) are not links, and the dialog knows only WT, HT, HR and BP');
      // A dialog exists, so import through it and require a stored row: the pair keeps meaning something once the gap closes.
      const before = Number(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`));
      await claims.reaching(async () => {
        await openTab(entry.page, 0);
        await hcLink.first().click();
        await entry.page.locator('#currentMeasurementValue').fill('35.5');
        await entry.page.locator('.meas-btn-save').click();
      });
      await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`, String(before + 1), 'the head circumference dialog stored no measurement');
    }, { take: hasDialog });
    // The date the dialog files by default, judged against the clinician's own calendar (the context's zone, see zoneWhereTodayDiffersFromUtc).
    // It compares two dates and drives no page, so it takes no browser problem. It is blocked unless the date the dialog filed was read.
    const filed = entry.defaultImport;
    if (!filed || filed.stored === undefined) blocked(entry, 'measuredate', 'the date the default-date import was filed under could not be read');
    else {
      await conclude(entry, 'measuredate', async () => judgeMeasureDate(filed, zone), { take: false });
    }
    // A value whose import failed is typed, so Save, redisplay and the rest are judged on their own.
    for (const field of entry.fields.filter(item => item.kind === 'measure')) {
      const input = entry.page.locator(`[name="${field.name}"]`).first();
      if (await input.inputValue().catch(() => '') !== field.value) entry.importFailed.add(field.name);
    }
  }

  /**
   * One chart PDF, judged from its bytes: it has the pages the chart is laid out on, its first page holds at least the circles
   * the typed points need, and every page draws as a PNG that is more than a blank sheet. Returns what it measured.
   */
  function judgeChart(entry, what, body, { pages, points }) {
    const found = pdfGraph.pageCount(body);
    const plotted = (pdfGraph.plottedPoints(body)[0]) || 0;
    const pngs = Array.from({ length: found }, (_, index) => pdfGraph.renderPng(body, { page: index + 1 }));
    detail(entry, `${what}: ${body.length} bytes, ${found} page(s), ${plotted} points plotted on page 1 (floor ${points}), PNG sizes ${pngs.map(png => png.length).join('/')}`);
    h.assert(found === pages, `${what} has ${found} pages, not the ${pages} its chart is laid out on`);
    h.assert(plotted >= points, `${what} plots ${plotted} points; ${points} typed points fit the chart`);
    pngs.forEach((png, index) => h.assert(pdfGraph.isPng(png) && png.length >= MIN_PNG_BYTES, `${what} page ${index + 1} draws as a ${png.length}-byte image, not a chart`));
    return { plotted, pages: found };
  }

  async function rourkeGraphsAndPrint(entry) {
    const page = entry.page;
    entry.graph = {};
    // The graphs plot what the form holds; the measurements the dialog saved are removed first so that none of them can stand
    // in for a typed point (graphmeasure then adds measurements of its own and asserts they are plotted too).
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient}`);
    const links = { length: page.locator('#tab-cp1 a[name="length"]').first(), headCirc: page.locator('#tab-cp1 a[name="headCirc"]').first() };
    const graphName = name => (name === 'length' ? 'Length and Weight' : 'Head Circumference');
    const fetchGraph = async name => {
      const answer = await answerTo(entry, isFormAnswer, () => links[name].click({ noWaitAfter: true }));
      const url = await claims.reaching(() => links[name].evaluate(a => (a.getAttribute('onclick').match(/'([^']*\/form\/formname\?[^']*)'/) || [])[1]));
      claims.precondition(url, 'the graph link carries no address');
      return { body: assertPdf(answer, `the ${graphName(name)} graph`), url };
    };
    await conclude(entry, 'graph', async () => {
      await openTab(page, 0);
      for (const [name, points, pages] of [['length', ROURKE_LENGTH_WEIGHT_POINTS, ROURKE_LENGTH_WEIGHT_PAGES], ['headCirc', ROURKE_HEAD_POINTS, ROURKE_HEAD_PAGES]]) {
        const { body, url } = await fetchGraph(name);
        entry.graph[name] = { url };
        entry.graph[name].plotted = judgeChart(entry, `the ${graphName(name)} graph`, body, { pages, points }).plotted;
      }
    });
    if (!passed(entry, 'graph')) { blocked(entry, 'graphmeasure', 'the graphs did not plot'); }
    else {
      // The PDF is fetched again from the link's address (no page is driven), so no browser problem is taken.
      await conclude(entry, 'graphmeasure', async () => {
        // Two measurements that exist only in the patient's measurements, as the chart's own Measurements module would hold them.
        const when = `${iso(day(dob, 100))} 10:00:00`;
        sql.execute(`INSERT INTO measurements (type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered,appointmentNo)
          VALUES ('WT',${patient},${h.sqlString(provider)},'6.4','in kg','',${h.sqlString(when)},NOW(),0),
                 ('HT',${patient},${h.sqlString(provider)},'62.5','in cm','',${h.sqlString(when)},NOW(),0)`);
        const answer = await s.context.request.get(new URL(entry.graph.length.url, chart.url()).toString());
        const body = assertPdf({ status: answer.status(), type: answer.headers()['content-type'] || '', body: await answer.body() }, 'the Length and Weight graph');
        const plotted = pdfGraph.plottedPoints(body)[0];
        detail(entry, `the Length and Weight graph plots ${plotted} points with a weight and a length added to measurements (${entry.graph.length.plotted} before)`);
        h.assert(plotted >= entry.graph.length.plotted + 2, `the graph plots ${plotted} points with a weight and a length in measurements, ${entry.graph.length.plotted} without`);
      }, { take: false });
    }
    // The baseline for graphgrowth: the head circumference graph as it is now, before the Growth 0-36 form is saved.
    try {
      const answer = await s.context.request.get(new URL(entry.graph.headCirc.url, chart.url()).toString());
      entry.graph.headCirc.baseline = pdfGraph.plottedPoints(await answer.body())[0];
    } catch { /* graphgrowth is judged blocked below */ }

    // Finding 252: the two Length and Weight links on page I open the same graph, so their windows should carry the same title.
    await conclude(entry, 'graphtitle', async () => {
      await openTab(page, 0);
      const lengthLinks = page.locator('#tab-cp1 a[name="length"]');
      const count = await claims.reaching(() => lengthLinks.count());
      claims.precondition(count === 2, `page I of a saved record has ${count} Graph Length and Weight links, not the two it is laid out with`);
      const titles = [];
      for (const index of [0, 1]) {
        const answer = await answerTo(entry, isFormAnswer, () => lengthLinks.nth(index).click({ noWaitAfter: true }));
        // Whether the link answers a PDF at all is the graph concern's question; here it is only what the title is read from.
        const body = await claims.asPrecondition(async () => assertPdf(answer, `Graph Length and Weight link ${index + 1}`), 'reading the graph');
        titles.push(await claims.asPrecondition(async () => pdfGraph.pdfTitle(body), 'reading the PDF title'));
      }
      detail(entry, `the two Graph Length and Weight links open PDFs titled ${titles.map(title => JSON.stringify(title)).join(' and ')}`);
      h.assert(titles[0] === titles[1] && !/head circumference/i.test(titles.join(' ')),
        `the two Graph Length and Weight links open PDFs titled ${titles.map(title => JSON.stringify(title)).join(' and ')}, though they open the same chart`);
    });

    await conclude(entry, 'print', async () => {
      await openTab(page, 0);
      const started = Date.now();
      const answer = await answerTo(entry, isFormAnswer, () => page.locator('#tab-cp1 input[type="submit"][value="Print"]').first().click({ noWaitAfter: true }));
      const answeredIn = Date.now() - started;
      const body = assertPdf(answer, 'Print');
      entry.printText = pdfTextBuffer(body);
      const pages = pdfGraph.pageCount(body);
      detail(entry, `Print answered a ${body.length}-byte, ${pages}-page PDF in ${answeredIn} ms`);
      h.assert(pages === ROURKE_PRINT_PAGES, `the printed Rourke record has ${pages} pages, not the form's ${ROURKE_PRINT_PAGES}`);
      const squashed = squash(entry.printText);
      h.assert(squashed.includes(squash(marker)), 'the PDF does not carry the typed text');
      const missing = entry.fields.filter(field => field.kind === 'measure'
        || (field.kind === 'text' && (/^[\d.]+$/.test(field.value) || (field.value.includes(marker) && !ROURKE_NOTES.includes(field.name)))))
        .filter(field => !squashed.includes(squash(field.value))).map(field => field.name);
      h.assert(!missing.length, `the PDF does not carry the typed ${missing.join(', ')}`);
      entry.printBody = body;
    });
    const printed = passed(entry, 'print');
    // The concerns below read the PDF Print answered and drive no page, so they take no browser problem.
    const afterPrint = async (concern, body) => {
      if (!printed) blocked(entry, concern, 'Print produced no readable PDF'); else await conclude(entry, concern, body, { take: false });
    };
    // Page I of the printed record, read once, as words with the box each occupies.
    let pageOne;
    const printedPageOne = async () => {
      if (!pageOne) pageOne = await claims.asPrecondition(async () => pdfGraph.pageWords(entry.printBody, 1), 'reading the words of printed page I');
      claims.precondition(pageOne.width === PRINT_PAGE.width && pageOne.height === PRINT_PAGE.height,
        `printed page I measures ${pageOne.width} x ${pageOne.height}, not the ${PRINT_PAGE.width} x ${PRINT_PAGE.height} the template's boxes are measured on`);
      return pageOne.words;
    };
    await afterPrint('printnull', async () => {
      const nulls = nullWords(entry.printText);
      h.assert(nulls === 0, `the printed record shows the word "null" ${nulls} times, where a visit date was left empty`);
    });
    await afterPrint('printsex', async () => {
      // The mark is judged where the template places it (the M and F boxes of page I), not as a count of x anywhere on the page.
      const marks = sexMarks(await printedPageOne());
      const own = PATIENT_SEX === 'F' ? 'female' : 'male';
      const other = PATIENT_SEX === 'F' ? 'male' : 'female';
      h.assert(marks[own] === 1 && marks[other] === 0,
        `the printed record puts ${marks[own]} mark(s) in the ${own} box and ${marks[other]} in the ${other} box, for a ${own} patient`);
    });
    await afterPrint('printgestation', async () => {
      // The gestational age is read from the cell the template places it in, not from the first "N weeks" anywhere in the record.
      const shown = printedGestation(await printedPageOne());
      const weeks = /^(\d+) weeks?$/.exec(shown);
      h.assert(weeks && weeks[1] === '40', `the printed gestational age is ${shown ? JSON.stringify(shown) : 'blank'} for a pregnancy that started exactly 40 weeks before the birth`);
    });
    await afterPrint('printnotes', async () => {
      const squashed = squash(entry.printText);
      const dropped = ROURKE_NOTES.filter(name => !squashed.includes(squash(entry.fields.find(field => field.name === name).value)));
      h.assert(!dropped.length, `the printed record does not carry the notes typed in ${dropped.join(', ')}`);
    });
  }

  /**
   * Rourke 2020's page offers 123 text boxes and 1,397 radio buttons and checkboxes, mapped to a table of columns and a list of field
   * names by hand-written code (FormRourke2020, FormRourke2020Constants). A second, new record types a distinct value into every
   * box and ticks every button, saves, and asks: is each value in its column, are the ticks stored, and does the redisplayed form
   * show them? A box with no column (the two Immunization boxes of pages III and IV) shows up as text that never comes back.
   * It is a record of its own so that the typed record the other concerns print and graph stays an ordinary one (a record with
   * every box ticked takes twice as long to print).
   */
  async function rourkeSweep(entry) {
    if (!passed(entry, 'open')) { for (const concern of SWEEP) blocked(entry, concern, 'the form did not open'); return; }
    let boxes;
    try {
      await openForm(entry);
      await findBoxes(entry);
      boxes = entry.page;
    } catch (error) {
      for (const concern of SWEEP) blocked(entry, concern, `the form did not open again: ${error.message.split('\n')[0]}`);
      await closeAll(entry);
      return;
    }
    let sweepId = null;
    await conclude(entry, 'sweep', async () => {
      await boxes.evaluate(({ extras }) => {
        for (const { name, value } of extras) { const element = document.querySelector(`#frmP1 [name="${name}"]`); if (element) element.value = value; }
        for (const element of document.querySelectorAll('#frmP1 input[type="radio"], #frmP1 input[type="checkbox"]')) element.checked = true;
      }, { extras: entry.extras });
      await pressSave(entry, { exactRows: false });
      const { landed, id } = await redisplayedId(entry);
      sweepId = id;
      const stored = entry.extras.filter(extra => extra.stored);
      const wrong = [];
      if (stored.length) {
        const [row] = sql.rows(`SELECT ${stored.map(extra => `\`${extra.name}\``).join(',')} FROM formRourke2020 WHERE ID=${Number(id)} AND demographic_no=${patient}`);
        stored.forEach((extra, index) => { if (!row || row[index] !== extra.value) wrong.push(extra.name); });
      }
      const ticked = new Set(sql.rows(`SELECT field_name FROM form_boolean_value WHERE form_name='formRourke2020' AND form_id=${Number(id)} AND value=1`).map(r => r[0]));
      const notTicked = entry.radios.filter(name => !ticked.has(name));
      const state = await landed.evaluate(({ extras, radios }) => ({
        values: extras.map(({ name }) => { const element = document.querySelector(`#frmP1 [name="${name}"]`); return element ? element.value : null; }),
        unticked: radios.filter(name => { const element = document.querySelector(`#frmP1 [name="${name}"]`); return element && !element.checked; }),
      }), { extras: entry.extras, radios: entry.radios });
      const lost = [];
      entry.extras.forEach((extra, index) => { if (state.values[index] !== extra.value) (extra.stored ? wrong : lost).push(extra.name); });
      entry.lost.sweep = lost.length ? `the redisplayed form does not show the typed ${lost.join(', ')}: nothing stores what is typed in them` : null;
      const problems = [];
      if (wrong.length) problems.push(`the stored or redisplayed ${wrong.slice(0, 6).join(', ')}${wrong.length > 6 ? ` and ${wrong.length - 6} more` : ''} differ from what was typed`);
      if (notTicked.length) problems.push(`${notTicked.length} ticked boxes were not stored (${notTicked.slice(0, 3).join(', ')}...)`);
      if (state.unticked.length) problems.push(`${state.unticked.length} ticked boxes are not shown ticked again (${state.unticked.slice(0, 3).join(', ')}...)`);
      h.assert(!problems.length, problems.join('; '));
    }, { carry: true });
    if (!passed(entry, 'sweep') && sweepId === null) blocked(entry, 'storage', 'the sweep record was not saved');
    else if (entry.lost.sweep === undefined) blocked(entry, 'storage', 'the redisplayed sweep record could not be read');
    else record(entry, 'storage', outcomeOfMessage(entry.lost.sweep));
    await closeAll(entry);
  }

  // ---- the Growth forms ------------------------------------------------------------------------------------------
  /** Press one of a Growth form's print buttons and return the PDF it answers (createpdf is the last request of the chain). */
  async function pressGrowthButton(entry, label, nth = 0) {
    const button = entry.page.getByRole('button', { name: label, exact: true }).nth(nth);
    const answer = await answerTo(entry, isCreatePdf, () => button.click({ noWaitAfter: true }));
    return assertPdf(answer, label);
  }
  async function growthPrints(entry) {
    const { form } = entry;
    const buttons = form.key === 'growthchart'
      ? [['Print Growth', 0], ['Print Growth', 1]]
      : [['Print Growth', 0], ['Head Circ(1)', 0], ['Head Circ(2)', 0]];
    const minPoints = form.key === 'growthchart' ? CHART_POINTS : GROWTH_ROWS * 2;
    const pdfs = [];
    await conclude(entry, 'print', async () => {
      for (const [label, nth] of buttons) {
        const body = await pressGrowthButton(entry, label, nth);
        const printed = pdfTextBuffer(body);
        h.assert(squash(printed).includes(squash(marker)), `${label}${nth ? ` (block ${nth + 1})` : ''} does not carry the typed text`);
        pdfs.push({ label, body, printed });
      }
    });
    // graph and printdob read the PDFs Print answered and drive no page, so they take no browser problem.
    if (!passed(entry, 'print')) blocked(entry, 'graph', 'Print produced no readable PDF');
    else {
      await conclude(entry, 'graph', async () => {
        for (const { label, body } of pdfs) judgeChart(entry, `${form.title} ${label}`, body, { pages: GROWTH_PRINT_PAGES, points: minPoints });
      }, { take: false });
    }
    if (!passed(entry, 'print')) blocked(entry, 'printdob', 'Print produced no readable PDF');
    else {
      await conclude(entry, 'printdob', async () => {
        // The rows' own dates are all after the birth date, so the birth date can only come from the chart's DOB line.
        const squashed = squash(pdfs[0].printed);
        const shown = [`${dob.y}/${pad(dob.m)}/${pad(dob.d)}`, `${dob.y}-${pad(dob.m)}-${pad(dob.d)}`, dmy(dob), `${pad(dob.d)}-${pad(dob.m)}-${dob.y}`]
          .some(format => squashed.includes(squash(format)));
        h.assert(shown, 'the printed chart leaves its DOB line empty: the patient date of birth is nowhere on it');
      }, { take: false });
    }
    if (form.key === 'growthchart') {
      // Print BMI answers a PDF or it does not: pressing the button is the control, the answer is the pinned assertion.
      await conclude(entry, 'printbmi', async () => {
        const body = await pressGrowthButton(entry, 'Print BMI', 0);
        h.assert(squash(pdfTextBuffer(body)).includes(squash(marker)), 'Print BMI does not carry the typed text');
      });
    }
  }

  // ---- the flows -------------------------------------------------------------------------------------------------
  /** One form's main flow: open to print. It returns early when a concern it depends on failed; the caller still runs what follows. */
  async function mainFlow(entry) {
    const { form } = entry;
    if (!await openAndKeys(entry)) return;
    // Everything from the Save on is judged against the typed values; a measurement import that failed is typed instead
    // (rourkeMeasurements), so that one defect is not reported by two pairs.
    if (form.key === 'rourke2020') await rourkeMeasurements(entry);
    if (!await saveAndRedisplay(entry, { typeMeasures: entry.importFailed || new Set() })) return;
    await reopen(entry);
    if (!entry.page) {
      blockAll(entry, 'the saved record did not reopen', ['restore']);
      concludeRestore(entry);
      return;
    }
    if (form.key === 'rourke2020') await rourkeGraphsAndPrint(entry);
    else await growthPrints(entry);
    concludeRestore(entry);
    await closeAll(entry);
  }
  for (const entry of entries) {
    const { form } = entry;
    entry.pages = [];
    await mainFlow(entry);
    // The sweep is a second, slow save (every box ticked): an entry that does not assert it does not make it.
    if (form.key === 'rourke2020' && SWEEP.some(concern => wanted.has(claims.claimKey(form.key, concern)))) await rourkeSweep(entry);

    // The Rourke graph reads the Growth 0-36 rows of the same patient: once they are saved, ask the Rourke graph again.
    const rourke = byKey('rourke2020');
    if (form.key === 'growth036' && rourke && rourke.graph && rourke.graph.headCirc) {
      // The graph is fetched again from the link's address and no page is driven, so no browser problem is taken.
      await conclude(rourke, 'graphgrowth', async () => {
        claims.precondition(passed(entry, 'save'), 'the Growth 0-36 form did not save');
        claims.precondition(Number.isInteger(rourke.graph.headCirc.baseline), 'the Head Circumference graph could not be read before the Growth 0-36 rows were saved');
        const answer = await s.context.request.get(new URL(rourke.graph.headCirc.url, chart.url()).toString());
        const body = assertPdf({ status: answer.status(), type: answer.headers()['content-type'] || '', body: await answer.body() }, 'the Head Circumference graph');
        const plotted = pdfGraph.plottedPoints(body)[0];
        // Every Growth 0-36 row is in range and adds its weight-for-age and length-for-age points to the chart.
        const floor = rourke.graph.headCirc.baseline + GROWTH_ROWS * 2;
        detail(rourke, `the Head Circumference graph plots ${plotted} points with the Growth 0-36 rows saved (${rourke.graph.headCirc.baseline} before; floor ${floor})`);
        h.assert(plotted >= floor, `the Head Circumference graph plots ${plotted} points with ${GROWTH_ROWS} Growth 0-36 rows, ${rourke.graph.headCirc.baseline} without them (two points for each row are expected)`);
      }, { take: false });
    }
  }
  const rourkeEntry = byKey('rourke2020');
  if (rourkeEntry) blocked(rourkeEntry, 'graphgrowth', 'the Rourke graphs or the Growth 0-36 form were not reached');

  // ---- Phase 3: assert the claimed pairs, in table order ----
  excuseViewerAborts();
  for (const entry of entries) {
    for (const concern of entry.form.concerns.filter(name => wanted.has(claims.claimKey(entry.form.key, name)))) {
      const label = stepLabel(entry.form.key, concern);
      // Only the pair's own failure carries the pinned label; a precondition, a concern that was not reached and
      // browser problems beyond the known one are reported under a label of their own (lib/form-claims.js).
      const failure = claims.claimFailure(entry.results[concern], label);
      if (failure) throw h.markFailedStep(new Error(failure.message), failure.label);
      console.log(`  ASSERTED ${NAME}: ${label}`);
    }
  }
}

/** A precondition of the claimed pairs, run before anything is created. It is reported under a label of its own, which no manifest pins. */
async function preconditionStep(label, body) {
  try {
    await body();
  } catch (error) {
    throw h.markFailedStep(error, `${label} (precondition)`);
  }
  console.log(`  PASS ${NAME}: ${label}`);
}

if (require.main === module) runWorkflow(NAME, workflow, {
  preflight: () => requirePoppler('pdftotext', 'pdfinfo', 'pdftoppm'),
  contextOptions: { timezoneId: RUN_ZONE },
});
module.exports = {
  workflow, FORMS, validatePin, stepLabel, generatedLabel, PINNED, claimForms: CLAIM_FORMS,
  zoneWhereTodayDiffersFromUtc, localDate, rourkeFields, chartFields, growthFields, day, nullWords, dmy, iso, INFANT_DAYS,
  ROURKE_LENGTH_WEIGHT_POINTS, ROURKE_HEAD_POINTS, CHART_POINTS, GROWTH_ROWS, MEASURE_OR_DATE, ROURKE_NOTES,
  ROURKE_LENGTH_WEIGHT_PAGES, ROURKE_HEAD_PAGES, ROURKE_PRINT_PAGES, GROWTH_PRINT_PAGES, PRINT_PAGE, PRINT_BOXES, BOX_SLACK,
  PATIENT_SEX, utcDate, sexMarks, printedGestation, judgeMeasureDate,
};
