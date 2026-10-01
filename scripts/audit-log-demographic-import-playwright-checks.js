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
 * card number 9 + nine random digits). Cleanup, registered before the upload, removes every row the importer wrote
 * for that demographic_no, the audit rows scoped to it or to the health card number, and asserts each is gone.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { buildCdsXml, fixtureValues } = require('./demographic-cds-import-playwright-checks');
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
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  s.cleanup(() => {
    for (const id of importedIds()) {
      h.assert(/^[1-9]\d*$/.test(id), 'Imported patient has an invalid identity');
      const drugs = `SELECT drugid FROM drugs WHERE demographic_no=${id}`;
      const allergies = `SELECT allergyid FROM allergies WHERE demographic_no=${id}`;
      const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${q(id)}`;
      sql.execute([
        `DELETE FROM partial_date WHERE (table_name=2 AND table_id IN (${drugs})) OR (table_name=1 AND table_id IN (${allergies}))`,
        `DELETE FROM casemgmt_note_link WHERE note_id IN (SELECT note_id FROM (${notes}) n)`,
        `DELETE FROM casemgmt_issue_notes WHERE note_id IN (SELECT note_id FROM (${notes}) n)`,
        `DELETE FROM casemgmt_note WHERE demographic_no=${q(id)}`, `DELETE FROM casemgmt_issue WHERE demographic_no=${q(id)}`,
        `DELETE FROM drugReason WHERE demographicNo=${id}`, `DELETE FROM drugs WHERE demographic_no=${id}`,
        `DELETE FROM allergies WHERE demographic_no=${id}`, `DELETE FROM admission WHERE client_id=${id}`,
        `DELETE FROM demographiccust WHERE demographic_no=${id}`, `DELETE FROM demographicExt WHERE demographic_no=${id}`,
        `DELETE FROM demographicArchive WHERE demographic_no=${id}`, `DELETE FROM DemographicContact WHERE demographicNo=${id}`,
        `DELETE FROM demographicPharmacy WHERE demographic_no=${id}`,
        `DELETE FROM log WHERE demographic_no=${id} OR (content IN (${KEYED}) AND contentId=${q(id)}) OR data REGEXP ${q(`(emographic(No|_no| id| no)?[ =:]+)${id}([^0-9]|$)`)}`,
      ].join(';\n'));
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${id}) + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${id})
        + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${q(id)}) + (SELECT COUNT(*) FROM log WHERE demographic_no=${id})`) === '0',
      'Imported patient child rows were not removed');
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${id} AND ${owner}`);
    }
    // The leak check looks for the number in content, contentId and data, so remove by all three.
    sql.execute(`DELETE FROM log WHERE ${hinRowsWhere}`);
    h.assert(importedIds().length === 0, 'The imported patient was not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE ${hinRowsWhere}`) === '0', 'Audit rows carrying the run health number were not removed');
  });
  const [first, last] = sql.rows(`SELECT first_name,last_name FROM provider WHERE provider_no=${q(provider)}`)[0] || [];
  h.assert(first && last, 'The test provider has no name to match as primary physician');
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE first_name=${q(first)} AND last_name=${q(last)}`) === '1',
    'The test provider name is not unique, so the import cannot target it');
  const xml = buildCdsXml(v, { first, last }).replace('</DateOfBirth>',
    `</DateOfBirth>\n<HealthCard><cdsd:Number>${hin}</cdsd:Number><cdsd:ProvinceCode>CA-ON</cdsd:ProvinceCode></HealthCard>`);
  h.assert(xml.includes(hin), 'The health card element was not added to the fixture');
  const file = { name: `${marker}.xml`, mimeType: 'text/xml', buffer: Buffer.from(xml, 'utf8') };

  let admin;
  let frame;
  await s.step('Administration > Data Management > Demographic Import opens the upload form', async () => {
    ({ page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'audit-import-admin', timeout: 20000 }));
    const link = admin.locator('#adminNav a.xlink[rel$="/form/importUpload"]');
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Demographic Import frame did not load');
    await frame.locator('form[name="ImportDemographicDataForm"] #importFile').waitFor();
  });

  let id;
  let before;
  await s.step('importing the CDS file creates exactly one patient (audit rows observed)', async () => {
    before = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM log'));
    await frame.locator('#importFile').setInputFiles(file);
    const uploaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/form/importUpload') && r.request().method() === 'POST', { timeout: 120000 });
    await frame.locator('input[type="submit"][name="Submit"]').click();
    h.assert((await uploaded).status() === 200, 'Import upload was refused');
    await frame.locator('#result > div').filter({ hasText: file.name }).getByRole('link', { name: 'Download Import Event Log' }).waitFor({ timeout: 120000 });
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
    const written = sql.rows(`SELECT ${cols} FROM log WHERE id>${before} AND (demographic_no=${id} OR contentId=${q(id)}
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
