/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {fillComposeMessage, prepareComposePassword, visibleMimeText, assertDeliveredComposeBody} = require('./lib/email-compose-probe');

function composer(id, {password = '', clue = '', editable = true} = {}) {
  const state = {message: '', password, clue};
  return {state, locator(selector) {
    if (selector === '#message, #bodyEmail') return {
      waitFor: async () => assert(id), count: async () => id === 'both' ? 2 : 1,
      getAttribute: async attribute => {assert.equal(attribute, 'id'); return id;},
      fill: async value => {state.message = value;},
    };
    const key = {'#emailPDFPassword': 'password', '#emailPDFPasswordClue': 'clue'}[selector];
    assert(key, `Unexpected composer selector ${selector}`);
    return {isEditable: async () => editable, fill: async value => {
      assert(editable, 'A server-owned password or clue must never be overwritten'); state[key] = value;
    }, inputValue: async () => state[key]};
  }};
}

for (const [id, unifiedMessage] of [['bodyEmail', false], ['message', true]]) {
  test(`the ${id} composer is filled and identifies its actual contract`, async () => {
    const page = composer(id);
    assert.deepEqual(await fillComposeMessage(page, 'Synthetic clinical text'), {unifiedMessage});
    assert.equal(page.state.message, 'Synthetic clinical text');
  });
}
test('ambiguous compose contracts fail before a message is filled', async () => {
  const page = composer('both');
  await assert.rejects(fillComposeMessage(page, 'Synthetic text'), /exactly one message contract/);
  assert.equal(page.state.message, '');
});
test('release password fields receive fixture values', async () => {
  const page = composer('bodyEmail');
  assert.deepEqual(await prepareComposePassword(page, 'PWfixture', 'Fixture clue'), {password: 'PWfixture', clue: 'Fixture clue'});
});
test('server-generated passwords and clues are read without editing them', async () => {
  const page = composer('message', {password: 'Server-owned password', clue: 'Separate delivery', editable: false});
  assert.deepEqual(await prepareComposePassword(page, 'PWfixture', 'Fixture clue'),
    {password: 'Server-owned password', clue: 'Separate delivery'});
});
test('missing server-owned encryption prerequisites fail explicitly', async () => {
  await assert.rejects(prepareComposePassword(composer('message', {editable: false}), 'PWfixture', 'Fixture clue'),
    /no usable password and delivery clue/);
});

const notice = 'You have a secure message from your clinic. Please open the attached password-protected PDF to read it.';
const contract = {message: 'Synthetic clinical text', clue: 'Fixture clue', unifiedMessage: true};
const mimeText = (body, encoding = '7bit') => `Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: ${encoding}\r\n\r\n${body}`;
test('release cleartext composer retains the composed body and password clue assertion', () => {
  assertDeliveredComposeBody(mimeText(`${contract.message}\r\n${contract.clue}`), {...contract, unifiedMessage: false});
  assert.throws(() => assertDeliveredComposeBody(mimeText(contract.clue), {...contract, unifiedMessage: false}), /lost its explicit cleartext body/);
});
for (const [encoding, encode] of [
  ['7bit', text => text], ['base64', text => Buffer.from(text).toString('base64')],
  ['quoted-printable', text => text.replaceAll(' ', '=20').replace('clinical', 'cli=\r\nnical')],
]) {
  test(`unified encrypted composer requires notice and clue and catches a ${encoding} cleartext leak`, () => {
    const clean = mimeText(encode(`${notice}\r\n${contract.clue}`), encoding);
    assertDeliveredComposeBody(clean, contract);
    const leak = mimeText(encode(`${notice}\r\n${contract.message}\r\n${contract.clue}`), encoding);
    assert.throws(() => assertDeliveredComposeBody(leak, contract), /leaked into cleartext MIME/);
  });
}
test('the encrypted body requires the actual secure-message notice and clue', () => {
  assert.throws(() => assertDeliveredComposeBody(mimeText(contract.clue), contract), /secure-message notice/);
  assert.throws(() => assertDeliveredComposeBody(mimeText(notice), contract), /delivery clue/);
});
test('multipart text is decoded but PDF attachments do not count as visible message text', () => {
  const raw = 'Content-Type: multipart/mixed;\r\n boundary="fixture"\r\n\r\n'
    + `--fixture\r\n${mimeText(`${notice}\r\n${contract.clue}`)}\r\n`
    + '--fixture\r\nContent-Type: application/pdf\r\nContent-Transfer-Encoding: base64\r\n\r\n'
    + `${Buffer.from(contract.message).toString('base64')}\r\n--fixture--\r\n`;
  assert.equal(visibleMimeText(raw).trim(), `${notice}\r\n${contract.clue}`);
  assertDeliveredComposeBody(raw, contract);
});
