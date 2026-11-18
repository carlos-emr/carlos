/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./playwright-harness');

const positive = value => typeof value === 'string' && /^[1-9]\d*$/.test(value);
const safeFilename = value => typeof value === 'string' && /^[A-Za-z0-9_-][A-Za-z0-9._-]*$/.test(value)
  && value !== '.' && value !== '..';

function preflightEmailArchives({env = process.env} = {}) {
  if (env.CARLOS_DISPOSABLE_VM !== 'true') throw new h.SkipCheck('Email archive fixtures require CARLOS_DISPOSABLE_VM=true');
  if (!env.DOCUMENT_DIR) throw new h.SkipCheck('Email archive fixtures require DOCUMENT_DIR before creating patients or sending');
  try {
    const store = fs.realpathSync(env.DOCUMENT_DIR);
    assert(fs.statSync(store).isDirectory());
    fs.accessSync(store, fs.constants.R_OK | fs.constants.W_OK | fs.constants.X_OK);
    return store;
  } catch (_) { throw new h.SkipCheck('DOCUMENT_DIR must be an accessible, writable document store'); }
}

function privatePath(file, directory = false) {
  const stat = fs.lstatSync(file);
  assert((directory ? stat.isDirectory() : stat.isFile()) && stat.uid === process.getuid()
    && (stat.mode & 0o077) === 0, 'Email cleanup receipt must be private, owned and not symbolic');
}

function fileIdentity(file) {
  const stat = fs.lstatSync(file, {bigint: true});
  assert(stat.isFile(), 'Email archive file is missing, symbolic or not regular');
  return {dev: String(stat.dev), ino: String(stat.ino), size: String(stat.size), modified: String(stat.mtimeNs)};
}

function syncDirectory(directory) {
  const descriptor = fs.openSync(directory, 'r');
  try { fs.fsyncSync(descriptor); } finally { fs.closeSync(descriptor); }
}

// Take routing marks before sending: the demo database can contain dangling DOC routes.
// Removing an archive must preserve those older rows even if an auto-increment id was reused.
function createEmailArchiveCleanup(sql, documentDir = process.env.DOCUMENT_DIR, {unlink = file => fs.unlinkSync(file)} = {}) {
  const store = preflightEmailArchives({env: {...process.env, DOCUMENT_DIR: documentDir}});
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
    assert.equal(process.env.CARLOS_DISPOSABLE_VM, 'true',
      'Removing synthetic email archives requires CARLOS_DISPOSABLE_VM=true');
    const directory = path.join(store, `.playwright-email-cleanup-${marker}-${patient}`);
    const receiptFile = path.join(directory, 'receipt.json');
    let receipt;
    if (fs.existsSync(directory)) {
      privatePath(directory, true); privatePath(receiptFile);
      assert(fs.statSync(receiptFile).size < 1024 * 1024, 'Email cleanup receipt is oversized');
      receipt = JSON.parse(fs.readFileSync(receiptFile, 'utf8'));
      assert(receipt.version === 1 && receipt.patient === patient && receipt.marker === marker,
        'Email cleanup receipt belongs to another fixture');
      assert.deepEqual(receipt.logIds, [...new Set(logIds)].sort(), 'Email cleanup receipt log scope changed');
      for (const key of ['archives', 'documents']) assert(Array.isArray(receipt[key])
        && receipt[key].length > 0 && receipt[key].every(positive)
        && new Set(receipt[key]).size === receipt[key].length, 'Invalid email cleanup receipt ids');
      assert.equal(receipt.archives.length, receipt.documents.length, 'Invalid email cleanup receipt document scope');
      assert(Array.isArray(receipt.files) && receipt.files.length === receipt.documents.length
        && new Set(receipt.files.map(file => file.filename)).size === receipt.files.length,
      'Invalid email cleanup receipt files');
      for (const file of receipt.files) assert(safeFilename(file.filename)
        && ['dev', 'ino', 'size', 'modified'].every(key => typeof file[key] === 'string' && /^\d+$/.test(file[key])),
      'Unsafe email cleanup receipt file identity');
      for (const table of Object.keys(floors)) assert(typeof receipt.floors?.[table] === 'string'
        && /^\d+$/.test(receipt.floors[table]), 'Invalid email cleanup receipt routing mark');
    }
    function remaining(plan) {
      const archives = plan.archives.join(','), documents = plan.documents.join(',');
      return sql.value(`SELECT (SELECT COUNT(*) FROM outboundEmailArchive WHERE id IN (${archives}))
        + (SELECT COUNT(*) FROM outboundEmailArchiveAttachment WHERE archiveId IN (${archives}))
        + (SELECT COUNT(*) FROM outboundEmailArchiveDeletion WHERE archiveId IN (${archives}))
        + (SELECT COUNT(*) FROM outboundEmailArchiveLegalHoldEvent WHERE archiveId IN (${archives}))
        + (SELECT COUNT(*) FROM document WHERE document_no IN (${documents}))
        + (SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${documents}) AND module='demographic' AND module_id=${patient})
        + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${documents}) AND demographic_no=${patient} AND id>${plan.floors.patientLabRouting})
        + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${documents}) AND id>${plan.floors.providerLabRouting})`);
    }
    function finish(plan) {
      const filenames = plan.files.map(file => h.sqlString(file.filename)).join(',');
      assert.equal(sql.value(`SELECT COUNT(*) FROM document WHERE docfilename IN (${filenames})`), '0',
        'A document now uses a pending cleanup file');
      // Verify every remaining file before unlinking any. Missing files are an acknowledged
      // earlier partial unlink; a replacement or symlink is never removed using an old receipt.
      for (const file of plan.files) {
        const target = path.join(store, file.filename);
        try { assert.deepEqual({filename: file.filename, ...fileIdentity(target)}, file, 'Email cleanup file identity changed'); }
        catch (error) { if (error.code !== 'ENOENT') throw error; }
      }
      for (const file of plan.files) {
        const target = path.join(store, file.filename);
        try { unlink(target); } catch (error) { if (error.code !== 'ENOENT') throw error; }
      }
      syncDirectory(store);
      fs.unlinkSync(receiptFile); syncDirectory(directory);
      fs.rmdirSync(directory); syncDirectory(store);
    }
    if (receipt && remaining(receipt) === '0') {
      try { finish(receipt); } catch (error) { throw new Error(`Email archive file cleanup is pending; preserve ${receiptFile}`, {cause: error}); }
      return;
    }
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
      AND d.source='Outbound Email Archive' AND d.docdesc=CONCAT('Outbound email archive ',l.id) ORDER BY a.id`);
    assert.equal(sql.value(`SELECT COUNT(*) FROM outboundEmailArchive WHERE emailLogId IN (${logs})`),
      String(rows.length), 'An email archive has unfamiliar ownership or document metadata');
    if (!rows.length) return;
    const archives = [], documents = [], files = [];
    for (const [archive, log, document, filename] of rows) {
      assert(positive(archive) && logIds.includes(log) && positive(document), 'Invalid email archive identity');
      assert(safeFilename(filename), 'Unsafe email archive filename');
      assert.equal(sql.value(`SELECT COUNT(*) FROM document WHERE docfilename=${h.sqlString(filename)}`),
        '1', 'Email archive file is shared with another document');
      const file = path.join(store, filename);
      archives.push(archive); documents.push(document); files.push({filename, ...fileIdentity(file)});
    }
    assert.equal(new Set(documents).size, rows.length, 'Email archives share a document');
    assert.equal(new Set(files.map(file => file.filename)).size, rows.length, 'Email archives share a file');
    const archiveIds = archives.join(','), documentIds = documents.join(',');
    assert.equal(sql.value(`SELECT COUNT(*) FROM outboundEmailArchive WHERE documentNo IN (${documentIds})
      AND id NOT IN (${archiveIds})`), '0', 'Another archive uses an owned document');
    for (const table of ['outboundEmailArchiveAttachment', 'outboundEmailArchiveDeletion']) {
      assert.equal(sql.value(`SELECT COUNT(*) FROM ${table} WHERE documentNo IN (${documentIds})
        AND archiveId NOT IN (${archiveIds})`), '0', 'Another archive child uses an owned document');
    }
    assert.equal(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${documentIds})
      AND NOT (module='demographic' AND module_id=${patient})`), '0', 'Another patient uses an owned archive document');
    const plan = {version: 1, patient, marker, logIds: [...new Set(logIds)].sort(), archives, documents, files,
      floors: receipt ? receipt.floors : floors};
    if (receipt) assert.deepEqual(plan, receipt, 'Pending email cleanup no longer matches the owned rows and files');
    else {
      fs.mkdirSync(directory, {mode: 0o700});
      const descriptor = fs.openSync(receiptFile, 'wx', 0o600);
      try { fs.writeFileSync(descriptor, JSON.stringify(plan)); fs.fsyncSync(descriptor); }
      finally { fs.closeSync(descriptor); }
      syncDirectory(directory); syncDirectory(store);
    }
    // Persist the private file receipt before committing any metadata removal. A failed SQL
    // transaction preserves the files; a failed unlink is recoverable from a new helper instance.
    sql.execute(`START TRANSACTION;
      DELETE FROM outboundEmailArchiveLegalHoldEvent WHERE archiveId IN (${archiveIds});
      DELETE FROM outboundEmailArchiveDeletion WHERE archiveId IN (${archiveIds});
      DELETE FROM outboundEmailArchiveAttachment WHERE archiveId IN (${archiveIds});
      DELETE FROM outboundEmailArchive WHERE id IN (${archiveIds}) AND demographicNo=${patient};
      DELETE FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${documentIds})
        AND demographic_no=${patient} AND id>${plan.floors.patientLabRouting};
      DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${documentIds}) AND id>${plan.floors.providerLabRouting};
      DELETE FROM ctl_document WHERE document_no IN (${documentIds}) AND module='demographic' AND module_id=${patient};
      DELETE FROM document WHERE document_no IN (${documentIds}) AND docClass='EMAIL' AND docSubClass='OUTBOUND';
      COMMIT`);
    assert.equal(remaining(plan), '0', 'Owned email archive rows or child rows remain');
    try { finish(plan); } catch (error) { throw new Error(`Email archive file cleanup is pending; preserve ${receiptFile}`, {cause: error}); }
  };
}

module.exports = {createEmailArchiveCleanup, preflightEmailArchives};
