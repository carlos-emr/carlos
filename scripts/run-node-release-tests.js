#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {run} = require('node:test');
const crypto = require('node:crypto');
const fingerprint = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

function readiness(summary) {
  const c = summary && summary.counts;
  const keys = ['tests', 'passed', 'failed', 'cancelled', 'skipped', 'todo'];
  if (!c || keys.some(key => !Number.isSafeInteger(c[key]) || c[key] < 0)) return false;
  return summary.success === true && c.tests > 0 && c.passed === c.tests
    && ['failed', 'cancelled', 'skipped', 'todo'].every(key => c[key] === 0);
}

async function execute(files, output, options = {}) {
  if (!files.length || new Set(files).size !== files.length) throw new Error('Empty or duplicate Node selection');
  fs.rmSync(output, {force: true});
  const sourceFiles = Object.fromEntries(files.map(file => [path.relative(process.cwd(), file), fingerprint(file)]));
  const cases = [];
  const containers = new Map();
  const starts = new Map();
  let summary;
  const stream = run({files, concurrency: options.concurrency || 4, execArgv: []});
  for await (const event of stream) {
    const d = event.data;
    if (event.type === 'test:summary' && !d.file) summary = d;
    if (event.type === 'test:start') {
      const stack = (containers.get(d.file) || []).slice(0, d.nesting);
      const ordinal = (starts.get(d.file) || 0) + 1;
      starts.set(d.file, ordinal);
      stack.push({name: d.name, ordinal});
      containers.set(d.file, stack);
    }
    if (['test:pass', 'test:fail'].includes(event.type) && d.details?.type !== 'suite') {
      cases.push({file: path.relative(process.cwd(), d.file || ''), name: d.name,
        line: d.line, column: d.column, testNumber: d.testNumber,
        parents: (containers.get(d.file) || []).slice(0, d.nesting),
        outcome: d.todo ? 'TODO' : d.skip ? 'SKIP' : event.type === 'test:pass' ? 'PASS' : 'FAIL'});
    }
  }
  const sourceChanged = files.some(file => {
    try {return fingerprint(file) !== sourceFiles[path.relative(process.cwd(), file)];}
    catch {return true;}
  });
  const record = {schemaVersion: 3, runtime: process.version, sourceFiles, sourceChanged,
    files: files.map(file => path.relative(process.cwd(), file)),
    summary: summary ? {success: summary.success, counts: summary.counts} : null, cases};
  fs.mkdirSync(path.dirname(output), {recursive: true});
  fs.writeFileSync(output, JSON.stringify(record, null, 2) + '\n', {mode: 0o600, flag: 'wx'});
  const identities = cases.map(c => JSON.stringify([c.file, c.parents, c.testNumber, c.name, c.line, c.column]));
  return !sourceChanged && readiness(summary) && cases.length === summary.counts.tests && new Set(identities).size === cases.length
    && files.every(file => cases.some(c => c.file === path.relative(process.cwd(), file)))
    && cases.every(c => c.outcome === 'PASS' && path.resolve(c.name) !== path.resolve(c.file)) ? 0 : 1;
}

async function main(argv = process.argv.slice(2)) {
  if (argv.length !== 2 || argv[0] !== '--output') throw new Error('Usage: run-node-release-tests.js --output FILE');
  const files = fs.readdirSync(path.join(__dirname)).filter(file => file.endsWith('.test.js'))
    .sort().map(file => path.join(__dirname, file));
  const code = await execute(files, argv[1]);
  console.log(code === 0 ? 'Node release tests: all cases passed' : 'Node release readiness incomplete; see metadata report');
  return code;
}
if (require.main === module) main().then(code => {process.exitCode = code;})
  .catch(error => {console.error(error.message); process.exitCode = 1;});
module.exports = {readiness, execute, main};
