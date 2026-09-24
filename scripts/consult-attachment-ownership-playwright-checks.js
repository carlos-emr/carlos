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
 * A consultation request can only carry the consultation patient's own documents (#3903,
 * issue #3867; adapted from MagentaHealth/Open-O).
 *
 * The attachment ids a consultation save posts (docNo, labNo, ...) come from the browser. The
 * save attached whatever ids arrived, so a forged or stale form could attach, and then send to
 * a specialist, another patient's document. The save now verifies every newly attached id
 * belongs to the patient before its first write, and refuses with a generic message that does
 * not say which attachment failed.
 *
 * From the patient's consultation list ("New Consultation"), the check:
 *   1. saves with another patient's document id added to the form: refused, nothing written;
 *   2. saves with one of the patient's own documents: created, and the attachment recorded.
 *
 * Every consultation row it creates is removed afterwards. Uses the demo dataset's documents:
 * the patient is the one with the most active documents, the foreign document any other
 * patient's.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');

const REFUSAL = /attachments could not be verified for this patient/i;

function activeDocumentsSql(where) {
  return 'SELECT c.module_id, c.document_no FROM ctl_document c JOIN document d ON d.document_no = c.document_no '
    + `WHERE c.module = 'demographic' AND d.status <> 'D' AND ${where}`;
}

async function pickFirstAutocomplete(page, inputSelector) {
  await page.locator(inputSelector).click();
  const item = page.locator('ul.ui-autocomplete:visible li.ui-menu-item').first();
  await item.waitFor({ state: 'visible', timeout: 15000 });
  await item.click();
  await page.waitForTimeout(250);
}

async function openNewConsultation(context, config, recorder, demographicNo, label) {
  const list = await context.newPage();
  h.wireStrictPage(list, `${label}-list`, recorder);
  await h.gotoApp(list, config.baseUrl,
    `/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${encodeURIComponent(demographicNo)}`);
  await list.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const [form] = await Promise.all([
    context.waitForEvent('page', { timeout: 30000 }),
    list.locator('a.btn', { hasText: /New Consultation/i }).first().click(),
  ]);
  h.wireStrictPage(form, `${label}-form`, recorder);
  await form.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await form.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: 30000 });
  await list.close();
  return form;
}

/** Fill the request and add one attachment id the way the attach dialog records it. */
async function fillAndAttach(form, reason, documentNo) {
  await pickFirstAutocomplete(form, '#serviceInput');
  await pickFirstAutocomplete(form, '#specialistInput');
  await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
  await form.evaluate((docNo) => {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'docNo';
    input.value = docNo;
    document.getElementById('EctConsultationFormRequest2Form').appendChild(input);
  }, documentNo);
  await Promise.all([
    form.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/encounter/RequestConsultation'), { timeout: 30000 }),
    form.locator('input[name="submitSaveOnly"]').click(),
  ]);
  await form.waitForLoadState('domcontentloaded', { timeout: 30000 });
}

async function main() {
  const config = h.readConfig();
  const sql = h.createSqlRunner(config.mysql);
  const recorder = h.createRecorder();
  const marker = `PW-ATTACH-${randomBytes(6).toString('hex')}`;
  const requestIds = () => sql.rows(`SELECT requestId FROM consultationRequests
    WHERE reason LIKE ${h.sqlString(`${marker}%`)}`).map(([id]) => id);
  let browser;
  try {
    const [patient, ownDoc] = sql.rows(`${activeDocumentsSql('1=1')}
      ORDER BY (SELECT COUNT(*) FROM ctl_document x WHERE x.module='demographic' AND x.module_id=c.module_id) DESC,
      c.document_no LIMIT 1`)[0] || [];
    if (!patient) throw new h.SkipCheck('no patient has an active document to attach');
    const [, foreignDoc] = sql.rows(`${activeDocumentsSql(`c.module_id <> ${patient}`)}
      AND c.document_no NOT IN (SELECT document_no FROM ctl_document WHERE module='demographic' AND module_id=${patient})
      ORDER BY c.document_no LIMIT 1`)[0] || [];
    if (!foreignDoc) throw new h.SkipCheck('no second patient has an active document to use as the foreign attachment');

    browser = await h.launchBrowser(config);
    const context = await h.newContext(browser, config, { viewport: { width: 1440, height: 1100 } });
    await h.login(context, config, recorder);

    // 1. Another patient's document: refused before anything is written.
    const forged = await openNewConsultation(context, config, recorder, patient, 'forged');
    await fillAndAttach(forged, `${marker}-forged`, foreignDoc);
    await h.assertNotErrorPage(forged, 'refused consultation save');
    h.assert(REFUSAL.test(await forged.locator('body').innerText()),
      'saving with another patient\'s document did not show the attachment refusal');
    h.assert(requestIds().length === 0, 'a consultation was created with another patient\'s document attached');
    h.assert(sql.value(`SELECT COUNT(*) FROM consultdocs WHERE document_no=${foreignDoc} AND doctype='D'
      AND attach_date=CURDATE()`) === '0', 'the other patient\'s document was attached to a consultation');
    await forged.close();

    // 2. The patient's own document: created, attachment recorded.
    const own = await openNewConsultation(context, config, recorder, patient, 'own');
    await fillAndAttach(own, `${marker}-own`, ownDoc);
    h.assert(/ViewConfirmConsultationRequest/.test(own.url()),
      'saving with the patient\'s own document did not reach the confirmation page');
    const ids = requestIds();
    h.assert(ids.length === 1, `expected one consultation for the own-document save, found ${ids.length}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM consultdocs WHERE requestId=${ids[0]} AND document_no=${ownDoc}
      AND doctype='D' AND (deleted IS NULL OR deleted <> 'Y')`) === '1',
    'the patient\'s own document was not attached to the saved consultation');
    await own.close();

    h.assertStrictPage(recorder);
    return { patient, refused: 1, saved: 1 };
  } finally {
    try {
      if (browser) await browser.close();
    } finally {
      try {
        const ids = requestIds();
        if (ids.length) {
          sql.execute(`DELETE FROM consultdocs WHERE requestId IN (${ids.join(',')});
            DELETE FROM consultationRequestExt WHERE requestId IN (${ids.join(',')});
            DELETE FROM consultationRequests WHERE requestId IN (${ids.join(',')})`);
        }
      } finally {
        sql.dispose();
      }
    }
  }
}

if (require.main === module) h.runCheck({ name: 'consult-attachment-ownership', run: main });
module.exports = { main };
