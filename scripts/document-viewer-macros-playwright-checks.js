#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The document viewer's Macros menu: a provider's lab macros run on a document.
 *
 * WHY THIS CHECK EXISTS. A provider's lab macros (Preferences > Lab Recall Macros, the labMacroJSON preference) are offered
 * in the document viewer too. Choosing one asks documentManager/inboxManage which patient the document belongs to and posts
 * oscarMDS/RunMacro, which acknowledges the document with the macro's comment and, when the macro says so, files a tickler
 * for that patient with the document attached. lab-macro-tickler runs a macro on an HL7 lab; nothing ran one on a document.
 *
 * User path (as the shared test login): Schedule > Search > Master Record > E-Chart > the document's entry in Documents
 *   (the document viewer popup) > Macros > the macro.
 *
 * Asserted, on owned rows only:
 *   1. An acknowledge-only macro: RunMacro, sent with the document's patient, answers success and acknowledged. The test
 *      provider's routing row is A with the macro's comment, the other provider's row is New, and no tickler is filed.
 *   2. A macro that acknowledges and files a tickler: RunMacro answers success and acknowledged, with no failure alert. The
 *      row is A with the macro's comment, and one tickler is filed for the document's patient, assigned to the test
 *      provider and attached to the document (ticklerdocs doctype D).
 *
 * Fixtures: viewerDocuments (document-viewer-review-actions-playwright-checks.js): the owned FAKE- patient and two owned
 * PDFs, each routed New to the test provider and another active provider, with an active queue link. The test login's
 * labMacroJSON preference (per-user state lab-macro-tickler also writes) holds the two owned macros while the run lasts and
 * is restored exactly afterwards (the row deleted if there was none), so the check must not run concurrently with other
 * checks (live wrapper: EXCLUSIVE=1). Cleanup removes the macro tickler with its ticklerdocs / tickler_link /
 * tickler_comments / tickler_update rows and the documents' rows and files, and asserts them gone.
 */
const crypto = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { viewerDocuments } = require('./document-viewer-review-actions-playwright-checks');

const NAME = 'document-viewer-macros';
const TIMEOUT = 60000;
const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const stamp = crypto.randomBytes(3).toString('hex');
  const ackMacro = { name: `${marker} ack`, comment: `FAKE macro review ${stamp}` };
  const ticklerMacro = { name: `${marker} tickler`, comment: `FAKE macro tickler review ${stamp}`, message: `${marker} macro tickler` };
  const macroJson = JSON.stringify([
    { name: ackMacro.name, closeOnSuccess: false, acknowledge: { comment: ackMacro.comment } },
    { name: ticklerMacro.name, closeOnSuccess: false, acknowledge: { comment: ticklerMacro.comment },
      tickler: { taskAssignedTo: provider, message: ticklerMacro.message } },
  ]);
  const tickler = () => sql.rows(`SELECT tickler_no, demographic_no, task_assigned_to FROM tickler
    WHERE message=${q(ticklerMacro.message)} ORDER BY tickler_no`);
  const macroProperty = () => sql.rows(`SELECT id, IFNULL(value,'') FROM property WHERE provider_no=${q(provider)}
    AND name='labMacroJSON' ORDER BY id`);
  const macroBefore = macroProperty();
  h.assert(macroBefore.length <= 1, 'The test login has more than one labMacroJSON preference row; refusing to change it');

  const { docs, routing, otherRow, routingQuery, openViewer } = viewerDocuments(s, ['macro', 'tickler']);
  s.cleanup(() => {
    for (const [id] of tickler()) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id=${id}; DELETE FROM tickler_link WHERE tickler_no=${id};
        DELETE FROM tickler_comments WHERE tickler_no=${id}; DELETE FROM tickler_update WHERE tickler_no=${id};
        DELETE FROM tickler WHERE tickler_no=${id} AND demographic_no=${patient}`);
    }
    h.assert(tickler().length === 0, 'The macro tickler was not removed');
  });
  let macroRow = null;
  s.cleanup(() => {
    if (macroBefore.length === 0) {
      if (macroRow) sql.execute(`DELETE FROM property WHERE id=${macroRow} AND provider_no=${q(provider)} AND name='labMacroJSON'`);
    } else {
      sql.execute(`UPDATE property SET value=${q(macroBefore[0][1])} WHERE id=${macroBefore[0][0]}`);
    }
    h.assert(JSON.stringify(macroProperty()) === JSON.stringify(macroBefore), 'The test login\'s labMacroJSON preference was not restored');
  });
  if (macroBefore.length === 0) {
    macroRow = h.insertId(sql, `INSERT INTO property (name,value,provider_no) VALUES ('labMacroJSON',${q(macroJson)},${q(provider)})`,
      'lab macro preference');
  } else {
    sql.execute(`UPDATE property SET value=${q(macroJson)} WHERE id=${macroBefore[0][0]}`);
  }

  /** Macros > `macro`: the linked-patient lookup, then RunMacro with the document's patient; its reply and any alert. */
  async function runMacro(doc, macro) {
    const viewer = await openViewer(doc);
    await viewer.locator('.macro-dropdown button.dropdown-toggle').click();
    const item = viewer.locator('.macro-dropdown .dropdown-item').filter({ hasText: macro.name });
    h.assert(await item.count() === 1, `The Macros menu does not offer "${macro.name}" once`);
    let lookup;
    let run;
    // A refused macro alerts; it is reported with the reply rather than as a stray dialog.
    const dialogs = await h.withExpectedDialogs(viewer, async () => {
      [lookup, run] = await Promise.all([
        viewer.waitForResponse((response) => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/documentManager/inboxManage')
          && new URLSearchParams(response.request().postData() || '').get('method') === 'isDocumentLinkedToDemographic',
        { timeout: TIMEOUT }),
        viewer.waitForResponse((response) => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/oscarMDS/RunMacro'), { timeout: TIMEOUT }),
        item.click(),
      ]);
      await viewer.waitForTimeout(1000);
    });
    h.assert(lookup.status() === 200 && (await lookup.json()).demoId === patient,
      'The macro\'s linked-patient lookup did not name the document\'s patient');
    const query = new URL(run.url()).searchParams;
    const sent = new URLSearchParams(run.request().postData() || '');
    h.assert(query.get('name') === macro.name && query.get('demographicNo') === patient && sent.get('segmentID') === doc.id
      && sent.get('labType') === 'DOC', 'RunMacro was not sent for this macro, this document and its patient');
    h.assert(run.status() === 200, `RunMacro answered HTTP ${run.status()}`);
    const reply = await run.json();
    await viewer.close();
    return { reply, alerts: dialogs.map((dialog) => dialog.text) };
  }

  await s.step('Macros > an acknowledge-only macro acknowledges the document with the macro\'s comment', async () => {
    const doc = docs.macro;
    const before = otherRow(doc);
    const { reply, alerts } = await runMacro(doc, ackMacro);
    h.assert(reply.success === true && reply.acknowledged === true && alerts.length === 0,
      `RunMacro answered ${JSON.stringify(reply)} (alerts ${JSON.stringify(alerts)}) for an acknowledge-only macro on a document`);
    await expectValue(sql, routingQuery(doc), `A|${ackMacro.comment}`,
      'The macro did not acknowledge the document with its comment for the test provider');
    h.assert(otherRow(doc) === before, 'The macro changed the other provider\'s routing row');
    h.assert(tickler().length === 0, 'An acknowledge-only macro filed a tickler');
  });

  await s.step('Macros > a macro with a tickler acknowledges the document and files the tickler for its patient', async () => {
    const doc = docs.tickler;
    const before = otherRow(doc);
    const { reply, alerts } = await runMacro(doc, ticklerMacro);
    h.assert(reply.success === true && reply.acknowledged === true && alerts.length === 0,
      `RunMacro answered ${JSON.stringify(reply)} (alerts ${JSON.stringify(alerts)}) for a macro that acknowledges and files a `
      + `tickler on a document: the document was left "${routing(doc)}" and ${tickler().length} tickler(s) were filed`);
    await expectValue(sql, routingQuery(doc), `A|${ticklerMacro.comment}`,
      'The macro did not acknowledge the document with its comment for the test provider');
    const filed = tickler();
    h.assert(filed.length === 1 && filed[0][1] === patient && filed[0][2] === provider,
      `The macro filed ${JSON.stringify(filed)}, not one tickler for the document's patient assigned to the test provider`);
    h.assert(sql.value(`SELECT COUNT(*) FROM ticklerdocs WHERE tickler_id=${filed[0][0]} AND doctype='D'
      AND document_no=${doc.id}`) === '1', 'The macro\'s tickler is not attached to the document');
    h.assert(otherRow(doc) === before, 'The macro changed the other provider\'s routing row');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
