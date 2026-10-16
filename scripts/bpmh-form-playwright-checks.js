#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * bpmh-form — the pharmacist's Best Possible Medication History form, opened, saved and reopened the way
 * a clinician does (#4731, #4732):
 *   Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Forms ▸ BPMH (formBPMH, registered as the BC seed does:
 *   "../formBPMH.do?demographic_no=") ▸ type the drug's instruction and a note ▸ Save (POST method=save) ▸
 *   E-Chart again ▸ the saved BPMH entry (form/forwardshortcutname?formId=latest) ▸ Print (POST method=print).
 * Asserts:
 *   - The Forms menu entry opens the form for the owned patient (not a 404 or the error page), showing the
 *     patient, the allergy, the drug with its directions and reason, the provider, and Save.
 *   - Save stores one formBPMH row for the patient, by the provider, with the note and the drug's typed
 *     instruction, and shows the saved form (read-only, no Save) within the application.
 *   - The saved entry reopens that form with the note and the instruction.
 *   - Print answers a PDF and leaves no stamped copy in the document store (when DOCUMENT_DIR says where).
 *   - GET and HEAD carrying the save are refused and store nothing.
 * Fixtures: the owned FAKE-PW patient, with one active allergy and one drug with an ICD-9 reason. The
 * BPMH Forms-menu registration is the BC seed's row, given a display order for the run (the seed ships it
 * hidden): an existing row is restored afterwards, an inserted one deleted. The registration is
 * clinic-wide, so the check must not run concurrently with other checks (live wrapper: EXCLUSIVE=1).
 * Cleanup deletes the patient's formBPMH rows, drug reason, drug and allergy, and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const x = require('./lib/export-content-helpers');

const TIMEOUT = 30000;
const BC_FORM_VALUE = '../formBPMH.do?demographic_no=';
const isBpmh = method => r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/formBPMH')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

async function workflow(s) {
  const { sql, patient, provider, marker, context, recorder } = s;
  const q = h.sqlString;
  const tag = marker.slice(-8);
  const allergy = `FAKE ${tag} penicillin`;
  const direction = `Take 1 tablet by mouth daily ${tag}`;
  const instruction = `${marker} continue at discharge`;
  const note = `${marker} reconciled with the patient`;

  const code = sql.value("SELECT icd9 FROM icd9 WHERE icd9='4019' LIMIT 1") || sql.value('SELECT icd9 FROM icd9 ORDER BY icd9 LIMIT 1');
  if (!code) throw new h.SkipCheck('No ICD-9 code is loaded to give the drug a reason');

  s.cleanup(() => {
    sql.execute(`DELETE FROM formBPMH WHERE demographic_no=${patient};
      DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient};
      DELETE FROM allergies WHERE demographic_no=${patient};
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM formBPMH WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugReason WHERE demographicNo=${patient}) + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})`) === '0', 'The owned BPMH, drug or allergy rows were not removed');
  });

  // The BC seed's registration, enabled for the run (display order 0 hides a form).
  const registered = sql.rows(`SELECT form_name, form_table, hidden FROM encounterForm WHERE form_value=${q(BC_FORM_VALUE)}`)[0];
  const order = sql.value('SELECT COALESCE(MAX(hidden),0)+1 FROM encounterForm');
  if (registered) {
    const [name, table, hidden] = registered;
    h.assert(name === 'BPMH' && table === 'formBPMH' && /^\d+$/.test(hidden), `The installed BPMH registration is ${JSON.stringify(registered)}`);
    s.cleanup(() => {
      sql.execute(`UPDATE encounterForm SET hidden=${hidden} WHERE form_value=${q(BC_FORM_VALUE)}`);
      h.assert(sql.value(`SELECT hidden FROM encounterForm WHERE form_value=${q(BC_FORM_VALUE)}`) === hidden, 'The BPMH registration was not restored');
    });
    sql.execute(`UPDATE encounterForm SET hidden=${order} WHERE form_value=${q(BC_FORM_VALUE)}`);
  } else {
    s.cleanup(() => {
      sql.execute(`DELETE FROM encounterForm WHERE form_value=${q(BC_FORM_VALUE)} AND form_name='BPMH' AND form_table='formBPMH'`);
      h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value=${q(BC_FORM_VALUE)}`) === '0', 'The BPMH registration was not removed');
    });
    sql.execute(`INSERT INTO encounterForm (form_name, form_value, form_table, hidden) VALUES ('BPMH', ${q(BC_FORM_VALUE)}, 'formBPMH', ${order})`);
  }

  sql.execute(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,archived,start_date,age_of_onset,
      severity_of_reaction,onset_of_reaction,life_stage,position,lastUpdateDate,providerNo,nonDrug)
    VALUES (${patient},CURDATE(),${q(allergy)},0,'rash',0,NULL,0,'2','1','A',0,NOW(),${q(provider)},0)`);
  const drug = sql.value(`INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,archived,archived_reason,
      create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode)
    VALUES (${q(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${q(`FAKE ${tag} atorvastatin`)},
      ${q(`FAKE ${tag} atorvastatin 20 mg\n${direction}`)},0,'',NOW(),1,NOW(),0,'30','30','D','OD'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(drug), 'The drug fixture was not created');
  sql.execute(`INSERT INTO drugReason (drugId,codingSystem,code,comments,primaryReasonFlag,archivedFlag,demographicNo,providerNo,dateCoded)
    VALUES (${drug},'icd9',${q(code)},${q(`FAKE ${tag}`)},1,0,${patient},${q(provider)},CURDATE())`);

  const chart = await s.chart();
  let form;
  await s.step('E-Chart ▸ Forms ▸ BPMH opens the form for the patient with the allergy, the drug and the provider', async () => {
    // The "+" never settles for Playwright's hover (it waits for a stable box), so its onmouseover is fired directly.
    await chart.locator('#menuTitle1 a').dispatchEvent('mouseover');
    const entry = chart.getByRole('link', { name: 'BPMH', exact: true });
    h.assert(await entry.count() === 1, `The Forms menu lists BPMH ${await entry.count()} times, not once`);
    form = await s.popup(chart, entry.first(), 'bpmh-new');
    await form.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    const url = new URL(form.url());
    h.assert(h.pathOnly(form.url()).endsWith('/formBPMH') && url.searchParams.get('demographic_no') === patient && url.searchParams.get('formId') === '0',
      `The Forms entry opened ${h.pathOnly(form.url())}${url.search}`);
    const text = await form.locator('body').innerText();
    h.assert(text.includes(marker), 'The form does not name the owned patient');
    h.assert(text.includes(allergy), 'The form does not list the patient\'s allergy');
    h.assert(text.includes(direction), 'The form does not show the drug\'s directions');
    h.assert(await form.locator(`input[name="form.drugs[0].id"][value="${drug}"]`).count() === 1, 'The form does not carry the drug');
    h.assert(await form.locator('input[name="form.demographicNo"]').inputValue() === patient, 'The form does not carry the patient');
    h.assert(await form.locator('button[name="method"][value="save"]').count() === 1, 'The form offers no Save');
    await h.screenshot(form, s.config.screenshotDir, 'bpmh-new');
  });

  let saved;
  await s.step('Save stores the form with the note and the drug\'s instruction, and shows the saved form', async () => {
    await form.locator('textarea[name="form.drugs[0].instruction"]').fill(instruction);
    await form.locator('textarea[name="form.note"]').fill(note);
    await form.locator('#confirm').check();
    const [response] = await Promise.all([
      form.waitForResponse(isBpmh('save'), { timeout: TIMEOUT }),
      form.locator('button[name="method"][value="save"]').click(),
    ]);
    h.assert(response.status() === 302, `Save answered HTTP ${response.status()}, not a redirect`);
    await form.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await h.assertNotErrorPage(form, 'the saved BPMH form');
    const rows = sql.rows(`SELECT id, provider_no, note, LOCATE(${q(instruction)}, drugs) > 0, LOCATE(${q(allergy)}, allergies) > 0
      FROM formBPMH WHERE demographic_no=${patient}`);
    h.assert(rows.length === 1, `Save stored ${rows.length} BPMH form(s), not 1`);
    const [[id, savedBy, savedNote, hasInstruction, hasAllergy]] = rows;
    saved = id;
    h.assert(savedBy === provider && savedNote === note, `The saved form is by ${savedBy} with note ${JSON.stringify(savedNote)}`);
    h.assert(hasInstruction === '1' && hasAllergy === '1', 'The saved form lost the drug\'s instruction or the allergy');
    const url = new URL(form.url());
    h.assert(h.pathOnly(form.url()).endsWith('/formBPMH') && url.searchParams.get('formId') === id,
      `Save left the browser on ${h.pathOnly(form.url())}${url.search}, not the saved form`);
    const text = await form.locator('body').innerText();
    h.assert(text.includes(note) && text.includes(instruction), 'The saved form does not show the note and the instruction');
    h.assert(await form.locator('button[name="method"][value="save"]').count() === 0, 'The saved form still offers Save');
    await h.screenshot(form, s.config.screenshotDir, 'bpmh-saved');
    await form.close();
  });

  await s.step('the saved BPMH entry in the E-Chart reopens the form with the note and the instruction', async () => {
    // A fresh E-Chart lists the saved form (reloading this one would abort its in-flight requests).
    await chart.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await chart.close();
    const fresh = await s.chart();
    await fresh.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    const link = fresh.locator('#leftNavBar a[onclick*="formname=BPMH&"], #rightNavBar a[onclick*="formname=BPMH&"]').first();
    if (!await link.isVisible().catch(() => false)) await fresh.locator('#forms img[src$="/expand.gif"]:visible').click().catch(() => {});
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    const reopened = await ui.clickOpensPopup(fresh, link, { context, recorder, label: 'bpmh-reopen', timeout: TIMEOUT, position: { x: 8, y: 9 } });
    await reopened.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    const url = new URL(reopened.url());
    h.assert(url.searchParams.get('formId') === saved, `The saved entry opened ${h.pathOnly(reopened.url())}${url.search}, not form ${saved}`);
    const text = await reopened.locator('body').innerText();
    h.assert(text.includes(note) && text.includes(instruction), 'The reopened form does not show the note and the instruction');
    form = reopened;
  });

  await s.step('Print answers a PDF of the saved form', async () => {
    // Headless Chromium hands an inline PDF to the download machinery, so the PDF is read from the download.
    const scratch = x.scratchDir();
    s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
    const pdf = await x.saveDownload(form, scratch, () => form.locator('button[name="method"][value="print"]').click(), { route: /\/formBPMH$/ });
    h.assert(pdf.status === 200 && /application\/pdf/.test(pdf.headers['content-type'] || ''),
      `Print answered HTTP ${pdf.status} ${pdf.headers['content-type']}`);
    h.assert(pdf.bytes.subarray(0, 4).toString() === '%PDF', 'Print did not answer a PDF');
    h.assert(sql.value(`SELECT COUNT(*) FROM formBPMH WHERE demographic_no=${patient}`) === '1', 'Printing the saved form stored another form');
    // The PDF is stamped to a file in the document store only to be streamed; none may be left behind.
    const store = process.env.DOCUMENT_DIR || process.env.RX_FAX_DOCUMENT_DIR;
    if (store) {
      const left = require('node:fs').readdirSync(store).filter(name => name.startsWith(`${patient}_`) && name.endsWith('_bpmh_template_marked.pdf'));
      h.assert(left.length === 0, `Print left ${left.length} stamped PDF(s) of the patient in the document store`);
    } else {
      console.log('  NOTE bpmh-form: DOCUMENT_DIR is not set, so the document store was not checked for a left-over PDF');
    }
  });

  await s.step('GET and HEAD carrying the save are refused and store nothing', async () => {
    const probe = `${marker} saved by GET`;
    for (const method of ['GET', 'HEAD']) {
      const response = await context.request.fetch(h.appUrl(s.config.baseUrl, '/formBPMH')
        + `?method=save&form.demographicNo=${patient}&form.formId=0&form.note=${encodeURIComponent(probe)}`,
      { method, maxRedirects: 0, failOnStatusCode: false });
      await h.assertRefused(s, { label: `${method} formBPMH method=save`, table: 'formBPMH',
        where: `demographic_no=${patient} AND note=${q(probe)}`, before: '0', response });
    }
    // A GET carrying print is not probed here: method contract pending.
  });
}

if (require.main === module) runWorkflow('bpmh-form', workflow);
module.exports = { workflow };
