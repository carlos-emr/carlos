/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const { spawnSync } = require('node:child_process');

const MAX_BYTES = 2 * 1024 * 1024;
const MAX_RECORDS = 10001; // Includes the already-observed starting record.
const COMMAND_TIMEOUT_MS = 10000;

/** A bounded, explicit journal source; never include journal text or stderr in errors. */
function createJournalLogSource(unit, { run = spawnSync } = {}) {
  if (typeof unit !== 'string' || unit.length > 200 || !/^[A-Za-z0-9][A-Za-z0-9_.@-]*\.service$/.test(unit)
      || /[\r\n]/.test(unit)) {
    throw new Error('CARLOS_LOG_JOURNAL_UNIT must name one explicit systemd .service unit');
  }
  const baseArgs = ['--no-pager', '--boot=0', `--unit=${unit}`, '--output=json', '--all',
    '--output-fields=__CURSOR,__REALTIME_TIMESTAMP,MESSAGE'];

  function validBoundary(record) {
    return record && typeof record.__CURSOR === 'string'
      && record.__CURSOR.length <= 1024 && /^[A-Za-z0-9_=:;.-]+$/.test(record.__CURSOR)
      && !/[\r\n]/.test(record.__CURSOR)
      && typeof record.__REALTIME_TIMESTAMP === 'string' && /^[0-9]{1,20}$/.test(record.__REALTIME_TIMESTAMP)
      && !/[\r\n]/.test(record.__REALTIME_TIMESTAMP);
  }

  function records(extraArgs) {
    let result;
    try {
      result = run('journalctl', [...baseArgs, ...extraArgs], {
        encoding: 'utf8', timeout: COMMAND_TIMEOUT_MS, maxBuffer: MAX_BYTES,
        stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true,
        env: { ...process.env, LC_ALL: 'C', SYSTEMD_COLORS: '0' },
      });
    } catch (_) { throw new Error('Journal log scan command failed'); }
    // A successful exit can still warn that system journals were inaccessible.
    // Do not use --quiet: it hides exactly that warning.
    if (!result || result.error || result.signal || result.status !== 0 || String(result.stderr || '').trim()) {
      throw new Error('Journal log scan failed or journal access is incomplete');
    }
    const output = result.stdout;
    if (typeof output !== 'string' || Buffer.byteLength(output, 'utf8') > MAX_BYTES) {
      throw new Error('Journal log scan exceeded its output limit');
    }
    const lines = output.split('\n').filter(line => line.trim());
    if (lines.length > MAX_RECORDS) throw new Error('Journal log scan exceeded its record limit');
    return lines.map(line => {
      let record;
      try { record = JSON.parse(line); } catch (_) { throw new Error('Journal log scan returned malformed records'); }
      if (!validBoundary(record) || typeof record.MESSAGE !== 'string') {
        throw new Error('Journal log scan returned incomplete records');
      }
      return record;
    });
  }

  function capture() {
    const result = records(['--lines=1']);
    if (result.length !== 1) throw new Error('Journal log scan could not capture a boundary record');
    return { __CURSOR: result[0].__CURSOR, __REALTIME_TIMESTAMP: result[0].__REALTIME_TIMESTAMP };
  }

  function readDelta(start) {
    if (!validBoundary(start)) throw new Error('Journal log scan has an invalid starting cursor');
    const end = capture();
    if (start.__CURSOR === end.__CURSOR) {
      if (start.__REALTIME_TIMESTAMP !== end.__REALTIME_TIMESTAMP) throw new Error('Journal log scan boundary metadata changed');
      return { text: '', entries: 0 };
    }
    const timestamp = BigInt(end.__REALTIME_TIMESTAMP);
    const until = `@${timestamp / 1000000n}.${String(timestamp % 1000000n).padStart(6, '0')}`;
    // Include the starting record to prove it still exists. --after-cursor alone
    // can silently seek to a nearby record after journal vacuuming.
    const interval = records([`--cursor=${start.__CURSOR}`, `--until=${until}`, `--lines=+${MAX_RECORDS}`]);
    if (!interval.length || interval[0].__CURSOR !== start.__CURSOR
        || interval[0].__REALTIME_TIMESTAMP !== start.__REALTIME_TIMESTAMP) {
      throw new Error('Journal log scan starting boundary is missing or changed');
    }
    const endIndex = interval.findIndex(record => record.__CURSOR === end.__CURSOR);
    if (endIndex < 1 || interval[endIndex].__REALTIME_TIMESTAMP !== end.__REALTIME_TIMESTAMP) {
      throw new Error('Journal log scan ending boundary is missing or its interval exceeds the limit');
    }
    const selected = interval.slice(1, endIndex + 1);
    if (new Set(interval.slice(0, endIndex + 1).map(record => record.__CURSOR)).size !== endIndex + 1) {
      throw new Error('Journal log scan returned duplicate cursors');
    }
    return { text: selected.map(record => record.MESSAGE).join('\n'), entries: selected.length };
  }

  return { capture, readDelta };
}

module.exports = { createJournalLogSource, MAX_BYTES, MAX_RECORDS, COMMAND_TIMEOUT_MS };
