/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {decodeHTML} = require('entities');

const ENCRYPTED_BODY_NOTICE_KEY = 'email.compose.msg.encryptedBodyNotice';

/** Recipient-facing notice values from the checked-out application's locale bundles. */
function encryptedBodyNotices(resourceDirectory = path.join(__dirname, '../../src/main/resources')) {
  let bundles;
  try {
    bundles = fs.readdirSync(resourceDirectory).filter(name => /^oscarResources(?:_[A-Za-z_]+)?\.properties$/.test(name))
      .map(name => path.join(resourceDirectory, name));
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    bundles = [];
  }
  function readNotice(file) {
    const prefix = `${ENCRYPTED_BODY_NOTICE_KEY}=`;
    // Resource bundles are ASCII/Java-properties escaped. This key occupies one
    // line; the final definition wins, matching Properties.load.
    const line = fs.readFileSync(file, 'utf8').split(/\r?\n/).filter(value => value.startsWith(prefix)).at(-1);
    return line?.slice(prefix.length).trim().replace(/\\u([\da-fA-F]{4})|\\(.)/g,
      (_, unicode, escaped) => unicode ? String.fromCharCode(parseInt(unicode, 16))
        : ({n: '\n', r: '\r', t: '\t', f: '\f'}[escaped] ?? escaped));
  }
  const notices = bundles.map(readNotice).filter(Boolean);
  // release/2026.08 still has the two-field composer and no such key. Keep a
  // source-attributed copy for the forward-merge contract and source-less runs.
  if (!notices.length) notices.push(readNotice(path.join(__dirname, '../fixtures/email/encrypted-body-notice.properties')));
  assert(notices.every(value => typeof value === 'string' && value.trim()), 'The encrypted-body notice resource is unavailable');
  return [...new Set(notices)];
}

/** Use the page's actual compose contract across the release/develop forward merge. */
async function fillComposeMessage(page, message) {
  const field = page.locator('#message, #bodyEmail');
  // Wait for initial rendering without asking a strict locator to resolve an
  // ambiguous old/new contract before we can report the useful diagnostic.
  await field.first().waitFor({state: 'attached'});
  assert.equal(await field.count(), 1, 'The composer must offer exactly one message contract');
  await field.waitFor({state: 'visible'});
  const unifiedMessage = await field.getAttribute('id') === 'message';
  await field.fill(message);
  return {unifiedMessage};
}

/** Older composers accept a password/clue; newer ones show server-owned values. */
async function prepareComposePassword(page, password, clue) {
  const passwordField = page.locator('#emailPDFPassword');
  const clueField = page.locator('#emailPDFPasswordClue');
  if (await passwordField.isEditable()) await passwordField.fill(password);
  if (await clueField.isEditable()) await clueField.fill(clue);
  const result = {password: await passwordField.inputValue(), clue: await clueField.inputValue()};
  assert(result.password.length >= 5 && result.clue.trim(), 'The encrypted composer has no usable password and delivery clue');
  return result;
}

/** Decode all MIME text parts, including text attachments, before checking for a cleartext leak. */
function visibleMimeText(raw) {
  const separator = /\r?\n\r?\n/.exec(raw);
  assert(separator, 'The SMTP capture has no MIME header/body separator');
  const headers = raw.slice(0, separator.index).replace(/\r?\n[ \t]+/g, ' ');
  const body = raw.slice(separator.index + separator[0].length);
  const contentType = /^Content-Type:\s*([^\r\n]+)/im.exec(headers)?.[1] || 'text/plain';
  if (/^multipart\//i.test(contentType)) {
    const boundary = /\bboundary=(?:"([^"]+)"|([^;\s]+))/i.exec(contentType);
    assert(boundary, 'Multipart SMTP capture has no boundary');
    return body.split(`--${boundary[1] || boundary[2]}`).slice(1)
      .filter(part => !part.startsWith('--'))
      .map(part => visibleMimeText(part.replace(/^\r?\n/, ''))).join('\n');
  }
  if (!/^text\//i.test(contentType)) return '';
  const encoding = /^Content-Transfer-Encoding:\s*(\S+)/im.exec(headers)?.[1].toLowerCase();
  let text = body;
  if (encoding === 'base64') text = Buffer.from(body.replace(/\s/g, ''), 'base64').toString('utf8');
  else if (encoding === 'quoted-printable') {
    text = Buffer.from(body.replace(/=\r?\n/g, '').replace(/=([A-Fa-f0-9]{2})/g,
      (_, hex) => String.fromCharCode(parseInt(hex, 16))), 'latin1').toString('utf8');
  }
  // HTML character references remain readable clinical text after transfer
  // decoding. Other text subtypes must retain their literal ampersands.
  return /^text\/html(?:;|$)/i.test(contentType) ? decodeHTML(text) : text;
}

/** Preserve the release cleartext contract and the unified composer's encrypted-message boundary. */
function assertDeliveredComposeBody(raw, {message, clue, unifiedMessage}, {resourceDirectory} = {}) {
  const visible = visibleMimeText(raw);
  assert(visible.includes(clue), 'The delivered body lacks the password delivery clue');
  if (unifiedMessage) {
    assert(encryptedBodyNotices(resourceDirectory).some(notice => visible.includes(notice)),
      'The encrypted unified-message body lacks its secure-message notice');
    assert(!visible.includes(message), 'The encrypted composed message leaked into cleartext MIME');
  } else {
    assert(visible.includes(message), 'The release composer lost its explicit cleartext body');
  }
}

module.exports = {ENCRYPTED_BODY_NOTICE_KEY, encryptedBodyNotices, fillComposeMessage, prepareComposePassword, visibleMimeText, assertDeliveredComposeBody};
