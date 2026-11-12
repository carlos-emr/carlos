#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * CDS (EMR Data Migration) export of one patient, five sections at once: Allergies and Adverse Reactions,
 * Medications and Treatments, Immunizations, Appointments and Care Elements. cds-export-lab-documents ticks
 * Laboratory Results (with Appointments and Clinical Notes as surrounding sections) and nothing checks the others.
 *
 * User path: the owned patient's master record ▸ Export (demographic/DemographicExport) ▸ tick the five sections ▸
 * Export; the download (PGP-encrypted by default) is decrypted and its zip read in memory.
 * Asserted: the export page is scoped to the patient; the zip holds one patient XML; each seeded entry lands in its
 * own section with its key fields (the allergy's agent and reaction, the drug's name and written date, the
 * immunization's name and date, the appointment's date, time and purpose, the height's value, unit and date); and the
 * whole file validates against the CDS schema.
 *
 * Fixtures: the owned FAKE- patient (lib/workflow-session.js) and one SQL-seeded row per section, each tied to that
 * patient and carrying the run's marker or FAKE- text (the immunization by its own id); cleanup deletes exactly those
 * rows and asserts they are gone. The appointment is today at 10:30 on the test login's provider, so it shows on that
 * provider's day sheet while the check runs (the manifest entry says not to run it alongside other checks). The export
 * is never written to disk by the check: Playwright's download file is deleted once read, and the decrypted zip and
 * XML stay in memory.
 *
 * ENCRYPTION, xmllint and gpg: as cds-export-lab-documents (CDS_EXPORT_GNUPGHOME decrypts the .pgp download; an
 * install that refuses the export for want of PGP, or a runner without gpg or xmllint, makes the check SKIP). The zip
 * reader and element lookup are that check's own exports; its PGP download, decryption and schema validation are
 * copied below. Moving those into a shared lib is a follow-up.
 * The section assertions are pinned without a deployment by scripts/cds-export-patient-sections.test.js.
 *
 * Environment: BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_* (fixture rows), CDS_EXPORT_GNUPGHOME.
 */
const path = require('node:path');
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { readZip, elements } = require('./cds-export-lab-documents-playwright-checks');

const SECTIONS = ['exAllergiesAndAdverseReactions', 'exMedicationsAndTreatments', 'exImmunizations', 'exAppointments', 'exCareElements'];

/** The first text of `name` inside `xml` (any namespace prefix), trimmed. */
function textOf(xml, name) {
  const [value] = elements(xml, name);
  return value === undefined ? undefined : value.trim();
}

/** The one entry of `section` whose `field` reads `value`; fails naming the section when it is missing or doubled. */
function entryOf(xml, section, field, value) {
  const found = elements(xml, section).filter(entry => textOf(entry, field) === value);
  h.assert(found.length === 1, `${section}: expected one entry with ${field} "${value}", found ${found.length}`);
  return found[0];
}

/** The calendar date written in a CDS date element (FullDate, or the date part of a FullDateTime). */
function fullDate(xml, name) {
  const [element] = elements(xml, name);
  if (element === undefined) return undefined;
  const value = textOf(element, 'FullDate') ?? textOf(element, 'FullDateTime');
  return value === undefined ? undefined : value.slice(0, 10);
}

/**
 * The Height readings in one CareElements block. CDS nests a Height value inside each Height reading (schema type
 * cdsd:height: Height, HeightUnit, Date), which elements() would cut at the inner close tag, so this reads the
 * schema's sequence as a whole.
 */
function heightsOf(careElements) {
  // Each tag takes its own prefix: the reading is declared in the main schema, its parts in the datatypes schema.
  const tag = (name, close = '') => `<${close}(?:[\\w.-]+:)?${name}>`;
  const part = name => `\\s*${tag(name)}([^<]*)${tag(name, '/')}`;
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- the element names are this file's own fixed literals
  const pattern = new RegExp(`${tag('Height')}${part('Height')}${part('HeightUnit')}${part('Date')}\\s*${tag('Height', '/')}`, 'g');
  return [...careElements.matchAll(pattern)].map(([, value, unit, date]) => ({ value: value.trim(), unit: unit.trim(), date: date.trim() }));
}

/** The element names in `xml`, in order and without their values: failure detail that carries no patient data. */
function tagNames(xml) {
  return [...xml.matchAll(/<([\w.-]+:)?([\w.-]+)[\s>]/g)].map(([, , name]) => name);
}

/**
 * One assertion per section, with its step label. Each check takes the patient XML and what the seeded rows must export
 * as; pure, so scripts/cds-export-patient-sections.test.js pins them against hand-built XML without a deployment.
 */
const SECTION_CHECKS = {
  allergy: {
    label: 'the allergy exports with its agent and reaction',
    check(xml, want) {
      const entry = entryOf(xml, 'AllergiesAndAdverseReactions', 'OffendingAgentDescription', want.allergy);
      h.assert(textOf(entry, 'Reaction') === want.reaction, `the allergy's reaction exported as ${JSON.stringify(textOf(entry, 'Reaction'))}`);
    },
  },
  medication: {
    label: 'the medication exports with its name and written date',
    check(xml, want) {
      const entry = entryOf(xml, 'MedicationsAndTreatments', 'DrugName', want.drug);
      const written = fullDate(entry, 'PrescriptionWrittenDate');
      h.assert(written === want.today, `the medication's written date exported as ${JSON.stringify(written)}, not ${want.today}`);
    },
  },
  immunization: {
    label: 'the immunization exports with its name and date',
    check(xml, want) {
      const entry = entryOf(xml, 'Immunizations', 'ImmunizationName', want.immunization);
      const given = fullDate(entry, 'Date');
      h.assert(given === want.today, `the immunization's date exported as ${JSON.stringify(given)}, not ${want.today}`);
    },
  },
  appointment: {
    label: 'the appointment exports with its date, time and purpose',
    check(xml, want) {
      const entry = entryOf(xml, 'Appointments', 'AppointmentPurpose', want.purpose);
      const day = fullDate(entry, 'AppointmentDate');
      h.assert(day === want.today, `the appointment's date exported as ${JSON.stringify(day)}, not ${want.today}`);
      const time = textOf(entry, 'AppointmentTime');
      h.assert(/^10:30:00/.test(time || ''), `the appointment's time exported as ${JSON.stringify(time)}, not 10:30:00`);
    },
  },
  height: {
    label: 'the height exports as a care element with its value, unit and date',
    check(xml, want) {
      const careElements = elements(xml, 'CareElements');
      const heights = careElements.flatMap(heightsOf).filter(height => height.value === want.height);
      h.assert(heights.length === 1, `CareElements: expected one Height of ${want.height}, found ${heights.length} `
        + `(${careElements.length} CareElements holding ${tagNames(careElements.join('')).slice(0, 12).join(', ') || 'nothing'})`);
      h.assert(heights[0].unit === 'cm', `the height's unit exported as ${JSON.stringify(heights[0].unit)}`);
      h.assert(heights[0].date.slice(0, 10) === want.today, `the height's date exported as ${JSON.stringify(heights[0].date)}, not ${want.today}`);
    },
  },
};

function assertValidCds(xml) {
  try {
    execFileSync('xmllint', ['--nonet', '--noout', '--schema',
      path.join(__dirname, '../src/main/resources/omdDataMigration/EMR_Data_Migration_Schema.xsd'), '-'],
    { input: Buffer.from(xml, 'utf8'), stdio: ['pipe', 'pipe', 'pipe'], timeout: 30000 });
  } catch (error) {
    if (error.code === 'ENOENT') throw new h.SkipCheck('xmllint (libxml2-utils) is needed to validate the CDS export');
    // Line and element only: xmllint's messages quote the offending value, which is patient data on a real chart.
    const where = [...String(error.stderr || '').matchAll(/^-:(\d+): element (\S+?):/gm)]
      .slice(0, 3).map(([, line, element]) => `line ${line} ${element}`);
    throw new Error(`the exported patient XML does not validate against the CDS schema${where.length ? ` (${where.join('; ')})` : ''}`);
  }
}

/** Submits the export; a refusal is a SKIP only when the page says PGP is not available (see the header). */
async function exportDownload(page) {
  const downloaded = page.waitForEvent('download', { timeout: 180000 }).then(download => ({ download }));
  const refused = page.locator('#exportErrorMessage').waitFor({ state: 'visible', timeout: 180000 })
    .then(() => ({ refused: true }));
  await page.locator('#DemographicExportForm input[type="submit"]').click();
  const outcome = await Promise.race([downloaded, refused]);
  downloaded.catch(() => {});
  refused.catch(() => {});
  if (outcome.download) return outcome.download;
  if (await page.locator('#pgpReady').inputValue() !== 'Yes') {
    throw new h.SkipCheck('the export was refused: this install exports encrypted files only and has no PGP '
      + 'configured (configure PGP_BIN/PGP_KEY/PGP_ENV and set CDS_EXPORT_GNUPGHOME)');
  }
  throw new Error('the export page reported that the export failed');
}

function decryptExport(encrypted) {
  const home = process.env.CDS_EXPORT_GNUPGHOME;
  if (!home) throw new h.SkipCheck('the export is PGP-encrypted; set CDS_EXPORT_GNUPGHOME to decrypt it');
  try {
    return execFileSync('gpg', ['--homedir', home, '--batch', '--quiet', '--decrypt'],
      { input: encrypted, stdio: ['pipe', 'pipe', 'pipe'], timeout: 60000, maxBuffer: 256 * 1024 * 1024 });
  } catch (error) {
    if (error.code === 'ENOENT') throw new h.SkipCheck('gpg is needed to decrypt the PGP-encrypted export');
    throw new Error('the PGP-encrypted export could not be decrypted with CDS_EXPORT_GNUPGHOME');
  }
}

/** One row per section, tied to the owned patient and tagged with the marker. Returns what each must export as. */
function seedSections(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const tag = marker.slice(-8);
  const want = {
    allergy: `FAKE-ALLERGEN ${tag}`,
    reaction: `FAKE rash ${tag}`,
    drug: `FAKE-DRUG ${tag}`,
    immunization: `FAKE-TD ${tag}`,
    purpose: `FAKE visit ${tag}`,
    height: '170',
  };
  let preventionId = null;
  // Registered before any insert, so a partial fixture still has an ownership-scoped cleanup.
  s.cleanup(() => {
    if (preventionId) {
      sql.execute(`DELETE FROM preventionsExt WHERE prevention_id=${preventionId};
        DELETE FROM preventions WHERE id=${preventionId} AND demographic_no=${patient}`);
    }
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient} AND DESCRIPTION=${q(want.allergy)};
      DELETE FROM drugs WHERE demographic_no=${patient} AND BN=${q(want.drug)};
      DELETE FROM appointment WHERE demographic_no=${patient} AND name=${q(marker)};
      DELETE FROM measurements WHERE demographicNo=${patient} AND type='HT' AND comments=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND DESCRIPTION=${q(want.allergy)})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND BN=${q(want.drug)})
      + (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient} AND name=${q(marker)})
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient} AND comments=${q(marker)})`) === '0',
    'Owned export section fixtures were not removed');
  });
  sql.execute(`INSERT INTO allergies (demographic_no, entry_date, DESCRIPTION, TYPECODE, reaction, archived, position,
      lastUpdateDate, providerNo, severity_of_reaction, onset_of_reaction)
    VALUES (${patient}, CURDATE(), ${q(want.allergy)}, 0, ${q(want.reaction)}, '0', 0, NOW(), ${q(provider)}, '1', '1')`);
  // The column set export-content-chart-print-sections seeds, so every field the Drug entity maps reads back.
  sql.execute(`INSERT INTO drugs (provider_no, demographic_no, rx_date, end_date, written_date, BN, GCN_SEQNO, customName,
      dosage, unit, takemin, takemax, freqcode, duration, durunit, quantity, \`repeat\`, special, archived, archived_reason,
      archived_date, position, dispenseInternal, create_date, lastUpdateDate)
    VALUES (${q(provider)}, ${patient}, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), CURDATE(), ${q(want.drug)}, 0,
      ${q(want.drug)}, '10', 'mg', 1, 1, 'OD', '30', 'D', '30', 0, ${q(`${want.drug} 10 mg once daily`)}, 0, '', NULL, 0, 0,
      NOW(), NOW())`);
  // Td is an "injection" item in PreventionItems.xml, so the export files it under Immunizations (type Td).
  preventionId = h.insertId(sql, `INSERT INTO preventions (demographic_no, creation_date, prevention_date, provider_no,
      prevention_type, deleted, refused, never, creator, lastUpdateDate)
    VALUES (${patient}, NOW(), CURDATE(), ${q(provider)}, 'Td', '0', '0', '0', ${q(provider)}, NOW())`, 'immunization');
  sql.execute(`INSERT INTO preventionsExt (prevention_id, keyval, val) VALUES (${preventionId}, 'name', ${q(want.immunization)})`);
  sql.execute(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no, notes,
      reason, location, resources, type, style, billing, status, createdatetime, creator)
    VALUES (${q(provider)}, CURDATE(), '10:30:00', '10:44:00', ${q(marker)}, ${patient}, '', ${q(want.purpose)}, '', '', '', '',
      '', 't', NOW(), ${q(s.config.testUser)})`);
  sql.execute(`INSERT INTO measurements (type, demographicNo, providerNo, dataField, measuringInstruction, comments,
      dateObserved, dateEntered)
    VALUES ('HT', ${patient}, ${q(provider)}, ${q(want.height)}, 'in cm', ${q(marker)}, CURDATE(), NOW())`);
  want.today = sql.value('SELECT CURDATE()');
  return want;
}

async function workflow(s) {
  const want = seedSections(s);

  let exportPage;
  await s.step('the master record opens the export page for this patient', async () => {
    exportPage = await s.popup(s.master,
      s.master.locator('input[type="button"][onclick*="/demographic/DemographicExport?demographicNo="]').first(),
      'cds-export-sections');
    await exportPage.locator('#DemographicExportForm').waitFor();
    h.assert(await exportPage.locator('#demographicNo').inputValue() === s.patient,
      'the export page is not scoped to the owned patient');
  });

  let xml;
  await s.step('exporting the five sections downloads one patient file', async () => {
    for (const option of SECTIONS) await exportPage.locator(`input[name="${option}"]`).check();
    const download = await exportDownload(exportPage);
    const file = await download.path();
    let zip;
    try {
      zip = fs.readFileSync(file);
    } finally {
      await download.delete();
    }
    if (download.suggestedFilename().toLowerCase().endsWith('.pgp')) zip = decryptExport(zip);
    const entries = readZip(zip);
    const xmlNames = [...entries.keys()].filter(name => name.toLowerCase().endsWith('.xml'));
    h.assert(xmlNames.length === 1, `expected one patient XML in the export zip, found ${xmlNames.length}`);
    xml = entries.get(xmlNames[0]).toString('utf8');
    await exportPage.locator('#exportSuccessMessage').waitFor({ state: 'visible', timeout: 30000 });
  });

  for (const { label, check } of Object.values(SECTION_CHECKS)) {
    await s.step(label, async () => check(xml, want));
  }

  await s.step('the exported patient file validates against the CDS schema', async () => {
    assertValidCds(xml);
  });
  await exportPage.close();
}

if (require.main === module) runWorkflow('cds-export-patient-sections', workflow);
module.exports = { workflow, SECTION_CHECKS, entryOf, fullDate, heightsOf, tagNames, textOf };
