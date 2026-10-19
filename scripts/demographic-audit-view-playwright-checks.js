#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Master Record ▸ Audit Info: the patient's audit log page.
 *
 * The Master Record toolbar's "Audit Info" opens demographic/ViewDemographicAudit
 * (demographicAudit.jsp), which lists the patient's audit log rows (OscarLog by demographic_no):
 * time, provider, action, content, keyword and data. The audit-log checks assert the rows CARLOS
 * writes; only a route probe opened this page, so nothing checked what it shows.
 *
 * User path: Master Record ▸ Audit Info (popup) ▸ Search for the run marker. The table shows ten
 * rows a page, oldest first, and opening the chart writes rows of its own, so the check searches
 * as a user would. Asserted: the page is for the owned patient; the search lists both log rows
 * seeded for it, each with its action, content, keyword and data and the provider's name; markup
 * in a row's data is shown as text, not rendered; and the row seeded for a second owned patient,
 * which also carries the marker, is not listed.
 *
 * Fixtures: two log rows for the workflow's own FAKE patient and one for a second FAKE patient
 * (last name = the run marker plus "A"), all with content starting with the run marker. Cleanup
 * deletes exactly those log rows and the second patient, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const otherName = `${marker}A`;
  let other = '';
  s.cleanup(() => {
    sql.execute(`DELETE FROM log WHERE content LIKE ${q(`${marker}%`)}`);
    if (other) {
      sql.execute(`DELETE FROM demographicArchive WHERE demographic_no=${other};
        DELETE FROM demographic WHERE demographic_no=${other} AND last_name=${q(otherName)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM log WHERE content LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${q(otherName)})`) === '0', 'The seeded log rows or the second patient remain');
  });
  other = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
      provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(otherName)},'Audit','1970','05','06','F','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(other), 'The second owned patient was not created');
  const rows = [
    { action: 'FAKE-read', content: `${marker} chart read`, keyword: 'FAKE-key-1', data: `${marker} plain data` },
    { action: 'FAKE-update', content: `${marker} chart update`, keyword: 'FAKE-key-2', data: `<b>${marker} markup data</b>` },
  ];
  for (const row of rows) {
    sql.execute(`INSERT INTO log (dateTime,provider_no,action,content,contentId,ip,demographic_no,data)
      VALUES (NOW(),${q(provider)},${q(row.action)},${q(row.content)},${q(row.keyword)},'127.0.0.1',${patient},${q(row.data)})`);
  }
  const otherContent = `${marker} other patient's row`;
  sql.execute(`INSERT INTO log (dateTime,provider_no,action,content,contentId,ip,demographic_no,data)
    VALUES (NOW(),${q(provider)},'FAKE-read',${q(otherContent)},'FAKE-key-3','127.0.0.1',${other},'')`);
  const providerLastName = sql.value(`SELECT last_name FROM provider WHERE provider_no=${q(provider)}`);

  let audit;
  await s.step('Audit Info opens the audit page of the owned patient', async () => {
    const button = s.master.locator(`input[type="button"][onclick*="/demographic/ViewDemographicAudit?demographic_no=${patient}"]`).first();
    h.assert(await button.count() === 1, 'The Master Record has no Audit Info button');
    audit = await s.popup(s.master, button, 'demographic-audit');
    await audit.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(audit, 'Demographic audit page');
    h.assert(await audit.locator('table#auditLog').count() === 1, 'The audit page has no audit table');
    h.assert((await audit.locator('h2').first().innerText()).includes(`(${patient})`), 'The audit page is not for the owned patient');
  });

  await s.step('searching for the marker lists both of the owned patient\'s seeded rows with the provider\'s name', async () => {
    const search = audit.locator('input[type="search"][aria-controls="auditLog"]');
    await search.waitFor({ state: 'visible', timeout: TIMEOUT });
    await search.fill(marker);
    await audit.locator('table#auditLog tbody tr').filter({ hasText: rows[0].content }).waitFor({ timeout: TIMEOUT });
    for (const row of rows) {
      const line = audit.locator('table#auditLog tr').filter({ hasText: row.content });
      h.assert(await line.count() === 1, `The audit page does not list the "${row.action}" row exactly once`);
      const text = await line.innerText();
      for (const value of [row.action, row.keyword, row.data, providerLastName]) {
        h.assert(text.includes(value), `The "${row.action}" row does not show ${JSON.stringify(value)}`);
      }
    }
  });

  await s.step('markup in a row\'s data is shown as text', async () => {
    const line = audit.locator('table#auditLog tr').filter({ hasText: rows[1].content });
    h.assert((await line.innerText()).includes(rows[1].data), 'The data cell does not show the markup as text');
    h.assert(await line.locator('b').count() === 0, 'The data cell rendered the markup');
  });

  await s.step('the second owned patient\'s row is not listed, though it matches the search', async () => {
    h.assert(await audit.locator('table#auditLog tr').filter({ hasText: otherContent }).count() === 0,
      'The audit page lists another patient\'s row');
  });
}

if (require.main === module) runWorkflow('demographic-audit-view', workflow, { openPatient: true });
module.exports = { workflow };
