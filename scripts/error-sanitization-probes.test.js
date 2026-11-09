/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const {assertControlledRefusal,assertScheduleDateRefusal}=require('./error-sanitization-playwright-checks');
test('former error probes accept a controlled input refusal',()=>{
  assert.doesNotThrow(()=>assertControlledRefusal('probe',400,'Invalid input',{},'SYNTHETIC_SECRET'));
  assert.doesNotThrow(()=>assertControlledRefusal('missing address',400,'Invalid input',{}));
});
test('former error probes reject login redirects, regressions, reflected input and sanitized 500 bodies',()=>{
  for(const status of [200,302,401,403,404,500]) {
    assert.throws(()=>assertControlledRefusal('probe',status,'Invalid input',{},'SYNTHETIC_SECRET'),/expected HTTP 400/);
  }
  for(const body of ['SYNTHETIC_SECRET','java.lang.Exception: hidden','org.hibernate.Error',
    'An error occurred. Reference ID: 12345678-1234-1234-1234-123456789abc']) {
    assert.throws(()=>assertControlledRefusal('probe',400,body,{},'SYNTHETIC_SECRET'));
  }
  assert.throws(()=>assertControlledRefusal('probe',400,'Invalid input',{'content-disposition':'attachment'},'SYNTHETIC_SECRET'),/download/);
});
test('each malformed satellite address is rejected if reflected raw or HTML encoded',()=>{
  const address='<b>Dr A</b><br>Clinic<br>Street';
  for(const body of [address,'Invalid input: &lt;b&gt;Dr A&lt;/b&gt;&lt;br&gt;Clinic&lt;br&gt;Street']) {
    assert.throws(()=>assertControlledRefusal('satellite',400,body,{},address),/reflected invalid input/);
  }
});
test('Java date-time and custom stack frames cannot pass the marker-independent refusal check',()=>{
  for(const body of ['java.time.format.DateTimeParseException',
    'Failure\n at java.base/java.time.format.DateTimeFormatter.parse(DateTimeFormatter.java:1986)',
    'Failure\n at clinic.custom.Parser.parse(Parser.java:22)']) {
    assert.throws(()=>assertControlledRefusal('probe',400,body,{},'SYNTHETIC_SECRET'),/Java exception or stack-frame/);
  }
});
test('the schedule refusal must come from the route rather than a generic upstream 400',()=>{
  assert.doesNotThrow(()=>assertScheduleDateRefusal(400,'Path parameter has the wrong format',{}));
  for(const body of ['Bad Request','nginx: invalid request','']) {
    assert.throws(()=>assertScheduleDateRefusal(400,body,{}),/did not come from the schedule route/);
  }
});

test('installed fault oracle rejects unsanitized, intercepted, redirected and assertion-free success', () => {
  const {assertSanitizedFault} = require('./error-sanitization-playwright-checks');
  const sanitized = 'Error. Reference ID: 12345678-1234-1234-1234-123456789abc';
  const headers = {'content-type':'text/html;charset=UTF-8'};
  assert.doesNotThrow(() => assertSanitizedFault(500, sanitized, headers));
  for (const [status, body, metadata, reason] of [
    [200, sanitized, headers, /expected HTTP 500/], [302, sanitized, headers, /expected HTTP 500/],
    [500, 'nginx 500', headers, /missing sanitized Reference ID/],
    [500, sanitized + ' FAKE-private-error-5794', headers, /leaked its original body/],
    [500, sanitized + ' java.lang.Exception', headers, /leaked a stack trace/],
    [500, sanitized, {'content-type':'application/json'}, /not replaced by the HTML sanitizer/],
    [500, sanitized, {...headers,'content-disposition':'attachment'}, /started a download/],
  ]) assert.throws(() => assertSanitizedFault(status, body, metadata), reason);
});

test('sendError qualification requires an observed ERROR dispatch as well as a sanitized body', () => {
  const {assertSanitizedFault} = require('./error-sanitization-playwright-checks');
  const body = 'Reference ID: 12345678-1234-1234-1234-123456789abc';
  const headers = {'content-type':'text/html'};
  assert.throws(() => assertSanitizedFault(500, body, headers, true), /real ERROR dispatcher/);
  assert.doesNotThrow(() => assertSanitizedFault(500, body, {...headers,'x-carlos-test-error-dispatch':'observed'}, true));
});

test('installed fault supervision forwards cancellation and waits for fixture restoration', async () => {
  const {EventEmitter} = require('node:events');
  const {superviseInstalledFault} = require('./error-sanitization-playwright-checks');
  const signals = new EventEmitter();
  const child = new EventEmitter();
  const forwarded = [];
  child.kill = signal => forwarded.push(signal);
  const environment = {CARLOS_DISPOSABLE_HOST:'true', RESULT_JSON:'/private/result.json'};
  let settled = false;
  const outcome = superviseInstalledFault({environment, signalProcess:signals, spawn(command, args, options) {
    assert.equal(command, 'python3');
    assert.deepEqual(args, ['scripts/ci/run-installed-error-sanitization.py', '--',
      'node', 'scripts/error-sanitization-playwright-checks.js']);
    assert.equal(options.env, environment);
    return child;
  }}).then(code => { settled = true; return code; });
  signals.emit('SIGTERM'); signals.emit('SIGINT'); signals.emit('SIGTERM');
  await Promise.resolve();
  assert.equal(settled, false, 'parent must wait for Python restoration');
  assert.deepEqual(forwarded, ['SIGTERM', 'SIGINT', 'SIGTERM']);
  child.emit('close', 1, null);
  assert.equal(await outcome, 1, 'restoration/browser failure must propagate');
  assert.equal(signals.listenerCount('SIGTERM') + signals.listenerCount('SIGINT'), 0);
});

test('installed fault supervision propagates a failed spawn and removes signal handlers', async () => {
  const {EventEmitter} = require('node:events');
  const {superviseInstalledFault} = require('./error-sanitization-playwright-checks');
  const signals = new EventEmitter();
  const child = new EventEmitter();
  const outcome = superviseInstalledFault({signalProcess:signals, spawn:() => child});
  child.emit('error', new Error('synthetic missing python'));
  await assert.rejects(outcome, /synthetic missing python/);
  assert.equal(signals.listenerCount('SIGTERM') + signals.listenerCount('SIGINT'), 0);
});
