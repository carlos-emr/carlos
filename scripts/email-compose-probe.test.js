/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {ENCRYPTED_BODY_NOTICE_KEY, encryptedBodyNotices, fillComposeMessage, prepareComposePassword, visibleMimeText, assertDeliveredComposeBody} = require('./lib/email-compose-probe');

function composer(id, {password = '', clue = '', editable = true} = {}) {
  const state = {message: '', password, clue, events: []};
  const matches = id === 'both' ? ['message', 'bodyEmail'] : [id].filter(Boolean);
  const strict = () => assert.equal(matches.length, 1, 'strict mode violation: locator resolves to multiple elements');
  return {state, locator(selector) {
    if (selector === '#message, #bodyEmail') return {
      first: () => ({waitFor: async options => {
        assert.deepEqual(options, {state: 'attached'});
        assert(matches.length > 0, 'No message field rendered');
        state.events.push('first-attached');
      }}),
      waitFor: async options => {strict(); assert.deepEqual(options, {state: 'visible'}); state.events.push('strict-visible');},
      count: async () => {state.events.push('count'); return matches.length;},
      getAttribute: async attribute => {strict(); assert.equal(attribute, 'id'); return matches[0];},
      fill: async value => {strict(); state.events.push('fill'); state.message = value;},
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
    assert.deepEqual(page.state.events, ['first-attached', 'count', 'strict-visible', 'fill']);
  });
}
test('ambiguous compose contracts fail before a message is filled', async () => {
  const page = composer('both');
  await assert.rejects(fillComposeMessage(page, 'Synthetic text'), /exactly one message contract/);
  assert.equal(page.state.message, '');
  assert.deepEqual(page.state.events, ['first-attached', 'count'],
    'The diagnostic must precede all strict locator actions, including visibility waits');
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

const [notice] = encryptedBodyNotices();
const contract = {message: 'Synthetic clinical text', clue: 'Fixture clue', unifiedMessage: true};
const mimeText = (body, encoding = '7bit') => `Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: ${encoding}\r\n\r\n${body}`;
for (const [encoding, encode] of [
  ['7bit', text => text], ['base64', text => Buffer.from(text).toString('base64')],
  ['quoted-printable', text => {
    const encoded = text.replaceAll('=', '=3D').replaceAll(' ', '=20');
    return encoded.match(/.{1,70}(?=(?:=[0-9A-F]{2}|<|$))/g)?.join('=\r\n') ?? encoded;
  }],
]) {
  test(`HTML character references cannot hide a ${encoding} clinical-text leak`, () => {
    const htmlMime = html => mimeText(encode(html), encoding).replace('text/plain', 'text/html');
    assertDeliveredComposeBody(htmlMime(`<p>${notice}</p><p>${contract.clue}</p>`), contract);
    for (const separator of ['&#32;', '&#x20;']) {
      const leaked = htmlMime(`<p>${notice}</p><p>${contract.clue}</p><p>Synthetic${separator}clinical text</p>`);
      assert(visibleMimeText(leaked).includes(contract.message));
      assert.throws(() => assertDeliveredComposeBody(leaked, contract), /leaked into cleartext MIME/);
    }
  });
}
test('HTML named references decode once while other text subtypes preserve literal references', () => {
  assert.equal(visibleMimeText(mimeText('Synthetic &amp; clinical text').replace('text/plain', 'text/html')),
    'Synthetic & clinical text');
  for (const subtype of ['text/plain', 'text/calendar', 'text/json']) {
    assert.equal(visibleMimeText(mimeText('Synthetic&#32;clinical text').replace('text/plain', subtype)),
      'Synthetic&#32;clinical text');
  }
});
test('the standalone validation runtime pins the declared HTML decoder dependency', () => {
  const pinned = require('../package.json').devDependencies.entities;
  const lock = require('../package-lock.json');
  assert.equal(pinned, '4.5.0');
  assert.equal(lock.packages[''].devDependencies.entities, pinned);
  assert.equal(lock.packages['node_modules/entities'].version, pinned);
  const wrapper = fs.readFileSync(path.join(__dirname, 'deb-docker-validation.sh'), 'utf8');
  assert.equal(wrapper.match(/^ENTITIES_VERSION="([^"]+)"$/m)?.[1], pinned);
  assert.match(wrapper, /^ARG ENTITIES_VERSION$/m);
  assert.match(wrapper, /"entities@\$\{ENTITIES_VERSION\}"/);
  assert.match(wrapper, /--build-arg ENTITIES_VERSION="\$ENTITIES_VERSION"/);
});
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
test('localized and reworded recipient notices follow the application bundle, not fixed English phrases', t => {
  const resourceDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-email-notice-'));
  t.after(() => fs.rmSync(resourceDirectory, {recursive: true, force: true}));
  const translated = 'Un document confidentiel est joint. Ouvrez le PDF chiffré.';
  fs.writeFileSync(path.join(resourceDirectory, 'oscarResources_fr.properties'),
    `${ENCRYPTED_BODY_NOTICE_KEY}=Un document confidentiel est joint. Ouvrez le PDF chiffr\\u00e9.\n`);
  assert.deepEqual(encryptedBodyNotices(resourceDirectory), [translated]);
  assertDeliveredComposeBody(mimeText(`${translated}\r\n${contract.clue}`), contract, {resourceDirectory});
  assert.throws(() => assertDeliveredComposeBody(mimeText(`${notice}\r\n${contract.clue}`), contract, {resourceDirectory}),
    /secure-message notice/, 'A stale notice must fail when the application bundle changes');
});
test('multipart text is decoded but PDF attachments do not count as visible message text', () => {
  const raw = 'Content-Type: multipart/mixed;\r\n boundary="fixture"\r\n\r\n'
    + `--fixture\r\n${mimeText(`${notice}\r\n${contract.clue}`)}\r\n`
    + '--fixture\r\nContent-Type: application/pdf\r\nContent-Transfer-Encoding: base64\r\n\r\n'
    + `${Buffer.from(contract.message).toString('base64')}\r\n--fixture--\r\n`;
  assert.equal(visibleMimeText(raw).trim(), `${notice}\r\n${contract.clue}`);
  assertDeliveredComposeBody(raw, contract);
});
for (const contentType of ['text/calendar', 'text/json']) {
  for (const [encoding, encode] of [
    ['base64', text => Buffer.from(text).toString('base64')],
    ['quoted-printable', text => text.replaceAll(' ', '=20').replace('clinical', 'cli=\r\nnical')],
  ]) {
    test(`encrypted compose detects a clinical-text leak in a ${encoding} ${contentType} attachment`, () => {
      const capture = attachmentText => 'Content-Type: multipart/mixed; boundary="fixture"\r\n\r\n'
        + `--fixture\r\n${mimeText(`${notice}\r\n${contract.clue}`)}\r\n`
        + `--fixture\r\nContent-Type: ${contentType}; charset=UTF-8\r\n`
        + 'Content-Disposition: attachment; filename="fixture.txt"\r\n'
        + `Content-Transfer-Encoding: ${encoding}\r\n\r\n${encode(attachmentText)}\r\n--fixture--\r\n`;
      assertDeliveredComposeBody(capture('Unrelated synthetic event'), contract);
      const leaked = capture(`Synthetic event: ${contract.message}`);
      assert(visibleMimeText(leaked).includes(contract.message), 'Every text subtype must be decoded');
      assert.throws(() => assertDeliveredComposeBody(leaked, contract), /leaked into cleartext MIME/);
    });
  }
}
