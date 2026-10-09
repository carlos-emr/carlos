#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * A lab result created in CARLOS flows into the patient's measurements, the Diabetes Flowsheet and the Health
 * Tracker (coverage plan: lab-to-flowsheet).
 *
 * WHY THIS CHECK EXISTS. A lab is not only a report to read. When it is filed, Hl7textResultsData
 * .populateMeasurementsTable copies every result line into `measurements`: the lab's test code is looked up in
 * measurementMap (the lab's row, then the FLOWSHEET row that shares its LOINC code) and the result becomes a
 * measurement of that flowsheet type (type = 'A1C' for CML 3767), with measurementsExt rows (lab_no, identifier,
 * unit, range, ...) tying it back to the lab. A code the flowsheets do not map is filed with an EMPTY type: a
 * lab-only record the Lab Result views read, which no flowsheet, tracker or Measurements line may show. The
 * flowsheet and the tracker then read the same rows by type. lab-manual-entry-cumulative covers the lab views;
 * health-tracker and diagnosis-flowsheet cover entering a measurement by hand. Nothing else follows a lab result
 * into the places a clinician manages diabetes from.
 *
 * User path: Schedule > Inbox > Create Lab (CML; Add Test; Submit to EMR) for the run's patient, two or three times
 * (below); the patient's E-Chart > Diagnostic Registry (Dx Research) > icd9 250 > Add, which makes the Diabetes
 * Flowsheet (diab2, dxcode_triggers icd9:250) appear in the Measurements module; the module's A1C line; the
 * Diabetes Flowsheet; and the Health Tracker (which ships empty, so the A1C item is added to this patient's
 * tracker with a flowsheet_customization row, exactly as health-tracker seeds it).
 *
 * The labs, all CML 3767 HEMOGLOBIN A1C unless said:
 *   - an A1C WITHIN the flowsheet's target (dated 2026-06-15, observed at 12:15: the noon hour, which the handler's
 *     12-hour time pattern misreads, finding 259; only the date reaches the flowsheet, so no other step minds),
 *   - an A1C ABOVE it (2026-09-30), filed together with CML 2010 HEMOGLOBIN 137 g/L, a code measurementMap maps to
 *     LOINC 718-7 but which no FLOWSHEET row shares, so it is the unmapped code,
 *   - only in the entry that pins finding 257: an A1C reported as a FRACTION, 0.095 (dated 2026-08-15), the form
 *     the A1C measurement type's own range (Range:0.040-0.200) and diab-A1C.drl describe.
 * Two entries file one more lab AFTER step 9, so the steps that count labs, rows and typed measurements never see it:
 *   - the entry for finding 256: Create Lab files a CML 3180 Creatinine of 112.5 umol/L, a mapped code that is not
 *     the A1C (it is the flowsheet type SCR) and whose first five characters are digits and '.',
 *   - the entry for finding 262: Inbox > HL7 Lab Upload files a CML A1C whose observation time has no seconds.
 * "Within" and "above" are judged against the target the flowsheet itself states ("Target <= 7.0%" on its A1C
 * row), read from the flowsheet before the labs are created, so no threshold is hard-coded: the values are
 * derived from it.
 *
 * Asserts, by step (labels are the manifest's expectedFailure steps, where a pin is named):
 *   1. the flowsheet states a numeric A1C target;
 *   2. each lab reaches hl7TextInfo under its accession, is matched to the patient and routed to the provider;
 *   3. each A1C result is a measurement with type A1C, the typed value and date, and measurementsExt rows
 *      lab_no, identifier 3767, the unit and the observation time the lab gave;
 *   4. the unmapped result is filed with an empty type (no flowsheet type), and no other typed measurement exists;
 *   5. Dx 250 through the registry makes the chart's Measurements module list the Diabetes Flowsheet;
 *   6. the module's A1C line shows the latest result and date and no line exists for the unmapped test;
 *   7. the flowsheet's A1C row lists each result with its date, and nothing else on the flowsheet holds a value;
 *   8. the flowsheet flags the result above its target and not the one within it;
 *   9. the Health Tracker lists each result under its date and does not list the unmapped test.
 *
 * ONE ENTRY PER DEFECT. A script stops at its first failing step, so one run cannot pin four defects. The
 * LAB_FLOWSHEET_PIN variable picks the entry (the manifest runs the script once per value):
 *   unset     The path above, then LAST and PINNED to finding 258: the flowsheet's A1C row links to the lab it
 *             came from. measurementsExt.lab_no is stored but no flowsheet, tracker or A1C history page reads it;
 *             the row only opens the Add Measurement form for the value.
 *   value     Pinned to finding 256, LAST (after step 9): a creatinine of 112.5 umol/L is filed as the SCR
 *             measurement with the value the lab sent. The shipped HL7_LAB_MEASUREMENT_FILTER has the alternative
 *             ^([+-\\?]{1,4}), and in a character class +-\? is a RANGE (0x2B-0x3F: the digits, '.', ...), so
 *             find() takes the first four characters of any such number: 112.5 becomes 112. The value is not the
 *             A1C fraction of the entry for 257, so a fix of 257 that normalises fractions on import cannot decide
 *             this pin, and a creatinine is mapped to a flowsheet type of its own, so the A1C steps never see it.
 *   time      Pinned to finding 259, right after step 4: a result observed at 12:15 is filed with that time of day.
 *             The handler parses the 24-hour observation time with the 12-hour pattern hh, which reads 12 as 0, so
 *             12:15 is stored as 00:15 (the date is right; the order of same-day results is not).
 *   fraction  Pinned to finding 257, right after step 8: the flowsheet flags an A1C reported as a fraction above
 *             the target. omdDiabetesFlowsheet.xml flags only a value above 7; its fraction rule is commented out.
 *   seconds   Pinned to finding 262, LAST (after step 9): a lab UPLOADED with an observation time that has no seconds
 *             (OBR-7 of 12 digits) is filed as observed at that time. CMLHandler.formatDateTime returns
 *             "yyyy-MM-dd HH:mm" for it, Hl7textResultsData parses with "yyyy-MM-dd hh:mm:ss", the parse throws and
 *             the catch files the upload time instead. Create Lab always writes seconds, so only an upload reaches it.
 * The steps after a pin run once its finding is fixed. A step named in an expectedFailure holds only the assertion
 * its finding breaks; the controls (the row exists, the flag works for a percentage, the measurement is filed with
 * its type and lab number) are earlier steps with their own labels, and they hand the pinned step what it judges (the
 * measurement row, the flowsheet row's markup, whether it carries a flag), so a pinned step cannot fail for want of a
 * page or a row, which the runner would read as the known defect.
 *
 * Fixtures: the run's FAKE-PW patient (runWorkflow), two or three labs under unique accessions, their routing,
 * measurements and measurementsExt rows, one dxresearch row and one flowsheet_customization row (tracker). Cleanup
 * removes each lab (removeOwnedHl7Labs: routing and its lock, info, message, checksum, archived file, measurements) and then
 * every remaining measurement and measurementsExt row, dxresearch row and flowsheet_customization row of the
 * patient, and asserts each by key. No clinic-wide state changes.
 *
 * Expected: every step passes except the pinned one of each entry (see the manifest).
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { submitCreateLab } = require('./lab-manual-entry-cumulative-playwright-checks');
const { removeOwnedHl7Labs, removeArchiveFiles, archivesNamed, uploadFromInbox } = require('./lab-forwarding-rules-playwright-checks');

const TIMEOUT = 30000;
const PINS = ['value', 'fraction', 'time', 'seconds'];
// LAB_FLOWSHEET_PIN selects which finding the run pins (see the header); judged when the check runs, never when
// the module is required, so requiring it with a bad value (a meta-test does) is harmless.
const PIN = (process.env.LAB_FLOWSHEET_PIN || '').trim();
/** LAB_FLOWSHEET_PIN must be unset or one of value, fraction, time, seconds. */
function validatePin(value = PIN) {
  if (!['', ...PINS].includes(value)) {
    throw new Error(`LAB_FLOWSHEET_PIN must be unset or ${PINS.join(', ')}, not ${value}`);
  }
}

/** The step labels the manifest's expectedFailure entries name; one place, so the script and the manifest agree. */
const STEP = {
  value: 'a creatinine result of five characters is filed as a measurement with the value the lab sent',
  fraction: 'the Diabetes Flowsheet flags an A1C reported as a fraction above its target',
  time: 'a result observed in the noon hour is filed as a measurement with the time of day the lab gave',
  seconds: 'a result uploaded with an observation time that has no seconds is filed as a measurement observed at that time',
  link: 'the Diabetes Flowsheet A1C row links to the lab it came from',
};

// CML test codes. 3767 maps to LOINC 4548-4, which the FLOWSHEET row A1C shares. 2010 maps to LOINC 718-7, which no
// FLOWSHEET row shares (the check proves that against measurementMap when it runs).
const A1C = { code: '3767', name: 'HEMOGLOBIN A1C' };
const UNMAPPED = { code: '2010', name: 'HEMOGLOBIN', value: '137', unit: 'g/L', low: '120', high: '160', flag: 'N' };
// CML 3180 Creatinine maps to the FLOWSHEET type SCR (measurementType "in umol/L"), so it is a mapped value that is not
// the A1C; 112.5 has five characters, all of them digits or '.', which the shipped result filter cuts to four (finding 256).
const LONG = { code: '3180', name: 'Creatinine', value: '112.5', unit: 'umol/L', low: '45', high: '110', flag: 'A', type: 'SCR' };
const DATES = { within: '2026-06-15', fraction: '2026-08-15', above: '2026-09-30', long: '2026-09-01' };
// The observation time of the within-target lab: the noon hour, the one hour a 12-hour clock misreads (finding 259).
const NOON = '12:15';
// The uploaded lab's observation time (finding 262): a morning hour, so a 12-hour pattern could not misread it (259).
const UPLOAD_TIME = { date: '20260820', time: '0945' };
const DIAB_TEMPLATE = 'diab2';

/** The flowsheet's own A1C target ("Target <= 7.0%") as a number, or null. */
function targetOf(guideline) {
  const match = /Target\s*(?:<=?|<|≤)\s*(\d+(?:\.\d+)?)/.exec(String(guideline || '').replace(/&lt;/g, '<'));
  return match ? Number(match[1]) : null;
}

/**
 * The tooltip title of the A1C item's heading in the flowsheet page's HTML ("fade=[on] header=[A1C] body=[Target
 * &lt;= 7.0%]"), or null. The attributes are matched loosely: the page prints `<p class="noborder" <%=colour%> title=`,
 * so a blank colour leaves two spaces between them.
 */
function a1cItemTitle(html) {
  const match = /<div class="preventionSection" id="A1C"[\s\S]*?<p class="noborder"[^>]*?\btitle="([^"]*)"/.exec(String(html || ''));
  return match ? match[1] : null;
}

/**
 * Whether markup (a flowsheet row's outerHTML) holds a link to the report of lab `labNo`: a lab display URL carrying
 * segmentID=<labNo> exactly. The lab report route is /lab/CA/ALL/ViewLabDisplay?segmentID=<lab_no> (also its Ajax and
 * legacy spellings); a measurement id that merely equals the lab number is not a link to it.
 */
function linksToLab(html, labNo) {
  const number = String(labNo).replace(/\D/g, '');
  if (!number) return false;
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- number holds digits only: every other character was removed from the lab number earlier in this function
  return new RegExp(`(?:ViewLabDisplay|labDisplay|CMLDisplay)\\w*(?:\\.jsp)?\\?[^'"\\s]*segmentID=${number}(?!\\d)`).test(String(html || ''));
}

/** An A1C test the Create Lab form takes: the lab's own reference range is the usual non-diabetic one. */
function a1cTest(value, { unit = '%', low = '4.0', high = '6.0', flag = 'A', time } = {}) {
  return { ...A1C, value, unit, low, high, flag, time };
}

/**
 * A CML ORU^R01 for the HL7 Lab Upload page: one result for the run's FAKE patient, shaped like lab-upload's synthetic
 * message (the PID that matches the patient by name, date of birth and sex), with the observation time `observed`
 * (OBR-7) exactly as given. Create Lab writes every time with seconds (CMLLabHL7Generator, 14 digits), so only a file
 * can carry the 12-digit time of finding 262.
 *
 * @param {{accession: string, last: string, test: object, observed: string}} lab `test` is a Create Lab test
 *   ({code, name, value, unit, low, high, flag}); `observed` is yyyyMMdd, yyyyMMddHHmm or yyyyMMddHHmmss
 */
function cmlUploadHl7({ accession, last, test, observed }) {
  const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 12);
  return [
    `MSH|^~\\&|Reports|CML|||${stamp}-500||ORU^R01||1|2.3`,
    `PID|1|||^^ON|${last}^Workflow||19800102|F`,
    `ORC|NW|${accession}|||F|||||||999998^DR. PROBE|||${observed.slice(0, 8)}`,
    `OBR|1|${accession}||ML70^SYNTHETIC PANEL||${observed.slice(0, 8)}083000|${observed}|||||||||999998^DR. PROBE|||||||||F`,
    `OBX|1|ST|${test.code}^${test.name}|^^CHEMISTRY|${test.value}|${test.unit}|${test.low}-${test.high}|${test.flag}|||F||765^1007010||70`,
    'NTE|1|L|SYNTHETIC LAB UPLOAD BROWSER CHECK - NOT A PATIENT RESULT',
    'FTS|1',
    '',
  ].join('\r');
}

async function workflow(s) {
  validatePin();
  const { sql, patient, provider, context } = s;
  const needsFraction = PIN === 'fraction';
  const labs = {}; // key -> { accession, labNo, date, tests }
  const claimed = [];
  const uploaded = []; // the upload file names of the 'seconds' entry, so cleanup can find a file whose lab never stored
  let workDir = null; // the 'seconds' entry's temporary directory; made after the cleanup that removes it is registered
  // What the control steps read, kept for the pinned steps: a pinned step is then a judgment on a value in hand and
  // cannot fail for want of a page, a row or a lab, which the runner would read as the known defect.
  const filed = {}; // lab key -> its A1C measurement (step 3)
  const rowHtml = {}; // lab key -> the flowsheet row's markup (step 7)
  const flags = {}; // lab key -> whether the flowsheet row carries an indicator colour (step 8)
  const keyed = (labNo) => h.sqlString(String(labNo));

  // ---- Cleanup, registered before anything is written; the cleanups run in reverse order -----------------------
  // Last: whatever measurement, dx or customization rows are left for the patient (a lab that failed halfway, the
  // tracker item), each deleted by the patient key and asserted gone.
  s.cleanup(() => {
    sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (SELECT id FROM measurements WHERE demographicNo=${patient});
      DELETE FROM measurements WHERE demographicNo=${patient};
      DELETE FROM dxresearch WHERE demographic_no=${patient};
      DELETE FROM flowsheet_customization WHERE demographic_no=${h.sqlString(String(patient))}`);
    const left = (table, where) => sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${where}`);
    h.assert(left('measurements', `demographicNo=${patient}`) === '0', 'The patient\'s measurement rows were not removed');
    h.assert(left('measurementsExt', `keyval='lab_no' AND val IN (${claimed.map((a) => keyed(a.labNo)).join(',') || "''"})`) === '0',
      'The labs\' measurementsExt rows were not removed');
    h.assert(left('dxresearch', `demographic_no=${patient}`) === '0', 'The patient\'s diagnosis rows were not removed');
    h.assert(left('flowsheet_customization', `demographic_no=${h.sqlString(String(patient))}`) === '0',
      'The patient\'s flowsheet customization rows were not removed');
  });
  // First: each lab by its accession (and the lab number, if the accession lookup came too late), asserting its
  // routing, info, message, checksum and archive rows gone.
  s.cleanup(() => {
    const labNos = [];
    for (const lab of claimed) {
      labNos.push(...sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(lab.accession)}`).map(([id]) => id));
      if (lab.labNo) labNos.push(lab.labNo);
    }
    removeOwnedHl7Labs(sql, labNos);
    if (workDir) fs.rmSync(workDir, { recursive: true, force: true });
    // An upload that failed before a lab was stored leaves its checksum row and archive without any lab row to find them by;
    // the run's unique upload name still identifies both.
    for (const fileName of uploaded) {
      const pattern = h.sqlString(`LabUpload.${fileName}.%`);
      sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${pattern}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ${pattern}`) === '0',
        'The run\'s lab upload checksum row was not removed');
      removeArchiveFiles(archivesNamed(fileName));
    }
  });

  /** Claims an accession no lab uses yet and records the lab so cleanup finds it. */
  const claim = (key, date, tests) => {
    const accession = `ML${crypto.randomBytes(4).toString('hex').toUpperCase()}`;
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`) === '0',
      `The ${key} lab accession is already in use`);
    const lab = { key, accession, labNo: null, date, tests };
    labs[key] = lab;
    claimed.push(lab);
    return lab;
  };

  /** The measurement rows a lab filed for the patient: [{ id, type, value, date, observed, ext: { keyval: val } }]. */
  const measurementsOf = (lab) => sql.rows(`SELECT m.id, m.type, m.dataField, DATE(m.dateObserved), m.dateObserved FROM measurements m
      JOIN measurementsExt l ON l.measurement_id=m.id AND l.keyval='lab_no' AND l.val=${keyed(lab.labNo)}
      WHERE m.demographicNo=${patient} ORDER BY m.id`).map(([id, type, value, date, observed]) => ({
    id, type, value, date, observed,
    ext: Object.fromEntries(sql.rows(`SELECT keyval, val FROM measurementsExt WHERE measurement_id=${id}`)),
  }));
  const a1cOf = (lab) => {
    const rows = measurementsOf(lab).filter((row) => row.ext.identifier === A1C.code);
    h.assert(rows.length === 1, `The ${lab.key} lab filed ${rows.length} measurement(s) for CML ${A1C.code}, expected 1`);
    return rows[0];
  };

  // ---- 1. The flowsheet's own target, read before any lab exists ------------------------------------------------
  let target;
  let values;
  await s.step('the Diabetes Flowsheet states an A1C target the lab results can be judged against', async () => {
    // The flowsheet page renders for any chart whatever its diagnoses; read it the way the Measurements module's own
    // link would (a GET of the same route) and take the A1C item's guideline, which the page writes as the item's
    // tooltip title: header=[A1C] body=[Target &lt;= 7.0%].
    const response = await context.request.get(h.appUrl(s.config.baseUrl,
      `/encounter/oscarMeasurements/ViewTemplateFlowSheet?demographic_no=${encodeURIComponent(patient)}&template=${DIAB_TEMPLATE}`));
    h.assert(response.status() === 200, `The Diabetes Flowsheet answered HTTP ${response.status()}`);
    const html = await response.text();
    const title = a1cItemTitle(html);
    h.assert(title !== null, 'The Diabetes Flowsheet has no A1C item');
    target = targetOf(title);
    h.assert(target !== null && target > 3 && target < 15,
      `The flowsheet's A1C item states no numeric target in per cent: ${JSON.stringify(title.slice(0, 120))}`);
    // Derived from the target, so the control and the defect keep their meaning if the clinic changes it.
    values = {
      within: (target - 0.7).toFixed(1),
      above: (target + 1.4).toFixed(1),
      fraction: ((target + 2.5) / 100).toFixed(3),
    };
    h.assert(Number(values.within) <= target && Number(values.above) > target && Number(values.fraction) * 100 > target,
      'The derived A1C values do not fall on the intended sides of the flowsheet target');
  });

  // ---- 2. Create Lab ---------------------------------------------------------------------------------------------
  /** The lab reached hl7TextInfo under its accession and is matched to the patient: records its lab number. */
  const assertStored = async (lab) => {
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(lab.accession)}`, '1',
      `The ${lab.key} lab did not reach hl7TextInfo under its accession`);
    lab.labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(lab.accession)}`);
    h.assert(/^[1-9]\d*$/.test(lab.labNo), `The ${lab.key} lab has no lab number`);
    h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${lab.labNo}`) === patient,
      `The ${lab.key} lab was not matched to the patient`);
  };
  /** Inbox > Create Lab for one planned lab, then the checks every Create Lab lab gets (stored, matched, routed to the submitter). */
  const createLab = async (lab) => {
    const { form, inbox } = await submitCreateLab(s, { accession: lab.accession, tests: lab.tests, label: `lab-flowsheet-${lab.key}`, date: lab.date });
    await assertStored(lab);
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${lab.labNo}
      AND provider_no=${h.sqlString(provider)}`) === '1', `The ${lab.key} lab was not routed to the submitting provider`);
    await form.close();
    if (inbox !== s.schedule) await inbox.close();
  };
  await s.step('Inbox ▸ Create Lab files the A1C results and an unmapped hemoglobin for the patient', async () => {
    const plan = [
      claim('within', DATES.within, [a1cTest(values.within, { time: NOON })]),
      claim('above', DATES.above, [a1cTest(values.above), UNMAPPED]),
    ];
    if (needsFraction) plan.push(claim('fraction', DATES.fraction, [a1cTest(values.fraction, { low: '0.040', high: '0.060', unit: 'fraction' })]));
    for (const lab of plan) await createLab(lab);
  });

  // ---- 3. The A1C measurements -----------------------------------------------------------------------------------
  await s.step('each A1C result is filed as an A1C measurement with its lab number, date and unit', async () => {
    for (const lab of Object.values(labs)) {
      const row = a1cOf(lab);
      filed[lab.key] = row;
      h.assert(row.type === 'A1C', `The ${lab.key} lab's CML ${A1C.code} result is filed with type ${JSON.stringify(row.type)}, not A1C`);
      h.assert(row.date === lab.date, `The ${lab.key} lab's A1C measurement is dated ${row.date}, not ${lab.date}`);
      h.assert(row.ext.lab_no === lab.labNo && row.ext.identifier === A1C.code && row.ext.name === A1C.name,
        `The ${lab.key} lab's A1C measurement does not carry its lab number, test code and name: ${JSON.stringify(row.ext)}`);
      h.assert(row.ext.unit === lab.tests[0].unit, `The ${lab.key} lab's A1C measurement carries the unit ${JSON.stringify(row.ext.unit)}`);
      // The time the lab gave, as the handler read it (the pinned time step judges what was stored against it).
      const given = `${lab.date} ${lab.tests[0].time || '09:00'}:00`;
      h.assert(row.ext.datetime === given, `The ${lab.key} lab's A1C measurement carries the observation time ${JSON.stringify(row.ext.datetime)}, not ${given}`);
      // The value of a result of four characters or fewer is exact; a longer one is the pinned step's question.
      if (lab.key !== 'fraction') {
        h.assert(row.value === lab.tests[0].value, `The ${lab.key} lab's A1C measurement holds ${JSON.stringify(row.value)}, not the ${lab.tests[0].value} entered`);
      }
    }
  });

  // ---- 4. The unmapped code ---------------------------------------------------------------------------------------
  await s.step('the unmapped hemoglobin result is filed with no flowsheet measurement type', async () => {
    // Proof that the code is unmapped: no measurementMap row with that identifier shares a LOINC code with a FLOWSHEET row.
    // The application's own lookup (MeasurementMapDaoImpl.findMeasurements) matches a.ident_code and the LOINC code and
    // never the lab type of a, so neither does this: a row of another lab type with the same identifier would map the result too.
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementMap a JOIN measurementMap b ON b.loinc_code=a.loinc_code
      AND b.lab_type='FLOWSHEET' WHERE a.ident_code=${h.sqlString(UNMAPPED.code)}`) === '0',
    `CML ${UNMAPPED.code} is mapped to a flowsheet measurement type, so it is not the unmapped code this check needs`);
    const rows = measurementsOf(labs.above).filter((row) => row.ext.identifier === UNMAPPED.code);
    h.assert(rows.length === 1, `The unmapped result was filed as ${rows.length} measurement(s); the lab-only record is expected once`);
    h.assert(rows[0].type === '' && rows[0].value === UNMAPPED.value && rows[0].ext.lab_no === labs.above.labNo,
      `The unmapped result is filed as type ${JSON.stringify(rows[0].type)}, value ${JSON.stringify(rows[0].value)}; it must carry no flowsheet type`);
    const typed = sql.rows(`SELECT DISTINCT type FROM measurements WHERE demographicNo=${patient} AND type<>''`).map(([type]) => type);
    h.assert(JSON.stringify(typed) === JSON.stringify(['A1C']), `The patient has measurements of type ${JSON.stringify(typed)}; only A1C is expected`);
  });

  // ---- Pin: finding 259 -------------------------------------------------------------------------------------------
  if (PIN === 'time') {
    // The control is step 3: the measurement is filed with its type, lab number, unit and date, and carries the time the
    // lab gave (measurementsExt datetime) in the form the pin compares against.
    await s.step(STEP.time, async () => {
      h.assert(filed.within.observed === filed.within.ext.datetime,
        `The lab gave the observation time ${filed.within.ext.datetime} and the measurement is dated ${filed.within.observed}`);
    });
  }

  // ---- 5. Dx 250 --------------------------------------------------------------------------------------------------
  let chart;
  await s.step('Diagnostic Registry ▸ Dx 250 adds the Diabetes Flowsheet to the chart Measurements module', async () => {
    chart = await s.chart();
    const registry = await s.popup(chart, chart.locator('a[onclick*="setupDxResearch"]').first(), 'lab-flowsheet-dx');
    await registry.locator('select[name="selectedCodingSystem"]').selectOption('icd9');
    await registry.locator('[name="xml_research1"]').fill('250');
    const results = await s.popup(registry, registry.locator('[name="codeSearch"]'), 'lab-flowsheet-dx-search');
    await results.locator('input[name="searchCodes"][value="250"]').check();
    // Confirm returns the code to the registry and closes the search window; the Add click must wait for that close, or it can
    // land while the registry page is still being handed the code (as dx-registry-status-update waits).
    const closed = results.waitForEvent('close');
    await results.locator('[name="confirm"]').click();
    await closed;
    h.assert(await registry.locator('[name="xml_research1"]').inputValue() === '250', 'Diagnosis search did not return code 250 to the entry form');
    await ui.clickAndAwaitReload(registry, registry.locator('[name="codeAdd"]'));
    await expectValue(sql, `SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient} AND dxresearch_code='250' AND status='A'`, '1',
      'The registry did not save diagnosis 250 for the patient');
    await registry.close();
    await chart.close();
    chart = await s.chart();
    const link = chart.locator(`#measurementslist a[onclick*="ViewTemplateFlowSheet"][onclick*="template=${DIAB_TEMPLATE}'"]`);
    await link.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert((await link.first().innerText()).trim() === 'Diabetes Flowsheet', 'The Measurements module names the flowsheet something else');
  });

  // ---- 6. The Measurements module ----------------------------------------------------------------------------------
  await s.step('the chart Measurements module lists the latest A1C result and no line for the unmapped test', async () => {
    const history = chart.locator('#measurementslist a[onclick*="SetupDisplayHistory?type="]');
    const types = await history.evaluateAll((els) => [...new Set(els.map((el) => /SetupDisplayHistory\?type=([^'&]*)/.exec(el.getAttribute('onclick'))[1]))]);
    h.assert(JSON.stringify(types) === JSON.stringify(['A1C']),
      `The Measurements module has history lines for ${JSON.stringify(types)}; only A1C is expected`);
    // The module title is "<type> <value> <date>" of the newest observation.
    const title = await history.first().getAttribute('title');
    h.assert(title === `A1C ${values.above} ${DATES.above}`, `The A1C line reads ${JSON.stringify(title)}, not the newest result`);
    h.assert(!/HEMOGLOBIN|\b137\b/.test(await chart.locator('#measurementslist').innerText()), 'The Measurements module shows the unmapped hemoglobin');
  });

  // ---- 7. The Diabetes Flowsheet ------------------------------------------------------------------------------------
  let flowsheet;
  const a1cRow = (lab) => flowsheet.locator('#A1C .preventionProcedure').filter({ hasText: lab.date });
  await s.step('the Diabetes Flowsheet lists every A1C result under its date and nothing for the unmapped test', async () => {
    flowsheet = await s.popup(chart, chart.locator(`#measurementslist a[onclick*="ViewTemplateFlowSheet"][onclick*="template=${DIAB_TEMPLATE}'"]`).first(), 'lab-flowsheet-diab');
    await flowsheet.waitForLoadState('networkidle').catch(() => {});
    h.assert(await flowsheet.locator('#A1C').count() === 1, 'The Diabetes Flowsheet has no A1C item');
    h.assert(targetOf(await flowsheet.locator('#A1C .headPrevention p').first().getAttribute('title')) === target,
      'The rendered A1C target is not the one the labs were derived from');
    for (const lab of Object.values(labs)) {
      const row = a1cRow(lab);
      h.assert(await row.count() === 1, `The A1C item has ${await row.count()} row(s) dated ${lab.date}, expected 1`);
      rowHtml[lab.key] = await row.evaluate((el) => el.outerHTML);
      // The value of the fraction lab is the pinned steps' question (the row shows what was filed).
      if (lab.key !== 'fraction') {
        const text = (await row.innerText()).replace(/\s+/g, ' ').trim();
        h.assert(text.startsWith(`A1C: ${lab.tests[0].value} ${lab.date}`), `The ${lab.key} lab's A1C row reads ${JSON.stringify(text)}`);
      }
    }
    // Every value on the flowsheet is an A1C result: the unmapped test reached no item, and no item was added for it.
    const shown = await flowsheet.locator('.preventionProcedure').count();
    h.assert(shown === Object.keys(labs).length, `The flowsheet shows ${shown} value(s) for ${Object.keys(labs).length} A1C result(s)`);
    h.assert(!/HEMOGLOBIN/i.test(await flowsheet.locator('#prevention-list').innerText()), 'The Diabetes Flowsheet lists the unmapped hemoglobin');
  });

  // ---- 8. Flags -----------------------------------------------------------------------------------------------------
  await s.step('the Diabetes Flowsheet flags the A1C above its target and not the one within it', async () => {
    for (const lab of Object.values(labs)) {
      flags[lab.key] = (await a1cRow(lab).locator('p').first().evaluate((el) => el.style.backgroundColor)) !== '';
    }
    h.assert(flags.above, `The A1C of ${values.above} is above the flowsheet's ${target} target and its row carries no flag`);
    h.assert(!flags.within, `The A1C of ${values.within} is within the flowsheet's ${target} target and its row is flagged`);
  });

  // ---- Pin: finding 257 -------------------------------------------------------------------------------------------
  if (PIN === 'fraction') {
    // The controls are steps 7 (the row is listed under its date) and 8 (a percentage above the target is flagged).
    await s.step(STEP.fraction, async () => {
      h.assert(flags.fraction,
        `The A1C of ${values.fraction} (${(Number(values.fraction) * 100).toFixed(1)}%) is above the flowsheet's ${target} target and its row carries no flag`);
    });
  }

  // ---- 9. The Health Tracker ----------------------------------------------------------------------------------------
  await s.step('the Health Tracker lists the A1C results under their dates and nothing for the unmapped test', async () => {
    // The tracker ships empty; this patient's tracker tracks A1C (the row health-tracker seeds, here for A1C).
    const item = '<item measurement_type="A1C" display_name="A1C" guideline="" graphable="yes" value_name="A1C" />';
    sql.execute(`INSERT INTO flowsheet_customization
      (flowsheet, action, measurement, payload, provider_no, demographic_no, create_date, archived)
      VALUES ('tracker','add',NULL,${h.sqlString(item)},${h.sqlString(provider)},${h.sqlString(String(patient))},NOW(),0)`);
    const tracker = await s.popup(chart, chart.locator('#measurementslist a[onclick*="ViewHealthTracker"]').first(), 'lab-flowsheet-tracker');
    await tracker.waitForLoadState('networkidle').catch(() => {});
    h.assert(await tracker.locator('#wrap-A1C').count() === 1, 'The Health Tracker has no card for the A1C item');
    for (const lab of [labs.within, labs.above]) {
      const entry = tracker.locator(`#wrap-A1C .history-value[data-observed="${lab.date}"]`);
      h.assert(await entry.count() === 1, `The tracker's A1C card has ${await entry.count()} value(s) dated ${lab.date}, expected 1`);
      h.assert(await entry.getAttribute('data-value') === lab.tests[0].value,
        `The tracker's A1C value dated ${lab.date} is ${JSON.stringify(await entry.getAttribute('data-value'))}, not ${lab.tests[0].value}`);
    }
    h.assert(await tracker.locator('.measurement-card').count() === 1, 'The Health Tracker shows a card besides the A1C one');
    h.assert(!/HEMOGLOBIN|\b137\b/i.test(await tracker.locator('#trackerForm').innerText()), 'The Health Tracker shows the unmapped hemoglobin');
    await tracker.close();
  });

  // ---- Pin: finding 256 (a lab of its own, after every step that counts labs and rows) ---------------------------
  if (PIN === 'value') {
    let longRow;
    await s.step('Inbox ▸ Create Lab files a creatinine of five characters as a measurement of its flowsheet type with its lab number, date and unit', async () => {
      const lab = claim('long', DATES.long, [{ ...LONG, time: '09:00' }]);
      await createLab(lab);
      const rows = measurementsOf(lab);
      h.assert(rows.length === 1, `The creatinine lab filed ${rows.length} measurements, expected 1`);
      longRow = rows[0];
      h.assert(longRow.type === LONG.type, `The creatinine result is filed with type ${JSON.stringify(longRow.type)}, not ${LONG.type}`);
      h.assert(longRow.date === DATES.long, `The creatinine measurement is dated ${longRow.date}, not ${DATES.long}`);
      h.assert(longRow.ext.lab_no === lab.labNo && longRow.ext.identifier === LONG.code && longRow.ext.name === LONG.name && longRow.ext.unit === LONG.unit,
        `The creatinine measurement does not carry its lab number, test code, name and unit: ${JSON.stringify(longRow.ext)}`);
    });
    // The control is the step above: the same measurement is filed with its type, date, lab number and unit.
    await s.step(STEP.value, async () => {
      h.assert(longRow.value === LONG.value, `The lab sent ${LONG.value} and the ${LONG.type} measurement holds ${JSON.stringify(longRow.value)}`);
    });
  }

  // ---- Pin: finding 262 (an upload of its own, after every step that counts labs and rows) ---------------------------
  if (PIN === 'seconds') {
    let uploadedRow;
    let given;
    await s.step('Inbox ▸ HL7 Lab Upload files a CML A1C whose observation time has no seconds, matched to the patient, with the time the file gave', async () => {
      const lab = claim('upload', null, [a1cTest(values.within)]);
      const fileName = `lab-flowsheet-upload-${lab.accession}.hl7`;
      uploaded.push(fileName);
      workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-flowsheet-'));
      const filePath = path.join(workDir, fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
      fs.writeFileSync(filePath, Buffer.from(cmlUploadHl7({ accession: lab.accession, last: s.marker, test: lab.tests[0],
        observed: `${UPLOAD_TIME.date}${UPLOAD_TIME.time}` }), 'latin1'));
      const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
        { context: s.context, recorder: s.recorder, label: 'lab-flowsheet-upload-inbox', timeout: TIMEOUT });
      const status = await uploadFromInbox(inbox, s.recorder, filePath, fileName);
      if (inbox !== s.schedule) await inbox.close();
      h.assert(status === 'Uploaded successfully', `The upload reported "${status}"`);
      await assertStored(lab);
      const rows = measurementsOf(lab);
      h.assert(rows.length === 1, `The uploaded lab filed ${rows.length} measurements, expected 1`);
      uploadedRow = rows[0];
      h.assert(uploadedRow.type === 'A1C' && uploadedRow.ext.lab_no === lab.labNo && uploadedRow.ext.identifier === A1C.code && uploadedRow.ext.unit === lab.tests[0].unit,
        `The uploaded A1C is not filed as an A1C measurement with its lab number, code and unit: ${JSON.stringify(uploadedRow)}`);
      // The time the lab gave, as the handler read it from OBR-7: the minute, with no seconds (the file carries none).
      given = `${UPLOAD_TIME.date.replace(/(\d{4})(\d{2})(\d{2})/, '$1-$2-$3')} ${UPLOAD_TIME.time.replace(/(\d{2})(\d{2})/, '$1:$2')}`;
      h.assert(uploadedRow.ext.datetime === given, `The upload's observation time was read as ${JSON.stringify(uploadedRow.ext.datetime)}, not ${given}`);
    });
    // The control is the step above: the measurement exists with its type, lab number and the time the file gave.
    await s.step(STEP.seconds, async () => {
      h.assert(uploadedRow.observed.slice(0, given.length) === given,
        `The file gave the observation time ${given} and the measurement is dated ${uploadedRow.observed}`);
    });
  }

  // ---- Pin: finding 258 -------------------------------------------------------------------------------------------
  if (PIN === '') {
    // The controls are step 7 (the row exists under its date, with the value) and step 3 (the measurement carries lab_no).
    await s.step(STEP.link, async () => {
      const labNo = labs.above.labNo;
      const html = rowHtml.above;
      h.assert(linksToLab(html, labNo), `The A1C row of lab ${labNo} carries no link to the lab: it opens only ${
        JSON.stringify((/fsPopup\(\d+,\d+,'([^'?]*)/.exec(html) || [])[1] || html.slice(0, 120))}`);
    });
  }
}

if (require.main === module) runWorkflow('lab-to-flowsheet', workflow, { contextOptions: { locale: 'en-US' } });
module.exports = { workflow, validatePin, STEP, targetOf, a1cItemTitle, linksToLab, cmlUploadHl7 };
