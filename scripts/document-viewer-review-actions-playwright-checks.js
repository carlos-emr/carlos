#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The document viewer's own review actions: Comment, and Acknowledge through its comment prompt.
 *
 * WHY THIS CHECK EXISTS. A clinician reviewing an incoming document records that review in the document viewer itself.
 * Comment and Acknowledge write the reviewing provider's providerLabRouting row (oscarMDS/UpdateStatus), and Acknowledge
 * also closes the document's Inbox queue link (documentManager/inboxManage updateDocStatusInQueue). The neighbouring
 * checks stop short of them: inbox-preview-acknowledge acknowledges labs only, because a document's Acknowledge opens a
 * comment prompt first; lab-acknowledge and inbox-acknowledge-per-provider drive HL7 labs; inbox-file-forward files a
 * document; stored-document-mutations covers Save & Next and the provider unlink. Nothing pressed a document's Comment
 * or Acknowledge. document-viewer-macros covers the viewer's Macros menu with the same fixture.
 *
 * User path (as the shared test login): Schedule > Search > Master Record > E-Chart > the document's entry in Documents
 *   (the document viewer popup, documentManager/ViewShowDocument?inWindow=true) > Comment / Acknowledge.
 *
 * Asserted, in order, on owned rows only:
 *   1. Comment > the comment prompt > OK posts UpdateStatus method=addComment for exactly that document. The test
 *      provider's routing row keeps status N and holds the typed comment, the viewer shows it, and the other provider's
 *      row and the queue link are unchanged.
 *   2. Acknowledge on the commented document (reopened) offers the saved comment in its prompt; OK keeps it. The row is A
 *      with the same comment, the queue link is closed (I), the viewer closes, and the other provider's row is still New.
 *   3. Acknowledge > Cancel on the comment prompt posts nothing and leaves the row New. Acknowledge > a typed comment > OK
 *      acknowledges with that comment for the test provider only and closes the queue link.
 *   4. On an uncommented document the comment prompt offers no text (not the viewer's "No comment" placeholder), and OK as
 *      offered acknowledges with no comment.
 *
 * Fixtures (viewerDocuments, shared with document-viewer-macros): the owned FAKE- patient (runWorkflow) and owned PDFs in
 * DOCUMENT_DIR (lib/stored-pdf-documents.js), each with its ctl_document and patientLabRouting rows, a New
 * providerLabRouting row for the test provider and one for another active provider, and an active queue_document_link.
 * The owned PDFs are in another provider's Inbox and the shared queue while the run lasts, so the check must not run
 * concurrently with other checks (live wrapper: EXCLUSIVE=1). The check SKIPs where the test login acknowledges without the
 * comment prompt (its lab_ack_comment preference). Cleanup
 * removes the documents' routing, queue and lock rows, the documents and files, and asserts them gone.
 */
const fs = require('node:fs');
const crypto = require('node:crypto');
const h = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const {
  directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

const NAME = 'document-viewer-review-actions';
const TIMEOUT = 60000;
const q = h.sqlString;

/**
 * Owned documents for the viewer checks, one per key, each routed New to the test provider and to another active provider and
 * linked to the first document queue; cleanup is registered before the first write. Returns the documents by key and readers
 * of their rows, and openViewer(doc), which opens a document's viewer from the E-Chart's Documents list.
 */
function viewerDocuments(s, keys) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const other = sql.value(`SELECT MIN(provider_no) FROM provider WHERE status='1' AND provider_no REGEXP '^[1-9][0-9]*$'
    AND provider_no<>${q(provider)}`);
  if (!/^[1-9]\d*$/.test(other)) throw new h.SkipCheck('The database has no other active provider to hold the documents too');
  const queue = sql.value('SELECT MIN(id) FROM queue');
  if (!/^[1-9]\d*$/.test(queue)) throw new h.SkipCheck('The install has no document queue to link the owned documents to');
  const docs = ownedPdfDocuments(store, marker, keys.map((key) => ({ key })));

  const routing = (doc, who = provider) => sql.value(`SELECT IFNULL(GROUP_CONCAT(CONCAT(status,'|',IFNULL(comment,''))
    ORDER BY id SEPARATOR ';'),'') FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${doc.id} AND provider_no=${q(who)}`);
  const otherRow = (doc) => JSON.stringify(sql.rows(`SELECT id, status, IFNULL(comment,'<null>'), IFNULL(timestamp,'<null>')
    FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${doc.id} AND provider_no=${q(other)} ORDER BY id`));
  const queueOf = (doc) => sql.value(`SELECT IFNULL(GROUP_CONCAT(status ORDER BY id),'') FROM queue_document_link
    WHERE document_id=${doc.id}`);
  const routingQuery = (doc) => `SELECT CONCAT(status,'|',IFNULL(comment,'')) FROM providerLabRouting WHERE lab_type='DOC'
    AND lab_no=${doc.id} AND provider_no=${q(provider)}`;

  // providerLabRoutingLock is keyed by the number alone, which the demo dataset's labs also use; cleanup removes a lock only
  // when none existed for that number before the run routed the owned document.
  const lockedBefore = new Set();
  s.cleanup(() => {
    const ids = docs.map((doc) => doc.id).filter(Boolean);
    if (ids.length) {
      const list = ids.join(',');
      h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no IN (${list}) AND docdesc LIKE ${q(`${marker}%`)}`)
        === String(ids.length), 'Document fixture ownership changed; refusing to delete rows keyed by document number');
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${list});
        DELETE FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}) AND demographic_no=${patient};
        DELETE FROM queue_document_link WHERE document_id IN (${list})`);
      const created = ids.filter((id) => !lockedBefore.has(String(id)));
      if (created.length) sql.execute(`DELETE FROM providerLabRoutingLock WHERE lab_no IN (${created.join(',')})`);
    }
    removeOwnedPdfDocuments({ sql, marker, patient, files: docs.map((doc) => doc.file), docs });
    assertOwnedPdfDocumentsRemoved({ sql, marker, patient, files: docs.map((doc) => doc.file), docs });
    if (ids.length) {
      const list = ids.join(',');
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}))
        + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}))
        + (SELECT COUNT(*) FROM queue_document_link WHERE document_id IN (${list}))
        + (SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no IN (${ids.filter((id) => !lockedBefore.has(String(id))).join(',') || 0}))`)
        === '0', 'The owned documents\' routing, queue or lock rows were not removed');
    }
  });

  for (const doc of docs) {
    // Demo data carries routing rows for long-deleted document numbers; take the next number rather than share one.
    for (let attempt = 1; ; attempt += 1) {
      seedOwnedPdfDocuments({ sql, store, patient, provider, docs: [doc] });
      const held = sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${doc.id})
        + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${doc.id})
        + (SELECT COUNT(*) FROM queue_document_link WHERE document_id=${doc.id})
        + (SELECT COUNT(*) FROM ctl_document WHERE document_no=${doc.id} AND NOT (module='demographic' AND module_id=${patient}))`);
      if (held === '0') {
        if (sql.value(`SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no=${doc.id}`) !== '0') lockedBefore.add(String(doc.id));
        break;
      }
      h.assert(attempt < 6, 'Six consecutive new document numbers collide with orphaned routing rows');
      sql.execute(`DELETE FROM ctl_document WHERE document_no=${doc.id} AND module='demographic' AND module_id=${patient};
        DELETE FROM document WHERE document_no=${doc.id} AND docdesc=${q(doc.label)}`);
      fs.unlinkSync(doc.file);
      delete doc.id;
    }
    sql.execute(`INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created) VALUES (${patient},${doc.id},'DOC',NOW());
      INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${q(provider)},${doc.id},'DOC','N');
      INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${q(other)},${doc.id},'DOC','N');
      INSERT INTO queue_document_link (queue_id,document_id,status) VALUES (${queue},${doc.id},'A')`);
    h.assert(routing(doc) === 'N|' && routing(doc, other) === 'N|' && queueOf(doc) === 'A',
      `The ${doc.key} document's routing or queue fixture is not New and active`);
  }

  /** The document's entry in the E-Chart's Documents list opens its viewer, the way a clinician opens it. */
  async function openViewer(doc) {
    const chart = await s.chart();
    const link = chart.locator(`#leftNavBar a.links[onclick*="segmentID=${doc.id}'"], #rightNavBar a.links[onclick*="segmentID=${doc.id}'"]`).first();
    await link.waitFor({ state: 'attached', timeout: TIMEOUT });
    // A long title sits under the right-floated date suffix; click its visible left edge.
    const viewer = await clickOpensPopup(chart, link, { context: s.context, recorder: s.recorder,
      label: `document-viewer-${doc.key}`, timeout: TIMEOUT, position: { x: 8, y: 9 } });
    await viewer.locator(`[id="ackBtn_${doc.id}"]`).waitFor({ state: 'visible', timeout: TIMEOUT });
    return viewer;
  }

  return { docs: Object.fromEntries(docs.map((doc) => [doc.key, doc])), other, routing, otherRow, queueOf, routingQuery, openViewer };
}

async function workflow(s) {
  const { sql, provider } = s;
  if (String(sql.value(`SELECT IFNULL(MAX(value),'') FROM property WHERE provider_no=${q(provider)}
      AND name='lab_ack_comment'`)).toLowerCase() === 'yes') {
    throw new h.SkipCheck('The test login acknowledges without the comment prompt (its lab_ack_comment preference is "yes")');
  }
  const { docs, routing, otherRow, queueOf, routingQuery, openViewer } = viewerDocuments(s, ['comment', 'ack', 'plain']);
  const stamp = crypto.randomBytes(3).toString('hex');

  const isUpdateStatus = (response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname.endsWith('/oscarMDS/UpdateStatus');
  const isQueueCompletion = (response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname.endsWith('/documentManager/inboxManage')
    && new URLSearchParams(response.request().postData() || '').get('method') === 'updateDocStatusInQueue';

  /**
   * Acknowledge > OK with `text`: the acknowledgement and its queue completion both answer, and the viewer closes.
   * Returns the text the comment prompt offered (a second listener only reads it; the harness one answers).
   */
  async function acknowledge(viewer, doc, text) {
    const closed = viewer.waitForEvent('close', { timeout: TIMEOUT });
    closed.catch(() => {});
    const offered = [];
    viewer.on('dialog', (dialog) => offered.push(dialog.defaultValue()));
    let ack;
    let completion;
    const dialogs = await h.withExpectedDialogs(viewer, async () => {
      [ack, completion] = await Promise.all([
        viewer.waitForResponse(isUpdateStatus, { timeout: TIMEOUT }),
        viewer.waitForResponse(isQueueCompletion, { timeout: TIMEOUT }),
        viewer.locator(`[id="ackBtn_${doc.id}"]`).click(),
      ]);
    }, { promptText: text });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Acknowledge did not ask for a comment first');
    const sent = new URLSearchParams(ack.request().postData() || '');
    h.assert(ack.status() === 200 && sent.get('segmentID') === doc.id && sent.get('labType') === 'DOC' && sent.get('status') === 'A',
      `Acknowledge did not post an acknowledgement of exactly this document (HTTP ${ack.status()})`);
    h.assert(completion.status() === 200, `The queue completion after Acknowledge answered HTTP ${completion.status()}`);
    await closed;
    return offered[0];
  }

  let typed;
  await s.step('Comment records the typed comment on the test provider\'s row and leaves the document New', async () => {
    const doc = docs.comment;
    const before = { other: otherRow(doc), queue: queueOf(doc) };
    const viewer = await openViewer(doc);
    typed = `FAKE review comment ${stamp}`;
    let response;
    const dialogs = await h.withExpectedDialogs(viewer, async () => {
      [response] = await Promise.all([
        viewer.waitForResponse(isUpdateStatus, { timeout: TIMEOUT }),
        viewer.locator(`#acknowledgeForm_${doc.id} input[type="button"][onclick^="addDocComment("]`).click(),
      ]);
    }, { promptText: typed });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Comment did not open the comment prompt');
    const sent = new URLSearchParams(response.request().postData() || '');
    h.assert(response.status() === 200 && sent.get('method') === 'addComment' && sent.get('segmentID') === doc.id
      && sent.get('labType') === 'DOC' && sent.get('comment') === typed,
    `Comment did not post addComment with the typed comment for exactly this document (HTTP ${response.status()})`);
    await expectValue(sql, routingQuery(doc), `N|${typed}`,
      'Comment did not store the typed comment on the test provider\'s row, or did not leave it New');
    h.assert(!viewer.isClosed(), 'The viewer closed after Comment');
    const shown = (await viewer.locator(`[id="comment_${doc.id}_${provider}"]`).innerText()).trim();
    h.assert(shown === typed, `The viewer shows "${shown}" as the test provider's comment, not the typed one`);
    h.assert(otherRow(doc) === before.other, 'Comment changed the other provider\'s routing row');
    h.assert(queueOf(doc) === before.queue, 'Comment changed the document\'s queue link');
    // Every document opens in the same named popup window, so close this one before the next opens.
    await viewer.close();
  });

  await s.step('a refused Comment shows a failure, preserves the saved comment and can be retried', async () => {
    const doc = docs.comment;
    const before = { mine: routing(doc), other: otherRow(doc), queue: queueOf(doc) };
    const viewer = await openViewer(doc);
    const commentButton = viewer.locator(`#acknowledgeForm_${doc.id} input[type="button"][onclick^="addDocComment("]`);
    const status = await viewer.locator(`[id="status_${doc.id}"]`).inputValue();
    const match = url => url.pathname.endsWith('/oscarMDS/UpdateStatus');
    const refuse = route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"date":"not saved"}' });
    await viewer.route(match, refuse);
    const mark = failureMark(s.recorder);
    try {
      const dialogs = await h.withExpectedDialogs(viewer, async () => {
        await Promise.all([
          viewer.waitForEvent('dialog', { predicate: dialog => dialog.type() === 'alert', timeout: TIMEOUT }),
          commentButton.click(),
        ]);
      }, { promptText: `${typed} unsaved` });
      h.assert(dialogs.length === 2 && dialogs[0].type === 'prompt' && dialogs[1].type === 'alert'
        && /comment could not be saved/i.test(dialogs[1].text), 'A refused Comment did not tell the reviewer it failed');
      h.assert(await viewer.locator(`[id="comment_${doc.id}"]`).inputValue() === typed
        && await viewer.locator(`[id="comment_${doc.id}_${provider}"]`).innerText() === typed
        && await viewer.locator(`[id="status_${doc.id}"]`).inputValue() === status,
      'The refused Comment replaced saved form state with an unsaved value');
      h.assert(routing(doc) === before.mine && otherRow(doc) === before.other && queueOf(doc) === before.queue,
        'A refused Comment changed document routing or its queue link');
      consumeExpectedFailure(s.recorder, mark, { status: 503, path: /\/oscarMDS\/UpdateStatus$/ });
    } finally {
      await viewer.unroute(match, refuse);
    }
    const offered = [];
    const retried = `${typed} retried`;
    viewer.on('dialog', dialog => offered.push(dialog.defaultValue()));
    await h.withExpectedDialogs(viewer, async () => {
      const [response] = await Promise.all([
        viewer.waitForResponse(isUpdateStatus, { timeout: TIMEOUT }),
        commentButton.click(),
      ]);
      h.assert(response.status() === 200 && new URLSearchParams(response.request().postData() || '').get('comment') === retried,
        'Retry did not save the comment successfully');
      await viewer.waitForFunction(({ id, text }) => document.getElementById(id).value === text,
        { id: `comment_${doc.id}`, text: retried });
    }, { promptText: retried });
    h.assert(offered.length === 1 && offered[0] === typed, 'Retry offered the unsaved comment as though it had been saved');
    await expectValue(sql, routingQuery(doc), `N|${retried}`, 'Retry did not store the new comment');
    typed = retried;
    await viewer.close();
  });

  await s.step('Acknowledge on the commented document offers its comment and keeps it, closes the queue link and the viewer', async () => {
    const doc = docs.comment;
    const before = otherRow(doc);
    const viewer = await openViewer(doc);
    const shown = (await viewer.locator(`[id="comment_${doc.id}_${provider}"]`).innerText()).trim();
    h.assert(shown === typed, `The reopened viewer shows "${shown}" as the test provider's comment, not the saved one`);
    const offered = await acknowledge(viewer, doc, typed);
    h.assert(offered === typed, `The comment prompt offered "${offered}", not the saved comment`);
    await expectValue(sql, routingQuery(doc), `A|${typed}`,
      `Acknowledge after Comment left the test provider's row as "${routing(doc)}", not acknowledged with the comment`);
    h.assert(queueOf(doc) === 'I', `The document's queue link reads "${queueOf(doc)}" after Acknowledge, not closed (I)`);
    h.assert(otherRow(doc) === before, 'Acknowledge changed the other provider\'s routing row');
  });

  await s.step('Acknowledge > Cancel posts nothing; Acknowledge with a typed comment acknowledges for this provider only', async () => {
    const doc = docs.ack;
    const before = { mine: routing(doc), other: otherRow(doc), queue: queueOf(doc) };
    const viewer = await openViewer(doc);
    const posted = [];
    const watch = (request) => { if (request.method() === 'POST') posted.push(new URL(request.url()).pathname); };
    viewer.on('request', watch);
    const dialogs = await h.withExpectedDialogs(viewer, async () => {
      await viewer.locator(`[id="ackBtn_${doc.id}"]`).click();
      await viewer.waitForTimeout(1500);
    }, { accept: false });
    viewer.off('request', watch);
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Acknowledge did not ask for a comment first');
    h.assert(posted.length === 0, `Cancelling the comment prompt still posted ${posted.join(', ')}`);
    h.assert(routing(doc) === before.mine && queueOf(doc) === before.queue && !viewer.isClosed(),
      'Cancelling the comment prompt changed the routing row or the queue link, or closed the viewer');

    const text = `FAKE acknowledgement ${stamp}`;
    await acknowledge(viewer, doc, text);
    await expectValue(sql, routingQuery(doc), `A|${text}`, 'Acknowledge did not set the test provider\'s row to A with the typed comment');
    h.assert(queueOf(doc) === 'I', `The document's queue link reads "${queueOf(doc)}" after Acknowledge, not closed (I)`);
    h.assert(otherRow(doc) === before.other, 'Acknowledge changed the other provider\'s routing row');
  });

  await s.step('Acknowledge on an uncommented document offers no comment, and OK as offered stores none', async () => {
    const doc = docs.plain;
    const viewer = await openViewer(doc);
    // What the prompt offers is read on a dismissed prompt first, so OK can then be given exactly that text.
    const offered = [];
    const peek = (dialog) => offered.push(dialog.defaultValue());
    viewer.on('dialog', peek);
    await h.withExpectedDialogs(viewer, async () => {
      await viewer.locator(`[id="ackBtn_${doc.id}"]`).click();
      await viewer.waitForTimeout(1500);
    }, { accept: false });
    viewer.off('dialog', peek);
    const shown = (await viewer.locator(`[id="comment_${doc.id}_${provider}"]`).innerText()).trim();
    h.assert(offered.length === 1, 'Acknowledge did not ask for a comment first');
    await acknowledge(viewer, doc, offered[0]);
    await expectValue(sql, `SELECT status FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${doc.id}
      AND provider_no=${q(provider)}`, 'A', 'OK as offered did not acknowledge the document');
    h.assert(offered[0] === '' && routing(doc) === 'A|',
      `The comment prompt of an uncommented document offered "${offered[0]}" (the viewer shows "${shown}" for no comment), `
      + `and OK as offered stored the comment "${routing(doc).slice(2)}"`);
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow, viewerDocuments };
