#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// hrm-tickler: making a tickler from an HRM report's Tickler button. The viewer (hospitalReportManager/Display,
// displayHRMReport.jsp) opens tickler/ForwardDemographicTickler with docType=HRM, the report and the patient the
// report is linked to; Save posts tickler/DbTicklerAdd, which attaches the report (ticklerdocs H). lab-linked-tickler
// (open PR) covers a lab's Tickler and document-tickler a document's; tickler-forward-filters leaves this forward out.
// User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ HRM Documents ▸ the owned report ▸ Tickler ▸ Save.
//   1. the form names the report's linked patient and carries the report; Save stores one tickler for that patient
//      with the report attached, asks nothing and closes the window;
//   2. the patient's tickler list shows the tickler with its one attachment;
//   3. a report linked to no patient offers its Tickler and Msg buttons disabled.
// Fixtures: two marker-named text HRM reports (lib/gap-records-hrm-fixture.js: the XML file in DOCUMENT_DIR and its
// HRMDocument row), one linked to the owned FAKE- patient and routed to the login, one linked to nobody. Cleanup
// deletes the check's ticklers (by marker) with their attachments, comments and history, then both reports with
// their links and files, and asserts none remain. No clinic-wide state is changed.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { createOwnedHrmReport } = require('./lib/gap-records-hrm-fixture');

const q = h.sqlString;
const POSITIVE = /^[1-9]\d*$/;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const message = `${marker} from the HRM report`;
  const linked = createOwnedHrmReport({ sql, marker, suffix: 'linked', description: `${marker} linked report`,
    reportText: `${marker} linked report body` });
  const unlinked = createOwnedHrmReport({ sql, marker, suffix: 'unlinked', description: `${marker} unlinked report`,
    reportText: `${marker} unlinked report body` });

  s.cleanup(() => {
    const ticklers = sql.rows(`SELECT tickler_no FROM tickler WHERE message LIKE ${q(`${marker}%`)}`).map(([id]) => id);
    h.assert(ticklers.every(id => POSITIVE.test(id)), 'Owned tickler id is invalid');
    if (ticklers.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ticklers.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ticklers.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ticklers.join(',')});
        DELETE FROM tickler WHERE tickler_no IN (${ticklers.join(',')}) AND message LIKE ${q(`${marker}%`)}`);
    }
    linked.remove();
    unlinked.remove();
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE message LIKE ${q(`${marker}%`)}`) === '0', 'Owned ticklers remain');
  });

  const reportId = linked.create();
  sql.execute(`INSERT INTO HRMDocumentToDemographic (demographicNo,hrmDocumentId,timeAssigned) VALUES (${patient},${reportId},NOW());
    INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (${q(provider)},${reportId},0,0)`);
  const unlinkedId = unlinked.create();

  /** The Add Tickler form's patient and attachment, as the user sees them. */
  async function readForm(form) {
    await form.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 30000 });
    return form.evaluate(() => {
      const service = document.forms.serviceform.elements;
      return {
        demographicNo: service.demographic_no.value, docType: service.docType.value, docId: service.docId.value,
        patientName: document.forms.ADDAPPT.elements.keyword.value,
      };
    });
  }

  let viewer;
  await s.step('E-Chart ▸ HRM Documents ▸ the report ▸ Tickler: Save stores the tickler with the report attached', async () => {
    const chart = await s.chart();
    const list = await s.popup(chart, chart.locator('#leftNavBar a[onclick*="/hospitalReportManager/ViewDocList"]').first(), 'hrm-list');
    viewer = await s.popup(list, list.locator(`#tblHRM a[onclick*="Display?id=${reportId}'"]`), 'hrm-viewer');
    await viewer.locator(`#hrmdoc_${reportId}`).waitFor({ state: 'attached' });
    const button = viewer.locator(`#mainTickler_${reportId}`);
    h.assert(await button.isEnabled(), 'The linked report\'s Tickler button is disabled');
    const form = await s.popup(viewer, button, 'tickler-add-from-hrm');
    const shown = await readForm(form);
    const linkedPatients = sql.rows(`SELECT demographicNo FROM HRMDocumentToDemographic WHERE hrmDocumentId=${q(reportId)}`).map(([no]) => no);
    h.assert(linkedPatients.length === 1 && shown.demographicNo === linkedPatients[0] && shown.demographicNo === String(patient),
      `The form names patient ${shown.demographicNo}, not the report's linked patient (${linkedPatients.join(', ')})`);
    h.assert(shown.patientName.startsWith(`${marker}, `), `The form names ${JSON.stringify(shown.patientName)}, not the owned patient`);
    h.assert(shown.docType === 'HRM' && shown.docId === String(reportId), `The form carries ${shown.docType} ${shown.docId}, not the report`);
    await form.locator('textarea[name="ticklerMessage"]').fill(message);
    // The save posts into a hidden frame; on success the window broadcasts the refresh and closes itself.
    const dialogs = await h.withExpectedDialogs(form, async () => {
      const closed = form.waitForEvent('close', { timeout: 30000 });
      await form.locator('input.btn-primary[name="Button"]').first().click();
      await closed;
    });
    h.assert(dialogs.length === 0, `Save asked ${JSON.stringify(dialogs.map(entry => entry.text))}`);
    await expectValue(sql, `SELECT COUNT(*) FROM tickler WHERE message=${q(message)}`, '1', 'The tickler was not saved once');
    const [[ticklerNo, savedFor, status]] = sql.rows(`SELECT tickler_no, demographic_no, status FROM tickler WHERE message=${q(message)}`);
    h.assert(savedFor === String(patient) && status === 'A', `The tickler was saved for ${savedFor} with status ${status}`);
    const attached = sql.rows(`SELECT doctype, document_no FROM ticklerdocs WHERE tickler_id=${ticklerNo}`);
    h.assert(attached.length === 1 && attached[0][0] === 'H' && attached[0][1] === String(reportId),
      `The tickler's attachments are ${JSON.stringify(attached)}, not the report`);
    await list.close().catch(() => {});
  });

  await s.step('the patient\'s tickler list shows the tickler with its one attachment', async () => {
    const page = await s.context.newPage();
    await h.gotoApp(page, s.config.baseUrl, `/tickler/ViewTicklerDemoMain?demoview=${encodeURIComponent(patient)}&ticklerview=A`, 'networkidle');
    const row = page.locator('tr', { hasText: message }).first();
    await row.waitFor({ state: 'visible', timeout: 30000 });
    const markers = await row.locator('a, span').filter({ hasText: /^ATT$/ }).count();
    h.assert(markers === 1, `The patient's tickler list shows ${markers} attachments on the tickler`);
    await page.close();
  });

  await s.step('a report linked to no patient offers Tickler and Msg disabled', async () => {
    const page = await s.context.newPage();
    await h.gotoApp(page, s.config.baseUrl, `/hospitalReportManager/Display?id=${unlinkedId}`);
    await page.locator(`#hrmdoc_${unlinkedId}`).waitFor({ state: 'attached' });
    h.assert(await page.locator(`#mainTickler_${unlinkedId}`).isDisabled(), 'The unlinked report\'s Tickler button is enabled');
    h.assert(await page.locator(`#msgBtn_${unlinkedId}`).isDisabled(), 'The unlinked report\'s Msg button is enabled');
    await page.close();
    if (viewer && !viewer.isClosed()) await viewer.close().catch(() => {});
  });
}

if (require.main === module) runWorkflow('hrm-tickler', workflow, { openPatient: true });
module.exports = { workflow };
