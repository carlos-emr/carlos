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
const {parseKeyFile} = require('./admin-keygen-create-key-retry-playwright-checks');

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
  const clientKey = 'TUlJQ2xpZW50UHJpdmF0ZUtleQ==';
  const oscarKey = 'TUlJQ0FSTE9TUHVibGljS2V5+/==';
  const text = vm.runInNewContext(template[1], {name: 'fake-lab.client', clientKey, oscarKey});
  assert.deepEqual(parseKeyFile(text), {service: 'fake-lab.client', clientPrivate: clientKey, carlosPublic: oscarKey});
});

test('the parser joins base64 that a client wrapped over several lines', () => {
  const text = '-------- Service Name --------\nsvc\n------------------------------\n'
    + '----- Client Private Key -----\nAAAA\nBBBB\n------------------------------\n'
    + '------ Oscar Public Key ------\nCCCC\r\nDD==\n------------------------------';
  assert.deepEqual(parseKeyFile(text), {service: 'svc', clientPrivate: 'AAAABBBB', carlosPublic: 'CCCCDD=='});
});
