#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Demographic CDS import (coverage plan §2.4 `demographic-cds-import`).
 *
 * User path: Schedule ▸ Administration ▸ Data Management ▸ Demographic Import
 * (the page loads into the admin shell's #myFrame), choose a file, Import, then
 * "Download Import Event Log" from the result panel.
 *
 * Asserts: an empty submit is refused client-side with its alert; an owned
 * synthetic EMR DM 5.0 (CDS) patient file -- demographics, one medication, one
 * allergy, one clinical note, validated against the CDS schema first -- creates
 * exactly one patient whose demographic, drugs, allergies and casemgmt_note rows
 * carry the file's values and the matched test provider; the downloaded import
 * event log counts 1 allergy / 1 medication / 1 clinical note for that patient;
 * re-importing the same file creates no second patient and reports the duplicate.
 *
 * Fixtures and cleanup: the patient exists only because the check imported it
 * (surname = per-run FAKE-PW marker). Cleanup, registered before the upload,
 * removes every row the importer wrote for that demographic_no (and nothing
 * keyed to any other patient) and asserts each is gone. The import names the
 * test login's own provider as primary physician/note author, so no provider
 * row is created or changed.
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const xmlText = value => String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;').replace(/'/g, '&apos;');
const personName = (first, last) => `<cdsd:FirstName>${xmlText(first)}</cdsd:FirstName>`
  + `<cdsd:LastName>${xmlText(last)}</cdsd:LastName>`;

/** The fixture's values, shared by the XML and the database assertions. */
function fixtureValues(marker) {
  return {
    lastName: marker,
    firstName: 'Import',
    dob: '1971-03-04',
    address: `${marker.slice(-6)} Fixture Import Rd`,
    city: 'Fixture City',
    postal: 'K1A0B1',
    phone: '613-555-0142',
    drug: `${marker} Fixture Med`,
    allergen: `${marker} Fixture Allergen`,
    note: `${marker} imported fixture encounter note`,
  };
}

/** A single-patient EMR DM 5.0 file. Pure, so it can be schema-checked offline. */
function buildCdsXml(v, provider) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<OmdCds xmlns="cds" xmlns:cdsd="cds_dt">
<PatientRecord>
<Demographics>
<Names><cdsd:LegalName>
<cdsd:FirstName><cdsd:Part>${xmlText(v.firstName)}</cdsd:Part><cdsd:PartType>GIV</cdsd:PartType></cdsd:FirstName>
<cdsd:LastName><cdsd:Part>${xmlText(v.lastName)}</cdsd:Part><cdsd:PartType>FAMC</cdsd:PartType></cdsd:LastName>
</cdsd:LegalName></Names>
<DateOfBirth>${v.dob}</DateOfBirth>
<Gender>F</Gender>
<UniqueVendorIdSequence>${xmlText(v.lastName.slice(-20))}</UniqueVendorIdSequence>
<Address addressType="M"><cdsd:Structured><cdsd:Line1>${xmlText(v.address)}</cdsd:Line1>
<cdsd:City>${xmlText(v.city)}</cdsd:City><cdsd:CountrySubdivisionCode>CA-ON</cdsd:CountrySubdivisionCode>
<cdsd:PostalZipCode><cdsd:PostalCode>${v.postal}</cdsd:PostalCode></cdsd:PostalZipCode></cdsd:Structured></Address>
<PhoneNumber phoneNumberType="R"><cdsd:phoneNumber>${v.phone}</cdsd:phoneNumber></PhoneNumber>
<PrimaryPhysician><Name>${personName(provider.first, provider.last)}</Name></PrimaryPhysician>
<PersonStatusCode><PersonStatusAsEnum>A</PersonStatusAsEnum></PersonStatusCode>
</Demographics>
<AllergiesAndAdverseReactions>
<OffendingAgentDescription>${xmlText(v.allergen)}</OffendingAgentDescription>
<PropertyOfOffendingAgent>DR</PropertyOfOffendingAgent>
<ReactionType>AL</ReactionType>
<StartDate><cdsd:FullDate>2020-05-06</cdsd:FullDate></StartDate>
<Severity>MO</Severity>
<Reaction>Rash</Reaction>
<RecordedDate><cdsd:FullDate>2020-05-07</cdsd:FullDate></RecordedDate>
</AllergiesAndAdverseReactions>
<MedicationsAndTreatments>
<PrescriptionWrittenDate><cdsd:FullDate>2026-01-15</cdsd:FullDate></PrescriptionWrittenDate>
<StartDate><cdsd:FullDate>2026-01-15</cdsd:FullDate></StartDate>
<DrugName>${xmlText(v.drug)}</DrugName>
<NumberOfRefills>2</NumberOfRefills>
<Dosage>1</Dosage>
<DosageUnitOfMeasure>tab</DosageUnitOfMeasure>
<Route>PO</Route>
<Frequency>BID</Frequency>
<Duration>30</Duration>
<Quantity>60</Quantity>
</MedicationsAndTreatments>
<ClinicalNotes>
<NoteType>Clinical</NoteType>
<MyClinicalNotesContent>${xmlText(v.note)}</MyClinicalNotesContent>
<EventDateTime><cdsd:FullDateTime>2026-02-03T10:20:00</cdsd:FullDateTime></EventDateTime>
<ParticipatingProviders><Name>${personName(provider.first, provider.last)}</Name>
<DateTimeNoteCreated><cdsd:FullDateTime>2026-02-03T10:25:00</cdsd:FullDateTime></DateTimeNoteCreated></ParticipatingProviders>
</ClinicalNotes>
</PatientRecord>
</OmdCds>
`;
}

/** The fixture must be a valid CDS file, or a refusal would prove nothing. */
function assertSchemaValid(xml) {
  try {
    execFileSync('xmllint', ['--nonet', '--noout', '--schema',
      path.join(__dirname, '../src/main/resources/omdDataMigration/EMR_Data_Migration_Schema.xsd'), '-'],
    { input: Buffer.from(xml, 'utf8'), stdio: ['pipe', 'pipe', 'pipe'], timeout: 30000 });
  } catch (error) {
    if (error.code === 'ENOENT') throw new h.SkipCheck('xmllint (libxml2-utils) is needed to validate the CDS fixture');
    throw new Error('The synthetic CDS fixture does not validate against the EMR DM 5.0 schema');
  }
}

/**
 * The import log's first table: a header row of category names and one data
 * row of counts. Returns {category: count} for the data row.
 */
function parseImportLogCounts(text) {
  const rows = text.split(/\r?\n/).filter(line => line.includes('|'));
  h.assert(rows.length >= 3, 'The import event log has no summary table');
  const cells = row => row.split('|').slice(0, -1).map(cell => cell.trim());
  const header = cells(rows[0]);
  const counts = cells(rows[2]);
  return Object.fromEntries(header.map((name, i) => [name, counts[i]]));
}

// Every child row the importer can write for one patient, keyed only by that
// patient's demographic_no (or a row owned by one of its drugs/allergies/notes).
function childCleanup(id) {
  const drugs = `SELECT drugid FROM drugs WHERE demographic_no=${id}`;
  const allergies = `SELECT allergyid FROM allergies WHERE demographic_no=${id}`;
  const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${h.sqlString(id)}`;
  return [
    `DELETE FROM partial_date WHERE (table_name=2 AND table_id IN (${drugs})) OR (table_name=1 AND table_id IN (${allergies}))`,
    `DELETE FROM casemgmt_note_link WHERE note_id IN (SELECT note_id FROM (${notes}) n)`,
    `DELETE FROM casemgmt_issue_notes WHERE note_id IN (SELECT note_id FROM (${notes}) n)`,
    `DELETE FROM casemgmt_note WHERE demographic_no=${h.sqlString(id)}`,
    `DELETE FROM casemgmt_issue WHERE demographic_no=${h.sqlString(id)}`,
    `DELETE FROM drugReason WHERE demographicNo=${id}`,
    `DELETE FROM drugs WHERE demographic_no=${id}`,
    `DELETE FROM allergies WHERE demographic_no=${id}`,
    `DELETE FROM admission WHERE client_id=${id}`,
    `DELETE FROM demographiccust WHERE demographic_no=${id}`,
    `DELETE FROM demographicExt WHERE demographic_no=${id}`,
    `DELETE FROM demographicArchive WHERE demographic_no=${id}`,
    `DELETE FROM DemographicContact WHERE demographicNo=${id}`,
    `DELETE FROM demographicPharmacy WHERE demographic_no=${id}`,
  ];
}

function remainingChildren(sql, id) {
  return sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${h.sqlString(id)})
    + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${h.sqlString(id)})
    + (SELECT COUNT(*) FROM drugReason WHERE demographicNo=${id})
    + (SELECT COUNT(*) FROM admission WHERE client_id=${id})
    + (SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM demographicArchive WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${id})
    + (SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${id})`);
}

async function workflow(s) {
  const v = fixtureValues(s.marker);
  const owner = `last_name=${h.sqlString(v.lastName)} AND first_name=${h.sqlString(v.firstName)}`;
  const importedIds = () => s.sql.rows(`SELECT demographic_no FROM demographic WHERE ${owner} ORDER BY demographic_no`).flat();
  s.cleanup(() => {
    for (const id of importedIds()) {
      h.assert(/^[1-9]\d*$/.test(id), 'Imported patient has an invalid identity');
      s.sql.execute(childCleanup(id).join(';\n'));
      h.assert(remainingChildren(s.sql, id) === '0', 'Imported patient child rows were not removed');
      s.sql.execute(`DELETE FROM demographic WHERE demographic_no=${id} AND ${owner}`);
    }
    h.assert(importedIds().length === 0, 'The imported patient was not removed');
  });
  const [first, last] = s.sql.rows(`SELECT first_name,last_name FROM provider WHERE provider_no=${h.sqlString(s.provider)}`)[0] || [];
  h.assert(first && last, 'The test provider has no name to match as primary physician');
  // The importer matches a named physician by first/last name; an ambiguous name
  // would silently attach the patient to someone else.
  h.assert(s.sql.value(`SELECT COUNT(*) FROM provider WHERE first_name=${h.sqlString(first)}
    AND last_name=${h.sqlString(last)}`) === '1', 'The test provider name is not unique, so the import cannot target it');
  const xml = buildCdsXml(v, { first, last });
  assertSchemaValid(xml);
  const file = { name: `${s.marker}.xml`, mimeType: 'text/xml', buffer: Buffer.from(xml, 'utf8') };

  let admin;
  let frame;
  await s.step('Administration ▸ Data Management ▸ Demographic Import opens the upload form', async () => {
    ({ page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'cds-import-admin', timeout: 20000 }));
    const link = admin.locator('#adminNav a.xlink[rel$="/form/importUpload"]');
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Demographic Import frame did not load');
    await frame.locator('form[name="ImportDemographicDataForm"] #importFile').waitFor();
  });

  await s.step('Import without a file is refused in the browser and writes nothing', async () => {
    const seen = await h.withExpectedDialogs(admin, async () => {
      await frame.locator('input[type="submit"][name="Submit"]').click();
      await expectValue(s.sql, 'SELECT 1', '1', '');
      await admin.waitForTimeout(300);
    });
    h.assert(seen.length === 1 && /select at least one file/i.test(seen[0].message),
      'Importing with no file did not raise the "select at least one file" alert');
    h.assert(importedIds().length === 0, 'An empty import created a patient');
  });

  let importLogHref;
  let id;
  await s.step('importing the CDS file reports success and offers the import event log', async () => {
    await frame.locator('#importFile').setInputFiles(file);
    const uploaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/form/importUpload')
      && r.request().method() === 'POST', { timeout: 120000 });
    await frame.locator('input[type="submit"][name="Submit"]').click();
    const response = await uploaded;
    h.assert(response.status() === 200, `Import upload answered HTTP ${response.status()}`);
    const result = frame.locator('#result > div').filter({ hasText: file.name });
    await result.getByRole('link', { name: 'Download Import Event Log' }).waitFor({ timeout: 120000 });
    h.assert(await result.locator('h5', { hasText: 'Imported Successfully' }).count() === 1,
      'The import result panel did not report success');
    importLogHref = await result.getByRole('link', { name: 'Download Import Event Log' }).getAttribute('href');
    h.assert(/\/form\/importLogDownload\?importlog=/.test(importLogHref || ''), 'The result panel links no import log');
    const ids = importedIds();
    h.assert(ids.length === 1, `Expected exactly one imported patient, found ${ids.length}`);
    [id] = ids;
  });

  await s.step('the imported demographic row carries the file values and the matched provider', async () => {
    const row = s.sql.rows(`SELECT CONCAT_WS('|',year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
      provider_no,address,city,province,postal,phone) FROM demographic WHERE demographic_no=${id}`).flat()[0];
    h.assert(row === ['1971', '03', '04', 'F', 'AC', s.provider, v.address, v.city, 'ON', v.postal, v.phone].join('|'),
      'Imported demographic fields differ from the CDS file');
  });

  await s.step('the medication, allergy and clinical note land on the imported patient', async () => {
    h.assert(s.sql.value(`SELECT CONCAT_WS('|',customName,rx_date,duration,durunit,quantity,\`repeat\`,route,freqcode,
      provider_no,archived) FROM drugs WHERE demographic_no=${id}`)
      === [v.drug, '2026-01-15', '30', 'D', '60', '2', 'PO', 'BID', s.provider, '0'].join('|'),
    'Imported medication row differs from the CDS file');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${id}`) === '1', 'Medication was not imported exactly once');
    h.assert(s.sql.value(`SELECT CONCAT_WS('|',DESCRIPTION,TYPECODE,reaction,severity_of_reaction,start_date,archived)
      FROM allergies WHERE demographic_no=${id}`) === [v.allergen, '13', 'Rash', '2', '2020-05-06', '0'].join('|'),
    'Imported allergy row differs from the CDS file');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${id}`) === '1', 'Allergy was not imported exactly once');
    h.assert(s.sql.value(`SELECT CONCAT_WS('|',provider_no,signing_provider_no,observation_date) FROM casemgmt_note
      WHERE demographic_no=${h.sqlString(id)} AND note=${h.sqlString(v.note)}`)
      === [s.provider, s.provider, '2026-02-03 10:20:00'].join('|'),
    'Imported clinical note is missing or not attributed to the matched provider');
  });

  await s.step('Download Import Event Log returns the log counting this patient\'s records', async () => {
    const link = frame.locator('#result > div').filter({ hasText: file.name })
      .getByRole('link', { name: 'Download Import Event Log' });
    const popupOrDownload = Promise.race([
      admin.waitForEvent('download', { timeout: 30000 }),
      s.context.waitForEvent('page', { timeout: 30000 }).then(p => p.waitForEvent('download', { timeout: 30000 })),
    ]);
    await link.click();
    const download = await popupOrDownload;
    h.assert(/^ImportEvent-.*\.log$/.test(download.suggestedFilename()), 'Import log download has an unexpected name');
    const text = fs.readFileSync(await download.path(), 'utf8');
    const counts = parseImportLogCounts(text);
    h.assert(counts.Patient === id, 'Import log summary does not name the imported patient');
    h.assert(counts.Allergy === '1' && counts.Medication === '1' && counts.Clinical === '1',
      'Import log does not count one allergy, one medication and one clinical note');
    h.assert(new RegExp(`^${id}\\s+\\|`, 'm').test(text.split(/Errors\/Notes/)[1] || ''),
      'Import log error section does not list the imported patient');
  });

  await s.step('re-importing the same file is refused as a duplicate and creates no second patient', async () => {
    await frame.locator('#importFile').setInputFiles(file);
    const uploaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/form/importUpload')
      && r.request().method() === 'POST', { timeout: 120000 });
    await frame.locator('input[type="submit"][name="Submit"]').click();
    h.assert((await uploaded).status() === 200, 'Duplicate import upload did not complete');
    const result = frame.locator('#result > div').filter({ hasText: file.name });
    await result.locator('li', { hasText: /already exist/ }).waitFor({ timeout: 60000 });
    h.assert(importedIds().length === 1, 'Re-importing the same file created a second patient');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${id}`) === '1'
      && s.sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${id}`) === '1',
    'Re-importing the same file duplicated the existing patient\'s records');
  });
}

if (require.main === module) runWorkflow('demographic-cds-import', workflow, { openPatient: false });
module.exports = { workflow, buildCdsXml, fixtureValues, parseImportLogCounts };
