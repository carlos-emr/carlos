/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const test = require('node:test');
const { createGracefulSignalCancellation } = require('./graceful-signal-cancellation');

for (const [signal, exitCode] of [['SIGINT', 130], ['SIGTERM', 143]]) {
  test(`${signal} waits for a submitted write before cleanup and prevents the next write`, async () => {
    const signalProcess = new EventEmitter();
    const cancellation = createGracefulSignalCancellation({ signalProcess });
    const events = [];
    let finishWrite;
    try {
      const work = (async () => {
        try {
          await cancellation.run(() => new Promise((resolve) => {
            events.push('write started');
            finishWrite = () => { events.push('write completed'); resolve(); };
          }));
          events.push('second write');
        } catch (error) {
          assert.equal(cancellation.isCancellation(error), true);
        } finally {
          events.push('cleanup');
        }
      })();
      signalProcess.emit(signal);
      signalProcess.emit(signal);
      await Promise.resolve();
      assert.deepEqual(events, ['write started']);
      finishWrite();
      await work;
      assert.deepEqual(events, ['write started', 'write completed', 'cleanup']);
      await assert.rejects(cancellation.run(() => assert.fail('new operation started')),
        (error) => cancellation.isCancellation(error));
      assert.equal(signalProcess.exitCode, exitCode);
    } finally {
      cancellation.dispose();
    }
    assert.equal(signalProcess.listenerCount(signal), 0);
  });
}

test('a failure in an in-flight operation remains visible after cancellation', async () => {
  const signalProcess = new EventEmitter();
  const cancellation = createGracefulSignalCancellation({ signalProcess });
  const failure = new Error('save failed');
  try {
    const operation = cancellation.run(async () => {
      signalProcess.emit('SIGTERM');
      throw failure;
    });
    await assert.rejects(operation, (error) => error === failure && !cancellation.isCancellation(error));
  } finally {
    cancellation.dispose();
  }
});

test('the grace deadline bounds a hung operation and retains the signal exit code', async () => {
  const signalProcess = new EventEmitter();
  const exits = [];
  signalProcess.exit = (code) => exits.push(code);
  const cancellation = createGracefulSignalCancellation({ signalProcess, graceMs: 10 });
  try {
    signalProcess.emit('SIGINT');
    await new Promise((resolve) => setTimeout(resolve, 30));
    assert.deepEqual(exits, [130]);
  } finally {
    cancellation.dispose();
  }
});

test('a process-like test emitter without exit retains its code past the deadline', async () => {
  const signalProcess = new EventEmitter();
  const cancellation = createGracefulSignalCancellation({ signalProcess, graceMs: 10 });
  try {
    signalProcess.emit('SIGTERM');
    await new Promise((resolve) => setTimeout(resolve, 30));
    assert.equal(signalProcess.exitCode, 143);
  } finally {
    cancellation.dispose();
  }
});

test('numeric fake timer handles do not prevent cooperative cancellation', (t) => {
  const signalProcess = new EventEmitter();
  t.mock.method(global, 'setTimeout', () => 42);
  const clear = t.mock.method(global, 'clearTimeout', () => {});
  const cancellation = createGracefulSignalCancellation({ signalProcess });
  try {
    assert.doesNotThrow(() => signalProcess.emit('SIGTERM'));
    assert.equal(cancellation.exitCode, 143);
    assert.throws(() => cancellation.throwIfCancelled(), (error) => cancellation.isCancellation(error));
  } finally {
    cancellation.dispose();
  }
  assert.deepEqual(clear.mock.calls[0].arguments, [42]);
});
