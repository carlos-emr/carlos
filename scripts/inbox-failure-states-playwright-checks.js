#!/usr/bin/env node
/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
// Deliberate schema fault: exclusive disposable installations only. Restores the
// table before cleaning owned clinical fixtures, including after a failed assertion.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved } = require('./lib/stored-pdf-documents');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR');
  const docs = ownedPdfDocuments(store, marker, [{ key: 'later-one' }, { key: 'later-two' }]);
  const owned = () => ({ sql, marker, patient, provider, docs, files: docs.map(doc => doc.file) });
  s.cleanup(() => { removeOwnedPdfDocuments(owned()); assertOwnedPdfDocumentsRemoved(owned()); });
  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  for (const doc of docs) sql.execute(`INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status,timestamp)
    VALUES (${h.sqlString(provider)},${doc.id},'DOC','N',NOW())`);
  const bad = -Number(docs[0].id);
  h.assert(Number.isSafeInteger(bad) && bad < 0 && sql.value(`SELECT (SELECT COUNT(*) FROM document WHERE document_no=${bad})
    +(SELECT COUNT(*) FROM ctl_document WHERE document_no=${bad})
    +(SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${bad})
    +(SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${bad})`) === '0',
    'No unused invalid document identity for this fixture');
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${bad} AND docdesc=${h.sqlString(`${marker} invalid`)}`) === '1',
      'Invalid document fixture ownership changed');
    sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${bad} AND lab_type='DOC' AND provider_no=${h.sqlString(provider)};
      DELETE FROM ctl_document WHERE document_no=${bad} AND module='demographic' AND module_id=${patient};
      DELETE FROM document WHERE document_no=${bad} AND docdesc=${h.sqlString(`${marker} invalid`)}`);
  });
  sql.execute(`INSERT INTO document (document_no,doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,
    contentdatetime,observationdate,public1,number_of_pages,restrictToProgram)
    VALUES (${bad},'others',${h.sqlString(`${marker} invalid`)},'unavailable.pdf',${h.sqlString(provider)},${h.sqlString(provider)},
      'A','application/pdf','2026-12-31 12:00:00','2026-12-31',0,1,0);
    INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${bad},'A');
    INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status,timestamp) VALUES (${h.sqlString(provider)},${bad},'DOC','N',NOW())`);
  const route = `/web/inboxhub/Inboxhub?method=displayInboxForm&query.patientLastName=${encodeURIComponent(marker)}&query.viewMode=false&query.searchAll=true`;
  const inbox = await s.context.newPage();
  await s.step('a bad document row has a visible completeness warning and later valid documents still render', async () => {
    await h.gotoApp(inbox, s.config.baseUrl, route);
    await inbox.locator('#inboxhubIncompleteNotice').waitFor({ state: 'visible' });
    for (const doc of docs) await inbox.locator(`#labdoc_DOC_${doc.id}`).waitFor({ state: 'attached' });
    h.assert(await inbox.locator(`#labdoc_DOC_${bad}`).count() === 0, 'An invalid identity became a clinical row');
    h.assert(!(await inbox.locator('#inboxhubIncompleteNotice').innerText()).includes(String(bad)), 'The notice exposes an invalid source identity');
  });
  const backup = `carlos_test_hrm_fault_${marker.replace('FAKE-PW', '')}`;
  h.assert(/^carlos_test_hrm_fault_[a-f0-9]+$/.test(backup), 'Invalid owned fault table name');
  const exists = name => sql.value(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=${h.sqlString(name)}`) === '1';
  const restore = () => {
    if (exists(backup)) {
      h.assert(!exists('HRMDocument'), 'Both HRM table names exist; retain the fixture for reconciliation');
      sql.execute(`RENAME TABLE ${backup} TO HRMDocument`);
    }
  };
  s.cleanup(restore);
  await s.step('an HRM count query failure returns HTTP 500 without publishing partial counts, then recovers after restoration', async () => {
    sql.execute(`RENAME TABLE HRMDocument TO ${backup}`);
    try {
      const failed = await s.context.request.get(h.appUrl(s.config.baseUrl, route));
      h.assert(failed.status() === 500, `A count failure returned HTTP ${failed.status()}`);
      h.assert(!(await failed.text()).includes('id="totalResultsCount"'), 'A failed count request published totals'); await failed.dispose();
    } finally { restore(); }
    const recovered = await s.context.request.get(h.appUrl(s.config.baseUrl, route));
    h.assert(recovered.status() === 200 && (await recovered.text()).includes('id="totalResultsCount"'),
      'The count page did not recover after table restoration'); await recovered.dispose();
  });
}

if (require.main === module) runWorkflow('inbox-failure-states', workflow, {
  openMaster: false,
  preflightConfig() {
    if (process.env.EXCLUSIVE !== '1' || process.env.CARLOS_DISPOSABLE_VM !== 'true') {
      throw new h.SkipCheck('Schema fault injection requires EXCLUSIVE=1 and CARLOS_DISPOSABLE_VM=true');
    }
  },
});
module.exports = { workflow };
