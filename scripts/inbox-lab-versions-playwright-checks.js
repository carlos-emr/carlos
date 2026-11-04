#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Route three existing demo versions of one accession to the test provider with
// different review states. Restore routing exactly; never acknowledge a report.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { applyStatusFilter, settle } = require('./inboxhub-filters-playwright-checks');

async function workflow(s) {
  const labs = s.sql.rows(`SELECT h.lab_no FROM hl7TextInfo h
    JOIN hl7TextMessage m ON m.lab_id=h.lab_no
    WHERE h.accessionNum=(SELECT accessionNum FROM hl7TextInfo
      WHERE accessionNum IS NOT NULL AND accessionNum<>''
      GROUP BY accessionNum HAVING COUNT(*)>=3 ORDER BY MIN(lab_no) LIMIT 1)
    AND IFNULL(m.type,'')<>'CLS'
    AND ABS(TIMESTAMPDIFF(MONTH,STR_TO_DATE(h.obr_date,'%Y-%m-%d %H:%i:%s'),
      (SELECT STR_TO_DATE(anchor.obr_date,'%Y-%m-%d %H:%i:%s') FROM hl7TextInfo anchor
       WHERE anchor.accessionNum=h.accessionNum ORDER BY anchor.lab_no LIMIT 1)))<4
    ORDER BY h.final_result_count, h.obr_date, h.lab_no LIMIT 3`).map(row => row[0]);
  if (labs.length !== 3) throw new h.SkipCheck('three demo HL7 versions of one accession are required');
  h.assert(labs.every(id => /^[1-9]\d*$/.test(id)), 'Invalid lab fixture identity');
  const provider = h.sqlString(s.provider);
  const where = `provider_no=${provider} AND lab_type='HL7' AND lab_no IN(${labs.join(',')})`;
  const before = s.sql.rows(`SELECT id,lab_no,status FROM providerLabRouting WHERE ${where} ORDER BY id`);
  s.cleanup(() => {
    const ids = before.map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid routing fixture identity');
    s.sql.execute(`DELETE FROM providerLabRouting WHERE ${where}${ids.length ? ` AND id NOT IN(${ids.join(',')})` : ''}`);
    for (const [id, , status] of before) {
      s.sql.execute(`UPDATE providerLabRouting SET status=${status === null ? 'NULL' : h.sqlString(status)} WHERE id=${id}`);
    }
    h.assert(JSON.stringify(s.sql.rows(`SELECT id,lab_no,status FROM providerLabRouting WHERE ${where} ORDER BY id`))
      === JSON.stringify(before), 'Lab routing was not restored exactly');
  });
  const states = ['F', 'N', 'A'];
  for (let index = 0; index < labs.length; index++) {
    const lab = labs[index];
    if (before.some(row => row[1] === lab)) {
      s.sql.execute(`UPDATE providerLabRouting SET status=${h.sqlString(states[index])} WHERE ${where} AND lab_no=${lab}`);
    } else {
      s.sql.execute(`INSERT INTO providerLabRouting (provider_no,lab_no,status,lab_type)
        VALUES (${provider},${lab},${h.sqlString(states[index])},'HL7')`);
    }
  }
  const { page } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
    { context: s.context, recorder: s.recorder, label: 'lab-versions-inbox' });
  await settle(page, 60000);
  await s.step('All retains every version and review states partition the same reports', async () => {
    for (const [id, value, title] of [
      ['#statusAll', '', 'All'], ['#statusNew', 'N', 'New'],
      ['#statusAcknowledged', 'A', 'Acknowledged'], ['#statusFiled', 'F', 'Filed'],
    ]) {
      const rows = await applyStatusFilter(page, { id, value, title }, 60000);
      const actual = rows.filter(row => labs.some(lab => row === `HL7:${lab}`)).sort();
      const expected = labs.filter((lab, index) => !value || states[index] === value).map(lab => `HL7:${lab}`).sort();
      h.assert(JSON.stringify(actual) === JSON.stringify(expected), `${title} lost or misclassified a report version`);
    }
  });
  await s.step('opening a filed version preserves the selected report identity', async () => {
    const row = page.locator(`tr[data-lab-type="HL7"][data-segment-id="${labs[0]}"]`);
    const report = await s.popup(page, row.locator('a[onclick*="reportWindow"]').first(), 'lab-version-report');
    const ids = await report.locator('form[id^="acknowledgeForm_"]')
      .evaluateAll(forms => forms.map(form => form.id.replace('acknowledgeForm_', '')));
    h.assert(ids.length === 1 && ids[0] === labs[0], 'The selected older lab silently opened a different version');
    await report.close();
  });
}
if (require.main === module) runWorkflow('inbox-lab-versions', workflow, { openPatient: false });
module.exports = { workflow };
