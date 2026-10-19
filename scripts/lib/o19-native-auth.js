/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

/** Read only an explicit private file; never echo its bytes in an error. */
function readNativeClinicianCredentials(file, clinician) {
  assert(path.isAbsolute(file), 'Native clinician credentials require an absolute private file path');
  const descriptor = fs.openSync(file, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW | fs.constants.O_NONBLOCK);
  try {
    const stat = fs.fstatSync(descriptor);
    assert(stat.isFile() && (stat.mode & 0o7777) === 0o600 && stat.uid === process.getuid() && stat.nlink === 1,
      'Native clinician credential file must be a regular, current-owner0600file');
    assert(stat.size > 0 && stat.size <= 16384, 'Native clinician credential file has an invalid size');
    let credentials;
    try { credentials = JSON.parse(fs.readFileSync(descriptor, 'utf8')); }
    catch { throw new Error('Native clinician credential file must contain valid JSON'); }
    assert(credentials && !Array.isArray(credentials) && typeof credentials === 'object'
      && Object.keys(credentials).sort().join(',') === 'password,pin,user',
    'Native clinician credential file requires exactly user, password and pin');
    for (const key of ['user', 'password', 'pin']) {
      assert(typeof credentials[key] === 'string' && credentials[key].length > 0
        && !credentials[key].includes('\0'), 'Native clinician credential fields must be nonempty text');
    }
    assert(clinician && credentials.user === clinician.userName && credentials.pin === String(clinician.pin),
      'Native clinician credential identity/PIN does not match the discovered active migrated clinician');
    return credentials;
  } finally { fs.closeSync(descriptor); }
}

/** Validate actual imported rows before any browser or credential mutation. */
function validateNativeImportedCredentials(credentials, clinicianRow, adminRow) {
  assert(clinicianRow.userName === credentials.user && clinicianRow.forcePasswordReset === '1',
    'Native clinician must retain the real import forced-reset state');
  assert(adminRow.forcePasswordReset === '1', 'Native administrator must retain the real import forced-reset state');
  // Read-only compatibility verification of the carried legacy SHA1 format; never writes SHA1 credentials.
  const legacy = Array.from(crypto.createHash('sha1').update(credentials.password).digest(),
    value => String(value < 128 ? value : value - 256)).join('');
  assert(clinicianRow.password === legacy,
    'Native clinician credential does not match the imported legacy password representation');
}

/** Native proof drives real first use and leaves the genuine resets in place. */
async function proveNativeClinicianLogin({ credentials, initialRow, adminRow, newPassword, reset, signIn, rejectOld, readRow }) {
  validateNativeImportedCredentials(credentials, initialRow, adminRow);
  await reset(credentials, newPassword);
  const changed = readRow();
  assert(changed.forcePasswordReset === '0' && /^\{bcrypt\}\$2/.test(changed.password)
    && changed.password !== initialRow.password, 'Native clinician reset did not produce a new cleared-reset BCrypt credential');
  await signIn(credentials, newPassword);
  await rejectOld(credentials);
}

/** Keep UI/client diagnostics useful without serializing imported credentials. */
function redactNativeAuthError(error, secrets, stack = false) {
  let text = String(stack ? (error.stack || error.message || error) : (error.message || error));
  const variants = new Set();
  for (const secret of secrets.filter(value => typeof value === 'string' && value.length)) {
    variants.add(secret);
    const json = JSON.stringify(secret);
    variants.add(json); variants.add(json.slice(1, -1));
    // Browser/client call logs can render fill arguments as quoted JavaScript
    // literals, including escaped backslashes, quotes, tabs and newlines.
    const controls = secret.replace(/[\\\r\n\t\b\f\v]/g, character => ({
      '\\': '\\\\', '\r': '\\r', '\n': '\\n', '\t': '\\t', '\b': '\\b', '\f': '\\f', '\v': '\\v',
    })[character]);
    for (const quote of ["'", '"', '`']) {
      let escaped = controls.split(quote).join('\\' + quote);
      if (quote === '`') escaped = escaped.split('${').join('\\${');
      variants.add(escaped); variants.add(quote + escaped + quote);
    }
    variants.add(require('node:util').inspect(secret, { colors: false, maxStringLength: null, compact: true }));
  }
  for (const secret of [...variants].sort((a, b) => b.length - a.length)) {
    text = text.split(secret).join('[redacted]');
  }
  return text;
}

module.exports = { readNativeClinicianCredentials, validateNativeImportedCredentials, proveNativeClinicianLogin, redactNativeAuthError };
