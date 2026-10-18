#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// document-tickler: making a tickler from a document's Tickler link. The link opens tickler/ForwardDemographicTickler
// (ForwardDemographicTickler2Action ▸ tickler/ticklerAdd.jsp) with docType=DOC, the document and the patient; Save posts
// tickler/DbTicklerAdd, which stores the tickler and attaches the document (ticklerdocs). tickler-attachments covers the
// attachment picker, and tickler-forward-filters leaves this forward out on purpose.
// User path: Schedule ▸ Search ▸ Master Record ▸ Documents ▸ the owned document's Tickler link ▸ Add Tickler ▸ Save.
//   1. the form names the owned patient and carries the document; Save stores one tickler for that patient with the
//      document attached, asks nothing and closes the window;
//   2. the patient's tickler list shows the tickler with its one attachment;
//   3. LAST: the login's own eDoc list (Schedule ▸ eDoc) offers no Tickler link on the login's own document, and the
//      URL that link opened (demographic_no = the provider number) opens the form with no patient, says a patient
//      must be chosen, and Save stores nothing (finding 377).
// Fixtures: two marker-named HTML documents seeded by SQL, the way an eForm's template files them (content in docxml,
// no file): one filed under the owned patient, one under the login (ctl_document module "providers"). Cleanup deletes
// the check's ticklers (by marker) with their attachments, comments and history, and both documents with their links,
// and asserts none remain. The login's eDoc list is clinic-wide, so the check runs alone (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const q = h.sqlString;
const POSITIVE = /^[1-9]\d*$/;
const CHOOSE_PATIENT = 'No patient was filled in from this document. Choose the patient this tickler is for.';
// tickler.ticklerAdd.msgInvalidDemographic: the form's own refusal of a Save without a patient.
const NO_PATIENT_REFUSAL = 'Invalid demographic information, please verify!';

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const patientDocDesc = `${marker} patient document`;
  const providerDocDesc = `${marker} login document`;
  const message = `${marker} from the document`;

  const ownedDocuments = () => sql.rows(`SELECT document_no FROM document
    WHERE docdesc IN (${q(patientDocDesc)},${q(providerDocDesc)})`).map(([id]) => id);
  const ownedTicklers = () => sql.rows(`SELECT tickler_no FROM tickler WHERE message LIKE ${q(`${marker}%`)}`).map(([id]) => id);
  s.cleanup(() => {
    const ticklers = ownedTicklers();
    h.assert(ticklers.every(id => POSITIVE.test(id)), 'Owned tickler id is invalid');
    if (ticklers.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ticklers.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ticklers.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ticklers.join(',')});
        DELETE FROM tickler WHERE tickler_no IN (${ticklers.join(',')}) AND message LIKE ${q(`${marker}%`)}`);
    }
    const documents = ownedDocuments();
    h.assert(documents.every(id => POSITIVE.test(id)), 'Owned document id is invalid');
    if (documents.length) {
      // HTML documents keep their content in the row (docxml); no file is written to the document store.
      sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${documents.join(',')});
        DELETE FROM document WHERE document_no IN (${documents.join(',')}) AND docdesc IN (${q(patientDocDesc)},${q(providerDocDesc)})`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE message LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM document WHERE docdesc IN (${q(patientDocDesc)},${q(providerDocDesc)}))`) === '0',
    'Owned tickler or document rows remain');
  });

  function seedDocument(desc, module, moduleId) {
    const id = h.insertId(sql, `INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,
      public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime,docxml)
      VALUES ('forms',${q(desc)},'html',${q(provider)},${q(provider)},'H','text/html',0,0,0,CURDATE(),NOW(),NOW(),${q(`<p>${desc}</p>`)})`, desc);
    sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES (${q(module)},${q(moduleId)},${id},'A')`);
    return id;
  }
  const patientDoc = seedDocument(patientDocDesc, 'demographic', patient);
  const providerDoc = seedDocument(providerDocDesc, 'providers', provider);

  /**
   * The document list's Tickler links for one document. The link's URL is JavaScript-encoded into its onclick
   * (the "&" after the id arrives as \x26), so the id is matched up to the next non-digit, not up to a "&".
   */
  async function ticklerLinks(report, docId) {
    const links = report.locator('a[onclick*="ForwardDemographicTickler"]');
    const indexes = await links.evaluateAll((nodes, id) => nodes.map((node, index) => {
      const onclick = node.getAttribute('onclick') || '';
      const at = onclick.indexOf(`docId=${id}`);
      const next = at < 0 ? '' : onclick.charAt(at + `docId=${id}`.length);
      return at >= 0 && !(next >= '0' && next <= '9') ? index : -1;
    }).filter(index => index >= 0), String(docId));
    return indexes.map(index => links.nth(index));
  }

  /** The Add Tickler form's patient, document and the choose-a-patient message, as the user sees them. */
  async function readForm(form) {
    await form.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 30000 });
    return form.evaluate(() => {
      const service = document.forms.serviceform.elements;
      const notice = document.getElementById('choosePatientForDocument');
      return {
        demographicNo: service.demographic_no.value, docType: service.docType.value, docId: service.docId.value,
        patientName: document.forms.ADDAPPT.elements.keyword.value,
        notice: notice && notice.offsetParent !== null ? notice.innerText.trim() : null,
      };
    });
  }

  let ticklerNo;
  await s.step('Master Record ▸ Documents ▸ Tickler on the patient\'s document: Save stores the tickler with the document attached', async () => {
    // The Master Record's Documents link, by the URL it opens (an onclick on older pages, an href the shared patient
    // navigation opens as a popup since #4468).
    const documents = masterRecordLink(s.master, '/documentManager/ViewDocumentReport?function=demographic').first();
    const report = await s.popup(s.master, documents, 'edoc-patient');
    await report.getByText(patientDocDesc).first().waitFor({ state: 'attached' });
    const links = await ticklerLinks(report, patientDoc);
    h.assert(links.length === 1, `The patient's document list offers ${links.length} Tickler links on the owned document`);
    const form = await s.popup(report, links[0], 'tickler-add-from-document');
    const shown = await readForm(form);
    h.assert(shown.demographicNo === String(patient) && shown.patientName.startsWith(`${marker}, `),
      `The form names ${JSON.stringify(shown.patientName)} (${shown.demographicNo}), not the owned patient`);
    h.assert(shown.docType === 'DOC' && shown.docId === String(patientDoc), `The form carries ${shown.docType} ${shown.docId}, not the document`);
    h.assert(shown.notice === null, `The form asks for a patient: ${shown.notice}`);
    await form.locator('textarea[name="ticklerMessage"]').fill(message);
    // The save posts into a hidden frame; on success the window broadcasts the refresh and closes itself.
    const dialogs = await h.withExpectedDialogs(form, async () => {
      const closed = form.waitForEvent('close', { timeout: 30000 });
      await form.locator('input.btn-primary[name="Button"]').first().click();
      await closed;
    });
    h.assert(dialogs.length === 0, `Save asked ${JSON.stringify(dialogs.map(entry => entry.text))}`);
    await expectValue(sql, `SELECT COUNT(*) FROM tickler WHERE message=${q(message)}`, '1', 'The tickler was not saved once');
    const [[id, savedFor, status]] = sql.rows(`SELECT tickler_no, demographic_no, status FROM tickler WHERE message=${q(message)}`);
    ticklerNo = id;
    h.assert(savedFor === String(patient) && status === 'A', `The tickler was saved for ${savedFor} with status ${status}`);
    const attached = sql.rows(`SELECT doctype, document_no FROM ticklerdocs WHERE tickler_id=${ticklerNo}`);
    h.assert(attached.length === 1 && attached[0][0] === 'D' && attached[0][1] === String(patientDoc),
      `The tickler's attachments are ${JSON.stringify(attached)}, not the document`);
    await report.close().catch(() => {});
  });

  await s.step('the patient\'s tickler list shows the tickler with its one attachment', async () => {
    const list = await s.context.newPage();
    await h.gotoApp(list, s.config.baseUrl, `/tickler/ViewTicklerDemoMain?demoview=${encodeURIComponent(patient)}&ticklerview=A`, 'networkidle');
    const row = list.locator('tr', { hasText: message }).first();
    await row.waitFor({ state: 'visible', timeout: 30000 });
    const markers = await row.locator('a, span').filter({ hasText: /^ATT$/ }).count();
    h.assert(markers === 1, `The patient's tickler list shows ${markers} attachments on the tickler`);
    await list.close();
  });

  await s.step('the login\'s own eDoc list offers no Tickler on its own document, and that link\'s URL asks for a patient', async () => {
    const { page: report } = await ui.clickOpensPopupOrNavigates(s.schedule,
      s.schedule.locator("a[onclick*='/documentManager/ViewDocumentReport']").first(),
      { context: s.context, recorder: s.recorder, label: 'edoc-login', timeout: 20000 });
    await report.getByText(providerDocDesc).first().waitFor({ state: 'attached', timeout: 20000 });
    const offered = (await ticklerLinks(report, providerDoc)).length;
    if (report !== s.schedule) await report.close().catch(() => {});
    h.assert(offered === 0, `The login's eDoc list offers ${offered} Tickler link(s) on the login's own document`);
    // The URL the list built, a bookmark or an open window may still hold: the provider number as the patient.
    const form = await s.context.newPage();
    await h.gotoApp(form, s.config.baseUrl, `/tickler/ForwardDemographicTickler?docType=DOC&docId=${providerDoc}`
      + `&demographic_no=${encodeURIComponent(provider)}`);
    const shown = await readForm(form);
    // Everything is asserted before Save: a form still naming a patient (one numbered like the provider, which
    // this check did not create) must fail the step without filing anything on that patient's chart.
    h.assert(shown.demographicNo === '' && shown.patientName === '', `The form names patient ${shown.demographicNo} ${JSON.stringify(shown.patientName)}`);
    h.assert(shown.notice === CHOOSE_PATIENT, `The form does not say a patient must be chosen (${JSON.stringify(shown.notice)})`);
    const loginMessage = `${marker} from the login document`;
    await form.locator('textarea[name="ticklerMessage"]').fill(loginMessage);
    const dialogs = await h.withExpectedDialogs(form, async () => {
      await form.locator('input.btn-primary[name="Button"]').first().click();
      await form.locator('#error').waitFor({ state: 'visible', timeout: 10000 });
    });
    const refusal = (await form.locator('#error').innerText()).trim();
    const stored = sql.value(`SELECT COUNT(*) FROM tickler WHERE message=${q(loginMessage)}`);
    await form.close().catch(() => {});
    h.assert(refusal.includes(NO_PATIENT_REFUSAL), `Save without a patient was not refused by the form (${JSON.stringify(refusal)})`);
    h.assert(stored === '0' && dialogs.length === 0, `Save without a patient stored ${stored} tickler(s) and asked ${JSON.stringify(dialogs)}`);
  });
}

if (require.main === module) runWorkflow('document-tickler', workflow, { openPatient: true });
module.exports = { workflow };
