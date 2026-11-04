/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/documentMutation.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const busy = {status: 503, retryAfter: '1', data: {success: false, accepted: false, retryable: true}};
const saved = {status: 200, data: {success: true, accepted: true, newDocNum: 42, pageCount: 3}};

function harness(overrides = {}, windowOverrides = {}) {
  const requests = [], timers = [], events = [];
  const window = {setTimeout(callback, delay) {const timer = {callback, delay}; timers.push(timer); return timer;},
    clearTimeout(timer) {if (timer) timer.cancelled = true;}, ...windowOverrides};
  const context = vm.createContext({window});
  vm.runInContext(source, context);
  const options = {
    send(body) {return new Promise((resolve, reject) => requests.push({body, resolve, reject}));},
    isSuccess: data => Number.isSafeInteger(data.newDocNum) && data.newDocNum > 0,
    random: () => .5,
    ...Object.fromEntries(['onState', 'onWaiting', 'onSuccess', 'onRejected', 'onUncertain', 'onCancelled']
      .map(name => [name, value => events.push({name, value})])),
    ...overrides,
  };
  const api = window.CarlosDocumentMutation.create(options);
  return {api, requests, timers, events,
    state: () => events.filter(e => e.name === 'onState').at(-1)?.value,
    async reply(result, index = requests.length - 1) {requests[index].resolve(result); await flush();},
    async start(body = 'page=1%2C0&page=2%2C180&document=7') {assert.equal(api.start(body), true); await flush();},
    async tick(timer = timers.at(-1)) {timer.callback(); await flush();},
  };
}

test('many busy replies retry exactly the frozen ordered payload with one request or timer and capped jitter', async () => {
  let sample = 0;
  const h = harness({random: () => sample});
  await h.start();
  const delays = [];
  for (let i = 0; i < 8; i++) {
    assert.equal(h.api.start('different clinical payload'), false);
    sample = i % 2;
    await h.reply(busy);
    assert.equal(h.state(), 'waiting');
    assert.equal(h.api.start('changed selection'), false);
    assert.equal(h.requests.length, i + 1);
    assert.ok(h.timers.at(-1).delay >= 1000 && h.timers.at(-1).delay <= 10000);
    if (i >= 5) delays.push(h.timers.at(-1).delay);
    await h.tick();
    assert.equal(h.state(), 'pending');
  }
  assert.ok(new Set(delays).size > 1, 'steady state retries remain spread across clients');
  await h.reply(saved);
  assert.equal(h.state(), 'idle');
  assert.equal(h.api.isLocked(), false);
  assert.equal(new Set(h.requests.map(r => r.body)).size, 1);
  assert.deepEqual(h.events.filter(e => e.name === 'onSuccess').map(e => e.value), [saved.data]);
});

test('explicit unaccepted rejection preserves manual retry without automatic retry', async () => {
  const h = harness(); await h.start();
  await h.reply({status: 422, data: {success: false, accepted: false, retryable: false}});
  assert.equal(h.state(), 'idle'); assert.equal(h.timers.length, 0);
  assert.equal(h.events.filter(e => e.name === 'onRejected').length, 1);
  await h.start('page=2%2C0&document=7');
  assert.equal(h.requests.length, 2);
});

for (const [name, response] of [
  ['untyped busy', {status: 503, data: {error: 'busy'}}],
  ['possibly accepted busy', {status: 503, data: {success: false, accepted: true, retryable: true}}],
  ['contradictory HTTP success', {status: 200, data: {success: false, accepted: false, retryable: true}}],
  ['missing accepted flag', {status: 200, data: {success: true, newDocNum: 42}}],
  ['invalid document identifier', {status: 200, data: {success: true, accepted: true, newDocNum: -1}}],
  ['HTML response', {status: 200, data: '<html>Login</html>'}],
  ['possibly accepted failure', {status: 500, data: {success: false, accepted: true, retryable: false}}],
  ['contradictory failed success', {...saved, status: 500}],
]) {
  test(`${name} stays locked with no automatic or manual replay`, async () => {
    const h = harness(); await h.start(); await h.reply(response);
    assert.equal(h.state(), 'uncertain'); assert.equal(h.api.isLocked(), true);
    assert.equal(h.api.start('duplicate'), false); assert.equal(h.api.cancelWaiting(), false);
    assert.equal(h.timers.length, 0); assert.equal(h.requests.length, 1);
  });
}

test('network failure and synchronous dispatch failure stay locked', async () => {
  const h = harness(); await h.start(); h.requests[0].reject(new Error('connection closed')); await flush();
  assert.equal(h.state(), 'uncertain'); assert.equal(h.timers.length, 0);
  const throwing = harness({send() {throw new Error('send failed');}});
  await throwing.start(); assert.equal(throwing.state(), 'uncertain');
});

test('only confirmed waiting can be cancelled, and a cleared stale timer cannot submit again', async () => {
  const h = harness(); await h.start();
  assert.equal(h.api.cancelWaiting(), false);
  await h.reply(busy); const old = h.timers.at(-1);
  assert.equal(h.api.cancelWaiting(), true); assert.equal(old.cancelled, true);
  assert.equal(h.state(), 'idle');
  await h.start('new selection'); await h.tick(old);
  assert.equal(h.requests.length, 2); assert.equal(h.requests[1].body, 'new selection');
});

test('pagehide cancels known waiting without replay on pageshow', async () => {
  const h = harness(); await h.start(); await h.reply(busy);
  const old = h.timers.at(-1); h.api.hide();
  assert.equal(old.cancelled, true); assert.equal(h.api.start('hidden'), false);
  h.api.show(); await h.tick(old);
  assert.equal(h.state(), 'idle'); assert.equal(h.requests.length, 1);
});

test('hidden in-flight work stays locked; late accepted success is delivered once on pageshow', async () => {
  const h = harness(); await h.start(); h.api.hide();
  assert.equal(h.api.isLocked(), true); assert.equal(h.api.cancelWaiting(), false);
  await h.reply(saved);
  assert.equal(h.events.filter(e => e.name === 'onSuccess').length, 0);
  assert.equal(h.api.isLocked(), true);
  h.api.show(); h.api.show();
  assert.equal(h.events.filter(e => e.name === 'onSuccess').length, 1);
  assert.equal(h.api.isLocked(), false);
});

test('late hidden busy cancels safely, while a late unknown outcome remains locked', async () => {
  const h = harness(); await h.start(); h.api.hide(); await h.reply(busy);
  assert.equal(h.state(), 'idle'); assert.equal(h.timers.length, 0);
  assert.equal(h.events.filter(e => e.name === 'onCancelled').length, 1);
  const unknown = harness(); await unknown.start(); unknown.api.hide(); await unknown.reply({status: 0});
  unknown.api.show(); assert.equal(unknown.state(), 'uncertain'); assert.equal(unknown.api.start('duplicate'), false);
});

for (const callback of ['onState', 'onWaiting', 'isSuccess', 'onSuccess', 'onRejected', 'onCancelled']) {
  test(`throwing ${callback} callback cannot strand waiting work or unlock an uncertain result`, async () => {
    const h = harness({[callback]() {throw new Error('UI failure');}});
    await h.start();
    if (callback !== 'onState') {
      if (callback === 'onWaiting' || callback === 'onCancelled') await h.reply(busy);
      else if (callback === 'onRejected') await h.reply({status: 422, data: {success: false, accepted: false, retryable: false}});
      else await h.reply(saved);
      if (callback === 'onCancelled') h.api.cancelWaiting();
    }
    assert.equal(h.api.isLocked(), true);
    assert.equal(h.api.start('duplicate'), false);
    assert.equal(h.events.filter(e => e.name === 'onUncertain').length, 1);
    assert.ok(h.timers.every(timer => timer.cancelled));
  });
}

test('broken uncertain notification is contained without an unhandled rejection', async () => {
  const h = harness({onUncertain() {throw new Error('UI also broken');}});
  await h.start(); await h.reply({status: 0}); assert.equal(h.api.isLocked(), true);
});

test('a waiting callback may cancel without leaving a new retry timer', async () => {
  let h;
  h = harness({onWaiting() {h.api.cancelWaiting();}});
  await h.start(); await h.reply(busy);
  assert.equal(h.state(), 'idle'); assert.equal(h.timers.length, 0);
});

test('production jitter uses crypto and safely falls back when crypto is unavailable', async () => {
  const crypto = {getRandomValues(array) {array[0] = 2147483648; return array;}};
  const h = harness({random: undefined}, {crypto}); await h.start(); await h.reply(busy);
  assert.equal(h.timers[0].delay, 1125);
  const fallback = harness({random: undefined}); await fallback.start(); await fallback.reply(busy);
  assert.ok(fallback.timers[0].delay >= 1000 && fallback.timers[0].delay < 1250);
});

test('pagehide before the dispatch microtask cancels a proven unsent intent even after immediate pageshow', async () => {
  const h = harness();
  assert.equal(h.api.start('document=7&method=rotate90'), true);
  h.api.hide(); h.api.show();
  await flush();
  assert.equal(h.requests.length, 0);
  assert.equal(h.state(), 'idle');
  assert.equal(h.events.filter(e => e.name === 'onCancelled').length, 1);
  assert.equal(h.events.filter(e => e.name === 'onUncertain').length, 0);
});

test('pagehide after a retry timer fires but before transport cannot dispatch that retry', async () => {
  const h = harness(); await h.start(); await h.reply(busy);
  h.timers[0].callback(); h.api.hide(); h.api.show(); await flush();
  assert.equal(h.requests.length, 1);
  assert.equal(h.state(), 'idle');
  assert.equal(h.events.filter(e => e.name === 'onCancelled').length, 1);
});

for (const throwing of [false, true]) test(`a ${throwing ? 'throwing' : 'refusing'} pre-dispatch guard is known unaccepted`, async () => {
  const h = harness({beforeSend() {if (throwing) throw new Error('Local token unavailable'); return false;}});
  await h.start();
  assert.equal(h.requests.length, 0); assert.equal(h.timers.length, 0);
  assert.equal(h.state(), 'idle'); assert.equal(h.api.isLocked(), false);
  const rejection = h.events.find(e => e.name === 'onRejected');
  assert.equal(rejection.value.accepted, false); assert.equal(rejection.value.local, true);
  assert.equal(h.events.filter(e => e.name === 'onUncertain').length, 0);
});

test('changed session guard cancels a later retry without rebasing the frozen intent', async () => {
  let sameSession = true;
  const h = harness({beforeSend: () => sameSession});
  await h.start(); await h.reply(busy); sameSession = false; await h.tick();
  assert.equal(h.requests.length, 1); assert.equal(h.state(), 'idle');
  assert.equal(h.events.filter(e => e.name === 'onRejected').length, 1);
  assert.equal(h.events.filter(e => e.name === 'onUncertain').length, 0);
});
