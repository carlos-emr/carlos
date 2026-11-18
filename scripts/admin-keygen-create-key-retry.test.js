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
const {parseKeyFile} = require('./admin-keygen-create-key-retry-playwright-checks');

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
