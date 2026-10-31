#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of a patient-file import: the written patient, and the health number it is looked up by
 * (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Administration > Data Management > Demographic Import (the page loads into the admin
 * shell's #myFrame) > choose an owned synthetic EMR DM 5.0 (CDS) patient file that carries a health card number,
 * a medication, an allergy and a clinical note > Import (the same file as demographic-cds-import, plus a HealthCard).
 *
 * Asserts the import created exactly one patient, and, scoped to that patient and to the run's synthetic health
 * card number: no audit row carries the health card number (the importer looks the patient up by it through
 * DemographicManager.searchByHealthCard, which writes "hin=<number>" into log.data), and the import left at
 * least one audit row naming the created patient in demographic_no with the provider and the client address (a
 * bulk write of a patient, their medication, allergy and clinical note). Every expectation is evaluated and
 * the violated ones are reported together in the last step.
 *
 * Fixtures and cleanup: the patient exists only because the check imported it (surname = the run marker; health
 * card number 9 + nine random digits). Cleanup removes only completed owned clinical fixtures; audit/ledger evidence
 * remains, and any held/incomplete clinical fixture is retained with a visible cleanup failure.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const {buildCdsXml,fixtureValues,assertSchemaValid,openImportFrame,uploadAndAwaitRun,registerImportCleanup} = require('./demographic-cds-import-playwright-checks');
const { phiLeaks, label, PATIENT_KEYED_CONTENT } = require('./lib/audit-log-helpers');

const q = h.sqlString;
const KEYED = PATIENT_KEYED_CONTENT.map(q).join(',');

async function workflow(s) {
  const { sql, marker, provider } = s;
  const v = fixtureValues(marker);
  const hin = `9${String(randomInt(0, 1e9)).padStart(9, '0')}`;
  const owner = `last_name=${q(v.lastName)} AND first_name=${q(v.firstName)}`;
  const hinRowsWhere = `(data LIKE ${q(`%${hin}%`)} OR content LIKE ${q(`%${hin}%`)} OR contentId LIKE ${q(`%${hin}%`)})`;
  const importedIds = () => sql.rows(`SELECT demographic_no FROM demographic WHERE ${owner} ORDER BY demographic_no`).flat();
  let before; // Audit watermark bounds all observations to this synthetic import attempt.
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  registerImportCleanup(s, owner); // Retain audit and immutable import evidence; never hide an incomplete held import.
  const [first, last] = sql.rows(`SELECT first_name,last_name FROM provider WHERE provider_no=${q(provider)}`)[0] || [];
  h.assert(first && last, 'The test provider has no name to match as primary physician');
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE first_name=${q(first)} AND last_name=${q(last)}`) === '1',
    'The test provider name is not unique, so the import cannot target it');
  const xml = buildCdsXml(v, { first, last }).replace('</DateOfBirth>',
    `</DateOfBirth>\n<HealthCard><cdsd:Number>${hin}</cdsd:Number><cdsd:ProvinceCode>CA-ON</cdsd:ProvinceCode></HealthCard>`);
  h.assert(xml.includes(hin), 'The health card element was not added to the fixture');
  const file = { name: `${marker}.xml`, mimeType: 'text/xml', buffer: Buffer.from(xml, 'utf8') };

  assertSchemaValid(xml);
  let admin,frame;
  await s.step('Existing administration menu opens the durable import UI', async () => {
    ({admin,frame}=await openImportFrame(s,'audit-import-admin'));
  });

  let id;
  await s.step('importing the CDS file creates exactly one patient (audit rows observed)', async () => {
    before = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM log'));
    const run=await uploadAndAwaitRun(s,admin,frame,file);
    h.assert(run.summary.clinicalComplete === true && run.summary.cleanComplete === true,
      'Import did not deliver a complete clinical and operational receipt');
    const ids = importedIds();
    h.assert(ids.length === 1, `Expected exactly one imported patient, found ${ids.length}`);
    [id] = ids;
    h.assert(sql.value(`SELECT hin FROM demographic WHERE demographic_no=${id}`) === hin, 'The imported patient does not carry the file\'s health card number');
    // LogAction.addLog commits on a background executor: poll (bounded) for the first row naming the created patient
    // instead of a fixed delay, then give late rows a moment to land. No row at all is a finding reported below.
    const named = `SELECT COUNT(*) FROM log WHERE id>${before} AND (demographic_no=${id} OR (content IN (${KEYED}) AND contentId=${q(id)})
      OR data REGEXP ${q(`(emographic(No|_no| id| no)?[ =:]+)${id}([^0-9]|$)`)})`;
    for (const deadline = Date.now() + 20000; sql.value(named) === '0' && Date.now() < deadline;) await new Promise(resolve => setTimeout(resolve, 250));
    await new Promise(resolve => setTimeout(resolve, 2000));
    const cols = `id,COALESCE(provider_no,'~NULL~'),action,COALESCE(content,'~NULL~'),COALESCE(contentId,'~NULL~'),COALESCE(ip,'~NULL~'),COALESCE(CAST(demographic_no AS CHAR),'~NULL~'),COALESCE(data,'~NULL~')`;
    const toRow = ([rid, who, action, content, contentId, ip, demographic, data]) => ({ id: rid, provider: who, action, content: content === '~NULL~' ? null : content,
      contentId: contentId === '~NULL~' ? null : contentId, ip: ip === '~NULL~' ? null : ip, demographic, data: data === '~NULL~' ? null : data });
    const hinRows = sql.rows(`SELECT ${cols} FROM log WHERE id>${before} AND ${hinRowsWhere}`).map(toRow);
    const hinLeaks = phiLeaks(hinRows, [hin]);
    expect(!hinLeaks.length, `The patient's health card number is stored in the audit log (${hinLeaks.join(', ')})`);
    const written = sql.rows(`SELECT ${cols} FROM log WHERE id>${before} AND (demographic_no=${id} OR (content IN (${KEYED}) AND contentId=${q(id)})
      OR data REGEXP ${q(`(emographic(No|_no| id| no)?[ =:]+)${id}([^0-9]|$)`)}) AND action NOT LIKE 'read%' AND action NOT LIKE '%Manager.get%'`).map(toRow);
    expect(written.length >= 1, 'Importing a patient file (a new patient with a medication, an allergy and a clinical note) wrote no audit row naming the created patient');
    for (const r of written) {
      expect(r.provider === provider, `The import audit row ${label(r)} does not carry the provider`);
      expect(Boolean(r.ip), `The import audit row ${label(r)} carries no client address`);
      expect(r.demographic === String(id), `The import audit row ${label(r)} carries no demographic_no`);
    }
  });

  await s.step('every expectation of the import audit trail held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the demographic import path:\n  - ${[...new Set(defects)].join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-demographic-import', workflow, { openPatient: false });
module.exports = { workflow };
