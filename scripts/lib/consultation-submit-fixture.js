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
  const root = tempRoot ? fs.realpathSync(tempRoot) : null;
  const snapshot = root ? new Set(fs.readdirSync(root)) : new Set();
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
      const expected = crypto.createHash('sha256').update(bytes).digest('hex');
      for (const entry of fs.readdirSync(root, {withFileTypes: true})) {
        if (!entry.isDirectory() || !/^tempPDF\d+$/.test(entry.name) || snapshot.has(entry.name)) continue;
        const directory = path.join(root, entry.name);
        for (const file of fs.readdirSync(directory, {withFileTypes: true})) {
          const preview = /^combinedPDF_([1-9]\d*)_\d+\.pdf$/.exec(file.name);
          if (!file.isFile() || !preview || preview[1] !== String(demographic)) continue;
          const filename = path.join(directory, file.name);
          const actual = crypto.createHash('sha256').update(fs.readFileSync(filename)).digest('hex');
          if (actual === expected) files.set(filename, {hash: actual, inode: fs.lstatSync(filename).ino});
        }
      }
      assert(files.size > 0, 'Generated consultation preview file was not found for owned cleanup');
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
          const stat = fs.lstatSync(filename);
          assert(stat.isFile() && stat.ino === expected.inode
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
