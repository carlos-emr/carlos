/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const {assert, sqlString} = require('./playwright-harness');

/** Own request markers survive HTTP errors, so failed submissions are recoverable too. */
function createConsultationSubmitFixture(sql, demographic, tempRoot) {
  assert(/^[1-9]\d*$/.test(String(demographic)), 'Invalid consultation fixture demographic');
  const marker = `PW_CONSULT_${crypto.randomUUID().replaceAll('-', '')}`;
  const reasons = new Set();
  const files = new Map();
  let root = null;
  let initiallyAbsent = false;
  let expectedOwner;
  if (tempRoot) {
    assert(path.isAbsolute(tempRoot), 'Application temp root must be absolute');
    root = path.resolve(tempRoot);
    const parent = path.dirname(root);
    assert(fs.realpathSync(parent) === parent && fs.lstatSync(parent).isDirectory(),
      'Application temp parent must be an existing real directory');
    expectedOwner = fs.lstatSync(parent).uid;
    try {
      const entry = fs.lstatSync(root);
      assert(entry.isDirectory() && !entry.isSymbolicLink() && fs.realpathSync(root) === root,
        'Application temp root must be a real directory');
      expectedOwner = entry.uid;
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
      assert(path.basename(root) === 'carlos-temp', 'Only the exact application temp tail may be absent');
      initiallyAbsent = true;
    }
  }
  function verifyTempRoot() {
    const entry = fs.lstatSync(root);
    assert(entry.isDirectory() && !entry.isSymbolicLink() && fs.realpathSync(root) === root,
      'Application temp root changed or became a symbolic link');
    assert(entry.uid === expectedOwner, 'Application temp root owner changed');
    if (initiallyAbsent) assert((entry.mode & 0o7777) === 0o700,
      'Native application temp root must be private0700');
    return entry;
  }
  const snapshot = root && !initiallyAbsent ? new Set(fs.readdirSync(root)) : new Set();
  const requestScope = () => `demographicNo=${demographic} AND reason IN (${[...reasons].map(sqlString).join(',')})`;
  return {
    reason(scenario) {
      assert(/^[a-z-]+$/.test(scenario), 'Invalid consultation scenario');
      const value = `${marker}_${scenario}`;
      reasons.add(value);
      return value;
    },
    requestId(scenario) {
      const rows = sql.rows(`SELECT requestId FROM consultationRequests WHERE demographicNo=${demographic}
        AND reason=${sqlString(`${marker}_${scenario}`)}`);
      assert(rows.length === 1 && /^[1-9]\d*$/.test(rows[0][0]), 'Expected exactly one owned consultation request');
      return rows[0][0];
    },
    capturePdf(bytes) {
      if (!root) return;
      const rootEntry = verifyTempRoot();
      const expected = crypto.createHash('sha256').update(bytes).digest('hex');
      let matched = false;
      for (const entry of fs.readdirSync(root, {withFileTypes: true})) {
        if (!entry.isDirectory() || !/^tempPDF\d+$/.test(entry.name) || snapshot.has(entry.name)) continue;
        const directory = path.join(root, entry.name);
        for (const file of fs.readdirSync(directory, {withFileTypes: true})) {
          const preview = /^combinedPDF_([1-9]\d*)_\d+\.pdf$/.exec(file.name);
          if (!file.isFile() || !preview || preview[1] !== String(demographic)) continue;
          const filename = path.join(directory, file.name);
          const stat = fs.lstatSync(filename);
          assert(stat.isFile() && !stat.isSymbolicLink() && stat.uid === rootEntry.uid,
            'Generated consultation preview file has an unexpected owner or type');
          const actual = crypto.createHash('sha256').update(fs.readFileSync(filename)).digest('hex');
          if (actual === expected) {
            files.set(filename, {hash: actual, inode: stat.ino, uid: stat.uid});
            matched = true;
          }
        }
      }
      assert(matched, 'Generated consultation preview file was not found for owned cleanup');
    },
    cleanup() {
      const failures = [];
      try {
        if (reasons.size) {
          const rows = sql.rows(`SELECT requestId, IFNULL(signature_img,'') FROM consultationRequests WHERE ${requestScope()}`);
          assert(rows.every(([id]) => /^[1-9]\d*$/.test(id)), 'Invalid owned consultation ID');
          if (rows.length) {
            const ids = rows.map(([id]) => id).join(',');
            const signatures = [...new Set(rows.map(([, id]) => id).filter(id => /^[1-9]\d*$/.test(id)))];
            const statements = ['START TRANSACTION'];
            for (const table of ['consultdocs', 'consultationRequestExt', 'consultationRequestExtArchive', 'consultationRequestsArchive']) {
              statements.push(`DELETE FROM ${table} WHERE requestId IN (${ids})`);
            }
            statements.push(`DELETE FROM consultationRequests WHERE requestId IN (${ids}) AND ${requestScope()}`);
            // Only signatures linked by these newly created requests are candidates. Keep a
            // signature if any remaining consultation (including its archive) references it.
            for (const id of signatures) statements.push(`DELETE FROM DigitalSignature WHERE id=${id}
              AND demographicId=${demographic}
              AND NOT EXISTS (SELECT 1 FROM consultationRequests WHERE signature_img='${id}')
              AND NOT EXISTS (SELECT 1 FROM consultationRequestsArchive WHERE signature_img='${id}')`);
            statements.push('COMMIT');
            sql.execute(statements.join(';'));
            assert(Number(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE requestId IN (${ids})`)) === 0,
              'Owned consultation cleanup did not remove every request');
          }
        }
      } catch (error) { failures.push(error); }
      for (const [filename, expected] of files) {
        try {
          verifyTempRoot();
          const stat = fs.lstatSync(filename);
          assert(stat.isFile() && !stat.isSymbolicLink() && stat.uid === expected.uid && stat.ino === expected.inode
            && crypto.createHash('sha256').update(fs.readFileSync(filename)).digest('hex') === expected.hash,
          'Owned preview file changed; refusing to remove it');
          fs.unlinkSync(filename);
          // Never recursively delete a directory: unrelated new files must survive.
          if (fs.readdirSync(path.dirname(filename)).length === 0) fs.rmdirSync(path.dirname(filename));
        } catch (error) { failures.push(error); }
      }
      if (failures.length) throw new AggregateError(failures, 'Consultation fixture cleanup failed');
    },
  };
}
module.exports = {createConsultationSubmitFixture};
