#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// A legacy/imported nullable booking flag must not prevent opening a referral.
const { assert, assertNotErrorPage, gotoApp, sqlString } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  let request;
  s.cleanup(() => {
    if (!request) return;
    assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE requestId=${request}
      AND demographicNo=${patient} AND reason=${sqlString(marker)}`) === '1', 'Referral fixture ownership changed');
    sql.execute(`DELETE FROM consultationRequests WHERE requestId=${request} AND demographicNo=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE requestId=${request}`) === '0',
      'Referral fixture cleanup failed');
  });
  const service = sql.value('SELECT MIN(serviceId) FROM consultationServices');
  assert(/^[1-9]\d*$/.test(service), 'The consultation service reference fixture is missing');
  const specialist = sql.value('SELECT MIN(specId) FROM professionalSpecialists');
  assert(/^[1-9]\d*$/.test(specialist), 'The specialist reference fixture is missing');
  request = sql.value(`INSERT INTO consultationRequests
    (referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,
     clinicalInfo,currentMeds,allergies,concurrentProblems,patientWillBook,lastUpdateDate)
    VALUES(CURRENT_DATE,${service},${specialist},${sqlString(provider)},${patient},'1','2',${sqlString(marker)},
      'Synthetic legacy referral','','','',NULL,NOW()); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(request), 'Referral fixture was not created');
  await s.step('nullable legacy booking flag opens without an error or data rewrite', async () => {
    // Direct navigation deliberately covers an existing saved referral URL.
    const page = await s.context.newPage();
    const response = await gotoApp(page, s.config.baseUrl, `/encounter/ViewRequest?requestId=${request}`);
    assert(response && response.status() === 200, 'Legacy referral did not render successfully');
    await assertNotErrorPage(page, 'legacy referral');
    await page.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'visible' });
    assert(!(await page.locator('input[name="patientWillBook"]').isChecked()),
      'An unspecified booking flag became an affirmative booking instruction');
    assert(sql.value(`SELECT patientWillBook IS NULL FROM consultationRequests WHERE requestId=${request}`) === '1',
      'Viewing a legacy referral rewrote its booking flag');
    await page.close();
  });
}
if (require.main === module) runWorkflow('consultation-null-booking', workflow);
module.exports = { workflow };
