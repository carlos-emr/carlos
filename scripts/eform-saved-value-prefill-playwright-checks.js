#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// eform-saved-value-prefill: eForm fields that copy a value the patient already has saved in an eForm
// (oscarDB=e$first#<field>, e$last#<field>, e$count#<field>).
// User path: E-Chart ▸ eForms "+" (efmformslistadd) ▸ the target eForm ▸
//   1. fields reading the target eForm's own saved copies: "first" shows the note of the patient's
//      earliest copy and "last" the latest one that is not deleted; neither a newer deleted copy nor a
//      newer copy saved for another patient is used;
//   2. fields reading another eForm (eform$name="<source eForm>"): "last" shows that eForm's latest
//      note for the patient and "count" (var$value="<note>") how many copies carry it.
// Step 2 fails on develop and runs last: EFormUtil.getAttributeMatcher only matches attribute names
// of [a-zA-Z][a-zA-Z0-9_:-]*, so eform$name and var$value are never read; the lookup falls back to the
// target eForm itself, and the fields silently show the target's own latest note and the count of its
// own copies. The lookup is EForm.getAPExtra (module "e") ▸
// apconfig.xml _eform_values_first/_last/_count, whose placeholders parameterizeAllFields binds as JDBC
// parameters. The eForm render checks cover the oscarDB patient fields and eform-measurement-prefill
// the m$ fields; none covers e$.
// Fixtures: the owned FAKE- patient; two marker-named eForms in the clinic's eForm library (status 1);
// saved copies seeded by SQL (eform_data + eform_values), one of them deleted (status 0) and one under
// an unused demographic number. Cleanup deletes the eForms and every copy saved from them, and asserts
// none remain. The eForm library is clinic-wide (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const sourceName = `${marker} source`;
  const targetName = `${marker} target`;
  const formNames = [sourceName, targetName].map(q).join(',');
  const owned = `SELECT fdid FROM eform_data WHERE form_name IN (${formNames})`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM eform_values WHERE fdid IN (${owned}); DELETE FROM eform_data WHERE form_name IN (${formNames});
      DELETE FROM eform WHERE form_name IN (${formNames})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name IN (${formNames}))
      + (SELECT COUNT(*) FROM eform_data WHERE form_name IN (${formNames}))`) === '0', 'Owned eForms or their saved copies remain');
  });
  const eform = (name, body) => {
    const html = `<html><head><title>${name}</title></head><body><form method="post" action="" name="FormName" id="FormName">`
      + `${body}<input type="submit" value="Submit" id="SubmitButton"></form></body></html>`;
    const fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
        status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${q(name)},'',${q(name)},CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fid || ''), `The ${name} eForm was not created`);
    return fid;
  };
  const sourceFid = eform(sourceName, '<input type="text" name="note" id="note">');
  const targetFid = eform(targetName, '<input type="text" name="note" id="note">'
    + '<input type="text" name="ownFirst" id="ownFirst" oscarDB=e$first#note>'
    + '<input type="text" name="ownLast" id="ownLast" oscarDB=e$last#note>'
    + `<input type="text" name="sourceLast" id="sourceLast" oscarDB=e$last#note eform$name="${sourceName}">`
    + `<input type="text" name="sourceCount" id="sourceCount" oscarDB=e$count#note eform$name="${sourceName}" var$value="${marker} source last">`);
  const save = (fid, name, demographic, note, status = 1) => {
    const fdid = sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,
        form_data,showLatestFormOnly,patient_independent,roleType)
      VALUES(${fid},${q(name)},${q(name)},${demographic},${status},CURDATE(),CURTIME(),${q(provider)},'',0,0,'');
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fdid || ''), `A saved copy of ${name} was not created`);
    sql.execute(`INSERT INTO eform_values(fdid,fid,demographic_no,var_name,var_value) VALUES(${fdid},${fid},${demographic},'note',${q(note)})`);
    return fdid;
  };
  const other = sql.value('SELECT IFNULL(MAX(demographic_no),0)+100000 FROM demographic');
  save(targetFid, targetName, patient, `${marker} target first`);
  save(targetFid, targetName, patient, `${marker} target last`);
  save(targetFid, targetName, patient, `${marker} target deleted`, 0);
  save(targetFid, targetName, other, `${marker} target other patient`);
  save(sourceFid, sourceName, patient, `${marker} source first`);
  save(sourceFid, sourceName, patient, `${marker} source last`);

  const chart = await s.chart();
  async function openTarget(label) {
    const list = await s.popup(chart, chart.locator('a[onclick*="efmformslistadd"]').first(), `${label}-list`);
    const form = await s.popup(list, list.locator(`[onclick*="efmformadd_data?fid=${targetFid}&"]`).first(), label);
    await form.locator('#ownLast').waitFor({ state: 'attached' });
    const shown = {};
    for (const id of ['ownFirst', 'ownLast', 'sourceLast', 'sourceCount']) shown[id] = await form.locator(`#${id}`).inputValue();
    await form.close();
    await list.close();
    return shown;
  }
  let shown;

  await s.step('fields reading the eForm\'s own saved copies show the first and the latest live one', async () => {
    shown = await openTarget('eform-target');
    h.assert(shown.ownFirst === `${marker} target first` && shown.ownLast === `${marker} target last`,
      `The eForm prefilled its own saved values as ${JSON.stringify({ first: shown.ownFirst, last: shown.ownLast })}`);
  });

  await s.step('fields reading another eForm by eform$name show its latest note and count', async () => {
    h.assert(shown.sourceLast === `${marker} source last` && shown.sourceCount === '1',
      `The fields reading ${sourceName} prefilled ${JSON.stringify({ last: shown.sourceLast, count: shown.sourceCount })}`);
  });
}

if (require.main === module) runWorkflow('eform-saved-value-prefill', workflow, { openPatient: true });
module.exports = { workflow };
