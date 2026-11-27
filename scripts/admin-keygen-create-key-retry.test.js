#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Static guards for Key Pair Creation (issue #6009), run without a deployment:
 *  - the create-key action goes through KeyPairGen.createClientKeys, which stores
 *    nothing until the CARLOS key exists, and never stores a client key itself first;
 *  - the key file the action writes and the parser admin-keygen-create-key-retry relies on agree,
 *    so a change to either cannot leave the browser check reading the wrong section.
 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {parseKeyFile, workflow} = require('./admin-keygen-create-key-retry-playwright-checks');

const action = fs.readFileSync(path.join(__dirname,
  '../src/main/java/io/github/carlos_emr/carlos/admin/gate/ViewKeygenCreateKey2Action.java'), 'utf8');

test('the client key branch asks createClientKeys and never stores the client key first', () => {
  assert.match(action, /KeyPairGen\.createClientKeys\(name, type\)/);
  assert.doesNotMatch(action, /KeyPairGen\.createKeys\(name, type\)/,
    'createKeys stores the client key before the CARLOS key is known to exist');
  assert.doesNotMatch(action, /KeyPairGen\.getPublic\(\)/, 'the CARLOS key is read by createClientKeys, before anything is stored');
  assert.match(action, /ClientKeyStatus\.CARLOS_KEY_MISSING[\s\S]*?The CARLOS key pair has not been created yet/);
});

test('the oscar branch tolerates a failed creation instead of dereferencing null', () => {
  assert.match(action, /"success"\.equals\(KeyPairGen\.createKeys\(SERVER_KEY_NAME, null\)\)/);
  assert.doesNotMatch(action, /createKeys\(SERVER_KEY_NAME, null\)\.equals/);
});

test('the key file the action writes is the one the browser check parses', () => {
  const template = action.match(/byte\[\] keyFile = \(([\s\S]*?)\)\s*\.getBytes\(StandardCharsets\.UTF_8\);/);
  assert.ok(template, 'The create-key action no longer builds the expected UTF-8 attachment');
  // Readable placeholders in the base64 alphabet (+, / and = included), not key material.
  const clientKey = 'FAKEclientPrivateKey==';
  const oscarKey = 'FAKE+CARLOS/publicKey=';
  const keys = {clientPrivateKey: () => clientKey, carlosPublicKey: () => oscarKey};
  const text = vm.runInNewContext(template[1], {name: 'fake-lab.client', keys});
  assert.deepEqual(parseKeyFile(text), {service: 'fake-lab.client', clientPrivate: clientKey, carlosPublic: oscarKey});
});

test('the parser joins base64 that a client wrapped over several lines', () => {
  const text = '-------- Service Name --------\nsvc\n------------------------------\n'
    + '----- Client Private Key -----\nAAAA\nBBBB\n------------------------------\n'
    + '------ Oscar Public Key ------\nCCCC\r\nDD==\n------------------------------';
  assert.deepEqual(parseKeyFile(text), {service: 'svc', clientPrivate: 'AAAABBBB', carlosPublic: 'CCCCDD=='});
});

for (const ambiguous of [false, true]) {
  const response = ambiguous ? 'a lost response' : 'an existing-key refusal';
  test(`the cleanup leaves a concurrent server key in place after ${response}`, async () => {
    const statements = [];
    let cleanup;
    const page = {
      locator: selector => ({
        fill: async () => {},
        selectOption: async () => {},
        innerText: async () => selector === '#fail' ? 'Failed: The oscar key pair has already been created' : 'Key Pair Creation',
        allTextContents: async () => [],
      }),
      waitForResponse: async () => {
        if (ambiguous) throw new Error('Synthetic response timeout');
        return {status: () => 200};
      },
      waitForLoadState: async () => {},
      url: () => 'http://127.0.0.1/carlos/admin/ViewKeygenCreateKey',
      getByRole: () => ({click: async () => {}}),
    };
    const sql = {
      execute: statement => statements.push(statement),
      value: statement => {
        statements.push(statement);
        if (statement.includes('pubKey')) return 'FAKE-existing-key';
        return statement.includes('oscarKeys') ? '1' : '0';
      },
    };
    await assert.rejects(workflow({
      sql,
      marker: 'FAKE-PWkeygen',
      config: {baseUrl: 'http://127.0.0.1/carlos'},
      context: {newPage: async () => page},
      cleanup: handler => { cleanup = handler; },
      step: (description, action) => description.startsWith('the page creates the CARLOS key pair') ? action() : undefined,
    }), ambiguous ? /Synthetic response timeout/ : /already been created/);
    assert.throws(cleanup, /left in place/);
    assert.ok(statements.every(statement => !/pubKey|DELETE FROM oscarKeys/i.test(statement)));
  });
}
