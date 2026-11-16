#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Static guards for Key Pair Creation (issue #6009), run without a deployment:
 *  - the client-key branch of createKey.jsp goes through KeyPairGen.createClientKeys, which stores
 *    nothing until the CARLOS key exists, and never stores a client key itself first;
 *  - the key file the page writes and the parser admin-keygen-create-key-retry relies on agree,
 *    so a change to either cannot leave the browser check reading the wrong section.
 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {workflow, parseKeyFile} = require('./admin-keygen-create-key-retry-playwright-checks');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/admin/keygen/createKey.jsp'), 'utf8');

test('the client key branch asks createClientKeys and never stores the client key first', () => {
  assert.match(jsp, /KeyPairGen\.createClientKeys\(name, type\)/);
  assert.doesNotMatch(jsp, /KeyPairGen\.createKeys\(name, type\)/,
    'createKeys stores the client key before the CARLOS key is known to exist');
  assert.doesNotMatch(jsp, /KeyPairGen\.getPublic\(\)/, 'the CARLOS key is read by createClientKeys, before anything is stored');
  assert.match(jsp, /ClientKeyStatus\.CARLOS_KEY_MISSING[\s\S]*?The CARLOS key pair has not been created yet/);
});

test('the oscar branch tolerates a failed creation instead of dereferencing null', () => {
  assert.match(jsp, /"success"\.equals\(KeyPairGen\.createKeys\(name, null\)\)/);
  assert.doesNotMatch(jsp, /createKeys\(name, null\)\.equals/);
});

test('the key file the page writes is the one the browser check parses', () => {
  const template = jsp.match(/String keyPairOut = ([\s\S]*?);\n/);
  assert.ok(template, 'createKey.jsp no longer builds keyPairOut');
  // Readable placeholders in the base64 alphabet (+, / and = included), not key material.
  const clientKey = 'FAKEclientPrivateKey==';
  const oscarKey = 'FAKE+CARLOS/publicKey=';
  const text = vm.runInNewContext(template[1], {name: 'fake-lab.client', clientKey, oscarKey});
  assert.deepEqual(parseKeyFile(text), {service: 'fake-lab.client', clientPrivate: clientKey, carlosPublic: oscarKey});
});

test('the parser joins base64 that a client wrapped over several lines', () => {
  const text = '-------- Service Name --------\nsvc\n------------------------------\n'
    + '----- Client Private Key -----\nAAAA\nBBBB\n------------------------------\n'
    + '------ Oscar Public Key ------\nCCCC\r\nDD==\n------------------------------';
  assert.deepEqual(parseKeyFile(text), {service: 'svc', clientPrivate: 'AAAABBBB', carlosPublic: 'CCCCDD=='});
});

for (const outcome of ['duplicate', 'interrupted', 'http-error']) {
  test(`cleanup leaves a concurrently created CARLOS key after a ${outcome} request`, async () => {
    const statements = [];
    let cleanup;
    const page = {
      url: () => 'https://127.0.0.1/carlos/admin/ViewKeygenCreateKey',
      locator: selector => ({
        fill: async () => {}, selectOption: async () => {},
        innerText: async () => selector === '#fail' ? 'Failed: Key pair has already been created' : 'Key Pair Creation',
        allTextContents: async () => [],
      }),
      getByRole: () => ({click: async () => {}}),
      waitForResponse: async () => {
        if (outcome === 'interrupted') throw new Error('synthetic interrupted request');
        return {status: () => outcome === 'http-error' ? 500 : 200};
      },
      waitForLoadState: async () => {},
    };
    const s = {
      marker: 'FAKE-PWconcurrent', config: {baseUrl: 'https://127.0.0.1/carlos'},
      context: {newPage: async () => page},
      sql: {
        execute: (query, params) => statements.push({query, params}),
        value: query => {
          if (query.includes('COUNT(*) FROM publicKeys')) return '0';
          if (query.includes('COUNT(*) FROM oscarKeys')) return '1';
          throw new Error('An unsuccessful creation must never capture a public key');
        },
      },
      cleanup: callback => {cleanup = callback;},
      step: async (name, callback) => {
        if (name === "the page creates the CARLOS key pair under the service name 'oscar'") await callback();
      },
    };
    await assert.rejects(workflow(s), outcome === 'interrupted' ? /synthetic interrupted/ : outcome === 'http-error' ? /HTTP 500/ : /already been created/);
    assert.throws(cleanup, /cannot show it created/);
    assert.deepEqual(statements, [{query: 'DELETE FROM publicKeys WHERE service = ?', params: ['FAKE-PWconcurrent.lab']}]);
  });
}
