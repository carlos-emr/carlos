/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Passive observations of real browser launches and direct fixture assertions.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const Module = require('node:module');
const source = process.env.CARLOS_FIXTURE_SOURCE;
const directory = process.env.CARLOS_FIXTURE_PROOFS;
const root = process.env.CARLOS_FIXTURE_ROOT;
if (!source || !directory || !root) throw new Error('Missing fixture observation binding');
const digest = () => crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex');
const record = {schemaVersion: 1, source, sourceBefore: digest(), assertions: 0, launches: []};
function directAssertion(caller) {
  const error = {};
  Error.captureStackTrace(error, caller);
  const frame = String(error.stack).split('\n')[1] || '';
  if (frame.includes('(' + source + ':') || frame.includes('at ' + source + ':')) record.assertions++;
}
const wrapped = new WeakMap();
function observeAssertions(value) {
  if (typeof value !== 'function') return value;
  if (wrapped.has(value)) return wrapped.get(value);
  function invoke(target, receiver, argumentsList) {
    directAssertion(invoke);
    return Reflect.apply(target, receiver, argumentsList);
  }
  const proxy = new Proxy(value, {apply: invoke, get(target, key) {
    const member = Reflect.get(target, key);
    return key === 'AssertionError' || key === 'CallTracker' ? member : observeAssertions(member);
  }});
  wrapped.set(value, proxy);
  return proxy;
}
const load = Module._load;
Module._load = function(request, ...argumentsList) {
  const exported = Reflect.apply(load, this, [request, ...argumentsList]);
  return ['assert', 'node:assert', 'assert/strict', 'node:assert/strict'].includes(request)
    ? observeAssertions(exported) : exported;
};
const playwright = require(require.resolve('playwright', {paths: [root]}));
for (const family of ['chromium', 'firefox', 'webkit']) {
  const provider = playwright[family];
  const launch = provider.launch;
  provider.launch = async function(...argumentsList) {
    const browser = await Reflect.apply(launch, this, argumentsList);
    const item = {family, version: browser.version(), disconnected: false};
    record.launches.push(item);
    browser.on('disconnected', () => { item.disconnected = true; });
    return browser;
  };
}
process.on('exit', code => {
  record.exitCode = code;
  record.sourceAfter = digest();
  fs.writeFileSync(path.join(directory, process.pid + '-browser.json'), JSON.stringify(record) + '\n',
    {flag: 'wx', mode: 0o600});
});
