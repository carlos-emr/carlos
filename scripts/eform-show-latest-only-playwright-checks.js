#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// eform-show-latest-only: an eForm set to "Show latest form only" (eForm manager ▸ edit ▸ the checkbox,
// eform.showLatestFormOnly, copied onto each saved copy) lists only the patient's newest saved copy in the
// E-Chart's eForms box, while the patient's eForm list keeps every copy.
// User path: E-Chart ▸
//   1. the eForms box (EctDisplayEForm2Action ▸ EFormDataDao.isLatestShowLatestFormOnlyPatientForm) lists the
//      newest live copy of that eForm, and neither its older copy nor a newer deleted one; it lists both copies of
//      an ordinary eForm;
//   2. the eForms box's list link (eform/efmpatientformlist) shows every live copy of both eForms.
// No other check sets showLatestFormOnly (the eForm seeds all write 0).
// Fixtures: the owned FAKE- patient; two marker-named eForms in the clinic's eForm library (status 1), one with
// showLatestFormOnly; saved copies seeded by SQL with fixed dates, one of them deleted (status 0). Cleanup deletes
// the eForms and every copy saved from them, and asserts none remain. The eForm library is clinic-wide (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const latestName = `${marker} latest`;
  const everyName = `${marker} every`;
  const formNames = [latestName, everyName].map(q).join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM eform_values WHERE fdid IN (SELECT fdid FROM eform_data WHERE form_name IN (${formNames}));
      DELETE FROM eform_data WHERE form_name IN (${formNames}); DELETE FROM eform WHERE form_name IN (${formNames})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name IN (${formNames}))
      + (SELECT COUNT(*) FROM eform_data WHERE form_name IN (${formNames}))`) === '0', 'Owned eForms or their saved copies remain');
  });
  const html = name => `<html><head><title>${name}</title></head><body><form method="post" action="" name="FormName" id="FormName">`
    + '<input type="text" name="note" id="note"><input type="submit" value="Submit" id="SubmitButton"></form></body></html>';
  const eform = (name, latestOnly) => {
    const fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
        status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${q(name)},'',${q(name)},CURDATE(),CURTIME(),${q(provider)},1,${q(html(name))},${latestOnly},0,'',0,1);
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fid || ''), `The ${name} eForm was not created`);
    return fid;
  };
  const copy = (fid, name, latestOnly, subject, date, status = 1) => {
    const fdid = sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,
        form_data,showLatestFormOnly,patient_independent,roleType)
      VALUES(${fid},${q(name)},${q(subject)},${patient},${status},${q(date)},'10:00:00',${q(provider)},${q(html(name))},${latestOnly},0,'');
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fdid || ''), `A saved copy of ${name} was not created`);
    return fdid;
  };
  const latestFid = eform(latestName, 1);
  const everyFid = eform(everyName, 0);
  // The newest copy is saved first, so it has the lower id: the box must go by the copy's date, not its id.
  const newest = copy(latestFid, latestName, 1, 'newest', '2026-02-10');
  const older = copy(latestFid, latestName, 1, 'older', '2026-01-10');
  const latest = { older, newest, deleted: copy(latestFid, latestName, 1, 'deleted', '2026-03-10', 0) };
  const every = { older: copy(everyFid, everyName, 0, 'older', '2026-01-11'), newer: copy(everyFid, everyName, 0, 'newer', '2026-02-11') };
  const listed = async (scope, ids) => {
    const shown = {};
    for (const [label, fdid] of Object.entries(ids)) shown[label] = await scope.locator(`[onclick*="efmshowform_data?fdid=${fdid}&"]`).count() > 0;
    return shown;
  };

  const chart = await s.chart();
  await s.step('the E-Chart eForms box lists only the newest live copy of a show-latest-only eForm', async () => {
    const box = chart.locator('#eformslist');
    await box.waitFor({ state: 'attached' });
    const shown = { latest: await listed(box, latest), every: await listed(box, every) };
    h.assert(JSON.stringify(shown) === JSON.stringify({
      latest: { older: false, newest: true, deleted: false }, every: { older: true, newer: true },
    }), `The E-Chart eForms box lists ${JSON.stringify(shown)}`);
  });

  await s.step('the patient\'s eForm list still shows every live copy', async () => {
    const list = await s.popup(chart, chart.locator('a[onclick*="/eform/efmpatientformlist?"]').first(), 'eform-patient-list');
    const table = list.locator('#efmTable');
    await table.waitFor();
    const shown = { latest: await listed(table, latest), every: await listed(table, every) };
    h.assert(JSON.stringify(shown) === JSON.stringify({
      latest: { older: true, newest: true, deleted: false }, every: { older: true, newer: true },
    }), `The patient's eForm list shows ${JSON.stringify(shown)}`);
    await list.close();
  });
}

if (require.main === module) runWorkflow('eform-show-latest-only', workflow, { openPatient: true });
module.exports = { workflow };
