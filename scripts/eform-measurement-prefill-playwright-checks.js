#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// eform-measurement-prefill: an eForm's measurement fields (oscarDB=m$<type>#<field>) open filled with
// the patient's latest reading.
// User path: E-Chart ▸ eForms "+" (efmformslistadd) ▸ the eForm ▸
//   1. the weight fields show the latest weight by observation date (not the one entered last): its
//      value, its observed date and its comment; a field for a type the patient has no reading of
//      (height) opens empty;
//   2. after a newer weight is recorded, opening the eForm again shows that one.
// The lookup is EForm.getAPExtra ▸ EctMeasurementsDataBeanHandler.getLast ▸
// MeasurementDao.findMeasurementsAndProvidersByDemoAndType (latest dateObserved, then createDate).
// The eForm render checks cover the oscarDB patient fields; none covers a measurement field.
// Fixtures: the owned FAKE- patient and its WT measurements seeded by SQL; one marker-named eForm in
// the clinic's eForm library (status 1) so the Add list offers it. Cleanup deletes the eForm, any data
// saved from it, and the patient's measurements, and asserts none remain. The eForm library is
// clinic-wide, so it must not run concurrently with other checks (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;

function templateHtml(title) {
  return `<html><head><title>${title}</title></head><body>`
    + '<form method="post" action="" name="FormName" id="FormName">'
    + '<input type="text" name="wtValue" id="wtValue" oscarDB=m$WT#value>'
    + '<input type="text" name="wtDate" id="wtDate" oscarDB=m$WT#dateObserved>'
    + '<input type="text" name="wtComment" id="wtComment" oscarDB=m$WT#comments>'
    + '<input type="text" name="htValue" id="htValue" oscarDB=m$HT#value>'
    + '<input type="submit" value="Submit" id="SubmitButton">'
    + '</form></body></html>';
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const formName = `${marker} prefill`;
  let fid;
  s.cleanup(() => {
    const fdids = sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient}`).map(([id]) => id)
      .filter(id => /^[1-9]\d*$/.test(id));
    if (fdids.length) {
      sql.execute(`DELETE FROM eform_values WHERE fdid IN (${fdids.join(',')});
        DELETE FROM eform_data WHERE fdid IN (${fdids.join(',')}) AND demographic_no=${patient}`);
    }
    sql.execute(`DELETE FROM eform WHERE form_name=${q(formName)}; DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient})`) === '0', 'Owned eForm or measurement rows remain');
  });
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
      status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'',${q(formName)},CURDATE(),CURTIME(),${q(provider)},
      1,${q(templateHtml(formName))},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid || ''), 'The marker eForm was not created');
  const weight = (value, observed, entered, comment) => sql.execute(`INSERT INTO measurements
      (type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
    VALUES('WT',${patient},${q(provider)},${q(value)},'in kg',${q(comment)},${q(observed)},${entered})`);
  // The later observation was entered first: the prefill must follow the observation date.
  weight('63.4', '2026-03-04 09:00:00', 'NOW() - INTERVAL 2 DAY', `${marker} March weight`);
  weight('61.2', '2026-01-02 09:00:00', 'NOW()', `${marker} January weight`);

  const chart = await s.chart();
  async function openForm(label) {
    const list = await s.popup(chart, chart.locator('a[onclick*="efmformslistadd"]').first(), `${label}-list`);
    const form = await s.popup(list, list.locator(`[onclick*="efmformadd_data?fid=${fid}&"]`).first(), label);
    await form.locator('#wtValue').waitFor({ state: 'attached' });
    const read = async id => form.locator(`#${id}`).inputValue();
    const values = { value: await read('wtValue'), date: await read('wtDate'), comment: await read('wtComment'), height: await read('htValue') };
    await form.close();
    await list.close();
    return values;
  }

  await s.step('the weight fields open with the latest observed weight, and a height field with no reading opens empty', async () => {
    const shown = await openForm('eform-prefill');
    h.assert(shown.value === '63.4' && shown.date.startsWith('2026-03-04') && shown.comment === `${marker} March weight`,
      `The eForm prefilled the weight as ${JSON.stringify(shown)}`);
    h.assert(shown.height === '', `The height field (no reading) opened as ${JSON.stringify(shown.height)}`);
  });

  await s.step('a newer weight is what the eForm shows next time', async () => {
    weight('64.0', '2026-04-05 09:00:00', 'NOW()', `${marker} April weight`);
    const shown = await openForm('eform-prefill-again');
    h.assert(shown.value === '64.0' && shown.date.startsWith('2026-04-05') && shown.comment === `${marker} April weight`,
      `After a newer weight the eForm prefilled ${JSON.stringify(shown)}`);
  });
}

if (require.main === module) runWorkflow('eform-measurement-prefill', workflow, { openPatient: true });
module.exports = { workflow };
