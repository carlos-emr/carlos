#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, the document writes behind the chart's Note Browser and the Document
 * Browser. User paths (driven with POST by note-browser-documents and document-refile-combine):
 *   E-Chart ▸ Browse Notes (casemgmt/ViewNoteBrowser) ▸ a document ▸ Delete / Undelete / Refile
 *     -> casemgmt/NoteBrowserDocumentDelete, NoteBrowserDocumentUndelete, NoteBrowserDocumentRefile
 *   Master Record ▸ Documents (documentManager/ViewDocumentBrowser) ▸ Delete / Undelete
 *     -> documentManager/DocumentDelete, documentManager/DocumentUndelete
 *
 * The existing checks send one GET to NoteBrowserDocumentDelete and to DocumentRefile. This
 * sweep sends GET and HEAD to all five routes against owned documents and requires a 405 from
 * CARLOS with nothing changed: no status flip in `document`, and no refiled copy written under
 * INCOMINGDOCUMENT_DIR/1/Refile.
 *
 * Fixtures: owned FAKE- patient and two owned PDFs (rows, ctl_document links, files carrying
 * the marker); one is set to deleted ('D') by SQL so an undelete has something to undo. All
 * removed and verified gone, with any refiled copy. Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR)
 * and INCOMINGDOCUMENT_DIR, plus the harness contract; SKIP when either is not set.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createLedger } = require('./lib/get-reject-probe');
const {
  directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

const NAME = 'get-reject-note-browser-documents';

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const incoming = directory('INCOMINGDOCUMENT_DIR', 'INCOMINGDOCUMENT_DIR');
  const docs = ownedPdfDocuments(store, marker, [{ key: 'A' }, { key: 'B' }]);
  const [published, deleted] = docs;
  // EDocUtil.getRefiledDocumentFileName drops the 14-character upload timestamp and prefixes R.
  const refiled = path.join(incoming, '1', 'Refile', `R${published.filename.substring(14)}`);
  const owned = { sql, marker, patient, docs, files: [...docs.map(doc => doc.file), refiled] };
  s.cleanup(() => {
    removeOwnedPdfDocuments(owned);
    assertOwnedPdfDocumentsRemoved(owned);
  });

  h.assert(!fs.existsSync(refiled), 'The refile destination already exists');
  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  sql.execute(`UPDATE document SET status='D' WHERE document_no=${deleted.id}`);
  const statuses = () => sql.value(`SELECT GROUP_CONCAT(status ORDER BY document_no) FROM document
    WHERE document_no IN (${published.id},${deleted.id})`);
  h.assert(statuses() === 'A,D', 'The owned documents were not seeded as published and deleted');
  const snapshot = () => `${statuses()}|${fs.existsSync(refiled) ? 'refiled' : 'not refiled'}`;
  const ledger = createLedger(NAME);

  await s.step('the note-browser and document-browser writes sent as GET/HEAD are recorded', async () => {
    const probes = [
      { label: 'casemgmt/NoteBrowserDocumentDelete', route: '/casemgmt/NoteBrowserDocumentDelete',
        params: { delDocumentNo: published.id, demographic_no: patient } },
      { label: 'casemgmt/NoteBrowserDocumentUndelete', route: '/casemgmt/NoteBrowserDocumentUndelete',
        params: { undelDocumentNo: deleted.id, demographic_no: patient } },
      { label: 'casemgmt/NoteBrowserDocumentRefile', route: '/casemgmt/NoteBrowserDocumentRefile',
        params: { refileDocumentNo: published.id, queueId: '1', demographic_no: patient } },
      { label: 'documentManager/DocumentDelete', route: '/documentManager/DocumentDelete',
        params: { delDocumentNo: published.id, function: 'demographic', functionid: patient } },
      { label: 'documentManager/DocumentUndelete', route: '/documentManager/DocumentUndelete',
        params: { undelDocumentNo: deleted.id, function: 'demographic', functionid: patient } },
    ];
    for (const { label, route, params } of probes) {
      await ledger.probe(s, { label, path: new URL(h.appUrl(s.config.baseUrl, route)).pathname,
        params: new URLSearchParams(params), snapshot });
    }
  });

  await s.step('note-browser and document-browser writes refuse GET/HEAD and change nothing', async () => {
    ledger.assertAllRefused();
    h.assert(snapshot() === 'A,D|not refiled', `The owned documents changed: ${snapshot()}`);
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
