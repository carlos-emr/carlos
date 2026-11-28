#!/usr/bin/env node
/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
// Authenticated rendering regressions for the viewer and both native attachment gates.
// Owns every HRM row, patient relationship and XML file; no external HRM transport is used.
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createOwnedHrmReport, hrmXml } = require('./lib/gap-records-hrm-fixture');
const { ownedReferrals, openConsultationsList, openFromList } = require('./lib/consultation-referral-fixture');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const fixtures = [];
  s.cleanup(() => fixtures.forEach(fixture => fixture.remove()));
  const group = `${marker}-group`;
  const create = (suffix, date, grouped = true) => {
    const fixture = createOwnedHrmReport({ sql, marker, suffix, description: `${marker} ${suffix}`, reportText: `${marker} report` });
    fixtures.push(fixture);
    const id = fixture.create();
    if (grouped) fs.writeFileSync(fixture.file, hrmXml(marker, group, `${marker} report`));
    sql.execute(`UPDATE HRMDocument SET reportDate=${date ? h.sqlString(date) : 'NULL'},timeReceived='2026-02-01 12:00:00',
      sourceFacilityReportNo=${h.sqlString(grouped ? group : `${marker}-${suffix}`)} WHERE id=${id};
      INSERT INTO HRMDocumentToDemographic (demographicNo,hrmDocumentId,timeAssigned) VALUES (${patient},${h.sqlString(id)},NOW());
      INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (${h.sqlString(provider)},${h.sqlString(id)},0,0)`);
    return id;
  };
  const unknown = create('unknown', null);
  const older = create('older', '2026-01-01 10:00:00');
  const tied = create('tied', '2026-01-01 10:00:00');
  const current = create('current', '2026-03-01 10:00:00');
  const unrelated = create('unrelated', '2026-01-01 10:00:00', false);
  const unused = sql.value('SELECT MAX(id)+1000 FROM HRMDocument');
  const page = await s.context.newPage();
  await s.step('history skips malformed, missing and unrelated IDs and labels a missing date explicitly', async () => {
    const requested = [older, unrelated, tied, unknown, unused, 'bad', older].join(',');
    await h.gotoApp(page, s.config.baseUrl, `/hospitalReportManager/Display?id=${current}&segmentId=${current}&providerNo=${provider}&status=N&duplicateLabIds=${encodeURIComponent(requested)}`);
    const history = page.locator('#duplicatesMessage tbody tr');
    const ids = await history.locator('td:first-child a').allTextContents();
    h.assert(JSON.stringify(ids) === JSON.stringify([tied, older, unknown]), `The validated history order is wrong: ${ids}`);
    h.assert((await history.last().innerText()).includes('Unknown date'), 'A missing date was not labelled');
    h.assert((await page.locator('[role="alert"]').allTextContents()).some(text => text.includes('unavailable')),
      'The skipped history entries have no visible notice');
    const opened = await s.popup(page, history.first().locator('td:first-child a'), 'hrm-history-open');
    h.assert(await opened.opener() === page, 'Opening history severed the existing report-window opener');
    h.assert(new URL(opened.url()).searchParams.get('providerNo') === provider
      && new URL(opened.url()).searchParams.get('status') === 'N', 'The history link lost its inbox navigation context');
    await opened.close();
  });
  const assertPicker = async (surface, rows, name, scope = '') => {
    const versions = surface.locator(rows);
    const ids = await versions.locator('input[name="hrmNo"]').evaluateAll(elements => elements.map(element => element.value));
    h.assert(JSON.stringify(ids) === JSON.stringify([tied, older, unknown]), `${name} version order is wrong: ${ids}`);
    const labels = await versions.allTextContents();
    h.assert(labels.every(text => text.includes('Report:') && text.includes('received:')), `${name} version labels omit the dates`);
    h.assert(labels[2].includes('Unknown date'), `${name} omitted the unknown date label`);
    h.assert(await surface.locator(`${scope}input[name="hrmNo"][value="${current}"]`).count() === 1, 'The current report selection disappeared');
    h.assert(await surface.locator(`${scope}input[name="hrmNo"]:checked`).count() === 0, 'A version was selected without an existing attachment');
  };
  await s.step('consultation picker uses dated version labels and preserves the current report and unchecked defaults', async () => {
    // previewDocs is an AJAX fragment whose scripts use the consultation page's jQuery.
    // Exercise its real Manage Attachments dialog, rather than navigating to the fragment.
    const referrals = ownedReferrals(s);
    const reason = `${marker} version picker`;
    const requestId = referrals.seed({ reason });
    const list = await openConsultationsList(s);
    const form = await openFromList(s, list, requestId, reason);
    await form.locator('#attachDocumentPanelBtn').click();
    await form.locator(`#attachDocumentDisplay #hrmVersions${current}`).waitFor({ state: 'attached' });
    await assertPicker(form, `#attachDocumentDisplay #hrmVersions${current} li`, 'consultation', '#attachDocumentDisplay ');
    await form.close();
  });
  await s.step('eForm picker uses dated version labels and preserves the current report and unchecked defaults', async () => {
    await h.gotoApp(page, s.config.baseUrl, `/eform/attachEform?demo=${patient}`);
    await assertPicker(page, 'details:has(input[name="hrmNo"]) .item', 'eForm');
  });
}

if (require.main === module) runWorkflow('hrm-version-history', workflow, { openMaster: false });
module.exports = { workflow };
