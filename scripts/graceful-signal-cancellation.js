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

// Cooperative cancellation for browser checks that must finish mutations before cleanup.
function createGracefulSignalCancellation({ signalProcess = process, graceMs = 180000 } = {}) {
  let interruption;
  let deadline;
  const handlers = new Map();
  for (const [signal, exitCode] of [['SIGINT', 130], ['SIGTERM', 143]]) {
    const handler = () => {
      if (interruption) return;
      interruption = new Error(`Interrupted by ${signal}`);
      interruption.exitCode = exitCode;
      signalProcess.exitCode = exitCode;
      console.error(`${signal}: waiting for the current operation, then cleaning up test fixtures`);
      deadline = setTimeout(() => {
        console.error('Signal cleanup deadline expired; generated fixtures may need manual cleanup');
        if (typeof signalProcess.exit === 'function') signalProcess.exit(exitCode);
      }, graceMs);
      if (typeof deadline?.unref === 'function') deadline.unref();
    };
    handlers.set(signal, handler);
    signalProcess.on(signal, handler);
  }
  function throwIfCancelled() {
    if (interruption) throw interruption;
  }
  return {
    throwIfCancelled,
    // Never race the operation against a signal: a submitted write must settle
    // before its caller enters finally and starts removing its fixture.
    async run(operation) {
      throwIfCancelled();
      const value = await operation();
      throwIfCancelled();
      return value;
    },
    isCancellation: (error) => error === interruption,
    get exitCode() { return interruption?.exitCode; },
    dispose() {
      clearTimeout(deadline);
      for (const [signal, handler] of handlers) signalProcess.removeListener(signal, handler);
    },
  };
}

// A rejected navigation/click must not abandon another already-started request.
// Preserve ordered values; drain every branch, then throw the first rejection in input order.
async function settleOperations(operations) {
  const results = await Promise.allSettled(operations);
  const failure = results.find((result) => result.status === 'rejected');
  if (failure) throw failure.reason;
  return results.map((result) => result.value);
}

module.exports = { createGracefulSignalCancellation, settleOperations };
