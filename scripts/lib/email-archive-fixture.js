/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./playwright-harness');

const positive = value => typeof value === 'string' && /^[1-9]\d*$/.test(value);

// Take routing marks before sending: the demo database can contain dangling DOC routes.
// Removing an archive must preserve those older rows even if an auto-increment id was reused.
function createEmailArchiveCleanup(sql, documentDir = process.env.DOCUMENT_DIR) {
  const floors = {};
  for (const table of ['providerLabRouting', 'patientLabRouting']) {
    const floor = sql.value(`SELECT COALESCE(MAX(id),0) FROM ${table}`);
    assert.match(floor, /^\d+$/, 'Email archive routing mark is invalid');
    floors[table] = floor;
  }
  return function removeArchives({patient, marker, logIds}) {
    assert(positive(patient) && /^FAKE-PW[0-9a-f]{16}Y?$/.test(marker), 'Invalid email fixture identity');
    assert.equal(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${h.sqlString(marker)}`), '1', 'Email fixture ownership changed');
    assert(Array.isArray(logIds) && logIds.every(positive), 'Invalid owned email log ids');
    if (!logIds.length) return;
    const logs = [...new Set(logIds)].join(',');
    assert.equal(sql.value(`SELECT COUNT(*) FROM emailLog WHERE id IN (${logs}) AND demographicNo=${patient}`),
      String(new Set(logIds).size), 'Email logs no longer belong to the fixture');
    const rows = sql.rows(`SELECT a.id,a.emailLogId,a.documentNo,d.docfilename
      FROM outboundEmailArchive a JOIN emailLog l ON l.id=a.emailLogId
      JOIN document d ON d.document_no=a.documentNo
      JOIN ctl_document c ON c.document_no=d.document_no AND c.module='demographic' AND c.module_id=${patient}
      WHERE a.emailLogId IN (${logs}) AND a.demographicNo=${patient} AND l.demographicNo=${patient}
      AND d.doctype='email' AND d.docClass='EMAIL' AND d.docSubClass='OUTBOUND'
      AND d.source='Outbound Email Archive' AND d.docdesc=CONCAT('Outbound email archive ',l.id)`);
    assert.equal(sql.value(`SELECT COUNT(*) FROM outboundEmailArchive WHERE emailLogId IN (${logs})`),
      String(rows.length), 'An email archive has unfamiliar ownership or document metadata');
    if (!rows.length) return;
    assert(documentDir, 'Owned email archive cleanup requires DOCUMENT_DIR');
    const store = fs.realpathSync(documentDir);
    assert(fs.statSync(store).isDirectory(), 'Email document store is not a directory');
    const archives = [], documents = [], files = [];
    for (const [archive, log, document, filename] of rows) {
      assert(positive(archive) && logIds.includes(log) && positive(document), 'Invalid email archive identity');
      assert(typeof filename === 'string' && /^[A-Za-z0-9_-][A-Za-z0-9._-]*$/.test(filename)
        && filename !== '.' && filename !== '..', 'Unsafe email archive filename');
      assert.equal(sql.value(`SELECT COUNT(*) FROM document WHERE docfilename=${h.sqlString(filename)}`),
        '1', 'Email archive file is shared with another document');
      const file = path.join(store, filename);
      assert(fs.lstatSync(file).isFile(), 'Email archive file is missing, symbolic or not regular');
      archives.push(archive); documents.push(document); files.push(file);
    }
    assert.equal(new Set(documents).size, rows.length, 'Email archives share a document');
    assert.equal(new Set(files).size, rows.length, 'Email archives share a file');
    const archiveIds = archives.join(','), documentIds = documents.join(',');
    assert.equal(sql.value(`SELECT COUNT(*) FROM outboundEmailArchive WHERE documentNo IN (${documentIds})
      AND id NOT IN (${archiveIds})`), '0', 'Another archive uses an owned document');
    assert.equal(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${documentIds})
      AND NOT (module='demographic' AND module_id=${patient})`), '0', 'Another patient uses an owned archive document');
    // All rows and files are validated before the first mutation. Leave files intact if SQL fails.
    sql.execute(`START TRANSACTION;
      DELETE FROM outboundEmailArchiveLegalHoldEvent WHERE archiveId IN (${archiveIds});
      DELETE FROM outboundEmailArchiveDeletion WHERE archiveId IN (${archiveIds});
      DELETE FROM outboundEmailArchiveAttachment WHERE archiveId IN (${archiveIds});
      DELETE FROM outboundEmailArchive WHERE id IN (${archiveIds}) AND demographicNo=${patient};
      DELETE FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${documentIds})
        AND demographic_no=${patient} AND id>${floors.patientLabRouting};
      DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${documentIds}) AND id>${floors.providerLabRouting};
      DELETE FROM ctl_document WHERE document_no IN (${documentIds}) AND module='demographic' AND module_id=${patient};
      DELETE FROM document WHERE document_no IN (${documentIds}) AND docClass='EMAIL' AND docSubClass='OUTBOUND';
      COMMIT`);
    assert.equal(sql.value(`SELECT (SELECT COUNT(*) FROM outboundEmailArchive WHERE emailLogId IN (${logs}))
      + (SELECT COUNT(*) FROM document WHERE document_no IN (${documentIds}))`), '0', 'Owned email archive rows remain');
    for (const file of files) fs.unlinkSync(file);
    assert(files.every(file => !fs.existsSync(file)), 'Owned email archive files remain');
  };
}

module.exports = {createEmailArchiveCleanup};
