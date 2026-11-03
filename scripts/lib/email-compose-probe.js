/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');

/** Use the page's actual compose contract across the release/develop forward merge. */
async function fillComposeMessage(page, message) {
  const field = page.locator('#message, #bodyEmail');
  await field.waitFor({state: 'visible'});
  assert.equal(await field.count(), 1, 'The composer must offer exactly one message contract');
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

/** Decode visible MIME text, never PDF attachments, before checking for a cleartext leak. */
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
  if (!/^text\/(?:plain|html)(?:;|$)/i.test(contentType)) return '';
  const encoding = /^Content-Transfer-Encoding:\s*(\S+)/im.exec(headers)?.[1].toLowerCase();
  if (encoding === 'base64') return Buffer.from(body.replace(/\s/g, ''), 'base64').toString('utf8');
  if (encoding === 'quoted-printable') {
    return Buffer.from(body.replace(/=\r?\n/g, '').replace(/=([A-Fa-f0-9]{2})/g,
      (_, hex) => String.fromCharCode(parseInt(hex, 16))), 'latin1').toString('utf8');
  }
  return body;
}

/** Preserve the release cleartext contract and the unified composer's encrypted-message boundary. */
function assertDeliveredComposeBody(raw, {message, clue, unifiedMessage}) {
  const visible = visibleMimeText(raw);
  assert(visible.includes(clue), 'The delivered body lacks the password delivery clue');
  if (unifiedMessage) {
    assert(/secure message from your clinic/i.test(visible) && /password-protected PDF/i.test(visible),
      'The encrypted unified-message body lacks its secure-message notice');
    assert(!visible.includes(message), 'The encrypted composed message leaked into cleartext MIME');
  } else {
    assert(visible.includes(message), 'The release composer lost its explicit cleartext body');
  }
}

module.exports = {fillComposeMessage, prepareComposePassword, visibleMimeText, assertDeliveredComposeBody};
