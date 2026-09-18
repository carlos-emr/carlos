#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for the Lab Requisition 2007 practitioner number (issue #3724).
 *
 * The form rebuilds practitionerNo from the provider record on every render and
 * discards whatever the stored row held. When the provider has no ohip_no the old
 * code emitted "0000--00", and that double hyphen is a SQL line comment, so OWASP
 * CRS rule 942100 (libinjection) scores the posted form body as an injection attempt
 * and the reverse proxy answers 403 before the request reaches Tomcat. The
 * requisition could then never be saved again.
 *
 *   1. opens the patient's seeded requisition and asserts the rendered practitioner
 *      number carries no empty segment, even though the shipped demo row still
 *      stores "0000--00";
 *   2. asserts the rendered value is exactly what the provider record supports:
 *      empty when there is no ohip_no, "0000-<ohip_no>-<specialty>" when there is.
 *      This is what keeps the fix from degenerating into "blank it always";
 *   3. opens a new requisition and saves it, asserting the POST is not rejected.
 *      Against a deployment that fronts Tomcat with the CRS rules this is the step
 *      that used to return 403; against a bare Tomcat it still pins that the saved
 *      row carries no double hyphen.
 *
 * The seeded requisition is only read. The row created by step 3 is deleted in
 * runCheck's cleanup, which also runs when the check is interrupted.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: LABREQ_DEMOGRAPHIC_NO (1), LABREQ_PROVIDER_NO (999998).
 */

const {
  SkipCheck,
  assert,
  assertNotErrorPage,
  createRecorder,
  createSqlRunner,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  runCheck,
  wirePage,
} = require('./lib/playwright-harness');

const config = readConfig({ require: ['MYSQL_PASSWORD'] });
const demographicNo = process.env.LABREQ_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.LABREQ_PROVIDER_NO || '999998';
assert(/^\d+$/.test(demographicNo) && /^\d+$/.test(providerNo),
  'LABREQ_DEMOGRAPHIC_NO and LABREQ_PROVIDER_NO must be numeric');

/*
 * The server-side rule, restated here so the check fails when the two drift apart
 * rather than agreeing with whatever the page happens to render. Mirrors
 * PractitionerNumber.ohipRequisition and the "00" default that
 * FrmLabReq07Record.extractSpecialtyCode falls back to.
 */
function expectedPractitionerNo(ohipNo, comments) {
  const billing = (ohipNo || '').trim();
  if (!billing) {
    return '';
  }
  const match = /<xml_p_specialty_code>([\s\S]*?)<\/xml_p_specialty_code>/.exec(comments || '');
  const specialty = (match ? match[1] : '').trim() || '00';
  return `0000-${billing}-${specialty}`;
}

async function openForm(context, recorder, label, appPath) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.confirm = () => true;
    window.close = () => { window.__closed = true; };
  });
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, appPath, 'networkidle');
  await assertNotErrorPage(page, label);
  await page.locator('input[name="practitionerNo"]').first().waitFor({ state: 'attached', timeout: 30000 });
  return page;
}

const sql = createSqlRunner(config.mysql);
let highWaterMark = 0;

function rowsCreatedByThisRun() {
  return sql.rows(`SELECT ID FROM formLabReq07 WHERE demographic_no=${Number(demographicNo)} AND ID > ${highWaterMark} ORDER BY ID`)
    .map(([id]) => id);
}

runCheck({
  name: 'form-labreq-practitioner-no',
  cleanup() {
    try {
      for (const id of rowsCreatedByThisRun()) {
        sql.execute(`DELETE FROM formLabReq07 WHERE ID=${Number(id)}`);
      }
    } finally {
      sql.dispose();
    }
  },
  async run() {
    const recorder = createRecorder();
    let browser;
    try {
      const hasTable = Number(sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='formLabReq07'"));
      if (!hasTable) {
        throw new SkipCheck('formLabReq07 is an Ontario-only table and this database does not have it');
      }
      const provider = sql.rows(`SELECT IFNULL(ohip_no,''), IFNULL(comments,'') FROM provider WHERE provider_no='${Number(providerNo)}'`)[0];
      assert(provider, `provider ${providerNo} not found`);
      const expected = expectedPractitionerNo(provider[0], provider[1]);
      assert(!expected.includes('--'), `the expectation itself carries an empty segment: "${expected}"`);

      highWaterMark = Number(sql.value(`SELECT IFNULL(MAX(ID), 0) FROM formLabReq07 WHERE demographic_no=${Number(demographicNo)}`));
      const seeded = sql.rows(`SELECT ID, IFNULL(practitionerNo,'') FROM formLabReq07 WHERE demographic_no=${Number(demographicNo)} ORDER BY ID LIMIT 1`)[0];

      browser = await launchBrowser(config);
      const context = await newContext(browser, config);
      const landingPage = await login(context, config, recorder);
      await landingPage.close();

      // 1 & 2. An existing requisition renders a usable number, not the stored skeleton.
      if (seeded) {
        const stored = await openForm(context, recorder, 'labreq-existing',
          `/form/formlabreq07?demographic_no=${encodeURIComponent(demographicNo)}&formId=${encodeURIComponent(seeded[0])}&provNo=${encodeURIComponent(providerNo)}`);
        const rendered = await stored.locator('input[name="practitionerNo"]').first().inputValue();
        assert(!rendered.includes('--'),
          `requisition ${seeded[0]} rendered practitionerNo "${rendered}"; a double hyphen is what CRS 942100 rejects`);
        assert(rendered === expected,
          `requisition ${seeded[0]} rendered practitionerNo "${rendered}", expected "${expected}" from the provider record`);
        await stored.close();
        console.log(`PASS requisition ${seeded[0]} renders "${rendered}" (row stores "${seeded[1]}")`);
      } else {
        console.log('SKIP no seeded requisition for this patient; only the new-form path is checked');
      }

      // 3. A new requisition renders the same value and saves without being rejected.
      const page = await openForm(context, recorder, 'labreq-new',
        `/form/formlabreq07?demographic_no=${encodeURIComponent(demographicNo)}&formId=0&provNo=${encodeURIComponent(providerNo)}`);
      const fresh = await page.locator('input[name="practitionerNo"]').first().inputValue();
      assert(fresh === expected, `a new requisition rendered practitionerNo "${fresh}", expected "${expected}"`);

      const [saveResponse] = await Promise.all([
        page.waitForResponse((response) => response.request().method() === 'POST'
          && /\/form\/formname/.test(response.url()), { timeout: 30000 }),
        page.locator('input[type="submit"][value="Save"], input[type="submit"][value="Save & Exit"]').first().click(),
      ]);
      assert(saveResponse.status() < 400,
        `saving the requisition returned HTTP ${saveResponse.status()}; 403 here is the front door rejecting the posted body`);

      const created = rowsCreatedByThisRun();
      assert(created.length >= 1, 'Save did not create a formLabReq07 row');
      for (const [id, value] of sql.rows(`SELECT ID, IFNULL(practitionerNo,'') FROM formLabReq07 WHERE ID IN (${created.map(Number).join(',')})`)) {
        assert(!value.includes('--'), `saved requisition ${id} stored practitionerNo "${value}"`);
        assert(value === expected, `saved requisition ${id} stored practitionerNo "${value}", expected "${expected}"`);
      }
      await page.close();
      await context.close();

      assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
      assert(recorder.pageErrors.length === 0, `unexpected page errors: ${JSON.stringify(recorder.pageErrors, null, 2)}`);
      return `lab requisition saved (ID ${created.join(', ')}) with practitionerNo "${expected || '(empty)'}"`;
    } finally {
      if (browser) {
        await browser.close().catch(() => {});
      }
    }
  },
});
