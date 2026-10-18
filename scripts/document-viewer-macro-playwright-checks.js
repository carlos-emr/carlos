#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Macros run from the document viewer (issue #4988). lab-macro-tickler covers the same macros on
 * an HL7 lab; this covers a stored document, which the viewer posts to oscarMDS/RunMacro as
 * labType=DOC. The tickler attachment service refuses a document submitted as a lab, so a macro
 * that filed a tickler used to answer {"success":false} and acknowledge nothing.
 *
 * User path: the document viewer (documentManager/ViewShowDocument, the Inbox's document pane) ▸
 * Macros ▸ a macro.
 * Asserted:
 *   1. an acknowledge-only macro acknowledges the document with its comment and files no tickler;
 *   2. a run naming another patient is refused as a whole: success=false, the document is still
 *      New and no tickler is filed (the attachment is checked before anything is acknowledged);
 *   3. an acknowledge + tickler macro answers success with acknowledged=true, raises no dialog,
 *      acknowledges the document with its comment, and files one tickler for the document's
 *      patient with the document attached as a document (ticklerdocs doctype D, no lab source).
 * Fixtures: the owned patient, one owned 1-page PDF document (row + file + ctl_document link) with a
 * New routing row for the login's provider, and two macros set on that provider for the run.
 * The provider's own macro list is restored, and the ticklers, routing row, document and file are
 * removed (and asserted gone) on cleanup.
 * Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const {
  ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments,
  assertOwnedPdfDocumentsRemoved, directory,
} = require('./lib/stored-pdf-documents');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const [doc] = ownedPdfDocuments(store, marker, [{ key: 'A', pages: 1 }]);
  const ackOnly = { name: `${marker} ack`, acknowledge: { comment: `${marker} reviewed` } };
  const ticklerMessage = `${marker} call patient`;
  const withTickler = {
    name: `${marker} ack+tickler`,
    acknowledge: { comment: `${marker} reviewed and filed` },
    tickler: { taskAssignedTo: provider, message: ticklerMessage, quantity: '2', timeUnits: '7' },
  };

  const owned = () => ({ sql, marker, patient, docs: [doc], files: [doc.file] });
  const propertyWhere = `provider_no=${h.sqlString(provider)} AND name='labMacroJSON'`;
  const macrosBefore = sql.value(`SELECT COUNT(*) FROM property WHERE ${propertyWhere}`) === '0'
    ? null : sql.value(`SELECT IFNULL(value,'') FROM property WHERE ${propertyWhere} ORDER BY id DESC LIMIT 1`);
  const ticklerIds = () => sql.rows(`SELECT tickler_no FROM tickler WHERE message=${h.sqlString(ticklerMessage)} ORDER BY tickler_no`).map(row => row[0]);
  const routingStatus = () => sql.value(`SELECT CONCAT(status,'|',IFNULL(comment,'')) FROM providerLabRouting
    WHERE lab_no=${doc.id} AND lab_type='DOC' AND provider_no=${h.sqlString(provider)} ORDER BY id DESC LIMIT 1`);

  s.cleanup(() => {
    for (const id of ticklerIds()) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id=${Number(id)}; DELETE FROM tickler_comments WHERE tickler_no=${Number(id)};
        DELETE FROM tickler WHERE tickler_no=${Number(id)}`);
    }
    if (doc.id) sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${Number(doc.id)} AND lab_type='DOC'`);
    if (macrosBefore === null) sql.execute(`DELETE FROM property WHERE ${propertyWhere}`);
    else sql.execute(`UPDATE property SET value=${h.sqlString(macrosBefore)} WHERE ${propertyWhere}`);
    removeOwnedPdfDocuments(owned());
    assertOwnedPdfDocumentsRemoved(owned());
    h.assert(ticklerIds().length === 0, 'The macro ticklers were not removed');
  });

  seedOwnedPdfDocuments({ sql, store, patient, provider, docs: [doc] });
  const macrosJson = JSON.stringify([ackOnly, withTickler]);
  h.assert(macrosJson.length < 2000, 'The macro fixture does not fit the property column');
  if (macrosBefore === null) {
    sql.execute(`INSERT INTO property (name,value,provider_no) VALUES ('labMacroJSON',${h.sqlString(macrosJson)},${h.sqlString(provider)})`);
  } else {
    sql.execute(`UPDATE property SET value=${h.sqlString(macrosJson)} WHERE ${propertyWhere}`);
  }
  const stageNew = () => {
    sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${Number(doc.id)} AND lab_type='DOC';
      INSERT INTO providerLabRouting (provider_no,lab_no,status,comment,timestamp,lab_type)
      VALUES (${h.sqlString(provider)},${Number(doc.id)},'N','',NOW(),'DOC')`);
  };

  let viewer;
  const openViewer = async () => {
    if (viewer && !viewer.isClosed()) await viewer.close();
    stageNew();
    viewer = await s.context.newPage();
    // The viewer's window.close() after a successful macro must not end the check's page.
    await viewer.addInitScript(() => { window.close = () => { window.__closed = true; }; });
    await h.gotoApp(viewer, s.config.baseUrl, `/documentManager/ViewShowDocument?segmentID=${doc.id}&providerNo=${provider}&inWindow=true`);
    await viewer.locator('.macro-dropdown button.dropdown-toggle').waitFor({ state: 'visible' });
  };
  const runMacro = async name => {
    await viewer.locator('.macro-dropdown button.dropdown-toggle').click();
    const entry = viewer.locator('.macro-dropdown a.dropdown-item', { hasText: name }).first();
    await entry.waitFor({ state: 'visible' });
    const [response] = await Promise.all([
      viewer.waitForResponse(r => r.request().method() === 'POST' && /\/oscarMDS\/RunMacro\?/.test(r.url())),
      entry.click(),
    ]);
    h.assert(response.status() === 200, `RunMacro answered HTTP ${response.status()}`);
    return { response, json: await response.json() };
  };

  await s.step('an acknowledge-only macro acknowledges the document with its comment and files no tickler', async () => {
    await openViewer();
    const { response, json } = await runMacro(ackOnly.name);
    h.assert(json.success === true && json.acknowledged === true, `The acknowledge-only macro answered ${JSON.stringify(json)}`);
    h.assert(new URL(response.url()).searchParams.get('demographicNo') === patient, 'RunMacro was not sent with the document\'s patient');
    await expectValue(sql, `SELECT CONCAT(status,'|',IFNULL(comment,'')) FROM providerLabRouting WHERE lab_no=${doc.id}
      AND lab_type='DOC' AND provider_no=${h.sqlString(provider)} ORDER BY id DESC LIMIT 1`, `A|${ackOnly.acknowledge.comment}`,
    'The acknowledge-only macro did not acknowledge the document with its comment');
    h.assert(ticklerIds().length === 0, 'The acknowledge-only macro filed a tickler');
  });

  await s.step('a macro run naming another patient is refused as a whole: nothing acknowledged, no tickler', async () => {
    await openViewer();
    const foreign = sql.value(`SELECT demographic_no FROM demographic WHERE demographic_no<>${Number(patient)} AND demographic_no>0 ORDER BY demographic_no LIMIT 1`);
    h.assert(foreign, 'No second patient exists to name in the crafted run');
    // The same POST the viewer makes, with the demographicNo swapped for another patient's.
    const refused = await viewer.evaluate(async ({ ctx, name, demographicNo, docId }) => {
      const params = new URLSearchParams(new FormData(document.getElementById(`acknowledgeForm_${docId}`)));
      if (!params.get('CSRF-TOKEN')) params.set('CSRF-TOKEN', getCsrfToken());
      const response = await fetch(`${ctx}/oscarMDS/RunMacro?name=${encodeURIComponent(name)}&demographicNo=${encodeURIComponent(demographicNo)}`, {
        method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString(), credentials: 'same-origin',
      });
      return { status: response.status, text: await response.text() };
    }, { ctx: new URL(s.config.baseUrl).pathname.replace(/\/$/, ''), name: withTickler.name, demographicNo: foreign, docId: doc.id });
    const json = JSON.parse(refused.text);
    h.assert(refused.status === 200 && json.success === false && json.acknowledged !== true,
      `The run naming another patient was not refused: HTTP ${refused.status} ${refused.text.slice(0, 200)}`);
    h.assert(routingStatus() === 'N|', `The refused run changed the document's routing: "${routingStatus()}"`);
    h.assert(ticklerIds().length === 0, 'The refused run filed a tickler');
  });

  await s.step('an acknowledge + tickler macro acknowledges the document and files its tickler with the document attached', async () => {
    await openViewer();
    const { response, json } = await runMacro(withTickler.name);
    h.assert(json.success === true && json.acknowledged === true, `The tickler macro answered ${JSON.stringify(json)}`);
    h.assert(new URL(response.url()).searchParams.get('demographicNo') === patient, 'RunMacro was not sent with the document\'s patient');
    h.assert(s.recorder.dialogs.length === 0, `Running the macro raised a dialog: ${JSON.stringify(s.recorder.dialogs)}`);
    await expectValue(sql, `SELECT CONCAT(status,'|',IFNULL(comment,'')) FROM providerLabRouting WHERE lab_no=${doc.id}
      AND lab_type='DOC' AND provider_no=${h.sqlString(provider)} ORDER BY id DESC LIMIT 1`, `A|${withTickler.acknowledge.comment}`,
    'The tickler macro did not acknowledge the document with its comment');
    const ids = ticklerIds();
    h.assert(ids.length === 1, `Expected one tickler from the macro, found ${ids.length}`);
    h.assert(sql.value(`SELECT CONCAT_WS('|',demographic_no,status,task_assigned_to,creator,DATE(service_date))
      FROM tickler WHERE tickler_no=${Number(ids[0])}`) === `${patient}|A|${provider}|${provider}|${sql.value('SELECT DATE(NOW() + INTERVAL 14 DAY)')}`,
    'The tickler is not the document patient\'s, active, assigned and dated as the macro defines');
    // Attached as a document: doctype D, no lab source, the authenticated provider, live.
    h.assert(sql.value(`SELECT CONCAT_WS('|',doctype,IFNULL(lab_type,''),document_no,provider_no,deleted IS NULL)
      FROM ticklerdocs WHERE tickler_id=${Number(ids[0])}`) === `D||${doc.id}|${provider}|1`,
    'The tickler does not carry the document as a live document attachment');
    h.assert(sql.value(`SELECT COUNT(*) FROM ticklerdocs WHERE tickler_id=${Number(ids[0])}`) === '1',
      'The tickler carries more than the document attachment');
  });
}

if (require.main === module) runWorkflow('document-viewer-macro', workflow, { openMaster: false });
module.exports = { workflow };
