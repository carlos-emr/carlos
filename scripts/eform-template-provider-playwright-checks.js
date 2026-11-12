#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// eform-template-provider: an eForm's action template (the <!-- <template> ... </template> --> block that
// AddEForm2Action ▸ EFormUtil.writeEformTemplate runs when the eForm is submitted) keeps the provider it names.
// User path: E-Chart ▸ eForms ▸ Add ▸ the fixture eForm ▸ Submit. The template asks for
//   1. a prevention whose <providers> tag names another provider: it is recorded under that provider, not under
//      the provider who submitted the eForm;
//   2. a prevention whose <provider> tag names that provider: also recorded under it;
//   3. an encounter note with $te{eform.provider} and $te{eform.providers}: both are filled in with the
//      submitting provider's number.
// <provider> and $te{eform.provider} are the OSCAR spellings; <providers> and $te{eform.providers} are the
// spellings a 2025 package rename gave them. No other check submits an eForm with a template.
// Fixtures: the owned FAKE- patient; a marker-named eForm in the clinic's eForm library (status 1); the "other"
// provider is the lowest-numbered active provider that is not the login. Cleanup deletes the eForm, its saved
// copies, and the preventions and encounter notes the submission wrote for the owned patient, and asserts none
// remain. The eForm library is clinic-wide (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;

async function waitForValue(sql, query, accept, timeout = 20000) {
  // The template is written after the eForm itself, in the same request; poll briefly for both.
  const deadline = Date.now() + timeout;
  let value;
  do {
    value = sql.value(query);
    if (accept(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 200));
  } while (Date.now() < deadline);
  return value;
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const formName = `${marker} template`;
  const other = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no REGEXP '^[1-9][0-9]*$'
    AND provider_no<>${q(provider)} ORDER BY CAST(provider_no AS UNSIGNED) LIMIT 1`);
  h.assert(/^[1-9]\d*$/.test(other || ''), 'No other active provider to name in the template');
  let fid;
  s.cleanup(() => {
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(notes.every(id => /^[1-9]\d*$/.test(id)), 'Owned note id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM eform_values WHERE fdid IN (SELECT fdid FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient});
      DELETE FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient}`);
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned eForm, prevention or note rows remain');
  });

  const template = '<!-- <template>'
    + `<EncounterNote>${marker} provider=[$te{eform.provider}] providers=[$te{eform.providers}]</EncounterNote>`
    + `<prevention><type>MMR</type><provider>${other}</provider><date>2026-01-05</date></prevention>`
    + `<prevention><type>Td</type><providers>${other}</providers><date>2026-01-06</date></prevention>`
    + '</template> -->';
  const html = `<html><head><title>${formName}</title></head><body>`
    + '<form method="post" action="" name="FormName" id="FormName"><input type="text" name="note" id="note">'
    + `<input type="submit" value="Submit" id="SubmitButton"></form>${template}</body></html>`;
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'',${q(formName)},CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid || ''), 'The template eForm was not created');
  // Same guard as double-submit-eform: the demo data ships EFormDocs rows for eForm instance ids that no
  // eform_data row owns any more. A new instance saved at one of those ids inherits their attachments and the
  // save is refused with 403 before the template runs. Move the id counter past them with an owned row.
  const staleAttachmentHigh = Number(sql.value('SELECT COALESCE(MAX(fdid),0) FROM EFormDocs'));
  const nextInstanceId = Number(sql.value(`SELECT AUTO_INCREMENT FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='eform_data'`));
  if (nextInstanceId <= staleAttachmentHigh) {
    const seed = staleAttachmentHigh + 1;
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${seed}`) === '0', 'The eForm instance id used to move the id counter is occupied');
    sql.execute(`INSERT INTO eform_data(fdid,fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,form_data,
      showLatestFormOnly,patient_independent,roleType)
      VALUES(${seed},${fid},${q(formName)},${q(`${marker}-IDSEED`)},${patient},1,CURDATE(),CURTIME(),${q(provider)},'',0,0,'')`);
    sql.execute(`DELETE FROM eform_data WHERE fdid=${seed} AND form_name=${q(formName)} AND demographic_no=${patient}`);
  }

  const chart = await s.chart();
  const observed = {};
  await s.step('submitting the eForm saves it and runs its template', async () => {
    // The Add eForm list is never closed from here: unloading it throws on a null window.opener (known, eform-groups).
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-template');
    await form.locator('#remoteSubmitButton').waitFor({ state: 'visible' });
    await form.locator('#note').fill(`${marker} submitted`);
    await form.locator('#remoteSubmitButton').click();
    const saved = await waitForValue(sql, `SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient}`, v => v === '1');
    h.assert(saved === '1', `The eForm was saved ${saved} time(s)`);
    const preventionProvider = async type => waitForValue(sql, `SELECT GROUP_CONCAT(provider_no) FROM preventions
      WHERE demographic_no=${patient} AND prevention_type=${q(type)} AND deleted='0'`, v => !!v);
    observed.mmrProvider = await preventionProvider('MMR');
    observed.tdProvider = await preventionProvider('Td');
    observed.note = await waitForValue(sql, `SELECT GROUP_CONCAT(note SEPARATOR ' | ') FROM casemgmt_note
      WHERE demographic_no=${patient} AND note LIKE ${q(`${marker} provider=%`)}`, v => !!v);
    console.log(`    observed: ${JSON.stringify({ other, login: provider, ...observed })}`);
    h.assert(observed.mmrProvider && observed.tdProvider && observed.note, 'The template wrote no prevention or no note');
    if (!form.isClosed()) await form.close();
  });

  await s.step('a prevention whose <providers> tag names another provider is recorded under that provider', async () => {
    h.assert(observed.tdProvider === other,
      `The <providers>${other}</providers> prevention was recorded under ${observed.tdProvider} (the login is ${provider})`);
  });

  await s.step('a prevention whose <provider> tag names another provider is recorded under that provider', async () => {
    h.assert(observed.mmrProvider === other,
      `The <provider>${other}</provider> prevention was recorded under ${observed.mmrProvider} (the login is ${provider})`);
  });

  await s.step('$te{eform.provider} and $te{eform.providers} fill in the submitting provider', async () => {
    const expected = `${marker} provider=[${provider}] providers=[${provider}]`;
    h.assert(observed.note.trim() === expected, `The encounter note reads "${observed.note.trim()}", not "${expected}"`);
  });
}

if (require.main === module) runWorkflow('eform-template-provider', workflow, { openPatient: true });
module.exports = { workflow };
